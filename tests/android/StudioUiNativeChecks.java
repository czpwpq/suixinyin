package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Device-only lifecycle and one-page flow checks, invoked from an Instrumentation thread.
 * Launches the actual production Activity to catch Activity/provider/declared class failures,
 * then uses a fake transfer Host. It never invokes MainActivity's real NFC sender and never
 * calls a weather endpoint. Selecting a photo is simulated after the real editor pick button.
 * Call run(instrumentation) from an existing test runner; this is not a standalone runner.
 */
public final class StudioUiNativeChecks {
    private StudioUiNativeChecks() { }
    private interface UiAction { void run() throws Exception; }

    public static void run(Instrumentation instrumentation) throws Exception {
        check(Looper.myLooper()!=Looper.getMainLooper(),"Run native checks on the instrumentation thread");
        Context target=instrumentation.getTargetContext();
        SharedPreferences preferences=target.getSharedPreferences("studio",Context.MODE_PRIVATE);
        Map<String,?> saved=new HashMap<>(preferences.getAll());
        SharedPreferences refreshPreferences=target.getSharedPreferences("refresh-settings",Context.MODE_PRIVATE);
        Map<String,?> savedRefresh=new HashMap<>(refreshPreferences.getAll());
        Activity activity=null;final AtomicReference<EditorStudio> holder=new AtomicReference<>();
        try {
            // Selecting the Root backend does not execute Root. It prevents automatic
            // Shizuku binding while the real Activity is used as the UI-test container.
            check(refreshPreferences.edit().putBoolean("rootBackend",true).commit(),"Cannot isolate native checks from real Shizuku binding");
            Intent launch=new Intent();launch.setClassName(target,"org.nfcresearch.qpolllab.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=instrumentation.startActivitySync(launch);
            check(activity!=null,"Production MainActivity did not launch");
            instrumentation.waitForIdleSync();
            final Activity screen=activity;
            ui(instrumentation,()->HomeNativeChecks.run(screen));
            BridgeNativeChecks.runLifecycle(instrumentation,screen);
            PhotoWorkspaceNativeChecks.run(instrumentation,screen);
            final FakeHost host=new FakeHost();
            ui(instrumentation,()->{
                Object production=read(screen,"studio");
                check(production instanceof EditorStudio,"Activity did not initialize the production editor");
                ((EditorStudio)production).dismiss();
                EditorStudio editor=new EditorStudio(screen,host);holder.set(editor);editor.open(EditorStudio.PHOTO);
                PhotoPlacementNativeChecks.run(screen);
            });
            EditorStudio editor=holder.get();
            AtomicReference<Dialog> sameDialog=new AtomicReference<>();
            ui(instrumentation,()->{
                Dialog dialog=(Dialog)read(editor,"dialog");sameDialog.set(dialog);
                check(dialog.isShowing(),"Photo editor is not visible");
                check(((Number)read(editor,"step")).intValue()==0,"Empty photo workflow must start with selection");
                check(((View)read(editor,"toolsPanel")).getVisibility()!=View.VISIBLE,"Photo parameters must start collapsed");
            });

            // Picker cancellation must resume the exact same editing page and caption.
            ui(instrumentation,()->{
                ((Button)read(editor,"sendButton")).performClick();
                check(host.picks==1,"Pick button did not reach Host.selectPhoto");
                check(Boolean.TRUE.equals(read(editor,"picking")),"Editor did not enter picker state");
                editor.onHostStop();
                check(read(editor,"dialog")==sameDialog.get(),"onHostStop dismissed the photo edit session");
                check(!sameDialog.get().isShowing(),"Picker should hide the editor");
                editor.photoCancelled();
                check(read(editor,"dialog")==sameDialog.get()&&sameDialog.get().isShowing(),"Picker cancellation opened another page");
                check(host.sends==0,"Photo picker cancellation sent a frame");
                ((Button)read(editor,"sendButton")).performClick();host.photo=sample(false);editor.deliverPhoto(host.photo);
                check(((Number)read(editor,"step")).intValue()==1,"Photo import must return to crop stage");
                ((EditText)read(editor,"text")).setText("保留这段文字 · Native test");((SeekBar)read(editor,"angle")).setProgress(227);
                check(((View)read(editor,"placement")).getVisibility()==View.VISIBLE&&((View)read(editor,"preview")).getVisibility()!=View.VISIBLE,"Crop must show only its editable canvas");
            });
            checkLayout(instrumentation,editor);noImplicitTransfer(host);
            ui(instrumentation,()->{((Button)read(editor,"sendButton")).performClick();check(((Number)read(editor,"step")).intValue()==2&&host.sends==0,"Crop CTA did not enter preview without sending");});
            PanelImage.Prepared first=awaitFrame(instrumentation,editor);validate(first);checkLayout(instrumentation,editor);
            checkPreviewTools(instrumentation,editor);
            ui(instrumentation,()->{
                check(((View)read(editor,"preview")).getVisibility()==View.VISIBLE&&((View)read(editor,"placement")).getVisibility()!=View.VISIBLE,"Preview must show only the final pixel canvas");
                ((Button)read(editor,"backButton")).performClick();check(((Number)read(editor,"step")).intValue()==1,"Back from preview lost the crop stage");
            });

            // Successful import must not dismiss/re-open the editor or lose text controls.
            ui(instrumentation,()->{
                invoke(editor,"showStep",new Class<?>[]{int.class},0);((Button)read(editor,"sendButton")).performClick();editor.onHostStop();
                host.photo=sample(true);editor.deliverPhoto(host.photo);
                check(read(editor,"dialog")==sameDialog.get()&&sameDialog.get().isShowing(),"Photo delivery replaced the editor page");
                check(((EditText)read(editor,"text")).getText().toString().startsWith("保留这段文字"),"Photo delivery lost caption");
                int displayed=((SeekBar)read(editor,"angle")).getProgress()-180;
                float actual=((Number)read(read(editor,"placement"),"angle")).floatValue();
                check(Math.abs(displayed-actual)<0.01,"Rotation slider disagrees with delivered photo transform");
                ((Button)read(editor,"sendButton")).performClick();check(((Number)read(editor,"step")).intValue()==2,"Reselected photo skipped crop or preview");
            });
            PanelImage.Prepared imported=awaitFrame(instrumentation,editor);validate(imported);
            check(!Arrays.equals(first.packed,imported.packed),"Imported photo did not change final pixels");noImplicitTransfer(host);

            // Missing panel produces guidance, then choosing A0 changes the exact frame.
            ui(instrumentation,()->((Spinner)read(editor,"panel")).setSelection(0));
            PanelImage.Prepared missing=awaitFrame(instrumentation,editor);check(missing.panelType.isEmpty(),"Missing panel was silently assumed");
            ui(instrumentation,()->{
                ((Button)read(editor,"sendButton")).performClick();
                check(((TextView)read(editor,"error")).getText().toString().contains("A0"),"Missing panel did not show guidance");
                check(host.sends==0,"Missing panel attempted transmission");
                ((Spinner)read(editor,"panel")).setSelection(1);
            });
            PanelImage.Prepared selected=awaitFrame(instrumentation,editor);validate(selected);
            check("A0".equals(selected.panelType)&&"A0".equals(host.selectedPanel),"Panel selection did not propagate to prepared frame");
            byte[] beforeSend=selected.packed.clone();
            ui(instrumentation,()->{
                ((Button)read(editor,"sendButton")).performClick();
                check(host.sends==1&&host.sent==selected,"Sender did not receive the exact preview snapshot");
                check(Boolean.TRUE.equals(read(editor,"transferring")),"Transfer did not lock the editor");
                check(!((Button)read(editor,"sendButton")).isEnabled(),"Send remains enabled while transferring");
                check(!((EditText)read(editor,"text")).isEnabled(),"Text can mutate the in-flight snapshot");
                host.observer.status("正在检查授权与连接…");check(((Button)read(editor,"sendButton")).getText().toString().contains("连接中"),"Connection phase not reflected on the primary CTA");
                host.observer.status("已确认 440 / 476 包 · 92% · 保持贴合");
                check(((Button)read(editor,"sendButton")).getText().toString().contains("92%")&&((ProgressBar)read(editor,"transferProgress")).getProgress()==92,"Upload progress is not visible on the fixed CTA/dock");
            });
            checkLayout(instrumentation,editor);
            ui(instrumentation,()->{
                host.observer.status("已确认 476 / 476 包 · 100% · 保持贴合");
                check(Boolean.TRUE.equals(read(editor,"transferring"))&&!((Button)read(editor,"sendButton")).isEnabled()&&((Button)read(editor,"sendButton")).getText().toString().contains("刷屏中"),"100% upload was mistaken for finished screen refresh");
                host.observer.status("图片已传完，正在启动屏幕刷新");
                check(((Button)read(editor,"sendButton")).getText().toString().contains("刷屏中"),"Refresh phase missing from CTA");
                ((Button)read(editor,"stopButton")).performClick();
                check(host.stops==1&&Boolean.TRUE.equals(read(editor,"stopRequested"))&&Boolean.TRUE.equals(read(editor,"transferring"))&&!((Button)read(editor,"sendButton")).isEnabled(),"Stop request prematurely released transfer controls");
                check(((Button)read(editor,"sendButton")).getText().toString().contains("正在停止"),"Stop-pending phase is not visible");
            });
            checkLayout(instrumentation,editor);
            ui(instrumentation,()->{
                host.observer.finished(false,"模拟失败，无 NFC 写入");
                check(!Boolean.TRUE.equals(read(editor,"transferring"))&&((Button)read(editor,"sendButton")).isEnabled(),"Finished failure did not permit an explicit retry");
                check(((Button)read(editor,"sendButton")).getText().toString().contains("重试"),"Failure CTA does not describe retry");
                ((Button)read(editor,"sendButton")).performClick();check(host.sends==2&&host.sent==selected,"Retry did not reuse the frozen preview snapshot");
                host.observer.finished(true,"模拟完成，无 NFC 写入");
                check(!Boolean.TRUE.equals(read(editor,"transferring"))&&((Button)read(editor,"sendButton")).isEnabled()&&((Button)read(editor,"sendButton")).getText().toString().contains("再次")&&((TextView)read(editor,"error")).getText().toString().contains("完成"),"Success state did not offer a repeat send with completion status");
                check(read(editor,"dialog")==sameDialog.get()&&sameDialog.get().isShowing(),"Sending jumped out of the editing page");
            });
            checkLayout(instrumentation,editor);
            check(Arrays.equals(beforeSend,host.sent.packed),"Transfer changed the prepared payload");save(target,selected.preview,"photo-final");

            // Text changes invalidate the old frame immediately, then refresh final RGBY pixels.
            ui(instrumentation,()->{
                editor.open(EditorStudio.NOTE);((EditText)read(editor,"text")).setText("这是便签\nHello, e-paper!");
                ((Spinner)read(editor,"foreground")).setSelection(0);((Spinner)read(editor,"background")).setSelection(1);
                ((CheckBox)read(editor,"outlined")).setChecked(true);((Spinner)read(editor,"outlineColor")).setSelection(3);
            });
            PanelImage.Prepared note=awaitFrame(instrumentation,editor);validate(note);
            check(count(note.preview,ImageRaster.color(0))>100&&count(note.preview,ImageRaster.color(3))>20,"Note text or red outline missing");
            ui(instrumentation,()->{
                ((EditText)read(editor,"text")).setText("新的便签内容\nNew note snapshot");
                check(read(editor,"finalFrame")==null&&!((Button)read(editor,"sendButton")).isEnabled(),"Text edits left a stale sendable preview");
            });
            PanelImage.Prepared changedNote=awaitFrame(instrumentation,editor);validate(changedNote);
            check(!Arrays.equals(note.packed,changedNote.packed),"Note edits did not update final frame");save(target,changedNote.preview,"note-final");

            // Manual weather and horizontal calendar templates are offline, in the same editor.
            ui(instrumentation,()->{
                editor.open(EditorStudio.CALENDAR);
                ((Spinner)read(editor,"calendarTemplate")).setSelection(2);
                ((EditText)read(editor,"text")).setText("横版日历测试");
                EditText city=(EditText)read(editor,"city");city.requestFocus();city.setText("上海");
                ((EditText)read(editor,"weather")).setText("多云");((EditText)read(editor,"temperature")).setText("18°C");
            });
            PanelImage.Prepared landscape=awaitFrame(instrumentation,editor);validate(landscape);save(target,landscape.preview,"calendar-weather-landscape");
            // Simulate actual asynchronous commit entry points without making a network call.
            // A late snapshot must not replace fields manually edited after its request began.
            AtomicReference<Dialog> weatherOwner=new AtomicReference<>();long[] weatherRequest=new long[1];
            ui(instrumentation,()->{
                Dialog owner=(Dialog)read(editor,"dialog");weatherOwner.set(owner);
                long stale=((Number)invoke(editor,"beginWeatherRequest",new Class<?>[0])).longValue();
                check(Boolean.TRUE.equals(read(editor,"weatherLoading"))&&!((Button)read(editor,"sendButton")).isEnabled(),"Pending weather did not guard send");
                ((EditText)read(editor,"city")).setText("杭州");((EditText)read(editor,"weather")).setText("晴");((EditText)read(editor,"temperature")).setText("23°C");
                check(!Boolean.TRUE.equals(read(editor,"weatherLoading")),"Manual editing left weather requests blocked");
                invoke(editor,"applyWeatherSnapshot",new Class<?>[]{Dialog.class,long.class,WeatherClient.Snapshot.class},owner,stale,
                    new WeatherClient.Snapshot("旧城市","过期结果","-99°C","旧时间","测试数据"));
                check("杭州".equals(((EditText)read(editor,"city")).getText().toString())&&"23°C".equals(((EditText)read(editor,"temperature")).getText().toString()),"Late weather overwrote manual input");
                check("".equals(read(editor,"weatherUpdated"))&&"".equals(read(editor,"weatherSource")),"Manual input retained stale weather attribution");
                long fresh=((Number)invoke(editor,"beginWeatherRequest",new Class<?>[0])).longValue();weatherRequest[0]=fresh;
                invoke(editor,"applyWeatherSnapshot",new Class<?>[]{Dialog.class,long.class,WeatherClient.Snapshot.class},owner,fresh,
                    new WeatherClient.Snapshot("杭州","多云","22°C","测试快照，未联网","测试数据"));
                check("22°C".equals(((EditText)read(editor,"temperature")).getText().toString())&&"测试数据".equals(read(editor,"weatherSource")),"Fresh weather snapshot did not commit atomically");
            });
            awaitFrame(instrumentation,editor);
            ui(instrumentation,()->{
                check(((TextView)read(editor,"error")).getText().toString().contains("横版"),"Horizontal calendar did not label viewing orientation");
                check("杭州".equals(((EditText)read(editor,"city")).getText().toString()),"Committed weather city lost");
                ((Spinner)read(editor,"calendarTemplate")).setSelection(3);
            });
            PanelImage.Prepared weekly=awaitFrame(instrumentation,editor);validate(weekly);
            check(!Arrays.equals(landscape.packed,weekly.packed),"Calendar template change did not refresh pixels");save(target,weekly.preview,"calendar-week-landscape");
            check(host.sends==2&&host.applies==0&&host.automations==0,"Editing invoked an implicit transfer or the old apply-to-main-page flow");

            final AtomicReference<EditText> retiredCity=new AtomicReference<>();
            ui(instrumentation,()->{
                retiredCity.set((EditText)read(editor,"city"));
                editor.open(EditorStudio.CLOCK);
                invoke(editor,"applyWeatherSnapshot",new Class<?>[]{Dialog.class,long.class,WeatherClient.Snapshot.class},weatherOwner.get(),weatherRequest[0],
                    new WeatherClient.Snapshot("跨页旧结果","雨","-99°C","旧时间","测试数据"));
                check(read(editor,"city")==null&&!"跨页旧结果".equals(retiredCity.get().getText().toString()),"Old calendar callback modified a different editor page");
            });
            PanelImage.Prepared clock=awaitFrame(instrumentation,editor);validate(clock);
            ui(instrumentation,()->{
                Button automatic=(Button)read(editor,"clockAutoButton");check(automatic.isEnabled(),"Ready clock cannot open automatic refresh settings");
                ((Button)read(editor,"sendButton")).performClick();
                @SuppressWarnings("unchecked") List<Dialog> children=(List<Dialog>)read(editor,"children");
                check(host.sends==3&&host.sent==clock&&!automatic.isEnabled(),"Clock transfer did not lock automatic refresh");
                int childCount=children.size();automatic.performClick();
                check(host.automations==0&&children.size()==childCount,"Clock timer bypassed in-flight transfer guard");
                host.finishOnStop=true;((Button)read(editor,"stopButton")).performClick();host.finishOnStop=false;
                check(!Boolean.TRUE.equals(read(editor,"transferring"))&&!Boolean.TRUE.equals(read(editor,"stopRequested"))&&((Button)read(editor,"sendButton")).isEnabled()&&!((Button)read(editor,"sendButton")).getText().toString().contains("正在停止"),"Synchronous stop completion was overwritten by a stale stopping state");
                check(automatic.isEnabled(),"Clock automatic refresh did not recover after transfer completion");
            });

            ui(instrumentation,()->{
                Dialog editing=(Dialog)read(editor,"dialog");editor.onHostStop();
                check(editor.isOpen()&&read(editor,"dialog")==editing,"Temporary host stop discarded the current edit page");
                editor.onHostResume();
                check(editor.isOpen()&&read(editor,"dialog")==editing&&editing.isShowing(),"Resume did not restore the exact edit page");
                editor.dismiss();check(!editor.isOpen(),"Explicit dismissal did not close the editor");
            });
            geometryVariants(instrumentation,screen,editor);
        } finally {
            final Activity closing=activity;EditorStudio editor=holder.get();
            try{ui(instrumentation,()->{if(editor!=null)editor.destroy();if(closing!=null)closing.finish();});instrumentation.waitForIdleSync();}
            finally{restore(preferences,saved);restore(refreshPreferences,savedRefresh);}
        }
    }

    private static final class FakeHost implements EditorStudio.Host {
        Bitmap photo;String selectedPanel="A1";int picks,sends,applies,automations,stops,connections;boolean finishOnStop;
        PanelImage.Prepared sent;EditorStudio.TransferObserver observer;
        public void send(PanelImage.Prepared frame,String name,EditorStudio.TransferObserver observer){sends++;sent=frame;this.observer=observer;}
        public void stopSending(){stops++;if(finishOnStop&&observer!=null)observer.finished(false,"授权等待已取消，无 NFC 写入");}public void connect(){connections++;}
        public String connectionState(){return "原生测试：发送由 FakeHost 接收，不操作 NFC";}
        public String panelType(){return selectedPanel;}public void selectPanel(String type){selectedPanel=type;}
        public Bitmap currentPhoto(){return photo;}public void selectPhoto(){picks++;}
        public void apply(Bitmap bitmap,String name){applies++;}public void automate(int mode,long intervalMillis){automations++;}
        public void manageRotation(){}
    }
    private static void checkLayout(Instrumentation instrumentation,EditorStudio editor) throws Exception {
        instrumentation.waitForIdleSync();SystemClock.sleep(80);
        ui(instrumentation,()->{
            Dialog dialog=(Dialog)read(editor,"dialog");check(dialog!=null&&dialog.isShowing(),"Editor dialog is not laid out");
            View decor=dialog.getWindow().getDecorView(),dock=(View)read(editor,"statusDock"),canvas=(View)read(editor,"canvasArea"),workspace=(View)read(editor,"workspace");
            Rect viewport=new Rect();check(decor.getGlobalVisibleRect(viewport),"Dialog has no visible viewport");
            visibleWithin(dock,viewport,"Fixed status dock");
            for(String name:new String[]{"statusDock","error","transferProgress","sendButton","backButton","stopButton"}){
                View view=(View)read(editor,name);check(!insideScroll(view),name+" must not scroll out of the viewport");
                if(view.getVisibility()==View.VISIBLE){visibleWithin(view,viewport,name);if(view instanceof Button)fitsText((TextView)view,name);}
            }
            check(descendant((View)read(editor,"sendButton"),dock)&&descendant((View)read(editor,"error"),dock),"Status and primary CTA do not share a fixed dock");
            check(canvas.getWidth()>0&&canvas.getHeight()>=dp(dialog.getContext(),48),"Editor canvas collapsed at this font/viewport size");
            View tools=(View)read(editor,"toolsPanel");
            check(canvas.getParent()==workspace&&tools.getParent()==workspace,"Preview and controls must be workspace siblings");
            check(!insideScroll(canvas)&&!insideScroll(workspace),"Preview canvas must remain outside the controls scroller");
            if(tools.getVisibility()==View.VISIBLE){
                Rect toolRect=new Rect(),canvasRect=new Rect(),workRect=new Rect(),dockRect=new Rect();
                check(tools.getGlobalVisibleRect(toolRect)&&canvas.getGlobalVisibleRect(canvasRect)&&workspace.getGlobalVisibleRect(workRect)&&dock.getGlobalVisibleRect(dockRect),"Split workspace did not lay out");
                check(!Rect.intersects(toolRect,canvasRect)&&canvasRect.bottom<=toolRect.top,"Tool panel overlaps and hides the preview");
                check(workRect.contains(canvasRect)&&workRect.contains(toolRect)&&toolRect.bottom<=dockRect.top,"Split workspace escaped its bounds or covered the fixed dock");
                double share=canvas.getHeight()/(double)Math.max(1,canvas.getHeight()+tools.getHeight());
                check(share>=0.45&&share<=0.70,"Open controls did not leave a meaningful preview share: "+share);
                View controlScroller=(View)((View)read(editor,"editorFields")).getParent();
                check(controlScroller instanceof ScrollView&&controlScroller.getHeight()>=dp(dialog.getContext(),40),"Tool sheet has no meaningful scrollable control viewport");
            }else expandedCanvas(canvas,workspace);
            ImageView preview=(ImageView)read(editor,"preview");
            if(preview.getVisibility()==View.VISIBLE&&preview.getDrawable()!=null)wholePreview(preview);
        });
    }
    /** Reproduce changing options while seeing the entire final image, not an overlay strip. */
    private static void checkPreviewTools(Instrumentation instrumentation,EditorStudio editor) throws Exception {
        ui(instrumentation,()->{
            check(((Number)read(editor,"step")).intValue()==2,"Tool-preview regression requires final preview stage");
            invoke(editor,"showTool",new Class<?>[]{String.class},"");
        });
        checkLayout(instrumentation,editor);
        for(String tool:new String[]{"style","frame","advanced"}){
            AtomicReference<PanelImage.Prepared> before=new AtomicReference<>();
            ui(instrumentation,()->{before.set((PanelImage.Prepared)read(editor,"finalFrame"));invoke(editor,"showTool",new Class<?>[]{String.class},tool);check(read(editor,"finalFrame")==before.get(),"Opening controls replaced the prepared frame before any image edit");});
            checkLayout(instrumentation,editor);
            PanelImage.Prepared revealed=awaitFrame(instrumentation,editor);
            check(revealed==before.get()&&Arrays.equals(before.get().packed,revealed.packed),"Opening controls replaced the prepared frame without an image edit");
            ui(instrumentation,()->{
                check(((View)read(editor,"toolsPanel")).getVisibility()==View.VISIBLE,"Requested options panel is hidden");
                check(((ImageView)read(editor,"preview")).getVisibility()==View.VISIBLE,"Choosing options hid the final preview");
                String active=tool.equals("style")?"styleFields":tool.equals("frame")?"frameFields":"advancedFields";
                check(((View)read(editor,active)).getVisibility()==View.VISIBLE,"Wrong options group is visible");
                if(tool.equals("style")){Spinner style=(Spinner)read(editor,"style");style.setSelection((style.getSelectedItemPosition()+1)%3);}
                else if(tool.equals("frame")){CheckBox frame=(CheckBox)read(editor,"frame");frame.setChecked(!frame.isChecked());}
                else {Spinner tone=(Spinner)read(editor,"tone");tone.setSelection((tone.getSelectedItemPosition()+1)%3);}
            });
            validate(awaitFrame(instrumentation,editor));checkLayout(instrumentation,editor);
        }
        ui(instrumentation,()->invoke(editor,"showTool",new Class<?>[]{String.class},""));checkLayout(instrumentation,editor);
        ui(instrumentation,()->{
            check(((View)read(editor,"toolsPanel")).getVisibility()!=View.VISIBLE,"Closing options did not hide the controls panel");
            expandedCanvas((View)read(editor,"canvasArea"),(View)read(editor,"workspace"));
            wholePreview((ImageView)read(editor,"preview"));
        });
    }
    private static void expandedCanvas(View canvas,View workspace){
        int margins=0;if(canvas.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)canvas.getLayoutParams();margins=params.topMargin+params.bottomMargin;}
        int available=workspace.getHeight()-workspace.getPaddingTop()-workspace.getPaddingBottom()-margins;
        check(canvas.getHeight()>=available-1,"Closing controls did not restore the complete current workspace to the preview");
    }
    private static void wholePreview(ImageView preview){
        check(preview.getScaleType()==ImageView.ScaleType.FIT_CENTER,"Preview must fit the complete image instead of cropping it");
        int width=preview.getWidth()-preview.getPaddingLeft()-preview.getPaddingRight(),height=preview.getHeight()-preview.getPaddingTop()-preview.getPaddingBottom();
        check(width>0&&height>0,"Preview content area is empty");
        RectF image=new RectF(0,0,preview.getDrawable().getIntrinsicWidth(),preview.getDrawable().getIntrinsicHeight());preview.getImageMatrix().mapRect(image);
        check(image.width()>0&&image.height()>0&&image.left>=-1&&image.top>=-1&&image.right<=width+1&&image.bottom<=height+1,"Final image is clipped inside the preview canvas");
    }
    /** Change only this target process's Resources, never the device's accessibility settings. */
    @SuppressWarnings("deprecation") private static void geometryVariants(Instrumentation instrumentation,Activity activity,EditorStudio editor) throws Exception {
        Configuration original=new Configuration(activity.getResources().getConfiguration());
        try{
            for(float scale:new float[]{original.fontScale,2f})for(int[] size:new int[][]{{360,640},{320,560}}){
                ui(instrumentation,()->{
                    editor.dismiss();Configuration test=new Configuration(original);test.fontScale=scale;
                    activity.getResources().updateConfiguration(test,activity.getResources().getDisplayMetrics());
                    editor.open(EditorStudio.PHOTO);Dialog dialog=(Dialog)read(editor,"dialog");
                    int width=Math.min(dp(activity,size[0]),activity.getResources().getDisplayMetrics().widthPixels);
                    int height=Math.min(dp(activity,size[1]),Math.max(dp(activity,400),activity.getResources().getDisplayMetrics().heightPixels-dp(activity,48)));
                    dialog.getWindow().setLayout(width,height);
                    check(((View)read(editor,"toolsPanel")).getVisibility()!=View.VISIBLE,"Parameters were expanded by default");
                });
                checkLayout(instrumentation,editor);
                ui(instrumentation,()->invoke(editor,"showTool",new Class<?>[]{String.class},"geometry"));checkLayout(instrumentation,editor);
                ui(instrumentation,()->{invoke(editor,"showTool",new Class<?>[]{String.class},"");((Button)read(editor,"sendButton")).performClick();});
                awaitFrame(instrumentation,editor);checkLayout(instrumentation,editor);checkPreviewTools(instrumentation,editor);
                ui(instrumentation,()->{((Button)read(editor,"sendButton")).performClick();((FakeHost)read(editor,"host")).observer.status("已确认 440 / 476 包 · 92% · 保持贴合");});
                checkLayout(instrumentation,editor);
                ui(instrumentation,()->((FakeHost)read(editor,"host")).observer.finished(false,"几何测试结束，无 NFC 写入"));
            }
        }finally{ui(instrumentation,()->{editor.dismiss();activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics());});}
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static boolean insideScroll(View view){for(android.view.ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView||parent instanceof HorizontalScrollView)return true;return false;}
    private static boolean descendant(View child,View ancestor){for(android.view.ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())if(parent==ancestor)return true;return false;}
    private static void visibleWithin(View view,Rect viewport,String name){Rect visible=new Rect();check(view.getWidth()>0&&view.getHeight()>0&&view.getGlobalVisibleRect(visible),name+" has no visible geometry");check(visible.width()==view.getWidth()&&visible.height()==view.getHeight()&&viewport.contains(visible),name+" is clipped or outside the window");}
    private static void fitsText(TextView view,String name){if(view.getLayout()!=null)check(view.getLayout().getHeight()<=view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom()+1,name+" clips its text at this font scale");}
    private static PanelImage.Prepared awaitFrame(Instrumentation instrumentation,EditorStudio editor) throws Exception {
        // Spinner notifications may arrive during the next layout. Let those notifications
        // and the editor's 160 ms debounce invalidate any old frame before accepting one.
        instrumentation.waitForIdleSync();SystemClock.sleep(230);
        long deadline=SystemClock.uptimeMillis()+12000;
        while(SystemClock.uptimeMillis()<deadline){
            AtomicReference<PanelImage.Prepared> ready=new AtomicReference<>();
            ui(instrumentation,()->{if(!Boolean.TRUE.equals(read(editor,"preparing")))ready.set((PanelImage.Prepared)read(editor,"finalFrame"));});
            if(ready.get()!=null)return ready.get();SystemClock.sleep(40);
        }
        AtomicReference<String> error=new AtomicReference<>();ui(instrumentation,()->error.set(((TextView)read(editor,"error")).getText().toString()));
        throw new AssertionError("Final preview timed out: "+error.get());
    }
    private static void validate(PanelImage.Prepared frame){
        check(frame.preview.getWidth()==552&&frame.preview.getHeight()==768&&frame.packed.length==105984,"Final frame shape");
        PanelType panel=frame.panelType.isEmpty()?PanelType.A1:PanelType.parse(frame.panelType);
        int[] decoded=panel.preview(frame.packed),actual=new int[552*768];frame.preview.getPixels(actual,0,552,0,0,552,768);
        check(Arrays.equals(decoded,actual),"Final preview differs from encoded bytes");
        for(int pixel:actual){boolean found=false;for(int i=0;i<4;i++)if(pixel==ImageRaster.color(i)){found=true;break;}check(found,"Intermediate color escaped final preview");}
    }
    private static Bitmap sample(boolean reversed){
        Bitmap bitmap=Bitmap.createBitmap(120,180,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);Paint p=new Paint();
        for(int i=0;i<4;i++){p.setColor(ImageRaster.color(reversed?3-i:i));c.drawRect(i*30,0,(i+1)*30,180,p);}return bitmap;
    }
    private static int count(Bitmap image,int color){int[] values=new int[552*768];image.getPixels(values,0,552,0,0,552,768);int n=0;for(int pixel:values)if(pixel==color)n++;return n;}
    private static void noImplicitTransfer(FakeHost host){check(host.sends==0&&host.applies==0&&host.automations==0,"Editing or selecting a photo triggered a transfer/apply callback");}
    private static Object read(Object object,String name) throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object... arguments) throws Exception {Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,arguments);}
    private static void ui(Instrumentation instrumentation,UiAction action) throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable problem){failure.set(problem);}});
        Throwable problem=failure.get();if(problem instanceof Exception)throw (Exception)problem;if(problem instanceof Error)throw (Error)problem;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void save(Context target,Bitmap bitmap,String name) throws Exception {
        File base=target.getExternalFilesDir(null);check(base!=null,"External files unavailable for native fixtures");
        File folder=new File(base,"studio-ui-checks");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("Cannot save native UI fixture");
        try(FileOutputStream output=new FileOutputStream(new File(folder,name+".png"))){check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output),"Could not save native UI fixture");}
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences preferences,Map<String,?> saved){
        SharedPreferences.Editor edit=preferences.edit().clear();
        for(Map.Entry<String,?> entry:saved.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);
            else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);
            else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);
        }
        edit.commit();
    }
}

package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Device-only photo workflow with a fake Host. Preferences are restored; no photo cache or NFC. */
public final class PhotoWorkspaceNativeChecks {
    private interface UiAction{void run()throws Exception;}
    public static void run(Instrumentation instrumentation,Activity activity)throws Exception{
        check(Looper.myLooper()!=Looper.getMainLooper(),"Run on instrumentation thread");
        SharedPreferences preferences=activity.getSharedPreferences("photo_workspace",0);Map<String,?> savedPreferences=new HashMap<>(preferences.getAll());
        check(preferences.edit().remove("completed_frame").putString("active_kind","photo").commit(),"Cannot isolate send receipt tests");
        final FakeHost host=new FakeHost();final AtomicReference<PhotoWorkspace> holder=new AtomicReference<>();final AtomicReference<Dialog> window=new AtomicReference<>();
        try{
            ui(instrumentation,()->{
                PhotoWorkspace workspace=new PhotoWorkspace(activity,host);holder.set(workspace);
                // Inject only the local decode result. Avoid replacing any user cache/draft.
                write(workspace,"original",PhotoWorkspace.demoBitmap());write(workspace,"edit",new PhotoEdit());
                invoke(workspace,"prepare");Dialog harness=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar);window.set(harness);harness.setContentView(workspace.view);harness.show();harness.getWindow().setLayout(-1,-1);
            });
            PhotoWorkspace workspace=holder.get();StudioImageCodec.Result initial=awaitFrame(instrumentation,workspace);verify(initial.prepared);
            layout(instrumentation,workspace,window.get());
            ui(instrumentation,()->{
                check(host.sends==0&&read(workspace,"editorDialog")==null,"Restoring/decoding opened editor or sent implicitly");
                check(workspace.onResult(PhotoWorkspace.PICK,Activity.RESULT_CANCELED,null),"Picker cancellation not handled");
                check(read(workspace,"frame")==initial,"Picker cancellation discarded final preview");
                check(!workspace.onResult(999,Activity.RESULT_CANCELED,null),"Unrelated activity result was consumed");
                tap(workspace.view,"日历与天气",false);tap(workspace.view,"便签",false);
                check(host.calendars==1&&host.notes==1&&host.sends==0,"Card entries do not use the Host");
                tap(workspace.view,"对比",true);check(Boolean.TRUE.equals(read(workspace,"originalShown")),"Compare did not show original");
                Button upload=(Button)read(workspace,"upload");upload.performClick();
                check(host.sends==0&&!Boolean.TRUE.equals(read(workspace,"originalShown")),"Original comparison sent an unseen final frame");
                check(read(workspace,"frame")==initial,"Comparison changed encoded snapshot");
                tap(workspace.view,"构图",true);Dialog editor=(Dialog)read(workspace,"editorDialog");check(editor!=null&&editor.isShowing(),"Crop tool did not open native editor");
                check(!upload.isEnabled(),"Editor left an old preview sendable");tap(editor.getWindow().getDecorView(),"取消编辑",true);
                check(read(workspace,"editorDialog")==null&&!Boolean.TRUE.equals(read(workspace,"editing")),"Cancel did not restore photo workspace");
                check(read(workspace,"frame")==initial,"Canceled edit replaced prepared frame");
                workspace.setBusy(true);check(!upload.isEnabled()&&!((Button)read(workspace,"pick")).isEnabled(),"External session did not lock photo operations");
                workspace.setBusy(false);check(upload.isEnabled(),"External session lock did not recover");
            });
            editorTransactions(instrumentation,activity);
            CardEditorNativeChecks.run(instrumentation,activity);
            DailyContentNativeChecks.run(instrumentation,activity);
            byte[] bytes=initial.prepared.packed.clone();
            ui(instrumentation,()->{
                Button upload=(Button)read(workspace,"upload");upload.performClick();check(host.sends==1&&host.sent==initial.prepared,"Host did not receive exact prepared snapshot");
                check(Boolean.TRUE.equals(read(workspace,"transferring"))&&!upload.isEnabled(),"Send did not lock until finished");
                check(!((Button)read(workspace,"pick")).isEnabled(),"In-flight photo can be replaced");
                workspace.setBusy(false);check(!upload.isEnabled(),"Global busy=false prematurely completed own transfer");
                host.observer.status("已确认 440 / 476 包 · 92% · 保持贴合");check(upload.getText().toString().contains("92%"),"Pinned transfer CTA lacks current packet progress");
            });
            layout(instrumentation,workspace,window.get());
            ui(instrumentation,()->{
                Button upload=(Button)read(workspace,"upload");
                host.observer.status("已确认 476 / 476 包 · 100%");check(!upload.isEnabled()&&upload.getText().toString().contains("刷屏中"),"Upload100% mistaken for finished refresh");
            });
            layout(instrumentation,workspace,window.get());
            ui(instrumentation,()->{
                Button upload=(Button)read(workspace,"upload");
                invoke(workspace,"openMore");selectMenu((AlertDialog)read(workspace,"moreDialog"),"停止本次推送");check(host.stops==1&&!upload.isEnabled(),"Stop menu action unlocked pending communication");
                host.observer.status("屏幕报告忙，继续供能");check(upload.getText().toString().contains("正在停止"),"Late progress erased stop-pending state");
                host.observer.finished(false,"模拟停止，无 NFC 写入");check(upload.isEnabled()&&upload.getText().toString().contains("重试"),"Failure did not allow explicit retry");
                EditorStudio.TransferObserver previous=host.observer;upload.performClick();check(host.sends==2&&host.sent==initial.prepared,"Explicit retry did not use same snapshot");
                previous.finished(true,"旧回调");check(!upload.isEnabled(),"Previous session completion unlocked a new session");
                host.observer.finished(true,"模拟完成，无 NFC 写入");check(!upload.isEnabled()&&upload.getText().toString().contains("完成"),"Successful content must remain disabled");
                upload.performClick();invoke(workspace,"send");check(host.sends==2,"Completed primary/direct send accidentally restarted NFC");
                workspace.setActive(false);check(!upload.isEnabled(),"Hidden workspace remains sendable");workspace.setActive(true);check(!upload.isEnabled()&&host.sends==2,"Resume cleared completion or implicitly sent");
                check(Arrays.equals(bytes,initial.prepared.packed),"UI operations mutated encoded snapshot");
                workspace.panelTypeChanged();
            });
            StudioImageCodec.Result rebuilt=awaitFrame(instrumentation,workspace);
            check(Arrays.equals(bytes,rebuilt.prepared.packed),"Unchanged rebuild altered physical frame");
            ui(instrumentation,()->{
                Button upload=(Button)read(workspace,"upload");check(!upload.isEnabled(),"Unchanged rebuild cleared completion receipt");invoke(workspace,"send");check(host.sends==2,"Unchanged rebuild allowed accidental resend");
                invoke(workspace,"openMore");selectMenu((AlertDialog)read(workspace,"moreDialog"),"再次推送当前内容");AlertDialog confirmation=(AlertDialog)read(workspace,"moreDialog");check(confirmation.isShowing()&&"确认重发".contentEquals(confirmation.getButton(AlertDialog.BUTTON_POSITIVE).getText()),"Explicit resend confirmation missing");confirmation.dismiss();check(host.sends==2,"Canceling resend confirmation sent content");
                tap(workspace.view,"对比",true);check(Boolean.TRUE.equals(read(workspace,"originalShown"))&&host.sends==2,"Completed comparison did not show original without sending");
                invoke(workspace,"openMore");selectMenu((AlertDialog)read(workspace,"moreDialog"),"再次推送当前内容");((AlertDialog)read(workspace,"moreDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();check(host.sends==3&&host.sent==rebuilt.prepared,"Confirming resend from original comparison did not actually call Host.send");
                ImageView shown=(ImageView)read(workspace,"preview");check(!Boolean.TRUE.equals(read(workspace,"originalShown"))&&shown.getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)shown.getDrawable()).getBitmap()==rebuilt.preview,"Confirmed resend did not restore the exact final four-color home preview");
                host.observer.finished(true,"模拟重发完成，无 NFC 写入");check(!upload.isEnabled(),"Confirmed repeat completion did not lock again");
                PhotoEdit changed=((PhotoEdit)read(workspace,"edit")).copy();changed.mirror=!changed.mirror;write(workspace,"edit",changed);invoke(workspace,"prepare");
            });
            StudioImageCodec.Result changed=awaitFrame(instrumentation,workspace);check(!Arrays.equals(bytes,changed.prepared.packed),"Real edit did not change generated content");
            ui(instrumentation,()->{
                Button upload=(Button)read(workspace,"upload");check(upload.isEnabled(),"New content did not unlock sending");host.finishOnStop=true;upload.performClick();check(host.sends==4&&host.sent==changed.prepared,"New content did not send its own snapshot");workspace.onHostStop();
                check(upload.isEnabled()&&upload.getText().toString().contains("重试"),"Synchronous foreground-stop completion left stale busy text");host.finishOnStop=false;
            });
            cardWorkflow(instrumentation,workspace,host);
            dailyWorkflow(instrumentation,activity,workspace,host);
            layout(instrumentation,workspace,window.get());
            // Inactive/closed callbacks must be harmless and must never restart a send.
            ui(instrumentation,()->{EditorStudio.TransferObserver observer=host.observer;workspace.destroy();observer.status("已确认 440 / 476 包 · 92%");observer.finished(true,"late");check(!workspace.isActive(),"Closed workspace revived from callback");});
        }finally{try{ui(instrumentation,()->{PhotoWorkspace workspace=holder.get();if(workspace!=null)workspace.destroy();Dialog harness=window.get();if(harness!=null)harness.dismiss();});}finally{restore(preferences,savedPreferences);}}
    }
    private static void cardWorkflow(Instrumentation instrumentation,PhotoWorkspace workspace,FakeHost host)throws Exception{
        AtomicReference<StudioImageCodec.Result> previous=new AtomicReference<>();AtomicReference<CardEditor.Session> editor=new AtomicReference<>();
        ui(instrumentation,()->{
            previous.set((StudioImageCodec.Result)read(workspace,"frame"));workspace.openCardEditor(CardScene.NOTE);CardEditor.WindowDialog dialog=(CardEditor.WindowDialog)read(workspace,"editorDialog");check(dialog!=null&&dialog.isShowing(),"Note entry did not open typed card editor");dialog.session.textInput.setText("取消这段便签修改");tap(dialog.getWindow().getDecorView(),"取消卡片编辑",true);
            check(read(workspace,"frame")==previous.get()&&read(workspace,"cardScene")==null&&host.sends==4,"Canceled card replaced photo or started NFC");
            workspace.openCardEditor(CardScene.NOTE);editor.set(((CardEditor.WindowDialog)read(workspace,"editorDialog")).session);editor.get().textInput.setText("首页便签快照\nNote snapshot");
        });
        StudioImageCodec.Result note=CardEditorNativeChecks.awaitReady(instrumentation,editor.get());
        ui(instrumentation,()->{
            editor.get().doneButton.performClick();CardScene card=(CardScene)read(workspace,"cardScene");check(card!=null&&card.kind==CardScene.NOTE&&card.text.equals("首页便签快照\nNote snapshot"),"Done did not commit typed note to home");check(read(workspace,"frame")==note&&host.sends==4,"Note Done must only update home preview");
            ((Button)read(workspace,"upload")).performClick();check(host.sends==5&&host.sent==note.prepared&&host.name.equals("便签"),"Note did not use the single home sender snapshot");host.observer.finished(false,"模拟便签结束，无 NFC 写入");
            workspace.openCardEditor(CardScene.CALENDAR);CardEditor.Session session=((CardEditor.WindowDialog)read(workspace,"editorDialog")).session;editor.set(session);session.scene.year=2026;session.scene.month=10;session.scene.day=24;session.scene.template=2;session.mode=2;session.showTools();session.cityInput.setText("杭州");session.weatherInput.setText("晴");session.tempInput.setText("25°C");session.queueRender();
        });
        StudioImageCodec.Result calendar=CardEditorNativeChecks.awaitReady(instrumentation,editor.get());
        ui(instrumentation,()->{
            editor.get().doneButton.performClick();CardScene card=(CardScene)read(workspace,"cardScene");check(card!=null&&card.kind==CardScene.CALENDAR&&card.isLandscape()&&card.city.equals("杭州"),"Calendar Done lost typed/manual content");check(read(workspace,"frame")==calendar&&host.sends==5,"Calendar Done sent instead of returning to home preview");
            ((Button)read(workspace,"upload")).performClick();check(host.sends==6&&host.sent==calendar.prepared&&host.name.equals("日历与天气"),"Calendar did not use home sender");host.observer.finished(true,"模拟日历完成，无 NFC 写入");check(!((Button)read(workspace,"upload")).isEnabled(),"Completed calendar not protected");
            workspace.openCardEditor(CardScene.NOTE);CardEditor.WindowDialog canceled=(CardEditor.WindowDialog)read(workspace,"editorDialog");tap(canceled.getWindow().getDecorView(),"取消卡片编辑",true);check(read(workspace,"frame")==calendar&&!((Button)read(workspace,"upload")).isEnabled()&&host.sends==6,"Cancel changed last calendar frame/completion receipt");
            workspace.openCardEditor(CardScene.CALENDAR);editor.set(((CardEditor.WindowDialog)read(workspace,"editorDialog")).session);
        });
        StudioImageCodec.Result same=CardEditorNativeChecks.awaitReady(instrumentation,editor.get());check(Arrays.equals(same.prepared.packed,calendar.prepared.packed),"Unchanged card rebuilt different payload");
        ui(instrumentation,()->{editor.get().doneButton.performClick();check(read(workspace,"frame")==same&&!((Button)read(workspace,"upload")).isEnabled()&&host.sends==6,"Done on unchanged card cleared completion");invoke(workspace,"send");check(host.sends==6,"Completed card direct send repeated NFC");});
    }
    /** New content modes share home snapshots and completion protection; no real network or NFC. */
    private static void dailyWorkflow(Instrumentation instrumentation,Activity activity,PhotoWorkspace workspace,FakeHost host)throws Exception{
        java.util.Map<java.io.File,byte[]> backups=new java.util.HashMap<>();
        for(int kind=DailyContent.HISTORY;kind<=DailyContent.BIRTHDAY;kind++)for(String suffix:new String[]{".cache",".cache.tmp"}){
            java.io.File file=new java.io.File(activity.getFilesDir(),"daily-card-"+kind+suffix);backups.put(file,file.isFile()?java.nio.file.Files.readAllBytes(file.toPath()):null);java.nio.file.Files.deleteIfExists(file.toPath());
        }
        try{
            for(int kind=DailyContent.HISTORY;kind<=DailyContent.BIRTHDAY;kind++){
                final int selectedKind=kind,before=host.sends;AtomicReference<DailyContentEditor.Session> editor=new AtomicReference<>();
                ui(instrumentation,()->{
                    StudioImageCodec.Result previous=(StudioImageCodec.Result)read(workspace,"frame");workspace.openDailyEditor(selectedKind);Dialog canceled=(Dialog)read(workspace,"editorDialog");check(canceled!=null&&canceled.isShowing(),"New home content entry did not open editor");canceled.dismiss();check(read(workspace,"frame")==previous&&host.sends==before,"Cancel changed home preview or sent content");
                    workspace.openDailyEditor(selectedKind);DailyContentEditor.Session session=((DailyContentEditor.WindowDialog)read(workspace,"editorDialog")).session;editor.set(session);session.scene.entries.clear();DailyContent.Entry entry=new DailyContent.Entry();entry.id="offline-"+selectedKind;entry.title="离线测试内容 "+selectedKind;entry.body="仅用于预览和发送快照检查，不请求网络。";entry.byline="离线测试";entry.work="本地测试作品";entry.source="测试夹具";session.scene.entries.add(entry);session.scene.selected=0;session.scene.source="测试夹具";session.queueRender();
                });
                StudioImageCodec.Result prepared=awaitDaily(instrumentation,editor.get());verify(prepared.prepared);
                ui(instrumentation,()->{
                    editor.get().done.performClick();DailyContent.Scene accepted=(DailyContent.Scene)read(workspace,"dailyScene");check(accepted!=null&&accepted.kind==selectedKind&&read(workspace,"cardScene")==null,"Done did not commit a distinct daily content scene");check(read(workspace,"frame")==prepared&&host.sends==before,"Content Done must only return the exact frame to home");
                    Button upload=(Button)read(workspace,"upload");upload.performClick();check(host.sends==before+1&&host.sent==prepared.prepared,"New content did not use the common home sender");host.observer.finished(true,"离线模拟完成，无NFC写入");upload.performClick();invoke(workspace,"send");check(host.sends==before+1&&!upload.isEnabled(),"Completed content restarted NFC");workspace.panelTypeChanged();
                });
                StudioImageCodec.Result rebuilt=awaitFrame(instrumentation,workspace);check(Arrays.equals(prepared.prepared.packed,rebuilt.prepared.packed),"Unchanged daily scene rebuilt a different payload");
                ui(instrumentation,()->{check(!((Button)read(workspace,"upload")).isEnabled()&&host.sends==before+1,"Rebuilding daily content cleared completion");});
            }
        }finally{
            ((java.util.concurrent.ExecutorService)read(workspace,"images")).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
            for(java.util.Map.Entry<java.io.File,byte[]> backup:backups.entrySet())if(backup.getValue()==null)java.nio.file.Files.deleteIfExists(backup.getKey().toPath());else java.nio.file.Files.write(backup.getKey().toPath(),backup.getValue());
        }
    }
    private static StudioImageCodec.Result awaitDaily(Instrumentation instrumentation,DailyContentEditor.Session session)throws Exception{
        long deadline=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<deadline){AtomicReference<StudioImageCodec.Result> ready=new AtomicReference<>();ui(instrumentation,()->{if(session.done.isEnabled()&&!session.preparing)ready.set(session.prepared);});if(ready.get()!=null)return ready.get();SystemClock.sleep(40);}throw new AssertionError("Daily content prepare timed out");
    }
    private static void editorTransactions(Instrumentation instrumentation,Activity activity)throws Exception{
        PhotoEdit original=new PhotoEdit();PhotoEdit.TextLayer caption=new PhotoEdit.TextLayer();caption.text="保留文字\nLocal scene";original.texts.add(caption);byte[] pristine=original.encode();
        Bitmap source=PhotoWorkspace.demoBitmap();int[] pixels=pixels(source);AtomicReference<PhotoEdit> applied=new AtomicReference<>();AtomicReference<Dialog> opened=new AtomicReference<>();
        try{
            ui(instrumentation,()->{Dialog dialog=PhotoEditor.open(activity,source,original,applied::set);opened.set(dialog);View root=dialog.getWindow().getDecorView();tap(root,"旋转 90°",false);tap(root,"翻转",false);tap(root,"撤销",true);PhotoEditor canvas=findCanvas(root);PhotoEdit draft=(PhotoEdit)read(canvas,"edit");check(draft.rotation==90&&!draft.mirror,"Undo did not restore composition");tap(root,"重做",true);check(((PhotoEdit)read(canvas,"edit")).mirror,"Redo did not restore mirror");tap(root,"取消编辑",true);check(applied.get()==null&&Arrays.equals(pristine,original.encode()),"Cancel committed a draft scene");});
            ui(instrumentation,()->{Dialog dialog=PhotoEditor.open(activity,source,original,2,applied::set);opened.set(dialog);View root=dialog.getWindow().getDecorView();tap(root,"拍立得",true);tap(root,"完成",false);check(applied.get()!=null&&applied.get()!=original&&applied.get().frameStyle==4,"Apply did not return independent frame scene");check(Arrays.equals(pristine,original.encode()),"Editor mutated caller's scene before apply");});
            check(Arrays.equals(pixels,pixels(source))&&!source.isRecycled(),"Editor changed or recycled original pixels");
        }finally{ui(instrumentation,()->{Dialog dialog=opened.get();if(dialog!=null)dialog.dismiss();source.recycle();});}
    }
    private static void layout(Instrumentation instrumentation,PhotoWorkspace workspace,Dialog harness)throws Exception{
        instrumentation.waitForIdleSync();ui(instrumentation,()->{
            Button upload=(Button)read(workspace,"upload");ViewGroup dock=(ViewGroup)read(workspace,"pushDock");Rect viewport=new Rect();check(harness.getWindow().getDecorView().getGlobalVisibleRect(viewport),"Harness has no viewport");
            check(dock.getChildCount()==1&&dock.getChildAt(0)==upload,"Photo footer must contain only its send/progress button");
            check(!insideScroll(dock)&&!insideScroll(upload),"Send/progress button scrolls out of view");Rect bounds=new Rect();check(upload.getGlobalVisibleRect(bounds)&&bounds.height()==upload.getHeight()&&viewport.contains(bounds),"Pinned send/progress button is clipped or outside viewport");
            if(upload.getLayout()!=null){check(upload.getLayout().getHeight()<=upload.getHeight()-upload.getCompoundPaddingTop()-upload.getCompoundPaddingBottom()+1,"Pinned progress text is clipped");for(int line=0;line<upload.getLayout().getLineCount();line++)check(upload.getLayout().getEllipsisCount(line)==0,"Pinned progress text is truncated");}
            check(find(workspace.view,"照片工作室",false)==null&&find(workspace.view,"让喜欢的画面，成为日常。",false)==null,"Removed photo heading/subtitle remains visible");
            check(find(workspace.view,"更多选项",true)==null&&find(workspace.view,"更多选图方式",true)==null,"More menu still occupies the photo canvas/scroll area");
            check(!containsType(workspace.view,ProgressBar.class),"Photo workspace still contains a separate graphical progress bar");
            check(find(workspace.view,"FakeHost：身份已验证，可以刷屏",false)==null&&find(workspace.view,"连接 / 授权",false)==null,"Removed footer connection row remains visible");
            check(find(workspace.view,"停止发送",false)==null&&find(workspace.view,"选好照片，预览后即可推送。",false)==null,"Removed footer status/stop row remains visible");
            check(((ImageView)read(workspace,"preview")).getScaleType()==ImageView.ScaleType.FIT_CENTER,"Preview crops final image");
        });
    }
    private static void selectMenu(AlertDialog dialog,String text){check(dialog!=null&&dialog.isShowing(),"More menu is not visible");android.widget.ListView list=dialog.getListView();check(list!=null,"More menu has no actions");for(int i=0;i<list.getAdapter().getCount();i++)if(text.equals(String.valueOf(list.getAdapter().getItem(i)))){list.performItemClick(list.getChildAt(i-list.getFirstVisiblePosition()),i,list.getAdapter().getItemId(i));return;}throw new AssertionError("Missing menu action: "+text);}
    private static StudioImageCodec.Result awaitFrame(Instrumentation instrumentation,PhotoWorkspace workspace)throws Exception{
        long deadline=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<deadline){AtomicReference<StudioImageCodec.Result> ready=new AtomicReference<>();ui(instrumentation,()->{if(!Boolean.TRUE.equals(read(workspace,"preparing")))ready.set((StudioImageCodec.Result)read(workspace,"frame"));});if(ready.get()!=null)return ready.get();SystemClock.sleep(40);}throw new AssertionError("Photo prepare timed out");
    }
    private static void verify(PanelImage.Prepared prepared){check(prepared.packed.length==105984,"Wrong physical payload size");PanelType panel=PanelType.parse(prepared.panelType);int[] physical=panel.preview(prepared.packed),actual=pixels(prepared.preview);check(physical.length==actual.length,"Wrong preview shape");for(int i=0;i<actual.length;i++){int code=-1;for(int c=0;c<4;c++)if(physical[i]==ImageRaster.color(c)){code=c;break;}check(code>=0&&actual[i]==InkSightQuantizer.COLORS[code],"Warm preview differs from packed code");}}
    private static int[] pixels(Bitmap bitmap){int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return pixels;}
    private static PhotoEditor findCanvas(View root){if(root instanceof PhotoEditor)return (PhotoEditor)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){PhotoEditor found=findCanvas(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void tap(View root,String text,boolean description){View target=find(root,text,description);check(target!=null,"Missing control: "+text);check(target.isEnabled(),"Control disabled: "+text);target.performClick();}
    private static View find(View view,String text,boolean description){if(view.getVisibility()!=View.VISIBLE)return null;if(description?text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()):view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text,description);if(found!=null)return found;}}return null;}
    private static boolean insideScroll(View view){for(android.view.ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView)return true;return false;}
    private static boolean containsType(View view,Class<?> type){if(type.isInstance(view))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsType(group.getChildAt(i),type))return true;}return false;}
    private static Object read(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void write(Object object,String name,Object value)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static void invoke(Object object,String name)throws Exception{Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    private static void ui(Instrumentation instrumentation,UiAction action)throws Exception{AtomicReference<Throwable> failure=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});Throwable error=failure.get();if(error instanceof Exception)throw (Exception)error;if(error instanceof Error)throw (Error)error;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences preferences,Map<String,?> saved){SharedPreferences.Editor edit=preferences.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);}check(edit.commit(),"Cannot restore photo workspace preferences");}
    private static final class FakeHost implements PhotoWorkspace.Host{
        int sends,stops,calendars,notes;boolean finishOnStop;String name="";PanelImage.Prepared sent;EditorStudio.TransferObserver observer;
        public String panelType(){return "A1";}public String connectionState(){return "FakeHost：身份已验证，可以刷屏";}public void connect(){}
        public void send(PanelImage.Prepared frame,String name,EditorStudio.TransferObserver observer){sends++;sent=frame;this.name=name;this.observer=observer;}
        public void stopSending(){stops++;if(finishOnStop&&observer!=null)observer.finished(false,"同步取消，无 NFC 写入");}
        public void log(String message){}public void openCalendar(){calendars++;}public void openNote(){notes++;}public void openDevice(){}public void openLogs(){}
    }
}

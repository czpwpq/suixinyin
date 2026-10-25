package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Offline device checks. No network method, source settings, user preference, cache or NFC is used. */
public final class DailyContentNativeChecks {
    private interface UiAction {void run()throws Exception;}
    public static void run(Instrumentation instrumentation,Activity activity)throws Exception{
        check(Looper.myLooper()!=Looper.getMainLooper(),"Run daily-content checks on instrumentation thread");
        byte[] png=portrait();
        for(int kind=DailyContent.HISTORY;kind<=DailyContent.BIRTHDAY;kind++){
            DailyContent.Scene scene=sample(kind,png);verify(scene);
            if(kind==DailyContent.BRIEF){scene.mode=1;verify(scene);scene.mode=2;verify(scene);}
            if(kind==DailyContent.DAILY){scene.mode=1;verify(scene);}
        }
        portraitOwnership(png);editor(instrumentation,activity,png);
    }
    private static DailyContent.Scene sample(int kind,byte[] portrait){
        DailyContent.Scene scene=new DailyContent.Scene(kind);scene.date="2026-10-24";scene.source="离线测试来源";scene.fetchedAt="2026-10-24 21:01";scene.warning="离线样本，不代表实时内容";
        for(int i=0;i<3;i++){
            DailyContent.Entry e=new DailyContent.Entry();e.id="offline-"+i;e.title=kind==DailyContent.BIRTHDAY?"离线人物 "+i:kind==DailyContent.DAILY?"每日测试文字":"离线内容 "+i;
            e.body="这些文字用于验证本地四色卡片排版。\nLocal fixture — no remote service.";e.byline=kind==DailyContent.HISTORY?"1980 年":kind==DailyContent.BIRTHDAY?"10 月 24 日":"本地测试署名";e.work="离线动漫作品名";e.source="离线样本";e.published="2026-10-24";scene.entries.add(e);
        }
        if(kind==DailyContent.BIRTHDAY){scene.portrait=portrait.clone();scene.portraitId=scene.entries.get(0).id;}scene.normalize();return scene;
    }
    private static void verify(DailyContent.Scene scene)throws Exception{
        byte[] original=scene.encode();int[] first=null;
        for(String panel:new String[]{"A0","A1"}){
            StudioImageCodec.Result result=DailyContentRenderer.prepare(scene,panel);
            try{
                check(result.preview==result.prepared.preview&&result.preview!=result.originalPreview,"Content frame/comparison ownership changed");
                check(result.preview.getWidth()==552&&result.preview.getHeight()==768,"Content card physical dimensions changed");
                check(result.prepared.packed.length==105984&&panel.equals(result.prepared.panelType),"Content payload metadata invalid");
                int[] actual=pixels(result.preview),comparison=pixels(result.originalPreview);check(Arrays.equals(actual,comparison),"Flat card comparison differs from final preview");
                if(first==null)first=actual;else check(Arrays.equals(first,actual),"A0/A1 content preview differs");
                int[] colors=new int[4];for(int y=0;y<768;y++)for(int x=0;x<552;x++){
                    // Independent factory-layout decode: descending x, bottom-up 2-bit pixels; A0 interlaces controller rows.
                    int row=551-x;if(panel.equals("A0"))row=row<276?row*2:1103-row*2;
                    int fromBottom=767-y,index=row*192+fromBottom/4,code=((result.prepared.packed[index]&255)>>(6-2*(fromBottom%4)))&3;
                    check(actual[y*552+x]==InkSightQuantizer.COLORS[code],"Packed data differs from displayed four-color code at "+x+","+y);colors[code]++;
                }
                check(colors[0]>50&&colors[1]>1000&&colors[2]>100&&colors[3]>50,"Card lacks expected hard palette regions");
                check(Arrays.equals(original,scene.encode()),"Renderer mutated caller's daily draft");
            }finally{release(result);}
        }
        DailyContent.Scene changed=scene.copy();changed.warning="另一段离线核对提示";
        if(scene.kind==DailyContent.BIRTHDAY){changed.source="另一数据来源";changed.fetchedAt="2017-02-03 04:05";for(DailyContent.Entry entry:changed.entries)entry.source="新的条目来源";}
        StudioImageCodec.Result left=DailyContentRenderer.prepare(scene,"A1"),right=DailyContentRenderer.prepare(changed,"A1");
        try{if(scene.kind==DailyContent.BIRTHDAY){check(Arrays.equals(pixels(left.preview),pixels(right.preview)),"Birthday technical metadata remains painted on the card");check(DailyContentRenderer.visibleWarning(scene).isEmpty(),"Cached birthday source explanation remains visible");}else onlyChangesInside(left.preview,right.preview,new Rect(24,636,528,674),"Warning painted into content/source region");
            if(scene.kind==DailyContent.DAILY&&scene.mode==1)for(int y=119;y<160;y++)for(int x=28;x<524;x++)check(left.preview.getPixel(x,y)==InkSightQuantizer.COLORS[1],"Book header still has duplicated horizontal decorations");
        }finally{release(left);release(right);}
    }
    private static void portraitOwnership(byte[] png){
        DailyContent.Scene match=sample(DailyContent.BIRTHDAY,png),missing=match.copy();missing.portrait=null;missing.portraitId="";
        StudioImageCodec.Result with=DailyContentRenderer.prepare(match,"A1"),without=DailyContentRenderer.prepare(missing,"A1");
        try{onlyChangesInside(with.preview,without.preview,new Rect(28,88,524,588),"Portrait altered text, warning or source areas");}finally{release(with);release(without);}
        DailyContent.Scene stale=match.copy();stale.selected=1;DailyContent.Scene absent=stale.copy();absent.portrait=null;absent.portraitId="";
        StudioImageCodec.Result staleFrame=DailyContentRenderer.prepare(stale,"A1"),absentFrame=DailyContentRenderer.prepare(absent,"A1");
        try{check(Arrays.equals(pixels(staleFrame.preview),pixels(absentFrame.preview)),"Another character's portrait remained visible after selection changed");check(stale.portrait!=null&&stale.portraitId.equals("offline-0"),"Preparing a mismatched portrait modified caller state");}finally{release(staleFrame);release(absentFrame);}
        DailyContent.Scene invalid=missing.copy();invalid.portrait=new byte[]{1,2,3,4};invalid.portraitId=invalid.entries.get(0).id;
        StudioImageCodec.Result bad=DailyContentRenderer.prepare(invalid,"A1"),blank=DailyContentRenderer.prepare(missing,"A1");
        try{check(Arrays.equals(pixels(bad.preview),pixels(blank.preview)),"Malformed portrait failed to retain the readable character placeholder");}finally{release(bad);release(blank);}
    }
    private static void editor(Instrumentation instrumentation,Activity activity,byte[] png)throws Exception{
        DailyContent.Scene original=sample(DailyContent.HISTORY,png);byte[] before=original.encode();AtomicInteger commits=new AtomicInteger();AtomicReference<DailyContent.Scene> accepted=new AtomicReference<>();AtomicReference<StudioImageCodec.Result> transfer=new AtomicReference<>();AtomicReference<DailyContentEditor.WindowDialog> window=new AtomicReference<>();
        DailyContentEditor.Done done=(scene,result)->{commits.incrementAndGet();accepted.set(scene);transfer.set(result);};
        try{
            ui(instrumentation,()->{DailyContentEditor.WindowDialog opened=(DailyContentEditor.WindowDialog)DailyContentEditor.open(activity,original,"A1",done);window.set(opened);DailyContentEditor.Session session=opened.session;session.tab=1;session.showTools();edit(session,"标题 / 人物姓名").setText("这次编辑要取消");session.apply();check(commits.get()==0&&!session.done.isEnabled(),"Unrendered daily draft was accepted");});
            StudioImageCodec.Result canceled=awaitReady(instrumentation,window.get().session);layout(instrumentation,window.get());
            ui(instrumentation,()->{DailyContentEditor.Session session=window.get().session;long revision=session.requestRevision;String message=session.message;window.get().dismiss();session.requestError(revision,new Exception("late offline error"));session.render(session.generation);session.queueRender();session.apply();check(commits.get()==0&&Arrays.equals(before,original.encode()),"Cancel changed original daily draft or committed");check(canceled.preview.isRecycled()&&session.message.equals(message),"Closed callback revived canceled preview/status");});
            DailyContent.Scene birthday=sample(DailyContent.BIRTHDAY,png);byte[] birthdayBefore=birthday.encode();
            ui(instrumentation,()->{DailyContentEditor.WindowDialog opened=(DailyContentEditor.WindowDialog)DailyContentEditor.open(activity,birthday,"A0",done);window.set(opened);DailyContentEditor.Session session=opened.session;session.tab=1;session.showTools();edit(session,"标题 / 人物姓名").setText("编辑后的人物名");});
            StudioImageCodec.Result exact=awaitReady(instrumentation,window.get().session);layout(instrumentation,window.get());
            ui(instrumentation,()->{DailyContentEditor.Session session=window.get().session;long stale=++session.requestRevision;session.loading=true;session.updateStatus();session.apply();check(!session.done.isEnabled()&&commits.get()==0,"Pending content request did not guard Done");session.invalidateRequest();String message=session.message;session.requestError(stale,new Exception("stale offline error"));check(session.message.equals(message),"Stale request replaced current status");session.updateStatus();check(session.done.isEnabled(),"Canceling request did not restore current ready preview");session.done.performClick();check(commits.get()==1&&transfer.get()==exact&&!exact.preview.isRecycled(),"Done did not transfer the exact preview");check(session.closed&&session.prepared==null&&accepted.get()!=birthday&&accepted.get()!=session.scene,"Done did not detach independent scene/frame ownership");check(accepted.get().entries.get(0)!=birthday.entries.get(0)&&accepted.get().portrait!=birthday.portrait&&accepted.get().entries.get(0).title.equals("编辑后的人物名"),"Done shared mutable draft data or lost edits");session.apply();session.requestError(session.requestRevision,new Exception("closed"));check(commits.get()==1&&Arrays.equals(birthdayBefore,birthday.encode()),"Duplicate Done or closed callback changed original/accepted scene");});
            release(transfer.getAndSet(null));
            DailyContent.Scene empty=new DailyContent.Scene(DailyContent.BRIEF);empty.date="2026-10-24";
            ui(instrumentation,()->{window.set((DailyContentEditor.WindowDialog)DailyContentEditor.open(activity,empty,"A1",done));});awaitReady(instrumentation,window.get().session);
            ui(instrumentation,()->{DailyContentEditor.Session session=window.get().session;check(!session.done.isEnabled(),"Placeholder-only content was sendable");session.apply();check(commits.get()==1,"Empty card committed");session.tab=1;session.showTools();check(!session.done.isEnabled(),"Creating an empty manual entry enabled Done");edit(session,"正文 / 语录 / 书籍介绍").setText("离线手动简报内容");});
            StudioImageCodec.Result manual=awaitReady(instrumentation,window.get().session);
            ui(instrumentation,()->{window.get().session.done.performClick();check(commits.get()==2&&transfer.get()==manual&&accepted.get().entries.get(0).body.equals("离线手动简报内容")&&accepted.get().warning.contains("手动编辑"),"Manual fallback did not commit its own card snapshot");check(empty.entries.isEmpty(),"Manual fallback changed caller's empty draft");});
        }finally{ui(instrumentation,()->{DailyContentEditor.WindowDialog opened=window.get();if(opened!=null)opened.dismiss();release(transfer.getAndSet(null));});}
    }
    private static StudioImageCodec.Result awaitReady(Instrumentation instrumentation,DailyContentEditor.Session session)throws Exception{long deadline=SystemClock.uptimeMillis()+25000;while(SystemClock.uptimeMillis()<deadline){AtomicReference<StudioImageCodec.Result> ready=new AtomicReference<>();ui(instrumentation,()->{if(!session.preparing&&!session.loading&&session.prepared!=null&&session.preparedGeneration==session.generation)ready.set(session.prepared);});if(ready.get()!=null)return ready.get();SystemClock.sleep(40);}throw new AssertionError("Offline content card preview timed out");}
    private static void layout(Instrumentation instrumentation,DailyContentEditor.WindowDialog dialog)throws Exception{instrumentation.waitForIdleSync();ui(instrumentation,()->{DailyContentEditor.Session session=dialog.session;Rect image=new Rect(),tools=new Rect(),viewport=new Rect(),done=new Rect();View toolViewport=(View)session.tools.getParent();check(dialog.getWindow().getDecorView().getGlobalVisibleRect(viewport),"Content editor viewport missing");check(session.preview.getGlobalVisibleRect(image)&&toolViewport.getGlobalVisibleRect(tools)&&session.done.getGlobalVisibleRect(done),"Content editor visible regions missing");check(image.bottom<=tools.top&&!Rect.intersects(image,tools)&&image.height()>0,"Content tool panel covers or collapses preview");check(viewport.contains(done)&&done.height()==session.done.getHeight(),"Content Done is outside viewport");check(session.preview.getScaleType()==android.widget.ImageView.ScaleType.FIT_CENTER,"Content preview crops the final frame");});}
    private static EditText edit(DailyContentEditor.Session session,String description){View view=find(session.tools,description);check(view instanceof EditText,"Missing offline edit input: "+description);return (EditText)view;}
    private static View find(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=find(group.getChildAt(i),description);if(child!=null)return child;}}return null;}
    private static void onlyChangesInside(Bitmap first,Bitmap second,Rect area,String message){int changed=0;for(int y=0;y<768;y++)for(int x=0;x<552;x++)if(first.getPixel(x,y)!=second.getPixel(x,y)){check(area.contains(x,y),message+" at "+x+","+y);changed++;}check(changed>20,"Fixture change is not visible");}
    private static byte[] portrait(){Bitmap image=Bitmap.createBitmap(96,120,Bitmap.Config.ARGB_8888);int[] pixels=new int[96*120];for(int y=0;y<120;y++)for(int x=0;x<96;x++)pixels[y*96+x]=x<48?(y<60?0xfff5cd00:0xffca2830):(y<60?0xff202124:0xffeeeeee);image.setPixels(pixels,0,96,0,0,96,120);try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();check(image.compress(Bitmap.CompressFormat.PNG,100,bytes),"Cannot create offline portrait PNG");return bytes.toByteArray();}finally{image.recycle();}}
    private static int[] pixels(Bitmap image){int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return pixels;}
    private static void release(StudioImageCodec.Result result){if(result==null)return;if(!result.preview.isRecycled())result.preview.recycle();if(result.originalPreview!=result.preview&&!result.originalPreview.isRecycled())result.originalPreview.recycle();}
    private static void ui(Instrumentation instrumentation,UiAction action)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});Throwable failure=error.get();if(failure instanceof Exception)throw (Exception)failure;if(failure instanceof Error)throw (Error)failure;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private DailyContentNativeChecks(){}
}

package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Typed draft/editor ownership checks. Weather replies are injected offline; no Host or NFC. */
public final class CardEditorNativeChecks {
    private interface UiAction{void run()throws Exception;}
    public static void run(Instrumentation instrumentation,Activity activity)throws Exception{
        CardScene original=CardScene.note();original.text="原始便签\nUntouched";byte[] pristine=original.encode();
        AtomicReference<CardEditor.WindowDialog> window=new AtomicReference<>();AtomicReference<CardScene> accepted=new AtomicReference<>();AtomicReference<StudioImageCodec.Result> transferred=new AtomicReference<>();AtomicInteger commits=new AtomicInteger();
        CardEditor.Done done=(scene,result)->{commits.incrementAndGet();accepted.set(scene);transferred.set(result);};
        try{
            ui(instrumentation,()->{CardEditor.WindowDialog dialog=(CardEditor.WindowDialog)CardEditor.open(activity,original,"A1",done);window.set(dialog);dialog.session.textInput.setText("这次修改要取消");dialog.session.apply();check(commits.get()==0&&!dialog.session.doneButton.isEnabled(),"Pending preview was accepted");});
            StudioImageCodec.Result discarded=awaitReady(instrumentation,window.get().session);
            layout(instrumentation,window.get());ui(instrumentation,()->{window.get().dismiss();check(commits.get()==0&&Arrays.equals(pristine,original.encode()),"Cancel committed or mutated the caller's draft");check(discarded.preview.isRecycled(),"Canceled preview bitmap ownership leaked");});
            ui(instrumentation,()->{CardEditor.WindowDialog dialog=(CardEditor.WindowDialog)CardEditor.open(activity,original,"A1",done);window.set(dialog);CardEditor.Session session=dialog.session;session.textInput.setText("新的便签\nNew note");session.mode=1;session.showTools();tap(dialog,"字色 红色",true);tap(dialog,"描边",false);});
            StudioImageCodec.Result note=awaitReady(instrumentation,window.get().session);layout(instrumentation,window.get());
            ui(instrumentation,()->{CardEditor.Session session=window.get().session;session.doneButton.performClick();check(commits.get()==1&&accepted.get()!=original&&accepted.get().kind==CardScene.NOTE&&accepted.get().text.equals("新的便签\nNew note")&&accepted.get().foreground==3&&accepted.get().outline,"Done lost typed note fields");check(transferred.get()==note&&!note.preview.isRecycled()&&session.closed&&session.prepared==null,"Done failed to transfer exact frame ownership");session.apply();check(commits.get()==1&&Arrays.equals(pristine,original.encode()),"Duplicate Done callback or caller mutation");});
            release(transferred.getAndSet(null));
            CardScene calendar=CardScene.calendar();calendar.weather="旧天气";calendar.weatherSource="旧来源";calendar.weatherUpdated="旧时间";byte[] calendarBefore=calendar.encode();
            ui(instrumentation,()->{CardEditor.WindowDialog dialog=(CardEditor.WindowDialog)CardEditor.open(activity,calendar,"A0",done);window.set(dialog);CardEditor.Session session=dialog.session;session.mode=2;session.showTools();long stale=++session.weatherRevision;session.weatherLoading=true;session.cityInput.setText("杭州");session.weatherInput.setText("手动晴天");session.tempInput.setText("24°C");check(!session.weatherLoading&&session.scene.weatherSource.isEmpty()&&session.scene.weatherUpdated.isEmpty(),"Manual weather retained API metadata/busy state");session.applyWeatherSnapshot(stale,new WeatherClient.Snapshot("过期城市","过期天气","-99°C","旧时间","旧来源"));check(session.scene.city.equals("杭州")&&session.scene.weather.equals("手动晴天"),"Stale weather overwrote manual fields");long current=++session.weatherRevision;session.weatherLoading=true;session.updateStatus();check(!session.doneButton.isEnabled(),"Weather pending did not guard Done");session.applyWeatherSnapshot(current,new WeatherClient.Snapshot("杭州","多云","23°C","离线测试时间","离线测试来源"));check(session.scene.weatherSource.equals("离线测试来源")&&!session.weatherLoading,"Current weather snapshot did not apply atomically");session.mode=0;session.showTools();tap(dialog,CalendarCards.templateNames()[2],true);});
            StudioImageCodec.Result wide=awaitReady(instrumentation,window.get().session);layout(instrumentation,window.get());
            ui(instrumentation,()->{CardEditor.Session session=window.get().session;check(session.scene.template==2&&session.scene.isLandscape(),"Calendar template selection not retained");long revision=session.weatherRevision;session.doneButton.performClick();check(commits.get()==2&&accepted.get().kind==CardScene.CALENDAR&&accepted.get().weather.equals("多云")&&accepted.get().template==2&&transferred.get()==wide&&!wide.preview.isRecycled(),"Calendar Done lost manual/API/template snapshot");session.applyWeatherSnapshot(revision,new WeatherClient.Snapshot("关闭后","旧结果","-99°C","旧时间","旧来源"));check(accepted.get().city.equals("杭州")&&Arrays.equals(calendarBefore,calendar.encode()),"Closed callback changed accepted/caller scene");});
        }finally{ui(instrumentation,()->{CardEditor.WindowDialog dialog=window.get();if(dialog!=null)dialog.dismiss();release(transferred.getAndSet(null));});}
    }
    static StudioImageCodec.Result awaitReady(Instrumentation instrumentation,CardEditor.Session session)throws Exception{
        long until=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<until){AtomicReference<StudioImageCodec.Result> ready=new AtomicReference<>();ui(instrumentation,()->{if(!session.preparing&&!session.weatherLoading&&session.prepared!=null&&session.preparedGeneration==session.generation)ready.set(session.prepared);});if(ready.get()!=null)return ready.get();SystemClock.sleep(40);}throw new AssertionError("Typed card preview timed out");
    }
    private static void layout(Instrumentation instrumentation,CardEditor.WindowDialog dialog)throws Exception{instrumentation.waitForIdleSync();ui(instrumentation,()->{CardEditor.Session session=dialog.session;Rect image=new Rect(),tools=new Rect(),done=new Rect(),viewport=new Rect();check(dialog.getWindow().getDecorView().getGlobalVisibleRect(viewport),"Card editor has no viewport");check(session.preview.getGlobalVisibleRect(image)&&session.toolScroll.getGlobalVisibleRect(tools)&&session.doneButton.getGlobalVisibleRect(done),"Card editor regions missing");check(!Rect.intersects(image,tools)&&image.bottom<=tools.top,"Card controls cover preview");check(viewport.contains(done)&&done.height()==session.doneButton.getHeight(),"Done button outside viewport");check(session.toolScroll.getHeight()>0&&image.height()>0,"Card editor collapsed preview/tools");});}
    private static void tap(CardEditor.WindowDialog dialog,String text,boolean description){View target=find(dialog.getWindow().getDecorView(),text,description);check(target!=null&&target.isEnabled(),"Missing typed card control: "+text);target.performClick();}
    private static View find(View view,String text,boolean description){if(view.getVisibility()!=View.VISIBLE)return null;if(description?text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()):view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text,description);if(found!=null)return found;}}return null;}
    private static void release(StudioImageCodec.Result result){if(result==null)return;if(!result.preview.isRecycled())result.preview.recycle();if(result.originalPreview!=result.preview&&!result.originalPreview.isRecycled())result.originalPreview.recycle();}
    private static void ui(Instrumentation instrumentation,UiAction action)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});if(error.get() instanceof Exception)throw (Exception)error.get();if(error.get() instanceof Error)throw (Error)error.get();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

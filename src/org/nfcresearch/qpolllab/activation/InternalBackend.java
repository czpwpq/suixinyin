package org.nfcresearch.qpolllab.activation;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import org.nfcresearch.qpolllab.BridgeClient;
import org.nfcresearch.qpolllab.BridgeIdentity;

/** In-process holder for owner-bound shell services. It never grants privileges to ordinary apps. */
public final class InternalBackend {
    public static final String AUTHORITY=BridgeIdentity.PACKAGE_NAME+".internal";
    static final String METHOD_DELIVER="deliver",METHOD_FAILURE="startupFailure";
    static final String CONTROL_TOKEN=BridgeIdentity.PACKAGE_NAME+".InternalControl";
    static final int CONTROL_STOP=1,CONTROL_INFO=2;
    private static final Object LOCK=new Object();
    private static final Binder OWNER=new Binder();
    private static Attempt pending,accepted;
    private static IBinder photo,reader,control;
    private static boolean stopping,confirmed;
    private static String currentStatus="尚未激活；请在本应用内完成无线调试配对";

    private InternalBackend(){}
    public static final class Attempt {
        public final String command;
        private final String apk;
        private final ActivationProof proof;
        private final int appUid;
        private final long deadline;
        private boolean cancelled,delivered;
        private String failure;
        private Attempt(ApplicationInfo info){
            appUid=info.uid;apk=info.sourceDir;proof=ActivationProof.create(appUid,apk,SystemClock.elapsedRealtime());deadline=proof.deadline();command=proof.command();
        }
        /** Call off the UI thread. A command having been accepted by adb is not activation success. */
        public boolean await(long timeoutMs)throws Exception{
            if(timeoutMs<0||timeoutMs>90000)throw new IllegalArgumentException("激活等待时间错误");
            long until=Math.min(deadline,SystemClock.elapsedRealtime()+timeoutMs);
            synchronized(LOCK){
                while(!cancelled&&failure==null&&!delivered){long remaining=until-SystemClock.elapsedRealtime();if(remaining<=0)break;LOCK.wait(remaining);}
                if(failure!=null)throw new IllegalStateException(failure);
                if(cancelled)throw new java.util.concurrent.CancellationException("激活已取消");
                if(delivered&&accepted==this&&rawReadyLocked())return true;
            }
            cancel();return false;
        }
        /** Invalidates this exact request. An older/late daemon cannot become a new session. */
        public void cancel(){boolean close=false;synchronized(LOCK){if(cancelled)return;cancelled=true;proof.cancel();if(pending==this){pending=null;currentStatus="激活已取消，请重新操作";}close=accepted==this;LOCK.notifyAll();}if(close)stop();}
        /** Use before persisting bootstrap output; never expose the launch command or its nonce. */
        public String sanitizeOutput(String text){return proof.sanitizeOutput(text);}
    }
    public static Attempt prepare(Context context){
        if(context==null)throw new IllegalArgumentException("App context missing");
        ApplicationInfo info=context.getApplicationInfo();
        if(!BridgeIdentity.PACKAGE_NAME.equals(context.getPackageName())||info==null||info.uid!=android.os.Process.myUid()||info.uid<10000||info.sourceDir==null||info.sourceDir.isEmpty())throw new SecurityException("应用安装身份不匹配");
        synchronized(LOCK){if(stopping)throw new IllegalStateException("上一内置会话正在释放，请稍后重试");if(rawReadyLocked())throw new IllegalStateException("内置服务已启动或正在确认独立运行，请先完成或明确停止");if(pending!=null){pending.cancelled=true;pending.proof.cancel();pending=null;}Attempt attempt=new Attempt(info);pending=attempt;confirmed=false;currentStatus="正在等待内置服务身份验证";LOCK.notifyAll();return attempt;}
    }
    public static boolean isReady(){synchronized(LOCK){return readyLocked();}}
    public static boolean isStopping(){synchronized(LOCK){return stopping;}}
    public static IBinder photoBinder(){synchronized(LOCK){return readyLocked()?photo:null;}}
    public static IBinder readerBinder(){synchronized(LOCK){return readyLocked()?reader:null;}}
    public static String status(){synchronized(LOCK){if(accepted!=null&&!rawReadyLocked()&&!stopping)return "内置服务已断开，请重新激活";return currentStatus;}}
    private static boolean rawReadyLocked(){return !stopping&&photo!=null&&reader!=null&&control!=null&&photo.pingBinder()&&reader.pingBinder()&&control.pingBinder();}
    private static boolean readyLocked(){return confirmed&&rawReadyLocked();}
    /** Call after the launching ADB transport is closed. Revalidates identity before publishing services. */
    public static String verifyReady(Context context)throws Exception{
        if(context==null||!BridgeIdentity.PACKAGE_NAME.equals(context.getPackageName()))throw new SecurityException("应用包名不匹配");
        ApplicationInfo info=context.getApplicationInfo();int uid=android.os.Process.myUid();if(info==null||info.uid!=uid||uid<10000||info.sourceDir==null||info.sourceDir.isEmpty())throw new SecurityException("应用安装身份不匹配");
        final IBinder checkedPhoto,checkedReader,checkedControl;final Attempt owner;
        synchronized(LOCK){if(!rawReadyLocked()||accepted==null||accepted.cancelled)throw new IllegalStateException("内置服务交付后已失联或取消，请重新激活");checkedPhoto=photo;checkedReader=reader;checkedControl=control;owner=accepted;}
        String identity=BridgeClient.read(checkedPhoto,uid,info.sourceDir);verifyReader(checkedReader,uid,info.sourceDir);verifyControl(checkedControl,uid,info.sourceDir);
        synchronized(LOCK){if(!rawReadyLocked()||photo!=checkedPhoto||reader!=checkedReader||control!=checkedControl||accepted!=owner||owner.cancelled)throw new IllegalStateException("独立运行确认期间内置会话已改变");confirmed=true;currentStatus="内置服务独立运行已确认 · 身份已验证 · 可以刷屏";LOCK.notifyAll();}
        return identity+" · 照片、阅读和宿主后端复验通过";
    }

    static Bundle deliver(Context context,Bundle extras,int sender)throws Exception{
        ActivationProof.requireSender(sender);
        if(extras==null)throw new SecurityException("Activation identity missing");
        final Attempt attempt;
        synchronized(LOCK){attempt=pending;validatePendingLocked(attempt,sender,extras.getString("nonce"));}
        IBinder candidatePhoto=extras.getBinder("photo"),candidateReader=extras.getBinder("reader"),candidateControl=extras.getBinder("control");
        if(candidatePhoto==null||candidateReader==null||candidateControl==null||!candidateControl.pingBinder())throw new SecurityException("内置服务未完整交付");
        long identity=Binder.clearCallingIdentity();
        try{
            BridgeClient.read(candidatePhoto,attempt.appUid,attempt.apk);
            verifyReader(candidateReader,attempt.appUid,attempt.apk);
            verifyControl(candidateControl,attempt.appUid,attempt.apk);
        }finally{Binder.restoreCallingIdentity(identity);}
        ApplicationInfo currentInstall=context.getApplicationInfo();attempt.proof.validate(sender,extras.getString("nonce"),currentInstall.uid,currentInstall.sourceDir,SystemClock.elapsedRealtime());
        synchronized(LOCK){
            validatePendingLocked(attempt,sender,extras.getString("nonce"));
            final IBinder watched=candidateControl;
            watched.linkToDeath(()->backendDied(watched),0);
            attempt.proof.consume(sender,extras.getString("nonce"),currentInstall.uid,currentInstall.sourceDir,SystemClock.elapsedRealtime());
            photo=candidatePhoto;reader=candidateReader;control=candidateControl;accepted=attempt;pending=null;attempt.delivered=true;stopping=false;confirmed=false;
            currentStatus="内置服务已交付，正在确认脱离 ADB 后的独立运行";LOCK.notifyAll();
        }
        Bundle reply=new Bundle();reply.putBinder("owner",OWNER);reply.putBoolean("accepted",true);return reply;
    }
    static Bundle failure(Bundle extras,int sender){
        ActivationProof.requireSender(sender);
        synchronized(LOCK){Attempt attempt=pending;validatePendingLocked(attempt,sender,extras==null?null:extras.getString("nonce"));String error=extras.getString("error","内置服务启动失败");if(error.length()>400)error=error.substring(0,400);attempt.failure=error;attempt.proof.cancel();pending=null;currentStatus=error;LOCK.notifyAll();}
        return Bundle.EMPTY;
    }
    private static void validatePendingLocked(Attempt attempt,int sender,String value){
        if(attempt==null||attempt!=pending||attempt.cancelled||attempt.delivered)throw new SecurityException("激活请求已取消、过期或身份不匹配");
        attempt.proof.validate(sender,value,attempt.appUid,attempt.apk,SystemClock.elapsedRealtime());
    }
    private static void verifyReader(IBinder binder,int uid,String apk)throws Exception{
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{
            request.writeInterfaceToken(BridgeIdentity.PACKAGE_NAME+".ReaderPanelSession");if(!binder.transact(5,request,reply,0))throw new SecurityException("阅读后端不支持身份校验");reply.readException();
            int protocol=reply.readInt(),version=reply.readInt();String pkg=reply.readString();int owner=reply.readInt(),caller=reply.readInt(),backend=reply.readInt();String source=reply.readString();
            BridgeIdentity.verify(pkg,protocol,version,owner,caller,backend,source,uid,apk);if(reply.dataAvail()!=0)throw new SecurityException("阅读后端返回未知身份字段");
        }finally{request.recycle();reply.recycle();}
    }
    private static void verifyControl(IBinder binder,int uid,String apk)throws Exception{
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{
            request.writeInterfaceToken(CONTROL_TOKEN);if(!binder.transact(CONTROL_INFO,request,reply,0))throw new SecurityException("内置宿主不支持身份校验");reply.readException();
            int protocol=reply.readInt(),version=reply.readInt();String pkg=reply.readString();int owner=reply.readInt(),caller=reply.readInt(),backend=reply.readInt();String source=reply.readString();
            BridgeIdentity.verify(pkg,protocol,version,owner,caller,backend,source,uid,apk);if(reply.dataAvail()!=0)throw new SecurityException("内置宿主返回未知身份字段");
        }finally{request.recycle();reply.recycle();}
    }
    /** Explicit user stop; NFC cancellation and worker cleanup occur off the main thread. */
    public static void stop(){
        final IBinder ending;
        synchronized(LOCK){confirmed=false;if(pending!=null){pending.cancelled=true;pending.proof.cancel();pending=null;}ending=control;if(ending==null){currentStatus="内置服务未运行";LOCK.notifyAll();return;}if(stopping)return;stopping=true;currentStatus="正在停止内置服务并释放 NFC 会话";LOCK.notifyAll();}
        Thread cleanup=new Thread(()->{Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{request.writeInterfaceToken(CONTROL_TOKEN);if(!ending.transact(CONTROL_STOP,request,reply,0))throw new IllegalStateException("停止命令未被接受");reply.readException();}catch(Exception error){synchronized(LOCK){if(control==ending){currentStatus="内置服务停止失败，请重新打开应用后检查";stopping=false;}}}finally{request.recycle();reply.recycle();}},"internal-backend-stop");cleanup.setDaemon(true);cleanup.start();
    }
    private static void backendDied(IBinder ended){synchronized(LOCK){if(control!=ended)return;boolean requested=stopping;photo=reader=control=null;accepted=null;stopping=confirmed=false;currentStatus=requested?"内置服务已停止":"内置服务已断开，请重新激活";LOCK.notifyAll();}}
}

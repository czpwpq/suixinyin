package org.nfcresearch.qpolllab.activation;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.inkreader.PanelShizukuService;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nfcresearch.qpolllab.BridgeIdentity;
import org.nfcresearch.qpolllab.ShellBridge;

/** Shell app_process entry point. Handoff follows Android's external-provider API conventions. */
public final class InternalBridgeBootstrap {
    private static final AtomicBoolean closing=new AtomicBoolean();
    private static ShellBridge photo;
    private static PanelShizukuService reader;
    private static IBinder appOwner;
    private static int expectedUid;
    private static String nonce,apk;
    private static volatile String lastStage="ENTRY";

    public static void main(String[] args){
        try{
            stage("MAIN_ENTERED");
            int processUid=android.os.Process.myUid();
            if(processUid!=0&&processUid!=2000)throw new SecurityException("Embedded backend must run under shell/root");
            stage("PRIVILEGED_UID_VERIFIED");
            if(args.length!=3)throw new IllegalArgumentException("Invalid activation argument count");
            expectedUid=Integer.parseInt(args[0]);nonce=args[1];apk=args[2];
            if(expectedUid<10000||!nonce.matches("[0-9a-f]{64}")||!apk.startsWith("/"))throw new SecurityException("Invalid activation identity");
            stage("ARGUMENTS_VALIDATED");
            isolateSession();
            Os.umask(0077);
            if(Looper.getMainLooper()==null)Looper.prepareMainLooper();
            stage("LOOPER_PREPARED");
            Context context=packageContext(expectedUid/100000);
            ApplicationInfo installed=context.getApplicationInfo();
            String classPath=System.getenv("CLASSPATH");
            if(!BridgeIdentity.PACKAGE_NAME.equals(context.getPackageName())||installed==null||installed.uid!=expectedUid||!apk.equals(installed.sourceDir)||classPath==null||!new File(classPath).getCanonicalPath().equals(new File(apk).getCanonicalPath()))throw new SecurityException("Installed application identity changed");
            stage("INSTALL_IDENTITY_VERIFIED");
            photo=new ShellBridge(context);stage("PHOTO_SERVICE_CREATED");
            reader=new PanelShizukuService(context);stage("READER_SERVICE_CREATED");
            Control control=new Control();
            stage("CONTROL_SERVICE_CREATED");
            Bundle payload=new Bundle();payload.putString("nonce",nonce);payload.putBinder("photo",photo);payload.putBinder("reader",reader);payload.putBinder("control",control);
            Bundle answer=send(InternalBackend.METHOD_DELIVER,payload,expectedUid/100000);
            if(answer==null||!answer.getBoolean("accepted")||answer.getBinder("owner")==null)throw new SecurityException("Application rejected activation handoff");
            appOwner=answer.getBinder("owner");appOwner.linkToDeath(InternalBridgeBootstrap::requestStop,0);
            stage("HANDOFF_ACCEPTED");
            stage("STDIO_DETACH_BEGIN");
            detachStdio();
            nonce=null;
            Looper.loop();
            requestStop();
        }catch(Throwable error){
            String failureStage=lastStage;String message=safeFailure(error);
            emit("ERROR",failureStage+" / "+message);
            if(nonce!=null&&expectedUid>=10000)try{Bundle failure=new Bundle();failure.putString("nonce",nonce);failure.putString("error",message);send(InternalBackend.METHOD_FAILURE,failure,expectedUid/100000);}catch(Throwable reporting){emit("ERROR","FAILURE_REPORT_UNAVAILABLE / "+safeFailure(reporting));}
            shutdownNow();System.exit(1);
        }
    }
    private static void isolateSession()throws Exception{
        stage("SESSION_ISOLATION_BEGIN");
        try{if(Os.setsid()!=android.os.Process.myPid())throw new IllegalStateException("Unexpected session identity");stage("SESSION_ISOLATED");}
        catch(ErrnoException error){if(error.errno!=OsConstants.EPERM||!alreadySessionLeader())throw error;stage("SESSION_ALREADY_ISOLATED");}
    }
    private static boolean alreadySessionLeader()throws Exception{
        byte[] bytes=new byte[4096];int count;try(FileInputStream input=new FileInputStream("/proc/self/stat")){count=input.read(bytes);}
        if(count<1)throw new IllegalStateException("Unable to read own process session");
        String stat=new String(bytes,0,count,java.nio.charset.StandardCharsets.US_ASCII);int end=stat.lastIndexOf(')');if(end<1)throw new IllegalStateException("Malformed own process session");
        String[] fields=stat.substring(end+1).trim().split("\\s+");return fields.length>3&&Integer.parseInt(fields[3])==android.os.Process.myPid();
    }
    /** Called only after the application accepted all binders. It leaves the daemon independent of ADB. */
    private static void detachStdio()throws Exception{
        System.out.flush();System.err.flush();
        FileDescriptor nil=Os.open("/dev/null",OsConstants.O_RDWR|OsConstants.O_CLOEXEC,0);
        try{Os.dup2(nil,0);Os.dup2(nil,2);System.out.flush();Os.dup2(nil,1);}finally{Os.close(nil);}
    }
    private static Context packageContext(int user)throws Exception{
        stage("ACTIVITY_THREAD_BEGIN");
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Object thread=activityThread.getMethod("systemMain").invoke(null);
        stage("ACTIVITY_THREAD_CREATED");
        Context system=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        stage("SYSTEM_CONTEXT_READY");
        Class<?> userHandle=Class.forName("android.os.UserHandle");Object target=userHandle.getMethod("of",int.class).invoke(null,user);
        stage("PACKAGE_CONTEXT_BEGIN");
        Context context=(Context)Context.class.getMethod("createPackageContextAsUser",String.class,int.class,userHandle).invoke(system,BridgeIdentity.PACKAGE_NAME,Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY,target);
        stage("PACKAGE_CONTEXT_READY");return context;
    }
    /** Public API shapes mirror IContentProvider.call, including Android 12+ AttributionSource. */
    private static Bundle call(Object provider,String method,Bundle extras)throws Exception{
        Class<?> providerType=Class.forName("android.content.IContentProvider");
        if(Build.VERSION.SDK_INT>=31){
            Class<?> attribution=Class.forName("android.content.AttributionSource"),builder=Class.forName("android.content.AttributionSource$Builder");
            Object sourceBuilder=builder.getConstructor(int.class).newInstance(android.os.Process.myUid());
            builder.getMethod("setPackageName",String.class).invoke(sourceBuilder,new Object[]{null});
            Object source=builder.getMethod("build").invoke(sourceBuilder);
            stage("ATTRIBUTION_SOURCE_READY");
            try{return (Bundle)providerType.getMethod("call",attribution,String.class,String.class,String.class,Bundle.class).invoke(provider,source,InternalBackend.AUTHORITY,method,null,extras);}
            catch(NoSuchMethodException missing){/* Some vendor releases retain the API30 shape. */}
        }
        if(Build.VERSION.SDK_INT>=30)return (Bundle)providerType.getMethod("call",String.class,String.class,String.class,String.class,String.class,Bundle.class).invoke(provider,null,null,InternalBackend.AUTHORITY,method,null,extras);
        if(Build.VERSION.SDK_INT>=29)return (Bundle)providerType.getMethod("call",String.class,String.class,String.class,String.class,Bundle.class).invoke(provider,null,InternalBackend.AUTHORITY,method,null,extras);
        return (Bundle)providerType.getMethod("call",String.class,String.class,String.class,Bundle.class).invoke(provider,null,method,null,extras);
    }
    private static Bundle send(String method,Bundle extras,int user)throws Exception{
        stage(InternalBackend.METHOD_DELIVER.equals(method)?"DELIVERY_PROVIDER_ACQUIRE_BEGIN":"FAILURE_PROVIDER_ACQUIRE_BEGIN");
        Class<?> managerType=Class.forName("android.app.IActivityManager");
        Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        stage("ACTIVITY_MANAGER_READY");
        Object holder;
        try{holder=managerType.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(manager,InternalBackend.AUTHORITY,user,null,InternalBackend.AUTHORITY);}
        catch(NoSuchMethodException old){holder=managerType.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class).invoke(manager,InternalBackend.AUTHORITY,user,null);}
        if(holder==null)throw new IllegalStateException("Unable to acquire activation provider");
        stage("PROVIDER_HOLDER_READY");
        try{
            Field field=holder.getClass().getField("provider");Object provider=field.get(holder);
            if(provider==null)throw new IllegalStateException("Activation provider is unavailable");
            stage("PROVIDER_CALL_BEGIN");Bundle reply=call(provider,method,extras);stage("PROVIDER_CALL_RETURNED");return reply;
        }finally{
            try{try{managerType.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(manager,InternalBackend.AUTHORITY,null,user);}
                catch(NoSuchMethodException old){if(user==0)managerType.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(manager,InternalBackend.AUTHORITY,null);}}
            catch(Throwable release){emit("WARN","PROVIDER_RELEASE / "+safeFailure(release));}
        }
    }
    private static final class Control extends Binder {
        Control(){attachInterface(null,InternalBackend.CONTROL_TOKEN);}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code!=InternalBackend.CONTROL_STOP&&code!=InternalBackend.CONTROL_INFO)return super.onTransact(code,data,reply,flags);
            if(Binder.getCallingUid()!=expectedUid)throw new SecurityException("Wrong embedded backend owner");
            data.enforceInterface(InternalBackend.CONTROL_TOKEN);reply.writeNoException();
            if(code==InternalBackend.CONTROL_INFO){reply.writeInt(BridgeIdentity.PROTOCOL_VERSION);reply.writeInt(BridgeIdentity.SERVICE_VERSION);reply.writeString(BridgeIdentity.PACKAGE_NAME);reply.writeInt(expectedUid);reply.writeInt(Binder.getCallingUid());reply.writeInt(android.os.Process.myUid());reply.writeString(apk);return true;}
            requestStop();return true;
        }
    }
    private static void requestStop(){if(!closing.compareAndSet(false,true))return;Thread cleanup=new Thread(()->{shutdownNow();System.exit(0);},"internal-host-stop");cleanup.setDaemon(false);cleanup.start();}
    private static void shutdownNow(){
        Thread photoStop=new Thread(()->{try{if(photo!=null)photo.shutdownFromHost();}catch(Throwable ignored){}},"internal-photo-stop");
        Thread readerStop=new Thread(()->{try{if(reader!=null)reader.shutdownFromHost();}catch(Throwable ignored){}},"internal-reader-stop");
        photoStop.start();readerStop.start();
        try{photoStop.join(331000);readerStop.join(20000);}catch(InterruptedException error){Thread.currentThread().interrupt();}
    }
    private static void stage(String name){lastStage=name;emit("STAGE",name);}
    private static void emit(String kind,String value){System.out.println("ACT|"+kind+"|"+value.replace('\n',' ').replace('\r',' ').replace('|','/'));System.out.flush();}
    private static String safeFailure(Throwable error){while(error instanceof InvocationTargetException&&((InvocationTargetException)error).getCause()!=null)error=((InvocationTargetException)error).getCause();String message="内置服务启动失败："+error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());if(nonce!=null)message=message.replace(nonce,"[activation-secret]");if(apk!=null)message=message.replace(apk,"[installed-apk]");message=message.replace('\n',' ').replace('\r',' ').replace('|','/');return message.length()>500?message.substring(0,500):message;}
}

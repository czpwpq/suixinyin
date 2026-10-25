/* Adapted from pjk2336/huawei-eink-reader a4b72b79acec37543339b9810bcb34105b061d65.
 * PolyForm Noncommercial 1.0.0. See third-party/inkreader/ notices. */
package com.inkreader;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.nfcresearch.qpolllab.BridgeIdentity;
import org.nfcresearch.qpolllab.NfcSessionLock;

/** Strict owner-bound bridge for a persistent, non-retrying reader NFC worker. */
public final class PanelShizukuService extends Binder {
    static final String TOKEN=BridgeIdentity.PACKAGE_NAME+".ReaderPanelSession";
    static final int INFO_TRANSACTION=5;
    private final Object lock=new Object();private final StringBuilder logs=new StringBuilder();
    private final int appUid;private final String apk,packageName;
    private volatile boolean active,stopping;private volatile Process child;
    private volatile BufferedWriter commands;private volatile File workDir,stopFile;
    private IBinder owner;private IBinder.DeathRecipient ownerDeath;private int sequence;private String selectedType;
    public PanelShizukuService()throws Exception{this(resolveContext());}
    public PanelShizukuService(Context context)throws Exception{
        int uid=android.os.Process.myUid();if(uid!=0&&uid!=2000)throw new SecurityException("Shell/root required");
        if(context==null)throw new IllegalStateException("App context unavailable; Shizuku13+ required");
        packageName=context.getPackageName();if(!BridgeIdentity.PACKAGE_NAME.equals(packageName))throw new SecurityException("Wrong reader service package");
        ApplicationInfo info=context.getApplicationInfo();if(info==null||info.uid<10000||info.sourceDir==null||info.sourceDir.isEmpty())throw new IllegalStateException("App identity unavailable");
        appUid=info.uid;apk=info.sourceDir;attachInterface(null,TOKEN);
    }
    private static Context resolveContext()throws Exception{Object context=Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);if(!(context instanceof Context))throw new IllegalStateException("App context unavailable; restart Shizuku13+");return (Context)context;}
    private void log(String line){synchronized(lock){logs.append(line).append('\n');if(logs.length()>80000){int cut=logs.indexOf("\n",logs.length()-60000);if(cut>=0)logs.delete(0,cut+1);}}}
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==16777115){new Thread(()->{stopAndWait();System.exit(0);},"reader-destroy").start();return true;}
        if(code==INFO_TRANSACTION){data.enforceInterface(TOKEN);reply.writeNoException();reply.writeInt(BridgeIdentity.PROTOCOL_VERSION);reply.writeInt(BridgeIdentity.SERVICE_VERSION);reply.writeString(packageName);reply.writeInt(appUid);reply.writeInt(Binder.getCallingUid());reply.writeInt(android.os.Process.myUid());reply.writeString(apk);return true;}
        if(code<1||code>4)return super.onTransact(code,data,reply,flags);
        int caller=Binder.getCallingUid();if(caller!=appUid)throw new SecurityException("Wrong reader caller: expected="+appUid+", actual="+caller);
        data.enforceInterface(TOKEN);
        if(code==1){IBinder token=data.readStrongBinder();if(token==null)throw new IllegalArgumentException("Reader owner missing");start(token);reply.writeNoException();return true;}
        if(code==2){synchronized(lock){reply.writeNoException();reply.writeString(logs.toString());logs.setLength(0);reply.writeInt(active?1:0);}return true;}
        if(code==3){int operation=data.readInt();String id=data.readString();byte[] image=data.createByteArray();String type=data.readString();int power=data.readInt(),poll=data.readInt();submit(operation,id,image,type,power,poll);reply.writeNoException();return true;}
        stopAndWait();reply.writeNoException();return true;
    }
    private void start(IBinder token){
        synchronized(lock){if(active)throw new IllegalStateException("上一阅读会话尚未结束");active=true;stopping=false;logs.setLength(0);sequence=0;selectedType=null;
            try{
                owner=token;ownerDeath=()->new Thread(this::stopAndWait,"reader-owner-died").start();owner.linkToDeath(ownerDeath,0);
                workDir=new File("/data/local/tmp/suixinyin-reader-"+appUid+"-"+android.os.Process.myPid()+"-"+System.nanoTime());
                android.system.Os.mkdir(workDir.toString(),0700);stopFile=new File(workDir,"stop");
                ProcessBuilder builder=new ProcessBuilder("/system/bin/app_process","/system/bin","org.nfcresearch.qpolllab.ReaderPanelWorker","45",stopFile.toString(),workDir.toString(),NfcSessionLock.path(appUid).toString()).redirectErrorStream(true);builder.environment().put("CLASSPATH",apk);
                Process running=builder.start();child=running;commands=new BufferedWriter(new OutputStreamWriter(running.getOutputStream(),StandardCharsets.UTF_8));
                Thread reader=new Thread(()->{try(BufferedReader input=new BufferedReader(new InputStreamReader(running.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null)log(line);}catch(Exception error){log("QP|ERROR|读取工作进程："+error);}finally{try{log("QP|BRIDGE_END|阅读工作进程退出码="+running.waitFor());}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}synchronized(lock){if(child==running){cleanup();child=null;commands=null;active=false;stopping=false;}}}},"reader-panel-log");reader.setDaemon(true);reader.start();
            }catch(Exception error){
                Process failed=child;stopping=true;
                if(failed!=null){
                    try{if(stopFile!=null)stopFile.createNewFile();}catch(Exception ignored){}
                    failed.destroyForcibly();
                    try{failed.waitFor(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                }
                if(failed==null||!failed.isAlive()){cleanup();child=null;commands=null;active=false;stopping=false;}
                throw new IllegalStateException("无法启动阅读面板："+error,error);
            }
        }
    }
    private void submit(int operation,String id,byte[] image,String type,int power,int poll){
        if(operation!=1&&operation!=2)throw new IllegalArgumentException("阅读操作错误");if(id==null||!id.matches("[0-9]{1,19}"))throw new IllegalArgumentException("阅读命令编号错误");
        if(image==null||image.length!=105984)throw new IllegalArgumentException("图像长度错误");if(!"A0".equals(type)&&!"A1".equals(type))throw new IllegalArgumentException("请先选择实物A0/A1");
        if(power<100||power>12700||power%100!=0||poll<0||poll>500)throw new IllegalArgumentException("刷新参数错误");
        synchronized(lock){if(!active||stopping||commands==null||workDir==null)throw new IllegalStateException("阅读会话未就绪或正在停止");if(selectedType!=null&&!selectedType.equals(type))throw new IllegalStateException("会话中不能切换屏幕类型");selectedType=type;
            File[] queued=workDir.listFiles((dir,name)->name.endsWith(".bin"));if(queued!=null&&queued.length>=4)throw new IllegalStateException("页面队列已满，请等待当前页完成");
            try{File imageFile=new File(workDir,"frame-"+(++sequence)+".bin");try(FileOutputStream output=new FileOutputStream(imageFile)){output.write(image);}String sha=hex(MessageDigest.getInstance("SHA-256").digest(image));commands.write((operation==1?"PRELOAD":"ADVANCE")+"\t"+id+"\t"+imageFile+"\t"+sha+"\t"+type+"\t"+power+"\t"+poll+"\n");commands.flush();}catch(Exception error){throw new IllegalStateException("阅读命令提交失败",error);}
        }
    }
    /** Called only from Binder/background threads; a UI caller never blocks here. */
    public void shutdownFromHost(){stopAndWait();}
    private void stopAndWait(){
        final Process running; synchronized(lock){running=child;if(running==null){active=false;return;}stopping=true;try{if(stopFile!=null)stopFile.createNewFile();}catch(Exception error){log("QP|ERROR|创建停止标记失败："+error);}try{if(commands!=null){commands.write("STOP\n");commands.flush();commands.close();commands=null;}}catch(Exception ignored){}}
        try{if(!running.waitFor(12,TimeUnit.SECONDS)){running.destroy();if(!running.waitFor(3,TimeUnit.SECONDS))running.destroyForcibly();}running.waitFor(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        synchronized(lock){if(child==running&&!running.isAlive()){cleanup();child=null;commands=null;active=false;stopping=false;}}
    }
    private void cleanup(){
        if(owner!=null&&ownerDeath!=null)try{owner.unlinkToDeath(ownerDeath,0);}catch(Exception ignored){}owner=null;ownerDeath=null;
        File dir=workDir;if(dir!=null&&dir.isDirectory()){File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();dir.delete();}workDir=null;stopFile=null;
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte value:bytes)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
}

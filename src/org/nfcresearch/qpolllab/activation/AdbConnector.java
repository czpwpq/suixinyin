package org.nfcresearch.qpolllab.activation;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.PairingConnectionCtx;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Blocking primitives for the controller's worker thread. Only the local device is reachable. */
public final class AdbConnector implements Closeable {
    public static final String LOCAL_HOST="127.0.0.1";
    private final Context context;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"local-adb-timeout");t.setDaemon(true);return t;});
    private final CopyOnWriteArrayList<ShellHandle> streams=new CopyOnWriteArrayList<>();
    private volatile PairingConnectionCtx pairing;
    private volatile AdbConnection connection;
    public AdbConnector(Context context){this.context=context.getApplicationContext();}
    private void allowed()throws IOException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Run local activation on a worker thread");
        if(closed.get())throw new IOException("Local activation cancelled");
        if(Build.VERSION.SDK_INT<30)throw new IOException("App 内无线调试激活需要 Android 11 或更新版本");
    }
    public static int validatePort(int port){if(port<1||port>65535)throw new IllegalArgumentException("端口应为 1–65535");return port;}
    public boolean pair(int port,char[] pairingCode)throws Exception{
        allowed();validatePort(port);
        if(pairingCode==null||pairingCode.length!=6)throw new IllegalArgumentException("请输入六位配对码");
        byte[] password=new byte[6];for(int i=0;i<6;i++){if(pairingCode[i]<'0'||pairingCode[i]>'9'){Arrays.fill(password,(byte)0);throw new IllegalArgumentException("请输入六位数字配对码");}password[i]=(byte)pairingCode[i];}
        PairingConnectionCtx active=null;ScheduledFuture<?> timeout=null;AtomicBoolean expired=new AtomicBoolean();
        try{
            AdbIdentityMaterial identity=AdbIdentityStore.loadOrCreate(context);allowed();
            active=new PairingConnectionCtx(LOCAL_HOST,port,password,identity.privateKey,identity.certificate,"suixinyin");pairing=active;
            if(closed.get())throw new IOException("Local activation cancelled");
            final PairingConnectionCtx target=active;timeout=timer.schedule(()->{expired.set(true);target.close();},20,TimeUnit.SECONDS);
            active.start();if(expired.get())throw new IOException("配对超时，请保持系统配对码窗口开启后重试");allowed();return true;
        }finally{Arrays.fill(password,(byte)0);if(timeout!=null)timeout.cancel(false);if(active!=null)active.close();if(pairing==active)pairing=null;}
    }
    public boolean connect(int port)throws Exception{
        allowed();validatePort(port);if(isConnected())return true;
        AdbIdentityMaterial identity=AdbIdentityStore.loadOrCreate(context);allowed();
        AdbConnection active=AdbConnection.create(LOCAL_HOST,port,identity.privateKey,identity.certificate,Build.VERSION.SDK_INT);connection=active;
        active.setDeviceName("suixinyin");
        if(closed.get()){active.close();throw new IOException("Local activation cancelled");}
        ScheduledFuture<?> timeout=timer.schedule(()->{try{active.close();}catch(IOException ignored){}},15,TimeUnit.SECONDS);
        boolean success=false;
        try{success=active.connect(12,TimeUnit.SECONDS,true);allowed();if(!success)throw new IOException("连接无线调试超时，请使用无线调试首页的连接端口");return true;}
        finally{timeout.cancel(false);if(!success){active.close();if(connection==active)connection=null;}}
    }
    public boolean isConnected(){AdbConnection active=connection;return !closed.get()&&active!=null&&active.isConnected()&&active.isConnectionEstablished();}
    public ShellHandle openShell(String command)throws Exception{
        allowed();if(command==null||command.length()==0||command.length()>16384||command.indexOf('\u0000')>=0)throw new IllegalArgumentException("Invalid activation command");
        AdbConnection active=connection;if(!isConnected()||active==null)throw new IOException("无线调试尚未连接");
        ScheduledFuture<?> deadline=timer.schedule(this::close,17,TimeUnit.SECONDS);
        AdbStream stream;try{stream=active.open("shell:"+command);}finally{deadline.cancel(false);}
        ShellHandle handle=new ShellHandle(stream);streams.add(handle);
        if(closed.get()){handle.close();throw new IOException("Local activation cancelled");}return handle;
    }
    public String exec(String command,int maxBytes)throws Exception{
        allowed();if(maxBytes<1||maxBytes>1024*1024)throw new IllegalArgumentException("Invalid output limit");
        try(ShellHandle shell=openShell(command)){
            AtomicBoolean expired=new AtomicBoolean();ScheduledFuture<?> timeout=timer.schedule(()->{expired.set(true);close();},15,TimeUnit.SECONDS);
            try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=shell.input().read(buffer))!=-1){if(out.size()+n>maxBytes)throw new IOException("激活输出超过限制");out.write(buffer,0,n);}if(expired.get())throw new IOException("激活命令超时");return new String(out.toByteArray(),StandardCharsets.UTF_8);}
            finally{timeout.cancel(false);}
        }
    }
    public static void clearIdentity(Context context){AdbIdentityStore.clear(context.getApplicationContext());}
    /** Does not create a key or initiate pairing; also supports identities from v1.0.0. */
    public static boolean hasSavedIdentity(Context context){return AdbIdentityStore.exists(context.getApplicationContext());}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;PairingConnectionCtx p=pairing;pairing=null;if(p!=null)p.close();
        AdbConnection c=connection;connection=null;if(c!=null)try{c.close();}catch(IOException ignored){}
        for(ShellHandle stream:streams)stream.close();streams.clear();timer.shutdownNow();
    }
    public final class ShellHandle implements Closeable {
        private final AdbStream stream;private final InputStream input;private final OutputStream output;private final AtomicBoolean stopped=new AtomicBoolean();
        private ShellHandle(AdbStream stream){this.stream=stream;input=stream.openInputStream();output=stream.openOutputStream();}
        public InputStream input(){return input;}public OutputStream output(){return output;}
        @Override public void close(){if(stopped.compareAndSet(false,true)){try{stream.close();}catch(IOException ignored){}streams.remove(this);}}
    }
}

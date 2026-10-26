package org.nfcresearch.qpolllab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.nfcresearch.qpolllab.activation.ActivationInputs;
import org.nfcresearch.qpolllab.activation.AdbConnector;
import org.nfcresearch.qpolllab.activation.InternalAdbDiscovery;
import org.nfcresearch.qpolllab.activation.InternalBackend;
import org.nfcresearch.qpolllab.activation.NotificationPairingInput;
import org.nfcresearch.qpolllab.activation.ActivationStartupLog;

/** App-scoped pairing/activation and foreground recovery. No operation here sends an NFC command. */
public final class InternalActivationController {
    public interface Listener{void changed();}
    private static InternalActivationController instance;
    private static final String CHANNEL="internal-activation";
    static final String REMOTE_CODE="internal_pairing_code";
    static final int NOTIFICATION=21921;
    static final int ACTIVATION_NOTIFICATION=21924;
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"internal-activation");t.setDaemon(true);return t;});
    private WeakReference<Listener> listener=new WeakReference<>(null);
    private final StringBuilder log=new StringBuilder();
    private InternalAdbDiscovery discovery;private AdbConnector connector;private InternalBackend.Attempt attempt;
    private int operation,discoveryEpoch,pairPort,connectPort,activationStep;private boolean busy,notificationPairing,discovering;
    private String operationKind="",status="尚未激活；可先配对，再连接本机无线调试";
    private long notificationDeadline;
    private volatile ActivationStartupLog startupLog;
    private final InternalReconnect reconnect;
    private boolean automaticActivation;
    public static synchronized InternalActivationController get(Context context){if(instance==null)instance=new InternalActivationController(context.getApplicationContext());return instance;}
    private InternalActivationController(Context context){this.context=context;reconnect=new InternalReconnect(context,this);connectPort=reconnect.savedPort();if(reconnect.hasIdentity())status="已有本机连接记录；重新打开时会尝试恢复，无需重复输入配对码";}
    public void attach(Listener target){listener=new WeakReference<>(target);changed();}
    public void detach(Listener target){if(listener.get()==target)listener.clear();}
    public boolean isBusy(){return busy||InternalBackend.isStopping()||reconnect.running();}public boolean isDiscovering(){return discovering;}
    boolean operationBusy(){return busy||notificationPairing()||InternalBackend.isStopping();}
    public boolean hasSavedIdentity(){return reconnect.hasIdentity();}
    public boolean needsPairing(){return reconnect.needsPairing();}
    public boolean isAutoRestoring(){return reconnect.running();}
    public int savedConnectionPort(){return reconnect.savedPort();}
    public boolean reconnectSaved(){return reconnect.retry();}
    public void foregroundStarted(Object host){reconnect.enter(host);}
    public void foregroundStopped(Object host){reconnect.leave(host);}
    public void selectedBackendChanged(){reconnect.backendChanged();}
    void reconnectStatus(String text){status(text);}
    void reconnectDiagnostic(String text){diagnostic(text);}
    void discoveredConnectionPort(int port){connectPort=port;}
    void cancelAutomaticActivation(){if(automaticActivation)cancelPending("自动连接已取消；配对信息保留");}
    public boolean isActivating(){return busy&&"activate".equals(operationKind);}
    public void cancelActivation(){if(isActivating())cancelPending("用户已取消本次激活；未发送任何图片");}
    void activationServiceEnded(String message){if(isActivating())cancelPending(message);}
    public boolean notificationPairing(){return notificationPairing&&SystemClock.elapsedRealtime()<notificationDeadline;}
    public int pairingPort(){return pairPort;}public int connectionPort(){return connectPort;}
    public String status(){return status;}
    public boolean ready(){return InternalBackend.isReady();}
    public String diagnostics(){ActivationStartupLog start=startupLog;return "随心印内置激活（本机无线ADB）\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n进程UID："+android.os.Process.myUid()+"\n状态："+status+"\n已有本机身份："+hasSavedIdentity()+"；自动恢复中："+isAutoRestoring()+"；需重新配对："+needsPairing()+"\n上次核验成功的连接端口："+savedConnectionPort()+"\n内置后端："+InternalBackend.status()+"\n\n"+log+(start==null?"":"\n启动阶段（已脱敏）：\n"+start.diagnostics());}
    private void changed(){Listener target=listener.get();if(target!=null)target.changed();}
    private void status(String text){status=text;log.append(SystemClock.elapsedRealtime()).append(" · ").append(text).append('\n');if(log.length()>12000)log.delete(0,log.indexOf("\n",log.length()-9000)+1);changed();if(isActivating())updateActivationNotification();}
    private void activationStatus(int id,int step,String text){main.post(()->{if(id==operation&&isActivating()&&step>=activationStep){activationStep=step;status(text);}});}
    private void diagnostic(String text){String safe=String.valueOf(text).replace('\n',' ').replace('\r',' ');if(safe.length()>400)safe=safe.substring(0,400);log.append(SystemClock.elapsedRealtime()).append(" · ").append(safe).append('\n');if(log.length()>12000)log.delete(0,log.indexOf("\n",log.length()-9000)+1);}
    public void discover(){if(!supported()||busy)return;reconnect.manualStarted();stopDiscovery();notificationPairing=false;InternalPairingService.finish(context);cancelNotification();startDiscovery();}
    private void startDiscovery(){
        pairPort=connectPort=0;discovering=true;final int epoch=++discoveryEpoch;
        discovery=new InternalAdbDiscovery(context,new InternalAdbDiscovery.Listener(){
            public void found(boolean pairing,int port){if(epoch!=discoveryEpoch)return;if(pairing)pairPort=port;else connectPort=port;status(pairing?"已发现本机配对端口，可输入6位配对码":"已发现本机连接端口，可点击激活");if(notificationPairing())showPairNotification();}
            public void status(String text){if(epoch==discoveryEpoch)InternalActivationController.this.status(text);}
            public void diagnostic(String text){if(epoch==discoveryEpoch)InternalActivationController.this.diagnostic(text);}
            public void lost(boolean pairing,int port){if(epoch!=discoveryEpoch)return;if(pairing&&pairPort==port){pairPort=0;status("配对端口已失效，可在通知里手动填写当前端口与6位码");if(notificationPairing())showPairNotification();}else if(!pairing&&connectPort==port){connectPort=0;status("连接端口已更新，请重新发现或手动填写");}}
        });
        status("正在查找本机无线调试服务；未发现时可手动填写端口");discovery.start();
        main.postDelayed(()->{if(epoch==discoveryEpoch){boolean wasNotification=notificationPairing;stopDiscovery();notificationPairing=false;if(wasNotification){InternalPairingService.finish(context);cancelNotification();}status("服务发现已结束；可重新发现或手动填写当前端口");}},120000);
    }
    public void notificationPairingStart(){
        if(!busy)reconnect.manualStarted();
        if(!supported()||busy)return;NotificationManager notices=notifications();if(notices==null||!notices.areNotificationsEnabled()||(Build.VERSION.SDK_INT>=26&&notices.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)){status("配对通知已关闭，请允许本App的通知，或使用分屏手动配对");return;}stopDiscovery();notificationPairing=true;notificationDeadline=SystemClock.elapsedRealtime()+120000;
        try{InternalPairingService.begin(context);startDiscovery();showPairNotification();}catch(RuntimeException failed){notificationPairing=false;InternalPairingService.finish(context);cancelNotification();status("无法启动通知配对，请保持本页前台后重试");}
    }
    private void stopDiscovery(){discoveryEpoch++;discovering=false;if(discovery!=null)discovery.close();discovery=null;}
    public void pageStopped(){
        if(busy&&!isActivating()&&!notificationPairing)cancelPending("页面已离开，本次配对已取消");
        if(!notificationPairing())stopDiscovery();
    }
    public void pair(int port,char[] code){pair(port,code,false,null);}
    public void pairFromNotification(CharSequence text,Runnable completion){
        if(busy){if(completion!=null)completion.run();return;}
        if(!notificationPairing()){notificationPairing=false;stopDiscovery();cancelNotification();InternalPairingService.finish(context);status("通知配对已过期，请回到App重新开始");if(completion!=null)completion.run();return;}
        try(NotificationPairingInput.Reply reply=NotificationPairingInput.parse(text,pairPort)){pairPort=reply.port;pair(reply.port,reply.code,true,completion);}
        catch(IllegalArgumentException invalid){status(invalid.getMessage());showPairNotification();if(completion!=null)completion.run();}
    }
    void notificationServiceEnded(String message){if(notificationPairing||busy&&"pair".equals(operationKind))cancelPending(message);}
    private void pair(int port,char[] code,boolean fromNotification,Runnable completion){
        try{ActivationInputs.port(Integer.toString(port));ActivationInputs.pairingCode(code);}catch(IllegalArgumentException invalid){ActivationInputs.clear(code);status(invalid.getMessage());if(completion!=null)completion.run();return;}
        if(!supported()||busy){ActivationInputs.clear(code);if(completion!=null)completion.run();return;}
        reconnect.manualStarted();
        stopDiscovery();if(!fromNotification){notificationPairing=false;InternalPairingService.finish(context);cancelNotification();}
        final int id=++operation;busy=true;operationKind="pair";final char[] owned=code.clone();ActivationInputs.clear(code);
        final AdbConnector current=new AdbConnector(context);connector=current;status("正在与本机配对，请保持无线调试的配对窗口打开");if(fromNotification)showProgressNotification("正在配对，请保持设置中的配对窗口打开");
        worker.execute(()->{
            boolean success=false;String failure=null;
            try{success=current.pair(port,owned);if(!success)failure="配对未成功，请重新打开配对窗口并核对配对端口和6位码";}catch(Throwable error){failure="配对未完成（"+ActivationInputs.errorLabel(error)+"），请重新获取配对码和配对端口";}finally{ActivationInputs.clear(owned);close(current);}
            final boolean paired=success;final String message=failure;
            main.post(()->{try{if(id!=operation)return;connector=null;busy=false;operationKind="";notificationPairing=false;InternalPairingService.finish(context);if(paired){reconnect.paired();status("配对成功。请使用无线调试主页的连接端口，点击激活");if(fromNotification)showDoneNotification();}else{cancelNotification();status(message);}}finally{if(completion!=null)completion.run();}});
        });
        timeout(id,22000,completion);
    }
    public void activate(int port){
        if(busy)return;reconnect.manualStarted();reconnect.enable();activate(port,false);
    }
    void activateAutomatically(int port){activate(port,true);}
    private void activate(int port,boolean automatic){
        if(InternalBackend.isStopping()){status("正在停止上一内置服务，请等待 NFC 会话释放后再激活");return;}
        if(!supported()||busy)return;if(InternalBackend.isReady()){status("内置服务已激活；可返回照片或阅读页使用");return;}
        try{ActivationInputs.port(Integer.toString(port));}catch(IllegalArgumentException error){status(error.getMessage());return;}
        stopDiscovery();notificationPairing=false;InternalPairingService.finish(context);cancelNotification();final int id=++operation;busy=true;operationKind="activate";automaticActivation=automatic;
        final AdbConnector current=new AdbConnector(context);connector=current;final ActivationStartupLog start=new ActivationStartupLog();startupLog=start;activationStep=1;status("1/5 正在连接本机无线ADB；尚未激活");
        try{InternalActivationService.begin(context);}catch(RuntimeException blocked){cancelPending("无法启动激活前台服务，请保持App前台并重新操作");return;}
        worker.execute(()->{
            InternalBackend.Attempt pending=null;AdbConnector.ShellHandle shell=null;boolean activated=false,authenticationRejected=false;String failure=null;
            try{
                if(!current.connect(port))throw new java.io.IOException("connect failed");
                activationStatus(id,2,"2/5 无线ADB已连接，正在启动授权服务；请继续等待");
                pending=InternalBackend.prepare(context);final InternalBackend.Attempt prepared=pending;
                synchronized(this){if(id!=operation)throw new InterruptedException();attempt=prepared;}
                shell=current.openShell(pending.command);final InputStream stream=shell.input();
                Thread drain=new Thread(()->{try{drainStartup(stream,line->{start.acceptLine(prepared.sanitizeOutput(line));activationStatus(id,3,"3/5 启动核验 · "+start.status());});}catch(Exception ignored){}},"activation-bootstrap-output");drain.setDaemon(true);drain.start();
                boolean handed=pending.await(25000);
                if(handed){activationStatus(id,4,"4/5 授权服务已交付，正在断开ADB并复验；尚未完成");shell.close();shell=null;close(current);Thread.sleep(500);InternalBackend.verifyReady(context);start.acceptLine("ACT|STAGE|DETACHED_VERIFIED");activationStatus(id,5,"5/5 身份复验通过，正在完成激活");activated=true;}
                else failure="授权服务未就绪："+start.status()+"。请复制激活诊断；未执行刷屏";
            }catch(Throwable error){authenticationRejected=pairingRejected(error);if(pending!=null)start.acceptLine("ACT|ERROR|"+pending.sanitizeOutput(error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage())));failure="激活未完成 · "+start.status()+"（"+ActivationInputs.errorLabel(error)+"）。请复制激活诊断";}
            finally{if(!activated&&pending!=null)pending.cancel();if(shell!=null)try{shell.close();}catch(Exception ignored){}close(current);}
            final boolean success=activated,rejected=authenticationRejected;final String message=failure;final InternalBackend.Attempt finished=pending;
            main.post(()->{if(id!=operation){if(success&&finished!=null)finished.cancel();return;}attempt=null;connector=null;busy=false;operationKind="";automaticActivation=false;InternalActivationService.finish(context);if(success&&InternalBackend.isReady()){connectPort=port;reconnect.connected(port);BackendMode.set(context,BackendMode.INTERNAL);status(automatic?"内置服务已自动恢复，可以使用；未自动刷屏":"内置服务已激活。可以返回照片或阅读页使用");}else{if(rejected&&!automatic)reconnect.requirePairing();status(rejected&&!automatic?"系统未接受已保存的身份，请重新配对；本机身份未被自动删除":message==null?"服务已断开，请重新连接":message);if(automatic)reconnect.failed(rejected,message);}updateActivationNotification();});
        });
        timeout(id,65000,null);
    }
    private void timeout(int id,long millis,Runnable completion){main.postDelayed(()->{if(id==operation&&busy){cancelPending("操作超时，已停止本次连接；请核对当前端口后重试");if(completion!=null)completion.run();}},millis);}
    private static void drainStartup(InputStream stream,java.util.function.Consumer<String> sink)throws java.io.IOException{
        java.io.Reader reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8);char[] block=new char[512];StringBuilder line=new StringBuilder();int seen=0,count;boolean overflow=false;
        while((count=reader.read(block))!=-1){for(int i=0;i<count;i++){char c=block[i];if(c=='\n'){if(seen<=65536)sink.accept(overflow?"启动输出行过长，已隐藏":line.toString());line.setLength(0);overflow=false;}else if(seen<=65536){if(line.length()<2048)line.append(c);else overflow=true;}seen=Math.min(65537,seen+1);}}
        if(line.length()>0&&seen<=65536)sink.accept(overflow?"启动输出行过长，已隐藏":line.toString());
    }
    private static boolean pairingRejected(Throwable error){for(int i=0;error!=null&&i<8;i++,error=error.getCause())if(error instanceof io.github.muntashirakon.adb.AdbAuthenticationFailedException||error instanceof io.github.muntashirakon.adb.AdbPairingRequiredException)return true;return false;}
    public void stop(){reconnect.pause();cancelPending("已停止本次连接与服务发现");InternalBackend.stop();if(InternalBackend.isStopping()){status("正在停止内置服务并释放 NFC，请稍候");watchStop(operation,0);}else status(InternalBackend.status()+"；自动恢复已暂停，可手动点重新连接");}
    private void watchStop(final int id,final int count){main.postDelayed(()->{if(id!=operation)return;if(!InternalBackend.isStopping())status(InternalBackend.status());else if(count<700)watchStop(id,count+1);else status("内置服务仍在释放，请稍后查看诊断");},500);}
    private void cancelPending(String text){
        reconnect.cancel(false,null);
        final AdbConnector old;final InternalBackend.Attempt pending;synchronized(this){operation++;old=connector;pending=attempt;connector=null;attempt=null;busy=false;operationKind="";automaticActivation=false;}
        notificationPairing=false;stopDiscovery();InternalPairingService.finish(context);InternalActivationService.finish(context);cancelNotification();NotificationManager manager=notifications();if(manager!=null)manager.cancel(ACTIVATION_NOTIFICATION);
        Thread close=new Thread(()->{if(pending!=null)pending.cancel();close(old);},"activation-cancel");close.setDaemon(true);close.start();status(text);
    }
    private boolean supported(){if(Build.VERSION.SDK_INT>=30)return true;status("内置无线激活需要Android11或更高版本，请使用外置Shizuku或Root方式");return false;}
    private static void close(AdbConnector value){if(value!=null)try{value.close();}catch(Exception ignored){}}
    private NotificationManager notifications(){NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null&&Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"随心印无线配对",NotificationManager.IMPORTANCE_DEFAULT));return manager;}
    private PendingIntent openPage(){Intent intent=new Intent(context,InternalActivationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(context,21921,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification.Builder baseNotification(String title,String message){return new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(openPage()).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS);}
    Notification buildActivationNotification(){notifications();return baseNotification(isActivating()?"随心印 · 激活尚未完成":ready()?"随心印 · 内置服务已激活":"随心印 · 激活未完成",status).setOngoing(isActivating()).setAutoCancel(!isActivating()).setTimeoutAfter(isActivating()?75000:120000).build();}
    private void updateActivationNotification(){NotificationManager manager=notifications();if(manager!=null)try{manager.notify(ACTIVATION_NOTIFICATION,buildActivationNotification());}catch(SecurityException ignored){}}
    Notification buildPairNotification(){
        notifications();String message=pairPort>0?"已找到配对端口，展开通知后点下方“输入配对码”。保持系统配对弹窗打开。":"自动发现尚未完成，也可点下方输入“端口 空格 6位码”，例如37123 012345。";
        Notification.Builder builder=baseNotification("随心印 · 通知栏配对",message).setContentIntent(null).setOngoing(true).setCategory(Notification.CATEGORY_MESSAGE).setTimeoutAfter(145000);
        Intent intent=new Intent(context,InternalPairingService.class).setAction(InternalPairingService.INPUT);int mutable=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
        PendingIntent reply=PendingIntent.getForegroundService(context,21922,intent,PendingIntent.FLAG_UPDATE_CURRENT|mutable);
        RemoteInput input=new RemoteInput.Builder(REMOTE_CODE).setLabel(pairPort>0?"6位配对码":"端口 空格 6位码").build();
        Notification.Action.Builder replyAction=new Notification.Action.Builder(android.R.drawable.ic_menu_edit,pairPort>0?"输入配对码":"输入端口和配对码",reply).addRemoteInput(input).setAllowGeneratedReplies(false);if(Build.VERSION.SDK_INT>=28)replyAction.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY);builder.addAction(replyAction.build());
        PendingIntent settings=PendingIntent.getActivity(context,21923,WirelessDebugSettings.intent(context),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_preferences,"打开无线调试设置",settings).build());return builder.build();
    }
    private void showPairNotification(){
        NotificationManager manager=notifications();if(manager==null)return;
        try{manager.notify(NOTIFICATION,buildPairNotification());}catch(SecurityException ignored){notificationPairing=false;stopDiscovery();InternalPairingService.finish(context);status("通知权限未开启，请允许通知或使用手动配对");}
    }
    private void showProgressNotification(String text){NotificationManager manager=notifications();if(manager!=null)try{manager.notify(NOTIFICATION,baseNotification("随心印 · 正在配对",text).setOngoing(true).setTimeoutAfter(25000).build());}catch(SecurityException ignored){}}
    private void showDoneNotification(){NotificationManager manager=notifications();if(manager!=null)try{manager.notify(NOTIFICATION,baseNotification("随心印 · 配对成功","返回随心印，使用连接端口完成激活；尚未刷屏").setAutoCancel(true).build());}catch(SecurityException ignored){}}
    private void cancelNotification(){NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancel(NOTIFICATION);}
}

package org.nfcresearch.qpolllab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Set;
import org.nfcresearch.qpolllab.activation.AdbConnector;
import org.nfcresearch.qpolllab.activation.InternalAdbDiscovery;
import org.nfcresearch.qpolllab.activation.InternalBackend;
import org.nfcresearch.qpolllab.activation.ReconnectPlan;

/** Foreground-only recovery of an existing local identity. Never pairs or starts NFC. */
final class InternalReconnect {
    private final Context context;
    private final InternalActivationController controller;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Object> hosts=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private final Set<Integer> discoveredPorts=new HashSet<>();
    private final Runnable background=this::background;
    private InternalAdbDiscovery discovery;
    private ReconnectPlan plan;
    private int epoch;
    private boolean running,attemptedThisForeground,waitingForStop;
    private long discoveryUntil;
    private String lastFailure="";
    private boolean lastPortWasDiscovered,freshAuthenticationRejected;

    InternalReconnect(Context context,InternalActivationController controller){this.context=context;this.controller=controller;}
    private SharedPreferences prefs(){return context.getSharedPreferences("internal-activation",Context.MODE_PRIVATE);}
    boolean hasIdentity(){return AdbConnector.hasSavedIdentity(context);}
    boolean needsPairing(){return prefs().getBoolean("pairingRequired",false);}
    int savedPort(){int port=prefs().getInt("lastConnectionPort",0);return port>0&&port<=65535?port:0;}
    boolean running(){return running;}

    void enter(Object host){
        hosts.add(host);main.removeCallbacks(background);
        if(!BackendMode.INTERNAL.equals(BackendMode.get(context))){cancel(true,null);return;}
        if(running){final int generation=epoch;main.post(()->next(generation));return;}
        if(!attemptedThisForeground&&!start(false)&&InternalBackend.isStopping()&&!waitingForStop){
            waitingForStop=true;waitForStop(epoch,SystemClock.elapsedRealtime()+10000);
        }
    }
    void leave(Object host){hosts.remove(host);if(hosts.isEmpty())main.postDelayed(background,800);}
    private void background(){
        if(!hosts.isEmpty())return;
        attemptedThisForeground=false;
        cancel(true,"已离开应用，自动连接已暂停；下次打开时会重试，配对信息保留");
    }
    private void waitForStop(int generation,long deadline){
        if(generation!=epoch||hosts.isEmpty()){waitingForStop=false;return;}
        if(start(false)||!InternalBackend.isStopping()||SystemClock.elapsedRealtime()>=deadline){waitingForStop=false;return;}
        main.postDelayed(()->waitForStop(generation,deadline),250);
    }
    void backendChanged(){
        if(!BackendMode.INTERNAL.equals(BackendMode.get(context))){cancel(true,null);attemptedThisForeground=false;}
        else if(!hosts.isEmpty())start(false);
    }
    boolean retry(){
        if(running)return true;
        if(!hasIdentity()||needsPairing()||hosts.isEmpty())return false;
        prefs().edit().putBoolean("reconnectPaused",false).apply();
        attemptedThisForeground=false;
        return start(true);
    }
    private boolean start(boolean explicit){
        if(running)return true;
        if((!explicit&&attemptedThisForeground)||needsPairing()||hosts.isEmpty()
                ||!ReconnectPlan.eligible(BackendMode.INTERNAL.equals(BackendMode.get(context)),Build.VERSION.SDK_INT>=30,
                    hasIdentity(),prefs().getBoolean("reconnectPaused",false),InternalBackend.isReady(),controller.operationBusy()))return false;
        attemptedThisForeground=true;running=true;lastFailure="";discoveredPorts.clear();freshAuthenticationRejected=false;
        plan=new ReconnectPlan(savedPort());discoveryUntil=SystemClock.elapsedRealtime()+12000;
        final int generation=++epoch;
        controller.reconnectStatus("已有本机连接记录，正在自动查找无线调试并恢复；无需重新输入配对码");
        discovery=new InternalAdbDiscovery(context,new InternalAdbDiscovery.Listener(){
            public void found(boolean pairing,int port){
                if(!current(generation)||pairing)return;
                if(port>0&&port<=65535&&discoveredPorts.size()<8)discoveredPorts.add(port);
                plan.offerDiscovered(port);controller.discoveredConnectionPort(port);next(generation);
            }
            public void status(String text){if(current(generation))controller.reconnectDiagnostic(text);}
            public void diagnostic(String text){if(current(generation))controller.reconnectDiagnostic(text);}
        });
        discovery.start();
        // Give a newly advertised port priority; the last verified port is only a fallback.
        main.postDelayed(()->next(generation),1200);
        main.postDelayed(()->next(generation),12000);
        main.postDelayed(()->{if(current(generation))cancel(true,"自动连接等待超时；配对信息仍保留，请确认无线调试已开启后点重新连接");},70000);
        return true;
    }
    private boolean current(int generation){return running&&epoch==generation&&plan!=null;}
    private void next(int generation){
        if(!current(generation))return;
        if(!BackendMode.INTERNAL.equals(BackendMode.get(context))){cancel(true,null);return;}
        // A host hand-off has an 800ms grace period; only background() ends the round.
        if(hosts.isEmpty())return;
        if(controller.operationBusy()){main.postDelayed(()->next(generation),300);return;}
        int port=plan.nextPort();
        if(port>0){
            lastPortWasDiscovered=discoveredPorts.contains(port);
            controller.reconnectStatus("正在使用已保存的配对信息恢复连接 · 第"+plan.attemptCount()+"次；不会自动刷屏");
            controller.activateAutomatically(port);return;
        }
        if(!plan.isActive()||SystemClock.elapsedRealtime()>=discoveryUntil){
            if(freshAuthenticationRejected){
                requirePairing();cancel(false,null);controller.reconnectStatus("系统未接受当前无线调试服务的配对身份，请重新配对；本机身份未被自动删除");return;
            }
            String message="配对信息已保留，暂未连接。请确认系统无线调试已开启，再点重新连接；不必重新配对";
            if(!lastFailure.isEmpty())controller.reconnectDiagnostic(lastFailure);
            cancel(false,null);controller.reconnectStatus(message);
        }
    }
    void failed(boolean authenticationRejected,String message){
        if(!running)return;
        lastFailure=message==null?"连接未完成":message;
        // An old cached port can have been reassigned to an unrelated TLS listener.
        // Try fresh advertised endpoints before treating authentication as revoked.
        if(authenticationRejected&&lastPortWasDiscovered)freshAuthenticationRejected=true;
        final int generation=epoch;main.postDelayed(()->next(generation),350);
    }
    void paired(){prefs().edit().putBoolean("pairingRequired",false).putBoolean("reconnectPaused",false).apply();}
    void requirePairing(){prefs().edit().putBoolean("pairingRequired",true).apply();}
    void enable(){prefs().edit().putBoolean("reconnectPaused",false).apply();}
    void connected(int port){
        prefs().edit().putInt("lastConnectionPort",port).putBoolean("pairingRequired",false).putBoolean("reconnectPaused",false).apply();
        cancel(false,null);
    }
    void manualStarted(){cancel(false,null);attemptedThisForeground=true;}
    void pause(){prefs().edit().putBoolean("reconnectPaused",true).apply();cancel(false,null);}
    void cancel(boolean abortActivation,String message){
        boolean wasRunning=running;running=false;waitingForStop=false;epoch++;
        if(plan!=null)plan.cancel();plan=null;
        if(discovery!=null)discovery.close();discovery=null;
        if(abortActivation)controller.cancelAutomaticActivation();
        if(wasRunning&&message!=null)controller.reconnectStatus(message);
    }
}

package org.nfcresearch.qpolllab;
import android.app.Notification;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
/** Explicit, bounded foreground pairing; never activates the backend or starts NFC. */
public final class InternalPairingService extends Service {
    static final String START="org.nfcresearch.qpolllab.easy.START_PAIR_NOTIFICATION",INPUT="org.nfcresearch.qpolllab.easy.INPUT_PAIR_NOTIFICATION";
    private static InternalPairingService current;
    private final Handler main=new Handler(Looper.getMainLooper());private boolean finishing;private int startId;
    private final Runnable deadline=()->expire("通知配对已超时，请重新开始");
    static void begin(Context context){context.startForegroundService(new Intent(context,InternalPairingService.class).setAction(START));}
    static void finish(Context context){InternalPairingService service=current;if(service!=null)service.finishSession();else context.stopService(new Intent(context,InternalPairingService.class));}
    @Override public void onCreate(){super.onCreate();current=this;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startId=id;InternalActivationController controller=InternalActivationController.get(this);
        if(intent==null||(!START.equals(intent.getAction())&&!INPUT.equals(intent.getAction()))||!controller.notificationPairing()){finishSession();return START_NOT_STICKY;}
        finishing=false;
        try{Notification notification=controller.buildPairNotification();if(Build.VERSION.SDK_INT>=34)startForeground(InternalActivationController.NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);else if(Build.VERSION.SDK_INT>=29)startForeground(InternalActivationController.NOTIFICATION,notification,0);else startForeground(InternalActivationController.NOTIFICATION,notification);}
        catch(RuntimeException failed){expire("无法启动配对前台服务，请返回App重新开始");return START_NOT_STICKY;}
        main.removeCallbacks(deadline);main.postDelayed(deadline,145000);
        if(INPUT.equals(intent.getAction())){Bundle values=RemoteInput.getResultsFromIntent(intent);CharSequence value=values==null?null:values.getCharSequence(InternalActivationController.REMOTE_CODE);controller.pairFromNotification(value,null);if(values!=null)values.clear();}
        return START_NOT_STICKY;
    }
    private void expire(String reason){InternalActivationController.get(this).notificationServiceEnded(reason);finishSession();}
    private void finishSession(){finishing=true;main.removeCallbacks(deadline);stopForeground(STOP_FOREGROUND_DETACH);stopSelfResult(startId);}
    @Override public void onDestroy(){main.removeCallbacks(deadline);if(current==this)current=null;if(!finishing)InternalActivationController.get(this).notificationServiceEnded("配对服务已停止，请回App重新开始");super.onDestroy();}
    @Override public void onTimeout(int id){expire("配对前台服务超时，已停止");}
    public void onTimeout(int id,int type){expire("配对前台服务超时，已停止");}
    @Override public IBinder onBind(Intent intent){return null;}
}

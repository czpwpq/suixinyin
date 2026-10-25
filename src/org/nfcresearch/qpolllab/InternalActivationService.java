package org.nfcresearch.qpolllab;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps an explicitly requested activation alive while its bounded handshake completes. */
public final class InternalActivationService extends Service {
    private static InternalActivationService current;
    private final Handler main=new Handler(Looper.getMainLooper());private boolean finishing;private int startId;
    private final Runnable deadline=()->expire("激活服务等待超时，已取消；请复制启动诊断");
    static void begin(Context context){context.startForegroundService(new Intent(context,InternalActivationService.class));}
    static void finish(Context context){InternalActivationService service=current;if(service!=null)service.finishSession();else context.stopService(new Intent(context,InternalActivationService.class));}
    @Override public void onCreate(){super.onCreate();current=this;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startId=id;InternalActivationController controller=InternalActivationController.get(this);
        if(!controller.isActivating()){finishSession();return START_NOT_STICKY;}
        finishing=false;
        try{if(Build.VERSION.SDK_INT>=34)startForeground(InternalActivationController.ACTIVATION_NOTIFICATION,controller.buildActivationNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);else if(Build.VERSION.SDK_INT>=29)startForeground(InternalActivationController.ACTIVATION_NOTIFICATION,controller.buildActivationNotification(),0);else startForeground(InternalActivationController.ACTIVATION_NOTIFICATION,controller.buildActivationNotification());}
        catch(RuntimeException error){expire("激活前台服务无法启动，请保持App前台并查看诊断");return START_NOT_STICKY;}
        main.removeCallbacks(deadline);main.postDelayed(deadline,70000);return START_NOT_STICKY;
    }
    private void expire(String reason){InternalActivationController.get(this).activationServiceEnded(reason);finishSession();}
    private void finishSession(){finishing=true;main.removeCallbacks(deadline);stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId);}
    @Override public void onDestroy(){main.removeCallbacks(deadline);if(current==this)current=null;if(!finishing)InternalActivationController.get(this).activationServiceEnded("激活服务被停止，请重新操作并查看诊断");super.onDestroy();}
    @Override public void onTimeout(int id){expire("激活服务超时，已取消");}
    public void onTimeout(int id,int type){expire("激活服务超时，已取消");}
    @Override public IBinder onBind(Intent intent){return null;}
}

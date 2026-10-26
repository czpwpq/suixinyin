package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.widget.EditText;
import java.lang.reflect.Field;

/** Device-only page/manifest checks. Does not request notification permission, pair, activate or send NFC. */
public final class InternalActivationNativeChecks {
    public static void run(Instrumentation instrumentation)throws Exception{
        Context context=instrumentation.getTargetContext();
        ActivityInfo activity=context.getPackageManager().getActivityInfo(new ComponentName(context,InternalActivationActivity.class),0);
        ActivityInfo receiver=context.getPackageManager().getReceiverInfo(new ComponentName(context,InternalPairingReceiver.class),0);
        check(!activity.exported&&!receiver.exported,"Activation UI or private pairing receiver exported");
        ServiceInfo service=context.getPackageManager().getServiceInfo(new ComponentName(context,InternalPairingService.class),0);
        check(!service.exported,"Notification pairing service exported");
        if(Build.VERSION.SDK_INT>=34)check((service.getForegroundServiceType()&ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)!=0,"Pairing foreground service lacks shortService type");
        PackageInfo declared=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
        check(declared.requestedPermissions!=null&&java.util.Arrays.asList(declared.requestedPermissions).contains("android.permission.FOREGROUND_SERVICE"),"Foreground-service permission missing");
        try{new org.nfcresearch.qpolllab.activation.InternalBridgeProvider().call("deliver",null,new android.os.Bundle());throw new AssertionError("Ordinary application UID can deliver privileged binders");}catch(SecurityException expected){}
        if(!org.nfcresearch.qpolllab.activation.InternalBackend.isReady()&&!org.nfcresearch.qpolllab.activation.InternalBackend.isStopping()){
            org.nfcresearch.qpolllab.activation.InternalBackend.Attempt first=org.nfcresearch.qpolllab.activation.InternalBackend.prepare(context);
            org.nfcresearch.qpolllab.activation.InternalBackend.Attempt second=org.nfcresearch.qpolllab.activation.InternalBackend.prepare(context);
            try{try{first.await(0);throw new AssertionError("Replaced activation request still valid");}catch(java.util.concurrent.CancellationException expected){}check(!second.await(0),"Zero-wait activation claimed success without a delivery");}
            finally{first.cancel();second.cancel();}
        }
        // UI checks must not contact ADB on a phone that already has a saved identity.
        // Use the same persisted pause as the explicit Stop action, restoring it below.
        SharedPreferences recovery=context.getSharedPreferences("internal-activation",Context.MODE_PRIVATE);
        boolean hadPause=recovery.contains("reconnectPaused"),oldPause=recovery.getBoolean("reconnectPaused",false);
        check(recovery.edit().putBoolean("reconnectPaused",true).commit(),"Unable to pause automatic recovery for UI checks");
        Activity page=null;
        try{
            page=instrumentation.startActivitySync(new Intent(context,InternalActivationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity checkedPage=page;
            final Throwable[] failed=new Throwable[1];instrumentation.runOnMainSync(()->{try{
                if(Build.VERSION.SDK_INT<30)return;
                EditText code=(EditText)read(checkedPage,"code");check(!code.isSaveEnabled(),"Pairing code can enter automatic saved state");
                code.setText("1234567");check(code.getText().length()==6,"Pairing code length not limited");code.setText("");
                InternalActivationController controller=InternalActivationController.get(context);check(!controller.isBusy(),"Paused activation page started a network/activation operation");
                savedConnectionUi((InternalActivationActivity)checkedPage,controller);
                notificationAndSettings(context,controller);
                fixedStatusAndGuardedBack((InternalActivationActivity)checkedPage,controller);
            }catch(Throwable error){failed[0]=error;}});
            if(failed[0]!=null)throw new AssertionError(failed[0]);
        }finally{
            if(page!=null){final Activity ending=page;instrumentation.runOnMainSync(ending::finish);instrumentation.waitForIdleSync();}
            SharedPreferences.Editor restore=recovery.edit();if(hadPause)restore.putBoolean("reconnectPaused",oldPause);else restore.remove("reconnectPaused");check(restore.commit(),"Unable to restore automatic-recovery pause preference");
        }
    }
    private static void savedConnectionUi(InternalActivationActivity page,InternalActivationController controller)throws Exception{
        android.view.View saved=(android.view.View)read(page,"savedConnectionCard"),first=(android.view.View)read(page,"firstSetup"),toggle=(android.view.View)read(page,"firstSetupToggle");
        android.widget.Button reconnect=(android.widget.Button)read(page,"reconnect");
        check(controller.ready()||reconnect.getText().toString().contains("无需配对码"),"Saved connection action implies pairing must be repeated");
        java.lang.reflect.Method layout=InternalActivationActivity.class.getDeclaredMethod("updateSetupVisibility",boolean.class,boolean.class);layout.setAccessible(true);
        Field expanded=InternalActivationActivity.class.getDeclaredField("pairingExpanded"),chosen=InternalActivationActivity.class.getDeclaredField("pairingChoiceMade");expanded.setAccessible(true);chosen.setAccessible(true);boolean oldExpanded=expanded.getBoolean(page),oldChosen=chosen.getBoolean(page);
        EditText port=(EditText)read(page,"connectionPort");String originalPort=port.getText().toString();Field last=InternalActivationActivity.class.getDeclaredField("lastConnectPort");last.setAccessible(true);int oldLast=last.getInt(page);
        try{
            chosen.setBoolean(page,false);layout.invoke(page,true,false);
            check(saved.getVisibility()==android.view.View.VISIBLE&&first.getVisibility()==android.view.View.GONE&&toggle.getVisibility()==android.view.View.VISIBLE,"Saved identity does not collapse initial pairing steps");
            expanded.setBoolean(page,true);chosen.setBoolean(page,true);layout.invoke(page,true,false);check(first.getVisibility()==android.view.View.VISIBLE,"Explicit pairing expansion is discarded");
            layout.invoke(page,true,true);check(saved.getVisibility()==android.view.View.GONE&&first.getVisibility()==android.view.View.VISIBLE,"Rejected identity still hides required pairing settings");
            layout.invoke(page,false,false);check(saved.getVisibility()==android.view.View.GONE&&first.getVisibility()==android.view.View.VISIBLE,"First use hides pairing setup");
            java.lang.reflect.Method prefill=InternalActivationActivity.class.getDeclaredMethod("prefillConnectionPort",int.class);prefill.setAccessible(true);prefill.invoke(page,40567);check("40567".contentEquals(port.getText()),"Remembered connection port is not shown");prefill.invoke(page,0);check("40567".contentEquals(port.getText()),"An invalid remembered port erased manual input");
            check(!controller.isBusy(),"Layout checks started an activation operation");
        }finally{expanded.setBoolean(page,oldExpanded);chosen.setBoolean(page,oldChosen);port.setText(originalPort);last.setInt(page,oldLast);page.changed();}
    }
    private static void fixedStatusAndGuardedBack(InternalActivationActivity page,InternalActivationController controller)throws Exception{
        android.view.View status=(android.view.View)read(page,"statusCard");
        check(!insideScroll(status),"Activation status is hidden inside the setup scroller");
        check(read(page,"setupScroll") instanceof android.widget.ScrollView,"Scrollable setup steps missing");
        Field busy=InternalActivationController.class.getDeclaredField("busy"),kind=InternalActivationController.class.getDeclaredField("operationKind");busy.setAccessible(true);kind.setAccessible(true);boolean originalBusy=busy.getBoolean(controller);String originalKind=(String)kind.get(controller);
        try{
            busy.setBoolean(controller,true);kind.set(controller,"activate");page.changed();
            check(((android.widget.ProgressBar)read(page,"activationProgress")).getVisibility()==android.view.View.VISIBLE,"Pending activation has no visible progress");
            String hint=((android.widget.TextView)read(page,"activationHint")).getText().toString();check(hint.contains("无线调试已连接")&&hint.contains("等待")&&hint.contains("内置服务已激活"),"ADB/system-connected toast can be confused with backend readiness");
            page.onBackPressed();android.app.AlertDialog guard=(android.app.AlertDialog)read(page,"leaveDialog");check(guard!=null&&guard.isShowing()&&!page.isFinishing(),"System back silently cancels pending activation");
            guard.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();check(controller.isActivating()&&!page.isFinishing(),"Continue waiting ended activation");
            ((android.widget.Button)read(page,"headerBack")).performClick();guard=(android.app.AlertDialog)read(page,"leaveDialog");check(guard!=null&&guard.isShowing()&&!page.isFinishing(),"Header back bypasses activation guard");guard.dismiss();
        }finally{busy.setBoolean(controller,originalBusy);kind.set(controller,originalKind);page.changed();}
    }
    private static boolean insideScroll(android.view.View view){for(android.view.ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent instanceof android.widget.ScrollView)return true;return false;}
    private static void notificationAndSettings(Context context,InternalActivationController controller)throws Exception{
        Field port=InternalActivationController.class.getDeclaredField("pairPort");port.setAccessible(true);int saved=port.getInt(controller);
        try{
            for(int value:new int[]{0,37123}){
                port.setInt(controller,value);Notification notice=controller.buildPairNotification();
                check(notice!=null&&notice.actions!=null&&notice.actions.length>0,"Pairing notification has no action when discovery is unavailable");
                Notification.Action replyAction=null;boolean settingsAction=false;for(Notification.Action action:notice.actions){RemoteInput[] inputs=action.getRemoteInputs();if(inputs!=null&&inputs.length>0){replyAction=action;check(inputs[0].getAllowFreeFormInput(),"Pairing action rejects free-form port and code");check(InternalActivationController.REMOTE_CODE.equals(inputs[0].getResultKey()),"RemoteInput result key mismatch");}else if(action.title!=null&&action.title.toString().contains("设置")){check(action.actionIntent!=null,"Settings action has no PendingIntent");if(Build.VERSION.SDK_INT>=31)check(action.actionIntent.isActivity(),"Settings action does not open an Activity");settingsAction=true;}}
                check(replyAction!=null&&replyAction.actionIntent!=null,"Input action missing before/after pairing-port discovery");
                check(context.getPackageName().equals(replyAction.actionIntent.getCreatorPackage())&&android.os.Process.myUid()==replyAction.actionIntent.getCreatorUid(),"Reply PendingIntent not owned by this app");
                if(Build.VERSION.SDK_INT>=31){check(!replyAction.actionIntent.isImmutable(),"RemoteInput PendingIntent is immutable");check(!replyAction.actionIntent.isActivity(),"Notification reply opens an Activity and hides the system pairing popup");}
                check(notice.contentIntent==null,"Notification body navigates away and can close the system pairing popup");
                check(settingsAction,"Pairing notification lacks an explicit separate settings action");
                check(notice.extras==null||!notice.extras.containsKey(Notification.EXTRA_REMOTE_INPUT_HISTORY),"Pairing code can persist in notification history");
            }
        }finally{port.setInt(controller,saved);}
        Intent settings=WirelessDebugSettings.intent(context);int flags=settings.getFlags();
        check((flags&Intent.FLAG_ACTIVITY_NEW_TASK)!=0,"Settings shares the application's task");
        check((flags&Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)!=0,"Settings launch omits task reset behavior");
        check((flags&(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_DOCUMENT))==0,"Settings launch destroys or duplicates the pairing task");
        String action=settings.getAction();check("android.settings.WIRELESS_DEBUGGING_SETTINGS".equals(action)||android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(action)||android.provider.Settings.ACTION_SETTINGS.equals(action),"Settings action is not an approved system settings screen");
    }
    private static Object read(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
}

package org.nfcresearch.qpolllab;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
/** Open system Settings in its own task without clearing a pairing dialog. */
public final class WirelessDebugSettings {
    private WirelessDebugSettings(){}
    static Intent intentFor(String action){return new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);}
    public static Intent intent(Context context){
        for(String action:new String[]{"android.settings.WIRELESS_DEBUGGING_SETTINGS",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,Settings.ACTION_SETTINGS}){Intent candidate=intentFor(action);if(context.getPackageManager().resolveActivity(candidate,0)!=null)return candidate;}
        return intentFor(Settings.ACTION_SETTINGS);
    }
    public static boolean open(Context context){
        for(String action:new String[]{"android.settings.WIRELESS_DEBUGGING_SETTINGS",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,Settings.ACTION_SETTINGS})try{context.startActivity(intentFor(action));return true;}catch(RuntimeException ignored){}
        return false;
    }
}

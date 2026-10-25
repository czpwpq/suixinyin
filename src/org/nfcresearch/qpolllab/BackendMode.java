package org.nfcresearch.qpolllab;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared selection for the photo and reading clients. Activation never starts NFC. */
public final class BackendMode {
    public static final String INTERNAL="internal",SHIZUKU="shizuku",ROOT="root";
    private BackendMode(){}
    public static String normalize(String value,boolean legacyRoot){
        if(INTERNAL.equals(value)||SHIZUKU.equals(value)||ROOT.equals(value))return value;
        return legacyRoot?ROOT:INTERNAL;
    }
    public static String get(Context context){SharedPreferences p=context.getSharedPreferences("refresh-settings",Context.MODE_PRIVATE);return normalize(p.getString("connectionBackend",null),p.getBoolean("rootBackend",false));}
    public static void set(Context context,String value){
        if(!INTERNAL.equals(value)&&!SHIZUKU.equals(value)&&!ROOT.equals(value))throw new IllegalArgumentException("Unknown connection backend");
        context.getSharedPreferences("refresh-settings",Context.MODE_PRIVATE).edit().putString("connectionBackend",value).putBoolean("rootBackend",ROOT.equals(value)).apply();
    }
    public static String label(String mode){return INTERNAL.equals(mode)?"内置激活":ROOT.equals(mode)?"Root":"外置 Shizuku";}
}

package org.nfcresearch.qpolllab.activation;
import android.content.Context;
public final class AdbConnector {
    public static boolean savedIdentity=true;
    public static int cleared,paired,nfcCalls;
    public static boolean hasSavedIdentity(Context context){return savedIdentity;}
    public static void clearIdentity(Context context){cleared++;throw new AssertionError("Recovery deleted pairing identity");}
    public static void pair(){paired++;throw new AssertionError("Recovery started pairing");}
    public static void sendNfc(){nfcCalls++;throw new AssertionError("Recovery sent NFC");}
    public static void reset(){savedIdentity=true;cleared=paired=nfcCalls=0;}
}

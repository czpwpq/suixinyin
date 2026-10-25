package org.nfcresearch.qpolllab;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private, explicit PendingIntent target. Pairing code is never persisted or logged. */
public final class InternalPairingReceiver extends BroadcastReceiver {
    public static final String ACTION="org.nfcresearch.qpolllab.easy.PAIR_LOCAL_ADB";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        Bundle input=RemoteInput.getResultsFromIntent(intent);if(input==null)return;CharSequence text=input.getCharSequence(InternalActivationController.REMOTE_CODE);if(text==null||text.length()>32)return;
        PendingResult result=goAsync();AtomicBoolean finished=new AtomicBoolean();Runnable complete=()->{if(finished.compareAndSet(false,true))result.finish();};
        new Handler(Looper.getMainLooper()).postDelayed(complete,24000);
        InternalActivationController.get(context).pairFromNotification(text,complete);input.clear();
    }
}

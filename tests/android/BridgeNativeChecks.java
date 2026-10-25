package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import rikka.shizuku.Shizuku;

/** Device-only Binder/Parcel smoke checks; every endpoint is fake and no NFC command is sent. */
public final class BridgeNativeChecks {
    private static final int APP_UID = 10329;
    private static final String APK = "/data/app/synthetic-easy/base.apk";

    private BridgeNativeChecks() { }

    public static void run() throws Exception {
        FakeBridge shell = new FakeBridge();
        String info = BridgeClient.read(shell, APP_UID, APK);
        check(info.contains(Integer.toString(APP_UID)) && info.contains("2000"),
                "Successful handshake omitted caller/backend information");
        shell.onlyInfo();
        FakeBridge root = new FakeBridge(); root.backendUid = 0;
        BridgeClient.read(root, APP_UID, APK); root.onlyInfo();

        FakeBridge old = new FakeBridge(); old.supported = false;
        rejects(old, IllegalStateException.class);
        FakeBridge wrongOwner = new FakeBridge(); wrongOwner.ownerUid++;
        rejects(wrongOwner, SecurityException.class);
        FakeBridge wrongCaller = new FakeBridge(); wrongCaller.callerUid++;
        rejects(wrongCaller, SecurityException.class);
        FakeBridge original = new FakeBridge(); original.ownerPackage = "org.nfcresearch.qpolllab";
        rejects(original, SecurityException.class);
        FakeBridge stale = new FakeBridge(); stale.version--;
        rejects(stale, SecurityException.class);
        FakeBridge wrongProtocol = new FakeBridge(); wrongProtocol.protocol++;
        rejects(wrongProtocol, SecurityException.class);
        FakeBridge wrongApk = new FakeBridge(); wrongApk.apk = "/data/app/synthetic-previous/base.apk";
        rejects(wrongApk, SecurityException.class);
        FakeBridge unprivileged = new FakeBridge(); unprivileged.backendUid = APP_UID;
        rejects(unprivileged, SecurityException.class);
        FakeBridge remoteFailure = new FakeBridge(); remoteFailure.throwRemote = true;
        rejects(remoteFailure, RemoteException.class);
        FakeBridge denied = new FakeBridge(); denied.replyDenied = true;
        rejects(denied, SecurityException.class);
        FakeBridge extra = new FakeBridge(); extra.extraField = true;
        rejects(extra, SecurityException.class);
        try {
            BridgeClient.read(null, APP_UID, APK);
            throw new AssertionError("Missing Binder was accepted");
        } catch (IllegalStateException expected) {
            check(expected.getMessage() != null && !expected.getMessage().isEmpty(), "Missing-Binder failure needs guidance");
        }
    }

    /**
     * Called with the production Activity already launched in Root mode by the UI
     * test harness. An unattached radio button replaces only the field read by the
     * guard, so these cases cannot call the real backend-selection listener.
     * Lifecycle transitions are simulated at the cancellation boundary; no real
     * Shizuku service, NFC sender, or Root process is invoked.
     */
    public static void runLifecycle(Instrumentation instrumentation, Activity activity) throws Exception {
        AtomicReference<RadioButton> actualRoot = new AtomicReference<>();
        AtomicReference<String> actualStatus = new AtomicReference<>();
        AtomicReference<String> actualIdentity = new AtomicReference<>();
        AtomicInteger actualVisibility = new AtomicInteger();
        AtomicReference<Boolean> actualResumed = new AtomicReference<>();
        AtomicReference<Shizuku.OnBinderReceivedListener> receivedListener = new AtomicReference<>();
        AtomicReference<Shizuku.OnBinderDeadListener> deadListener = new AtomicReference<>();
        AtomicReference<Boolean> removedReceived = new AtomicReference<>(false),removedDead = new AtomicReference<>(false);
        ui(instrumentation, () -> {
            check(activity instanceof MainActivity, "Identity lifecycle checks need production MainActivity");
            check(((RadioButton)field(activity,"useRoot")).isChecked(),
                    "Launch production Activity with rootBackend=true to isolate Shizuku");
            check(!(Boolean)field(activity,"binding") && field(activity,"bridgeBinder")==null,
                    "Launch MainActivity with rootBackend=true before identity lifecycle checks");
            actualRoot.set((RadioButton)field(activity,"useRoot"));
            actualResumed.set((Boolean)field(activity,"resumed"));
            actualStatus.set(((TextView)field(activity,"connectionStatus")).getText().toString());
            actualIdentity.set((String)field(activity,"bridgeIdentityStatus"));
            actualVisibility.set(((Button)field(activity,"connectionButton")).getVisibility());
            receivedListener.set((Shizuku.OnBinderReceivedListener)field(activity,"binderReceived"));
            deadListener.set((Shizuku.OnBinderDeadListener)field(activity,"binderDead"));
            // These only change the local listener registry; no remote service is called.
            removedReceived.set(Shizuku.removeBinderReceivedListener(receivedListener.get()));
            removedDead.set(Shizuku.removeBinderDeadListener(deadListener.get()));
            RadioButton guardOnly = new RadioButton(activity);
            guardOnly.setChecked(false);
            field(activity,"useRoot",guardOnly);
            // Suppress automatic binder-received listeners during this isolated test.
            field(activity,"resumed",false);
        });
        try {
            for (String transition : new String[]{"disconnect", "timeout", "root", "destroy", "replacement"}) {
                for (boolean failReply : new boolean[]{false,true}) {
                    staleHandshake(instrumentation,activity,transition,failReply);
                }
            }
        } finally {
            ui(instrumentation, () -> {
                invoke(activity,"invalidateBridge",new Class<?>[]{String.class},"Identity lifecycle checks finished");
                field(activity,"afterConnection",null);
                field(activity,"destroyed",false);
                field(activity,"resumed",actualResumed.get());
                field(activity,"useRoot",actualRoot.get());
                field(activity,"bridgeIdentityStatus",actualIdentity.get());
                ((TextView)field(activity,"connectionStatus")).setText(actualStatus.get());
                ((Button)field(activity,"connectionButton")).setVisibility(actualVisibility.get());
                if(removedReceived.get())Shizuku.addBinderReceivedListener(receivedListener.get());
                if(removedDead.get())Shizuku.addBinderDeadListener(deadListener.get());
            });
        }
    }

    private static void staleHandshake(Instrumentation instrumentation, Activity activity,
                                       String transition, boolean failReply) throws Exception {
        DelayedBridge candidate = new DelayedBridge(activity,failReply);
        AtomicInteger continuations = new AtomicInteger();
        AtomicReference<Binder> replacement = new AtomicReference<>();
        String marker="native-test-"+transition;
        ui(instrumentation, () -> {
            invoke(activity,"invalidateBridge",new Class<?>[]{String.class},"Native handshake case");
            ((RadioButton)field(activity,"useRoot")).setChecked(false);
            field(activity,"destroyed",false);
            field(activity,"binding",true);
            field(activity,"afterConnection",(Runnable)continuations::incrementAndGet);
            invoke(activity,"verifyBridge",new Class<?>[]{android.os.IBinder.class},candidate);
        });
        try {
            check(candidate.entered.await(5,TimeUnit.SECONDS),"Identity worker did not reach fake Binder");
            ui(instrumentation, () -> {
                if(transition.equals("root"))((RadioButton)field(activity,"useRoot")).setChecked(true);
                else if(transition.equals("destroy"))field(activity,"destroyed",true);
                else invoke(activity,"invalidateBridge",new Class<?>[]{String.class},marker);
                if(transition.equals("replacement")) {
                    Binder next=new Binder();replacement.set(next);
                    field(activity,"binding",true);field(activity,"bridgeCandidate",next);
                }
                field(activity,"bridgeIdentityStatus",marker);
                // If the stale result were accepted, this counter would run.
                field(activity,"resumed",true);
            });
        } finally { candidate.release.countDown(); }
        Thread identityWorker=candidate.worker;
        check(identityWorker!=null,"Fake Binder did not capture its worker");
        identityWorker.join(5000);
        check(!identityWorker.isAlive(),"Identity worker did not finish after fake Binder release");
        instrumentation.waitForIdleSync();
        ui(instrumentation, () -> {
            check(field(activity,"bridgeBinder")==null,"Late handshake installed a backend after "+transition);
            check(marker.equals(field(activity,"bridgeIdentityStatus")),"Late handshake replaced cancellation reason after "+transition);
            check(continuations.get()==0,"Late handshake ran a pending send continuation after "+transition);
            if(replacement.get()!=null) {
                check(field(activity,"bridgeCandidate")==replacement.get()&&(Boolean)field(activity,"binding"),
                        "Old handshake cleared the new connection attempt");
            }
            check(candidate.calls==1,"Lifecycle handshake must only read INFO once");
            field(activity,"destroyed",false);field(activity,"resumed",false);
        });
    }

    private interface UiAction { void run() throws Exception; }
    private static void ui(Instrumentation instrumentation,UiAction action)throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable t){failure.set(t);}});
        Throwable t=failure.get();if(t instanceof Exception)throw (Exception)t;if(t instanceof Error)throw (Error)t;
    }
    private static Object field(Object owner,String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void field(Object owner,String name,Object value)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private static void invoke(Object owner,String name,Class<?>[] types,Object...values)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,values);
    }

    private static final class DelayedBridge extends Binder {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final int uid;final String apk;final boolean failReply;volatile Thread worker;int calls;
        DelayedBridge(Activity activity,boolean failReply){uid=android.os.Process.myUid();apk=activity.getApplicationInfo().sourceDir;this.failReply=failReply;}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
            calls++;check(code==BridgeIdentity.INFO_TRANSACTION,"Lifecycle test attempted NFC transaction "+code);
            data.enforceInterface(BridgeIdentity.DESCRIPTOR);worker=Thread.currentThread();entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new RemoteException("Fake identity barrier timed out");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new RemoteException("Fake identity interrupted");}
            reply.writeNoException();reply.writeInt(BridgeIdentity.PROTOCOL_VERSION);reply.writeInt(BridgeIdentity.SERVICE_VERSION);
            reply.writeString(failReply?"org.nfcresearch.qpolllab":BridgeIdentity.PACKAGE_NAME);
            reply.writeInt(uid);reply.writeInt(uid);reply.writeInt(2000);reply.writeString(apk);return true;
        }
    }

    private static void rejects(FakeBridge bridge, Class<? extends Exception> type) throws Exception {
        try {
            BridgeClient.read(bridge, APP_UID, APK);
        } catch (Exception expected) {
            check(type.isInstance(expected), "Wrong rejection type: " + expected);
            check(expected.getMessage() != null && !expected.getMessage().isEmpty(),
                    "Handshake failures must contain useful diagnostics");
            bridge.onlyInfo();
            return;
        }
        throw new AssertionError("Invalid backend identity accepted: " + type.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeBridge extends Binder {
        int calls, nfcCalls;
        int protocol = BridgeIdentity.PROTOCOL_VERSION;
        int version = BridgeIdentity.SERVICE_VERSION;
        String ownerPackage = BridgeIdentity.PACKAGE_NAME;
        int ownerUid = APP_UID, callerUid = APP_UID, backendUid = 2000;
        String apk = APK;
        boolean supported = true, throwRemote, replyDenied, extraField;

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            calls++;
            if (code >= 1 && code <= 3) nfcCalls++;
            check(code == BridgeIdentity.INFO_TRANSACTION, "Identity check attempted an NFC/mutating transaction: " + code);
            data.enforceInterface(BridgeIdentity.DESCRIPTOR);
            check(data.dataAvail() == 0, "Identity request should contain no session payload");
            if (throwRemote) throw new RemoteException("Synthetic backend transport failure");
            if (!supported) return false;
            if (replyDenied) { reply.writeException(new SecurityException("Synthetic caller denied")); return true; }
            reply.writeNoException();
            reply.writeInt(protocol); reply.writeInt(version); reply.writeString(ownerPackage);
            reply.writeInt(ownerUid); reply.writeInt(callerUid); reply.writeInt(backendUid); reply.writeString(apk);
            if (extraField) reply.writeInt(42);
            return true;
        }

        void onlyInfo() { check(calls == 1 && nfcCalls == 0, "Handshake must only issue a single read-only INFO call"); }
    }
}

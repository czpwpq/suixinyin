/* Adapted from pjk2336/huawei-eink-reader a4b72b79acec37543339b9810bcb34105b061d65. PolyForm Noncommercial 1.0.0. */
package com.inkreader;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Binder;
import org.nfcresearch.qpolllab.BridgeIdentity;
import org.nfcresearch.qpolllab.BackendMode;
import org.nfcresearch.qpolllab.activation.InternalBackend;
import android.os.Parcel;
import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import rikka.shizuku.Shizuku;

/** App-side handle for the persistent D3/D4 Shizuku session. */
public final class PanelClient {
    public interface Listener {
        void onPanelStatus(String message);
        void onPanelReady();
        void onPreloadDone(String id);
        void onAdvanced(String id, boolean fast, long millis);
        void onPanelFailure(String message);
        default void onPanelDiagnostic(String line) {}
    }

    private static final int PERMISSION_CODE = 611;
    private static final String TOKEN = PanelShizukuService.TOKEN;
    private static final java.util.concurrent.atomic.AtomicInteger RELEASING=new java.util.concurrent.atomic.AtomicInteger();
    public static boolean isReleasePending(){return RELEASING.get()>0;}
    private final Binder owner=new Binder();
    private final Object connectionLock=new Object();
    private final StringBuilder diagnostic=new StringBuilder();
    private final Activity activity;
    private final Listener listener;
    private final String panelType;
    private final int powerMs;
    private final int pollMs;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicLong ids = new AtomicLong();
    private volatile IBinder binder;
    private volatile boolean bound;
    private volatile boolean serviceReady;
    private volatile boolean internal;
    private final IBinder.DeathRecipient internalDeath=()->{serviceReady=false;if(!closed.get())failure("内置授权服务已断开，请回设备页重新激活");};
    private Thread pollThread;

    private final Shizuku.UserServiceArgs args;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (closed.get()) return;
            binder = service;
            bound = true;
            status("已连接服务，正在核验本应用阅读后端身份");
            new Thread(()->{try{synchronized(connectionLock){verify(service);if(closed.get())return;transact(1);startPolling();}}catch(Exception error){failure("阅读后端验证/启动失败："+error.getMessage());}},"reader-identity").start();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null;
            bound = false;
            serviceReady = false;
            if (!closed.get()) failure("Shizuku 传输服务已断开");
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != PERMISSION_CODE || closed.get()) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) bind();
                else failure("未授予 Shizuku 权限");
            };

    public PanelClient(Activity activity, Listener listener, String panelType,
                       int powerMs, int pollMs) {
        this.activity = activity;
        this.listener = listener;
        this.panelType = panelType;
        this.powerMs = powerMs;
        this.pollMs = pollMs;
        args=new Shizuku.UserServiceArgs(new ComponentName(BridgeIdentity.PACKAGE_NAME,"com.inkreader.PanelShizukuService"))
                .tag(BridgeIdentity.serviceTag(android.os.Process.myUid())+".reader")
                .version(BridgeIdentity.SERVICE_VERSION).daemon(false).processNameSuffix("suixinyin_reader_v"+BridgeIdentity.SERVICE_VERSION);
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        if(!"A0".equals(panelType)&&!"A1".equals(panelType)){failure("请先在设备页选择实物A0或A1，再连接阅读屏幕");return;}
        if(isReleasePending()){failure("上一阅读会话正在释放，请稍后重试");return;}
        String backend=BackendMode.get(activity);
        if(BackendMode.INTERNAL.equals(backend)){
            internal=true;IBinder service=InternalBackend.readerBinder();
            if(service==null){org.nfcresearch.qpolllab.InternalActivationController activation=org.nfcresearch.qpolllab.InternalActivationController.get(activity);failure(activation.isAutoRestoring()?"内置连接正在自动恢复，请就绪后再点连接；TXT仍可离线阅读":activation.hasSavedIdentity()&&!activation.needsPairing()?"已有本机连接记录，请在设备页重新连接，无需重复配对；TXT仍可离线阅读":"内置服务未激活，请先在设备页完成首次激活；TXT仍可离线阅读");return;}
            try{service.linkToDeath(internalDeath,0);connection.onServiceConnected(new ComponentName(BridgeIdentity.PACKAGE_NAME,"com.inkreader.PanelShizukuService"),service);}catch(Exception error){failure("内置阅读后端不可用，请重新激活");}return;
        }
        if(BackendMode.ROOT.equals(backend)){failure("阅读请先在设备页选择内置激活或外置 Shizuku；照片的直接 Root 方式仍可使用");return;}
        Shizuku.addRequestPermissionResultListener(permissionListener);
        try {
            if (!Shizuku.pingBinder()) {
                failure("Shizuku 未运行；小说仍可正常离线阅读");
                return;
            }
            if (Shizuku.isPreV11() || Shizuku.getVersion()<13) {
                failure("Shizuku 版本过旧，请升级");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status("等待 Shizuku 授权");
                Shizuku.requestPermission(PERMISSION_CODE);
                return;
            }
            bind();
        } catch (Throwable error) {
            failure("Shizuku 不可用：" + error.getMessage());
        }
    }

    private void bind() {
        if (closed.get() || bound) return;
        bound = true;
        try {
            status("正在连接 Shizuku 传输服务");
            Shizuku.bindUserService(args, connection);
        } catch (Throwable error) {
            bound = false;
            failure("绑定 Shizuku 服务失败：" + error.getMessage());
        }
    }

    public boolean isReady() {
        return serviceReady && !closed.get();
    }

    public String submitAdvance(byte[] packed) {
        return submit(2, packed);
    }

    public String submitPreload(byte[] packed) {
        return submit(1, packed);
    }

    private String submit(int operation, byte[] packed) {
        IBinder service = binder;
        if (service == null || !serviceReady || closed.get()) return null;
        String id = Long.toString(ids.incrementAndGet());
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(TOKEN);
            request.writeInt(operation);
            request.writeString(id);
            request.writeByteArray(packed);
            request.writeString(panelType);
            request.writeInt(powerMs);
            request.writeInt(pollMs);
            if (!service.transact(3, request, reply, 0)) {
                failure("传输服务不兼容");
                return null;
            }
            reply.readException();
            return id;
        } catch (Exception error) {
            failure("提交页面失败：" + error.getMessage());
            return null;
        } finally {
            request.recycle();
            reply.recycle();
        }
    }

    private void startPolling() {
        if (pollThread != null) return;
        pollThread = new Thread(() -> {
            boolean lastActive = true;
            while (!closed.get() && lastActive) {
                IBinder service = binder;
                if (service == null) break;
                Parcel request = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    request.writeInterfaceToken(TOKEN);
                    if (!service.transact(2, request, reply, 0)) break;
                    reply.readException();
                    String lines = reply.readString();
                    lastActive = reply.readInt() != 0;
                    dispatch(lines);
                } catch (Exception error) {
                    if (!closed.get()) failure("读取传输状态失败：" + error.getMessage());
                    break;
                } finally {
                    request.recycle();
                    reply.recycle();
                }
                SystemClock.sleep(70);
            }
            if (!closed.get()) {
                serviceReady = false;
                if (!lastActive) failure("墨水屏传输已结束，请重新开始");
            }
        }, "inkreader-panel-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void dispatch(String lines) {
        if (lines == null) return;
        for (String line : lines.split("\\n")) {
            if (!line.startsWith("QP|")) continue;
            synchronized(diagnostic){diagnostic.append(line).append('\n');if(diagnostic.length()>80000){int cut=diagnostic.indexOf("\n",diagnostic.length()-60000);if(cut>=0)diagnostic.delete(0,cut+1);}}
            onMain(()->listener.onPanelDiagnostic(line));
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) continue;
            String kind = parts[1];
            String value = parts[2];
            switch (kind) {
                case "READY":
                    serviceReady = true;
                    status("墨水屏已连接，可推送页面");
                    onMain(listener::onPanelReady);
                    break;
                case "PRELOADED": {
                    String id = before(value, '|');
                    onMain(() -> listener.onPreloadDone(id));
                    break;
                }
                case "ADVANCED": {
                    String[] values = value.split("\\|", -1);
                    String id = values.length > 0 ? values[0] : "";
                    boolean fast = values.length > 2 && "1".equals(values[2]);
                    long millis = 0;
                    try {
                        if (values.length > 3) millis = Long.parseLong(values[3]);
                    } catch (NumberFormatException ignored) {
                    }
                    final long elapsed = millis;
                    onMain(() -> listener.onAdvanced(id, fast, elapsed));
                    break;
                }
                case "PROGRESS": {
                    String[] values = value.split("\\|", 3);
                    if (values.length >= 3) {
                        try {
                            int done = Integer.parseInt(values[0]);
                            int total = Integer.parseInt(values[1]);
                            int percent = total <= 0 ? 0 : done * 100 / total;
                            status("后台预传 " + percent + "% · " + values[2]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                }
                case "STAGE": {
                    int split = value.indexOf('|');
                    status(split >= 0 ? value.substring(split + 1) : value);
                    break;
                }
                case "FAST_TURN":
                    status(value);
                    break;
                case "ERROR":
                case "RESTORE_FAILED":
                    failure(value);
                    break;
                case "CANCELLED":
                    serviceReady = false;
                    status("墨水屏会话已停止");
                    break;
                default:
                    break;
            }
        }
    }

    public String diagnostics(){synchronized(diagnostic){return diagnostic.toString();}}
    public void close() {
        if(!closed.compareAndSet(false,true))return;
        final IBinder service=binder;final boolean wasBound=bound,wasInternal=internal;
        binder=null;bound=false;serviceReady=false;
        try{Shizuku.removeRequestPermissionResultListener(permissionListener);}catch(Throwable ignored){}
        RELEASING.incrementAndGet();
        Thread cleanup=new Thread(()->{try{
            synchronized(connectionLock){if(service!=null){Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{request.writeInterfaceToken(TOKEN);if(service.transact(4,request,reply,0))reply.readException();}catch(Exception ignored){}finally{request.recycle();reply.recycle();}}}
            try{if(wasInternal&&service!=null)service.unlinkToDeath(internalDeath,0);if(wasBound&&!wasInternal)Shizuku.unbindUserService(args,connection,false);}catch(Throwable ignored){}
        }finally{RELEASING.decrementAndGet();}},"reader-stop-wait");cleanup.setDaemon(true);cleanup.start();
    }
    private void verify(IBinder service)throws Exception{
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{request.writeInterfaceToken(TOKEN);if(!service.transact(PanelShizukuService.INFO_TRANSACTION,request,reply,0))throw new IllegalStateException("阅读后端不支持身份检查");reply.readException();
            int protocol=reply.readInt(),version=reply.readInt();String pkg=reply.readString();int appUid=reply.readInt(),caller=reply.readInt(),backend=reply.readInt();String apk=reply.readString();
            BridgeIdentity.verify(pkg,protocol,version,appUid,caller,backend,apk,android.os.Process.myUid(),activity.getApplicationInfo().sourceDir);
            if(reply.dataAvail()!=0)throw new SecurityException("阅读后端身份回复有未知字段");
        }finally{request.recycle();reply.recycle();}
    }

    private void transact(int code) throws Exception {
        IBinder service = binder;
        if (service == null) throw new IllegalStateException("服务未连接");
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(TOKEN);if(code==1)request.writeStrongBinder(owner);
            if (!service.transact(code, request, reply, 0)) {
                throw new IllegalStateException("传输服务不兼容");
            }
            reply.readException();
        } finally {
            request.recycle();
            reply.recycle();
        }
    }

    private void status(String message) {
        onMain(() -> listener.onPanelStatus(message));
    }

    private void failure(String message) {
        serviceReady = false;
        onMain(() -> listener.onPanelFailure(message));
    }

    private void onMain(Runnable action) {
        if (closed.get()) return;
        activity.runOnUiThread(() -> {
            if (!closed.get()) action.run();
        });
    }

    private static String before(String value, char delimiter) {
        int index = value.indexOf(delimiter);
        return index < 0 ? value : value.substring(0, index);
    }
}

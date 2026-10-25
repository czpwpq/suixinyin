package org.nfcresearch.qpolllab;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** Read-only identity handshake. It never starts, polls, or cancels an NFC session. */
public final class BridgeClient {
    private BridgeClient() { }

    /** Call off the UI thread: a Binder transaction may wait for the remote process. */
    public static String read(IBinder binder, int expectedUid, String expectedApk) throws RemoteException {
        if (binder == null) throw new IllegalStateException("尚未收到后端连接，请在设备页连接当前授权方式。");
        if (!binder.pingBinder()) throw new IllegalStateException("后端连接已失效，请在设备页重新激活或连接。");
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(BridgeIdentity.DESCRIPTOR);
            if (!binder.transact(BridgeIdentity.INFO_TRANSACTION, request, reply, 0)) {
                throw new IllegalStateException("后端不支持身份检查，可能仍是旧版服务；请重新激活或连接当前版本服务。");
            }
            reply.readException();
            int protocol = reply.readInt();
            int version = reply.readInt();
            String ownerPackage = reply.readString();
            int ownerUid = reply.readInt();
            int callerUid = reply.readInt();
            int backendUid = reply.readInt();
            String apk = reply.readString();
            BridgeIdentity.verify(ownerPackage, protocol, version, ownerUid, callerUid,
                    backendUid, apk, expectedUid, expectedApk);
            if (reply.dataAvail() != 0) throw new SecurityException("后端身份回复包含未知字段，请重新连接当前版本服务。");
            return "后端身份校验通过 · 服务版本 " + version + " · " + ownerPackage
                    + " · 应用 UID=" + ownerUid + " / 调用 UID=" + callerUid
                    + " / 后端 UID=" + backendUid;
        } finally {
            request.recycle();
            reply.recycle();
        }
    }
}

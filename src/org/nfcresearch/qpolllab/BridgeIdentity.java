package org.nfcresearch.qpolllab;

/** Immutable wire identity shared by the client and its privileged service.
 * Full Android UIDs are compared; an appId match across users is never sufficient.
 * SERVICE_VERSION is compiled into the backend so an old process cannot claim freshness
 * by reading the currently installed package's metadata.
 */
public final class BridgeIdentity {
    public static final String PACKAGE_NAME="org.nfcresearch.qpolllab.easy";
    public static final String DESCRIPTOR="org.nfcresearch.qpolllab.easy.NfcSession";
    public static final int SERVICE_VERSION=34;
    public static final int PROTOCOL_VERSION=1;
    public static final int INFO_TRANSACTION=4;
    private BridgeIdentity() { }

    /** Separate service cache entries for packages, users and reinstalled UIDs. */
    public static String serviceTag(int fullUid) {
        if(fullUid<0)throw new IllegalArgumentException("Invalid Android UID");
        return PACKAGE_NAME+".nfc-shell.uid-"+fullUid+".v"+SERVICE_VERSION;
    }

    /** Only identity information is consumed; success does not start NFC or a worker. */
    public static void verify(String packageName,int protocol,int serviceVersion,int appUid,
                              int callerUid,int backendUid,String apkPath,int expectedUid,String expectedApk) {
        if(!PACKAGE_NAME.equals(packageName))throw new SecurityException("Backend package mismatch");
        if(protocol!=PROTOCOL_VERSION)throw new SecurityException("Backend protocol mismatch: "+protocol);
        if(serviceVersion!=SERVICE_VERSION)throw new SecurityException("Backend compiled version mismatch: "+serviceVersion);
        if(expectedUid<0||appUid!=expectedUid||callerUid!=expectedUid)
            throw new SecurityException("Backend UID mismatch: expected="+expectedUid+", owner="+appUid+", caller="+callerUid);
        if(backendUid!=0&&backendUid!=2000)throw new SecurityException("Backend requires shell/root identity: "+backendUid);
        if(expectedApk==null||expectedApk.isEmpty()||apkPath==null||!expectedApk.equals(apkPath))
            throw new SecurityException("Backend APK path mismatch; recreate the service");
    }
}

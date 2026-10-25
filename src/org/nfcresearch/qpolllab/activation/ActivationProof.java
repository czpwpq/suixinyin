package org.nfcresearch.qpolllab.activation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** Actual one-use authorization checks for privileged Binder handoff; no Android runtime dependency. */
public final class ActivationProof {
    private final int expectedUid;
    private final String expectedApk,nonce;
    private final long deadline;
    private boolean cancelled,consumed;
    ActivationProof(int uid,String apk,long now,String secret){
        if(uid<10000||apk==null||!apk.startsWith("/")||apk.indexOf('\0')>=0||apk.indexOf('\r')>=0||apk.indexOf('\n')>=0||now<0||now>Long.MAX_VALUE-90000||secret==null||!secret.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid activation proof identity");
        expectedUid=uid;expectedApk=apk;nonce=secret;deadline=now+90000;
    }
    static ActivationProof create(int uid,String apk,long now){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder secret=new StringBuilder(64);for(byte value:bytes)secret.append(String.format(Locale.ROOT,"%02x",value&255));return new ActivationProof(uid,apk,now,secret.toString());}
    public static void requireSender(int sender){if(sender!=0&&sender!=2000)throw new SecurityException("Only shell/root may deliver internal services");}
    synchronized void validate(int sender,String value,int installedUid,String installedApk,long now){
        requireSender(sender);
        if(installedUid!=expectedUid||!expectedApk.equals(installedApk))throw new SecurityException("Activation installation identity changed");
        if(cancelled||consumed||now<0||now>deadline||value==null||value.length()!=64||!MessageDigest.isEqual(nonce.getBytes(StandardCharsets.US_ASCII),value.getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("Activation request expired, cancelled, consumed or mismatched");
    }
    synchronized void consume(int sender,String value,int installedUid,String installedApk,long now){validate(sender,value,installedUid,installedApk,now);consumed=true;}
    synchronized void cancel(){cancelled=true;}
    long deadline(){return deadline;}
    String nonce(){return nonce;}
    String command(){return "(trap '' HUP; export CLASSPATH="+quote(expectedApk)+"; exec /system/bin/app_process /system/bin --nice-name="+quote("suixinyin-internal-"+expectedUid)+" org.nfcresearch.qpolllab.activation.InternalBridgeBootstrap "+expectedUid+" "+quote(nonce)+" "+quote(expectedApk)+" 2>&1) & suixinyin_daemon_pid=$!; printf 'ACT|SHELL_STARTED|%s\\n' \"$suixinyin_daemon_pid\"; wait \"$suixinyin_daemon_pid\"";}
    String sanitizeOutput(String text){if(text==null)return "";String safe=text.replace(nonce,"[activation-secret]").replace(expectedApk,"[installed-apk]");return safe.length()>1200?safe.substring(0,1200)+"…":safe;}
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}

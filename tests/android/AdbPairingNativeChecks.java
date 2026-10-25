package io.github.muntashirakon.adb;

import java.util.Arrays;
import java.util.Random;

/** Device-only execution of the actual shipped JNI SPAKE2 and Java HKDF/AEAD path. */
public final class AdbPairingNativeChecks {
    public static void run()throws Exception {
        Random random=new Random(311);
        for(int trial=0;trial<12;trial++){
            byte[] password=new byte[70];random.nextBytes(password);for(int i=0;i<6;i++)password[i]=(byte)('0'+trial%10);
            PairingAuthCtx alice=PairingAuthCtx.createAlice(password),bob=PairingAuthCtx.createBob(password.clone());
            check(alice!=null&&bob!=null,"Pairing context allocation");
            try{
                check(alice.getMsg().length==32&&bob.getMsg().length==32,"SPAKE2 wire message length");
                check(alice.initCipher(bob.getMsg())&&bob.initCipher(alice.getMsg()),"SPAKE2 exchange");
                byte[] peerInfo=new byte[8192];random.nextBytes(peerInfo);
                byte[] encrypted=alice.encrypt(peerInfo);check(encrypted.length==8208,"Peer-info AEAD tag length");
                check(Arrays.equals(peerInfo,bob.decrypt(encrypted)),"Client/server pairing key mismatch");
                byte[] reverse=bob.encrypt(peerInfo);check(Arrays.equals(peerInfo,alice.decrypt(reverse)),"Server/client pairing key mismatch");
                encrypted=alice.encrypt(peerInfo);encrypted[99]^=1;check(bob.decrypt(encrypted)==null,"Tampered pairing payload accepted");
            }finally{alice.destroy();bob.destroy();}
            check(alice.encrypt(new byte[1])==null,"Destroyed pairing context reused");
        }
        byte[] a="123456tls-exporter".getBytes("UTF-8"),b="123457tls-exporter".getBytes("UTF-8");
        PairingAuthCtx alice=PairingAuthCtx.createAlice(a),bob=PairingAuthCtx.createBob(b);
        try{check(alice.initCipher(bob.getMsg())&&bob.initCipher(alice.getMsg()),"Wrong-code exchange");check(bob.decrypt(alice.encrypt(new byte[8192]))==null,"Wrong pairing code authenticated");}finally{alice.destroy();bob.destroy();}
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}

package org.nfcresearch.qpolllab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Identifies successfully pushed physical data, independent of preview object identity. */
public final class PushReceipt {
    private String fingerprint;
    public PushReceipt(String stored){fingerprint=stored!=null&&stored.matches("[0-9a-f]{64}")?stored:"";}
    public boolean matches(String panel,byte[] packed){return !fingerprint.isEmpty()&&fingerprint.equals(digest(panel,packed));}
    public void complete(String panel,byte[] packed){fingerprint=digest(panel,packed);}
    public String stored(){return fingerprint;}
    public void clear(){fingerprint="";}
    private static String digest(String panel,byte[] packed){
        if(panel==null||packed==null||packed.length!=105984)throw new IllegalArgumentException("Invalid physical push snapshot");
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(panel.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);byte[] bytes=hash.digest(packed);StringBuilder out=new StringBuilder(64);for(byte b:bytes){out.append(Character.forDigit((b>>>4)&15,16));out.append(Character.forDigit(b&15,16));}return out.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}

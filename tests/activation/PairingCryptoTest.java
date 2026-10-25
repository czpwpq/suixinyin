package io.github.muntashirakon.adb;

import java.util.Arrays;
import java.util.Random;
import org.nfcresearch.qpolllab.activation.AdbIdentityMaterial;

/** Real RSA/X509 and ADB wire identity checks. Native SPAKE2 is covered by a device-only check. */
public final class PairingCryptoTest {
    public static void main(String[] args)throws Exception {
        AdbIdentityMaterial identity=AdbIdentityMaterial.generate(),other=AdbIdentityMaterial.generate();
        check(!Arrays.equals(identity.privateKey.getEncoded(),other.privateKey.getEncoded()),"Per-install identities repeated");
        identity.certificate.checkValidity();identity.certificate.verify(identity.certificate.getPublicKey());
        boolean rejected=false;try{new AdbIdentityMaterial(identity.privateKey,other.certificate);}catch(java.security.GeneralSecurityException expected){rejected=true;}
        check(rejected,"Mismatched private key accepted");
        byte[] encoded=AndroidPubkey.encode((java.security.interfaces.RSAPublicKey)identity.certificate.getPublicKey());
        check(encoded.length==524,"ADB RSA public-key wire length");
        check(Arrays.equals(AndroidPubkey.decode(encoded).getEncoded(),identity.certificate.getPublicKey().getEncoded()),"ADB RSA public-key encoding roundtrip");
        System.out.println("PairingCryptoTest PASS: fresh RSA/X509 identities, validity/signature, mismatched private key rejected and ADB RSA wire format roundtrip");
        System.out.println("Not a phone TLS pairing, Binder bootstrap, NFC or physical refresh test.");
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}

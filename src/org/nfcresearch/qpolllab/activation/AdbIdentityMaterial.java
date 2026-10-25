package org.nfcresearch.qpolllab.activation;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;

/** Self-signed, per-install ADB client identity. Does not install a global security provider. */
public final class AdbIdentityMaterial {
    public final java.security.PrivateKey privateKey;
    public final X509Certificate certificate;
    public AdbIdentityMaterial(java.security.PrivateKey key, X509Certificate certificate) throws Exception {
        this.privateKey=key;this.certificate=certificate;
        if(!"RSA".equals(key.getAlgorithm())||!(certificate.getPublicKey() instanceof java.security.interfaces.RSAPublicKey)
                ||((java.security.interfaces.RSAPublicKey)certificate.getPublicKey()).getModulus().bitLength()!=2048)
            throw new java.security.GeneralSecurityException("Invalid ADB identity");
        certificate.verify(certificate.getPublicKey());
        byte[] probe="suixinyin-adb-identity-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Signature check=Signature.getInstance("SHA256withRSA");check.initSign(key);check.update(probe);byte[] signed=check.sign();
        check.initVerify(certificate.getPublicKey());check.update(probe);
        if(!check.verify(signed))throw new java.security.GeneralSecurityException("ADB identity does not match");
    }
    @SuppressWarnings("deprecation")
    public static AdbIdentityMaterial generate() throws Exception {
        SecureRandom random=new SecureRandom();KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048,random);KeyPair pair=generator.generateKeyPair();
        X509V3CertificateGenerator cert=new X509V3CertificateGenerator();
        X500Principal name=new X500Principal("CN=Suixinyin Local Activation");cert.setIssuerDN(name);cert.setSubjectDN(name);
        cert.setSerialNumber(new BigInteger(128,random).setBit(127));
        cert.setNotBefore(new Date(1577836800000L));cert.setNotAfter(new Date(4102444800000L));
        cert.setPublicKey(pair.getPublic());cert.setSignatureAlgorithm("SHA256withRSA");
        return new AdbIdentityMaterial(pair.getPrivate(),cert.generate(pair.getPrivate(),random));
    }
}

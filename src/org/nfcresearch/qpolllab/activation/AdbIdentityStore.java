package org.nfcresearch.qpolllab.activation;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/** Identity is app-private, excluded from backup, atomically persisted, and never logged. */
final class AdbIdentityStore {
    private static final int MAGIC=0x53584131,MAX_PART=16384;
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getNoBackupFilesDir(),"local-adb-identity.bin"));}
    static synchronized boolean exists(Context context){
        File base=file(context).getBaseFile();return base.isFile()||new File(base.getPath()+".bak").isFile();
    }
    static synchronized AdbIdentityMaterial loadOrCreate(Context context) throws Exception {
        AtomicFile file=file(context);FileInputStream existing;
        try{existing=file.openRead();}catch(FileNotFoundException missing){return create(file);}
        try(DataInputStream in=new DataInputStream(existing)){
            if(in.readInt()!=MAGIC)throw new IOException("ADB identity format error; reset activation pairing before retrying");
            byte[] key=read(in),cert=read(in);
            try{
                if(in.read()!=-1)throw new IOException("ADB identity has trailing data");
                return new AdbIdentityMaterial(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key)),
                        (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert)));
            }finally{Arrays.fill(key,(byte)0);}
        }
    }
    private static byte[] read(DataInputStream in)throws IOException{int n=in.readInt();if(n<1||n>MAX_PART)throw new IOException("Invalid ADB identity length");byte[] b=new byte[n];in.readFully(b);return b;}
    private static AdbIdentityMaterial create(AtomicFile file)throws Exception{
        AdbIdentityMaterial material=AdbIdentityMaterial.generate();FileOutputStream stream=null;byte[] key=material.privateKey.getEncoded();
        try{stream=file.startWrite();DataOutputStream out=new DataOutputStream(stream);out.writeInt(MAGIC);out.writeInt(key.length);out.write(key);byte[] cert=material.certificate.getEncoded();out.writeInt(cert.length);out.write(cert);out.flush();file.finishWrite(stream);stream=null;return material;}
        finally{Arrays.fill(key,(byte)0);if(stream!=null)file.failWrite(stream);}
    }
    static synchronized void clear(Context context){file(context).delete();}
}

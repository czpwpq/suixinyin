package org.nfcresearch.qpolllab;

import java.util.HashSet;
import java.util.Set;

public final class BridgeIdentityTest {
    private static final int VERSION=BridgeIdentity.SERVICE_VERSION;
    private static final String APK="/data/app/easy-current/base.apk";
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void verify(String pkg,int protocol,int version,int owner,int caller,int backend,String apk,int expected,String expectedApk){
        BridgeIdentity.verify(pkg,protocol,version,owner,caller,backend,apk,expected,expectedApk);
    }
    private static void rejected(Runnable action,String message){try{action.run();}catch(SecurityException expected){return;}throw new AssertionError(message);}
    private static void accepted(int uid,int backend){verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,uid,uid,backend,APK,uid,APK);}
    public static void main(String[] args){
        check("org.nfcresearch.qpolllab.easy.NfcSession".equals(BridgeIdentity.DESCRIPTOR),"Descriptor must be package scoped");
        check(BridgeIdentity.INFO_TRANSACTION==4,"INFO wire transaction");
        accepted(10432,2000);accepted(10432,0);accepted(1010432,2000);accepted(99910432,0);
        rejected(()->verify("org.nfcresearch.qpolllab",1,VERSION,10432,10432,2000,APK,10432,APK),"Original package accepted");
        rejected(()->verify(null,1,VERSION,10432,10432,2000,APK,10432,APK),"Missing package accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,0,VERSION,10432,10432,2000,APK,10432,APK),"Old protocol accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,2,VERSION,10432,10432,2000,APK,10432,APK),"Unknown protocol accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION-1,10432,10432,2000,APK,10432,APK),"Old compiled service accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION+1,10432,10432,2000,APK,10432,APK),"Unexpected service build accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10431,10432,2000,APK,10432,APK),"Cached pre-reinstall owner UID accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10431,2000,APK,10432,APK),"Wrong Binder caller accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,1010432,2000,APK,1010432,APK),"Owner appId-only comparison accepted across users");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,1010432,10432,2000,APK,1010432,APK),"Caller appId-only comparison accepted across users");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,-1,-1,2000,APK,-1,APK),"Invalid negative UID accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,1000,APK,10432,APK),"System UID substituted for shell/root");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,10432,APK,10432,APK),"Unprivileged process accepted as backend");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,-1,APK,10432,APK),"Missing backend identity accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,2000,"/data/app/easy-old/base.apk",10432,APK),"Old APK path accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,2000,null,10432,APK),"Null APK accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,2000,"",10432,""),"Empty APK identities accepted");
        rejected(()->verify(BridgeIdentity.PACKAGE_NAME,1,VERSION,10432,10432,2000,APK,10432,null),"Missing client APK identity accepted");
        Set<String> tags=new HashSet<>();
        for(int uid:new int[]{10431,10432,110432,1010432,99910432,Integer.MAX_VALUE}){
            String tag=BridgeIdentity.serviceTag(uid);check(tag.contains(BridgeIdentity.PACKAGE_NAME)&&tag.contains(Integer.toString(uid)),"Tag lost package/full UID");
            check(!"nfc-shell".equals(tag)&&tags.add(tag),"Service tag collision");check(tag.equals(BridgeIdentity.serviceTag(uid)),"Unstable service tag");
        }
        try{BridgeIdentity.serviceTag(-1);throw new AssertionError("Invalid service tag UID accepted");}catch(IllegalArgumentException expected){}
        System.out.println("BridgeIdentityTest: PASS (owner/root/secondary-user identities, strict full UID, protocol/build/APK mismatch, unique service tags)");
    }
}

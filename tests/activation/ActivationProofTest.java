package org.nfcresearch.qpolllab.activation;

/** Exercises the proof object used by InternalBackend, not a second implementation of the checks. */
public final class ActivationProofTest {
    private static final String APK="/data/app/example/base.apk",SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static int checks;
    public static void main(String[] args){
        ActivationProof valid=proof();valid.validate(2000,SECRET,10329,APK,1000);valid.validate(0,SECRET,10329,APK,91000);checks+=2;
        deny(()->valid.validate(10329,SECRET,10329,APK,1000));
        deny(()->valid.validate(1000,SECRET,10329,APK,1000));
        deny(()->valid.validate(2000,SECRET,110329,APK,1000));
        deny(()->valid.validate(2000,SECRET,10330,APK,1000));
        deny(()->valid.validate(2000,SECRET,10329,APK+".old",1000));
        deny(()->valid.validate(2000,SECRET,10329,APK,91001));
        deny(()->valid.validate(2000,null,10329,APK,1000));
        deny(()->valid.validate(2000,SECRET.substring(1),10329,APK,1000));
        deny(()->valid.validate(2000,"f"+SECRET.substring(1),10329,APK,1000));
        ActivationProof cancelled=proof();cancelled.cancel();deny(()->cancelled.validate(2000,SECRET,10329,APK,1001));
        ActivationProof used=proof();used.consume(2000,SECRET,10329,APK,1001);deny(()->used.consume(2000,SECRET,10329,APK,1002));
        ActivationProof old=proof();old.cancel();ActivationProof replacement=ActivationProof.create(10329,APK,2000);replacement.validate(2000,replacement.nonce(),10329,APK,2001);checks++;
        deny(()->old.validate(2000,SECRET,10329,APK,2001));deny(()->replacement.validate(2000,SECRET,10329,APK,2001));
        ActivationProof secondary=new ActivationProof(110329,APK,1000,SECRET);secondary.validate(2000,SECRET,110329,APK,1000);checks++;deny(()->secondary.validate(2000,SECRET,10329,APK,1000));
        String hostile="/data/app/a' `touch x` $(touch y)/base.apk";ActivationProof quoted=new ActivationProof(10329,hostile,1000,SECRET);String command=quoted.command();
        check(command.contains("CLASSPATH='/data/app/a'\\'' `touch x` $(touch y)/base.apk'"),"APK not quoted for shell");
        check(command.startsWith("(trap '' HUP; export CLASSPATH="),"bootstrap does not inherit ignored HUP");
        check(command.contains("; exec /system/bin/app_process"),"shell does not exec the bootstrap");
        check(!command.contains("nohup")&&!command.contains("toybox"),"bootstrap still depends on a native applet");
        check(command.contains(" 10329 '"+SECRET+"' "),"owner UID or nonce missing");
        check(command.contains(" 2>&1) & suixinyin_daemon_pid=$!; printf 'ACT|SHELL_STARTED|%s\\n'"),"launch output or child identity unavailable");
        check(command.endsWith("wait \"$suixinyin_daemon_pid\""),"ADB shell does not wait for the observable child");
        check(!command.contains(">/dev/null"),"startup errors are discarded before handoff");
        String clean=quoted.sanitizeOutput("ACT|ERROR|"+SECRET+" "+hostile);check(!clean.contains(SECRET)&&!clean.contains(hostile)&&clean.contains("ACT|ERROR|"),"bootstrap output leaks activation identity");
        check("".equals(quoted.sanitizeOutput(null)),"null bootstrap output rejected");
        invalid(()->new ActivationProof(2000,APK,1000,SECRET));invalid(()->new ActivationProof(10329,"relative.apk",1000,SECRET));invalid(()->new ActivationProof(10329,APK+"\n",1000,SECRET));invalid(()->new ActivationProof(10329,APK,Long.MAX_VALUE,SECRET));
        System.out.println("ActivationProofTest PASS: "+checks+" actual proof/nonce/UID/expiry/replay/command checks; no Android or NFC claims");
    }
    private static ActivationProof proof(){return new ActivationProof(10329,APK,1000,SECRET);}
    private static void deny(Runnable action){try{action.run();throw new AssertionError("Expected SecurityException");}catch(SecurityException expected){checks++;}}
    private static void invalid(Runnable action){try{action.run();throw new AssertionError("Expected IllegalArgumentException");}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}

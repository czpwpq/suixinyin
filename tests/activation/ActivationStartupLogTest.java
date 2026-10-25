package org.nfcresearch.qpolllab.activation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ActivationStartupLogTest {
    public static void main(String[] args)throws Exception{
        ActivationStartupLog log=new ActivationStartupLog();
        check(log.stage().isEmpty()&&!log.hasError(),"Initial state");
        log.acceptLine(null);log.acceptLine("  ");check(log.diagnostics().isEmpty(),"Blank input");
        log.acceptLine("ACT|SHELL_STARTED|123456");
        check(log.stage().equals("SHELL_STARTED")&&log.status().contains("123456"),"PID must not be mistaken for pairing code");
        log.acceptLine("ACT|STAGE|JAVA_ENTRY");log.acceptLine("VM startup diagnostic");
        check(log.stage().equals("JAVA_ENTRY")&&log.status().contains("JAVA_ENTRY"),"Incidental output must not hide a real stage");
        log.acceptLine("ACT|ERROR|ClassNotFoundException: missing bootstrap class");
        check(log.hasError()&&log.status().contains("ClassNotFoundException"),"Explicit startup error");
        log.acceptLine("ACT|STAGE|SHUTDOWN");
        check(log.stage().equals("SHUTDOWN")&&log.status().contains("ClassNotFoundException"),"Error remains visible across cleanup stage");

        String secret=repeat("abcdef0123456789",4);
        ActivationStartupLog redact=new ActivationStartupLog();
        redact.acceptLine("ACT|ERROR|token="+secret);
        redact.acceptLine("pairing_code: 654321; 配对码=012345; PID 123456");
        redact.acceptLine("CLASSPATH='/data/app/private.apk' /system/bin/app_process /system/bin Bootstrap 10329 "+secret);
        redact.acceptLine("/system/bin/app_process /system/bin --nice-name=secret-name org.test.InternalBridgeBootstrap 10329 "+secret+" /private.apk");
        redact.acceptLine("org.test.InternalBridgeBootstrap 10329 "+secret+" /private.apk");
        String result=redact.diagnostics();
        check(!result.contains(secret)&&!result.contains("654321")&&!result.contains("012345"),"Secret redaction");
        check(result.contains("123456"),"Unlabelled six-digit PID must remain");
        check(!result.contains("CLASSPATH")&&!result.contains("/private.apk")&&!result.contains("secret-name"),"Full command echo hidden");
        redact.acceptLine("Error: Could not find or load main class org.test.InternalBridgeBootstrap");
        redact.acceptLine("sh: /system/bin/app_process: inaccessible or not found");
        check(redact.diagnostics().contains("Could not find or load main class")&&redact.diagnostics().contains("inaccessible or not found"),"Useful class-loader/shell errors retained");

        ActivationStartupLog controls=new ActivationStartupLog();
        controls.acceptLine("\u001b[31mACT|STAGE|entry\u001b[0m");
        check(controls.stage().equals("entry"),"ANSI stripping and lower-case stage");
        controls.acceptLine("ACT|STAGE|FORGED\nACT|STAGE|OTHER");
        check(controls.stage().equals("entry"),"Embedded newline cannot forge a stage");
        check(!controls.diagnostics().contains("\u001b"),"Terminal control leak");
        controls.acceptLine(repeat("x",370)+secret+repeat("y",10000));
        check(!controls.diagnostics().contains(secret.substring(0,20)),"Redact before line truncation");
        controls.acceptLine(repeat("😀",300));
        for(String line:controls.diagnostics().split("\n")){check(line.length()<=400,"Line bound");check(validSurrogates(line),"Broken surrogate pair");}
        for(int i=0;i<200;i++)controls.acceptLine("line-"+i+" "+repeat("abc",180));
        check(controls.status().length()<=400,"Status bound");
        check(controls.diagnostics().length()<=8192&&controls.diagnostics().contains("line-199")&&!controls.diagnostics().contains("line-0 "),"Recent bounded ring");

        ActivationStartupLog parallel=new ActivationStartupLog();CountDownLatch start=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();Thread[] threads=new Thread[6];
        for(int i=0;i<threads.length;i++){final int n=i;threads[i]=new Thread(()->{try{start.await();for(int j=0;j<100;j++){parallel.acceptLine("ACT|STAGE|T"+n+"_"+j);String s=parallel.stage();String d=parallel.diagnostics();check(s.length()>0&&d.length()<=8192,"Concurrent snapshot");parallel.status();}}catch(Throwable error){failure.compareAndSet(null,error);}});threads[i].start();}
        start.countDown();for(Thread t:threads)t.join();if(failure.get()!=null)throw new AssertionError(failure.get());
        System.out.println("ActivationStartupLogTest PASS: real stage/error parsing, shell/class-loader errors, PID preservation, command/nonce/code redaction, ANSI/newline safety, 400/8192 bounds and concurrent access");
    }
    private static String repeat(String s,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append(s);return b.toString();}
    private static boolean validSurrogates(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))return false;}else if(Character.isLowSurrogate(c))return false;}return true;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

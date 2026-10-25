package io.github.muntashirakon.adb;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.nfcresearch.qpolllab.activation.AdbIdentityMaterial;

/** Actual ADB socket/stream state machine against an in-process fake daemon; no TLS authentication. */
public final class AdbSocketTest {
    public static void main(String[] args)throws Exception{
        AdbIdentityMaterial identity=AdbIdentityMaterial.generate();ExecutorService pool=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"fake-adb");t.setDaemon(true);return t;});
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))){
            Future<?> fake=pool.submit(()->{try(Socket socket=server.accept()){socket.setSoTimeout(5000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();expect(in,AdbProtocol.A_CNXN);out.write(AdbProtocol.generateMessage(AdbProtocol.A_CNXN,0x01000001,4096,new byte[0]));out.flush();
                for(int round=0;round<40;round++){AdbProtocol.Message open=expect(in,AdbProtocol.A_OPEN);int local=open.arg0,remote=round+40;
                    // Respond immediately: this used to race an unconditional wait() after OPEN.
                    out.write(AdbProtocol.generateMessage(AdbProtocol.A_OKAY,remote,local,null));
                    out.write(AdbProtocol.generateMessage(AdbProtocol.A_WRTE,remote,local,("page-"+round).getBytes("UTF-8")));
                    out.write(AdbProtocol.generateMessage(AdbProtocol.A_CLSE,remote,local,null));out.flush();}
                while(in.read()!=-1){}
            }catch(Exception error){throw new RuntimeException(error);}});
            AdbConnection connection=AdbConnection.create("127.0.0.1",server.getLocalPort(),identity.privateKey,identity.certificate,36);
            try{check(connection.connect(2,TimeUnit.SECONDS,true),"Fake connection did not establish");for(int round=0;round<40;round++){try(AdbStream stream=connection.open("shell:only-test-"+round)){ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] b=new byte[32];int n;while((n=stream.openInputStream().read(b))!=-1)result.write(b,0,n);check(result.toString("UTF-8").equals("page-"+round),"ADB stream payload/close lost");}}}finally{connection.close();}
            fake.get(3,TimeUnit.SECONDS);
        }
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))){
            CountDownLatch requested=new CountDownLatch(1);
            Future<?> fake=pool.submit(()->{try(Socket socket=server.accept()){socket.setSoTimeout(5000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();expect(in,AdbProtocol.A_CNXN);out.write(AdbProtocol.generateMessage(AdbProtocol.A_CNXN,0x01000001,4096,new byte[0]));out.flush();expect(in,AdbProtocol.A_OPEN);requested.countDown();while(in.read()!=-1){};}catch(Exception error){throw new RuntimeException(error);}});
            AdbConnection connection=AdbConnection.create("127.0.0.1",server.getLocalPort(),identity.privateKey,identity.certificate,36);check(connection.connect(2,TimeUnit.SECONDS,true),"Cancellation connection");
            Future<Boolean> pending=pool.submit(()->{try{connection.open("shell:never-acknowledged");return false;}catch(IOException stopped){return true;}});
            check(requested.await(2,TimeUnit.SECONDS),"Pending open not delivered");long started=System.nanoTime();connection.close();
            check(pending.get(2,TimeUnit.SECONDS),"Closing connection failed to cancel OPEN");check(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(2),"Close cancellation exceeded bound");fake.get(3,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        System.out.println("AdbSocketTest PASS: 40 immediate-OKAY streams, payload/CLSE drain, close interrupts pending OPEN within 2s (fake local daemon; no phone TLS pairing)");
    }
    private static AdbProtocol.Message expect(InputStream in,int command)throws Exception{for(;;){AdbProtocol.Message p=AdbProtocol.Message.parse(in,0x01000001,1024*1024);if(p.command==command)return p;if(p.command!=AdbProtocol.A_OKAY&&p.command!=AdbProtocol.A_CLSE)throw new AssertionError("Unexpected ADB packet");}}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}

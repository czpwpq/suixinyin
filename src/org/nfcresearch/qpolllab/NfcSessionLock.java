package org.nfcresearch.qpolllab;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One private, stable advisory lock shared by all of this installation's NFC workers. */
public final class NfcSessionLock {
    private static final Pattern PATH=Pattern.compile("/data/local/tmp/suixinyin-nfc-([0-9]+)/session\\.lock");
    public static File path(int fullUid){if(fullUid<10000)throw new IllegalArgumentException("Invalid application UID");return new File("/data/local/tmp/suixinyin-nfc-"+fullUid,"session.lock");}
    public static final class Lease implements AutoCloseable {
        private final RandomAccessFile file;private final FileLock lock;
        private Lease(RandomAccessFile file,FileLock lock){this.file=file;this.lock=lock;}
        @Override public void close()throws IOException{try{lock.release();}finally{file.close();}}
    }
    public static Lease acquire(String requested)throws Exception{
        Matcher match=PATH.matcher(requested==null?"":requested);
        if(!match.matches())throw new IllegalArgumentException("Invalid NFC session lock path");
        int fullUid;try{fullUid=Integer.parseInt(match.group(1));}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid application UID",invalid);}
        File target=path(fullUid);if(!target.getPath().equals(requested))throw new IllegalArgumentException("Noncanonical NFC lock path");
        int uid=android.os.Process.myUid();if(uid!=0&&uid!=2000)throw new SecurityException("NFC lock requires shell/root");
        File dir=target.getParentFile();
        if(!dir.exists())try{android.system.Os.mkdir(dir.toString(),0700);}catch(android.system.ErrnoException error){if(!dir.isDirectory())throw error;}
        if(Files.isSymbolicLink(dir.toPath())||!dir.getCanonicalFile().equals(dir)||!dir.isDirectory())throw new SecurityException("Unsafe NFC lock directory");
        if(android.system.Os.stat(dir.toString()).st_uid!=2000&&android.system.Os.stat(dir.toString()).st_uid!=0)throw new SecurityException("NFC lock directory owner mismatch");
        android.system.Os.chmod(dir.toString(),0700);if(uid==0)android.system.Os.chown(dir.toString(),2000,2000);
        if(Files.isSymbolicLink(target.toPath())||(target.exists()&&!target.isFile()))throw new SecurityException("Unsafe NFC lock file");
        RandomAccessFile file=new RandomAccessFile(target,"rw");
        try{
            if(Files.isSymbolicLink(target.toPath())||!target.getCanonicalFile().equals(target))throw new SecurityException("Unsafe NFC lock file");
            android.system.Os.chmod(target.toString(),0600);if(uid==0)android.system.Os.chown(target.toString(),2000,2000);
            FileLock lock=file.getChannel().tryLock();if(lock==null)throw new IllegalStateException("另一NFC会话尚未释放，请稍后再试");
            return new Lease(file,lock);
        }catch(Exception error){file.close();throw error;}
    }
    private NfcSessionLock(){}
}

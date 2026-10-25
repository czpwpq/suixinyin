package org.nfcresearch.qpolllab.activation;
import java.util.Arrays;
/** Bounded notification input; errors never echo submitted text. */
public final class NotificationPairingInput {
    private NotificationPairingInput(){}
    public static final class Reply implements AutoCloseable {
        public final int port;public final char[] code;
        private Reply(int port,char[] code){this.port=port;this.code=code;}
        @Override public void close(){Arrays.fill(code,'\0');}
    }
    private static boolean separator(char c){return c==':'||Character.isWhitespace(c)||c=='\u3000';}
    public static Reply parse(CharSequence text,int discoveredPort){
        if(text==null||text.length()<1||text.length()>32)throw new IllegalArgumentException("请输入6位码，或按“端口 空格 6位码”填写");
        int start=0,end=text.length();while(start<end&&Character.isWhitespace(text.charAt(start)))start++;while(end>start&&Character.isWhitespace(text.charAt(end-1)))end--;
        int firstEnd=start;while(firstEnd<end&&!separator(text.charAt(firstEnd)))firstEnd++;
        int codeStart=firstEnd;while(codeStart<end&&separator(text.charAt(codeStart)))codeStart++;
        int port;
        if(codeStart==end){
            if(firstEnd!=end)throw new IllegalArgumentException("请输入6位码，或按“端口 空格 6位码”填写");
            if(firstEnd-start!=6)throw new IllegalArgumentException("配对码需要6位数字");
            if(discoveredPort<1||discoveredPort>65535)throw new IllegalArgumentException("尚未发现配对端口，请按“端口 空格 6位码”输入，例如37123 012345");
            port=discoveredPort;codeStart=start;
        }else{
            port=ActivationInputs.port(text.subSequence(start,firstEnd).toString());
            if(end-codeStart!=6)throw new IllegalArgumentException("请按“端口 空格 6位码”输入，不填写IP地址");
        }
        char[] code=new char[6];for(int i=0;i<6;i++){char c=text.charAt(codeStart+i);if(c<'0'||c>'9'){Arrays.fill(code,'\0');throw new IllegalArgumentException("配对码需要6位数字");}code[i]=c;}
        return new Reply(port,code);
    }
}

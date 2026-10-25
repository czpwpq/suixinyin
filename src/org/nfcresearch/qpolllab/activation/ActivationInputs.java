package org.nfcresearch.qpolllab.activation;

import java.util.Arrays;
import java.util.Locale;

/** Input validation shared by the page, notification action and discovery. No network side effects. */
public final class ActivationInputs {
    public static final String PAIR_SERVICE="_adb-tls-pairing._tcp.";
    public static final String CONNECT_SERVICE="_adb-tls-connect._tcp.";
    private ActivationInputs(){}
    public static int port(String value){
        String text=value==null?"":value.trim();
        if(text.isEmpty()||text.length()>5)throw new IllegalArgumentException("请输入1–65535之间的端口");
        int result=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<'0'||c>'9')throw new IllegalArgumentException("端口只填写数字，不包含IP地址或冒号");result=result*10+c-'0';}
        if(result<1||result>65535)throw new IllegalArgumentException("请输入1–65535之间的端口");return result;
    }
    public static void pairingCode(char[] value){
        if(value==null||value.length!=6)throw new IllegalArgumentException("配对码需要6位数字");
        for(char c:value)if(c<'0'||c>'9')throw new IllegalArgumentException("配对码需要6位数字");
    }
    /** DNS names are case insensitive; platform results may include a root/local suffix. */
    public static String serviceType(String type){
        if(type==null||type.length()>160)return "";String value=type.trim().toLowerCase(Locale.ROOT);
        while(value.startsWith("."))value=value.substring(1);
        while(value.endsWith("."))value=value.substring(0,value.length()-1);
        if(value.endsWith(".local"))value=value.substring(0,value.length()-6);
        return value;
    }
    public static boolean pairingService(String type){return "_adb-tls-pairing._tcp".equals(serviceType(type));}
    public static boolean connectService(String type){return "_adb-tls-connect._tcp".equals(serviceType(type));}
    public static byte[] addressBytes(byte[] value){
        if(value==null)return null;
        if(value.length==16){boolean mapped=true;for(int i=0;i<10;i++)mapped&=value[i]==0;mapped&=value[10]==(byte)255&&value[11]==(byte)255;if(mapped)return Arrays.copyOfRange(value,12,16);}
        return value.clone();
    }
    public static boolean ownAddress(byte[] candidate,Iterable<byte[]> local){
        candidate=addressBytes(candidate);if(candidate==null)return false;
        boolean zero=true;for(byte b:candidate)zero&=b==0;if(zero)return false;
        if(candidate.length==4&&(candidate[0]&255)==127)return true;
        if(candidate.length==16){boolean loop=true;for(int i=0;i<15;i++)loop&=candidate[i]==0;if(loop&&candidate[15]==1)return true;}
        if(candidate.length!=4&&candidate.length!=16)return false;
        if(candidate.length==4&&(candidate[0]&255)>=224)return false;
        if(candidate.length==16&&(candidate[0]&255)==255)return false;
        if(local!=null)for(byte[] address:local)if(Arrays.equals(candidate,addressBytes(address)))return true;
        return false;
    }
    public static String errorLabel(Throwable error){
        if(error==null)return "未知错误";
        String name=error.getClass().getSimpleName();
        return name.matches("[A-Za-z0-9_$]{1,80}")?name:"连接错误";
    }
    public static void clear(char[] code){if(code!=null)Arrays.fill(code,'\0');}
}

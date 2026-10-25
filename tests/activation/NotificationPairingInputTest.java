package org.nfcresearch.qpolllab.activation;

import java.util.Arrays;

/** Runs the parser actually used by RemoteInput handling; no Android/network or duplicated parser. */
public final class NotificationPairingInputTest {
    private static int checks;
    public static void main(String[] args){
        accepted("123456",37123,37123,"123456");
        accepted("000001",1,1,"000001");
        accepted("  000001  ",65535,65535,"000001");
        accepted("37123 123456",0,37123,"123456");
        accepted("37123:000001",0,37123,"000001");
        accepted("1 000000",0,1,"000000");
        accepted("65535:999999",0,65535,"999999");
        accepted("  37123   000001  ",0,37123,"000001");
        accepted("37123 123456",1234,37123,"123456");
        accepted("37123:123456",65536,37123,"123456");
        accepted("00001 000001",0,1,"000001");
        accepted("1"+spaces(25)+"123456",0,1,"123456");
        for(int missing:new int[]{0,-1,65536,Integer.MAX_VALUE})rejected("123456",missing);
        for(String invalid:new String[]{null,""," ","0 123456","65536 123456","999999:123456","-1 123456","+1 123456","1.0 123456","37123 12345","37123 1234567","37123 abcdef","37123 12 456","37123:123:456","１２３４５６","３７１２３ 123456","127.0.0.1:37123 123456","192.168.1.9:37123 123456","[::1]:37123 123456","37123 123456 trailing","37123\n123456\ntrailing"})rejected(invalid,0);
        rejected("123456 extra",37123);rejected("123456:",37123);rejected("123456 : ",37123);rejected("1"+spaces(26)+"123456",0);
        String secret="37123 12x456";try{NotificationPairingInput.parse(secret,0);throw new AssertionError("invalid secret accepted");}catch(IllegalArgumentException error){check(error.getMessage()!=null&&!error.getMessage().contains(secret)&&!error.getMessage().contains("12x456"),"invalid input echoed into an error");}
        NotificationPairingInput.Reply first=NotificationPairingInput.parse("1 000001",0),second=NotificationPairingInput.parse("1 000001",0);
        try{check(first.code!=second.code,"parse calls share a secret buffer");first.close();check(Arrays.equals(first.code,new char[6]),"close did not clear owned secret");check("000001".equals(new String(second.code)),"closing one reply cleared another reply");first.close();check(Arrays.equals(first.code,new char[6]),"close is not idempotent");}finally{first.close();second.close();}
        System.out.println("NotificationPairingInputTest PASS: "+checks+" parser/port/ASCII/leading-zero/secret-clearing checks; no Android notification or pairing claims");
    }
    private static void accepted(String input,int discovered,int port,String expectedCode){NotificationPairingInput.Reply reply=NotificationPairingInput.parse(input,discovered);try{check(reply.port==port,"wrong pairing port");check(expectedCode.equals(new String(reply.code)),"pairing code lost leading zero or changed");}finally{reply.close();}check(Arrays.equals(reply.code,new char[6]),"reply close failed to clear code");}
    private static String spaces(int count){char[] text=new char[count];Arrays.fill(text,' ');return new String(text);}
    private static void rejected(String input,int discovered){try{NotificationPairingInput.Reply reply=NotificationPairingInput.parse(input,discovered);reply.close();throw new AssertionError("invalid notification input accepted");}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}

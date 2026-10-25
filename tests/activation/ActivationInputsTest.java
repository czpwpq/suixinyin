package org.nfcresearch.qpolllab.activation;
import java.util.Arrays;
import java.util.Collections;
public final class ActivationInputsTest{
    public static void main(String[] args){
        check(ActivationInputs.port(" 1 ")==1&&ActivationInputs.port("65535")==65535,"port endpoints");
        for(String bad:new String[]{null,"","0","65536","999999","-1","+1","1.2","127.0.0.1:1234","1234\n5555","１２３４"})reject(()->ActivationInputs.port(bad));
        ActivationInputs.pairingCode("000001".toCharArray());
        for(String bad:new String[]{"","12345","1234567","１２３４５６","12 456","12a456"})reject(()->ActivationInputs.pairingCode(bad.toCharArray()));
        reject(()->ActivationInputs.pairingCode(null));
        check(ActivationInputs.pairingService("_adb-tls-pairing._tcp.")&&ActivationInputs.pairingService("_adb-tls-pairing._tcp"),"pair service forms");
        check(!ActivationInputs.pairingService(ActivationInputs.CONNECT_SERVICE)&&!ActivationInputs.connectService(ActivationInputs.PAIR_SERVICE),"service kinds cannot mix");
        for(String variant:new String[]{"._adb-tls-pairing._tcp.","_ADB-TLS-PAIRING._TCP","_adb-tls-pairing._tcp.local.","._ADB-TLS-PAIRING._TCP.LOCAL."," _adb-tls-pairing._tcp. "})check(ActivationInputs.pairingService(variant)&&!ActivationInputs.connectService(variant),"pairing DNS spelling: "+variant);
        for(String variant:new String[]{"._adb-tls-connect._tcp.","_ADB-TLS-CONNECT._TCP","_adb-tls-connect._tcp.local.","._ADB-TLS-CONNECT._TCP.LOCAL."})check(ActivationInputs.connectService(variant)&&!ActivationInputs.pairingService(variant),"connect DNS spelling: "+variant);
        for(String wrong:new String[]{null,"",".","_ssh._tcp.","_adb-tls-pairing._tcp.example.com","_adb-tls-connect._tcp.local.evil","adb._tcp","name._adb-tls-pairing._tcp.local."})check(!ActivationInputs.pairingService(wrong)&&!ActivationInputs.connectService(wrong),"unrelated service remains rejected");
        byte[] own={10,0,0,2},other={10,0,0,3},v6=new byte[16];v6[15]=1;
        check(ActivationInputs.ownAddress(new byte[]{127,0,0,1},null),"localhost4");check(ActivationInputs.ownAddress(v6,null),"localhost6");
        check(ActivationInputs.ownAddress(own,Collections.singletonList(own)),"own interface allowed");check(!ActivationInputs.ownAddress(other,Collections.singletonList(own)),"remote LAN rejected");
        check(!ActivationInputs.ownAddress(new byte[4],Collections.singletonList(own))&&!ActivationInputs.ownAddress(null,null),"wildcard/null rejected");
        byte[] mapped=new byte[16];mapped[10]=mapped[11]=(byte)255;System.arraycopy(own,0,mapped,12,4);
        check(ActivationInputs.ownAddress(mapped,Collections.singletonList(own))&&ActivationInputs.ownAddress(own,Collections.singletonList(mapped)),"IPv4-mapped IPv6 compares with actual local IPv4");
        mapped[15]=3;check(!ActivationInputs.ownAddress(mapped,Collections.singletonList(own)),"mapped remote LAN remains rejected");
        mapped[12]=127;mapped[13]=0;mapped[14]=0;mapped[15]=1;check(ActivationInputs.ownAddress(mapped,null),"mapped loopback");
        check(!ActivationInputs.ownAddress(new byte[4],Collections.singletonList(new byte[4])),"wildcard cannot validate against a wildcard interface");
        byte[] multicast={(byte)239,1,2,3};check(!ActivationInputs.ownAddress(multicast,Collections.singletonList(multicast)),"multicast cannot be an own unicast endpoint");
        byte[] input={10,0,0,2};byte[] copied=ActivationInputs.addressBytes(input);copied[0]=99;check(input[0]==10,"normalization retains caller ownership");
        String secret="123456 private nonce abcdef";check(!ActivationInputs.errorLabel(new java.io.IOException(secret)).contains(secret),"diagnostics ignore secret exception message");
        char[] code="123456".toCharArray();ActivationInputs.clear(code);check(Arrays.equals(code,new char[6]),"code buffer cleared");
        check("internal".equals(org.nfcresearch.qpolllab.BackendMode.normalize(null,false)),"default internal");
        check("root".equals(org.nfcresearch.qpolllab.BackendMode.normalize(null,true)),"legacy root");
        check("internal".equals(org.nfcresearch.qpolllab.BackendMode.normalize("broken",false)),"bad setting recovers");
        for(String mode:new String[]{"internal","shizuku","root"})check(mode.equals(org.nfcresearch.qpolllab.BackendMode.normalize(mode,true)),"explicit backend retained");
        System.out.println("ActivationInputsTest PASS: ports, ASCII code, separate service types, local-only discovery, secret-free labels");
    }
    private interface Task{void run();}private static void reject(Task task){try{task.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException expected){}}
    private static void check(boolean good,String what){if(!good)throw new AssertionError(what);}
}

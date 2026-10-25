package org.nfcresearch.qpolllab.activation;

/** Tests the state class used by live NSD entries, not an independent test-only model. */
public final class AdbDiscoveryStateTest {
    public static void main(String[] args){
        AdbDiscoveryState pairing=new AdbDiscoveryState(),connection=new AdbDiscoveryState();
        check(pairing.beginResolve()==1&&pairing.retryDelay()==1200,"first failure becomes retryable");
        check(connection.beginResolve()==1&&connection.adopt(39541),"a failed pairing lookup does not poison another service");
        check(pairing.beginResolve()==2&&pairing.retryDelay()==2400,"second failure backs off");
        check(pairing.beginResolve()==3&&pairing.retryDelay()==3600,"third failure backs off");
        check(pairing.beginResolve()==4&&pairing.retryDelay()<0&&pairing.beginResolve()==0,"failed lookups are bounded");
        pairing.rediscovered();check(pairing.beginResolve()==1,"rediscovery is not blocked by permanent seen entry");
        check(pairing.adopt(41235)&&pairing.port()==41235,"late service/address update can deliver after failed lookup");
        check(!pairing.adopt(41235),"unchanged update is not delivered twice");
        check(pairing.adopt(41236)&&pairing.port()==41236,"port update replaces stale port");
        check(pairing.lost()==41236&&pairing.port()==0,"lost service immediately invalidates port");
        check(pairing.adopt(41237),"same watched service may reappear with a fresh port");
        check(pairing.close()==41237&&!pairing.adopt(41238)&&pairing.beginResolve()==0,"closed discovery cannot revive from late callbacks");
        check(connection.port()==39541,"closing pairing leaves connection entry isolated");
        for(int bad:new int[]{0,-1,65536})try{connection.adopt(bad);throw new AssertionError("invalid port accepted");}catch(IllegalArgumentException expected){}
        System.out.println("AdbDiscoveryStateTest PASS: retry exhaustion/rediscovery, independent entries, changed/lost/closed endpoints");
    }
    private static void check(boolean good,String label){if(!good)throw new AssertionError(label);}
}

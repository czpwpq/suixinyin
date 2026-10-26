package org.nfcresearch.qpolllab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.nfcresearch.qpolllab.activation.AdbConnector;
import org.nfcresearch.qpolllab.activation.InternalAdbDiscovery;
import org.nfcresearch.qpolllab.activation.InternalBackend;

/** Runs the actual production helper with fake clock/storage/NSD/controller boundaries. */
public final class InternalReconnectTest {
    private static int checks;
    public static void main(String[] args){
        List<String> failures=new ArrayList<>();
        run("old install/new ports",InternalReconnectTest::oldInstallAndNewPorts,failures);
        run("cache failure/late NSD",InternalReconnectTest::lateDiscoveryAfterCacheFailure,failures);
        run("attempt bounds",InternalReconnectTest::boundedAttempts,failures);
        run("deadlines",InternalReconnectTest::deadlines,failures);
        run("foreground lifecycle",InternalReconnectTest::foregroundLifecycle,failures);
        run("old backend stopping",InternalReconnectTest::stoppingRace,failures);
        run("backend selection",InternalReconnectTest::backendSwitch,failures);
        run("persistent pause",InternalReconnectTest::persistentPauseAndRetry,failures);
        run("process reopen",InternalReconnectTest::processReopen,failures);
        run("authentication",InternalReconnectTest::authentication,failures);
        run("cached TLS rejection/new endpoint",InternalReconnectTest::cachedAuthenticationFallback,failures);
        run("cached TLS rejection/offline",InternalReconnectTest::cachedAuthenticationOffline,failures);
        run("fresh auth rejection/alternative succeeds",InternalReconnectTest::freshAuthenticationFallback,failures);
        run("ineligible states",InternalReconnectTest::ineligible,failures);
        if(!failures.isEmpty())throw new AssertionError(String.join("; ",failures));
        System.out.println("InternalReconnectTest PASS: "+checks+" actual-helper checks; process reopen, preferences, NSD/ports, lifecycle, retry bounds and auth classification");
        System.out.println("Fake boundaries only: no Android UI, ADB network, pairing, identity clearing or NFC commands executed.");
    }
    private static void run(String label,Runnable scenario,List<String> failures){
        try{scenario.run();}catch(AssertionError error){failures.add(label+": "+error.getMessage());}
    }
    private static final class Fixture {
        final Context context;
        final InternalActivationController controller=new InternalActivationController();
        final InternalReconnect reconnect;
        final Object host=new Object();
        Fixture(){this(new Context());}
        Fixture(Context context){this.context=context;reconnect=new InternalReconnect(context,controller);}
        SharedPreferences prefs(){return context.getSharedPreferences("internal-activation",Context.MODE_PRIVATE);}
        void saved(int port){prefs().edit().putInt("lastConnectionPort",port).apply();}
        void start(){reconnect.enter(host);}
        void failure(boolean auth){controller.busy=false;reconnect.failed(auth,auth?"authentication rejected":"connection refused");}
        void ports(Integer... expected){check(controller.ports.equals(Arrays.asList(expected)),"requested ports "+controller.ports+" expected "+Arrays.asList(expected));}
    }
    private static void reset(){Handler.reset();InternalAdbDiscovery.instances.clear();AdbConnector.reset();InternalBackend.ready=InternalBackend.stopping=false;Build.VERSION.SDK_INT=36;}
    private static void safe(){check(AdbConnector.savedIdentity&&AdbConnector.cleared==0&&AdbConnector.paired==0&&AdbConnector.nfcCalls==0,"existing identity retained; no pairing, erasure or NFC");}
    private static void oldInstallAndNewPorts(){
        reset();Fixture f=new Fixture();f.saved(41000);f.start();
        check(f.reconnect.running()&&InternalAdbDiscovery.instances.size()==1,"v1 install with an identity and no new pairing flag starts recovery");
        InternalAdbDiscovery nsd=InternalAdbDiscovery.latest();nsd.found(true,31000);f.ports();
        nsd.found(false,42000);f.ports(42000);
        Handler.advance(1200);f.ports(42000);
        check(!f.reconnect.needsPairing(),"pairing NSD does not demand a new pairing");safe();
    }
    private static void lateDiscoveryAfterCacheFailure(){
        reset();Fixture f=new Fixture();f.saved(41000);f.start();Handler.advance(1200);f.ports(41000);
        f.failure(false);Handler.advance(350);check(f.reconnect.running(),"cache connection failure waits for fresh discovery");
        InternalAdbDiscovery.latest().found(false,42000);f.ports(41000,42000);
        f.failure(false);InternalAdbDiscovery.latest().found(false,41000);Handler.advance(350);f.ports(41000,42000);
        check(!f.reconnect.needsPairing(),"network error does not mark a saved identity rejected");safe();
    }
    private static void boundedAttempts(){
        reset();Fixture f=new Fixture();f.start();InternalAdbDiscovery nsd=InternalAdbDiscovery.latest();
        nsd.found(false,42000);nsd.found(false,42001);nsd.found(false,42002);nsd.found(false,42003);
        f.ports(42000);f.failure(false);Handler.advance(350);f.ports(42000,42001);
        f.failure(false);Handler.advance(350);f.ports(42000,42001,42002);
        f.failure(false);Handler.advance(1000);check(!f.reconnect.running(),"third failed connection exhausts round");
        nsd.found(false,49000);Handler.advance(70000);f.ports(42000,42001,42002);safe();
    }
    private static void deadlines(){
        reset();Fixture waiting=new Fixture();waiting.start();Handler.advance(11999);check(waiting.reconnect.running(),"discovery remains active before deadline");
        Handler.advance(1);check(!waiting.reconnect.running()&&!waiting.reconnect.needsPairing(),"empty NSD finishes at discovery deadline without erasing pairing");safe();
        reset();Fixture hung=new Fixture();hung.saved(41000);hung.start();Handler.advance(1200);hung.ports(41000);
        Handler.advance(68799);check(hung.reconnect.running(),"in-flight operation can continue to hard deadline");
        Handler.advance(1);check(!hung.reconnect.running()&&hung.controller.cancelCalls>0,"70-second deadline cancels in-flight automatic activation");
        InternalAdbDiscovery.latest().found(false,49000);Handler.advance(5000);hung.ports(41000);safe();
    }
    private static void foregroundLifecycle(){
        reset();Fixture f=new Fixture();f.start();InternalAdbDiscovery original=InternalAdbDiscovery.latest();
        f.reconnect.enter(f.host);Object editor=new Object();f.reconnect.enter(editor);
        check(InternalAdbDiscovery.instances.size()==1,"repeated resume and another activity share one attempt");
        f.reconnect.leave(f.host);Handler.advance(900);check(f.reconnect.running(),"one remaining app host prevents background cancellation");
        f.reconnect.leave(editor);original.found(false,41000);f.ports();Handler.advance(799);check(f.reconnect.running(),"activity handoff has a brief grace period");
        f.reconnect.enter(f.host);Handler.advance(1);check(f.reconnect.running()&&!original.closed,"return during grace keeps current discovery");
        f.ports(41000);
        f.reconnect.leave(f.host);Handler.advance(800);check(!f.reconnect.running()&&original.closed,"actual background closes discovery");
        original.found(false,42000);f.ports(41000);
        f.start();check(f.reconnect.running()&&InternalAdbDiscovery.instances.size()==2,"next foreground is allowed a fresh recovery round");safe();
    }
    private static void backendSwitch(){
        reset();Fixture f=new Fixture();f.start();InternalAdbDiscovery old=InternalAdbDiscovery.latest();old.found(false,41000);
        BackendMode.set(f.context,BackendMode.SHIZUKU);f.reconnect.backendChanged();
        check(!f.reconnect.running()&&old.closed&&!f.controller.busy,"switching to external backend cancels automatic internal operation");
        old.found(false,42000);Handler.advance(70000);f.ports(41000);
        f.reconnect.leave(f.host);Handler.advance(800);f.start();check(!f.reconnect.running(),"external selection never launches internal recovery");safe();
    }
    private static void stoppingRace(){
        reset();Fixture f=new Fixture();InternalBackend.stopping=true;f.start();
        Handler.advance(249);check(!f.reconnect.running()&&InternalAdbDiscovery.instances.isEmpty(),"old backend cleanup cannot overlap a new recovery");
        f.controller.busy=false;InternalBackend.stopping=false;Handler.advance(1);
        check(f.reconnect.running()&&InternalAdbDiscovery.instances.size()==1,"cleanup completion starts recovery without another resume");safe();
        reset();Fixture hung=new Fixture();hung.controller.busy=true;InternalBackend.stopping=true;hung.start();Handler.advance(10000);
        hung.controller.busy=false;InternalBackend.stopping=false;Handler.advance(5000);
        check(!hung.reconnect.running()&&InternalAdbDiscovery.instances.isEmpty(),"cleanup polling is bounded at ten seconds");
        hung.reconnect.leave(hung.host);Handler.advance(800);hung.start();check(hung.reconnect.running(),"a later foreground may try after cleanup timed out");safe();
        reset();Fixture paused=new Fixture();paused.controller.busy=true;InternalBackend.stopping=true;paused.start();paused.reconnect.pause();
        paused.controller.busy=false;InternalBackend.stopping=false;Handler.advance(1000);
        check(!paused.reconnect.running()&&InternalAdbDiscovery.instances.isEmpty(),"manual pause invalidates waiting-for-cleanup callbacks");safe();
    }
    private static void persistentPauseAndRetry(){
        reset();Fixture f=new Fixture();f.saved(41000);f.start();f.reconnect.pause();
        check(!f.reconnect.running()&&f.prefs().getBoolean("reconnectPaused",false),"manual pause is persisted");
        Fixture reopened=new Fixture(f.context);reopened.start();check(!reopened.reconnect.running(),"manual pause survives new helper/process simulation");
        check(reopened.reconnect.retry()&&reopened.reconnect.running(),"explicit reconnect resumes saved identity");
        check(!reopened.prefs().getBoolean("reconnectPaused",true),"explicit retry clears pause");Handler.advance(1200);reopened.ports(41000);safe();
    }
    private static void processReopen(){
        reset();Fixture first=new Fixture();first.start();InternalAdbDiscovery old=InternalAdbDiscovery.latest();old.found(false,42500);
        first.controller.busy=false;first.reconnect.connected(42500);
        check(!first.reconnect.running()&&old.closed&&first.reconnect.savedPort()==42500,"verified success persists connection endpoint");
        // Owner death is represented by a fresh controller/helper while private preferences and identity survive.
        Handler.reset();Fixture reopened=new Fixture(first.context);reopened.start();Handler.advance(1200);reopened.ports(42500);
        reopened.controller.busy=false;reopened.reconnect.connected(42500);
        check(!reopened.reconnect.running()&&!reopened.reconnect.needsPairing(),"reopened app returns to ready path without fresh pairing");safe();
    }
    private static void authentication(){
        reset();Fixture f=new Fixture();f.start();InternalAdbDiscovery.latest().found(false,41000);
        f.failure(false);check(!f.reconnect.needsPairing(),"connection failure retains pairing classification");
        InternalAdbDiscovery.latest().found(false,42000);f.failure(true);
        check(f.reconnect.running()&&!f.reconnect.needsPairing(),"advertised endpoint rejection still allows discovery alternatives");
        Handler.advance(11999);check(f.reconnect.running()&&!f.reconnect.needsPairing(),"fresh authentication failure does not terminate discovery early");
        Handler.advance(1);check(!f.reconnect.running()&&f.reconnect.needsPairing(),"fresh endpoint authentication rejection requests pairing after alternatives expire");safe();
        Handler.reset();Fixture reopened=new Fixture(f.context);reopened.start();
        check(!reopened.reconnect.running()&&!reopened.reconnect.retry(),"authentication rejection survives restart without an automatic retry loop");
        reopened.reconnect.paired();check(!reopened.reconnect.needsPairing()&&reopened.reconnect.retry(),"successful explicit pairing permits recovery again");safe();
    }
    private static void cachedAuthenticationFallback(){
        reset();Fixture f=new Fixture();f.saved(41000);f.start();Handler.advance(1200);f.ports(41000);
        InternalAdbDiscovery nsd=InternalAdbDiscovery.latest();f.failure(true);Handler.advance(350);
        check(f.reconnect.running()&&!f.reconnect.needsPairing(),"cached port TLS/protocol rejection is not proof of revoked pairing");
        nsd.found(false,42000);f.ports(41000,42000);
        f.controller.busy=false;f.reconnect.connected(42000);
        check(!f.reconnect.running()&&!f.reconnect.needsPairing()&&f.reconnect.savedPort()==42000,"late current endpoint can restore and replace stale cache without pairing");
        Handler.advance(70000);f.ports(41000,42000);safe();
    }
    private static void cachedAuthenticationOffline(){
        reset();Fixture f=new Fixture();f.saved(41000);f.start();Handler.advance(1200);f.failure(true);
        Handler.advance(10800);
        check(!f.reconnect.running()&&!f.reconnect.needsPairing(),"no wireless-debugging advertisement leaves cache-only rejection retryable");
        check(f.reconnect.savedPort()==41000&&f.reconnect.hasIdentity(),"offline cached rejection retains endpoint and identity");
        Handler.reset();Fixture reopened=new Fixture(f.context);reopened.start();Handler.advance(1200);reopened.ports(41000);
        check(reopened.reconnect.running()&&!reopened.reconnect.needsPairing(),"cached rejection never persists a re-pair requirement across reopen");safe();
    }
    private static void freshAuthenticationFallback(){
        reset();Fixture f=new Fixture();f.start();InternalAdbDiscovery nsd=InternalAdbDiscovery.latest();
        nsd.found(false,41000);f.failure(true);Handler.advance(350);
        check(!f.reconnect.needsPairing()&&f.reconnect.running(),"fresh rejection waits for possible current replacement");
        nsd.found(false,42000);f.ports(41000,42000);
        f.controller.busy=false;f.reconnect.connected(42000);Handler.advance(12000);
        check(!f.reconnect.needsPairing()&&!f.reconnect.running(),"successful alternative clears the deferred authentication failure");safe();
    }
    private static void ineligible(){
        for(int kind=0;kind<6;kind++){
            reset();Fixture f=new Fixture();
            if(kind==0)AdbConnector.savedIdentity=false;
            if(kind==1)Build.VERSION.SDK_INT=29;
            if(kind==2)InternalBackend.ready=true;
            if(kind==3)f.controller.busy=true;
            if(kind==4)BackendMode.set(f.context,BackendMode.ROOT);
            if(kind==5)BackendMode.set(f.context,BackendMode.SHIZUKU);
            f.start();Handler.advance(70000);
            check(!f.reconnect.running()&&InternalAdbDiscovery.instances.isEmpty()&&f.controller.ports.isEmpty(),"ineligible state starts no recovery: "+kind);
            check(AdbConnector.cleared==0&&AdbConnector.paired==0&&AdbConnector.nfcCalls==0,"ineligible state invokes no privileged action");
        }
    }
    private static void check(boolean good,String label){checks++;if(!good)throw new AssertionError(label);}
}

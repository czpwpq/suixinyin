package org.nfcresearch.qpolllab.activation;

import java.lang.reflect.Field;
import java.util.Set;

/** Recovery decisions run without ADB, Android or any privileged action. */
public final class ReconnectPlanTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        eligibility();changedPorts();cacheFallback();duplicatesAndLimits();waitingAndCancel();queueBound();invalidPorts();
        System.out.println("ReconnectPlanTest PASS: "+checks+" checks; foreground eligibility, dynamic ports, cache fallback, bounded attempts and cancellation");
    }
    private static void eligibility(){
        check(ReconnectPlan.eligible(true,true,true,false,false,false),"paired internal client may recover");
        check(!ReconnectPlan.eligible(false,true,true,false,false,false),"external Shizuku or Root selection is untouched");
        check(!ReconnectPlan.eligible(true,false,true,false,false,false),"unsupported Android cannot start wireless activation");
        check(!ReconnectPlan.eligible(true,true,false,false,false,false),"a new install cannot auto-pair or create an identity");
        check(!ReconnectPlan.eligible(true,true,true,true,false,false),"explicit user stop wins over automatic recovery");
        check(!ReconnectPlan.eligible(true,true,true,false,true,false),"ready backend must not be restarted");
        check(!ReconnectPlan.eligible(true,true,true,false,false,true),"an existing activation or pairing cannot be replaced");
        for(int flags=0;flags<64;flags++){
            boolean internal=(flags&1)!=0,supported=(flags&2)!=0,identity=(flags&4)!=0;
            boolean stopped=(flags&8)!=0,ready=(flags&16)!=0,busy=(flags&32)!=0;
            check(ReconnectPlan.eligible(internal,supported,identity,stopped,ready,busy)==(flags==7),"eligibility truth table "+flags);
        }
    }
    private static void changedPorts(){
        ReconnectPlan plan=new ReconnectPlan(41000);
        plan.offerDiscovered(42000);
        check(plan.nextPort()==42000,"fresh local endpoint precedes saved port");
        plan.offerDiscovered(42001);
        check(plan.nextPort()==42001,"changed port may recover after the first endpoint fails");
        check(plan.nextPort()==41000,"saved port remains a last untried fallback");
        check(!plan.isActive()&&plan.attemptCount()==3,"third issued attempt exhausts round");
    }
    private static void cacheFallback(){
        ReconnectPlan plan=new ReconnectPlan(41000);
        check(plan.nextPort()==41000,"saved port works without NSD support");
        check(plan.nextPort()==0&&plan.isActive(),"stale cache failure can wait for NSD");
        plan.offerDiscovered(42000);
        check(plan.nextPort()==42000,"late discovery can recover from stale cache");
        plan.offerDiscovered(41000);
        check(plan.nextPort()==0,"rediscovered stale cache is not tried twice");
        ReconnectPlan promoted=new ReconnectPlan(41000);
        promoted.offerDiscovered(42000);promoted.offerDiscovered(41000);promoted.offerDiscovered(43000);
        check(promoted.nextPort()==42000&&promoted.nextPort()==41000&&promoted.nextPort()==43000,"discovering cache promotes it into fresh order without duplicating it");
    }
    private static void duplicatesAndLimits(){
        ReconnectPlan plan=new ReconnectPlan(0);
        for(int port=41000;port<41012;port++){plan.offerDiscovered(port);plan.offerDiscovered(port);}
        for(int i=0;i<3;i++){check(plan.nextPort()==41000+i,"unique bounded candidate "+i);check(plan.attemptCount()==i+1,"attempt count is issued work "+i);}
        plan.offerDiscovered(49000);
        check(plan.nextPort()==0&&!plan.isActive(),"queued or late ports cannot bypass retry budget");
    }
    private static void waitingAndCancel(){
        ReconnectPlan plan=new ReconnectPlan(0);
        for(int i=0;i<10;i++)check(plan.nextPort()==0&&plan.isActive()&&plan.attemptCount()==0,"empty queue waits without spending attempts "+i);
        plan.offerDiscovered(65535);plan.cancel();
        check(!plan.isActive()&&plan.nextPort()==0&&plan.attemptCount()==0,"cancel discards pending work");
        plan.offerDiscovered(1);plan.cancel();
        check(!plan.isActive()&&plan.nextPort()==0,"late NSD update cannot revive a cancelled round");
        ReconnectPlan attempted=new ReconnectPlan(1);check(attempted.nextPort()==1,"smallest valid port accepted");attempted.cancel();
        check(attempted.attemptCount()==1&&attempted.nextPort()==0,"cancel preserves diagnostic attempt count");
    }
    private static void queueBound()throws Exception{
        for(int cache:new int[]{0,41000}){
            ReconnectPlan plan=new ReconnectPlan(cache);
            for(int port=42000;port<43000;port++)plan.offerDiscovered(port);
            Field candidates=ReconnectPlan.class.getDeclaredField("discovered"),saved=ReconnectPlan.class.getDeclaredField("cachedPort");
            candidates.setAccessible(true);saved.setAccessible(true);
            check(((Set<?>)candidates.get(plan)).size()+(saved.getInt(plan)==0?0:1)<=8,"resource bound includes cached fallback");
            check(plan.nextPort()==42000,"overflow does not replace the first discovered endpoint");
            plan.cancel();check(((Set<?>)candidates.get(plan)).isEmpty()&&saved.getInt(plan)==0,"cancel clears queued candidates");
        }
    }
    private static void invalidPorts(){
        for(int bad:new int[]{Integer.MIN_VALUE,-1,0,65536,Integer.MAX_VALUE}){
            ReconnectPlan plan=new ReconnectPlan(bad);plan.offerDiscovered(bad);
            check(plan.nextPort()==0&&plan.attemptCount()==0&&plan.isActive(),"invalid endpoint is never connected: "+bad);
            plan.offerDiscovered(65535);check(plan.nextPort()==65535,"invalid cache does not poison a later valid endpoint");
        }
    }
    private static void check(boolean good,String label){checks++;if(!good)throw new AssertionError(label);}
}

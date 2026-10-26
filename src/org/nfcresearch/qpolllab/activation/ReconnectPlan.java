package org.nfcresearch.qpolllab.activation;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** One bounded recovery round. A saved port is only a fallback, never proof of authorization. */
public final class ReconnectPlan {
    private static final int MAX_ATTEMPTS=3,MAX_QUEUED=8;
    private final Set<Integer> discovered=new LinkedHashSet<>();
    private final Set<Integer> attempted=new LinkedHashSet<>();
    private int cachedPort;
    private boolean active=true;

    /** Zero or an invalid persisted port means that discovery must supply a candidate. */
    public ReconnectPlan(int cachedPort){this.cachedPort=valid(cachedPort)?cachedPort:0;}

    public static boolean eligible(boolean internalSelected,boolean supported,boolean identityAvailable,
                                   boolean suspended,boolean ready,boolean busy){
        return internalSelected&&supported&&identityAvailable&&!suspended&&!ready&&!busy;
    }

    /** Invalid, duplicate, late and already attempted endpoint updates cannot add work. */
    public synchronized void offerDiscovered(int port){
        if(!active||!valid(port)||attempted.contains(port)||discovered.contains(port))return;
        if(port==cachedPort)cachedPort=0; // Promote a confirmed discovery ahead of stale fallback.
        if(discovered.size()+(cachedPort==0?0:1)<MAX_QUEUED)discovered.add(port);
    }

    /** Returns a fresh discovery first, then the untried cache; zero may mean wait for discovery. */
    public synchronized int nextPort(){
        if(!active)return 0;
        int port;
        Iterator<Integer> candidates=discovered.iterator();
        if(candidates.hasNext()){port=candidates.next();candidates.remove();}
        else{port=cachedPort;cachedPort=0;}
        if(port==0)return 0;
        attempted.add(port);
        if(attempted.size()>=MAX_ATTEMPTS){active=false;discovered.clear();cachedPort=0;}
        return port;
    }

    public synchronized int attemptCount(){return attempted.size();}
    public synchronized boolean isActive(){return active;}
    public synchronized void cancel(){active=false;discovered.clear();cachedPort=0;}
    private static boolean valid(int port){return port>=1&&port<=65535;}
}

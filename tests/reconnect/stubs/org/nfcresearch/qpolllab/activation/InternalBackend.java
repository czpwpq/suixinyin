package org.nfcresearch.qpolllab.activation;
public final class InternalBackend {
    public static boolean ready,stopping;
    public static boolean isReady(){return ready;}
    public static boolean isStopping(){return stopping;}
}

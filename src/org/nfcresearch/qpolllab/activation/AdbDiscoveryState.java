package org.nfcresearch.qpolllab.activation;

/** Per discovered service: bounded retry, change notification and explicit lost/closed state. */
public final class AdbDiscoveryState {
    private boolean active=true;private int attempts,port;
    public int beginResolve(){return active&&attempts<4?++attempts:0;}
    public long retryDelay(){return active&&attempts>0&&attempts<4?attempts*1200L:-1;}
    public void rediscovered(){if(active)attempts=0;}
    public boolean adopt(int value){if(!active)return false;if(value<1||value>65535)throw new IllegalArgumentException("Invalid discovered port");attempts=0;if(port==value)return false;port=value;return true;}
    public int port(){return port;}
    public int lost(){int previous=port;port=0;return previous;}
    public int close(){int previous=lost();active=false;return previous;}
}

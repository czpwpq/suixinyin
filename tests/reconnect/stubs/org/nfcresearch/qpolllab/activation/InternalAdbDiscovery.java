package org.nfcresearch.qpolllab.activation;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
/** NSD boundary can emit current or deliberately late callbacks; no discovery/network API is invoked. */
public final class InternalAdbDiscovery implements AutoCloseable {
    public interface Listener {
        void found(boolean pairing,int port);
        void status(String text);
        default void diagnostic(String text){status(text);}
    }
    public static final List<InternalAdbDiscovery> instances=new ArrayList<>();
    public final Listener listener;
    public boolean started,closed;
    public InternalAdbDiscovery(Context context,Listener listener){this.listener=listener;instances.add(this);}
    public void start(){started=true;}
    public void close(){closed=true;}
    public void found(boolean pairing,int port){listener.found(pairing,port);}
    public static InternalAdbDiscovery latest(){return instances.get(instances.size()-1);}
}

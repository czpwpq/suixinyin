package org.nfcresearch.qpolllab;
import java.util.ArrayList;
import java.util.List;
/** Controller boundary records requests; it cannot execute ADB commands or NFC. */
public final class InternalActivationController {
    public boolean busy;
    public int cancelCalls;
    public final List<Integer> ports=new ArrayList<>(),discovered=new ArrayList<>();
    public final List<String> statuses=new ArrayList<>(),diagnostics=new ArrayList<>();
    public boolean operationBusy(){return busy||org.nfcresearch.qpolllab.activation.InternalBackend.isStopping();}
    public void reconnectStatus(String text){statuses.add(text);}
    public void reconnectDiagnostic(String text){diagnostics.add(text);}
    public void discoveredConnectionPort(int port){discovered.add(port);}
    public void activateAutomatically(int port){if(busy)throw new AssertionError("Duplicate activation while busy");ports.add(port);busy=true;}
    public void cancelAutomaticActivation(){cancelCalls++;busy=false;}
}

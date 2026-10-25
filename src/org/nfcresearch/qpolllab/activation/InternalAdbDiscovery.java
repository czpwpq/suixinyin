package org.nfcresearch.qpolllab.activation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only endpoint discovery. Actual ADB connections remain fixed to 127.0.0.1. */
public final class InternalAdbDiscovery implements AutoCloseable {
    public interface Listener {
        void found(boolean pairing,int port);
        void status(String message);
        default void diagnostic(String message){status(message);}
        default void lost(boolean pairing,int port){}
    }
    private static final int MAX_SERVICES=24;
    private final Context context;
    private final NsdManager manager;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> searches=new ArrayList<>();
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final ArrayDeque<Entry> queue=new ArrayDeque<>();
    private boolean closed=true;
    private int epoch,resolveSerial;
    private Entry active;
    private NsdManager.ResolveListener activeResolver;
    public InternalAdbDiscovery(Context context,Listener listener){this.context=context.getApplicationContext();manager=(NsdManager)this.context.getSystemService(Context.NSD_SERVICE);this.listener=listener;}
    public void start(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::start);return;}
        stop();closed=false;
        if(manager==null){listener.status("此系统没有可用的本机服务发现，请手动填写端口");return;}
        List<byte[]> local=localAddresses();diag("NSD 启动 · API "+Build.VERSION.SDK_INT+" · 本机地址数 "+local.size()+" · "+(Build.VERSION.SDK_INT>=34?"持续服务更新":"串行解析"));
        final int generation=epoch;search(ActivationInputs.PAIR_SERVICE,true,generation);search(ActivationInputs.CONNECT_SERVICE,false,generation);
    }
    private void search(String type,boolean pairing,int generation){
        NsdManager.DiscoveryListener search=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){main.post(()->{if(current(generation))diag("NSD 搜索开始 · "+safe(type));});}
            public void onServiceFound(NsdServiceInfo info){main.post(()->discovered(info,pairing,generation));}
            public void onServiceLost(NsdServiceInfo info){main.post(()->{if(current(generation)){Entry entry=entries.remove(key(info,pairing));if(entry!=null)remove(entry,true);}});}
            public void onDiscoveryStopped(String type){main.post(()->{if(current(generation))diag("NSD 搜索停止 · "+safe(type));});}
            public void onStartDiscoveryFailed(String type,int code){main.post(()->{if(current(generation))listener.status("自动发现启动失败 · "+kind(pairing)+" · code="+code+"，可手动填写本机端口");});try{manager.stopServiceDiscovery(this);}catch(Exception ignored){}}
            public void onStopDiscoveryFailed(String type,int code){main.post(()->{if(current(generation))diag("NSD 停止回调失败 · code="+code);});}
        };
        searches.add(search);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,search);}catch(Exception error){searches.remove(search);listener.status("自动发现不可用 · "+ActivationInputs.errorLabel(error)+"，可手动填写本机端口");}
    }
    private void discovered(NsdServiceInfo info,boolean pairing,int generation){
        if(!current(generation)||info==null)return;String type=info.getServiceType();
        diag("NSD 发现 · "+kind(pairing)+" · type="+safe(type)+" · id="+label(info));
        // Some vendor callbacks omit type. The registered query still supplies its kind.
        boolean missing=type==null||type.trim().isEmpty();
        if(!missing&&(pairing?!ActivationInputs.pairingService(type):!ActivationInputs.connectService(type))){diag("NSD 过滤 · 服务类型不匹配："+safe(type));return;}
        String key=key(info,pairing);Entry entry=entries.get(key);
        if(entry!=null){entry.info=info;if(entry.watcher==null&&!entry.queued&&entry!=active){entry.state.rediscovered();enqueue(entry,0);}return;}
        if(entries.size()>=MAX_SERVICES){diag("NSD 过滤 · 服务数量达到上限，等待当前解析完成");return;}
        entry=new Entry(key,info,pairing,generation);entries.put(key,entry);
        if(Build.VERSION.SDK_INT>=34)Api34.watch(this,entry);else enqueue(entry,0);
    }
    private void enqueue(Entry entry,long delay){
        if(!current(entry)||entry.queued||entry==active)return;entry.queued=true;
        main.postDelayed(()->{if(!current(entry)){entry.queued=false;return;}queue.addLast(entry);resolveNext();},delay);
    }
    private void resolveNext(){
        if(closed||active!=null)return;
        Entry entry;
        do{entry=queue.pollFirst();if(entry==null)return;entry.queued=false;}while(!current(entry));
        final Entry pending=entry;int attempt=pending.state.beginResolve();if(attempt==0){resolveNext();return;}active=pending;final int serial=++resolveSerial;
        diag("NSD 解析 · "+kind(pending.pairing)+" · id="+label(pending.info)+" · 第"+attempt+"次");
        NsdManager.ResolveListener resolver=new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo info,int code){main.post(()->{if(owns(pending,serial)){finishResolve();retry(pending,"解析失败 code="+code);resolveNext();}});}
            public void onServiceResolved(NsdServiceInfo info){main.post(()->{if(!owns(pending,serial))return;finishResolve();boolean accepted=resolved(pending,info,"resolve");if(!accepted)retry(pending,"结果尚无可验证的本机地址/端口");else if(pending.watcher==null)enqueue(pending,15000);resolveNext();});}
        };
        activeResolver=resolver;
        try{manager.resolveService(pending.info,resolver);}catch(Exception error){if(owns(pending,serial)){finishResolve();retry(pending,"解析调用失败 "+ActivationInputs.errorLabel(error));resolveNext();}return;}
        main.postDelayed(()->{if(owns(pending,serial)){if(Build.VERSION.SDK_INT>=34)Api34.stopResolve(manager,resolver);finishResolve();retry(pending,"解析超时，队列继续处理其他服务");resolveNext();}},6000);
    }
    private boolean owns(Entry entry,int serial){return current(entry)&&active==entry&&serial==resolveSerial;}
    private void finishResolve(){active=null;activeResolver=null;}
    private void retry(Entry entry,String reason){
        if(!current(entry))return;diag("NSD "+reason+" · "+kind(entry.pairing)+" · id="+label(entry.info));
        long delay=entry.state.retryDelay();if(delay>=0){diag("NSD 将在 "+delay+"ms 后重试；失败不会永久占住发现记录");enqueue(entry,delay);}
        else diag("NSD 本轮解析重试已用完；仍可接收服务更新或重新发现");
    }
    private boolean resolved(Entry entry,NsdServiceInfo info,String source){
        if(!current(entry)||info==null)return false;
        List<InetAddress> addresses=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=34)addresses.addAll(Api34.addresses(info));
        if(addresses.isEmpty()&&info.getHost()!=null)addresses.add(info.getHost());
        List<byte[]> local=localAddresses();int port=info.getPort();StringBuilder candidates=new StringBuilder();boolean own=false;
        for(InetAddress address:addresses){if(address==null)continue;if(candidates.length()>0)candidates.append(',');candidates.append(safe(address.getHostAddress()));if(ActivationInputs.ownAddress(address.getAddress(),local))own=true;}
        diag("NSD "+source+" · "+kind(entry.pairing)+" · id="+label(info)+" · port="+port+" · host="+(candidates.length()==0?"<尚无地址>":candidates)+" · 本机地址数="+local.size());
        if(port<1||port>65535){diag("NSD 暂不采用 · 端口尚未有效");return false;}
        if(!own){diag(addresses.isEmpty()?"NSD 暂不采用 · 尚未收到地址，等待后续更新":"NSD 过滤 · 地址不属于本机；不会连接该局域网设备");return false;}
        entry.info=info;
        if(entry.state.adopt(port)){diag("NSD 本机验证通过 · "+kind(entry.pairing)+" · 连接目标仍为127.0.0.1");listener.found(entry.pairing,port);}
        return true;
    }
    private List<byte[]> localAddresses(){
        List<byte[]> result=new ArrayList<>();int links=0;
        try{
            ConnectivityManager connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connectivity!=null){Network active=connectivity.getActiveNetwork();if(active!=null)links+=addLinks(result,connectivity.getLinkProperties(active),0);for(Network network:connectivity.getAllNetworks())links+=addLinks(result,connectivity.getLinkProperties(network),0);}
        }catch(Exception error){diag("NSD LinkProperties不可读 · "+ActivationInputs.errorLabel(error)+"；继续检查本机网卡");}
        int interfaces=0;
        try{Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();if(networks!=null)while(networks.hasMoreElements()){Enumeration<InetAddress> addresses=networks.nextElement().getInetAddresses();while(addresses.hasMoreElements())if(addAddress(result,addresses.nextElement()))interfaces++;}}
        catch(Exception error){diag("NSD 本机网卡地址不可读 · "+ActivationInputs.errorLabel(error));}
        diag("NSD 地址来源 · LinkProperties="+links+" · NetworkInterface="+interfaces+" · 去重="+result.size()+" · 本机="+addressSummary(result));return result;
    }
    private static String addressSummary(List<byte[]> addresses){StringBuilder result=new StringBuilder();for(byte[] value:addresses){if(result.length()>220){result.append("…");break;}try{if(result.length()>0)result.append(',');result.append(InetAddress.getByAddress(value).getHostAddress());}catch(Exception ignored){}}return result.length()==0?"<无可读地址>":result.toString();}
    private static int addLinks(List<byte[]> result,LinkProperties links,int depth){if(links==null)return 0;int count=0;for(LinkAddress address:links.getLinkAddresses())if(addAddress(result,address.getAddress()))count++;return count;}
    private static boolean addAddress(List<byte[]> result,InetAddress address){if(address==null||address.isAnyLocalAddress()||address.isMulticastAddress())return false;byte[] bytes=ActivationInputs.addressBytes(address.getAddress());for(byte[] old:result)if(Arrays.equals(old,bytes))return false;result.add(bytes);return true;}
    private boolean current(int generation){return !closed&&generation==epoch;}
    private boolean current(Entry entry){return entry!=null&&current(entry.generation)&&entries.get(entry.key)==entry;}
    private static String key(NsdServiceInfo info,boolean pairing){String network="";if(Build.VERSION.SDK_INT>=33)try{network=String.valueOf(info.getNetwork());}catch(Exception ignored){}return(pairing?"p:":"c:")+info.getServiceName()+":"+network;}
    private static String label(NsdServiceInfo info){return Integer.toHexString(String.valueOf(info.getServiceName()).hashCode());}
    private static String kind(boolean pairing){return pairing?"配对":"连接";}
    private static String safe(String text){if(text==null)return "<null>";text=text.replace('\n',' ').replace('\r',' ');return text.length()>160?text.substring(0,160):text;}
    private void diag(String message){if(!closed)listener.diagnostic(message);}
    private void remove(Entry entry,boolean lost){
        entry.queued=false;queue.remove(entry);if(active==entry){if(Build.VERSION.SDK_INT>=34&&activeResolver!=null)Api34.stopResolve(manager,activeResolver);resolveSerial++;finishResolve();resolveNext();}
        if(Build.VERSION.SDK_INT>=34)Api34.unwatch(this,entry);
        int previous=entry.state.close();if(lost){diag("NSD 服务离场 · "+kind(entry.pairing)+" · id="+label(entry.info));if(previous>0)listener.lost(entry.pairing,previous);}
    }
    private void stop(){
        epoch++;closed=true;queue.clear();for(Entry entry:new ArrayList<>(entries.values()))remove(entry,false);entries.clear();resolveSerial++;finishResolve();for(NsdManager.DiscoveryListener search:searches)try{manager.stopServiceDiscovery(search);}catch(Exception ignored){}searches.clear();
    }
    @Override public void close(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::close);return;}stop();}
    private static final class Entry {
        final String key;final boolean pairing;final int generation;
        final AdbDiscoveryState state=new AdbDiscoveryState();NsdServiceInfo info;boolean queued;Object watcher;
        Entry(String key,NsdServiceInfo info,boolean pairing,int generation){this.key=key;this.info=info;this.pairing=pairing;this.generation=generation;}
    }
    /** Keep API34-only interface references out of older platforms' outer class verifier. */
    private static final class Api34 {
        static List<InetAddress> addresses(NsdServiceInfo info){try{List<InetAddress> values=info.getHostAddresses();return values==null?new ArrayList<>():values;}catch(Exception ignored){return new ArrayList<>();}}
        static void stopResolve(NsdManager manager,NsdManager.ResolveListener listener){try{manager.stopServiceResolution(listener);}catch(Exception ignored){}}
        static void unwatch(InternalAdbDiscovery owner,Entry entry){Object watcher=entry.watcher;entry.watcher=null;if(watcher instanceof NsdManager.ServiceInfoCallback)try{owner.manager.unregisterServiceInfoCallback((NsdManager.ServiceInfoCallback)watcher);}catch(Exception ignored){}}
        static void watch(InternalAdbDiscovery owner,Entry entry){
            NsdManager.ServiceInfoCallback callback=new NsdManager.ServiceInfoCallback(){
                public void onServiceInfoCallbackRegistrationFailed(int code){if(!owner.current(entry)||entry.watcher!=this)return;entry.watcher=null;owner.diag("NSD 持续解析注册失败 code="+code+"，改用串行解析");owner.enqueue(entry,0);}
                public void onServiceUpdated(NsdServiceInfo info){if(owner.current(entry)&&entry.watcher==this)owner.resolved(entry,info,"更新");}
                public void onServiceLost(){if(owner.current(entry)&&entry.watcher==this){owner.diag("NSD 持续更新报告离场 · "+kind(entry.pairing));int old=entry.state.lost();if(old>0)owner.listener.lost(entry.pairing,old);}}
                public void onServiceInfoCallbackUnregistered(){}
            };
            entry.watcher=callback;
            try{owner.manager.registerServiceInfoCallback(entry.info,owner.main::post,callback);owner.diag("NSD 已订阅持续解析 · "+kind(entry.pairing)+" · id="+label(entry.info));}
            catch(Exception error){entry.watcher=null;owner.diag("NSD 持续解析不可用 · "+ActivationInputs.errorLabel(error)+"，改用串行解析");owner.enqueue(entry,0);return;}
            owner.main.postDelayed(()->{if(owner.current(entry)&&entry.watcher==callback&&entry.state.port()==0){owner.diag("NSD 尚未收到完整本机服务更新，补充一次带重试的解析");owner.enqueue(entry,0);}},8000);
        }
    }
}

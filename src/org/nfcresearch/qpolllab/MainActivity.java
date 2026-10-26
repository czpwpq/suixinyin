package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import android.os.Parcel;

public final class MainActivity extends Activity implements InternalActivationController.Listener {
    private final Handler main=new Handler();
    private final ArrayList<View> idleControls=new ArrayList<>();
    private final StringBuilder transcript=new StringBuilder();
    private final int[] durations={5,10,30,60},powerTimes={500,1000,2000,3000,4000,5000},pollTimes={0,10,20,50,100,200,300,400,500},blockSizes={1,2,4,8,16};
    private TextView state,detail,log,imageInfo,thresholdLabel;
    private PixelPreview preview;
    private EditorStudio studio;
    private PhotoWorkspace photoWorkspace;
    private static final int PAGE_PHOTO=0,PAGE_READING=1,PAGE_DEVICE=2,PAGE_LOGS=3;
    private final LinearLayout[] mainPages=new LinearLayout[4];
    private com.inkreader.ReaderShelf readerShelf;
    private final Button[] mainTabs=new Button[4];
    private int mainPage;
    private TextView homeConnection;
    private View homeMore;
    private GalleryBrowser galleryBrowser;
    private LinearLayout settingsContent;
    private android.app.Dialog settingsDialog;
    private Bitmap photoSource;
    private Spinner diffusion,tone;
    private SeekBar diffusionStrength;
    private final RotationSchedule rotationSchedule=new RotationSchedule();
    private RotationSchedule.Ticket autoTicket;
    private boolean autoClock,sessionConfirmed;
    private String autoUid="";
    private final ArrayList<String> gallery=new ArrayList<>();
    private final Runnable rotationTick=()->runRotationTick();
    private ProgressBar progress;
    private Button refresh,stop;
    private Spinner duration,fit,power,poll,blocks,panelType;
    private CheckBox dither,binary;
    private SeekBar threshold,brightness,contrast,saturation,shadows;
    private CheckBox perceptual;
    private boolean adjusting;
    private TextView connectionStatus;
    private Button connectionButton;
    private boolean binding,permissionPending;
    private long bridgeEpoch;
    private android.os.IBinder bridgeCandidate;
    private String bridgeIdentityStatus="尚未完成后端身份校验";
    private String bridgeIdentityReport="尚未执行只读身份握手";
    private Runnable afterConnection;
    private EditorStudio.TransferObserver editorObserver;
    private final Runnable permissionTimeout=()->permissionRequestTimedOut();
    private void permissionRequestTimedOut(){if(permissionPending&&!destroyed){permissionPending=false;afterConnection=null;connectionStatus.setText("授权请求未返回，可重试；列表缺失时请重启 Shizuku 服务");connectionButton.setVisibility(View.VISIBLE);finishEditor(false,"授权未返回，请点击连接重试，或查看连接诊断。");}}
    private final Shizuku.OnBinderReceivedListener binderReceived=()->main.post(()->handleBinderReceived());
    private final Shizuku.OnBinderDeadListener binderDead=()->main.post(()->handleBinderDead());
    private final Runnable connectionTimeout=()->connectionTimedOut();
    private void handleBinderReceived(){if(!destroyed&&resumed&&useShizuku.isChecked())connectShizuku(false);}
    private void handleBinderDead(){if(!destroyed&&useShizuku.isChecked()){invalidateBridge("Shizuku 服务已断开");permissionPending=false;main.removeCallbacks(permissionTimeout);afterConnection=null;stopRotation("Shizuku 已停止，定时刷写结束");connectionStatus.setText("Shizuku 已停止，请重新启动");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();if(!busy)finishEditor(false,"Shizuku 连接已断开");if(busy)requestStop("Shizuku 服务断开");}}
    private void connectionTimedOut(){if(binding&&!destroyed){invalidateBridge("后端连接 / 身份校验超时");afterConnection=null;if(useShizuku.isChecked())try{Shizuku.unbindUserService(bridgeArgs,bridgeConnection,false);}catch(Exception ignored){}connectionStatus.setText("连接 / 身份校验超时，请点击重试");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();finishEditor(false,"连接超时，请在本页重试。");}}
    private Bitmap source,originalSource;
    private boolean cropped,editingCrop;
    private PanelImage.Prepared prepared;
    private int rotation,revision;
    private String imageName="内置四色测试图";
    private volatile boolean busy,preparing,resumed,destroyed;
    private volatile File stopFile;
    private RadioButton useRoot,useInternal,useShizuku;
    private String bridgeBackend="";
    private android.os.IBinder watchedInternal;
    private android.os.IBinder.DeathRecipient internalDeath;
    private final LinearLayout[] pageBodies=new LinearLayout[3];
    private final ScrollView[] pageScrolls=new ScrollView[3];
    private final Button[] tabs=new Button[3];
    private int activePage;
    private TextView chipInfo,capabilityInfo;
    private android.app.Dialog previewDialog;
    private volatile android.os.IBinder bridgeBinder;
    private final Shizuku.UserServiceArgs bridgeArgs=new Shizuku.UserServiceArgs(new android.content.ComponentName(BridgeIdentity.PACKAGE_NAME,"org.nfcresearch.qpolllab.ShellBridge")).tag(BridgeIdentity.serviceTag(android.os.Process.myUid())).version(BridgeIdentity.SERVICE_VERSION).daemon(false).processNameSuffix("easy_nfc_shell");
    private final android.content.ServiceConnection bridgeConnection=new android.content.ServiceConnection(){
        public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){main.post(()->{if(useShizuku.isChecked())verifyBridge(binder);});}
        public void onServiceDisconnected(android.content.ComponentName name){main.post(()->{if(destroyed||!useShizuku.isChecked())return;invalidateBridge("Shizuku 后端已断开");afterConnection=null;stopRotation("连接已断开，定时刷写结束");connectionStatus.setText("连接已断开，请重试");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();append("Shizuku 后端已断开。");if(busy)requestStop("授权服务断开");else finishEditor(false,"后端连接已断开，请重新连接后发送。");});}
    };
    private void invalidateBridge(String reason){unwatchInternal();bridgeEpoch++;bridgeBinder=null;bridgeCandidate=null;binding=false;main.removeCallbacks(connectionTimeout);bridgeIdentityStatus=reason;}
    private void unwatchInternal(){if(watchedInternal!=null&&internalDeath!=null)try{watchedInternal.unlinkToDeath(internalDeath,0);}catch(Exception ignored){}watchedInternal=null;internalDeath=null;}
    private void watchInternal(android.os.IBinder binder){unwatchInternal();watchedInternal=binder;internalDeath=()->main.post(()->{if(destroyed||watchedInternal!=binder||!useInternal.isChecked())return;invalidateBridge("内置授权服务已断开");afterConnection=null;stopRotation("内置服务已断开，定时任务停止");connectionStatus.setText("内置服务已断开，请重新激活");connectionButton.setText("打开内置激活");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();if(busy)requestStop("内置授权服务断开");else finishEditor(false,"内置服务已断开，请重新激活后发送。");});try{binder.linkToDeath(internalDeath,0);}catch(android.os.RemoteException dead){internalDeath.binderDied();}}
    private boolean currentBridgeCheck(long epoch,android.os.IBinder binder){return !destroyed&&binding&&bridgeEpoch==epoch&&bridgeCandidate==binder&&!useRoot.isChecked()&&bridgeBackend.equals(BackendMode.get(this));}
    private void verifyBridge(android.os.IBinder binder){
        if(destroyed||!binding||useRoot.isChecked()||bridgeCandidate==binder)return;
        bridgeCandidate=binder;final long epoch=bridgeEpoch;final int uid=android.os.Process.myUid();final String apk=getApplicationInfo().sourceDir;final String checkedBackend=bridgeBackend,label=BackendMode.label(checkedBackend);
        bridgeIdentityReport="正在校验本应用后端，应用 UID="+uid;connectionStatus.setText(label+" · 正在校验后端身份…");refreshConnectionUi();
        new Thread(()->{try{
            final String identity=BridgeClient.read(binder,uid,apk);
            main.post(()->{if(!currentBridgeCheck(epoch,binder))return;bridgeBinder=binder;bridgeCandidate=null;binding=false;main.removeCallbacks(connectionTimeout);if(BackendMode.INTERNAL.equals(checkedBackend))watchInternal(binder);bridgeIdentityStatus=identity;bridgeIdentityReport=identity;connectionStatus.setText(label+" 身份已验证 · 可以刷屏");connectionButton.setVisibility(View.GONE);refreshConnectionUi();append(label+" 后端身份验证通过："+identity);Runnable next=afterConnection;afterConnection=null;if(next!=null&&resumed)next.run();});
        }catch(Exception e){main.post(()->{if(!currentBridgeCheck(epoch,binder))return;String message="后端身份校验失败："+e.getClass().getSimpleName()+": "+e.getMessage();bridgeIdentityReport=message;invalidateBridge(message);afterConnection=null;if(BackendMode.SHIZUKU.equals(checkedBackend))try{Shizuku.unbindUserService(bridgeArgs,bridgeConnection,true);}catch(Exception cleanup){append("移除本应用后端失败："+cleanup);}connectionStatus.setText("后端身份未通过，请重新连接并查看诊断");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();append(message);finishEditor(false,message);});}},"bridge-identity").start();
    }
    private void buildWorkspaceHome(LinearLayout root){
        LinearLayout brand=new LinearLayout(this);brand.setGravity(android.view.Gravity.CENTER_VERTICAL);brand.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView title=AppUi.text(this,"随心印",24,AppUi.INK,true);brand.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        homeConnection=AppUi.text(this,"● 正在检查连接",11,AppUi.MUTED,false);homeConnection.setMaxLines(1);homeConnection.setEllipsize(android.text.TextUtils.TruncateAt.END);homeConnection.setGravity(android.view.Gravity.RIGHT);homeConnection.setContentDescription("设备连接状态");homeConnection.setOnClickListener(v->selectMainPage(PAGE_DEVICE));brand.addView(homeConnection,new LinearLayout.LayoutParams(dp(112),-2));homeMore=UiIcon.iconButton(this,"more","更多选项",false,()->{if(photoWorkspace!=null)photoWorkspace.openMore();});brand.addView(homeMore,new LinearLayout.LayoutParams(dp(42),dp(42)));root.addView(brand);
        android.widget.FrameLayout content=new android.widget.FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<mainPages.length;i++){mainPages[i]=AppUi.column(this);mainPages[i].setPadding(dp(20),0,dp(20),0);content.addView(mainPages[i],new android.widget.FrameLayout.LayoutParams(-1,-1));}
        photoWorkspace=new PhotoWorkspace(this,new PhotoWorkspace.Host(){
            public String panelType(){return selectedPanelType();}public String connectionState(){return connectionStatus.getText().toString();}
            public void connect(){connectSelected(true);}
            public void send(PanelImage.Prepared frame,String name,EditorStudio.TransferObserver observer){sendFromStudio(frame,name,observer);}
            public void stopSending(){afterConnection=null;main.removeCallbacks(permissionTimeout);permissionPending=false;if(busy)requestStop("用户在照片页停止");else finishEditor(false,"已取消发送");}
            public void log(String message){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())append(message);else main.post(()->{if(!destroyed)append(message);});}public void openCalendar(){openCreation(EditorStudio.CALENDAR);}public void openNote(){openCreation(EditorStudio.NOTE);}
            public void openDevice(){selectMainPage(PAGE_DEVICE);}public void openLogs(){selectMainPage(PAGE_LOGS);}
        });mainPages[0].addView(photoWorkspace.view,new LinearLayout.LayoutParams(-1,-1));
        ScrollView deviceScroll=new ScrollView(this);LinearLayout device=AppUi.column(this);device.setPadding(0,0,0,dp(20));deviceScroll.addView(device);mainPages[PAGE_DEVICE].addView(deviceScroll,new LinearLayout.LayoutParams(-1,-1));AppUi.title(device,"设备与连接","选择内置激活、外置 Shizuku 或 Root 连接。");
        LinearLayout connectionCard=AppUi.card(this);device.addView(connectionCard);moveTo(connectionStatus,connectionCard);moveTo(connectionButton,connectionCard);moveTo(state,connectionCard);moveTo(detail,connectionCard);moveTo(progress,connectionCard);moveTo(stop,connectionCard);AppUi.gap(device,14);
        LinearLayout screenCard=AppUi.card(this);screenCard.addView(AppUi.text(this,"实物屏幕类型",15,AppUi.INK,true));AppUi.gap(screenCard,6);moveTo(panelType,screenCard);screenCard.addView(AppUi.text(this,"按实物选择 A0 或 A1；会同步重新生成照片发送数据。",11,AppUi.MUTED,false));device.addView(screenCard);AppUi.gap(device,14);
        device.addView(AppUi.button(this,"内置激活 · 无需另装 Shizuku",true,()->openActivation()));AppUi.gap(device,9);device.addView(AppUi.button(this,"连接与授权",false,()->{selectPage(0);openSettings();}));AppUi.gap(device,9);device.addView(AppUi.button(this,"屏幕与传输",false,()->{selectPage(2);openSettings();}));AppUi.gap(device,9);device.addView(AppUi.button(this,"日历与天气",false,()->openCreation(EditorStudio.CALENDAR)));AppUi.gap(device,9);device.addView(AppUi.button(this,"参考项目与许可证",false,()->showLicense()));AppUi.gap(device,14);device.addView(AppUi.text(this,"随心印 2.20-test3\n照片在本机处理；天气和每日内容仅在主动获取时联网。",11,AppUi.MUTED,false));
        readerShelf=new com.inkreader.ReaderShelf(this);mainPages[PAGE_READING].addView(readerShelf.view,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout logs=mainPages[PAGE_LOGS];AppUi.title(logs,"运行日志","查看连接、图像生成和本次传输状态。");LinearLayout logActions=new LinearLayout(this);logActions.addView(AppUi.button(this,"复制日志",false,()->copyLogs()),new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(0,dp(44),1);saveParams.leftMargin=dp(8);logActions.addView(AppUi.button(this,"保存日志",false,()->saveLogs()),saveParams);logs.addView(logActions);AppUi.gap(logs,12);ScrollView logScroll=new ScrollView(this);moveTo(log,logScroll);logs.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(this);navigation.setBackgroundColor(Color.WHITE);navigation.setPadding(dp(12),dp(4),dp(12),dp(8));String[] labels={"照片","阅读","设备","日志"};for(int i=0;i<labels.length;i++){final int which=i;Button tab=AppUi.ghost(this,labels[i],false,()->selectMainPage(which));tab.setTextSize(14);tab.setContentDescription("主导航 · "+labels[i]);mainTabs[i]=tab;navigation.addView(tab,new LinearLayout.LayoutParams(0,dp(50),1));}root.addView(navigation);selectMainPage(0);refreshConnectionUi();
    }
    private static void moveTo(View child,android.view.ViewGroup parent){if(child.getParent()!=null)((android.view.ViewGroup)child.getParent()).removeView(child);parent.addView(child);}
    private void selectMainPage(int index){
        if(index<0||index>=mainPages.length||mainPages[index]==null)return;
        if((busy||editorObserver!=null)&&index!=mainPage){Toast.makeText(this,"刷写中，请保持贴合；结束后可切换页面。",Toast.LENGTH_SHORT).show();return;}
        mainPage=index;for(int i=0;i<mainPages.length;i++){mainPages[i].setVisibility(i==index?View.VISIBLE:View.GONE);mainTabs[i].setTextColor(i==index?AppUi.INK:AppUi.MUTED);mainTabs[i].setTypeface(null,i==index?Typeface.BOLD:Typeface.NORMAL);}if(homeMore!=null)homeMore.setVisibility(index==0?View.VISIBLE:View.GONE);if(photoWorkspace!=null)photoWorkspace.setActive(index==0);
    }
    private void refreshConnectionUi(){if(studio!=null)studio.refreshConnection();if(photoWorkspace!=null)photoWorkspace.refreshConnection();if(homeConnection!=null){String value=connectionStatus.getText().toString();boolean ready=value.contains("身份已验证")||value.contains("可以刷屏"),connecting=binding||(useInternal.isChecked()&&InternalActivationController.get(this).isBusy());homeConnection.setText(ready?"● 已连接":connecting?"● 连接中":useRoot.isChecked()?"● Root 模式":"● 未连接");homeConnection.setTextColor(ready?AppUi.INK:AppUi.MUTED);homeConnection.setContentDescription(value);}}
    @Override public void changed(){if(!destroyed&&resumed&&useInternal!=null&&useInternal.isChecked())connectInternal(false);}
    private void copyLogs(){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("随心印日志",transcript.toString()));Toast.makeText(this,"日志已复制",Toast.LENGTH_SHORT).show();}
    private void saveLogs(){try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"随心印-日志.txt"),71);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"没有可用的文件保存器",Toast.LENGTH_SHORT).show();}}
    private final Shizuku.OnRequestPermissionResultListener permissionResult=(code,result)->{if(code==28){main.removeCallbacks(permissionTimeout);permissionPending=false;if(!useShizuku.isChecked())return;if(result==android.content.pm.PackageManager.PERMISSION_GRANTED)connectShizuku(false);else{afterConnection=null;connectionStatus.setText("授权被拒绝，可点击重试；列表缺失请查看恢复指引");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();finishEditor(false,"授权未允许，请点击连接重试。");}}};
    private void syncBackendSelection(){String mode=BackendMode.get(this);RadioButton selected=BackendMode.ROOT.equals(mode)?useRoot:BackendMode.SHIZUKU.equals(mode)?useShizuku:useInternal;if(!selected.isChecked())selected.setChecked(true);}
    private void connectSelected(boolean ask){if(useRoot.isChecked()){connectionStatus.setText("使用 Root · 执行操作时请求授权");connectionButton.setVisibility(View.GONE);refreshConnectionUi();return;}if(useInternal.isChecked())connectInternal(ask);else connectShizuku(ask);}
    private void openActivation(){if(busy||rotationSchedule.isRunning()){Toast.makeText(this,"请先停止当前刷写任务",Toast.LENGTH_SHORT).show();return;}BackendMode.set(this,BackendMode.INTERNAL);syncBackendSelection();startActivity(new Intent(this,InternalActivationActivity.class));}
    private void connectInternal(boolean ask){
        if(destroyed||!useInternal.isChecked())return;
        android.os.IBinder candidate=org.nfcresearch.qpolllab.activation.InternalBackend.photoBinder();
        if(candidate==null){
            invalidateBridge("内置服务尚未激活或已失效");afterConnection=null;
            InternalActivationController activation=InternalActivationController.get(this);
            boolean saved=activation.hasSavedIdentity()&&!activation.needsPairing();
            if(ask&&saved&&!activation.isBusy())activation.reconnectSaved();
            boolean restoring=activation.isAutoRestoring(),activating=activation.isActivating();
            connectionStatus.setText(restoring||saved||activating?activation.status():"内置服务未就绪，请完成首次激活");
            connectionButton.setText(restoring?"查看连接进度":activating?"查看激活进度":saved?"重新连接 · 无需配对":"打开内置激活");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();
            if(ask){if(restoring||activating)Toast.makeText(this,"正在恢复内置连接，就绪后再点推送；不会自动重传",Toast.LENGTH_LONG).show();else if(!saved)openActivation();}
            return;
        }
        if(candidate==bridgeBinder&&candidate.isBinderAlive()&&BackendMode.INTERNAL.equals(bridgeBackend)){connectionStatus.setText("内置激活 身份已验证 · 可以刷屏");connectionButton.setVisibility(View.GONE);refreshConnectionUi();Runnable next=afterConnection;afterConnection=null;if(next!=null&&resumed)next.run();return;}
        if(binding&&BackendMode.INTERNAL.equals(bridgeBackend))return;
        invalidateBridge("正在校验内置后端");bridgeBackend=BackendMode.INTERNAL;binding=true;connectionButton.setVisibility(View.GONE);main.postDelayed(connectionTimeout,10000);verifyBridge(candidate);
    }
    private void connectShizuku(boolean ask){
        if(destroyed||!useShizuku.isChecked())return;
        try{
            if(!Shizuku.pingBinder()){invalidateBridge("本应用未收到有效 Shizuku Binder");connectionStatus.setText("尚未收到 Shizuku 连接；服务已运行时请查看诊断与恢复");connectionButton.setText("打开 Shizuku / 启动帮助");connectionButton.setVisibility(View.VISIBLE);afterConnection=null;refreshConnectionUi();return;}
            connectionButton.setText("授权 / 重新连接");
            if(Shizuku.isPreV11()||Shizuku.getVersion()<13){connectionStatus.setText("后端身份校验需要 Shizuku 13 或更新版本");afterConnection=null;finishEditor(false,"请先升级 Shizuku 至 13 或更新版本。");refreshConnectionUi();return;}
            if(Shizuku.checkSelfPermission()!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                connectionStatus.setText("Shizuku 正在运行 · 请允许本应用使用");connectionButton.setVisibility(View.VISIBLE);
                if(ask&&!permissionPending){permissionPending=true;main.removeCallbacks(permissionTimeout);main.postDelayed(permissionTimeout,20000);Shizuku.requestPermission(28);}return;
            }
            if(bridgeBinder!=null&&bridgeBinder.isBinderAlive()){Runnable next=afterConnection;afterConnection=null;connectionStatus.setText("Shizuku 身份已验证 · 可以刷屏");connectionButton.setVisibility(View.GONE);refreshConnectionUi();if(next!=null&&resumed)next.run();return;}
            if(binding)return;
            invalidateBridge("正在等待后端身份校验");bridgeBackend=BackendMode.SHIZUKU;binding=true;connectionStatus.setText("正在连接并校验 Shizuku 后端…");connectionButton.setVisibility(View.GONE);refreshConnectionUi();
            main.postDelayed(connectionTimeout,10000);Shizuku.bindUserService(bridgeArgs,bridgeConnection);
        }catch(Exception e){invalidateBridge("连接失败："+e);permissionPending=false;afterConnection=null;main.removeCallbacks(permissionTimeout);connectionStatus.setText("连接失败，请重试或查看日志");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();append("Shizuku 连接失败："+e);finishEditor(false,"Shizuku 连接失败，请查看连接诊断。");}
    }
    private boolean ensureConnection(Runnable next){
        if(useRoot.isChecked())return true;
        if(useInternal.isChecked()){if(BackendMode.INTERNAL.equals(bridgeBackend)&&bridgeBinder!=null&&bridgeBinder==org.nfcresearch.qpolllab.activation.InternalBackend.photoBinder()&&bridgeBinder.isBinderAlive())return true;afterConnection=next;connectInternal(true);return false;}
        try{if(bridgeBinder!=null&&bridgeBinder.isBinderAlive()&&Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==android.content.pm.PackageManager.PERMISSION_GRANTED)return true;}catch(IllegalStateException ignored){}
        afterConnection=next;connectShizuku(true);return false;
    }
    private void openConnection(){
        if(useInternal.isChecked()){if(InternalActivationController.get(this).isAutoRestoring())openActivation();else connectInternal(true);return;}
        if(useRoot.isChecked()){connectSelected(true);return;}
        if(Shizuku.pingBinder()){connectShizuku(true);return;}
        Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if(launch!=null){startActivity(launch);return;}
        new AlertDialog.Builder(this).setTitle("先安装并启动 Shizuku")
            .setMessage("首次：安装 Shizuku → 开发者选项开启无线调试 → 在 Shizuku 中配对并启动 → 返回本应用授权。\n\n配对通常只需一次；手机重启后需重新启动服务。返回本应用后会自动连接。")
            .setPositiveButton("打开官方教程",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/"))))
            .setNegativeButton("关闭",null).show();
    }
    private boolean scanning,terminal;
    private final SessionFailure failure=new SessionFailure();
    private long scanDeadline;
    private final Runnable tick=new Runnable(){public void run(){
        if(busy&&scanning){detail.setText("寻找兼容墨水屏 · 最长还剩 "+Math.max(0,(scanDeadline-SystemClock.elapsedRealtime()+999)/1000)+" 秒；保持贴合。");main.postDelayed(this,250);}
    }};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(HomeScreen.PAPER);setContentView(root);
        // These transport controls keep their existing state and listeners. The photo
        // workspace presents transfer feedback through the existing observer instead.
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        studio=new EditorStudio(this,new EditorStudio.Host(){
            public Bitmap currentPhoto(){return photoSource;}
            public String panelType(){return selectedPanelType();}
            public void selectPanel(String type){int value=type.equals("A0")?1:type.equals("A1")?2:0;if(panelType.getSelectedItemPosition()!=value)panelType.setSelection(value);}
            public String connectionState(){return connectionStatus==null?"正在检查连接…":connectionStatus.getText().toString();}
            public void connect(){connectSelected(true);}
            public void send(PanelImage.Prepared frame,String name,EditorStudio.TransferObserver observer){sendFromStudio(frame,name,observer);}
            public void stopSending(){afterConnection=null;main.removeCallbacks(permissionTimeout);permissionPending=false;if(busy)requestStop("用户在编辑页停止");else finishEditor(false,"已取消发送");}
            public void selectPhoto(){pickPhoto(51,false);}
            public void apply(Bitmap bitmap,String name){applyCard(bitmap,name);}
            public void automate(int mode,long interval){applyCard(studio.currentClock(),"时钟");confirmRotation(true,interval);}
            public void manageRotation(){showRotation();}
        });
        loadGallery();
        state=text("准备就绪",18,0xff437f6e);state.setTypeface(null,Typeface.BOLD);card.addView(state);
        detail=text("选择图片并预览，核对屏幕类型后发送。",12,0xff52635d);card.addView(detail);
        connectionStatus=text("正在检查连接…",12,0xff52635d);card.addView(connectionStatus);
        connectionButton=button("连接 / 启动 Shizuku");connectionButton.setOnClickListener(v->openConnection());card.addView(connectionButton);idleControls.add(connectionButton);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(476);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        stop=button("停止并恢复 NFC");stop.setEnabled(false);stop.setVisibility(View.GONE);stop.setOnClickListener(v->{stopRotation("用户停止定时刷写");requestStop("用户停止");});
        settingsContent=new LinearLayout(this);settingsContent.setOrientation(1);settingsContent.setPadding(dp(16),dp(10),dp(16),dp(8));settingsContent.setBackgroundColor(HomeScreen.PAPER);
        LinearLayout settingsHeader=new LinearLayout(this);TextView settingsTitle=text("设置与设备",23,HomeScreen.INK);settingsTitle.setTypeface(null,Typeface.BOLD);settingsHeader.addView(settingsTitle,new LinearLayout.LayoutParams(0,-2,1));Button closeSettings=button("完成");closeSettings.setOnClickListener(v->{if(settingsDialog!=null)settingsDialog.dismiss();});settingsHeader.addView(closeSettings);settingsContent.addView(settingsHeader);
        android.widget.FrameLayout host=new android.widget.FrameLayout(this);settingsContent.addView(host,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<3;i++){pageScrolls[i]=new ScrollView(this);pageScrolls[i].setFillViewport(true);pageBodies[i]=new LinearLayout(this);pageBodies[i].setOrientation(LinearLayout.VERTICAL);pageBodies[i].setPadding(0,dp(14),0,dp(24));pageScrolls[i].addView(pageBodies[i]);host.addView(pageScrolls[i],new android.widget.FrameLayout.LayoutParams(-1,-1));}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(0,dp(4),0,dp(8));settingsContent.addView(nav);
        String[] names={"设备与授权","图像参数","屏幕与传输"};for(int i=0;i<3;i++){final int index=i;tabs[i]=button(names[i]);tabs[i].setTextSize(13);tabs[i].setOnClickListener(v->selectPage(index));nav.addView(tabs[i],weighted());}tabs[1].setVisibility(View.GONE);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        LinearLayout page=pageBodies[0];section(page,"设备与 NFC");
        page.addView(text(android.os.Build.MANUFACTURER+" · "+android.os.Build.MODEL+" / "+android.os.Build.DEVICE,14,0xff52635d));
        chipInfo=text("芯片：尚未检查",15,0xff263e35);page.addView(chipInfo);
        capabilityInfo=text("Q-Poll：尚未测试",15,0xff263e35);page.addView(capabilityInfo);
        page.addView(text("芯片信息来自当前系统配置；能力以实际寻卡命令与控制器响应为准。",12,0xff68766e));
        section(page,"授权方式");RadioGroup backend=new RadioGroup(this);backend.setOrientation(RadioGroup.VERTICAL);
        useInternal=new RadioButton(this);useInternal.setId(View.generateViewId());useInternal.setText("内置激活 · 无需另装应用");backend.addView(useInternal);idleControls.add(useInternal);
        useShizuku=new RadioButton(this);useShizuku.setId(View.generateViewId());useShizuku.setText("外置 Shizuku · 备用");backend.addView(useShizuku);idleControls.add(useShizuku);
        useRoot=new RadioButton(this);useRoot.setId(View.generateViewId());useRoot.setText("Root");backend.addView(useRoot);idleControls.add(useRoot);page.addView(backend);syncBackendSelection();
        backend.setOnCheckedChangeListener((group,id)->{afterConnection=null;permissionPending=false;main.removeCallbacks(permissionTimeout);String mode=useRoot.isChecked()?BackendMode.ROOT:useShizuku.isChecked()?BackendMode.SHIZUKU:BackendMode.INTERNAL;BackendMode.set(this,mode);InternalActivationController.get(this).selectedBackendChanged();invalidateBridge("授权方式已切换");try{Shizuku.unbindUserService(bridgeArgs,bridgeConnection,false);}catch(Exception ignored){}connectSelected(false);});
        page.addView(action("内置激活设置 · 无线调试配对",v->openActivation()));
        page.addView(action("连接当前授权方式",v->connectSelected(true)));
        page.addView(action("外置 Shizuku 诊断与恢复",v->showConnectionRecovery()));
        page.addView(action("运行日志",v->showLogs()));
        page.addView(action("热门图库网址",v->galleryBrowser.configure()));
        page.addView(text("内置激活无需另装应用；先在系统开启无线调试并完成配对。外置 Shizuku 和 Root 可作为备用。",12,0xff68766e));
        page.addView(action("检查芯片 / NFC",v->start("inspect")));
        section(page,"Q-Poll 能力测试");
        page.addView(text("测试会开启短时寻卡，不发送图像或刷新指令。",12,0xff68766e));
        duration=spinner(new String[]{"寻卡 5 秒","寻卡 10 秒","寻卡 30 秒","寻卡 60 秒"});duration.setSelection(1);page.addView(duration);
        page.addView(action("测试 Q-Poll 能力",v->start("qtag")));page.addView(action("普通 NFC-A 对照",v->start("standard")));
        page.addView(text("未读到标签不等于不支持 Q-Poll；成功寻卡也不代表墨水屏供电与刷新已验证。",12,0xff68766e));
        page=pageBodies[1];section(page,"创作与发送");
        String[] modes={"拍立得 / 自由构图","便签文字","日历卡片","时钟卡片"};
        for(int row=0;row<2;row++){LinearLayout cards=new LinearLayout(this);for(int col=0;col<2;col++){final int mode=row*2+col;cards.addView(action(modes[mode],v->studio.open(mode)),weighted());}page.addView(cards);}
        page.addView(action("多图轮播 / 定时刷写",v->showRotation()));
        page.addView(text("屏幕类型",14,0xff52635d));
        panelType=spinner(new String[]{"请选择屏幕类型","A0 · 华为 552×768 四色","A1 · 华为 552×768 四色"});
        String savedPanel=getSharedPreferences("refresh-settings",MODE_PRIVATE).getString("panelType","");
        panelType.setSelection(savedPanel.equals("A0")?1:savedPanel.equals("A1")?2:0);page.addView(panelType);
        page.addView(text("按实物选择 A0 或 A1；无法自动识别。更换屏幕后请核对选择。",12,0xff68766e));
        preview=new PixelPreview(this);preview.setContentDescription("最终写入像素预览，仅黑白黄红；点击放大 · 双指缩放");page.addView(preview,new LinearLayout.LayoutParams(-1,dp(250)));
        preview.setOnClickListener(v->showPixels());
        refresh=action("发送当前预览到墨水屏",v->confirmRefresh());refresh.setTextColor(Color.WHITE);refresh.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff437f6e));page.addView(refresh);
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int height=Math.min(dp(250),Math.max(dp(100),b-t-dp(150)));if(preview.getLayoutParams().height!=height){preview.getLayoutParams().height=height;preview.requestLayout();}});
        page.addView(text("预览仅使用屏幕色板 · 点击放大 · 双指缩放",12,0xff68766e));
        imageInfo=text("正在生成预览…",12,0xff52635d);page.addView(imageInfo);
        section(page,"编辑图片");
        page.addView(text("照片优化在手机本地完成；四色预览与发送使用同一份像素。实体色彩仍需实屏对照。",12,0xff68766e));
        LinearLayout choose=new LinearLayout(this);
        choose.addView(action("选择图片",v->pickPhoto(41,false)),weighted());
        choose.addView(action("四色测试图",v->{Bitmap pattern=PanelImage.testPattern();photoSource=pattern;applyCard(pattern,"内置四色测试图");}),weighted());page.addView(choose);
        LinearLayout options=new LinearLayout(this);options.setGravity(android.view.Gravity.CENTER_VERTICAL);
        fit=spinner(new String[]{"完整显示 · 留白","填满屏幕 · 裁剪"});options.addView(fit,new LinearLayout.LayoutParams(0,dp(48),1));
        options.addView(action("旋转 90°",v->{rotation=(rotation+90)%360;prepare();}),weighted());page.addView(options);
        LinearLayout cropActions=new LinearLayout(this);
        cropActions.addView(action("裁切图片",v->showCrop()),weighted());
        cropActions.addView(action("还原原图",v->{if(originalSource!=null){source=originalSource;rotation=0;cropped=false;prepare();}}),weighted());page.addView(cropActions);
        section(page,"照片 / 插画优化");
        page.addView(action("选择优化预设",v->new AlertDialog.Builder(this).setTitle("选择起点，再按实屏调整")
            .setItems(new String[]{"原版兼容 · 重置增强","照片增强 · 提亮暗部","浅色插画 · 加深色块","纯四色素材 · 无抖动"},(d,which)->applyPhotoPreset(which)).show()));
        brightness=photoSlider(page,"亮度","brightness",-50,50,0);
        contrast=photoSlider(page,"对比度 %","contrast",50,180,100);
        saturation=photoSlider(page,"饱和度 %","saturation",0,180,100);
        shadows=photoSlider(page,"提亮暗部","shadows",0,60,0);
        perceptual=new CheckBox(this);perceptual.setText("亮度优先四色（实验，可与原版对照）");perceptual.setChecked(getSharedPreferences("refresh-settings",MODE_PRIVATE).getBoolean("perceptual",false));idleControls.add(perceptual);page.addView(perceptual);perceptual.setVisibility(View.GONE);
        perceptual.setOnCheckedChangeListener((v,checked)->{getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putBoolean("perceptual",checked).apply();prepare();});
        binary=new CheckBox(this);binary.setText("黑白二值化");binary.setChecked(getSharedPreferences("refresh-settings",MODE_PRIVATE).getBoolean("binary",false));idleControls.add(binary);page.addView(binary);
        int savedThreshold=getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("threshold",128);
        thresholdLabel=text("二值化阈值："+Math.max(0,Math.min(255,savedThreshold))+"（松手更新预览）",13,0xff52635d);page.addView(thresholdLabel);
        threshold=new SeekBar(this);threshold.setMax(255);threshold.setProgress(Math.max(0,Math.min(255,savedThreshold)));idleControls.add(threshold);page.addView(threshold);
        page.addView(text("阈值越高，黑色区域越多。可与抖动或图像简化配合。",12,0xff68766e));
        binary.setOnCheckedChangeListener((b,checked)->{getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putBoolean("binary",checked).apply();prepare();});
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){thresholdLabel.setText("二值化阈值："+value+"（松手更新预览）");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("threshold",s.getProgress()).apply();prepare();}});
        page.addView(text("图像简化 · 供电紧张时可尝试",14,0xff52635d));
        blocks=spinner(new String[]{"原始细节","轻度 · 2×2 色块","中度 · 4×4 色块","强 · 8×8 色块","极强 · 16×16 色块"});page.addView(blocks);
        int savedBlock=getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("blockSize",1),selectedBlock=0;
        for(int i=0;i<blockSizes.length;i++)if(blockSizes[i]==savedBlock)selectedBlock=i;
        blocks.setSelection(selectedBlock);
        page.addView(text("简化后每个色块使用一种颜色，自动关闭抖动；档位越强细节越少。可能改善刷新稳定性，实际效果需测试。",12,0xff68766e));
        dither=new CheckBox(this);dither.setText("抖动处理（细碎色点更多，供电紧张时关闭）");dither.setChecked(selectedBlock==0&&getSharedPreferences("refresh-settings",MODE_PRIVATE).getBoolean("dither",false));idleControls.add(dither);page.addView(dither);
        page.addView(text("抖动算法与色彩映射",14,0xff52635d));
        diffusion=spinner(FourColorDither.names());diffusion.setSelection(Math.max(0,Math.min(FourColorDither.names().length-1,getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("diffusion",1))));page.addView(diffusion);
        tone=spinner(new String[]{"原版 RGB","感知色彩 · 保护灰白","暖调照片 · 实验"});tone.setSelection(Math.max(0,Math.min(2,getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("tone",0))));page.addView(tone);
        diffusionStrength=photoSlider(page,"抖动强度 %","diffusionStrength",0,100,75);
        AdapterView.OnItemSelectedListener diffusionListener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View v,int pos,long id){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("diffusion",diffusion.getSelectedItemPosition()).putInt("tone",tone.getSelectedItemPosition()).apply();prepare();}public void onNothingSelected(AdapterView<?> p){}};
        diffusion.setOnItemSelectedListener(diffusionListener);tone.setOnItemSelectedListener(diffusionListener);
        page.addView(text("色块简化和黑白二值化继续使用原版处理；新四色抖动作用于原始细节模式。照片可试暖调 + 蛇形扩散，文字卡片建议关闭抖动。",12,0xff68766e));
        dither.setOnCheckedChangeListener((b,checked)->{getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putBoolean("dither",checked).apply();prepare();});
        blocks.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("blockSize",blockSizes[pos]).apply();if(pos>0)dither.setChecked(false);prepare();}public void onNothingSelected(AdapterView<?> p){}});
        panelType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putString("panelType",selectedPanelType()).apply();prepare();if(photoWorkspace!=null)photoWorkspace.panelTypeChanged();}public void onNothingSelected(AdapterView<?> p){}});
        fit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){prepare();}public void onNothingSelected(AdapterView<?> p){}});
        page.addView(action("只读检查屏幕",v->start("identity")));
        page.addView(text("支持相同原厂配置的 552×768 四色屏。发送时持续贴合并保持应用在前台；切换本应用内的页面不会中断操作。",12,0xff68766e));
        page=pageBodies[2];section(page,"高级参数");
        page.addView(text("参数用于下一次发送，运行期间锁定。",12,0xff68766e));
        section(page,"上电等待时间");power=spinner(new String[]{"500 ms","1000 ms","2000 ms（默认）","3000 ms","4000 ms","5000 ms"});
        int savedPower=getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("powerMs",2000),selectedPower=2;for(int i=0;i<powerTimes.length;i++)if(powerTimes[i]==savedPower)selectedPower=i;power.setSelection(selectedPower);page.addView(power);
        page.addView(text("写入刷新指令中的上电等待参数，用于屏幕刷新准备阶段。",12,0xff68766e));
        power.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("powerMs",powerTimes[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}});
        section(page,"状态查询间隔");poll=spinner(new String[]{"0 ms · 连续查询","10 ms","20 ms","50 ms","100 ms","200 ms","300 ms","400 ms","500 ms（默认）"});
        int savedPoll=getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("pollMs",PanelProtocol.DEFAULT_POLL_MS),selectedPoll=pollTimes.length-1;for(int i=0;i<pollTimes.length;i++)if(pollTimes[i]==savedPoll)selectedPoll=i;poll.setSelection(selectedPoll);page.addView(poll);
        poll.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("pollMs",pollTimes[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}});
        page.addView(text("0 ms 表示收到应答后立即再次查询，实际频率取决于通信耗时。",12,0xff68766e));
        page.addView(action("恢复默认参数",v->{power.setSelection(2);poll.setSelection(pollTimes.length-1);}));
        Button about=button("参考项目与许可证");about.setOnClickListener(v->showLicense());page.addView(about);
        page.addView(action("时钟模式",v->studio.open(EditorStudio.CLOCK)));
        page.addView(text("随心印 · v2.20-test3",12,0xff8b877b));
        log=text("",11,0xff233c31);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);log.setPadding(dp(10),dp(8),dp(10),dp(8));
        selectPage(getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt("page",1));
        buildWorkspaceHome(root);
        append("随心印 v2.20-test3 就绪；照片 / 阅读 / 设备 / 日志；支持内置激活。");source=PanelImage.testPattern();originalSource=source;prepare();
        Shizuku.addBinderReceivedListenerSticky(binderReceived);Shizuku.addBinderDeadListener(binderDead);
    }
    private SeekBar photoSlider(LinearLayout page,String label,String key,int min,int max,int initial){
        int value=Math.max(min,Math.min(max,getSharedPreferences("refresh-settings",MODE_PRIVATE).getInt(key,initial)));
        TextView caption=text(label+"："+value,13,0xff52635d);page.addView(caption);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);page.addView(bar);idleControls.add(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean user){caption.setText(label+"："+(v+min));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt(key,s.getProgress()+min).apply();prepare();}});return bar;
    }
    private void applyPhotoPreset(int preset){
        adjusting=true;
        int b=0,c=100,s=100,h=0;
        if(preset==1){c=120;s=115;h=25;}else if(preset==2){b=-5;c=145;s=150;}
        brightness.setProgress(b+50);contrast.setProgress(c-50);saturation.setProgress(s);shadows.setProgress(h);perceptual.setChecked(preset==1||preset==2);
        binary.setChecked(false);blocks.setSelection(0);tone.setSelection(preset==1?2:preset==2?1:0);diffusion.setSelection(1);diffusionStrength.setProgress(75);dither.setChecked(preset==1);
        getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("brightness",b).putInt("contrast",c).putInt("saturation",s).putInt("shadows",h).putInt("blockSize",1).putInt("tone",preset==1?2:preset==2?1:0).putInt("diffusion",1).putInt("diffusionStrength",75).putBoolean("dither",preset==1).putBoolean("binary",false).apply();
        adjusting=false;prepare();
    }
    private PhotoAdjust photoSettings(){return new PhotoAdjust(brightness.getProgress()-50,contrast.getProgress()+50,saturation.getProgress(),shadows.getProgress(),tone.getSelectedItemPosition()!=0);}
    private void section(LinearLayout page,String title){TextView t=text(title,18,0xff302f29);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(12),0,dp(6));page.addView(t);}
    private void openCreation(int mode){if(busy||editorObserver!=null||preparing||rotationSchedule.isRunning()){Toast.makeText(this,"请先等待处理完成或停止轮播",Toast.LENGTH_SHORT).show();return;}if(photoWorkspace!=null&&(mode==EditorStudio.CALENDAR||mode==EditorStudio.NOTE)){selectMainPage(0);photoWorkspace.openCardEditor(mode);return;}studio.open(mode);}
    private void openSettings(){if(settingsDialog!=null&&settingsDialog.isShowing())return;if(settingsContent.getParent()!=null)((android.view.ViewGroup)settingsContent.getParent()).removeView(settingsContent);final android.app.Dialog opened=new android.app.Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);settingsDialog=opened;opened.setContentView(settingsContent);opened.setOnDismissListener(d->{if(settingsDialog==opened)settingsDialog=null;});opened.show();opened.getWindow().setLayout(-1,-1);}
    private void selectPage(int index){activePage=index==2?2:0;for(int i=0;i<3;i++){pageScrolls[i].setVisibility(i==activePage?View.VISIBLE:View.GONE);tabs[i].setTextColor(i==activePage?Color.WHITE:HomeScreen.INK);tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(i==activePage?HomeScreen.INK:HomeScreen.SAND));}getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("page",activePage).apply();}
    private void showLogs(){if(settingsDialog!=null)settingsDialog.dismiss();selectMainPage(PAGE_LOGS);}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,dp(48),1);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
    private GradientDrawable round(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(12));return g;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(HomeScreen.INK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(HomeScreen.SAND));b.setMinHeight(dp(48));return b;}
    private Button action(String s,View.OnClickListener listener){Button b=button(s);b.setOnClickListener(listener);idleControls.add(b);return b;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values));idleControls.add(s);return s;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String selectedPanelType(){int pos=panelType.getSelectedItemPosition();return pos==1?"A0":pos==2?"A1":"";}
    void syncPanelSelection(){String saved=getSharedPreferences("refresh-settings",MODE_PRIVATE).getString("panelType","");int position="A0".equals(saved)?1:"A1".equals(saved)?2:0;if(panelType.getSelectedItemPosition()!=position)panelType.setSelection(position);}
    private void controls(){boolean enabled=!busy&&!preparing&&!editingCrop&&!rotationSchedule.isRunning();for(View v:idleControls)v.setEnabled(enabled);dither.setEnabled(enabled&&blocks.getSelectedItemPosition()==0);threshold.setEnabled(enabled&&binary.isChecked());refresh.setEnabled(enabled&&prepared!=null&&!prepared.panelType.isEmpty());stop.setEnabled(busy||rotationSchedule.isRunning());stop.setVisibility(busy||rotationSchedule.isRunning()?View.VISIBLE:View.GONE);if(photoWorkspace!=null)photoWorkspace.setBusy(busy||rotationSchedule.isRunning());}
    private void prepare(){
        if(source==null||busy||destroyed||editingCrop||adjusting)return;
        final int token=++revision,angle=rotation,block=blockSizes[blocks.getSelectedItemPosition()],level=threshold.getProgress();final boolean cover=fit.getSelectedItemPosition()==1,noise=dither.isChecked(),mono=binary.isChecked(),hasCrop=cropped;final Bitmap input=source;final String chosenPanel=selectedPanelType();final PhotoAdjust photo=photoSettings();final int algorithm=noise?diffusion.getSelectedItemPosition():0,strength=diffusionStrength.getProgress(),colorTone=tone.getSelectedItemPosition();
        prepared=null;preparing=true;controls();imageInfo.setText("正在生成四色预览…");
        new Thread(()->{try{PanelImage.Prepared image=PanelImage.prepare(input,angle,cover,noise,block,mono,level,hasCrop,chosenPanel,photo,algorithm,strength,colorTone);
            main.post(()->{if(token!=revision||destroyed)return;prepared=image;preview.setImage(image.preview);imageInfo.setText(imageName+" · 552×768 · "+image.packed.length+" 字节\n"+image.settings);preparing=false;controls();if(autoTicket!=null&&rotationSchedule.isCurrent(autoTicket))beginScheduledRefresh();});
        }catch(Throwable e){main.post(()->{if(token!=revision||destroyed)return;preparing=false;controls();imageInfo.setText("预览失败："+e.getMessage());stopRotation("生成轮播预览失败");});}},"image-preview").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==71){if(result==RESULT_OK&&data!=null&&data.getData()!=null){final android.net.Uri uri=data.getData();final String snapshot=transcript.toString();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法保存日志");out.write(snapshot.getBytes("UTF-8"));main.post(()->Toast.makeText(this,"日志已保存",Toast.LENGTH_SHORT).show());}catch(Exception e){main.post(()->Toast.makeText(this,"日志保存失败："+e.getMessage(),Toast.LENGTH_SHORT).show());}},"save-log").start();}return;}
        if(readerShelf!=null&&readerShelf.onResult(request,result,data))return;
        if(photoWorkspace!=null&&photoWorkspace.onResult(request,result,data))return;
        if(galleryBrowser!=null&&galleryBrowser.onActivityResult(request,result,data))return;
        if(result!=RESULT_OK||data==null){if(request==51)studio.photoCancelled();else if(request==61&&!destroyed)showRotation();return;}
        if(request==61){importGallery(data);return;}
        if((request!=41&&request!=51)||data.getData()==null)return;
        final android.net.Uri uri=data.getData();preparing=true;controls();imageInfo.setText("正在读取图片…");
        new Thread(()->{try{Bitmap image=PanelImage.load(getContentResolver(),uri);main.post(()->{if(destroyed)return;source=image;originalSource=image;photoSource=image;cropped=false;rotation=0;imageName="自选图片";preparing=false;prepare();if(request==51)studio.deliverPhoto(image);});}
        catch(Throwable e){main.post(()->{if(destroyed)return;preparing=false;controls();imageInfo.setText("图片读取失败："+e.getMessage());if(request==51)studio.photoCancelled();});}},"image-load").start();
    }
    private void showPixels(){
        if(prepared==null||busy||preparing||editingCrop)return;
        final Bitmap snapshot=prepared.preview;android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);previewDialog=dialog;dialog.setOnDismissListener(d->previewDialog=null);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(0xfff8f6f0);content.setPadding(dp(12),dp(12),dp(12),dp(12));
        content.addView(text("最终发送像素 · 552 × 768",20,0xff302f29));content.addView(text("双指缩放 · 单指拖动 · 双击切换缩放",12,0xff52635d));
        ZoomPreview viewer=new ZoomPreview(this,snapshot);content.addView(viewer,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);Button fitButton=button("适应屏幕"),actual=button("1:1 像素"),close=button("关闭");fitButton.setOnClickListener(v->viewer.fit());actual.setOnClickListener(v->viewer.actual());close.setOnClickListener(v->dialog.dismiss());actions.addView(fitButton,weighted());actions.addView(actual,weighted());actions.addView(close,weighted());content.addView(actions);
        dialog.setContentView(content);dialog.show();dialog.getWindow().setLayout(-1,-1);
    }
    private void showCrop(){
        if(source==null||busy||preparing||editingCrop)return;
        try {
            final CropView cropView=new CropView(this,PanelImage.rotated(source,rotation));
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),0,dp(12),0);
            content.addView(text("单指拖动，双指缩放；白框内为保留区域。",13,0xff52635d));
            content.addView(cropView,new LinearLayout.LayoutParams(-1,Math.min(dp(380),getResources().getDisplayMetrics().heightPixels/2)));
            editingCrop=true;controls();
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("裁切图片 · 屏幕比例").setView(content).setNegativeButton("取消",null).setNeutralButton("重置裁切",null).setPositiveButton("应用裁切",null).create();
            dialog.setOnDismissListener(d->{editingCrop=false;if(!destroyed)controls();});dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->cropView.reset());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{Bitmap result=cropView.result();source=result;rotation=0;cropped=true;dialog.dismiss();fit.setSelection(1);prepare();}catch(Exception e){Toast.makeText(this,"裁切失败："+e.getMessage(),Toast.LENGTH_LONG).show();}});
        }catch(Exception e){editingCrop=false;controls();Toast.makeText(this,"无法打开裁切："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void confirmRefresh(){
        if(busy||preparing||prepared==null||rotationSchedule.isRunning())return;
        if(!ensureConnection(()->confirmRefresh()))return;
        if(prepared.panelType.isEmpty()){Toast.makeText(this,"请先选择屏幕类型 A0 或 A1",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("将当前预览写入墨水屏？")
            .setMessage("这会替换当前贴合墨水屏的画面。\n\n目标：相同原厂配置的 552×768 四色屏（不限 UID）\n图片："+imageName+"\n"+prepared.settings+"\n上电等待："+powerTimes[power.getSelectedItemPosition()]+" ms\n忙状态查询间隔："+pollTimes[poll.getSelectedItemPosition()]+" ms\n\n仅贴放要刷新的那块屏。请贴稳，全程保持应用在前台和 NFC 供能，直到日志提示结束。中断可能留下不完整画面。")
            .setNegativeButton("返回预览",null).setPositiveButton("确认并开始刷新",(d,w)->start("refresh")).show();
    }
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private void start(final String mode){
        if(busy||preparing||editingCrop||!resumed)return;
        if(com.inkreader.PanelClient.isReleasePending()){append("阅读会话正在释放，请稍后重新开始。");return;}
        final boolean rootMode=useRoot.isChecked();
        if(rotationSchedule.isRunning()&&(!mode.equals("refresh")||autoTicket==null||!rotationSchedule.isCurrent(autoTicket)))return;
        if(!rootMode){final int expectedRevision=revision;if(!ensureConnection(()->{if(revision==expectedRevision){if(mode.equals("refresh"))confirmRefresh();else start(mode);}else append("图片已改变，请重新确认发送。");}))return;}
        final int seconds=durations[duration.getSelectedItemPosition()];
        final boolean panel=mode.equals("identity")||mode.equals("refresh");
        if(mode.equals("refresh")&&(prepared==null||prepared.panelType.isEmpty())){append("请先在图像与发送页选择屏幕类型 A0 或 A1。");return;}
        final RotationSchedule.Ticket sessionTicket=autoTicket;sessionConfirmed=false;
        final String id=Long.toString(SystemClock.elapsedRealtime());
        final File sessionStop=new File(getFilesDir(),"stop-"+id),jobFile=new File(getFilesDir(),"job-"+id+".json"),imageFile=new File(getFilesDir(),"image-"+id+".bin");
        try{
            // The privileged worker creates and acquires the shared NFC lease.
            if(panel){JSONObject job=new JSONObject();
                if(mode.equals("refresh")){byte[] image=prepared.packed.clone();try(FileOutputStream out=new FileOutputStream(imageFile)){out.write(image);}String sha=PanelProtocol.toHex(MessageDigest.getInstance("SHA-256").digest(image));
                    job.put("panelType",prepared.panelType);if(sessionTicket!=null&&!autoUid.isEmpty())job.put("expectedUid",autoUid);job.put("imageFile",imageFile.getAbsolutePath());job.put("sha256",sha);job.put("powerMs",powerTimes[power.getSelectedItemPosition()]);job.put("pollMs",pollTimes[poll.getSelectedItemPosition()]);append("图片参数："+prepared.settings);append("预览快照 SHA-256="+sha);}
                try(FileOutputStream out=new FileOutputStream(jobFile)){out.write(job.toString().getBytes("UTF-8"));}}
        }catch(Exception e){append("准备失败："+e);imageFile.delete();jobFile.delete();if(sessionTicket!=null)stopRotation("准备定时画面失败，请检查存储空间");return;}
        if(mode.equals("qtag"))capabilityInfo.setText("Q-Poll：测试中，等待实际寻卡命令与响应…");
        stopFile=sessionStop;busy=true;terminal=false;scanning=false;failure.clear();controls();progress.setProgress(0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        state.setText(rootMode?"等待 Root / NFC":BackendMode.label(BackendMode.get(this))+" / NFC");detail.setText(rootMode?"请允许 Root 授权。":useInternal.isChecked()?"使用本应用已激活的授权服务。":"使用 Shizuku 授权，无需 Root。");
        append("──────── "+mode+" · 寻卡 "+seconds+" 秒 ────────");
        final String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin org.nfcresearch.qpolllab.QPollWorker "
            +mode+" "+seconds+" "+quote(sessionStop.getAbsolutePath())+" "+quote(NfcSessionLock.path(android.os.Process.myUid()).toString())+(panel?" "+quote(jobFile.getAbsolutePath()):"");
        new Thread(()->{try{
            if(!rootMode){runBridge(mode,seconds,jobFile,imageFile,sessionStop);return;}
            Process child=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
            Thread watchdog=new Thread(()->{try{if(!child.waitFor(mode.equals("refresh")?320:140,TimeUnit.SECONDS)){sessionStop.createNewFile();main.post(()->append("执行超时，已请求停止。"));if(!child.waitFor(10,TimeUnit.SECONDS))child.destroy();}}catch(Exception ignored){}},"root-watchdog");watchdog.setDaemon(true);watchdog.start();
            try(BufferedReader in=new BufferedReader(new InputStreamReader(child.getInputStream(),"UTF-8"))){String line;while((line=in.readLine())!=null){final String value=line;main.post(()->{if(!destroyed)event(value);});}}
            int code=child.waitFor();if(code!=0)main.post(()->{sessionConfirmed=false;if(!destroyed){append("进程退出码="+code);if(!terminal){state.setText("执行未成功");detail.setText("请检查 Root 授权与日志。");}}});
        }catch(Exception e){main.post(()->{sessionConfirmed=false;if(!destroyed){state.setText("无法执行");detail.setText("启动失败："+e.getMessage());append("启动失败："+e);if(!rootMode&&e instanceof SecurityException){bridgeIdentityReport="发送前后端拒绝调用："+e;invalidateBridge(bridgeIdentityReport);afterConnection=null;if(useShizuku.isChecked())try{Shizuku.unbindUserService(bridgeArgs,bridgeConnection,true);}catch(Exception cleanup){append("移除本应用后端失败："+cleanup);}connectionStatus.setText("后端身份不匹配，请重新连接并查看诊断");connectionButton.setVisibility(View.VISIBLE);refreshConnectionUi();}}});}
        finally{sessionStop.delete();jobFile.delete();imageFile.delete();main.post(()->{busy=false;scanning=false;main.removeCallbacks(tick);if(!destroyed){if(editorObserver!=null)finishEditor(sessionConfirmed&&!failure.present(),sessionConfirmed&&!failure.present()?"刷新周期结束，请目视确认屏幕。":"发送未完成："+(failure.present()?failure.message():detail.getText().toString()));if(sessionTicket!=null)finishScheduledSession(sessionTicket);if(!rotationSchedule.isRunning())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);controls();}});}},"panel-session").start();
    }
    private void event(String line){
        if(!line.startsWith("QP|")){if(!line.trim().isEmpty())append(line);return;}
        String[] p=line.split("\\|",3);if(p.length!=3)return;String kind=p[1],message=p[2];
        if(kind.equals("CHIP"))chipInfo.setText("芯片："+message);
        if(kind.equals("CAPABILITY"))capabilityInfo.setText("Q-Poll："+message);
        if(kind.equals("QTAG_REJECTED"))capabilityInfo.setText("Q-Poll："+message);
        if(failure.accept(kind,message)){sessionConfirmed=false;terminal=true;scanning=false;main.removeCallbacks(tick);state.setText("需要检查");detail.setText(failure.message());publishEditorStatus();append(kind+" · "+message);return;}
        if(failure.present()){append(kind+" · "+message);return;}
        if(kind.equals("START")){String[] f=message.split(":");scanning=true;scanDeadline=SystemClock.elapsedRealtime()+Integer.parseInt(f[1])*1000L;state.setText(f[0].equals("standard")?"普通 A 寻卡":"Q-Poll 寻卡中");main.post(tick);if(!resumed)requestStop("页面已离开");}
        if(kind.equals("TAG")){if(autoTicket!=null&&autoUid.isEmpty()){java.util.regex.Matcher uid=java.util.regex.Pattern.compile("UID=([0-9A-Fa-f]+)").matcher(message);if(uid.find())autoUid=uid.group(1);}scanning=false;main.removeCallbacks(tick);state.setText("收到标签");detail.setText(message);}
        if(kind.equals("STAGE")){scanning=false;main.removeCallbacks(tick);String[] s=message.split("\\|",2);state.setText(s.length==2?s[1]:message);detail.setText("当前贴合的墨水屏 · 保持贴合，不要切换应用。");}
        if(kind.equals("PROGRESS")){String[] f=message.split("\\|",3);int done=Integer.parseInt(f[0]),total=Integer.parseInt(f[1]);progress.setMax(total);progress.setProgress(done);detail.setText("已确认 "+done+" / "+total+" 包 · "+(100*done/total)+"% · 保持贴合");publishEditorStatus();return;}
        if(kind.equals("BUSY"))detail.setText(message+" · 保持贴合供能");
        if(kind.equals("READY")){state.setText("检查通过");detail.setText("授权 / NFC 可用；这不代表 Q-Poll 已受支持。");terminal=true;}
        if(kind.equals("IDENTITY"))detail.setText(message);
        if(kind.equals("LOST")&&autoTicket!=null&&!sessionConfirmed){stopRotation("标签离场，定时刷写已停止");requestStop("标签离场");}
        if(kind.equals("REFRESH_DONE")||kind.equals("REFRESH_UNCONFIRMED")){sessionConfirmed=kind.equals("REFRESH_DONE");terminal=true;state.setText(kind.equals("REFRESH_DONE")?"刷新周期已结束 · 请看屏幕":"刷新结果待确认");detail.setText(message);}
        if(kind.equals("RESULT")){terminal=true;state.setText(message.startsWith("D1")?"屏幕规格校验通过":message.startsWith("已收到")?"寻卡完成":message.startsWith("用户")?"已停止":"未收到标签");detail.setText(message);}
        if(kind.equals("CANCELLED")){sessionConfirmed=false;terminal=true;state.setText("已停止 · 请检查屏幕画面");detail.setText(message);}
        publishEditorStatus();
        append(kind+" · "+message);
    }
    private void publishEditorStatus(){if(editorObserver!=null)editorObserver.status(state.getText()+" · "+detail.getText());}
    private void append(String line){
        String stamped=new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date())+"  "+line+"\n";transcript.append(stamped);
        if(transcript.length()>60000)transcript.delete(0,transcript.indexOf("\n",transcript.length()-50000)+1);
        log.setText(transcript.toString());
        try(FileWriter out=new FileWriter(new File(getFilesDir(),"latest.log"),true)){out.write(stamped);}catch(Exception ignored){}
    }
    private void runBridge(String mode,int seconds,File job,File image,File stop)throws Exception {
        android.os.IBinder binder=bridgeBinder;if(binder==null||!binder.isBinderAlive())throw new IOException("请先在设备页连接当前授权方式。");
        String json="{}";if(job.exists())json=new String(readFile(job),"UTF-8");byte[] bytes=image.exists()?readFile(image):new byte[0];
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();final android.os.Binder owner=new android.os.Binder();
        try{request.writeInterfaceToken(BridgeIdentity.DESCRIPTOR);request.writeString(mode);request.writeInt(seconds);request.writeString(json);request.writeByteArray(bytes);request.writeStrongBinder(owner);if(!binder.transact(1,request,reply,0))throw new IOException("后端不兼容");reply.readException();}finally{request.recycle();reply.recycle();}
        boolean cancelSent=false;
        try{boolean running=true;long deadline=SystemClock.elapsedRealtime()+335000;
            while(running){if(stop.exists()&&!cancelSent){cancelBridge();cancelSent=true;}
                request=Parcel.obtain();reply=Parcel.obtain();
                try{request.writeInterfaceToken(BridgeIdentity.DESCRIPTOR);binder.transact(2,request,reply,0);reply.readException();String logs=reply.readString();running=reply.readInt()!=0;if(logs!=null)for(String line:logs.split("\\n")){final String value=line;main.post(()->{if(!destroyed)event(value);});}}finally{request.recycle();reply.recycle();}
                if(SystemClock.elapsedRealtime()>deadline){cancelBridge();throw new IOException("后端超时，已请求停止。");}if(running)SystemClock.sleep(100);
            }
        }finally{}
    }
    private static byte[] readFile(File file)throws IOException {byte[] bytes=new byte[(int)file.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);}return bytes;}
    private void cancelBridge()throws Exception {android.os.IBinder binder=bridgeBinder;if(binder==null)return;Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{request.writeInterfaceToken(BridgeIdentity.DESCRIPTOR);binder.transact(3,request,reply,0);reply.readException();}finally{request.recycle();reply.recycle();}}
    private void requestStop(String reason){if(!busy)return;try{if(stopFile!=null)stopFile.createNewFile();append(reason+"；等当前通信返回后恢复 NFC，不重发写入。");stop.setEnabled(false);}catch(Exception e){append("停止标记失败："+e);}}
    private void pickPhoto(int request,boolean multiple){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);startActivityForResult(i,request);
    }
    private void applyCard(Bitmap card,String name){
        stopRotation("创作画面已更换");adjusting=true;
        brightness.setProgress(50);contrast.setProgress(50);saturation.setProgress(100);shadows.setProgress(0);perceptual.setChecked(false);binary.setChecked(false);blocks.setSelection(0);dither.setChecked(false);tone.setSelection(1);fit.setSelection(0);
        getSharedPreferences("refresh-settings",MODE_PRIVATE).edit().putInt("brightness",0).putInt("contrast",100).putInt("saturation",100).putInt("shadows",0).putInt("blockSize",1).putBoolean("binary",false).putBoolean("dither",false).putInt("tone",1).apply();
        source=card;originalSource=card;rotation=0;cropped=false;imageName=name;adjusting=false;prepare();
    }
    private void loadGallery(){
        try{org.json.JSONArray values=new org.json.JSONArray(getSharedPreferences("studio",MODE_PRIVATE).getString("gallery","[]"));for(int i=0;i<values.length()&&i<30;i++){String name=values.getString(i);if(name.matches("rotation-[0-9]+-[0-9]+\\.png")&&new File(getFilesDir(),name).isFile())gallery.add(name);}}catch(Exception ignored){}
    }
    private void importGallery(Intent data){
        final ArrayList<android.net.Uri> uris;try{uris=ImagePicker.collect(data,30-gallery.size());}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();showRotation();return;}
        if(uris.isEmpty())return;final ArrayList<String> previous=new ArrayList<>(gallery);
        preparing=true;controls();imageInfo.setText("正在保存轮播图片…");
        new Thread(()->{ArrayList<String> saved=new ArrayList<>();try{
            String batch=Long.toString(System.nanoTime()&Long.MAX_VALUE);
            for(int i=0;i<uris.size();i++){Bitmap b=PanelImage.load(getContentResolver(),uris.get(i));float scale=Math.min(1,1200f/Math.max(b.getWidth(),b.getHeight()));Bitmap small=scale==1?b:Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);String name="rotation-"+batch+"-"+i+".png";saved.add(name);try(FileOutputStream output=new FileOutputStream(new File(getFilesDir(),name))){if(!small.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("保存图片失败");}if(small!=b)b.recycle();small.recycle();}
            main.post(()->{ArrayList<String> combined=new ArrayList<>(previous);combined.addAll(saved);org.json.JSONArray values=new org.json.JSONArray(combined);getSharedPreferences("studio",MODE_PRIVATE).edit().putString("gallery",values.toString()).apply();gallery.clear();gallery.addAll(combined);if(!destroyed){preparing=false;controls();imageInfo.setText("已添加 "+saved.size()+" 张，共 "+gallery.size()+" 张轮播图片");if(resumed)showRotation();}});
        }catch(Exception e){for(String name:saved)new File(getFilesDir(),name).delete();main.post(()->{if(!destroyed){preparing=false;controls();imageInfo.setText("保存轮播失败："+e.getMessage());Toast.makeText(this,"保存轮播失败："+e.getMessage(),Toast.LENGTH_LONG).show();if(resumed)showRotation();}});}},"gallery-import").start();
    }
    private void showRotation(){
        if(busy||preparing||rotationSchedule.isRunning()){Toast.makeText(this,"请先停止并等待当前任务结束",Toast.LENGTH_SHORT).show();return;}
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(12),dp(8),dp(12),dp(8));body.setBackgroundColor(HomeScreen.PAPER);body.addView(text("已保存 "+gallery.size()+" / 30 张 · 按列表顺序轮播",15,HomeScreen.INK));body.addView(text("文件选择器中长按图片可多选；不支持时，可分次添加，已有图片会保留。",13,0xff8b877b));
        HorizontalScrollView strip=new HorizontalScrollView(this);LinearLayout tiles=new LinearLayout(this);strip.addView(tiles);body.addView(strip);
        final AlertDialog panel=new AlertDialog.Builder(this).setTitle("我的轮播图库").setView(body).setNegativeButton("完成",null).create();
        for(String name:new ArrayList<>(gallery)){LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setPadding(dp(4),dp(8),dp(4),dp(8));ImageView image=new ImageView(this);android.graphics.BitmapFactory.Options decode=new android.graphics.BitmapFactory.Options();decode.inSampleSize=8;image.setImageBitmap(android.graphics.BitmapFactory.decodeFile(new File(getFilesDir(),name).toString(),decode));image.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(image,new LinearLayout.LayoutParams(dp(72),dp(96)));Button remove=button("移除");remove.setOnClickListener(v->{gallery.remove(name);new File(getFilesDir(),name).delete();saveGallery();panel.dismiss();showRotation();});tile.addView(remove,new LinearLayout.LayoutParams(dp(72),dp(44)));tiles.addView(tile);}
        Button add=button("继续添加 · 文件多选");add.setEnabled(gallery.size()<30);add.setOnClickListener(v->{panel.dismiss();pickPhoto(61,true);});body.addView(add);
        Button single=button("逐张添加（相册兼容）");single.setEnabled(gallery.size()<30);single.setOnClickListener(v->{panel.dismiss();pickPhoto(61,false);});body.addView(single);
        Button begin=button("设置间隔并开始轮播");begin.setTextColor(Color.WHITE);begin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(HomeScreen.GREEN));begin.setEnabled(!gallery.isEmpty());begin.setOnClickListener(v->{panel.dismiss();new AlertDialog.Builder(this).setTitle("每次完成刷新后等待").setItems(new String[]{"1 分钟","5 分钟","10 分钟","30 分钟","1 小时","6 小时","24 小时"},(d,pos)->{long[] intervals={1,5,10,30,60,360,1440};confirmRotation(false,intervals[pos]*60000);}).show();});body.addView(begin);
        Button clear=button("清空轮播");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("清空全部轮播图片？").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{for(String old:gallery)new File(getFilesDir(),old).delete();gallery.clear();saveGallery();panel.dismiss();showRotation();}).show());body.addView(clear);panel.show();
    }
    private void saveGallery(){getSharedPreferences("studio",MODE_PRIVATE).edit().putString("gallery",new org.json.JSONArray(gallery).toString()).apply();}
    private void confirmRotation(boolean clock,long interval){
        if(busy||editingCrop||rotationSchedule.isRunning())return;
        if(selectedPanelType().isEmpty()){Toast.makeText(this,"先选择实物 A0 或 A1 屏幕类型",Toast.LENGTH_LONG).show();return;}
        if(!useRoot.isChecked()&&!ensureConnection(()->confirmRotation(clock,interval)))return;
        new AlertDialog.Builder(this).setTitle(clock?"开始时钟定时刷写？":"开始多图定时刷写？")
            .setMessage("现在开始首轮；每轮结束后等待 "+interval/60000+" 分钟。\n\n保持应用在前台、手机解锁并持续贴合墨水屏。屏幕没有独立时钟或供电，不会离线自动变化。\n\n离开应用、停止、通信失败或结果未确认都会结束本次定时任务，不自动重试。首轮后只刷写同一块屏。")
            .setNegativeButton("取消",null).setPositiveButton("开始并持续保持贴合",(d,w)->{
                if(busy||rotationSchedule.isRunning()||!resumed||(!clock&&gallery.isEmpty()))return;
                autoClock=clock;autoUid="";rotationSchedule.start(clock?1:gallery.size(),interval,SystemClock.elapsedRealtime());getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);controls();main.post(rotationTick);
            }).show();
    }
    private void runRotationTick(){
        if(!rotationSchedule.isRunning()||destroyed)return;
        if(!resumed||!((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive()||((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()){stopRotation("页面离开或手机已锁定，定时刷写停止");return;}
        if(busy||preparing){main.postDelayed(rotationTick,250);return;}
        RotationSchedule.Ticket ticket=rotationSchedule.claimDue(SystemClock.elapsedRealtime());
        if(ticket==null){long remaining=Math.max(0,rotationSchedule.nextDueMillis()-SystemClock.elapsedRealtime());detail.setText("定时刷写中 · 下次约 "+(remaining+999)/1000+" 秒 · 保持前台和贴合");main.postDelayed(rotationTick,Math.min(1000,Math.max(100,remaining)));return;}
        autoTicket=ticket;preparing=true;controls();final boolean clock=autoClock;final String file=clock?"":gallery.get(ticket.index);
        new Thread(()->{try{Bitmap bitmap=clock?studio.currentClock():PanelImage.load(getContentResolver(),android.net.Uri.fromFile(new File(getFilesDir(),file)));
            main.post(()->{if(destroyed||!resumed||!rotationSchedule.isCurrent(ticket)){bitmap.recycle();return;}source=bitmap;rotation=0;cropped=false;imageName=clock?"时钟定时更新":"轮播 "+(ticket.index+1)+" / "+gallery.size();preparing=false;prepare();});
        }catch(Exception e){main.post(()->{if(!destroyed&&rotationSchedule.isCurrent(ticket)){preparing=false;stopRotation("读取轮播画面失败："+e.getMessage());controls();}});}},"rotation-render").start();
    }
    private void beginScheduledRefresh(){
        if(!resumed||!rotationSchedule.isCurrent(autoTicket)||busy||preparing)return;
        if(!((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive()||((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()){stopRotation("手机锁定，定时刷写停止");return;}
        if(!useRoot.isChecked()){try{boolean authorized=useInternal.isChecked()?bridgeBinder!=null&&bridgeBinder==org.nfcresearch.qpolllab.activation.InternalBackend.photoBinder():Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==android.content.pm.PackageManager.PERMISSION_GRANTED;if(!authorized||bridgeBinder==null||!bridgeBinder.isBinderAlive()){stopRotation("连接不可用，请重新连接后手动开始定时任务");return;}}catch(Exception e){stopRotation("授权服务不可用");return;}}
        start("refresh");
    }
    private void finishScheduledSession(RotationSchedule.Ticket ticket){
        if(!rotationSchedule.isCurrent(ticket))return;
        boolean success=sessionConfirmed&&!failure.present()&&!autoUid.isEmpty();rotationSchedule.complete(ticket,success,SystemClock.elapsedRealtime());autoTicket=null;
        if(!success){detail.setText("定时刷写已暂停：本轮失败或结果未确认，请查看日志后手动开始。");main.removeCallbacks(rotationTick);return;}
        main.post(rotationTick);
    }
    private void stopRotation(String reason){
        boolean wasRunning=rotationSchedule.isRunning();rotationSchedule.stop();autoTicket=null;main.removeCallbacks(rotationTick);
        if(wasRunning&&!destroyed){if(preparing){revision++;preparing=false;}if(!busy)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);detail.setText(reason);append(reason);controls();}
    }

    private void sendFromStudio(PanelImage.Prepared frame,String name,EditorStudio.TransferObserver observer){
        if(busy||rotationSchedule.isRunning()){observer.finished(false,"当前有刷写任务，请先停止并等待结束。");return;}
        if(com.inkreader.PanelClient.isReleasePending()){observer.finished(false,"阅读会话正在释放，请稍后再次推送。");return;}
        if(!resumed||!(studio.isOpen()||photoWorkspace!=null&&photoWorkspace.isActive())){observer.finished(false,"编辑页面已离开，请重新发送。");return;}
        editorObserver=observer;observer.status("正在检查授权与连接…");
        if(!ensureConnection(()->sendFromStudio(frame,name,observer))){if(afterConnection==null)finishEditor(false,useInternal.isChecked()?InternalActivationController.get(this).isActivating()?"授权服务正在激活，请等到“内置服务已激活”后再推送。":"内置服务未就绪，请在激活页完成核验后再推送。":"尚未建立授权连接，请点击本页连接按钮。列表缺失可查看恢复指引。");return;}
        // The photo workspace releases retired display frames. Keep an owned copy
        // for the existing sender/settings while retaining exactly the same bytes.
        Bitmap ownedPreview=frame.preview.copy(Bitmap.Config.ARGB_8888,false);
        if(ownedPreview==null){finishEditor(false,"无法保存发送快照，请重试。");return;}
        revision++;preparing=false;source=ownedPreview;originalSource=ownedPreview;rotation=0;cropped=false;prepared=new PanelImage.Prepared(ownedPreview,frame.packed.clone(),frame.settings,frame.panelType,frame.palette);imageName=name;preview.setImage(ownedPreview);imageInfo.setText(name+" · 与编辑页相同的发送快照");
        start("refresh");if(!busy&&afterConnection==null)finishEditor(false,"本轮未启动，请检查授权和屏幕类型。");
    }
    private void finishEditor(boolean success,String message){EditorStudio.TransferObserver observer=editorObserver;editorObserver=null;if(observer!=null)observer.finished(success,message);}
    private void showConnectionRecovery(){
        String guide=ShizukuDiagnostics.recoveryInstructions();
        new AlertDialog.Builder(this).setTitle("Shizuku 连接诊断与恢复").setMessage(guide)
            .setPositiveButton("切换外置 Shizuku 并连接",(d,w)->{BackendMode.set(this,BackendMode.SHIZUKU);syncBackendSelection();permissionPending=false;main.removeCallbacks(permissionTimeout);connectShizuku(true);})
            .setNeutralButton("打开 Shizuku",(d,w)->{Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch!=null)startActivity(launch);else startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/")));})
            .setNegativeButton("查看 / 复制诊断",(d,w)->{String report=ShizukuDiagnostics.report(this)+"\n后端连接状态："+bridgeIdentityStatus+"\n最近一次身份握手："+bridgeIdentityReport;TextView t=text(report,12,0xff213b33);t.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle("连接诊断（无图片内容）").setView(scroll).setPositiveButton("复制",(a,b)->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Shizuku 连接诊断",report))).setNegativeButton("关闭",null).show();}).show();
    }

    private void showLicense(){
        StringBuilder body=new StringBuilder("参考 https://gitee.com/kur0neko/huawei-epd-pn532\n固定提交 5d3acf730a988815b637262fd4862c1794ab079e\nRequired Notice: Copyright (c) 2026 kur0neko\n\n");
        for(String name:new String[]{"kur0neko-LICENSE.txt","miaomo-LICENSE.txt","miaomo-NOTICE.txt","miaomo-THIRD-PARTY-LICENSES.txt","inkreader-LICENSE.txt","inkreader-NOTICE.txt","inkreader-THIRD-PARTY-NOTICES.txt","inkreader-lzokay-LICENSE.txt","activation-LICENSES.txt"}){body.append("\n\n—— ").append(name).append(" ——\n");try(BufferedReader in=new BufferedReader(new InputStreamReader(getAssets().open("licenses/"+name),"UTF-8"))){String line;while((line=in.readLine())!=null)body.append(line).append('\n');}catch(Exception e){body.append(e);}}
        TextView t=text(body.toString(),12,0xff263e35);t.setTextIsSelectable(true);t.setPadding(dp(16),dp(10),dp(16),dp(10));ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle("开源许可与来源").setView(s).setPositiveButton("关闭",null).show();
    }
    @Override protected void onResume(){super.onResume();resumed=true;syncPanelSelection();syncBackendSelection();studio.onHostResume();if(readerShelf!=null)readerShelf.onResume();InternalActivationController activation=InternalActivationController.get(this);activation.attach(this);activation.foregroundStarted(this);connectSelected(false);refreshConnectionUi();}

    @Override protected void onStop(){stopRotation("离开应用，定时刷写已停止");studio.onHostStop();if(photoWorkspace!=null)photoWorkspace.onHostStop();resumed=false;InternalActivationController activation=InternalActivationController.get(this);activation.detach(this);activation.foregroundStopped(this);afterConnection=null;if(busy)requestStop("页面已离开，自动停止");super.onStop();}
    @Override protected void onDestroy(){unwatchInternal();stopRotation("页面关闭");studio.destroy();if(readerShelf!=null)readerShelf.destroy();if(photoWorkspace!=null)photoWorkspace.destroy();if(galleryBrowser!=null)galleryBrowser.destroy();if(settingsDialog!=null)settingsDialog.dismiss();main.removeCallbacks(permissionTimeout);editorObserver=null;destroyed=true;afterConnection=null;main.removeCallbacks(connectionTimeout);Shizuku.removeBinderReceivedListener(binderReceived);Shizuku.removeBinderDeadListener(binderDead);revision++;if(previewDialog!=null)previewDialog.dismiss();if(busy)requestStop("页面关闭，自动停止");Shizuku.removeRequestPermissionResultListener(permissionResult);try{Shizuku.unbindUserService(bridgeArgs,bridgeConnection,false);}catch(Exception ignored){}main.removeCallbacks(tick);super.onDestroy();}
}

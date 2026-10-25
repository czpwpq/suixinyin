package com.inkreader;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;
import org.nfcresearch.qpolllab.ImageRaster;
import org.nfcresearch.qpolllab.PanelType;
import org.nfcresearch.qpolllab.RefreshTestPattern;

/** Explicit factory-driver baseline; it does not probe undocumented opcodes or install drivers. */
public final class ReaderRefreshTestActivity extends Activity {
    private final StringBuilder report=new StringBuilder();
    private PanelClient client;
    private TextView status;
    private ImageView preview;
    private Button connect,sendA,preloadB,showB,stop;
    private Bitmap imageA,imageB;
    private byte[] packedA,packedB;
    private boolean ready,busy,resumed,closed;
    private int stage,revision;
    private String pendingAdvance,pendingPreload;
    private boolean advancingB;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);Ui.applyWindow(this,false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Ui.PAPER);page.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,12));
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);View back=Ui.iconButton(this,"ic_back","返回阅读设置");back.setOnClickListener(v->finish());title.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));title.addView(Ui.title(this,"刷新对照测试",20,Ui.INK));page.addView(title);
        TextView note=Ui.text(this,12,Ui.MUTED,false);note.setText("固定边框，仅中央 A / B 变化。观察边框是否闪烁。\n边框不闪也不能单独证明局刷；本测试不改写驱动。");page.addView(note);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("A/B刷新测试图，四角和边框保持不变");page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        imageA=bitmap(false);imageB=bitmap(true);preview.setImageBitmap(imageA);
        status=Ui.text(this,13,Ui.INK,true);status.setMinLines(3);page.addView(status);
        connect=Ui.primaryButton(this,"连接并开始对照");connect.setOnClickListener(v->connect());page.addView(connect,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));gap(page,8);
        LinearLayout actions=new LinearLayout(this);sendA=Ui.button(this,"① 显示 A");preloadB=Ui.button(this,"② 预传 B");showB=Ui.button(this,"③ 显示 B");sendA.setOnClickListener(v->advance(false));preloadB.setOnClickListener(v->preload());showB.setOnClickListener(v->advance(true));actions.addView(sendA,weighted());actions.addView(preloadB,weighted());actions.addView(showB,weighted());page.addView(actions);gap(page,8);
        LinearLayout bottom=new LinearLayout(this);stop=Ui.button(this,"停止测试");stop.setOnClickListener(v->stop("已停止，保留屏幕当前画面"));Button copy=Ui.button(this,"复制测试记录");copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("随心印刷新对照",report.toString()));Toast.makeText(this,"测试记录已复制",Toast.LENGTH_SHORT).show();});bottom.addView(stop,weighted());bottom.addView(copy,weighted());page.addView(bottom);
        setContentView(page);line("原厂刷新 A/B 对照；未执行局刷命令，未写入 DA/DB。");show("尚未发送。请贴稳屏幕后连接。");
    }
    private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);p.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);return p;}
    private void gap(LinearLayout page,int size){page.addView(new View(this),new LinearLayout.LayoutParams(1,Ui.dp(this,size)));}
    private Bitmap bitmap(boolean b){byte[] codes=RefreshTestPattern.codes(b);int[] pixels=new int[codes.length];for(int i=0;i<pixels.length;i++)pixels[i]=ImageRaster.color(codes[i]);return Bitmap.createBitmap(pixels,552,768,Bitmap.Config.ARGB_8888);}
    private void connect(){
        if(!resumed||busy||closed)return;stop(null);String panel=Ui.selectedPanelType(this);
        if(!"A0".equals(panel)&&!"A1".equals(panel)){show("请先在设备页选择实物 A0 或 A1。");return;}
        packedA=PanelType.parse(panel).pack(RefreshTestPattern.codes(false));packedB=PanelType.parse(panel).pack(RefreshTestPattern.codes(true));
        final int token=++revision;stage=0;ready=false;busy=true;line("连接："+panel+"；A标准等待2000ms/查询100ms；B预传命中使用阅读链路参数。");
        client=new PanelClient(this,new PanelClient.Listener(){
            public void onPanelStatus(String text){if(current(token)){line(text);show(text);}}
            public void onPanelDiagnostic(String text){if(current(token))line(text);}
            public void onPanelReady(){if(current(token)){ready=true;busy=false;show("已连接。先显示 A，完成后再预传 B。");}}
            public void onPreloadDone(String id){if(current(token)&&id.equals(pendingPreload)){pendingPreload=null;stage=2;busy=false;show("B 已预传，实体屏仍应显示 A。点③观察边框。");}}
            public void onAdvanced(String id,boolean fast,long millis){if(current(token)&&id.equals(pendingAdvance)){pendingAdvance=null;busy=false;stage=advancingB?3:1;preview.setImageBitmap(advancingB?imageB:imageA);String text=(advancingB?"B":"A")+" 完成 · "+(fast?"预传命中":"完整传图")+" · "+millis+" ms；均为原厂全局刷新。";line(text);show(text+(advancingB?"\n请记录：固定边框是否闪烁，画面是否完整。":"\n继续②预传 B，不会立即改变实体画面。"));}}
            public void onPanelFailure(String error){if(current(token)){line("失败："+error);stop("测试失败："+error+"\n不会自动重试，请核对屏幕。");}}
        },panel,2000,100);show("正在建立测试会话，请保持贴合。");client.start();
    }
    private boolean current(int token){return !closed&&resumed&&client!=null&&token==revision;}
    private void advance(boolean b){if(!ready||busy||client==null||stage!=(b?2:0))return;busy=true;advancingB=b;pendingAdvance=client.submitAdvance(b?packedB:packedA);if(pendingAdvance==null){stop("页面未提交，请重新连接。");return;}show((b?"显示 B":"显示 A")+" · 刷屏中，请勿移动");}
    private void preload(){if(!ready||busy||client==null||stage!=1)return;busy=true;pendingPreload=client.submitPreload(packedB);if(pendingPreload==null){stop("预传未提交，请重新连接。");return;}show("预传 B 中，屏幕仍显示 A，请勿移动");}
    private void stop(String text){revision++;PanelClient old=client;client=null;ready=busy=false;pendingAdvance=pendingPreload=null;stage=0;if(old!=null)old.close();if(text!=null){line(text);show(text);}else controls();}
    private void line(String text){report.append(String.format(Locale.ROOT,"%tT · ",new java.util.Date())).append(text).append('\n');if(report.length()>60000)report.delete(0,report.indexOf("\n",report.length()-45000)+1);}
    private void show(String text){if(status!=null)status.setText(text);controls();}
    private void controls(){if(connect==null)return;connect.setEnabled(resumed&&!closed&&!busy&&client==null);sendA.setEnabled(resumed&&ready&&!busy&&stage==0);preloadB.setEnabled(resumed&&ready&&!busy&&stage==1);showB.setEnabled(resumed&&ready&&!busy&&stage==2);stop.setEnabled(client!=null);}
    @Override protected void onResume(){super.onResume();resumed=true;controls();}
    @Override protected void onStop(){resumed=false;stop("已离开测试页，会话停止；返回后不会自动发送。");super.onStop();}
    @Override protected void onDestroy(){closed=true;stop(null);preview.setImageDrawable(null);if(imageA!=null)imageA.recycle();if(imageB!=null)imageB.recycle();super.onDestroy();}
}

package org.nfcresearch.qpolllab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.nfcresearch.qpolllab.activation.ActivationInputs;

/** Explicit local pairing and activation, styled like the existing photo/device workspace. */
public final class InternalActivationActivity extends Activity implements InternalActivationController.Listener {
    private static final int NOTICE_PERMISSION=2192;
    private InternalActivationController controller;
    private TextView state,detail,discoveryState,activationHint;
    private LinearLayout statusCard;
    private ScrollView setupScroll;
    private ProgressBar activationProgress;
    private AlertDialog leaveDialog;
    private EditText pairingPort,connectionPort,code;
    private Button pair,activate,discover,notification,stop,headerBack,settingsButton;
    private boolean visible,destroyed;
    private int lastPairPort,lastConnectPort;
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);controller=InternalActivationController.get(this);getWindow().setStatusBarColor(AppUi.BG);getWindow().setNavigationBarColor(AppUi.BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout page=AppUi.column(this);page.setBackgroundColor(AppUi.BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(7),dp(16),dp(8));headerBack=AppUi.ghost(this,"‹ 返回",false,this::requestLeave);header.addView(headerBack);TextView heading=AppUi.text(this,"内置激活",22,AppUi.INK,true);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        statusCard=AppUi.card(this);state=AppUi.text(this,"尚未激活",18,AppUi.INK,true);statusCard.addView(state);detail=AppUi.text(this,"",13,AppUi.INK,false);detail.setMaxLines(3);detail.setEllipsize(TextUtils.TruncateAt.END);statusCard.addView(detail);activationProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);activationProgress.setIndeterminate(true);activationProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(AppUi.INK));activationProgress.setVisibility(View.GONE);LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(3));progressParams.setMargins(0,dp(9),0,dp(7));statusCard.addView(activationProgress,progressParams);activationHint=AppUi.text(this,"无线 ADB 连接 ≠ 授权服务就绪。请以本页“内置服务已激活”为完成标志。",12,AppUi.MUTED,false);statusCard.addView(activationHint);LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);statusParams.setMargins(dp(18),0,dp(18),dp(12));page.addView(statusCard,statusParams);
        setupScroll=new ScrollView(this);setupScroll.setFillViewport(false);LinearLayout body=AppUi.column(this);body.setPadding(dp(18),0,dp(18),dp(26));setupScroll.addView(body);page.addView(setupScroll,new LinearLayout.LayoutParams(-1,0,1));
        if(Build.VERSION.SDK_INT<30){body.addView(AppUi.text(this,"此功能需要Android 11或更新版本。当前系统可继续使用外置Shizuku或Root方式；照片编辑和本地阅读不受影响。",15,AppUi.INK,false));setContentView(page);return;}
        body.addView(AppUi.text(this,"只需安装随心印。首次配对需在系统开启无线调试；重启或系统停止后台服务后，需再次激活。此页面只启动连接，不会自动刷屏。",13,AppUi.MUTED,false));AppUi.gap(body,14);
        LinearLayout first=card(body,"① 开启无线调试","连接Wi-Fi，打开开发者选项中的无线调试。配对窗口和连接主页显示的是两个不同端口。");
        settingsButton=AppUi.button(this,"打开无线调试设置",false,this::openSettings);first.addView(settingsButton);
        notification=AppUi.button(this,"开始通知栏配对",false,this::startNotificationPairing);first.addView(notification);first.addView(AppUi.text(this,"先点通知栏配对，再打开系统的“使用配对码配对设备”。保持弹窗打开，下拉并展开通知，点击输入按钮；未发现端口时填“配对端口 空格 6位码”。",12,AppUi.MUTED,false));
        AppUi.gap(body,14);LinearLayout second=card(body,"② 配对本机","已配对过可直接跳到③。也可以分屏显示系统配对窗口，在这里手动填写。");
        discoveryState=AppUi.text(this,"仅发现并连接本机服务，不连接局域网其他设备",12,AppUi.MUTED,false);second.addView(discoveryState);
        discover=AppUi.button(this,"查找本机端口",false,()->controller.discover());second.addView(discover);
        pairingPort=portField(second,"配对端口","配对弹窗里的端口，例如 37123");
        second.addView(AppUi.label(this,"6位配对码"));code=new EditText(this);code.setTextColor(AppUi.INK);code.setTextSize(16);code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});code.setSaveEnabled(false);code.setHint("只用于本次配对，不保存");second.addView(code,new LinearLayout.LayoutParams(-1,dp(50)));
        pair=AppUi.button(this,"配对本机",false,this::pair);second.addView(pair);
        AppUi.gap(body,14);LinearLayout third=card(body,"③ 激活内置服务","连接端口来自无线调试主页的“IP地址和端口”。点击激活后，请留在本页等待顶部显示“内置服务已激活”。");
        connectionPort=portField(third,"连接端口","无线调试主页的端口，例如 40567");
        third.addView(AppUi.text(this,"安卓提示“无线调试已连接”，只表示无线 ADB 通道建立；仍需等待内置授权服务启动并验证。激活成功后，照片和阅读都使用内置后端。",12,AppUi.MUTED,false));
        activate=AppUi.button(this,"激活并使用内置连接",true,this::activate);third.addView(activate);
        AppUi.gap(body,14);stop=AppUi.button(this,"停止内置服务 / 取消操作",false,this::stopOperation);body.addView(stop);
        AppUi.gap(body,10);body.addView(AppUi.button(this,"查看 / 复制激活诊断",false,this::diagnostics));AppUi.gap(body,10);body.addView(AppUi.text(this,"激活成功只代表权限后端可用；NFC兼容性和供电仍需实际发送验证。配对失败可重新获取端口和配对码，或在设备页切换外置Shizuku。",12,AppUi.MUTED,false));
        setContentView(page);if(saved!=null){pairingPort.setText(saved.getString("pairPort",""));connectionPort.setText(saved.getString("connectPort",""));}
    }
    private int dp(float value){return AppUi.dp(this,value);}
    private LinearLayout card(LinearLayout body,String title,String subtitle){LinearLayout card=AppUi.card(this);card.addView(AppUi.text(this,title,17,AppUi.INK,true));AppUi.gap(card,5);card.addView(AppUi.text(this,subtitle,12,AppUi.MUTED,false));AppUi.gap(card,9);body.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;}
    private EditText portField(LinearLayout parent,String label,String hint){parent.addView(AppUi.label(this,label));EditText field=new EditText(this);field.setSingleLine(true);field.setTextColor(AppUi.INK);field.setTextSize(16);field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});field.setHint(hint);parent.addView(field,new LinearLayout.LayoutParams(-1,dp(50)));return field;}
    private void pair(){char[] owned=null;try{int port=ActivationInputs.port(pairingPort.getText().toString());Editable input=code.getText();owned=new char[input.length()];TextUtils.getChars(input,0,input.length(),owned,0);code.setText("");ActivationInputs.pairingCode(owned);controller.pair(port,owned);}catch(IllegalArgumentException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}finally{ActivationInputs.clear(owned);}}
    private void activate(){try{int port=ActivationInputs.port(connectionPort.getText().toString());View focus=getCurrentFocus();if(focus!=null){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}controller.activate(port);}catch(IllegalArgumentException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}}
    private void openSettings(){
        if(controller.isActivating()){Toast.makeText(this,"正在等待授权服务就绪，请留在本页或明确取消本次激活",Toast.LENGTH_LONG).show();return;}
        if(WirelessDebugSettings.open(this))return;
        Toast.makeText(this,"请手动打开系统开发者选项中的无线调试",Toast.LENGTH_LONG).show();
    }
    private void startNotificationPairing(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTICE_PERMISSION);return;}controller.notificationPairingStart();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==NOTICE_PERMISSION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){controller.notificationPairingStart();}else if(request==NOTICE_PERMISSION)Toast.makeText(this,"未开启通知，可使用分屏手动配对",Toast.LENGTH_LONG).show();}
    private void diagnostics(){String report=controller.diagnostics();TextView text=AppUi.text(this,report,12,AppUi.INK,false);text.setTextIsSelectable(true);text.setPadding(dp(16),dp(10),dp(16),dp(10));ScrollView view=new ScrollView(this);view.addView(text);new AlertDialog.Builder(this).setTitle("内置激活诊断（无配对码）").setView(view).setNegativeButton("关闭",null).setPositiveButton("复制",(d,w)->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("随心印内置激活",report))).show();}
    @Override public void changed(){if(!visible||destroyed)return;boolean activating=controller.isActivating(),ready=controller.ready()&&!activating,busy=controller.isBusy();state.setText(ready?"● 内置服务已激活":activating?"● 正在激活授权服务，请等待":busy?"● 正在处理，请稍候":"○ 尚未激活");detail.setText(controller.status());activationProgress.setVisibility(busy?View.VISIBLE:View.GONE);activationProgress.setContentDescription(activating?"正在等待授权服务完成启动和身份验证":"当前操作进行中");activationHint.setText(ready?"授权服务已通过身份验证，可以返回照片或阅读页使用。尚未刷屏。":activating?"系统显示“无线调试已连接”，只表示 ADB 通道已连接。请留在本页，等待“内置服务已激活”后再返回。":"无线 ADB 连接 ≠ 授权服务就绪。请以本页“内置服务已激活”为完成标志。");if(!activating&&leaveDialog!=null&&leaveDialog.isShowing())leaveDialog.dismiss();if(pair==null)return;pair.setEnabled(!busy);activate.setEnabled(!busy&&!ready);activate.setText(ready?"已激活，可返回使用":activating?"正在激活，请等待服务就绪…":busy?"请等待当前操作完成":"激活并使用内置连接");discover.setEnabled(!busy);notification.setEnabled(!busy);settingsButton.setEnabled(!activating);pairingPort.setEnabled(!busy);connectionPort.setEnabled(!busy);code.setEnabled(!busy);stop.setText(activating?"取消本次激活":"停止内置服务 / 取消操作");stop.setEnabled(busy||ready||controller.isDiscovering()||controller.notificationPairing());discoveryState.setText(controller.isDiscovering()?"正在查找本机端口 · 配对与连接端口会分别填写":"仅发现并连接本机服务，不连接局域网其他设备");int p=controller.pairingPort(),c=controller.connectionPort();if(p>0&&p!=lastPairPort){pairingPort.setText(Integer.toString(p));lastPairPort=p;}if(c>0&&c!=lastConnectPort){connectionPort.setText(Integer.toString(c));lastConnectPort=c;}}
    private void requestLeave(){if(controller.isActivating()){confirmCancelActivation(true);return;}finish();}
    private void confirmCancelActivation(boolean leave){if(leaveDialog!=null&&leaveDialog.isShowing())return;AlertDialog dialog=new AlertDialog.Builder(this).setTitle("授权服务尚未就绪").setMessage("安卓的“无线调试已连接”仅表示 ADB 通道已经建立。请等待本页显示“内置服务已激活”。\n\n可以继续等待，或明确取消本次激活。尚未刷屏。").setPositiveButton("继续等待",null).setNegativeButton(leave?"取消激活并返回":"取消本次激活",(d,w)->{controller.cancelActivation();if(leave)finish();}).create();leaveDialog=dialog;dialog.setOnDismissListener(d->{if(leaveDialog==dialog)leaveDialog=null;});dialog.show();}
    private void stopOperation(){if(controller.isActivating()){confirmCancelActivation(false);return;}new AlertDialog.Builder(this).setTitle("停止内置连接？").setMessage("将结束内置连接及正在进行的内置刷写。屏幕保留当前画面，不会自动重试。").setNegativeButton("返回",null).setPositiveButton("停止",(d,w)->controller.stop()).show();}
    @Override public void onBackPressed(){requestLeave();}
    @Override protected void onResume(){super.onResume();visible=true;controller.attach(this);}
    @Override protected void onStop(){visible=false;if(code!=null)code.setText("");controller.detach(this);controller.pageStopped();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;if(leaveDialog!=null)leaveDialog.dismiss();controller.detach(this);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){if(pairingPort!=null){out.putString("pairPort",pairingPort.getText().toString());out.putString("connectPort",connectionPort.getText().toString());}super.onSaveInstanceState(out);}
}

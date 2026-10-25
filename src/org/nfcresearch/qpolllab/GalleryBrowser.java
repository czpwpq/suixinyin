package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** User-configured HTTPS community gallery. No native JavaScript bridge or local-file access.
 * Root Activity forwards onActivityResult before its generic cancellation handling.
 */
public final class GalleryBrowser {
    /** First request code; each chooser receives a new persisted 16-bit code. */
    public static final int REQUEST_FILES=82;
    private static final int LAST_FILE_REQUEST=32767;
    private static final int PAPER=HomeScreen.PAPER,INK=HomeScreen.INK;
    private final Activity activity;
    private final SharedPreferences preferences;
    private Dialog dialog;private AlertDialog configuration;
    private WebView web;private ProgressBar loading;private TextView address,status;
    private ValueCallback<Uri[]> fileCallback;private boolean chooseMultiple;private int fileRequestCode=-1;
    private final LinkedHashSet<String> queuedDownloads=new LinkedHashSet<>();
    public GalleryBrowser(Activity activity){this.activity=activity;preferences=activity.getSharedPreferences("gallery-web",Activity.MODE_PRIVATE);}

    public void open(){
        if(dialog!=null){dialog.show();return;}
        String saved=preferences.getString("url","");
        if(saved==null||saved.trim().isEmpty()){configure();return;}
        final String url;
        try{url=GalleryUrl.normalize(saved);}catch(IllegalArgumentException invalid){configure();return;}
        build();web.loadUrl(url);
    }
    public void configure(){
        if(configuration!=null&&configuration.isShowing())return;
        LinearLayout fields=new LinearLayout(activity);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),dp(8),dp(20),dp(8));fields.setBackgroundColor(PAPER);
        TextView hint=label("填写随心印或你自己的图库网址。网址会保存在本机，可随时更换。",14);fields.addView(hint);
        EditText entry=new EditText(activity);entry.setSingleLine(true);entry.setTextColor(INK);entry.setHint("https://你的图库网址");entry.setText(preferences.getString("url",""));entry.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);fields.addView(entry);
        TextView issue=label("",12);fields.addView(issue);
        AlertDialog window=new AlertDialog.Builder(activity).setTitle("设置热门图库网址").setView(fields).setNegativeButton("取消",null).setPositiveButton("保存并打开",null).create();configuration=window;
        window.setOnDismissListener(d->{if(configuration==window)configuration=null;});window.show();
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String url=GalleryUrl.normalize(entry.getText().toString());finishFileChooser(null);preferences.edit().putString("url",url).apply();window.dismiss();if(web==null)open();else web.loadUrl(url);}
            catch(IllegalArgumentException invalid){issue.setText(invalid.getMessage());}
        });
    }
    private void build(){
        Dialog window=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar);dialog=window;
        LinearLayout page=new LinearLayout(activity);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(14),dp(12),dp(14),dp(10));page.setBackgroundColor(PAPER);
        LinearLayout title=new LinearLayout(activity);title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.addView(label("随心印 · 热门图库",22),new LinearLayout.LayoutParams(0,-2,1));Button close=button("关闭");close.setOnClickListener(v->window.dismiss());title.addView(close);page.addView(title);
        address=label("",12);address.setMaxLines(2);page.addView(address);
        LinearLayout actions=new LinearLayout(activity);
        Button back=button("返回"),reload=button("刷新"),settings=button("网址"),downloads=button("下载记录"),external=button("外部打开");
        back.setOnClickListener(v->{if(web!=null&&web.canGoBack())web.goBack();else window.dismiss();});
        reload.setOnClickListener(v->{if(web!=null)web.reload();});settings.setOnClickListener(v->configure());downloads.setOnClickListener(v->openDownloads());external.setOnClickListener(v->openExternal());
        for(Button item:new Button[]{back,reload,settings,downloads,external})actions.addView(item,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(actions);
        loading=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);loading.setMax(100);page.addView(loading,new LinearLayout.LayoutParams(-1,dp(4)));
        status=label("正在打开图库…",12);page.addView(status);
        web=new WebView(activity);web.setBackgroundColor(PAPER);configureWebView(web);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return blockNavigation(request.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return blockNavigation(url);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String scheme=request.getUrl().getScheme();
                if("file".equalsIgnoreCase(scheme)||"content".equalsIgnoreCase(scheme))
                    return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){if(web!=view)return;finishFileChooser(null);address.setText(url);status.setText("正在加载图库…");loading.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){if(web!=view)return;address.setText(url);loading.setVisibility(View.GONE);if(!status.getText().toString().startsWith("加载失败")&&!status.getText().toString().startsWith("证书"))status.setText("可浏览作品、上传图片；下载会保存到系统下载管理器。");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(web==view&&request.isForMainFrame()){status.setText("加载失败，请检查网址和网络，然后点击刷新。");loading.setVisibility(View.GONE);}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(web==view&&request.isForMainFrame()){status.setText("加载失败：网站返回 HTTP "+response.getStatusCode()+"，可稍后刷新。");loading.setVisibility(View.GONE);}}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();if(web==view){status.setText("证书验证失败，请使用证书有效的 HTTPS 图库网址。");loading.setVisibility(View.GONE);}}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(web==view){loading.setProgress(value);if(value>=100)loading.setVisibility(View.GONE);}}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(web!=view){callback.onReceiveValue(null);return true;}
                finishFileChooser(null);fileCallback=callback;chooseMultiple=params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE;
                try{fileRequestCode=nextFileRequest();activity.startActivityForResult(ImagePicker.request(chooseMultiple),fileRequestCode);}
                catch(RuntimeException missing){finishFileChooser(null);toast("无法打开照片或文件选择器，请检查系统设置");}
                return true;
            }
        });
        web.setDownloadListener((url,userAgent,disposition,mime,length)->confirmDownload(url,userAgent,disposition,mime));
        window.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){if(web!=null&&web.canGoBack())web.goBack();else window.dismiss();return true;}return false;});
        window.setOnDismissListener(d->{if(dialog==window){dialog=null;release();}});window.setContentView(page);window.show();window.getWindow().setLayout(-1,-1);
    }
    private static void configureWebView(WebView view){
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
        // No addJavascriptInterface: the site only receives URIs explicitly selected by the user.
    }
    private boolean blockNavigation(String url){
        if(GalleryUrl.isHttps(url))return false;
        toast("仅打开 HTTPS 网页；本地文件和其他应用链接不会在图库中打开。");return true;
    }
    public boolean onActivityResult(int request,int result,Intent data){
        if(fileRequestCode<0||request!=fileRequestCode)return false;
        if(fileCallback==null)return true;
        if(result!=Activity.RESULT_OK||data==null){finishFileChooser(null);return true;}
        try{ArrayList<Uri> selected=ImagePicker.collect(data,chooseMultiple?30:1);finishFileChooser(selected.isEmpty()?null:selected.toArray(new Uri[0]));}
        catch(IllegalArgumentException invalid){toast(invalid.getMessage());finishFileChooser(null);}
        return true;
    }
    private int nextFileRequest(){int value=preferences.getInt("nextFileRequest",REQUEST_FILES);if(value<REQUEST_FILES||value>LAST_FILE_REQUEST)value=REQUEST_FILES;if(!preferences.edit().putInt("nextFileRequest",value==LAST_FILE_REQUEST?REQUEST_FILES:value+1).commit())throw new IllegalStateException("Unable to reserve file request");return value;}
    private void finishFileChooser(Uri[] values){ValueCallback<Uri[]> callback=fileCallback;fileCallback=null;fileRequestCode=-1;if(callback!=null)callback.onReceiveValue(values);}

    private void confirmDownload(String url,String agent,String disposition,String mime){
        if(!GalleryUrl.isHttps(url)){toast("仅支持从 HTTPS 链接下载图片");return;}
        if(queuedDownloads.contains(url)){toast("这张图片已加入下载，请查看下载记录");return;}
        String suggested=GalleryUrl.fileName(URLUtil.guessFileName(url,disposition,mime));
        enqueue(url,agent,mime,suggested,Build.VERSION.SDK_INT>=29);
    }
    private void enqueue(String url,String agent,String mime,String suggested,boolean publicFolder){
        if(queuedDownloads.contains(url))return;
        try{
            DownloadManager manager=(DownloadManager)activity.getSystemService(Activity.DOWNLOAD_SERVICE);if(manager==null)throw new IllegalStateException("下载服务不可用");
            // Preserve the source URL verbatim, including request_id/signature. A website can
            // deduplicate its initial WebView request and this DownloadManager follow-up.
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
            String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null&&!cookie.isEmpty())request.addRequestHeader("Cookie",cookie);
            if(agent!=null&&!agent.isEmpty())request.addRequestHeader("User-Agent",agent);
            if(web!=null&&GalleryUrl.isHttps(web.getUrl()))request.addRequestHeader("Referer",web.getUrl());
            if(mime!=null&&!mime.isEmpty())request.setMimeType(mime);
            String file=System.currentTimeMillis()+"-"+suggested;
            request.setTitle(suggested);request.setDescription("随心印 · 图库图片");request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if(publicFolder)request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file);
            else request.setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,file);
            manager.enqueue(request);if(queuedDownloads.size()>=128)queuedDownloads.remove(queuedDownloads.iterator().next());queuedDownloads.add(url);
            toast(publicFolder?"已加入下载，完成后可在手机 Download / 下载目录查看":"已加入下载，可从下载记录打开（应用外部目录）");
        }catch(Exception failure){toast("无法开始下载，请稍后重试或在浏览器打开图库");}
    }
    private void openDownloads(){try{activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(ActivityNotFoundException missing){toast("未找到下载记录，请在系统文件管理器中查看 Download 目录");}}
    private void openExternal(){String url=web==null?"":web.getUrl();if(!GalleryUrl.isHttps(url)){toast("当前没有可打开的 HTTPS 网页");return;}try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException missing){toast("没有找到可用的浏览器");}}
    public void destroy(){
        if(configuration!=null){configuration.dismiss();configuration=null;}
        if(dialog!=null){dialog.dismiss();dialog=null;}else release();
    }
    private void release(){
        finishFileChooser(null);
        WebView previous=web;web=null;if(previous!=null){previous.stopLoading();previous.setDownloadListener(null);previous.setWebChromeClient(null);previous.setWebViewClient(new WebViewClient());if(previous.getParent() instanceof ViewGroup)((ViewGroup)previous.getParent()).removeView(previous);previous.destroy();}
        queuedDownloads.clear();
    }
    private TextView label(String value,int size){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(INK);text.setPadding(0,dp(5),0,dp(5));return text;}
    private Button button(String text){Button button=new Button(activity);button.setText(text);button.setTextSize(13);button.setTextColor(HomeScreen.GREEN);button.setAllCaps(false);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(4),0,dp(4),0);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(HomeScreen.SAND));return button;}
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}

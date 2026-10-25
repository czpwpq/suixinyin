package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Device-only chooser isolation and WebView configuration checks; no site is loaded. */
public final class GalleryNativeChecks {
    private GalleryNativeChecks() { }
    public static void run(Instrumentation instrumentation,Activity activity) throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        instrumentation.runOnMainSync(()->{try{cases(activity);}catch(Throwable problem){failure.set(problem);}});
        Throwable problem=failure.get();if(problem instanceof Exception)throw (Exception)problem;if(problem instanceof Error)throw (Error)problem;
    }
    private static void cases(Activity activity) throws Exception {
        SharedPreferences preferences=activity.getSharedPreferences("gallery-web",Activity.MODE_PRIVATE);
        boolean hadCounter=preferences.contains("nextFileRequest");int oldCounter=preferences.getInt("nextFileRequest",GalleryBrowser.REQUEST_FILES);
        GalleryBrowser first=new GalleryBrowser(activity),second=new GalleryBrowser(activity);WebView web=null;
        try {
            int older=((Number)call(first,"nextFileRequest",new Class<?>[0])).intValue();
            int current=((Number)call(second,"nextFileRequest",new Class<?>[0])).intValue();
            check(older!=current&&older>=82&&current<=32767,"Chooser reused an old request code across browser instances");
            Capture retired=new Capture();set(first,"fileCallback",retired);set(first,"fileRequestCode",older);
            first.destroy();check(retired.calls==1&&retired.value==null,"Closing browser did not cancel its file callback");
            Capture active=new Capture();set(second,"fileCallback",active);set(second,"fileRequestCode",current);set(second,"chooseMultiple",true);
            Intent result=new Intent().setData(Uri.parse("content://gallery-native-test/photo/1"));
            check(!second.onActivityResult(older,Activity.RESULT_OK,result)&&active.calls==0,"Retired picker result reached a new site's callback");
            check(second.onActivityResult(current,Activity.RESULT_OK,result)&&active.calls==1&&active.value.length==1,"Current picker result was not delivered once");
            check(!second.onActivityResult(current,Activity.RESULT_OK,result)&&active.calls==1,"Picker result was delivered twice");
            Capture navigation=new Capture();set(second,"fileCallback",navigation);int navigating=((Number)call(second,"nextFileRequest",new Class<?>[0])).intValue();set(second,"fileRequestCode",navigating);
            call(second,"finishFileChooser",new Class<?>[]{Uri[].class},(Object)null);
            check(navigation.calls==1&&navigation.value==null&&!second.onActivityResult(navigating,Activity.RESULT_OK,result),"Navigation cancellation left a live upload callback");

            web=new WebView(activity);Method setup=GalleryBrowser.class.getDeclaredMethod("configureWebView",WebView.class);setup.setAccessible(true);setup.invoke(null,web);
            WebSettings settings=web.getSettings();
            check(settings.getJavaScriptEnabled()&&settings.getDomStorageEnabled(),"Modern gallery features disabled");
            check(!settings.getAllowFileAccess()&&!settings.getAllowContentAccess(),"Local resource access enabled");
            check(!settings.getAllowFileAccessFromFileURLs()&&!settings.getAllowUniversalAccessFromFileURLs(),"Local-origin cross access enabled");
            check(settings.getMixedContentMode()==WebSettings.MIXED_CONTENT_NEVER_ALLOW&&settings.getSafeBrowsingEnabled(),"Web security defaults weakened");
            check(!CookieManager.getInstance().acceptThirdPartyCookies(web),"Third-party cookies unexpectedly enabled");
            ImagePickerNativeChecks.run();
        } finally {
            first.destroy();second.destroy();if(web!=null)web.destroy();
            SharedPreferences.Editor restore=preferences.edit();if(hadCounter)restore.putInt("nextFileRequest",oldCounter);else restore.remove("nextFileRequest");restore.commit();
        }
    }
    private static final class Capture implements ValueCallback<Uri[]> {int calls;Uri[] value;public void onReceiveValue(Uri[] values){calls++;value=values;}}
    private static void set(Object object,String name,Object value) throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static Object call(Object object,String name,Class<?>[] types,Object... args) throws Exception {Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,args);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

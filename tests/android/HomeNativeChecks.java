package org.nfcresearch.qpolllab;
import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import java.lang.reflect.Field;

/** Device-only navigation check. Does not start network, picker, Root or NFC operations. */
public final class HomeNativeChecks {
    public static void run(Activity activity)throws Exception{
        View root=activity.getWindow().getDecorView();
        check(find(root,"随心印")!=null,"App branding missing");
        Button[] tabs=(Button[])read(activity,"mainTabs");
        check(tabs.length==4,"Home must expose four native pages");
        String[] names={"照片","阅读","设备","日志"};
        for(int i=0;i<names.length;i++){
            check(names[i].contentEquals(tabs[i].getText()),"Wrong home tab label: "+i);
            check(tabs[i].getVisibility()==View.VISIBLE&&tabs[i].isEnabled(),"Home tab unavailable: "+names[i]);
        }
        check(((Number)read(activity,"mainPage")).intValue()==0,"Home must start on local photo workspace");
        PhotoWorkspace photo=(PhotoWorkspace)read(activity,"photoWorkspace");check(photo!=null,"Photo workspace was not initialized");
        View more=findDescription(root,"更多选项");check(more!=null,"Top-level more menu missing");
        check(more.getParent()==find(root,"随心印").getParent(),"More menu did not move to the main brand row");
        check(!insideScroll(more)&&!descendant(more,photo.view),"Main more menu is inside the photo scroller");
        check(find(photo.view,"照片工作室")==null&&find(photo.view,"让喜欢的画面，成为日常。")==null,"Removed photo heading/subtitle remains on home");
        check(find(root,"热门图库")==null,"Deferred gallery still has a home entry");
        tabs[1].performClick();check(((Number)read(activity,"mainPage")).intValue()==1,"Reading tab did not select bookshelf");
        check(read(activity,"readerShelf")!=null&&find(root,"＋ 导入 TXT")!=null,"Embedded reader bookshelf missing");
        android.content.SharedPreferences panelPrefs=activity.getSharedPreferences("refresh-settings",Activity.MODE_PRIVATE);String oldPanel=panelPrefs.getString("panelType","");
        try{android.widget.Spinner panel=(android.widget.Spinner)read(activity,"panelType");panelPrefs.edit().putString("panelType","A0").apply();((MainActivity)activity).syncPanelSelection();check(panel.getSelectedItemPosition()==1,"Reader A0 selection did not synchronize to device page");panelPrefs.edit().putString("panelType","A1").apply();((MainActivity)activity).syncPanelSelection();check(panel.getSelectedItemPosition()==2,"Reader A1 selection did not synchronize to device page");}finally{panelPrefs.edit().putString("panelType",oldPanel).apply();((MainActivity)activity).syncPanelSelection();}
        tabs[2].performClick();check(((Number)read(activity,"mainPage")).intValue()==2,"Device tab did not select device page");
        check(find(root,"设备与连接")!=null,"Device heading missing");
        View connect=find(root,"连接与授权");check(connect!=null,"Connection settings entry missing");connect.performClick();
        Dialog settings=(Dialog)read(activity,"settingsDialog");check(settings!=null&&settings.isShowing(),"Connection entry did not open preserved settings");
        View settingsRoot=settings.getWindow().getDecorView();
        check(find(settingsRoot,"设备与授权")!=null&&find(settingsRoot,"屏幕与传输")!=null,"Preserved settings sections missing");
        settings.dismiss();
        tabs[3].performClick();check(((Number)read(activity,"mainPage")).intValue()==3,"Logs tab did not select logs page");
        tabs[0].performClick();check(((Number)read(activity,"mainPage")).intValue()==0,"Returning to photo workspace failed");
    }
    private static Object read(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static View find(View view,String label){
        if(view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),label);if(result!=null)return result;}}
        return null;
    }
    private static View findDescription(View view,String label){if(view.getVisibility()!=View.VISIBLE)return null;if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=findDescription(group.getChildAt(i),label);if(result!=null)return result;}}return null;}
    private static boolean insideScroll(View view){for(android.view.ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView)return true;return false;}
    private static boolean descendant(View view,View ancestor){for(android.view.ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent==ancestor)return true;return false;}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}

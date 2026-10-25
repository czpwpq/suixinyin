package org.nfcresearch.qpolllab;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Local image/card editing with optional weather queries and explicit confirmed NFC sending. */
public final class EditorStudio {
    public static final int PHOTO=0,NOTE=1,CALENDAR=2,CLOCK=3;
    public interface TransferObserver {void status(String message);void finished(boolean success,String message);}
    public interface Host {
        void send(PanelImage.Prepared frame,String name,TransferObserver observer);
        void stopSending();void connect();String connectionState();String panelType();void selectPanel(String type);
        Bitmap currentPhoto();
        void selectPhoto();
        void apply(Bitmap bitmap,String name);
        void automate(int mode,long intervalMillis);
        void manageRotation();
    }
    private final Activity activity;private final Host host;private final SharedPreferences prefs;
    private final Handler handler=new Handler();private Dialog dialog;private final ArrayList<Dialog> children=new ArrayList<>();private Runnable update;
    private Bitmap photo;private int mode,year,month,day,margin=24;
    private Spinner calendarTemplate,panel,tone,diffusion;private SeekBar strength;private CheckBox dither;
    private EditText city,weather,temperature;private String weatherUpdated="",weatherSource="";
    private long generation,weatherRevision;private boolean picking,transferring,preparing,weatherLoading,applyingWeather;private PanelImage.Prepared finalFrame;
    private java.util.concurrent.Future<?> pendingImage;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private LinearLayout workspace,editorFields,statusDock,toolbar,stepper;private Button sendButton,clockAutoButton,backButton,stopButton,connectButton;private TextView connection,summary,toolsTitle;
    private FrameLayout canvasArea,toolsPanel;private LinearLayout geometryFields,frameFields,styleFields,advancedFields,cardFields;private View emptyPhoto;private ProgressBar transferProgress;
    private int step,lastResult;private boolean stopRequested;private String activeTool="";private TransferDisplay activeTransfer;private final ArrayList<Button> stepButtons=new ArrayList<>();
    private ImageView preview;private PhotoPlacementView placement;
    private EditText text;private Spinner foreground,background,style,outlineColor;
    private SeekBar fontSize,marginBar,angle;private CheckBox outlined,bold,analog,frame;
    private TextView error;private Bitmap rendered;
    private static final String[] COLOR_NAMES={"黑色","白色","黄色","红色"};
    private static final int[] COLORS={ImageRaster.color(0),ImageRaster.color(1),ImageRaster.color(2),ImageRaster.color(3)};
    public EditorStudio(Activity a,Host h){activity=a;host=h;prefs=a.getSharedPreferences("studio",Context.MODE_PRIVATE);}
    public void open(int requested){
        dismiss();clearFields();finalFrame=null;transferring=false;stopRequested=false;lastResult=0;weatherLoading=false;preparing=false;picking=false;mode=requested;
        Calendar now=Calendar.getInstance();year=now.get(Calendar.YEAR);month=now.get(Calendar.MONTH)+1;day=now.get(Calendar.DAY_OF_MONTH);
        photo=mode==PHOTO?host.currentPhoto():null;step=mode==PHOTO?(photo==null?0:1):2;
        dialog=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout page=column();page.setPadding(dp(14),dp(8),dp(14),dp(8));page.setBackgroundColor(HomeScreen.PAPER);page.setFitsSystemWindows(true);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=label(new String[]{"拍立得","便签","日历与天气","时钟"}[mode],22);heading.setTypeface(null,Typeface.BOLD);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button exit=button("关闭");exit.setTextSize(13);exit.setOnClickListener(v->{if(transferring){navigateBack();return;}save();dismiss();});header.addView(exit,new LinearLayout.LayoutParams(-2,dp(42)));page.addView(header);
        LinearLayout connectionRow=new LinearLayout(activity);connectionRow.setGravity(Gravity.CENTER_VERTICAL);connection=label("",11);connection.setMaxLines(1);connection.setEllipsize(TextUtils.TruncateAt.END);connection.setPadding(dp(9),dp(5),dp(9),dp(5));connection.setBackground(round(HomeScreen.SAND,10));connectionRow.addView(connection,new LinearLayout.LayoutParams(0,-2,1));
        connectButton=button("连接");connectButton.setTextSize(12);connectButton.setOnClickListener(v->host.connect());connectionRow.addView(connectButton,new LinearLayout.LayoutParams(-2,dp(36)));page.addView(connectionRow);
        stepper=new LinearLayout(activity);stepper.setBaselineAligned(false);stepButtons.clear();if(mode==PHOTO){String[] titles={"① 选图","② 构图","③ 四色与发送"};for(int i=0;i<3;i++){final int target=i;Button b=button(titles[i]);b.setTextSize(11);b.setMinHeight(dp(36));b.setMaxLines(2);b.setPadding(dp(3),0,dp(3),0);b.setOnClickListener(v->{if(!transferring)showStep(target);});stepButtons.add(b);stepper.addView(b,new LinearLayout.LayoutParams(0,-2,1));}page.addView(stepper);}
        // The tools get their own measured space. They must never cover the image.
        workspace=column();page.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        canvasArea=new FrameLayout(activity);canvasArea.setBackground(round(Color.WHITE,18));LinearLayout.LayoutParams canvasParams=new LinearLayout.LayoutParams(-1,0,1.2f);canvasParams.setMargins(0,dp(10),0,dp(6));workspace.addView(canvasArea,canvasParams);
        preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setPadding(dp(10),dp(10),dp(10),dp(10));preview.setContentDescription("最终四色预览，与发送像素一致");canvasArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        if(mode==PHOTO){
            placement=new PhotoPlacementView(activity);placement.setPhoto(photo);placement.setChanged(()->queue());canvasArea.addView(placement,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));TextView plus=label("＋",52);empty.addView(plus);empty.addView(label("放进一张喜欢的照片",19));TextView hint=label("选图 → 构图调整 → 四色预览与发送",12);hint.setGravity(Gravity.CENTER);empty.addView(hint);empty.setOnClickListener(v->pickPhoto());emptyPhoto=empty;canvasArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        }
        toolsPanel=new FrameLayout(activity);toolsPanel.setBackground(round(0xffeeefec,16));toolsPanel.setVisibility(View.GONE);
        LinearLayout sheet=column();sheet.setPadding(dp(12),dp(4),dp(12),dp(8));LinearLayout sheetHeader=new LinearLayout(activity);toolsTitle=label("调整工具",14);toolsTitle.setTypeface(null,Typeface.BOLD);sheetHeader.addView(toolsTitle,new LinearLayout.LayoutParams(0,-2,1));Button done=button("收起");done.setTextSize(12);done.setOnClickListener(v->showTool(""));sheetHeader.addView(done,new LinearLayout.LayoutParams(-2,dp(38)));sheet.addView(sheetHeader);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);editorFields=column();scroll.addView(editorFields);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));toolsPanel.addView(sheet,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams toolParams=new LinearLayout.LayoutParams(-1,0,1);toolParams.setMargins(0,0,0,dp(6));workspace.addView(toolsPanel,toolParams);
        geometryFields=group();frameFields=group();styleFields=group();advancedFields=group();cardFields=group();
        panel=select(advancedFields,"实物屏幕类型",new String[]{"请选择 A0 / A1","A0","A1"},host.panelType().equals("A0")?1:host.panelType().equals("A1")?2:0);
        panel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){private int previous=panel.getSelectedItemPosition();public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(n==previous)return;previous=n;host.selectPanel(n==1?"A0":n==2?"A1":"");queue();}public void onNothingSelected(AdapterView<?> p){}});
        if(mode==PHOTO)buildPhotoTools();else buildCardTools();
        toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);HorizontalScrollView toolScroll=new HorizontalScrollView(activity);toolScroll.setHorizontalScrollBarEnabled(false);toolScroll.addView(toolbar);page.addView(toolScroll,new LinearLayout.LayoutParams(-1,dp(48)));
        summary=label("",11);summary.setMaxLines(1);summary.setEllipsize(TextUtils.TruncateAt.END);page.addView(summary);
        statusDock=column();statusDock.setPadding(0,dp(4),0,0);transferProgress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);transferProgress.setMax(100);transferProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(HomeScreen.INK));statusDock.addView(transferProgress,new LinearLayout.LayoutParams(-1,dp(4)));
        error=label("",12);error.setMinLines(2);error.setMaxLines(2);error.setEllipsize(TextUtils.TruncateAt.END);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);statusDock.addView(error);
        LinearLayout footer=new LinearLayout(activity);footer.setBaselineAligned(false);footer.setGravity(Gravity.CENTER_VERTICAL);backButton=button("返回");backButton.setTextSize(13);backButton.setMinHeight(dp(54));backButton.setMaxLines(1);backButton.setOnClickListener(v->navigateBack());footer.addView(backButton,new LinearLayout.LayoutParams(dp(76),-2));
        stopButton=button("停止");stopButton.setTextSize(13);stopButton.setMinHeight(dp(54));stopButton.setMaxLines(1);stopButton.setOnClickListener(v->requestStop());footer.addView(stopButton,new LinearLayout.LayoutParams(dp(76),-2));
        sendButton=button("");sendButton.setTextSize(15);sendButton.setMinHeight(dp(54));sendButton.setMaxLines(2);sendButton.setEllipsize(TextUtils.TruncateAt.END);sendButton.setTextColor(Color.WHITE);sendButton.setOnClickListener(v->{if(mode==PHOTO&&step==0)pickPhoto();else if(mode==PHOTO&&step==1)nextStep();else confirmSend();});LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(0,-2,1);sendParams.setMargins(dp(8),0,0,0);footer.addView(sendButton,sendParams);statusDock.addView(footer);page.addView(statusDock);
        clockAutoButton=null;if(mode==CLOCK){clockAutoButton=button("定时刷写");clockAutoButton.setOnClickListener(v->{if(clockAutoReady()){save();chooseInterval();}});advancedFields.addView(clockAutoButton);}
        dialog.setContentView(page);dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){navigateBack();return true;}return key==KeyEvent.KEYCODE_BACK;});
        final Dialog opened=dialog;dialog.setOnDismissListener(d->{if(dialog==opened){handler.removeCallbacksAndMessages(null);dialog=null;}});dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        update=()->{if(dialog!=null)render();};refreshConnection();showStep(step);render();
    }
    private void buildPhotoTools(){
        geometryFields.addView(label("双指缩放 · 单指移动照片",13));
        toolRow(geometryFields,new String[]{"↶ 90°","↷ 90°"},new Runnable[]{()->rotate(-90),()->rotate(90)});
        toolRow(geometryFields,new String[]{"铺满","完整显示","居中"},new Runnable[]{()->placement.fill(),()->placement.fitInside(),()->placement.center()});
        angle=slider(geometryFields,"旋转角度 °",-180,180,0);angle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean u){((TextView)b.getTag()).setText("旋转角度 °："+(n-180));placement.setAngle(n-180);queue();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){queue();}});
        Button reset=button("重置构图");reset.setOnClickListener(v->{angle.setProgress(180);placement.reset();queue();});geometryFields.addView(reset);
        frame=check(frameFields,"拍立得留白相框",true);frame.setOnCheckedChangeListener((b,c)->queue());marginBar=slider(frameFields,"留白宽度",12,72,24);
        text=input(frameFields,"底部文字（可留空）",prefs.getString("caption",""),160);foreground=select(frameFields,"文字颜色",COLOR_NAMES,0);background=select(frameFields,"相框 / 留白底色",COLOR_NAMES,1);
        styleFields.addView(label("选择风格后，查看最终四色效果",13));
        toolRow(styleFields,new String[]{"自然暖调","全面暖色"},new Runnable[]{()->preset(0),()->preset(1)});toolRow(styleFields,new String[]{"纯灰度","原色直出"},new Runnable[]{()->preset(2),()->preset(3)});
        style=select(styleFields,"照片预处理",new String[]{"原图","黑白","暖色"},0);tone=select(styleFields,"四色色彩映射",new String[]{"原版 RGB","感知色彩","实验暖调"},1);
        dither=check(styleFields,"开启抖动",true);dither.setOnCheckedChangeListener((b,c)->queue());diffusion=select(styleFields,"抖动算法",FourColorDither.names(),1);strength=slider(styleFields,"抖动强度 %",0,100,75);
        advancedFields.addView(label("屏幕类型请按实物选择。照片复杂时可在风格中降低抖动强度。",12));Button rotation=button("我的轮播图库 · 多选 / 分次添加");rotation.setOnClickListener(v->{if(!transferring){save();dismiss();host.manageRotation();}});advancedFields.addView(rotation);
    }
    private void buildCardTools(){
        if(mode==NOTE){text=input(cardFields,"输入便签正文（支持换行）",prefs.getString("note","今天也要好好生活。"),1000);fontSize=slider(cardFields,"字体大小",22,88,prefs.getInt("fontSize",44));foreground=select(cardFields,"字体颜色",COLOR_NAMES,prefs.getInt("noteColor",0));background=select(cardFields,"底色",COLOR_NAMES,prefs.getInt("noteBackground",1));outlined=check(cardFields,"文字描边",prefs.getBoolean("outline",false));outlineColor=select(cardFields,"描边颜色",COLOR_NAMES,prefs.getInt("outlineColor",1));bold=check(cardFields,"粗体",prefs.getBoolean("bold",true));outlined.setOnCheckedChangeListener((b,c)->queue());bold.setOnCheckedChangeListener((b,c)->queue());return;}
        foreground=select(cardFields,"强调色",COLOR_NAMES,mode==CALENDAR?3:prefs.getInt("clockColor",3));background=select(cardFields,"卡片底色",COLOR_NAMES,mode==CALENDAR?1:prefs.getInt("clockBackground",1));
        if(mode==CALENDAR){calendarTemplate=select(cardFields,"日历版式（横版需横放手机壳）",CalendarCards.templateNames(),prefs.getInt("calendarTemplate",0));text=input(cardFields,"日历标题",prefs.getString("calendarTitle","把日子过成喜欢的样子"),60);Button date=button("选择月份 / 标记日期");date.setOnClickListener(v->track(new DatePickerDialog(activity,(d,y,m,selected)->{year=y;month=m+1;day=selected;date.setText(year+" 年 "+month+" 月 · 标记 "+day+" 日");queue();},year,month-1,day)).show());cardFields.addView(date);
            city=input(cardFields,"天气城市（支持搜索或手动填写）",prefs.getString("weatherCity",""),80);weather=input(cardFields,"天气文字（可手动填写）",prefs.getString("weatherText",""),80);temperature=input(cardFields,"温度（如 18°C 或 12–20°C）",prefs.getString("weatherTemperature",""),40);weatherUpdated=prefs.getString("weatherUpdated","");weatherSource=prefs.getString("weatherSource","");Button fetch=button("搜索城市并获取天气");fetch.setOnClickListener(v->searchWeather());cardFields.addView(fetch);cardFields.addView(label("仅点击获取时联网，无定位权限；获取失败可手动填写。",12));
        }else{analog=check(cardFields,"指针时钟（关闭为数字时钟）",prefs.getBoolean("analog",false));analog.setOnCheckedChangeListener((b,c)->queue());cardFields.addView(label("显示手机当前时区。静态画面；在更多中开启定时刷写才会继续更新。",12));}
    }
    private void clearFields(){calendarTemplate=null;panel=null;tone=null;diffusion=null;strength=null;dither=null;city=null;weather=null;temperature=null;text=null;foreground=null;background=null;style=null;outlineColor=null;fontSize=null;marginBar=null;angle=null;outlined=null;bold=null;analog=null;frame=null;placement=null;preview=null;workspace=null;editorFields=null;error=null;sendButton=null;clockAutoButton=null;connection=null;statusDock=null;toolbar=null;summary=null;toolsTitle=null;canvasArea=null;toolsPanel=null;emptyPhoto=null;activeTool="";}
    private void pickPhoto(){if(transferring)return;save();picking=true;dialog.hide();host.selectPhoto();}
    private void nextStep(){if(mode==PHOTO&&photo!=null)showStep(2);}
    private void showStep(int requested){
        if(transferring||dialog==null)return;step=mode==PHOTO?Math.max(0,Math.min(2,requested)):2;if(mode==PHOTO&&photo==null)step=0;showTool("");
        if(mode==PHOTO){emptyPhoto.setVisibility(step==0?View.VISIBLE:View.GONE);placement.setVisibility(step==1?View.VISIBLE:View.GONE);}preview.setVisibility(step==2?View.VISIBLE:View.GONE);
        toolbar.removeAllViews();if(mode==PHOTO&&step>0){if(step==1){toolbarButton("换图",()->pickPhoto());toolbarButton("构图",()->showTool("geometry"));}else toolbarButton("调整构图",()->showStep(1));toolbarButton("留白文字",()->showTool("frame"));toolbarButton("风格",()->showTool("style"));toolbarButton("更多",()->showTool("advanced"));}else if(mode!=PHOTO){toolbarButton(mode==CALENDAR?"版式与天气":mode==NOTE?"编辑文字":"时钟样式",()->showTool("edit"));toolbarButton("更多",()->showTool("advanced"));}
        for(int i=0;i<stepButtons.size();i++){Button b=stepButtons.get(i);boolean selected=i==step;b.setTextColor(selected?Color.WHITE:HomeScreen.INK);b.setBackground(round(selected?HomeScreen.INK:HomeScreen.SAND,10));}
        if(error!=null&&!preparing){error.setText(mode==PHOTO?(step==0?"选择一张照片，开始创作":step==1?"双指缩放、单指移动；调整后查看四色效果":"最终四色预览 · 可直接发送"):"编辑内容后，点击下方按钮发送");error.setTextColor(HomeScreen.INK);}setControls();
    }
    private void showTool(String key){
        if(toolsPanel==null||transferring)return;hideKeyboard();boolean hide=key.isEmpty()||key.equals(activeTool);activeTool=hide?"":key;toolsPanel.setVisibility(hide?View.GONE:View.VISIBLE);
        if(!hide)toolsTitle.setText(key.equals("geometry")?"调整构图":key.equals("frame")?"留白与文字":key.equals("style")?"风格与抖动":key.equals("advanced")?"更多设置":"编辑内容");
        // While adjusting, give the image and controls the space used by repeated hints.
        if(stepper!=null)stepper.setVisibility(mode==PHOTO&&hide?View.VISIBLE:View.GONE);
        if(summary!=null)summary.setVisibility(hide?View.VISIBLE:View.GONE);
        if(error!=null){error.setMinLines(hide?2:1);error.setMaxLines(hide?2:1);}
        if(geometryFields==null)return;geometryFields.setVisibility(!hide&&key.equals("geometry")?View.VISIBLE:View.GONE);frameFields.setVisibility(!hide&&key.equals("frame")?View.VISIBLE:View.GONE);styleFields.setVisibility(!hide&&key.equals("style")?View.VISIBLE:View.GONE);advancedFields.setVisibility(!hide&&key.equals("advanced")?View.VISIBLE:View.GONE);cardFields.setVisibility(!hide&&key.equals("edit")?View.VISIBLE:View.GONE);
    }
    private void navigateBack(){if(transferring){error.setText("刷屏中，请保持贴合；需要中止时点击停止。");return;}if(toolsPanel!=null&&toolsPanel.getVisibility()==View.VISIBLE){showTool("");return;}if(mode==PHOTO&&step==2){showStep(1);return;}save();dismiss();}
    private void rotate(int degrees){int value=angle.getProgress()-180+degrees;while(value>180)value-=360;while(value<-180)value+=360;angle.setProgress(value+180);}
    private void preset(int which){style.setSelection(which==1?2:which==2?1:0);tone.setSelection(which==0?2:which==3?0:1);dither.setChecked(which!=3);diffusion.setSelection(which==3?0:1);strength.setProgress(which==0?60:75);queue();}
    private void requestStop(){if(!transferring||stopRequested)return;stopRequested=true;error.setText("已请求停止，等待当前通信结束…");sendButton.setText("正在停止 · 请勿移动");stopButton.setEnabled(false);host.stopSending();}
    private void toolbarButton(String title,Runnable action){Button b=button(title);b.setTextSize(12);b.setMinHeight(0);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(0,0,dp(6),0);toolbar.addView(b,p);}
    private void toolRow(LinearLayout parent,String[] titles,Runnable[] actions){LinearLayout row=new LinearLayout(activity);row.setBaselineAligned(false);for(int i=0;i<titles.length;i++){final Runnable action=actions[i];Button b=button(titles[i]);b.setTextSize(13);b.setMinHeight(dp(46));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,dp(4),dp(4));row.addView(b,p);}parent.addView(row);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout group(){LinearLayout l=column();l.setVisibility(View.GONE);editorFields.addView(l);return l;}
    private android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void hideKeyboard(){View focus=dialog==null?null:dialog.getCurrentFocus();if(focus!=null){((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
    private boolean clockAutoReady(){return mode==CLOCK&&dialog!=null&&!transferring&&!preparing&&!weatherLoading&&finalFrame!=null;}
    private void chooseInterval(){if(!clockAutoReady())return;final Dialog owner=dialog;track(new AlertDialog.Builder(activity).setTitle("时钟刷新间隔")
        .setItems(new String[]{"1 分钟（整次刷屏结束后再计时）","5 分钟","10 分钟","30 分钟","60 分钟"},(d,n)->{if(dialog!=owner||!clockAutoReady())return;long[] values={1,5,10,30,60};final long interval=values[n]*60000;final boolean clockAnalog=analog.isChecked();final int fg=foreground.getSelectedItemPosition(),bg=background.getSelectedItemPosition();save();dismiss();prefs.edit().putBoolean("analog",clockAnalog).putInt("clockColor",fg).putInt("clockBackground",bg).apply();host.automate(CLOCK,interval);}).create()).show();}
    public Bitmap currentClock(){return CardRenderer.clock(System.currentTimeMillis(),TimeZone.getDefault().getID(),prefs.getBoolean("analog",false),COLORS[prefs.getInt("clockColor",3)],COLORS[prefs.getInt("clockBackground",1)]);}
    private void queue(){if(update!=null&&dialog!=null&&!picking&&!transferring){generation++;lastResult=0;if(pendingImage!=null)pendingImage.cancel(false);finalFrame=null;preparing=true;setControls();handler.removeCallbacks(update);handler.postDelayed(update,160);}}
    private void render(){
        try{
            if(mode==PHOTO){
                if(photo==null){error.setText("请先选一张照片");rendered=null;preparing=false;setControls();return;}
                int bg=COLORS[background.getSelectedItemPosition()],fg=COLORS[foreground.getSelectedItemPosition()];margin=marginBar.getProgress()+12;
                Rect box=frame.isChecked()?CardRenderer.polaroidPhotoBounds(margin):new Rect(0,0,552,768);placement.setTarget(box.width(),box.height(),bg);
                Bitmap crop=placement.export();
                if(frame.isChecked())rendered=CardRenderer.polaroid(crop,text.getText().toString(),margin,bg,fg,style.getSelectedItemPosition());
                else{
                    rendered=CardRenderer.styledPhoto(crop,style.getSelectedItemPosition());Canvas c=new Canvas(rendered);
                    String caption=text.getText().toString().replace('\n',' ');if(!caption.isEmpty()){android.text.TextPaint p=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(bg);c.drawRect(0,688,552,768,p);p.setColor(fg);p.setTextSize(26);p.setTextAlign(Paint.Align.CENTER);while(p.measureText(caption)>504&&p.getTextSize()>14)p.setTextSize(p.getTextSize()-1);caption=TextUtils.ellipsize(caption,p,504,TextUtils.TruncateAt.END).toString();c.drawText(caption,276,735,p);}
                }
                crop.recycle();
            }else if(mode==NOTE)rendered=CardRenderer.note(text.getText().toString(),fontSize.getProgress()+22,COLORS[foreground.getSelectedItemPosition()],COLORS[background.getSelectedItemPosition()],outlined.isChecked(),COLORS[outlineColor.getSelectedItemPosition()],bold.isChecked());
            else if(mode==CALENDAR)rendered=CalendarCards.render(year,month,day,text.getText().toString(),COLORS[foreground.getSelectedItemPosition()],COLORS[background.getSelectedItemPosition()],calendarTemplate.getSelectedItemPosition(),city.getText().toString(),weather.getText().toString(),temperature.getText().toString(),weatherUpdated.isEmpty()?"手动天气":weatherUpdated+" · "+weatherSource);
            else rendered=CardRenderer.clock(System.currentTimeMillis(),TimeZone.getDefault().getID(),analog.isChecked(),COLORS[foreground.getSelectedItemPosition()],COLORS[background.getSelectedItemPosition()]);
            prepareFinalFrame();
        }catch(Exception e){rendered=null;finalFrame=null;preparing=false;setControls();error.setText("无法生成："+e.getMessage());}
    }
    private void save(){SharedPreferences.Editor e=prefs.edit();if(mode==PHOTO)e.putString("caption",text.getText().toString());if(mode==NOTE)e.putString("note",text.getText().toString()).putInt("fontSize",fontSize.getProgress()+22).putInt("noteColor",foreground.getSelectedItemPosition()).putInt("noteBackground",background.getSelectedItemPosition()).putBoolean("outline",outlined.isChecked()).putInt("outlineColor",outlineColor.getSelectedItemPosition()).putBoolean("bold",bold.isChecked());if(mode==CALENDAR)e.putString("calendarTitle",text.getText().toString()).putInt("calendarTemplate",calendarTemplate.getSelectedItemPosition()).putString("weatherCity",city.getText().toString()).putString("weatherText",weather.getText().toString()).putString("weatherTemperature",temperature.getText().toString()).putString("weatherUpdated",weatherUpdated).putString("weatherSource",weatherSource);if(mode==CLOCK)e.putBoolean("analog",analog.isChecked()).putInt("clockColor",foreground.getSelectedItemPosition()).putInt("clockBackground",background.getSelectedItemPosition());e.apply();}
    public void deliverPhoto(Bitmap bitmap){if(dialog==null)open(PHOTO);if(mode!=PHOTO)return;photo=bitmap;picking=false;angle.setProgress(180);placement.setPhoto(bitmap);dialog.show();showStep(1);queue();}
    public void photoCancelled(){if(dialog!=null&&picking){picking=false;dialog.show();queue();}}
    public void onHostStop(){if(transferring)host.stopSending();}
    public void onHostResume(){if(dialog!=null&&!picking&&!transferring){dialog.show();queue();}}
    public void destroy(){dismiss();worker.shutdownNow();}
    public boolean isOpen(){return dialog!=null;}
    public void refreshConnection(){if(dialog!=null&&connection!=null){String state=host.connectionState();boolean ready=state.contains("身份已验证")||state.contains("可以刷屏")||state.contains("可刷屏");connection.setText(ready?"● 已连接 · "+(state.contains("Root")?"Root":state.contains("内置")?"内置激活":"Shizuku"):state);connection.setTextColor(ready?HomeScreen.INK:HomeScreen.COPPER);connectButton.setVisibility(ready?View.GONE:View.VISIBLE);connectButton.setEnabled(!transferring);}}
    private void setControls(){
        if(editorFields!=null)enableTree(editorFields,!transferring);if(toolbar!=null)enableTree(toolbar,!transferring);if(placement!=null)placement.setEnabled(!transferring);if(stepper!=null)enableTree(stepper,!transferring);
        if(sendButton==null)return;
        if(dialog!=null){dialog.setCancelable(!transferring);dialog.setCanceledOnTouchOutside(false);}backButton.setVisibility(transferring?View.GONE:View.VISIBLE);stopButton.setVisibility(transferring?View.VISIBLE:View.GONE);stopButton.setEnabled(transferring&&!stopRequested);
        boolean ready=!transferring&&!preparing&&!weatherLoading&&finalFrame!=null;sendButton.setEnabled(mode==PHOTO&&step<2?!transferring&&(step==0||photo!=null):ready);
        String caption;if(transferring)caption=stopRequested?"正在停止 · 请勿移动":activeTransfer==null?"刷屏中 · 请勿移动":activeTransfer.buttonText;else if(mode==PHOTO&&step==0)caption="选择照片";else if(mode==PHOTO&&step==1)caption="查看四色预览";else caption=preparing?"正在生成四色预览…":weatherLoading?"正在获取天气…":lastResult==1?"再次发送":lastResult==2?"重试发送":"发送到屏幕";
        sendButton.setText(caption);sendButton.setAlpha(1f);sendButton.setBackground(round(transferring||sendButton.isEnabled()?HomeScreen.INK:0xffa5a6a3,14));sendButton.setTextColor(Color.WHITE);
        if(!transferring){transferProgress.setIndeterminate(preparing||weatherLoading);transferProgress.setVisibility(preparing||weatherLoading?View.VISIBLE:View.INVISIBLE);}
        if(clockAutoButton!=null)clockAutoButton.setEnabled(clockAutoReady());if(connectButton!=null)connectButton.setEnabled(!transferring);
        if(summary!=null){String screen=panel!=null&&panel.getSelectedItemPosition()>0?panel.getSelectedItemPosition()==1?"A0":"A1":"屏幕未设置";summary.setText(mode==PHOTO&&step==0?"照片仅在本机处理":screen+" · 黑 / 白 / 黄 / 红 · "+(mode==PHOTO&&step==1?"构图中，下一步查看四色":"552 × 768"));}
    }
    private static void enableTree(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)enableTree(g.getChildAt(i),enabled);}}
    private void prepareFinalFrame(){
        final long token=++generation;final Bitmap input=rendered;final String chosen=panel.getSelectedItemPosition()==1?"A0":panel.getSelectedItemPosition()==2?"A1":"";
        final int mapping=mode==PHOTO?tone.getSelectedItemPosition():1,alg=mode==PHOTO&&dither.isChecked()?diffusion.getSelectedItemPosition():0,amount=mode==PHOTO?strength.getProgress():0;
        finalFrame=null;preparing=true;setControls();error.setText("正在生成最终四色预览…");
        if(pendingImage!=null)pendingImage.cancel(false);pendingImage=worker.submit(()->{try{PanelImage.Prepared result=PanelImage.prepare(input,0,false,alg>0,1,false,128,false,chosen,PhotoAdjust.ORIGINAL,alg,amount,mapping);handler.post(()->{if(dialog==null||token!=generation)return;finalFrame=result;preparing=false;if(mode==CALENDAR&&CalendarCards.isLandscape(calendarTemplate.getSelectedItemPosition())){Matrix orientation=new Matrix();orientation.setRotate(-90);preview.setImageBitmap(Bitmap.createBitmap(result.preview,0,0,552,768,orientation,false));}else preview.setImageBitmap(result.preview);error.setText(mode==PHOTO&&step==1?"双指缩放、单指移动；调整后查看四色效果":"最终四色预览 · 可直接发送"+(mode==CALENDAR&&CalendarCards.isLandscape(calendarTemplate.getSelectedItemPosition())?" · 横版请横放观看":""));error.setTextColor(HomeScreen.INK);refreshConnection();setControls();});}catch(Exception e){handler.post(()->{if(dialog!=null&&token==generation){preparing=false;finalFrame=null;error.setText("四色预览失败："+e.getMessage());error.setTextColor(0xffa02a20);setControls();}});}});
    }
    private void confirmSend(){
        if(preparing||weatherLoading||finalFrame==null||transferring||(mode==PHOTO&&step!=2))return;if(finalFrame.panelType.isEmpty()){showTool("advanced");error.setText("请在更多中选择实物 A0 或 A1 屏幕类型");error.setTextColor(0xffa02a20);return;}
        final Dialog sendingPage=dialog;final PanelImage.Prepared frame=finalFrame;final String name=new String[]{"拍立得","便签","日历","时钟"}[mode];
        save();showTool("");hideKeyboard();transferring=true;stopRequested=false;lastResult=0;activeTransfer=TransferDisplay.from("");error.setText("刷屏中，请勿移动手机和墨水屏");error.setTextColor(HomeScreen.INK);transferProgress.setVisibility(View.VISIBLE);transferProgress.setIndeterminate(true);setControls();
        try{host.send(frame,name,new TransferObserver(){public void status(String value){if(dialog==sendingPage&&transferring){activeTransfer=TransferDisplay.from(value);if(!stopRequested){error.setText(activeTransfer.phase);transferProgress.setIndeterminate(activeTransfer.percent<0||activeTransfer.uploadComplete);if(activeTransfer.percent>=0)transferProgress.setProgress(activeTransfer.percent);}refreshConnection();setControls();}}public void finished(boolean success,String value){if(dialog==sendingPage&&transferring){transferring=false;stopRequested=false;lastResult=success?1:2;error.setText(value);error.setTextColor(success?HomeScreen.INK:0xffa02a20);refreshConnection();setControls();}}});}
        catch(Exception e){transferring=false;lastResult=2;error.setText("发送未完成："+e.getMessage());error.setTextColor(0xffa02a20);setControls();}
    }
    private void searchWeather(){
        String query=city.getText().toString().trim();if(query.length()<2){error.setText("请输入至少两个字的城市名，或城市拼音。");return;}if(weatherLoading)return;
        final Dialog owner=dialog;final long request=beginWeatherRequest();error.setText("正在搜索城市…");
        new Thread(()->{try{java.util.List<WeatherClient.Place> places=WeatherClient.search(query);handler.post(()->{if(!isWeatherCurrent(owner,request))return;weatherLoading=false;setControls();if(places.isEmpty()){error.setText("没有找到城市，可尝试拼音或手动填写天气。");return;}String[] labels=new String[places.size()];for(int i=0;i<labels.length;i++)labels[i]=places.get(i).label();track(new AlertDialog.Builder(activity).setTitle("选择天气城市").setItems(labels,(d,n)->{if(isWeatherCurrent(owner,request))fetchWeather(places.get(n));}).setNegativeButton("取消",null).create()).show();});}catch(Exception e){handler.post(()->{if(isWeatherCurrent(owner,request)){weatherLoading=false;setControls();error.setText("城市查询失败，可手动填写："+e.getMessage());}});}},"weather-search").start();
    }
    private void fetchWeather(WeatherClient.Place place){
        final Dialog owner=dialog;final long request=beginWeatherRequest();error.setText("正在获取天气…");new Thread(()->{try{WeatherClient.Snapshot data=WeatherClient.fetch(place);handler.post(()->applyWeatherSnapshot(owner,request,data));}catch(Exception e){handler.post(()->{if(isWeatherCurrent(owner,request)){weatherLoading=false;setControls();error.setText("获取失败，保留原内容："+e.getMessage());}});}},"weather-current").start();
    }
    private long beginWeatherRequest(){weatherLoading=true;setControls();return ++weatherRevision;}
    private boolean isWeatherCurrent(Dialog owner,long request){return dialog!=null&&dialog==owner&&request==weatherRevision;}
    private void invalidateWeather(){weatherRevision++;weatherLoading=false;weatherUpdated="";weatherSource="";setControls();}
    private void applyWeatherSnapshot(Dialog owner,long request,WeatherClient.Snapshot data){
        if(!isWeatherCurrent(owner,request))return;
        weatherLoading=false;applyingWeather=true;
        try{city.setText(data.city);weather.setText(data.condition);temperature.setText(data.temperature);weatherUpdated=data.updated;weatherSource=data.source;}
        finally{applyingWeather=false;}
        save();queue();setControls();
    }

    private <T extends Dialog> T track(T child){children.add(child);return child;}
    public void dismiss(){generation++;weatherRevision++;weatherLoading=false;if(pendingImage!=null)pendingImage.cancel(false);for(Dialog child:new ArrayList<>(children))if(child.isShowing())child.dismiss();children.clear();handler.removeCallbacksAndMessages(null);if(dialog!=null){Dialog old=dialog;dialog=null;old.dismiss();}update=null;}
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(0xff213b33);t.setPadding(0,dp(6),0,dp(4));return t;}
    private Button button(String label){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(42));b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(3),dp(12),dp(3));b.setTextColor(HomeScreen.INK);b.setBackgroundTintList(null);b.setBackground(round(HomeScreen.SAND,12));b.setStateListAnimator(null);return b;}
    private EditText input(LinearLayout parent,String title,String value,int limit){parent.addView(label(title,14));EditText e=new EditText(activity);e.setText(value);e.setTextSize(17);e.setMinLines(2);e.setMaxLines(5);e.setGravity(Gravity.TOP);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);parent.addView(e);e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if((e==city||e==weather||e==temperature)&&!applyingWeather)invalidateWeather();queue();}public void afterTextChanged(Editable s){}});return e;}
    private Spinner select(LinearLayout parent,String title,String[] choices,int selected){parent.addView(label(title,14));Spinner s=new Spinner(activity);s.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,choices));final int initial=Math.max(0,Math.min(choices.length-1,selected));s.setSelection(initial);parent.addView(s);s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){private int previous=initial;public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(n==previous)return;previous=n;queue();}public void onNothingSelected(AdapterView<?> p){}});return s;}
    private CheckBox check(LinearLayout p,String title,boolean value){CheckBox b=new CheckBox(activity);b.setText(title);b.setChecked(value);p.addView(b);return b;}
    private SeekBar slider(LinearLayout p,String title,int min,int max,int value){TextView label=label(title+"："+value,14);p.addView(label);SeekBar s=new SeekBar(activity);s.setMax(max-min);s.setProgress(Math.max(0,Math.min(max-min,value-min)));p.addView(s);s.setTag(label);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean u){label.setText(title+"："+(min+n));queue();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){queue();}});return s;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}

package org.nfcresearch.qpolllab;

import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Transactional local card editor. Done returns a fixed frame; this class never sends NFC. */
final class CardEditor {
    /** Result bitmap ownership transfers to the callback when Done is invoked. */
    interface Done { void apply(CardScene scene,StudioImageCodec.Result frame); }
    private static final int[] COLORS={0xff000000,0xffffffff,0xfff5cd00,0xffc80000};
    private static final String[] COLOR_NAMES={"黑色","白色","黄色","红色"};
    private CardEditor() { }

    static Dialog open(Activity activity,CardScene initial,String panelType,Done done) {
        return new Session(activity,initial,panelType,done).open();
    }
    static final class WindowDialog extends Dialog {
        Session session;
        WindowDialog(Activity activity){super(activity);}
        @Override public void dismiss(){if(session!=null)session.close();super.dismiss();}
    }
    static final class Session {
        final Activity activity; final WindowDialog dialog; final Done callback; final String panelType;
        final CardScene scene; final ImageView preview; final TextView hint; final LinearLayout tools,navigation;
        final ScrollView toolScroll; final Button doneButton;
        final Handler main=new Handler(Looper.getMainLooper());
        final ExecutorService renderer=Executors.newSingleThreadExecutor(),weatherIo=Executors.newSingleThreadExecutor();
        final ArrayList<Dialog> children=new ArrayList<>();
        EditText textInput,cityInput,weatherInput,tempInput;
        Button dateButton,fetchButton; TextView weatherInfo;
        volatile boolean closed; boolean preparing,weatherLoading,updatingFields;
        long generation,weatherRevision,preparedGeneration=-1;
        int mode; String renderError="",weatherMessage="";
        Future<?> renderTask,weatherTask; Runnable renderPending;
        StudioImageCodec.Result prepared; Bitmap displayBitmap;

        Session(Activity activity,CardScene initial,String panelType,Done done) {
            if(activity==null||initial==null||done==null)throw new IllegalArgumentException("编辑器参数缺失");
            this.activity=activity;scene=initial.copy();scene.normalize();this.panelType=panelType==null?"":panelType;callback=done;
            dialog=new WindowDialog(activity);dialog.session=this;
            LinearLayout page=AppUi.column(activity);page.setBackgroundColor(AppUi.DARK);
            LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(10),dp(6),dp(16),0);
            header.addView(UiIcon.iconButton(activity,"back","取消卡片编辑",true,dialog::dismiss),new LinearLayout.LayoutParams(dp(44),dp(46)));
            TextView title=AppUi.text(activity,scene.kind==CardScene.CALENDAR?"日历与天气":"编辑便签",16,AppUi.WHITE,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            doneButton=AppUi.darkButton(activity,"完成",true,this::apply);header.addView(doneButton,new LinearLayout.LayoutParams(dp(62),dp(34)));page.addView(header);
            hint=AppUi.text(activity,"正在生成卡片预览…",11,AppUi.DIM,false);hint.setGravity(Gravity.CENTER);hint.setMaxLines(2);hint.setEllipsize(TextUtils.TruncateAt.END);hint.setPadding(dp(16),dp(4),dp(16),dp(4));page.addView(hint);
            FrameLayout canvasArea=new FrameLayout(activity);canvasArea.setPadding(dp(24),dp(10),dp(24),dp(10));preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("最终四色卡片预览，完成后返回首页");canvasArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));page.addView(canvasArea,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout bottom=AppUi.column(activity);android.graphics.drawable.GradientDrawable panel=AppUi.shape(AppUi.PANEL,18,activity);panel.setCornerRadii(new float[]{dp(18),dp(18),dp(18),dp(18),0,0,0,0});bottom.setBackground(panel);bottom.setPadding(dp(18),dp(16),dp(18),dp(6));
            toolScroll=new ScrollView(activity);toolScroll.setVerticalScrollBarEnabled(false);tools=AppUi.column(activity);toolScroll.addView(tools);bottom.addView(toolScroll,new LinearLayout.LayoutParams(-1,dp(156)));AppUi.gap(bottom,12);
            View separator=new View(activity);separator.setBackgroundColor(0xff303135);bottom.addView(separator,new LinearLayout.LayoutParams(-1,Math.max(1,dp(.5f))));navigation=new LinearLayout(activity);bottom.addView(navigation);page.addView(bottom);
            page.setOnApplyWindowInsetsListener((view,insets)->{page.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
            dialog.setContentView(page);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.setStatusBarColor(AppUi.DARK);window.setNavigationBarColor(AppUi.DARK);}
            showTools();queueRender();
        }
        int dp(float value){return AppUi.dp(activity,value);}
        Dialog open(){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.getDecorView().setSystemUiVisibility(0);window.setStatusBarColor(AppUi.DARK);window.setNavigationBarColor(AppUi.DARK);}return dialog;}
        void showTools(){
            tools.removeAllViews();navigation.removeAllViews();textInput=cityInput=weatherInput=tempInput=null;dateButton=fetchButton=null;weatherInfo=null;
            boolean calendar=scene.kind==CardScene.CALENDAR;
            String[] names=calendar?new String[]{"版式","内容","天气"}:new String[]{"文字","样式"};
            String[] icons=calendar?new String[]{"frame","text","nfc"}:new String[]{"text","frame"};
            for(int i=0;i<names.length;i++){final int tab=i;navigation.addView(UiIcon.tool(activity,icons[i],names[i],mode==i,true,()->{mode=tab;showTools();}),new LinearLayout.LayoutParams(0,dp(61),1));}
            updatingFields=true;
            if(calendar){if(mode==0)calendarStyle();else if(mode==1)calendarContent();else weatherTools();}
            else if(mode==0)noteContent();else noteStyle();
            updatingFields=false;updateStatus();
        }
        void calendarStyle(){
            HorizontalScrollView strip=new HorizontalScrollView(activity);strip.setHorizontalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(activity);
            String[] names=CalendarCards.templateNames();
            for(int i=0;i<names.length;i++){final int template=i;LinearLayout tile=AppUi.column(activity);tile.setGravity(Gravity.CENTER);tile.addView(new TemplateThumb(activity,i,scene.template==i),new LinearLayout.LayoutParams(dp(72),dp(67)));AppUi.gap(tile,5);
                String label=names[i].replace("竖屏 · ","").replace("横屏 · ","");tile.addView(AppUi.text(activity,label,10,scene.template==i?AppUi.LIME:AppUi.DIM,false));tile.setContentDescription(names[i]);tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(v->{scene.template=template;queueRender();showTools();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(86),dp(88));lp.rightMargin=dp(4);choices.addView(tile,lp);}
            strip.addView(choices);tools.addView(strip);AppUi.gap(tools,9);LinearLayout row=new LinearLayout(activity);colorField(row,"强调色",scene.foreground,false,index->{scene.foreground=index;queueRender();showTools();});colorField(row,"底色",scene.background,false,index->{scene.background=index;queueRender();showTools();});tools.addView(row);
        }
        void calendarContent(){
            LinearLayout dateRow=new LinearLayout(activity);dateButton=control(dateText(),false,this::chooseDate);dateRow.addView(dateButton,new LinearLayout.LayoutParams(0,dp(37),1));Button clear=control("不标记",scene.day==0,()->{scene.day=0;queueRender();showTools();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(70),dp(37));lp.leftMargin=dp(7);dateRow.addView(clear,lp);tools.addView(dateRow);AppUi.gap(tools,11);
            tools.addView(AppUi.text(activity,"日历标题",11,AppUi.DIM,false));AppUi.gap(tools,5);textInput=input("把日子过成喜欢的样子",scene.title,100,false);textInput.setContentDescription("日历标题");tools.addView(textInput,new LinearLayout.LayoutParams(-1,dp(47)));watch(textInput,value->{scene.title=value;queueRender();});
        }
        String dateText(){return scene.year+" 年 "+scene.month+" 月"+(scene.day>0?" · "+scene.day+" 日":" · 整月");}
        void chooseDate(){
            if(closed)return;DatePickerDialog picker=new DatePickerDialog(activity,(view,year,month,day)->{if(closed)return;scene.year=year;scene.month=month+1;scene.day=day;queueRender();showTools();},scene.year,scene.month-1,Math.max(1,scene.day));children.add(picker);picker.show();
        }
        void noteContent(){
            textInput=input("写下这一刻…",scene.text,4000,true);textInput.setContentDescription("便签正文");textInput.setMinLines(3);textInput.setMaxLines(5);tools.addView(textInput,new LinearLayout.LayoutParams(-1,dp(112)));watch(textInput,value->{scene.text=value;queueRender();});AppUi.gap(tools,8);tools.addView(AppUi.text(activity,"支持换行 · 长文字自动缩小或省略",10,AppUi.DIM,false));
        }
        void noteStyle(){
            LinearLayout colors=new LinearLayout(activity);colorField(colors,"字色",scene.foreground,false,index->{scene.foreground=index;queueRender();showTools();});colorField(colors,"底色",scene.background,false,index->{scene.background=index;queueRender();showTools();});tools.addView(colors);AppUi.gap(tools,9);
            LinearLayout switches=new LinearLayout(activity);addControl(switches,control("B  粗体",scene.bold,()->{scene.bold=!scene.bold;queueRender();showTools();}));addControl(switches,control("描边",scene.outline,()->{scene.outline=!scene.outline;queueRender();showTools();}));tools.addView(switches);AppUi.gap(tools,10);
            TextView label=AppUi.text(activity,"字号  "+scene.fontSize+" px",11,AppUi.DIM,false);tools.addView(label);SeekBar size=new SeekBar(activity);size.setContentDescription("便签字号");size.setMax(104);size.setProgress(scene.fontSize-16);size.setProgressTintList(ColorStateList.valueOf(AppUi.LIME));size.setThumbTintList(ColorStateList.valueOf(AppUi.LIME));tools.addView(size,new LinearLayout.LayoutParams(-1,dp(32)));size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){scene.fontSize=value+16;label.setText("字号  "+scene.fontSize+" px");queueRender();}}});
            if(scene.outline){AppUi.gap(tools,5);LinearLayout outline=new LinearLayout(activity);colorField(outline,"描边颜色",scene.outlineColor,false,index->{scene.outlineColor=index;queueRender();showTools();});tools.addView(outline);}
        }
        void weatherTools(){
            LinearLayout cityRow=new LinearLayout(activity);cityInput=input("城市名或拼音",scene.city,80,false);cityInput.setContentDescription("天气城市");cityRow.addView(cityInput,new LinearLayout.LayoutParams(0,dp(42),1));fetchButton=control(weatherLoading?"取消获取":"查天气",false,()->{if(weatherLoading)cancelWeather();else searchWeather();});LinearLayout.LayoutParams buttonLayout=new LinearLayout.LayoutParams(dp(78),dp(42));buttonLayout.leftMargin=dp(7);cityRow.addView(fetchButton,buttonLayout);tools.addView(cityRow);AppUi.gap(tools,8);
            LinearLayout values=new LinearLayout(activity);weatherInput=input("天气，如晴",scene.weather,80,false);weatherInput.setContentDescription("手动天气文字");tempInput=input("温度，如18°C",scene.temperature,40,false);tempInput.setContentDescription("手动天气温度");LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(42),1);left.rightMargin=dp(7);values.addView(weatherInput,left);values.addView(tempInput,new LinearLayout.LayoutParams(0,dp(42),1));tools.addView(values);AppUi.gap(tools,8);
            weatherInfo=AppUi.text(activity,weatherLabel(),10,AppUi.DIM,false);weatherInfo.setMaxLines(3);tools.addView(weatherInfo);AppUi.gap(tools,5);tools.addView(AppUi.text(activity,"仅点击获取时联网 · 无定位权限 · 可手动填写",10,AppUi.DIM,false));
            watch(cityInput,value->{scene.city=value;manualWeather();});watch(weatherInput,value->{scene.weather=value;manualWeather();});watch(tempInput,value->{scene.temperature=value;manualWeather();});
        }
        String weatherLabel(){return scene.weatherSource.isEmpty()?"手动天气 · 未提供联网时间":scene.weatherSource+" · "+scene.weatherUpdated;}
        void manualWeather(){
            weatherRevision++;weatherLoading=false;if(weatherTask!=null)weatherTask.cancel(true);scene.weatherSource="";scene.weatherUpdated="";weatherMessage="";updateWeatherFields(false);queueRender();
        }
        void updateWeatherFields(boolean values){
            updatingFields=true;if(values){if(cityInput!=null)cityInput.setText(scene.city);if(weatherInput!=null)weatherInput.setText(scene.weather);if(tempInput!=null)tempInput.setText(scene.temperature);}if(weatherInfo!=null)weatherInfo.setText(weatherLabel());if(fetchButton!=null)fetchButton.setText(weatherLoading?"取消获取":"查天气");updatingFields=false;updateStatus();
        }
        void cancelWeather(){weatherRevision++;weatherLoading=false;if(weatherTask!=null)weatherTask.cancel(true);weatherMessage="已取消获取，保留当前天气。";updateWeatherFields(false);}
        void searchWeather(){
            if(closed||weatherLoading)return;final String query=scene.city.trim();if(query.length()<2){weatherMessage="请输入至少两个字的城市名或拼音。";updateStatus();return;}
            final long revision=++weatherRevision;weatherLoading=true;weatherMessage="正在搜索城市…";updateWeatherFields(false);
            weatherTask=weatherIo.submit(()->{try{final List<WeatherClient.Place> places=WeatherClient.search(query);main.post(()->{
                if(closed||weatherRevision!=revision)return;weatherLoading=false;updateWeatherFields(false);if(places.isEmpty()){weatherMessage="没有找到城市，可换拼音或手动填写。";updateStatus();return;}
                weatherMessage="请选择天气城市。";updateStatus();String[] names=new String[places.size()];for(int i=0;i<names.length;i++)names[i]=places.get(i).label();
                AlertDialog choices=new AlertDialog.Builder(activity).setTitle("选择天气城市").setItems(names,(dialog,index)->{if(!closed&&weatherRevision==revision)fetchWeather(places.get(index),revision);}).setNegativeButton("取消",null).create();choices.setOnDismissListener(dialog->{if(!closed&&weatherRevision==revision&&!weatherLoading){weatherMessage="";updateStatus();}});children.add(choices);choices.show();
            });}catch(Exception error){main.post(()->weatherError(revision,error));}});
        }
        void fetchWeather(WeatherClient.Place place,long revision){
            if(closed||weatherRevision!=revision)return;weatherLoading=true;weatherMessage="正在获取天气…";updateWeatherFields(false);
            weatherTask=weatherIo.submit(()->{try{final WeatherClient.Snapshot snapshot=WeatherClient.fetch(place);main.post(()->applyWeatherSnapshot(revision,snapshot));}catch(Exception error){main.post(()->weatherError(revision,error));}});
        }
        void applyWeatherSnapshot(long revision,WeatherClient.Snapshot snapshot){
            if(closed||weatherRevision!=revision)return;weatherLoading=false;scene.city=snapshot.city;scene.weather=snapshot.condition;scene.temperature=snapshot.temperature;scene.weatherUpdated=snapshot.updated;scene.weatherSource=snapshot.source;weatherMessage="";scene.normalize();updateWeatherFields(true);queueRender();
        }
        void weatherError(long revision,Exception error){if(closed||weatherRevision!=revision)return;weatherLoading=false;weatherMessage="获取失败，保留原内容；可手动填写。";updateWeatherFields(false);}
        void queueRender(){
            if(closed)return;generation++;preparing=true;renderError="";doneButton.setEnabled(false);doneButton.setAlpha(.4f);if(renderPending!=null)main.removeCallbacks(renderPending);if(renderTask!=null)renderTask.cancel(false);updateStatus();final long token=generation;renderPending=()->render(token);main.postDelayed(renderPending,140);
        }
        void render(long token){
            if(closed||token!=generation)return;final CardScene snapshot=scene.copy();snapshot.normalize();
            renderTask=renderer.submit(()->{try{final StudioImageCodec.Result result=CardFrames.prepare(snapshot,panelType);main.post(()->{
                if(closed||token!=generation){release(result);return;}StudioImageCodec.Result previous=prepared;Bitmap previousDisplay=displayBitmap;prepared=result;preparedGeneration=token;preparing=false;
                if(snapshot.isLandscape()){Matrix rotation=new Matrix();rotation.setRotate(-90);displayBitmap=Bitmap.createBitmap(result.preview,0,0,552,768,rotation,false);preview.setImageBitmap(displayBitmap);}else{displayBitmap=null;preview.setImageBitmap(result.preview);}
                if(previousDisplay!=null&&!previousDisplay.isRecycled())previousDisplay.recycle();if(previous!=null)release(previous);updateStatus();
            });}catch(Throwable error){main.post(()->{if(closed||token!=generation)return;preparing=false;renderError="预览生成失败，请修改内容后重试。";updateStatus();});}});
        }
        void updateStatus(){
            if(closed)return;boolean ready=!preparing&&!weatherLoading&&prepared!=null&&preparedGeneration==generation;doneButton.setEnabled(ready);doneButton.setAlpha(ready?1:.4f);
            hint.setText(!renderError.isEmpty()?renderError:weatherLoading?weatherMessage:!weatherMessage.isEmpty()?weatherMessage:preparing?"正在生成四色卡片…":scene.isLandscape()?"横版四色预览 · 完成后返回首页":"最终四色预览 · 完成后返回首页");
        }
        void apply(){
            if(closed||preparing||weatherLoading||prepared==null||preparedGeneration!=generation)return;
            CardScene accepted=scene.copy();accepted.normalize();StudioImageCodec.Result result=prepared;prepared=null;dialog.dismiss();callback.apply(accepted,result);
        }
        void close(){
            if(closed)return;closed=true;generation++;weatherRevision++;if(renderPending!=null)main.removeCallbacks(renderPending);if(renderTask!=null)renderTask.cancel(false);if(weatherTask!=null)weatherTask.cancel(true);
            for(Dialog child:new ArrayList<>(children))if(child.isShowing())child.dismiss();children.clear();preview.setImageDrawable(null);if(displayBitmap!=null){displayBitmap.recycle();displayBitmap=null;}if(prepared!=null){release(prepared);prepared=null;}renderer.shutdown();weatherIo.shutdownNow();
        }
        Button control(String text,boolean selected,Runnable action){return AppUi.darkButton(activity,text,selected,action);}
        void addControl(LinearLayout row,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(35),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(child,lp);}
        EditText input(String placeholder,String value,int limit,boolean multiline){EditText input=new EditText(activity);input.setTextColor(AppUi.WHITE);input.setHintTextColor(AppUi.DIM);input.setTextSize(multiline?17:13);input.setGravity(Gravity.TOP);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(AppUi.shape(AppUi.CONTROL,9,activity));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(multiline?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));if(!multiline)input.setSingleLine(true);input.setHint(placeholder);input.setText(value);return input;}
        interface Changed {void change(String value);}
        void watch(EditText input,Changed callback){input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){if(!updatingFields&&!closed)callback.change(text.toString());}public void afterTextChanged(Editable text){}});}
        interface ColorChoice {void choose(int index);}
        void colorField(LinearLayout row,String title,int chosen,boolean unused,ColorChoice callback){LinearLayout column=AppUi.column(activity);column.addView(AppUi.text(activity,title,11,AppUi.DIM,false));LinearLayout swatches=new LinearLayout(activity);swatches.setGravity(Gravity.CENTER_VERTICAL);
            for(int index=0;index<COLORS.length;index++){final int value=index;FrameLayout target=new FrameLayout(activity);View dot=new View(activity);android.graphics.drawable.GradientDrawable bg=AppUi.shape(COLORS[index],20,activity);bg.setStroke(dp(chosen==index?2:1),chosen==index?AppUi.LIME:0xff626366);dot.setBackground(bg);target.addView(dot,new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));target.setContentDescription(title+" "+COLOR_NAMES[index]);target.setClickable(true);target.setFocusable(true);target.setOnClickListener(v->callback.choose(value));swatches.addView(target,new LinearLayout.LayoutParams(0,dp(34),1));}column.addView(swatches);row.addView(column,new LinearLayout.LayoutParams(0,-2,1));}
    }
    private static final class TemplateThumb extends View {
        final int template;final boolean selected;final Paint paint=new Paint();
        TemplateThumb(Context context,int template,boolean selected){super(context);this.template=template;this.selected=selected;}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight();paint.setColor(selected?AppUi.LIME:AppUi.CONTROL);canvas.drawRoundRect(0,0,w,h,8,8,paint);boolean landscape=template>=2;float cw=landscape?w-10:(h-10)*552/768f,ch=landscape?(w-10)*552/768f:h-10;canvas.save();canvas.translate((w-cw)/2,(h-ch)/2);paint.setColor(Color.WHITE);canvas.drawRect(0,0,cw,ch,paint);
            paint.setColor(0xfff5cd00);if(template==0)canvas.drawRect(4,4,6,ch*.22f,paint);else if(template==1)canvas.drawRect(0,0,cw,ch*.27f,paint);else if(template==2)canvas.drawRect(0,0,cw,ch*.22f,paint);else canvas.drawRect(3,3,cw*.34f,ch-3,paint);
            float left=template==3?cw*.42f:4,right=template==2?cw*.65f:cw-4,top=template==1?ch*.36f:ch*.31f,bottom=ch*.78f;paint.setColor(0xff3f4540);paint.setStrokeWidth(1);for(int row=0;row<5;row++)for(int column=0;column<7;column++){float x=left+(right-left)*(column+.5f)/7,y=top+(bottom-top)*(row+.5f)/5;canvas.drawCircle(x,y,.7f,paint);}paint.setColor(0xffc80000);canvas.drawCircle(left+(right-left)*.64f,top+(bottom-top)*.31f,1.5f,paint);paint.setColor(0xfff5cd00);if(template==2)canvas.drawRect(cw*.73f,ch*.31f,cw-4,ch-4,paint);else if(template<2)canvas.drawRect(4,ch*.85f,cw-4,ch-4,paint);canvas.restore();
        }
    }
    private static void release(StudioImageCodec.Result result){if(result==null)return;if(result.preview!=null&&!result.preview.isRecycled())result.preview.recycle();if(result.originalPreview!=null&&result.originalPreview!=result.preview&&!result.originalPreview.isRecycled())result.originalPreview.recycle();}
}

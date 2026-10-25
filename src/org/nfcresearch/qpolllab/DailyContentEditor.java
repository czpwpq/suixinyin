package org.nfcresearch.qpolllab;

import android.app.*;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.*;

/** A transactional editor: fetching/choosing renders a draft; Done returns a snapshot and never sends NFC. */
final class DailyContentEditor {
    interface Done {void apply(DailyContent.Scene scene,StudioImageCodec.Result frame);}
    static Dialog open(Activity activity,DailyContent.Scene scene,String panelType,Done done){return new Session(activity,scene,panelType,done).open();}
    static final class WindowDialog extends Dialog {
        Session session;WindowDialog(Activity activity){super(activity);}
        @Override public void dismiss(){if(session!=null)session.close();super.dismiss();}
    }
    static final class Session {
        final Activity activity;final WindowDialog dialog;final Done callback;final String panelType;
        final ImageView preview;final TextView hint;final LinearLayout tools,navigation;final Button done;
        final Handler main=new Handler(Looper.getMainLooper());final ExecutorService renders=Executors.newSingleThreadExecutor(),network=Executors.newSingleThreadExecutor();
        final ArrayList<Dialog> children=new ArrayList<>();
        DailyContent.Scene scene;StudioImageCodec.Result prepared;long generation,preparedGeneration=-1,requestRevision;int tab;
        volatile boolean closed;boolean preparing,loading,updating;String message="",renderError="";Future<?> renderTask,requestTask;Runnable renderPending;
        Session(Activity activity,DailyContent.Scene initial,String panelType,Done callback){
            if(activity==null||initial==null||callback==null)throw new IllegalArgumentException("内容编辑参数缺失");
            this.activity=activity;scene=initial.copy();scene.normalize();scene.count=Math.min(3,scene.count);this.panelType=panelType==null?"":panelType;this.callback=callback;
            dialog=new WindowDialog(activity);dialog.session=this;
            LinearLayout page=AppUi.column(activity);page.setBackgroundColor(AppUi.DARK);
            LinearLayout header=new LinearLayout(activity);header.setPadding(dp(10),dp(6),dp(16),0);header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(UiIcon.iconButton(activity,"back","取消内容编辑",true,dialog::dismiss),new LinearLayout.LayoutParams(dp(44),dp(46)));
            TextView title=AppUi.text(activity,kindName(scene.kind),16,AppUi.WHITE,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            done=AppUi.darkButton(activity,"完成",true,this::apply);header.addView(done,new LinearLayout.LayoutParams(dp(62),dp(34)));page.addView(header);
            hint=AppUi.text(activity,"点击获取内容，或在文字页手动填写",11,AppUi.DIM,false);hint.setMaxLines(2);hint.setEllipsize(TextUtils.TruncateAt.END);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(16),dp(5),dp(16),dp(5));page.addView(hint);
            FrameLayout area=new FrameLayout(activity);area.setPadding(dp(24),dp(10),dp(24),dp(10));preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("内容卡片的最终四色预览，完成后回到首页");area.addView(preview,new FrameLayout.LayoutParams(-1,-1));page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout bottom=AppUi.column(activity);android.graphics.drawable.GradientDrawable shape=AppUi.shape(AppUi.PANEL,18,activity);shape.setCornerRadii(new float[]{dp(18),dp(18),dp(18),dp(18),0,0,0,0});bottom.setBackground(shape);bottom.setPadding(dp(18),dp(14),dp(18),dp(6));
            ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);tools=AppUi.column(activity);scroll.addView(tools);bottom.addView(scroll,new LinearLayout.LayoutParams(-1,dp(166)));AppUi.gap(bottom,10);
            View separator=new View(activity);separator.setBackgroundColor(0xff303135);bottom.addView(separator,new LinearLayout.LayoutParams(-1,1));navigation=new LinearLayout(activity);bottom.addView(navigation);page.addView(bottom);
            page.setOnApplyWindowInsetsListener((v,insets)->{page.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
            dialog.setContentView(page);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.setStatusBarColor(AppUi.DARK);window.setNavigationBarColor(AppUi.DARK);}
            showTools();queueRender();
        }
        Dialog open(){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.getDecorView().setSystemUiVisibility(0);window.setStatusBarColor(AppUi.DARK);window.setNavigationBarColor(AppUi.DARK);}return dialog;}
        int dp(float value){return AppUi.dp(activity,value);}
        void showTools(){
            tools.removeAllViews();navigation.removeAllViews();String[] names={"内容","文字"},icons={"image","text"};
            for(int i=0;i<2;i++){final int target=i;navigation.addView(UiIcon.tool(activity,icons[i],names[i],tab==i,true,()->{tab=target;showTools();}),new LinearLayout.LayoutParams(0,dp(61),1));}
            updating=true;if(tab==0)contentTools();else textTools();updating=false;updateStatus();
        }
        void contentTools(){
            if(scene.kind==DailyContent.BRIEF||scene.kind==DailyContent.DAILY){
                String[] modes=scene.kind==DailyContent.BRIEF?new String[]{"科技热榜","AI 简报","合并"}:new String[]{"语录","书籍"};
                LinearLayout row=new LinearLayout(activity);for(int i=0;i<modes.length;i++){final int mode=i;addControl(row,AppUi.darkButton(activity,modes[i],scene.mode==i,()->changeMode(mode)));}tools.addView(row);AppUi.gap(tools,8);
            }
            LinearLayout fetch=new LinearLayout(activity);Button date=AppUi.darkButton(activity,scene.date,false,this::chooseDate);fetch.addView(date,new LinearLayout.LayoutParams(0,dp(38),1));
            Button request=AppUi.darkButton(activity,loading?"取消获取":"获取内容",false,()->{if(loading)cancelRequest();else fetch();});LinearLayout.LayoutParams requestLp=new LinearLayout.LayoutParams(dp(112),dp(38));requestLp.leftMargin=dp(8);fetch.addView(request,requestLp);tools.addView(fetch);AppUi.gap(tools,8);
            DailyContent.Entry selected=DailyContentRenderer.selected(scene);String choice=selected==null?"尚未获取，可在文字页填写":(scene.selected+1)+" / "+scene.entries.size()+"  "+selected.title;
            Button entries=AppUi.darkButton(activity,choice,false,this::chooseEntry);entries.setSingleLine(true);entries.setEllipsize(TextUtils.TruncateAt.END);entries.setEnabled(selected!=null);tools.addView(entries,new LinearLayout.LayoutParams(-1,dp(38)));
            AppUi.gap(tools,8);LinearLayout extra=new LinearLayout(activity);
            if(scene.kind==DailyContent.HISTORY||scene.kind==DailyContent.BRIEF)addControl(extra,AppUi.darkButton(activity,"展示 "+scene.count+" 条",false,this::chooseCount));
            if(scene.kind==DailyContent.BIRTHDAY){Button portrait=AppUi.darkButton(activity,"获取所选人物画像",false,this::fetchPortrait);portrait.setEnabled(selected!=null&&!clean(selected.imageUrl).isEmpty()&&!loading);addControl(extra,portrait);}
            if(selected!=null&&!clean(selected.url).isEmpty())addControl(extra,AppUi.darkButton(activity,"查看原文",false,()->openSource(selected.url)));
            addControl(extra,AppUi.darkButton(activity,"内容源设置",false,this::sourceSettings));tools.addView(extra);AppUi.gap(tools,8);
            tools.addView(AppUi.text(activity,"仅主动获取时联网 · 可编辑内容 · 完成后回首页预览",10,AppUi.DIM,false));
        }
        void textTools(){
            DailyContent.Entry e=ensureEntry();
            addInput("标题 / 人物姓名",e.title,160,false,value->{e.title=value;manual();});
            addInput("正文 / 语录 / 书籍介绍",e.body,4000,true,value->{e.body=value;manual();});
            addInput("署名 / 年份 / 补充说明",e.byline,160,false,value->{e.byline=value;manual();});
            if(scene.kind==DailyContent.BIRTHDAY)addInput("动漫或作品名称",e.work,200,false,value->{e.work=value;manual();});
        }
        DailyContent.Entry ensureEntry(){if(scene.entries.isEmpty()){DailyContent.Entry e=new DailyContent.Entry();e.id="manual-"+scene.kind;scene.entries.add(e);scene.selected=0;}return DailyContentRenderer.selected(scene);}
        void addInput(String label,String text,int limit,boolean multiline,Changed changed){tools.addView(AppUi.text(activity,label,11,AppUi.DIM,false));AppUi.gap(tools,4);EditText input=input(text,limit,multiline);input.setContentDescription(label);tools.addView(input,new LinearLayout.LayoutParams(-1,dp(multiline?90:42)));input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!updating&&!closed)changed.change(s.toString());}public void afterTextChanged(Editable e){}});AppUi.gap(tools,9);}
        void manual(){invalidateRequest();if(!scene.source.startsWith("手动编辑"))scene.source="手动编辑"+(clean(scene.source).isEmpty()?"":" · "+scene.source);scene.warning="手动编辑内容，请核对事实与署名";message="";queueRender();}
        void changeMode(int mode){if(scene.mode==mode)return;invalidateRequest();scene.mode=mode;scene.entries.clear();scene.selected=0;scene.portrait=null;scene.portraitId="";scene.fetchedAt=scene.source=scene.warning="";message="";queueRender();showTools();}
        void chooseDate(){LocalDate date;try{date=LocalDate.parse(scene.date);}catch(Exception invalid){date=LocalDate.now();}DatePickerDialog picker=new DatePickerDialog(activity,(v,year,month,day)->{if(closed)return;String value=LocalDate.of(year,month+1,day).toString();if(value.equals(scene.date))return;invalidateRequest();scene.date=value;scene.entries.clear();scene.selected=0;scene.portrait=null;scene.portraitId="";scene.fetchedAt=scene.source=scene.warning="";message="";queueRender();showTools();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());children.add(picker);picker.show();}
        void chooseEntry(){if(scene.entries.isEmpty())return;String[] items=new String[scene.entries.size()];for(int i=0;i<items.length;i++)items[i]=(i+1)+". "+scene.entries.get(i).title;AlertDialog choices=new AlertDialog.Builder(activity).setTitle(scene.kind==DailyContent.BIRTHDAY?"选择人物":"选择起始条目").setSingleChoiceItems(items,scene.selected,(d,index)->{if(closed)return;invalidateRequest();scene.selected=index;DailyContent.Entry e=DailyContentRenderer.selected(scene);if(!clean(scene.portraitId).equals(clean(e.id))){scene.portrait=null;scene.portraitId="";}message="";queueRender();showTools();d.dismiss();loadSelectedPortrait();}).setNegativeButton("取消",null).create();children.add(choices);choices.show();}
        void chooseCount(){AlertDialog choices=new AlertDialog.Builder(activity).setTitle("卡片展示条数").setItems(new String[]{"1 条 · 大字详细","2 条 · 简洁排版","3 条 · 标题速览"},(d,index)->{if(!closed){invalidateRequest();scene.count=index+1;message="";queueRender();showTools();}}).create();children.add(choices);choices.show();}
        void sourceSettings(){
            SharedPreferences prefs=activity.getSharedPreferences("daily_content",0);LinearLayout form=AppUi.column(activity);form.setPadding(dp(20),dp(10),dp(20),0);TextView caption=AppUi.text(activity,"历史和简报使用的 HTTPS 内容服务根地址。公共源适合个人测试，正式发布建议自部署。生日和语录仍使用各自服务。",12,AppUi.MUTED,false);form.addView(caption);EditText input=new EditText(activity);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setText(prefs.getString("api_base","https://60s.viki.moe"));form.addView(input);
            AlertDialog settings=new AlertDialog.Builder(activity).setTitle("内容源设置").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();children.add(settings);settings.show();settings.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String base=DailyContentClient.normalizeApiBase(input.getText().toString());prefs.edit().putString("api_base",base).apply();invalidateRequest();message="内容源已保存，点击获取刷新内容";settings.dismiss();showTools();}catch(Exception error){input.setError("请输入不含账号、查询参数的 HTTPS 根地址");}});
        }
        void openSource(String url){try{java.net.URI uri=new java.net.URI(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception error){message="无法打开来源链接，请检查浏览器";updateStatus();}}
        void fetch(){
            if(closed||loading)return;final long revision=++requestRevision;final int kind=scene.kind,mode=scene.mode,count=scene.count;final String date=scene.date,base=activity.getSharedPreferences("daily_content",0).getString("api_base","https://60s.viki.moe");loading=true;message="正在获取内容…";showTools();
            requestTask=network.submit(()->{try{final DailyContent.Scene result=DailyContentClient.fetch(kind,mode,date,base);if(result.kind!=kind||result.mode!=mode||!date.equals(result.date))throw new IllegalArgumentException("内容日期或类型与请求不一致");main.post(()->{if(closed||requestRevision!=revision)return;loading=false;scene=result.copy();scene.count=count;scene.normalize();message=!DailyContentRenderer.visibleWarning(scene).isEmpty()?DailyContentRenderer.visibleWarning(scene):scene.entries.isEmpty()?"没有可用内容，可换来源或手动填写":"已获取 "+scene.entries.size()+" 条，选择后完成";queueRender();showTools();loadSelectedPortrait();});}catch(Exception error){main.post(()->requestError(revision,error));}});
        }
        void loadSelectedPortrait(){DailyContent.Entry selected=DailyContentRenderer.selected(scene);if(scene.kind==DailyContent.BIRTHDAY&&selected!=null&&!clean(selected.imageUrl).isEmpty()&&(scene.portrait==null||!clean(scene.portraitId).equals(clean(selected.id))))fetchPortrait();}
        void fetchPortrait(){
            DailyContent.Entry current=DailyContentRenderer.selected(scene);if(closed||loading||current==null||clean(current.imageUrl).isEmpty())return;final DailyContent.Entry entry=current.copy();final String id=entry.id;final long revision=++requestRevision;loading=true;message="正在获取所选人物画像…";showTools();
            requestTask=network.submit(()->{try{final byte[] portrait=DailyContentClient.portrait(entry);android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(portrait,0,portrait.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>32000000L)throw new IllegalArgumentException("画像格式无效，请重新获取");main.post(()->{if(closed||requestRevision!=revision)return;DailyContent.Entry selected=DailyContentRenderer.selected(scene);if(selected==null||!clean(selected.id).equals(id))return;loading=false;scene.portrait=portrait;scene.portraitId=id;message="画像已载入，请核对人物与作品";queueRender();showTools();});}catch(Exception error){main.post(()->requestError(revision,error));}});
        }
        void requestError(long revision,Exception error){if(closed||requestRevision!=revision)return;loading=false;message="获取失败，保留当前草稿："+(error.getMessage()==null?"请稍后重试":error.getMessage());showTools();}
        void invalidateRequest(){requestRevision++;loading=false;if(requestTask!=null)requestTask.cancel(true);}
        void cancelRequest(){invalidateRequest();message="已取消获取，保留当前草稿";showTools();}
        void queueRender(){if(closed)return;generation++;preparing=true;renderError="";if(renderPending!=null)main.removeCallbacks(renderPending);if(renderTask!=null)renderTask.cancel(false);final long token=generation;renderPending=()->render(token);main.postDelayed(renderPending,140);updateStatus();}
        void render(long token){if(closed||token!=generation)return;final DailyContent.Scene snapshot=scene.copy();snapshot.normalize();renderTask=renders.submit(()->{try{final StudioImageCodec.Result result=DailyContentRenderer.prepare(snapshot,panelType);main.post(()->{if(closed||token!=generation){release(result);return;}StudioImageCodec.Result previous=prepared;prepared=result;preparedGeneration=token;preparing=false;preview.setImageBitmap(result.preview);release(previous);updateStatus();});}catch(Throwable error){main.post(()->{if(closed||token!=generation)return;preparing=false;renderError="预览生成失败，请修改内容后重试";updateStatus();});}});}
        void updateStatus(){if(closed)return;boolean ready=!loading&&!preparing&&prepared!=null&&preparedGeneration==generation&&DailyContentRenderer.hasContent(scene);done.setEnabled(ready);done.setAlpha(ready?1:.4f);hint.setText(!renderError.isEmpty()?renderError:loading?message:preparing?"正在生成四色预览…":!message.isEmpty()?message:!DailyContentRenderer.visibleWarning(scene).isEmpty()?DailyContentRenderer.visibleWarning(scene):ready?"核对内容后点完成，返回首页预览":"点击获取内容，或在文字页手动填写");}
        void apply(){if(closed||loading||preparing||prepared==null||preparedGeneration!=generation||!DailyContentRenderer.hasContent(scene))return;DailyContent.Scene accepted=scene.copy();accepted.normalize();StudioImageCodec.Result result=prepared;prepared=null;dialog.dismiss();callback.apply(accepted,result);}
        void close(){if(closed)return;closed=true;generation++;invalidateRequest();if(renderPending!=null)main.removeCallbacks(renderPending);if(renderTask!=null)renderTask.cancel(false);for(Dialog child:new ArrayList<>(children))if(child.isShowing())child.dismiss();children.clear();preview.setImageDrawable(null);release(prepared);prepared=null;renders.shutdown();network.shutdownNow();}
        EditText input(String text,int limit,boolean multiline){EditText input=new EditText(activity);input.setTextColor(AppUi.WHITE);input.setHintTextColor(AppUi.DIM);input.setTextSize(multiline?16:13);input.setPadding(dp(10),dp(9),dp(10),dp(9));input.setGravity(Gravity.TOP);input.setBackground(AppUi.shape(AppUi.CONTROL,8,activity));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(multiline?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));if(!multiline)input.setSingleLine(true);input.setText(text);return input;}
        void addControl(LinearLayout row,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(child,lp);}
    }
    private interface Changed{void change(String value);}
    private static String kindName(int kind){return kind==DailyContent.HISTORY?"今日历史":kind==DailyContent.BRIEF?"简报":kind==DailyContent.DAILY?"每日":"动漫人物生日";}
    private static String clean(String value){return value==null?"":value.trim();}
    private static void release(StudioImageCodec.Result result){if(result==null)return;if(result.preview!=null&&!result.preview.isRecycled())result.preview.recycle();if(result.originalPreview!=null&&result.originalPreview!=result.preview&&!result.originalPreview.isRecycled())result.originalPreview.recycle();}
    private DailyContentEditor(){}
}

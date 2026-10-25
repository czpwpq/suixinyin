package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Original native layout inspired by the user's warm paper reference. */
public final class HomeScreen {
    public static final int PAPER=0xfff7f7f5, INK=0xff202124, GREEN=0xff202124, SAND=0xffeeefec, COPPER=0xff85868a;
    private HomeScreen(){}
    public static void header(Activity a,LinearLayout root,Runnable settings){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(a,14),0,dp(a,12));
        Icon mark=new Icon(a,0);row.addView(mark,new LinearLayout.LayoutParams(dp(a,42),dp(a,48)));
        LinearLayout brand=new LinearLayout(a);brand.setOrientation(1);TextView name=label(a,"随心印",25,INK);name.setTypeface(null,Typeface.BOLD);brand.addView(name);brand.addView(label(a,"把喜欢的瞬间，留在身边",12,0xff8b877b));row.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button gear=new Button(a);gear.setText("⚙");gear.setTextSize(24);gear.setContentDescription("设置与设备");gear.setAllCaps(false);gear.setBackground(shape(a,SAND,16));gear.setOnClickListener(v->settings.run());row.addView(gear,new LinearLayout.LayoutParams(dp(a,52),dp(a,52)));root.addView(row);
    }
    public static void cards(Activity a,LinearLayout root,Runnable[] actions){
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setPadding(0,dp(a,16),0,dp(a,24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView heading=label(a,"今天，想留下什么？",22,INK);heading.setTypeface(null,Typeface.BOLD);body.addView(heading);TextView sub=label(a,"一张照片，一段心情，或值得期待的一天。",13,0xff8b877b);sub.setPadding(0,dp(a,6),0,dp(a,18));body.addView(sub);
        String[] titles={"拍立得","便签","日历与天气","热门图库"};String[] captions={"选图 · 构图 · 四色预览","写下此刻，印在身边","横竖日历 · 城市天气","发现好图 · 分享收藏"};
        for(int r=0;r<2;r++){LinearLayout row=new LinearLayout(a);for(int c=0;c<2;c++){final int n=r*2+c;LinearLayout tile=new LinearLayout(a);tile.setOrientation(1);tile.setGravity(Gravity.TOP);tile.setPadding(dp(a,16),dp(a,20),dp(a,12),dp(a,18));tile.setBackground(shape(a,Color.WHITE,22));tile.setElevation(dp(a,1));tile.setContentDescription(titles[n]);tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(v->actions[n].run());
            Icon icon=new Icon(a,n);tile.addView(icon,new LinearLayout.LayoutParams(dp(a,36),dp(a,36)));TextView title=label(a,titles[n],19,INK);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(a,12),0,dp(a,5));tile.addView(title);tile.addView(label(a,captions[n],12,0xff8b877b));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,174),1);p.setMargins(c==0?0:dp(a,6),0,c==0?dp(a,6):0,dp(a,12));row.addView(tile,p);}body.addView(row);}
        TextView footer=label(a,"黑 · 白 · 黄 · 红\n让四种颜色，装下更多日常。",13,0xff8b877b);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(a,18),0,0);body.addView(footer);
    }
    private static TextView label(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    public static GradientDrawable shape(android.content.Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    private static int dp(android.content.Context a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static final class Icon extends View {
        private final int mode;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Activity a,int mode){super(a);this.mode=mode;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas original){super.onDraw(original);Canvas c=original;int save=c.save();c.scale(getWidth()/40f,getHeight()/40f);p.setColor(mode==3?GREEN:COPPER);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(mode==0){c.save();c.rotate(-7,20,20);c.drawRoundRect(6,4,34,36,3,3,p);c.drawRect(10,8,30,27,p);Path heart=new Path();heart.moveTo(20,22);heart.cubicTo(5,13,16,7,20,14);heart.cubicTo(24,7,35,13,20,22);c.drawPath(heart,p);c.drawLine(15,31,25,31,p);c.restore();}
            else if(mode==1){c.drawRoundRect(7,4,33,36,3,3,p);for(int y=13;y<=29;y+=8)c.drawLine(13,y,27,y,p);}
            else if(mode==2){c.drawRoundRect(4,8,36,35,4,4,p);c.drawLine(4,17,36,17,p);c.drawLine(12,4,12,12,p);c.drawLine(28,4,28,12,p);p.setStyle(Paint.Style.FILL);c.drawCircle(13,25,2,p);c.drawCircle(22,25,2,p);c.drawCircle(30,25,2,p);}
            else {for(int x=5;x<34;x+=17)for(int y=5;y<34;y+=17)c.drawRoundRect(x,y,x+13,y+13,3,3,p);}c.restoreToCount(save);
        }
    }
}

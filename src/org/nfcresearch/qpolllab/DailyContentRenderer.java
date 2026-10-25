package org.nfcresearch.qpolllab;

import android.graphics.*;
import android.text.*;

/** Local card composition. Only a birthday portrait enters the photo quantizer. */
final class DailyContentRenderer {
    private static final int K=ImageRaster.color(0),W=ImageRaster.color(1),Y=ImageRaster.color(2),R=ImageRaster.color(3);
    static StudioImageCodec.Result prepare(DailyContent.Scene requested,String panelType){
        if(requested==null)throw new IllegalArgumentException("缺少内容草稿");
        DailyContent.Scene scene=requested.copy();scene.normalize();
        Bitmap artwork=Bitmap.createBitmap(552,768,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(artwork);canvas.drawColor(W);
            if(scene.kind==DailyContent.BIRTHDAY){
                box(canvas,28,24,34,64,R);line(canvas,label(scene),48,54,300,30,K,Paint.Align.LEFT);
                line(canvas,clean(scene.date),524,52,170,22,K,Paint.Align.RIGHT);box(canvas,28,77,524,81,Y);
            }else{
                box(canvas,28,28,36,102,R);line(canvas,label(scene),52,61,472,36,K,Paint.Align.LEFT);
                line(canvas,clean(scene.date),52,100,472,24,K,Paint.Align.LEFT);
                if(scene.kind!=DailyContent.DAILY||scene.mode!=1)box(canvas,28,119,524,126,Y);
            }
            DailyContent.Entry entry=selected(scene);
            if(entry==null){paragraph(canvas,"点击获取内容\n也可以在文字页手动填写",52,234,448,240,30,K,Layout.Alignment.ALIGN_CENTER);}
            else if(scene.kind==DailyContent.BIRTHDAY)birthday(canvas,scene,entry);
            else if(scene.kind==DailyContent.DAILY)daily(canvas,scene,entry);
            else bulletin(canvas,scene);
            if(scene.kind!=DailyContent.BIRTHDAY){
                if(!visibleWarning(scene).isEmpty())line(canvas,visibleWarning(scene),28,662,496,22,K,Paint.Align.LEFT);
                box(canvas,28,677,524,680,Y);
                String source=clean(scene.source);if(source.isEmpty()&&entry!=null)source=clean(entry.source);
                line(canvas,"来源："+(source.isEmpty()?"手动内容":source),28,709,496,22,K,Paint.Align.LEFT);
                if(!clean(scene.fetchedAt).isEmpty())line(canvas,"获取："+scene.fetchedAt,28,743,496,22,K,Paint.Align.LEFT);
                else line(canvas,"核对内容后，从首页推送",28,743,496,22,K,Paint.Align.LEFT);
            }
            int[] pixels=new int[552*768];artwork.getPixels(pixels,0,552,0,0,552,768);byte[] codes=new byte[pixels.length];
            for(int i=0;i<pixels.length;i++){if((i&8191)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();codes[i]=(byte)CardFrames.paletteIndex(pixels[i]);}
            PanelImage.Prepared prepared=PanelImage.fromIndexed(codes,panelType,InkSightQuantizer.COLORS,label(scene)+" · 四色内容卡片");
            try{return StudioImageCodec.fromPrepared(prepared);}catch(RuntimeException|Error error){prepared.preview.recycle();throw error;}
        }finally{artwork.recycle();}
    }
    static String label(DailyContent.Scene scene){
        if(scene.kind==DailyContent.HISTORY)return "历史上的今天";
        if(scene.kind==DailyContent.BRIEF)return scene.mode==0?"科技热榜":scene.mode==1?"AI 简报":"科技与 AI 简报";
        if(scene.kind==DailyContent.DAILY)return scene.mode==0?"每日语录":"每日书籍";
        return "动漫人物生日";
    }
    static DailyContent.Entry selected(DailyContent.Scene scene){return scene.entries.isEmpty()?null:scene.entries.get(Math.max(0,Math.min(scene.entries.size()-1,scene.selected)));}
    static boolean hasContent(DailyContent.Scene scene){DailyContent.Entry e=selected(scene);return e!=null&&(!clean(e.title).isEmpty()||!clean(e.body).isEmpty());}
    static String visibleWarning(DailyContent.Scene scene){return scene.kind==DailyContent.BIRTHDAY&&hasContent(scene)?"":clean(scene.warning);}
    private static void bulletin(Canvas canvas,DailyContent.Scene scene){
        int count=Math.min(Math.max(1,Math.min(3,scene.count)),scene.entries.size());float slot=480f/count;
        for(int i=0;i<count;i++){
            DailyContent.Entry e=scene.entries.get((scene.selected+i)%scene.entries.size());float top=145+i*slot;
            box(canvas,28,top,34,top+34,R);
            String kicker=clean(e.byline);if(kicker.isEmpty())kicker=clean(e.published);if(kicker.isEmpty())kicker=String.valueOf(i+1);
            line(canvas,kicker,50,top+23,474,22,R,Paint.Align.LEFT);
            int titleHeight=count==1?96:count==2?76:66;
            int titleSize=count==1?34:30;paragraph(canvas,e.title,50,top+38,474,titleHeight,titleSize,K,Layout.Alignment.ALIGN_NORMAL);
            float bodyTop=top+40+titleHeight;int bodyHeight=Math.max(28,(int)(slot-titleHeight-62));
            if(!clean(e.body).isEmpty())paragraph(canvas,e.body,50,bodyTop,474,bodyHeight,count==1?28:24,K,Layout.Alignment.ALIGN_NORMAL);
            if(i<count-1)box(canvas,50,top+slot-12,524,top+slot-10,Y);
        }
    }
    private static void daily(Canvas canvas,DailyContent.Scene scene,DailyContent.Entry e){
        if(scene.mode==0){
            String quote=clean(e.body).isEmpty()?e.title:e.body;
            line(canvas,"“",45,205,462,70,R,Paint.Align.LEFT);
            paragraph(canvas,quote,52,220,448,330,34,K,Layout.Alignment.ALIGN_CENTER);
            line(canvas,clean(e.byline).isEmpty()?"未提供署名":"— "+e.byline,500,602,448,26,K,Paint.Align.RIGHT);
        }else{
            paragraph(canvas,e.title,52,184,448,130,38,K,Layout.Alignment.ALIGN_NORMAL);
            if(!clean(e.byline).isEmpty())line(canvas,e.byline,52,345,448,26,R,Paint.Align.LEFT);
            paragraph(canvas,e.body,52,384,448,244,28,K,Layout.Alignment.ALIGN_NORMAL);
        }
    }
    private static void birthday(Canvas canvas,DailyContent.Scene scene,DailyContent.Entry e){
        // The portrait gets the space freed by the compact header and removed technical footer.
        // Contain fitting keeps the complete character image; no stretching or face crop is applied.
        boolean displayed=scene.portrait!=null&&scene.portrait.length>0&&clean(scene.portraitId).equals(clean(e.id))&&portrait(canvas,scene.portrait,28,88,496,500);
        if(!displayed){box(canvas,28,88,524,92,Y);paragraph(canvas,"画像未加载或无法显示\n在内容页点“获取画像”",52,272,448,124,28,K,Layout.Alignment.ALIGN_CENTER);box(canvas,28,584,524,588,Y);}
        line(canvas,e.title,276,632,496,34,K,Paint.Align.CENTER);
        String work=clean(e.work);if(work.isEmpty())work="作品信息未提供";
        paragraph(canvas,work,28,654,496,58,24,R,Layout.Alignment.ALIGN_CENTER);
        String detail=clean(e.byline);if(detail.isEmpty())detail=clean(e.body);
        if(!detail.isEmpty())line(canvas,detail,276,746,496,22,K,Paint.Align.CENTER);
    }
    private static boolean portrait(Canvas canvas,byte[] bytes,int x,int y,int width,int height){
        if(bytes.length>2*1024*1024)return false;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>32000000L)return false;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inSampleSize=1;while(Math.max(bounds.outWidth/options.inSampleSize,bounds.outHeight/options.inSampleSize)>1024)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)return false;
        Bitmap output=null;try{
            int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
            int[] fitted=InkSightFitter.fit(pixels,bitmap.getWidth(),bitmap.getHeight(),width,height,false,1,0,0);
            byte[] codes=InkSightQuantizer.quantize(fitted,width,height,true);for(int i=0;i<fitted.length;i++)fitted[i]=ImageRaster.color(codes[i]);
            output=Bitmap.createBitmap(fitted,width,height,Bitmap.Config.ARGB_8888);canvas.drawBitmap(output,x,y,null);return true;
        }finally{bitmap.recycle();if(output!=null)output.recycle();}
    }
    private static TextPaint paint(int color,int size){TextPaint paint=new TextPaint();paint.setColor(color);paint.setAntiAlias(false);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(size);paint.setSubpixelText(false);return paint;}
    private static void box(Canvas c,float left,float top,float right,float bottom,int color){Paint p=new Paint();p.setColor(color);c.drawRect(Math.round(left),Math.round(top),Math.round(right),Math.round(bottom),p);}
    private static void line(Canvas c,String value,float x,float baseline,int width,int size,int color,Paint.Align align){TextPaint p=paint(color,Math.max(22,size));p.setTextAlign(align);String s=clean(value).replace('\n',' ');CharSequence text=TextUtils.ellipsize(s,p,width,TextUtils.TruncateAt.END);c.drawText(text,0,text.length(),Math.round(x),Math.round(baseline),p);}
    private static void paragraph(Canvas c,String value,float x,float y,int width,int height,int requested,int color,Layout.Alignment alignment){
        String text=clean(value);TextPaint p=paint(color,requested);StaticLayout chosen=null;
        for(int size=requested;size>=22;size--){p.setTextSize(size);StaticLayout candidate=layout(text,p,width,Integer.MAX_VALUE,alignment);if(candidate.getHeight()<=height){chosen=candidate;break;}}
        if(chosen==null){p.setTextSize(22);StaticLayout all=layout(text,p,width,Integer.MAX_VALUE,alignment);int lines=1;while(lines<all.getLineCount()&&all.getLineBottom(lines)<height)lines++;chosen=layout(text,p,width,lines,alignment);}
        c.save();c.clipRect(x,y,x+width,y+height);c.translate(Math.round(x),Math.round(y));chosen.draw(c);c.restore();
    }
    private static StaticLayout layout(String text,TextPaint p,int width,int maxLines,Layout.Alignment alignment){return StaticLayout.Builder.obtain(text,0,text.length(),p,width).setIncludePad(false).setAlignment(alignment).setLineSpacing(6,1).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();}
    private static String clean(String value){if(value==null)return "";return value.replace('\r',' ').replace('\t',' ').trim();}
    private DailyContentRenderer(){}
}

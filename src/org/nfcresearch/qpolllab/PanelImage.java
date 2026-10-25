package org.nfcresearch.qpolllab;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.InputStream;

/** Offline preview and 2-bit factory-layout image encoding. */
public final class PanelImage {
    public static final int[] PALETTE={ImageRaster.color(0),ImageRaster.color(1),ImageRaster.color(2),ImageRaster.color(3)};
    public static final class Prepared {
        public final Bitmap preview;
        public final byte[] packed;
        public final String settings;
        public final String panelType;
        /** Display RGB for the encoded K/W/Y/R indices; does not change transport codes. */
        public final int[] palette;
        Prepared(Bitmap preview,byte[] packed,String settings,String panelType){this(preview,packed,settings,panelType,PALETTE);}
        Prepared(Bitmap preview,byte[] packed,String settings,String panelType,int[] palette){this.preview=preview;this.packed=packed;this.settings=settings;this.panelType=panelType;this.palette=palette.clone();}
    }
    /** Encode an already quantized image with the existing A0/A1 layout, without another color pass. */
    public static Prepared fromIndexed(byte[] codes,String panelType) {
        return fromIndexed(codes,panelType,PALETTE,"黑白黄红四色");
    }
    public static Prepared fromIndexed(byte[] codes,String panelType,int[] palette,String settings) {
        if(codes==null||codes.length!=PanelProtocol.WIDTH*PanelProtocol.HEIGHT)throw new IllegalArgumentException("屏幕像素码长度错误");
        if(panelType==null||palette==null||palette.length!=4)throw new IllegalArgumentException("屏幕类型或四色色板无效");
        int[] colors=palette.clone();
        for(int color:colors)if((color>>>24)!=255)throw new IllegalArgumentException("四色色板必须不透明");
        for(byte code:codes)if(code<0||code>3)throw new IllegalArgumentException("屏幕像素码必须为 0 到 3");
        PanelType type=panelType.isEmpty()?PanelType.A1:PanelType.parse(panelType);
        byte[] packed=type.pack(codes);
        // Render from the packed payload's decoded indices, including physical A0 row mapping.
        int[] decoded=type.preview(packed);
        for(int i=0;i<decoded.length;i++) {
            int code=0;
            while(code<4&&decoded[i]!=PALETTE[code])code++;
            if(code==4)throw new IllegalStateException("屏幕编码色板无效");
            decoded[i]=colors[code];
        }
        Bitmap image=Bitmap.createBitmap(decoded,PanelProtocol.WIDTH,PanelProtocol.HEIGHT,Bitmap.Config.ARGB_8888);
        return new Prepared(image,packed,settings==null?"":settings,panelType,colors);
    }
    public static Bitmap load(ContentResolver resolver,Uri uri) throws Exception {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=resolver.openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<1||options.outHeight<1)throw new IllegalArgumentException("无法识别图片");
        int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>2048)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB);
        Bitmap decoded;
        try(InputStream in=resolver.openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,options);}
        if(decoded==null)throw new IllegalArgumentException("图片解码失败");
        int orientation=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream in=resolver.openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
        catch(Exception ignored) { }
        Matrix m=new Matrix();
        switch(orientation) {
            case 2:m.setScale(-1,1);break;
            case 3:m.setRotate(180);break;
            case 4:m.setScale(1,-1);break;
            case 5:m.setRotate(90);m.postScale(-1,1);break;
            case 6:m.setRotate(90);break;
            case 7:m.setRotate(270);m.postScale(-1,1);break;
            case 8:m.setRotate(270);break;
            default:break;
        }
        return m.isIdentity()?decoded:Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
    }
    public static Bitmap testPattern() {
        Bitmap b=Bitmap.createBitmap(552,768,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(44);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("NFC 刷新测试",44,85,p);p.setTextSize(27);c.drawText("SN220U / Q-Poll",44,132,p);
        for(int i=0;i<4;i++){p.setColor(PALETTE[i]);c.drawRect(i*138,180,(i+1)*138,570,p);}
        p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRect(3,3,549,765,p);p.setStyle(Paint.Style.FILL);
        p.setTextSize(28);c.drawText("552 × 768",44,640,p);c.drawText("顶部 ↑    黑 白 黄 红",44,690,p);
        return b;
    }
    public static Bitmap rotated(Bitmap source,int rotation) {
        if(rotation==0)return source;
        Matrix rotate=new Matrix();rotate.setRotate(rotation);
        return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),rotate,true);
    }
    public static Prepared prepare(Bitmap source,int rotation,boolean cover,boolean dither,int block,boolean binary,int threshold,boolean cropped,String panelType) {
        return prepare(source,rotation,cover,dither,block,binary,threshold,cropped,panelType,PhotoAdjust.ORIGINAL);
    }
    public static Prepared prepare(Bitmap source,int rotation,boolean cover,boolean dither,int block,boolean binary,int threshold,boolean cropped,String panelType,PhotoAdjust photo) {
        return prepare(source,rotation,cover,dither,block,binary,threshold,cropped,panelType,photo,-1,100,photo.perceptual?1:0);
    }
    public static Prepared prepare(Bitmap source,int rotation,boolean cover,boolean dither,int block,boolean binary,int threshold,boolean cropped,String panelType,PhotoAdjust photo,int algorithm,int strength,int tone) {
        if(block>1)dither=false;
        if(algorithm>=0){if(!dither)algorithm=0;if(algorithm==0||strength==0)dither=false;}
        Bitmap input=rotated(source,rotation);
        Bitmap fitted=Bitmap.createBitmap(552,768,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(fitted);canvas.drawColor(Color.WHITE);
        float sx=552f/input.getWidth(),sy=768f/input.getHeight(),scale=cover?Math.max(sx,sy):Math.min(sx,sy);
        float w=input.getWidth()*scale,h=input.getHeight()*scale;
        canvas.drawBitmap(input,null,new RectF((552-w)/2,(768-h)/2,(552+w)/2,(768+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[552*768];
        fitted.getPixels(pixels,0,552,0,0,552,768);
        // Leave letterbox margins white; adjust only the drawn image area.
        for(int y=0;y<768;y++)for(int x=0;x<552;x++)if(x+0.5f>=(552-w)/2&&x+0.5f<(552+w)/2&&y+0.5f>=(768-h)/2&&y+0.5f<(768+h)/2)pixels[y*552+x]=photo.apply(pixels[y*552+x]);
        byte[] codes=(!binary&&block==1&&algorithm>=0)?FourColorDither.quantize(pixels,552,768,algorithm,strength,tone):ImageRaster.quantize(pixels,552,768,binary,threshold,dither,block,photo.perceptual);
        // An unselected panel can still preview; sending requires an explicit selection.
        PanelType type=panelType.isEmpty()?PanelType.A1:PanelType.parse(panelType);
        byte[] packed=type.pack(codes);
        Bitmap image=Bitmap.createBitmap(type.preview(packed),552,768,Bitmap.Config.ARGB_8888);
        String settings="屏幕："+(panelType.isEmpty()?"尚未选择":panelType)+" · "+(binary?"黑白二值化 · 阈值 "+threshold:"黑白黄红四色")+" · 简化："+(block==1?"原始细节":block+"×"+block+" 色块")+" · 抖动："+(dither?"开启":"关闭")+" · "+(cropped?"已手动裁切 · ":"")+"旋转 "+rotation+"° · "+(cover?"填满":"留白");
        return new Prepared(image,packed,settings+" · "+photo.describe()+(algorithm>=0&&!binary&&block==1?" · "+FourColorDither.names()[dither?algorithm:0]+" / 强度 "+strength+"% / "+new String[]{"原版 RGB","感知色彩","实验暖调"}[tone]:" · 原版色块/二值化处理"),panelType);
    }
}

package org.nfcresearch.qpolllab;

/** Immutable, offline adjustments before quantization. No display/RF configuration changes. */
public final class PhotoAdjust {
    public final int brightness, contrast, saturation, shadows;
    public final boolean perceptual;
    public static final PhotoAdjust ORIGINAL=new PhotoAdjust(0,100,100,0,false);
    public PhotoAdjust(int brightness,int contrast,int saturation,int shadows,boolean perceptual) {
        if(brightness < -50 || brightness > 50 || contrast < 50 || contrast > 180 ||
           saturation < 0 || saturation > 180 || shadows < 0 || shadows > 60)
            throw new IllegalArgumentException("图像增强参数超出范围");
        this.brightness=brightness;this.contrast=contrast;this.saturation=saturation;
        this.shadows=shadows;this.perceptual=perceptual;
    }
    public int apply(int p) {
        if(brightness==0&&contrast==100&&saturation==100&&shadows==0)return p;
        double r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
        double l=(299*r+587*g+114*b)/1000;
        double lift=shadows*1.5*Math.pow(1-l/255,2);
        r=(r-128)*contrast/100+128+brightness*2+lift;
        g=(g-128)*contrast/100+128+brightness*2+lift;
        b=(b-128)*contrast/100+128+brightness*2+lift;
        l=(299*r+587*g+114*b)/1000;
        return 0xff000000|(clamp(l+(r-l)*saturation/100)<<16)|
            (clamp(l+(g-l)*saturation/100)<<8)|clamp(l+(b-l)*saturation/100);
    }
    private static int clamp(double v){return (int)Math.round(Math.max(0,Math.min(255,v)));}
    public String describe(){return "亮度 "+brightness+" · 对比度 "+contrast+"% · 饱和度 "+saturation+"% · 提亮暗部 "+shadows;}
}

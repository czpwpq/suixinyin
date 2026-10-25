package org.nfcresearch.qpolllab;

/**
 * Offline four-color quantization. Independently implemented standard diffusion kernels.
 * Code values and display colors come from ImageRaster, never from an assumed RGBY order.
 * Tone 0 deliberately retains the legacy RGB nearest-color behavior (including gray tint).
 * Tones 1/2 use linear-light error diffusion and neutral/highlight protection. Tone 2 is
 * an experimental HSV warm-color compression, not a measured calibration of the panel.
 * Additional working memory is O(width); the input is never modified.
 */
public final class FourColorDither {
    private static final String[] NAMES={"关闭抖动", "蛇形 Floyd–Steinberg", "Atkinson", "有序 Bayer 4×4", "Sierra Lite"};
    private static final int[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
    private static final double[] LINEAR=new double[256];
    private static final double[][] PANEL=new double[4][3], RAW=new double[4][3], LAB=new double[4][3];
    static {
        for(int i=0;i<256;i++){double v=i/255.0;LINEAR[i]=v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
        for(int k=0;k<4;k++){
            int p=ImageRaster.color(k);
            RAW[k][0]=(p>>>16)&255;RAW[k][1]=(p>>>8)&255;RAW[k][2]=p&255;
            PANEL[k][0]=LINEAR[(p>>>16)&255];PANEL[k][1]=LINEAR[(p>>>8)&255];PANEL[k][2]=LINEAR[p&255];
            lab(PANEL[k][0],PANEL[k][1],PANEL[k][2],LAB[k]);
        }
    }
    private FourColorDither(){}
    public static String[] names(){return NAMES.clone();}

    /** Algorithm 0..4, strength 0..100, tone 0=legacy RGB, 1=perceptual, 2=warm photo. */
    public static byte[] quantize(int[] pixels,int width,int height,int algorithm,int strength,int tone){
        if(pixels==null||width<=0||height<=0||width>Integer.MAX_VALUE-4||
           (long)width*height!=pixels.length||algorithm<0||algorithm>=NAMES.length||
           strength<0||strength>100||tone<0||tone>2)
            throw new IllegalArgumentException("四色处理参数错误");
        byte[] out=new byte[pixels.length];
        int mode=strength==0?0:algorithm;
        boolean diffusion=mode==1||mode==2||mode==4;
        float[][][] error=diffusion?new float[3][3][width+4]:null;
        double[] target=new double[3],unclipped=new double[3],point=new double[3];
        double amount=strength/100.0,limit=tone==0?255:1;
        for(int y=0;y<height;y++){
            int direction=diffusion&&(y&1)!=0?-1:1;
            int first=direction==1?0:width-1;
            for(int step=0;step<width;step++){
                int x=first+step*direction,index=y*width+x,p=pixels[index];
                int r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
                // Preserve already prepared RGBY artwork even beside diffusion errors.
                int exact=exact(p);
                if(exact>=0){out[index]=(byte)exact;continue;}
                int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
                boolean neutral=tone!=0&&max-min<=10;
                if(tone!=0&&min>=247&&max-min<=8){out[index]=1;continue;}
                if(tone==0){target[0]=r;target[1]=g;target[2]=b;}
                else {
                    target[0]=LINEAR[r];target[1]=LINEAR[g];target[2]=LINEAR[b];
                    if(tone==2&&!neutral)warm(r,g,b,target);
                }
                if(diffusion)for(int c=0;c<3;c++)target[c]+=error[y%3][c][x+2];
                if(neutral){double gray=luminance(target[0],target[1],target[2]);target[0]=target[1]=target[2]=gray;}
                for(int c=0;c<3;c++){unclipped[c]=target[c];target[c]=clamp(target[c],limit);}
                int best=nearest(target,tone,neutral,point);
                if(mode==3)best=ordered(target,tone,neutral,best,amount,(BAYER[(y&3)*4+(x&3)]+0.5)/16.0);
                out[index]=(byte)best;
                if(diffusion)for(int c=0;c<3;c++){
                    double chosen=tone==0?((ImageRaster.color(best)>>>(16-8*c))&255):PANEL[best][c];
                    // Preserve out-of-gamut residual in linear modes. Dropping negative
                    // light before diffusion washes out midtones with perceptual selection.
                    spread(error,x,y,width,height,direction,mode,c,((tone==0?target[c]:unclipped[c])-chosen)*amount);
                }
            }
            if(diffusion)for(float[] channel:error[y%3])java.util.Arrays.fill(channel,0);
        }
        return out;
    }

    /** Bayer selects a mixture of two panel colors instead of adding RGB noise.
     * In linear modes this preserves average light (a perceptual threshold alone does not).
     */
    private static int ordered(double[] rgb,int tone,boolean neutral,int nearest,double amount,double threshold){
        double[][] colors=tone==0?RAW:PANEL;
        double r=rgb[0]*amount+colors[nearest][0]*(1-amount);
        double g=rgb[1]*amount+colors[nearest][1]*(1-amount);
        double b=rgb[2]*amount+colors[nearest][2]*(1-amount);
        int count=neutral?2:4,first=nearest,second=nearest;
        double ratio=0,best=Double.POSITIVE_INFINITY;
        for(int i=0;i<count;i++)for(int j=i+1;j<count;j++){
            double vr=colors[j][0]-colors[i][0],vg=colors[j][1]-colors[i][1],vb=colors[j][2]-colors[i][2];
            double er=r-colors[i][0],eg=g-colors[i][1],eb=b-colors[i][2];
            double t=clamp(dot(er,eg,eb,vr,vg,vb)/dot(vr,vg,vb,vr,vg,vb),1);
            er-=t*vr;eg-=t*vg;eb-=t*vb;
            double score=dot(er,eg,eb,er,eg,eb);
            if(score<best){best=score;first=i;second=j;ratio=t;}
        }
        return threshold<ratio?second:first;
    }
    private static double dot(double r,double g,double b,double rr,double gg,double bb){
        double y=luminance(r,g,b),yy=luminance(rr,gg,bb);
        return 4*y*yy+(r-y)*(rr-yy)+(b-y)*(bb-yy);
    }

    private static int exact(int p){
        for(int k=0;k<4;k++)if((p&0xffffff)==(ImageRaster.color(k)&0xffffff))return k;
        return -1;
    }
    private static int nearest(double[] rgb,int tone,boolean neutral,double[] point){
        if(tone!=0)lab(rgb[0],rgb[1],rgb[2],point);
        int best=0;double score=Double.POSITIVE_INFINITY;
        for(int k=0;k<(neutral?2:4);k++){
            double distance;
            if(tone==0){int p=ImageRaster.color(k);double dr=rgb[0]-((p>>>16)&255),dg=rgb[1]-((p>>>8)&255),db=rgb[2]-(p&255);distance=dr*dr+dg*dg+db*db;}
            else {double dl=point[0]-LAB[k][0],da=point[1]-LAB[k][1],db=point[2]-LAB[k][2];distance=1.35*dl*dl+da*da+db*db;}
            if(distance<score){score=distance;best=k;}
        }
        return best;
    }

    /** Compress unsupported cool chroma; retain luminance while reducing orange/skin tint. */
    private static void warm(int r,int g,int b,double[] rgb){
        int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),delta=max-min;
        if(delta==0||max==0)return;
        double hue;
        if(max==r)hue=60.0*(g-b)/delta;
        else if(max==g)hue=120+60.0*(b-r)/delta;
        else hue=240+60.0*(r-g)/delta;
        if(hue<0)hue+=360;
        double saturation=delta/(double)max;
        // Smooth hue windows avoid a discontinuity around the yellow/red boundaries.
        double warmWeight=hue<=65?1:hue<100?(100-hue)/35:hue>320?(hue-320)/40:0;
        double keep=(0.20+0.65*warmWeight)*Math.pow(saturation,0.15);
        double light=luminance(rgb[0],rgb[1],rgb[2]);
        for(int c=0;c<3;c++)rgb[c]=light+(rgb[c]-light)*keep;
    }
    private static double luminance(double r,double g,double b){return 0.2126*r+0.7152*g+0.0722*b;}
    private static double clamp(double value,double max){return Math.max(0,Math.min(max,value));}
    // Oklab from linear sRGB, with D65 coefficients; no Android/library dependency.
    private static void lab(double r,double g,double b,double[] out){
        double l=Math.cbrt(0.4122214708*r+0.5363325363*g+0.0514459929*b);
        double m=Math.cbrt(0.2119034982*r+0.6806995451*g+0.1073969566*b);
        double s=Math.cbrt(0.0883024619*r+0.2817188376*g+0.6299787005*b);
        out[0]=0.2104542553*l+0.7936177850*m-0.0040720468*s;
        out[1]=1.9779984951*l-2.4285922050*m+0.4505937099*s;
        out[2]=0.0259040371*l+0.7827717662*m-0.8086757660*s;
    }
    private static void spread(float[][][] rows,int x,int y,int width,int height,int dir,int algorithm,int channel,double value){
        if(algorithm==1){
            add(rows,x+dir,y,width,height,channel,value*7/16);
            add(rows,x-dir,y+1,width,height,channel,value*3/16);
            add(rows,x,y+1,width,height,channel,value*5/16);
            add(rows,x+dir,y+1,width,height,channel,value/16);
        }else if(algorithm==2){
            double e=value/8;
            add(rows,x+dir,y,width,height,channel,e);add(rows,x+2*dir,y,width,height,channel,e);
            add(rows,x-dir,y+1,width,height,channel,e);add(rows,x,y+1,width,height,channel,e);
            add(rows,x+dir,y+1,width,height,channel,e);add(rows,x,y+2,width,height,channel,e);
        }else {
            add(rows,x+dir,y,width,height,channel,value/2);
            add(rows,x-dir,y+1,width,height,channel,value/4);
            add(rows,x,y+1,width,height,channel,value/4);
        }
    }
    private static void add(float[][][] rows,int x,int y,int width,int height,int channel,double value){
        if(x>=0&&x<width&&y<height)rows[y%3][channel][x+2]+=(float)value;
    }
}

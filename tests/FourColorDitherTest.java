package org.nfcresearch.qpolllab;

import java.util.Arrays;
import java.util.Random;

public final class FourColorDitherTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void rejected(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected invalid parameter rejection");}
    private static int gray(int value){return 0xff000000|(value<<16)|(value<<8)|value;}
    private static double light(int value){double s=value/255.0;return s<=0.04045?s/12.92:Math.pow((s+0.055)/1.055,2.4);}
    public static void main(String[] args){
        String[] names=FourColorDither.names();check(names.length==5,"Algorithm count");names[0]="changed";
        check(!"changed".equals(FourColorDither.names()[0]),"Names exposes mutable state");
        Random rng=new Random(20261022);int w=37,h=23;int[] pixels=new int[w*h];
        for(int i=0;i<pixels.length;i++)pixels[i]=rng.nextInt();
        int[] original=pixels.clone();int combinations=0;
        for(int tone=0;tone<3;tone++){
            byte[] plain=FourColorDither.quantize(pixels,w,h,0,100,tone);
            for(int algorithm=0;algorithm<names.length;algorithm++){
                check(Arrays.equals(plain,FourColorDither.quantize(pixels,w,h,algorithm,0,tone)),"Zero strength differs from no dithering");
                for(int strength:new int[]{0,1,50,99,100}){
                    byte[] result=FourColorDither.quantize(pixels,w,h,algorithm,strength,tone);
                    check(result.length==pixels.length,"Wrong output length");
                    for(byte code:result)check(code>=0&&code<4,"Code outside panel palette");
                    check(Arrays.equals(result,FourColorDither.quantize(pixels,w,h,algorithm,strength,tone)),"Non-deterministic output");
                    check(Arrays.equals(original,pixels),"Mutated source pixels");combinations++;
                }
            }
        }
        // Compatibility intentionally includes the legacy tendency to tint middle grays.
        for(int i=0;i<10;i++){
            for(int p=0;p<pixels.length;p++)pixels[p]=rng.nextInt();
            check(Arrays.equals(ImageRaster.quantize(pixels,w,h,false,128,false,1),FourColorDither.quantize(pixels,w,h,0,100,0)),"Legacy RGB no-dither compatibility");
        }
        // Prepared four-color artwork must remain identical for every algorithm and tone.
        int[] artwork=new int[17*19];byte[] expected=new byte[artwork.length];
        for(int i=0;i<artwork.length;i++){int code=rng.nextInt(4);artwork[i]=ImageRaster.color(code);expected[i]=(byte)code;}
        for(int tone=0;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++)for(int strength:new int[]{0,35,100})
            check(Arrays.equals(expected,FourColorDither.quantize(artwork,17,19,algorithm,strength,tone)),"Exact palette changed");
        // Palette swatches interleaved with photos also ignore incoming error.
        for(int i=0;i<pixels.length;i++)pixels[i]=(i%3==0)?ImageRaster.color(i%4):rng.nextInt();
        for(int tone=0;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++){
            byte[] codes=FourColorDither.quantize(pixels,w,h,algorithm,100,tone);
            for(int i=0;i<pixels.length;i+=3)check(codes[i]==i%4,"Palette corrupted by neighboring diffusion error");
        }
        int[] grays=new int[256*9];for(int i=0;i<grays.length;i++)grays[i]=gray(i%256);
        for(int tone=1;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++){
            byte[] codes=FourColorDither.quantize(grays,256,9,algorithm,100,tone);
            for(int i=0;i<codes.length;i++){
                check(codes[i]<=1,"Neutral gradient became chromatic");
                if((i%256)>=247)check(codes[i]==1,"Near-white highlight polluted");
            }
        }
        // Neutral protection also holds after bright colored input in the same row.
        for(int i=0;i<pixels.length;i++)pixels[i]=(i%2==0)?0xffff6611:gray(128);
        for(int tone=1;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++){
            byte[] codes=FourColorDither.quantize(pixels,w,h,algorithm,100,tone);
            for(int i=1;i<codes.length;i+=2)check(codes[i]<=1,"Color diffusion leaked into neutral pixel");
        }
        // Conservation is meaningful for photos: full-strength linear FS/Sierra match
        // a gray field's average reflected light; Bayer is limited to 1/16 coverage.
        int[] field=new int[128*128];Arrays.fill(field,gray(128));
        for(int tone=1;tone<3;tone++)for(int algorithm:new int[]{1,3,4}){
            byte[] codes=FourColorDither.quantize(field,128,128,algorithm,100,tone);
            int whites=0;for(byte code:codes){check(code<=1,"Gray field acquired tint");if(code==1)whites++;}
            double coverage=whites/(double)codes.length;
            check(Math.abs(coverage-light(128))<(algorithm==3?0.035:0.025),"Gray luminance coverage lost: "+coverage+" algorithm "+algorithm);
        }
        // Very narrow/short images cover mirrored kernels, two-row lookahead, edge clipping.
        for(int width:new int[]{1,2,3,4,19})for(int height:new int[]{1,2,3,4,11}){
            int[] small=new int[width*height];for(int i=0;i<small.length;i++)small[i]=rng.nextInt();
            for(int tone=0;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++)
                check(FourColorDither.quantize(small,width,height,algorithm,100,tone).length==small.length,"Edge dimensions");
        }
        // Full screen smoke check, including O(width) diffusion row reuse.
        int[] panel=new int[552*768];for(int i=0;i<panel.length;i++)panel[i]=rng.nextInt();
        long begin=System.nanoTime();
        for(int tone=0;tone<3;tone++)for(int algorithm=0;algorithm<5;algorithm++){
            byte[] result=FourColorDither.quantize(panel,552,768,algorithm,100,tone);
            for(byte code:result)check(code>=0&&code<=3,"Full panel palette");
            for(PanelType type:PanelType.values()){
                byte[] packed=type.pack(result);check(packed.length==105984,"Packed frame length");
                int[] preview=type.preview(packed);
                for(int i=0;i<result.length;i++)check(preview[i]==ImageRaster.color(result[i]),"Dither / panel packed preview mismatch: "+type);
            }
        }
        rejected(()->FourColorDither.quantize(null,1,1,0,50,0));
        rejected(()->FourColorDither.quantize(new int[1],0,1,0,50,0));
        rejected(()->FourColorDither.quantize(new int[1],1,-1,0,50,0));
        rejected(()->FourColorDither.quantize(new int[4],2,3,0,50,0));
        rejected(()->FourColorDither.quantize(new int[1],Integer.MAX_VALUE,1,0,50,0));
        rejected(()->FourColorDither.quantize(new int[1],65536,65536,0,50,0));
        rejected(()->FourColorDither.quantize(new int[1],1,1,-1,50,0));
        rejected(()->FourColorDither.quantize(new int[1],1,1,5,50,0));
        rejected(()->FourColorDither.quantize(new int[1],1,1,0,-1,0));
        rejected(()->FourColorDither.quantize(new int[1],1,1,0,101,0));
        rejected(()->FourColorDither.quantize(new int[1],1,1,0,50,-1));
        rejected(()->FourColorDither.quantize(new int[1],1,1,0,50,3));
        System.out.println("FourColorDitherTest: PASS ("+combinations+" settings, legacy compatibility, exact RGBY, neutral/highlights, linear light, determinism, input immutability, bounds, 15 full-panel modes and 30 A0/A1 packed-preview roundtrips in "+((System.nanoTime()-begin)/1000000)+" ms)");
    }
}

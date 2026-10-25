package org.nfcresearch.qpolllab;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Additional app-side bounds, edit, alpha and cancellation invariants, beyond upstream RGB fixtures. */
public final class InkSightFitterTest {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void invalid(Runnable action){try{action.run();throw new AssertionError("Invalid bounds accepted");}catch(IllegalArgumentException expected){}}
    private static void equal(int[] actual,int[] expected,String label){check(Arrays.equals(actual,expected),label);}
    public static void main(String[] args) {
        int[] alpha={0x00000000,0x80ff0000,0xffffffff,0xff000000},pristine=alpha.clone();
        equal(InkSightFitter.fit(alpha,2,2,2,2,false),new int[]{0xffffffff,0xffff7f7f,0xffffffff,0xff000000},"Alpha is flattened on white before filtering");
        equal(alpha,pristine,"Fitter mutated alpha input");
        int[] stripes=new int[12];
        for(int y=0;y<2;y++)for(int x=0;x<6;x++)stripes[y*6+x]=0xff000000|(x*40<<16)|(x*30<<8)|x*20;
        int[] left=InkSightFitter.fit(stripes,6,2,2,2,true,1,1,0),right=InkSightFitter.fit(stripes,6,2,2,2,true,1,-1,0);
        equal(left,new int[]{stripes[0],stripes[1],stripes[6],stripes[7]},"Positive offset reveals the source left edge");
        equal(right,new int[]{stripes[4],stripes[5],stripes[10],stripes[11]},"Negative offset reveals the source right edge");
        int[] vertical=new int[12];for(int y=0;y<6;y++)for(int x=0;x<2;x++)vertical[y*2+x]=stripes[y];
        equal(InkSightFitter.fit(vertical,2,6,2,2,true,1,0,1),Arrays.copyOf(vertical,4),"Vertical edge position");
        equal(InkSightFitter.fit(stripes,6,2,2,2,true,1,8,0),left,"Offset clamp");
        equal(InkSightFitter.fit(stripes,6,2,2,2,true,Double.NaN,Double.NaN,0),InkSightFitter.fit(stripes,6,2,2,2,true),"Nonfinite edit sanitization");
        equal(InkSightFitter.fit(alpha,2,2,2,2,true,9,0,0),InkSightFitter.fit(alpha,2,2,2,2,true,4,0,0),"Zoom clamp");
        int[] square=new int[16];for(int i=0;i<16;i++)square[i]=0xff000000|i*0x0d0d0d;
        equal(InkSightFitter.fit(square,4,4,2,2,true,2,0,0),new int[]{square[5],square[6],square[9],square[10]},"Zoom applied before center crop");
        int[] black={0xff000000,0xff000000};
        int[] contain=InkSightFitter.fit(black,2,1,4,4,false);
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)check(contain[y*4+x]==(y==0||y==3?0xffffffff:0xff000000),"Contain white letterbox placement");
        int[] cover=InkSightFitter.fit(black,2,1,4,4,true);for(int value:cover)check(value==0xff000000,"Cover gap");
        invalid(()->InkSightFitter.fit(null,1,1,1,1,false));
        invalid(()->InkSightFitter.fit(new int[1],0,1,1,1,false));
        invalid(()->InkSightFitter.fit(new int[1],1,2,1,1,false));
        invalid(()->InkSightFitter.fit(new int[1],1,1,0,1,false));
        invalid(()->InkSightFitter.fit(new int[1],1,1,Integer.MAX_VALUE,2,false));
        invalid(()->InkSightFitter.fit(new int[1],1,1,4001,4000,false));
        invalid(()->InkSightFitter.fit(new int[10000],10000,1,552,768,true));
        invalid(()->InkSightQuantizer.quantize(null,1,1,false));
        invalid(()->InkSightQuantizer.quantize(new int[1],Integer.MAX_VALUE,2,false));
        for(boolean enhanced:new boolean[]{false,true}) {
            int[] palette=InkSightQuantizer.COLORS.clone(),original=palette.clone();
            byte[] result=InkSightQuantizer.quantize(palette,4,1,enhanced);
            check(Arrays.equals(palette,original),"Quantizer mutated source");
            for(byte code:result)check(code>=0&&code<4,"Palette index bounds");
            check(Arrays.equals(result,InkSightQuantizer.quantize(palette,4,1,enhanced)),"Quantizer is not deterministic");
        }
        check(Arrays.equals(InkSightQuantizer.quantize(InkSightQuantizer.COLORS,4,1,false),new byte[]{0,1,2,3}),"Plain exact palette roundtrip");
        Thread.currentThread().interrupt();
        try{InkSightFitter.fit(alpha,2,2,2,2,false);throw new AssertionError("Fitter ignored cancellation");}catch(CancellationException expected){}finally{Thread.interrupted();}
        Thread.currentThread().interrupt();
        try{InkSightQuantizer.quantize(alpha,2,2,true);throw new AssertionError("Quantizer ignored cancellation");}catch(CancellationException expected){}finally{Thread.interrupted();}
        System.out.println("InkSight fitter tests passed: alpha, contain/cover, zoom/offset, input ownership, bounds, cancellation");
    }
}

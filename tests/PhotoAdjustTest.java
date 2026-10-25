package org.nfcresearch.qpolllab;

import java.util.Arrays;

public final class PhotoAdjustTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        // Neutral settings must preserve upstream inputs exactly, including alpha.
        java.util.Random random=new java.util.Random(17020);
        for(int i=0;i<10000;i++){int p=random.nextInt();check(PhotoAdjust.ORIGINAL.apply(p)==p,"neutral changed a pixel");}
        check(new PhotoAdjust(10,100,100,0,false).apply(0xff404040)==0xff545454,"brightness");
        check((new PhotoAdjust(0,100,100,30,true).apply(0xff202020)&255)>32,"shadow lift");
        int gray=new PhotoAdjust(0,100,0,0,true).apply(0xffff0000);
        check(((gray>>16)&255)==((gray>>8)&255)&&((gray>>8)&255)==(gray&255),"desaturation");
        try{new PhotoAdjust(51,100,100,0,false);throw new AssertionError("bounds");}catch(IllegalArgumentException expected){}
        int width=552,height=768;int[] pixels=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=ImageRaster.color(x/138);
        int[] original=pixels.clone();
        for(boolean dither:new boolean[]{false,true}){
            byte[] codes=ImageRaster.quantize(pixels,width,height,false,128,dither,1,true);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)check(codes[y*width+x]==x/138,"pure color changed");
            for(PanelType type:new PanelType[]{PanelType.A0,PanelType.A1}){
                byte[] packed=type.pack(codes);check(packed.length==105984,"frame size");
                check(Arrays.equals(type.preview(packed),pixels),"layout/preview mismatch");
            }
        }
        for(int i=0;i<pixels.length;i++){int v=i%256;pixels[i]=0xff000000|(v<<16)|(v<<8)|v;}
        byte[] neutral=ImageRaster.quantize(pixels,width,height,false,128,false,1,true);
        for(byte b:neutral)check(b==0||b==1,"neutral gray became chromatic");
        for(int block:new int[]{1,2,4,8,16})for(boolean binary:new boolean[]{false,true}){
            byte[] codes=ImageRaster.quantize(pixels,width,height,binary,128,true,block,true);
            for(byte b:codes)check(b>=0&&b<(binary?2:4),"invalid color");
            if(block>1)for(int y=0;y<height;y++)for(int x=0;x<width;x++)check(codes[y*width+x]==codes[(y/block*block)*width+x/block*block],"nonuniform block");
        }
        int[] synthetic=new int[4096];for(int i=0;i<synthetic.length;i++)synthetic[i]=random.nextInt()|0xff000000;
        int[] snapshot=synthetic.clone();ImageRaster.quantize(synthetic,64,64,false,128,true,4,true);
        check(Arrays.equals(synthetic,snapshot),"input mutated");
        check(original[0]==ImageRaster.color(0),"test fixture corrupted");
        System.out.println("Photo adjustments, neutral grays, palette, blocks, A0/A1 round trips passed");
    }
}

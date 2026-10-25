package org.nfcresearch.qpolllab;

/** Fixed A/B test images. Only the central glyph changes; there are no timestamps or user data. */
public final class RefreshTestPattern {
    public static byte[] codes(boolean pageB){
        byte[] out=new byte[552*768];java.util.Arrays.fill(out,(byte)1);
        fill(out,16,16,536,24,0);fill(out,16,744,536,752,0);fill(out,16,24,24,744,0);fill(out,528,24,536,744,0);
        fill(out,32,32,80,80,2);fill(out,472,32,520,80,3);fill(out,32,688,80,736,0);fill(out,472,688,520,736,2);
        fill(out,48,116,504,122,0);fill(out,48,646,504,652,0);
        int[] glyph=pageB?new int[]{30,17,17,30,17,17,30}:new int[]{14,17,17,31,17,17,17};
        for(int row=0;row<7;row++)for(int col=0;col<5;col++)if((glyph[row]&(1<<(4-col)))!=0)fill(out,196+col*32,264+row*32,228+col*32,296+row*32,0);
        return out;
    }
    private static void fill(byte[] out,int left,int top,int right,int bottom,int color){for(int y=top;y<bottom;y++)java.util.Arrays.fill(out,y*552+left,y*552+right,(byte)color);}
    private RefreshTestPattern(){}
}

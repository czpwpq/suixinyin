package org.nfcresearch.qpolllab;

public final class RefreshTestPatternTest {
    public static void main(String[] args){byte[] a=RefreshTestPattern.codes(false),b=RefreshTestPattern.codes(true);int changed=0;for(int y=0;y<768;y++)for(int x=0;x<552;x++){int i=y*552+x;if((a[i]&255)>3||(b[i]&255)>3)throw new AssertionError("Palette");if(a[i]!=b[i]){changed++;if(x<196||x>=356||y<264||y>=488)throw new AssertionError("Reference edge changed");}}if(changed<1000)throw new AssertionError("Pattern is not distinguishable");a[0]=3;if(RefreshTestPattern.codes(false)[0]!=1)throw new AssertionError("Shared mutable pattern");System.out.println("RefreshTestPatternTest PASS: fixed edge markers, central-only A/B difference, four colors, independent frames");}
}

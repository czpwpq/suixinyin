package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;
import java.util.Arrays;

/** Cards must retain every renderer index, without photo enhancement or a second color choice. */
public final class CardFramesNativeChecks {
    public static void run() throws Exception {
        for(int template=0;template<4;template++) {
            CardScene scene=CardScene.calendar();scene.year=2020;scene.month=8;scene.day=31;scene.template=template;scene.title="八月 · 第六行 31 日";scene.foreground=3;scene.background=1;
            scene.city="上海";scene.weather="晴转多云";scene.temperature="23–29 °C";scene.weatherUpdated="2026-10-22 20:30 Asia/Shanghai";scene.weatherSource="Open-Meteo";
            Bitmap expected=CalendarCards.render(scene.year,scene.month,scene.day,scene.title,ImageRaster.color(scene.foreground),ImageRaster.color(scene.background),template,scene.city,scene.weather,scene.temperature,scene.weatherUpdated+" · "+scene.weatherSource);
            try{verify(scene,expected);}finally{expected.recycle();}
        }
        for(int color=0;color<4;color++) {
            CardScene scene=CardScene.note();scene.text="文字保持四色直出\nHello, 四色!";scene.fontSize=67;scene.foreground=color;scene.background=(color+1)&3;scene.outlineColor=(color+2)&3;scene.outline=true;scene.bold=color%2==0;
            Bitmap expected=CardRenderer.note(scene.text,scene.fontSize,ImageRaster.color(scene.foreground),ImageRaster.color(scene.background),scene.outline,ImageRaster.color(scene.outlineColor),scene.bold);
            try{verify(scene,expected);}finally{expected.recycle();}
        }
        CardScene emoji=CardScene.note();emoji.text="便签也能写表情 😀 🙂 🌈 ❤️\nKeep a little color.";
        Bitmap emojiArtwork=CardRenderer.note(emoji.text,emoji.fontSize,ImageRaster.color(emoji.foreground),ImageRaster.color(emoji.background),emoji.outline,ImageRaster.color(emoji.outlineColor),emoji.bold);
        try{verify(emoji,emojiArtwork);}finally{emojiArtwork.recycle();}
        for(int code=0;code<4;code++)check(CardFrames.paletteIndex(ImageRaster.color(code))==code,"Exact palette color was changed by emoji fallback");
        for(int rgb:new int[]{0xff4171b8,0xfffaa359,0xffb591cc,0xffaa7711})check(CardFrames.paletteIndex(rgb)==nearest(rgb),"Residual RGB does not use the nearest four-color index");
        invalid(()->CardFrames.prepare(null,"A1"));CardScene unknown=CardScene.note();unknown.kind=3;invalid(()->CardFrames.prepare(unknown,"A1"));
        invalid(()->CardFrames.prepare(CardScene.note(),"A2"));
        invalid(()->StudioImageCodec.fromPrepared(null));
    }
    private static void verify(CardScene scene,Bitmap rendered) throws Exception {
        byte[] state=scene.encode();int[] expected=pixels(rendered);byte[] codes=new byte[expected.length];
        for(int i=0;i<expected.length;i++)codes[i]=(byte)nearest(expected[i]);
        for(String selected:new String[]{"A0","A1",""}) {
            StudioImageCodec.Result result=CardFrames.prepare(scene,selected);
            try {
                PanelType type=selected.isEmpty()?PanelType.A1:PanelType.parse(selected);
                check(result.prepared.panelType.equals(selected),"Unselected type metadata lost");
                check(result.preview==result.prepared.preview&&result.originalPreview!=result.preview,"Card result ownership");
                check(result.preview.getWidth()==552&&result.preview.getHeight()==768,"Canonical dimensions changed");
                check(Arrays.equals(result.prepared.packed,type.pack(codes)),"Card pixels were altered before "+type+" encoding");
                check(Arrays.equals(result.prepared.palette,InkSightQuantizer.COLORS),"Home card palette inconsistent with photo preview");
                int[] preview=pixels(result.preview);for(int i=0;i<preview.length;i++)check(preview[i]==result.prepared.palette[codes[i]],"Card palette index changed at "+i);
                check(Arrays.equals(preview,pixels(result.originalPreview)),"Card comparison image should preserve the exact flat artwork");
            } finally {result.preview.recycle();result.originalPreview.recycle();}
        }
        check(Arrays.equals(state,scene.encode()),"Card preparation mutated editor parameters");check(Arrays.equals(expected,pixels(rendered)),"Card preparation changed caller's artwork");
    }
    private static int nearest(int pixel){int best=0;long nearest=Long.MAX_VALUE;for(int code=0;code<4;code++){int candidate=ImageRaster.color(code);long r=((pixel>>>16)&255)-((candidate>>>16)&255),g=((pixel>>>8)&255)-((candidate>>>8)&255),b=(pixel&255)-(candidate&255);long distance=r*r+g*g+b*b;if(distance<nearest){nearest=distance;best=code;}}return best;}
    private static int[] pixels(Bitmap bitmap){int[] out=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(out,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return out;}
    private static void invalid(Runnable action){try{action.run();throw new AssertionError("Invalid card accepted");}catch(IllegalArgumentException expected){}}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private CardFramesNativeChecks(){}
}

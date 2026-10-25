package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;

/** Flat cards preserve renderer palette indices exactly; no photo enhancement or dithering pass. */
public final class CardFrames {
    public static StudioImageCodec.Result prepare(CardScene requested,String panelType) {
        if(requested==null)throw new IllegalArgumentException("请先创建日历或便签");
        CardScene scene=requested.copy();scene.normalize();
        String updated=scene.weatherUpdated;
        if(updated.isEmpty())updated="手动天气";
        else if(!scene.weatherSource.isEmpty())updated+=" · "+scene.weatherSource;
        Bitmap rendered=scene.kind==CardScene.CALENDAR?
            CalendarCards.render(scene.year,scene.month,scene.day,scene.title,color(scene.foreground),color(scene.background),scene.template,scene.city,scene.weather,scene.temperature,updated):
            CardRenderer.note(scene.text,scene.fontSize,color(scene.foreground),color(scene.background),scene.outline,color(scene.outlineColor),scene.bold);
        try {
            int[] pixels=new int[PanelProtocol.WIDTH*PanelProtocol.HEIGHT];rendered.getPixels(pixels,0,PanelProtocol.WIDTH,0,0,PanelProtocol.WIDTH,PanelProtocol.HEIGHT);
            byte[] codes=new byte[pixels.length];
            for(int i=0;i<pixels.length;i++) {
                if((i&8191)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                codes[i]=(byte)paletteIndex(pixels[i]);
            }
            PanelImage.Prepared prepared=PanelImage.fromIndexed(codes,panelType,InkSightQuantizer.COLORS,
                (scene.kind==CardScene.CALENDAR?"日历 · "+CalendarCards.templateNames()[scene.template]:"便签")+" · 四色直出 · 无照片增强");
            try{return StudioImageCodec.fromPrepared(prepared);}catch(RuntimeException|Error error){prepared.preview.recycle();throw error;}
        } finally {rendered.recycle();}
    }
    /** Color-font emoji can contain RGB pixels even when text antialiasing is disabled. */
    static int paletteIndex(int pixel) {
        for(int code=0;code<4;code++)if(pixel==color(code))return code;
        int best=0;long nearest=Long.MAX_VALUE;
        for(int code=0;code<4;code++) {
            int candidate=color(code);long red=((pixel>>>16)&255)-((candidate>>>16)&255),green=((pixel>>>8)&255)-((candidate>>>8)&255),blue=(pixel&255)-(candidate&255);
            long distance=red*red+green*green+blue*blue;if(distance<nearest){nearest=distance;best=code;}
        }
        return best;
    }
    private static int color(int index){return ImageRaster.color(index);}
    private CardFrames(){}
}

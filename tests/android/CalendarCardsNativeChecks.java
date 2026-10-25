package org.nfcresearch.qpolllab;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;

/** Called by a device instrumentation runner. Building alone does not execute these checks. */
public final class CalendarCardsNativeChecks {
    private CalendarCardsNativeChecks() { }
    public static void run(Instrumentation runner) throws Exception {
        String[] names = CalendarCards.templateNames();
        check(names.length == 4, "Four calendar templates expected");
        names[0] = "modified";
        check(!CalendarCards.templateNames()[0].equals("modified"), "Template labels leaked mutable internal storage");
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 120; i++) longText.append("中英文天气记录 Mixed text ");
        for (int template = 0; template < 4; template++) {
            check(CalendarCards.isLandscape(template) == (template >= 2), "Wrong orientation metadata");
            Bitmap normal = CalendarCards.render(2020, 8, 31, "八月 · 第六行的 31 日", ImageRaster.color(3), Color.WHITE,
                template, "上海", "晴转多云", "23—29 °C", "2026-10-22 20:30");
            inspect(normal);
            if (template == 2) {
                check(normal.getPixel(541, 10) == ImageRaster.color(2), "Landscape header must rotate to right edge");
                check(normal.getPixel(10, 10) == Color.WHITE, "Landscape source bottom-left should map to physical top-left");
            }
            save(runner, normal, "calendar-template-" + template);
            Bitmap longLabels = CalendarCards.render(2000, 2, 29, longText.toString(), ImageRaster.color(2), Color.BLACK,
                template, longText.toString(), longText.toString(), longText.toString(), longText.toString());
            inspect(longLabels); save(runner, longLabels, "calendar-long-labels-" + template);
            inspect(CalendarCards.render(2026, 2, 0, "", Color.RED, Color.WHITE, template, "", "", "", ""));
            readabilityBounds(template);
        }
        invalid(() -> CalendarCards.isLandscape(-1));
        invalid(() -> CalendarCards.render(2026, 1, 1, "", Color.RED, Color.WHITE, 4, "", "", "", ""));
        invalid(() -> CalendarCards.render(1900, 2, 29, "", Color.RED, Color.WHITE, 0, "", "", "", ""));
        invalid(() -> CalendarCards.render(2026, 13, 1, "", Color.RED, Color.WHITE, 0, "", "", "", ""));
    }
    /** Black-box pixel bounds: changing a long title or old record cannot paint over the month grid. */
    private static void readabilityBounds(int template) {
        String oldRecord="2017-02-03 04:05 (Asia/Shanghai) · Open-Meteo";
        Bitmap first=logical(sample(template,"测试",oldRecord),template);
        Bitmap later=logical(sample(template,"测试","2030-12-29 21:56 (America/New_York) · Open-Meteo"),template);
        Bitmap longTitle=logical(sample(template,"长标题测试 Long calendar title 长标题测试 Long calendar title 长标题测试",oldRecord),template);
        Bitmap again=logical(sample(template,"测试",oldRecord),template);
        try {
            Rect[] recordAreas={new Rect(40,670,512,740),new Rect(40,662,512,740),new Rect(504,416,732,504),new Rect(36,448,292,520)};
            Rect[] titleAreas={new Rect(48,20,504,80),new Rect(24,8,528,64),new Rect(20,4,748,60),new Rect(320,20,752,80)};
            onlyChangesInside(first,later,recordAreas[template],"Record escaped weather footer");
            onlyChangesInside(first,longTitle,titleAreas[template],"Long title escaped its header");
            check(java.util.Arrays.equals(pixels(first),pixels(again)),"Re-rendering the same old weather record changed the artwork");
            // August 2020 needs a sixth row, with 31 on Monday. A visible red marker proves it was not clipped.
            int[] left={28,28,24,328},baseline={521,517,464,463},cell={70,70,63,59};
            int red=0;for(int y=baseline[template]-36;y<=baseline[template]+8;y++)for(int x=left[template];x<left[template]+cell[template];x++)if(first.getPixel(x,y)==ImageRaster.color(3))red++;
            check(red>150,"Sixth-row marked day is missing or clipped");
        } finally {first.recycle();later.recycle();longTitle.recycle();again.recycle();}
    }
    private static Bitmap sample(int template,String title,String record){return CalendarCards.render(2020,8,31,title,ImageRaster.color(3),Color.WHITE,template,"上海","小毛毛雨","20.7°C",record);}
    private static Bitmap logical(Bitmap physical,int template){if(template<2)return physical;Matrix rotation=new Matrix();rotation.setRotate(-90);Bitmap result=Bitmap.createBitmap(physical,0,0,552,768,rotation,false);physical.recycle();return result;}
    private static int[] pixels(Bitmap bitmap){int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return pixels;}
    private static void onlyChangesInside(Bitmap first,Bitmap second,Rect allowed,String reason){int changed=0;for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getPixel(x,y)!=second.getPixel(x,y)){check(allowed.contains(x,y),reason+" at "+x+","+y);changed++;}check(changed>20,"Expected artwork change was not visible");}
    private static void inspect(Bitmap bitmap) {
        check(bitmap.getWidth() == 552 && bitmap.getHeight() == 768, "Physical frame must remain portrait 552x768");
        int[] pixels = new int[552 * 768]; bitmap.getPixels(pixels, 0, 552, 0, 0, 552, 768);
        int yellow = 0;
        for (int pixel : pixels) {
            boolean member = false;
            for (int code = 0; code < 4; code++) if (pixel == ImageRaster.color(code)) member = true;
            check(member, "Calendar contains transparency or colors outside RGBY");
            if (pixel == ImageRaster.color(2)) yellow++;
        }
        check(yellow > 1000, "Yellow accents missing");
    }
    private static void invalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid calendar input accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void save(Instrumentation runner, Bitmap bitmap, String name) throws Exception {
        File root = runner.getTargetContext().getExternalFilesDir(null);
        if (root == null) throw new IllegalStateException("External fixture directory unavailable");
        File folder = new File(root, "card-tests");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create fixture directory");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out), "Could not save fixture");
        }
    }
}

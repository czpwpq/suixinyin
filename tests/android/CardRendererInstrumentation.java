package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.Arrays;

/** Device-side pixel invariants and exported visual fixtures. No external test dependency. */
public final class CardRendererInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            BridgeNativeChecks.run();
            StudioImageNativeChecks.run();
            CardFramesNativeChecks.run();
            DailyContentClientNativeChecks.run();
            runCases();
            CalendarCardsNativeChecks.run(this);
            StudioUiNativeChecks.run(this);
            ReaderNativeChecks.run(this);
            InternalActivationNativeChecks.run(this);
            io.github.muntashirakon.adb.AdbPairingNativeChecks.run();
            result.putString("stream", "CardRendererInstrumentation: PASS\nNative fixtures saved to target external-files/card-tests\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "CardRendererInstrumentation: FAIL\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void runCases() throws Exception {
        Bitmap calendar = CardRenderer.calendar(2020, 8, 31, "八月 · 第六行日期测试", Color.RED, Color.WHITE);
        shape(calendar); palette(calendar, Color.BLACK, Color.WHITE, Color.RED); save(calendar, "calendar-sixth-row");
        shape(CardRenderer.calendar(2000, 2, 29, "闰年", Color.YELLOW, Color.BLACK));
        invalid(() -> CardRenderer.calendar(1900, 2, 29, "", Color.RED, Color.WHITE));
        invalid(() -> CardRenderer.calendar(2026, 13, 0, "", Color.RED, Color.WHITE));

        long midnight = Instant.parse("2026-10-21T16:00:00Z").toEpochMilli();
        Bitmap shanghai = CardRenderer.clock(midnight, "Asia/Shanghai", false, Color.RED, Color.WHITE);
        Bitmap utc = CardRenderer.clock(midnight, "UTC", false, Color.RED, Color.WHITE);
        shape(shanghai); palette(shanghai, Color.BLACK, Color.WHITE, Color.RED);
        check(!Arrays.equals(pixels(shanghai), pixels(utc)), "Different dates/timezones rendered identically");
        save(shanghai, "clock-digital-midnight");
        Bitmap analog = CardRenderer.clock(midnight + 10 * 3600000L + 8 * 60000L, "Asia/Shanghai", true, Color.RED, Color.WHITE);
        shape(analog); palette(analog, Color.BLACK, Color.WHITE, Color.RED); save(analog, "clock-analog");
        invalid(() -> CardRenderer.clock(midnight, "not/a-zone", false, Color.RED, Color.WHITE));

        Bitmap note = CardRenderer.note("今天也要好好生活\nHello, e-paper!", 67, Color.RED, Color.YELLOW, true, Color.BLACK, true);
        shape(note); palette(note, Color.BLACK, Color.YELLOW, Color.RED);
        check(count(note, Color.RED) > 100 && count(note, Color.BLACK) > 100, "Note text/outline missing");
        save(note, "note-outline");
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 900; i++) longText.append("中文与 English 混排。\n");
        Bitmap longNote = CardRenderer.note(longText.toString(), 120, Color.BLACK, Color.WHITE, false, Color.RED, false);
        shape(longNote); palette(longNote, Color.BLACK, Color.WHITE); save(longNote, "note-overflow");
        Bitmap blank = CardRenderer.note(null, -200, Color.BLACK, Color.WHITE, true, Color.RED, false);
        check(count(blank, Color.WHITE) == 552 * 768, "Empty note should remain blank");

        Bitmap source = Bitmap.createBitmap(8, 11, Bitmap.Config.ARGB_8888); source.eraseColor(Color.BLUE);
        Rect low = CardRenderer.polaroidPhotoBounds(-1), high = CardRenderer.polaroidPhotoBounds(1000);
        check(low.left == 12 && high.left == 72 && high.width() > 0 && high.height() > 0, "Margin bounds invalid");
        Rect bounds = CardRenderer.polaroidPhotoBounds(24);
        Bitmap polaroid = CardRenderer.polaroid(source, longText.toString(), 24, Color.WHITE, Color.RED, 0);
        shape(polaroid); save(polaroid, "polaroid-long-caption");
        for (int y = 0; y < 768; y++) for (int x = 0; x < 552; x++) {
            int color = polaroid.getPixel(x, y);
            if (bounds.contains(x, y)) check(color == Color.BLUE, "Photo has a gap or caption leaked into photo");
            else {
                check(color == Color.RED || color == Color.WHITE, "Photo escaped its clip");
                if (color == Color.RED) check(x >= bounds.left && x < bounds.right && y >= bounds.bottom + 8 && y < bounds.bottom + 112,
                    "Caption escaped its bounds");
            }
        }
        check(count(source, Color.BLUE) == 88, "Source mutated by composition");
        Bitmap mono = CardRenderer.styledPhoto(source, 1);
        check(mono != source && mono.getWidth() == 8 && mono.getHeight() == 11, "styledPhoto ownership or dimensions changed");
        int gray = mono.getPixel(0, 0);
        check(Color.red(gray) == Color.green(gray) && Color.green(gray) == Color.blue(gray), "Monochrome is not gray");
        source.eraseColor(Color.TRANSPARENT);
        check(CardRenderer.styledPhoto(source, 0).getPixel(0, 0) == Color.WHITE, "Transparency not flattened");
        invalid(() -> CardRenderer.polaroid(null, "", 24, Color.WHITE, Color.BLACK, 0));
    }

    private static void shape(Bitmap bitmap) {
        check(bitmap.getWidth() == 552 && bitmap.getHeight() == 768, "Wrong output size");
        for (int pixel : pixels(bitmap)) check(Color.alpha(pixel) == 255, "Transparent output pixel");
    }
    private static int[] pixels(Bitmap bitmap) {
        int[] result = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(result, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight()); return result;
    }
    private static int count(Bitmap bitmap, int color) { int n = 0; for (int pixel : pixels(bitmap)) if (pixel == color) n++; return n; }
    private static void palette(Bitmap bitmap, int... colors) {
        for (int pixel : pixels(bitmap)) {
            boolean found = false; for (int color : colors) if (color == pixel) { found = true; break; }
            check(found, "Unexpected intermediate color " + Integer.toHexString(pixel));
        }
    }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private void save(Bitmap bitmap, String name) throws Exception {
        File root = getTargetContext().getExternalFilesDir(null);
        if (root == null) throw new IllegalStateException("Target external files unavailable");
        File folder = new File(root, "card-tests");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create fixture directory");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out), "Fixture write failed");
        }
    }
}

package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Local artwork composition. All output is opaque, portrait 552 x 768 sRGB. */
public final class CardRenderer {
    public static final int WIDTH = 552, HEIGHT = 768;
    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] MONTHS = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
    private CardRenderer() { }

    public static Bitmap calendar(int year, int month1, int markedDay, String title,
                                  int accent, int background) {
        CalendarModel model = new CalendarModel(year, month1);
        if (markedDay < 0 || markedDay > model.days)
            throw new IllegalArgumentException("标记日期须在当月内；0 表示不标记");
        Bitmap result = sheet(background);
        Canvas c = new Canvas(result);
        int ink = contrastInk(background);
        Paint p = paint(ink, 28, false);
        drawSingleLine(c, title == null || title.trim().isEmpty() ? "日历" : title,
            48, 62, 456, 32, 18, ink, true, Paint.Align.LEFT);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(112);
        c.drawText(String.format(Locale.ROOT, "%02d", month1), 44, 179, p);
        p.setTextSize(34); p.setTypeface(Typeface.DEFAULT);
        c.drawText(String.format(Locale.ROOT, "%04d", year), 238, 125, p);
        drawSingleLine(c, MONTHS[month1 - 1], 240, 168, 264, 26, 19, ink, false, Paint.Align.LEFT);
        p.setColor(opaque(accent)); c.drawRect(48, 205, 504, 212, p);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(25);
        float cell = 456f / 7;
        for (int col = 0; col < 7; col++) {
            p.setColor(col >= 5 ? visibleAccent(accent, background) : ink);
            c.drawText(WEEKDAYS[col], 48 + (col + .5f) * cell, 259, p);
        }
        for (int row = 0; row < 6; row++) for (int col = 0; col < 7; col++) {
            int day = model.dayAt(row, col);
            if (day == 0) continue;
            float x = 48 + (col + .5f) * cell, baseline = 321 + row * 59;
            if (day == markedDay) {
                p.setColor(visibleAccent(accent, background));
                c.drawRoundRect(new RectF(x - 25, baseline - 35, x + 25, baseline + 12), 9, 9, p);
                p.setColor(contrastInk(visibleAccent(accent, background)));
            } else p.setColor(col >= 5 ? visibleAccent(accent, background) : ink);
            p.setTypeface(day == markedDay ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(30); c.drawText(Integer.toString(day), x, baseline, p);
        }
        p.setColor(ink); c.drawRect(48, 658, 504, 660, p);
        String footer = markedDay == 0 ? String.format(Locale.ROOT, "%d 年 %d 月", year, month1)
            : String.format(Locale.ROOT, "%d 月 %d 日  ·  星期%s", month1, markedDay,
                WEEKDAYS[model.cellForDay(markedDay) % 7]);
        drawSingleLine(c, footer, 276, 710, 456, 27, 18, ink, false, Paint.Align.CENTER);
        return result;
    }

    /** The displayed minute is the render time. This does not keep time after NFC is removed. */
    public static Bitmap clock(long epochMillis, String zoneId, boolean analog,
                               int accent, int background) {
        ZoneId zone;
        try {
            zone = zoneId == null || zoneId.trim().isEmpty() ? ZoneId.systemDefault() : ZoneId.of(zoneId.trim());
        } catch (DateTimeException invalidZone) {
            throw new IllegalArgumentException("无法识别时区，请使用 Asia/Shanghai 等标准时区名称", invalidZone);
        }
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(zone);
        Bitmap result = sheet(background);
        Canvas c = new Canvas(result);
        int ink = contrastInk(background), color = visibleAccent(accent, background);
        Paint p = paint(ink, 30, false);
        drawSingleLine(c, time.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)),
            276, 83, 456, 32, 18, ink, false, Paint.Align.CENTER);
        drawSingleLine(c, "星期" + WEEKDAYS[time.getDayOfWeek().getValue() - 1],
            276, 131, 456, 26, 18, color, true, Paint.Align.CENTER);
        if (analog) {
            float cx = 276, cy = 402, radius = 190;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(ink);
            c.drawCircle(cx, cy, radius, p); p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 60; i++) {
                double angle = i * Math.PI / 30;
                float outer = radius - 13, inner = outer - (i % 5 == 0 ? 17 : 6);
                p.setStrokeWidth(i % 5 == 0 ? 5 : 2);
                c.drawLine(cx + (float)Math.sin(angle) * inner, cy - (float)Math.cos(angle) * inner,
                    cx + (float)Math.sin(angle) * outer, cy - (float)Math.cos(angle) * outer, p);
            }
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(27); p.setTypeface(Typeface.DEFAULT_BOLD);
            for (int hour = 1; hour <= 12; hour++) {
                double angle = hour * Math.PI / 6;
                c.drawText(Integer.toString(hour), cx + (float)Math.sin(angle) * 140,
                    cy - (float)Math.cos(angle) * 140 - (p.ascent() + p.descent()) / 2, p);
            }
            p.setStrokeCap(Paint.Cap.ROUND);
            hand(c, p, cx, cy, (time.getHour() % 12 + time.getMinute() / 60.0) * Math.PI / 6, 91, 12, ink);
            hand(c, p, cx, cy, time.getMinute() * Math.PI / 30, 130, 7, color);
            p.setColor(ink); c.drawCircle(cx, cy, 11, p);
            drawSingleLine(c, time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)),
                276, 659, 456, 40, 25, ink, true, Paint.Align.CENTER);
        } else {
            drawSingleLine(c, time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)),
                276, 420, 488, 134, 64, ink, true, Paint.Align.CENTER);
            p.setColor(color); c.drawRect(196, 457, 356, 465, p);
        }
        drawSingleLine(c, zone.getId(), 276, 723, 456, 22, 16, ink, false, Paint.Align.CENTER);
        return result;
    }

    public static Bitmap note(String text, int fontSize, int foreground, int background,
                              boolean outline, int outlineColor, boolean bold) {
        Bitmap result = sheet(background);
        Canvas c = new Canvas(result);
        String content = bounded(text, 4000);
        TextPaint p = new TextPaint();
        p.setAntiAlias(false); p.setColor(opaque(foreground));
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        // The same final layout is drawn for both stroke and fill, keeping baselines identical.
        StaticLayout layout = fit(content, p, 456, 640, Math.max(16, Math.min(120, fontSize)), 16, 40);
        float x = 48, y = (HEIGHT - layout.getHeight()) / 2f;
        c.save(); c.clipRect(40, 56, 512, 712); c.translate(x, y);
        if (outline) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(2, p.getTextSize() / 13));
            p.setColor(opaque(outlineColor)); layout.draw(c);
        }
        p.setStyle(Paint.Style.FILL); p.setColor(opaque(foreground)); layout.draw(c); c.restore();
        return result;
    }

    /** The bottom 120 pixels inside the margin are always reserved for a caption. */
    public static Rect polaroidPhotoBounds(int margin) {
        int m = Math.max(12, Math.min(72, margin));
        return new Rect(m, m, WIDTH - m, HEIGHT - m - 120);
    }

    /** style: 0 original, 1 monochrome, 2 warm. Photos are center-cropped into photoBounds. */
    public static Bitmap polaroid(Bitmap photo, String caption, int margin, int background,
                                  int textColor, int style) {
        if (photo == null || photo.isRecycled()) throw new IllegalArgumentException("请先选择照片");
        Bitmap result = sheet(background); Canvas c = new Canvas(result);
        Rect bounds = polaroidPhotoBounds(margin);
        float scale = Math.max(bounds.width() / (float)photo.getWidth(), bounds.height() / (float)photo.getHeight());
        float width = photo.getWidth() * scale, height = photo.getHeight() * scale;
        RectF destination = new RectF(bounds.exactCenterX() - width / 2, bounds.exactCenterY() - height / 2,
            bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2);
        Paint imagePaint = photoPaint(style);
        c.save(); c.clipRect(bounds); c.drawBitmap(photo, null, destination, imagePaint); c.restore();
        TextPaint p = new TextPaint(); p.setAntiAlias(false); p.setColor(opaque(textColor));
        p.setTypeface(Typeface.DEFAULT);
        StaticLayout layout = fit(bounded(caption, 500), p, bounds.width() - 8, 96, 31, 16, 3);
        c.save(); c.clipRect(bounds.left, bounds.bottom + 8, bounds.right, bounds.bottom + 112);
        c.translate(bounds.left + 4, bounds.bottom + 12 + (96 - layout.getHeight()) / 2f);
        layout.draw(c); c.restore();
        return result;
    }

    /** New bitmap, same size as photo, composited over white; the caller owns the result. */
    public static Bitmap styledPhoto(Bitmap photo, int style) {
        if (photo == null || photo.isRecycled()) throw new IllegalArgumentException("请先选择照片");
        Bitmap result = Bitmap.createBitmap(photo.getWidth(), photo.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result); c.drawColor(Color.WHITE);
        c.drawBitmap(photo, 0, 0, photoPaint(style));
        return result;
    }

    private static Paint photoPaint(int style) {
        Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (style == 1) {
            ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0);
            imagePaint.setColorFilter(new ColorMatrixColorFilter(matrix));
        } else if (style == 2) {
            imagePaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                1.06f, 0, 0, 0, 8, 0, 1, 0, 0, 2, 0, 0, .87f, 0, 0, 0, 0, 0, 1, 0
            })));
        }
        return imagePaint;
    }

    private static Bitmap sheet(int background) {
        Bitmap b = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        b.eraseColor(opaque(background)); return b;
    }
    private static int opaque(int color) { return color | 0xff000000; }
    private static int contrastInk(int color) {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 145
            ? Color.BLACK : Color.WHITE;
    }
    private static int visibleAccent(int accent, int background) {
        return opaque(accent) == opaque(background) ? contrastInk(background) : opaque(accent);
    }
    private static Paint paint(int color, float size, boolean bold) {
        Paint p = new Paint(); p.setAntiAlias(false); p.setColor(opaque(color)); p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); return p;
    }
    private static void hand(Canvas c, Paint p, float x, float y, double angle,
                             float length, float width, int color) {
        p.setColor(color); p.setStrokeWidth(width);
        c.drawLine(x - (float)Math.sin(angle) * 17, y + (float)Math.cos(angle) * 17,
            x + (float)Math.sin(angle) * length, y - (float)Math.cos(angle) * length, p);
    }
    private static String bounded(String text, int maxCodePoints) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        int count = normalized.codePointCount(0, normalized.length());
        return count > maxCodePoints ? normalized.substring(0, normalized.offsetByCodePoints(0, maxCodePoints - 1)) + "…" : normalized;
    }
    private static StaticLayout layout(String text, TextPaint p, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(4, 1)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
    }
    private static StaticLayout fit(String text, TextPaint p, int width, int height,
                                    int requestedSize, int minSize, int maxLines) {
        for (int size = requestedSize; size >= minSize; size--) {
            p.setTextSize(size);
            StaticLayout candidate = layout(text, p, width, Integer.MAX_VALUE);
            if (candidate.getHeight() <= height && candidate.getLineCount() <= maxLines) return candidate;
        }
        p.setTextSize(minSize);
        StaticLayout all = layout(text, p, width, Integer.MAX_VALUE);
        int lines = 1;
        while (lines < Math.min(maxLines, all.getLineCount()) && all.getLineBottom(lines) <= height) lines++;
        return layout(text, p, width, lines);
    }
    private static void drawSingleLine(Canvas c, String text, float x, float baseline, int width,
                                       int size, int minSize, int color, boolean bold, Paint.Align align) {
        TextPaint p = new TextPaint(paint(color, size, bold)); p.setTextAlign(align);
        String content = bounded(text, 120).replace('\n', ' ');
        while (p.getTextSize() > minSize && p.measureText(content) > width) p.setTextSize(p.getTextSize() - 1);
        CharSequence display = TextUtils.ellipsize(content, p, width, TextUtils.TruncateAt.END);
        c.drawText(display, 0, display.length(), x, baseline, p);
    }
}

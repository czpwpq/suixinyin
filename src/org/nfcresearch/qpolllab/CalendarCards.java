package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import java.time.LocalDate;
import java.util.Locale;

/** Independent RGBY calendar artwork. Weather is supplied text, never inferred live data. */
public final class CalendarCards {
    private static final int BLACK = ImageRaster.color(0), WHITE = ImageRaster.color(1);
    private static final int YELLOW = ImageRaster.color(2), RED = ImageRaster.color(3);
    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] MONTHS = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
    private static final String[] NAMES = {"竖屏 · 清爽月历", "竖屏 · 大字日期", "横屏 · 月历天气", "横屏 · 本周与月历"};
    private CalendarCards() { }

    public static String[] templateNames() { return NAMES.clone(); }
    public static boolean isLandscape(int template) { validateTemplate(template); return template >= 2; }

    /** Landscape artwork is rotated clockwise into the physical 552 x 768 panel frame. */
    public static Bitmap render(int year, int month, int markedDay, String title, int accent, int background,
                                int template, String city, String weather, String temperature, String weatherUpdated) {
        validateTemplate(template);
        CalendarModel model = new CalendarModel(year, month);
        if (markedDay < 0 || markedDay > model.days) throw new IllegalArgumentException("标记日期须在当月内；0 表示不标记");
        int bg = paletteColor(background), color = paletteColor(accent), ink = ink(bg);
        if (color == bg) color = ink;
        Bitmap logical = Bitmap.createBitmap(template >= 2 ? 768 : 552, template >= 2 ? 552 : 768, Bitmap.Config.ARGB_8888);
        logical.eraseColor(bg); Canvas c = new Canvas(logical);
        Weather info = new Weather(city, weather, temperature, weatherUpdated);
        String heading = text(title, 100); if (heading.isEmpty()) heading = "把日子过成喜欢的样子";
        if (template == 0) portraitMonth(c, model, markedDay, heading, color, bg, ink, info);
        else if (template == 1) portraitDate(c, model, markedDay, heading, color, bg, ink, info);
        else if (template == 2) landscapeWeather(c, model, markedDay, heading, color, bg, ink, info);
        else landscapeWeek(c, model, markedDay, heading, color, bg, ink, info);
        if (template < 2) return logical;
        Matrix rotation = new Matrix(); rotation.setRotate(90);
        Bitmap physical = Bitmap.createBitmap(logical, 0, 0, logical.getWidth(), logical.getHeight(), rotation, false);
        logical.recycle(); return physical;
    }

    private static void portraitMonth(Canvas c, CalendarModel m, int day, String title, int accent, int bg, int ink, Weather weather) {
        rect(c, 28, 28, 36, 155, YELLOW);
        line(c, title, 56, 62, 438, 30, 26, ink, true, false);
        line(c, String.format(Locale.ROOT, "%02d", m.month1), 48, 165, 194, 112, 70, ink, true, false);
        line(c, Integer.toString(m.year), 268, 118, 240, 42, 32, ink, true, false);
        line(c, MONTHS[m.month1 - 1], 270, 158, 238, 26, 22, ink, true, false);
        rect(c, 28, 187, 524, 195, YELLOW);
        grid(c, m, day, 28, 231, 496, 48, 32, accent, bg, ink, false);
        weatherStrip(c, weather, 28, 562, 496, 178);
    }

    private static void portraitDate(Canvas c, CalendarModel m, int day, String title, int accent, int bg, int ink, Weather weather) {
        rect(c, 0, 0, 552, 191, YELLOW);
        line(c, title, 32, 44, 488, 30, 26, BLACK, true, false);
        line(c, day > 0 ? String.format(Locale.ROOT, "%02d", day) : String.format(Locale.ROOT, "%02d", m.month1),
            27, 164, 211, 124, 80, BLACK, true, false);
        line(c, m.year + " 年 " + m.month1 + " 月", 253, 103, 267, 32, 26, BLACK, true, false);
        line(c, day > 0 ? "星期" + WEEK[m.cellForDay(day) % 7] : "整月一览", 255, 151, 265, 34, 28, BLACK, true, false);
        grid(c, m, day, 28, 232, 496, 47, 32, accent, bg, ink, false);
        weatherStrip(c, weather, 28, 554, 496, 186);
    }

    private static void landscapeWeather(Canvas c, CalendarModel m, int day, String title, int accent, int bg, int ink, Weather weather) {
        rect(c, 0, 0, 768, 114, YELLOW);
        line(c, title, 28, 40, 712, 30, 26, BLACK, true, false);
        line(c, m.year + " 年 " + m.month1 + " 月", 26, 94, 454, 44, 32, BLACK, true, false);
        line(c, MONTHS[m.month1 - 1], 492, 93, 248, 26, 22, BLACK, true, false);
        grid(c, m, day, 24, 164, 444, 50, 32, accent, bg, ink, false);
        rect(c, 24, 507, 468, 513, YELLOW);
        rect(c, 492, 138, 744, 522, YELLOW);
        line(c, weather.city, 618, 178, 212, 32, 26, BLACK, true, true);
        line(c, weather.caption.source, 618, 210, 212, 22, 22, BLACK, true, true);
        weatherIcon(c, weather.condition, 618, 267, 32, YELLOW);
        line(c, weather.temperature, 618, 349, 216, 48, 30, BLACK, true, true);
        line(c, weather.condition, 618, 390, 216, 30, 24, BLACK, true, true);
        line(c, weather.caption.dateLine, 618, 445, 216, 22, 22, BLACK, true, true);
        line(c, weather.caption.timeLine, 618, 482, 216, 22, 22, BLACK, true, true);
    }

    private static void landscapeWeek(Canvas c, CalendarModel m, int day, String title, int accent, int bg, int ink, Weather weather) {
        rect(c, 24, 24, 304, 528, YELLOW);
        line(c, day > 0 ? "标记日期" : "本月", 44, 62, 240, 26, 24, BLACK, true, false);
        line(c, day > 0 ? String.format(Locale.ROOT, "%02d", day) : String.format(Locale.ROOT, "%02d", m.month1),
            39, 184, 247, 124, 90, BLACK, true, false);
        line(c, m.year + " 年 " + m.month1 + " 月", 44, 224, 240, 28, 24, BLACK, true, false);
        line(c, day > 0 ? "星期" + WEEK[m.cellForDay(day) % 7] : "整月一览", 44, 264, 240, 30, 26, BLACK, true, false);
        rect(c, 44, 284, 284, 286, BLACK);
        line(c, weather.city, 44, 320, 240, 28, 26, BLACK, true, false);
        line(c, weather.caption.source, 44, 350, 240, 22, 22, BLACK, true, false);
        line(c, weather.temperature, 44, 399, 173, 40, 28, BLACK, true, false);
        weatherIcon(c, weather.condition, 265, 382, 21, YELLOW);
        line(c, weather.condition, 44, 438, 240, 26, 24, BLACK, true, false);
        line(c, weather.caption.dateLine, 44, 477, 240, 22, 22, BLACK, true, false);
        line(c, weather.caption.timeLine, 44, 508, 240, 22, 22, BLACK, true, false);
        line(c, title, 330, 59, 410, 30, 26, ink, true, false);
        line(c, m.year + " / " + String.format(Locale.ROOT, "%02d", m.month1), 330, 119, 410, 44, 32, ink, true, false);
        grid(c, m, day, 328, 173, 416, 48, 30, accent, bg, ink, true);
        String week;
        if (day > 0) {
            LocalDate selected = LocalDate.of(m.year, m.month1, day);
            LocalDate start = selected.minusDays(selected.getDayOfWeek().getValue() - 1), end = start.plusDays(6);
            week = "本周 " + start.getMonthValue() + "/" + start.getDayOfMonth() + " — " + end.getMonthValue() + "/" + end.getDayOfMonth();
        } else week = "选择标记日，突出所在周";
        line(c, week, 536, 512, 406, 24, 22, ink, true, true);
    }

    /** top is the weekday baseline; fixed six rows preserve every possible Gregorian month. */
    private static void grid(Canvas c, CalendarModel m, int day, float left, float top, float width,
                             float rowHeight, int fontSize, int accent, int bg, int ink, boolean weekHighlight) {
        float cell = width / 7f;
        for (int col = 0; col < 7; col++)
            line(c, WEEK[col], left + cell * (col + .5f), top, (int)cell - 4, 26, 26, col >= 5 ? accent : ink, true, true);
        int markedRow = day > 0 ? m.cellForDay(day) / 7 : -1;
        for (int row = 0; row < 6; row++) {
            float baseline = top + 50 + row * rowHeight;
            boolean band = weekHighlight && row == markedRow;
            if (band) rect(c, left, baseline - fontSize - 2, left + width, baseline + 6, YELLOW);
            for (int col = 0; col < 7; col++) {
                int value = m.dayAt(row, col); if (value == 0) continue;
                float x = left + cell * (col + .5f);
                int foreground = band ? BLACK : (col >= 5 ? accent : ink);
                if (value == day) {
                    int fill = accent == YELLOW ? RED : accent;
                    Paint p = paint(fill, 1, false);
                    float radius = Math.min(cell * .40f, rowHeight * .44f);
                    c.drawRoundRect(new RectF(Math.round(x - radius), Math.round(baseline - fontSize - 2), Math.round(x + radius), Math.round(baseline + 6)), 7, 7, p);
                    foreground = ink(fill);
                }
                line(c, Integer.toString(value), x, baseline, (int)cell - 8, fontSize, fontSize, foreground, true, true);
            }
        }
    }

    private static final class Weather {
        final String city, condition, temperature; final WeatherCaption caption;
        Weather(String city, String condition, String temperature, String updated) {
            this.city = fallback(city, "未填写城市");
            this.condition = fallback(condition, "尚未填写天气");
            this.temperature = fallback(temperature, "-- °C");
            this.caption = WeatherCaption.from(updated);
        }
    }
    private static void weatherStrip(Canvas c, Weather w, float x, float y, float width, float height) {
        rect(c, x, y, x + width, y + height, YELLOW);
        int sourceWidth = 168;
        line(c, w.city, x + 20, y + 36, (int)width - sourceWidth - 50, 30, 26, BLACK, true, false);
        line(c, w.caption.source, x + width - 20, y + 35, sourceWidth, 22, 22, BLACK, true, false, Paint.Align.RIGHT);
        weatherIcon(c, w.condition, x + 43, y + 79, 22, YELLOW);
        line(c, w.temperature, x + 82, y + 90, 198, 40, 32, BLACK, true, false);
        line(c, w.condition, x + 293, y + 87, (int)width - 313, 28, 24, BLACK, true, false);
        line(c, w.caption.dateLine, x + 20, y + 132, (int)width - 40, 24, 22, BLACK, true, false);
        line(c, w.caption.timeLine, x + 20, y + 162, (int)width - 40, 24, 22, BLACK, true, false);
    }

    /** Simple vector icons avoid color-font emoji and never infer a condition from an empty field. */
    private static void weatherIcon(Canvas c, String condition, float x, float y, float r, int bg) {
        String value = condition.toLowerCase(Locale.ROOT);
        boolean rain = value.contains("雨") || value.contains("rain");
        boolean snow = value.contains("雪") || value.contains("snow");
        boolean cloud = rain || snow || value.contains("云") || value.contains("阴") || value.contains("cloud") || value.contains("overcast");
        Paint p = paint(BLACK, 1, false);
        if (cloud) {
            p.setColor(WHITE); c.drawOval(new RectF(x - r, y - r * .15f, x + r, y + r * .6f), p);
            c.drawCircle(x - r * .38f, y - r * .16f, r * .47f, p);
            c.drawCircle(x + r * .24f, y - r * .35f, r * .61f, p);
            p.setColor(BLACK); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            Path shape = new Path(); shape.moveTo(x - r * .8f, y + r * .59f);
            shape.cubicTo(x - r * 1.3f, y + r * .4f, x - r * 1.1f, y - r * .23f, x - r * .68f, y - r * .14f);
            shape.cubicTo(x - r * .64f, y - r * .8f, x - r * .05f, y - r * .84f, x + r * .05f, y - r * .67f);
            shape.cubicTo(x + r * .64f, y - r * 1.08f, x + r * .98f, y - r * .34f, x + r * .76f, y - r * .02f);
            shape.cubicTo(x + r * 1.25f, y + r * .07f, x + r * 1.22f, y + r * .59f, x + r * .78f, y + r * .59f);
            shape.close(); c.drawPath(shape, p);
            if (rain || snow) for (int i = -1; i <= 1; i++) {
                float dx = x + i * r * .6f, yy = y + r * .88f;
                if (rain) c.drawLine(dx, yy, dx - r * .13f, yy + r * .29f, p);
                else {
                    c.drawLine(dx - r * .12f, yy, dx + r * .12f, yy + r * .22f, p);
                    c.drawLine(dx + r * .12f, yy, dx - r * .12f, yy + r * .22f, p);
                }
            }
        } else if (value.contains("晴") || value.contains("sun") || value.contains("clear")) {
            p.setColor(bg == YELLOW ? WHITE : YELLOW); c.drawCircle(x, y, r * .55f, p);
            p.setColor(BLACK); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); c.drawCircle(x, y, r * .55f, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(x + (float)Math.cos(a) * r * .77f, y + (float)Math.sin(a) * r * .77f,
                    x + (float)Math.cos(a) * r * 1.04f, y + (float)Math.sin(a) * r * 1.04f, p);
            }
        } else {
            p.setColor(WHITE); c.drawCircle(x, y + r * .5f, r * .4f, p);
            p.setColor(BLACK); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
            c.drawRoundRect(new RectF(x - r * .16f, y - r * .8f, x + r * .16f, y + r * .55f), 5, 5, p);
            c.drawCircle(x, y + r * .5f, r * .4f, p);
            c.drawLine(x, y - r * .45f, x, y + r * .6f, p);
        }
    }

    private static void validateTemplate(int template) {
        if (template < 0 || template >= NAMES.length) throw new IllegalArgumentException("请选择有效的日历模板");
    }
    private static int paletteColor(int color) {
        int best = BLACK; long distance = Long.MAX_VALUE;
        for (int candidate : new int[] {BLACK, WHITE, YELLOW, RED}) {
            long r = Color.red(color) - Color.red(candidate), g = Color.green(color) - Color.green(candidate), b = Color.blue(color) - Color.blue(candidate);
            long d = r * r + g * g + b * b; if (d < distance) { distance = d; best = candidate; }
        }
        return best;
    }
    private static int ink(int color) {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 > 145000 ? BLACK : WHITE;
    }
    private static Paint paint(int color, float size, boolean bold) {
        Paint p = new Paint(); p.setAntiAlias(false); p.setColor(color); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setSubpixelText(false);p.setLinearText(false);p.setHinting(Paint.HINTING_ON);return p;
    }
    private static void rect(Canvas c, float l, float t, float r, float b, int color) { c.drawRect(l, t, r, b, paint(color, 1, false)); }
    private static String text(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        int n = clean.codePointCount(0, clean.length());
        return n > max ? clean.substring(0, clean.offsetByCodePoints(0, max - 1)) + "…" : clean;
    }
    private static String fallback(String value, String fallback) { String clean = text(value, 100); return clean.isEmpty() ? fallback : clean; }
    private static void line(Canvas c, String value, float x, float baseline, int width, int size, int min, int color, boolean bold, boolean centered) {
        line(c, value, x, baseline, width, size, min, color, bold, centered, centered ? Paint.Align.CENTER : Paint.Align.LEFT);
    }
    private static void line(Canvas c, String value, float x, float baseline, int width, int size, int min, int color, boolean bold, boolean centered, Paint.Align align) {
        int floor = Math.max(22, min);
        TextPaint p = new TextPaint(paint(color, Math.max(size, floor), bold)); p.setTextAlign(align);
        String content = text(value, 120);
        while (p.getTextSize() > floor && p.measureText(content) > width) p.setTextSize(p.getTextSize() - 1);
        CharSequence display = TextUtils.ellipsize(content, p, width, TextUtils.TruncateAt.END);
        c.drawText(display, 0, display.length(), Math.round(x), Math.round(baseline), p);
    }
}

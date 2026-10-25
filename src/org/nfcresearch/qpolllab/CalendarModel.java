package org.nfcresearch.qpolllab;

import java.time.YearMonth;

/** Gregorian month with a Monday-first 6-by-7 grid; independent of Android. */
public final class CalendarModel {
    public final int year, month1, days, firstWeekday;

    public CalendarModel(int year, int month1) {
        if (year < 1 || year > 9999 || month1 < 1 || month1 > 12)
            throw new IllegalArgumentException("年份须为 1—9999，月份须为 1—12");
        YearMonth month = YearMonth.of(year, month1);
        this.year = year;
        this.month1 = month1;
        days = month.lengthOfMonth();
        firstWeekday = month.atDay(1).getDayOfWeek().getValue() - 1;
    }

    /** Returns 1..31, or zero for a blank cell. */
    public int dayAt(int row, int column) {
        if (row < 0 || row >= 6 || column < 0 || column >= 7)
            throw new IllegalArgumentException("日历格坐标超出范围");
        int day = row * 7 + column - firstWeekday + 1;
        return day >= 1 && day <= days ? day : 0;
    }

    /** Returns a zero-based grid cell; rejects dates outside the month. */
    public int cellForDay(int day) {
        if (day < 1 || day > days) throw new IllegalArgumentException("日期超出当月范围");
        return firstWeekday + day - 1;
    }
}

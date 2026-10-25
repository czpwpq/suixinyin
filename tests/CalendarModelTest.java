package org.nfcresearch.qpolllab;

import java.time.LocalDate;

public final class CalendarModelTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void invalid(int year, int month) {
        try { new CalendarModel(year, month); throw new AssertionError("Invalid month accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        check(new CalendarModel(1900, 2).days == 28, "Gregorian century leap rule");
        check(new CalendarModel(2000, 2).days == 29, "400-year leap rule");
        check(new CalendarModel(2024, 2).days == 29, "Leap day");
        check(new CalendarModel(2026, 10).firstWeekday == 3, "October starts on Thursday");
        check(new CalendarModel(2026, 2).firstWeekday == 6, "Sunday must be final column");
        check(new CalendarModel(2020, 8).dayAt(5, 0) == 31, "Sixth row must include August 31");
        invalid(0, 1); invalid(10000, 1); invalid(2026, 0); invalid(2026, 13);
        int dates = 0;
        for (int year = 1900; year <= 2200; year++) for (int month = 1; month <= 12; month++) {
            CalendarModel model = new CalendarModel(year, month);
            int previous = 0, count = 0;
            for (int row = 0; row < 6; row++) for (int col = 0; col < 7; col++) {
                int day = model.dayAt(row, col);
                if (day == 0) continue;
                check(day == previous + 1, "Missing/repeated day"); previous = day; count++; dates++;
                check(model.cellForDay(day) == row * 7 + col, "Day/grid mapping differs");
                check(LocalDate.of(year, month, day).getDayOfWeek().getValue() - 1 == col, "Wrong weekday");
            }
            check(count == model.days, "Month length mismatch");
            try { model.cellForDay(0); throw new AssertionError("Day zero accepted"); }
            catch (IllegalArgumentException expected) { }
            try { model.cellForDay(model.days + 1); throw new AssertionError("Past-end day accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("CalendarModelTest: PASS (" + dates + " dates, leap rules, Monday-first grid, sixth row, invalid inputs)");
    }
}

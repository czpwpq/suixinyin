package org.nfcresearch.qpolllab;

public final class WeatherCaptionTest {
    public static void main(String[] args){
        String record="2017-02-03 04:05 (Asia/Shanghai) · Open-Meteo";
        WeatherCaption caption=WeatherCaption.from(record);
        check(caption.source.equals("Open-Meteo"),"Attribution lost");
        check(caption.dateLine.equals("记录 2017-02-03"),"Saved date was replaced");
        check(caption.timeLine.equals("时间 04:05 · 当地"),"Saved local minute was replaced");
        check(!caption.dateLine.contains("Open-Meteo")&&!caption.timeLine.contains("Open-Meteo"),"Repeated attribution");
        check(!caption.timeLine.contains("Asia/Shanghai"),"Long timezone remains in narrow artwork caption");
        check(record.equals("2017-02-03 04:05 (Asia/Shanghai) · Open-Meteo"),"Caller record changed");
        caption=WeatherCaption.from("2000-02-29T23:59:47+09:00 · open-meteo · Open-Meteo");
        check(caption.dateLine.equals("记录 2000-02-29")&&caption.timeLine.equals("时间 23:59 · 当地"),"ISO timestamp or leap day changed");
        caption=WeatherCaption.from("2026-10-24 15:15");
        check(caption.source.equals("手动天气")&&caption.timeLine.equals("时间 15:15"),"Manual record incorrectly described as online");
        for(String invalid:new String[]{"1900-02-29 20:30","2026-13-01 20:30","2026-01-01 24:00","2026-01-01 12:60","昨晚记录"}){
            caption=WeatherCaption.from(invalid);check(caption.dateLine.equals("记录时间")&&caption.timeLine.equals(invalid),"Unknown record fabricated a date: "+invalid);
        }
        for(String empty:new String[]{null,"","手动天气","未记录时间","Open-Meteo"}){
            caption=WeatherCaption.from(empty);check(caption.dateLine.equals("未记录时间")&&caption.timeLine.isEmpty(),"Missing record fabricated a clock value");
        }
        StringBuilder longRecord=new StringBuilder();for(int i=0;i<2000;i++)longRecord.append("文字");
        check(WeatherCaption.from(longRecord.toString()).timeLine.length()<=400,"Unbounded fallback caption");
        System.out.println("WeatherCaptionTest PASS: saved timestamp, local minute, ISO/leap day, single attribution, manual/unknown/invalid records, bounded text");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

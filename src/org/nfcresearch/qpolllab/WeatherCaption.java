package org.nfcresearch.qpolllab;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compact artwork captions derived only from the saved weather record, never the clock. */
public final class WeatherCaption {
    private static final Pattern STAMP=Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2})(?::\\d{2}(?:\\.\\d+)?)?");
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public final String source,dateLine,timeLine;
    private WeatherCaption(String source,String dateLine,String timeLine){this.source=source;this.dateLine=dateLine;this.timeLine=timeLine;}
    public static WeatherCaption from(String record){
        String raw=record==null?"":record.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim();
        if(raw.length()>400)raw=raw.substring(0,400);
        boolean online=raw.toLowerCase(Locale.ROOT).contains("open-meteo");
        String source=online?"Open-Meteo":"手动天气";
        String content=raw.replaceAll("(?i)\\s*[·|;]?\\s*Open-Meteo","").trim();
        Matcher stamp=STAMP.matcher(content);
        if(stamp.find())try{
            LocalDateTime.parse(stamp.group(1)+" "+stamp.group(2),FORMAT);
            return new WeatherCaption(source,"记录 "+stamp.group(1),"时间 "+stamp.group(2)+(online?" · 当地":""));
        }catch(DateTimeParseException invalid){/* Preserve unfamiliar/invalid data as text below. */}
        if(content.isEmpty()||content.equals("手动天气")||content.equals("未记录时间"))return new WeatherCaption(source,"未记录时间","");
        return new WeatherCaption(source,"记录时间",content);
    }
}

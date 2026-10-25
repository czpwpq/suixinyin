package org.nfcresearch.qpolllab;

public final class WeatherCodeTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        int[] known={0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99};
        for(int code:known){String text=WeatherCode.describe(code);check(text!=null&&!text.isEmpty()&&!text.startsWith("未知"),"Missing WMO code "+code);}
        check("晴".equals(WeatherCode.describe(0)),"Clear sky");
        check("多云".equals(WeatherCode.describe(2)),"Partly cloudy");
        check(WeatherCode.describe(48).contains("雾"),"Rime fog");
        check(WeatherCode.describe(57).contains("冻"),"Freezing drizzle");
        check(WeatherCode.describe(63).contains("雨"),"Rain");
        check(WeatherCode.describe(67).contains("冻"),"Freezing rain");
        check(WeatherCode.describe(75).contains("雪"),"Snowfall");
        check(WeatherCode.describe(86).contains("阵雪"),"Snow showers");
        check(WeatherCode.describe(95).contains("雷"),"Thunderstorm");
        check(WeatherCode.describe(99).contains("冰雹"),"Thunderstorm hail");
        for(int code:new int[]{-1,4,44,50,60,70,90,100,Integer.MAX_VALUE,Integer.MIN_VALUE})
            check(("未知("+code+")").equals(WeatherCode.describe(code)),"Unknown code must remain explicit: "+code);
        System.out.println("WeatherCodeTest: PASS (28 WMO codes, weather families, explicit unknown values)");
    }
}

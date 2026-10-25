package org.nfcresearch.qpolllab;

/** WMO weather interpretation codes used by Open-Meteo's weather_code field.
 * https://open-meteo.com/en/docs#weather_variable_documentation
 */
public final class WeatherCode {
    private WeatherCode() { }
    public static String describe(int code) {
        switch(code) {
            case 0:return "晴";
            case 1:return "大部晴朗";
            case 2:return "多云";
            case 3:return "阴";
            case 45:return "雾";
            case 48:return "雾凇";
            case 51:return "小毛毛雨";
            case 53:return "中毛毛雨";
            case 55:return "大毛毛雨";
            case 56:return "轻度冻毛毛雨";
            case 57:return "强冻毛毛雨";
            case 61:return "小雨";
            case 63:return "中雨";
            case 65:return "大雨";
            case 66:return "小冻雨";
            case 67:return "大冻雨";
            case 71:return "小雪";
            case 73:return "中雪";
            case 75:return "大雪";
            case 77:return "雪粒";
            case 80:return "小阵雨";
            case 81:return "阵雨";
            case 82:return "强阵雨";
            case 85:return "小阵雪";
            case 86:return "强阵雪";
            case 95:return "雷阵雨";
            case 96:return "雷雨伴小冰雹";
            case 99:return "雷雨伴大冰雹";
            default:return "未知("+code+")";
        }
    }
}

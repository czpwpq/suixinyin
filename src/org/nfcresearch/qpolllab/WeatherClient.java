package org.nfcresearch.qpolllab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Optional, synchronous HTTPS weather queries. The UI must call this on a worker thread.
 * City names are manually supplied: no location permission, GPS, or API key is used.
 * Display data source attribution as Open-Meteo (https://open-meteo.com/).
 * API references: https://open-meteo.com/en/docs and
 * https://open-meteo.com/en/docs/geocoding-api . Weather is a model value at the API's
 * current.time, not a claim that a station or this app measured the temperature now.
 */
public final class WeatherClient {
    private static final int MAX_RESPONSE_BYTES=128*1024;
    private WeatherClient() { }

    public static final class Place {
        public final String name,region,country,timezone;
        public final double lat,lon;
        public Place(String name,String region,String country,double lat,double lon,String timezone) {
            if(name==null||name.trim().isEmpty()||!Double.isFinite(lat)||!Double.isFinite(lon)||
               lat < -90||lat > 90||lon < -180||lon > 180)
                throw new IllegalArgumentException("城市名称或经纬度无效");
            this.name=name.trim();this.region=clean(region);this.country=clean(country);
            this.lat=lat;this.lon=lon;this.timezone=clean(timezone);
        }
        public String label() {
            String label=name;
            if(!region.isEmpty()&&!region.equals(name))label+=" · "+region;
            if(!country.isEmpty()&&!country.equals(name)&&!country.equals(region))label+=" · "+country;
            return label;
        }
    }
    public static final class Snapshot {
        public final String city,condition,temperature,updated,source;
        public Snapshot(String city,String condition,String temperature,String updated,String source) {
            this.city=city;this.condition=condition;this.temperature=temperature;this.updated=updated;this.source=source;
        }
    }

    /** Empty list is a valid no-match result; malformed or missing coordinates are errors. */
    public static List<Place> search(String query) throws IOException {
        if(query==null||query.trim().isEmpty()||query.length()>100)
            throw new IllegalArgumentException("请输入 1–100 字的城市名称");
        String endpoint="https://geocoding-api.open-meteo.com/v1/search?name="+
            URLEncoder.encode(query.trim(),"UTF-8")+"&count=5&language=zh&format=json";
        JSONObject json=request(endpoint);
        ArrayList<Place> result=new ArrayList<>();
        try {
            if(!json.has("results"))return result;
            JSONArray values=json.getJSONArray("results");
            for(int i=0;i<Math.min(5,values.length());i++) {
                JSONObject value=values.getJSONObject(i);
                result.add(new Place(requiredText(value,"name"),optionalText(value,"admin1"),
                    optionalText(value,"country"),requiredNumber(value,"latitude"),
                    requiredNumber(value,"longitude"),optionalText(value,"timezone")));
            }
        } catch(JSONException|IllegalArgumentException invalid) {
            throw new IOException("城市服务返回的数据不完整，请重试");
        }
        return result;
    }

    /** Temperature is Celsius. updated contains the API's local timestamp and timezone. */
    public static Snapshot fetch(Place place) throws IOException {
        if(place==null)throw new IllegalArgumentException("请先选择城市");
        String endpoint="https://api.open-meteo.com/v1/forecast?latitude="+Double.toString(place.lat)+
            "&longitude="+Double.toString(place.lon)+
            "&current=temperature_2m,weather_code&temperature_unit=celsius&timezone=auto&forecast_days=1";
        JSONObject json=request(endpoint);
        try {
            JSONObject current=json.getJSONObject("current");
            double temperature=requiredNumber(current,"temperature_2m");
            double code=requiredNumber(current,"weather_code");
            if(code!=Math.rint(code)||code<Integer.MIN_VALUE||code>Integer.MAX_VALUE)
                throw new IllegalArgumentException("天气码无效");
            String time=requiredText(current,"time"),zone=requiredText(json,"timezone");
            LocalDateTime.parse(time);ZoneId.of(zone);
            return new Snapshot(place.name,WeatherCode.describe((int)code),
                String.format(Locale.ROOT,"%.1f°C",temperature),time.replace('T',' ')+" ("+zone+")","Open-Meteo");
        } catch(JSONException|IllegalArgumentException|java.time.DateTimeException invalid) {
            throw new IOException("天气服务返回的数据不完整，请重试");
        }
    }

    private static JSONObject request(String endpoint) throws IOException {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(endpoint).openConnection();
            connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Accept-Encoding","identity");
            connection.setRequestProperty("User-Agent","QPollLab-Weather/1.0");
            int status=connection.getResponseCode();
            if(status!=HttpURLConnection.HTTP_OK)throw new IOException("天气服务返回 HTTP "+status+"，请稍后重试");
            if(connection.getContentLengthLong()>MAX_RESPONSE_BYTES)throw new IOException("天气服务返回的数据过大");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count,total=0;
                while((count=input.read(buffer))!=-1) {
                    total+=count;
                    if(total>MAX_RESPONSE_BYTES)throw new IOException("天气服务返回的数据过大");
                    output.write(buffer,0,count);
                }
                JSONObject body=new JSONObject(new String(output.toByteArray(),"UTF-8"));
                if(body.optBoolean("error",false))throw new IOException("天气服务暂时无法提供数据，请稍后重试");
                return body;
            }
        } catch(JSONException invalid) {
            throw new IOException("天气服务返回了无法识别的数据，请重试");
        } finally {
            if(connection!=null)connection.disconnect();
        }
    }
    private static double requiredNumber(JSONObject object,String name) throws JSONException {
        Object value=object.get(name);
        if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue()))
            throw new IllegalArgumentException("缺少数值");
        return ((Number)value).doubleValue();
    }
    private static String requiredText(JSONObject object,String name) throws JSONException {
        Object value=object.get(name);
        if(!(value instanceof String)||((String)value).trim().isEmpty())throw new IllegalArgumentException("缺少文本");
        return ((String)value).trim();
    }
    private static String optionalText(JSONObject object,String name) throws JSONException {
        return !object.has(name)||object.isNull(name)?"":requiredText(object,name);
    }
    private static String clean(String value){return value==null?"":value.trim();}
}

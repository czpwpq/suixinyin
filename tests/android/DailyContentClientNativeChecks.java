package org.nfcresearch.qpolllab;
import java.io.IOException;
import java.time.LocalDate;
import org.json.*;

/** Offline source-shape checks. Uses no sockets and is executed only by device instrumentation. */
public final class DailyContentClientNativeChecks {
    public static void run()throws Exception{
        LocalDate day=LocalDate.of(2026,10,24);DailyContent.Scene scene=new DailyContent.Scene(DailyContent.HISTORY);
        JSONObject history=new JSONObject("{\"code\":200,\"data\":{\"month\":10,\"day\":24,\"items\":[{\"title\":\"人物出生\",\"year\":\"1800\",\"event_type\":\"birth\"},{\"title\":\"<b>重要事件</b>\",\"year\":\"1900\",\"event_type\":\"event\",\"link\":\"http://example.com/a\"}]}}");
        DailyContentClient.parseHistory(scene,day,history);check(scene.entries.size()==2&&scene.entries.get(0).title.equals("重要事件"),"Events first / HTML clean failed");check(scene.entries.get(0).url.isEmpty(),"Insecure source link retained");
        expect(()->DailyContentClient.parseHistory(new DailyContent.Scene(1),day.withDayOfMonth(23),history));
        scene=new DailyContent.Scene(DailyContent.BRIEF);DailyContentClient.parseTech(scene,new JSONObject("{\"code\":200,\"data\":[{\"title\":\"科技榜条目\",\"link\":\"https://example.com/article\"}]}"));check(scene.entries.size()==1&&scene.entries.get(0).published.isEmpty(),"Tech rank fabricated publication date");
        JSONObject ai=new JSONObject("{\"code\":200,\"data\":{\"date\":\"2026-10-24\",\"news\":[{\"title\":\"当天AI条目\",\"detail\":\"来源摘要\",\"date\":\"2026-10-24\"},{\"title\":\"旧条目\",\"date\":\"2026-10-23\"}]}}");
        DailyContentClient.parseAi(scene,day,ai);check(scene.entries.size()==2&&scene.entries.get(1).body.equals("来源摘要"),"AI source date filtering / summary lost");expect(()->DailyContentClient.parseAi(new DailyContent.Scene(2),day.minusDays(1),ai));
        DailyContent.Scene empty=new DailyContent.Scene(2);DailyContentClient.parseAi(empty,day,new JSONObject("{\"data\":{\"date\":\"2026-10-24\",\"news\":[]}}"));check(empty.entries.isEmpty(),"Empty AI response fabricated stories");
        scene=new DailyContent.Scene(DailyContent.DAILY);DailyContentClient.parseQuote(scene,new JSONObject("{\"id\":1,\"hitokoto\":\"这是一条离线测试句子。\",\"from_who\":null}"));check(scene.entries.get(0).byline.startsWith("未署名"),"Missing quote author fabricated");
        JSONArray characters=new JSONArray();characters.put(character(1,10,24,false));characters.put(character(2,10,23,false));characters.put(character(3,10,24,true));JSONObject response=new JSONObject().put("data",new JSONObject().put("Page",new JSONObject().put("characters",characters)));scene=new DailyContent.Scene(DailyContent.BIRTHDAY);DailyContentClient.parseBirthday(scene,day,response);check(scene.entries.size()==1&&scene.entries.get(0).title.equals("人物1")&&scene.entries.get(0).work.equals("Anime Work"),"Birthday/date/adult/title fallback filter failed");check(scene.entries.get(0).imageUrl.equals("https://example.com/portrait.png"),"HTTPS portrait missing");
        expect(()->DailyContentClient.parseBirthday(new DailyContent.Scene(4),day,new JSONObject("{\"errors\":[{\"message\":\"rate limit\"}]}")));
        for(String bad:new String[]{"http://example.com","https://u:p@example.com","https://localhost","https://127.0.0.1","https://example.com/?token=a","https://example.com/#a"})try{DailyContentClient.normalizeApiBase(bad);throw new AssertionError("Unsafe API root accepted: "+bad);}catch(IllegalArgumentException expected){}
        check(DailyContentClient.normalizeApiBase(" https://example.com/api/ ").equals("https://example.com/api"),"Custom root normalization");
    }
    private static JSONObject character(int id,int month,int day,boolean adult)throws Exception{return new JSONObject().put("id",id).put("name",new JSONObject().put("native","人物"+id)).put("dateOfBirth",new JSONObject().put("month",month).put("day",day)).put("image",new JSONObject().put("large","https://example.com/portrait.png")).put("media",new JSONObject().put("nodes",new JSONArray().put(new JSONObject().put("isAdult",adult).put("title",new JSONObject().put("native",JSONObject.NULL).put("romaji","Anime Work")))));}
    interface Operation {void run()throws Exception;}
    private static void expect(Operation operation)throws Exception{try{operation.run();throw new AssertionError("Invalid source accepted");}catch(IOException expected){}}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private DailyContentClientNativeChecks(){}
}

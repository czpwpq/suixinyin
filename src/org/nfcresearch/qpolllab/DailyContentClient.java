package org.nfcresearch.qpolllab;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Explicit, bounded public-content reads; no background polling, credentials or automatic retries. */
public final class DailyContentClient {
    public static final String DEFAULT_API_BASE="https://60s.viki.moe";
    private static final int JSON_LIMIT=512*1024,IMAGE_LIMIT=2*1024*1024;
    private static final String USER_AGENT="Suixinyin/2.18-test1 (Android NFC e-paper; public read-only content)";
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public static DailyContent.Scene fetch(int kind,int mode,String date) throws Exception{return fetch(kind,mode,date,DEFAULT_API_BASE);}
    public static DailyContent.Scene fetch(int kind,int mode,String date,String apiBase) throws Exception {
        LocalDate day=LocalDate.parse(date);DailyContent.Scene scene=new DailyContent.Scene(kind);scene.mode=mode;scene.date=day.toString();scene.normalize();String base=normalizeApiBase(apiBase);
        if(kind==DailyContent.HISTORY)history(scene,day,base);
        else if(kind==DailyContent.BRIEF)brief(scene,day,base);
        else if(kind==DailyContent.DAILY){if(scene.mode==1)books(scene,day);else quote(scene);}
        else birthday(scene,day);
        scene.fetchedAt=ZonedDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm '(Asia/Shanghai)'",Locale.ROOT));scene.normalize();return scene;
    }
    public static String normalizeApiBase(String requested){
        String value=requested==null||requested.trim().isEmpty()?DEFAULT_API_BASE:requested.trim();while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        URI uri=publicHttps(value);if(uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new IllegalArgumentException("接口根地址不能包含查询参数或片段");return uri.toASCIIString();
    }
    private static void history(DailyContent.Scene scene,LocalDate day,String base) throws Exception {
        parseHistory(scene,day,json(base+"/v2/today-in-history?month="+day.getMonthValue()+"&day="+day.getDayOfMonth(),null));
    }
    static void parseHistory(DailyContent.Scene scene,LocalDate day,JSONObject response) throws Exception {
        JSONObject data=dataObject(response);
        if(data.optInt("month",-1)!=day.getMonthValue()||data.optInt("day",-1)!=day.getDayOfMonth())throw new IOException("历史接口返回的月日与所选日期不一致");
        JSONArray values=data.optJSONArray("items");scene.source="60s API · 百度百科";
        if(values!=null)for(int i=0;i<Math.min(60,values.length());i++){JSONObject item=values.optJSONObject(i);if(item==null)continue;DailyContent.Entry e=new DailyContent.Entry();e.id="history-"+i;e.title=clean(item.optString("title"));e.body=clean(item.optString("description"));e.byline=clean(item.optString("year"))+"年";e.source="百度百科 · 60s API";e.url=safeLink(item.optString("link"));e.published=day.getMonthValue()+"月"+day.getDayOfMonth()+"日";if(!e.title.isEmpty())scene.entries.add(e);}
        // The source separates historical events from births/deaths; put events first without inventing a ranking.
        if(values!=null){List<DailyContent.Entry> ordered=new ArrayList<>();for(DailyContent.Entry e:scene.entries){int index=Integer.parseInt(e.id.substring(8));JSONObject item=values.optJSONObject(index);if(item!=null&&"event".equals(item.optString("event_type")))ordered.add(e);}for(DailyContent.Entry e:scene.entries)if(!ordered.contains(e))ordered.add(e);scene.entries.clear();scene.entries.addAll(ordered);}
        if(scene.entries.isEmpty())scene.warning="来源未提供所选月日的历史事件。";
    }
    private static void brief(DailyContent.Scene scene,LocalDate day,String base) throws Exception {
        StringBuilder warnings=new StringBuilder();int successful=0;
        if(scene.mode!=1)try{if(!day.equals(LocalDate.now(ZONE)))throw new IOException("科技热榜只能获取当前榜单，不能查询历史日期");tech(scene,base);successful++;append(warnings,"科技榜无逐条发布时间，仅标注获取时间。");}catch(Exception error){if(scene.mode==0)throw error;append(warnings,"科技榜未获取："+error.getMessage());}
        if(scene.mode!=0)try{ai(scene,day,base);successful++;}catch(Exception error){if(scene.mode==1)throw error;append(warnings,"AI简报未获取："+error.getMessage());}
        if(successful==0)throw new IOException(warnings.toString());boolean hasAi=scene.entries.stream().anyMatch(e->e.id.startsWith("ai-"));boolean hasTech=scene.entries.stream().anyMatch(e->e.id.startsWith("tech-"));if(scene.mode!=0&&!hasAi)warnings.insert(0,"所选日期来源暂无 AI 资讯，未回填昨天数据。\n");
        if(scene.mode!=0)append(warnings,"AI条目为来源摘要，未调用生成模型进行分析。");
        if(scene.mode==2){List<DailyContent.Entry> tech=new ArrayList<>(),ai=new ArrayList<>();for(DailyContent.Entry e:scene.entries)(e.id.startsWith("ai-")?ai:tech).add(e);scene.entries.clear();for(int i=0;i<Math.max(tech.size(),ai.size());i++){if(i<tech.size())scene.entries.add(tech.get(i));if(i<ai.size())scene.entries.add(ai.get(i));}}
        scene.warning=warnings.toString();scene.source=scene.mode==0?"IT之家 · 60s API":scene.mode==1?"AI工具集 · 60s API":hasAi&&hasTech?"IT之家 / AI工具集 · 60s API":hasAi?"AI工具集 · 60s API":hasTech?"IT之家 · 60s API":"60s API · 无可用内容";
    }
    private static void tech(DailyContent.Scene scene,String base) throws Exception {
        parseTech(scene,json(base+"/v2/it-news/rank",null));
    }
    static void parseTech(DailyContent.Scene scene,JSONObject response) throws Exception {
        checkCode(response);JSONArray values=response.optJSONArray("data");if(values==null)throw new IOException("科技榜格式异常");
        for(int i=0;i<Math.min(24,values.length());i++){JSONObject item=values.optJSONObject(i);if(item==null)continue;DailyContent.Entry e=new DailyContent.Entry();e.id="tech-"+i;e.title=clean(item.optString("title"));e.source="IT之家实时榜";e.byline="科技热榜 · 第"+(i+1)+"条";e.url=safeLink(item.optString("link"));if(!e.title.isEmpty())scene.entries.add(e);}
    }
    private static void ai(DailyContent.Scene scene,LocalDate day,String base) throws Exception {
        parseAi(scene,day,json(base+"/v2/ai-news?date="+day,null));
    }
    static void parseAi(DailyContent.Scene scene,LocalDate day,JSONObject response) throws Exception {
        JSONObject data=dataObject(response);if(!day.toString().equals(data.optString("date")))throw new IOException("AI来源日期不符，未作为所选日期的内容使用");JSONArray values=data.optJSONArray("news");if(values==null)throw new IOException("AI简报格式异常");
        for(int i=0;i<Math.min(24,values.length());i++){JSONObject item=values.optJSONObject(i);if(item==null)continue;String published=item.optString("date",day.toString());if(!published.isEmpty()&&!published.equals(day.toString()))continue;DailyContent.Entry e=new DailyContent.Entry();e.id="ai-"+i;e.title=clean(item.optString("title"));e.body=clean(item.optString("detail"));e.byline="AI简报 · 来源摘要";e.source=clean(item.optString("source","AI工具集"));e.url=safeLink(item.optString("link"));e.published=published;if(!e.title.isEmpty())scene.entries.add(e);}
    }
    private static void quote(DailyContent.Scene scene) throws Exception {
        parseQuote(scene,json("https://v1.hitokoto.cn/?c=d&c=i&c=k",null));
    }
    static void parseQuote(DailyContent.Scene scene,JSONObject data) throws Exception {
        DailyContent.Entry e=new DailyContent.Entry();e.id="quote-"+data.optString("id");e.body=clean(data.optString("hitokoto"));if(e.body.isEmpty())throw new IOException("一言来源未返回句子");e.title="今日一句";e.work=clean(data.optString("from"));e.byline=clean(data.optString("from_who"));if(e.byline.isEmpty())e.byline="未署名（来源未提供）";e.source="一言 Hitokoto";e.url="https://hitokoto.cn/";scene.entries.add(e);scene.source=e.source;scene.warning="随机语录，署名由来源提供；不表示今日首次发表。";
    }
    private static void books(DailyContent.Scene scene,LocalDate day){
        String[][] catalog={
            {"朝花夕拾","鲁迅","从回忆中的生活细节进入散文阅读。适合每天选一篇，慢慢读。","https://zh.wikisource.org/wiki/%E6%9C%9D%E8%8A%B1%E5%A4%95%E6%8B%BE"},
            {"陶渊明集","陶渊明","在诗文里观察田园、日常与个人选择。可以从熟悉的篇目开始，再读不同主题。","https://zh.wikisource.org/wiki/%E9%99%B6%E6%B7%B5%E6%98%8E%E9%9B%86"},
            {"瓦尔登湖","亨利·戴维·梭罗","关于自然观察、简朴生活和独立思考的阅读。推荐留出一段安静时间，边读边记录自己的问题。","https://www.gutenberg.org/ebooks/205"},
            {"呐喊","鲁迅","从短篇故事中观察人物处境与社会关系。读完后不急着下结论，试着找出最让自己停顿的细节。","https://zh.wikisource.org/wiki/%E5%90%B6%E5%96%8A"},
            {"野草","鲁迅","篇幅短，意象浓，适合一篇一篇地读。保留暂时不懂的部分，也是一种阅读方式。","https://zh.wikisource.org/wiki/%E9%87%8E%E8%8D%89"},
            {"人间词话","王国维","把诗词阅读与审美思考放在一起。可以先选一则评论，再回到其中提及的诗词中体会。","https://zh.wikisource.org/wiki/%E4%BA%BA%E9%96%93%E8%A9%9E%E8%A9%B1"},
            {"浮生六记","沈复","从生活片段中感受人与人之间的情意。建议慢读日常的描写，留意平凡事物怎样进入文字。","https://zh.wikisource.org/wiki/%E6%B5%AE%E7%94%9F%E5%85%AD%E8%A8%98"},
            {"傲慢与偏见","简·奥斯汀","跟着人物的相处重新审视第一印象。适合留意对话中的分寸、误解和判断的变化。","https://www.gutenberg.org/ebooks/1342"},
            {"爱丽丝梦游仙境","刘易斯·卡罗尔","给想象力一点不必立刻解释的空间。可以从语言游戏和奇特场景中找回好奇心。","https://www.gutenberg.org/ebooks/11"},
            {"八十天环游地球","儒勒·凡尔纳","一次关于旅程、时间与意外的冒险阅读。试着一边读，一边记录人物如何面对计划之外的变化。","https://www.gutenberg.org/ebooks/103"},
            {"福尔摩斯历险记","阿瑟·柯南·道尔","从短篇推理开始练习细致观察。揭晓之前可以写下自己的猜测，读后比较哪些线索被自己忽略。","https://www.gutenberg.org/ebooks/1661"}
        };
        int start=(int)Math.floorMod(day.toEpochDay(),catalog.length);for(int i=0;i<catalog.length;i++){int index=(start+i)%catalog.length;String[] book=catalog[index];DailyContent.Entry e=new DailyContent.Entry();e.id="book-"+index;e.title=book[0];e.byline=book[1];e.body=book[2];e.source="随心印 · 本地编辑书单";e.url=book[3];scene.entries.add(e);}scene.source="随心印 · 本地编辑书单";scene.warning="本地轮换推荐；推荐理由为编辑文字，不是作者原话，不代表销量或新书排行榜。链接为作品文本/书目信息。";
    }
    private static void birthday(DailyContent.Scene scene,LocalDate day) throws Exception {
        if(!day.equals(LocalDate.now(ZONE)))throw new IOException("生日接口仅提供当前日期，请切回今天后获取");
        String query="query { Page(perPage:40) { characters(isBirthday:true,sort:FAVOURITES_DESC) { id name { full native } image { large } dateOfBirth { month day } siteUrl media(type:ANIME,sort:POPULARITY_DESC,perPage:2) { nodes { title { native romaji } isAdult } } } } }";
        JSONObject payload=new JSONObject().put("query",query);parseBirthday(scene,day,json("https://graphql.anilist.co",payload.toString()));
    }
    static void parseBirthday(DailyContent.Scene scene,LocalDate day,JSONObject response) throws Exception {
        if(response.has("errors"))throw new IOException("AniList返回查询错误或限流，请稍后手动再试");JSONObject data=response.optJSONObject("data"),page=data==null?null:data.optJSONObject("Page");JSONArray values=page==null?null:page.optJSONArray("characters");if(values==null)throw new IOException("生日来源格式异常");
        for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item==null)continue;JSONObject dob=item.optJSONObject("dateOfBirth");if(dob==null||dob.optInt("month")!=day.getMonthValue()||dob.optInt("day")!=day.getDayOfMonth())continue;JSONObject media=item.optJSONObject("media");JSONArray nodes=media==null?null:media.optJSONArray("nodes");if(nodes==null||nodes.length()==0)continue;boolean adult=false;StringBuilder works=new StringBuilder();for(int n=0;n<nodes.length();n++){JSONObject node=nodes.optJSONObject(n);if(node==null)continue;if(node.optBoolean("isAdult",false)){adult=true;break;}JSONObject title=node.optJSONObject("title");String work=title==null?"":clean(title.optString("native"));if(work.isEmpty()&&title!=null)work=clean(title.optString("romaji"));if(!work.isEmpty()){if(works.length()>0)works.append(" / ");works.append(work);}}if(adult||works.length()==0)continue;
            DailyContent.Entry e=new DailyContent.Entry();e.id="anilist-"+item.optString("id");JSONObject name=item.optJSONObject("name"),image=item.optJSONObject("image");e.title=name==null?"":clean(name.optString("native"));if(e.title.isEmpty()&&name!=null)e.title=clean(name.optString("full"));e.work=works.toString();e.byline=day.getMonthValue()+"月"+day.getDayOfMonth()+"日生日";e.source="AniList";e.url=safeLink(item.optString("siteUrl"));e.imageUrl=image==null?"":safeLink(image.optString("large"));e.published=day.toString();if(!e.title.isEmpty())scene.entries.add(e);
        }
        scene.source="AniList · 今日生日角色";scene.warning=scene.entries.isEmpty()?"未查到与所选月日相符且有关联动画作品的角色；名单可能不完整，跨时区时可稍后再试。":"生日按来源返回月日核对，名单以有关联动画的角色为主，作品名保留来源原文；画像需手动获取。";
    }
    public static byte[] portrait(DailyContent.Entry entry) throws Exception {if(entry==null||entry.imageUrl==null||entry.imageUrl.isEmpty())throw new IOException("当前人物没有可用画像链接");return request(entry.imageUrl,null,IMAGE_LIMIT,true);}
    private static JSONObject json(String url,String payload) throws Exception {return new JSONObject(new String(request(url,payload,JSON_LIMIT,false),StandardCharsets.UTF_8));}
    private static JSONObject dataObject(JSONObject response) throws Exception {checkCode(response);JSONObject data=response.optJSONObject("data");if(data==null)throw new IOException("来源返回格式异常");return data;}
    private static void checkCode(JSONObject response) throws IOException {if(response.optInt("code",200)!=200)throw new IOException("内容服务返回错误或限流，请稍后手动再试");}
    private static byte[] request(String endpoint,String payload,int limit,boolean image) throws Exception {
        String current=endpoint;for(int redirects=0;redirects<4;redirects++){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消获取");publicHttps(current);HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("User-Agent",USER_AGENT);c.setRequestProperty("Accept",image?"image/*":"application/json");c.setRequestProperty("Accept-Encoding","identity");
            try{if(payload!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
                int status=c.getResponseCode();if(image&&status>=300&&status<=399){String location=c.getHeaderField("Location");if(location==null)throw new IOException("画像重定向缺少地址");current=new URL(new URL(current),location).toString();continue;}if(status!=200)throw new IOException(status==429?"来源请求过多，请稍后手动再试":"内容服务返回 HTTP "+status);if(c.getContentLengthLong()>limit)throw new IOException("来源内容超出大小限制");if(image){String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))throw new IOException("画像来源未返回图片");}
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消获取");if(out.size()+n>limit)throw new IOException("来源内容超出大小限制");out.write(buffer,0,n);}if(out.size()==0)throw new IOException("来源内容为空");return out.toByteArray();}
            }finally{c.disconnect();}
        }throw new IOException("画像重定向次数过多");
    }
    private static URI publicHttps(String value){try{URI uri=new URI(value);String host=uri.getHost();if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("仅支持不含账号信息的 HTTPS 公网地址");String lower=host.toLowerCase(Locale.ROOT);if(lower.equals("localhost")||lower.endsWith(".localhost")||lower.endsWith(".local")||lower.contains(":")||lower.matches("[0-9.]+"))throw new IllegalArgumentException("请使用 HTTPS 公网域名");return uri;}catch(URISyntaxException invalid){throw new IllegalArgumentException("地址格式无效",invalid);}}
    private static String safeLink(String value){if(value==null||value.isEmpty())return "";try{return publicHttps(value).toASCIIString();}catch(IllegalArgumentException invalid){return "";}}
    private static String clean(String value){if(value==null)return "";String text=value.replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace("&amp;","&").replace("&quot;","\"").trim();return text.equals("null")?"":text;}
    private static void append(StringBuilder builder,String text){if(builder.length()>0)builder.append("\n");builder.append(text);}
    private DailyContentClient(){}
}

package org.nfcresearch.qpolllab;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Bounded local snapshots of public content. A draft does not imply the source is current. */
public final class DailyContent {
    public static final int HISTORY=1,BRIEF=2,DAILY=3,BIRTHDAY=4;
    private static final int MAGIC=0x44415943,VERSION=1,MAX_BYTES=3*1024*1024,MAX_PORTRAIT=2*1024*1024,MAX_ENTRIES=80;
    public static final class Entry {
        public String id="",title="",body="",byline="",source="",url="",imageUrl="",work="",published="";
        public Entry copy(){Entry e=new Entry();e.id=id;e.title=title;e.body=body;e.byline=byline;e.source=source;e.url=url;e.imageUrl=imageUrl;e.work=work;e.published=published;return e;}
        private void normalize(){id=bound(id,100);title=bound(title,300);body=bound(body,4000);byline=bound(byline,200);source=bound(source,160);url=bound(url,2048);imageUrl=bound(imageUrl,2048);work=bound(work,400);published=bound(published,100);}
    }
    public static final class Scene {
        public int kind,mode,selected=0,count=3;
        public String date=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),fetchedAt="",source="",warning="",portraitId="";
        public final List<Entry> entries=new ArrayList<>();
        public byte[] portrait;
        public Scene(int kind){this.kind=kind;normalize();}
        public Scene copy(){Scene s=new Scene(kind);s.mode=mode;s.selected=selected;s.count=count;s.date=date;s.fetchedAt=fetchedAt;s.source=source;s.warning=warning;s.portraitId=portraitId;s.portrait=portrait==null?null:portrait.clone();for(Entry e:entries)if(e!=null)s.entries.add(e.copy());return s;}
        public void normalize(){
            if(kind<HISTORY||kind>BIRTHDAY)throw new IllegalArgumentException("未知每日内容类型");
            mode=Math.max(0,Math.min(kind==BRIEF?2:kind==DAILY?1:0,mode));count=Math.max(1,Math.min(3,count));
            date=bound(date,10);LocalDate.parse(date);fetchedAt=bound(fetchedAt,100);source=bound(source,160);warning=bound(warning,500);portraitId=bound(portraitId,100);
            for(int i=entries.size()-1;i>=0;i--)if(entries.get(i)==null)entries.remove(i);
            while(entries.size()>MAX_ENTRIES)entries.remove(entries.size()-1);for(Entry e:entries)e.normalize();
            selected=Math.max(0,Math.min(Math.max(0,entries.size()-1),selected));
            if(portrait!=null&&(portrait.length>MAX_PORTRAIT||portrait.length==0||portraitId.isEmpty()||entries.isEmpty()||!portraitId.equals(entries.get(selected).id))){portrait=null;portraitId="";}
            if(portrait==null)portraitId="";
        }
        public byte[] encode() throws IOException {
            Scene s=copy();s.normalize();ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(s.kind);out.writeInt(s.mode);out.writeInt(s.selected);out.writeInt(s.count);out.writeUTF(s.date);out.writeUTF(s.fetchedAt);out.writeUTF(s.source);out.writeUTF(s.warning);out.writeInt(s.entries.size());
            for(Entry e:s.entries){out.writeUTF(e.id);out.writeUTF(e.title);out.writeUTF(e.body);out.writeUTF(e.byline);out.writeUTF(e.source);out.writeUTF(e.url);out.writeUTF(e.imageUrl);out.writeUTF(e.work);out.writeUTF(e.published);}
            out.writeUTF(s.portraitId);out.writeInt(s.portrait==null?0:s.portrait.length);if(s.portrait!=null)out.write(s.portrait);out.flush();if(buffer.size()>MAX_BYTES)throw new IOException("每日内容草稿过大");return buffer.toByteArray();
        }
        public static Scene decode(byte[] bytes) throws IOException {
            if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("每日内容草稿无效");DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("未知每日内容版本");
            try{Scene s=new Scene(in.readInt());s.mode=in.readInt();s.selected=in.readInt();s.count=in.readInt();s.date=in.readUTF();s.fetchedAt=in.readUTF();s.source=in.readUTF();s.warning=in.readUTF();int n=in.readInt();if(n<0||n>MAX_ENTRIES)throw new IOException("条目数量异常");
                for(int i=0;i<n;i++){Entry e=new Entry();e.id=in.readUTF();e.title=in.readUTF();e.body=in.readUTF();e.byline=in.readUTF();e.source=in.readUTF();e.url=in.readUTF();e.imageUrl=in.readUTF();e.work=in.readUTF();e.published=in.readUTF();s.entries.add(e);}
                s.portraitId=in.readUTF();int length=in.readInt();if(length<0||length>MAX_PORTRAIT||length>in.available())throw new IOException("画像数据异常");if(length>0){s.portrait=new byte[length];in.readFully(s.portrait);}if(in.available()!=0)throw new IOException("每日内容尾部异常");s.normalize();return s;
            }catch(IllegalArgumentException|java.time.DateTimeException error){throw new IOException("每日内容参数无效",error);}
        }
    }
    private static String bound(String s,int length){if(s==null)return "";if(s.length()<=length)return s;return s.substring(0,length-(Character.isHighSurrogate(s.charAt(length-1))?1:0));}
    private DailyContent(){}
}

package org.nfcresearch.qpolllab;
import java.io.IOException;
import java.util.Arrays;

public final class DailyContentTest {
    public static void main(String[] args)throws Exception{
        for(int kind=1;kind<=4;kind++)for(int mode=0;mode<3;mode++){
            DailyContent.Scene s=new DailyContent.Scene(kind);s.mode=mode;s.date="2026-10-24";s.fetchedAt="2026-10-24 21:05 (Asia/Shanghai)";s.source="来源测试";s.warning="旧快照仅代表原日期";s.count=2;
            for(int i=0;i<3;i++){DailyContent.Entry e=new DailyContent.Entry();e.id="id-"+i;e.title="条目 "+i;e.body="中文与英文\nHello";e.byline="署名";e.source="来源";e.url="https://example.com/item/"+i;e.imageUrl="https://example.com/image/"+i;e.work="作品";e.published="2017-02-03";s.entries.add(e);}
            s.selected=1;s.portraitId="id-1";s.portrait=new byte[]{1,2,3,4};s.normalize();byte[] encoded=s.encode();DailyContent.Scene decoded=DailyContent.Scene.decode(encoded);check(Arrays.equals(encoded,decoded.encode()),"Roundtrip changed state");
            DailyContent.Scene copy=s.copy();copy.entries.get(1).title="changed";copy.portrait[0]=55;check(s.entries.get(1).title.equals("条目 1")&&s.portrait[0]==1,"Copy leaked mutable storage");copy.selected=2;copy.normalize();check(copy.portrait==null&&copy.portraitId.isEmpty(),"Selected character retained another portrait");
            check(s.date.equals("2026-10-24")&&s.entries.get(0).published.equals("2017-02-03"),"Dates were replaced by today");
            invalid(Arrays.copyOf(encoded,encoded.length-1));byte[] trailing=Arrays.copyOf(encoded,encoded.length+1);invalid(trailing);byte[] broken=encoded.clone();broken[0]=0;invalid(broken);
        }
        DailyContent.Scene bounds=new DailyContent.Scene(DailyContent.DAILY);bounds.mode=99;bounds.count=99;bounds.selected=99;for(int i=0;i<100;i++){DailyContent.Entry e=new DailyContent.Entry();e.id="x"+i;bounds.entries.add(e);}bounds.normalize();check(bounds.mode==1&&bounds.count==3&&bounds.entries.size()==80&&bounds.selected==79,"Bounds lost");
        bounds.portraitId=bounds.entries.get(79).id;bounds.portrait=new byte[2*1024*1024+1];bounds.normalize();check(bounds.portrait==null,"Oversized portrait retained");
        DailyContent.Scene unknown=new DailyContent.Scene(1);unknown.date="2026-02-29";try{unknown.encode();throw new AssertionError("Invalid date accepted");}catch(java.time.DateTimeException expected){}
        invalid(new byte[3*1024*1024+1]);try{new DailyContent.Scene(0);throw new AssertionError("Unknown kind accepted");}catch(IllegalArgumentException expected){}
        System.out.println("DailyContentTest PASS: 12 roundtrips, deep copies, portrait identity, original dates, entry/count/image limits, damaged cache rejection");
    }
    private static void invalid(byte[] bytes)throws Exception{try{DailyContent.Scene.decode(bytes);throw new AssertionError("Invalid cache accepted");}catch(IOException expected){}}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

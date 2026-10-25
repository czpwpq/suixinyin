package org.nfcresearch.qpolllab;

import java.io.IOException;
import java.util.Arrays;

public final class CardSceneTest {
    private interface Read {void run()throws IOException;}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void invalid(Read action)throws IOException{try{action.run();throw new AssertionError("Corrupt card accepted");}catch(IOException expected){}}
    private static CardScene specimen(int kind,int template) {
        CardScene scene=new CardScene();scene.kind=kind;scene.year=2000;scene.month=2;scene.day=29;scene.template=template;scene.foreground=2;scene.background=0;scene.fontSize=67;scene.outlineColor=3;scene.bold=false;scene.outline=true;
        scene.title="二月 · 闰年";scene.text="今天也要好好生活\nEnglish / 😀";scene.city="上海";scene.weather="晴转多云";scene.temperature="12–20 °C";scene.weatherUpdated="2000-02-29T11:22 Asia/Shanghai";scene.weatherSource="Open-Meteo";return scene;
    }
    private static void same(CardScene a,CardScene b,String label)throws IOException{check(Arrays.equals(a.encode(),b.encode()),label);}
    public static void main(String[] args)throws Exception {
        check(CardScene.NOTE==1&&CardScene.CALENDAR==2,"Legacy creation kind values changed");
        for(int kind:new int[]{CardScene.NOTE,CardScene.CALENDAR})for(int template=0;template<4;template++)for(int foreground=0;foreground<4;foreground++) {
            CardScene original=specimen(kind,template);original.foreground=foreground;byte[] encoded=original.encode();CardScene decoded=CardScene.decode(encoded);
            same(original,decoded,"Roundtrip dropped a field");check(decoded.isLandscape()==(kind==CardScene.CALENDAR&&template>=2),"Orientation metadata");
            CardScene copy=original.copy();same(original,copy,"Copy dropped a field");copy.text="changed";copy.year=2026;copy.foreground=0;copy.weatherSource="manual";copy.outline=false;
            check(Arrays.equals(original.encode(),encoded),"Copy aliases caller state");
        }
        CardScene bounds=specimen(CardScene.NOTE,0);bounds.year=0;bounds.month=0;bounds.day=88;bounds.template=8;bounds.foreground=-1;bounds.background=99;bounds.outlineColor=-9;bounds.fontSize=999;
        bounds.title=null;bounds.text=null;bounds.city=null;bounds.weather=null;bounds.temperature=null;bounds.weatherUpdated=null;bounds.weatherSource=null;
        byte[] normalizedBytes=bounds.encode();check(bounds.year==0&&bounds.text==null,"Encoding mutated caller fields");CardScene normalized=CardScene.decode(normalizedBytes);
        check(normalized.year==1&&normalized.month==1&&normalized.day==31&&normalized.template==3,"Date/template lower bounds");
        check(normalized.foreground==0&&normalized.background==3&&normalized.outlineColor==0&&normalized.fontSize==120,"Palette/font bounds");
        check(normalized.title.isEmpty()&&normalized.text.isEmpty()&&normalized.city.isEmpty()&&normalized.weather.isEmpty()&&normalized.temperature.isEmpty()&&normalized.weatherUpdated.isEmpty()&&normalized.weatherSource.isEmpty(),"Null strings");
        bounds.year=Integer.MAX_VALUE;bounds.month=99;bounds.day=-1;bounds.template=-1;bounds.fontSize=-999;bounds.normalize();
        check(bounds.year==9999&&bounds.month==12&&bounds.day==0&&bounds.template==0&&bounds.fontSize==16,"Upper date or lower size bounds");
        bounds.year=1900;bounds.month=2;bounds.day=29;bounds.normalize();check(bounds.day==28,"Century leap-day clamp");
        bounds.year=2000;bounds.day=29;bounds.normalize();check(bounds.day==29,"Four-century leap day");
        CardScene lengths=CardScene.calendar();StringBuilder longValue=new StringBuilder();for(int i=0;i<5000;i++)longValue.append('中');String value=longValue.toString();
        lengths.title=value;lengths.text=value;lengths.city=value;lengths.weather=value;lengths.temperature=value;lengths.weatherUpdated=value;lengths.weatherSource=value;lengths.normalize();
        check(lengths.title.length()==100&&lengths.text.length()==4000&&lengths.city.length()==80&&lengths.weather.length()==80&&lengths.temperature.length()==40&&lengths.weatherUpdated.length()==160&&lengths.weatherSource.length()==80,"String limits");
        check(lengths.encode().length<=32768,"Scene can exceed local cache bound");
        lengths.text=new String(new char[3999]).replace('\0','a')+"😀";lengths.normalize();check(lengths.text.length()==3999,"Truncation split a surrogate pair");
        CardScene note=CardScene.note(),calendar=CardScene.calendar();check(note.kind==CardScene.NOTE&&note.foreground==0&&calendar.kind==CardScene.CALENDAR&&calendar.foreground==3,"Factory defaults");note.normalize();calendar.normalize();
        byte[] valid=specimen(CardScene.CALENDAR,2).encode();
        invalid(()->CardScene.decode(null));invalid(()->CardScene.decode(new byte[32769]));
        for(int length=0;length<valid.length;length++){final byte[] truncated=Arrays.copyOf(valid,length);invalid(()->CardScene.decode(truncated));}
        byte[] badMagic=valid.clone();badMagic[0]^=1;invalid(()->CardScene.decode(badMagic));
        byte[] badVersion=valid.clone();badVersion[7]=2;invalid(()->CardScene.decode(badVersion));
        byte[] badKind=valid.clone();badKind[11]=3;invalid(()->CardScene.decode(badKind));
        invalid(()->CardScene.decode(Arrays.copyOf(valid,valid.length+1)));
        CardScene unknown=new CardScene();unknown.kind=3;try{unknown.normalize();throw new AssertionError("Unknown type normalized");}catch(IllegalArgumentException expected){}
        System.out.println("CardScene tests passed: 32 complete roundtrips, independent copies, date/color/text bounds, orientation, corrupt/truncated cache rejection");
    }
}

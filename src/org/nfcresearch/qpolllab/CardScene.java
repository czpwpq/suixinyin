package org.nfcresearch.qpolllab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;

/** Local, versioned editing state for flat four-color cards; color fields are K/W/Y/R indices. */
public final class CardScene {
    public static final int NOTE=1,CALENDAR=2;
    private static final int MAGIC=0x43415244,VERSION=1,MAX_BYTES=32768;
    public int kind=NOTE,year,month,day,template;
    public int foreground=3,background=1,fontSize=44,outlineColor=1;
    public String title="把日子过成喜欢的样子",text="今天也要好好生活。",city="",weather="",temperature="",weatherUpdated="",weatherSource="";
    public boolean bold=true,outline;

    public CardScene(){LocalDate today=LocalDate.now();year=today.getYear();month=today.getMonthValue();day=today.getDayOfMonth();}
    public static CardScene note(){CardScene scene=new CardScene();scene.foreground=0;return scene;}
    public static CardScene calendar(){CardScene scene=new CardScene();scene.kind=CALENDAR;return scene;}
    public CardScene copy(){
        CardScene scene=new CardScene();scene.kind=kind;scene.year=year;scene.month=month;scene.day=day;scene.template=template;
        scene.foreground=foreground;scene.background=background;scene.fontSize=fontSize;scene.outlineColor=outlineColor;
        scene.title=title;scene.text=text;scene.city=city;scene.weather=weather;scene.temperature=temperature;scene.weatherUpdated=weatherUpdated;scene.weatherSource=weatherSource;scene.bold=bold;scene.outline=outline;return scene;
    }
    public boolean isLandscape(){return kind==CALENDAR&&template>=2&&template<=3;}
    public void normalize(){
        if(kind!=NOTE&&kind!=CALENDAR)throw new IllegalArgumentException("未知卡片类型");
        year=clamp(year,1,9999);month=clamp(month,1,12);day=clamp(day,0,YearMonth.of(year,month).lengthOfMonth());template=clamp(template,0,3);
        foreground=clamp(foreground,0,3);background=clamp(background,0,3);outlineColor=clamp(outlineColor,0,3);fontSize=clamp(fontSize,16,120);
        title=bounded(title,100);text=bounded(text,4000);city=bounded(city,80);weather=bounded(weather,80);temperature=bounded(temperature,40);weatherUpdated=bounded(weatherUpdated,160);weatherSource=bounded(weatherSource,80);
    }
    public byte[] encode() throws IOException {
        CardScene scene=copy();scene.normalize();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(scene.kind);out.writeInt(scene.year);out.writeInt(scene.month);out.writeInt(scene.day);out.writeInt(scene.template);
        out.writeInt(scene.foreground);out.writeInt(scene.background);out.writeInt(scene.fontSize);out.writeInt(scene.outlineColor);out.writeBoolean(scene.bold);out.writeBoolean(scene.outline);
        out.writeUTF(scene.title);out.writeUTF(scene.text);out.writeUTF(scene.city);out.writeUTF(scene.weather);out.writeUTF(scene.temperature);out.writeUTF(scene.weatherUpdated);out.writeUTF(scene.weatherSource);out.flush();return bytes.toByteArray();
    }
    public static CardScene decode(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("卡片参数无效");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("未知卡片版本");
        CardScene scene=new CardScene();scene.kind=in.readInt();scene.year=in.readInt();scene.month=in.readInt();scene.day=in.readInt();scene.template=in.readInt();
        scene.foreground=in.readInt();scene.background=in.readInt();scene.fontSize=in.readInt();scene.outlineColor=in.readInt();scene.bold=in.readBoolean();scene.outline=in.readBoolean();
        scene.title=in.readUTF();scene.text=in.readUTF();scene.city=in.readUTF();scene.weather=in.readUTF();scene.temperature=in.readUTF();scene.weatherUpdated=in.readUTF();scene.weatherSource=in.readUTF();
        if(in.available()!=0)throw new IOException("卡片参数尾部异常");
        try{scene.normalize();}catch(IllegalArgumentException error){throw new IOException("卡片参数类型无效",error);}return scene;
    }
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    private static String bounded(String value,int length){if(value==null)return "";return value.length()<=length?value:value.substring(0,length-(Character.isHighSurrogate(value.charAt(length-1))?1:0));}
}

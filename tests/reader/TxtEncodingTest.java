package com.inkreader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Executes production import helpers on the host; no Android framework method is invoked. */
public final class TxtEncodingTest {
    public static void main(String[] args)throws Exception{
        Method utf8=TxtImporter.class.getDeclaredMethod("isUtf8Prefix",byte[].class,int.class);utf8.setAccessible(true);
        byte[] complete="ASCII 中文🙂".getBytes(StandardCharsets.UTF_8);check(prefix(utf8,complete),"Complete UTF8 rejected");
        for(int n=1;n<=3;n++)check(prefix(utf8,Arrays.copyOf(complete,complete.length-n)),"Truncated final four-byte codepoint rejected");
        for(byte[] value:new byte[][]{{(byte)0xc2},{(byte)0xe0,(byte)0xa0},{(byte)0xf4,(byte)0x8f,(byte)0xbf}})check(prefix(utf8,value),"Legal incomplete prefix rejected");
        for(byte[] value:new byte[][]{{65,(byte)0xff},{65,(byte)0x80},{(byte)0xc0,(byte)0xaf},{(byte)0xed,(byte)0xa0,(byte)0x80},{(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80},{(byte)0xe0,(byte)0x80},{(byte)0xf0,(byte)0x80},{(byte)0xf4,(byte)0x90},"ASCII 汉".getBytes(Charset.forName("GB18030"))})check(!prefix(utf8,value),"Invalid UTF8 tail was silently trimmed: "+Arrays.toString(value));
        Method bom=TxtImporter.class.getDeclaredMethod("skipBom",PushbackInputStream.class,Charset.class);bom.setAccessible(true);
        strip(bom,new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,65,66},StandardCharsets.UTF_8,new byte[]{65,66});strip(bom,new byte[]{(byte)0xff,(byte)0xfe,65,0},StandardCharsets.UTF_16LE,new byte[]{65,0});strip(bom,new byte[]{(byte)0xfe,(byte)0xff,0,65},StandardCharsets.UTF_16BE,new byte[]{0,65});strip(bom,new byte[]{65,66},StandardCharsets.UTF_8,new byte[]{65,66});
        Class<?> counter=Class.forName("com.inkreader.TxtImporter$CountingInputStream");Constructor<?> ctor=counter.getDeclaredConstructor(InputStream.class,long.class);ctor.setAccessible(true);InputStream limited=(InputStream)ctor.newInstance(new ByteArrayInputStream(new byte[]{1,2,3}),2L);check(limited.read()==1&&limited.read()==2,"Counter lost bytes");try{limited.read();throw new AssertionError("Source byte limit ignored");}catch(java.io.IOException expected){}limited.close();
        limited=(InputStream)ctor.newInstance(new ByteArrayInputStream(new byte[]{1,2,3}),2L);try{limited.read(new byte[4]);throw new AssertionError("Bulk source byte limit ignored");}catch(java.io.IOException expected){}limited.close();
        System.out.println("TxtEncodingTest PASS: strict UTF8 prefix, BOM preservation, GB18030 fallback, source limit (no Android IO)");
    }
    private static boolean prefix(Method method,byte[] bytes)throws Exception{return (Boolean)method.invoke(null,bytes,bytes.length);}
    private static void strip(Method method,byte[] input,Charset charset,byte[] expected)throws Exception{PushbackInputStream stream=new PushbackInputStream(new ByteArrayInputStream(input),4);method.invoke(null,stream,charset);ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;while((b=stream.read())!=-1)bytes.write(b);check(Arrays.equals(expected,bytes.toByteArray()),"BOM helper lost non-BOM data");}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

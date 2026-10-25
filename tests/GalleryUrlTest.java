package org.nfcresearch.qpolllab;

public final class GalleryUrlTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void reject(String url){check(!GalleryUrl.isHttps(url),"Unsafe URL accepted: "+url);try{GalleryUrl.normalize(url);}catch(IllegalArgumentException expected){return;}throw new AssertionError("Unsafe address normalized: "+url);}
    public static void main(String[] args){
        String signed="https://example.com/api/download/123?request_id=abc-123&signature=a%2Fb%2B%3D&x=1&x=2";
        check(signed.equals(GalleryUrl.normalize(signed)),"Signed download query changed");
        check(GalleryUrl.isHttps(signed),"HTTPS signed URL rejected");
        check("https://example.com/gallery".equals(GalleryUrl.normalize(" example.com/gallery ")),"Bare hostname default HTTPS");
        check(GalleryUrl.isHttps("HTTPS://example.com/path#photo"),"Scheme case");
        check(GalleryUrl.isHttps("https://example.com:8443/gallery"),"HTTPS custom port");
        check(GalleryUrl.isHttps("https://[::1]:443/"),"HTTPS IPv6");
        for(String url:new String[]{null,""," ","http://example.com","http://192.168.1.3:8080/","javascript:alert(1)","file:///sdcard/secret","content://provider/item","data:text/html,hi","intent://host","https://user:pass@example.com/","https:///missing-host","https://example.com:0/","https://example.com:99999/","https://example.com\\@evil.com/","https://example.com/\r\nheader"})reject(url);
        check(!GalleryUrl.isHttps("example.com"),"Navigation must be absolute HTTPS");
        check("gallery-image.png".equals(GalleryUrl.fileName(null)),"Null filename fallback");
        String name=GalleryUrl.fileName("../folder\\name\r\n.png");
        check(!name.contains("/")&&!name.contains("\\")&&!name.contains("\r")&&!name.contains("\n")&&!name.startsWith("."),"Filename path/header escape");
        StringBuilder longName=new StringBuilder();for(int i=0;i<500;i++)longName.append('a');
        check(GalleryUrl.fileName(longName.toString()).length()<=100,"Filename bound");
        System.out.println("GalleryUrlTest: PASS (HTTPS policy, signed queries, blocked local/cleartext schemes, safe bounded filenames)");
    }
}

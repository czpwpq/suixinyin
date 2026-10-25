package org.nfcresearch.qpolllab;

import java.net.URI;
import java.net.URISyntaxException;

/** HTTPS-only address policy; download URLs are validated without rewriting signed queries. */
public final class GalleryUrl {
    private GalleryUrl() { }
    public static String normalize(String input) {
        if(input==null||input.trim().isEmpty())throw new IllegalArgumentException("请填写图库的 HTTPS 网址");
        String value=input.trim();
        try {
            URI uri=new URI(value);
            if(uri.getScheme()==null){value="https://"+value;uri=new URI(value);}
            validate(uri);
            return uri.toASCIIString();
        } catch(URISyntaxException invalid) {
            throw new IllegalArgumentException("网址格式不正确，请使用完整 HTTPS 网址");
        }
    }
    public static boolean isHttps(String value) {
        if(value==null||value.isEmpty())return false;
        try{validate(new URI(value));return true;}catch(IllegalArgumentException|URISyntaxException invalid){return false;}
    }
    private static void validate(URI uri) {
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.isOpaque()||uri.getHost()==null||uri.getHost().isEmpty()||
           uri.getRawUserInfo()!=null||uri.getPort()==0||uri.getPort()>65535)
            throw new IllegalArgumentException("图库仅支持 HTTPS 网址，且不能包含账号密码");
    }
    public static String fileName(String suggestion) {
        if(suggestion==null||suggestion.trim().isEmpty())return "gallery-image.png";
        StringBuilder safe=new StringBuilder();
        for(int i=0;i<suggestion.length()&&safe.length()<100;i++){
            char c=suggestion.charAt(i);
            safe.append(Character.isISOControl(c)||"/\\:*?\"<>|".indexOf(c)>=0?'_':c);
        }
        String value=safe.toString().trim();while(value.startsWith("."))value=value.substring(1).trim();
        return value.isEmpty()?"gallery-image.png":value;
    }
}

package org.nfcresearch.qpolllab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Presentation of an active send. Only TransferObserver.finished ends the session. */
public final class TransferDisplay {
    private static final Pattern PACKETS=Pattern.compile("(-?[0-9]+)\\s*/\\s*(-?[0-9]+)\\s*包");
    private static final Pattern PERCENT=Pattern.compile("(?<![0-9.\\-])([0-9]{1,3})\\s*%");
    public final String phase,buttonText;
    /** Image transfer progress; refresh and connection stages are indeterminate. */
    public final int percent;
    /** All image packets are sent. The screen can still be refreshing or cleaning up. */
    public final boolean uploadComplete;

    private TransferDisplay(String phase,String buttonText,int percent,boolean uploadComplete){
        this.phase=phase;this.buttonText=buttonText;this.percent=percent;this.uploadComplete=uploadComplete;
    }

    public static TransferDisplay from(String message){
        String text=message==null?"":message.trim();
        if(has(text,"请求停止","正在停止","已停止","用户停止"))
            return new TransferDisplay("正在停止，等待当前通信结束","正在停止 · 请勿移动",-1,false);
        if(has(text,"需要检查","未完成","未成功","失败","异常","无法执行","超时","未收到标签","身份不匹配"))
            return new TransferDisplay("发送未完成，正在收尾","正在收尾 · 请勿移动",-1,false);
        if(has(text,"刷新结果待确认"))
            return new TransferDisplay("刷新结果待确认，正在收尾","正在收尾 · 请勿移动",-1,true);
        if(has(text,"刷新周期已结束","刷新周期结束"))
            return new TransferDisplay("刷新周期结束，正在收尾","正在收尾 · 请勿移动",-1,true);
        if(has(text,"启动屏幕刷新","等待屏幕刷新","屏幕报告忙","屏幕报告空闲","继续供能"))
            return refreshing(-1);
        if(has(text,"图片已传完","图片传输完毕","图片传输完成","上传完成"))
            return refreshing(-1);

        Matcher packets=PACKETS.matcher(text);
        if(packets.find()){
            try{
                long done=Long.parseLong(packets.group(1)),total=Long.parseLong(packets.group(2));
                // Invalid counts must not become a misleading 100% through clamping or overflow.
                if(total>0&&total<=Integer.MAX_VALUE&&done>=0&&done<=total)
                    return uploading((int)(done*100/total));
            }catch(NumberFormatException ignored){}
            return uploading(-1);
        }
        if(has(text,"传输图片","图片传输","传输中","上传")){
            Matcher percentage=PERCENT.matcher(text);
            if(percentage.find()){
                int value=Integer.parseInt(percentage.group(1));
                if(value<=100)return uploading(value);
            }
            return uploading(-1);
        }
        if(has(text,"图像区访问权限"))return waiting("正在校验图片写入权限");
        if(has(text,"校验屏幕规格"))return waiting("正在校验屏幕规格");
        if(has(text,"收到标签"))return waiting("已识别屏幕，正在连接");
        if(has(text,"寻卡"))return waiting("正在寻找墨水屏，请贴稳");
        if(has(text,"ISO-DEP","建立连接"))return waiting("正在连接墨水屏");
        if(has(text,"授权","连接","Root","Shizuku","NFC"))return waiting("正在检查授权与连接");
        return new TransferDisplay("正在准备刷屏","刷屏中 · 请勿移动",-1,false);
    }

    private static TransferDisplay waiting(String phase){
        return new TransferDisplay(phase,"连接中 · 请勿移动",-1,false);
    }
    private static TransferDisplay uploading(int percent){
        if(percent==100)return refreshing(100);
        return new TransferDisplay("正在传输图片，请保持贴合",percent<0?"传输中 · 请勿移动":"传输中 "+percent+"% · 请勿移动",percent,false);
    }
    private static TransferDisplay refreshing(int percent){
        return new TransferDisplay("图片已传完，屏幕刷新中，请继续贴合供能","刷屏中 · 请勿移动",percent,true);
    }
    private static boolean has(String text,String... words){
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
}

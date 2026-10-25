package org.nfcresearch.qpolllab;

public final class TransferDisplayTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void activeRefresh(String message){
        TransferDisplay display=TransferDisplay.from(message);
        require(display.uploadComplete&&display.percent==-1,"Refresh remains indeterminate: "+message);
        require(display.buttonText.equals("刷屏中 · 请勿移动"),"Keep active CTA during refresh: "+message);
        require(!display.phase.contains("刷新完成"),"Must not claim completed screen: "+message);
    }
    public static void main(String[] args){
        TransferDisplay initial=TransferDisplay.from("正在检查授权与连接…");
        require(initial.percent==-1&&!initial.uploadComplete&&initial.buttonText.contains("连接中"),"Initial connection");
        require(TransferDisplay.from("Q-Poll 寻卡中 · 剩余 10 秒").phase.contains("寻找"),"Finding screen");
        require(TransferDisplay.from("正在建立 ISO-DEP 连接").phase.equals("正在连接墨水屏"),"Connect stage");
        require(TransferDisplay.from("读取并校验屏幕规格 · 保持贴合").phase.equals("正在校验屏幕规格"),"Check stage");
        require(TransferDisplay.from("检查图像区访问权限").phase.contains("写入权限"),"Authentication stage");
        TransferDisplay packet=TransferDisplay.from("正在传输图片 · 已确认 440 / 476 包 · 92% · 保持贴合");
        require(packet.percent==92&&!packet.uploadComplete,"Confirmed packet progress");
        require(packet.buttonText.equals("传输中 92% · 请勿移动"),"Progress in pinned CTA");
        require(TransferDisplay.from("已确认 440 / 476 包 · 100%").percent==92,"Counts outrank stale percentage");
        require(TransferDisplay.from("已确认 0 / 476 包 · 0%").percent==0,"Zero is determinate");
        TransferDisplay last=TransferDisplay.from("已确认 476 / 476 包 · 100% · 保持贴合");
        require(last.percent==100&&last.uploadComplete,"Last packet completes upload only");
        require(last.buttonText.equals("刷屏中 · 请勿移动")&&!last.phase.contains("刷新完成"),"100% must not release screen");
        activeRefresh("图片已传完，正在启动屏幕刷新 · 当前贴合的墨水屏");
        activeRefresh("保持贴合供能，等待屏幕刷新");
        activeRefresh("屏幕报告忙，继续供能");
        activeRefresh("屏幕报告空闲");
        activeRefresh("图片传输完成");
        // Even a terminal protocol event waits for the host's finished callback and NFC cleanup.
        TransferDisplay ending=TransferDisplay.from("刷新周期已结束 · 请看屏幕 · 已观察到忙→连续三次空闲");
        require(ending.uploadComplete&&ending.percent==-1&&ending.buttonText.contains("收尾"),"Terminal status still awaiting finished");
        require(TransferDisplay.from("刷新结果待确认").buttonText.contains("收尾"),"Unconfirmed is never success");
        TransferDisplay stopped=TransferDisplay.from("已请求停止，等待当前通信结束…");
        require(stopped.percent==-1&&!stopped.uploadComplete&&stopped.buttonText.equals("正在停止 · 请勿移动"),"Stop remains active until callback");
        require(TransferDisplay.from("需要检查 · 传输失败").buttonText.contains("收尾"),"Failure cleanup");
        require(TransferDisplay.from("发送未完成：已确认 476 / 476 包 · 100%").buttonText.contains("收尾"),"Failure wins over old progress");
        require(TransferDisplay.from("上传 27%").percent==27,"Legacy upload percentage");
        require(TransferDisplay.from("正在传输图片 101%").percent==-1,"Out of bounds percentage");
        require(TransferDisplay.from("正在传输图片 1000%").percent==-1,"No suffix percentage from huge value");
        require(TransferDisplay.from("正在传输图片 -1%").percent==-1,"Negative percent not parsed as positive");
        require(TransferDisplay.from("已确认 1 / 0 包 · 100%").percent==-1,"Invalid denominator not success");
        require(TransferDisplay.from("已确认 477 / 476 包 · 100%").percent==-1,"Invalid numerator not success");
        require(TransferDisplay.from("正在传输图片 · 已确认 -1 / 476 包 · 100%").percent==-1,"Negative counts outrank stale percent");
        require(TransferDisplay.from("已确认 9999999999999999999999 / 476 包").percent==-1,"Overflow is harmless");
        require(TransferDisplay.from("已确认 2147483647 / 2147483647 包").percent==100,"Long calculation avoids integer overflow");
        require(TransferDisplay.from(null).percent==-1&&TransferDisplay.from("").percent==-1,"Missing status safe");
        require(TransferDisplay.from("未知消息 100%").percent==-1,"Unrelated percent cannot complete upload");
        System.out.println("TransferDisplayTest OK");
    }
}

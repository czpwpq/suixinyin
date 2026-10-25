package org.nfcresearch.qpolllab.activation;

import java.util.regex.Pattern;

/**
 * Bounded diagnostics for the actual ADB startup output. This is display-only:
 * an output line can never establish backend identity or mark activation ready.
 * The owner should first apply Attempt.sanitizeOutput for its exact nonce.
 */
public final class ActivationStartupLog {
    public static final int MAX_LINE_CHARS=400,MAX_TOTAL_CHARS=8192;
    private static final int MAX_INSPECT_CHARS=16384;
    private static final Pattern HEX_SECRET=Pattern.compile("(?i)[0-9a-f]{64}");
    private static final Pattern PAIRING_CODE=Pattern.compile("(?i)((?:pair(?:ing)?[ _-]?code|配对码)\\s*[:=：]?\\s*)([0-9]{6})(?![0-9])");
    private static final Pattern ANSI=Pattern.compile("\\u001b\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern CONTROLS=Pattern.compile("[\\x00-\\x1f\\x7f\\u2028\\u2029]");
    private static final Pattern CLASSPATH_ASSIGNMENT=Pattern.compile("(?i)\\bCLASSPATH\\s*=");
    private static final Pattern BOOTSTRAP_ARGUMENTS=Pattern.compile("(?:[A-Za-z0-9_.$]*InternalBridgeBootstrap)\\s+[0-9]{4,}\\s+");
    private static final Pattern ERROR_TEXT=Pattern.compile("(?i)(?:exception|\\berror\\b|\\bfatal\\b|\\bfailed\\b|\\bfailure\\b|\\bdenied\\b|not found|inaccessible|\\bcannot\\b|could not|unable to)");
    private final StringBuilder lines=new StringBuilder();
    private String lastStage="",stageStatus="",lastOutput="",lastError="";

    /** Accept one line from stdout/stderr. All public state access is synchronized. */
    public synchronized void acceptLine(String value){
        if(value==null)return;
        String raw=value.length()>MAX_INSPECT_CHARS?value.substring(0,MAX_INSPECT_CHARS):value;
        String normalized=CONTROLS.matcher(ANSI.matcher(raw).replaceAll("")).replaceAll(" ").trim();
        if(normalized.isEmpty())return;
        boolean explicitError=normalized.startsWith("ACT|ERROR|");
        if(commandEcho(normalized)){
            String hidden=explicitError||ERROR_TEXT.matcher(normalized).find()?"启动命令执行错误（命令内容已隐藏）":"启动命令回显已隐藏";
            if(explicitError||ERROR_TEXT.matcher(normalized).find())lastError=hidden;
            else lastOutput=hidden;
            append((explicitError?"错误":"输出")+" · "+hidden);
            return;
        }
        String safe=HEX_SECRET.matcher(normalized).replaceAll("[密钥已隐藏]");
        safe=PAIRING_CODE.matcher(safe).replaceAll("$1[配对码已隐藏]");
        if(safe.startsWith("ACT|SHELL_STARTED|")){
            String pid=safe.substring("ACT|SHELL_STARTED|".length()).trim();
            if(pid.matches("[0-9]{1,10}")){
                lastStage="SHELL_STARTED";stageStatus="启动脚本已运行 · PID "+pid;append(stageStatus);return;
            }
        }else if(safe.startsWith("ACT|STAGE|")){
            String name=safe.substring("ACT|STAGE|".length()).trim();
            if(name.matches("[A-Za-z][A-Za-z0-9_.:-]{0,79}")){
                lastStage=name;stageStatus="服务启动阶段："+name;append(stageStatus);return;
            }
        }else if(explicitError){
            String detail=safe.substring("ACT|ERROR|".length()).trim();
            lastError=limit("服务启动错误："+(detail.isEmpty()?"未提供错误详情":detail),MAX_LINE_CHARS);
            append(lastError);return;
        }
        lastOutput=limit(safe,MAX_LINE_CHARS);
        if(ERROR_TEXT.matcher(safe).find())lastError=limit("服务启动输出错误："+safe,MAX_LINE_CHARS);
        append("输出 · "+safe);
    }
    private static boolean commandEcho(String line){
        return CLASSPATH_ASSIGNMENT.matcher(line).find()
                ||BOOTSTRAP_ARGUMENTS.matcher(line).find()
                ||(line.contains("app_process")&&line.contains("--nice-name="));
    }
    private void append(String line){
        lines.append(limit(line,MAX_LINE_CHARS)).append('\n');
        while(lines.length()>MAX_TOTAL_CHARS){int first=lines.indexOf("\n");if(first<0){lines.setLength(0);break;}lines.delete(0,first+1);}
    }
    private static String limit(String value,int max){
        if(value.length()<=max)return value;
        int end=max-1;
        if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
        return value.substring(0,end)+"…";
    }
    /** Latest wire stage name, empty until a well-formed shell/stage record arrives. */
    public synchronized String stage(){return lastStage;}
    /** Latest retained error takes precedence over progress or incidental VM output. */
    public synchronized String status(){
        if(!lastError.isEmpty())return lastError;
        if(!stageStatus.isEmpty())return stageStatus;
        if(!lastOutput.isEmpty())return limit("启动输出："+lastOutput,MAX_LINE_CHARS);
        return "等待启动脚本输出";
    }
    public synchronized boolean hasError(){return !lastError.isEmpty();}
    /** Recent sanitized output only; at most MAX_TOTAL_CHARS, including newlines. */
    public synchronized String diagnostics(){return lines.toString();}
}

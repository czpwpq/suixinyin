package org.nfcresearch.qpolllab;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Process;
import rikka.shizuku.Shizuku;

/** Read-only connection information. No photos, account/device IDs, logs, or private configuration are read. */
public final class ShizukuDiagnostics {
    private static final String MANAGER = "moe.shizuku.privileged.api";
    private static final String API_PERMISSION = "moe.shizuku.manager.permission.API_V23";
    private static final String SUPPORT_KEY = "moe.shizuku.client.V3_SUPPORT";
    private static final String PROVIDER_CLASS = "rikka.shizuku.ShizukuProvider";
    private static final String PROVIDER_PERMISSION = "android.permission.INTERACT_ACROSS_USERS_FULL";

    private ShizukuDiagnostics() { }

    public static String report(Context context) {
        StringBuilder out = new StringBuilder("Shizuku 连接诊断（只读）\n");
        out.append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("；Android ").append(Build.VERSION.RELEASE).append(" / API ")
                .append(Build.VERSION.SDK_INT).append('\n');
        int processUid = Process.myUid();
        out.append("进程 UID：").append(processUid).append("；Android 用户号：")
                .append(processUid / 100000).append('\n');
        if (context == null) return out.append("应用上下文不可用\n").toString();

        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        out.append("应用包名：").append(packageName).append('\n');
        try {
            PackageInfo self = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA
                    | PackageManager.GET_PERMISSIONS | PackageManager.GET_PROVIDERS);
            out.append("应用版本：").append(version(self)).append('\n');
            ApplicationInfo app = self.applicationInfo;
            if (app != null) {
                out.append("安装信息 UID：").append(app.uid).append("；targetSdk：")
                        .append(app.targetSdkVersion).append("；已启用：").append(app.enabled).append('\n');
                Object support = app.metaData == null ? null : app.metaData.get(SUPPORT_KEY);
                out.append("V3_SUPPORT：").append(support == null ? "缺失" :
                        String.valueOf(support) + "（" + support.getClass().getSimpleName() + "）").append('\n');
            }
            out.append("已声明 API_V23：").append(contains(self.requestedPermissions, API_PERMISSION)).append('\n');
            out.append("系统 API_V23 授权：").append(permission(pm.checkPermission(API_PERMISSION, packageName))).append('\n');
            ProviderInfo provider = null;
            if (self.providers != null) for (ProviderInfo entry : self.providers) {
                if (PROVIDER_CLASS.equals(entry.name)) { provider = entry; break; }
            }
            if (provider == null) out.append("ShizukuProvider：缺失\n");
            else {
                out.append("Provider authority：").append(provider.authority).append('\n');
                out.append("Provider 配置：exported=").append(provider.exported)
                        .append("，enabled=").append(provider.enabled)
                        .append("，multiprocess=").append(provider.multiprocess).append('\n');
                out.append("Provider 保护权限：read=").append(provider.readPermission)
                        .append("，write=").append(provider.writePermission).append('\n');
                boolean expected = (packageName + ".shizuku").equals(provider.authority)
                        && provider.exported && provider.enabled && !provider.multiprocess
                        && PROVIDER_PERMISSION.equals(provider.readPermission)
                        && PROVIDER_PERMISSION.equals(provider.writePermission);
                out.append("Provider 声明匹配官方配置：").append(expected).append('\n');
                out.append("Provider 组件开关：")
                        .append(pm.getComponentEnabledSetting(new ComponentName(packageName, provider.name))).append("（0=默认，1=开启，2/3/4=禁用）\n");
            }
        } catch (Exception e) {
            out.append("读取本应用声明失败：").append(e.getClass().getSimpleName()).append('\n');
        }
        try {
            PackageInfo manager = pm.getPackageInfo(MANAGER, 0);
            out.append("Shizuku 管理器：").append(version(manager)).append('\n');
            if (manager.applicationInfo != null) out.append("管理器 UID：")
                    .append(manager.applicationInfo.uid).append("；已启用：")
                    .append(manager.applicationInfo.enabled).append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            out.append("Shizuku 管理器：当前 Android 用户未找到或不可见\n");
        } catch (Exception e) {
            out.append("读取管理器版本失败：").append(e.getClass().getSimpleName()).append('\n');
        }
        boolean received = false;
        try { received = Shizuku.pingBinder(); }
        catch (Exception e) { out.append("检查 Binder 失败：").append(e.getClass().getSimpleName()).append('\n'); }
        out.append("本应用收到有效 Shizuku Binder：").append(received).append('\n');
        if (received) {
            try { out.append("Shizuku API 授权检查：").append(permission(Shizuku.checkSelfPermission())).append('\n'); }
            catch (Exception e) { out.append("API 授权检查失败：").append(e.getClass().getSimpleName()).append('\n'); }
            try { out.append("服务协议版本：").append(Shizuku.getVersion())
                    .append("；服务 UID：").append(Shizuku.getUid()).append("（2000=ADB，0=Root）\n"); }
            catch (Exception e) { out.append("服务信息读取失败：").append(e.getClass().getSimpleName()).append('\n'); }
        } else out.append("未收到 Binder 不等于 Shizuku 服务未运行；请在 Shizuku 首页确认。\n");
        out.append("本报告只读取本应用声明、管理器版本和连接状态，不读取照片、日志或 Shizuku 私有配置。\n");
        return out.toString();
    }

    public static String recoveryInstructions() {
        return "1. 使用 Shizuku 13 或更新版本，并在其首页确认服务已启动。返回便捷版点击连接；不贴手机壳也能完成只读身份校验。\n\n"
                + "2. 只有显示“身份已验证”才表示本应用后端通过包名、完整 UID、版本及 APK 检查。已授权 / 收到 Binder 并不等于后端身份正确。\n\n"
                + "3. 若出现 Wrong caller 或身份校验失败，请先复制连接诊断和本轮日志。报告保留最近一次握手结果；不要通过反复发送图片尝试恢复。\n\n"
                + "4. 若便捷版从授权列表消失，保留现有安装，可尝试在 Shizuku 中停止并重新启动服务后再请求授权；仅划掉管理器后台不等于重启服务。列表消失的根因仍需结合诊断确认。\n\n"
                + "原版与便捷版独立授权，无须删除原版、清除数据或更改其他应用的分身设置。";
    }

    private static String version(PackageInfo info) {
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return String.valueOf(info.versionName) + "（" + code + "）";
    }

    private static String permission(int value) {
        return value == PackageManager.PERMISSION_GRANTED ? "已允许" : "未允许";
    }

    private static boolean contains(String[] values, String target) {
        if (values != null) for (String value : values) if (target.equals(value)) return true;
        return false;
    }
}

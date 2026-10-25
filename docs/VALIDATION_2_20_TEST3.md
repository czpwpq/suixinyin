# 2.20-test3 验证记录

日期2026-10-25；versionCode33，编译后端身份34，包名与签名延续2.20-test2。

## 证据与边界

用户诊断显示实际配对已成功，ADB连接后进入InternalBackend.prepare；存在18秒交付超时以及两次离开页面取消。旧命令把stdout/stderr丢弃，不能据此断言Android16改了Provider签名、缺少nohup或某个特定反射接口失败。AOSP16确实包含nohup；本次改动重点是可观测启动、后台等待与确认门控。

## 实现

- Activity顶部固定状态，返回/页头返回等待或明确取消，忙时禁误改端口；成功显示同时要求ready且不再isActivating。
- 新增非导出shortService InternalActivationService，用户发起后保持有界后台处理，65秒controller/70秒service及平台onTimeout兜底，完成/取消清理。
- 实际ActivationProof.command使用trap/export/exec、后台child和wait，保留stderr与ACT阶段；不输出完整命令。Bootstrap验证setsid或自身session，Context/服务构造/Provider/交付各阶段可见，交付后dup2。
- Attempt.await只等raw交付；关闭启动ADB/等500ms后verifyReady验证全部三个Binder、安装UID/APK/版本和owner后提交confirmed。public isReady/photoBinder/readerBinder都受confirmed门控；停止/死亡清确认。
- ActivationStartupLog线程安全、有界/脱敏；独立日志区保留错误，不会被NSD地址日志冲掉。Controller限制输出行和总量，阶段进度不回退。
- 保留既有UID权限保护、nonce期限/重放/取消校验，不改NFC协议或DA/DB。

## 检查

主机回归、阅读/LZO、输入/发现/启动证明及输出脱敏检查；新增实际POSIX Shell运行生产命令（仅将app_process替换为无害假脚本），验证精确argv和CLASSPATH引用、禁止命令替换、HUP忽略、stderr可见、wait保留退出码。

Android原生用例增加固定状态布局和两种Back防误退检查；完整APK和Instrumentation构建、签名及源码包完整性检查。**原生仅编译，未Android运行。**

本机无目标小米或模拟器，因此不声称Android16真实服务交付或NFC成功；本版不是实体局刷改动。

## 最终结果

- 原主机回归、阅读协议与4005组LZO独立解码：通过。
- 激活runner：Inputs/BackendMode、ActivationProof32项、真实Shell命令、DiscoveryState、NotificationPairingInput70项与ActivationStartupLog全部通过。
- 真实Git Bash执行生产启动命令（app_process替换为假程序）：通过HUP忽略、精确引用/argv、注入防护、stderr可见及wait退出码验证；没有执行Android。
- RSA/X509、公钥wire、本机fake-ADB40次立即OKAY/CLSE/关闭唤醒、64位库LOAD对齐：通过。
- 主APK与原生Instrumentation编译、资源/DEX/JNI打包、对齐及签名验证：通过；原生检查仍只编译。
- 签名证书与2.20-test2匹配，可覆盖安装保留配对身份；源码ZIP校验通过，363个文件，排除签名私钥与缓存。
- NFC照片/阅读协议、Worker及图像核心与已交付2.19-test1逐字节一致。

最终APK SHA-256：`25aabff8eee8f13ce9b166ebf5a61f8b499cb12b8673b204c04481b96e8ef615`。

日志work/2.20-test3-*.log。未发布GitHub或服务器。目标手机交付和独立存活尚待确认。

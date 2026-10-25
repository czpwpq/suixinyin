# 2.20-test2 验证记录

日期2026-10-25，versionCode32，后端编译身份33，包名和签名延续2.20-test1。

## 已确认的代码问题与修复

2.20-test1只有pairPort>0才添加RemoteInput，未发现端口时没有回复动作，点击正文会回App。这是明确的UI分支问题；截图不能单独确定该手机没有发现端口的具体原因。

本版通知始终含输入动作：已发现端口可填六码；未发现时支持端口+码后备。输入交给非导出的短前台服务，不打开Activity；正文contentIntent为空，另提供明确的设置动作。设置Intent使用NEW_TASK和RESET_TASK_IF_NEEDED，不使用CLEAR_TASK/CLEAR_TOP或NEW_DOCUMENT。

原生服务声明shortService与FOREGROUND_SERVICE普通权限，API34+传入SHORT_SERVICE；旧系统分支不传未知type。服务非粘性、有独立截止计时和onTimeout兜底；完成/取消/发现到期会停止，绝不从BOOT启动。

NSD修复规范化类型、LinkProperties/网卡及IPv4mapped地址、API34多地址与持续更新、legacy6秒超时和有界重试，避免失败的seen占位永久丢服务。新增AdbDiscoveryState实际接入Entry；发现、解析、采用/过滤及失效进入诊断。仍严格验证本机地址，实际ADB目标固定127.0.0.1。

## 验证范围

- 输入parser实际函数：端口范围、6码与组合输入、leadingzero、长输入、远端IP格式拒绝、清码及错误不回显。
- 实际AdbDiscoveryState与ActivationInputs：类型变体/本机地址、超时重试、服务重新发现/离场/旧更新失效；ActivationProof保留26项真实函数校验。
- 既有主机/阅读回归、本机ADB Socket和密码学检查及APK构建签名。
- 新原生检查：pairPort=0和有效时均有RemoteInput，PI可变且不启动Activity，正文不跳转、设置动作独立；Manifest私有shortService、权限与设置Intent flags。**原生检查只编译，没有Android执行。**

本机没有目标手机或模拟器，未验证HyperOS通知展开外观、系统两个最近任务、真实后台NSD或TLS/SPAKE2配对成功；需要用户安装本版后确认。NFC和图像核心协议未为此改动；全刷仍是全刷。

## 执行结果

- 主机回归和阅读协议/LZO独立解码检查通过。
- `tests/activation/run_tests.py`通过：Inputs类型/地址/后端模式、实际ActivationProof26项、实际AdbDiscoveryState状态及NotificationPairingInput70项检查。
- 原RSA/X509/公钥wire格式、本机fake-ADB40次立即OKAY/CLSE/关闭唤醒、64位原生库LOAD对齐检查通过；不属于手机TLS配对结果。
- APK编译、DEX/资源/JNI打包、对齐与签名验证通过；签名证书与2.20-test1一致，可覆盖安装。
- 原生Instrumentation APK编译与签名通过，新增通知/服务/设置任务断言已纳入；没有在Android端执行。
- 源码ZIP校验通过，356个文件，保留依赖/对应源码/许可，排除私钥、缓存和APK。NFC照片/阅读核心协议与已交付2.19-test1逐字节一致。

最终APK SHA-256：`5c8efa487c8369d068f6f0b35599da75cd642082755ae4d70b0fa8814558e8a4`。

日志为work/2.20-test2-*.log；未发布GitHub或服务器。目标小米的通知外观、任务切换及真实配对仍待用户确认。

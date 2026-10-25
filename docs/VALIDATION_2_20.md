# 随心印 2.20-test1 验证记录

日期2026-10-25；versionCode31，后端编译身份32，包名`org.nfcresearch.qpolllab.easy`。

## 实现范围

- 单APK内提供真实本机无线ADB配对、连接与专用app_process授权服务，不内嵌完整Shizuku管理器或给其他应用提供权限。
- LibADB真实TLS1.3/SPAKE2配对，私有持久RSA身份；本机NSD与手动端口入口，区分配对/连接服务，固定只连127.0.0.1。
- 灰白黑激活页面、可选RemoteInput通知配对、明确激活/停止及脱敏诊断。配对码和一次性启动nonce不进日志。
- 受INTERACT_ACROSS_USERS_FULL保护的Provider，只接受shell/root交付；一次性nonce、90秒期限、完整UID/Android用户号/APK/编译版本及三个Binder身份核验，取消和重放拒绝。
- 复用原照片ShellBridge和阅读PanelShizukuService操作协议。两服务只加Host清理入口；调用者UID校验未放宽。阅读关闭只停阅读会话，不能误停全局内置宿主。
- Main/阅读按内置、外置Shizuku、Root选择路由；外置权限/断开等旧回调受模式守卫，不会覆盖内置状态。切换模式清理旧epoch和待操作。
- 原厂NFC命令、图片算法、局刷状态及共享排他锁延续2.19。内置激活不会执行刷屏。

## 构建与依赖

依赖固定在本地libs并保留原始许可与版本/hash资料。Java8源码由JDK17与Android34 SDK编译；D8包含全部DEX，AAR JNI四ABI与非class运行资源入APK。原始SPAKE2 AAR的Java facade触发SDK34 D8内部错误，因此使用**同版官方JNI facade源码重新编译**，AAR仍原样保留，仅提取其JNI；没有换用另一个数学实现。

Android安装时提取压缩JNI，分别检查Conscrypt和SPAKE2原生库的64位ELF LOAD段对齐。该检查不等于已在16KiB内核或目标手机运行。

许可证和可获取的对应源码/替换说明保留在third-party/activation及APK licenses资产；原有Shizuku MIT和其他来源许可继续保留。

## 自动验证范围

- 原有主机回归及阅读协议/LZO独立解码检查。
- ActivationInputs/BackendMode及真实使用的ActivationProof：端口与ASCII六码、本机地址过滤、完整UID/APK、过期/取消/重放/非shell拒绝等。
- 真实RSA2048、X509、签名与ADB公钥wire格式，以及本机fake-ADB Socket的快速OPEN/OKAY、WRTE/CLSE、EOF和关闭唤醒回归；后者不是Android TLS配对。
- APK完整编译、对齐、签名和与2.19-test1证书比对；源码ZIP排除签名密钥与缓存，验证JNI、DEX和许可证入包。
- 原生Instrumentation新增激活UI/Provider拒绝普通UID/旧attempt取消/无自动激活以及JNI SPAKE2、HKDF/AEAD、错码和篡改检查。**仅编译，没有Android端执行结果。**

## 必须手机确认的边界

本机没有已连接的Android设备、模拟器或墨水屏，未执行真实无线调试配对、Android16 AMS/AttributionSource/SELinux/Binder交付、通知后台行为、手机音量翻页或NFC实际刷新。不能把主机Socket或密码学检查称为真实手机配对成功。

App全进程结束会使owner Binder死亡并清理专用服务，需重新激活；不承诺永久保活。原厂刷新仍为全刷。

## 最终执行结果

- `tests/run_java.py`：通过，原照片/卡片/推送状态主机回归。
- `tests/reader/run_tests.py`：通过，原阅读协议和布局、预传分离、4005组LZO独立解码、文本/搜索/锁路径检查。
- `tests/activation/run_tests.py`：通过，输入/后端模式及实际使用的ActivationProof26项检查。
- `tests/activation/run_adb_crypto_tests.py`：通过。RSA/X509/错配拒绝/公钥wire格式、本机fake-ADB40次立即OKAY响应、WRTE/CLSE排空、待响应OPEN关闭唤醒与64位原生库LOAD对齐。测试只连接127.0.0.1，不连接手机。
- `build.py`：通过，Android34/JDK17编译、DEX、资源与JNI打包、对齐和签名验证。
- `tests/android/build_card_tests.py`：通过，新增原生UI/Provider/真实JNI配对用例全部编译，**尚未Android执行**。
- 签名证书SHA-256与已交付2.19-test1一致，支持覆盖安装。源码ZIP完整性检查通过，348个文件，含依赖及SPAKE2对应源码/许可，排除签名私钥/缓存。
- APK含四ABI的Conscrypt与SPAKE2 JNI及activation-LICENSES资产。照片/阅读NFC核心协议、QPollWorker、ReaderPanelWorker、InkSightQuantizer和StudioImageCodec与已交付2.19-test1逐字节一致。

APK SHA-256：`97c6244a301e59dcff8b428386af49364c1d516ffe089356efb76ac6764c934d`。

日志位于work/2.20-test1-*.log；GitHub和服务器均未发布。真实小米无线配对、Binder交付与NFC仍待手机验收。

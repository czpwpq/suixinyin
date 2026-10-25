# 2.19-test1 验证记录

日期：2026-10-25。版本号2.19-test1（30），后端编译身份31，包名`org.nfcresearch.qpolllab.easy`。

## 改动

- 新增与照片/设备/日志并列的阅读主导航及本地TXT书架；统一默认UI，加入目录、搜索、书签、排版与音量键翻页。
- 从huawei-eink-reader固定提交`a4b72b79acec37543339b9810bcb34105b061d65`改编原生阅读、分页及真实LZO压缩/预传链路；完整来源和许可见READER_PROVENANCE.md。
- 修复移植时发现的UTF-8样本边界误判及单行超长TXT分块问题；分块不拆UTF-16代理对。
- 加强跨章节generation、旧预传/渲染回调、后台关闭及会话释放保护。一次只刷新一个目标；忙时合并用户目标。未命中预传时完整传图。
- 阅读服务采用当前完整UID、APK、后端编译身份校验；不复用上游固定用户0/宽松调用者判断。与原照片后端共用稳定NFC文件锁，释放锁而不删除锁文件。
- 新增明确操作的A/B原厂刷新对照页与阶段耗时。没有写入DA/DB，也没有声称已启用局刷。
- 所有阅读Activity不对外导出；导入数据在私有目录/数据库内。APK与源码保留上游及依赖许可。

## 自动验证范围

`tests/run_java.py`：原照片/卡片/推送状态等主机回归及固定A/B测试图约束。

`tests/reader/run_tests.py`：Fake APDU覆盖压缩D3传图、预传与D4触发分离、断开/错误/取消、A0/A1布局；导出的LZO样本用独立lzokay 2.1.0解码验证；实际章节识别、搜索及导入编码/计数字节helper运行在JVM，只有搜索SQLite边界使用测试替身。锁测试覆盖完整UID用户隔离及无效路径提前拒绝，不执行Android文件权限操作。

`build.py`：Android34/JDK17编译、DEX、资源打包、对齐和签名验证；与已交付2.18-test3签名证书比对。

`tests/android/build_card_tests.py`：包含全部主源码，编译新增ReaderNativeChecks及原有渲染/主页测试，生成同签名Instrumentation APK。新增原生用例覆盖真实ContentResolver/SQLite导入、编码、重复、长行分块、取消、分页连续性和A0/A1硬黑白输出。

原生用例**只构建，尚未在设备或模拟器运行**；不能据此称Android界面、字体、数据库或Bitmap效果通过。

## 必须手机验收

本机无连接的小米17 Pro、ADB/模拟器或墨水屏。Shizuku服务身份握手、持久连接/停止、共享锁目录的SELinux权限、音量键、页面排版及真实NFC刷新均未实机执行。

“预传命中”表示目标packed哈希与板内最近预传一致，可省掉当次D3；D4继续执行原厂完整驱动。对照页只量化普通/预传差异，不能证明某种局刷波形可用。局刷需进一步的匹配驱动资料、命令和物理测试。

所有输出放在本机outputs，未发布到GitHub或外部服务器；源码包排除签名私钥、构建缓存和导入书籍。

## 执行结果

- 主机回归：通过（work/2.19-test1-host-tests.log）。包含原有推送/图片/卡片状态及固定A/B图约束；原InkSight参考检查34组、859582像素通过。
- 阅读离线测试：通过（work/2.19-test1-reader-tests.log）。24项协议检查、A0/A1映射、预传分离、章节/搜索/编码/限额/锁路径检查通过；4005组LZO样本由独立lzokay解码通过。
- 主APK构建与签名验证：通过（work/2.19-test1-build.log）。最后补充阅读设置→设备页屏幕型号同步后重新构建。
- 原生Instrumentation APK构建：通过（work/2.19-test1-native-build.log），包含四主标签、A0/A1同步、导入与排版检查；未执行Android端用例。
- 已交付2.18-test3与新APK签名证书SHA-256匹配，可同包覆盖升级。保留原签名私钥在本机build目录，源码包不含私钥。
- 源码ZIP校验通过：254个文件，保留阅读许可与测试，排除.git、build、dist、私钥和APK。未发布GitHub或服务器。

最终APK SHA-256：`d13ce80faa1bccfae9890daeaa32d583989c9a466d3df12d8ae5b1cbf82fb43a`。

这些结果不包含实体局刷成功或目标手机NFC成功，真机步骤见GUIDE_2_19.md。

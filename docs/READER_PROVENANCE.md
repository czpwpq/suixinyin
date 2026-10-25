# 阅读模块来源与验证边界

阅读模块参考并改编自 [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader)，固定提交 `a4b72b79acec37543339b9810bcb34105b061d65`（上游0.3.9）。本项目沿用其非商业许可，不将上游测试结果或硬件兼容性直接宣称为随心印的验收结果。

## 保留与改编

- 保留本地TXT导入、章节与分块、SQLite书架/进度/书签、搜索、手机与墨水屏共用分页、音量键翻页的结构。
- 书架改为随心印的“阅读”主标签，默认颜色、按钮及品牌与已有界面统一；普通阅读不依赖Shizuku或网络。
- NFC阅读链路单独移植真实LZO1X压缩、D3预传与命中后的D4-only翻页，继续使用A0/A1物理布局和板内已存驱动。预传是提前传输下一帧，并非局部波形刷新。
- 原项目Shizuku服务中的固定包名、固定用户0查询与授权判断不能原样使用；集成采用随心印包名、当前应用完整UID/APK/编译后端身份校验及与照片刷写共享的互斥锁。
- 不嵌入参考截图、小说、硬件演示图片、原厂替换驱动或来自协议研究项目的Python实现。

## 许可

原始LICENSE、NOTICE、THIRD_PARTY_NOTICES及CREDITS原样保存在 `third-party/inkreader/`。APK内保留 `assets/licenses/inkreader-LICENSE.txt`、`inkreader-NOTICE.txt`、`inkreader-THIRD-PARTY-NOTICES.txt`。

Required Notice: Copyright (c) 2026 kur0neko

InkReader: Copyright (c) 2026 pjk2336 and contributors.

运行时LZO1X压缩布局的上游归属和MIT文本保留在 `assets/licenses/inkreader-lzokay-LICENSE.txt`，涉及 [encounter/lzokay-rs](https://github.com/encounter/lzokay-rs) / jackoalan/lzokay，Copyright 2021 Luke Street。Shizuku原有MIT许可继续随包提供。引用协议资料不代表原作者为集成项目背书。

## 自动检查与实际验收

`tests/reader/run_tests.py`在独立输出目录运行Fake APDU协议、预传/刷新分离、压缩与A0/A1布局、章节识别、实际搜索函数和实际导入编码/字节限额helper，以及NFC互斥锁的完整UID路径隔离/无效路径拒绝。搜索只在SQLite边界使用测试替身。`lzokay==2.1.0`是仅用于开发主机的独立解码依赖，不放入APK或源码包；所有导出的LZO样本都需由该解码器核验，不能仅比较编码器自身结果。

`tests/android/ReaderNativeChecks.java`为设备端保留真实ContentResolver/SQLite导入及StaticLayout分页测试，使用独立临时目录、数据库和偏好设置。覆盖UTF-8/UTF-16/GB18030、重复导入、超长单行的48Ki UTF-16分块/表情符号边界、取消清理、连续分页、A0/A1硬黑白输出和手机绘制边界；不连接Shizuku或NFC。需要在Android仪器测试中实际运行才能验收这些路径。

JVM检查不执行Android字体排版、数据库/ContentResolver、Shizuku授权、厂商NFC或实体屏幕波形。原生测试编译也不等同于这些用例已经运行。尤其跨服务锁的SELinux权限、后台/锁屏行为、音量键及实际刷新耗时，仍需目标小米手机和墨水屏单独验收。

当前如只使用板内原厂驱动和D4执行既有流程，不能因预传命中、传输压缩或轮询变快就称为局刷。局部/差分刷新必须另有可核验的协议与物理表现证据。

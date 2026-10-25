# 随心印 · Suixinyin

把照片、文字和日常信息，留在无源四色墨水屏上。

随心印是一款面向兼容华为 nova NFC 墨水屏手机壳的 Android 应用。它把照片创作、四色处理、日历天气、内容卡片和 TXT 阅读放在同一个 App 中，支持内置无线调试激活，无需为本应用单独安装 Shizuku 管理器。

**[下载首版 APK](https://github.com/czpwpq/suixinyin/releases/latest)** · [首版发布说明](docs/RELEASE_V1_0_0.md) · [内置激活操作](docs/GUIDE_2_20_TEST3.md) · [来源与致谢](CREDITS.md) · [完整来源/许可证](docs/OPEN_SOURCE_SOURCES.md)

> 首个公开版本标记为 **v1.0.0**。为保持与已确认可用的安装包完全一致，本次 APK 内部版本保留 **2.20-test3（versionCode 33）**，没有为改版本名而重新构建或更换签名。

## 功能

主导航为「照片 / 阅读 / 设备 / 日志」，默认采用统一的灰白黑界面。照片、日历、便签和内容卡片都遵循「编辑 → 完成 → 首页预览 → 明确推送」的流程。

| 模块 | 已实现功能 |
| --- | --- |
| 照片与拍立得 | 本机选图、拖动与双指缩放、横竖构图、填满、旋转、镜像、重置、撤销/重做、草稿恢复、原图对比。 |
| 文字与相框 | 多文字图层、位置/大小/旋转、字体/粗体/对齐、四色文字、底色与描边；无相框、留白、细线、双线、拍立得、胶片等样式及边框颜色/宽度。 |
| 四色图像处理 | 本地 InkSight 增强、适配与默认 Atkinson 四色处理；保留高级图像参数及抖动/色块简化对照入口。四色预览可另存为 PNG。 |
| 推送反馈 | 推送按钮显示连接、传输百分比与刷屏状态；可停止当前任务。完成后锁定按钮防止误触重传，需要从「⋯ → 再次推送当前内容」明确重发。 |
| 日历与天气 | 多套横竖版式、月份与日期选择、重点日期标记、黄色元素、大字与粗体排版；手动填写天气，或搜索城市后主动获取天气。 |
| 便签 | 直接输入文字，调整字号、粗体、文字/底色/描边颜色，生成清晰的四色文字卡片。 |
| 今日历史 | 按月日获取历史事件，可选择条目、编辑内容后排版为卡片。 |
| 科技与 AI 简报 | 科技热榜、AI 资讯简报或合并视图，可选条目并编辑。这里的 AI 简报是来源返回的资讯摘要，**没有接入生成式 AI 模型或云端重绘**。 |
| 每日语录与书籍 | 随机语录与本地轮换书单，支持编辑与切换；书籍推荐理由为编辑文字，不是作者原句，也不是销量或新书排行榜。 |
| 动漫人物生日 | 获取今日生日角色，多位时可选择；大图画像区展示人物，并排版姓名、动画作品及生日信息。画像按需获取。 |
| TXT 阅读 | 本地书架、多选导入、编码识别、章节/大章节分块、目录、搜索、书签和进度保存；可调字号、字体、行距、边距、亮度及阅读配色。 |
| 墨水屏翻页 | 音量下键下一页、上键上一页；真实 LZO 压缩、下一页预传和命中后的 D4-only 翻页，减少传图等待。 |
| 时钟与轮播 | 高级设置保留数字/指针时钟及定时刷写、多图本地轮播；轮播最多保存 30 张，可多选或分次添加。运行条件见下方限制。 |
| 设备与诊断 | A0/A1 手动选择、NFC/控制器检测、只读屏幕检查、传输参数、运行日志复制/保存、内置激活诊断与原厂刷新 A/B 对照。 |

## 设备与兼容性

- 安装最低要求为 Android 8.0（API 26）及 NFC 硬件；**单 App 内置无线激活需要 Android 11 或以上**。
- 目标屏幕为 **552 × 768、黑/白/黄/红四色**，支持与已有原厂驱动相符的 A0/A1 布局。必须按实物选择；App 无法自动识别实体屏幕型号，也不会把 A1 驱动板改装为 A0。
- 当前连接依赖手机控制器及系统固件提供的厂商 Q-Poll/QTag 通道。支持普通 NFC、公交卡或门禁卡，不代表能驱动这个手机壳。
- 已收到小米 17 Pro / Android 16 用户对本次集成版本可用的反馈。这是具体设备与系统组合的反馈，不代表所有小米、所有系统版本或全部面板均兼容。
- 识别到标签、传完图片与完成屏幕刷新是不同阶段。刷新还取决于线圈位置、贴合、持续供电和屏幕负载。

## 快速开始

### 1. 安装并选择屏幕

从 [Releases](https://github.com/czpwpq/suixinyin/releases/latest) 下载 APK。已有同签名开发版可直接覆盖安装，保留本机草稿、书库和配对身份；自行重新签名的包无法覆盖不同签名的安装。

打开「设备」，按实物选择 A0 或 A1，并开启手机 NFC。

### 2. 激活本应用

推荐使用「设备 → 内置激活」：

1. 手机连接 Wi-Fi，在系统开发者选项开启无线调试。
2. 点击「开始通知栏配对」，再打开系统「使用配对码配对设备」弹窗，保持弹窗开启。
3. 下拉并展开随心印通知，点击输入按钮。发现端口后可只填六码；没有发现时，按 `配对端口 空格 六位码` 填写，例如 `37123 012345`（请替换为实际数字，不填写 IP）。
4. 配对成功后，使用无线调试首页当前的**连接端口**完成第③步激活。配对端口与连接端口不同。
5. 等随心印明确显示 **「内置服务已激活」**，再返回照片或阅读页使用。系统的「已连接到无线调试」只表示 ADB 通道，并不等于授权服务已经就绪。

配对身份保存在本机私有目录，通常不必每次重配；手机重启、撤销配对或 App 进程被完全结束后，可能需要重新连接激活。内置服务只供随心印使用，不替其他应用提供 Shizuku 授权。

也可以在「设备 → 连接与授权」选择**外置 Shizuku**；照片/检测链路还保留**直接 Root**方式。阅读使用内置激活或外置 Shizuku。完整步骤见[激活说明](docs/GUIDE_2_20_TEST3.md)与[通知配对说明](docs/GUIDE_2_20_TEST2.md)。

### 3. 编辑并推送

在「照片」选图或进入日历、便签、内容卡片。编辑完成回首页核对最终预览，贴稳手机与墨水屏，再点击推送。保持应用在前台、手机解锁并持续供能，直到本轮结束。

图片完成传输后，屏幕仍可能处于物理刷新阶段；请以 App 的本轮结束状态和实屏画面共同确认。失败、停止或失联不会自动重发。

### 4. 阅读 TXT

进入「阅读 → ＋ 导入 TXT」，可多选或分批导入。支持 UTF-8、带 BOM 的 UTF-16 与 GB18030，每本最多 32 MiB；导入文件保存在本机书库，原文件不改动。

打开书籍后可使用界面按钮或音量键翻页：**下键下一页，上键上一页**，长按不连续触发。普通手机阅读离线可用；使用手机壳时，贴稳后明确点击连接。离开阅读、打开子页面或锁屏会暂停阅读 NFC 会话，回来后手动连接。

「息屏翻页」是保持应用前台的黑色覆盖层，不是 Android 锁屏。TXT 输出为硬黑白；下一页预传减少传输等待，但仍使用原厂全局刷新。详见[阅读说明](docs/GUIDE_2_19.md)，其中旧版的仅 Shizuku 授权描述已由本版内置激活扩展。

## 已知限制

- **当前没有真正的局部/差分刷新。** LZO、预传、D4-only 命中和较短等待不改变原厂波形，也没有“翻 5–10 页再全刷”模式。
- 保留板内原厂驱动，不写入替换 DA/DB 配方；App 的 A/B 对照是原厂刷新观察与测时工具，不能单凭边框不闪就认定支持局刷。
- 时钟与轮播需要应用前台、手机解锁并持续贴合供电；每轮完成后再计时。离开应用、锁屏、失联、失败或结果未确认会停止任务，首轮后只接受同一目标 UID。不是手机壳脱离手机后的离线时钟，也不是后台无人值守推送。
- 四色屏只能使用四种实色，通过抖动近似其他颜色；手机预览与反射式实屏的色彩、亮度和层次会有差异。
- 社区/热门图库、用户上传、点赞和排行**暂缓接入**，不属于本次公开版本已提供的在线服务。
- 没有云端照片重绘、GPT 图像分析或 WorkBuddy 集成。

## 联网、数据来源与隐私

照片处理、卡片排版与 TXT 阅读在本机进行，不上传原图或小说到图像生成服务。天气、资讯、语录、生日名单/画像仅在你主动获取时请求对应服务；内置 ADB 固定连接本机 `127.0.0.1`。

| 内容 | 来源与说明 |
| --- | --- |
| 城市与天气 | [Open-Meteo](https://open-meteo.com/)，手动城市查询，不申请定位权限；也可手动填写。 |
| 今日历史、科技热榜、AI 资讯 | 默认兼容 [60s API](https://60s.viki.moe/)；历史/简报可在内容源设置里改为自己的兼容 HTTPS 服务。公共服务有其使用条件、可用性及限额。 |
| 随机语录 | [一言 Hitokoto](https://hitokoto.cn/)，署名以来源提供的信息为准。 |
| 动漫人物生日与画像 | [AniList](https://anilist.co/)，当日查询、作品关联筛选，画像按需获取；名单可能不完整，获取失败会明确提示。 |
| 书籍 | 11 部本地轮换编辑书单，相关链接指向 [维基文库](https://zh.wikisource.org/)或 [Project Gutenberg](https://www.gutenberg.org/)作品/书目信息。 |

联网内容及图片的权利属于原作者或相应提供方，请遵守各来源条件。失败、限流或没有当天数据时不会把旧内容冒充当天结果。配对码、私钥和激活 nonce 不写入常规诊断；运行日志可能含设备型号、标签 UID、传输参数和本机网络地址，分享前请按需要检查。

## 来源、许可证与致谢

随心印基于社区公开研究和已有项目继续开发。感谢原作者们提供协议研究、代码、算法、工具与文档；本项目不把这些成果宣称为自行原创，致谢也不表示原作者参与维护或为本项目背书。

| 仓库 / 项目 | 在本项目中的用途 |
| --- | --- |
| [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app) | Android App 开发基线、原有连接与 NFC 刷写实现。 |
| [kur0neko/huawei-epd-pn532](https://gitee.com/kur0neko/huawei-epd-pn532) | 华为墨水屏 NFC 协议逆向、D3/D4 与原厂扫描布局。Required Notice: Copyright (c) 2026 kur0neko。 |
| [solar2ain/ink-nfc-fmsc-by-pn532](https://github.com/solar2ain/ink-nfc-fmsc-by-pn532) | D8/APDU、LZO 格式及 A0/A1 映射等公开协议研究；未嵌入其 Python 实现或替换驱动。 |
| [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo) | 原生照片工作台、构图/文字/相框与撤销重做，以及本地 InkSight 处理移植来源。 |
| [datascale-ai/inksight](https://github.com/datascale-ai/inksight) | 四色调色板、照片增强与 Atkinson 处理算法参考。 |
| [python-pillow/Pillow](https://github.com/python-pillow/Pillow) | 图像增强及 Lanczos 适配的算法兼容参考；未嵌入 Python/Pillow 运行时。 |
| [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader) | TXT 书架、分页、阅读交互及真实 LZO/预传链路的移植来源。 |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)、[Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | 外置授权 API 与内置专用 Binder 服务交付设计参考；没有打包完整 Shizuku 管理器。 |
| [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android) | 本机无线 ADB 配对、连接和流协议代码，加入超时、取消与流状态修复。 |
| [MuntashirAkon/spake2-java](https://github.com/MuntashirAkon/spake2-java)、[spake2-c](https://github.com/MuntashirAkon/spake2-c) | 官方 SPAKE2 Android JNI 与相应源码，版本 2.2.1。 |
| [google/conscrypt](https://github.com/google/conscrypt)、[google/boringssl](https://github.com/google/boringssl) | 配对与 ADB TLS 支持及底层密码学组件。 |
| [bcgit/bc-java](https://github.com/bcgit/bc-java) | RSA/X509、HKDF/AEAD 等密码学依赖。 |
| [androidx/androidx](https://github.com/androidx/androidx) | AndroidX 注解依赖。 |
| [encounter/lzokay-rs](https://github.com/encounter/lzokay-rs)、[jackoalan/lzokay](https://github.com/jackoalan/lzokay) | LZO 格式/实现参考，以及开发主机独立解码验证。 |

另感谢用户提供的 [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab) 社区学习参考，以及 [atunverse/moink](https://github.com/atunverse/moink) 的交互需求参考。本版没有据此宣称移植它们的源码、素材或算法参数；moink 的页面、素材和源码未复制进本 App。miaomo 声明的其他裁切/图层交互参考与依赖内部的作者，也保留在[完整致谢](CREDITS.md)和[逐项来源清单](docs/OPEN_SOURCE_SOURCES.md)中。

项目整体包含 **PolyForm Noncommercial 1.0.0** 许可的派生部分，请遵守[主许可证](LICENSE)的非商业条款。各组件仍遵守各自许可证。ZhaoGY-N 基线未单列顶级 LICENSE，本项目保留原有声明，不为上游未单列授权的部分重新指定许可。完整版权、固定提交、修改范围和许可文本见：

- [完整来源与致谢](CREDITS.md)、[逐项来源/固定版本/借用范围](docs/OPEN_SOURCE_SOURCES.md)及[完整第三方声明](THIRD_PARTY_NOTICES.md)
- [照片工作台来源](docs/PHOTO_STUDIO_PROVENANCE.md)与[对应原始通知](third-party/miaomo/PROVENANCE.md)
- [阅读模块来源](docs/READER_PROVENANCE.md)
- [内置激活依赖、对应源码与 LGPL 替换构建说明](third-party/activation/PROVENANCE.md)
- [APK 中分发的许可证资产](assets/licenses/)

## 自行构建与验证

需要 Python 3、JDK 17、Android SDK platform `android-34` 和 build-tools `34.0.0`。设置 `JAVA_HOME`、`ANDROID_HOME` 后，在仓库根目录执行：

```sh
python build.py
```

脚本直接使用 SDK 命令行工具，支持 Windows 工具路径；不依赖 Gradle，也不会在构建时下载依赖。固定 Java/AAR/JNI 依赖位于 `libs/`。APK 和 SHA-256 文件输出到 `dist/`。首次本地构建会生成本地签名；请自行保存自己的签名密钥，发布者的私钥不在仓库中。

主机和原生检查入口：

```sh
python tests/run_java.py
python tests/reader/run_tests.py
python tests/activation/run_tests.py
python tests/activation/run_adb_crypto_tests.py
python tests/android/build_card_tests.py
```

阅读独立解码检查需要主机依赖 `lzokay==2.1.0`，可安装到相邻的 `../reader-test-deps`，或通过 `READER_TEST_DEPS` 指定目录。激活检查需要 POSIX Shell（Windows 可用 Git Bash）及本机 loopback 网络权限；fake ADB 测试只连接 `127.0.0.1`，不会连接手机或刷屏。

构建前已执行主机回归、独立 LZO 解码、激活请求与真实 Shell 命令、本机 ADB 流及密码学检查，并验证 APK 签名。Android Instrumentation 用例只完成构建，**没有在设备或模拟器执行**。发布前用户确认当前集成版本可用，不等于所有机型或每个功能组合均已完整验收。构建时细节见[验证记录](docs/VALIDATION_2_20_TEST3.md)。

旧版本说明保存于[开发历史](docs/DEVELOPMENT_HISTORY.md)。反馈问题时请说明手机型号、系统版本、屏幕类型、所选授权方式及失败阶段，可附脱敏诊断；无需提供配对码、私钥或个人原图。

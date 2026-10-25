# 来源与致谢

随心印建立在 NFC 墨水屏社区公开的协议研究、Android 实现、图像算法和工具库之上。感谢下列项目的作者和贡献者；协议发现、上游代码及算法的成果不属于随心印独创。

本页对应首个公开版本 `v1.0.0`，APK 内部版本保留已验证的 `2.20-test3`。精确版本、提交、借用范围和许可文件位置见 [完整来源清单](docs/OPEN_SOURCE_SOURCES.md) 与 [第三方声明](THIRD_PARTY_NOTICES.md)。

## 主要项目

| 项目与作者 | 对随心印的贡献 |
| --- | --- |
| [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app) | 本项目直接使用的 Android 基础：Q-Poll、Shizuku/Root、A0/A1 图像打包与传输、屏幕检查及构建/测试结构。感谢原作者提供可继续开发的起点。 |
| [kur0neko/huawei-epd-pn532](https://gitee.com/kur0neko/huawei-epd-pn532)（氧化钙硼） | 华为 NFC 墨水屏逆向研究、D3 传图/D4 刷新及原厂扫描布局，是底层实现的重要基础。 |
| [solar2ain/ink-nfc-fmsc-by-pn532](https://github.com/solar2ain/ink-nfc-fmsc-by-pn532)（海猫混合物） | 公开的 D8/APDU、LZO1X 帧格式、A0/A1 映射及原厂驱动行为资料；用于协议学习和对照验证。未打包其 Python 实现或驱动配方。 |
| [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo) | 原生照片工作台、构图/文字图层/相框、撤销重做、本地图像适配和 InkSight 四色处理的直接移植来源。 |
| [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader) | TXT 书架、导入、章节、分页、搜索、书签、音量键翻页，以及真实 LZO/下一页预传阅读链路。 |
| [datascale-ai/inksight](https://github.com/datascale-ai/inksight) | 经 miaomo 引入的四色色板、照片增强和 Atkinson 图像处理参考。 |
| [python-pillow/Pillow](https://github.com/python-pillow/Pillow) | 经 miaomo 引入的 Pillow 兼容增强、Lanczos 适配算法参考；APK 不包含 Python/Pillow 运行时。 |

Required Notice: Copyright (c) 2026 kur0neko

## 授权桥、无线 ADB 与基础库

| 项目 | 使用范围 |
| --- | --- |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) 与 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | 外置 Shizuku 接入；内置专用服务也参考其 app_process、Provider/Binder 交付设计。没有将完整 Shizuku 管理器改名打包。 |
| [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android) | 真实无线 ADB 配对/连接及数据流实现；本地修改包括超时、取消、流状态修复和仅连接本机。 |
| [MuntashirAkon/spake2-java](https://github.com/MuntashirAkon/spake2-java) 与 [spake2-c](https://github.com/MuntashirAkon/spake2-c) | Android SPAKE2 JNI 配对实现及对应 C 源码。 |
| [google/conscrypt](https://github.com/google/conscrypt) 与 [BoringSSL](https://boringssl.googlesource.com/boringssl/) | TLS 1.3 与其底层密码学实现。 |
| [bcgit/bc-java](https://github.com/bcgit/bc-java) | RSA/X509、HKDF/AEAD 等配对所需密码学组件。 |
| [androidx/androidx](https://github.com/androidx/androidx) | AndroidX annotation。 |
| [encounter/lzokay-rs](https://github.com/encounter/lzokay-rs) 与 [jackoalan/lzokay](https://github.com/jackoalan/lzokay) | 阅读压缩布局参考及开发主机上的独立解码校验；不将 Rust/Python 解码器作为 APK 运行时依赖。 |

上述库内部的其他作者、OpenSSL/SSLeay、Netty、Apache Harmony 等版权和许可也一并保留，见 [内置激活来源及完整许可](third-party/activation/PROVENANCE.md)。

## 交互参考与间接来源

- [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab)：用户提供的学习参考，也是 miaomo 声明的 Android 基础来源。本项目未移植其 ST54L 后端，不继承其硬件兼容性结论。
- [atunverse/moink](https://github.com/atunverse/moink)：用户提供的拍立得、便签及同页编辑交互参考。未复制其源码、素材、网页或算法参数，也未打包其 ESP32 固件或 Cropper.js。
- miaomo 的来源记录还列出 [burhanrashid52/PhotoEditor](https://github.com/burhanrashid52/PhotoEditor)、[Yalantis/uCrop](https://github.com/Yalantis/uCrop)、[CanHub/Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) 作为图层/裁切交互参考；随心印未将这些 SDK 作为运行时依赖。

## 内容服务

感谢 [Open-Meteo](https://open-meteo.com/) 的城市和天气服务、[vikiboss/60s](https://github.com/vikiboss/60s) 的历史/科技/AI 资讯接口、[一言 Hitokoto](https://hitokoto.cn/) 的语录及 [AniList](https://anilist.co/) 的角色生日/作品信息。书目外链指向 [维基文库](https://zh.wikisource.org/) 和 [Project Gutenberg](https://www.gutenberg.org/)。这些属于用户主动获取的内容服务或外链，服务软件许可不等于文字、图片、人物形象等内容版权授权；数据和服务的使用仍遵循各自条款。

## 许可说明

本项目不是将所有借用代码统一改成 MIT。根目录 [LICENSE](LICENSE) 保留 PolyForm Noncommercial 1.0.0 文本；适用该许可的协议、照片和阅读组件须遵循非商业条款。MIT、Apache、HPND、LGPL 等第三方组件继续按其原许可提供。SPAKE2 LGPL 部分的对应源码、替换与重新构建说明已随源码分发。

ZhaoGY-N 基线仓库未单列顶级 `LICENSE`；它的 README 与第三方声明明确列出所参考协议代码的 PolyForm 和依赖许可。本项目保留这些声明，不声称对上游未单列授权的部分取得额外授权，也不为其重新指定许可。具体范围见 [完整来源清单](docs/OPEN_SOURCE_SOURCES.md)。

感谢原作者、社区研究者以及参与真机反馈的用户。署名和致谢不代表原作者为随心印背书、参与本次修改或承诺其兼容性。

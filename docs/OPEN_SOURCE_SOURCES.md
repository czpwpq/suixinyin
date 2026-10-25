# 源码、协议、算法与依赖来源

本清单对应首次公开发布 `v1.0.0` / APK 内部版本 `2.20-test3`。版本固定指本次实际采用的源码或工件，不表示已跟踪上游最新版本。尚未单独固定版本的间接参考会明确标出，不臆造提交号。

## 直接基础、移植与协议参考

| 来源 | 本次版本/提交 | 使用范围与许可记录 |
| --- | --- | --- |
| [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app) | [`f16adae5aae1da0db387f83abe4331ca6019a22d`](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app/tree/f16adae5aae1da0db387f83abe4331ca6019a22d) | 直接 Android 基线，原 Q-Poll/Shizuku/Root、屏幕检查、传输、A0/A1 打包和构建测试结构。该基线根目录没有独立 LICENSE；其 README/THIRD_PARTY_NOTICES 说明协议参考代码为 PolyForm Noncommercial 1.0.0、Shizuku 为 MIT。本项目保留这些声明，不将“公开仓库”或根 LICENSE 视为对上游所有部分的额外授权。 |
| [kur0neko/huawei-epd-pn532](https://gitee.com/kur0neko/huawei-epd-pn532) | `5d3acf730a988815b637262fd4862c1794ab079e` | D3/D4、原厂扫描布局等逆向研究与参考实现。PolyForm Noncommercial 1.0.0，原文及 Required Notice 在 `third-party/kur0neko/` 和 APK licenses 资产。 |
| [solar2ain/ink-nfc-fmsc-by-pn532](https://github.com/solar2ain/ink-nfc-fmsc-by-pn532) | [`0f88cb315eab60e538c102e5883c5f957d9c1be0`](https://github.com/solar2ain/ink-nfc-fmsc-by-pn532/tree/0f88cb315eab60e538c102e5883c5f957d9c1be0) | GPL-3.0-or-later 的协议研究仓库，用于公开 D8/APDU、LZO1X 帧格式、A0/A1 映射及打包对照。未嵌入其 Python 模块、原生二进制、DB/DA 驱动配方；当前 App 保留板内原厂驱动。 |
| [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo) | [`499268b34637a5c9e5199ef06e2eda4f9789870a`](https://github.com/3dhaoxinren/miaomo/tree/499268b34637a5c9e5199ef06e2eda4f9789870a) | 照片 UI、场景、文字/相框、历史、图像增强及四色处理的移植来源。PolyForm Noncommercial 1.0.0；原始 LICENSE、NOTICE、依赖许可保留在 `third-party/miaomo/`。未复制其样图、截图、品牌位图、更新服务和 NFC 后端。详见 [组件对照](../third-party/miaomo/PROVENANCE.md)。 |
| [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader) | [`a4b72b79acec37543339b9810bcb34105b061d65`](https://github.com/pjk2336/huawei-eink-reader/tree/a4b72b79acec37543339b9810bcb34105b061d65)，0.3.9 | TXT 导入/书架/分页/目录/搜索/书签/设置/音量键及 LZO/预传阅读链路。PolyForm Noncommercial 1.0.0，完整 LICENSE/NOTICE/CREDITS/第三方声明保留在 `third-party/inkreader/`。品牌、主导航、服务身份和生命周期按随心印改编；不带上游小说、截图或测试照片。详见 [阅读来源](READER_PROVENANCE.md)。 |

Required Notice: Copyright (c) 2026 kur0neko

## 图像算法与压缩

| 来源 | 固定范围 | 使用与许可 |
| --- | --- | --- |
| [datascale-ai/inksight](https://github.com/datascale-ai/inksight) | 经上述 miaomo 固定提交移植，未另行声称固定 InkSight 独立提交 | 四色色板、照片增强、Atkinson 等参考。MIT，Copyright (c) 2026 datascale-ai；原文在 `third-party/miaomo/THIRD-PARTY-LICENSES.txt` 及 APK 同名许可资产。 |
| [python-pillow/Pillow](https://github.com/python-pillow/Pillow) | 10.1.0 的兼容算法，经 miaomo 移植 | 增强、Lanczos 和相关行为参考，HPND；保留 PIL/Pillow 版权。APK 没有 Python 或 Pillow 运行时。 |
| [encounter/lzokay-rs](https://github.com/encounter/lzokay-rs) / [jackoalan/lzokay](https://github.com/jackoalan/lzokay) | 阅读编码布局经上述 reader 固定提交；开发主机独立解码 `lzokay==2.1.0` | MIT。保留上游压缩归属，Luke Street 等版权与完整文本在 `assets/licenses/inkreader-lzokay-LICENSE.txt`。主机 Rust/Python 解码依赖不打包进 APK；不是将 GPL LZO 原库作为本 App 的运行时依赖。 |

## 授权与无线 ADB 运行依赖

| 来源 | 本次版本/提交 | 本次分发与原许可 |
| --- | --- | --- |
| [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | api/provider/aidl/shared 13.1.5，固定本地 AAR | 外置授权接入。MIT；版权与许可在 `assets/licenses/Shizuku-LICENSE.txt`。 |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) | ServiceStarter / UserService / Provider 公开设计参考；不是整套管理器移植，未独立固定 server 二进制 | 专用 app_process/Provider/Binder 交付设计参考，MIT。随心印内置后端只供本 App，不替其他应用提供 Shizuku 授权，也未打包管理器 APK。 |
| [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android) | 3.1.1，`c849886ebc6d48e7b46d967e78a6bb65c90c3b74` | 源码在 `src/io/github/muntashirakon/adb/`。上游 Apache-2.0 或 GPL-3.0-or-later 双许可，本次选择 Apache-2.0，并保留文件内 BSD/MIT 等原声明；修改范围详见下方 PROVENANCE。 |
| [MuntashirAkon/spake2-java](https://github.com/MuntashirAkon/spake2-java) | 2.2.1，`7615ddd680b990e14513ebb66eac4cb0dbf82464` | 官方 Android AAR 的 JNI 及同版两个 Java facade。LGPL-3.0；未采用实验性纯 Java 数学模块。 |
| [MuntashirAkon/spake2-c](https://github.com/MuntashirAkon/spake2-c) | `0d15933e5ba3e662cb01245a7ac0dc9fca3eac31` | SPAKE2 Android 的 C 子模块，对应源码随 `third-party/activation/spake2-source/` 保留。整体 LGPL-3.0-or-later；各 C 文件的 Apache/MIT/LGPL-2.1 等原声明继续适用，未用 App 的非商业许可替换。 |
| [google/conscrypt](https://github.com/google/conscrypt) | 2.5.3，`fd209de6167201e4f3bca5b99c6db9f08d1843c6` | TLS/JNI，Apache-2.0；保留其 Netty、Apache Harmony 通知。 |
| [BoringSSL](https://boringssl.googlesource.com/boringssl/) | Conscrypt 工件声明的 `49f0329110a1d93a5febc2bceceedc655d995420` | 经 Conscrypt 包含的密码学组件；完整 ISC、OpenSSL/SSLeay、MIT 等原许可集合保留在 `third-party/activation/BoringSSL-LICENSE.txt`，不简化成“全部 Apache”。 |
| [bcgit/bc-java](https://github.com/bcgit/bc-java) | `bcprov-jdk15to18:1.81` | Bouncy Castle 原许可（MIT 风格），保留 `BouncyCastle-LICENSE.html`；用于证书/配对所需密码学，不安装全局替换 Provider。 |
| [androidx/androidx](https://github.com/androidx/androidx) | annotation 1.3.0 | Apache-2.0，固定 JAR。 |

上述工件 SHA-256、修改记录、完整许可证、SPAKE2 对应源码及可替换/重新构建说明见 [内置激活 PROVENANCE](../third-party/activation/PROVENANCE.md)。APK 包含 `assets/licenses/activation-LICENSES.txt`。库用户对 LGPL 部分进行修改、替换、重新链接及为调试这些修改而逆向的权利，不因 App 的其他许可而被移除。

## 没有作为运行时依赖的参考

| 来源 | 归属边界 |
| --- | --- |
| [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab) | 用户给出的参考，也是 miaomo 上游来源记录里的 Android 基础。未在本项目另行 vendor 一个固定版本；未移植其 ST54L 后端或厂商库，不引用其设备成功案例作为本 App 的兼容性保证。 |
| [atunverse/moink](https://github.com/atunverse/moink) | 用于理解拍立得/便签/同页编辑需求。原参考记录显示版权保留、未提供可再分发许可证，因此没有移植源码、网页、素材、算法参数或 ESP32 固件；本页不替该项目授权。其使用的 Cropper.js 也未嵌入。 |
| [burhanrashid52/PhotoEditor](https://github.com/burhanrashid52/PhotoEditor)、[Yalantis/uCrop](https://github.com/Yalantis/uCrop)、[CanHub/Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) | miaomo 来源记录中的图层/裁切交互参考。保留该间接来源说明；本 App 未打包这些 SDK，不冒称其源码是本次新增依赖。 |

## 内容接口与外链

这些属于数据获取与链接，不是 vendored Android 代码。服务可能变化、限额或要求使用授权；代码仓库许可不等于数据、图片或人物形象的内容许可。

| 来源 | 使用范围 |
| --- | --- |
| [Open-Meteo](https://open-meteo.com/) / [open-meteo/open-meteo](https://github.com/open-meteo/open-meteo) | 用户主动查询城市与天气；记录服务返回的时间和来源，不上传照片。 |
| [vikiboss/60s](https://github.com/vikiboss/60s) | 今日历史、科技与 AI 资讯的兼容接口。返回来源包含百度百科、IT之家、AI工具集；不把转供服务的代码许可当作新闻内容许可。公共源存在使用条件，App 提供自定义服务根地址。 |
| [Hitokoto / 一言](https://hitokoto.cn/) | 主动获取随机语录，署名由来源提供。 |
| [AniList](https://anilist.co/) / [API 文档](https://docs.anilist.co/) | 主动获取角色生日、姓名、作品及画像地址。人物画像与角色版权仍属于原权利人。 |
| [维基文库](https://zh.wikisource.org/) / [Project Gutenberg](https://www.gutenberg.org/) | 本地书目推荐的原文/书目外链，不随 APK 内置整本作品或封面。 |

## 发布与署名范围

本清单只说明实际来源，不把所有组件重新许可为 MIT，也不把全部组件都放进本 App 的 PolyForm 条款。各文件原许可、Required Notice 和对应源码要求须同时保留。ZhaoGY-N 上游未单列的项目级授权不能由本清单补造；本项目的改动和根 LICENSE 不宣称授予任何原权利人未授予的权利。

本项目未分发用户原始手机日志、私有照片、签名私钥、配对密钥、从手机提取的厂商 APK/JAR、系统镜像或 NFC 固件。致谢不意味着上游作者参与或认可随心印发布。

# 随心印 v1.0.0 · 首个公开版本

这是随心印的首个 GitHub 公开版本，基于用户已确认可用的集成版本发布。为保留已经安装测试的二进制和签名，APK 内部版本继续使用 **2.20-test3（versionCode 33）**；公开 Release 标签为 **v1.0.0**。本次公开发行没有仅为了改版本号而重新构建 APK。

## 本版功能

- **照片创作**：选图、拖动/双指缩放、横竖构图、填满、旋转、镜像、撤销重做、草稿恢复和原图对比。
- **文字与相框**：多文字图层、字体/大小/位置/旋转/对齐、四色文字、底色和描边；留白、细线、双线、拍立得、胶片等相框，支持调整颜色与宽度。
- **本地四色处理**：InkSight 增强、适配与默认 Atkinson 四色处理；保留高级图像/抖动/色块简化对照入口，四色预览可保存为 PNG。
- **统一编辑与推送**：照片、日历、便签和内容卡片编辑完成后回首页预览，再明确发送；推送按钮显示进度，完成后防误触重传，支持停止与明确重发。
- **日历与天气**：多套横竖版式、日期标记、大字/粗体和黄色元素；手动填写或搜索城市主动查询天气。
- **便签**：直接输入文字，设置字号、粗体、四色文字/底色/描边。
- **每日内容卡片**：今日历史、科技热榜与 AI 资讯摘要、随机语录、本地轮换书单；支持选取与编辑。AI 简报没有接入生成式 AI 或云端重绘。
- **动漫人物生日**：选择今日生日人物，按需获取大图画像，展示姓名、关联动画及生日。
- **TXT 阅读**：多选导入、本地书架、编码识别、大章节分块、目录、搜索、书签、进度及排版设置；音量下键下一页，上键上一页。
- **阅读提速**：真实 LZO 压缩、下一页预传与命中后 D4-only 翻页；仍使用原厂全局刷新。
- **单 App 内置激活**：本机无线 ADB、通知栏配对、本机端口发现、独立授权服务及诊断；连接、启动、交付和断开 ADB 后复验分阶段显示。外置 Shizuku 及照片/检测的直接 Root 方式保留备用。
- **设备与维护**：A0/A1 手动选择、检测/只读检查、传输参数、日志复制/保存、原厂刷新 A/B 对照，以及高级设置中的时钟/多图轮播。

## 安装与首次使用

1. 下载 Release APK，已有同签名开发版可覆盖安装，保留配对身份、草稿与书库。
2. 在「设备」按实物选择 A0 或 A1，开启 NFC。
3. 使用「内置激活」按指引完成无线调试配对，再使用无线调试首页的连接端口激活；必须等待 App 显示“内置服务已激活”，不能把系统“已连接无线调试”当作完成。
4. 返回照片或阅读页，贴稳并保持前台供电，再明确推送/连接。

已成功配对的开发版用户通常无需重配，覆盖安装后使用当前连接端口重新激活即可。[详细操作](GUIDE_2_20_TEST3.md)。

## 兼容性与限制

- 目标为兼容原厂配置的 552×768 黑白黄红 A0/A1 屏；需手机控制器及固件支持 Q-Poll/QTag，不能凭品牌或“有 NFC”保证可用。
- App 最低 Android 8.0；内置无线激活需要 Android 11+。已收到小米 17 Pro / Android 16 用户对本次集成版本可用的反馈，其他设备/功能组合仍需验证。
- 当前是**原厂全局刷新**，没有局刷或“每 5–10 页再全刷”。保留板内驱动，不写替换 DA/DB。
- 时钟/轮播需要前台、解锁、持续贴合供电；离开、锁屏、失联或失败会停止。首轮后锁定同一目标 UID，不是后台无人值守或离线自动更新。
- 热门/社区图库及上传、点赞、排行暂缓；没有云端 AI 重绘或 WorkBuddy 集成。
- 联网内容由各来源提供，可能限流或不可用；图片内容及数据权利遵守各来源条件。照片处理与普通 TXT 阅读在本机完成。

## 验证

已通过主机回归、阅读协议与 4005 组独立 LZO 解码、激活证明/输出脱敏、真实 Shell 启动命令（替换为无害假程序）、本机 fake-ADB 流与密码学检查，以及 APK 构建、对齐和签名验证。

Android Instrumentation 用例仅构建，未在设备/模拟器执行。用户已确认本次集成版本可用；这不表示所有机型或所有功能均经过完整实机验收。[构建时验证记录](VALIDATION_2_20_TEST3.md)。

已确认 APK 的 SHA-256：

```text
25aabff8eee8f13ce9b166ebf5a61f8b499cb12b8673b204c04481b96e8ef615
```

## 来源与感谢

感谢下列原作者与社区公开代码、协议研究和工具，使本项目能够继续完善：

- [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app)：Android 基线与已有刷写链路。
- [kur0neko/huawei-epd-pn532](https://gitee.com/kur0neko/huawei-epd-pn532)：华为墨水屏协议逆向及扫描布局。Required Notice: Copyright (c) 2026 kur0neko。
- [solar2ain/ink-nfc-fmsc-by-pn532](https://github.com/solar2ain/ink-nfc-fmsc-by-pn532)：公开 APDU/LZO/A0-A1 协议研究。
- [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo)、[datascale-ai/inksight](https://github.com/datascale-ai/inksight)、[python-pillow/Pillow](https://github.com/python-pillow/Pillow)：照片工作台与图像算法来源/兼容参考。
- [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader)：TXT 阅读、分页及 LZO/预传实现来源。
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)、[Shizuku-API](https://github.com/RikkaApps/Shizuku-API)：授权 API 和 Binder 交付设计参考。
- [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android)、[spake2-java](https://github.com/MuntashirAkon/spake2-java)、[spake2-c](https://github.com/MuntashirAkon/spake2-c)：无线 ADB 与真实 SPAKE2 JNI 配对依赖。
- [google/conscrypt](https://github.com/google/conscrypt)、[google/boringssl](https://github.com/google/boringssl)、[bcgit/bc-java](https://github.com/bcgit/bc-java)、[androidx/androidx](https://github.com/androidx/androidx)：TLS、密码学和注解依赖。
- [encounter/lzokay-rs](https://github.com/encounter/lzokay-rs)、[jackoalan/lzokay](https://github.com/jackoalan/lzokay)：LZO 格式/实现参考与独立解码验证。
- [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab)、[atunverse/moink](https://github.com/atunverse/moink)：社区学习与交互需求参考；不宣称复制其源码或素材。

也感谢 Open-Meteo、60s API、一言 Hitokoto、AniList、维基文库和 Project Gutenberg 提供相关数据/内容服务，以及参与实测和反馈问题的用户。

完整致谢、间接交互参考和固定提交、借用范围、版权通知及各组件许可见[来源与致谢](../CREDITS.md)、[逐项来源清单](OPEN_SOURCE_SOURCES.md)、[第三方声明](../THIRD_PARTY_NOTICES.md)及[内置激活依赖说明](../third-party/activation/PROVENANCE.md)。本项目包含 PolyForm Noncommercial 1.0.0 派生部分，遵守[主许可证](../LICENSE)的非商业条款；其他依赖仍遵守各自许可证。感谢不表示原作者参与维护或背书。

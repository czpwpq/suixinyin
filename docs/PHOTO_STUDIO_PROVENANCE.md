# 照片工作台移植来源

照片工作台和 InkSight 本地处理参考 [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo)，固定提交 `499268b34637a5c9e5199ef06e2eda4f9789870a`，对应上游 2026-10-22 的源码状态。

上游项目采用 PolyForm Noncommercial 1.0.0；其中 InkSight 和 Pillow 兼容算法另保留 MIT、HPND 的完整声明。原始许可和通知逐字节保存在 `third-party/miaomo/`，并随 APK 的 `assets/licenses/miaomo-*.txt` 分发。[完整组件对照和修改说明](../third-party/miaomo/PROVENANCE.md)列出了来源范围。

本次移植的是原生照片页面、构图/文字/相框状态、撤销重做，以及本地图像处理。品牌继续使用“随心印”。连接、后端身份检查、NFC 传输与清理继续使用本项目现有实现；上游的标准 NFC、ST54L、更新检查、图标位图和演示照片未引入。日历与天气继续使用已有卡片功能，社区图库暂缓接入。

图像在本机处理，无 InkSight 服务或 API Key。算法参考夹具属于合成测试输入，不能作为实屏显示效果或手机 NFC 兼容性的证明。主机测试验证算法、场景状态和打包；原生 UI 测试使用假的发送 Host，仅编译不代表已在设备执行。实屏刷写、视觉效果与手机适配仍需真机验证。

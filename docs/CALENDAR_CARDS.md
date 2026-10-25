# 日历模板与天气文字

`CalendarCards` 提供四套独立排版：竖屏清爽月历、竖屏大字日期、横屏月历天气、横屏本周与月历。固定使用屏幕黑、白、黄、红色表，黄色用于信息卡、标题带和当前周背景；天气图标使用 Canvas 几何图形，不使用系统彩色 Emoji。

```java
String[] templateNames();
boolean isLandscape(int template);
Bitmap render(int year, int month, int markedDay, String title,
              int accent, int background, int template,
              String city, String weather, String temperature, String weatherUpdated);
```

`template` 是从 0 开始的索引。`templateNames()` 返回副本，可直接用作选择框。API 使用现有 `CalendarModel`，每套月历固定六行，周一到周日；闰年及第六行日期不会遗漏。`markedDay=0` 不标记，日期须在当月内，非法输入抛出 `IllegalArgumentException`。横屏本周模式根据标记日显示从周一到周日的起止日期，正确处理跨月/跨年；未标记时仅显示整月。

返回值始终是物理屏幕尺寸 **552×768**。横屏模板先在 **768×552** 逻辑画布绘制，再顺时针旋转 90°，不改变打包与发送协议。查看横屏内容时应把手机壳逆时针转 90°，编辑器可另外显示逆时针预览。旋转不插值，不引入额外灰度。API 的横竖屏元数据仅影响预览；不要再次旋转发送帧。

天气文字只展示调用方明确提供的信息，不会捏造温度或实时定位。2.13 创作界面可以手动输入，也可主动查询 Open-Meteo：输入城市搜索后选择地点，只发送城市名 / 所选地点坐标，不使用 GPS、定位权限或 API Key，也不上传照片。网络失败可继续手动填写。

模板根据 `weatherUpdated` 的来源标记显示“天气 · Open-Meteo”或“手动天气”；调用方传入服务返回的时间 / 时区及来源，渲染器不自行请求网络。城市、天气、温度和记录时间为空时，分别显示“未填写城市”“尚未填写天气”“-- °C”“未记录时间”。默认未知天气图标为温度计；只有文字明确包含晴/云/阴/雨/雪或对应英文关键词时才使用对应图标。记录时间不应随预览重绘自动改成当前时间；旧天气保留原时间，主动重新获取才更新。手动修改城市、天气或温度后，界面清除联网来源标记，避免继续冒充 API 原始数据。

每个单行文本都限制 Unicode 码点数、缩小字号再按剩余宽度省略，避免长城市名、标题或天气文字溢出。温度与单位按用户原文显示，不添加或猜测单位。前景和背景颜色映射至实际四色色表；主内容按背景选择黑/白文字，强调色等于背景时使用正文色。彩色 Emoji 仍可能由系统字体绘制，应通过最终量化保持成品严格四色。

`tests/android/CalendarCardsNativeChecks.java` 可由设备侧 instrumentation 调用 `run(this)`；它覆盖四套模板、超长中英文文本、缺省天气、黑底/黄强调色、六行月历、闰日、横屏旋转坐标、输出不透明四色及黄色色块存在，并导出视觉核对 PNG。仅通过 javac 编译不代表设备端绘制与字体检查已经执行。现有 `CalendarModelTest` 对公历日期与星期映射的 109938 个日期检查仍适用。

# 本地创作模板

`CardRenderer` 是独立实现的 Android Canvas 排版器，不依赖网页或在线服务。所有成品均为不透明的 552×768 位图。文字和线条关闭抗锯齿，以免把指定的四色文字变成彩色边缘点阵；照片本身仍由发送流水线转换为屏幕色表。调用方拥有返回位图，排版器不会回收或修改传入照片。

## API

- `calendar(year, month1, markedDay, title, accent, background)`：公历月历，周一至周日，固定六行，支持跨世纪闰年。月份为 1—12，年份为 1—9999，`markedDay=0` 不标记；不合法的日期抛出可显示给用户的 `IllegalArgumentException`。
- `clock(epochMillis, zoneId, analog, accent, background)`：数字或指针时钟，按指定时区生成日期、星期及分钟。空时区使用设备默认时区，错误时区抛出异常。画面不显示秒针。**这是一张静态图片，NFC 刷新结束后不会自己走时。** 自动更新时间必须由主程序重新生成并实际刷写。
- `note(text, fontSize, foreground, background, outline, outlineColor, bold)`：中英文自动换行，保留手动换行，居中排版。字号先限制至 16—120；不足时逐步缩小，最低 16，再对末尾加省略号，限制在安全区域内。输入最多处理 4000 个 Unicode 码点，避免超长文本拖慢预览。空文本生成纯底色页面。
- `polaroidPhotoBounds(margin)`：返回拍立得照片可视区域，margin 限制至 12—72。矩形为 `(m,m,552-m,768-m-120)`，底部始终保留 120 像素题字区。
- `polaroid(photo, caption, margin, background, textColor, style)`：照片等比中心裁剪至上述矩形，题字区最多三行，自动缩小至 16，再截断加省略号。照片裁剪与题字绘制均使用独立 clip，避免互相覆盖。自由构图界面应先输出 `photoBounds.width × photoBounds.height` 的照片位图再调用本函数。空题字保留底边。
- `styledPhoto(photo, style)`：原尺寸的新位图，可供无边框照片使用，透明区铺白。style 为 0 原图、1 灰度、2 暖色；其余值按原图处理。

颜色参数为 Android ARGB 整数，Alpha 会强制为 255。日历/时钟根据背景选择黑或白作为正文；强调色与背景相同时自动改用正文色。便签和拍立得题字尊重所选颜色；请在 UI 防止用户选用相同前景/底色。彩色 Emoji 的实际绘制由系统字体提供，应依然经过最终四色量化。

## 验证

`CalendarModelTest` 为纯 Java 测试，覆盖 1900—2200 年全部 109938 个日期：格子连续性、星期对应、闰年世纪规则、第六行、非法月份和日期。

`tests/android/CardRendererInstrumentation.java` 为设备侧测试，不包含于主 APK。它检查模板尺寸/透明度、指定色表、长中文排版不崩溃、拍立得裁剪及题字不越界、输入照片不被修改、风格输出独立所有权、时区和非法输入，并把视觉检查 PNG 写到主应用的 external-files/card-tests。先使用与主构建相同的 `JAVA_HOME`、`ANDROID_HOME` 和签名环境变量运行 `python tests/android/build_card_tests.py`。测试 APK 必须与主 APK 使用相同签名；完成打包安装后执行：

```text
adb shell am instrument -w org.nfcresearch.qpolllab.cardtests/org.nfcresearch.qpolllab.CardRendererInstrumentation
adb pull /sdcard/Android/data/org.nfcresearch.qpolllab.easy/files/card-tests
```

设备侧检查仍需要实际连接的 Android 设备或模拟器。仅编译通过不表示已执行原生字体排版测试，输出 PNG 也需人工核对字形、行距和边缘。

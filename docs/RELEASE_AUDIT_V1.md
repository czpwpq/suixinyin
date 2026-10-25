# 首个公开版本发布核对

日期：2026-10-25。公开版本拟为`v1.0.0`，保留用户已确认可用的APK，不重编或改动程序逻辑。APK内部版本为`2.20-test3`、versionCode33，包名`org.nfcresearch.qpolllab.easy`。

用户在本次对话中确认该版本“没问题”并授权发布。此确认不等于项目维护端已经逐项测试所有机型、功能或Android版本；下面区分文件核对、自动检查和设备验证。

## 发布APK

- 本机`outputs/QPollLab-2.20-test3.apk`与构建输出中的同名APK的SHA-256一致：

  `25aabff8eee8f13ce9b166ebf5a61f8b499cb12b8673b204c04481b96e8ef615`

- 本次再次执行`apksigner verify --print-certs`通过。签名证书SHA-256：

  `a403153d58ed4ffccda357d27a64729079d12b04821cb3365ac611c456ea7198`

- APK内有DEX、四个ABI共8个Conscrypt/SPAKE2原生库和11个许可证资产。证书公钥与证书摘要可公开；签名私钥不随源码或Release上传。
- 首版公开版本号与APK内部开发版本号不同，应在README和Release说明中明确。覆盖升级仍依赖相同包名、签名及递增versionCode。

## 源码与私人文件

- 交付时的源码ZIP为363个文件，完整性校验通过，未包含`.git`、`build`、`dist`、`__pycache__`、APK、日志或签名密钥。首版加入发布文档后文件数会增加。
- 发布候选按源码目录筛选，排除构建缓存、签名私钥、环境变量文件和日志。`.gitignore`已覆盖`build/`、`dist/`、`*.keystore`、`*.jks`、`.env*`、`*.log`及本地SDK配置。
- 对当时315个候选文本文件执行限定模式扫描，未发现个人Windows用户目录、剪贴板附件名、粘贴诊断文件名、常见GitHub/AWS凭据前缀或私钥文本块。未发现候选目录中的用户JPEG/PNG照片、导入小说、运行日志和配对身份文件。
- `tests/inksight-fixtures/`中的RGB与颜色码是已有合成算法夹具，来源说明见`PHOTO_STUDIO_PROVENANCE.md`；它们不是用户相册或实屏效果证明。
- 原工作目录存在旧上游Git历史和大量开发改动；发布采用上游公开仓库的Fork保留可见源码血缘，在干净发布checkout上仅提交本次筛选源码，不上传本机私有原`.git`、工具链、附件或日志。

## 依赖与对应源码

- 原Shizuku 13.1.5 AAR及新增AndroidX annotation、Bouncy Castle、Conscrypt和SPAKE2依赖保存在`libs/`；版本/来源与许可证文档保留。
- 独立计算新增4个依赖的SHA-256，与`third-party/activation/PROVENANCE.md`记录一致。
- SPAKE2对应JNI/C源码、Java facade、CMake和许可文本位于`third-party/activation/spake2-source/`；原始SPAKE2 AAR保持原样，构建仅提取JNI，Java facade从同版源码编译。
- 已有`assets/licenses/`和`third-party/`原文许可/notice继续保留。实际借用范围、版本和感谢以`CREDITS.md`、`THIRD_PARTY_NOTICES.md`和来源文档为准，不把整个组合项目概括成MIT。

## 构建、测试与签名边界

- 交付记录中的主机回归、阅读协议与LZO独立解码、激活输入/证明/输出脱敏、真实Shell假程序及本机fake-ADB Socket检查均通过。
- 主APK及原生Instrumentation APK构建、资源/DEX/JNI打包、对齐和签名记录通过。原生Instrumentation只完成编译，未在维护端Android设备或模拟器执行。
- `build.py`使用JDK17、Android platform34/build-tools34.0.0、Python和本地固定依赖，不在编译过程中下载运行库。完整阅读主机测试另需开发机的`lzokay==2.1.0`；Shell启动检查需要POSIX Shell，Windows可用Git Bash。
- 首次本地或CI构建会生成自己的`build/debug.keystore`。这种临时构建的APK不能保证覆盖官方Release。官方Release上传已核对的用户验证包；后续官方版本必须由维护者私下保管同一签名密钥。
- GitHub Actions已配置Python3.12、JDK17、Android34及主机专用`lzokay==2.1.0`，在测试前显式创建build目录。工作流包含主机/阅读/LZO/激活Shell/RSA与fake-ADB回归，以及主APK和原生Instrumentation编译。
- CI产物名称含`ci-test-only`及运行编号，产物内附`CI-TEST-ONLY.txt`说明临时签名与原生仅编译。工作流不写入Release，也不接收官方签名私钥；这些测试产物不应取代官方Release APK。Actions是否在GitHub实际运行成功须以仓库运行结果为准，不能用本机通过或工作流静态检查代替。
- 此次工作流静态检查通过：YAML解析、9个run块的`bash -n`语法、6个Python脚本路径、事件/只读权限、Python/JDK版本、LZO依赖目录及测试前创建build目录的顺序。此检查没有启动GitHub Actions。

## 设备支持声明

用户确认当前手机和手机壳组合可用。该结论不能推广为所有小米、所有Android系统或所有四色面板兼容。实体NFC供能、厂商寻卡、Android授权后台行为及实屏画面仍取决于具体设备。

下一页预传和LZO减少传图等待，执行的仍是原厂全局刷新；没有已验证的局刷，也没有改写DA/DB。图库入口仍暂停。发布说明应保留这些实际边界。

此文档记录发布前文件与验证核对，不代表GitHub仓库或Release已经创建。最终仓库链接、标签及上传结果以实际发布操作为准。

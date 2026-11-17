# 0.6.3-beta.1 交付记录

2026-11-12，Asia/Shanghai。用户要求实施游客分发计划、修复底栏跳转与夜间开本背景，并以0.6.3第一个Beta交付安装包，验证方式为本地构建和检查。

## 安装包

[EatRecord-0.6.3-beta.1-debug.apk](dist/EatRecord-0.6.3-beta.1-debug.apk)，versionCode203，121224305 bytes，约115.609 MiB。包名com.eatrecord.app，min26/target37，普通安装包，debuggable为false。

SHA256为`4ebd3b9af54609ad59213899782a287b59b117f6af69a930b8db5349069e50b8`。

v2/v3签名通过，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与原正式包的证书摘要一致。

## 本轮功能与修复

- 底栏从其他设置、记录本等子页面导航时走实际整页横移。原路径可能按子页来源尝试容器收缩，返回目标与来源不匹配时直接归位。本轮底栏使用显式导航进度回调，复用单次render及480ms整页时钟。
- 已附着的普通根页之间使用RootPagePager，恢复设置页及转场中的改选使用整页导航。新根页先在旧页不透明覆盖下完成正常绘制再建立硬件层，底栏初始化遵循当前液态弹簧状态。
- 开本背景快照直接以UiTokens原纸色像素填底，单独绘制纸纹，复用CaptureAppearance临时捕获作用域并恢复背景、padding与callback。开本覆盖层显式关闭ForceDark，新纸页正常绘制后再捕获，随后启动1000ms时钟。准备监听、帧回调及纹理统一随会话清理。
- 底栏显示与无障碍描述采用“足迹”。关于该软件增加GitHub项目入口，浏览器不可用时使用复制链接反馈。
- AppUpdateCheck先判断原位置是否有高于本机versionCode的版本，再决定是否查游客目录。原位置有更新时立即返回，原位置无新版本或异常时继续查游客来源，检测回调按世代和页面身份接收。
- AppDownloadClient支持游客路径及百度管理路径映射，匿名请求独立于原账号会话。字体链接优先从游客位置获取，获取失败时回退原位置，下载与字体安装沿用现有流程。

## 游客分发资源

已从原位置校验并同步五种字体、原有三份许可和教程，补齐小赖及悠哉许可。游客fonts共五份TTF与五份许可，根目录提供教程及update.json。悠哉许可原文来自其字体仓库。[悠哉OFL原文](https://github.com/lxgw/yozai-font/blob/master/OFL.txt)

游客根为`/24.Personal/2_EatRecord`，管理根为`/baidu/03.工具/24.Personal/2_EatRecord`。游客update.json对应已发布0.6.2/code202，APK路径采用游客前缀，GitHub正式附件作为回退地址。原正式清单与游客清单的版本、APK大小和SHA256对应同一成品。

通过全新HTTP会话及空Authorization完成12份共享资料和正式0.6.2 APK的完整下载SHA256校验，包含所有分段顺序重组计算。记录位于[migration-result.json](build/v063b1/distribution/migration-result.json)。本版Beta交付文件位于本地dist目录。

## 实际验证

- [最终构建](build/v063b1/build-final.log)与[JVM回归](build/v063b1/jvm-final.log)通过。新增35项认证/游客分发检查及13项顺序更新源检查，既有底栏、根页、容器、数据与备份检查通过。
- [真实接口检查](build/v063b1/distribution-live.log)通过，使用实际Java客户端依次读取原位置和游客正式清单，并取得游客TTF头。共享资源完整下载校验由上述迁移核对记录覆盖。
- [Android QA编译](build/v063b1/qa-compile.log)通过。新增revision-v063b1覆盖其他设置及记录本底栏切换的真实点击、半程位移、会话清理、夜间快照纸色、开本取消、足迹文案和GitHub入口。场景为独立测试设备的合成数据入口。
- [验包](build/v063b1/apk.json)通过四模型、双ARM共8个原生库、6份固定运行库摘要、依赖闭包、字体、许可、QA隔离与包体检查。版本和签名记录见[delivery.json](build/v063b1/delivery.json)。
- git diff --check通过。任务开始前的24份修改文件与目标源码已保存到build/v063b1/before，原有未提交文档继续保留。

本轮按用户要求采用本地验证，Android QA仅编译。夜间开本的真实首帧色阶、底栏转场观感、大字/RTL/短窗口与设备帧率需要安装后的设备验收。

临时构建与下载分段清理被自动安全审核拒绝。已核对实际目录并尝试限定到本轮4个run目录和build/v063b1/distribution/range-cache，工具均仅返回blocked by policy，缓存本轮保留。

## 后续

先收集本版安装反馈，按实际问题修正。后续正式版发布按RELEASE_PROCESS.md同步GitHub、OpenList及百度版本档案。旧分发目录退役时间和旧版兼容安排见DISTRIBUTION_MIGRATION_PLAN.md。

用户后续验收反馈其他设置→今日、记录本→今日仍出现停顿后快速跳转，记录本→我正常。后续修正与验证见[V063_BETA2_REPORT.md](V063_BETA2_REPORT.md)。

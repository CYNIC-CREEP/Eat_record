# Current Task

更新：2026-10-29（Asia/Shanghai）。

## 0.6.0 正式版远端发布中

- 用户最新明确要求上传OpenList、GitHub并推送更新，已取代上一轮暂不上传要求，授权本版源码提交、v0.6.0标签、main推送、正式Release与应用更新清单。Manifest、ReleaseNotes、README、内置教程为 `0.6.0 / versionCode 184`。
- 发布预检：origin/main与本机原HEAD e85ff8d一致，远端尚无v0.6.0。已再次验包，采用用户验收的相同普通包；本机凭据和Python缓存排除Git/源码包。已准备正式说明与更新清单，上传及回下载校验完成后更新本文件；不使用旧脚本的全目录压缩/无关字体删除。
- 安装包：`dist/EatRecord-0.6.0.apk`，121162865 bytes（115.55 MiB）；SHA256：`1753a9754d06b5e29b850f0f8b1080a05ea01e3051ebd91d032509e76e9b0eed`。与普通 `-debug.apk` 字节相同，实际无debuggable/QA/instrumentation，沿用原证书，支持覆盖升级。
- 本轮修改：餐次短文字在输入区上下居中、仍左对齐，上下padding对称；长文字可滚动，保存/返回仍重读最新记录后仅合并文字。修复原图加载中切后台取消请求后，未提交目标未复位导致下次滑动跳图；按当前viewer owner清理，旧会话不能改新会话。
- 保留beta.35共享纸面顶栏、原位日夜过渡及滚动监听清理，以及已有连续页面/日期/记录本、媒体异步显示、凭据隔离和备份事务保护。未迁移架构、删功能或改变数据格式。
- 验证：22组JVM共3096项及独立预览选择14项通过；独立emulator-5586上 `release-v060 all`、`revision-v33 all`、`revision-v28 all`、`revision-v32 books`、`revision-v24 dates`、`revision-v35 all`通过。QA与普通构建602个生产class逐字节一致。
- 普通/QA均NoUpload编译成功；普通包验签、版本、4个抠图模型、字体/许可、双ARM共8库及依赖闭包、退役识别移除、无QA与系统备份排除规则核对通过。详细证据见 [V06_RELEASE_REPORT.md](V06_RELEASE_REPORT.md)，元数据在 `build/release060-delivery-metadata.json`。
- 已将同一普通正式包覆盖安装到用户授权手机461QYGFL22429（魅族21，Android16），只读检查餐次文字/键盘、图片详情与原图返回、连续日期和主页面、记录本开合/连续翻页、日夜多页顶栏、字体框等。未在日常手机使用QA、清数据、删除真实Key、付费识别或破坏性恢复；测试夜间偏好已恢复原值。具体覆盖和限制见正式报告，不能据此宣称所有设备零卡顿。
- 未实测：真实付费API/营养准确率、实际拍照后保存/识别、系统备份传输、日常手机破坏性恢复及其他品牌长时间帧率。故障与凭据测试仅独立设备/合成数据。发布不扩大上述应用验收范围。
- 当前原生Java动态View/Canvas+JNI/MNN、自定义PowerShell。多轮工作区源码/资源/报告按明确文件清单审查后纳入本版，原签名不入库；禁止reset/clean，不以build备份替代src。server/update.json已准备为0.6.0/code184，待版本文件验证后再更新远端。

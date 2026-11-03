# 0.6.1-beta.3 营养师退出与餐次管理

更新：2026-10-30（Asia/Shanghai）。版本0.6.1-beta.3/code187；未提交、tag、push或上传。保留此前未提交源码、文档及未跟踪文件。线上清单仍0.6.0/code184。

## 修复范围

- 上一轮覆盖了“营养师已开启，但仍停留今日餐纸”的根页切换，遗漏了“当前显示营养师”的另一分支。该分支的目标页初始化会同步finishPeerIndicator，使底栏在页面横移开始前跳到终点。
- 保留实际营养师作为出场页，沿用PageCrossfade完整纸面横移。新增每次转场的进度owner，底栏X/宽度使用同一个ease值，目标根页初始化不得抢写。中断捕获已呈现位置，旧owner不能清新owner。
- owner在转场真正开始才取得，避免负向反馈延后请求在pause/stop取消后残留。转场期间阻止内层根页手势/进度竞争；完成和取消恢复普通RootPagePager导航。不改变三页层级或迁移架构。
- 其他设置“全局”增加餐次管理入口和独立页面，原开启夜宵开关及说明移入其中。存储键nightSnackEnabled、默认false及历史记录兼容不变。配置变化保持子页，标题/系统返回恢复其他设置及父页滚动。

## 自动验证

- 普通和QA均build.ps1 -NoUpload成功；JVM完整脚本通过；git diff --check通过。证据build/v061b3-build.log、v061b3-qa-build.log、v061b3-jvm.log。
- 独立emulator-5586运行revision-v061b3 navigation-settings通过：真正进入营养师后分别切迹/我；180ms暂停实际时钟，确认旧页面仍为营养师且参与水平位移、目标页处于中间位置、底栏位置/宽度与同一ease值一致；快速改选及反向延后后pause/stop取消不残留owner，后续普通根页切换仍复用pager。
- 餐次管理检查真实入口、父页无直接夜宵开关、子页标签/开关、配置保留、标题及真实Back、父滚动恢复；测试不改真实日记，不发HTTP(S)。证据build/v061b3-targeted.log。
- 完整revision-v061b3正常及动画缩放0均通过，同时回归既有三餐/夜宵、旧记录、营养师上下滑、真实IME、双向1440ms主题/柔边/图标及HWUI、结束与Stop清理。证据build/v061b3-device.log、v061b3-reduced.log。
- 初轮冷模拟器System UI ANR及一次真实IME边界断言未通过，不计作PASS；随后同一生产实现的定向、完整和关闭动画复测通过。未把模拟器结果扩大为所有设备/所有首次启动时序的保证。
- 普通与QA的612个生产class逐字节相同。四模型、双ARM共8库及依赖闭包/固定摘要、字体/许可、退役组件移除、QA隔离通过；包内教程与源码、两份教程分别字节一致。

## 魅族21普通包复验

- 用户此前明确授权的461QYGFL22429，MEIZU21/Android16，1080×2340。确认没有正在输入的非空编辑框，使用同证书普通包install -r升级到187；冷启动Status: ok。没有QA、卸载、清数据、修改Key或编辑饮食记录。
- 实际UI确认营养师开启、自动分析关闭，然后从真实营养师画面直接切迹，再回到营养师直接切我，分别录屏。页标题帧显示营养师横移退出并露出足迹/原“我”返回页，没有先跳回今日。
- 两段录屏以30fps抽样，迹297帧、我197帧均检测到选中背景；分别有7、9个远离静止端点的中间位置帧。这仅说明本次录屏中有连续移动，不作全刷新率/长期帧率保证。
- 手机实际进入其他设置→全局→餐次管理，确认开关只在子页并可返回；未改变夜宵开关值。自动分析、原日间模式及其他偏好未改，未主动触发付费识别/分析。最后返回今日。
- 抽查事件日志未见本App新增am_crash/am_anr记录。实际日记/头像相关截图与录屏仅存Git忽略的build/v061b3-phone，公开报告不附原始用户内容；手机临时测试文件已清理。

## 普通包

- dist/EatRecord-0.6.1-beta.3-debug.apk，121171057 bytes（115.56MiB），普通包无debuggable/QA/instrumentation。
- SHA256：0719e2f121519cf56731e9bf1ace4ec56dafef6fd44abc10ccf607cb0df0f1ad。
- versionCode187，min26/target37；v2/v3验签通过，沿用原证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。证据build/v061b3-verify.log、v061b3-signature.log、v061b3-badging.log。

## 修改文件与边界

MainActivity.java、PageCrossfade.java、UpgradeSmoke.java；AndroidManifest.xml与ReleaseNotes.java；CURRENT_TASK/PROJECT_CONTEXT/DECISIONS/RELEASE_PROCESS、README和两份教程、本报告。未改模型/许可/字体、日夜参数、备份数据格式或正式更新清单。

独立模拟器设置已恢复并关闭。未做真实付费API、系统备份传输、其他品牌或长时间性能基准。保持Beta本地交付，下一轮修改加Beta.4，未经明确批准不转正式版/发布。

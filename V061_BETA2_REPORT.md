# 0.6.1-beta.2 回归修复

更新：2026-10-30（Asia/Shanghai）。版本0.6.1-beta.2/code186。保留此前所有工作区改动，本轮无commit/tag/push/上传，正式清单仍为0.6.0/code184。

## 问题与修复

1. 首次营养师聊天栏到顶部：beta.1预加载页位于屏幕下方，旧键盘计算把host的整页translation误算为IME。改用固定root屏幕底部与可见窗口底部之差，adjustResize缩小root后不重复抬升；保留监听器attach/detach和正文padding联动，并限制底距。
2. 开启营养师后底栏背景不连续：旧mainPeerVisible只认content直接子View的main_peer标签，新增AdvisorPager后误走PageCrossfade，提前跳底栏并重建主页面。统一使用rootPagesVisible，恢复保留RootPagePager的点击弹簧及底栏共用位置；没有删除或替换原底栏美术。
3. 日夜速度及图标：1800/1.25=1440ms，曲线(.65,0,.35,1)和20dp柔边保留。旧图标在可见后突然重置-20度/.9缩放是闪动来源；改为250ms同一轮廓连续形变，旧快照省略图标，新静态按钮暂时透明，揭示层唯一绘制。图标墨色跟随同一圆半径；圆波越过按钮后切换到与静态按钮完全相同的画笔，完成/取消恢复真实按钮，无可见后transform重置。

## 本轮修改文件

- 业务：src/com/eatrecord/app/MainActivity.java、ThemeRevealView.java、ThemeModeIcon.java。
- 版本：AndroidManifest.xml、src/com/eatrecord/app/ReleaseNotes.java（Beta.2/code186）。
- 回归：tests/android/UpgradeSmoke.java（revision-v061b2、更新revision-v061的现行主题契约）。
- 文档：CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md、README.md、本报告；assets/help/getting-started.md与server/新手教程.md。
- 未改云端更新清单、API/数据/媒体存储格式、模型/字体/许可及发布上传逻辑。beta.1既有脚本拦截和未提交文件继续保留。

## 验证入口与边界

- 普通及QA均build.ps1 -NoUpload成功；JVM完整脚本通过。日志build/v061b2-build.log、v061b2-qa-build.log、v061b2-jvm.log。
- 独立EatRecordV06QA/emulator-5586专项：隐藏host处于H、H/2和0时强制global-layout，检查聊天栏底距/正文padding；真实IME开关以WindowInsets确认；营养师开启状态实际底栏performClick，保留pager身份，逐帧检查选中背景/页面位置及不走层级转场；双向圆内/外/羽化及HWUI对照，图标0–300ms连续轮廓、唯一owner、末态与静态按钮交接和清理。
- 系统UI启动ANR曾遮挡独立模拟器，关闭该环境对话框后重测；未当作App通过。末态图标检查区分shader与实色边缘抗锯齿，生产绘制在波面越过按钮后复用静态画笔；软件像素采样后显式请求下一帧硬件显示列表，避免测试采样消耗脏标记后拿旧HWUI帧比较。
- 正常动画与关闭系统动画分支均通过；真实IME由WindowInsets确认。关闭动画时只验稳定终态，普通模式另逐帧严格检查中间位置。
- CPU软件采样后递归使decor整棵树失效并等待FrameCommitCallback，修复测试采样消耗dirty flags后拿旧硬件帧比较的问题。此前旧月亮/旧整页截图不能代表生产遮罩失败，最终完整CPU/HWUI对照已通过。
- QA仅独立emulator-5586、合成日记、禁HTTP(S)。模拟器IME/动画设置已恢复原值，测试后关闭。未做真实付费API或系统备份验收。

## 普通安装包

- dist/EatRecord-0.6.1-beta.2-debug.apk，121171057 bytes（115.56MiB），实际普通包无debuggable/QA/instrumentation。
- SHA256：2ca397c88bd07897c5426539ba2c08b3855f2b7867e82d57596a6ed38b38045a。
- versionCode186，min26/target37，v2/v3验签通过，沿用原证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- verify-food-apk通过四模型、双ARM八库/固定摘要/依赖闭包、默认字体和许可、退役组件移除、QA隔离；普通/QA生产class逐字节一致，教程随包字节与源码一致。

## 魅族21普通包实测（用户明确授权）

- 序列号461QYGFL22429，MEIZU21/Android16/1080×2340。先核对已安装Beta.1证书与本包一致，再普通包install -r覆盖为Beta.2/code186；启动成功，无卸载/清数据/QA runner。
- 先确认营养师开启，自动分析、AI读取记录和记忆关闭；未改这些开关，未主动触发付费识别/分析。原日间模式在两组正反主题切换后恢复。
- 首次上滑进入营养师，实际输入框bounds为[72,1874][859,2023]，标题在顶部，聊天栏在下方；未输入或删除文字。返回今日后底栏多次切迹/我/今日，录屏已观察选中背景经过中间位置。
- 实际底栏录屏按20fps抽取221帧，均可检测到选中背景，45帧包含远离三个静止中心的过渡位置；这是本次样本的可见性证据，不是全设备帧率保证。另一次补录时手机已回桌面，未纳入通过证据。
- 两方向日夜切换各两次，检查实际录屏的图标早期与交接帧，正圆扩散及同一图标形变可见；没有旧版可见后缩放/旋转重置。手机保持原日间模式，未修改系统动画/字体/明暗设置，未编辑用户饮食记录或API凭据。
- 手机截图/录屏及安装前普通APK仅保存在Git忽略的build/v061b2-phone；公开报告不附用户实际日记/头像。手机/data/local/tmp中本轮创建的文件已清理。

## 最终状态

普通/QA NoUpload构建、JVM、revision-v061b2正常及关闭动画、验包与验签均通过；610个生产class逐字节相同，包内教程与当前源码/服务器教程分别字节一致。普通APK已安装到授权手机，未提交或上传。日志build/v061b2-device.log、v061b2-reduced.log、v061b2-verify.log、v061b2-signature.log。尚未实测真实API、系统备份传输、其他品牌或长时间帧率。

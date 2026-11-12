# 0.6.2-beta.3｜餐次管理容器视觉精修

2026-11-04（Asia/Shanghai）。仅“其他设置→餐次管理”，保持480ms和Bezier(.25,0,.25,1)。仍beta.3/code196同轮纠正，普通包已按既有授权在魅族21保留数据覆盖；不推广、不发布。本报告为最新现场，上一份容器报告为历史。

## 四项真实根因

|问题|原因|修复|
|---|---|---|
|父页底部残留|每帧全alpha画完整parent，增长Path只覆盖其内部，外部“记录本/恢复出厂”必然露出；并非当前证据证明的高度/Z/终态clear错误|parent整页含旧标题与底部在e=.18退净，先于新内容出现；原底栏独立层与系统安全区不改|
|标题正文错位|父标题烘焙在parent里没有退场；新page固定(0,0)，裁剪先扫到y约539正文，后到y约125标题|旧标题随parent退出；新标题正文同纹理、同alpha、同容器顶部偏移，终态偏移0，无独立改字时钟|
|大块空白|源行e=.25已消失，新page直到e=.30才出现，有明确空窗；固定原位又先裁到页面空白部分|源行保留至e=.33，新内容e=.18开始、e=.43全显，正常字号从卡片内部显现；几何扩张与文字显现分离，整页文字不缩放|
|过早近似矩形|原r×(1-e)前段很快弱化，卡片变大后相对圆角更小|改r×(1-e²)，真实起点r与终点0保持，前中期轮廓更圆润|

## 同一个视觉进度

e仍是原时钟经过原Bezier后的展开度。四边插值、Path和反向接用当前Rect/currentRadius不改。smoothstep(x)=x²(3-2x)，先钳制0..1。

- 父身份/旧标题：1-smoothstep(e/.18)。新内容出现时父标题必为0。
- 源行：1-smoothstep((e-.08)/.25)；新标题正文：smoothstep((e-.18)/.25)。有连续交接，不存在双方同时为0的区间。
- 新页位移只取bounds.top-end.top，字号和布局保持1:1；终点原位，属于容器内部显现，不是另叠整页Slide。
- 主题卡色取真实GradientDrawable与当前纸色，通过可复用1×1原始色像素填绘，alpha=1-e²，平滑露出原纸纹。无玻璃/发光/新Shader或主题修改。

原业务render只提交一次；系统与顶栏仍原返回owner，不在动画end再pop。返回在父列表scroll恢复后测新行，不用旧屏幕坐标。旧clock/preDraw身份检查、后台/尺寸/detach/0动画清理、输入/语义隔离均保留。overlay本身不可点击；原host gate在过渡中屏蔽隐藏树，返回途中点隐藏行安全忽略，落位后重进正常，不排队重复导航。

## 暗色诊断与处理

本机诊断：软件Canvas输入ff1e1b19，drawColor后Bitmap像素为ff3d3b3a，Paint矩形为ff3d3c3b；Bitmap.setPixel保持ff1e1b19，色空间sRGB。灰亮已在捕获时发生。仅禁Layer forceDark或删除重复背景绘制都没有消除，失败候选未算通过；诊断日志代码最终已移除。

最终captureHost只保存透明标题/正文/控件，不在软件Canvas重画纸面；真实root Paper一直拥有背景，容器填色直接写原始像素，不硬编码减32、不改全局主题。1×1色纹理随clear回收，没有每帧大纹理分配，也不再逐帧重画数千纸面点。

最终暗色纸面ROI首尾均(28,26,24)，lower运动范围(28..33,26..29,22..27)，外缘为(28,26,22..24)，原整页+32跳亮已消除。小范围变化含卡片染色和视频编码，非色彩校准。部分离屏控件底色/抗锯齿与真实HWUI仍有轻微差异，不能宣称高保真复刻。

## 真实视觉证据

本机忽略目录build/v062b3-polish，普通包ADB真机，原速不变速：

- day-transparent-final.mp4：进入/顶栏返回/再次进入/系统返回。
- dark-transparent-final.mp4：最终暗色进入/顶栏返回，之后恢复日间。
- day-push-keyframes-final.jpg、day-top-pop-keyframes-final.jpg、day-system-pop-keyframes-final.jpg。
- dark-push-keyframes-final.jpg、dark-top-pop-keyframes-final.jpg。
- 对应day/dark-*-000/010/025/050/075/100.png单帧，具体实际帧号与PTS见day-final-visual-analysis.json、dark-final-visual-analysis.json。

已人工逐张查看：早期源行/圆角可辨，25%～50%标题正文在容器内部共同显现，新正文出现时旧标题已退出；卡片外没有无关父页文字，终态交回真实页无透明遮罩；返回同规则。页面内容本身少，扩张中仍有自然留白，未声称完全无空白像素。

VFR录像进度由正常字号标题模板位置与未变曲线估读，约一刷新间隔不确定性，100%取稳定完成帧，不能把状态栏更新当移动末帧，也不冒充精确vsync同步。

最终日间父footer辅助像素检查：记录本ROI(250,2006..900,2070)、恢复出厂ROI(55,2290..340,2338)。4段新内容可见窗口分别49/36/49/35，共169帧，max RGB<180的深色像素均0。初始与返回稳定父页同ROI有文字，进入终态无文字。证据footer-transparent-final.json；仅覆盖两个ROI/阈值，不排除ROI外淡残影，不替代人工观察。

## 实测与产物

- git diff --check、完整test-food-analysis.ps1通过：24680容器/身份/坐标、6219设置、365底栏、1160根页及其余食物/隐私/备份等JVM回归。
- UpgradeSmoke新增真实Layer标题/正文标记像素、父footer哨兵、旧Layer不绘和fillPixel回收。最新classes+android-37.0 javac通过；runner会写合成夹具，未在日常手机安装/执行。
- build.ps1 -NoUpload普通构建、verify-food-apk.py、原证书v2/v3通过。min26/target37，无debuggable；4模型/双ARM8库/6摘要/依赖/字体/许可/QA隔离正常。
- 最终APK：dist/EatRecord-0.6.2-beta.3-debug.apk，121207921 bytes，SHA256 2d7283b384b6fd0267bd4174d756cc1bc5bdb8b8ddbfb8083fa14d4b5650a3db；证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 仅ADB魅族21/461QYGFL22429/Android16 API36普通包保留数据覆盖。无模拟器/QA包/清数据/卸载/记录或夜宵编辑/私有导出/API/同步/备份操作。前台遮罩时停止测试，dump拒绝旧XML。
- 最终包通过明暗进入、顶栏/系统返回、即时/120ms快速返回、返回中点击安全忽略/落位重进、scroll原bounds保持(文字635..699px)、动画0、迹↔我与原根页正文右滑。系统三倍率1.0及日间/我页恢复。
- gfx-transparent-final：4组进退496帧、11次deadline jank(2.22%)，p50=8/p90=11/p95=11/p99=27ms，MissedVsync=0，慢Bitmap上传=0。包含准备/落位，不是全链路零掉帧或跨设备保证。录像运动段PTS中位约8.3ms，日间最大约11.8ms，暗色进入样本最大17.4ms。
- 保留crash buffer未发现本包Process记录，非长期零崩溃保证。

## 修改范围、回退与限制

生产仅SettingsContainerTransform.java、SettingsContainerMath.java、ReleaseNotes.java；测试仅SettingsContainerMathTest.java、tests/android/UpgradeSmoke.java；另更新四份交接文档和本文。MainActivity/PageCrossfade/UiTokens/Manifest、根Tab/底栏/业务/数据库/登录/同步/备份/JNI/网络/构建架构未改，16份保护文件摘要一致。

SETTINGS_CONTAINER_MOTION_ENABLED=false仍独立恢复原餐次安全转场，业务导航owner不变。未新增系统预测返回进度；API26品牌机/RTL/大字/旋转分屏/TalkBack及所有业务未全面实测。临时控件色阶差异与稀疏内容留白仍是视觉限制。

Git仍main/8cc26c5，保护所有既有dirty/untracked；无commit/tag/push/上传，server/dist正式清单仍0.6.1/code193。本轮停止，等用户观看，不推广其它入口。

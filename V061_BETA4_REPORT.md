# 0.6.1-beta.4 底栏响应与小幅上滑

更新：2026-10-30（Asia/Shanghai）。版本0.6.1-beta.4/code188，未提交、tag、push或上传；保留全部已有工作区修改。正式清单仍0.6.0/code184。

## 最终实现

- 营养师曾使用额外反馈preDraw/post及独立横向转场，在首帧前重建或重挂主页。最终将AdvisorPager放入RootPagePager.page(0)，今日餐纸/营养师仅在根页0内部纵向切换；营养师离开与普通主页直接共用原RootPageMotion/弹簧、三页缓存和底栏绝对位置，不再为此创建页面、占位预览或额外层。
- 保留实际营养师作为出场内容，根页1/2真正归位后才静默复位纵向Today，沿用原返回落点。根页0和纵向状态共同决定Today/日历写权，当前AI或纵向交互时延后根刷新；owner验证root实例/page0/父链。设置恢复仍沿用原层级策略，未改变返回偏好。
- 配置和后台缓存恢复保留AI页及其滚动，避免把AI滚动套给Today。停止/继续纵向与根导航时，旧回调不取得新的写权。
- 星标和手写按钮按纵向位置同步移动、以1-progress连续渐隐；15/30dp小幅上滑仍可见，取消回位，拖动不触发按钮点击。到营养师才隐藏。
- 真实IME回归发现键盘余量原为减4dp，改为加4dp，使聊天栏保留在键盘上方；不改普通关闭键盘时的底距。未改主题1440ms/20dp/250ms、餐次管理和夜宵键、模型/备份/记录格式或API授权。

## 自动验证

- 普通与QA均NoUpload构建成功；JVM完整脚本通过，git diff --check通过。build/v061b4-build.log、v061b4-qa-build.log、v061b4-jvm.log。
- 定向revision-v061b4 navigation-settings、完整正常及关闭系统动画均通过。同步RootPager启动、无pendingNavClick/独立PageCrossfade、同一pager/世代/三页、实际AI出场、根页改选/暂停、离屏复位、Today日期owner及15/30dp按钮连续/取消均检查。
- 完整回归同时覆盖三餐/夜宵、旧记录、真实IME、上下滑、餐次管理入口/返回/配置/父滚动、双向主题/羽化/图标和HWUI、清理。所有QA为独立emulator-5586合成数据，HTTP(S)=0。
- 软件主题取帧在同一次UI调用中取得并暂停圆形/图标时钟，修复检查线程调度慢时短动画已结束的测试竞争；随后仍按真实硬件帧提交验证。独立模拟器安装空间不足时仅清理系统缓存，未清应用数据或旧QA证据。
- 同包采样记录click handler及首个非零硬件提交帧；模拟器GPU/冷缓存波动大，不据这些值承诺手机速度或百分比。最终完整采样handler约普通19–20ms、AI约5–11ms；其首次硬件提交约0.57–0.79s，属于该模拟器本次运行，非品牌机延迟指标。结构验证保证共用同一路径，原根页响应参数不变。
- 612个普通/QA生产class逐字节相同；包内教程与当前源码/服务器教程分别字节一致。验包通过四模型、双ARM共8库/依赖闭包及固定摘要、字体许可、退役组件移除与QA隔离。

## 授权手机复验

- 461QYGFL22429，MEIZU21/Android16。安装前检查没有非空焦点编辑框，普通包同证书install -r覆盖为188，冷启动Status: ok；无QA、卸载、清数据、编辑记录或Key。
- UI确认营养师开启、自动分析关闭；返回我根页后录制真实营养师→迹/我，直接使用已存在根页。未主动触发付费识别/分析，原开关及日间模式未改。
- 在今日以DOWN/MOVE保持小幅上滑，两个添加按钮在UI树中均保持可见且bounds随位移上移；UP取消后精确恢复原bounds。两次复验均通过，证据build/v061b4-phone/tiny-controls.json。
- 截图、录屏和公开设置状态仅在Git忽略的build/v061b4-phone，不把实际用户内容放入源码或公开报告。手机临时测试文件已清理；模拟器设置恢复并关闭。

## 最终普通包

- dist/EatRecord-0.6.1-beta.4-debug.apk，121171057 bytes（115.56MiB）；实际无debuggable、QA或instrumentation。
- SHA256：2e8434efd9c88f558b4d68add3fc49dc948d7b07bb3a52bab52efe6650868b9e。
- version188，min26/target37；v2/v3验签通过，沿用证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。

## 文件与边界

MainActivity.java、AdvisorPager.java、RootPagePager.java及PageCrossfade.java；UpgradeSmoke.java；Manifest/ReleaseNotes、交接文档、README、两份教程及本报告。中间评估的提取/重挂/延后预览方案未纳入最终实现，最终没有这些占位或retire状态。

未做真实付费API、系统备份服务、其他品牌及长时间帧率/内存压力系统回收实测。不声称所有设备绝无掉帧。Beta仅本地交付，下一轮修改再加Beta.5，未经明确批准不发布。

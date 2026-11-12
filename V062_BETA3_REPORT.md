# 0.6.2-beta.3｜普通设置子页协同进退

完成：2026-11-04（Asia/Shanghai，跨日开发）。versionCode196。普通包仅本地交付，不改正式更新清单，不提交、打标签、推送或上传。

## 原实现与本轮范围

原MainActivity用已有页面标志确定业务身份，在smoothContentSwap内执行一次render；PageCrossfade保留出场View，用480ms独立ease做不透明100%宽度相邻横移。RootPagePager拥有根Tab滑动，Dialog、资料浮层、相机／照片／记录本及外部Activity各有独立行为。

本轮增加显式smoothSettingsSwap入口，接入“我”的普通设置及同架构子子页：个性化、个人信息、关于／字体来源／本地日志、其他设置及餐次／提醒／照片查看／文案／震动／记录本／书架布局、智能助手及其普通授权设置、数据与安全概览。配置API的Dialog、外部站点、云同步、回收站、收藏、媒体等特殊路径继续原模式。

先以“其他设置→餐次管理”完成普通构建和魅族21试点，验证顶栏／系统返回及快速反选后才推广。根页与底栏没有第二套转场或导航owner。

## 进入／返回的实际时序

| 通道 | 实际采用 |
| --- | --- |
| 普通时钟 | 450ms；ValueAnimator只提供线性时间u |
| 位移 | LTR push：新页+0.25W×(1−E)，旧页−0.25W×E；pop方向镜像，RTL再镜像 |
| E(u) | 用户给出的两段Bezier，按时间横坐标二分反求参数（28步），不是把t当x；未引入动画库 |
| 新页内容 | raw u=0.15..0.55，easeOutCubic |
| 旧页内容 | raw u=0..0.30线性淡出，与新页约在0.20交接 |
| 标题额外门控 | 旧sticky_paper_header在0..0.16退场，新标题在0.16..0.34接入；额外factor乘原页alpha，消除普通进退时的双标题重影 |
| 输入交接 | 前20%新内容不接触摸／焦点／无障碍；系统返回仍由原owner处理 |
| 结束／关闭动画 | 接受的目标页alpha=1、translationX=0；原背景／padding／焦点／层类型恢复，退出View及快照释放 |

使用同一个Slide会话驱动两页与标题，业务render仍只执行一次。旧页通过原startViewTransition保留呈现，不再是正常输入子View；显式禁止其焦点／无障碍。背景继续原主题Paper／补底，不改布局、字体、系统栏、Insets或IME位置。

背景与内容没有拆成第二份业务树：这是原宿主内的slide+fade近似，完整背景与内容仍共用现有View alpha。纸面颜色由原背景保持一致。正常进入／返回增加了独立标题factor；快速中断时复用原“合成当前呈现帧”路径，因此已烘焙进该帧的标题无法再完全独立门控。这是保留生命周期及不复制业务View的高保真边界。

## 返回、状态与取消

- 不新增pop：原顶栏回调、afterReturnFeedback真实绘制帧guard、API33 OnBackInvokedCallback、dispatchKeyEvent、100ms防重复及既有24dp边缘提交规则保留。动画end只清理自己的资源，不更改页面身份。
- 当前项目虽开启enableOnBackInvokedCallback，但注册的是确认回调，没有预测返回进度owner。本轮不新增OnBackAnimationCallback或预渲染业务父页；普通系统返回确认后播放本轮动画。取消系统手势不提交，不提供子页随预测进度拖动的能力。
- Slide对象身份同时约束Animator与preDraw回调；清理先移除listener，旧end不能清理新会话。中断从当前合成画面续接，不闪回原进入起点。零动画、后台、配置／尺寸变化沿原clear路径安全归位。
- 重复push根据现有已接受页面标志和活动设置会话过滤，不新增可写业务selected状态。settingsScrollPositions只按原页面标志保存有限的滚动整数；普通子子页pop后在实际布局时恢复，既非业务返回栈，也不保存凭据／记录。
- 资料浮层IME回归沿原行为：返回收起IME及资料浮层，不额外pop个人信息页。没有编辑或保存实际资料，没有修改该浮层动画／返回逻辑。

## 实际修改文件

| 文件 | 修改 |
| --- | --- |
| MainActivity.java | 普通设置适配、输入门控、重复push guard、有限滚动记忆；保留特殊分发及原系统返回方法 |
| PageCrossfade.java | 原宿主新增settings会话模式、450ms／0.25W／alpha通道、原标题marker门控及焦点资源恢复 |
| SettingsTransitionMath.java（新增） | 纯数学的分段曲线、位移、内容与标题factor |
| UiTokens.java | 独立SETTINGS_MOTION_ENABLED构建期开关；原底栏／根页参数不改 |
| AndroidManifest.xml／ReleaseNotes.java | beta.3/code196及本地公告 |
| scripts/test-food-analysis.ps1／SettingsTransitionMathTest.java（新增） | 接入6219项新曲线／通道／方向／标题不重叠检查 |
| tests/android/UpgradeSmoke.java | revision-v062b3隔离QA会话／旧clock／重复push／幽灵层断言，仅编译 |
| CURRENT_TASK.md／PROJECT_CONTEXT.md／DECISIONS.md／RELEASE_PROCESS.md／本文 | 真实交接与有效规则 |

RootPagePager、RootPageMotion、GestureSpring及全部BottomNav／NavLensGeometry文件与本轮起点摘要一致。资料、饮食／营养、日历、备份、同步、网络、图片／JNI等业务源码及资源未做本轮修改；所有接手前未提交成果保留。

## 构建、数学与设备证据

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-food-analysis.ps1`通过：6219项设置曲线／通道检查，365项原底栏几何、1160项根页连续导航及其余既有JVM回归全部通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`通过。最终普通包：dist/EatRecord-0.6.2-beta.3-debug.apk，121203825 bytes，SHA256 `d121af92eba94e0366302fdb3e68a732498538937038f5a9a8f4fc3d7bef1f5e`。
- verify-food-apk.py通过4模型、双ARM8库／6固定摘要、依赖闭包、字体／许可／QA隔离及预算；min26/target37，普通包无debuggable。原证书v2/v3通过，SHA256 `7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。
- 延续用户覆盖普通包及无损设置UI测试授权，魅族21（Android16／API36）／461QYGFL22429覆盖195→196并启动。只使用ADB真机；不卸载、清数据、写夹具、删除Key或主动调用模型／同步。QA仅javac编译，不安装instrumentation包，不启动模拟器。
- 最终标题门控包复查：我→其他设置→餐次管理及我→其他设置→记录本→书架布局进入／返回；两层父页滚动行位置一致，快速系统返回回到合法父页。标题修订后再次以约20ms延迟Home测试早期后台／恢复，标题和页面归位；最终包再次通过系统动画0导航、暗色push、系统边缘pop、顶栏pop，原三个倍率及日间模式均恢复。
- 本轮主逻辑包还验证了pop途中重新push、pop途中再次Back（每次只退一层）、IME显示及资料浮层原owner、根页正文拖动停留／反向／取消与反向切页；最终标题修订没有改Main相关分发或根／底栏文件。标题区的输入探针不作为正文有效手势验收，未修改原仲裁。
- 日间、暗色与中断记录均有前台保护。最终交付视频来自settings-header-final-raw.mp4及settings-dark-header-final-raw.mp4；settings-demo.mp4仅剪去脚本查询等待，动画本身没有变速。push-phases.jpg／pop-phases.jpg是最终包原生帧，已人工查看标题交接；不是数学通过即宣称视觉通过。
- 证据在忽略的build/v062b3：device-acceptance.json、device-final-guards.json、device-interruptions.json、device-guards.json、jvm-final.log、build-final.log、apk-verification.json、signature.txt、qa-compile.log。试点及标题修订前记录属于过程证据，不冒充最终视觉包。

## 独立关闭、回退与覆盖边界

SETTINGS_MOTION_ENABLED=false后重建，普通设置回到原480ms／全宽／不透明模式；不改NAV_LIQUID_MOTION_ENABLED或ROOT_TAB_SLIDE_ANIMATIONS_ENABLED。本轮未另外交付关闭该构建开关的APK。

build/v062b3/before、before-manifest.json及this-round.patch保留本轮开始的脏工作区和beta.2普通包。可先git apply --reverse --check检查，再由用户决定是否反向应用；不使用HEAD整文件覆盖，不自动执行回退。

未做强制RTL／大字体、真实横竖屏／分屏／折叠屏、TalkBack完整朗读及所有特殊业务的真机专项；现有Portrait Manifest、配置清理路径和数学RTL镜像保留。未实现预测返回跟手进度；不扩大到弹窗、底部面板、主题或其它下一阶段。最终观感仍待用户确认。

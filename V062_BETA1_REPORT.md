# 0.6.2-beta.1 第一阶段底栏与Tab动效

清理说明（2026-11-03）：用户已要求结束第一阶段并清理录屏、截图及临时取证文件。下文媒体路径仅作历史过程记录，文件已删除；源码、测试／构建日志、摘要和必要回退快照保留。

历史记录：本方案的根Tab Fade与2dp视觉限制已被用户否定并由[V062_BETA1_REVISION_REPORT.md](V062_BETA1_REVISION_REPORT.md)取代。

日期：2026-11-03（Asia/Shanghai）。versionCode194。保留Java动态View、C++ JNI、自定义PowerShell构建，无Gradle／Compose迁移。测试版仅本地交付，线上清单仍为0.6.1/code193；不提交、不打标签、不推送或上传。

## 基线与范围

- HEAD及本地origin/main为8cc26c5，发布标签v0.6.1仍指向8a0d15d。开始时只有CURRENT_TASK.md、RELEASE_PROCESS.md、V061_DELIVERY_REPORT.md三份交接文档未暂存；保留其已有内容／意图，未reset、clean或stash。
- 修改前Git状态、完整patch、最近十次提交及源码／已发布APK摘要保存在忽略的build/v062b1/baseline-*。旧独立模拟器的外观图为code192历史QA基线，不能冒充code193同fixture像素对照。
- 底栏Item稳定ID仍为0／1／3，原点击回调与currentTab／RootPageMotion拥有业务身份；RootPagePager保留三个页面。设置子页仍由PageCrossfade处理，未叠加新转场。
- 饮食JSON、凭据、API、云同步、备份、抠图、记录本与设置业务源码均未调整。原有主题／文字／图标／字体／安全区与Item尺寸保留。

## 修改文件与实现

| 文件 | 修改 |
| --- | --- |
| src/com/eatrecord/app/BottomNavMotion.java（新增） | 三个独立物理通道，解析弹簧步进、当前位置／速度重定向、抬升释放条件与速度／加速度jelly |
| src/com/eatrecord/app/BottomNavIndicator.java（新增） | 原背景View的transform控制器，稳定ID查找实际bounds／宽度，RTL／不等宽、逆变换圆角、裁剪保护，静止／卸载撤帧回调 |
| src/com/eatrecord/app/RootTabFade.java（新增） | 三页当前透明度续接，非相邻点击不显示中间页，手势接管保留当前合成 |
| src/com/eatrecord/app/RootPagePager.java | 原宿主内220ms淡化；不重建／重挂页面，独占当前clock、取消旧回调，退出页禁止焦点／触摸／无障碍，保留原导航手势 |
| src/com/eatrecord/app/MainActivity.java | 原点击／按压入口接入纯视觉层，导航接受目标后驱动指示器；按原Item身份测量，补选中及键盘语义 |
| src/com/eatrecord/app/UiTokens.java | TAB_MOTION_ENABLED与TAB_FADE_MS，局部构建期开关 |
| AndroidManifest.xml、src/com/eatrecord/app/ReleaseNotes.java | 0.6.2-beta.1/code194及本地说明 |
| tests/TabMotionTest.java（新增）、scripts/test-food-analysis.ps1 | 物理／中断／不驻留帧循环／三页alpha合同，接入已有JVM入口 |
| tests/android/UpgradeSmoke.java | revision-v062隔离QA场景 |
| CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md、本文 | 当前进度、取代旧根页横移规则、测试入口和证据 |

本轮未编辑原有V061_DELIVERY_REPORT.md，其工作区diff来自接手前。

## 参数与视觉边界

- m=1；位置stiffness246.740110、dampingRatio0.85；已有根手势跟随1024／0.9。
- lift631.654682／1.0。释放等待lift≥0.975且位置误差≤0.04个测量Item单位后回落；取消立即回落。不是250ms或400ms强制截断。
- jelly100／0.4，目标为0.2×tanh(abs(v)/3)−0.15×tanh(max(0,−a×sign(v))/25)，v／a按实际Item间距换算为秒制。显示幅度克制，横向约−5.4%至+7.2%，垂向约−2.4%至+1.8%；最多2dp抬升／2dp增长，受实际留白限制。原Item按压反馈保留，不叠加第二套Item缩放，也不新增底栏拖动切Tab。
- 页面220ms，PathInterpolator(.42,0,.58,1)，仅alpha，translationX/Y=0、scale=1。三页alpha各自从当前值转向目标，非相邻不经过中间页面的业务提交。
- 独立指示器与淡化共享唯一导航目标，各自运行，不等待弹簧结束。系统动画关闭时直接收敛。UiTokens.TAB_MOTION_ENABLED=false后重建可恢复原根页横移与背景跟随，不改变业务导航状态；本轮未另外交付关闭该构建开关的APK。

## 验证

- git diff --check通过，只有接手前V061_DELIVERY_REPORT.md的LF／CRLF提示。完整scripts/test-food-analysis.ps1通过，包括新增4130项物理／透明度检查及既有食物、导航、日期、备份、凭据等JVM回归。
- 普通build.ps1 -NoUpload和独立QA构建通过；普通包为dist/EatRecord-0.6.2-beta.1-debug.apk，121195633 bytes，SHA256 `d158c3c45a848b6281851c05cb40be9caab6c257c772cc658671c9c03422178d`。
- verify-food-apk.py通过4模型、双ARM8库、6固定运行库摘要、依赖闭包、字体／许可、退休组件排除、QA隔离及预算；min26/target37，普通包无debuggable／instrumentation。原证书SHA256 `7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，v2/v3签名通过。
- 安全设备：旧EatRecordV06QA存储不足导致安装失败，未清理旧数据；恢复其网络后关闭，另建EatRecordV062QA（build/avd-v062，Android36.1、x86_64、专用6GiB盘），所有ADB均指定emulator-5582。新盘首次启动的SystemUI ANR先处理环境再开始应用回归，不算产品通过证据。
- revision-v062默认显示通过；暗色／系统字体150%／900×1600短窗口／营养师开启通过；三项系统动画缩放0的终态分支通过。实际View点击、暂停原clock的110ms中间态、快速反选和旧回调、保留实例／Me滚动、取消及无障碍点击、既有根手势、RTL不等宽实测bounds、设置进入／返回均有断言；合成日记逐字节不变，HTTP(S)=0。
- 最终产品源码的手势接管修订已包含在普通包和设备QA中。关闭动画首次回归暴露测试分支准备遗漏，仅修正runner准备后重建QA并通过；不以测试夹具失败冒充产品缺陷。日志：build-normal.log、build-qa.log、jvm.log、qa-day.log、qa-night-large.log、qa-reduced.log、apk-verification.json、signature.txt；截图在build/v062b1/screens。
- 普通交付APK也在专用模拟器覆盖安装／启动成功，包名com.eatrecord.app、code194，无debuggable，未卸载／清数据；crash buffer无应用崩溃。设备测试完成后恢复窗口、字体、动画和网络，关闭自建模拟器，保留其测试盘。
- server/update.json与dist/update.json保持原0.6.1/code193，摘要均为 `de35f1a74b3ff18e2280c8700af5c782ed7d2dbb56384d17bc6dc68473cb97de`。没有Gradle工程，因此实际执行自定义构建，而非虚构Gradle结果。

## 验证边界

- 日常魅族21未安装、未清数据、未运行QA；没有本版品牌机触感／帧率或真实营养API结论。
- RTL／不等宽为真实View布局测试，不代表所有OEM及语言组合；无障碍点击及焦点策略检查不代替TalkBack人工朗读验收。
- Manifest仍固定竖屏；横屏／折叠屏／分屏、真实IME输入草稿、登录同步和完整记录本业务流程未做专项真机验收。三根页复用及合成日记不变有专项证据，不等于所有业务完整回归。

# 0.6.2-beta.3｜容器第三次精修与上下同步校正

2026-11-05（Asia/Shanghai）。本轮范围为“其他设置→餐次管理”。用户后续指出先上后下，最终候选已改为上下同步，并已在魅族21保留数据覆盖安装。

## 真实诊断

上一轮来源row快照含背景和描边，绘制Rect固定startRect，扩大的容器里面一直嵌着原胶囊。正文和标题各自计算完整区域显现，造成正文先出现。

before-frame-state.json记录前版255个真实视频帧所对应的参数复原，实际PTS/帧号与图像对照见before-frame-audit.md。该记录按前版代码和视频估读时钟复原，属于模型辅助，具有刷新间隔误差。

前版进入约50%的frame34/PTS1.133233s，e约.784，Rect约(13.5,200.6,1066.5,2076.3)，r36.8，来源alpha .009，父标题 .686，新标题0，新正文 .991。进入25%来源alpha1、新内容0，图中原胶囊还完整嵌在大纸面里。这与用户观察一致。

底部“恢复出厂”等在进入0%与返回100%本已被原屏幕/浮动底栏边缘截断，属于当前父页稳态布局。转场中另有实体边界经过父文字的局部遮挡，两类情况已区分。

## 有限的一组修改

- 捕获来源row时暂隐背景，finally恢复原Drawable alpha。同一容器继承原主题填色、圆角和原描边的视觉副本，来源快照只承载内容，消除嵌套小胶囊。
- 来源文字是局部共享元素，沿容器内部向实测目标标题baseline接近。裁剪仍约束它，源文字保持原比例，图标/副标题/箭头较早退出。目标page继续在最终(0,0)绘制。
- 同一e计算joint identity，取标题/正文安全显现的较小值，再乘原内容窗。来源在identity前40%退出，目标标题/分组/开关/说明在后60%一起进入，只有一个Fade Through零交点。旧标题同时乘来源退出因子。
- 来源与目标标题alpha乘积为0，新标题和正文共用一个targetAlpha。实际内容完整落入圆角安全范围才显示，feather为8dp。
- 来源当前绘制Rect随反向会话传递。四边按各自通道从actual Rect续接，饱和通道遇到新锚点时用全局分数落到真实端点。原page纹理及baseline/bounds元数据一起转移。

短期Bitmap保持parent/row/page三张，现有单一控制器和Overlay继续拥有呈现。新增的是原描边Drawable副本及少量几何元数据。导航render仍由原业务owner提交一次。

## 用户要求的上下同步

本轮初版曾采用top=smooth(e/.62)、bottom=smooth((e-.45)/.55)。用户指出先上后下后，最终改为两条边共同smooth(e/.62)。e仍为480ms原Bezier(.25,0,.25,1)后的展开度，左右仍e，圆角仍r×(1-e²)。上下按各自真实到边界的距离同时开始、同时收敛，返回共用此路径。

最终模型逐帧使用相同上下归一进度，JVM覆盖完全相等和端点/重定向；实际录屏对比也已目视确认。模型值与视频估时不等同于读取Animator内存。

## 最终视觉证据

最终交付位于build/v062b3-polish3/sync。

- day-app.mp4、dark-app.mp4为最终同步版，日间254帧、暗色104帧，裁顶部100px系统状态栏后逐帧PTS最大差0，原速保持，应用标题/底栏完整。
- day/dark-*-keyframes.jpg及*-000/010/025/050/075/100.png为真实阶段帧。
- compare-vertical-push.jpg、compare-vertical-pop.jpg，上排为本轮先上后下过程版，下排为最终同步版，约25%/50%/75%。
- compare-push-25-50-75.jpg、compare-system-pop-25-50-75.jpg为上一轮polish2与本轮最终版对照。
- day/dark-visual-final.json、after-frame-state.json、dark-after-frame-state.json记录实际帧号/PTS、约阶段和模型参数。暗色估时较粗，可有数帧误差。

已人工逐张查看明暗进入和返回。来源内容现在延续在统一轮廓内，原位嵌套胶囊已去掉。日间新标题/正文各119个高置信匹配，暗色各43个，目标位置固定在原生ROI y112/y536。日间清楚新正文36/18/39/20帧、暗色26/16帧，与清楚旧标题共存帧均0，正文清楚而新标题不匹配帧均0。来源19sp与目标30sp同时清楚帧均0。这些是局部模板检查，完整观感仍以录像为准。

暗色纸面首尾RGB(28,26,24)，运动中位范围最高(33,31,27)，包含原主题色混合。临时离屏控件色阶和真实HWUI仍有差异。

同步版约25%时覆盖的纸面比先上后下过程版更大。中段身份交接更明确，但稀疏留白依然显眼。这一限制保留在交付结论中。

## 轻量局部展开备选

全屏终点、稀疏内容、最终字号/坐标这组约束会保留较多纸面。可选择“局部展开＋短交接”作为下一步备选，来源卡片在原位增加约40–56dp高度、两侧各扩约8dp，随后以约80ms原位内容交接呈现已接受页面。它将扩张距离限制在来源附近，目标正文仍按真实页面布局显示。这是供用户选择的独立视觉方案。本轮以当前同步候选及其限制收尾。

## 检查与真实设备

- 完整scripts/test-food-analysis.ps1通过，含61678同步容器/边界/身份/source移动检查、6219设置、365底栏、1160根页及其余JVM回归。git diff --check通过。
- Android UpgradeSmoke增加背景恢复（含异常finally）、透明来源、共享baseline/元数据、边通道、统一targetAlpha、反向SourceRect、固定目标坐标及旧clock/清理检查。最终生产classes+SDK37 javac通过，runner状态为只编译。
- PowerShell build.ps1 -NoUpload、verify-food-apk.py、原证书v2/v3通过。min26/target37，4模型/双ARM8库/6固定摘要、依赖/字体/许可/QA隔离通过。
- dist/EatRecord-0.6.2-beta.3-debug.apk，121212017 bytes，SHA256 8af12e51e6596eb97813cab0d4757a1e95f665ed3cd3902aca7583a3bd6be8ba，证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 魅族21/461QYGFL22429/Android16 API36，普通包保留数据覆盖。最终同步包正常明暗进退、顶栏/系统返回、即时/120ms返回、返回中点击安全处理、落位重进、父scroll文字bounds635..699px保持、动画0、迹↔我及原根正文右滑均通过。三倍率恢复1.0，日间/我页恢复。
- 通知横幅期间顶部输入与录制保护会停下。最后的屏幕下部非录屏回归读取系统实际触摸区并留96px余量，同时校验焦点与应用包节点。通知显示配置沿用用户设置。
- 最终gfx为477帧、30次deadline jank(6.29%)，p50=11/p90=19/p95=21/p99=31ms，慢Bitmap上传0。此采样包含准备与动画，仍有慢帧。

## 修改范围与待验证项

生产文件为SettingsContainerTransform.java、SettingsContainerMath.java、ReleaseNotes.java。测试为SettingsContainerMathTest.java和tests/android/UpgradeSmoke.java，交接为四份约定文档与本文。16份保护文件摘要一致，包含MainActivity/PageCrossfade/UiTokens/Manifest及根页/底栏实现，业务数据和存储/网络/JNI架构沿用当前实现。

安全降级继续处理锚点失效/布局异常/超预算/不可容纳内容。系统和顶栏仍由原owner提交单次返回，旧回调只清自己的会话。关闭SETTINGS_CONTAINER_MOTION_ENABLED可走原安全设置模式。

API26–28纸面回退、大字/RTL/短窗/旋转分屏/TalkBack全流程尚待实际设备覆盖。来源保留原字号、目标标题采用原目标字号，交接时仍可感到字号变化；目标页面本身内容较少，纸面留白与父文字的实体边缘遮挡仍存在。

Git main/8cc26c5。工作区保留既有dirty/untracked，正式server/dist清单为0.6.1/code193。本轮候选保持本地Beta交付状态，等待用户观看。

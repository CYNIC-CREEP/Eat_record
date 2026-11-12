# 0.6.2-beta.1｜镜片主导与整栏柔和反馈

清理说明（2026-11-03）：用户已要求结束第一阶段并清理录屏、截图及临时取证文件。下文媒体路径仅作历史过程记录，文件已删除；源码、测试／构建日志、摘要和必要回退快照保留。

历史参数：整栏扩张比例及背景绘制空间已由[V062_BETA2_REPORT.md](V062_BETA2_REPORT.md)更新。本文保留beta.1对应证据。

2026-11-03（Asia/Shanghai）。这是当前返修中的底栏专项，仍为beta.1/code194。之前恢复的根页横滑保持，本次没有再修改RootPagePager／RootPageMotion／GestureSpring或设置转场。

## 根因与修改

- 旧镜片图文可放大16%，同时真实Item还保留0.97缩放／0.72透明度按压反馈；背景只增长约11dp且中段主题色alpha低至43/255，没有整栏绘制响应。因此更容易看到按钮变化与浅透明块。
- NavLensGeometry将普通52dp高镜片名义宽度增长调整为约16.64dp（最大18dp），高度约增加14.14dp；图文改为最高1.035倍。jelly继续由原物理速度／加速度驱动，宽度乘1+0.8j、高度乘1−0.38j，j限制−0.18..0.24，尺寸还受实际空间约束。
- BottomNavIndicator按当前位置两侧可用空间限制宽度，边缘膨胀不再靠把中心推向内侧。镜片仍上下对称，不作真实上移。位置弹簧246.740110／0.85、跟随1024／0.9、lift631.654682／1、jelly100／0.4均保留；不改变导航目标、速度重定向或Item映射。
- MainActivity的底栏触摸分支在新视觉启用时保持真实Item scale=1、alpha=1；纯视觉层承担反馈。原点击回调、目标、Item顺序及矩形不变，关闭新视觉时仍保留原反馈路径。

## 整栏呼吸与镜片质感

- PaperNavLayout只在onDraw中缩放背景纸面：横向最多0.8%、纵向最多5%，并小幅增强原阴影。子View不随Canvas变换，因此图标、文字、命中、安全区与测量不变。反馈由lift及jelly驱动，静止归零，不增加永久帧循环。
- BottomNavLens采用局部柔焦近似，没有实时底图采样或光学折射。当前软件绘制层若整体施加RenderEffect会连文字一起模糊；为避免该问题，柔化只画在背景通道。
- 背景层次：薄乳白／纸色底→更饱满的主题色渐变→6dp BlurMaskFilter柔化的低alpha内高光→轻薄表面高光与软阴影→清晰的原图标／文字→细边光。渐变、矩阵、Paint、Path和模糊遮罩复用，不逐帧分配整屏Bitmap。
- 日间主题染色alpha为146／112／139，夜间115／84／108，比上一方案更有存在感；没有把整块做成灰白。原UiTokens黄／绿／红／蓝／灰色映射继续使用。

## 实际文件

| 文件 | 本次专项变化 |
| --- | --- |
| MainActivity.java | 底栏背景绘制swell、真实Item去缩放／去压暗、视觉边界及反馈接入；未改根页路由函数 |
| BottomNavIndicator.java | 容器反馈及围绕当前中心的空间约束 |
| NavLensGeometry.java | 背景主导的尺寸、3.5%图文辅助、整栏反馈映射 |
| BottomNavLens.java | 局部柔焦近似、多层染色／高光／阴影 |
| TabMotionTest.java、tests/android/UpgradeSmoke.java | 镜片主导／按钮不变形／整栏反馈断言；Android runner只编译 |
| scripts/AdbMotionGesture.java | shell输入前台保护与短按释放，不是APK／instrumentation，不写夹具 |
| 四份交接文档及本文 | 当前有效参数、证据和边界 |

专项起点的RootPagePager SHA256已保存并比对一致。数据、网络、JNI、备份、图片、登录同步和PageCrossfade没有本次专项修改；未执行Git破坏性命令、提交或发布。

## 构建与验证

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-food-analysis.ps1`通过，含360项本轮物理／几何检查及既有1160项RootPageMotion检查，其他JVM回归全部通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`通过。普通包121199729 bytes，SHA256 `239beabd74bc38c3f6e5b8aebb6fb6a0b959fc3f93a22890d98180b134e65e69`。
- `verify-food-apk.py`及原证书v2/v3通过：4模型、双ARM8库、6固定摘要、依赖／字体／许可、QA隔离正常，min26/target37。更新后的QA runner仅javac编译通过，不在日常手机执行。
- 延续用户本次明确授权，魅族21／461QYGFL22429同证书普通包覆盖安装及启动成功；不卸载、不清数据、不注入测试记录，不主动操作API／同步。
- 本次只用ADB真机，没有启动模拟器。日间／暗色短按与回落、当前项及其他项点击、镜片移动、快速反向、原页面横滑／拖动反向／取消有实际录屏及关键帧；日间模式已恢复，未改手机全局字体／RTL／动画倍率。
- 魅族长按会触发Aicy系统菜单，未关闭用户系统功能；当前取证使用约360ms短按，并监测前台包名。前台离开应用即停止并拒绝证据。此前混入相册／桌面及Aicy画面的录屏不能用于验收；交付使用`build/v062b1s/visual-probe.json`标记valid=true的新录屏。
- 可审阅：`build/v062b1s/lens-demo.mp4`、`lens-comparison.png`（同一真实录屏：上稳态／下短按鼓起）、`lens-phases.png`、`dark-nav.png`。日志为build-normal.log、jvm.log、apk-verification.json、signature.txt、qa-compile.log。没有用数学通过替代视觉检查。

## 边界与最小后续

- 当前为局部柔焦近似，不是真实实时背景折射。镜片主导比例和光影仍需用户主观确认。
- 本版真机未改全局设置去重跑大字体、RTL及动画缩放0；几何边界／实际bounds映射和关闭动画的收敛代码保留，但不冒充专项设备通过。其他主题共用UiTokens渲染，上一返修曾实机切换全部主题，本次没有逐色重新录制。
- 不进入设置子页第二阶段。若仍需细调，最小下一步只调整镜片的染色密度与背景增长比例；不扩大到导航或实时全屏模糊。
- `NAV_LIQUID_MOTION_ENABLED=false`可关闭新底栏视觉，原根页横滑继续；`ROOT_TAB_SLIDE_ANIMATIONS_ENABLED`没有在本次专项更改。专项起点快照在build/v062b1s/before，阶段反向patch须先check，不能用HEAD整文件覆盖此前成果。

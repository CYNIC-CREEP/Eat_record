# 0.6.2-beta.2｜参考胶囊底栏强化整栏扩张

清理说明（2026-11-03）：用户已要求结束第一阶段并清理录屏、截图及临时取证文件。下文媒体路径仅作历史过程记录，文件已删除；源码、测试／构建日志、摘要和必要回退快照保留。

2026-11-03（Asia/Shanghai），versionCode195。只优化底栏视觉，未提交／上传，正式更新清单保持0.6.1/code193。

## 改动与参考关系

- 两张参考图的区别不仅是选中镜片变大，外层胶囊也明显变宽。beta.1整栏横向最多0.8%，短按实际更低，所以不够显眼。本版将整栏峰值提高到宽4.5%、高7.5%，按同一lift／jelly连续鼓起并回落，图文仍只辅助放大3.5%。不复制参考App的四项导航或蓝色图标，保留一蔬一饭三项和主题色。
- 新增BottomNavBackdrop.java：小型非交互背景绘制层，位于原navHost后面。绘制区域水平多20dp、上24dp／下16dp，避开原软件图层裁剪；原底栏与Item尺寸、位置、命中、安全区均不改。就绪后原PaperNavLayout不再重复画背景，避免双重边框。
- MainActivity只接入该背景、几何同步、原模糊的同步及swell转发。NavLensGeometry把整栏反馈映射到完整峰值；普通52dp高镜片名义宽度增长18.72dp、高度约18.35dp（受可用空间限制），镜片独立于外层胶囊鼓出。位置物理、颜色、柔焦、高光与图文绘制沿用beta.1。
- 根页横滑、RootPageMotion／GestureSpring、PageCrossfade设置转场、业务数据／网络／同步／备份／JNI不改。RootPagePager、RootPageMotion、PageCrossfade、BottomNavMotion、BottomNavIndicator、BottomNavLens与本轮起点SHA256一致。
- AndroidManifest.xml／ReleaseNotes.java为0.6.2-beta.2/code195；TabMotionTest新增整栏可见增长及精确归位约束，隔离Android QA补独立背景非交互／绘制空间断言。

## 实际验证

- git diff --check通过，仅接手前文档LF／CRLF提示。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-food-analysis.ps1`通过，含365项物理／几何及1160项既有根导航检查，其余JVM回归全部通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`成功；普通包`dist/EatRecord-0.6.2-beta.2-debug.apk`为121199729 bytes，SHA256 `013d7254736a5d69bcda7d41aeb5a3daa444ced93db50f4b7a9c3cd18a09454d`。
- verify-food-apk.py、原证书v2/v3签名通过，min26/target37；4模型、双ARM8库／6固定摘要、字体／许可、依赖闭包和QA隔离正常。QA runner仅编译通过，未在日常手机执行。
- 延续本次已授予的覆盖安装／无损UI检测授权，在魅族21／461QYGFL22429同证书覆盖194→195并启动成功；普通包无debuggable，不卸载、不清数据、不注入测试记录。
- 只用ADB真机，没有模拟器。当前项连续三次短按、快速今日→我→迹及反向操作有前台保护录屏。对原生关键帧做视觉检查：外层胶囊明显变宽／变高，镜片再独立鼓出，边缘圆角完整，无旧背景残留内框；真实图文未随整栏一起放大。原页面仍横移。
- 证据：忽略的`build/v062b2/visual-probe.json`为valid=true；`nav-reference-demo.mp4`为压缩预览，`phone-raw.mp4`为原录屏，`nav-compare.png`上为稳态／下为短按膨胀，均来自真实画面。日志为build-normal.log、jvm.log、apk-verification.json、signature.txt、qa-compile.log。中断后已确认没有仍运行的screenrecord。

## 边界与交接

- 本版未完成新的暗色、大字体、RTL及系统动画0真机专项；不把beta.1的暗色记录冒充beta.2通过。数学边界检查和共享UiTokens路径不替代设备覆盖。
- 保留NAV_LIQUID_MOTION_ENABLED开关，关闭后使用原背景；ROOT_TAB_SLIDE_ANIMATIONS_ENABLED未更改。
- 整栏鼓起幅度、柔焦质感最终仍待用户主观验收。暂不继续加幅度或进入设置子页动效。
- 本轮起点源码／文档SHA256、旧patch和beta.1原包在`build/v062b2/before`及before-manifest.json；仅本轮反向patch需先check后再应用，不使用HEAD整文件覆盖旧未提交成果。

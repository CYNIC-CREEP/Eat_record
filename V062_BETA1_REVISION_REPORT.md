# 0.6.2-beta.1 返修：恢复横滑与透明水滴镜片

清理说明（2026-11-03）：用户已要求结束第一阶段并清理录屏、截图及临时取证文件。下文媒体路径仅作历史过程记录，文件已删除；源码、测试／构建日志、摘要和必要回退快照保留。

后续底栏视觉专项已更新增长比例、图文强调及柔焦层次，当前参数以[V062_BETA1_LENS_REPORT.md](V062_BETA1_LENS_REPORT.md)为准。本文保留横滑恢复与前段返修证据。

日期：2026-11-03（Asia/Shanghai）。按用户贴文标题作为beta.1返修，仍为code194，不转正式、不提交或上传。第一轮报告V062_BETA1_REPORT.md仅作历史，Fade／2dp方案不再是当前实现。

## 原因与恢复

1. 上一轮在RootPagePager中让TAB_MOTION_ENABLED同时选中startFade及alpha绘制分支，因此虽然RootPageMotion仍算出连续位置，真实View被固定translationX=0，原卡片横移被替换。现已逐段对照HEAD恢复translationX=(pageIndex-position)×width、alpha=1及原GestureSpring调用，不整文件checkout；RootPageMotion.java、GestureSpring.java均未修改。
2. 点击仍用原0.48秒响应弹簧，拖动直接1:1更新位置，不启动追赶手指的时长动画；松手才由原位移／速度规则选择目标，手势0.4秒响应。保留原三根页、0／1／3身份、缓存、取消、反向、越界与子控件仲裁。RootTabFade.java仅为上一轮新增，已安全移除并替换对应测试。
3. 底栏原来被2dp增长／抬升上限和jelly的0.45倍映射压弱，并受原PaperNavLayout软件层范围限制。现保留物理内核与速度重定向，另用小尺寸root兄弟绘制层避开裁剪；底栏、Item、点击矩形及业务回调不变。

## 最新视觉（含用户中途反馈）

- 用户指出镜片不上下居中后，取消6dp真实向上位移；背景围绕原底栏中心上下对称膨胀，图文仍围绕原Item中心放大。抬升改由膨胀及轻薄边缘光影表达。
- 常规52dp高背景名义宽度增长约11.44dp（上限12dp），高度增加约8dp；jelly依据原秒制速度／加速度，横向乘1+0.95×j、纵向乘1−0.45×j，受实际视觉区域限制，j约束为−0.18..0.24。图文最大1.16倍，只变Canvas，不变真实触摸范围。
- 不再铺实色accentSoft。UiTokens.navLensTint集中管理黄／绿／红／蓝／灰主题水滴色；日间填充alpha为92／43／71，夜间108／57／91，沿纵向连续渐变，并有细边和窄高光。是真实半透明绘制，不宣称背景采样或光学折射。
- 底栏纸面→原图文（镜片区clipOut）→透明镜片→镜片内重绘的原图标／文字→细边高光。BottomNavItems只裁剪绘制，防透明及放大时叠出两套字；BottomNavLens不持有点击、焦点或无障碍节点。没有新图标／字体资源或永久帧循环。
- 页面手势已有临时position仅用于镜片预览；导航真正提交的target仍来自MainActivity／RootPageMotion。点击时页面和镜片各自运行，不互相等待，不在动画完成时再次导航。

## 文件与边界

- 恢复／接入：RootPagePager.java、MainActivity.java、UiTokens.java、ReleaseNotes.java；MainActivity中的原模糊仅同步到新增视觉层，不改设置子页转场。
- 保留并增强：BottomNavMotion.java（物理内核未重写）、BottomNavIndicator.java；新增BottomNavLens.java、BottomNavItems.java、NavLensGeometry.java，删除仅上一轮新增的RootTabFade.java。
- 测试：TabMotionTest.java、scripts/test-food-analysis.ps1、tests/android/UpgradeSmoke.java；新增scripts/AdbMotionGesture.java为shell手势工具，保持一个downTime，只有输入事件，不是APK／instrumentation，不写偏好或测试数据。
- 四份交接文档同步。接手前的V061_DELIVERY_REPORT.md修改保持原样，未清理其他未提交成果。
- NutritionStore／FoodUi、数据与备份、API／云同步／凭据、日历内部运动、图片／JNI、PageCrossfade设置转场、构建架构均未改动。

## 实际验证

- git diff --check通过；完整powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-food-analysis.ps1通过，含357项本轮物理／横滑／居中及边界几何检查和1160项既有RootPageMotion序列检查。旧4130项Fade检查不再当作当前证据。
- powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload普通构建通过。verify-food-apk.py、原证书v2/v3签名通过；4模型、双ARM8库／6固定摘要、字体／许可、依赖闭包、QA隔离正常。最终包摘要见CURRENT_TASK.md。
- 调整后的隔离QA runner只编译通过，本次未执行，不在日常手机安装QA包。按用户明确“只用ADB真机”，没有启动模拟器。
- 用户明确授权后，在魅族21／461QYGFL22429用install -r --no-streaming安装同证书普通包；不卸载、不清数据、不写夹具，不删除Key、未主动触发识别／同步操作。
- 已观察真机按住膨胀／取消恢复、A→C→B及反向点击、根页300px停留与150px反向、取消归位、设置进入／返回、日夜模式和黄／绿／红／蓝／灰主题。停留阶段两张间隔约半秒的页面内容区域（排除状态栏和镜片）像素校验一致。原始录屏及关键帧在忽略的build/v062b1r，交付预览亦在该目录。
- 原手机黄色／日间模式已恢复；未改系统字体、RTL、密度或动画倍率。未编辑实际餐食，未读写私有日记／API配置；不声称对真实日记做了字节级比对。

## 开关与只回退本轮

- UiTokens.NAV_LIQUID_MOTION_ENABLED=false后重建：关闭新镜片，仍走原底栏背景与根页横滑。
- ROOT_TAB_SLIDE_ANIMATIONS_ENABLED独立控制松手／点击后的横移弹簧，关闭则直接到目标，手动拖动仍1:1；不存在回到Fade的分支。
- build/v062b1r/before保存本轮开始时的源码／文档、SHA256及旧APK（d158c3c…），包含上一轮尚未提交的成果。this-round.patch仅描述该快照到本轮结束的变化，可先用git apply --reverse --check检查，再由用户决定是否执行反向应用；不要用HEAD整文件恢复。当前未执行回退。

## 仍未验证

没有把数学检查当成视觉验收。最终高级感仍需用户亲自体验；本轮无全局动画0／RTL／大字体真机专项、TalkBack完整朗读、横屏／折叠屏、极端低内存、营养API或同步准确率／速度结论。系统动画关闭分支沿用现有ValueAnimator检查，但本轮未修改日常手机全局设置来实测。

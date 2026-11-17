# 0.6.3-beta.6 日间底栏与封面节奏

2026-11-14，Asia/Shanghai。versionCode208。

## 安装包

[普通安装包](dist/EatRecord-0.6.3-beta.6-debug.apk)，127207837 bytes（121.315 MiB）。SHA256为`b92377ff16bd171895a0373617524123a92835db2607512bebeb57f53cbd7a69`。min26／target37、双ARM、多DEX，无debuggable，沿用原v2／v3证书。详见[验包明细](build/v063b6/delivery.json)。

## 修改

- 底栏移除可见文字，餐碗、脚印和头像图标按绘制边界上下居中。三个Tab的语义标签用于读屏及操作定位。
- 主题提交时局部重建内部ComposeView、Recomposer和帧时钟，外层导航状态、回调和生命周期owner继续承担原有职责。实际uiMode变化后的配置回调同步原生绘制宿主，揭示期间也校正显式主题。
- 新组成的底栏跳过历史按压计数的首个值，避免主题刷新重播旧按压。外层重新挂载时绑定新View树owner，主题快照在尚无新帧时可绘制当前静止视觉。
- 开本总时长1000ms，放大阶段300ms，封面翻开阶段40–660ms。封面更早转向并显露内页。合本捕获当前封面阶段，从该形态衔接返回。

纯几何计算中，300ms封面正面投影覆盖约60.7%，450ms内页正面已显露。该数据按名义进度计算，实际耗时受系统倍率和显示帧影响。详见[时序记录](build/v063b6/book-presentation-timing.json)。

## 验证

- [JVM回归](build/v063b6/jvm.log)通过，包含151962项封面几何与时序检查。[NoUpload构建](build/v063b6/build.log)、[APK内容](build/v063b6/apk.json)及[签名](build/v063b6/signature.log)核验通过。
- [Beta6专项QA](build/v063b6/qa.log)通过，使用独立Android16／API36模拟器、1080×2340及合成餐食，HTTP(S)请求为0。
- [图标像素](build/v063b6/qa-evidence-validated/v063b6-icon-centering.json)检查三个Tab及各主题状态，垂直中心误差在2dp内，原文字区域无文字墨迹，实际屏幕和软件图均通过。
- [六次主题切换](build/v063b6/qa-evidence-validated/v063b6-live-theme-cycles.json)在同一Activity内完成，夜间采样亮度43，三次返回日间均为253。
- [原生层恢复](build/v063b6/qa-evidence-validated/v063b6-native-surface-recovery.json)先通过黑色硬件滤镜建立亮度0的旧层，再切回日间，验证旧内部View退出、新宿主及日间像素恢复。这是明确的模拟故障注入。
- [重新挂载](build/v063b6/qa-evidence-validated/v063b6-reattach.json)验证新owner、同一外层宿主、图标像素及后续Tab操作。
- [实际开本采样](build/v063b6/qa-evidence-validated/v063b6-cover-presentation.json)记录31个绘制样本，约13.3%整体进度已有明显翻动，约36.7%已转过半周。[半途返回](build/v063b6/qa-evidence-validated/v063b6-opening-back.json)和日记完整性检查通过。
- [普通包交互](build/v063b6/navigation.log)通过快速Tab、菜单反向和隐藏动作检查。另完成一次实际日夜返回及开本／返回，进程身份持续一致、崩溃缓冲为空。见[结果](build/v063b6/normal/result.json)、[日间返回截图](build/v063b6/normal/day-returned.png)与[开本录屏](build/v063b6/normal/opening.mp4)。
- [保护核验](build/v063b6/final-audit.json)核对当前源码与编译时刻、Kotlin摘要、原签名及上一轮Beta5包。最终检查时模拟器连接及进程均已退出，见[设备状态](build/v063b6/final-device-state.json)。

## 验证边界

截图中的蓝黑背景与日间图标组合指向原生绘制状态残留，Flyme内部原因属于推断。标准模拟器通过了绘制层故障恢复及连续主题切换，魅族21的实际恢复效果与封面观感待用户安装复核。

## 交接

main分支HEAD为2434aae，既有未提交修改保留，正式分发清单为0.6.2/code202。下一步为Beta安装反馈，最终版及上传授权到达后执行同版归档。前一轮见[V063_BETA5_REPORT.md](V063_BETA5_REPORT.md)。

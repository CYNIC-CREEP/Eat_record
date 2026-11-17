# 0.6.3-beta.5 动画与布局修复

2026-11-14，Asia/Shanghai。versionCode207。

## 安装包

[普通安装包](dist/EatRecord-0.6.3-beta.5-debug.apk)，127207837 bytes（121.315 MiB）。SHA256为`95247046d449cb2fbf61b397f0aa8985854c4506cb434fedf3e133248f8a3162`。原v2／v3证书、min26／target37、双ARM及多DEX，无debuggable。见[验包明细](build/v063b5/delivery.json)。

## 修改

- 日期餐纸使用全宽绘制区域和内部侧边距，保留静止卡片宽度，修复滑动时20dp边缘遮挡。日历和底栏间隙调整为28dp，图片条目的额外高度减少56dp，餐图尺寸保持204／192dp。
- 封面由单个平面改为32段曲面投影，书脊固定，保留真实封面、纸背、圆角和阴影。开合共用曲面与进度，名义1000／800ms保留。
- PageCrossfade、GestureSpring、BookOpenView和局部Compose限制每帧推进至33.3ms。页面准备期间底栏保留当前位置与速度，准备完成后从首个新帧续接。长帧积压时间直接舍弃，较差渲染环境下总时长会延长。
- 干净根页缓存同时用于记录本返回今日和我，避免正向底栏退出重建整组三页。减少餐纸／图片裁剪对象分配，完全不透明的快捷按钮省去额外离屏透明层。
- 日夜切换快照加入上一帧底栏，揭示层统一覆盖页面与新底栏。材质按明暗及主题色重建，原生Compose子View显式关闭自动深色处理。
- 普通点击按下缩放由0.985调整为0.975，抬起恢复由205ms调整为180ms，按下帧直接反馈。

局部Recomposer继续绑定本地生命周期，并在其帧时钟上限制视觉推进。实现参考[Android生命周期感知Recomposer说明](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowRecomposerFactory)。

## 验证

- [JVM回归](build/v063b5/jvm.log)通过，包含20项时钟检查和151223项柔性封面几何检查。[NoUpload构建](build/v063b5/build.log)、[APK内容](build/v063b5/apk.json)、[签名](build/v063b5/signature.log)核验通过。
- [Beta5专项QA](build/v063b5/qa.log)通过。测试为独立Android16／API36模拟器、1080×2340，使用合成餐图和营养，HTTP(S)请求为0。
- [首页布局](build/v063b5/qa-evidence-final/v063b5-home-layout.json)测得日历间隙74px、底栏间隙75px，全宽1080px、卡片974px。实际日期拖动的硬件截图及侧边颜色探针通过，静止邻页隐藏、取消保持日期、日记不变。外侧留白手势归属使用应用内dispatch验证，系统边缘返回独立于该探针。
- [其他设置返回今日](build/v063b5/qa-evidence-final/settings-to-today-timeline.json)在240ms人为停顿后保留16个中间绘制帧，最大进度单步6.94%。[记录本返回今日](build/v063b5/qa-evidence-final/book-to-today-timeline.json)和[返回我](build/v063b5/qa-evidence-final/book-to-me-timeline.json)各有14个中间绘制帧，缓存复用及快速改选通过。
- 四次实际日夜切换均检查揭示早期旧底栏颜色和结束后的亮／暗像素，均通过。[FrameMetrics](build/v063b5/qa-evidence-final/v063b5-frame-metrics.json)保留真实帧耗时与人为停顿标记。
- [普通包快速Tab与菜单检查](build/v063b5/navigation.log)通过。[开本录屏](build/v063b5/normal/book/opening-reversal.mp4)和[连续帧](build/v063b5/normal/book/opening-contact.jpg)核对了可见弯曲、半途返回、完整开合及书架恢复。见[结果](build/v063b5/normal/book/result.json)。[其他设置通过底栏返回我](build/v063b5/normal/settings-to-me.json)通过。[关闭动画时的Tab与菜单](build/v063b5/normal/zero-motion.json)通过。

## 观测边界

模拟器仍记录到长帧，本轮修复了停顿后的快进并减少重复准备开销。FrameMetrics包含截图及240ms注入停顿，不能外推魅族21的掉帧率。该模拟器上的旧版四次切换未复现手机截图中的持续黑底，本版四次切换像素复核通过。用户要求本轮不对手机使用ADB，魅族21的实际观感与持续黑底修复效果待安装复核。

## 交接

main分支HEAD为2434aae，既有未提交修改继续保留。原签名和Beta4包已复核，见[保护核验](build/v063b5/final-audit.json)。测试设置已恢复，普通包留在独立模拟器内，模拟器已关闭，见[设备收尾](build/v063b5/final-device-state.json)。正式分发清单为0.6.2/code202，本轮交付本地Beta。下一步由用户在魅族21安装复核，再根据最终版及上传指令执行发布归档。前一轮记录见[V063_BETA4_REPORT.md](V063_BETA4_REPORT.md)。

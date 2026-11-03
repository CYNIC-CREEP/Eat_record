# 0.6.1-beta.1 本地交付与验证

日期：2026-10-29（Asia/Shanghai）。版本0.6.1-beta.1/code185；无提交、标签、push或上传。线上清单保留0.6.0/code184。本轮开始三份交接文档已脏，未覆盖其规则整理或reset/clean。

## 实现

- 新增餐次选择默认早餐/午餐/晚餐；其他设置增加默认关闭的夜宵开关，分享导入同策略。关闭不删除/隐藏历史夜宵，旧数据仍可管理。
- 今日与已开启营养师使用AdvisorPager中的两张真实View纵向相邻呈现，复用GestureSpring。拖动乘0.62产生阻尼，超过96dp位移或30dp加1100dp/s同向速度后换页；取消回原目标，多指吞到终态，重抓从当前位置接续。正文/输入控件、展开日历及日期横滑优先；正文到边界后再新起一次切页手势。隐藏今日freeze，返回resume；隐藏页只读准备，不发自动API。
- 我页右上角ThemeModeIcon月亮/太阳替代旧个性化开关。ThemeRevealView对一张旧主题快照挖正圆渐变透明洞，露出实际新主题（文字、图标、卡片均切换），并非黑白遮盖。中心按按钮与root的window坐标相减，最大半径按四角距离加柔边计算。
- durationMillis=1800、easing=(.65,0,.35,1)、featherWidth=20dp、iconDuration=250；集中在ThemeRevealView.java的DURATION_MILLIS、easing()、FEATHER_WIDTH_DP、ICON_DURATION_MILLIS。图标在圆心区域揭示后轻旋转/缩放。
- 关闭系统动画、Surface/TextureView、FLAG_SECURE、快照超过32MiB或分配失败时直接应用真实主题。动画期间屏蔽连点；尺寸/后台/结束清理，清理可重入。系统状态/导航栏由窗口主题同步，不属于应用圆形图层。
- 默认自动上传路径和OpenList/蓝奏PS/Node入口在打包、认证及浏览器副作用前拒绝非x.y.z正式版本。四入口Beta拒绝实际检查通过；未进行认证或远端写入。旧发布脚本的其他安全流程仍未固化，不因此认为可以直接发布。

## 证据

- 普通与QA均build.ps1 -NoUpload构建成功，610个生产class逐字节一致；JVM完整脚本通过。日志build/v061b1-build.log、v061b1-qa-build.log、v061b1-jvm.log。
- 独立EatRecordV06QA/emulator-5586运行revision-v061正常动画与系统动画缩放0两分支通过；动画设置已恢复1。三餐入口/显式夜宵开关/历史日记不变，今日点击不进入、取消/上下滑、隐藏owner和禁用检查通过。
- 正常分支双向检查圆内新UI/圆外旧UI/20dp柔边逐点混合，另等真实绘制后对比HWUI截图与实际组成；检查完成与Stop取消后快照/父层/owner释放。截图位于build/v061b1-final-*-hwui.png及独立模拟器qa目录，均为合成数据。
- 既有revision-v28 all、release-v060 all及revision-v33 all通过，覆盖连续根导航/日期和餐次原位保存/媒体暂停回归；证据build/v061b1-root.log、v061b1-editor.log、v061b1-returns.log。
- 验证过程中先关闭独立模拟器的System UI ANR遮挡，再重新运行；该环境错误不算App通过。补查清理时修复了View detach重入，最后软件与HWUI专项全部重跑通过；不沿用失败候选截图。
- 普通APK通过verify-food-apk：四模型、双ARM八库/依赖闭包/固定摘要、默认字体与许可、退役组件移除、无QA/instrumentation。打包教程与当前源码字节一致，两份教程和正式更新清单分别字节一致。

## 最终普通包

- 文件：dist/EatRecord-0.6.1-beta.1-debug.apk（后缀是现有产物约定，普通包不启用debuggable）。
- 大小121166961 bytes（115.55MiB）。
- SHA256：6b5fba84d89db8c298a0d101f85fe8375c419057bdfe1b6822e9854613bbaa24。
- version185、min26/target37；v2/v3验签通过，沿用证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。

## 修改文件

- 业务：src/com/eatrecord/app/MainActivity.java、AdvisorPager.java、ThemeRevealView.java、ThemeModeIcon.java、ReleaseNotes.java；AndroidManifest.xml。
- 构建/上传拦截：build.ps1、scripts/publish-openlist.ps1、scripts/upload-lanzou.ps1、scripts/upload-lanzou.mjs。
- 测试：tests/android/UpgradeSmoke.java（新增revision-v061，根层级断言允许真实纵向父容器）。
- 文档：CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md、README.md、本报告；assets/help/getting-started.md与server/新手教程.md。

## 验收边界

本轮仅独立模拟器和合成夹具，无日常手机安装/改动、无真实付费API。最终普通包已在同一独立模拟器覆盖安装，冷启动Status: ok；测试动画缩放恢复1，独立模拟器已关闭。未测品牌机手势手感、长时间帧率、真实服务商、系统备份传输、实际拍照保存；不宣称全设备零卡顿或营养准确率。Beta仅本地交付，等待用户体验；新一轮修改加Beta号，未经明确批准不转正式/不发布。

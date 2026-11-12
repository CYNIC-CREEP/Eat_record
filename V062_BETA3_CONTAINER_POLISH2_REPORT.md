# 0.6.2-beta.3｜容器第二次精修：静态父页与原位内容

2026-11-04（Asia/Shanghai）。仅“其他设置→餐次管理”试点，仍beta.3/code196，同轮需求修正。最终普通包已保留数据覆盖到魅族21；不推广其它设置，不提交或发布。本文取代上一轮关于父页提前淡出和整页偏移的当前结论。

## 原机制与实际原因

1. 真实父View在导航render时被替换，当前业务树会暂时alpha=0/禁输入；上一轮可见父快照又在e=.18归零，所以早期空间参照消失，不是业务页面本身缺失。
2. Rect四边从源行到整页插值始终有效，但page bitmap绘在y=bounds.top-end.top，标题/分组/开关/说明整张一起上移。真实业务View没有scale/translate，漂移发生在绘制纹理层。
3. 填色alpha=1-e²，父快照又已提前淡净，返回自然成为半透明、发白的小矩形。
4. 所有内容仍在同一动态Path内裁剪。若只把纹理偏移改0而不增加完整区域门控，会重新出现半行文字和正文先被截出的现象。

## 最终绘制策略

- 几何：保持480ms、Bezier(.25,0,.25,1)、原四边插值与r×(1-e²)。起点仍取整行真实矩阵bounds，终点仍content宿主实际区域。
- 父层：固定(0,0)、alpha=1作为非交互背景，在当前圆角Path外绘制。原列表不平移/缩放；容器覆盖多少，才遮住多少。旧父标题仅在增长顶边实际进入其文字bounds时局部淡出，未整体淡掉父页。
- 容器：1×1原始主题色像素始终以255不透明度填入Path，卡色向原纸色混合；透明的目标快照不再透出父列表。原Paper在API29+用独立RenderNode记录一次并复用，API26–28/不支持路径回退原Drawable，避免每帧Java重画纸点。没有新配色/玻璃/Shader。
- 新内容：page bitmap固定(0,0)，scale=1，标题与正文均为最终布局坐标，不再追随容器中心或top移动。
- 目标区域：布局后测sticky_paper_header完整区域及其真实TextView bounds；meal_management_page中“餐次”分组、开关/状态、说明文字组成正文bounds。各区域完整进入保守圆角安全范围后，在约16dp clearance中渐显，再乘同一e的20%..65%内容窗。正文作为保持排版关系的一组显现，标题单独门控；没有多个延迟任务。
- 来源行：固定真实startRect，alpha=1-实际正文alpha，保持可辨认再交接；不再向扩张矩形中心漂移。返回反向恢复原位置。

正文通常先进入容器范围，顶部标题较晚完整进入；父页标题仍属于未被覆盖的背景，这与上一轮“父页已消失却残留旧标题”不同。新标题只在完整进入轮廓后于最终位置出现，不把它提前放到容器外。

若短窗/大字导致完整内容区域在终态都无法安全容纳，直接展示已接受的真实页面，不让空白动画持续到结尾。该安全分支没有额外render或导航提交。

## 返回、取消与所有权

保留MainActivity原开/关、runContentSwap及系统/顶栏返回owner，业务只render一次，Animator end不再pop。父页恢复scroll后重新查meal_management_entry、测真实行/圆角/可见性；无有效锚点直接落真实目标，不沿旧坐标收缩。

PageContent只有不可变几何描述，随原page纹理一起转移给反向会话；当前Rect/currentRadius续接。旧clock/preDraw只处理自己的Session，clear恢复当前真实树、撤gate并回收Bitmap、1×1像素及Paper缓存。隐藏树按原host gate禁输入/焦点/语义，overlay本身不可点击；返回途中点隐藏行安全忽略，落位后重进正常。未新增预测返回手势进度。

## 真实视频与关键帧

证据位于build/v062b3-polish2：

- day-app.mp4：完整进入/顶栏返回/再次进入/系统返回。
- dark-app.mp4：暗色进入与系统返回。
- day-push-keyframes.jpg、day-top-pop-keyframes.jpg、day-system-pop-keyframes.jpg。
- dark-push-keyframes.jpg、dark-top-pop-keyframes.jpg。
- 对应*-000/010/025/050/075/100.png和day/dark-visual-final.json：原帧号、实际PTS、约阶段、原始与交付哈希。

最终视频与拼图仅裁去顶部100px系统状态栏，保留完整应用标题/底栏；日间255帧、暗色172帧与原片一致，每帧PTS最大差0，未变速。含无关系统ticker的原片在分析后删除，通知横幅遮挡的试拍作废并删除；没有复制通知内容到报告。坐标映射为app_y=原始_y-100。

已人工逐张查看明暗进入/返回六阶段：父列表早期清晰、局部被实体轮廓逐步覆盖；新内容在最终坐标显现；来源卡片位置明确，返回不再是一块透明浮片；终态没有副本遮罩。

实际模板检查：高置信新标题日间64/暗色25帧，原始ROI位置均y112；开关正文97/37帧，均y536（文字View约539），没有整体上漂。parent-static-occlusion-check.json中“照片查看”在约10%仍原位NCC>.999，覆盖后对比RMS<1，返回时在同位置恢复。这次正确契约是Path外保留父页、Path内不透明遮挡，不再沿用上轮“父footer整体清空”的断言。

阶段由固定来源行的透明度/梯度与同一曲线估读，未用旧标题位移拟合；暗色返回估时较粗，JSON已标近似，不冒充精确vsync同步。原片去除系统栏不改变这些PTS。

暗色空白纸面ROI(400,1830..700,1900)整段中位色均(28,26,24)，首尾一致，没有之前的整页+32跳亮。临时离屏控件底色/抗锯齿与真实HWUI仍可能有轻微差别。

## 构建、测试、真机结果

- git diff --check通过；完整test-food-analysis.ps1通过：39094容器几何/静态父页/固定坐标/完整区域/反向检查，6219普通设置、365底栏、1160根页及所有其余JVM回归。
- UpgradeSmoke更新真实单render、Path外父哨兵可见/Path内不透、原位标记、PageContent反向复用、旧clock/clear/纹理释放；最新生产classes+SDK37 javac通过。此runner会写合成夹具，只编译，未在日常手机安装/运行。
- build.ps1 -NoUpload普通构建、verify-food-apk.py、原证书v2/v3通过，min26/target37，无debuggable，4模型/双ARM8库/6固定摘要、依赖/字体/许可/QA隔离正常。
- 最终APK：dist/EatRecord-0.6.2-beta.3-debug.apk，121212017 bytes；SHA256 9588a7206c282bcd9878e4aa0b39c6f3fe48e8e8fd75dd8bfa71c38665ce4bdd；证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 仅ADB魅族21/461QYGFL22429/Android16 API36普通包保留数据覆盖。明暗正常进退、顶栏/系统返回、即时/120ms返回、返回中点击安全忽略/落位重进、scroll文字bounds635..699px保持、动画0、迹↔我及原根正文右滑通过。三倍率1.0、日间与我页恢复。未清数据/卸载/写夹具/编辑记录或夜宵/主动执行API同步备份。
- gfx-final.txt：4组进退487帧，22次deadline jank(4.52%)，p50=10/p90=18/p95=20/p99=31ms。包含准备与动画，不承诺零掉帧，也不拿不同时段采样微差宣称性能提升。保留crash buffer无本包Process记录，非长期保证。

## 真实修改范围与限制

生产仅SettingsContainerTransform.java、SettingsContainerMath.java、ReleaseNotes.java；测试仅SettingsContainerMathTest.java、tests/android/UpgradeSmoke.java；另更新四份交接文档及本文。MainActivity/PageCrossfade/UiTokens/Manifest与全部根/底栏源码等16份保护文件摘要一致，业务/数据库/网络/同步/登录/备份/JNI/构建架构未动。

SETTINGS_CONTAINER_MOTION_ENABLED=false仍可独立恢复原安全转场，业务owner不变。API26–28 Paper回退、大字/RTL/短窗/旋转分屏/TalkBack未全面真机验证；内容太大时采用明确的即时落页降级。稀疏设置内容仍自然留白，旧背景文字被边界覆盖时可能局部遮挡，属于前后层遮挡而不是目标内容半行提前露出；暗色临时控件有色阶差异，仍有少量慢帧。

Git仍main/8cc26c5，保留既有dirty/untracked，正式server/dist清单仍0.6.1/code193。没有reset/clean/stash、提交、标签、push或上传。本轮停止，只等待本入口观感反馈。

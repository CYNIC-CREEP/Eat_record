# beta.24：文字操作、真实导入抠图与连续切换

日期：2026-10-25。已完成0.6.0-beta.24/code172；仅本地开发、测试和普通APK交付，不提交/推送/上传，不转正式版，不操作用户日常手机。

## 用户问题与修改

1. 手写文字长按背景不居中、无法删除：为真实文字投影标注原始textValue，使用对称留白/垂直居中和独立选中背景。右上48dp触区只显示删除，不提供旋转/抖动。单一外层无障碍节点承接操作。删除确认后持NutritionStore.LOCK重读最新记录，精确匹配被展示原文字，仅删除text/manualTextEntry；图片、foodItems、营养和手工热量保留。旧回调不能删除后来修改的文字。
2. 面条图仍含托盘/衣物：上轮raw decode-only测试漏掉真实导入。Main原decode目标1440使1279×1706原图半采样到约640×853，再JPEG84保存；worker实际输入已丢细节。本轮照片解码目标提高至2048，保持EXIF归一和1600px保存上限，JPEG质量改为92；内存失败可回落较小解码，逐张释放。未改生产native、模型、许可、评分/面积/强前景守卫。
3. 底栏快切白屏：中断时显式合成当前normal层与保留的outgoing层；手工绘制每个平移子View时施加本地裁剪，底纸使用受变换约束的矩形填充并成对恢复Canvas，避免底纸擦除相邻页。首次布局前连点可转移同一有效快照。preDraw同时检查转场实例和listener所有权，旧帧回调不能操纵新一代。捕获失败直接显示新页，清理归一当前子页位置/透明度并释放旧监听器。
4. 美食栏更柔和：仅美食栏进入/返回采用360ms/16dp及(.32,.72,0,1)曲线，其余下级页仍220ms/24dp，主页面/餐纸弹簧参数不变。
5. 连续滑两日期后卡住：新的DOWN按已可见日期重心化并保留屏幕位置；补齐pager自己消费触摸时MOVE/UP/CANCEL，释放pointer/tracker并恢复被抓停spring；UP计入最后有效坐标。日历点击临时日期集合拖过最外端后衔接自然邻日，明确点按/长按归属不被UP最近页选择覆盖。取消及未来边界保留原语义。

## 抠图复现与设备证据

旧导入JPEG真实桌面MNN聚焦最大评分0.7873，被0.85守卫拒绝；完整解码后1199×1600/JPEG92候选0.9030。备用核心框虽能过分数，仍带托盘，未接生产；未用放宽门槛冒充修好。数字是候选评分，不是人工分割准确率。

原附件temp路径失效后，从本地旧QA包恢复同一397217字节夹具至`build/beta24-cutout-inputs/original-qa-fixture.jpg`，SHA256 `3353bf454497991e7132689c2c32ed17ea6b19416d7a4919ad77613a41479fe1`，未从远端取得图片。

`import-cutout-v24`已实际通过：受控URI→Main导入解码→1199×1600 JPEG92私有源→持久草稿/JobService worker→真实JNI聚焦。六个食物/碗沿/勺柄检查点保留，托盘/衣物/桌面三个点透明；日记未提交、0HTTP。最终重跑ARM桥接模拟器11651ms，不是品牌机速度或通用准确率。证据`build/beta24-final-import-cutout.log`、`build/beta24-ui-captures/import-cutout-final.png`。

旧640像素源的细节已经丢失；本轮不自动覆盖旧图或营养。旧记录要适用新导入质量，应重新选择原图，重抠旧低质源不能恢复丢失细节。详细桌面对照、排除误标探索记录见`build/beta24-cutout-findings.md`、`beta24-cutout-half-repro/`、`beta24-cutout-java-floor-check/`。

## 可评估组件（官方来源核对）

- [SAM 2/2.1](https://github.com/facebookresearch/sam2)：官方代码及权重Apache-2.0，可评估提示式分割；尚未证明在本项目餐图、Android资源限制下优于当前组合。
- [BiRefNet-lite](https://huggingface.co/ZhengPeng7/BiRefNet_lite)：官方MIT自动前景分割候选；本项目此前已试更大BiRefNet，仍需餐图对照，不能凭模型名认定碗/托盘边界更准。
- [ML Kit Subject Segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android)：当前Beta，模型需经Google Play services动态下载，因此不作为本项目唯一默认离线能力。

本轮未下载/打包新模型，不使用公共付费Key、不自动将照片送云端。优先修复已复现的输入损失；未来更换模型须单独比较质量、资源和许可。

## 审查与验收

- 独立只读审查未发现文字、日期连续滑动、导入质量改动的新阻断问题。已修正内外文字节点重复焦点、长按日期归属、UP最近页选择覆盖及Canvas恢复边界；删除操作核对原文并重读最新字段。
- 最终JVM 2285项通过：`build/beta24-jvm-delivery.log`；18项C++主体保护策略通过：`build/beta24-native-policy.log`。本轮未变更生产native/权重。
- 导航QA已加强：0ms走真实底栏OnClick，16/40/100ms走应用窗口指针；截图检查六列分布及横向跨度。独立硬件层在15%/50%/85%进度连续五轮反向中断，15个分布点检查全宽覆盖；包括布局前连切、过期preDraw及软件截图失败回退。专项42指针事件通过：`build/beta24-final-navigation.log`。
- 完整revision-v24三轮均通过，每轮375个应用窗口指针事件、0HTTP：日间`build/beta24-final-ui-day.log`；夜间360×800dp＋150%字体`build/beta24-final-ui-night150.log`；关闭三项系统动画`build/beta24-final-ui-reduced.log`。关闭动画时只检查最终状态，不伪造中间动画帧结果。
- 覆盖日期连续3/4日、30/80/160ms再次抓取、反向/取消、远日期长按归属、未来边界恢复、文字居中/删除取消/陈旧回调/同期媒体营养合并，以及美食栏360ms/16dp进入返回。
- 最终QA重跑revision-v23通过60事件、0HTTP：`build/beta24-final-routes.log`；覆盖真实周/月日期点击、平级短甩/反向/取消/书架边界、七条下级页路线、过渡中返回、原位输入及最新字段合并。
- 实际导入→真实JNI重跑通过，见上一节；完整餐图回归通过：`build/beta24-final-full-regression.log`，包括独立草稿、重建、SSE营养合同、确认/取消、手动优先、备份/回收恢复及独立周报。营养使用QA传输，不是真实收费服务商验收；备份回归也不证明API配置已剥离。
- 首轮真实触摸录屏发现大片空白，核对为早于关键局部裁剪补丁的旧包。最终普通包已包含局部裁剪与Canvas成对恢复，重新覆盖安装并录屏，七次真实底栏快点、反向及美食栏100ms中断返回未再复现大片空白。视频为`build/beta24-ui-captures/delivery-transitions.mp4`，对应`delivery-transitions-sheet.jpg`。
- 最终普通包八次真实左右快滑可继续推进、反向并回到今日，未出现两次后卡死：`build/beta24-ui-captures/delivery-dates.mp4`及`delivery-dates-sheet.jpg`。设置中的个性化/其他设置及100ms中途返回也已录屏检查：`delivery-settings.mp4`及`delivery-settings-sheet.jpg`。上述缩略表是抽帧检查，不能当作逐帧无掉帧证明。
- 独立设备EatRecordOfflineV22QA，serial emulator-5586，Android36.1/x86_64＋ARM64桥接；先用SwiftShader复现，再重启用Intel Arc host GPU检查。QA只在该独立设备运行；QA夹具交互与真实导入推理证据分开。最终普通包中无本应用instrumentation，末次crash buffer为空。
- 流畅度观测：最终普通包、host GPU、不录屏时五次底栏切换采样158帧，13帧错过deadline（8.23%），中位32ms、90分位53ms：`build/beta24-delivery-gfx.log`。仍有模拟器掉帧，不能据此承诺品牌机60fps；无空白、状态连续和恒定帧率是不同验收项。

## 最终普通安装包

- 文件：`dist/EatRecord-0.6.0-beta.24-debug.apk`，123093110字节（117.391MiB）。
- SHA256：`ebcfde78dced9cd9d3ebc31e3d1acde5f688934060ef5eba0bb7f8b9665708eb`。
- 版本：beta.24/code172，minSDK26、target37；v2/v3签名有效，证书SHA256：`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与既有安装一致。
- 普通NoUpload构建、四模型/默认字体/12固定运行库摘要、双ARM/16库依赖闭包、许可、60条参考、七文件下载清单、包体预算及QA隔离均通过。无debuggable、runner或用户夹具；未把QA包重命名交付。
- 同证书覆盖安装成功，冷启动1928ms，仅为模拟器样本。构建、验包、版本、签名、启动及末次crash证据依次为`build/beta24-delivery-build.log`、`beta24-delivery-verification.json`、`beta24-delivery-badging.log`、`beta24-delivery-signature.log`、`beta24-delivery-start.log`、`beta24-delivery-crash.log`。

## 剩余边界

本轮没有品牌真机、人工分割/称重基准、真实收费API或完整Android离线权重下载/续传验收；离线营养仍60条参考。少量抠图暗边及未覆盖图片误选仍可能出现。旧低清来源须重选原图。便携备份API配置剥离缺口未在本轮修改，不能宣称本地导出不含配置。工作区仍大量未提交/未跟踪，普通包本地交付不等于发布或源码已保存至远端。

测试设置已恢复为日间、字体1.0、物理density420无覆盖及三项动画1；本轮未改网络，恢复证据为`build/beta24-delivery-device-restored.json`。独立测试模拟器已在交付前关闭并保留数据，不操作日常设备。不提交、打标签、推送或上传；继续保持Beta，后续序号与正式发布均按用户指令。

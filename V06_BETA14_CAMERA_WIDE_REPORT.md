# beta.14 相机最小倍率与完整取景

日期：2026-10-19。版本：0.6.0-beta.14，versionCode 162。未提交、推送或公开上传。

## 修订范围

- 旧实现仅选择第一个可用后摄，使用模板默认缩放；JPEG只按像素选，预览中心填充在比例不同时会额外裁边。这些是源码层可复现条件，不能仅凭截图断言用户手机的全部原因。
- 现在比较系统公开可拍JPEG的彩色后摄，以传感器尺寸和焦距估计视野。logical支持缩小时，用物理sensor/focal配对估计，避免将最短物理焦距再乘逻辑0.5而双算；排除黑白、纯深度辅助传感器。只打开公开cameraIdList中的ID，不绕过厂商限制打开隐藏物理ID。
- API30及以上，查询请求键和合法zoomRatioRange，下限可能是0.5/0.6/1或其他值，不硬编码“0.5倍”。预览、对焦、静态JPEG和拍后恢复执行同一最小倍率和完整active-array crop；非logical可调焦距镜头使用最短可用焦距。
- 旧API/无zoomRatio支持使用公开镜头完整sensor crop。关闭受支持的电子防抖和系统rotate-and-crop自动裁切，光学防抖不变。保留既有JPEG方向/EXIF、生命周期与迟到JPEG保护。
- 照片优先4:3且保留合理分辨率：候选不少于1MP，低分辨率设备用预算内最高档；正常8MP预算，无预算内档时保留原来的最小可用尺寸回退。没有降低JPEG质量或把取景截图当照片。
- 预览优先匹配JPEG比例，不匹配时fit-center完整显示而非再次放大裁边。少数比例可能留边。相机框仍竖向宽3高4，单一相册按钮、系统栏安全区及草稿确认均不变。

依据：[Android缩放请求](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_ZOOM_RATIO)、[缩放范围](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_ZOOM_RATIO_RANGE)、[物理相机访问](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#getPhysicalCameraIds())、[系统旋转裁切](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SCALER_ROTATE_AND_CROP)。

## 验证

- scripts/test-food-analysis.ps1原有173项JVM检查通过；本轮未修改营养识别、模型或存储合同。
- 新增camera-wide-v14，独立EatRecordV06QA API36.1通过：7种合法/缺失/无效倍率范围；按传感器区分广角/主摄/微距/长焦；4:3及分辨率下限/尺寸回退；48组sensor/display/aspect组合的完整适配边界及JPEG方向。
- 同场景验证真实Camera2预览请求及在camera worker上构造的PREVIEW/STILL模板，支持时均为最小倍率、完整crop、电子防抖OFF和rotate NONE。暂停重开再次检查、取消后硬件和线程释放，日记/草稿/API调用不变。截图build/beta14-qa/beta14-camera-minimum-live.png及-resumed.png。
- 第一轮测试被模拟器System UI isn't responding系统弹窗遮挡，无法点击午餐；读取UI树确认后选择Wait，使用最终构建完整重跑通过，不将失败轮计为通过。
- camera-wide-v14在夜间150%系统字体再次通过，截图已人工查看，标题/快门/单一相册安全且取景仍宽3高4；保存在beta14-camera-minimum-live-night-large.png。
- capture-live通过实际Camera2取景、静态JPEG、暂停/重开无迟到交付、临时文件清理、真实本地抠图草稿取消，以及相册明确确认且旧早餐不变；More操作映射也回归通过。相册选择结果由QA夹具提供，非用户相册。
- 撤销CAMERA授权后，夜间150%字体capture-gallery通过：无相机权限仍完成相册→真实本地抠图→明确确认，不会获得相机权限。测试日志均在build/beta14-qa。

## 普通安装包

- dist/EatRecord-0.6.0-beta.14-debug.apk：121,027,472字节，115.4208MiB。
- SHA-256：7104c729a08977cd59a9b9d7e6001cbed072a0c56a197a0dcd3ae9033a160229。
- 完整build.ps1 -NoUpload成功，版本162、minSdk26、targetSdk37，v2/v3签名通过，普通包无DEBUGGABLE。
- verify-food-apk.py通过：四模型SHA-256、字体字节和许可证、两个ARM ABI、QA runner/夹具/源模型隔离及300MiB预算；权重与原生推理未改。QA APK不用于交付。
- 普通包覆盖安装到独立模拟器并冷启动成功：Status ok、LaunchState COLD、进程存活，AndroidRuntime无错误且无instrumentation注册。恢复系统字体/密度/夜间/动画缩放并关闭本轮EatRecordV06QA；git diff --check通过。未安装用户手机。

## 验收边界

模拟器仅验证本程序的选择/请求/取景和流程，不能证明用户手机的超广角开放、真实光学视野或画质。没有连接用户品牌手机，API26—29实机仍未验证。某些厂商只向系统相机开放超广角，本程序只能用第三方接口公开的最小倍率，不能保证任何手机都获得0.5倍，也不能把缩小倍率等同于提升清晰度。

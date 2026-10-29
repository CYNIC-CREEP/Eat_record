# beta.23：餐图主体、连续导航与餐次原位输入

日期：2026-10-24（Asia/Shanghai）。本地Beta开发；不commit/push/上传，不操作用户日常手机。用户要求持续递增0.6.0-beta.N，直到其明确批准正式版。

## 本轮范围

- 用户面条原图与当前结果截图显示托盘、下方衣物混入贴纸。本轮修复抠图，不把营养识别模型或称重精度混为同一问题。
- 点击周/月日期复用餐纸弹簧与逐帧日期联动，支持远日期、快速改选、反向、未来边界和关闭动画。
- 今日／迹／我保持边缘相连横移；短甩按释放惯性投影停靠，保留原35%慢拖门槛；旧式书架在滚动边界可交接主导航。
- 营养师、美食栏、设置及共用下级页面使用220ms、24dp短位移；进入与返回方向一致。营养师历史聊天各级进入/返回也接入共用转场。
- 餐次记录弹窗的空白区域改为原位EditText，提示仅作hint；完成、返回或添加图片/打开已有餐图前保存。只修改最新餐次中的文字字段，保留媒体、worker营养、手工热量；清空只删文字，空白打开/关闭不创建日记。

## 抠图原因与最终方案

IS-Net把托盘/衣物部分算为显著主体；旧EdgeTAM只允许扩大并与旧mask取并集，无法删除这类误选。新增保守聚焦分支，仅在普通增强未通过、高置信主体集中且旧前景明显扩散时触发；候选评分至少0.85、限制面积、保护至少99.5%强前景，并保留已有显著性支持且与主体相连的明亮中性细节。没有更换权重、伪造盘形或直接降低旧扩张门槛。

首次桌面A/B使用PIL抗混叠，Android原输入仅四点双线性跳采；真实JNI未触发聚焦，不能把该桌面结果当作设备修复。进一步修正IS-Net下采样为可测的分离式三角抗混叠，固定22位权重与RGB8分轮取整，保持归一化合同与源图不变。

低内存U²-NetP对同一采样变更存在蓝盘退化，因此最终**仅IS-Net完整模式使用新下采样，U²-NetP保持原路径**。完整模式仍受原内存守卫保护；本轮不承诺低内存模式也解决托盘问题。

最终桌面四图真实固定FP16 MNN＋同一生产C++策略：新图前景94998→53611，聚焦候选0.87164；旧白盘、蓝盘、香蕉不触发聚焦，旧强前景均保留100%。旧前景保留比例分别99.9838%、99.8788%、99.9891%，这只是新旧输出一致性指标，不是人工标注准确率。蓝盘盘沿和勺有可见补全，白盘仍有原有餐巾背景，碗底仍可见少量暗边。

证据：`build/beta23-cutout-native-input-regression/report.json`、`comparison-metrics.json`与四张comparison.jpg；低内存负例在`build/beta23-cutout-small-regression/`。早期`beta23-cutout-regression`是采样不忠实的探索记录，不用于最终Android结论。

实际Android JNI新图：聚焦分支启用；六个食物/碗沿/勺柄alpha定位点保留，托盘/衣物/桌面点透明，0HTTP。ARM桥接模拟器本次12967ms，不是品牌手机耗时或通用准确率。输出`build/beta23-ui-captures/noodle-cutout-jni.png`，日志`build/beta23-cutout-jni-final.log`。

## 转场与审查修正

常规PageCrossfade使用Android startViewTransition/endViewTransition保留旧View/display list，首个MOVE不再分配并软件绘制整屏bitmap。仅连续导航打断时捕获一张当前合成帧，释放上一组旧页，避免累积；结束、停止或尺寸变化清理。平级页仍逐帧1:1，使用独立0.3s临界弹簧；日卡原0.4s保持。

程序化日期切换只保留当前可见纸和目标纸，不遍历中间日期或创建空日记。快速改选保留位置/速度，远日期中途点图先固定记录归属，仍可见的旧纸保持到离屏后才归一为新邻页。

独立审查发现并修正：抓取回滚后只轻点原先会强制提交；旧日期View在disappearing状态仍attached，可能继续写日期；远日期点图立即换邻页造成背景跳变。有效层级检查同时处理indexOfChild=-1与祖先parent=null，旧页停止回调。

## 验收状态

- 最终2148项JVM检查通过（`build/beta23-jvm-delivery.log`），18项C++采样/策略检查通过；`git diff --check`通过。未重跑旧报告额外490项官方CSV外部复核。
- 独立设备：EatRecordOfflineV22QA，序列号emulator-5586，Android 36.1/x86_64＋ARM64桥接。QA会改写测试数据，绝不交付QA包。
- 最终revision-v23日间、360×800dp夜间150%字体、关闭动画三轮均通过，每轮60个应用窗口指针事件、0HTTP。日志：`build/beta23-ui-day-accepted.log`、`beta23-ui-night150.log`、`beta23-ui-reduced.log`。覆盖真实周/月click回调、远日/快反向/点图可见纸不替换、最终年月、旧页状态归属/释放、主导航取消/短甩/书架边界、七类页面中断返回、原弹窗输入与最新字段合并。短甩专用用例使用120ms排队MotionEvent时间戳模拟硬件事件，不把宿主/桥接等待时间算作手指速度；不是硬件输入时延基准。
- 既有40事件连续日期/授权/OS停止保护通过（`build/beta23-linked-motion-regression.log`）。首轮增强QA真实捕获parent=null遗漏；另一轮被Pixel Launcher ANR遮挡，截图/XML在`build/beta23-ui-captures/system-focus.*`，未计通过。确认是外部桌面弹窗并点击Close app恢复焦点后复测通过。
- 旧白盘/蓝盘真实JNI通过（`build/beta23-cutout-quality.log`）：白盘/食物检查点保留、背景角透明；此次ARM桥接模拟器9518/7907ms及进程RSS只作本机诊断，不是品牌机速度/峰值预测。
- 完整既有餐图回归通过（`build/beta23-full-regression.log`）：真实JNI、无授权不请求、流式临时名称/完整营养、草稿重建/幂等确认、手动热量、编辑、备份/回收、在途取消和独立周报。营养是明确QA响应，不是实际服务商准确率；不能据该用例宣称本地备份配置剥离已修好。
- 普通NoUpload构建及`verify-food-apk.py`通过；四模型/字体/12固定运行库摘要、双ARM ABI/16原生库依赖闭包、许可、60条参考、七文件下载清单、300MiB预算与QA隔离通过。实际min26/target37，code171/beta.23，v2/v3签名有效，无debuggable/instrumentation；同证书覆盖安装COLD启动Status ok/2133ms，crash buffer为空。
- 测试结束核对字体1.0、物理密度420无override、系统日间、三项动画1，未更改网络；关闭本轮启动的独立模拟器，不删除数据、模型或用户文件。

## 交付与剩余边界

普通包：`dist/EatRecord-0.6.0-beta.23-debug.apk`，123,089,014字节，117.3868313MiB；SHA256 `25428f585dfde16b88a943fe0064cdaedfed911906c7e7d581e60b495357085c`。证书SHA256 `7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与既有证书相同。包后缀为构建惯例，交付的是无QA入口的普通包。元数据：`build/beta23-apk-verification.json`、`beta23-badging.log`、`beta23-signature.log`、`beta23-ordinary-start.log`。

尚未完成品牌机速度/流畅度/发热、人工分割与称重基准、真实收费视觉API验收、完整Android权重下载/续传。离线营养仍60条实验性参考；本地便携备份配置剥离缺口本轮只纠正文档，未修改备份实现。用户原图仅作本地QA夹具，普通APK不包含照片或QA入口。

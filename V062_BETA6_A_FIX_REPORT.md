# 0.6.2-beta.6 方案A与暗色灰闪修复

2026-11-07。按用户最新选择，餐次管理试点默认恢复方案A。普通包已安装并完成ADB真机进退回归。

## 最终行为

- 默认FULL_A，保留480ms、Bezier(.25,0,.25,1)、四边smooth(e/.90)、原圆角及来源定位。
- 原导航owner提交一次业务render。新真实View先在完整旧视觉覆盖下正常绘制一帧，随后一次有界帧回调捕获Bitmap，再接入原动画时钟。
- 准备态继续屏蔽输入、焦点和无障碍。旧回调与动画回调检查Session身份，清理会撤销回调、恢复真实View并释放纹理。
- 快速反向保留原page/bounds/source元数据。必要时短期保留当前合成帧，按实际纹理字节数检查32MiB，准备结束即释放，超限沿原路径安全归位。
- A的纸面来自真实根背景。过渡层以原主题像素承托，后段逐渐让出根纸面，固定paperPixel与可变卡片fillPixel分开。

本轮范围仍为“其他设置→餐次管理”。原B与Baseline保留为历史回退，设置其他入口继续原转场。

## 灰闪的具体原因和修复

原过渡层会重新回放Paper。本机实录中整页底色由约27升至59，结束再降回。A已同时跳过Paper缓存创建和回放，避免暗色纸面重复处理。

另一个问题发生在首次正常绘制之前。已显示页面的软件快照卡片像素为ff2b2622，新建但尚未正常绘制页面的快照却为ff44413f。临时buildLayer和仅恢复alpha的探针均未改善。改为真实View正常绘制后再捕获，两次进入、两次返回的探针均为ff2b2622，释放时的卡片色阶跳变消除。

准备态最初还复用了同一张可变1×1位图绘制纸面与卡片。硬件延迟读取时，全屏底取到了后写入的卡片颜色，产生一帧约40–41的灰峰。最终paperPixel只在构造时设色，fillPixel仅绘卡片，分别释放。QA增加纹理身份、像素、GenerationId不变及回收断言。

返修中直接持有子View RenderNode的临时路径曾造成内容丢失，用户已提供截图。该路径已撤回。最终使用独立Bitmap和原会话所有权，源码无新增独立Renderer读回或跨业务View生命周期的子节点持有。曾短暂切回旧基线恢复显示，最终默认与已安装包均为A。

## 最终包与构建

[EatRecord-0.6.2-beta.6-debug.apk](dist/EatRecord-0.6.2-beta.6-debug.apk)，code199，121216113 bytes，min26/target37。

SHA256为`cc017bb337f6a6273f3f8cd9089e4ca733b93c3f35aaad47dde93813668aca89`。手机base.apk读回摘要与交付包相同。

- `build.ps1 -NoUpload`成功，日志为[build-final.log](build/v062b6-fix/build-final.log)。中断过的签名构建已重新完整执行。
- 完整JVM通过，含52937项A/B与默认A检查、61678项容器、6219项普通设置、365项底栏和1160项根页检查，其余业务兼容检查通过。
- 验包通过，4个模型、双ARM共8库、6份固定运行库摘要、字体/许可及QA隔离有效。
- 普通包无debuggable，v2/v3签名有效，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，沿用现有安装证书并保留数据覆盖安装。
- UpgradeSmoke增加A禁止重放Paper、捕获失败恢复、准备态完整覆盖、单次render、旧回调隔离、纸面像素独立及回收检查。QA仅编译，日常手机只使用普通包。

## 真机结果

魅族21，461QYGFL22429，Android16/API36，1080×2340。明暗两段各12秒，顺序均为进入、顶栏返回、再次进入、Back键返回。另单独检查真实右缘系统返回。

| 项目 | 实际结果 |
| --- | --- |
| 暗色纸面左缘全段 | RGB约26–27，最大相邻变化1级 |
| 暗色标题旁空白纸面 | RGB约27–30.5，最大相邻变化3.5级 |
| 原准备态整页40–41峰 | 最终录像已消除 |
| 暗色四段结束卡片 | 约40/37/33，释放相邻变化0–2级，原25–29级跳变消除 |
| 日间纸面 | 左缘约247–249，标题空白约246–250，整体交接连贯 |
| 立即、100ms、250ms返回 | 通过 |
| 快速重复点击/Back组合 | 三组通过，未见重复跳页 |
| 非零父列表滚动 | 来源文字bounds前后均[254,610][940,674] |
| 真实右缘系统返回 | 通过，一次回到其他设置 |
| 动画倍率0 | 安全归位，随后恢复三项1.0 |
| 原普通震动页与返回 | 通过 |
| 根Tab点击、当前Tab重复点击及横向跟手 | 通过 |
| 最终设备状态 | 日间、我页、三项动画倍率1.0，夜宵仍为原“关” |

右缘部分ROI会短暂达到约41，对应容器边缘及填色经过，左缘和标题空白没有同步抬升。该局部几何变化与原整页灰峰分别量化。

人工检查进入与两种返回的六阶段解码帧，来源空间关系、标题正文交接和终态均正常。证据为[final-visual-summary.json](build/v062b6-fix/final-review/final-visual-summary.json)、[device-final.json](build/v062b6-fix/device-final.json)。

## 录像与关键帧

- [最终暗色原速录像](build/v062b6-fix/A-final-dark-app.mp4)
- [最终日间原速录像](build/v062b6-fix/A-final-day-app.mp4)
- [暗色进入六帧](build/v062b6-fix/final-review/final-dark-push-keyframes.jpg)
- [暗色顶栏返回六帧](build/v062b6-fix/final-review/final-dark-top-pop-keyframes.jpg)
- [暗色Back返回六帧](build/v062b6-fix/final-review/final-dark-system-pop-keyframes.jpg)
- [日间进入六帧](build/v062b6-fix/final-review/final-day-push-keyframes.jpg)
- [暗色准备态相邻帧](build/v062b6-fix/final-review/final-dark-prep-001-002-003.jpg)

六阶段为约0%、10%、25%、50%、75%、100%，由录屏和曲线辅助定位，属于近似进度。录像只裁顶部100px系统栏，保留App标题和底栏。暗色260帧、日间262帧，导出前后逐帧PTS差0，原速保持。摘要见[media-final.json](build/v062b6-fix/media-final.json)。原始系统栏媒体分析完成后清理，保留App画面、解码帧及摘要。

## 性能和剩余限制

独立于录屏的日间暖态四组往返采样504帧，deadline jank为9次、1.79%，p50/p90/p95/p99为7/12/13/17ms，Missed Vsync为0，slow bitmap uploads为1。该采样仍有慢帧，单轮结果不用于证明所有设备或情境的性能。日志为[gfx-final.txt](build/v062b6-fix/gfx-final.txt)。

暗色录像最初两帧的父列表卡片已约63灰，早于本次容器准备画面，随后为正常暖暗色。这是进入“其他设置”后已有的源页面缓存色阶边界，保留为后续检查项。餐次试点的整页灰峰和结束释放跳变已分别验证消除。

API26–28、大字体、RTL、分屏、TalkBack及完整内存峰值仍待专项实测。A继续呈现完整展开的中段留白。普通包检查没有写入餐次/日记/账户测试夹具，首次版本公告只沿原逻辑更新已读元数据。

## 文件、回退与交接

- SettingsContainerTransform.java调整准备时序、状态清理、捕获外观恢复与纸面/卡片像素分工。
- UiTokens.java默认选择FULL_A，AndroidManifest.xml与ReleaseNotes.java更新beta.6/code199。
- 两份容器测试补默认A及Android准备态/纸面回归，普通QA仅编译。
- CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md更新当前选择和验证，beta.5报告标记为被后续实测取代。

`SETTINGS_CONTAINER_MOTION_ENABLED=false`沿原普通设置转场回退。系统动画0直接显示已接受页面，旧临时selector继续支持历史基线。根页与底栏开关继续由原组件拥有。

本轮开始前42份文件及beta.5 APK保存在[before](build/v062b6-fix/before)。MainActivity、PageCrossfade、SettingsContainerMath、根页/底栏及正式更新清单等15份文件摘要与开始前相同，见[protection-final.json](build/v062b6-fix/protection-final.json)。本轮修改补丁为[this-round.patch](build/v062b6-fix/this-round.patch)，仅检查反向适用性。最终源文件与已安装字节、媒体和Git检查见[final-audit.json](build/v062b6-fix/final-audit.json)。

当前main与本地origin/main仍为8cc26c5，server/dist正式清单为0.6.1/code193。交付为本地Beta，保留全部已有未提交修改。

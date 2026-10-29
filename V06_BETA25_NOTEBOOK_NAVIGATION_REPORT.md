# beta.25：底栏流畅度与记录本翻开

日期：2026-10-25（构建机本地日期）。0.6.0-beta.25 / versionCode173，仅本地开发、独立设备测试和普通APK交付；保持Beta，未提交、打标签、推送、上传或正式发布。

## 本轮修改

1. 底栏今日／迹／我：PageCrossfade在首个移动帧前为旧页、新页准备临时硬件层，避免密集文字、餐纸和媒体在平移中反复绘制。仅平级切换使用，按两个全屏纹理32MiB限制；软件渲染、显式软件层和超大窗口回落原路径。完成／取消／中断释放自己创建的层。保留beta.24完整覆盖、当前合成帧、旧preDraw归属和捕获失败直接归位的保护，未用延长时长掩盖掉帧。今日图片原本已异步解码，本轮没有加入长期页面缓存。
2. 翻页上下短边：NotebookTurnRenderer的纸背路径与反射圆角原来使用lift..h-lift，上下均被收进；现在统一0..h。横向弯曲、圆角、主题和正文不压扁规则保持，未拉长实际纸页或改变日记排版。
3. 开本：原860ms平面旋转改为440ms、复用(.32,.72,0,1)曲线，从所点封面边界展开至实际已布局纸张边界，沿左书脊作透视掀开。这个时长用于可中断的封面展开与掀开两个动作，不套到频繁底栏切换。实际纸页截图替代简化预览，动画无逐帧ensureDay或写日记。
4. 审查发现的封面连续性问题一并修复：不按大纸尺寸重新设计封面；按原View边界绘制所点封面，保留食物图案、书脊和圆角。起点逐层映射carousel／按压矩阵和滚动。触摸、返回、切底栏、窗口隐藏、尺寸变化及移除可结束动画，恢复仅由本轮隐藏的纸张，回收三张临时截图／监听器／animator；系统动画关闭时直接进入真实页。

生产修改限定MainActivity的开本区域、NotebookTurnRenderer、PageCrossfade、UiTokens开本时长与版本说明；无数据格式／识图模型／本地备份／服务端逻辑变更。

## 审查与构建

- 并行只读审查最初阻断了按目标尺寸重画封面造成的图案缩小，修复后静态审查Approve；详见build/beta25-review.md。该结论不代替设备流畅度观测。
- JVM 2285项通过：build/beta25-jvm.log。本轮未改native或模型，不把上轮C++／真实JNI样本结果写作本轮重测。
- git diff --check通过；QA与普通NoUpload完整构建通过。Java8过时API／JDK native-access提示为工具链警告，无编译错误。
- 构建日志build/beta25-qa-final-build.log、build/beta25-release-build.log。

## 独立Android验收

唯一使用设备emulator-5586 / EatRecordOfflineV22QA，Android36.1 x86_64＋ARM64 bridge、host GPU、1080×2400，未安装／清空用户日常设备。

| 场景 | 设置与实际结果 | 证据 |
| --- | --- | --- |
| revision-v25 all | 日间、字体1、密度420；149个应用窗口指针事件，0HTTP(S)，日记不变 | beta25-final-day-qa.log |
| revision-v25 all | 夜间、150%字体、密度480（360×800dp）；149事件，0HTTP(S) | beta25-night150-qa.log |
| revision-v25 all | 三项系统动画0；131事件，0HTTP(S) | beta25-noanim-qa.log |
| 封面外观修正后books专项 | 夜间150%字体／动画0分别87／69事件，0HTTP(S)，其余导航阶段明确跳过 | beta25-final-night150-qa.log、beta25-final-noanim-qa.log |
| revision-v24 all回归 | 快切／旧callback／捕获失败／美食栏；连续3／4日期、反向取消、远日期后续滑动、未来边界；文字居中删除／取消／旧原文与最新媒体营养合并，375事件，0HTTP(S) | beta25-v24-regression.log |

revision-v25包括0／16／40／100ms底栏序列与真实硬件帧完整覆盖、连续5轮迹→今日45ms间隔、正反翻页20／50／80%六处纸背像素上下边缘、真实封面点击及早／中／完成截图、开本中切底栏／关闭及旧回调保护，并复用v20翻页取消／反向／抓停spring／多指／离开释放。未以单张正常截图代替这些测试。

最终普通包覆盖安装并冷启动成功；pm list instrumentation无本应用runner，crash buffer为空。用普通包实际adb输入录制快切、开本、正反翻页、打开中切今日／返回；抽帧检查未见整页白屏、纸背短边、遮罩遗留或返回后旧回调重入。原始视频不是帧率基准，编码会丢帧；tile末尾黑格是视频不足拼图槽位的填充，不是应用黑屏。

画面证据位于build/beta25-ui-captures/：final-book.mp4、final-nav.mp4、final-interrupt.mp4及相应sheet，qa-day-sheet.jpg、night150翻页截图。早／中时点截图受调度影响，不能仅凭文件名认为准确捕获指定毫秒。

## 相同数据的性能对照

普通beta.24与普通beta.25在同一AVD、相同四日期／餐图fixture、日间字体1／420dpi／动画1、host GPU、不录屏、暖机后，以相同迹→今日→我→迹→今日五次adb点击、480ms间隔采样：

| gfxinfo指标 | beta.24/code172 | beta.25/code173 |
| --- | --- | --- |
| 总帧数 | 132 | 152 |
| Janky frames／deadline missed | 28（21.21%） | 12（7.89%） |
| 中位帧耗时 | 40ms | 36ms |
| 90分位 | 69ms | 48ms |
| 95分位 | 97ms | 69ms |
| 99分位 | 150ms | 150ms |

证据build/beta25-comparable-beta24-gfx.log、beta25-comparable-beta25-gfx.log，comparable-content.jpg核对两包同样内容。仅隔离AVD先临时覆盖QA允许降级，再原普通beta.24，数据保留，最后覆盖回普通beta.25。未卸载／清数据。

早期beta25-baseline-gfx.log与beta25-final-gfx.log的fixture不同，只是探索观测，不作为严格改善百分比。最终采用上表同fixture结果。短样本及模拟器GPU调度有波动；99分位仍有长帧，不能承诺所有手机无卡顿／60fps。首帧重建与纹理准备仍有成本，品牌真机是后续验收重点。

## 普通安装包

- 路径：dist/EatRecord-0.6.0-beta.25-debug.apk
- 版本：0.6.0-beta.25 / 173；minSDK26、target37。
- 大小：123093110字节，117.39073753356934MiB。
- SHA256：6f29914ff4ad475ecba7e00bb183b815f9225fadbe493e7a4f2bb289a0195a13。
- 既有证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee，v2／v3签名有效。
- verify-food-apk.py通过：四模型／默认字体／12固定运行库摘要、双ARM ABI／16库依赖闭包、许可、60条营养参考及下载式7文件清单；不打包离线Qwen权重、QA runner或夹具。普通Manifest无debuggable；文件名debug后缀不表示QA包。
- 验包／签名／badging：build/beta25-apk-verification.json、beta25-signature.txt、beta25-badging.txt；普通启动／无runner／crash分别见beta25-comparable-beta25-start.txt、beta25-ordinary-instrumentation.txt、beta25-ordinary-crash.txt。

## 边界与交接

用户未批准正式版。Git仍main／e85ff8d，工作区包含多轮未提交／未跟踪0.6源码与资源，暂存区为空；server/update.json仍线上0.5.26/code148，未改远端或签名文件。

未完成品牌手机、全部Android版本及真实媒体／提醒／同步矩阵；本轮未换抠图组件，少量暗边／餐巾残留和便携备份完整profile的既有缺口仍在。完整1.29GiB Android权重下载、收费API质量、称重／人工蒙版准确率仍未验收，不据动画测试宣称通过。

测试后恢复字体1、420dpi无override、日间、三项动画1；未改变网络，关闭独立模拟器并保留数据。下一步由用户安装体验本轮三项，按新反馈继续beta.26；长期缺口单独安排，不擅自大改。

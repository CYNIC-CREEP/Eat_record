# 0.6.0-beta.27 底栏与连续翻页修复

日期：2026-10-25，版本175，持续Beta。用户最后明确暂停ADB后，本轮没有再进行设备安装、调试或测试。

## 原因与最终实现

- 启动背景偏左：onCreate时按钮宽度为0，旧finish逻辑计算负X，又让正确定位post失效。统一requestNavIndicator／placeNavIndicator，未测量不显示；一次性preDraw等按钮和背景实际尺寸就绪后定位。尺寸变化重对齐，旧请求／手势接管／销毁取消监听。
- 记录本连续停住：DOWN暂停弹簧，但beginDrag沿用旧nextPage，日期只等onEnd更新。连续触摸反复阻止结束，且UP没应用最终坐标。新增纯NotebookPageMotion，连续位置与目标分开；同向新手势追加目标，在整页端点晋级View，保留位移连续性。反向、取消、边界、无末帧MOVE共用同一模型，动画夹紧防超冲。
- 多指：onIntercept直接取消子View持有的流；吞掉后续多指事件直至UP／CANCEL，统一回收tracker／pointer、解除拦截，同时保留回退弹簧。DOWN和MOVE采样去重，避免双入口重置。
- 底栏响应：DOWN立即设置轻量按压反馈；该反馈经过preDraw后才排队重建目标页，旧排队请求有世代和离开清理。点击使用0.48秒弹簧响应，手势保留0.3秒；响应参数不等于固定动画时长。背景继续与页面共享实际进度。
- 记录本保留同一折页几何与真实纸张高度；新可见页先布局再捕获，复用已到达View。旧纹理／媒体刷新回调在换页或退出时释放，最多当前／下一页，不保存日记。日期列表是只读浏览快照。

## 验证

- scripts/test-food-analysis.ps1：3260项通过。新增975项覆盖连续四页、不同归位程度、正反、取消／边界、过时输入／动画和高速度不跨目标；其余2285项及Manifest／ReleaseNotes一致性保持。
- 完整Java源码＋Android QA runner编译通过（JDK26、Android37、--release 8；既有弃用及测试泛型警告）。
- revision-v27已补启动前置背景检查、缺少末帧MOVE、四页连续翻动／平移、真实子View持有的多指取消及退出清理。**只编译，未运行设备。**
- 静态复审找到并修复多指onIntercept缺口；后续复审未发现确定阻塞。git diff --check通过。
- 普通NoUpload最终构建、verify-food-apk.py通过，Manifest175／0.6.0-beta.27、SDK26..37、既有证书v2/v3核对通过。
- 包内四模型／默认字体／12固定运行库摘要、双ARM／16原生库依赖和许可完整；无QA runner／夹具、临时导航日志，普通Manifest未启用debuggable。
- APK：`dist/EatRecord-0.6.0-beta.27-debug.apk`，123101302字节（117.399MiB）；SHA256：`7b0f06ed25aa2db0d26cd5ccedb9f696e9800637dbf437f96a67498bb5e35f82`。构建／验包／签名证据见build/beta27-final-*.log、beta27-final-cert.txt和beta27-final-badging.txt。最终源码摘要见build/beta27-final-source-hashes.json。

最终安装后的启动显示和动画手感尚未实测；beta.26的历史QA／手机截图不能当作beta.27通过。没有提交、打标签、推送、远端上传或正式发布。

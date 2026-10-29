# 0.6.0-beta.29 主页边界与慢开封面

日期：2026-10-26，版本177，持续Beta。按用户要求不使用ADB；未提交、标签、push、上传或正式发布。

## 修改

- 今日主页向右／我主页向左：静止边界在横向slop确认并排除嵌套组件后，取消原子View按压／点击，吞到UP或CANCEL，不调用RootPager.grab／drag／release，不暂停今日日期动画。Root模型overscroll固定0，呈现层删除rubber位移；页面和底栏不再随越界拖动。
- 根页移动中仍允许抓取／反向；餐纸与日历内部历史日期手势不改变，下一次合法根手势不受阻。
- 原normal内部翻页偏好打开本子只走220ms短转场，原flip虽440ms，但缓动前置，约110ms已掀76%。现在开封面独立于内部翻页方式：系统动画开启时都显示BookOpenView，内部normal／flip与原音效门控保持。
- 开本1000ms、线性wall进度：局部grow用既有motionEaseSoft在前300ms完成，封面220–1000ms逐步掀开，书架前350ms淡出。原书脊透视、封面／纸背、末段透明、动态贴纸刷新和触摸／返回／切tab／detach／窗口／尺寸清理不变。
- 列表行没有真实封面时用日期的BookCoverDrawable，从行中心的portrait区域起步，避免把行文字拉伸成封面。系统动画关闭仍直接落定。

## 验证

- JVM4420项通过（原4390＋30个边界静止／极值／取消后恢复检查），build/beta29-final-jvm.log。
- 静态复审未发现确定阻塞；Android新增v29逐MOVE静止与开本真实时间点几何检查，完整生产源码＋QA runner编译通过，未设备运行。
- 普通NoUpload构建、verify-food-apk.py、Manifest／签名核对通过。code177，minSDK26/target37，与前版同证书v2/v3；模型／字体／12固定库摘要、双ARM／16库依赖和许可通过，普通包无QA runner／夹具、临时日志及debuggable。
- APK：`dist/EatRecord-0.6.0-beta.29-debug.apk`，123105398字节（117.402MiB）；SHA256：`e4a75e52ed8264fc780d8063a4902dc48fe9c2a212d9ffe83b9b68bc7b33da2d`。
- 证据：build/beta29-final-build.log、beta29-full-qa-javac.log、beta29-final-verify.log、beta29-final-cert.txt、beta29-final-badging.txt及beta29-final-source-hashes.json。

本轮未运行设备，不能将纯状态／编译结果说成最终手机动画流畅或可见性已验收。

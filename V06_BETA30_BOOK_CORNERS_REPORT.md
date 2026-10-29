# 0.6.0-beta.30 开本圆角与阴影

日期：2026-10-26，versionCode178，保持Beta。用户继续要求修复，仍未恢复ADB授权；本轮不操作设备，不提交、标签、push、上传或正式发布。

## 已确认的源码原因

- 封面先以18dp裁角，再由缩略图非等比放大到纸张纹理，圆角变为18dp×paper/source，常见60–100dp且Rx/Ry不同；底层纸页仍24dp。pageImage先画、coverImage透明角后叠，所以闭合封面就透出另一层纸R。
- 书脊阴影直接drawRect，未受圆角限制；纸页ScrollView内容／fading edge的软件捕获缺少统一轮廓裁切，native elevation也未显式提供相同RoundRect Outline。

## 实现

1. BookCoverDrawable新增仅开本用的完整纹理绘制，保持源图案尺寸比例，移除烘焙的透明大圆角。动画用一套动态round Path同时裁封面正／背面；Rx/Ry由实际源R映射平滑衔接纸页24dp。
2. 闭合阶段不画底层纸页；打开后paper区域clip掉实际投影front遮挡的部分。纸页和书脊阴影同时clip真实paper及动画轮廓，避免角部穿透和矩形落影。
3. live纸张使用RoundedClipFrameLayout、clipToOutline，封面／纸张Drawable明确round Outline及alpha。开本时保存并清零隐藏纸张的elevation／translationZ，所有完成／中断路径恢复原Z和alpha。
4. 初始／异步pageImage和NotebookTurnRenderer.front都在同一真实paper Path内捕获；保持同一Bitmap动态刷新，无逐帧Bitmap分配。
5. 保持约1秒分阶段开本、系统关闭动画的直接落定、normal／flip内部偏好、静止根边界及原声音门控。未改数据格式、模型或媒体文件。

## 验证

- 既有JVM4420项通过，build/beta30-final-jvm.log；这些不构成图形像素验收。
- 完整生产源码＋Android QA runner编译通过，build/beta30-full-qa-javac.log。新增revision-v30：food/checker/nightstar/clear×normal/flip；实际overlay/animator必须存在。
- 新像素oracle不复制圆角公式：隔离outgoing背景，0/100/200ms对比真实cover-only与高对比paper探针四角，同时要求cover中心不为空；550/850ms对比真实阴影与透明shader，差异不得跑出实际paper background的alpha轮廓。检查flat cover texture角部不再透明、native圆Outline、隐藏Z及完成恢复／纹理回收，all附v29时点和中断回归。
- **按用户要求仅编译，以上Android像素断言没有设备运行。isolated Canvas检查也不等同于HWUI真机结果。**
- git diff --check通过；普通NoUpload构建、verify-food-apk.py、Manifest／签名核对通过。code178、minSDK26/target37、同证书v2/v3，模型／字体／12固定库摘要、双ARM／16原生依赖及许可通过；普通包无QA runner／夹具、临时日志及debuggable。
- APK：`dist/EatRecord-0.6.0-beta.30-debug.apk`，123109494字节（117.406MiB）；SHA256：`b2f5b6571140140304c35036ffa467343c383c90d732acb6390e5624ac697578`。
- 构建／验包／签名及源摘要见build/beta30-final-build.log、beta30-final-verify.log、beta30-final-cert.txt、beta30-final-badging.txt及beta30-final-source-hashes.json。

实际四角观感尚未设备复测，交付普通包后等待用户安装反馈。

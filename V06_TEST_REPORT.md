# 0.6.0-beta.1 验证报告

日期：2026-10-18（Asia/Shanghai）。源码基线 e85ff8d + 本轮未提交修改。此报告不等于正式 0.6.0 发布验收。

## 产物

- 普通测试包：dist/EatRecord-0.6.0-beta.1-debug.apk，83.537 MiB。
- 包名 com.eatrecord.app，versionCode 149，versionName 0.6.0-beta.1，minSdk 26，targetSdk 37。
- SHA-256：61698d1ba19646887338e86cdf91b950ffa3bce469091e721eb31124f7f8e4fd。
- 仍使用现有 debug 签名。apksigner verify 通过，v2/v3 签名有效；正式生产签名未决。
- ARM64 和 ARMv7 均含 eatvision、MNN、mnncore、libc++；两份 FP16 模型哈希与 assets/models/model_manifest.json 匹配。
- 不含 SAM、ONNX、LLM、Vulkan、UpgradeSmoke runner 或 assets/qa 图片。包含 MNN、U²-Net 和 rembg 许可证。
- 初期构建生成过同版本号的 0.5.26 调试产物，已从本地原始 0.5.26 正式 APK 恢复，避免保留版本号错配的旧文件；未修改线上版本。

## 已执行

| 检查 | 结果与范围 |
| --- | --- |
| 完整 build.ps1 -NoUpload | 通过，Java 与两个 ABI 的 C++ 均完成编译、打包、对齐、签名 |
| JVM 数据合同测试 | 23 项通过：JSON/Markdown、版本、数字类型/范围、明细汇总、区间、空食物、旧记录、跳过餐次、并发合并、删除源照片防回写、备份引用、路径安全、严格 Schema |
| 模型转换对比 | 用户提供的一张炒饭照片；U²-NetP（320）蒙版 IoU 0.997229、平均 alpha 差 0.001116；U²-Net（512）IoU 0.999371、差 0.000141 |
| Android instrumentation | 独立 API 36.1 Google Play x86_64 模拟器，使用 ARM64 translation；四主页面、保存图片、去重、真实 JNI 抠图、无 API 不产生营养值、手动热量优先、备份恢复、回收站恢复食物记录、恢复取消旧队列全部通过 |
| UI 截图 | 四主页面日间/夜间和 150% 系统字体；已检查主要页面无明显文字裁切；追加主卡片 36dp、弹窗 32dp、输入/小控件 26dp 圆角后重新截图 |
| APK 内容 | 两份模型哈希正确、两个 ABI 存在，测试 runner/图片夹具、SAM/ONNX 均未进入普通包 |
| Git | git diff --check 通过；没有提交或上传。行尾转换提示属于本机 Git 配置，不是空白错误 |

模拟器测试过程修复了真实故障：JNI 创建的 Bitmap 不可变，Java 后处理 setPixels 抛错；现已复制可变蒙版。桌面对比仅证明模型格式转换的一致性，不是人工抠图准确率，也不能用桌面时延代表手机速度。

## 使用与测试隔离

- 普通包长按“我”页面底部版本号开启新界面，再在 AI 设置内授权自动识别并配置支持图片输入的 HTTPS API。
- 模拟器截图中的营养数字来自明确标注“测试数据”的合同夹具，未调用真实视觉 API。普通 APK 不含该夹具。
- -Qa APK 仅用于独立测试设备，runner 会改写该设备上的本应用记录；不得发给日常使用者或作为正式 Release。
- 截图及运行日志在 output/vision-qa 与 build/，这些生成物已被 Git 忽略。模型缓存也不进入 Git。

## 未验收，不能据此宣称达标

- 未具备 50 张内部图片、20 张人工蒙版、30 份已知重量/配料的餐食；85% 抠图可用率、35% 热量误差目标尚未测量。
- 未调用用户真实视觉 API，兼容性、费用、菜品正确率、结构化解析率的真实服务统计尚未测量。
- 无荣耀/华为与 4GB/8GB 真机，本地时延、峰值内存和实际安装后空间尚未测量。APK 体积达标不等于峰值内存达标。
- 未完成 API 26/30/33/35/37 系统矩阵、真实相机杀进程、全套通知与跨设备云同步回归。
- OpenCL 未启用，INT8 未验证；SAM 无调用遗留代码保留到新版稳定。
- 尚未默认开启 new_ui_v2，也未发布 beta 或正式 0.6.0 到 GitHub/OpenList。

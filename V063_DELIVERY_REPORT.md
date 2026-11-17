# 0.6.3 正式交付核对

2026-11-17，Asia/Shanghai。用户确认转正式版，授权GitHub与OpenList双位置同步，发布执行进度见CURRENT_TASK.md。

## 产物

- 版本0.6.3/code213，包名com.eatrecord.app。
- 普通APK为127244701 bytes（121.350 MiB）。SHA256为`705a910c88e1acc5d9ebfb813f9e8fa852e2af26e41b222455e7ec0f621aa3d3`。
- 原证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。min26／target37，双ARM、10个原生库、两个DEX，无debuggable与QA runner。
- dist/EatRecord-0.6.3.apk与构建产物EatRecord-0.6.3-debug.apk为同一字节，兼容目录扫描使用-debug文件名。

## 本轮变化与验证

- 左侧简笔上端沿斜向略延长，右侧四角星半径从6dp增至6.5dp。
- Manifest、ReleaseNotes、随包／共享教程版本及两份更新清单对应正式0.6.3。
- 构建使用-NoUpload，JVM与APK检查通过，固定四模型、默认字体、10个原生库及许可校验通过。本机详细日志位于build/publish063，公开结果以本文和最终发布记录为准。
- 最终普通包在独立EatRecordComposeV063QA模拟器（emulator-5588）通过底栏与菜单操作检查，已安装APK的完整摘要与交付包一致，崩溃缓冲为空。系统字体与动画设置保持原值。
- 本轮未连接魅族21，品牌机动画观感由用户后续安装验收。软件模拟器可能出现长帧，前序专项结果按各Beta报告记载。
- 前序Beta10的后台事务、缓存、书架、开合与像素问题验证见[V063_BETA10_REPORT.md](V063_BETA10_REPORT.md)，七项实现见[实施清单](PERFORMANCE_OPTIMIZATION_PLAN.md)。

## 发布一致性

GitHub附件、原OpenList目录和百度版本档案使用同一份APK及由发布提交git archive生成的源码ZIP。原位置和游客位置的update.json分别使用各自访问上下文路径，版本、大小、摘要和说明一致。上传、草稿启用、清单切换和完整下载摘要以最终发布记录为准。

源码、签名、本机凭据及用户数据按项目规则审查，签名与认证材料由本机安全位置读取。普通包设备检查范围与剩余验收在最终发布记录中列明。

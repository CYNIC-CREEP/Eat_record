# 0.6.1-beta.5 修整贴纸小修订

日期：2026-11-01（Asia/Shanghai）。仅本地Beta；未提交、打标签、推送或上传，正式更新清单仍0.6.0/code184。

## 结果

- 擦除／恢复改左侧宽圆角按钮，右侧两个48dp箭头逐步撤销／重做，无历史时变淡禁用。每次成功抬手为一步，重置也可撤销；双指、取消、失焦不提交，新笔画替换重做分支。
- 最近32步使用Path/Paint命令重放，较早步骤合入单张检查点，不为每步存全图；重置仍使用开窗时的初始蒙版。
- 标题眼睛默认开启，只影响本窗口。静止预览最长512px、拖动256px，单个后台任务／80ms启动间隔；同笔画允许最近完成的轮廓呈现并追最新输入，结束／取消／关闭后拒绝旧世代，关闭回收迟到结果。
- 轮廓复用FoodStickerRenderer当前四种样式及明暗配色，独立绘制纸边和线条。源图、蒙版与预览缓存分离，保存仍为原图×蒙版及原裁剪流程，不烘焙纸边。
- 画布20dp、照片16dp实际Path裁角、左右16dp留白；主题色圆角工具栏替代系统ToggleButton并去掉底横线。上下间距按提示末行及底部选项实际墨迹平衡，保留48dp触控；短窗口先保证操作区，再缩短预览。

## 最终验证

- git diff --check通过，只有LF/CRLF提示；scripts/test-food-analysis.ps1通过（先修正本机PATH中的旧javac，使用Adoptium JDK后重跑，日志build/v061b5-jvm.log）。
- 普通／QA build.ps1 -NoUpload均成功，615个生产class逐字节一致。日志build/v061b5-build.log、v061b5-qa-build.log。
- 独立EatRecordV06QA/emulator-5586的revision-v061b5：正常1080×2400/100%字号、1080×1600/150%字号两次PASS。日夜均覆盖实际擦除／恢复、逐步回退／重做、分支、重置、32步压缩、双指与取消、活跃笔画轮廓、排队worker关闭后快照释放和迟到交付隔离、圆角像素与实际文字间距、保存alpha不含纸边、取消文件字节不变、旧文字／营养／手工热量不变。另回归屏幕笔宽和旧crop外恢复；HTTP(S)=0。结果build/v061b5-device.log、v061b5-short.log。
- 授权魅族21/461QYGFL22429已覆盖安装最终普通包；已有餐图中实点眼睛、擦除、撤销／重做并检查布局，最终取消回今日。没有保存真实贴纸、修改设置或调用营养API；磁盘不变由独立QA验证，未导出日常手机私有记录。截图／XML仅在Git忽略的build/v061b5-phone，不进入报告或仓库。
- 最终普通APK通过verify-food-apk.py：4抠图模型、ARM64/ARM32共8库／6个固定运行库摘要及依赖闭包、默认字体／许可、包体、退役组件缺失和QA隔离。原证书v2/v3验签通过；教程与assets源码和server教程逐字节一致。
- 模拟器尺寸／字体及原动画／输入设置恢复后关闭，保留测试数据；手机只清理本轮已知临时UI dump和截图文件。

## 产物

- dist/EatRecord-0.6.1-beta.5-debug.apk：code189，min26/target37，121179249 bytes（115.57MiB）。
- SHA256：1f32f3c387a05bd8b6ef40a676afe115cfdd7d1f368f696ce853decc183bef1a。
- 证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。

## 文件与边界

本轮业务：src/com/eatrecord/app/CutoutEditor.java、FoodStickerRenderer.java；版本／公告：AndroidManifest.xml、ReleaseNotes.java；测试：tests/android/UpgradeSmoke.java；文档：CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md、README.md、assets/help/getting-started.md、server/新手教程.md及本报告。前轮脏修改和未跟踪文件全部保留。

未改模型、记录格式、营养合并、营养师导航或日夜揭示参数。轮廓为限流显示预览，蒙版和画笔不等待后台计算；不宣称所有手机零掉帧。未额外运行模型质量、真实营养API、其他品牌长时间性能或系统备份验收。

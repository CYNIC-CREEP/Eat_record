# 0.6.1-beta.7 日历淡化、水波与内联热量目标

完成：2026-11-02（Asia/Shanghai）。本轮与追加修订共用Beta.7/code191；未commit/tag/push/上传，正式清单仍0.6.0/code184。

## 最终改动

- 完整月历只按浏览页的月份淡化相邻月日期，当月未来日期正常显示，仍禁止选择未来。周历按当前自然年月缓存参照，不随选中日期或餐纸跨月滑动改变，修复10月31日／11月1日切换时整排明暗翻转；跨年同月份也会正确区分。
- 水波动态基线由0.9增至1.25dp，周一至周日使用固定的细微幅度、频率和相位差；静态水面不变。仍为淡色、960ms、单目标、旧日期静止，重按续接且不累积放大；隐藏／展开／失去owner／关闭动画时清理，无静止循环刷新。
- 营养概览使用自定义完成区，收紧底部留白。调整目标在同一个Dialog内展开，220ms ChangeBounds并复用UiTokens缓动；减少动画时直接布局。0／留空关闭，其他合法值300–10000；非法输入保留编辑，取消／收起／关闭不保存草稿，显式保存或键盘完成才提交。
- Main的saveDailyGoal在共享锁内读取最新PrivateProfileStore，仅合并目标字段；成功后原位更新概览，关闭弹窗后由既有根页安全刷新更新日历，避免覆盖异步设置或丢日期／滚动。
- 短窗输入区遵守Dialog父级的真实高度，去除重复扣除固定空间。保留原生ScrollView触摸滚动，无额外焦点状态或第二个弹窗。

## 最终验证

- 普通与QA NoUpload构建、全JVM、git diff --check通过（仅LF/CRLF提示）；最终619个生产class在普通／QA构建中逐字节一致。日志build/v061b7-build.log、v061b7-qa-build.log、v061b7-jvm.log。
- 独立EatRecordV06QA/emulator-5586的revision-v061b7：日间正常all、深色animator scale0 all、1080×1600／150%字号／真实IME的goal三组PASS，HTTP(S)=0。覆盖月归属与未来禁选、同周跨月0/.24/.68/1进度／反向／取消／点击／餐纸手势／Root重建的明暗不变、年度区别；七日稳定微差、单目标波动及连续输入；同窗展开、取消、无效值、边界值、0／空、IME保存、关闭迟到回调、Back、最新设置字段合并、同日期和日记／手工热量保持、底层目标刷新。
- 小窗口中先要求程序可见性请求，再以原生触摸拖动检验用户可达性，仍要求44dp以上完整按钮。最终短窗日志中，取消按钮经过一次真实拖动露出117px（密度下满足44dp），并完成全部保存路径；没有为过测试缩小点击门槛。程序请求与真实触摸分开判断，参考[Android ScrollView实现](https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/widget/ScrollView.java)。早期可见性断言与已修的固定高度扣减不计为最终PASS。
- 授权魅族21/461QYGFL22429已同证书覆盖最终普通包191。升级前检查无聚焦编辑器；营养概览原窗token在输入展开前后相同，数字键盘、取消及完成流程复核，未保存测试目标或编辑餐食／Key，未主动调用营养API。
- 真机在动画归位后对比10月31日／11月1日选中状态，未选中的周一至周五区域[61,301,739,469] RGB差异像素数为0；非本月标签也保持不变。第一次未等月历收起完全归位的截图最大色差3/255，随后等待归位重测为0，未把过渡帧当作整排翻色。结果build/v061b7-phone/shadow-pixels.json。
- 手机检查结束恢复本次检查起点日期及日历展开状态；锁屏／离开App时停止触控，经用户解锁后继续。私有PNG／XML只在被Git忽略的build/v061b7-phone，不进入报告或仓库。
- 验包通过：4个抠图模型、双ARM共8库、6个固定运行库摘要及依赖闭包、默认字体／许可、包体、退役组件缺失和QA隔离；原证书v2/v3验签。APK教程与assets及本地server教程逐字节一致。
- 模拟器原尺寸1080×2400、字体1、动画1、IME设置0恢复后关闭；AVD配置未改，只清系统／编译缓存处理安装空间不足，未清应用测试数据。手机仅清理本轮已知临时文件。

结果日志：build/v061b7-device.log、v061b7-reduced.log、v061b7-short.log；历史探查和截图均属本机忽略产物，不能代替最终三个PASS。

## 产物与位置

- dist/EatRecord-0.6.1-beta.7-debug.apk，versionCode191，min26/target37，121187441 bytes（115.57MiB）。
- SHA256：849116167ea492ed607446a13167fc5d4b003b8ce983283c6d1281b6e9afe4b9。
- 证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 周历参照：TodayWeekStrip.referenceMonth/rebuild/updateWater；完整月历淡化：HomeCalendarView.populateMonth。
- 波幅／星期细差／时长：UiTokens.WEEK_WATER_*与weekWaterAmplitude/Frequency/Phase；波形绘制和续接：TodayWeekStrip.drawWater/startWaterMotion。
- 内联输入、完成区、测量与清理：FoodUi.SummaryPanel/SummaryScroll；目标保存与延后刷新：MainActivity.foodUi Host.saveDailyGoal。

业务文件：FoodUi.java、MainActivity.java、HomeCalendarView.java、TodayWeekStrip.java、UiTokens.java；测试UpgradeSmoke.java；Manifest/ReleaseNotes、README、两份教程与四份交接文档及本报告。前轮脏改动和未跟踪文件保留。

未改模型、日记格式、餐次／贴纸编辑、API删除策略或营养师导航。QA为合成数据和UI验证，不代表真实营养准确率、服务商或所有品牌帧率验收。下一轮修改才递增Beta.8，发布仍须单独明确授权。

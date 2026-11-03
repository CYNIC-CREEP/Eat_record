# 0.6.1-beta.6 日历水位与API配置删除

完成：2026-11-02（Asia/Shanghai），包含11月1日开发中的同轮追加修订。版本190，不另加Beta号；未commit/tag/push/上传，正式清单仍0.6.0/code184。

## 最终行为

- 收起周历按dailyCalorieGoal和FoodUi.displayCalories显示各日期的独立水位，保留手动热量优先、有效营养及旧热量兼容。无目标保持旧样，0摄入不填水，达到／超过目标封顶，未来日期不填水；有目标时其他日期显示淡虚线胶囊，今日提示细实线，选中保留原实线标记。
- 水色非常淡：浅色alpha35、深色45；水面线15/20。静态波幅0.5dp、动态最多0.9dp，960ms后静止；仅目标日期晃动，旧日期不同时波动。连续点击续接相位／幅度并限制上限，不循环唤醒；展开、隐藏、失去根owner、尺寸变化及关闭系统动画时清理。
- 完整月历只保留选中提示。首行顶部描边曾跨出clip边界，当天单元格自身背景还会覆盖剩余部分；现在描边向内收、选中当天子背景透明，高亮底色日52/夜70alpha，修复残缺顶弧与叠画色块，不改变日期共享位移。
- 删除API Key增加“删除 API 配置？”确认。取消保持字段、授权和世代；确认清除Key、地址、模型、模式、检测列表、能力和名称缓存，关闭相关授权、提升世代并取消在途连接。校验原窗口快照，旧owner不能删除新配置。重开Key／地址为空、模型待选择，不再自动补默认服务商。云账户、记录和独立输出偏好保留。

## 验证

- git diff --check、全JVM脚本、普通及QA NoUpload构建通过；API删除／过期授权36项JVM检查通过。最终普通／QA的617个生产class逐字节一致。
- 独立EatRecordV06QA/emulator-5586：revision-v061b6 phase=all，日间正常动画及深色animator_duration_scale=0两次PASS。真实Main投影、0/四分之一/半满/达标/超额像素、圆角裁剪、淡色和虚线、960ms单目标波动、相邻日期水面静止、保留页和日期进度、展开／重建／owner清理、跨周恢复；历史首行及真实今日的顶部描边和子背景检查。月历顶部与向下平移的无裁剪参考逐像素比较。
- 同一隔离QA实点API二次确认取消／删除，检查私有持久字段、授权和世代，合成连接被断开，旧probe／迟到旧按钮不能复活配置，重开空白；合成云账户、输出偏好、目标及旧日记／手工热量保持。HTTP(S)=0，无真实服务商调用。
- 初次旧QA夹具是无餐次照片所有权的孤立foodItems，被正常mergeLatest过滤而失败；已改为合成PNG及合法photos/sourcePhoto关系后重跑最终版本，不改生产数据合同迎合夹具。独立模拟器安装曾空间不足，仅trim系统缓存并改流式覆盖安装，未清App测试数据。
- 授权魅族21/461QYGFL22429已覆盖最终普通包190；升级前确认没有非空聚焦编辑器。通过已有餐图日期检查淡周历、虚线、11月1日首行完整月历和日期切换录屏，结束恢复原11月2日／收起状态。未保存餐食、修改目标／设置、删除真实Key或触发营养API。手机截图、XML和录屏仅保留在被Git忽略的build/v061b6-phone，不进入报告或仓库。
- 模拟器动画恢复1、字体1、1080×2400和原IME设置0后关闭；手机只删除本轮已知临时UI dump／截图／录屏文件。
- 最终普通APK通过verify-food-apk.py：4抠图模型、双ARM/8库、6个固定运行库摘要和依赖闭包、默认字体及许可、包体、退役组件缺失和QA隔离；v2/v3原证书验签通过。APK教程与当前assets及server教程逐字节一致。

证据：build/v061b6-build.log、v061b6-qa-build.log、v061b6-jvm.log、v061b6-device.log、v061b6-reduced.log；本地原始UI证据均不入Git。

## 产物与参数

- dist/EatRecord-0.6.1-beta.6-debug.apk：versionCode190，min26/target37，121183345 bytes（115.57MiB）。
- SHA256：8bdfb459446470b31ab7091e041d4e939cd7cb53d0260e5635809c4e6dde3c3d。
- 证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 波动时长／波幅：UiTokens.WEEK_WATER_SLOSH_MS、WEEK_WATER_STATIC_DP、WEEK_WATER_SLOSH_DP；水色／水面线alpha在TodayWeekStrip.drawWater，线性时间和衰减在startWaterMotion。月历高亮alpha及描边内缩在HomeCalendarView.MonthPager.drawMarker。

## 文件与边界

业务：MainActivity.java、HomeCalendarView.java、TodayWeekStrip.java、UiTokens.java、ApiCredentialPolicy.java；测试：tests/ApiCredentialPolicyTest.java、tests/android/UpgradeSmoke.java；版本：AndroidManifest.xml、ReleaseNotes.java；README、两份教程与四份交接文档及本报告。前轮脏修改／未跟踪文件保留。

未修改模型、日记格式、餐次选择、贴纸编辑、营养师导航或日夜揭示参数。QA使用合成营养与本机图形，不代表模型准确率、真实API服务或所有品牌帧率验收；日常手机没有删除真实配置。下一轮修改才递增Beta.7，发布需用户另行明确授权。

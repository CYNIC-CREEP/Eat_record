# 0.6.3-beta.4 修复报告

2026-11-13至2026-11-14，Asia/Shanghai。versionCode206。

## 安装包

[普通安装包](dist/EatRecord-0.6.3-beta.4-debug.apk)，127203741 bytes（121.311 MiB）。SHA256为`956d4ee2fff18604b670e29fe182306142bbfa8578b7dd58262cb1e8a3c5a213`。

minSdk26／targetSdk37，双ARM、多DEX，v2／v3沿用原证书，普通包debuggable=false。详见[验包明细](build/v063b4/delivery.json)。

## 本轮修改

- 今日餐纸按实际胶囊边界预留16dp空隙。餐图从184／172dp调整为204／192dp，图片条目使用首屏剩余高度居中，第二条通过滚动查看。
- 记录本按顶部标题与可见胶囊之间的区域上下居中。
- 右侧碗叶按钮使用独立6.5%弹簧放大反馈，支持连续点击，Tab选择面的膨胀由Tab操作触发。
- 移除今日右上角周报入口，足迹中的“这一周的味道”保留。
- 新增图片、相册导入、外部图片分享及手写使用手机本地时间归类。早餐05:00–10:59、午餐11:00–15:59、晚餐16:00–22:59、夜宵23:00–次日04:59。日期沿用当前选择的日记日期，图片草稿保留入口时确定的目标。
- 添加前的餐次选择及其他设置中的餐次管理／夜宵开关已移除。历史夜宵、下午茶和备份数据继续读取。
- 仅食物详情弹窗中的餐次字样提供“午→早→夜→晚→午”循环切换，同一弹窗保持打开。保存时移动选中食物及媒体引用，并同步后台任务目标。共享原图的编辑、修边和重处理按食物ID定位。
- 食物营养、识别身份和媒体文件保留，原餐次备注与手填热量继续属于原餐次。随包和本地server教程文件同步新操作。

## 验证

- [JVM回归](build/v063b4/jvm.log)通过，新增27项时间归类和55项餐次迁移／运行中任务目标回归。
- [NoUpload构建](build/v063b4/build.log)、[APK内容](build/v063b4/apk.json)及[签名](build/v063b4/signature.log)核验通过。四个模型、默认字体、固定原生库和许可通过核验。
- [日间专项QA](build/v063b4/qa.log)通过，覆盖单餐首屏／第二餐滚动、间距、独立按钮反馈、直接添加及取消、同一弹窗四次持久餐次切换、营养与图片保持、周报和设置入口、记录本居中。
- 设备为独立Android16／API36模拟器emulator-5588，1080×2340。QA图片和营养均为合成夹具，HTTP(S)请求计数为0。
- [夜间1.5倍字体专项QA](build/v063b4/qa-night-font150.log)通过同一完整场景。初跑曾被System UI无响应弹窗遮挡，关闭系统弹窗并等待配置稳定后完整复测通过。
- [普通包快速交互](build/v063b4/navigation.log)与[录屏](build/v063b4/device/navigation/interaction.mp4)通过。普通包实际点击图片、首页标签、弹窗餐次字样与进程重启后的持久化检查通过，见[结果](build/v063b4/device/final/result.json)。
- 字体与三项动画设置已恢复，临时相机权限已撤回，普通包留在独立测试设备内，模拟器已关闭。见[设备收尾](build/v063b4/device/final-device-state.json)。
- 原签名和上一轮Beta3安装包保持，既有未修改文件的摘要已核对。见[保护核验](build/v063b4/final-audit.json)。

## 交接

真机动画观感与厂商兼容验收待完成。代码在既有main脏工作区继续修改，HEAD为2434aae。正式更新清单对应0.6.2/code202，本轮交付本地Beta。

主要源码为ComposeBottomBar、TodayPaperView、MainActivity、MealTime、FoodUi、FoodMealAssignment及FoodAnalysisJobService。前一轮底栏重构见[V063_BETA3_REPORT.md](V063_BETA3_REPORT.md)。

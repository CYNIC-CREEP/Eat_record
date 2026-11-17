# 0.6.3-beta.2 今日返回修正

2026-11-12，Asia/Shanghai。用户验收beta.1发现其他设置→今日与记录本→今日仍有停顿和快速跳转，记录本→我正常。本轮聚焦这两条回今日路径。

## 安装包

[EatRecord-0.6.3-beta.2-debug.apk](dist/EatRecord-0.6.3-beta.2-debug.apk)，code204，121224305 bytes，约115.609 MiB，普通包，min26/target37。

SHA256为`6c6ce9a28118ac1bc76c88551b4f604aaa53b659e3fd4a640898fc2c0ae5b8fb`。v2/v3签名证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与原证书一致。交付详情见[delivery.json](build/v063b2/delivery.json)。

## 原路径与修正

beta.1底栏选择先唤醒弹簧，随后同步构造目标页并准备硬件层。底栏下一帧会把这段UI阻塞当作运动时间，BottomNavMotion允许单次推进至0.25秒。回今日路径还固定重建三个根页，并在外层转场前就让今日获得日期联动所有权，增加准备和移动阶段的工作。

- BottomNavIndicator新增准备阶段的暂停与恢复，保留位置、速度和目标。AnimationFrameClock对准备后的第一帧返回零间隔，随后按真实帧间隔推进。
- PageCrossfade将缓存准备和起跑分到相邻帧，由onReady释放底栏时钟。时长仍为480ms，实例校验、取消回调、系统关闭动画和输入隔离沿用原路径。
- 干净且自然日、选中日期、尺寸及待完成日期目标匹配时，回今日复用已测量的根页层级。TodayPaperView.FoodImage已有重新附着时按新世代异步加载及迟到结果丢弃逻辑，餐图随附着重载。
- 脏数据、日期变化、目标未归位或尺寸不匹配时继续重建，并使用完整准备流程。RootPagePager在外层整页运动期间暂缓今日日期与输入所有权，结束后恢复，营养师日期刷新同样延后。

## 实际验证

- [JVM回归](build/v063b2/jvm.log)通过，新增9项帧时钟检查包含600ms准备、首帧零推进、正常后续帧、反向时间、改选时的位置/速度保留与收敛。
- [最终构建](build/v063b2/build-final.log)通过。
- [Android QA编译](build/v063b2/qa-compile.log)通过。revision-v063b2增加干净今日实例复用、脏数据重建、外层运动期间的所有权门控、结束恢复以及合成PNG餐图重新附着世代和Bitmap有效性检查。
- [APK验包](build/v063b2/apk.json)通过四模型、双ARM共8库、6份固定运行库摘要、依赖闭包、字体/许可、包体与QA隔离检查，普通Manifest无debuggable。
- git diff --check通过。开始前12份目标文件及已有Git差异保存在build/v063b2/before与before.patch，原beta.1安装包仍可用于比较。

本轮沿用用户无需ADB的验证范围，Android QA仅编译。以上是源代码、JVM时钟及构建层面的验证，实际设备的点击响应、连续可见帧和观感仍待安装确认。

## 下一步

优先复测其他设置→今日、记录本→今日，再比较记录本→我，并检查当日日历日期与餐图显示。设备确认后再决定正式版交付。当前正式更新清单继续对应0.6.2/code202，beta.2为本地交付。

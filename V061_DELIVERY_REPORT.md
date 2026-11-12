# 0.6.1 本地交付记录

日期：2026-11-03（Asia/Shanghai）。用户明确要求直接使用正式版本号、免模拟器和自动功能测试，安装魅族21人工检查；交付后用户已明确回复“没问题了，上传吧”，本版人工验收通过，授权GitHub／OpenList发布。实际发布状态见CURRENT_TASK.md。

## 本轮修改

- MainActivity.java：删除首页热量下方提示行。记录本热量表单从最新日记只读投影每餐热量，监听days_json并更新未编辑字段；输入草稿独立保留。保存仅合并实际编辑的热量／消费，收藏仅合并favorite；锁内重新读取最新记录，避免覆盖后台营养结果或复活已删除记录。关闭窗口撤销监听。
- NutritionStore.java：新增只读餐次热量投影，手填优先，否则汇总completed食物的有效caloriesKcal；未知显示空白。没有上传图片或调用API。
- FoodUi.java：新增按餐手填的动态日汇总分支manualCaloriesByMeal，未手填餐次仍实时使用识别结果；记录本日总量统一走同一函数。旧整日手填总量不自动迁移，仅在用户明确修改热量后转为按餐覆盖；改用识别热量时清除手填标记和餐次手填值。
- FavoriteStarView：已收藏填充不再大量混白或半透明，黄色主题使用UiTokens.yellow，其他主题保持色相并提升饱和度／亮度；未收藏轮廓与现有点击动画不变。
- AndroidManifest.xml／ReleaseNotes.java：0.6.1/code193，汇总Beta.1–8及本轮修改。README、内置教程与本地server教程、PROJECT_CONTEXT、DECISIONS、RELEASE_PROCESS和CURRENT_TASK同步；新增RELEASE_NOTES_0.6.1.md。交付待验收时线上update.json仍为0.6.0/code184；后续已完成0.6.1/code193发布，见V061_PUBLISH_REPORT.md。

按餐覆盖的预期行为：识别早餐300、午餐600时默认显示300／600；将午餐明确改成550并保存，当日为850；此后早餐识别更新到320，当日为870。只保存消费不会把早餐300固化为手填。清空午餐并保存则恢复最新午餐识别值。这是代码行为说明，不是本轮实测结果。

## 构建与安装

- powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload：退出0，普通包构建成功，build/v061-final-build.log。
- git diff --check：通过，只有换行提示。
- verify-food-apk.py：4个模型、arm64-v8a／armeabi-v7a共8库、6个固定运行库摘要、依赖闭包、字体／许可、QA排除及预算通过；未运行测试runner。
- aapt2：com.eatrecord.app，0.6.1/code193，min26/target37；普通Manifest不含debuggable。
- apksigner：v2/v3通过，证书SHA256为7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee，与之前安装包相同。
- dist/EatRecord-0.6.1.apk为dist/EatRecord-0.6.1-debug.apk的同字节副本，名称不改变签名／调试属性。大小121191537 bytes，SHA256 e308ab640f7a8ac718ab66526bfe1da7bee01c2bc66d2080c1c22bb9246750c7。
- adb devices确认461QYGFL22429为MEIZU_21；安装前公开UI在Launcher，未锁屏、无EditText。普通包install -r --no-streaming返回Success，MainActivity启动Status ok；原192覆盖193，不卸载或清数据。仅安装启动，不执行功能测试或编辑真实记录。临时安装检查XML清理完成。

## 人工复查与发布边界

按要求未运行模拟器、JVM或设备自动功能测试，未使用QA包，也未主动触发营养API。历史Beta报告属于历史版本证据，不代替193人工验收。

人工复查内容包括：首页提示、记录本识别／手填／清空恢复、消费独立保存和收藏颜色。用户已确认本版无问题并授权上传；发布操作沿用已安装的同一包，具体提交／标签／远端摘要见发布收尾记录。若后续改动产品源码／资源，需重建并重新交付确认。

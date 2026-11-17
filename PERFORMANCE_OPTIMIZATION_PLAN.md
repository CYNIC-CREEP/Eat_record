# 全局优化实施清单

2026-11-17，0.6.3-beta.10/code212。原七项来自2026-11-15的Beta9源码审查，当前代码均已实施，JVM与独立模拟器专项已完成。各项证据及剩余验收由[V063_BETA10_REPORT.md](V063_BETA10_REPORT.md)汇总，实际进度见CURRENT_TASK.md。

| 项目 | 代码状态 | 验证状态 |
| --- | --- | --- |
| P1 食物详情异步准备 | 已实现 | [模拟器通过](build/v063b10/qa-detail-final.log) |
| P1 分享后台生成与失败清理 | 已实现 | [JVM与模拟器通过](build/v063b10/qa-share-final.log) |
| P1 餐食文本后台保存 | 已实现 | [JVM与模拟器通过](build/v063b10/qa-saving-final.log) |
| P2 有界书架加载 | 已实现 | [1460天日夜／横向通过](build/v063b10/qa-books-night-final.log) |
| P2 日期与热量增量投影 | 已实现 | [13项JVM通过](build/v063b10/jvm.log) |
| P3 小头像缓存 | 已实现 | [模拟器通过](build/v063b10/evidence/v063b10-avatar-cache.json) |
| P3 字体缓存窄范围失效 | 已实现 | [模拟器通过](build/v063b10/evidence/v063b10-font-cache.json) |

## P1 食物详情异步准备

- FoodUi.DetailImage先显示固定图片区域，后台执行解码、贴纸轮廓及旋转。每个弹窗拥有自己的请求与结果，关闭／detach取消请求并回收迟到图片。
- 模拟器通过冷加载、阻塞解码队列时的UI回调、关闭后重开及迟到结果拒绝。旋转和描边沿用原算法，实际字体及图片观感由安装检查确认。
- 实现见[FoodUi.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/FoodUi.java)。

## P1 分享后台生成与失败清理

- UI冻结单日文案、主题、字体、排版和媒体描述，NotebookShareRenderer后台绘制，NotebookShareExport负责压缩、MediaStore写入及主线程结果交接。来源页面、日期、任务与生命周期校验接受结果，重复点击复用在途任务。
- ShareImageTransaction统一输出流关闭、编码返回值、发布及失败／取消删除。本次插入的未完成条目进入统一清理路径。
- 50项纯JVM故障／取消检查通过，模拟器通过真实1640×2000 PNG发布可读性、单任务去重、页面退出取消与本次新增条目清理。
- 实现见[NotebookShareRenderer.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/NotebookShareRenderer.java)、[NotebookShareExport.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/NotebookShareExport.java)和[ShareImageTransaction.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/ShareImageTransaction.java)。

## P1 餐食文本后台保存

- MealTextSave在进程级串行队列中取得共享锁，读取最新days_json并仅合并目标文字，保留媒体、营养和其他日期。失败保留输入及重试入口，commit(false)后的同值重试再次执行持久化写入。
- 内联完成／返回／转图片等待保存成功后继续，手写取消沿原语义处理。空白且未编辑的内联窗口保持只读，成功回执校验当前编辑窗口。
- 22项JVM检查与模拟器通过持久化锁阻塞下UI回调、最新字段合并、失败重试、完成／返回和手写取消；额外覆盖晚到提交快照、OOM及停止页面后的回执。
- 实现见[MealTextSave.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/MealTextSave.java)及[MainActivity.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/MainActivity.java)的MealTextSaveController。

## P2 有界书架加载

- BookShelfWindow保留完整逻辑滚动范围，仅创建可见网格行或横向书本及邻近缓冲区，按滚动移出旧子View。ensureDate可装载打开／返回所需的真实日期封面，保留滚动恢复、收藏入口、逻辑无障碍顺序与横向RTL映射。
- 不可见缓存页保留其当前窗口，重新可见时按实际视口更新。日期分节和字号测量沿现有界面布局处理。
- 1460天模拟器日夜夹具通过头／中／尾滚动、远日期物理打开与返回锚点，网格最多13个行／分节View、横向最多10本；RTL路径已作代码审查，设备场景以本轮报告为准。
- 实现见[BookShelfWindow.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/BookShelfWindow.java)及[MainActivity.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/MainActivity.java)的buildBooksRoot／addBookGrid。

## P2 日期与热量增量投影

- DiaryProjectionIndex按精确单日JSON片段复用未变日期投影，只解析变化日期，以TreeSet维护日期顺序并复用餐数和每日热量。数据对象、已提交raw及受控就地修改钩子触发更新，餐数可直接读取当前内存日记。
- NutritionStore.displayDayCalories由日历和FoodUi共享，沿原手工热量、旧媒体及餐次字段算法。
- 13项JVM检查通过1825日数据、不变数据零重解析、单日增量、整体替换、转义JSON及旧记录投影。
- 实现见[DiaryProjectionIndex.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/DiaryProjectionIndex.java)及[NutritionStore.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/NutritionStore.java)。

## P3 小头像缓存

- AvatarLoader复用后台缩略图队列，按文件版本和目标尺寸缓存小图并合并请求。文件事件及attach世代隔离迟到结果，缓存淘汰释放引用，显示对象继续持有Bitmap，Activity销毁解除自身订阅。DirectoryWatch共享父目录监听，字体继续接收目录替换事件。
- 模拟器通过冷读让出UI、热缓存身份、真实文件替换／删除、detach迟到拒绝、重挂恢复及显示Bitmap引用保全。
- 实现见[AvatarLoader.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/AvatarLoader.java)。

## P3 字体缓存窄范围失效

- AppTypography复用常规／粗体对象，按字体族及选中文件事件重载。普通资料更新和字号修改复用字体对象，字号比例独立更新，选中字体替换／删除、目录变化和下载完成校正缓存。
- 模拟器通过字形对象身份、字号比例、真实替换／删除与文件恢复，使用内置合法字体复制到下载文件名测试缓存。共享目录监听修复已由头像／字体同时活跃的场景验证，旧字体别名映射沿原实现。
- 实现见[AppTypography.java](D:/Users/cynic/Documents/Eat_record/src/com/eatrecord/app/AppTypography.java)。

## 本轮安装验收

- 底栏主图标采用左侧简笔与右侧四角星，按原底栏动画参数复核短按、横拖、反向和菜单退出。
- 记录本四角采用当前圆角外沿，先呈现封面反馈，再由同一世代准备开本，需复核冷首开、日夜像素、返回及中断。
- 用户要求Beta10等待下一轮安装检查。设备与JVM证据按本轮报告记录，魅族21实际流畅度由安装验收确认。

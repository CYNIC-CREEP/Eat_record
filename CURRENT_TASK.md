# 当前任务

更新：2026-11-03（Asia/Shanghai），0.6.1/code193已通过用户人工复查，正在按明确授权发布GitHub／OpenList。

## 版本与授权

- 用户明确要求本轮直接使用正式版0.6.1，不走Beta.9；Manifest与ReleaseNotes为0.6.1/code193。main/HEAD仍2b8aae9，既有脏修改／未跟踪文件完整保留。
- 已授权本地修改、构建、覆盖安装魅族21；本轮不运行模拟器或自动功能测试。功能由用户人工复查，不能把旧Beta测试结果当作193功能通过。
- 用户明确回复“没问题了，上传吧”，授权本版提交／标签／push及GitHub／OpenList发布。server/update.json与dist/update.json已准备0.6.1/code193；远端切换待完整附件校验。

## 本轮完成

- 删除今日餐纸热量下方整行说明提示，保留热量点击入口与食物详情。
- 记录本热量输入默认同步最新识别值，已手填餐次优先手填；清空保存恢复对应餐次识别。弹窗监听日记更新，未编辑字段实时更新，已编辑草稿保持。只改消费／收藏／直接保存不把AI值冻结为手填。
- 新手填采用manualCaloriesByMeal，动态汇总手填餐次＋其余最新识别餐次，首页／周历／周报／记录本共用FoodUi.displayCalories。旧整日manualCaloriesTotal兼容保留，直到明确修改热量；仅消费／收藏不改变旧总量。
- 保存锁内重读最新日记并只合并编辑字段；收藏仅写favorite，避免整份旧表单覆盖同期营养／媒体。已删日记／变化餐次不被旧表单复活。
- 收藏实心星标改用鲜明主题色，黄色主题为金黄色，其他主题保持对应色相、提升饱和度与不透明度。
- 版本、公告、README、本地教程／server教程、交接文档与RELEASE_NOTES_0.6.1.md同步。前八轮内容见既有V061_BETA*_REPORT.md。

## 交付证据与未测范围

- NoUpload普通构建成功，日志build/v061-final-build.log；git diff --check通过（仅既有换行提示）。验包与v2/v3原证书签名核对完成：4模型、双ARM8库／6固定摘要、依赖闭包、字体／许可、QA隔离和包体预算通过。
- dist/EatRecord-0.6.1.apk与-debug.apk为同字节普通包，121191537 bytes；SHA256 e308ab640f7a8ac718ab66526bfe1da7bee01c2bc66d2080c1c22bb9246750c7；min26/target37，无debuggable／QA。
- 魅族21/461QYGFL22429：安装前在Launcher，未锁屏、无输入框；从普通192同证书覆盖193，adb安装Success、MainActivity启动Status ok。未清数据／运行QA／写测试记录／改Key／人工触发营养请求；临时XML已删除。
- 用户明确免本轮模拟器、JVM及设备自动功能回归；仅完成构建、包体／签名核对和ADB安装启动。本版功能以用户本轮人工复查为验收，未补跑自动功能回归。
- 详细修改与交付记录见V061_DELIVERY_REPORT.md。

## 下一步

按RELEASE_PROCESS.md审查累计源码／文档并提交、标记v0.6.1，源码包从发布提交git archive生成；GitHub先草稿附件校验后正式发布，OpenList暂存完整回下载后启用目录并切换清单。沿用已人工验收APK，不重编译。最终证据另作收尾记录，不移动发布标签。

# 项目上下文

本文件描述稳定架构；当前版本、暂停状态和验收缺口见 `CURRENT_TASK.md`，历史过程见根目录 `V06_*REPORT.md`。不依赖旧聊天恢复上下文。

## 项目与使用场景

- 名称：一蔬一饭；Android 包名：`com.eatrecord.app`。
- 目的：本地优先的日常饮食记录，把文字、餐图与贴纸整理成可回看的吃饭记录本；用户可记录三餐/夜宵、浏览历史、查看营养估算及周报。
- 主页面为今日／迹／我。今日含可展开日历、统一餐纸、拍照/相册和手写餐食；迹展示记录本；我提供资料、个性化及其他设置。
- 已实现：图片旋转/修边/双指缩放、独立重抠图与识别信息、记录本翻页/分享、收藏/回收站、备份恢复、三餐提醒、可选云同步、餐图营养和独立营养师。
- 只保留当前餐纸界面，不再有旧版切换入口；旧文字、photos/stickers、手动热量和备份数据继续兼容。

## 技术栈

- 原生 Android Java，Activity＋动态 View/Canvas；无 Kotlin、Compose、Gradle、gradlew、settings.gradle.kts 或 app 模块。
- C++17 JNI＋固定 MNN 3.6.0。原生库支持 arm64-v8a/armeabi-v7a，仅用于本地抠图；每ABI为eatvision、MNN、mnncore和c++运行库，共8库。实验性本机营养识别已于beta.32移除。
- 根目录 `build.ps1` 直接调用 javac、aapt2、d8、CMake/NDK、zipalign、apksigner。Java 编译使用 `--release 8`；构建机需 JDK（README 基线17）、Android SDK、CMake/NDK、Python3.11及模型转换依赖。
- 最低 SDK26；target SDK由脚本选取本机最高平台，必须按实际 APK 核对，不能假定跨机器固定。
- 无 Android Studio/Gradle 独立 release variant；当前签名/产物策略见发布流程。

## 模块与目录

| 路径/模块 | 职责 |
| --- | --- |
| `MainActivity.java` | 导航、设置、记录本、备份/同步、主状态与旧数据兼容；暂不整体拆分 |
| `TodayDayPager/TodayPaperView/TodayWeekStrip/HomeCalendarView/HomeDayMotion` | 只读餐纸投影、日期及连续手势联动 |
| `RootPagePager/RootPageMotion` | 三个主页面复用、绝对位置／目标、手势／点击与底栏联动 |
| `PageCrossfade/GestureSpring/NotebookPageMotion/NotebookTurnRenderer` | 子页面转场、共享弹簧、记录本连续状态与纸张绘制 |
| `FoodCaptureActivity/MealCameraController/MealCameraProvider` | Camera2取景、最小公开倍率、JPEG与受控URI |
| `FoodDraftStore/NutritionStore/FoodAnalysisJobService` | 独立草稿、共享存储锁、后台队列与取消/世代保护 |
| `FoodCutoutEngine`＋`native/eatvision.cpp` | 离线抠图：IS-Net512、U²-NetP回退、EdgeTAM单图增强 |
| `FoodAnalysisClient`、`FoodAnalysisResult`、`FoodRecognitionPolicy`、`FoodRecognitionSetup` | 自备HTTPS视觉API、流式/结构化校验、来源授权与首用选择 |
| `ApiCredentialPolicy/FoodRecognitionRetirement/RetiredRecognitionCache` | 凭据世代、旧识别路线退役与限定私有缓存清理 |
| `FoodUi/FoodProcessingDialog/FoodWeeklyActivity` | 详情、确认预览及独立周报 |
| `UiTokens/AppTypography/AppearanceMode` | 全局主题、个性字体和显式明暗模式 |
| `MealReminderReceiver` | AlarmManager、通知渠道、启动/更新后重排提醒 |
| `res/`、`assets/` | 资源、默认小赖字体、模型清单/抠图模型、成分参考和许可证 |
| `scripts/`、`tests/` | 构建/验包/模型转换/分发脚本、JVM与隔离Android QA |
| `server/` | Python云同步API、SQLite、更新清单、教程、按需字体 |
| `build/`、`dist/` | 可再生依赖/缓存/测试输出及APK；多为Git忽略文件 |

beta.33已移除旧SAM源码、JNI声明、构建与空打包链；当前仅native/eatvision.cpp参与视觉构建。历史记录／热量字段读取不属于旧实现清理。

## 页面呈现边界

beta.28起，今日／迹／我由RootPagePager保留最多三个attached根View，RootPageMotion统一0..2连续位置和目标；beta.29根边界为硬限制，今日向右／我向左在静止时不grab、无回弹，不影响内部日期手势。根页工厂与路由重置分开；页面和底栏按钮复用，MOVE、松手及缓存根页点击不重建整页、不截图、不调用buildLayer。滑动用与美食框同一0.4秒弹簧响应，点击保留0.48秒；参数是物理响应而非固定时长。底栏背景使用同一位置插值，首次显示等实际测量，过渡宽度用scale避免每帧布局。

TodayDayPager／HomeCalendarView／TodayWeekStrip按RootPager的静止owner启停，隐藏时保留位置／目标并暂停输入和弹簧，不detach回收餐图；旧根回调须同时核对世代和实例。SharedPreferences的日记／资料变化只标脏，主页面、内部日期手势、弹窗及子页面转场完成后才重建，保留待完成日期目标及当前滚动。提醒跳今日强制更新，跨午夜标脏；后台内存压力释放根页，回前台重建。缓存增加首次准备成本，减少后续输入路径开销；不能据此宣称设备零卡顿。

PageCrossfade只负责层级子页面；旧peer API／状态链已删除，根页由RootPagePager管理。其旧View／中断合成帧只负责呈现，不拥有新页面写权。层级转场采用480ms线性时钟和独立页面ease曲线(.25,.1,.25,1)，不更改开本共用soft曲线，完整不透明纸页相邻横移，不交叉叠加文字。透明根临时补完整纸面，背景明确clip自身bounds，避免drawColor忽略Drawable边界并在HWUI遮住另一页；每帧保证父层重绘。单根保留原View，只有中断或多个根同时存在时合成一帧；布局后缓存本轮硬件图层，总预算32MiB，结束恢复layer／背景／回调／padding及位移／alpha。设置入口、返回使用同一机制，原位偏好刷新保持滚动并清理旧转场，主题覆盖层被后续导航取消。记录本开本独立于内部normal／flip偏好，系统动画启用时约1000ms：线性时间、前300ms放大、220–1000ms掀盖，书架前350ms淡出。使用所点封面与真实纸页的短期纹理；beta.30封面纹理保存未裁角的完整图案，开本统一裁切front／back轮廓，源圆角随放大衔接纸张R。闭合时不绘纸页，展开后按投影遮挡露出；纸张内容／软件快照／书脊阴影受同一纸页轮廓限制。原生Outline匹配绘制，隐藏纸张的Z／alpha完成后恢复。异步媒体完成后合并重绘同一开本／折页纹理，不逐帧分配Bitmap或解码。NotebookPageMotion分开管理本子目标与位置，在整页端点复用View；纸背保持真实高度／圆角。返回复用BookOpenView反向合封面，完整关闭约800ms，开本途中按已展开比例缩短；内部翻页先落到最近真实端点。返回页提前提交，布局后按最新日期（无匹配时回入口日期）定位真实封面，隐藏目标alpha／Z后缩回，完成只恢复与释放，不延迟重建路由；无锚点安全退到实际返回页。关闭期间延后根数据重建，切页／生命周期清除pending与旧回调。新返回封面可能尚未首次draw，捕获时临时使用已布局的source宽高设置背景bounds，finally恢复，保证底色和图案完整。折页三个渐变和两个局部矩阵按实例复用；折线终点按稳定viewport左边界延伸，使反射纸背及AA描边在释放前移出可见区域，正反方向共用同一位置映射，纹理所有权不变。动画不制造日记，离开、尺寸或窗口变化须清理。

返回按钮DOWN直接呈现按下态；负向转场在一次真实OnDraw后post准备工作，不用固定等待，generation在tab／前进／dialog／pause／config／destroy取消过期返回。干净、同日期／尺寸的Me与书架根页可复用已布局实例；Today因媒体detach解码生命周期不复用。离开根页前若日期尚未归位，保存明确目标并强制按目标重建，避免缓存attach的nearest覆盖用户日期选择。

自定义纸面弹窗仅root RenderEffect（支持时6dp），window blurBehind关闭，dim在show前设置；Dialog身份＋generation拥有模糊，第一窗口施加、最后窗口关闭清除，旧dismiss不清新owner。营养师玻璃栏由内容／布局／滚动／位置变化标脏，活动捕获节流48ms，双Bitmap／Canvas／数组／Shader复用，静止不唤醒；玻璃和键盘监听随当前页面attach/detach，旧页面不调新页padding。

设置“记录本”集中书架子页、封面纸张、记录图片边框、翻页／内容摆放／分享；“照片查看”只管原图背景，食物贴纸轮廓与界面疏密留个性化。存储键及expanded/original等值不变。迹有收藏入口；按用户要求移除Me重复的饮食助手／营养师入口，今日原有营养师入口保留。Me偏好设置按个性化、智能助手、其他设置排列；智能助手管理API与两类独立授权，返回Me。其他设置“恢复出厂”仅保留原有外观默认值恢复项，确认和记录保护不变。收藏来源只在点击后的close action清理，书本返回收藏仍保留来源。API输出兼容选项默认折叠高级。未用餐文案只用于提示，不写成餐食。

## 数据与事务边界

- `SharedPreferences("eat_record_prefs")` 中 `days_json` 为饮食日记、`profile_json` 为设置/资料；照片、透明图和下载字体位于应用私有目录。
- 餐次键：breakfast、lunch、afternoon_tea、dinner、snack。餐次内保留旧text/photos/stickers，增量foodItems保存菜名、来源、展示角度、营养及估算依据。
- 新餐图先入独立FoodDraftStore；用户明确确定才进入days_json。草稿/后台队列不进入应用备份、云同步或营养汇总，打开/预载页面不创建空日记。
- UI与worker在 `NutritionStore.LOCK` 下读取最新数据后按字段合并；手动热量优先。analysisId/epoch/识别来源及授权世代防止迟到任务复活取消记录。
- 图像与营养分别更新，显示旋转不重编码原图/蒙版；重抠图不调用营养API，识别信息复用现有抠图。
- beta.24照片导入先按2048目标解码、归一EXIF方向，再以最长1600px/JPEG92保存；低内存可回落较小解码。模型/主体守卫不因此改变，旧低清来源不自动覆盖，重抠不能恢复丢失细节。
- 真实手写文字可长按居中选中并单独删除；删除在共享锁内核对原始文字后仅合并文字字段，保留同期媒体、营养和手工热量。
- beta.23餐次弹窗原位输入文字，完成/返回/转到图片前重读最新记录并仅合并文字；清空不删媒体或营养，空白打开/关闭不创建日记。日期点击与滑动共用同一日卡实例和连续呈现进度。
- beta.34起，ProfilePrivacy划分本机API／云会话／上传授权及世代，PrivateProfileStore迁移到eat_record_private；先确认私有副本持久化，再清理公共profile，内存READY不能当作磁盘成功。界面／worker读取合并视图，公共profile不持久化敏感字段。XML同时排除云备份与设备迁移的私有preferences；SafeBackupAgent在备份前恢复事务并完成清理，失败终止；系统恢复剥离旧凭据与外来事务标记。不宣称新增静态加密或已经实测系统备份服务。
- 本地导出／项目云同步共享脱敏策略及流式ZIP写入，正常路径不在内存保存整个ZIP；旧base64服务器兼容有大小上限。恢复先在noBackup暂存、校验路径／形状／媒体完整性／空间／解压上限，再用同文件系统rename与单次公共preferences提交安装；持久日志支持失败回滚和启动恢复。commit(false)仍可能更新内存，因此失败恢复使用已知事务和旧快照，不依赖RAM中的标记。无法恢复时暂停编辑并展示重试界面，保留日志。退役缓存限定清理及旧记录格式兼容保留。

## 三类独立网络能力

1. 个人云同步：用户登录/启用后访问项目配置的云端API；实现为 `server/cloud-api-server.py`，由package.json的start启动。SQLite保存账号与备份；旧JS服务文件不代表当前入口，实际部署状态需另查。
2. 餐图/营养师API：直连用户自行配置的HTTPS Chat Completions端点。营养师读取授权的记录，不等同于餐图上传；项目服务器不代理模型，不内置公共付费Key。
3. 应用只读分发：`AppDownloadClient` 使用 `https://pan.freefeng.icu` 与账号相对路径 `/eat record` 获取字体、更新和APK；会话只在内存，不把客户端只读目录当管理员上传挂载路径。新手教程随当前APK以assets/help/getting-started.md打包，本机展示，不再拉取可能推荐退役能力的旧远端文档。

餐图菜名和营养估算只使用用户配置的HTTPS视觉API，并明确取得上传授权；本地抠图不需要Key。新用户只有配置API或稍后，旧source=offline迁到api且关闭自动识别／照片授权、提升世代并hold旧营养任务，不自动产生API调用。旧营养值及真实来源仍可读，显示为历史基础估算，不冒充API结果。相关模型类、下载器、JNI、参考表、声明及LLM库不再构建或打包。

API Key输入为密码模式，禁自动填充／文本状态保存，提供删除持久化Key入口。删除／重新保存提升API及餐图世代、取消连接，模型检测和营养师回调检查授权快照，旧结果不能回写；凭据使用上述独立本机存储，不随导出／恢复同步。

详情reload只读，不清缓存／使根失效；餐次编辑关闭仅保存真实变更并恢复pending，不无条件重建或清后继窗口。食物详情先确认安全现存来源并成功创建原图查看器，才关闭原详情；失败保留原窗口。查看器覆盖期间RootDirty／餐图刷新只保留pending，当前owner关闭后恢复；旧关闭动画不得清掉新viewer。

beta.34原图首开及切换在独立单线程队列解码，旧请求用世代／owner取消，成功时原子更新图像、索引和计数；读取失败保留旧图。缩略图同key合并弱引用订阅，key含文件版本，缓存失效世代拦旧结果；后台内存清理转主线程，长边控制采样。空白餐纸只引导下方星标，图案不添加；字体弹窗按内容收缩，同时遵守父高度与最大高度。

beta.35夜间共享顶栏透明，整页由同一Paper背景提供纸色和无纹理标题区；滚动容器顶部预留96dp、正文12dp，首屏108dp起点与原布局一致，clipToPadding及顶部渐隐阻止内容进入标题区。标题空白处不透传点击，按钮／外层手势保留。滚动恢复等实际布局后的preDraw，明暗配置回调使用本轮保存的目标；保留原位500ms明暗过渡，不重建Activity。AppDayTheme/AppNightTheme明确区分，夜间资源不再继承浅色主题。具体设备的颜色以实际HWUI和手机截图验收，局部软件draw通过不代表真机通过。

## 分发与资料维护

- Git origin：`https://github.com/CYNIC-CREEP/Eat_record.git`；普通本地产物：`dist/EatRecord-<versionName>-debug.apk`。
- 发布渠道已有GitHub Release、OpenList脚本及蓝奏辅助脚本；当前可用管理端点/账号权限和生产签名方案不是由文件存在即可确认，见 `RELEASE_PROCESS.md`。
- `ReleaseNotes.java` 是本机公告、当前特性和更新日志的数据源；远端正式版清单与本地未发布Beta不必相同。
- `AGENTS.md` 定规则，本文定架构，`CURRENT_TASK.md` 定现场，`DECISIONS.md` 定约束，`RELEASE_PROCESS.md` 定操作；历史报告只作证据，不自动授权开发或发布。

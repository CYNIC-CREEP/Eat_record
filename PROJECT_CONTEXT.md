# 项目上下文

本文件描述稳定架构；当前版本、暂停状态和验收缺口见 `CURRENT_TASK.md`，历史过程见根目录 `V06_*REPORT.md`。不依赖旧聊天恢复上下文。

## 项目与使用场景

- 名称：一蔬一饭；Android 包名：`com.eatrecord.app`。
- 目的：本地优先的日常饮食记录，把文字、餐图与贴纸整理成可回看的吃饭记录本；用户可记录三餐/夜宵、浏览历史、查看营养估算及周报。
- 主页面为今日／足迹／我。今日含可展开日历、统一餐纸、拍照/相册和手写餐食，足迹展示记录本，我提供资料、个性化及其他设置。
- 已实现：图片旋转/修边/双指缩放、独立重抠图与识别信息、记录本翻页/分享、收藏/回收站、备份恢复、三餐提醒、可选云同步、餐图营养和独立营养师。
- 只保留当前餐纸界面，不再有旧版切换入口；旧文字、photos/stickers、手动热量和备份数据继续兼容。

## 技术栈

- 主体为原生Android Java与Activity＋动态View/Canvas，底部导航及餐食快捷菜单在0.6.3-beta.3按用户要求使用Kotlin＋Jetpack Compose，beta.8将记录本开合呈现纳入局部Compose。PowerShell自定义构建调用固定Kotlin编译器、Compose插件及AndroidX依赖。
- C++17 JNI＋固定 MNN 3.6.0。原生库支持 arm64-v8a/armeabi-v7a，仅用于本地抠图；视觉模块每ABI为eatvision、MNN、mnncore和c++运行库，共8库。Compose另引入每ABI一个固定graphics-path库，当前APK双ARM共10库。实验性本机营养识别已于beta.32移除。
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
| `res/`、`assets/` | 资源、默认小赖字体、模型清单/抠图模型和许可证 |
| `scripts/`、`tests/` | 构建/验包/模型转换/分发脚本、JVM与隔离Android QA |
| `server/` | Python云同步API、SQLite、更新清单、教程、按需字体 |
| `build/`、`dist/` | 可再生依赖/缓存/测试输出及APK；多为Git忽略文件 |

beta.33已移除旧SAM源码、JNI声明、构建与空打包链；当前仅native/eatvision.cpp参与视觉构建。历史记录／热量字段读取不属于旧实现清理。

## Compose底部交互区域

ComposeBottomBar提供无可见文字的三图标浮动胶囊、水滴选择面及仅今日根页显示的餐食菜单。Java通过onTab/onCapture/onWrite接入导航及按时间归类的拍照/相册和手写入口，Compose Animatable与弹簧负责底部视觉，旧Java底栏绘制层和动画实例退出实际View树。菜单及主按钮的显隐目标同时影响胶囊宽度，逻辑隐藏即移除交互语义，子按钮充分分离后才接收点击。

该局部ComposeView安装自己的Lifecycle、SavedState、ViewModel及Recomposer，Activity生命周期通过桥接方法驱动。主题提交及实际uiMode配置变化会重建内部ComposeView和Recomposer，保留外层Appearance、导航回调与owner，外层重新挂载时绑定新的View树owner。触摸区域取实际胶囊和圆形按钮，透明区域让页面继续接收手势。原生DOWN启动反馈，短按UP在压力达到0.86后保留速度回落，长按继续至1并维持。按压独立使用有界帧时间并继承系统动画倍率，长帧最多推进1/30秒，反馈时钟持续运行，位置和菜单继续有界时钟。胶囊持续拥有横拖，按当前位置和触摸增量跟手，UP提交最近Tab一次，CANCEL恢复原选择并清理捕获状态。右侧主按钮最大放大24%，与左侧膨胀独立。beta.10主图标为左右排列的简笔与四角星组合。阴影按材质一次生成小纹理，绘制复用几何和静态图标路径。按压／拖动数值在draw读取，语义启用按阈值更新，菜单只触发placement，presence变化才更新命中布局测量。根页跟手通过followRootPosition传入，释放后从当前位移与速度回到目标。底栏在整页导航准备期暂停局部呈现时钟，准备完成后从当前状态接续。

MainActivity的软件Canvas截图临时排除Compose宿主。书本快照继续由独立活底栏呈现，主题快照额外绘入上一帧底栏，主题揭示层统一覆盖页面和新底栏。底部业务按原页面身份接收，触发当前时间对应的拍照／相册和手写编辑入口，菜单退出沿即时目标收起。构建细节及验证见V063_BETA3_REPORT.md。

## 页面呈现边界

今日／足迹／我由RootPagePager保留三个attached根View，RootPageMotion继续拥有0..2连续位置和最终目标，底栏身份仍为0／1／3。0.6.2-beta.1返修恢复HEAD原横移：translationX=(index-position)×width，alpha=1；点击沿用0.48秒响应弹簧，手势MOVE直接1:1、可停留和反向，松手按原速度／位移规则吸附（0.4秒响应）。上一轮RootTabFade已删除，不再默认交叉淡化，不叠两套呈现控制。退出页禁焦点／触摸／无障碍，缓存、日历内部手势和静止根边界不变。

0.6.2及0.6.3-beta.1／beta.2的Java底栏使用BottomNavMotion、BottomNavIndicator、BottomNavLens和BottomNavBackdrop。beta.3的实际界面由前述ComposeBottomBar接管，这些旧视觉类保留为历史实现及测试依赖。根页仍由RootPagePager拥有，普通设置子页继续PageCrossfade。历史视觉参数见V062_BETA2_REPORT.md。

TodayDayPager／HomeCalendarView／TodayWeekStrip按RootPager的静止owner启停，隐藏时保留位置／目标并暂停输入和弹簧，不detach回收餐图；旧根回调须同时核对世代和实例。SharedPreferences的日记／资料变化只标脏，主页面、内部日期手势、弹窗及子页面转场完成后才重建，保留待完成日期目标及当前滚动。提醒跳今日强制更新，跨午夜标脏；后台内存压力释放根页，回前台重建。缓存增加首次准备成本，减少后续输入路径开销；不能据此宣称设备零卡顿。

PageCrossfade只负责层级子页面，根页继续RootPagePager。beta.3起普通“我”设置走显式smoothSettingsSwap：450ms线性时钟，SettingsTransitionMath按时间横坐标求解用户的分段Bezier，新旧页面各0.25W协同位移，RTL镜像；新内容0.15..0.55 easeOutCubic、旧内容0..0.30淡出。复用sticky_paper_header原标记，旧标题0..0.16退出／新标题0.16..0.34接入，避免正常进退的双标题。前20%新内容禁输入／焦点／无障碍，旧退出层不拥有交互。原页面标志仍拥有业务身份，render一次；视觉end只清理，Slide对象身份撤销旧Animator／preDraw。beta.7普通settings新页先在旧页不透明覆盖下完成一次正常窗口绘制，随后单次帧回调才建立硬件缓存并启动原时钟，修正冷页透明状态预建缓存导致的夜间灰卡片。Slide同时拥有首次绘制监听和回调，取消时一并撤销。beta.7原生截图确认Me、Other首次与子页返回颜色一致，详见V062_BETA7_NIGHT_CARD_REPORT.md。有限settingsScrollPositions只存原页面标志对应的滚动整数，不是新返回栈；pop在布局时恢复。

beta.3需求纠正仅“其他设置→餐次管理”例外：PageCrossfade委托SettingsContainerTransform为视觉owner，480ms/Bezier(.25,0,.25,1)，上下共用smooth(e/.62)、左右e、圆角r×(1-e²)。固定父快照在Path外提供背景，不透明容器承接原卡色/边框。来源快照只有内容，局部向目标标题baseline接近，装饰先退；共同identity取标题/正文安全显现较小值，来源前40%退出、目标标题/正文后60%统一在最终坐标进入。来源绘制Rect与page/bounds/baseline元数据反向续接，原导航owner/scroll恢复后重测锚点及安全降级继续拥有业务状态。Bitmap数量保持三张、一个Overlay，API29+原纸面缓存与原始主题色像素承托继续使用。该模式为beta.4继续保留的默认基线。

beta.6按用户最新反馈将A设为餐次试点默认，480ms/Bezier(.25,0,.25,1)，四边同smooth(e/.90)。beta.5的B为历史选择，临时selector仍保留0基线/1A/2B的安全回退。A在同一Session/Layer内使用一次准备帧，完整旧视觉覆盖新真实根View的一次正常硬件绘制，随后有界帧回调捕获稳定Bitmap，再隐藏真实根并运行原时钟，业务render仍只提交一次。A不缓存或重放过渡Paper，固定主题paperPixel与可变card fillPixel独立，后段逐渐透出真实root Paper。快速反向可短期保留当前合成帧，按现有纹理实际字节数检查32MiB并在捕获完成后回收。捕获/动画/清理继续同一owner与实例校验。SETTINGS_CONTAINER_MOTION_ENABLED=false回退普通设置转场，系统动画0直接归位。最终真机验收和剩余限制见V062_BETA6_A_FIX_REPORT.md与CURRENT_TASK.md，历史A/B见V062_BETA4_AB_REPORT.md。

特殊云同步／回收站／收藏／媒体等继续原480ms独立ease及全宽不透明横移，不全局替换PageCrossfade默认模式。所有模式沿用实际旧View／中断合成帧、同主题纸面补底、局部bounds裁剪、32MiB硬件层预算及原清理路径；仅中断／多direct roots合成一帧，不复制业务View或增加订阅。配置／后台／零动画安全归位并恢复alpha／位移／背景／padding／焦点／层。原API33确认返回、按键／防重复和24dp内部边缘提交保留；没有接入预测返回进度。SETTINGS_MOTION_ENABLED可独立关闭新模式，不影响根页及底栏。记录本开合由下节的局部Compose宿主管理。NotebookPageMotion继续拥有内部翻页位置与目标，复用整页端点View及原折页渲染器。折页渐变、矩阵及稳定viewport边界修正继续保留。

## Compose记录本开合

0.6.3-beta.8的ComposeBookTransition提供Animatable进度、共享矩形、圆角和封面转轴，beta.9统一由单Compose Canvas录制硬件绘制命令。MainActivity.BookOpenView继承该宿主，保留日期、收藏来源、返回目标、锚点与真实原生页面捕获。开本620ms、合本520ms，部分合本至少300ms，系统关闭动画时直接归位。页面矩形按整个进度变化，封面翻过90°显示纸背。打开中返回从当前矩形、圆角和开合程度衔接，requestId隔离旧完成回调。

宿主安装独立Lifecycle、SavedState、ViewModel、Recomposer及有界呈现时钟。姿态每帧原地计算一次，Canvas复用Camera、矩阵、画笔及BitmapShader，封面按源尺寸捕获，纸页保留原尺寸。beta.10使用跟随当前圆角的低透明外沿，源封面态外沿透明度为零，纹理prepareToDraw提前准备，Java的分段曲面绘制和开合Choreographer循环已移除。BookCoverGeometry及其JVM测试已退役，原文件保全于build/v063b8/retired-source。当前渲染验证采用Compose宿主和实际OnDraw帧，零进度同值返回直接清理。

beta.10点击封面先显示一次来源抬起反馈，再由同一世代准备页面与纹理。纸页已布局时在preDraw捕获并启动，纸页捕获后保留与原生View尺寸一致的背景bounds，并按需刷新圆角outline，防止首次软件绘制消费尺寸更新后留下空边界。返回先提交实际书架或收藏页，定位最新日期封面，缺失时退到入口日期或实际返回页。新封面捕获临时修正背景bounds并在finally恢复。目标封面alpha／Z临时隐藏，结束恢复。返回时实时书架承托背景，打开中反向保留旧背景纹理至清理完成。内部翻页中的返回先归最近真实端点。切页、尺寸、窗口及生命周期取消准备监听和旧请求，先释放Composition和Recomposer再回收纹理，结束只恢复与释放。业务与媒体写入继续使用原路径。当前实现与验证见[V063_BETA10_REPORT.md](V063_BETA10_REPORT.md)。

返回按钮DOWN直接呈现按下态；负向转场在一次真实OnDraw后post准备工作，不用固定等待，generation在tab／前进／dialog／pause／config／destroy取消过期返回。0.6.3-beta.2起，干净、同日期／尺寸且无待完成日期目标的今日、Me与书架根页可复用已布局实例。今日餐图在重新attach时按新世代异步重载，外层导航结束后恢复日期与交互所有权。离开根页前若日期尚未归位，保存明确目标并强制按目标重建，避免缓存attach的nearest覆盖用户日期选择。

自定义纸面弹窗仅root RenderEffect（支持时6dp），window blurBehind关闭，dim在show前设置；Dialog身份＋generation拥有模糊，第一窗口施加、最后窗口关闭清除，旧dismiss不清新owner。营养师玻璃栏由内容／布局／滚动／位置变化标脏，活动捕获节流48ms，双Bitmap／Canvas／数组／Shader复用，静止不唤醒；玻璃和键盘监听随当前页面attach/detach，旧页面不调新页padding。

设置“记录本”集中书架子页、封面纸张、记录图片边框、翻页／内容摆放／分享；“照片查看”只管原图背景，食物贴纸轮廓与界面疏密留个性化。存储键及expanded/original等值不变。迹有收藏入口；按用户要求移除Me重复的饮食助手／营养师入口，今日原有营养师入口保留。Me偏好设置按个性化、智能助手、其他设置排列；智能助手管理API与两类独立授权，返回Me。其他设置“恢复出厂”仅保留原有外观默认值恢复项，确认和记录保护不变。收藏来源只在点击后的close action清理，书本返回收藏仍保留来源。API输出兼容选项默认折叠高级。未用餐文案只用于提示，不写成餐食。

beta.8将方案A泛化为PageCrossfade.runContainer。Main的settingRow点击外层同步提供来源View，正文/标题/页面用键值tag标记，原默认tag继续供业务使用。NavigationTransitionHistory为最多64条纯字符串视觉来源记录，业务flags决定实际返回目标，render后再验证父子身份。返回在父页与scroll恢复后查找真实来源，支持嵌套/跳层/收藏多父；无来源重入失效旧记录，跨入口不复用旧page纹理。通用测量仅计可见正文，逐行延后未完整展开的文字，hint按整字段处理。CaptureAppearance仅在捕获作用域移除PaperComponents.Paper并恢复padding/callback。适用同宿主选项导航已推广，独立Activity/书本/图片/弹窗/根手势各自持有现有呈现。完整覆盖和验证边界见V062_BETA8_NAVIGATION_REPORT.md。

0.6.3-beta.1将底栏的子页退出交给显式整页导航回调，沿用480ms时钟，目标页先在旧页覆盖下完成正常绘制。根页互切由RootPagePager处理，液态底栏延续既有弹簧。开本使用原主题像素与单独纸纹捕获背景，新纸页完成首次正常绘制后捕获并启动原1000ms时钟，取消统一撤销准备回调与纹理。验证范围见V063_BETA1_REPORT.md。

0.6.3-beta.2将根导航准备与运动阶段分开。BottomNavIndicator在准备期间暂停帧回调，AnimationFrameClock从准备完成后的首帧重新建立时间基准。PageCrossfade在缓存准备后的下一帧发出onReady并启动原480ms动画。RootPagePager通过canOwnInput守卫在外层转场结束后恢复今日的日期联动，干净且日期匹配的今日层级可复用。 beta.3的底栏采用Compose连续帧时钟，PageCrossfade的页面准备和RootPagePager的输入所有权继续沿用。

AnimationFrameClock限制每次呈现推进至1/30秒，PageCrossfade、GestureSpring和局部Compose帧时钟共用该界限。长帧之后继续运动，墙上时间的积压被舍弃，低帧率环境的实际总时长可能延长。系统动画关闭时直接归位，页面、封面及Compose继续保留非零倍率。日夜材质重建与导航状态分开，主题快照使用ComposeBottomBar.drawCurrentFrame绘入真实上一帧视觉。beta.8的开合驱动由Compose接管。

## 后台编辑、媒体和投影缓存

beta.10的MealTextSaveController管理内联与手写编辑的保存中、失败重试和完成回执。进程级串行队列执行MealTextSave，在NutritionStore.LOCK内读取最新days_json并按字段合并，保留媒体、营养及旧餐次数据。成功回执属于当前编辑窗口，内联完成／返回／转图片按持久化结果继续，手写取消沿原语义处理。Activity销毁时内联编辑提交到进程队列，worker持有preferences与弱UI回执。

FoodUi.DetailImage先建立固定图片区域，独立后台队列解码、生成轮廓并旋转，弹窗关闭或View detach撤销请求并释放迟到Bitmap。NotebookShareRenderer接收不可变的单日文字、排版、主题、Typeface和媒体描述快照，NotebookShareExport后台绘制、压缩和写入MediaStore，ShareImageTransaction统一输出流、发布、取消及失败条目清理。Main按任务、页面、日期、窗口与生命周期身份接受结果，重复触发沿同一在途任务处理。

BookShelfWindow在原ScrollView／HorizontalScrollView中维持完整逻辑范围，按可见区域与邻近缓冲区创建子View。日期映射可即时ensureDate并定位真实封面，支持收藏和旧日期的返回锚点、滚动恢复、逻辑无障碍顺序及横向RTL布局。不可见缓存页保留当前子树，重新可见时按视口更新。

DiaryProjectionIndex按精确单日JSON片段复用餐数、每日热量和有序日期投影，只解析变化日期。Main在数据对象、已提交raw和受控就地修改的dirty钩子变化后更新索引，餐数读取当前内存单日对象。NutritionStore.displayDayCalories由日历与FoodUi共享，手工热量及旧字段算法沿用原逻辑。

AvatarLoader复用mediaThumbnailExecutor，以文件版本和目标尺寸缓存小头像、合并请求，文件事件和attach世代校验回执。缓存释放引用，显示对象继续拥有其Bitmap，Activity销毁时解除头像订阅。DirectoryWatch按规范化目录共用原生监听，最后一个订阅退出才停止，锁外分发回调；字体父目录订阅在进程内保留，子目录替换后重挂。AppTypography复用常规／粗体对象，字体族及选中文件事件控制字体重载，普通资料与字号修改复用字形对象，字号比例独立计算。七项状态见[优化清单](PERFORMANCE_OPTIMIZATION_PLAN.md)。

## 新增餐次与修正

MealTime按手机本地小时确定新增餐次，5／11／16／23点为切换边界。当前日记日期独立于餐次时间，图片草稿保留入口时确定的目标。新增流程直接进入原编辑与确认链路。其他设置的餐次管理及夜宵开关已移除，历史夜宵、下午茶和旧字段继续兼容。

FoodUi详情内提供独立餐次字样点击区域，循环午→早→夜→晚→午。修正使用NutritionStore.LOCK读取最新食物并移动引用，与队列路由一次提交。食物识别身份和营养字段保留，页面在弹窗或原图查看结束后刷新。

## 数据与事务边界

- `SharedPreferences("eat_record_prefs")` 中 `days_json` 为饮食日记、`profile_json` 为设置/资料；照片、透明图和下载字体位于应用私有目录。
- 餐次键：breakfast、lunch、afternoon_tea、dinner、snack。餐次内保留旧text/photos/stickers，增量foodItems保存菜名、来源、展示角度、营养及估算依据。
- 新餐图先入独立FoodDraftStore；用户明确确定才进入days_json。草稿/后台队列不进入应用备份、云同步或营养汇总，打开/预载页面不创建空日记。
- 手动修整在内存维护蒙版，最多撤销／重做32步（含重置），双指和取消不提交；较早笔画合入单张检查点。轮廓复用FoodStickerRenderer，以单任务低分辨率异步预览跟随修边，眼睛只控制本窗口显示；关闭后回收并拒绝迟到结果。保存仍用原图×蒙版，不烘焙展示轮廓。
- UI与worker在 `NutritionStore.LOCK` 下读取最新数据后按字段合并；手动热量优先。analysisId/epoch/识别来源及授权世代防止迟到任务复活取消记录。
- 0.6.1记录本热量输入默认只读同步各餐识别值，显式修改后按餐覆盖（manualCaloriesByMeal），当天汇总使用手填餐次＋其余餐次最新识别值；旧整日manualCaloriesTotal保持兼容。表单监听更新时保护已编辑草稿，保存仅合并已编辑字段，收藏只写favorite；消费／收藏／直接保存不把AI值固化为手填。
- 图像与营养分别更新，显示旋转不重编码原图/蒙版；重抠图不调用营养API，识别信息复用现有抠图。
- beta.24照片导入先按2048目标解码、归一EXIF方向，再以最长1600px/JPEG92保存；低内存可回落较小解码。模型/主体守卫不因此改变，旧低清来源不自动覆盖，重抠不能恢复丢失细节。
- 真实手写文字可长按居中选中并单独删除；删除在共享锁内核对原始文字后仅合并文字字段，保留同期媒体、营养和手工热量。
- beta.23餐次弹窗原位输入文字，beta.10在完成/返回/转到图片前通过后台事务重读最新记录并仅合并文字；清空不删媒体或营养，空白打开/关闭不创建日记。日期点击与滑动共用同一日卡实例和连续呈现进度。
- beta.34起，ProfilePrivacy划分本机API／云会话／上传授权及世代，PrivateProfileStore迁移到eat_record_private；先确认私有副本持久化，再清理公共profile，内存READY不能当作磁盘成功。界面／worker读取合并视图，公共profile不持久化敏感字段。XML同时排除云备份与设备迁移的私有preferences；SafeBackupAgent在备份前恢复事务并完成清理，失败终止；系统恢复剥离旧凭据与外来事务标记。不宣称新增静态加密或已经实测系统备份服务。
- 本地导出／项目云同步共享脱敏策略及流式ZIP写入，正常路径不在内存保存整个ZIP；旧base64服务器兼容有大小上限。恢复先在noBackup暂存、校验路径／形状／媒体完整性／空间／解压上限，再用同文件系统rename与单次公共preferences提交安装；持久日志支持失败回滚和启动恢复。commit(false)仍可能更新内存，因此失败恢复使用已知事务和旧快照，不依赖RAM中的标记。无法恢复时暂停编辑并展示重试界面，保留日志。退役缓存限定清理及旧记录格式兼容保留。

## 三类独立网络能力

1. 个人云同步：用户登录/启用后访问项目配置的云端API；实现为 `server/cloud-api-server.py`，由package.json的start启动。SQLite保存账号与备份；旧JS服务文件不代表当前入口，实际部署状态需另查。
2. 餐图/营养师API：直连用户自行配置的HTTPS Chat Completions端点。营养师读取授权的记录，不等同于餐图上传；项目服务器不代理模型，不内置公共付费Key。
3. 应用只读分发由`AppDownloadClient`访问`https://pan.freefeng.icu`，支持账号相对路径`/eat record`与游客路径`/24.Personal/2_EatRecord`。账号会话仅在内存使用，游客请求采用匿名上下文。新手教程随当前APK以assets/help/getting-started.md打包，本机展示，不再拉取可能推荐退役能力的旧远端文档。

0.6.3-beta.1由AppDownloadClient同时支持应用账号相对路径和游客路径，AppUpdateCheck按原位置、游客位置顺序判断versionCode，发现新版本后返回该来源的下载信息。游客读取采用匿名请求，字体优先读取游客fonts并可回退原位置，默认内置字体与已下载字体继续复用。关于该软件提供GitHub仓库入口，检测回调按会话世代及页面身份接收。

百度管理根为`/baidu/03.工具/24.Personal/2_EatRecord`，游客根为`/24.Personal/2_EatRecord`。游客目录已有五种字体、五份许可、教程及正式0.6.2/code202清单，当前Beta为0.6.3-beta.10/code212。云端资料与APK均已通过无登录完整下载校验。实施清单与设备验收状态见[DISTRIBUTION_MIGRATION_PLAN.md](DISTRIBUTION_MIGRATION_PLAN.md)。

餐图菜名和营养估算只使用用户配置的HTTPS视觉API，并明确取得上传授权；本地抠图不需要Key。新用户只有配置API或稍后，旧source=offline迁到api且关闭自动识别／照片授权、提升世代并hold旧营养任务，不自动产生API调用。旧营养值及真实来源仍可读，显示为历史基础估算，不冒充API结果。相关模型类、下载器、JNI、参考表、声明及LLM库不再构建或打包。

API Key输入为密码模式，禁自动填充／文本状态保存。删除入口二次确认后清空Key、地址、模型及检测元数据；取消保持原配置，未配置界面不自动补默认服务商。删除／重新保存提升API及餐图世代、取消连接，模型检测和营养师回调检查授权快照，旧结果不能回写；凭据使用上述独立本机存储，不随导出／恢复同步。

详情reload只读，不清缓存／使根失效；餐次编辑关闭仅保存真实变更并恢复pending，不无条件重建或清后继窗口。食物详情先确认安全现存来源并成功创建原图查看器，才关闭原详情；失败保留原窗口。查看器覆盖期间RootDirty／餐图刷新只保留pending，当前owner关闭后恢复；旧关闭动画不得清掉新viewer。

beta.34原图首开及切换在独立单线程队列解码，旧请求用世代／owner取消，成功时原子更新图像、索引和计数；读取失败保留旧图。缩略图同key合并弱引用订阅，key含文件版本，缓存失效世代拦旧结果；后台内存清理转主线程，长边控制采样。空白餐纸只引导下方星标，图案不添加；字体弹窗按内容收缩，同时遵守父高度与最大高度。

beta.35夜间共享顶栏透明，整页由同一Paper背景提供纸色和无纹理标题区；滚动容器顶部预留96dp、正文12dp，首屏108dp起点与原布局一致，clipToPadding及顶部渐隐阻止内容进入标题区。标题空白处不透传点击，按钮／外层手势保留。滚动恢复等实际布局后的preDraw，明暗配置回调使用本轮保存的目标；保留原位500ms明暗过渡，不重建Activity。AppDayTheme/AppNightTheme明确区分，夜间资源不再继承浅色主题。具体设备的颜色以实际HWUI和手机截图验收，局部软件draw通过不代表真机通过。

## 分发与资料维护

- Git origin：`https://github.com/CYNIC-CREEP/Eat_record.git`；普通本地产物：`dist/EatRecord-<versionName>-debug.apk`。
- 发布渠道已有GitHub Release、OpenList脚本及蓝奏辅助脚本；当前可用管理端点/账号权限和生产签名方案不是由文件存在即可确认，见 `RELEASE_PROCESS.md`。
- `ReleaseNotes.java` 是本机公告、当前特性和更新日志的数据源；远端正式版清单与本地未发布Beta不必相同。
- `AGENTS.md` 定规则，本文定架构，`CURRENT_TASK.md` 定现场，`DECISIONS.md` 定约束，`RELEASE_PROCESS.md` 定操作；历史报告只作证据，不自动授权开发或发布。
- 2026-11-12本地历史产物归档与精简状态见`ARCHIVE_REPORT_20261112.md`。历史报告中的build/output路径通过归档证据索引检索。后续最终版经用户确认并要求上传OpenList和GitHub时，同轮同步百度版本档案，执行流程见`RELEASE_PROCESS.md`，游客分发规划见`DISTRIBUTION_MIGRATION_PLAN.md`。

## 0.6.1交互增量

- AdvisorPager位于RootPagePager的今日页内部，把今日餐纸与真实营养师页纵向排列，复用GestureSpring；触摸仲裁在MainActivity，隐藏今日freeze，返回resume。距离/速度门槛、取消/多指、重抓与尺寸/后台清理均与当前owner绑定；正常滚动到边界后再新起一次切页手势。隐藏营养师只读准备，首次提交进入再允许原自动分析；日期切换归位后更新隐藏页。
- nightSnackEnabled默认false，控制新增餐次选择；历史餐次可见性仍由旧记录内容决定。“其他设置 > 全局 > 餐次管理 > 开启夜宵”管理，替代无条件四餐入口。
- 收起的周历按dailyCalorieGoal与FoodUi.displayCalories只读投影显示每日胶囊水位，0摄入不填水／未设目标保持原样，达标封顶。有目标时非今日／非选中日期用淡虚线容器，今日提示细实线、选中保留原实线标记；水色很淡。水位绑定日期，独立于移动选中标记。DayCell缓存比率，绘制不读JSON／偏好；仅目标日期960ms微幅晃动，展开、隐藏、失去根owner或系统关闭动画时停动，静止无循环刷新。月历只显示原选中提示，不显示水位；描边向内避开祖先clip，当天cell的selected背景透明，避免覆盖父级标记。
- Beta.7日期淡化按月历pageMonth／周历固定自然年月，future仅管理可选性；周历参照不随选日、跨月滑动和根页重建改变，防整排明暗翻转。周波动采用稳定weekday细差，保持单目标与静止无刷新。营养概览内联展开每日目标草稿，只有显式保存才在锁内合并最新profile的dailyCalorieGoal；当前弹窗原位更新，根页沿用modal关闭后的延后刷新，保留日期和滚动。
- Beta.8将日期文字淡化与热量进度层分开：非本月只淡化文字，水位／水面线／虚线容器保持相同的基础淡色强度，不能再乘文字alpha，否则历史水位会近乎消失。0摄入仍不填水，水位算术和单目标波动规则不变。
- ThemeModeIcon位于我页顶栏右侧；ThemeRevealView在root坐标系按按钮中心揭示新主题，保留快照上限32MiB。圆心、四角覆盖、20dp羽化与同一半径推进；新主题为实际重建的UiTokens/Android日夜主题，不是黑白覆盖。系统栏由既有窗口主题同步，系统栏本身不截入应用圆形图层。设置页不再提供旧夜间开关。

0.6.1-beta.2修正：主页面可见性统一通过rootPagesVisible识别AdvisorPager包裹关系，底栏点击仍调用保留RootPagePager而非层级转场。营养师键盘遮挡只由固定root/可见窗口计算，不从平移host计算。日夜扩散1440ms，250ms图标由揭示层独占绘制并连续形变，旧快照省略图标、真实新按钮在交接前透明；图标墨色随同一圆形半径变化，禁止揭示后突然重置transform。

0.6.1-beta.3补充：营养师当前可见时切换根页仍保留实际营养师画面作为出场页；PageCrossfade按本轮owner提供同一ease进度驱动底栏位置/宽度，根页初始化不得抢写。中断取当前呈现位置，结束/取消释放owner。餐次管理成为独立preferences子页，夜宵键/默认值不变，配置更新保留子页，返回恢复其他设置滚动。

0.6.1-beta.4补充：营养师成为根页0内部的纵向子页，离开它与普通主页共用RootPagePager和RootPageMotion，点击路径不重建/重挂页面、不新增preDraw或层准备等待；根页0显示营养师时暂停Today日期写权，数据刷新延至允许时机。根页1/2真正归位后才静默复位纵向Today，保留原返回落点。生命周期、配置及后台缓存恢复同时考虑纵向状态和对应滚动；实际advisor owner绑定root/page0/父链。今日两个添加控件随同一垂直位置和1-progress透明度呈现，取消回位，不在越过slop时突然隐藏。层级设置和设置恢复分支继续原PageCrossfade，不改日夜参数、数据格式或模型授权。

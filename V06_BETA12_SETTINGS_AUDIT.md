# beta.12 设置能力审计（只读基线）

审计基线：0.6.0-beta.11 / versionCode 159，2026-10-19。以下是实施 beta.12 前的源码行为，不是新版验收结论；MainActivity 行号以该基线为准，后续编辑可能移动。未读取或记录 API Key、账户令牌、Cookie。没有修改业务源代码、运行构建或连接设备。

本次覆盖“我 > 个性化”“我 > 其他设置”所有可达设置页、条件子项与弹窗，并追踪消费者。分类：**共用**表示新旧界面都有真实消费者；**旧版**表示只影响旧版主页/玻璃界面；**新版**表示 0.6 餐图流程新增项；**失效**表示当前只保存偏好而无消费者。共用项应在两种模式保留，而不是因为最早出现在旧版本就全部隐藏。隐藏不应删除偏好或记录。

## 1. 个性化完整映射

入口：`renderThemeSettingsPage()`（约 14072）。

| 设置 / 子选项 | JSON 键 | 分类与真实消费者 |
| --- | --- | --- |
| 字体大小：80/86/93/100/108/116/126%，七档 | `font` | 共用。`scaled()`、`AppTypography.sp()`；原生设置页和 FoodUi/相机/餐纸均消费。 |
| 笔记字体：小赖、悠哉、文楷、漫黑、站酷快乐；下载/应用 | `bookFont`，私有下载文件 | 共用。`bookDetailTypeface()` / `styleBookText()` 用于本子及导出，`AppTypography.typeface()` 在全局跟随开启时用于新 UI。小赖内置，其他仍按需下载。 |
| 食物贴纸边框：白边+虚线、素白、双线、点点、无边框 | `foodStickerBorder` | 新版能力。`FoodStickerRenderer`、`TodayPaperView.FoodImage`、新美食栏、FoodUi/FoodProcessingDialog。它不同于下表旧式 `stickerFrame`；不要混用两个键。旧版当前也可进入共用 0.6 处理预览，故若选择严格旧版设置目录，应隐藏此新增设置，不销毁已存值。 |
| 主题色：绿、黄、红、蓝、深夜灰 | `decoration` | 共用。`applyDecoration()`、`nightDecoration()`；新版纸张仍用主题 accent/accentSoft，灰主题控制夜间底色。 |
| 头像背景半透明 | `profileGlassCard` | 旧版。`renderMe()` 约8456在新模式直接选 `PaperComponents.rounded()`，不进入 `ProfileCardDrawable`，因此新版开关无效。 |
| 底栏半透明 | `navGlass` | 旧版玻璃能力。`FloatingNavGlass.onDraw()` 会读它，但新模式 `navSurfaceColor()` 约23793强制不透明纸色，玻璃图像被盖住，只残留部分高光/描边/采样开销；不能对新版承诺真实半透明。 |
| 顶栏透明 | `headerTransparent` | 旧版。`StickyGlassHeader.setTransparentMode/onDraw/updateGlassProgress/requestSnapshot` 在新模式跳过玻璃路径、直接 `drawPaperVeil()`，约24177–24262。 |
| 全局字体跟随笔记字体 | `globalFontFollowsBook` | 共用。`applyGlobalTextTypeface` / `refreshCurrentSurfaceTypefaces` 以及 `AppTypography.typeface()`；默认小赖和用户所选笔记字体的全局切换仍有意义。 |
| 夜间模式（实际跟随系统） | `followSystemNight` | 共用。`configuredNightContext()`、`applyNightModeOverride()`、`nightDecoration()`、FoodStickerRenderer。不要误改成独立强制深色按钮。 |
| 布局：0–100，紧凑到舒展（保存兼容枚举） | `layoutLevel`、`layout` | 共用。`spaceByLayout()` 用于 basePage/设置行/底栏/本子/足迹间距；即使新版主页部分尺寸固定，仍非完全失效。 |

字体来源页属于“关于该软件”，不是个性化子页；更改字体列表不能移除其来源/许可证入口。字体弹窗预览和下载行为不是另一个模式开关。

## 2. 其他设置顶层

入口：`renderPreferencesPage()`（约9237）。当前结构为全局 / 今日 / 足迹 / 其他。

| 分组 / 条目 | 跳转或键 | 分类 / 建议 |
| --- | --- | --- |
| 全局 > 启动页面 | `chooseStartTab()`；`startTab` | 共用。可选今日/足迹/日历/我，日历合并或隐藏、设置隐藏时动态过滤。 |
| 全局 > 震动 | `renderVibrationPage()` | 共用，详见下一节。 |
| 全局 > 界面布局 | `renderInterfaceLayoutPage()` | 混合页，必须过滤今日旧卡选项；足迹部分共用。不能隐藏整页而丢失足迹布局。 |
| 今日 > 餐次管理 | `renderHomeManagerPage()`；`meals` | 旧版首页控制。新版 `TodayPaperView` 用固定五餐 `DayFoodEntries.collect()`，不读 `meals`；新版拍照选餐也固定三餐。该选项对新版首页承诺不成立。仍影响共用管理菜单/本子餐次可见集合，但不应宣称能隐藏新版首页餐图。 |
| 今日 > 图片与贴纸 | `renderStickerSettingsPage()` | 混合页，详见第4节；不能一刀切判断所有子项旧版。 |
| 今日 > 提示词管理 | `renderHintManagerPage()` | 混合，首页提示旧版专属，缺席提示在共用本子仍消费，详见第5节。 |
| 足迹 > 记录本封面 | `renderFootprintSettingsPage()` | 共用。 |
| 足迹 > 笔记页面 | `renderNotebookSettingsPage()` | 共用。 |
| 其他 > 其他功能 | `renderMoreSettingsPage()` | 当前容器，内含提醒+AI；按本次要求删除其可见入口，将提醒另成全局按钮，AI项直接放其他分组。必须改完所有回调和返回落点，不能只删一行。 |
| 其他 > 隐藏设置 | `renderHiddenSettingsPage()` | 共用，但“贴纸栏”在新模式应改称“美食栏”。 |
| 其他 > 实验性功能 | `renderExperimentalPage()` | 当前只有两个禁用占位：智能餐单、智能抠图。均无可执行设置，尤其0.6已有本地抠图，不应在新版继续显示“智能抠图待开发”。严格新目录应不展示空占位。保留源代码不等于保留入口。 |
| 其他 > 恢复默认设置 | `confirmResetPersonalizationDefaults()` / `resetPersonalizationDefaults()` | 共用操作，但原实现会重置两套偏好及API/云同步等，见风险节；新模式应只重置实际适用的个性化键，不能误改隐藏旧偏好。 |

## 3. 全局子页、界面布局与餐次

`renderVibrationPage()`（约9646）：强度 `vibrationLevel` =轻/中/强，四场景 `navHaptic`、`calendarHaptic`、`pageSwipeHaptic`、`mediaEditHaptic`。均共用，实际触发 `playTabFeedback/playCalendarFeedback/playPageSwipeFeedback/playMediaEditFeedback`。模拟器无振动硬件不代表真机触感验收。

`renderInterfaceLayoutPage()`（约9376）：

| 子设置 / 选项 | 键 | 分类 / 消费者 |
| --- | --- | --- |
| 今日 > 文本框：透明度0–100%、玻璃度0–100% | `mealCardAlpha`、`mealCardGlass` | 旧版；`mealCardBackgroundForValues()` 约15066在新模式硬返回纸卡，不读这两个数值。 |
| 今日 > 左侧条痕：0–100；无/极细/细/标准/粗/很粗 | `timelineLevel`、`timeline` | 旧版；`timelineVisible()` 约23706在新模式硬返回false。 |
| 足迹 > 笔记字体 | `bookFont` | 共用；与个性化中的同一键重复入口，不是独立字体设置。 |
| 足迹 > 页面布局：平滑 / 展开 | `footprintLayout` | 共用；`renderBooks()` 约5697。 |
| 足迹 > 每行本数：3/4/5/6 | `footprintColumns` | 共用但仅展开布局实际消费，`renderExpandedBooks()` 约5747。原页平滑模式仍显示此项，会形成“保存但没变化”体验；建议与下四项统一仅展开显示。 |
| 足迹 > 本子间距：4–28dp | `footprintGap` | 共用，仅展开显示/生效。 |
| 足迹 > 显示日期 | `footprintDateSections` | 共用，仅展开显示/生效，约5765按年月分组。 |
| 足迹 > 显示热量 | `footprintShowCalories` | 共用，仅展开显示/生效，约6039。 |
| 足迹 > 显示消费 | `footprintShowSpending` | 共用，仅展开显示/生效，约6039。 |

`renderHomeManagerPage()`：早餐/午餐/下午茶/晚餐/夜宵五个 `mealToggleRow`，全存于 `meals`；至少保留一餐的既有约束、已有数据不得隐藏丢失逻辑在 `activeMeals()/visibleMealsForDay()`。新版固定餐纸不消费此列表，旧界面可保留。不要为了隐藏设置删历史 `afternoon_tea/snack`。

三餐提醒（原 `renderMoreSettingsPage()` 约10990）：`mealNotificationsEnabled` 总开关，以及 `breakfastNotificationTime` / `lunchNotificationTime` / `dinnerNotificationTime`（默认08:00/12:00/18:30，24小时TimePicker）。全部共用，`MealReminderReceiver` 不读UI模式。本次应以单独“全局 > 三餐提醒”按钮打开同样内容。更新时需要保留通知权限授予/拒绝回调、时间更改后的闹钟重排、关闭取消所有闹钟及开机/升级恢复。

## 4. 图片与贴纸完整子项

`renderStickerSettingsPage()` 约9735，`renderStackSettingsPage()`是历史单项叠放页；本基线未找到 `openStackSettingsPage()` 的调用入口（只有定义），因此它不是另一个可见设置分类。

| 原分组 / 子设置 | 键 | 分类 / 追踪结论 |
| --- | --- | --- |
| 图片 > 图片背景：黑/白/透明 | `photoBg` | 共用。原图查看器 `photoViewerBackground/photoViewerTextColor` 约20370真实读取，详情点击原图仍使用它；不能因为新版主页透明贴纸不读就认定整项无效。可在新版明确命名“原图查看背景”。 |
| 贴纸 > 上传贴纸 | `uploadCutout` | **失效**：全仓只找到定义、设置读取/写入和默认值；无导入流程调用 `uploadCutoutEnabled()`。现新图直达相机/草稿并自动本地抠图，旧界面也已共用该新导入链路。应移除开关和旧说明或明确恢复真实旧语义，不能保留一个无消费者开关。不要删已保存字段造成兼容风险。 |
| 贴纸 > 贴纸上滑收缩 | `stickerCollapse` | 旧版主页 `mealEditor()` 约5474/5491消费；新版固定餐纸无收缩交互。新模式应隐藏；共用“管理今日”旧餐次编辑窗口仍可间接显示旧控件，但不应当作新版首页设置。 |
| 贴纸栏 > 图片合集 | `photoCollection` | 旧版。新 `collectStickerEntries()` 约5064先直接返回 `collectJournalFoodImages()`，不消费includePhotos；新美食栏统一去重展示餐食。 |
| 贴纸栏 > 合并贴纸 | `galleryStack` | 旧版。与上项同一早返回导致新版忽略mergeStacks；旧集合才读 `mediaStackGroup()`。 |
| 样式 > 贴纸边框：柔和/无/邮票/泡泡/餐盘/虚线 | `stickerFrame` | **共用旧式小图框**。旧餐卡/旧贴纸栏消费；新旧本子小图及分享导出同样读 `notebookStickerFrameBackground()/stickerFramePadding()`（约6730、17967）。不能直接删掉新模式的设置值；若新版只留一种边框入口，需明确迁移/统一本子绘制，或在新模式将此项改称“本子小图边框”，不要承诺这是首页alpha轮廓。 |
| 样式 > 允许叠放图片/贴纸 | `mediaStacking` | 旧版 `MediaBoard` 拖拽分组约24614消费，新 `journalMedia()` 无拖拽叠放。切换关闭会调用 `clearAllMediaStacks()` **写入并清理已有叠放组**，所以模式切换不得为隐藏开关自动关闭/清理。 |

集合页自身还有 `stickerColumns`（3–6列）和 `stickerGroupMode`（day/month/none）的顶部按钮，两个模式的集合渲染都继续消费；它们不是图片设置页子选项，切版不应清空。

没有可见 `aiCutout` 开关。`aiCutoutEnabled()` 同样只剩未调用定义，不能将它误当成仍在起作用的模型开关。

## 5. 提示词及本子设置完整子项

`renderHintManagerPage()` 约11951：

| 提示分类 | 键 | 分类 / 消费者 |
| --- | --- | --- |
| 今日提示 | `homeHints` | 旧版 `homeHint()`，仅 `renderToday()`/旧局部刷新使用；新版日期热量摘要未使用。 |
| 早餐/午餐/下午茶/晚餐/夜宵提示 | `mealHints.<meal>` | 旧版餐次表单主用途；`mealEditor()`也在新版“管理今日”对话框可用，所以不能断言全程序无消费者，但新主页没有未记录餐次卡。若严格精简新版可隐藏该分类、保留键。 |
| 缺席一次/连续两次/连续三次/很多天 | `skipHints.one/two/three/many` | 共用。`skippedReminder()` 在本子叙事约6616和旧编辑器消费；新版餐纸本身用固定“已标记未用餐”，不直接消费个性提示。 |

每类提示子页都有新增、修改/保存、长按删除；空分类回退默认池，不是“空=禁用”。它们是内容偏好，不是AI system prompt。

`renderFootprintSettingsPage()` > 本子封面：`bookCoverStyle`，18种可选风格（food/strawberry/bear/ocean/checker/cat/flower/nightstar/rabbit/puppy/duck/sheep/business/pastel/classic/duotone/tritone/clear），共用 `BookCoverDrawable`，另有兼容旧枚举simple/patchwork/summer归一。

`renderNotebookSettingsPage()` 约9960，所有项目均共用：

| 分组 / 设置 / 子选项 | 键 | 消费者 / 条件 |
| --- | --- | --- |
| 格式 > 衔接动画：平滑/翻页 | `bookTransitionMode`、兼容`bookFlipAnimation` | `renderBookDetail()`转场约6355；保存同步兼容bool。 |
| 格式 > 翻页音效：关闭/轻柔/清脆/厚纸 | `bookFlipSoundMode`、兼容`bookFlipSound` | 仅“翻页”时设置页显示，`playPageFlipSound()`选择三个raw资源。 |
| 外观 > 纸张：奶油横线/清新/粉粉/方格/素白/食谱/点点/便签/信纸/果果/晚霞/蓝图 | `notebookPageStyle` | 本子视图和导出纸张绘制。 |
| 记录 > 记录方式：分开几行/合并一句 | `bookStoryLayout` | `fillNotebookContent()`约6547和导出约17836。 |
| 记录 > 贴纸摆放：自然斜放/整齐摆放 | `bookStickerLayout` | 本子小图板约6708和导出17900/17967。 |
| 记录 > 图片摆放（确认开启/关闭） | `bookPhotoBoard` | `NotebookMediaEntries.collect(day,includePhotos)`控制额外普通原图；结构化食物alpha始终显示，不能误说明为关闭就隐藏食物图片。 |
| 分享 > 分享界面选项：全屏界面/纸张界面 | `bookShareMode`、兼容`bookSharePaperOnly` | `shareNotebook()`约17778；保存保持兼容bool。 |

## 6. 隐藏、AI与API完整子项

`renderHiddenSettingsPage()`约10852：

| 设置 | 键 | 分类 / 约束 |
| --- | --- | --- |
| 隐藏设置（隐藏“我”） | `hideSettings` | 共用。与隐藏日历互斥；否则丢失恢复入口。开启时启动页若为我则改今日；日历齿轮仍可恢复设置。 |
| 隐藏贴纸栏 / 新模式应叫美食栏 | `hideStickerGallery` | 共用。`openStickerGalleryPage()`先拒绝跳转。 |
| 隐藏日历 | `hideCalendar` | 共用。与隐藏设置互斥；启动页为日历时迁到今日。 |
| 合并足迹和日历 | `mergeFootprintCalendar` | 共用。`buildNav()`和重复点击足迹切换日历逻辑消费。 |

`addUsableAiSettings()`约11007，原“其他功能 > AI设置”下项目应平铺进“其他设置 > 其他”：

| 设置 | 键 | 分类 / 依赖 |
| --- | --- | --- |
| 餐图识别与隐私 | 下表五子项 | 新版0.6新增能力；严格旧版目录不展示此新增入口。其他AI助手不等于它。 |
| 调用 API | `apiKey/apiEndpoint/apiModel/apiModels/apiModelMode/apiVisionSupported/apiEnabled` | 共用。API检测、动态模型列表/手动模型、快速/思考、官网入口。无效检测不得保存为有效。 |
| AI分析 | `aiAnalysisEnabled` | 共用。`buildNav()`约1519重复点今日，在两个模式均可打开AI助手，不是只存在旧首页。开启要求API已配置。 |
| 自动分析 | `aiAutoAnalysis` | 共用。`renderAiAnalysisPage()`约2714；开启要求AI分析。 |
| 食物识别 | `aiVisionEnabled` | 共用AI助手视觉能力。`aiPhotoContent()`/AI请求上下文约3463/3513读取，依赖`apiVisionSupported`；**不同于0.6云端自动营养**。 |
| AI读取记录 | `aiRecordAccess` | 共用。分析/聊天约3507/3793读取；开启要求API。 |
| 开启记忆 | `aiChatMemory` | 共用。聊天约3797读取；开启要求API。 |
| 自动识别热量 | `calorieRecognition` | 共用旧式整日估算回退。`requestCalorieEstimate()`约6922；无结构化营养/手动覆盖时才调用，共用本子会触发。不能仅因新版有foodItems营养就判定死开关。 |

餐图识别与隐私（`FoodUi.settings()`约265）五子项：

1. 云端营养自动识别开启/关闭：`foodAutoAnalyze` + `foodPhotoConsent`；明确授权后才可发餐图。关闭不关闭本地抠图；既有后台任务等待。
2. 配置图片识别API：复用共用 `configureApi()`，不是第二份凭据。
3. 每日热量目标：`dailyCalorieGoal`；300–10000kcal或留空=0；新首页/当天营养总览显示。旧首页无目标显示，按严格旧版目录隐藏此新增设置即可，不清零。
4. 严格JSON Schema启用/关闭：`foodJsonSchema`，只约束0.6图片营养请求，兼容模式保留。
5. 识别已保存照片：`FoodUi.retryAll()`检查所有日期餐次原photos，可能产生API费用；已有结果去重，不是个人偏好bool。

API弹窗包括Key输入、地址输入、检测模型/保存、动态模型/手动模型、快速/思考。官网页仅DeepSeek/MiMo/MiniMax三个外链，不是模型质量承诺。Key/地址/模型及食物授权不得加入云同步；迁平设置后所有成功回调、官网返回和系统返回必须落回“其他设置”，不能重建已经删除的“其他功能”页。

## 7. 模式切换与全局强制行为风险

- 模式由`profile_json.new_ui_v2`读出，缺省false（旧版）；`toggleNewUiPreview()`约196直接反转并`recreate()`。当前隐藏入口是新版品牌长按约2429、我页面版本长按约8579；新可见按钮应走统一确认函数，隐藏入口也应取消或同样确认，避免无提示绕过。
- `onCreate()`约1086每次都把Intent中的`new_ui_v2`写回。若Intent含QA/恢复extra，`recreate()`会把用户切换覆盖回原值；应一次消费或切换时删除extra。
- 切换仅应更改mode并重建界面，不重置原记录、照片、alpha、餐次、字体下载、API/授权、队列或叠放组。后台营养无需因界面切换取消；但FoodProcessing/草稿关闭或Activity重建必须保持现有取消/恢复世代安全，不隐式确认或丢弃。
- 新旧偏好均在同一profile保存；隐藏旧选项不等于覆盖它们。切回旧版仍应恢复用户曾设置的玻璃、侧线、集合/叠放等值。
- `timelineVisible()`、`mealCardBackgroundForValues()`、新Me纸卡、新navSurface、新StickyGlassHeader是明确覆盖旧选项的五处，应由模式过滤设置而非显示虚假的开关。
- `FoodStickerRenderer`本身不判断`new_ui_v2`，FoodUi/FoodProcessing也共用；若要求“旧版模式完全恢复0.5处理链”，不是只过滤设置即可完成的任务。当前用户要求可按界面/设置能力隔离实现，不能声称整个应用已回退到旧APK。
- 云同步的`preserveLocalApiProfile()`约16712已保留本机`new_ui_v2`；本地备份恢复可能恢复profile模式。模式只是本机UI选择，不应被异步远端资料覆盖。
- `renderMoreSettingsPage()`被FoodUi.Host.refresh/api、TimePicker保存、通知权限回调、configureApi保存、API官网返回、系统返回多处调用。迁平页面必须遍历这些落点，避免旧页面重新出现。

## 8. 恢复默认及建议验证矩阵

当前`resetPersonalizationDefaults()`约9523除字体/主题/布局外，还关闭`apiEnabled`及六个AI助手开关、`autoCloudSync`，并复位餐次、图库、叠放和两套边框。弹窗却只声明恢复界面个性化，不提这些功能状态。建议本次至少明确分离：共同外观键，两模式各自外观键；**不改mode、不改API/授权、不改提醒、不改云同步、不清记录、不清旧模式隐藏偏好**。任何需要改变重置定义的部分由主实现记录决策。

建议UI/状态回归至少覆盖：

- 新旧模式各完整遍历个性化、其他设置、所有子页；新模式看不到玻璃/侧线/旧主页收缩/图库叠放等无效控件，旧模式看不到0.6食物轮廓/餐图营养隐私等新增入口；共用本子/AI助手仍完整可达。
- 可见旧版入口↔新版入口的文案、取消不变、确认切换、重启持久化；Intent带modeextra启动后切换不反弹；隐藏入口无无提示绕过。
- 两次切换前后days/media哈希、draft数、队列和各模式隐藏偏好不变；已有历史照片和结构化餐图继续显示。
- 全局三餐提醒入口位于该分组底部；开关拒绝/允许通知权限、改时重排、关闭取消、返回不进入旧空页面。
- API保存/官网/食物授权弹窗关闭后仍在其他设置；只有明确授权才启用发送，无真实API密钥测试时只验合同与配置路由。
- 平滑/展开两种足迹设置条件、翻页音效条件、主题绿/黄/红/蓝/夜、150%系统字体、长名称及键盘状态。
- 恢复默认后仍处当前模式，记录/草稿/API授权/提醒/云同步及另一模式专属偏好未被误改。

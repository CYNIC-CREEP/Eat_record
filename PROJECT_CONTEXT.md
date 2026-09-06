# 项目上下文

## 项目名称

一蔬一饭（包名 `com.eatrecord.app`）。

## 项目目的

一款轻量、原生的 Android 饮食记录应用。用户可以把每天的文字、照片和贴纸整理成可回看的吃饭本子，并通过日历、收藏和营养师页面回顾饮食情况。

## 主要功能

- 今日：早餐、午餐、晚餐，以及可选下午茶和夜宵；支持文字、图片、贴纸、缺席标记和心情标记。
- 图片与贴纸：相册、相机、分享导入，长按后可删除、拖动、旋转、折叠和展开。
- 足迹与本子：按日期生成记录本，支持封面、纸张、笔记字体、布局、翻页音效和分享水印。
- 日历与收藏：按日期查看餐次、就餐方式和记录，收藏某一天并切换列表或展开布局。
- 营养师：在用户配置兼容的大模型接口后，生成饮食分析、历史聊天和热量估算。
- 个性化：主题、夜间模式、顶栏/底栏效果、界面布局、震动、图片贴纸和字体设置。
- 数据安全：本地备份恢复、回收站、登录后的手动或自动云同步。
- 三餐通知：可配置早餐、午餐、晚餐提醒时间，通知点击后进入应用。

## 技术栈与架构

- Android 原生 Java，最低 SDK 26；当前构建时从本机 Android SDK 选择可用的最高平台和 Build Tools，当前版本目标 SDK 为 37。
- 主界面和设置页面主要集中在 `src/com/eatrecord/app/MainActivity.java`，使用 `Activity`、`FrameLayout`、`LinearLayout`、`ScrollView`、自定义 View 和动态渲染，不是 Compose 架构。
- `MealReminderReceiver.java` 负责闹钟和系统通知；`MealCameraProvider.java` 为相机输出文件提供受控的 `content://` URI。
- 源码中仍保留 `native/eatsam.cpp`、`native/CMakeLists.txt` 以及 `MainActivity.java` 中的部分 SAM/MNN 相关实现，但这些属于遗留/待决代码。
- 当前 `build.ps1` 的正式构建流程不会构建或打包 JNI/MNN 原生库和 SAM 模型，当前 APK 不包含 `.so` 或 `.mnn`，不应将它描述为已具备完整 SAM/MNN 图像能力。是否未来重新启用尚未决定。
- 根目录 `build.ps1` 的当前实际流程直接调用 `aapt2`、`javac`、`d8`、`zipalign` 和 `apksigner`；脚本中保留的 CMake/MNN/SAM 辅助函数当前未被调用。仓库当前没有 `build.gradle.kts`、`settings.gradle.kts`、`gradlew` 或 `app/`。

## 重要目录

- `src/com/eatrecord/app/`：Android Java 源码。
- `res/`：Manifest 使用的 Android 资源、图标、音效和样式。
- `assets/`：随 APK 打包的应用资源及可选模型资源。
- `native/`：C++ 原生模块。
- `server/`：云端 API 源码、更新清单、教程和云端字体文件。
- `scripts/`：Android SDK 安装、OpenList 发布和蓝奏云上传脚本。
- `dist/`：构建出的 APK、源码压缩包和本地更新清单；其中生成物大多被 Git 忽略。
- `build/`：临时构建、模型依赖和测试输出；被 Git 忽略。

## 数据与网络

- 本地主要使用 `SharedPreferences` 保存 JSON：`days_json` 保存饮食记录，`profile_json` 保存用户设置和资料。图片、贴纸及按需下载字体保存在应用私有目录。
- 云端 API 是一个 Python 标准库 HTTP 服务，使用 SQLite 保存账号、资料和云端备份；服务器源码在 `server/cloud-api-server.py`，旧的 `cloud-api-server.js` 仍在目录中但当前 `package.json` 的启动入口是 Python。
- 云同步是用户主动或按设置启用的功能。API Key、接口地址和模型配置只保存在本机，不应上传到云端。
- 应用还通过 OpenList API 获取更新清单、APK 和按需字体。当前客户端代码使用 `47.97.215.111:5244` 的 OpenList API。

## 发布方式

- 本地构建产物是 `dist/EatRecord-<version>-debug.apk`。
- GitHub 仓库为 `https://github.com/CYNIC-CREEP/Eat_record.git`，正式 Release 的安装包应使用不含 `-debug` 的附件名。
- OpenList 发布由 `scripts/publish-openlist.ps1` 完成，会上传版本目录、更新清单、服务端文件、教程和字体。

## 已完成的核心能力

- 当前 `0.5.26` 已包含紧凑的更新提示窗口、`个性化 > 更多设置 > AI功能`、三餐通知和自定义提醒时间、未来日期限制，以及荣耀/华为相关启动和系统特效兼容保护。
- 当前版本已在一台通过 ADB 连接的 Android 真机上安装并完成启动、通知设置和日期边界等检查；身边使用者已确认荣耀设备可用，不再将荣耀真机验证列为待办。

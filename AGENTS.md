# Codex 工作规则

## Workspace sanity check

- 开始新的 Codex 会话时，首先确认当前项目根目录至少包含 `.git/`、`AGENTS.md`、`PROJECT_CONTEXT.md`、`CURRENT_TASK.md`、`DECISIONS.md`、`RELEASE_PROCESS.md`、`build.ps1`、`AndroidManifest.xml` 和 `src/`。
- 本项目当前不是 Gradle 工程。如果上述关键文件大量缺失，应优先怀疑打开了错误工作区，立即停止并确认正确仓库。
- 不得因关键文件缺失而自动执行 `git init`、创建 Gradle 工程、创建 `app/` 模块或大规模重构。

## 项目基线

- 真实项目根目录以 `git rev-parse --show-toplevel` 的结果和上述关键结构为准，不依赖固定的绝对路径。
- 当前工程是原生 Android Java 项目，界面主要由 `MainActivity.java` 动态创建；另有少量 C++ JNI 代码和 Android 资源。
- 当前仓库不使用 Gradle、Kotlin、Jetpack Compose 或 `app/` 模块。构建入口是根目录的 `build.ps1`。

## 修改前

- 先阅读 `CURRENT_TASK.md`、`PROJECT_CONTEXT.md`、`DECISIONS.md`，再阅读 `README.md`、`AndroidManifest.xml`、`build.ps1` 和本次涉及的源码。
- 涉及界面、数据或发布时，继续阅读 `MainActivity.java` 中对应的渲染、状态、保存和返回逻辑，以及 `server/`、`scripts/` 中的相关文件。
- 先理解现有实现和数据格式，再做最小必要修改。不得为了方便随意进行大型重构、替换架构或删除已有功能。
- 不允许无理由删除旧代码、兼容字段、用户数据迁移逻辑或已有功能。只有确认代码无调用、无兼容价值且删除不会影响用户数据时，才清理失效代码。
- 遇到复杂的状态、手势、动画、媒体生命周期或跨端同步问题时，先停止机械试错，重新画清状态流和生命周期，再修改。

## 修改后

- 至少运行 `git diff --check`，并使用当前构建链执行 `powershell -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`。
- 检查 APK 的版本号、签名状态、关键资源和体积；业务逻辑变化要补充针对性静态检查或运行检查。
- Android 功能涉及启动、通知、相机、文件、权限、系统返回、动画或真实设备兼容性时，优先使用 `adb` 验证。先执行 `adb devices`，确认设备在线后再安装和测试；没有目标品牌设备时必须明确标注未实机验证。
- 修改完成后更新 `CURRENT_TASK.md`。产生长期技术决策时更新 `DECISIONS.md`；发布流程的长期变化更新 `RELEASE_PROCESS.md`。

## 发布与安全

- 发布前确认工作区、版本号、`versionCode`、`versionName`、更新清单、APK 哈希、GitHub Release 和 OpenList 文件一致。
- 发布脚本可能改写 `dist/update.json` 和 `server/update.json`，发布后检查 diff，确认内容正确后再提交。
- 不在源码、Markdown、日志、提交信息或 Git 历史中写入密码、Token、Cookie、API Key、签名密码或其他秘密。命令中需要凭据时使用环境变量或本机安全凭据。
- 尊重当前原生 Java 架构和本地数据兼容性，优先做可回滚、范围清晰的小改动。

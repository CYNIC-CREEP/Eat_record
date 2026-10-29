# Codex 项目工作规则

## 仓库与架构

- 先用 `git rev-parse --show-toplevel` 确认根目录，核对 `AndroidManifest.xml`、`build.ps1`、`src/`、`res/`、`native/`。结构不符先确认工作区，不得自动初始化或迁移工程。
- 本项目是原生 Android Java＋动态 View UI＋C++ JNI，Windows PowerShell 自定义构建；不是 Gradle、Kotlin、Jetpack Compose 或 `app/` 工程。
- 业务源码放 `src/com/eatrecord/app/`，资源放 `res/`、`assets/`，原生模块放 `native/`，辅助脚本放 `scripts/`，服务端放 `server/`；`build/`、`dist/` 是缓存/生成物，不是源码替代品。

## 开始修改前

1. 阅读本文件，再读 `CURRENT_TASK.md` → `PROJECT_CONTEXT.md` → `DECISIONS.md`；构建/发布时读 `RELEASE_PROCESS.md`。
2. 检查 README、Manifest、构建脚本、Git 状态/最近提交/未提交 diff，再读相关源码、保存与返回逻辑和测试；不要假设聊天历史或旧报告代表当前实现。
3. 先理解数据格式、调用关系和生命周期，再做最小必要修改。未经明确要求，不迁移架构、大型重构、替换框架或删除功能。
4. 脏工作区的修改及未跟踪文件均须保护；禁止擅自 reset、clean、覆盖签名文件或批量清除旧数据。功能移除须有需求依据，并保留必要的旧记录/备份兼容。
5. 遇到复杂状态、手势、异步写入、媒体或同步问题，停止机械试改，先厘清状态流、所有权、取消和恢复路径。

## 修改后验证

- 代码/资源/构建变化至少执行 `git diff --check`、相关测试及 `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`；食物相关 JVM 入口是 `scripts/test-food-analysis.ps1`。纯文档任务只做文档校验，除非用户另有要求。
- APK 核对版本、签名、模型/字体摘要、ABI/原生依赖、许可、包体及 QA 隔离；使用 `scripts/verify-food-apk.py`，不套用旧版“不能含 .so/.mnn”的断言。
- 启动、相机、权限、文件、通知、返回、动画及设备兼容变化应使用 adb。先确认设备序列号与用途，不擅自安装/清空用户日常设备。测试后恢复独立设备设置。
- QA 包/runner 会改写测试数据，只能用于独立测试设备，绝不发布。模拟响应、真实离线推理、参考表算术与品牌真机验收须明确区分；未测就说明，不虚构准确率/速度。

## 交接、发布与安全

- 每轮结束更新 `CURRENT_TASK.md`：短写真实状态、已完成/剩余、证据和下一步；历史细节链接已有报告，不无限追加流水账。
- 长期技术决定更新 `DECISIONS.md`；长期构建/发布变化更新 `RELEASE_PROCESS.md`；稳定架构变化更新 `PROJECT_CONTEXT.md`。被取代的规则不得继续当作当前指令。
- 以上四份文件是本项目交接约定，不能假设新对话会自动读完；由本文件明确要求读取。参考：[OpenAI 官方工作流说明](https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex)。
- 发布前核对工作区、Manifest/ReleaseNotes、产物、更新清单、标签、远端附件及 SHA256；现有发布脚本可能覆盖远端文件、打源码包、改本地清单或删除退休字体，先审查副作用。
- 构建默认带 `-NoUpload`；提交、打标签、push、GitHub Release、OpenList/蓝奏上传须有用户明确授权。文档中的命令不是执行授权。
- 禁止把密码、Token、Cookie、API Key、签名密码、私钥或带凭据链接写入 Markdown、日志、源码或 Git；通过本机安全凭据/环境读取。公开校验摘要不是凭据，签名文件仍不得入库。

# Current Task

## 当前版本 / 分支

- 版本：`0.5.26`，`versionCode 148`
- 分支：`main`
- 最近业务基线：`0429955`（`Release 0.5.26 with meal reminders and compatibility fixes`）

## 当前项目状态

- 当前业务开发已暂停，暂无正在修改的业务功能。
- 交接体系已整理完成，`AGENTS.md`、`PROJECT_CONTEXT.md`、`CURRENT_TASK.md`、`DECISIONS.md` 和 `RELEASE_PROCESS.md` 已通过文档提交正式纳入版本管理，未修改业务代码。
- 可用根目录构建：`powershell -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`
- 最近一次构建成功，APK 位于 `dist/EatRecord-0.5.26-debug.apk`。
- 最近一次已在 ADB 真机上安装并启动验证；身边使用者已确认荣耀设备可用，无需再将荣耀真机验证列为未完成项。
- `0.5.26` 已完成 GitHub Release 和 OpenList 发布。

## 最近已经完成

- 更新提示窗口改为按内容自适应并限制最大高度，短内容不再留下大块空白。
- 在个性化其他设置中增加 `更多设置`，集中放置 `AI功能`、三餐通知、提醒时间和可用 AI 开关；实验性页面保留未来功能占位。
- 新增早餐、午餐、晚餐系统提醒，支持自定义时间、Android 13+ 通知权限和点击通知进入应用。
- 首页日期切换和日期选择均禁止进入未来日期。
- 对夜间模式、模糊效果、启动公告和系统配置变化增加荣耀/华为兼容保护。
- 已完成版本 `0.5.26` 的 GitHub 正式 Release 和 OpenList 同步，GitHub 附件名为 `EatRecord-0.5.26.apk`。

## 当前正在进行

- 没有正在进行的业务功能。
- 五份交接文档已正式纳入版本管理。

## 尚未完成

- 没有待本轮实现的业务需求。
- 正式生产签名方案待确认；当前只确认了 debug keystore 构建流程。
- 公网旧更新路由 `http://47.97.215.111/eat-record/update.json` 是否继续使用待确认；客户端当前直接访问 OpenList API。
- SAM/MNN 遗留源码是保留、清理还是未来重新启用待确认；当前正式构建不打包相关原生库或模型。

## 已知问题

- 当前没有已确认且尚未解决的业务故障。
- 自定义 PowerShell 构建链、OpenList 与 GitHub 的 APK 命名差异属于已知项目约定，不是未解决问题。

## 相关文件

- `src/com/eatrecord/app/MainActivity.java`
- `src/com/eatrecord/app/MealReminderReceiver.java`
- `src/com/eatrecord/app/MealCameraProvider.java`
- `AndroidManifest.xml`
- `build.ps1`
- `server/cloud-api-server.py`
- `server/update.json`
- `scripts/publish-openlist.ps1`

## 下一步

1. 新任务开始时先读取本文件及 `AGENTS.md`、`PROJECT_CONTEXT.md`、`DECISIONS.md`。
2. 如修改业务，先定位 `MainActivity.java` 的状态、渲染、保存和返回路径，再做小范围改动。
3. 修改后执行构建、`git diff --check` 和必要的 ADB 验证，并在本文件记录结果。
4. 发布前确认更新路由、版本清单、GitHub Release 和 OpenList 远端文件一致。

## 注意事项

- 真实源码目录是 `D:\Users\cynic\Documents\Eat_record`，不要编辑旧的 `D:\Documents\Eat_record`。
- 不要随意删除兼容逻辑或本地 JSON 字段；用户本地记录和已下载字体必须保持可迁移。
- 不要在任何交接文档、提交、日志或脚本输出中记录密码、Token、Cookie、API Key 或签名密码。

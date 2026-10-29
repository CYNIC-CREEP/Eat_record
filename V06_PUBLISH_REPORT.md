# 0.6.0 正式发布记录

日期：2026-10-29（Asia/Shanghai）。用户在本地正式版验收后明确授权上传OpenList、GitHub并推送更新，取代此前暂不上传要求。

## 源码与GitHub

- 发布提交：`93bcf58b7d7e8a8c9363599d7ac650e9b7c222bf`，原main为e85ff8d；普通非强制推送。
- 注释标签：`v0.6.0`，标签对象 `2e89ada9bb3abab6b9f57129df2d598532b0dd4a`，指向上述发布提交；main和标签已原子推送。后续文档收尾提交不更改本版产品源码或移动标签。
- [GitHub正式Release](https://github.com/CYNIC-CREEP/Eat_record/releases/tag/v0.6.0)，发布于2026-10-29 08:58:25 UTC，已设为latest，非draft、非prerelease。
- GitHub附件的服务端SHA256与本地相同；正式APK已从公开下载地址完整回下载，按顺序重组HTTP Range数据后计算SHA256通过。

| 附件 | 字节数 | SHA256 |
| --- | ---: | --- |
| EatRecord-0.6.0.apk | 121162865 | `1753a9754d06b5e29b850f0f8b1080a05ea01e3051ebd91d032509e76e9b0eed` |
| EatRecord-0.6.0-source.zip | 51641473 | `dc3aa69f04dfbaf71edaa85bde6308d8a15d7bd5b9226cbdb8b3d48a3c4e4e34` |

APK就是已交付、已手机验收的0.6.0/code184普通包；本轮未重编译替换。签名、SDK、模型、字体、许可、双ARM和QA隔离结果见 [正式包报告](V06_RELEASE_REPORT.md)。

## OpenList与应用更新

- HTTPS管理入口为 `https://pan.freefeng.icu`，管理端目录为 `/lanzou/Myapp/eat record`。应用只读账号的base_path为 `/lanzou/Myapp`，因此应用请求路径为 `/eat record`；两者不能直接互换。
- 先上传到不参与版本检测的临时目录，完整回下载验证后重命名为0.6.0。正式目录包括apk、source、更新说明与教程。
- APK同时提供 `EatRecord-0.6.0.apk` 和 `EatRecord-0.6.0-debug.apk`：两份均完整回下载SHA通过，字节完全相同；后者用于兼容既有目录扫描的文件名约定，实际无debuggable或QA。
- 源码ZIP、更新说明及教程也已上传并回下载校验；本轮不重新部署云服务、不改现有字体或旧版本。
- 最后替换根目录教程和update.json：新文档先校验，原文档改名保留，再切换新文件；未直接删除旧文件。旧文档备份名称为 `_previous-guide-before-060-93bcf58.md`、`_previous-update-before-060-93bcf58.json`。
- 通过应用只读账号重新获取update.json，versionName为0.6.0、versionCode为184，说明已换为本版内容，APK大小/摘要与正式包相同；其原始文件与server/update.json和dist/update.json一致。清单SHA256为 `f682ee6ace01460d599bf157490f65f480eefa8db0884d2fd7024640f0a97c9f`。
- 更新清单apkPath保留旧管理绝对路径以兼容已有格式；0.6.0的AppDownloadClient会映射成只读账号路径，apkUrl提供GitHub同一正式包作为回退。

发布过程中发现下载连接限速，采用服务明确支持的HTTP Range分段完整下载、顺序重组和SHA256核对；没有以文件名或仅上传成功响应代替内容验证。OpenList提供者显示的大小会按KiB取整，验收采用实际下载字节数。

## 源码与凭据边界

- 源码ZIP通过git archive从发布提交生成，包含源码、资源、测试、许可和文档；不包含签名、凭据目录、浏览器会话、build/output缓存、QA APK或用户照片/日记。
- 发布前检查实际本机凭据未出现在待提交文件或源码ZIP中；Git加入本机凭据目录与Python缓存排除规则。
- assets许可保留原始字节，使用限定gitattributes保留第三方原文格式；未为通过空白检查改写APK中的许可内容。
- 发布API遵循[OpenList官方文件接口](https://openlistteam.github.io/docs/guide/api/fs.html)。会话和签名下载链接仅在进程内使用，报告和Git不记录真实凭据。

证据文件仅本机保存于 `build/publish060/`：github-published.json、github-download-verified.json、openlist-state.json、app-update-verified.json。公开说明见 [RELEASE_NOTES_0.6.0.md](RELEASE_NOTES_0.6.0.md)。

## 已知范围

本轮额外检查0.5.26源码中的旧HTTP匿名更新get/list入口，均为HTTP200但应用层code500；未修改账号权限、匿名根目录或服务器路由。仍运行该旧版的用户可从GitHub下载正式APK覆盖升级。当前版本采用的HTTPS只读账号链路已验收，不能将其结果误说为旧匿名接口也已修复。

本轮是发布操作，沿用正式包报告的应用实测范围；没有新增真实付费API、系统备份传输或其他品牌设备验收的声明。

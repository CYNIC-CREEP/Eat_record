# 0.6.3 正式发布记录

2026-11-17，Asia/Shanghai。用户确认Beta10后授权本轮转正式版，并同步发布GitHub与两个OpenList位置。

## 版本与提交

- 包名com.eatrecord.app，版本0.6.3，versionCode213，minSdk26／targetSdk37。
- 发布提交`8fe54275862a234a75deb7be724e0e2ebfa1c0f1`，标签v0.6.3固定指向该提交。源码ZIP由该提交git archive生成，收尾文档另作后续提交。
- 右侧按钮左侧简笔上端延长约1.3dp，右侧四角星半径从6dp增至6.5dp。完整版本内容见[更新说明](RELEASE_NOTES_0.6.3.md)。

## 产物与摘要

| 文件 | 字节数 | SHA256 |
| --- | ---: | --- |
| EatRecord-0.6.3.apk | 127244701 | `705a910c88e1acc5d9ebfb813f9e8fa852e2af26e41b222455e7ec0f621aa3d3` |
| EatRecord-0.6.3-source.zip | 52049474 | `364ff7b0ca900acc719dec1dedb5b0e557f69154ae9f0ad0928b1f1d4a92b7f8` |
| SHA256SUMS-0.6.3.txt | 181 | `3034590dc097f98f53158491e1ec31877a63003ca7adbfb0016c77f6377d2eca` |

原证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。原签名文件与受保护快照一致。普通包未启用debuggable，QA runner及夹具隔离通过。

## 发布结果

- [GitHub v0.6.3](https://github.com/CYNIC-CREEP/Eat_record/releases/tag/v0.6.3)为正式latest。APK、同提交源码ZIP及SHA清单在草稿与公开状态下均完整回下载并匹配本地摘要。
- [原OpenList目录](https://pan.freefeng.icu/lanzou/Myapp/eat%20record/0.6.3)按apk、source、文档分类。暂存文件校验后启用0.6.3，再通过应用只读账号的`/eat record`上下文完整回下载核对。
- [百度游客目录](https://pan.freefeng.icu/24.Personal/2_EatRecord/0.6.3)按apk、源码、文档分类，对应管理挂载`/baidu/03.工具/24.Personal/2_EatRecord`。暂存文件及启用后的全部版本文件均完整回下载，游客验证使用无登录请求。
- 两处apk目录各提供正式名和同字节-debug兼容文件名，后者供已有目录扫描回退使用。资料包含更新说明、教程、公开交付核对与SHA清单。
- 两份根update.json均为0.6.3/code213，APK大小、摘要、说明和GitHub回退地址一致，分别采用原目录及游客路径。共享教程已同步，百度项目索引与归档清单已加入该版。
- 共享文件采用新文件上传校验、旧文件改名备份、再启用新文件的顺序。暂存和正式路径、管理员与应用／游客上下文分别校验。下载发生超时时刷新链接并续取有明确Content-Range的字节，按顺序计算完整文件摘要。

## 本地与设备验证

- JVM测试、build.ps1 -NoUpload、git diff --check及APK检查通过。模型、字体、双ARM原生依赖、许可、包体预算与Compose固定运行库摘要通过检查。
- 最终普通包在独立EatRecordComposeV063QA／emulator-5588通过底栏与菜单操作检查。已安装APK的完整摘要与正式附件一致，崩溃缓冲为空，字体与动画设置保持初始值，测试结束关闭本轮启动的模拟器。
- 前序七项优化和Beta10设备专项见[V063_BETA10_REPORT.md](V063_BETA10_REPORT.md)。本轮验证使用独立模拟器，魅族21的实机动画观感由用户安装复核，软件模拟器可能出现长帧。
- 构建输入快照与发布源码一致。源码压缩包共325个条目，签名文件、本机凭据、浏览器会话、构建缓存、用户数据及QA产物已排除，并完成已知凭据字节检查。

本机执行证据保存在build/publish063，包括artifacts.json、github-release.json、pan-primary-stage.json、pan-primary-final.json、pan-baidu-stage.json、pan-baidu-final.json及设备、构建、JVM和验包记录。该目录属于本机生成物，公开验证结果以本文列出的产物、路径和摘要为准。

发布完成后尝试清理本轮回下载分段和临时副本，自动审批以blocked by policy拒绝删除。临时文件仍在build/publish063，普通APK的两个同字节本地文件名也仍存在，清理状态见本机cleanup.json。

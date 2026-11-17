# 版本归档与游客分发计划

更新于2026-11-12，Asia/Shanghai。0.6.3-beta.1已实现游客读取、顺序更新检测、游客字体下载与GitHub入口。共享字体、许可、教程和已发布0.6.2更新清单已准备并完成游客下载校验，设备视觉验收列入后续反馈。

## 发布时同步归档

用户确认某版最终成品，并要求上传OpenList和GitHub时，同轮发布工作包含将该版安装包、源码和相关资料写入百度版本档案。后续对这一发布请求的执行范围按此约定解释。

- 按完整版本号建立目录，例如0.6.3。每版使用apk、源码、文档目录，按资料需要增加验收证据等目录。
- 保存本版最终普通APK、固定发布提交导出的源码ZIP、版本说明与更新日志、SHA256清单，以及适用的教程、许可和公开验证摘要。
- GitHub、原OpenList分发位置与百度版本目录使用同一份正式APK及源码ZIP，记录发布提交、版本、字节数和SHA256。
- 对上传文件完整回下载核对摘要，再更新版本索引和发布记录。沿用已确认的最终成品保留策略及本地空间维护流程。
- 游客可读的资料按公开发布内容审查，签名文件、本机凭据、用户记录和QA安装包遵循项目既有排除规则。

百度管理目录使用`/baidu/03.工具/24.Personal/2_EatRecord`。游客访问对应`/24.Personal/2_EatRecord`。[百度管理目录](https://pan.freefeng.icu/baidu/03.%E5%B7%A5%E5%85%B7/24.Personal/2_EatRecord) · [游客目录](https://pan.freefeng.icu/24.Personal/2_EatRecord)

## 起始实现与规划时核对

- 已发布0.6.2/code202的AppDownloadClient使用应用只读账号及相对目录`/eat record`，映射的既有管理根为`/lanzou/Myapp/eat record`。
- 0.6.2检测更新入口为关于该软件页面的checkForUpdates。fetchUpdateInfo先读原位置update.json，异常时扫描同一位置的版本目录，showUpdateResult随后判断versionCode并显示结果。
- 起始字体根为`/eat record/fonts`，更新清单为`/eat record/update.json`。关于该软件已有检测更新、更新日志、联系作者和字体来源入口。
- 2026-11-12通过无Authorization、无账号登录的fs/list请求成功读取游客目录，API返回200且总计133项。该次结果包含0.6.2版本目录，缺少fonts目录及根update.json，对这两个目标的单独读取返回API 500。
- 百度管理路径、游客路径与应用只读账号的相对路径分别对应各自访问上下文，客户端按下载来源处理路径和请求头。

同日实施后游客根已有fonts、教程和update.json。当前游客清单对应正式0.6.2/code202，客户端Beta为0.6.3-beta.10/code212。游客字体优先、原字体位置回退，更新检测采用原位置优先再查游客位置。实测与交付记录见[V063_BETA1_REPORT.md](V063_BETA1_REPORT.md)。

既有OpenList位置用于当前发布兼容，百度目录承接版本档案及计划中的游客分发。[既有目录](https://pan.freefeng.icu/lanzou/Myapp/eat%20record) · [游客目录](https://pan.freefeng.icu/24.Personal/2_EatRecord)

## 云端字体与资料迁移清单

- [x] 清点既有OpenList中的有效字体、许可证、共享教程、版本说明、更新清单与下载引用，建立来源、目标、大小及SHA256映射。
- [x] 在百度版本档案根准备共享fonts目录，将现有有效字体及许可文件复制到该目录并完成摘要核对。
- [x] 清点并校验Xiaolai-Regular.ttf、Yozai-Regular.ttf、LXGWWenKaiLite-Regular.ttf、LXGWMarkerGothic-Regular.ttf和ZCOOLKuaiLe-Regular.ttf，补齐五种字体的许可文件。
- [x] 客户端接入游客字体路径`/24.Personal/2_EatRecord/fonts`，复用现有字体选择键、文件名、内置小赖和本机已下载字体。
- [ ] 在设备验收首次字体下载、进度、文件安装和重启加载。
- [x] 在百度根目录提供update.json，沿用versionCode、versionName、apkPath、apkUrl、sha256、apkSize和notes字段，apkPath指向游客可读的同版APK，GitHub附件作为可用下载地址一并核对。
- [x] 核对旧位置和游客位置对应同一正式0.6.2产物与摘要，将后续同版同步步骤写入发布流程。
- [x] 同步原教程，接入游客下载路径，核对游客可访问的文件原文与实际下载字节。
- [x] 对游客接口、原文下载、正式APK和字体直链完成无登录完整下载SHA256核对。

## 关于该软件的检测更新顺序

1. 用户点击检测更新，先按原位置的现有清单及兼容目录路径查询，并判断是否存在高于本机versionCode的可用版本。
2. 原位置发现新版本时显示该版本更新结果，结束此次检测。
3. 原位置未发现新版本时继续检查游客目录。原位置请求或解析失败时也尝试游客来源。
4. 游客来源发现新版本时显示更新结果，下载操作使用该来源对应的APK路径及可用回退地址。
5. 两个位置的有效检测结果均无新版本时，统一显示已是最新版本弹窗。连接和解析异常沿用现有检测失败反馈，具体异常组合纳入实现和验收清单。

- [x] 将先判断版本、再决定是否进入下一来源的流程接入checkForUpdates及fetchUpdateInfo，处理原清单成功返回但版本较旧的情况。
- [x] 在AppDownloadClient及下载解析路径中增加游客读取能力，以游客上下文解析游客路径，核对路径约束与请求头。
- [x] 让更新结果使用命中来源的路径，联通showUpdateAvailableDialog、openOpenListDownload及链接解析。
- [x] JVM验证原位置有新版本、原位置无新而游客有新、两个位置都无新、单源失败后另一源有新、全部失败及无效清单。
- [x] 接入连续点击去重、页面退出及迟到回调的世代校验。
- [ ] 在设备验收检测结果弹窗、连续点击与页面退出。

## 关于该软件的GitHub入口

- [x] 新增GitHub设置项，点击后通过既有外部链接打开方式访问本项目仓库。
- [x] 入口文案说明用途，沿用现有设置项、主题、字体及点击反馈，浏览器不可用时复用复制链接反馈。
- [ ] 在设备验收GitHub入口与浏览器不可用场景。

目标仓库为CYNIC-CREEP/Eat_record。[项目GitHub](https://github.com/CYNIC-CREEP/Eat_record)

## 实施顺序与长期迁移

先完成字体与更新元数据的资源清点和游客访问验证，再实现顺序更新检测及GitHub入口，随后对应用下载、字体使用和发布同步做验证。代码实施轮次按既有Beta版本及测试约定执行。

- [x] 完成共享资源与元数据准备。
- [x] 完成客户端更新检测、游客下载、字体迁移和GitHub入口。
- [x] 执行相关JVM、完整-NoUpload构建及验包，编译隔离Android QA场景。
- [ ] 完成设备视觉与交互验收。
- [ ] 用户确认后续最终版并要求OpenList与GitHub发布时，按同步归档约定将同版产物写入百度目录。
- [ ] 结合用户后续的完整迁移安排，制定旧目录退役时间、旧版App访问兼容及旧链接处理方案。

长期目标为将既有OpenList分发资料逐步整合到百度游客目录。具体切换与退役范围由后续迁移任务确定。代码交付与设备验收状态见CURRENT_TASK.md。

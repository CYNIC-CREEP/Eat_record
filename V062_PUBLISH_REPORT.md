# 0.6.2 正式发布记录

2026-11-12，Asia/Shanghai。用户明确授权GitHub、OpenList及应用更新发布。沿用已交付的0.6.2/code202普通APK，公开文件摘要与本地交付一致。

## Git与GitHub

- 发布提交为`71578805543c6be54aacf910e3ca0c60546392f4`，原main为8cc26c5。明确审查并加入48个累计修改/新增文件，包含当前产品代码、测试、报告、版本说明及更新清单。
- 注释标签v0.6.2的对象为`d6d8569417463de62926a898286b89325cd0f346`，指向发布提交。main与标签通过普通原子推送完成，后续交接文档提交位于发布提交之后。
- [GitHub正式Release](https://github.com/CYNIC-CREEP/Eat_record/releases/tag/v0.6.2)于2026-11-12T02:11:03Z发布，draft/prerelease均为false，latest已指向v0.6.2。
- 三份草稿附件的GitHub服务端摘要和完整回下载字节均通过校验。发布后又从公开下载地址完整下载APK与源码ZIP，摘要一致。

| 附件 | 字节数 | SHA256 |
| --- | ---: | --- |
| EatRecord-0.6.2.apk | 121220209 | `86d0061e7aded20eb2750aabc5b88f5797efdd6835d2e2c3ba773c9cb3da00cb` |
| EatRecord-0.6.2-source.zip | 51874090 | `5cd7b7aead2bb9d2a39c367e67f6add5f47d4da22d9a44f8b744815977dcdb87` |
| SHA256SUMS-0.6.2.txt | 181 | `873193eaba409e72c9bdc79c5d13a4a3a4324de97737ef144f03e02e2055c568` |

源码ZIP由发布提交git archive生成，逐项核对产品源文件与本地一致，文本换行归一比较。导出前后的候选文件和归档内容通过本机凭据精确匹配及敏感路径排除检查。签名、本机凭据、浏览器会话、缓存、APK和用户记录由既有Git忽略边界排除。

## OpenList与应用更新

- HTTPS管理入口为https://pan.freefeng.icu，管理发布根为/lanzou/Myapp/eat record，应用只读根为/eat record。认证会话仅在执行进程内使用。
- 先将正式APK、同字节-debug兼容副本、源码ZIP、版本说明与随版教程上传至_publish-062-code202暂存目录，五项均完整回下载SHA256校验。
- 暂存目录启用为0.6.2后，通过应用只读账号和正式相对路径完整回下载APK，使用独立于管理员暂存校验的缓存，摘要与交付包一致。
- 新update.json先上传并校验，再切换公开文件。原清单保留为_previous-update-before-062-code202.json，正式清单校验通过后完成切换。
- 最终应用只读入口返回0.6.2/code202、APK大小121220209 bytes、六条本版说明、OpenList路径及GitHub回退下载URL。server/update.json与dist/update.json为同字节932 bytes，SHA256为`9fc855322a8aae2ad39f64ee117380f94133888c20d169a922bf8c4526af82e0`。
- 最后按AppDownloadClient的refresh=false目录请求复核可见版本及清单，普通客户端下载路径已验证。

## 测试与验收范围

普通包验包重新通过4模型、双ARM8库、固定运行库、依赖闭包、字体/许可及QA隔离，min26/target37，无debuggable。APK摘要与此前v2/v3同证书验签记录匹配，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。

发布时完整JVM回归再次通过。构建与描边QA编译记录见V062_DELIVERY_REPORT.md。最终APK和新推广入口仍待逐项真机视觉验收，大字体/RTL/短窗口及帧率结论也需要设备证据。用户在获知该状态后授权发布。

## 本地证据与交接

build/publish062保存local-artifacts.json、github-draft-verified.json、github-public-verified.json、github-published.json、openlist-state.json、app-update-verified.json及发布期JVM日志。签名与构建记录继续位于build/v062-final。

下一步按用户设备反馈处理实际可复现问题，验收边界见CURRENT_TASK.md。

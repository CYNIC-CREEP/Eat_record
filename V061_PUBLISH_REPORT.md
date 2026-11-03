# 0.6.1 正式发布记录

日期：2026-11-03（Asia/Shanghai）。用户在魅族21人工复查后明确回复“没问题了，上传吧”，授权本版GitHub、OpenList及应用更新发布。沿用已经安装并验收的0.6.1/code193普通包，本轮没有重新编译或修改产品源码／资源。

## Git与GitHub

- 发布提交：`8a0d15ded2190f7b6510cbbd4c824d48eca078b7`，原main为2b8aae9；审查并明确加入41个累计修改／新增文件，未reset/clean或丢弃前轮工作。
- 注释标签：`v0.6.1`，标签对象`6bb5129bd895ed3dcef22eef4a56849481db4b65`，指向发布提交；main和标签通过普通原子推送完成。后续文档收尾不移动标签、不替换已发布源码包。
- [GitHub正式Release](https://github.com/CYNIC-CREEP/Eat_record/releases/tag/v0.6.1)，2026-11-03 08:45:41 UTC发布，非draft／prerelease，latest指向v0.6.1。
- 先上传草稿附件，核对服务端摘要并完整下载APK、源码包和校验文件；正式发布后又从公开下载地址完整回下载APK、源码包，SHA256一致。没有以附件名或上传响应代替字节校验。

| 附件 | 字节数 | SHA256 |
| --- | ---: | --- |
| EatRecord-0.6.1.apk | 121191537 | `e308ab640f7a8ac718ab66526bfe1da7bee01c2bc66d2080c1c22bb9246750c7` |
| EatRecord-0.6.1-source.zip | 51733432 | `57d343e92168b6915f2468fb54d7ddbf78a91478b76bea48694495bdb14c8d55` |
| SHA256SUMS-0.6.1.txt | 181 | `5bbba503345826b4c734b1821bc8f79a4ebe2ac135cb9c794a643da146c4c995` |

源码ZIP从发布提交git archive生成，共246个目录／文件条目；排除了本机管理凭据、签名、浏览器会话、缓存、APK和用户记录。dist仅包含明确跟踪的update.json。Git导出普通文本时按本机属性使用CRLF，与本地LF文档做归一比较；随包assets教程保持原字节，源码内容没有差异。

## OpenList与应用更新

- HTTPS管理入口`https://pan.freefeng.icu`，管理根`/lanzou/Myapp/eat record`，应用只读根`/eat record`。会话仅在进程内使用，不写入报告、Git或源码包。
- 先上传到不参与版本扫描的`_publish-061-code193`暂存目录。正式APK、同字节-debug兼容副本、源码ZIP、更新说明和教程全部完整回下载SHA256通过，再启用0.6.1目录。
- 正式目录APK再次通过应用只读账号和相对路径完整下载，使用独立校验缓存，未复用管理员暂存下载结果。摘要仍与手机验收包相同。
- 根目录教程和update.json先上传校验，旧文件改名保留，再切换新文件；备份为`_previous-guide-before-061-code193.md`和`_previous-update-before-061-code193.json`。未删除旧版本／字体或部署无关云服务。
- 最终通过只读账号获取清单：0.6.1/code193，APK大小121191537 bytes，摘要如上，7条本版说明，GitHub回退URL与OpenList路径均指向0.6.1。实际文件与server/update.json、dist/update.json逐字节一致：1101 bytes，SHA256 `de35f1a74b3ff18e2280c8700af5c782ed7d2dbb56384d17bc6dc68473cb97de`。
- 应用只读目录检查采用与AppDownloadClient一致的fs/list refresh=false；管理员强制刷新参数不用于只读账号，不修改账号权限。

## 产物与验收范围

验包、4模型、双ARM8库／6固定摘要、依赖闭包、字体／许可、QA隔离、包体预算及原签名v2/v3重新核对通过。证书SHA256：`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。min26/target37，普通包无debuggable。正式名和-debug名只是同字节副本。

用户明确免本轮模拟器／自动功能测试并完成人工验收；发布操作没有运行QA、重写手机记录、删除Key或主动调用营养API。应用功能／历史测试边界见V061_DELIVERY_REPORT.md及Beta.1–8报告，不将发布下载校验称为新功能回归。

证据仅本机保存于被忽略的build/publish061：local-artifacts.json、github-draft.json、github-draft-verified.json、github-published.json、github-download-verified.json、openlist-state.json、app-update-verified.json。公开发布包和本次验收包一致；旧0.5.26匿名HTTP更新接口限制仍见V06_PUBLISH_REPORT.md，本轮未扩大服务器权限或修改旧路由。

# beta.18 DeepSeek协议、模型名称和更新说明

2026-10-21，0.6.0-beta.18（versionCode166）。用户确认使用DeepSeek官方https://api.deepseek.com/chat/completions。没有读取/使用用户真实密钥、收费测试、公开上传、commit或push。

## 定位与修复

1. 官方Chat Completions文档的response_format.type只包含text/json_object，而App严格选项发送json_schema。beta.17收窄兼容重试后，枚举校验措辞不匹配unsupported，400/422落入笼统“不支持图片”提示。这是确定的App协议缺陷和高度相关的回归路径；没有真实服务端错误体，不能把截图单独当作全部个案原因的实测证明。
2. 官方DeepSeek端点的严格选项改用json_object；同一原图、提示、模型ID及最终营养/范围/明细校验不变。非官方仍支持json_schema，仅遇明确参数拒绝/枚举错误才有限兼容；无效图片/权限/错误Schema本身不会触发盲目降级。兼容缓存作用域、取消世代和草稿确认保留。
3. 快速/思考在官方精确HTTPS主机显式设置thinking.type；此前快速也会继承服务商默认思考。未知主机不套DeepSeek私有参数，不强改用户模型或模式。
4. 参数拒绝、无效图片、无效模型ID、服务商明确拒绝图片输入分别提示，附HTTP状态；只分析有界错误证据，不显示或持久化服务商原始错误、照片或密钥。

## 模型展示

官方/models示例id=deepseek-flash，name=DeepSeek-V4.1-Flash。前者是合法调用ID，不能擅改为营销版本字符串。新增本机端点作用域的展示名称缓存，选择框/模型列表/设置摘要显示正式名称与API ID；缺少元数据时标名称待刷新，不给旧记录伪造版本来源。云同步/导出继续排除本机API配置与元数据。

按照emil-design-eng的可读性原则，列表行和当前选择改自然换行、至少48dp触点，列表按内容高度、最大420dp滚动；避免长名称/能力说明挤进固定50dp或被单行裁掉。不改变现有动画或引入框架。

## 日志与公告

此前内置数据只写到beta.7，版本号更新并不会自动生成新说明；无更新弹窗又错误地把服务器0.5.26说明标为当前Beta特性。

ReleaseNotes作为单一纯Java数据源，保留历史restart/final分组并补beta.8–18。更新日志倒序从安装版本开始；启动公告和“当前版本特性”匹配实际版本，不套旧说明。真正远端新版本的下载弹窗仍使用远端说明。未知本机版本明确提示未收录。

JVM与普通构建均校验Manifest当前版本必须是本地说明最新项，防止只改版本号再次漏写日志；不修改远端清单或伪装已发布。

## 依据与测试边界

- [DeepSeek图像理解](https://api-docs.deepseek.com/zh-cn/guides/vision/)支持deepseek-flash和现有image_url/base64格式。
- [DeepSeek Chat Completions](https://api-docs.deepseek.com/zh-cn/api/create-chat-completion/)声明json_object与thinking开关。
- [DeepSeek模型列表](https://api-docs.deepseek.com/zh-cn/api/list-models/)区分id、name、输入模态。
- 本地320项检查通过：118合同/兼容、42调度、20能力、29日志/名称、28媒体、28本子、12日期、19提醒、24文字/明暗。覆盖官方JSON格式、快速/思考、精确主机、枚举有限兼容、无效图片不重试、错误隐私以及Manifest/日志一致性；历史0.5同版本restart分组有意保留，未当成重复Beta。
- Android deepseek-v18初轮及最终360×800dp夜间150%字体均通过官方协议假传输与真实ARM64本地抠图、模型name/id多行无裁切、启动说明/旧服务器说明隔离、最终营养草稿/一次请求/明确确认。截图/日志在build/beta18-qa。假HTTPS传输不等于真实DeepSeek可用性、速度或准确率实测。
- 最终默认完整流程通过无授权本地抠图/不上传、草稿/SSE名称和营养校验、重建/确认幂等/手动优先、备份恢复/回收站、在途取消与周报。

## 普通包

- dist/EatRecord-0.6.0-beta.18-debug.apk：120,982,416字节（115.3778MiB），SHA-256 1d124a782bcea3247cf2d5ee7e6fe6063795a0e87223245ca49d2f9e4858bb6f。
- 普通NoUpload构建通过新的说明一致性门槛；minSdk26/target37，v2/v3签名、四模型摘要/双ARM ABI/内置字体和许可证、QA隔离通过。Manifest没有debuggable或instrumentation，普通包不含测试模型传输/图片。
- 普通包覆盖安装与冷启动Status ok/COLD，PID5026，本次AndroidRuntime无错误，安装flags无DEBUGGABLE且没有QA instrumentation。字体/密度/系统夜间/动画恢复，关闭自建EatRecordV06QA。只在独立模拟器检查，没有品牌真机或真实账户联测；不改用户API密钥、不公开发布。

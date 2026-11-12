# 0.6.2-beta.8 通用容器导航过渡

2026-11-12。已将方案A提炼为通用点击来源过渡，并接入适合的同宿主层级页面。本轮交付普通Beta APK，供用户自行安装验收。

## 安装包

[EatRecord-0.6.2-beta.8-debug.apk](dist/EatRecord-0.6.2-beta.8-debug.apk)，code201，121220209 bytes，约115.605MiB，min26/target37。

SHA256为`6ee347157ea3297ae64d4c58b1d508a61c3eafcca8dad4ba279dbece7ddf28a1`。

包名com.eatrecord.app，沿用原v2/v3证书，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。普通包无debuggable，验包确认QA隔离、4个模型、双ARM共8个原生库、6份固定运行库摘要及字体/许可。

## 接入范围

| 来源 | 目标 |
| --- | --- |
| 我 | 个性化、智能助手、其他设置、个人信息、数据与安全、关于该软件 |
| 其他设置 | 震动、三餐提醒、餐次管理、照片查看、未用餐文案、记录本设置 |
| 记录本设置 | 书架布局 |
| 智能助手 | 餐图识别、营养师设置 |
| 关于该软件 | 更新日志、字体来源 |
| 数据与安全 | 同步数据、回收站、收藏记录 |
| 足迹标题收藏入口 | 收藏记录 |
| 今日品牌标题 | 美食栏 |
| 营养师标题 | 历史聊天 |
| 历史聊天 | 按日期只读聊天 |
| 未用餐文案 | 四种缺席分组详情 |

点击设置项仍执行原业务回调。外层仅在同步点击期间提供实际来源View，原回调调用一次。对话框、选择器、开关、根Tab、日历、图片、书本及独立Activity继续由其现有交互owner负责。API官网从配置Dialog关闭后进入，来源已脱离页面宿主，使用原安全回退。

## 通用契约

`PageCrossfade.runContainer(render, direction, source, requestedKey, acceptedEntry)`复用原SettingsContainerTransform渲染器。通用调用固定使用FULL_A，保留480ms及原Bezier(.25,0,.25,1)、四边同步与圆角变化。

NavigationTransitionHistory只保存最多64条child、parent、sourceTag字符串。原业务flags与原返回函数继续决定实际目标。render之后取实际页面身份，再记录或解析视觉来源；同页刷新不会记录层级来源，无来源重新进入会清除过期来源。

返回先按原业务逻辑构建父页、恢复已有滚动，再从新页面树中按稳定tag查找选项并重测bounds。该机制支持收藏的不同父来源，以及现有业务Back跳过一层时的祖先来源解析。历史记录不执行导航或选择业务父页面。

Session仅在同一入口key与完整父子身份匹配时复用中断纹理。跨入口改向、来源失效、异常、窗口不适合或内存预算不足时，沿已有安全归位路径显示实际接受的页面。

## 页面内容与夜间绘制

- 新增键值tag标注实际页面、标题及正文，保留原默认tag供业务与QA使用。字体来源的独立身份通过实际页面标记识别，原About/FontSources双业务标志继续存在。
- 标题读取实际带权重的TextView，排除返回箭头；来源文本按真实View提取Rect与baseline。
- 长页面只测当前可见正文及可见文字行。首个完整正文作为身份门控，其余文字保持最终坐标，在容器完整容纳该行后显现。空EditText的hint按完整字段控制。
- 延续先正常绘制一帧、再捕获的已验证准备态，输入/焦点/无障碍门控与Session清理继续生效。
- A继续使用独立固定paperPixel承托真实根纸面。捕获时暂时移除PaperComponents.Paper背景，并在finally恢复背景、padding与callback，避免Advisor等带纸面根重复烘焙背景；JournalPaper等业务背景继续使用原绘制。

关闭 `UiTokens.SETTINGS_CONTAINER_MOTION_ENABLED` 可回退原页面转场，原餐次API和历史模式接口继续兼容。系统动画0直接归位。

## 验证结果

- `build.ps1 -NoUpload`完整构建通过，日志为[build-final.log](build/v062b8-navigation/build-final.log)。
- 完整JVM通过，含新增145项有限来源/嵌套/跳层/多父/循环/失效检查及无来源重新进入失效回归，原容器61678项、A/B52937项、普通设置6219项、底栏365项、根页1160项与其他业务兼容检查通过。
- 新Android QA场景 `revision-v062b8`覆盖实际点击、长页面、Hint嵌套/系统跳层、收藏双来源、字体来源独立身份、跨入口中断、旧回调及纹理释放。使用最终构建classes编译通过，日志为[qa-compile-final.log](build/v062b8-navigation/qa-compile-final.log)。
- `verify-food-apk.py`、apksigner v2/v3、aapt2版本核对及git diff检查通过。
- 本轮新扩展页面尚未逐项真机视觉验收，Android QA只编译，安装包由用户自行安装验证。此前餐次试点与夜间修复的真机证据仍属于历史版本。

适合人工重点观察的路径为我→其他设置→记录本→书架布局、我→智能助手→餐图识别、我→关于→字体来源，以及足迹/数据安全分别进入收藏后返回。长表单、快速Back、RTL、大字和短窗口的观感仍需设备验证。

## 修改与保护

主要修改MainActivity的视觉来源接入、PageCrossfade通用委托、SettingsContainerTransform元数据与入口身份、NavigationTransitionHistory、资源键值id、版本及相关测试。原主题色token数值、原业务回调、数据格式及仓库接口继续使用原实现。

本轮开始前44份文件及beta.7普通APK保存在[before](build/v062b8-navigation/before)，摘要见[before-manifest.json](build/v062b8-navigation/before-manifest.json)。本轮独立补丁为[this-round.patch](build/v062b8-navigation/this-round.patch)，仅检查反向适用性。最终源文件与包体核对见[final-audit.json](build/v062b8-navigation/final-audit.json)。

当前main及本地origin/main仍为8cc26c5，server/dist正式清单仍0.6.1/code193。本地交付保持Beta及未提交工作区。

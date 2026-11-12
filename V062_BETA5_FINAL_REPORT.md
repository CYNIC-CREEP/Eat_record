# 0.6.2-beta.5 餐次管理动效收尾

2026-11-06收尾时记录。后续用户实测确认局部放大后突切、暗色整页灰闪，本文先前的视觉通过结论已被取代。beta.6正在返修，当前状态以CURRENT_TASK.md为准。

## 最终实现

- 总时长320ms，前240ms局部展开，后80ms内容交接。
- 原Bezier(.25,0,.25,1)，来源卡片上下各增24dp、左右各增8dp，按实际空间对称限幅。
- 交接期旧场景先32ms退出，真实目标纹理后48ms在最终布局坐标接入，标题正文共用alpha。
- 返回沿原局部几何路径收缩，布局及滚动恢复后重测真实来源行。原单次业务render、会话身份、旧回调取消、纹理释放及动画0直接归位继续使用。
- 仅餐次试点默认改为B，原A和Baseline保留为历史回退分支。SettingsContainerMath与本轮之前字节一致，运动参数及绘制分支均沿用已验证版本。

## 右侧灰弧归属

在当前安装包的餐次页，使用真实右缘系统返回手势，从x1078向x750、y1180、持续1200ms。手势尚未释放、应用仍显示完整餐次子页时，同样的灰色弧形已经出现。

同期窗口证据如下。

| 状态 | EdgeBackGestureHandler0 |
| --- | --- |
| 手势前 | mHasSurface=false，isVisible=false |
| 手势进行中 | mHasSurface=true，isVisible=true |
| 手势结束后 | mHasSurface=false，isVisible=false |

窗口mOwnerUid为10069，package为com.android.systemui，类型NAVIGATION_BAR_PANEL，重力位于屏幕右侧。SurfaceFlinger同期增加其可见Surface。应用B的局部绘制不包含该边缘弧形，出现位置也超出局部卡片Path。证据确认它属于系统返回手势呈现，应用过渡层保持原实现。

- [手势进行中截图](build/v062b5-final/gesture-held-app.png)
- [手势结束后截图](build/v062b5-final/gesture-after-app.png)
- [原速手势录屏](build/v062b5-final/gesture-app.mp4)
- [进行中窗口信息](build/v062b5-final/held-gesture-windows.txt)
- [手势前窗口信息](build/v062b5-final/before-gesture-windows.txt)
- [手势后窗口信息](build/v062b5-final/after-gesture-windows.txt)

旧B录屏也有同形弧面，但仅凭旧录屏的操作段名称不足以判断来源。本轮通过当前真机窗口归属及按住时的实际截图完成定位。

## 实际构建与安装

普通包为[EatRecord-0.6.2-beta.5-debug.apk](dist/EatRecord-0.6.2-beta.5-debug.apk)，code198，121212017 bytes，min26/target37。

SHA256为`641e9b0fa3d0dae053dfc769875e38c3ea6f4cc54395081154fe400707b00df2`。

- `build.ps1 -NoUpload`成功，日志为[build.log](build/v062b5-final/build.log)。保留Beta版本标记。
- 全部JVM通过，包含52937项默认B/A-B合同、61678项原容器、6219项设置、365项底栏及1160项根页检查，其他既有数据兼容回归通过。日志为[jvm.log](build/v062b5-final/jvm.log)。
- UpgradeSmoke仅使用本轮普通构建classes编译通过。新增默认B与无extra Intent检查，旧基线oracle显式选择Baseline后恢复默认B。未在日常手机执行写夹具runner。
- 验包通过，4个模型、双ARM共8库、6份运行库固定摘要、字体/许可及QA隔离通过。普通包无debuggable。
- v2/v3签名有效，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与原安装相同。
- 魅族21，461QYGFL22429，Android16/API36。安装前核对原code197 APK摘要，获授权后用普通包`install -r --no-streaming`保留数据覆盖。安装后冷启动未发送视觉selector，直接验证默认B。

## 真机与视觉回归

| 检查 | 实际结果 |
| --- | --- |
| 日间进入、顶栏返回、Back键返回 | 通过，终态可正常继续操作 |
| 真实右缘系统返回 | 通过，一次返回到其他设置 |
| 暗色进入、顶栏及Back键返回 | 通过，原纸面主题连贯 |
| 进入后立即返回、100ms后返回 | 通过 |
| 连点来源及连续Back的快速组合 | 三组通过，最终仍在其他设置，未见重复跳页 |
| 父列表非零滚动位置 | 前后来源文字bounds均为[254,605][940,669] |
| 恢复入口滚动0 | 来源文字bounds恢复[254,968][940,1032] |
| 动画倍率0 | 日间与暗色进入/返回安全归位，最终恢复原三项1.0 |
| 夜宵状态 | UI仍为原“关”，未操作开关 |
| 原普通震动设置入口与返回 | 保持一次返回到其他设置 |
| 根页我向右连续滑到迹 | 中途仍为相邻横向页面与底栏跟随，终态为足迹 |
| 今日/我点击及当前Tab重复点击 | 通过，当前Tab保持原行为 |
| 最终手机状态 | 日间、我页、三项系统动画倍率1.0 |

验收脚本曾误把今日页标题写成“今日”，实际标题为“一蔬一饭”。修正脚本oracle后补验通过，应用源码无需调整。测试期间短暂ADB断连，经用户重新连接后核对设备和原APK继续，无清空或卸载。

人工查看日间、暗色进入/返回解码帧及真实手势截图。未见新增闪白、目标内容漂移、终态父字或覆盖层残留。B既有80ms交接仍有短暂低对比纸面，末段接入较集中，保留用户选定的节奏。

| 媒体 | 文件 |
| --- | --- |
| 日间原速进入与两种返回 | [day-app.mp4](build/v062b5-final/day-app.mp4) |
| 暗色原速进入与两种返回 | [dark-app.mp4](build/v062b5-final/dark-app.mp4) |
| 日间解码总览 | [day-contact.jpg](build/v062b5-final/day-contact.jpg) |
| 暗色解码总览 | [dark-contact.jpg](build/v062b5-final/dark-contact.jpg) |

视频只裁顶部100px系统栏，保留App标题和底栏。日间182帧、暗色177帧、手势200帧，各自导出前后PTS逐帧差0，原速保持。摘要在[media.json](build/v062b5-final/media.json)，逐项状态在[regression.json](build/v062b5-final/regression.json)及[device-final.json](build/v062b5-final/device-final.json)。

暗色非录屏四组进入/返回采样327帧，deadline jank为27次、8.26%，p50/p90/p95/p99为8/21/29/46ms，slow bitmap uploads为0。仍有慢帧，见[gfx-dark.txt](build/v062b5-final/gfx-dark.txt)。本轮与之前日间采样条件不同，不能据此推断性能提升或退化，更不能宣称零卡顿。API26–28、大字体、RTL、分屏、TalkBack及精确内存峰值仍待专项验收。

## 本轮文件及保护

- UiTokens.java将默认值设为LOCAL_B。
- SettingsContainerTransform.java的独立控制器默认引用同一UiTokens常量。
- MainActivity.java的Intent默认回退值引用同一常量，原业务和导航代码继续使用。
- AndroidManifest.xml与ReleaseNotes.java更新beta.5/code198及选定B说明。
- tests/SettingsContainerExperimentTest.java补默认B/320ms检查。tests/android/UpgradeSmoke.java显式区分历史基线oracle与当前默认B，仅编译。
- CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md记录选定B及真实验收，历史A/B报告修正动效关闭开关说明。

根Tab、液态底栏、实际B几何/绘制代码、业务存储/同步/登录/备份及其他设置路由均保留原代码。本轮相对开始快照只有默认选择和版本相关生产改动。server/update.json、dist/update.json继续为0.6.1/code193，未提交、推送、发布或上传。

开始前41份dirty/untracked及保护文件和beta.4 APK已存入[before](build/v062b5-final/before)，摘要为[before-manifest.json](build/v062b5-final/before-manifest.json)。本轮补丁为[this-round.patch](build/v062b5-final/this-round.patch)，只验证反向适用性。最终源码、包和媒体检查见[final-audit.json](build/v062b5-final/final-audit.json)。

## 关闭与回退

- `UiTokens.SETTINGS_CONTAINER_MOTION_ENABLED=false`关闭餐次容器试点，入口和返回回退现有普通设置转场。
- 系统动画倍率0继续由原areAnimatorsEnabled检查直接落到已接受页面，释放视觉资源。
- 源码默认值可改回SettingsContainerMath.BASELINE，或用原meal_motion_variant=0临时选择旧基线。selector只驻留Activity，重建恢复当前源码默认B。
- 原SETTINGS_MOTION_ENABLED独立控制普通设置模式，根页和底栏各有原有开关。

开关与异常来源/尺寸/内存安全回退保留源码路径，本轮实际测试系统动画0，未另行安装关闭编译开关的专用APK。最终选择范围保持餐次管理试点，本阶段完成。

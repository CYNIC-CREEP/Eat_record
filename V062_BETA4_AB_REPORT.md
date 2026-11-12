# 0.6.2-beta.4 餐次管理容器A/B试验

2026-11-06，Windows PowerShell、Java动态View，ADB魅族21普通包。

## 交付状态

仅“其他设置→餐次管理”完成两套可切换视觉试验。保留beta.3已验证上下同步版为Baseline，源码默认和最终手机模式均为Baseline，等待用户选择。

普通包为[EatRecord-0.6.2-beta.4-debug.apk](dist/EatRecord-0.6.2-beta.4-debug.apk)，code197，121212017 bytes，SHA256为`55d94f2467216437fe16fbcc2beaaaca7678b2fcc377f7066159c560cb75ea6c`。用户已授权同证书普通包保留数据安装及无损UI检查，安装测试沿用此授权。

线上server/dist更新清单仍为0.6.1/code193。当前main与本地origin/main仍为8cc26c5，先前dirty/untracked保留。本轮未执行提交、标签、推送、发布或上传。

## 基线诊断

原480ms时钟以Bezier(.25,0,.25,1)生成e。上下边界共同smooth(e/.62)，左右按e。在e达到.62时上下已全高，容器会较早形成大面积纸面。

目标内容必须完整进入圆角裁剪安全区域后才能显示。原来源、标题与正文又由共同identity分成前40%和后60%交接。在稀疏的单开关目标页，几何扩张明显早于清楚内容接入，造成中段大纸面和后段集中显现。

本轮保留该分支用于随时对照。进入的父列表快照固定，目标纹理在最终坐标绘制，返回仍重测已恢复滚动的真实来源行。

## A 完整展开节奏

设u为进入归一化时钟，e为原Bezier结果，smooth(x)=clamp(x)²×(3−2×clamp(x))。

| 通道 | 实际采用 |
| --- | --- |
| 总时长 | 480ms，沿用原Bezier(.25,0,.25,1) |
| 四边 | 全部共同smooth(e/.90)，上下同步，左右保持相同归一化扩张比例 |
| 圆角 | 原r×(1−e²)，中断从当前真实圆角续接 |
| 来源内容 | 1−smooth((u−.24)/.20)，进入约115.2–211.2ms退出 |
| 目标标题和正文 | clamp((u−.44)/.36)乘二者较小的完整区域安全显现值 |
| 目标显现名义区间 | 进入约211.2–384ms，实际起点受圆角和完整文字安全区域限制 |
| 目标布局 | 位置(0,0)，无整页缩放、平移 |

clockProgress从e恢复同一Bezier时钟，视觉通道没有另开延迟任务。返回使用现有反向e和同一组几何函数，因此上述毫秒区间专指完整进入。重新测得的终点、反向续接和饱和边重定向继续复用原会话机制。

实帧中A的标题与正文同步，但50%附近仍有大面积低对比纸面。完整安全裁剪限制了新内容提前显现的幅度，本轮未继续堆加标题副本或覆盖层。

## B 轻量局部展开

| 通道 | 实际采用 |
| --- | --- |
| 总时长 | 320ms，线性u作为唯一会话时钟 |
| 局部几何 | 前240ms，Bezier(.25,0,.25,1)将来源卡片扩到localEnd |
| 增长范围 | 左右各8dp，上下各24dp，总高度增加48dp，按实际可用空间对称限幅 |
| 圆角 | 保留来源圆角，中断从当前形状续接 |
| 内容交接 | 后80ms，旧场景240–272ms退出，目标272–320ms接入 |
| 旧场景 | 固定父列表、局部扩张卡片及原字号来源行作为整体交接 |
| 目标场景 | 已接受目标页的原位纹理及原纸面背景，标题正文共同alpha |

localEnd只服务局部几何，end始终为完整viewport。目标最终页面不会被限制在小卡片里。反向先完成80ms交接，再将局部卡片缩回重新测量的真实来源项。中断复用原纹理、来源绘制Rect和元数据。

B使用原有三张短期纹理和同一Layer，alpha交接时临时Canvas图层按viewport有界。真实业务只render一次，输入、焦点及无障碍继续沿原会话门控。80ms交接包含短暂的近纸面阶段，实帧中较短，仍能感到目标页较快接入。

## 同条件真机证据

- MEIZU_21，461QYGFL22429，Android16/API36，1080×2340。两版均日间原黄色主题，三项动画倍率1.0，同一普通APK、同一字体、入口滚动0。
- A/B来源文字bounds均为[254,968][940,1032]。完整行来源几何由实际布局测得，并非使用文字bounds当作startRect。
- 每段原始录屏8秒，顺序为进入、顶栏返回、再次进入、系统返回。录制前检查应用前台及通知遮挡，拒绝有顶部横幅的输入段。
- 导出视频仅裁顶部100px系统状态栏，保留App标题和底栏。A254帧、B218帧，导出与原视频帧数相同，PTS最大差0，保持原速。全系统栏原始媒体分析完成后删除，仅保留摘要。

A-app SHA256为`7dceaf4c10cadc01b822eaf9648f9520783ecbf21f888dc5f3e0c0a10edd5900`，B-app为`d3ecf06cba47783298c4080996ca2905d9e808c543bb9066c1092456add76a86`。原始媒体摘要和清理明细见[cleanup.json](build/v062b4-ab/cleanup.json)。

最终证据目录为[build/v062b4-ab](build/v062b4-ab)。

| 用途 | 文件 |
| --- | --- |
| A完整操作 | [A-app.mp4](build/v062b4-ab/A-app.mp4) |
| B完整操作 | [B-app.mp4](build/v062b4-ab/B-app.mp4) |
| 进入25%/50%/75%，上A下B | [AB-push-25-50-75.jpg](build/v062b4-ab/AB-push-25-50-75.jpg) |
| 系统返回25%/50%/75%，上A下B | [AB-system-pop-25-50-75.jpg](build/v062b4-ab/AB-system-pop-25-50-75.jpg) |
| A进入六帧 | [A-push-keyframes.jpg](build/v062b4-ab/A-push-keyframes.jpg) |
| B进入六帧 | [B-push-keyframes.jpg](build/v062b4-ab/B-push-keyframes.jpg) |
| A顶栏返回六帧 | [A-top-pop-keyframes.jpg](build/v062b4-ab/A-top-pop-keyframes.jpg) |
| B顶栏返回六帧 | [B-top-pop-keyframes.jpg](build/v062b4-ab/B-top-pop-keyframes.jpg) |
| A系统返回六帧 | [A-system-pop-keyframes.jpg](build/v062b4-ab/A-system-pop-keyframes.jpg) |
| B系统返回六帧 | [B-system-pop-keyframes.jpg](build/v062b4-ab/B-system-pop-keyframes.jpg) |

六帧均为约0%、10%、25%、50%、75%、100%。时刻由实际VFR录屏PTS及代码几何/局部像素拟合估计。A由被遮挡父列表文字估时，B由局部卡片上方间隙估时，B起点可能偏2–3帧。百分比为近似视觉阶段，未采集Animator/vsync遥测。

## 逐帧结果

| 观察项 | A | B |
| --- | --- | --- |
| 进入25%/50%/75%模型轮廓占App viewport | 约40%/97%/100% | 局部卡片约10%/13%/13% |
| 上述轮廓内低信息纸面占App viewport | 约39%/94%/94% | 约9%/10%/12% |
| 来源或目标文字均不清楚的最长进入窗口 | 两次约142–150ms | 两次均约41ms |
| 系统返回对应最长窗口 | 约93ms | 约33ms |
| 首次清楚目标正文 | 约0.30秒 | 约0.30秒 |
| 新正文清楚且旧标题仍清楚 | 两次进入、两次返回均0帧 | 两次进入、两次返回均0帧 |
| 目标标题/正文位置 | 固定原生Y112/Y536 | 固定原生Y112/Y536 |

低信息纸面采用1/4尺寸实际解码像素，暖浅色阈值及邻域梯度估计，覆盖范围采用代码与视频重建的圆角几何。B数据只量局部卡片，80ms整页交接背景未算作该卡片面积。目标页终态本身内容很少，因此该指标不能单独作为体验评分。

“文字清楚”代理需原生模板NCC及至少50%的模板投影对比度，连续窗口按实际PTS计算。它只描述局部来源标题与目标正文可辨认程度，不能等同整屏语义空白。B时钟拟合较粗，两版首次目标清楚均报告约0.30秒，不能据约20ms估计差宣称更快。

人工检查两张并排图及各方向六帧后，A的全页生长关系更完整，但巨大纸面阶段仍存在。B在此前始终保留来源与父列表参照，局部扩张明显、目标接入更克制，较适合此单开关页面。B的末段80ms整体交接观感较快，选择仍由用户决定。

父列表边缘被容器覆盖时会有正常几何遮挡，A仍会截到相邻行，未发现闪烁或终态旧字残留。两版初态底栏轮廓下缘已经可见“恢复出厂”等父列表文字，A约75%消失，B随旧场景退出，100%目标页均消失。源码为content填满父容器、navHost叠于其上、pageScroll允许padding外绘制，这属于原设置父列表与浮动底栏的布局关系。本轮没有改动它，不能把它描述成host外泄漏。专门对照见[AB-bottom-initial-to-final.png](build/v062b4-ab/AB-bottom-initial-to-final.png)。

详细离线数据为[A-visual-analysis.json](build/v062b4-ab/A-visual-analysis.json)、[B-visual-analysis.json](build/v062b4-ab/B-visual-analysis.json)及[blank-and-identity-metrics.json](build/v062b4-ab/blank-and-identity-metrics.json)。

## 构建与回归

- `git diff --check`通过，完整`scripts/test-food-analysis.ps1`通过。新增52935项A/B检查、原61678项容器、6219项普通设置、365项底栏及1160项根页检查通过，其余已有JVM检查通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload`成功。仅有现有Java8目标/弃用API与新版JDK原生访问警告。
- `scripts/verify-food-apk.py`通过。4个模型、双ARM共8个原生库、6份固定运行库摘要、依赖闭包、字体/许可及QA隔离通过。普通包无debuggable，min26/target37。
- apksigner核对v2/v3有效，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与原安装相同。
- Android UpgradeSmoke新增selector、A/B几何、单次render、纹理续接、旧clock、输入门控、清理及动画0断言，仅编译通过。日常手机只运行普通包，未运行会写夹具的QA runner。
- A/B顶栏与系统返回、立即/100ms快速返回通过。滚动前后来源bounds在各自测试内完全一致，A为[254,642][940,706]，B为[254,641][940,705]。
- A/B/Baseline动画0安全归位通过，随后三项倍率恢复1.0。普通记录本设置单次返回、迹/我点击、我向右滑至迹通过，最终恢复日间、我页、Baseline。
- 本轮未主动更改餐次开关、日记、记录或账户。首次升级显示正常版本公告，点击“我已了解”，应用按原逻辑更新已读版本元数据。
- 收尾只读核查确认手机安装base.apk摘要与交付APK完全相同，code197及三项倍率1.0。此时App已不在前台，补充selector调用按保护规则拒绝，未执行坐标输入。日间/我页/Baseline恢复记录来自此前已完成的device-final.json及成功切换。系统crash缓冲中未匹配到本应用进程崩溃记录，已删除全量缓冲，只留检查结果。

非录屏下每版四组进入/返回的gfx采样如下。

| 项目 | A | B |
| --- | --- | --- |
| 帧数 | 482 | 340 |
| deadline jank | 26，5.39% | 15，4.41% |
| p50/p90/p95/p99 | 10/18/21/31ms | 7/16/20/31ms |
| Missed Vsync | 7 | 0 |
| Slow bitmap uploads | 0 | 0 |

两版总时长及采样帧数不同，以上为单机单轮观察，仍有慢帧。没有足够证据宣称无性能回归、稳定提升或零卡顿。状态、返回与根页专项未发现回归。新A/B暗色、大字、RTL、API26–28、分屏、TalkBack及精确内存峰值尚未专项实测，beta.3历史证据保留为历史。

## 实际修改文件

| 文件 | 本轮内容 |
| --- | --- |
| src/com/eatrecord/app/SettingsContainerMath.java | 0/1/2模式、A曲线与alpha、B局部时钟和交接函数 |
| src/com/eatrecord/app/SettingsContainerTransform.java | Session固定模式、localEnd、A/B绘制分支，沿原反向和释放路径 |
| src/com/eatrecord/app/MainActivity.java | 单次视觉Intent selector及向原PageCrossfade传参 |
| src/com/eatrecord/app/PageCrossfade.java | 视觉模式委托setter |
| src/com/eatrecord/app/UiTokens.java | 默认Baseline常量 |
| AndroidManifest.xml、ReleaseNotes.java | beta.4/code197及实验说明 |
| tests/SettingsContainerExperimentTest.java | 新增纯数学A/B回归 |
| scripts/test-food-analysis.ps1 | 接入新增测试 |
| tests/android/UpgradeSmoke.java | 隔离QA断言，仅编译 |
| CURRENT_TASK.md、PROJECT_CONTEXT.md、DECISIONS.md、RELEASE_PROCESS.md | 更新真实交接状态和实验边界 |
| V062_BETA4_AB_REPORT.md | 本报告 |

原根/底栏实现、V061_DELIVERY_REPORT及server/dist清单共12份文件摘要与修改前一致。MainActivity剔除本轮视觉适配行后与之前相同，PageCrossfade仅增selector、UiTokens仅增默认值。[protection-check.json](build/v062b4-ab/protection-check.json)保留检查结果。之前Git diff中的其他修改继续保留。

修改前全部脏源码和文档、beta.3 APK保存在[before](build/v062b4-ab/before)，[before-manifest.json](build/v062b4-ab/before-manifest.json)含摘要。本轮从该快照生成[this-round.patch](build/v062b4-ab/this-round.patch)，只执行`git apply --reverse --check`验证，未应用。旧code196 APK仅留作证据，手机当前code197内切换视觉即可比较。

## 独立切换

先将App留在前台，建议停在“其他设置”入口静止处。在项目根目录运行已测试的辅助脚本，Variant取A、B或Baseline。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\v062b4-ab\select-variant.ps1 -Variant A -Serial 461QYGFL22429
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\v062b4-ab\select-variant.ps1 -Variant B -Serial 461QYGFL22429
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\v062b4-ab\select-variant.ps1 -Variant Baseline -Serial 461QYGFL22429
```

脚本检查当前前台Activity及版本，发送`meal_motion_variant`一次性extra，0/1/2对应上述三种视觉，切换不执行导航。extra立即消费，值只驻留当前Activity，本报告对应beta.4的Activity重建/进程重启恢复默认Baseline。UiTokens.SETTINGS_CONTAINER_MOTION_ENABLED=false可在源码关闭餐次容器试点并回退普通设置转场，SETTINGS_MOTION_ENABLED独立控制普通设置模式，原业务目标继续由原owner提交。后续beta.5已按用户选择将B设为默认，见V062_BETA5_FINAL_REPORT.md。

本轮比较完成后停止，等待用户选择最终视觉。

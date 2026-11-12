# 0.6.2-beta.7 夜间设置卡片颜色一致性

2026-11-11。首次进入“其他设置”、子页返回和重新进入均已显示原暖暗色。普通包已保留数据安装到魅族21。

## 原因与修改

“我”页、首次“其他设置”及餐次返回使用同一设置工厂和UiTokens.card。原首次进入路径在新根View仍为alpha0、尚未正常窗口绘制时调用buildLayer，偏灰结果进入了硬件缓存。餐次返回已有一次正常绘制准备，因此显示正确颜色。

PageCrossfade的普通settings路径现先在旧页不透明覆盖下，让已接受的新View正常绘制一帧。随后通过一次帧回调建立原硬件缓存、恢复进度0并启动原动画。该阶段由同一Slide持有，输入及无障碍门控继续生效，清理会撤销监听与回调，旧任务检查对象身份。

直接取消incoming缓存的诊断分支已验证颜色正确，但出现较高绘制开销。最终实现采用延后建缓存，原缓存机制继续承担移动阶段的绘制。设置工厂、主题色值、餐次A参数及业务render均复用原实现。

## 真机颜色结果

设备为MEIZU_21，461QYGFL22429，Android16/API36，1080×2340，当前黄色主题，动画倍率1.0。先采原生窗口PNG，再分析像素，采样前未进行软件View绘制预热。

| 场景 | 修复前RGB | 最终RGB |
| --- | --- | --- |
| 我页选项 | 43/38/34 | 43/38/34 |
| 首次进入其他设置 | 68/65/63 | 43/38/34 |
| 餐次子页返回 | 43/38/34 | 43/38/34 |
| 冷启动后首次进入 | 曾偏灰 | 43/38/34 |
| 两次日→夜循环后进入 | 曾不一致 | 43/38/34 |
| 动画0的首次进入与返回 | 本轮新增检查 | 43/38/34 |

暖暗色对应原主题`#2B2622`。日间两次循环均为原卡片色255/253/247。最终11张截图的采样值与各自主题预期一致，数据见[color-final.json](build/v062b7-night/color-final.json)。

- [修复前后对照，左前右后](build/v062b7-night/before-after.png)
- [最终首次进入](build/v062b7-night/final-other-first.png)
- [最终我页卡片](build/v062b7-night/final-me-card.png)
- [冷启动首次进入](build/v062b7-night/after-cold-first.png)
- [餐次返回](build/v062b7-night/after-top-return.png)
- [普通子页返回](build/v062b7-night/after-ordinary-return.png)
- [夜间餐次进退录像](build/v062b7-night/night-app.mp4)

录像只裁顶部100px系统栏，249帧，导出前后逐帧PTS差0，保持原速。摘要为[media-final.json](build/v062b7-night/media-final.json)。

## 回归与构建

- 冷启动、两轮日夜切换、A顶栏返回、普通子页系统返回、快速A进退、动画0及根Tab点击/横向跟手通过。
- 滚动位置前后来源文字bounds均为[254,604][940,668]，餐次开关保持原状态。
- 设备恢复原夜间模式、我页、三项动画倍率1.0，临时USB保持亮屏设置恢复原值0。
- `build.ps1 -NoUpload`成功，完整JVM通过。Android QA增加原生截图优先的Me/首次Other/Meal返回颜色检查，以及准备态覆盖、缓存时机、旧回调隔离断言，仅编译。
- 本机默认Python已指向另一ABI的Conda，构建临时使用原要求的Python3.11后完成模型缓存验证，构建脚本继续使用原版本。
- 验包通过，模型4个、双ARM共8库、固定运行库摘要6份、字体/许可及QA隔离有效，普通包无debuggable。
- v2/v3原证书有效，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。

产物为[EatRecord-0.6.2-beta.7-debug.apk](dist/EatRecord-0.6.2-beta.7-debug.apk)，code200，121216113 bytes，min26/target37，SHA256为`b328ef95815089aaf6b0259096797d8613453aca62efa4b9e6c63289a62d07ba`。

非录屏四组普通设置往返采样446帧，deadline jank为33次、7.40%，p50/p90/p95/p99为9/21/53/101ms。该单轮采样仍有慢帧，日志为[gfx-final.txt](build/v062b7-night/gfx-final.txt)。直接取消缓存的诊断采样为19.15%，两次帧数和运行状态不同，保留为本机观察。

## 交接与范围

生产修改为PageCrossfade的首次绘制与建缓存时序、Manifest/ReleaseNotes的beta.7/code200。测试修改仅UpgradeSmoke。餐次A、底栏、根Tab及业务源码的本轮前摘要校验见[protection-final.json](build/v062b7-night/protection-final.json)。

本轮开始前43份文件与beta.6普通APK保存在[before](build/v062b7-night/before)。修改补丁为[this-round.patch](build/v062b7-night/this-round.patch)，只检查反向适用性，最终检查为[final-audit.json](build/v062b7-night/final-audit.json)。

原设置动效关闭开关和异常归位路径继续可用。当前main及本地origin/main为8cc26c5，正式更新清单仍为0.6.1/code193，交付保持本地Beta及未提交工作区。

全部UI检查和设置恢复完成后USB断开，最后一次APK读回及手机临时XML/录屏清理留待下次连接；安装成功、原生截图及恢复记录已保留。

当前真机验证覆盖所用主题的夜间/日间。旧系统、其它品牌及所有字体/窗口组合仍待专项验证。beta.6报告中源页面初态卡片色差已由本轮定位并修复。

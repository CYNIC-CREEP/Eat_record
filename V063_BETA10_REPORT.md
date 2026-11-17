# 0.6.3-beta.10 修改与验证

2026-11-17，Asia/Shanghai。code212，main／HEAD2434aae，正式发布基线0.6.2/code202。按用户要求交付Beta供下一轮安装检查。

## 交付

- [普通APK](dist/EatRecord-0.6.3-beta.10-debug.apk)，127244701 bytes，121.350 MiB。
- SHA256为`53d68c4591ff12faba7756e18b8eb0dab6f67ae2466a4b5227603d79bfb1b1ab`。
- 原证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。最低SDK26、target37、双ARM、两个DEX，普通包无debuggable与QA runner。详见[验包核对](build/v063b10/delivery.json)。

## 图标与记录本

- 添加主图标改为左侧简笔、右侧四角星。简笔仅保留外轮廓，子菜单图标继续表达原功能。底栏反馈、横拖与弹簧参数沿Beta9。
- 去掉按最终书页圆角填充的实心阴影缓存，过渡改用跟随当前圆角的低透明外沿，源封面态外沿透明度为零，修复四角露灰。
- 点击来源封面先呈现抬起反馈，再由同一世代准备开本。已布局纸页在preDraw捕获，快速返回、切Tab及退出窗口取消过期准备。
- 逐帧普通包复核发现首次软件捕获会消费View背景尺寸更新，恢复旧空bounds会影响后续原生纸页。捕获后现保留原生View正确尺寸并刷新outline，新增原生背景bounds和圆角断言。
- 开本620ms、合本520ms，部分合本至少300ms，零进度同值请求立即结束。日期、收藏入口、内部翻页、媒体与日记字段继续沿原业务路径。

## 七项全局优化

1. 食物详情先显示图片区域，后台解码、描边及旋转。弹窗关闭／重开隔离迟到结果并回收图片。
2. 分享冻结单日文字、主题、字体、排版和媒体描述，后台绘图、压缩与MediaStore写入，单任务去重，离开页面取消。空流、编码失败、关闭／发布异常和取消进入本次条目清理。
3. 文字保存使用进程级串行队列和共享锁，合并最新日记字段，失败保留输入并可重试。覆盖commit(false)的同值再写、晚到提交后的最新快照、OOM失败回执及后台页面完成后延后UI动作。
4. 网格／横向书架仅装载可见内容及邻近缓冲，保留完整滚动范围。日期定位可实体化远处封面，返回使用真实锚点，无障碍顺序及横向RTL路径保留。
5. 日期与热量按精确单日JSON片段增量投影，未变化日期复用缓存，日期顺序与餐数／热量共享计算，受控就地修改显式失效。
6. 头像冷加载复用后台队列，文件版本缓存与请求合并，替换／删除及attach世代校验，显示中的Bitmap引用保全。
7. 字体常规／粗体对象复用，普通资料与字号修改保留字形缓存。文件事件使选中字体失效，字号独立更新。

头像与字体同时监听filesDir曾使字体父目录事件丢失。现由DirectoryWatch按规范化目录共用原生observer，锁外分发回调，最后订阅关闭才停止，字体子目录替换后重挂。诊断依据为AOSP按watch descriptor登记单一回调的实现，已由两客户端同时活跃的缓存场景复测。[AOSP Java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/FileObserver.java) · [AOSP JNI](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_util_FileObserver.cpp)

逐项实现与证据见[全局优化实施清单](PERFORMANCE_OPTIMIZATION_PLAN.md)。

## 验证

### 构建与纯JVM

- [普通构建](build/v063b10/build.log)、[QA构建](build/v063b10/qa-build.log)均使用-NoUpload并通过，git diff --check通过。
- [JVM回归](build/v063b10/jvm.log)通过，新增13项增量索引、22项文字事务及50项分享发布／故障清理／取消检查。现有餐次、导航、隐私与备份等检查继续通过。
- [验包](build/v063b10/apk.json)通过四模型、默认字体、许可、十个原生库、固定摘要、双ARM依赖闭包、包体预算与QA隔离。
- 普通构建前后Java／Kotlin源码摘要一致，两份Kotlin编译输入匹配，原签名文件及Beta9 APK摘要保全。

### 独立模拟器专项

设备EatRecordComposeV063QA／emulator-5588，API36.1、1080×2340、density420、软件图形渲染。所有数据与故障均为独立设备合成夹具，HTTP(S)请求数0。

- [保存](build/v063b10/qa-saving-final.log)通过锁阻塞下UI回调、最新字段合并、提交失败重试、内联返回保存和手写取消。额外检查晚到提交、模拟OOM及停止页面后的回执。
- [详情](build/v063b10/qa-detail-final.log)阻塞解码队列时先显示窗口，UI继续回调，关闭后重开仅当前窗口接收图片。
- [缓存](build/v063b10/qa-caches-final.log)通过字形对象复用、字号比例、真实字体文件替换／删除，头像冷／热／替换／删除／detach及重挂。原文件字节、mtime和资料在finally恢复。字体夹具为内置合法字体复制到下载文件名。
- [分享](build/v063b10/qa-share-final.log)通过真实1640×2000 PNG发布可读性、单任务去重、UI回调、借用Bitmap所有权与退出取消。本次新增2个MediaStore条目已由夹具清理。系统分享面板由ActivityMonitor拦截，测试没有选择外部接收应用。
- [日间](build/v063b10/qa-books-day-final.log)与[夜间](build/v063b10/qa-books-night-final.log)1460天书架通过头／中／尾滚动、最旧日期物理打开及返回。网格最多13个行／分节View，横向最多10本，日期索引完整，原生纸页背景与圆角通过断言。
- 返回保留安全区域内的入场滚动。来源被浮动底栏遮挡时，沿既有规则微调滚动使真实封面完整露出，相关数值保存在[书架记录](build/v063b10/evidence/v063b10-books-night.json)。
- [动画回归](build/v063b10/qa-motion-regression-final.log)通过短按完整反馈、中间回落、长按横拖／取消／反向、菜单离开及无障碍，覆盖完整／半途／重复返回、收藏与无锚点路径，纹理复用及清理通过。
- 截图采样坐标在取帧前观察，四角裁剪用于视觉复核。最终观感另由普通包录屏检查，按实际绘制时点采样闭合进度。

### 普通包

普通包已复核[导航与菜单](build/v063b10/navigation-final.log)、[日间开合](build/v063b10/normal-final/book-day.mp4)、[夜间开合](build/v063b10/normal-final/book-night.mp4)，以及中断／重复返回、切今日、四次日夜切换和系统关闭动画路径。最终截图与设置记录位于build/v063b10/normal-final及device-settings-after.json。

## 验收边界

本轮覆盖独立模拟器，魅族21实际手感和个性化字体／纸张组合待用户下一轮安装检查。软件渲染仍可出现长帧，呈现时钟会延长实际动画总时长；本轮没有手机帧率提升数值。日记继续使用原有JSON存储，后台保存读取最新整库再合并目标字段。

工作区及交接文件已更新，正式渠道清单保持0.6.2/code202。最终版与上传按用户下一轮验收及明确发布指令执行。

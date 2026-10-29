# 0.6.0-beta.28 三个主页面连续切换

日期：2026-10-26。版本0.6.0-beta.28/code176；持续Beta，不正式发布。用户本轮明确指今日／迹／我的整页滑动与底栏点击，保持不使用ADB。

## 已确认原因

1. 旧根导航依靠from/to pair及commit标志，只有上一弹簧commit且同向才advance。回退中再滑会继续抓旧pair，不断打断其onEnd而进不了第三页。
2. 第一个显著MOVE调用showTab→renderCurrent，同步构建目标页；迹展开会遍历全部记录创建封面。美食框则已保有三个真实页。
3. 旧PageCrossfade首个preDraw同步buildLayer，中断点击会截取整屏。beta.27延后点击仅让按压先呈现，没有消除后续页面重建成本。

## 最终实现

- RootPagePager固定三个真实attached根View，RootPageMotion管理连续0..2位置、目标、反向、取消和边界。根缓存切换不调用PageCrossfade，不在MOVE／松手／缓存页点击重建整页或截图。
- 根页工厂只构建UI，路由flags仍由Main管理；底栏图标和文字复用。点击立即重定向，从当前位置开始，0.48秒响应弹簧；手势为与今日餐纸相同的0.4秒。弹簧以像素计算，模型限位；到目标时取消残余动量，避免画面到位仍被动画锁住。
- 页面位置和底栏背景共享同一position；背景宽度插值用scale，避免移动帧setLayoutParams。保留beta.27首次真实测量定位。
- Today餐纸、日历、月份、周条有明确root owner；隐藏时暂停而不detach，防餐图回收重解码与迟到日期回调。回到今日续原目标。根回调带世代和实例校验，构造早回调不改当前路由。
- prefs日记／资料、头像／字体释放、跨午夜等标脏，输入、日期动画、弹窗和子页转场期间延后刷新。后台只标脏不轮询；内存压力释放根缓存，前台恢复选定页、滚动和待完成日期。提醒跳今日不复用旧历史页。
- 过渡中的根内容不激活旧页按钮，根手势和外部底栏仍可重抓／反向／重定向；多指及异常终态清理。保留记录本内部及媒体／模型行为。

## 验证与边界

- JVM4390项通过：既有3260项＋1130项新根导航序列。覆盖未归位时连续两页、回退途中进第三页、交替、点击中断、边界恢复、末MOVE缺失、取消及过时tick／超冲。日志build/beta28-final-jvm.log。
- 完整生产源码＋Android QA runner编译通过；revision-v28已新增90日夹具、同一pager／三页身份、逐帧位移和底栏、真实窗口输入、隐藏日期owner及日记不变断言。**只编译，未运行设备。**
- 静态复审修复旧pager回调、首次隐藏owner、异常终态吞流、动画到位锁定及后台失效轮询问题，未发现新的确定阻塞。
- 普通NoUpload构建、verify-food-apk.py通过；code176、minSDK26/target37、既有证书v2/v3通过，模型／字体／12固定库摘要、双ARM／16库依赖及许可齐全；普通包无QA runner／夹具、临时日志及debuggable。
- 产物：`dist/EatRecord-0.6.0-beta.28-debug.apk`，123105398字节（117.402MiB）；SHA256：`7f091f393e383a72b6294a9199a4485a8e76debb4d736bceb152cf887c0fc84c`。
- 构建／验包／完整QA编译：build/beta28-final-build.log、beta28-final-verify.log、beta28-full-qa-javac.log；Manifest／证书：beta28-final-badging.txt、beta28-final-manifest-tree.txt、beta28-final-cert.txt。源摘要见beta28-final-source-hashes.json。

页面准备成本前移到初始化／失效重建，三个View及其媒体会增加常驻内存。此轮不含手机冷启动、帧率、触摸延迟或实际动画观感验收；不能据纯状态测试宣称不卡顿。没有ADB、提交、标签、push、远端上传或正式发布。

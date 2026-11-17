# 0.6.3-beta.8 修改与验证

2026-11-15，Asia/Shanghai。版本code210，基于既有Beta7工作区继续修改。main／HEAD2434aae，正式发布基线0.6.2/code202。

## 交付

- [普通APK](dist/EatRecord-0.6.3-beta.8-debug.apk)，127232413 bytes，121.338 MiB。
- SHA256为`c1e00f9be69225d5037c46eb96452412e1d697281d725e83e4c89a50895be64b`。
- 原证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。普通包无debuggable、无QA runner，最低SDK26、target37，双ARM及两个DEX。详见[交付核对](build/v063b8/delivery.json)。

## 修改

- 原底栏短按UP过早撤销上升，越过单Tab边界会丢失物理手势。本版原生DOWN锁存完整膨胀，短按UP提交一次动作，上升完成后回落，长按维持。新输入从当前值和速度续接。
- 胶囊持续拥有横拖，按当前显示位置及触摸增量移动选择面，允许中途反向，UP提交最近Tab，CANCEL恢复原目标。
- 添加主按钮最大缩放1.24，独立于左侧膨胀。上升／回落为临界阻尼弹簧，刚度420／246.74011。三张软阴影按主题缓存，帧内复用几何和绘制对象，减少逐帧BlurMaskFilter及临时分配。
- 新增ComposeBookTransition，以Animatable、共享矩形、圆角和GPU封面转轴取代Java分段曲面。设计采用轻透视转轴及纸背，完整开本620ms、合本520ms，部分合本至少300ms。半途返回保留当前矩形、圆角和开合阶段。
- Java BookOpenView继续拥有日期、收藏来源、原生页面捕获、锚点及清理。纹理按Bitmap身份复用，首次真实窗口绘制后捕获，返回提前提交实际书架并定位目标封面。独立Compose生命周期与requestId隔离旧回调，先释放Compose再回收纹理。
- 旧BookCoverGeometry及其JVM测试退役，原文件摘要核对后保全于build/v063b8/retired-source。当前Android QA采用Compose宿主及真实OnDraw样本。
- 移除图片添加星光右上角圆点，以及智能助手星光左下角圆点、右上角加号。

## 构建与验证

- git diff --check通过。[JVM回归](build/v063b8/jvm.log)通过餐次、任务路由、弹簧、帧时钟、导航、隐私及备份等现有入口。
- [普通构建](build/v063b8/build.log)与[QA构建](build/v063b8/qa-build.log)均使用build.ps1 -NoUpload并通过。[验包](build/v063b8/apk.json)通过四模型、默认字体、许可、十个原生库、固定摘要、双ARM依赖闭包、300MiB预算及QA隔离检查。
- 两份Kotlin源码摘要匹配普通构建清单，Java／Kotlin源码早于该构建产物。原签名文件和Beta7 APK摘要保全。

### 独立模拟器专项

设备为EatRecordComposeV063QA／emulator-5588，API36.1、1080×2340、density420，软件图形渲染。仅独立QA包使用合成双餐夹具。

- [专项日志](build/v063b8/qa.log)通过revision-v063b8，日记保持一致，HTTP(S)请求数0。
- [短按反馈](build/v063b8/qa-evidence/v063b8-short-feedback.json)两次真实按住均为42ms，松手后峰值均达到1，主按钮直径147→181px，约1.231倍。模拟器释放至峰值观测953／392ms，反映该环境实际绘制节奏。
- [横拖](build/v063b8/qa-evidence/v063b8-drag.json)跟随0.35→1.15→1.8→0.7位置，UP单次提交，CANCEL恢复原Tab，快速反向归最后目标。
- [菜单](build/v063b8/qa-evidence/v063b8-menu-departure.json)展开中离开今日后，隐藏动作命中穿透、移除语义，返回后的无障碍业务操作通过。[装饰像素](build/v063b8/qa-evidence/v063b8-adornments.json)确认三处附加标记消失。
- [记录本](build/v063b8/qa-evidence/v063b8-compose-books.json)通过完整开合、半途返回及重复Back、收藏来源、缺失封面锚点四种路径，采到18／9／16／15个运动OnDraw样本，纹理身份保持，结束清理通过。

### 普通包复核

- 专项后装回同证书普通APK，实际版本210，QA runner已移除。[导航菜单冒烟](build/v063b8/navigation.log)通过，复核[快速切换与菜单录屏](build/v063b8/normal/navigation/interaction.mp4)。
- 复核[记录本开合与中途返回录屏](build/v063b8/normal/book-interaction.mp4)，及[打开](build/v063b8/normal/book-open-contact.png)、[关闭](build/v063b8/normal/book-close-contact.png)逐帧抽样，页面和书架正确衔接。
- [四次日夜切换](build/v063b8/normal/theme-contact.png)均正确回到对应底栏颜色。系统动画关闭时打开／返回可用，见[补充检查记录](build/v063b8/normal/extra-smoke.json)。本次crash缓冲为空。
- [设备设置](build/v063b8/device-settings-after.json)恢复至轮前值，字体1.0、窗口／转场1.0、animator倍率默认。普通包留在独立设备，模拟器正常关闭。

## 验收边界

本轮按用户要求使用模拟器复测，魅族21实际帧率与主观手感待安装验收。软件渲染环境仍存在长帧，呈现时钟每帧最多推进1/30秒，总动画时间可能延长。内部翻页中的返回继续先归最近真实页。当前实现衔接位置、圆角和开合阶段，手机上的回弹与转轴观感需用户复核。

项目记忆文档已更新到Beta8，线上正式清单仍为202。Git工作区保留既有未提交工作，后续最终版与发布按RELEASE_PROCESS.md执行。

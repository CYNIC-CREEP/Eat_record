# 0.6.3-beta.9 修改与验证

2026-11-15，Asia/Shanghai。code211，main／HEAD2434aae，正式发布基线0.6.2/code202。继续保护既有脏工作区。

## 交付

- [普通APK](dist/EatRecord-0.6.3-beta.9-debug.apk)，127228317 bytes，121.334 MiB。
- SHA256为`4aa2fb6baddf5e544a41e86900a0109ad55406f1d1e619fc737b99fe04ef5375`。
- 原证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。普通包无debuggable、无QA runner，最低SDK26、target37、双ARM／两个DEX。详见[产物核对](build/v063b9/delivery.json)。

## 本轮修改

### 底栏停留与切换

- 短按上升在压力达到0.86后保留当前速度进入回落，提前结束视觉已经到位后的数值收敛等待。长按继续到完整峰值并维持，上升／回落临界阻尼刚度420／246.74011，主按钮最大缩放1.24。
- 按压反馈使用独立有界帧时钟，每次最多推进1/30秒，导航准备阶段继续运行。14天网格测试曾抓到原始时钟在长帧中跳过回落中段，最终版修复后保留连续可见的回落。
- 按压和位置仅在draw读取，语义启用按阈值更新，菜单位移仅更新placement，presence尺寸变化才重新测量。复用命中矩形及静态图标路径。
- 长按横拖、反向、松手单次提交、CANCEL恢复原目标、菜单退出和日夜宿主恢复继续可用。
- 主按钮图标改为笔与四角星组合，复用两个子功能的视觉语言。见[组合图标](build/v063b9/qa-evidence/v063b9-main-combined-glyph.png)。

### 记录本绘制

- Compose Animatable继续管理进度，单Compose Canvas录制硬件绘制命令，取代多层透明／裁剪／动态阴影组合。
- 每帧原地计算一次姿态，复用Camera、矩阵、画笔与BitmapShader。封面按源分辨率捕获，纸页保留原尺寸，纹理提前prepareToDraw。
- 软阴影一次按1/4分辨率缓存，内存不足时安全降级。Composition释放及纹理回收继续按原所有权顺序处理。
- 开本620ms、合本520ms、可见部分返回至少300ms。打开进度为零的同值请求直接清理，修复恒值动画等待300ms期间拦截触摸的边界问题。
- 业务日期、存储格式、收藏来源、返回锚点和内部翻页继续沿用既有路径。

## 验证

- [JVM回归](build/v063b9/jvm.log)、git diff --check、[最终普通构建](build/v063b9/build.log)和[最终QA构建](build/v063b9/qa-build.log)通过，均使用-NoUpload。
- [验包](build/v063b9/apk.json)通过四模型、默认字体、许可、十个原生库、固定摘要、双ARM依赖闭包、300MiB预算及QA隔离检查。
- 普通构建前后的全部Java／Kotlin源码摘要一致，两份Kotlin编译输入摘要匹配。原签名文件与Beta8安装包摘要保全，正式更新清单保持202。

### 独立模拟器

设备EatRecordComposeV063QA／emulator-5588，API36.1、1080×2340、density420、软件图形渲染。QA使用14天网格书架及合成双餐夹具。

- [最终专项](build/v063b9/qa.log)通过revision-v063b9，日记保持一致，HTTP(S)请求数0。已核对设备安装的QA APK摘要与最终构建字节一致。
- [短按](build/v063b9/qa-evidence/v063b9-short-feedback.json)实际按住42／41ms，压力峰值0.915／0.888，压力≥0.8附近观测跨度112／102ms，回落采到5／6个中间帧。松手到峰值约931／204ms，首切网格的准备长帧影响前者。
- [长按](build/v063b9/qa-evidence/v063b9-held-feedback.json)保持到1，取消保留业务状态。[横拖／反向](build/v063b9/qa-evidence/v063b9-drag.json)、[菜单及无障碍](build/v063b9/qa-evidence/v063b9-menu-departure.json)、[装饰移除](build/v063b9/qa-evidence/v063b9-adornments.json)通过。
- [四种记录本路径](build/v063b9/qa-evidence/v063b9-compose-books.json)覆盖完整、半途及重复返回、收藏来源、缺失锚点，分别采到14／8／16／14个运动帧，最大相邻宽度变化0.144／0.233／0.145／0.173，纹理复用与结束清理通过。
- [零进度注入](build/v063b9/qa-evidence/v063b9-zero-progress-injection.json)在真实已准备的宿主调用0→0请求，同步释放overlay、触摸和纹理并恢复原纸页属性。此项为显式QA注入。
- [OnDraw／FrameMetrics诊断](build/v063b9/qa-evidence/v063b9-frame-diagnostics.json)保留实际样本。阶段中的主动等待与静止间隙需从运动分析中排除。

### 普通包

- 装回同证书普通APK后，[导航与菜单冒烟](build/v063b9/navigation.log)通过，已复核[录屏](build/v063b9/normal/navigation/interaction.mp4)。
- 复核[记录本录屏](build/v063b9/normal/book-interaction.mp4)与[逐帧抽样](build/v063b9/normal/book-contact.png)，覆盖打开、返回、半途重复返回及切今日。
- [四次日夜切换](build/v063b9/normal/theme-contact.png)颜色恢复正确，关闭系统动画时打开／返回可用，见[补充检查](build/v063b9/normal/extra-smoke.json)。
- [设备设置](build/v063b9/device-settings-after.json)恢复至轮前值，普通包装回，独立模拟器已关闭。

## 全局检查与剩余验收

[全局优化清单](PERFORMANCE_OPTIMIZATION_PLAN.md)包含7项，优先处理详情同步解码、分享同步绘制／写入及失败清理、文本同步保存，其次为书架分段加载、日期与热量索引、小头像缓存及字体缓存失效范围。分享失败路径存在遗留MediaStore未完成条目的源码缺口，建议正式版前优先处理。

模拟器软件渲染仍有长帧，整帧耗时改善和魅族21实际手感待安装复核。早期对照采样位于build/v063b9/baseline和final-benchmark，当时的按压时钟方案随后因扩展书架回落问题被修正。最终版验证以本报告关联的专项与普通包证据为准。

用户安装验收与正式版确认完成后，按RELEASE_PROCESS.md执行同版归档和发布。

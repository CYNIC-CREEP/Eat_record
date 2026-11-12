# 0.6.2-beta.3｜餐次管理容器展开试点纠正

日期：2026-11-04（Asia/Shanghai）。这是beta.3同轮需求纠正，仍code196；仅本地普通包，未发布。用户已确认首个360ms版本的动画方向正确；按最新反馈，最终改为480ms，等待对放慢版的实际观感反馈。不推广其它入口。

## 为什么上一轮不同

上一轮PageCrossfade的450ms模式只给两个整页施加0.25W横移及alpha，宽高一直不变，因此无法表达列表卡片变成页面。本试点取消该入口的Slide owner，改由同一PageCrossfade契约委托SettingsContainerTransform；其它普通设置仍450ms，特殊路径仍legacy。

## 实现与所有权

- 自定义Java View/Canvas，无Material依赖、无Gradle/Compose迁移。MainActivity只在openMealManagementPage(View)及closeMealManagementPage适配；复用runContentSwap的反馈/取消/单次render/scroll恢复，不建立第二返回栈，不在Animator结束再pop。
- 源：settingRow返回的整行LinearLayout，tag=meal_management_entry。不是文字。真实背景为PaperComponents.rounded的GradientDrawable，36dp半径并按行高一半安全钳制，颜色取该Drawable状态色。
- 目标：原renderMealManagementPage经installStickyHeader得到的整页FrameLayout，含ScrollView和原标题；结束区域为content宿主0,0,width,height。Insets和纸面坐标继续使用原root/content，不扩大布局、命中或导航区域。
- startRect使用source.transformMatrixToGlobal与host.transformMatrixToLocal映射，包含原按压0.985倍视觉缩放。endRect同一宿主坐标系。设备上push约(62.3,928.5,1017.7,1119.5)，pop新行约(55,927,1025,1121)，end=(0,0,1080,2340)。原36dp约99px，由真实行高钳制到约95.5/97px。
- 一个ValueAnimator统一驱动left/top/right/bottom/圆角。最终480ms，Bezier(.25,0,.25,1)按x反解；用户要求放慢取代初始360ms/300–400ms建议。无过冲，返回同路径反向。
- 原列表背景（暂时排除源行）、行本身和全尺寸新页各一次短期内存纹理。源行只随容器中心轻移，不向整页拉字；几何0..25%源行退出，30..75%正常字号新内容进入，使用smoothstep。全尺寸目标纹理只裁剪显现，不缩小整张子页。纸色由真实card色向主题paper色插值，原阴影仅绘容器外缘。
- pop先按原业务render父列表、恢复实际scroll，再在preDraw通过tag找到新行，校验attached/祖先/可见/非零/窗口内边界。不用上轮旧行或旧屏幕坐标；无安全锚点、分配失败、超预算直接显示已接受的真实目标页，不缩到错误位置。
- ready会话反向接用当前Rect/圆角/展开度及全尺寸子页纹理；尚未首帧准备完即反向则直接落真实目标页。身份检查与撤监听阻止旧clock/preDraw清新层。clear恢复alpha/焦点策略/无障碍，释放overlay与Bitmap。后台、detach、窗口尺寸、原配置及其它导航均接原PageCrossfade清理。系统返回仍是原确认回调，不接假预测进度。
- 视觉overlay不可点击/无语义；真实当前树在过渡期间禁触摸、焦点及无障碍，系统返回继续原owner。旧业务树不长期保留，也不重复render/订阅/网络请求。

## 放慢与帧稳定修正

360→480ms；曲线从(.2,0,0,1)变为(.25,0,.25,1)，让主要扩张不挤在最前段。Bitmap.prepareToDraw预热、透明内容跳绘、复用Path/Paint/RectF、去逐帧日志构造、阴影内部clipOutPath减少覆盖。起点Layer真正onDraw后，postOnAnimation在下一vsync启动一次时钟，布局/快照准备不会吃掉移动前段；没有永久循环或每帧新协程。

完整5组进退采样gfx-final.txt：618帧，18次平台deadline jank（2.91%），p50=8ms/p90=11ms/p95=16ms/p99=32ms，慢Bitmap上传=0。包含点击准备、布局、落位；不能宣称全链路零jank或全机型120fps。最终原速录像在push约0.90..1.38s的59帧、pop约2.45..2.93s的57帧中，PTS间隔中位数均8.256ms，最大分别9.245/9.722ms；人工查看未见运动中长停顿。这只是本次录像样本，不等于SurfaceFlinger完整帧率保证。原360ms样本和中间480ms样本保留，采样环境变化，不拿总jank比例微差宣称严格性能提升。

## 实际视觉证据

位于本机忽略目录build/v062b3-container：

- container-480-final.mp4：最终480ms日间进入/系统返回，原速未变速。
- container-dark-final.mp4：最终暗色进入/系统边缘返回，日间已恢复。
- before-final.png、push-keyframes.jpg、pop-keyframes.jpg及push/pop-000/010/025/050/075/100.png：真实录屏关键帧。下表以视频帧顺序和起点附近PTS估计时间比例，非精确帧同步；边界为实测锚点与同一几何模型结合画面估读，起点约一个刷新间隔误差，不冒充逐像素测量。完整JSON见keyframes-bounds.json。

|方向/约时间进度|实际录像PTS(s)|left,top,right,bottom(px，估读)|width×height|圆角(px，估读)|
|---|---|---|---|---|
|push 0%|0.900511|62,928,1018,1120|956×192|96|
|push 10%|0.947656|58,871,1022,1195|964×324|90|
|push 25%|1.020356|40,589,1040,1566|1000×977|61|
|push 50%|1.137089|13,201,1067,2076|1054×1875|21|
|push 75%|1.261667|3,41,1077,2287|1074×2246|4|
|push 100%|1.377633|0,0,1080,2340|1080×2340|0|
|pop 0%|2.449156|0,0,1080,2340|1080×2340|0|
|pop 10%|2.499811|3,58,1077,2264|1074×2206|6|
|pop 25%|2.565689|20,339,1060,1894|1040×1555|35|
|pop 50%|2.690489|43,726,1037,1385|994×659|76|
|pop 75%|2.806367|53,886,1027,1174|974×288|93|
|pop 100%|2.931367|55,927,1025,1121|970×194|97|

中间帧确有宽高同时改变，top向上/bottom向下推进；返回反向。相同浅纸色使左右扩张比高度扩张克制，几何边界可见；裁剪经过标题时可能短暂露出部分标题，这是当前轻量快照近似，未声称其它应用高保真复刻。

## 文件变更

|文件|本轮变更|
|---|---|
|src/com/eatrecord/app/SettingsContainerTransform.java|新增唯一试点绘制会话、测量/纹理/裁剪/释放|
|src/com/eatrecord/app/SettingsContainerMath.java|新增480ms几何及内容交接纯函数|
|src/com/eatrecord/app/PageCrossfade.java|委托owner并接active/clear/input，无第二Slide|
|src/com/eatrecord/app/MainActivity.java|只在餐次开关传真实整行，复用原导航事务|
|src/com/eatrecord/app/UiTokens.java|SETTINGS_CONTAINER_MOTION_ENABLED独立开关|
|src/com/eatrecord/app/ReleaseNotes.java|beta.3添加试点说明|
|tests/SettingsContainerMathTest.java|19756项几何/曲线/正反/非法输入|
|scripts/test-food-analysis.ps1|接入上述JVM类和测试|
|tests/android/UpgradeSmoke.java|旧450断言改测震动页，补试点owner断言；仅编译|
|CURRENT_TASK/PROJECT_CONTEXT/DECISIONS/RELEASE_PROCESS及本文|实际交接和试点范围|

## 构建/设备验收

- git diff --check通过。完整test-food-analysis.ps1通过：19756容器、6219旧设置、365底栏、1160根页及食物/隐私/备份等现有JVM回归。数学测试不替代视觉验收。
- build.ps1 -NoUpload普通构建通过；verify-food-apk.py、v2/v3原证书通过，4模型/双ARM8库/6固定运行库摘要及QA隔离正常。新增Android runner javac通过，未安装或运行QA夹具。
- 最终普通包dist/EatRecord-0.6.2-beta.3-debug.apk，121207921字节，SHA256 04a196b839f062a3d774f05165d34801b0f8d0395471bd0a2606eb1b6410a15a。包名com.eatrecord.app，min26/target37，无debuggable。证书7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 按既有授权仅ADB真机：魅族21/461QYGFL22429/Android16 API36，同证书保留数据覆盖。无模拟器、无清数据、无QA包、无夜宵开关/记录/配置表单编辑或API/同步/备份操作。
- 最终普通包通过标准adb input即时进入后Back、延迟120ms Back、重复点击后一次Back、顶栏返回、父行滚动前后bounds完全相同（文字642..706px）、后台恢复、系统动画0、暗色及系统边缘返回。系统三倍率恢复1.0，日间恢复。JSON见device-rapid-final/device-acceptance-final/device-dark-final。
- 自定义shell key injector的Back探针没有像标准adb input一样触发已确认返回，不计为应用通过；已改用标准input复测。根页Footprints某起点左滑探针未提交，不在本轮改其原手势仲裁、不以此声称完整根跟手重新验收；根与底栏源码摘要仍逐字一致，Tab点击实测另见root-device-final.json。
- 未做真实旋转/分屏/强制RTL/大字体/TalkBack全流程或独立QA runner执行。无应用崩溃的声明以本次保留crash buffer是否存在本包Process记录为限，不代表长期稳定性。

## 关闭与下一步

SETTINGS_CONTAINER_MOTION_ENABLED=false重建：餐次入口恢复原smoothSettingsSwap，与同一业务返回owner共用；其它开关不改。全局SETTINGS_MOTION_ENABLED仍控制其它普通设置。没有修改根Tab/底栏、业务数据/存储、云端、登录、备份、网络、JNI或构建架构。protected-final.json记录12份受保护源码/清单/既有报告摘要一致；main仍8cc26c5，server/dist正式更新清单仍0.6.1/code193，不提交/上传。

本轮停止，等待用户观看最终480ms版本；不自动推广其它设置。

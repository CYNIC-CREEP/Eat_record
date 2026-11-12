# 0.6.2 正式版安装包交付

2026-11-12。根据用户反馈修正容器过渡粗边框，并按用户要求转为正式版本号。

## 安装包

[EatRecord-0.6.2.apk](dist/EatRecord-0.6.2.apk)，versionCode202，121220209 bytes，约115.605MiB。

SHA256为`86d0061e7aded20eb2750aabc5b88f5797efdd6835d2e2c3ba773c9cb3da00cb`。

包名com.eatrecord.app，min26/target37，普通包无debuggable。v2/v3签名证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`，与beta.8相同。正式附件与构建输出EatRecord-0.6.2-debug.apk字节一致。

## 粗边框原因与修复

普通卡片通过PaperComponents.rounded将1dp线宽换算成像素。原来源轮廓使用newDrawable(Resources)复制运行时GradientDrawable，GradientState会按设备密度再次缩放描边，造成进入准备帧及返回末端比邻行粗。Android实现中的密度缩放路径已核对。[AOSP GradientDrawable](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/drawable/GradientDrawable.java)

SettingsContainerTransform.measureSource改用newDrawable().mutate()，保留原像素线宽、颜色与独立状态。准备帧、进入和返回共用该轮廓。移除容器外三层偏移阴影，动态边缘使用来源选项本身的细线。

方案A继续采用480ms、Bezier(.25,0,.25,1)及四边同步smooth(e/.90)。轮廓随原几何逐渐展开和收缩，目标内容原位裁剪与现有返回会话继续使用通用实现。关闭方式仍为UiTokens.SETTINGS_CONTAINER_MOTION_ENABLED=false。

## 实际验证

- PowerShell build.ps1 -NoUpload完整构建通过，见[build.log](build/v062-final/build.log)。
- 完整JVM回归通过，含容器61678项、A/B52937项、设置6219项、底栏365项、来源历史145项和根页1160项，见[jvm.log](build/v062-final/jvm.log)。
- Android QA增加实际Session描边直边像素厚度检查，期望与普通1dp线宽一致。revision-v062b8使用最终classes编译通过，见[qa-compile.log](build/v062-final/qa-compile.log)。
- 验包通过4模型、双ARM共8个原生库、固定运行库、字体、许可、QA隔离及包体检查，见[apk.json](build/v062-final/apk.json)。版本、签名及交付摘要见[delivery.json](build/v062-final/delivery.json)。
- git diff --check及独立修改补丁反向适用检查通过，开始前48份文件已保存于[before](build/v062-final/before)。

ADB设备列表为空，本轮Android QA只完成编译，细边框的实际高密度手机观感及进入/返回仍待用户安装验收。

## 本轮文件

SettingsContainerTransform.java修正轮廓克隆和外影，UpgradeSmoke.java增加像素回归检查，AndroidManifest.xml与ReleaseNotes.java转为0.6.2/code202，CURRENT_TASK.md、DECISIONS.md及本报告记录交接。

通用接入清单见[V062_BETA8_NAVIGATION_REPORT.md](V062_BETA8_NAVIGATION_REPORT.md)。本轮补丁和保护核对见[final-audit.json](build/v062-final/final-audit.json)。

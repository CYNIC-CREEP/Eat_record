# 0.6.3-beta.7 按下反馈与连续合本

2026-11-14，Asia/Shanghai。versionCode209。

## 安装包

[普通安装包](dist/EatRecord-0.6.3-beta.7-debug.apk)，127211933 bytes（121.319 MiB）。SHA256为`7e8022c3ba2e873611c0ddf3747dfafad2f7a76d02ac0532d31623877e249fbb`。min26／target37、双ARM、多DEX，无debuggable，沿用原证书。见[验包明细](build/v063b7/delivery.json)。

## 修改

- 底栏原生DOWN即建立视觉按压目标，按住维持膨胀，UP沿当前速度回弹并提交动作。CANCEL、越界、多指及宿主切换会清理捕获手势。
- 右侧添加主按钮最大缩放1.16。左右两组反馈分别控制，快速点击、读屏点击和取消后操作均有对应处理。
- 封面改为12段轻微弯曲投影，弯曲参数0.09弧度，透视距离4倍页宽，降低大幅弯曲和逐帧绘制负担。
- 合本矩形使用整个返回进度平滑缩回书架，实时书架提供背景，目标封面纹理按锚点捕获。返回入口直接进入准备，实际布局有待完成工作时继续等待。

## 验证

- [完整JVM](build/v063b7/jvm.log)通过，包含61687项封面几何与连续收拢检查。[NoUpload构建](build/v063b7/build.log)、[APK内容](build/v063b7/apk.json)和[签名](build/v063b7/signature.log)通过。
- [Beta7专项QA](build/v063b7/qa.log)使用独立Android16／API36模拟器及合成餐食，日记保持完整，HTTP(S)请求为0。
- [按压反馈](build/v063b7/qa-evidence-pass/v063b7-press-feedback.json)在尚未UP时检测到左侧压力0.847、主按钮压力0.890，主按钮直径由147px增至167px。取消、移出、单次松手提交、读屏、快速改选和隐藏命中均通过。
- [完整合本](build/v063b7/qa-evidence-pass/v063b7-full-close.json)采集23个移动绘制样本，剩余90.6%进度时已有可见缩小，最大宽度单步变化约6.24%。首个合本绘制约164ms，达到可见缩小阈值约314ms。
- [半途及重复返回](build/v063b7/qa-evidence-pass/v063b7-midopen-close.json)采集13个移动样本，最大宽度单步约10.65%，返回足迹及纹理复用通过。上述时间属于模拟器采样。
- [普通包快速交互](build/v063b7/navigation.log)通过。[普通包开本／合本](build/v063b7/normal/result.json)通过，见[录屏](build/v063b7/normal/book-open-close.mp4)。崩溃缓冲为空。

## 剩余边界与后续

模拟器仍可能出现长帧，魅族21上的封面观感和响应速度待安装复核。内部翻页尚未归位时，当前流程先落到最近真实页，再执行合本。

若用户对这版封面安装验收仍不满意，下一轮按用户要求将封面开合呈现改写为Compose。届时仍需沿用当前日期、收藏来源、数据及返回身份。

[保护核验](build/v063b7/final-audit.json)检查源码与编译、原签名及上一轮Beta6包。[设备设置](build/v063b7/final-device-state.json)已恢复，独立模拟器已关闭。

main分支HEAD为2434aae，既有未提交修改保留，正式分发清单为0.6.2/code202。本轮交付本地Beta。前一轮见[V063_BETA6_REPORT.md](V063_BETA6_REPORT.md)。

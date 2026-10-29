# beta.15 手势、弹窗与识别尾注

日期：2026-10-20。版本0.6.0-beta.15，versionCode163。普通安装包为本地debug证书签名，未公开发布/上传、commit或push。

## 改动

- FoodProcessingDialog完成态去掉独立“AI估算·区间”行；小贴士后最后一项显示已保存的模型来源及“仅供参考”。FoodUi详情同步移除重复区间行。低置信/用户修正说明保留；未保存模型名不套当前设置补造来源，calorieLow/calorieHigh/confidence和估算依据原数据不删除。
- 普通弹窗使用当前窗口安全区宽减48dp、最大560dp，内容留白24dp，长自定义内容可滚动；不把全屏识别、修边及原图改成窄弹窗。平台title/message/button和延迟/回收列表行统一字体及颜色，保留列表选择/滚动状态。字体选择样张例外，不覆盖本来用于对比的各字体。
- 旧版入口、三餐提醒、智能助手、恢复默认设置复用普通图标的浅化/alpha规则。四个新图标按完整轮廓一次合成透明度，避免交点叠色又变深；个人资料强调色不受影响。
- HomeCalendarView新增三个月份真实页面的横向跟手切换，TodayWeekStrip同步可中断归位。GestureSpring使用原生Choreographer、闭式critically damped spring（damping1、response0.4s），无新框架或依赖；拖动即时更新translationX，松手移交速度、可反向/取消，关闭动画/无障碍走即时状态。
- 只有日期区域开始的下拉可展开月历。MainActivity仅在今日窗口观察明确上滑，向原子控件发取消后接管收起，不劫持横滑、弹窗或媒体编辑。本次展开未选日期时恢复今日周；选过日期回所选日周。月份浏览不改已选日记或创建记录。
- TodayDayPager三张真实相邻餐纸，保留新中间页以避免切换时重建闪图；右滑前一天、左滑后一天，今天往未来仅弹性回位。预载只读已有JSON，不创建未来/空日期、不触发识别。日历选择、首页日期、周报和餐纸操作目标同步。

## 验证

- 173项JVM原有合同/兼容、媒体、本子、日期和提醒检查通过；本轮不改识别/存储合同或模型权重。
- 日间revision-v15通过真实屏幕触摸：日期区下拉、餐纸下拉不展开、月翻页/日翻页中间帧、反向/打断/取消、未选日回今日周、选历史日重建后保留该日周、未来弹性边界；媒体长按/编辑不误翻日、点击详情看原图，日记JSON不变。
- 同场景通过四图标像素墨色/alpha一致，普通弹窗宽度、隐私菜单首尾回收行主题字体，手输提醒键盘下可用、非法值不保存、IME保存/取消、结果尾注位置。
- 首轮图标像素检查揭示半透明交点叠色，已修复后完整重跑通过。夜间大字首轮字体尺寸断言使用scaledDensity线性计算，改用TypedValue.applyDimension遵循系统SP规则，不将错误断言当作应用问题或改小字体：[Android非线性字体说明](https://developer.android.com/about/versions/14/features#non-linear-font-scaling)。
- revision-v15在360×800dp夜间150%字体、同配置关闭系统动画两种状态完整重跑通过；禁用动画时保留即时切换/边界反馈，不残留spring或偏移。
- progress-v13在日间动画开启、夜间150%字体关闭动画均通过真实本地JNI抠图、同次SSE菜名/临时热量/最终营养、小贴士、最后尾注与旧区间行隐藏；不校验的营养不落库，不额外请求tips，明确确认后入库且关闭清理资源。模型返回为QA合同数据。
- dialog-policy-v15补验旧版切换弹窗取消不改变模式、当天营养总览共享留白，以及隐私菜单/提醒回归。自动测试请求IME后未将其显示与否当断言；随后在普通包中按真实UI树点开输入，dumpsys确认mInputShown/mIsInputViewShown为true，150%大字下输入/取消/保存均在键盘上方，截图ordinary-real-keyboard.png。
- 截图和日志位于build/beta15-qa（qa子目录为从独立模拟器拉取的截图），包含month-left-mid、paper-collapse-mid、yesterday-day-right-mid、future-elastic-edge、privacy-theme-last-row、reminder-keyboard-invalid及detail-footer等。

## 安装包

- dist/EatRecord-0.6.0-beta.15-debug.apk：121,035,664字节（115.4286MiB）。
- SHA-256：7a21c8c1239b147b5e34872ad1d6dcd0dd24f30e4176b32de3f7a3092e49c60e。
- 完整build.ps1 -NoUpload、versionCode163/minSdk26/targetSdk37、v2/v3签名及四模型/双ARM ABI/内置字体字节和许可证检查通过，无DEBUGGABLE/QA runner/QA夹具，低于300MiB。
- 普通包覆盖安装到独立模拟器后冷启动Status ok、LaunchState COLD、进程存活，AndroidRuntime无错误且没有注册instrumentation；实际点击我→其他设置→三餐提醒→早餐提醒→输入框，键盘可见且取消正常。恢复字体/密度/夜间/动画/硬件键盘输入法设置并关闭本轮EatRecordV06QA；git diff --check通过。

## 边界

只在独立API36.1模拟器验证，没有在用户手机安装或做品牌真机触感验收。手势跟手的主观速度仍可按实际手机反馈微调。营养/API返回为明确QA夹具；真实本地抠图不代表真实营养精度或模型接口速度已验收。Beta旧UI/旧字段兼容仍保留，不提前按正式版规则移除。

# 0.6.3-beta.3 Compose底栏重构

2026-11-13，Asia/Shanghai。参考用户提供的FlClash点击前后截图与30fps录屏，围绕整栏轻微膨胀、选中面突出和连续改选重构底部交互区。

## 安装包

[EatRecord-0.6.3-beta.3-debug.apk](dist/EatRecord-0.6.3-beta.3-debug.apk)，code205，普通包，min26／target37，无debuggable。大小为127199645 bytes（121.307 MiB）。SHA256为`d116e2b992f73d16c92463a0f63efd90db1fa612179d4e364380624aec62baa9`。

v2／v3沿用原证书，证书SHA256为`7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee`。最终包明细见[delivery.json](build/v063b3/delivery.json)。

## 修改内容

- 新增局部ComposeBottomBar，三Tab为今日／足迹／我。水滴选择面使用主题半透明渐变、柔光和软阴影，整栏膨胀、选择面拉伸与图标反馈共同响应改选。
- Compose Animatable从当前值和速度重新设定弹簧目标，菜单和布局显隐随新目标续接。根页拖动直接传入连续位置，松手显式传入页面归一化速度，避免UP重复位置清掉释放速度。
- 今日右侧使用原创碗叶主按钮，向上展开原星光与手写入口。展开早期重叠动作停止命中，逻辑离开今日立即撤去动作语义并同步收拢布局，快速反向与返回均可关闭菜单。
- Java继续拥有页面和业务状态，局部Compose宿主安装生命周期、SavedState、ViewModel与Recomposer。旧Java底栏视觉实例退出实际View树，软件截图排除Compose宿主，书本和主题过渡保留独立活底栏。
- 随包教程、空白今日提示及本地server教程文件同步新的餐食入口。自定义构建固定Kotlin2.0.21、Compose1.7.8及依赖SHA256，加入AAR资源、多DEX、双ARM graphics-path及许可。

## 已执行验证

测试设备为新建独立AVD EatRecordComposeV063QA，序列号emulator-5588，Android16／API36，1080×2340。使用普通包，测试记录仅位于该独立模拟器。测试后恢复字体和三项动画设置并关闭本轮创建的模拟器，保留其数据。见[设备收尾](build/v063b3/device/final-device-state.json)。

| 检查 | 实际结果与证据 |
| --- | --- |
| JVM与构建 | 既有业务、导航及数据兼容回归通过。记录见[JVM日志](build/v063b3/jvm-final.log)与[构建日志](build/v063b3/build-final.log) |
| 快速Tab与菜单反向 | 九次连续Tab改选、菜单四次快速切换、展开中离开今日、返回今日保持收起及返回键收起通过。记录见[UI日志](build/v063b3/device/ui-release.log)、[录屏](build/v063b3/device/release/interaction.mp4)与[连续帧](build/v063b3/device/release/motion-contact.jpg) |
| 主题与原业务入口 | 夜间展开及我页面动作隐藏通过，星光与手写均进入原餐次选择，随后恢复日间。见[结果](build/v063b3/device/extra/result.json) |
| 系统缩放 | 关闭系统动画时菜单可展开，1.5倍字体下两个动作可用，测试后恢复字体与动画设置。见[截图](build/v063b3/device/scale/motion-disabled-expanded.png)与[结果](build/v063b3/device/scale/result.json) |
| 记录本联动 | 经原手写入口创建合成餐食后打开记录本，开本过程中底栏可见，底栏返回今日与我均通过，崩溃缓冲为空。见[结果](build/v063b3/device/book/result.json) |
| 教程及返回 | 空白今日展示新提示，展开后返回只收起菜单并保留当前Activity，崩溃缓冲为空。见[结果](build/v063b3/device/final/result.json) |
| 页面滑动与其他设置 | 最终包根页四次左右滑动归位、其他设置→今日、动作恢复通过，崩溃缓冲为空。见[结果](build/v063b3/device/release-extra/result.json) |
| APK内容 | 四模型、默认字体、原6份运行库摘要、双ARM共10库及依赖闭包通过，额外核对2份graphics-path摘要、许可、多DEX与QA隔离。见[验包](build/v063b3/apk.json)与[签名](build/v063b3/signature.log) |

最后速度桥接修正已重新构建并核验。表中快速交互录屏与页面滑动结果对应最终交付包，主题、字体、原业务入口和记录本截图保留本轮此前迭代证据。

## 验收边界与交接

当前未连接真机，真机动画观感与厂商兼容验证仍待完成。模拟器录屏可核对中间状态与布局，物理设备上的流畅度需安装复核。

工作区保留此前未提交改动，原签名文件与本轮未修改的既有脏文件摘要均已复核，见[保护核验](build/v063b3/final-audit.json)。起始文件摘要与差异见[before-manifest.json](build/v063b3/before-manifest.json)及[before.patch](build/v063b3/before.patch)。本轮交付Beta，线上正式清单仍对应0.6.2/code202。下一步为真机安装验收，再根据用户最终版与上传指令执行发布及同版百度归档。

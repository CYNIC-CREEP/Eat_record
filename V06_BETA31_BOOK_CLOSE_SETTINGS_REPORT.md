# 0.6.0-beta.31 关本与设置过渡

日期：2026-10-26，versionCode179。保持Beta，仅本地普通NoUpload构建交付；未提交、打标签、push、上传或正式发布。用户暂停ADB的要求仍有效，本轮无设备操作。

## 源码原因

- closeBookDetail原来只结束开本overlay，再走普通层级返回；没有合封面／缩回路径。
- PageCrossfade新页首帧alpha即为1，而位移较大、时长220ms且位移曲线前段很快，视觉主要是立即换内容再轻移。提醒和API官网部分入口直接render，绕过转场；活动转场中的原位刷新、主题遮罩和root快切也有接管不完整的路径。
- 复审确认Android ViewGroup将保留的disappearing旧子View画在普通新子View上方，所以不能用旧层始终不透明＋只渐显新层；也不能把旧纸底和旧内容分成两个独立淡出的完整背景，否则中间会混入额外纸色。

## 实现

1. BookOpenView增加closing，复用同一投影、完整封面纹理、动态圆角、真实纸张Path和遮挡关系。完整关闭800ms，从当前p反向到0，中途开本直接复用已有overlay与Bitmap；先合封面再缩回，不逐帧分配。
2. 内部翻页有afterTurn延续：先落当前最近真实端点，稳定currentDate后才捕获；重复返回不替换owner，取消／离开清理generation和回调。等待期间新的页内触摸不会抢停归位spring，边缘返回不再截走UP；配置变化在替换frame前清全局pending与overlay，避免后续返回长期失效。
3. 返回页先提交，两个preDraw阶段布局和定位真实日期封面，必要时使用入口日期；恢复入口滚动、保证目标可见，再保存并隐藏目标alpha/elevation/translationZ。完成仅恢复和回收，不延迟重建路由。普通书架、展开／列表收藏统一tag；真正无锚点仍返回实际列表，不制造数据。
4. 关闭背景只保留旧chrome／背景，排除整张纸，避免缩小时残留满尺寸纸影。目标页禁用另一次animateIn；切tab、窗口／尺寸、销毁等清理overlay并恢复隐藏对象；根数据重建等关闭完成后再处理。系统关闭动画直接返回。
5. PageCrossfade默认300ms线性时钟，位移16dp并仅执行一次既有曲线；旧层按1-p淡出，新层完整不透明。单根给原View临时补纸面，不重parent；多根或导航中断才捕获一帧。结束／取消恢复背景、Drawable callback、padding、原位移及alpha，peer路径保持。
6. 提醒与API官网入口／返回统一；智能助手／贴纸边框等原位设置刷新保持滚动并清理活动转场。后续导航取消旧主题覆盖层，子页返回到root途中点击继续从当前合成帧接管。

## 验证与证据

| 检查 | 实际结果 |
| --- | --- |
| test-food-analysis.ps1 | 17组，共4327项PASS；纯JVM状态／合同，不是图形验收 |
| 全部Java＋Android QA编译 | 59份源文件，通过；仅旧Java8/API及unchecked警告 |
| 普通构建与最终源码一致 | 612个生产class与最后完整编译逐文件SHA256一致 |
| git diff --check | 通过；既有CRLF提示不属于内容错误 |
| build.ps1 -NoUpload | 普通包构建成功，未上传 |
| verify-food-apk.py | 模型／字体、双ARM依赖闭包、12固定运行库摘要、许可和QA隔离通过 |
| aapt2 / apksigner | beta.31/code179，min26/target37，同证书v2/v3，无debuggable或instrumentation |
| Android运行／截图／帧率 | 未执行：当前用户要求不ADB |

日志位于build/beta31-final-jvm.log、beta31-full-qa-javac.log、beta31-final-build.log、beta31-final-verify.log、beta31-final-cert.txt、beta31-final-badging.txt、beta31-final-manifest.txt；源码摘要和产物信息见beta31-final-source-hashes.json。

新增revision-v31：closing覆盖normal／flip的真实X／Back、0/40/80%时间点、同Bitmap、真实返回页保持、最后封闭四角pixel oracle、45%开本反向、内部翻页32%／68%中返回、pending中连续页内输入／左缘右滑、模拟onConfigurationChanged后真实Back可用（非实际设备旋转）、最新日期、两种收藏和真正无锚点、重复返回／导航取消及alpha／Z／纹理清理。settings覆盖15组开返、300ms／16dp、旧层淡出与新层不透明、背景／padding／旧View变换恢复、快速导航、滚动和主题中断；attached实际host.draw与capturePresentation在0/25/50/75/90%进行高对比红蓝混合及透明区域oracle，再验证中断改目标和取消。

**上述Android专项只编译，未运行。软件Canvas oracle即使未来通过，也不能替代HWUI、品牌手机画面和实际帧率验收。** 既有版本报告不能当作当前代码设备PASS。本轮JVM总数按本次日志逐项相加，不沿用旧报告中的汇总数。

## 安装包

- 文件：dist/EatRecord-0.6.0-beta.31-debug.apk（普通包，非QA）。
- 大小：123113590字节，117.410MiB。
- SHA256：aec3a76ad589b370ce958441328f7755beb65a4137255168bd8a105b55636233。
- 签名证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。
- 4模型／16个双ARM原生库；不包含下载式Qwen权重、QA runner／夹具、诊断日志和debuggable。

交付后等待用户实际安装反馈；当前不能承诺设备零卡顿。若重新授权ADB，先在独立设备运行revision-v31，再回归开本四角、根导航连续手势和内页连续翻页。

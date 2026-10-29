# 0.6.0-beta.32 动画、API管理与识别退役

更新2026-10-27，versionCode180。本轮未最终交付前持续并入用户补充要求，保持Beta；无提交、标签、push、上传或正式发布。

## 最终行为

- 设置完整纸页不透明相邻平移，480ms与独立标准ease(.25,.1,.25,1)，不延迟点击反馈，中段比原360ms soft更慢。局部clip限制Paper.drawColor，避免旧纸面盖住新页；布局后临时硬件层≤32MiB，结束／取消归还。
- 纸背原来在t→1仍停在paper.left，随后释放导致整条左边缘突灭。新行程按稳定frame viewport及描边AA裕量把反射边缘送出画面，正反／取消共享物理位置。缓存3个渐变／2个矩阵，前纹理复用。开／关本时间与角部裁切保留，关本捕获新封面前临时设背景bounds。
- Key默认密码遮掩，禁自动填充、文本状态保存和IME学习；新增真正删除持久化Key。删除／重新保存提升授权世代、关闭旧连接，模型检测及AI回调不能复活旧Key或写入旧结果，地址／模型保留。删除是本机配置操作，不是远端吊销，也不是静态加密。
- 退役实验性本机营养识别全部入口、推荐、下载、JNI、模型声明／参考表和专属库，只有用户API可识别。旧选择关闭上传授权／自动识别，旧营养任务hold，普通API任务不被无差别暂停。缓存后台仅清理files/offline_food、不跟随链接；旧日记、图片、已存营养及真实来源保留，本地抠图4模型保留。
- 食物窗口的确定路径：详情reload安排120ms根刷新；点图dismiss后，原图仍覆盖在attached root上，刷新误执行renderCurrent而清窗。现覆盖期间保留dirty/pending、停止刷新，当前viewer关闭后再恢复；旧close不影响新viewer。原图安全存在且解码成功、新viewer建立后才关详情，失败保留，不把丢失文件默认为同餐第一张。
- 当前教程随APK打包、本机显示，与本地server教程一致，不继续推荐远端旧路线；远端没有上传。其他优化仅[V06_BETA32_OPTIMIZATION_CHECKLIST.md](V06_BETA32_OPTIMIZATION_CHECKLIST.md)内建议，未顺带重构备份或设置分类。

## 实测范围与最后限制

本轮早期在用户MEIZU21（Android16、1080×2340／440dpi）仅普通包及已有资料上检查过：设置3组进退、480ms中段、仿真连续左4／右4、普通左3／右3、系统Back／X、中途开／关、内翻返回和纸边出屏。相关录屏含final-slow-settings-verified.mp4、final-tail-verified.mp4和更早阶段照片；前台丢失或页面未命中的自动化样本不计通过。后期增加前台包名与页面校验；孤立系统返回正常，快速注入差异仍待专项复核，不当作确定缺陷。

早期480ms页内返回的非录屏定向样本356帧、12帧deadline missed，中位8ms／P95 18ms，只是该机该场景，不承诺所有设备零卡顿；不得混用录屏、旧360ms与不同操作数量的数据计算提升比例。

**最后Key管理、识别退役／清理、图片窗口及随包教程修改后，手机断开。用户明确选择先交包并标注未实测，因此最终APK没有再次安装或运行；早期手机base.apk摘要不是最终包摘要。没有删除真实Key、写日记夹具或调用真实付费API，也未在日常手机装QA。** 原翻页／音效和系统倍率在早期测试后已恢复，之后不再操作手机。

## 本地验证

| 检查 | 本次真实结果 |
| --- | --- |
| JVM主脚本 | 20组2991项通过；API删除23、API-only／迁移策略47、限定缓存删除4（Windows symlinks=false） |
| 食物文件选择独立测试 | 14项通过：原图优先、缺失回贴纸、目录／路径穿越／空值拒绝，无UI或解码声明 |
| 生产＋R＋Android QA编译 | 58源文件通过；609个普通class与最终完整编译一致 |
| Android UI runner | 仅编译，未运行；v32保留480ms空间／纸边／纹理回归，未加入未完成maintenance场景 |
| NoUpload构建／diff | 普通包成功，git diff --check通过，未上传 |
| APK校验 | 4抠图模型／字体／8许可证、8原生库／6固定runtime摘要和依赖闭包、教程一致性、退役组件缺席与QA隔离通过 |
| Manifest／签名 | beta.32/code180，min26/target37，同证书v2/v3，无debuggable或instrumentation |

总计JVM3005项；旧离线套件被移除，不能直接沿用旧统计。日志见build/beta32-final-jvm.log、beta32-preview-selection.log、beta32-full-qa-javac.log、beta32-final-build.log、beta32-final-verify.log、beta32-final-cert.txt、beta32-final-badging.txt、beta32-final-manifest.txt；源码摘要在beta32-final-source-hashes.json。未给截图中的真实Key制作测试夹具或日志。

## 普通安装包

- dist/EatRecord-0.6.0-beta.32-debug.apk；121146481字节，115.534MiB。
- SHA256：25885bd50d299615ac9ffb959684e7a68ca5ade1cfbd1ea0d2684bf34899c693。
- 证书SHA256：7a279f9102fa24c73e03f8cbe94eef79eeb9276a552acf505e8c6abc9d0a71ee。

下轮优先最终包升级与窗口延迟≥600ms的真实复测，Key删除／迁移仅用独立夹具；备份凭据隔离、恢复事务和其余品牌／大字体／夜间场景另列后续，不虚构通过。

# 构建、测试与发布流程

以下是可执行的操作指南，不是执行授权。默认只本地验证；commit、tag、push、发布、服务器修改须用户单独确认。纯文档任务不必构建或启动App。

## 1. 预检

从 `git rev-parse --show-toplevel` 确认的真实根目录开始，读交接文件后检查：

```powershell
git status --short --branch
git log -5 --oneline
git diff --stat
git diff --cached --stat
git diff --check
```

保护未提交及未跟踪文件，不使用reset/clean。根目录应有AndroidManifest.xml、build.ps1、src/res/native，不存在Gradle构建命令或app/build/outputs路径。

版本在 `AndroidManifest.xml`：versionCode递增、versionName匹配 `src/com/eatrecord/app/ReleaseNotes.java` 最新条目。未发布Beta与线上正式版清单不同是正常现象，不为“对齐”自动改/上传server/update.json。

每轮功能修改递增一次Beta及versionCode，同轮修正/验证不再递增；未经用户明确批准始终交付Beta。新版本名称按当前任务确定，不沿用已结束版本的轮次。更新渠道只推正式版，Beta只本地交付，不替换server/update.json或上传。build.ps1在未带-NoUpload时拒绝预发布版本，OpenList/蓝奏入口也在打包/认证/浏览器操作前拒绝非x.y.z正式版本；这些拦截不代表旧发布流程其余副作用已修复。本地转正式版本号与远端发布分别授权：用户可明确要求先使用正式版本号安装复查，仍须等待人工验收及发布指令后才上传。单轮免测试或上传授权不延续到后续版本，也不能把未测试项目标为通过；当前验收范围和实际发布状态见CURRENT_TASK.md及对应发布报告。

构建始终带NoUpload。已验收普通APK可在产品源码未变化、版本/签名/内容及SHA256重新核对后原样发布，避免重编译替换已验收字节；源码或资源变化则重新构建并验证。普通包复制为正式附件名时前后摘要须相同，后缀不改变调试标志或签名。

## 2. 环境与签名

- Windows PowerShell、JDK（README基线17）、`%LOCALAPPDATA%/Android/Sdk` 下platform/build-tools/CMake/NDK，PATH中有Python3.11。
- build.ps1优先本机Eclipse Adoptium JDK并选择最高SDK/Build Tools；实际target/工具版本须每次核对，跨机器不保证自动一致。
- scripts/build-food-vision.ps1获取固定MNN3.6.0头文件/运行库，Python转换抠图/EdgeTAM；首次会下载较大模型和转换依赖，缓存于build/deps。许可和固定摘要不可无验证更新。
- 当前脚本签名文件为根目录debug.keystore（Git忽略），普通包后缀仍为-debug，但普通Manifest不启用debuggable；无独立release签名variant。正式生产密钥/管理策略待用户确认。
- 已安装版本需同证书覆盖升级。密钥缺失时脚本会生成新文件，可能破坏覆盖兼容：先停下确认/恢复原密钥，不得把“签名成功”等同于“证书兼容”。密钥和签名密码不入Git/文档。

## 3. 本地普通包与JVM检查

```powershell
# javac/java须在PATH；首次测试可能下载org.json测试依赖。
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-food-analysis.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload
```

产物：`dist/EatRecord-<versionName>-debug.apk`；不是QA包。`-NoUpload`不能省略：脚本无此参数会调用蓝奏上传脚本。构建不等于发布。

```powershell
[xml]$TaskManifest = Get-Content .\AndroidManifest.xml
$TaskVersion = $TaskManifest.manifest.versionName
$TaskApk = Join-Path (Get-Location) "dist/EatRecord-$TaskVersion-debug.apk"
python .\scripts\verify-food-apk.py $TaskApk
Get-FileHash -LiteralPath $TaskApk -Algorithm SHA256
```

不要按dist最新mtime挑包，容易误选QA或旧版本。Verifier检查四抠图模型、默认小赖字体、固定运行库摘要、双ARM ABI/依赖闭包、许可证、随包教程、300MiB预算及QA隔离。beta.32起只含8个双ARM视觉库／6个固定运行库摘要，禁止退役营养识别类、模型声明／参考表、eatoffline或LLM专属库，以及旧SAM/ONNX、权重和QA夹具。

另用实际选定Build Tools核对：

```powershell
& "<build-tools目录>/aapt2.exe" dump badging $TaskApk
& "<build-tools目录>/apksigner.bat" verify --verbose --print-certs $TaskApk
```

目录占位符要替换为本机真实路径，不凭旧截图推断版本。核对包名、SDK、versionCode/versionName、签名摘要、无debuggable及模型/字体/许可。

## 4. adb与隔离QA

先 `adb devices -l` 确认设备，之后所有命令指定序列号。普通包覆盖安装/启动示例：

```powershell
adb -s <serial> install -r --no-streaming <普通APK绝对路径>
adb -s <serial> shell am force-stop com.eatrecord.app
adb -s <serial> shell am start -W -S -n com.eatrecord.app/.MainActivity
adb -s <serial> shell pidof com.eatrecord.app
adb -s <serial> logcat -d -b crash
```

启动/相机/权限/通知/文件/返回/动画/ABI变更做相关设备检查；必要时复现前保存既有日志再清空并检查AndroidRuntime。无品牌机就明确未实测，不拿模拟器耗时作手机承诺。

仅独立测试设备：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload -Qa -QaFixture <测试食物照片>
adb -s <test-serial> install -r --no-streaming .\dist\EatRecord-<versionName>-qa.apk
adb -s <test-serial> shell am instrument -w com.eatrecord.app/.UpgradeSmoke
```

QA会改写日记/设置，不在用户日常手机运行，不发布。Android QA同设备串行，不同时启动多个instrumentation。通用回归覆盖真实JNI、草稿重建/SSE合同/确认/取消/手动优先/备份回收；云端结果使用假传输，不算真实服务商验证。

定向入口（完整前提/结果见相应报告及tests/android/UpgradeSmoke.java）：

- `-e scenario revision-v062b8`覆盖通用A入口的真实点击、长页、嵌套/跳层、收藏双来源、字体来源身份及跨入口中断清理。runner写合成夹具，仅独立QA设备。本轮仅使用最终classes编译，新增场景的真机视觉由用户安装普通包验收，见V062_BETA8_NAVIGATION_REPORT.md。

- `-e scenario revision-v062b3`覆盖普通设置450ms/0.25W和餐次容器。历史几何oracle显式选择Baseline，结束恢复UiTokens当前默认。beta.6默认A，追加Poison Paper禁止重放、捕获异常ForceDark恢复、单次完整覆盖准备帧、bounded ready等待、准备中清理与旧回调隔离，以及paperPixel/fillPixel独立和释放断言。runner写合成夹具，仅独立QA设备使用。beta.7增加普通settings首帧后建缓存的准备态/取消断言，以及先取原生截图的Me/首次Other/Meal返回夜间颜色检查，避免软件绘制预热掩盖缺陷。日常魅族21只用普通同证书包和非破坏性ADB检查，QA目前仅编译。最新状态与证据见CURRENT_TASK.md，历史A/B和beta.5见V062_BETA4_AB_REPORT.md与V062_BETA5_FINAL_REPORT.md。


- `-e scenario revision-v062`：已按beta.1返修更新为原根页横移、拖动停留／反向／取消、液态镜片按住膨胀与原图文强调、实例保留及日记不变；JVM入口同时运行TabMotionTest与纯几何检查。此runner仍写合成夹具，只能独立QA设备。本次用户只允许ADB真机，故只编译runner，不在日常魅族21执行；普通包获明确授权后以非破坏性UI及录屏检查，脚本每段输入检查应用前台，离开则停止并拒绝证据；魅族长按可能唤起Aicy，不改用户系统开关，优先短按录屏。上一轮Fade／4130项证据仅属历史，不作为返修通过。

- `-e scenario revision-v061b8`：历史日期文字淡化与水位／容器透明度独立，真实七日热量投影、六个非空日及一个空日的实际HWUI像素，单目标／关闭动画，回归自然年月稳定、未来禁选与重建／手势。仅独立QA设备／合成数据；不能仅凭很小的非零软件像素判定历史水位可读。
- `-e scenario revision-v061b7`：显示月淡化与未来禁选分离、跨月周历更新、weekday微差与略增波幅／单目标／减少动画；营养概览单窗口内联目标、显式保存／取消／清零／校验、最新设置合并和原位显示，短窗大字与实际IME。仅独立QA设备，手机仅普通包展开／取消，不保存测试目标。旧直接反射goal()的新建弹窗QA属于历史契约，不能为它恢复双重弹窗。
- `-e scenario revision-v061b6`：收起周历的每日水位、手工热量优先、无目标／0／半满／达标／超额、同日点击与切日短暂晃动及生命周期／展开／关闭动画清理；合成API配置删除确认取消／应用、关联字段及授权世代清理、重开空白、旧owner隔离，旧餐食／云账号不变。仅独立QA设备，绝不在日常手机删除真实配置。
- `-e scenario revision-v061b5`：合成PNG上的实际擦除／恢复、逐步撤销／重做、分支及32步压缩、重置、双指／取消、轮廓开关与拖动预览、圆角与间距、保存alpha不含预览边框、关闭回收和迟到结果。只用于独立模拟器，另在短窗口／大字环境复跑并恢复设置，不在日常手机运行。
- `-e scenario revision-v061b4`：营养师作为根页0的真实子页，点击同步启动同一RootPager、保留实例/世代/三页，无PageCrossfade/重建/重挂；实际出场、改选/暂停及离屏后复位、日期owner；15/30dp小幅上滑按钮连续/取消恢复。记录handler和首个非零硬件提交帧时间，不设模拟器SLA。含v061b3与既有回归，定向phase=navigation-settings。
- `-e scenario revision-v061b3`：实际停在营养师后分别点击迹/我，核对出场仍为营养师、横向页/底栏同进度、中断续接/owner清理；餐次管理新页入口、开关、返回/滚动和配置保留，另回归既有v061b2。仅独立QA/合成数据。
- `-e scenario revision-v061b2`：隐藏/半屏营养师强制global-layout不产生假IME，真实键盘开关、营养师开启时底栏点击逐帧连续、1440ms双向主题与前250ms图标唯一owner/末态交接；仍仅独立QA、禁网络。
- `-e scenario revision-v061`：默认三餐/夜宵显式开关及旧记录保留、今日点击不进营养师、纵向取消/上滑/下滑/隐藏owner及关闭开关、正反主题半程圆内/外/柔边像素与HWUI帧和快照释放。仅独立QA，HTTP(S)禁用；旧v35中直接点击个性化夜间开关的流程已被取代。
- `-e scenario release-v060 -e phase all|editor|media`：餐次实际Layout／空hint墨迹垂直居中且保持左对齐、窄窗大字、多行溢出滚动、直接编辑／真实Back／Done与最新媒体营养合并、空日记不写；阻塞原图切换、暂停恢复后下一张连续、旧会话重置回调不影响新owner。仅独立设备／合成夹具，日常手机只普通包和已有数据。
- `-e scenario revision-v35 -e phase all|headers`：真实日夜循环、迹／个性化／其他／记录本／安全五页，正常／弹窗关闭／中断返回的背景与对比度；透明标题区与实际整页纸面合成、夜间正文滚动不进入标题区、明暗切换保留页／scroll／日期／日历和快照清理，all附空餐纸及短窗口字体回归。真机必须另用普通包反复切模式与跨页截图，不能把旧固定底色oracle或仅软件draw当通过。
- `-e scenario revision-v34 -e phase all|ui|media|privacy|restore`：空餐纸单行居中且不触发添加、星标入口、五主题夜间顶栏软件／HWUI像素、内容自适应字体框与窄屏大字、疏密保存留页；阻塞独立图片worker验证异步首开／失败原子性／最新目标／关闭取消、缩略图共享；实际导出脱敏、旧设置导入权限边界、真实restore提交与无效ZIP、Android偏好提交不确定结果回滚、启动式恢复与重试页面。仅独立QA设备，合成凭据，不能用于日常手机。JVM脚本另含分存失败恢复与64MiB heap流式／文件事务故障检查。最终普通包须核对SafeBackupAgent、两个系统备份版本及device-transfer的私有preferences排除规则，不宣称实际系统备份传输已验证。
- `-e scenario revision-v33 -e phase all|refresh|dialogs|returns|settings|glass`：650ms详情关闭不失效／原图覆盖defer，单层blur/owner交接，实际draw后返回与取消、clean根复用／dirty及未完日期目标重建，新Hub/来源返回／高级折叠，静止glass不捕获／buffer复用及detach清理。只独立QA，日常手机普通包保持真数据。生命周期config/pause为模拟callback，不等同真实旋转。旧v21／24–27的peer navigation和旧分类settings入口退役失败，不能为旧测试恢复生产dead API；有效v28根导航、日期／媒体／本子和v32空间／纸边回归保留。
- `-e scenario revision-v32 -e phase all|settings|books`：480ms独立ease的不透明整宽相邻页、双向／单多根／中断／direct ScrollView、真实drawColor纸底的局部bounds sentinel与无混色空间oracle、15组设置和资源恢复；books附v31关本及新封面只layout未draw时bounds为空的完整纹理、3个Shader／2个Matrix复用及末端左边距纸背出屏像素。beta.32取代旧v31 settings的交叉淡出／16dp断言；不要运行旧设置phase来判新实现。仅独立设备运行；日常手机只用同证书普通包、现有数据和非破坏性ADB检查。
- `-e scenario revision-v31 -e phase all|closing|settings`：normal／flip真实X与系统Back、开本中反向、内翻中pending归最近端点、最新日期／两种收藏封面／真正无锚点、重复返回和切tab取消，800ms比例关闭时间线与圆角／alpha／Z／纹理清理；设置15组开返、300ms／16dp、旧层淡出／新层不透明及背景／padding恢复、快切、滚动与主题中断。附attached真实host.draw与中断capture多时点红蓝混合像素oracle，防全旧画面、漏底与双纸层混白；源码编译不等于设备PASS，仍须新的ADB授权及独立设备。
- `-e scenario revision-v30 -e phase all|corners`：4封面风格×normal／flip；真实开本pause到0/100/200ms，用同cover-only与高对比page探针对照四角；550/850ms切换阴影shader，差异须在真实背景alpha内；native Outline、隐藏Z、恢复／纹理清理。all另跑v29时点及中断回归。isolated Canvas oracle是软件覆盖层检查，不是整机HWUI／品牌机通过；源码只编译时必须明确未运行。
- `-e scenario revision-v29 -e phase all|navigation|opening`：静止今日右滑／我左滑逐MOVE保持页位移、底栏和Today owner不变，下一合法手势可用；opening在normal／flip两模式真实点封面，暂停实际animator到150/300/550/850ms检查成长与封面正／背面几何、完成和中断清理。按系统动画0分支验证最终可用。未运行不得声称设备PASS。
- `-e scenario revision-v28 -e phase all|navigation`：新RootPagePager三页身份保持、逐帧绝对位移／底栏位置及缩放、无PageCrossfade根转场，0/45/80ms连滑、回退中续第三页、交替／缺末MOVE／多指／边界／点击即滑；all另验隐藏今日暂停及返回续日期。90日fixture，日记不变、禁网络；仅有ADB授权且独立设备才运行。beta.28根导航不再适用旧v26/v27依赖PageCrossfade.peerActive的root阶段，不能用那些历史PASS代替新契约。
- `-e scenario revision-v27 -e phase startup|all|navigation|media|books`：启动后先检查选中背景可见、实际宽度和边界，不能先showTab修正后再测；navigation还覆盖缺少末帧MOVE、UP直达终点以及0/100ms停顿。books覆盖正反四页连续翻动（翻页／平移、0/45ms间隔）、末帧MOVE缺失、真实子View持有的多指取消与资源清理。仅当用户允许ADB并选择独立设备时运行；代码编译不等于设备PASS。
- `-e scenario revision-v26 -e phase all|navigation|media -e name <输出名>`：0/45/80ms连续跨页与反向／重抓／取消／多指／边界、逐帧底栏和页面进度，真实冷缓存异步开本与翻页纹理刷新／HWUI可见贴纸像素、暖缓存及退出清理。可加 `-e night true`，系统动画关闭时只验最终状态；选择单一phase。QA会重写fixture，只用于独立测试设备。未知revision场景必须失败，不能用通用PASS代替专项证据。
- `-e scenario revision-v25 -e phase all|navigation|books -e name <输出名>`：底栏完整覆盖／重复迹切今日、正反纸背六处像素边缘、真实封面打开／切tab和关闭中断、翻页取消／反向／抓停／多指；可加 `-e night true` 配合设备夜间／150%字体，或关闭三项系统动画另验。尖括号及all|navigation|books是占位，应选择单一值；只用于独立设备。
- `-e scenario revision-v24`：完整日期／文字／快切回归；phase可选all／navigation／dates／text，定向阶段结果不可说全部通过。
- `-e scenario revision-v22`：API-only日期／授权／退役来源／旧世代回归。旧离线推理／下载场景已退役，不能执行或当作当前能力。日间、夜间150%字体及关闭动画重复。
- `-e scenario onboarding-v22`：真实启动公告→自动选择→取消API配置不启用。
- `revision-v21`、`reprocess-v20`、`supplement-v20`分别覆盖平级导航/图片命中、独立重处理、夜宵/修边；不要按旧测试要求恢复已删除入口。

系统/Launcher ANR遮挡时先确认焦点与来源，修复测试环境后重跑，不算应用通过。测试后恢复字体/密度/明暗/动画/网络，关闭自建模拟器但不清除数据。实际Google备份、品牌机及真实API/称重精度另验。

## 5. 发布前检查

仅用户批准发布后继续：

1. 审查全部变更/未跟踪资源，确认该版本源码、模型/字体许可、说明和测试可追溯；排除缓存、浏览器会话、凭据、QA数据及签名文件。
2. 使用符合第1节要求的普通包并核对摘要/证书/ABI/版本；需要重建时带NoUpload，切勿重命名QA包冒充正式包。
3. 确定生产证书、渠道、标签/提交目标、管理端点/权限、更新清单说明。当前OpenList脚本可能复用旧notes，必须人工核对。
4. 清单、附件、提交/标签、远端下载哈希一致后才报告发布完成；本机Beta尚未发布时不要求覆盖线上正式清单。

## 6. Git与GitHub（须另行授权）

```text
git add <逐个审查后明确列出的文件>
git commit -m "<描述>"
git tag v<versionName>
git push origin <确认的分支>
git push origin v<versionName>
```

不git add .，不强移标签、不强推。origin见PROJECT_CONTEXT；本地tracking信息不证明远端实时状态。

普通包复制成无-debug正式附件名后再创建Release；重命名不改变证书或调试属性：

```text
gh release create v<versionName> <普通APK附件路径> --target <已确认提交> --title "<标题>" --notes-file <已审查说明文件>
```

这是已有渠道的操作模板，不是已发布证据；若版本/附件已存在先检查，不擅自clobber。

源码ZIP由审查后的发布提交生成，不压缩整个工作目录：

```text
git archive --format=zip --prefix=EatRecord-<versionName>/ --output=dist/EatRecord-<versionName>-source.zip <已确认发布提交>
```

提交前审查明确文件清单；压缩后再检查签名、本机凭据、浏览器会话、缓存、用户数据及QA产物均未包含。先上传草稿附件并核对大小/摘要，再发布为正式Release，回下载APK计算SHA256；核对标签实际指向发布提交。收尾文档可以另作后续提交，不移动已发布标签。Git推送连接失败时先查远端是否已接收，再重试；如需代理，仅对本次命令使用本机已配置的代理，不记录认证信息或永久改网络设置。

## 7. OpenList与其他上传（须另行授权）

- 脚本：`scripts/publish-openlist.ps1`；支持BaseUrl、RemoteRoot、ArtifactSuffix、SkipBuild及从OPENLIST_USERNAME/OPENLIST_PASSWORD环境变量读取管理员凭据。只记录变量名，不写真实值或带凭据命令。
- 旧脚本默认管理端点仍为HTTP，不直接采用。2026-10-29已确认管理HTTPS入口为 `https://pan.freefeng.icu`，管理挂载根 `/lanzou/Myapp/eat record`，用户提供的本机管理凭据可用；应用只读账号base_path为 `/lanzou/Myapp`，其请求路径是 `/eat record`。登录域名相同不代表两类账号路径相同，不在文档/Git写入真实凭据。
- 该脚本仍保留全工作目录压缩、复用旧notes、删除同名文件/退休字体及上传额外服务文件等旧行为，尚未实现下述安全流程，不能直接作为当前发布入口。最近0.6.1的安全辅助程序／证据位于被Git忽略的build/publish061，0.6.0历史实现位于build/publish060；两者仅属本机产物，新检出环境不能假定存在。不可不审查便复用脚本中的固定版本、暂存／备份名称或缓存路径。0.6.1执行证据见V061_PUBLISH_REPORT.md。
- 当前流程：明确本版说明和server/update.json，保持dist/update.json字节一致；先上传到不参与版本扫描的临时目录，回下载核对APK/源码及必要文档，再启用正式版本目录。普通包可同时提供正式名和同字节-debug兼容文件名，后者用于目录扫描回退，不能使用QA包。
- 最后切换共享教程与update.json：新文件先上传并校验，旧文件改名保留，再切换新文件；失败时恢复旧文件。只替换本版必要文档，不删除既有版本、字体或部署无关云服务。
- 通过应用只读账号验证相对路径的清单与安装包；检查versionCode/versionName、notes、下载路径和实际字节。更新清单可保留旧绝对路径，由AppDownloadClient映射到只读目录，并提供已验证的GitHub附件作为回退。
- 只读账号的fs/list使用与客户端一致的refresh=false，强制刷新仅由管理员执行。校验缓存必须区分暂存／正式路径和管理员／只读访问，避免正式下载验证被已有同名缓存跳过。
- 下载限速时可以使用服务支持的HTTP Range分段完整回下载；必须检查各段范围/长度，按顺序重组后计算完整SHA256。不能用仅首部、文件名、上传成功响应或服务端取整大小替代完整文件验证。接口参考：https://openlistteam.github.io/docs/guide/api/fs.html 。
- 上传后重新下载APK对比SHA256，核对版本/notes/链接及本地清单diff。不要凭脚本旧提示推断旧HTTP静态路由仍有效。
- `scripts/upload-lanzou.ps1/.mjs`为另一自动上传路径，依赖Node/浏览器会话；当前登录与可用性 **待确认**。不为检查状态擅自运行。

## 8. 维护边界

每次任务更新CURRENT_TASK；长期流程变化更新本文，固定模型/数据/签名策略变化还需更新DECISIONS。测试详细证据放已有版本报告，本文不累积每轮结果。发布端点、远端状态、凭据或命令未实测时标“待确认”，不虚构成功。

凭据删除／旧授权拦截和退役缓存范围由JVM假数据验证；绝不为验证而删除日常手机真实Key、导出含凭据profile或启动真实付费请求。未进行最终APK手机复测时须明确说明，不沿用早期动画包SHA作为最终安装证据。

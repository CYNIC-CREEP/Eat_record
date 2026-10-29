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

用户2026-10-24要求持续使用0.6.0-beta.N，每轮交付Beta序号加一，直到其明确满意并批准正式版；当前版本与versionCode以CURRENT_TASK.md和Manifest为准。交付普通本地APK不授权正式发布或远端上传。

2026-10-29用户已批准本地0.6.0正式版/code184打包交付，并明确暂不上传GitHub、OpenList。构建仍带NoUpload，沿用现有签名；普通构建产物验证后复制为EatRecord-0.6.0.apk，复制前后摘要须一致。此授权不包含commit/tag/push或远端清单修改。

2026-10-29后续用户明确要求上传OpenList和GitHub并推送更新，已取代上一段暂不上传的限制，包含本版源码提交、v0.6.0标签、main推送、正式Release附件和应用更新清单。复用已手机验收且摘要固定的普通包，发布前重新验包，不用重编译替换已验收字节。源码ZIP从审查后的Git提交生成；本机凭据目录与缓存不进入Git或源码ZIP。OpenList先上传并校验版本文件，最后替换更新清单；不运行旧脚本中的无关字体删除。

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
2. 重建普通包并核对摘要/证书/ABI/版本；切勿仅重命名QA包。
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

## 7. OpenList与其他上传（须另行授权）

- 脚本：`scripts/publish-openlist.ps1`；支持BaseUrl、RemoteRoot、ArtifactSuffix、SkipBuild及从OPENLIST_USERNAME/OPENLIST_PASSWORD环境变量读取管理员凭据。只记录变量名，不写真实值或带凭据命令。
- 旧脚本默认管理端点仍为HTTP，不直接采用。2026-10-29已确认管理HTTPS入口为 `https://pan.freefeng.icu`，管理挂载根 `/lanzou/Myapp/eat record`，用户提供的本机管理凭据可用；应用只读账号base_path为 `/lanzou/Myapp`，其请求路径是 `/eat record`。登录域名相同不代表两类账号路径相同，不在文档/Git写入真实凭据。
- 确认参数及副作用后可使用模板 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\publish-openlist.ps1 -SkipBuild -BaseUrl <已确认管理入口> -RemoteRoot <已确认挂载目录>`。脚本默认上传-debug后缀包，与GitHub附件命名不同。
- 会打源码ZIP并上传APK/源码/服务文件/教程/字体/清单；可能先删除同名远端文件及退休字体，并改写dist/update.json、server/update.json。不是无副作用的检查工具，执行前审查源码包排除项及删除目标。
- 上传后重新下载APK对比SHA256，核对版本/notes/链接及本地清单diff。不要凭脚本旧提示推断旧HTTP静态路由仍有效。
- `scripts/upload-lanzou.ps1/.mjs`为另一自动上传路径，依赖Node/浏览器会话；当前登录与可用性 **待确认**。不为检查状态擅自运行。

## 8. 维护边界

每次任务更新CURRENT_TASK；长期流程变化更新本文，固定模型/数据/签名策略变化还需更新DECISIONS。测试详细证据放已有版本报告，本文不累积每轮结果。发布端点、远端状态、凭据或命令未实测时标“待确认”，不虚构成功。

凭据删除／旧授权拦截和退役缓存范围由JVM假数据验证；绝不为验证而删除日常手机真实Key、导出含凭据profile或启动真实付费请求。未进行最终APK手机复测时须明确说明，不沿用早期动画包SHA作为最终安装证据。

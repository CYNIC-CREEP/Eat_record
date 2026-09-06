# 发布流程

## 1. 发布前检查

在真实项目根目录执行：

```powershell
Set-Location D:\Users\cynic\Documents\Eat_record
git status --short --branch
git diff --check
git log -5 --oneline --decorate
```

确认 `AndroidManifest.xml` 中的 `versionCode`、`versionName` 已更新，且 `server/update.json`、`dist/update.json` 的版本和说明一致。不要把密码、Token、Cookie、API Key 或签名密码写入命令、文档或提交。

## 2. 构建 APK

当前项目不使用 Gradle，使用根目录脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -NoUpload
```

脚本从 `%LOCALAPPDATA%\Android\Sdk` 选择可用的最高 Build Tools 和 Android platform，并使用本机 JDK。脚本也会定位 CMake 和 NDK，但当前正式流程不会调用它们构建原生模块。成功产物为：

```text
dist\EatRecord-<versionName>-debug.apk
```

当前 `build.ps1` 的实际正式构建流程不会调用脚本中保留的 JNI/MNN/SAM 构建函数，也不会向 APK 打包相关原生库或模型。当前 APK 不包含 `.so` 或 `.mnn` 文件。`native/` 和 `MainActivity.java` 中的部分 SAM/MNN 实现属于遗留/保留代码，是否未来重新启用尚未决定；不得将它描述为当前正式 APK 已包含的能力。

构建脚本会对齐 APK 并签名。当前仓库确认的是本地 debug 构建流程；独立的生产签名配置尚未确认，不能把 debug keystore 当作正式签名方案。

## 3. APK 检查

至少检查文件存在、体积、哈希和 Manifest 元数据：

```powershell
$apk = Get-ChildItem .\dist\EatRecord-*.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Get-FileHash -Algorithm SHA256 $apk.FullName

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
& (Join-Path $bt.FullName 'aapt2.exe') dump badging $apk.FullName |
    Select-String 'package: name=|versionCode=|versionName='
& (Join-Path $bt.FullName 'apksigner.bat') verify --verbose $apk.FullName
```

同时确认 APK 中没有意外打包应按需下载的字体、测试文件、`.so` 或 `.mnn` 文件，并检查 `git diff --check`。如果未来正式决定重新启用 SAM/MNN，必须先同步更新构建流程、验证要求和本文档。

## 4. ADB 真机验证

涉及启动、权限、通知、相机、媒体导入、系统返回或动画时使用 ADB：

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb install -r $apk.FullName
& $adb shell am force-stop com.eatrecord.app
& $adb shell monkey -p com.eatrecord.app 1
& $adb shell pidof com.eatrecord.app
```

需要检查崩溃时，可在复现前清空日志，再读取关键异常：

```powershell
& $adb logcat -c
# 执行要验证的操作
& $adb logcat -d -v brief | Select-String 'FATAL EXCEPTION|AndroidRuntime|com.eatrecord.app'
```

记录测试设备、测试日期和结果。没有目标品牌设备时明确记录“未实机验证”。

## 5. Git 提交与标签

确认代码、版本、更新清单和交接文档内容正确后，执行：

```powershell
git status --short
git diff --check
git add <明确列出的文件>
git commit -m "Release <versionName>"
git tag v<versionName>
git push origin main --follow-tags
```

不要使用破坏性回滚命令覆盖用户已有改动。标签已存在时先检查远端状态，不要强行移动标签。

## 6. GitHub Release

正式 Release 的附件名不带 `-debug`。先复制构建产物，不改变 APK 内容：

```powershell
Copy-Item .\dist\EatRecord-<versionName>-debug.apk .\dist\EatRecord-<versionName>.apk -Force
gh release create v<versionName> .\dist\EatRecord-<versionName>.apk `
    --target main `
    --title "一蔬一饭 v<versionName>" `
    --notes-file <release-notes-file>
```

若 Release 已存在，使用 `gh release upload v<versionName> .\dist\EatRecord-<versionName>.apk --clobber`，并核对页面中的附件名和 SHA-256。当前 GitHub 仓库地址为 `https://github.com/CYNIC-CREEP/Eat_record.git`。

## 7. OpenList 发布

脚本为 `scripts/publish-openlist.ps1`，会读取 Manifest 版本，构建或复用 APK，并上传版本目录、源码包、云端 API 文件、字体、更新清单和教程：

```powershell
$env:OPENLIST_USERNAME = '<从本机安全凭据读取>'
$env:OPENLIST_PASSWORD = '<从本机安全凭据读取>'
powershell -ExecutionPolicy Bypass -File .\scripts\publish-openlist.ps1 -SkipBuild
```

不要把真实凭据保存到文档、脚本、PowerShell 历史或 Git。脚本默认连接 `http://47.97.215.111:5244`，远端根目录为 `/lanzou/Myapp/eat record`，并会在上传前删除指定的退休字体文件。

发布后检查脚本输出、OpenList 目录和更新清单，并重新下载远端 APK 与本地文件比对哈希。注意：脚本当前默认把 OpenList 安装包命名为 `EatRecord-<version>-debug.apk`，这与 GitHub 正式附件命名规则不同。

## 8. 更新入口待确认项

- `server/update-server-guide.md` 仍记录 `http://47.97.215.111/eat-record/update.json`，但当前客户端代码通过 OpenList API 获取更新清单。
- 公开域名、Caddy 反向代理、更新清单静态路由和 OpenList 是否需要签名，必须在实际服务器上确认；不要凭文档旧地址推断发布已生效。
- 如果发布脚本改写了 `dist/update.json` 或 `server/update.json`，发布后检查 diff，确认是否应作为本次版本元数据提交。

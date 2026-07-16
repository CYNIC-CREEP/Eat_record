# eat record 更新服务器说明

App 当前读取这个地址：

```text
http://47.97.215.111/eat-record/update.json
```

更新检测文件建议放在 ECS 的普通静态目录里，例如：

```text
/var/www/html/eat-record/update.json
```

APK 和源码仍然可以放在 OpenList / 蓝奏云目录：

```text
/lanzou/Myapp/eat record/0.4.21/安装包/EatRecord-0.4.21-debug.apk
/lanzou/Myapp/eat record/0.4.21/代码/EatRecord-0.4.21-source.zip
```

`update.json` 格式：

```json
{
  "versionCode": 35,
  "versionName": "0.4.22",
  "apkUrl": "http://47.97.215.111:5244/lanzou/Myapp/eat%20record/0.4.22/%E5%AE%89%E8%A3%85%E5%8C%85/EatRecord-0.4.22-debug.apk",
  "notes": [
    "新增检测更新",
    "优化体验"
  ]
}
```

以后发新版时，把 `versionCode` 改成更大的数字，把 `versionName` 和 `apkUrl` 改成新版即可。

注意：OpenList 的 `/d/...` 直链如果开启了签名保护，会返回 `401 Unauthorized / expire missing`，APP 无法直接读取。`update.json` 必须是浏览器直接打开就能看到 JSON 文本的公开地址。

自动发布命令：

```powershell
$env:OPENLIST_USERNAME="你的OpenList用户名"
$env:OPENLIST_PASSWORD="你的OpenList密码"
.\scripts\publish-openlist.ps1
```

package com.eatrecord.app;

import java.io.IOException;
import org.json.JSONObject;

/** Query the next distribution source only while no newer version has been found. */
final class AppUpdateCheck {
    interface Source {
        JSONObject read() throws Exception;
    }

    static JSONObject validate(JSONObject update) throws IOException {
        if (update == null || update.optInt("versionCode", 0) <= 0
                || update.optString("versionName", "").trim().isEmpty()) {
            throw new IOException("更新清单缺少有效版本信息");
        }
        return update;
    }

    static JSONObject fetch(int installedCode, Source primary, Source guest) throws Exception {
        JSONObject latest = null;
        Exception failure = null;
        for (Source source : new Source[]{primary, guest}) {
            try {
                JSONObject update = validate(source.read());
                if (update.optInt("versionCode") > installedCode) {
                    if (update.optString("apkPath", "").trim().isEmpty()
                            && update.optString("apkUrl", "").trim().isEmpty()) {
                        throw new IOException("更新清单缺少安装包地址");
                    }
                    return update;
                }
                if (latest == null || update.optInt("versionCode") > latest.optInt("versionCode")) {
                    latest = update;
                }
            } catch (Exception unavailable) {
                failure = unavailable;
            }
        }
        if (failure != null) {
            throw new IOException("部分更新来源暂时无法检测，请稍后重试", failure);
        }
        return latest;
    }
}

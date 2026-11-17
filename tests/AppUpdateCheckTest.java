package com.eatrecord.app;

import java.io.IOException;
import org.json.JSONObject;

/** Ordered distribution checks, including a reachable but stale primary source. */
public final class AppUpdateCheckTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static JSONObject update(int code) throws Exception {
        return new JSONObject().put("versionCode", code).put("versionName", "0.6." + code)
                .put("apkPath", AppDownloadClient.GUEST_ROOT + "/version/apk/app.apk");
    }

    private static void expectFailure(AppUpdateCheck.Source primary, AppUpdateCheck.Source guest) throws Exception {
        boolean failed = false;
        try {
            AppUpdateCheck.fetch(203, primary, guest);
        } catch (IOException expected) {
            failed = true;
        }
        check(failed, "An incomplete check must not claim the latest version");
    }

    public static void main(String[] args) throws Exception {
        int[] guestReads = {0};
        JSONObject primaryNew = update(204);
        JSONObject guestNew = update(205);
        JSONObject selected = AppUpdateCheck.fetch(203, () -> primaryNew, () -> {
            guestReads[0]++;
            return guestNew;
        });
        check(selected == primaryNew && guestReads[0] == 0, "Primary update ends the search immediately");

        JSONObject primaryOld = update(202);
        selected = AppUpdateCheck.fetch(203, () -> primaryOld, () -> guestNew);
        check(selected == guestNew, "A successfully read stale primary must query the guest source");
        selected = AppUpdateCheck.fetch(203, () -> { throw new IOException("synthetic primary failure"); }, () -> guestNew);
        check(selected == guestNew, "Guest update survives a primary connection failure");

        JSONObject same = update(203);
        check(AppUpdateCheck.fetch(203, () -> same, () -> primaryOld) == same, "Keep the highest valid non-update result");
        check(AppUpdateCheck.fetch(203, () -> primaryOld, () -> same) == same, "Guest current version is retained");
        check(AppUpdateCheck.fetch(203, () -> primaryOld, () -> update(202)).optInt("versionCode") == 202,
                "An installed Beta does not downgrade to the current formal version");

        JSONObject missingAddress = update(204);
        missingAddress.remove("apkPath");
        check(AppUpdateCheck.fetch(203, () -> missingAddress, () -> guestNew) == guestNew,
                "An unusable update address allows the second source");
        JSONObject missingVersion = new JSONObject().put("versionCode", 204);
        check(AppUpdateCheck.fetch(203, () -> missingVersion, () -> guestNew) == guestNew,
                "Malformed primary metadata allows a valid guest update");
        expectFailure(() -> primaryOld, () -> { throw new IOException("synthetic guest failure"); });
        expectFailure(() -> { throw new IOException("synthetic primary failure"); }, () -> same);
        expectFailure(() -> null, () -> new JSONObject());
        expectFailure(() -> update(-1), () -> update(0));
        check(primaryOld.optInt("versionCode") == 202 && !primaryOld.has("source"), "Checking preserves source metadata");
        System.out.println("PASS: " + checks + " ordered update-source checks");
    }
}

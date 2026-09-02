package com.eatrecord.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;

public final class MealReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_MEAL_REMINDER = "com.eatrecord.app.action.MEAL_REMINDER";
    public static final String EXTRA_OPEN_FROM_REMINDER = "open_from_meal_reminder";

    private static final String EXTRA_MEAL = "meal";
    private static final String PREFS = "eat_record_prefs";
    private static final String PROFILE_KEY = "profile_json";
    private static final String CHANNEL_ID = "meal_reminders";
    private static final String[] MEALS = {"breakfast", "lunch", "dinner"};
    private static final String[] DEFAULT_TIMES = {"08:00", "12:00", "18:30"};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ensureScheduled(context);
            return;
        }
        if (!ACTION_MEAL_REMINDER.equals(action) || !notificationsEnabled(context)) {
            return;
        }
        String meal = intent.getStringExtra(EXTRA_MEAL);
        int index = mealIndex(meal);
        if (index < 0) {
            return;
        }
        showMealNotification(context, index);
        scheduleMeal(context, index);
    }

    public static void ensureScheduled(Context context) {
        if (context == null) {
            return;
        }
        if (!notificationsEnabled(context)) {
            cancelAll(context);
            return;
        }
        createNotificationChannel(context);
        for (int i = 0; i < MEALS.length; i++) {
            scheduleMeal(context, i);
        }
    }

    public static void cancelAll(Context context) {
        if (context == null) {
            return;
        }
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null) {
                return;
            }
            for (int i = 0; i < MEALS.length; i++) {
                manager.cancel(alarmIntent(context, i));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scheduleMeal(Context context, int index) {
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null || index < 0 || index >= MEALS.length) {
                return;
            }
            int[] time = parseTime(notificationTime(context, index), DEFAULT_TIMES[index]);
            Calendar trigger = Calendar.getInstance();
            trigger.set(Calendar.HOUR_OF_DAY, time[0]);
            trigger.set(Calendar.MINUTE, time[1]);
            trigger.set(Calendar.SECOND, 0);
            trigger.set(Calendar.MILLISECOND, 0);
            if (trigger.getTimeInMillis() <= System.currentTimeMillis() + 5000L) {
                trigger.add(Calendar.DAY_OF_MONTH, 1);
            }
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), alarmIntent(context, index));
        } catch (Throwable ignored) {
        }
    }

    private static PendingIntent alarmIntent(Context context, int index) {
        Intent intent = new Intent(context, MealReminderReceiver.class);
        intent.setAction(ACTION_MEAL_REMINDER);
        intent.putExtra(EXTRA_MEAL, MEALS[index]);
        return PendingIntent.getBroadcast(context, 4100 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showMealNotification(Context context, int index) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            createNotificationChannel(context);
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            open.putExtra(EXTRA_OPEN_FROM_REMINDER, true);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 4200 + index, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String[] titles = {"早餐时间到啦", "午餐时间到啦", "晚餐时间到啦"};
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_photo_upload)
                    .setContentTitle(titles[index])
                    .setContentText("记录这一餐吃了什么呀？")
                    .setColor(Color.rgb(86, 191, 112))
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build();
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(4300 + index, notification);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "三餐提醒", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("在设定的早餐、午餐和晚餐时间提醒记录");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        } catch (Throwable ignored) {
        }
    }

    private static boolean notificationsEnabled(Context context) {
        return readProfile(context).optBoolean("mealNotificationsEnabled", false);
    }

    private static String notificationTime(Context context, int index) {
        JSONObject profile = readProfile(context);
        String[] keys = {"breakfastNotificationTime", "lunchNotificationTime", "dinnerNotificationTime"};
        return profile.optString(keys[index], DEFAULT_TIMES[index]);
    }

    private static JSONObject readProfile(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONObject(prefs.getString(PROFILE_KEY, "{}"));
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static int[] parseTime(String value, String fallback) {
        String source = value == null ? "" : value.trim();
        if (!source.matches("\\d{1,2}:\\d{2}")) {
            source = fallback;
        }
        try {
            String[] parts = source.split(":");
            int hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
            int minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
            return new int[]{hour, minute};
        } catch (Throwable ignored) {
            return new int[]{8, 0};
        }
    }

    private static int mealIndex(String meal) {
        for (int i = 0; i < MEALS.length; i++) {
            if (MEALS[i].equals(meal)) {
                return i;
            }
        }
        return -1;
    }
}

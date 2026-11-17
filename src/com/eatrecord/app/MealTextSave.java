package com.eatrecord.app;

import org.json.JSONObject;

/** Latest-value text merge. The caller supplies the same lock used by all diary writers. */
final class MealTextSave {
    interface Store {
        String read() throws Exception;
        boolean write(String value) throws Exception;
        default boolean restoring() { return false; }
    }

    static final class Result {
        final boolean saved;
        final JSONObject days;
        final String raw;
        private Result(boolean saved, JSONObject days, String raw) {
            this.saved = saved;
            this.days = days;
            this.raw = raw;
        }
    }

    private MealTextSave() { }

    static Result save(Store store, Object lock, String date, String key, String input, boolean inline) {
        String value = inline ? ManualMealText.clean(input) : ManualMealText.normalize(input);
        if (value == null || date == null || date.isEmpty() || key == null || key.isEmpty()) return failed();
        synchronized (lock) {
            try {
                if (store.restoring()) return failed();
                String original = store.read();
                JSONObject latest = new JSONObject(original);
                JSONObject day = latest.optJSONObject(date);
                JSONObject current = day == null ? null : day.optJSONObject(key);
                if (current == null && value.isEmpty()) return new Result(true, latest, original);
                JSONObject updated = inline ? ManualMealText.updatedInlineMeal(current, value)
                        : ManualMealText.updatedMeal(current, value);
                if (updated == null) return failed();
                if (day == null) day = new JSONObject();
                day.put(key, updated);
                latest.put(date, day);
                String raw = latest.toString();
                // Always retry the durable write. A failed SharedPreferences commit may already
                // have published its intended value to memory.
                return store.write(raw) ? new Result(true, latest, raw) : failed();
            } catch (Exception | OutOfMemoryError unavailable) {
                return failed();
            }
        }
    }

    static Result latest(Store store,Object lock) {
        synchronized(lock) {
            try {
                if(store.restoring())return failed();
                String raw=store.read();
                return new Result(true,new JSONObject(raw),raw);
            }catch(Exception | OutOfMemoryError unavailable){return failed();}
        }
    }

    private static Result failed() { return new Result(false, null, null); }
}

package com.eatrecord.app;

import java.util.LinkedHashMap;

/** Short-lived hints from explicit optional-parameter refusals, never model/permission guesses. */
final class FoodRequestCompatibility {
    // TUNING covers only the optional token cap; selected thinking controls are never dropped.
    static final int STREAM = 1, SCHEMA = 2, TUNING = 4;
    private final int capacity;
    private final long ttlMs;
    private final LinkedHashMap<String, Hint> hints = new LinkedHashMap<>();

    FoodRequestCompatibility(int capacity, long ttlMs) {
        this.capacity = capacity;
        this.ttlMs = ttlMs;
    }

    synchronized int rejected(String scope, long now) {
        Hint hint = hints.get(scope);
        if (hint == null) return 0;
        if (now < hint.at || now - hint.at >= ttlMs) { hints.remove(scope); return 0; }
        return hint.flags;
    }

    synchronized void remember(String scope, int flag, long now) {
        int previous = rejected(scope, now);
        hints.remove(scope);
        hints.put(scope, new Hint(previous | flag, now));
        while (hints.size() > capacity) hints.remove(hints.keySet().iterator().next());
    }

    private static final class Hint {
        final int flags;
        final long at;
        Hint(int flags, long at) { this.flags = flags; this.at = at; }
    }
}

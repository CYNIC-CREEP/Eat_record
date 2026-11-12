package com.eatrecord.app;

import java.util.LinkedHashMap;

/** Bounded visual origins. The caller's accepted routes own navigation. */
final class NavigationTransitionHistory {
    static final class Entry {
        final String parent;
        final String child;
        final String sourceTag;

        Entry(String parent, String child, String sourceTag) {
            this.parent = parent;
            this.child = child;
            this.sourceTag = sourceTag;
        }

        String key() {
            return parent + "\n" + sourceTag;
        }

        String identity() {
            return parent + "\n" + child + "\n" + sourceTag;
        }
    }

    private final int limit;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    NavigationTransitionHistory() {
        this(64);
    }

    NavigationTransitionHistory(int limit) {
        this.limit = Math.max(1, Math.min(64, limit));
    }

    Entry record(String parent, String child, String sourceTag) {
        if (!valid(parent) || !valid(child) || !valid(sourceTag) || parent.equals(child)) {
            return null;
        }
        Entry entry = new Entry(parent, child, sourceTag);
        entries.remove(child);
        entries.put(child, entry);
        if (entries.size() > limit) {
            entries.remove(entries.keySet().iterator().next());
        }
        return entry;
    }

    Entry get(String child) {
        return entries.get(child);
    }

    void forget(String child) {
        entries.remove(child);
    }

    Entry resolve(String child, String acceptedParent) {
        if (!valid(child) || !valid(acceptedParent) || child.equals(acceptedParent)) {
            return null;
        }
        Entry match = null;
        String route = child;
        // A route can appear at most once in an acyclic chain of this size.
        // Continue beyond a match so stale cyclic origins safely fall back.
        for (int remaining = entries.size(); remaining > 0; remaining--) {
            Entry entry = entries.get(route);
            if (entry == null) {
                return match;
            }
            if (match == null && entry.parent.equals(acceptedParent)) {
                match = entry;
            }
            route = entry.parent;
        }
        return entries.get(route) == null ? match : null;
    }

    private static boolean valid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

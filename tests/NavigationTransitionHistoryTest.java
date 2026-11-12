package com.eatrecord.app;

/** Route ownership and origin lookup contracts, independent of Android rendering. */
public final class NavigationTransitionHistoryTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void nestedAndSkippedReturns() {
        NavigationTransitionHistory history = new NavigationTransitionHistory();
        NavigationTransitionHistory.Entry other = history.record("me", "other", "setting_row_other");
        NavigationTransitionHistory.Entry hints = history.record("other", "hints", "setting_row_hints");
        NavigationTransitionHistory.Entry section = history.record("hints", "hint:skip:two", "setting_row_two");

        check(history.get("hint:skip:two") == section, "A nested page retains its direct visual origin");
        check(history.resolve("hint:skip:two", "hints") == section, "Toolbar return resolves the section row");
        check(history.resolve("hint:skip:two", "other") == hints, "A system return skipping the hints page resolves the real parent's row");
        check(history.resolve("hint:skip:two", "me") == other, "A return may skip several accepted ancestors");
        check(history.resolve("hint:skip:two", "unrelated") == null, "Unaccepted origins cannot select another parent");
        check(history.get("hints") == hints, "Resolving an origin does not pop the parent's visual origin");
        check(history.resolve("hints", "other") == hints, "A later parent return retains its own entry");
        check(history.resolve("me", "other") == null, "A forward destination is not an ancestor origin");
    }

    private static void reopenedPagesAndRedirection() {
        NavigationTransitionHistory history = new NavigationTransitionHistory();
        NavigationTransitionHistory.Entry fromBooks = history.record("footprints", "favorites", "footprints_favorites");
        check(history.resolve("favorites", "footprints") == fromBooks, "Favorites can return to the bookshelf");
        NavigationTransitionHistory.Entry fromSecurity = history.record("security", "favorites", "setting_row_favorites");
        check(history.get("favorites") == fromSecurity, "Reopening a shared destination replaces its visual origin");
        check(history.resolve("favorites", "security") == fromSecurity, "The latest accepted parent is resolved");
        check(history.resolve("favorites", "footprints") == null, "An earlier parent does not override the current route");
        check(!fromBooks.key().equals(fromSecurity.key()), "Different parent entries have separate motion keys");
        check(!fromBooks.identity().equals(fromSecurity.identity()), "A shared page preserves its full parent-child identity");

        NavigationTransitionHistory.Entry one = history.record("archive", "chat:2026-11-10", "chat_row");
        NavigationTransitionHistory.Entry two = history.record("archive", "chat:2026-11-11", "chat_row");
        check(one.key().equals(two.key()), "The source key identifies one entry within a parent");
        check(!one.identity().equals(two.identity()), "A reused entry leading to different content has a distinct accepted identity");
        check(one.key().equals("archive\nchat_row"), "The source key matches the renderer contract");
        check(two.identity().equals("archive\nchat:2026-11-11\nchat_row"), "The identity includes actual destination content");
    }

    private static void invalidRequestsDoNotMutateOrigins() {
        NavigationTransitionHistory history = new NavigationTransitionHistory();
        NavigationTransitionHistory.Entry original = history.record("me", "other", "entry");
        for (String invalid : new String[]{null, "", " ", "\t\r\n"}) {
            check(history.record(invalid, "other", "entry") == null, "An absent parent cannot register an origin");
            check(history.record("me", invalid, "entry") == null, "An absent child cannot register an origin");
            check(history.record("me", "other", invalid) == null, "An absent source cannot register an origin");
            check(history.resolve(invalid, "me") == null, "An absent source route cannot resolve");
            check(history.resolve("other", invalid) == null, "An absent accepted parent cannot resolve");
            check(history.get("other") == original, "An invalid request preserves a valid previous origin");
        }
        check(history.record("other", "other", "entry") == null, "Same-page redraws do not register navigation");
        check(history.resolve("other", "other") == null, "Same-page changes cannot animate as a return");
        check(history.get(null) == null, "Lookup without a route safely fails");
        check(history.resolve("missing", "me") == null, "Missing child history safely falls back");
        check(history.get("other") == original, "Queries preserve the registry");
    }

    private static void staleAndCyclicOrigins() {
        NavigationTransitionHistory history = new NavigationTransitionHistory();
        NavigationTransitionHistory.Entry leaf = history.record("missing-parent", "leaf", "leaf-entry");
        check(history.resolve("leaf", "missing-parent") == leaf, "A direct return works without history for the root parent");
        check(history.resolve("leaf", "root") == null, "A missing ancestor is not invented");

        history.record("b", "a", "entry-a");
        history.record("a", "b", "entry-b");
        history.record("a", "c", "entry-c");
        check(history.resolve("a", "b") == null, "A direct matching edge within a stale cycle is rejected");
        check(history.resolve("c", "a") == null, "A path entering a stale cycle safely falls back");
        check(history.resolve("c", "root") == null, "A cycle cannot loop indefinitely looking for a parent");
        NavigationTransitionHistory.Entry corrected = history.record("root", "a", "corrected-a");
        check(history.resolve("c", "root") == corrected, "Replacing a stale cyclic origin restores resolution");
        check(history.resolve("b", "root") == corrected, "Corrected origin is reusable by another descendant");
    }

    private static void boundedEviction() {
        NavigationTransitionHistory history = new NavigationTransitionHistory();
        for (int i = 0; i < 80; i++) {
            history.record("root", "child-" + i, "entry-" + i);
        }
        for (int i = 0; i < 80; i++) {
            check((history.get("child-" + i) != null) == (i >= 16), "Default registry retains at most the latest 64 origins");
        }

        NavigationTransitionHistory limited = new NavigationTransitionHistory(2);
        limited.record("root", "a", "a1");
        limited.record("root", "b", "b1");
        NavigationTransitionHistory.Entry reopened = limited.record("root", "a", "a2");
        limited.record("root", "c", "c1");
        check(limited.get("a") == reopened, "A reopened destination is a recent origin");
        check(limited.get("b") == null, "Replacement refreshes eviction order");
        check(limited.resolve("c", "root") == limited.get("c"), "Eviction does not invalidate independent recent entries");

        NavigationTransitionHistory chain = new NavigationTransitionHistory(2);
        chain.record("root", "a", "a");
        chain.record("a", "b", "b");
        chain.record("b", "c", "c");
        check(chain.resolve("c", "a") == chain.get("b"), "A surviving skipped edge remains usable");
        check(chain.resolve("c", "root") == null, "An evicted ancestor safely falls back");
        check(chain.resolve("c", "b") == chain.get("c"), "The direct parent remains valid after eviction");

        for (int limit : new int[]{Integer.MIN_VALUE, -1, 0, 1}) {
            NavigationTransitionHistory small = new NavigationTransitionHistory(limit);
            small.record("root", "a", "a");
            small.record("root", "b", "b");
            check(small.get("a") == null && small.get("b") != null, "A nonpositive limit remains bounded to one usable origin");
        }
        NavigationTransitionHistory oversized = new NavigationTransitionHistory(Integer.MAX_VALUE);
        for (int i = 0; i < 65; i++) {
            oversized.record("root", "item-" + i, "entry-" + i);
        }
        check(oversized.get("item-0") == null && oversized.get("item-64") != null, "A custom limit cannot exceed the 64-origin ceiling");
    }

    private static void untrackedEntryInvalidation() {
        NavigationTransitionHistory history=new NavigationTransitionHistory();
        history.record("me","about","about-row");
        history.record("about","updates","updates-row");
        history.forget("updates");
        if(history.get("updates")!=null||history.resolve("updates","me")!=null)
            throw new AssertionError("Untracked entry reused a stale visual source");
        if(history.get("about")==null)throw new AssertionError("Invalidation removed its parent source");
    }

    public static void main(String[] args) {
        untrackedEntryInvalidation();
        nestedAndSkippedReturns();
        reopenedPagesAndRedirection();
        invalidRequestsDoNotMutateOrigins();
        staleAndCyclicOrigins();
        boundedEviction();
        System.out.println("PASS: " + checks + " bounded navigation origin and accepted-parent checks");
    }
}

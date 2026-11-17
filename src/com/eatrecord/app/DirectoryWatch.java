package com.eatrecord.app;

import android.os.FileObserver;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/** FileObserver has one callback per native descriptor, so directory clients share one observer. */
final class DirectoryWatch {
    interface Listener { void changed(int event, String name); }

    private static final int EVENTS = FileObserver.CLOSE_WRITE | FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;
    private static final Object LOCK = new Object();
    private static final HashMap<String, Entry> WATCHES = new HashMap<>();

    static final class Subscription implements AutoCloseable {
        private final Entry entry;
        private final Listener listener;
        private volatile boolean closed;

        private Subscription(Entry entry, Listener listener) {
            this.entry = entry;
            this.listener = listener;
        }

        @Override public void close() {
            synchronized (LOCK) {
                if (closed) return;
                closed = true;
                entry.listeners.remove(this);
                if (entry.listeners.isEmpty()) {
                    WATCHES.remove(entry.path);
                    entry.observer.stopWatching();
                }
            }
        }
    }

    private static final class Entry {
        final String path;
        final ArrayList<Subscription> listeners = new ArrayList<>();
        final FileObserver observer;

        Entry(String path) {
            this.path = path;
            observer = new FileObserver(path, EVENTS) {
                @Override public void onEvent(int event, String name) { dispatch(event, name); }
            };
        }

        void dispatch(int event, String name) {
            ArrayList<Subscription> recipients;
            synchronized (LOCK) { recipients = new ArrayList<>(listeners); }
            // Subscribers may acquire their own locks or change subscriptions while handling an event.
            for (Subscription recipient : recipients) {
                if (recipient.closed) continue;
                try { recipient.listener.changed(event, name); }
                catch (RuntimeException unavailable) {
                    android.util.Log.w("EatRecord", "Directory change listener unavailable: "
                            + unavailable.getClass().getSimpleName());
                }
            }
        }
    }

    private DirectoryWatch() { }

    static Subscription subscribe(File directory, Listener listener) {
        String path;
        try { path = directory.getCanonicalPath(); }
        catch (IOException unavailable) { path = directory.getAbsolutePath(); }
        synchronized (LOCK) {
            Entry entry = WATCHES.get(path);
            if (entry == null) {
                entry = new Entry(path);
                WATCHES.put(path, entry);
            }
            Subscription subscription = new Subscription(entry, listener);
            entry.listeners.add(subscription);
            entry.observer.startWatching();
            return subscription;
        }
    }
}

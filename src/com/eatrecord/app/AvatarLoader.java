package com.eatrecord.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** One small avatar cache. Views own their displayed bitmap references after cache eviction. */
final class AvatarLoader {
    private final File file;
    private final Executor executor;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final WeakHashMap<ImageView, Target> targets = new WeakHashMap<>();
    private final HashMap<String, ArrayList<Pending>> inFlight = new HashMap<>();
    private final DirectoryWatch.Subscription observer;
    private long epoch;
    private String cachedKey;
    private Bitmap cached;
    private volatile boolean closed;
    private final Runnable fileChanged = this::invalidate;

    AvatarLoader(File file, Executor executor) {
        this.file = file;
        this.executor = executor;
        observer = DirectoryWatch.subscribe(file.getParentFile(), (event, path) -> {
            if (!closed && file.getName().equals(path)) {
                ui.removeCallbacks(fileChanged);
                ui.post(fileChanged);
            }
        });
    }

    void load(ImageView view, int size, View fallback) {
        if (closed) return;
        Target previous = targets.remove(view);
        if (previous != null) previous.dispose();
        Target target = new Target(view, Math.min(256, Math.max(64, size)), fallback);
        targets.put(view, target);
        view.addOnAttachStateChangeListener(target);
        if (view.isAttachedToWindow()) request(target);
    }

    void invalidate() {
        if (closed) return;
        clear();
        for (Target target : new ArrayList<>(targets.values())) {
            ImageView view = target.view.get();
            if (view != null && view.isAttachedToWindow()) request(target);
        }
    }

    void clear() {
        epoch++;
        cachedKey = null;
        cached = null;
        inFlight.clear();
    }

    void close() {
        closed = true;
        observer.close();
        ui.removeCallbacks(fileChanged);
        clear();
        for (Target target : new ArrayList<>(targets.values())) target.dispose();
        targets.clear();
    }

    private String version() {
        return file.isFile() ? file.length() + ":" + file.lastModified() : "missing";
    }

    private void request(Target target) {
        ImageView view = target.view.get();
        if (closed || view == null || !view.isAttachedToWindow()) return;
        long token = ++target.token;
        String version = version();
        String key = version + "@" + target.size;
        if (key.equals(cachedKey)) {
            target.show(cached);
            return;
        }
        target.show(null);
        if ("missing".equals(version)) return;
        Pending pending = new Pending(target, token);
        ArrayList<Pending> subscribers = inFlight.get(key);
        if (subscribers != null) {
            subscribers.add(pending);
            return;
        }
        ArrayList<Pending> waiting = new ArrayList<>();
        waiting.add(pending);
        inFlight.put(key, waiting);
        long generation = epoch;
        int requestedSize = target.size;
        try {
            executor.execute(() -> {
                Bitmap bitmap = decode(requestedSize);
                ui.post(() -> {
                    if (closed || generation != epoch || inFlight.get(key) != waiting) {
                        if(bitmap!=null)bitmap.recycle();
                        return;
                    }
                    inFlight.remove(key);
                    if (!version.equals(version())) {
                        if(bitmap!=null)bitmap.recycle();
                        invalidate();
                        return;
                    }
                    cachedKey = key;
                    cached = bitmap;
                    for (Pending result : waiting) {
                        Target recipient = result.target.get();
                        ImageView image = recipient == null ? null : recipient.view.get();
                        if (recipient != null && recipient.token == result.token && image != null
                                && image.isAttachedToWindow() && targets.get(image) == recipient) recipient.show(bitmap);
                    }
                });
            });
        } catch (RejectedExecutionException stopped) {
            inFlight.remove(key);
        }
    }

    private Bitmap decode(int target) {
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > target * 2
                    || bounds.outHeight / options.inSampleSize > target * 2) options.inSampleSize *= 2;
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (decoded == null) return null;
            float scale = Math.min(1f, target / (float) Math.max(decoded.getWidth(), decoded.getHeight()));
            if (scale == 1f) return decoded;
            Bitmap small = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * scale)),
                    Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            if (small != decoded) decoded.recycle();
            return small;
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if(decoded!=null&&!decoded.isRecycled())decoded.recycle();
            return null;
        }
    }

    private final class Target implements View.OnAttachStateChangeListener {
        final WeakReference<ImageView> view;
        final WeakReference<View> fallback;
        final int size;
        long token;
        Target(ImageView view, int size, View fallback) {
            this.view = new WeakReference<>(view);
            this.fallback = new WeakReference<>(fallback);
            this.size = size;
        }
        void show(Bitmap bitmap) {
            ImageView image = view.get();
            if (image != null) image.setImageBitmap(bitmap);
            View placeholder = fallback.get();
            if (placeholder != null) placeholder.setVisibility(bitmap == null ? View.VISIBLE : View.INVISIBLE);
        }
        void dispose() {
            token++;
            ImageView image = view.get();
            if (image != null) image.removeOnAttachStateChangeListener(this);
        }
        @Override public void onViewAttachedToWindow(View view) { request(this); }
        @Override public void onViewDetachedFromWindow(View view) { token++; }
    }

    private static final class Pending {
        final WeakReference<Target> target;
        final long token;
        Pending(Target target, long token) { this.target = new WeakReference<>(target); this.token = token; }
    }
}

package com.eatrecord.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Application-owned rendering/writing queue. Cancelled results are removed before releasing ownership. */
final class NotebookShareExport {
    interface Callback {
        /** Return true when the still-current screen accepts the completed image. */
        boolean ready(Request request, Uri image, boolean failed);
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "notebook-share");
        thread.setDaemon(true);
        return thread;
    });

    static final class Request implements Runnable {
        private final ContentResolver resolver;
        private final NotebookShareRenderer.Snapshot snapshot;
        private final String date;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Callback callback;
        private Future<?> future;

        private Request(ContentResolver resolver, NotebookShareRenderer.Snapshot snapshot, String date, Callback callback) {
            this.resolver = resolver;
            this.snapshot = snapshot;
            this.date = date;
            this.callback = callback;
        }
        void cancel() {
            cancelled.set(true);
            callback = null;
            if (future != null) future.cancel(true);
        }
        @Override public void run() {
            Bitmap bitmap = null;
            Uri image = null;
            boolean failed = false;
            try {
                if (cancelled.get()) return;
                bitmap = new NotebookShareRenderer(snapshot, cancelled::get).render();
                image = write(resolver, bitmap, date, cancelled::get);
            } catch (Exception | OutOfMemoryError unavailable) {
                failed = !cancelled.get();
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            final Uri completed = image;
            final boolean failure = failed;
            if (!MAIN.post(() -> {
                Callback target = callback;
                callback = null;
                boolean accepted = false;
                try {
                    if (!cancelled.get() && target != null) accepted = target.ready(this, completed, failure);
                } finally {
                    if (!accepted && completed != null) WORKER.execute(() -> remove(resolver, completed));
                }
            }) && completed != null) remove(resolver, completed);
        }
    }

    private NotebookShareExport() { }

    static Request start(ContentResolver resolver, NotebookShareRenderer.Snapshot snapshot, String date, Callback callback) {
        Request request = new Request(resolver, snapshot, date, callback);
        request.future = WORKER.submit(request);
        return request;
    }

    static Uri write(ContentResolver resolver, Bitmap bitmap, String date, BooleanSupplier cancelled) throws Exception {
        return ShareImageTransaction.write(new ShareImageTransaction.Sink<Uri>() {
            @Override public Uri insert() {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "eat_record_" + date.replace("-", "") + "_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/一蔬一饭");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }
            @Override public OutputStream open(Uri image) throws Exception { return resolver.openOutputStream(image); }
            @Override public void publish(Uri image) throws Exception {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    if (resolver.update(image, values, null, null) != 1)
                        throw new IOException("Image publication failed");
                }
            }
            @Override public void delete(Uri image) { resolver.delete(image, null, null); }
        }, output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), cancelled);
    }

    private static void remove(ContentResolver resolver, Uri image) {
        try { resolver.delete(image, null, null); }
        catch (RuntimeException unavailable) {
            android.util.Log.w("EatRecord", "Cancelled share image cleanup unavailable");
        }
    }
}

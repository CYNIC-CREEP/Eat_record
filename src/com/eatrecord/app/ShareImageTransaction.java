package com.eatrecord.app;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** One inserted image is either fully written and published, or removed on every failure path. */
final class ShareImageTransaction {
    interface Sink<T> {
        T insert() throws Exception;
        OutputStream open(T item) throws Exception;
        void publish(T item) throws Exception;
        void delete(T item) throws Exception;
    }
    interface Encoder { boolean write(OutputStream output) throws Exception; }

    private ShareImageTransaction() { }

    static <T> T write(Sink<T> sink, Encoder encoder, BooleanSupplier cancelled) throws Exception {
        check(cancelled);
        T item = sink.insert();
        if (item == null) throw new IOException("Image destination unavailable");
        boolean completed = false;
        Throwable failure = null;
        try {
            check(cancelled);
            try (OutputStream output = sink.open(item)) {
                if (output == null) throw new IOException("Image output unavailable");
                check(cancelled);
                if (!encoder.write(output)) throw new IOException("Image encoding failed");
                check(cancelled);
            }
            check(cancelled);
            sink.publish(item);
            check(cancelled);
            completed = true;
            return item;
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        } finally {
            if (!completed) {
                try { sink.delete(item); }
                catch (Exception cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    private static void check(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new CancellationException("Image share cancelled");
    }
}

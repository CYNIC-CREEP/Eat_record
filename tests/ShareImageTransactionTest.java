package com.eatrecord.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShareImageTransactionTest {
    private static int checks;
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static final class Sink implements ShareImageTransaction.Sink<String> {
        final List<String> events = new ArrayList<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        String failure = "";
        String cancelAt = "";
        int deleted;
        boolean closed;
        @Override public String insert() throws Exception {
            events.add("insert");
            if ("insert".equals(failure)) throw new IOException("insert");
            if ("insert-null".equals(failure)) return null;
            if ("insert".equals(cancelAt)) cancelled.set(true);
            return "image-1";
        }
        @Override public OutputStream open(String item) throws Exception {
            events.add("open");
            if ("open".equals(failure)) throw new IOException("open");
            if ("open-null".equals(failure)) return null;
            if ("open".equals(cancelAt)) cancelled.set(true);
            return new OutputStream() {
                private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                @Override public void write(int value) throws IOException {
                    if ("write".equals(failure)) throw new IOException("write");
                    bytes.write(value);
                }
                @Override public void close() throws IOException {
                    closed = true;
                    events.add("close");
                    if ("close".equals(failure)) throw new IOException("close");
                    if ("close".equals(cancelAt)) cancelled.set(true);
                    super.close();
                }
            };
        }
        @Override public void publish(String item) throws Exception {
            events.add("publish");
            if ("publish".equals(failure)) throw new IOException("publish");
            if ("publish".equals(cancelAt)) cancelled.set(true);
        }
        @Override public void delete(String item) throws Exception {
            events.add("delete");
            deleted++;
            if ("delete".equals(failure)) throw new IOException("delete");
        }
        boolean encode(OutputStream output) throws Exception {
            events.add("encode");
            if ("encode".equals(failure)) throw new IOException("encode");
            if ("encode-error".equals(failure)) throw new OutOfMemoryError("encode");
            if ("encode".equals(cancelAt)) cancelled.set(true);
            output.write(new byte[]{1, 2, 3});
            return !"encode-false".equals(failure);
        }
    }
    public static void main(String[] arguments) throws Exception {
        Sink success = new Sink();
        String image = ShareImageTransaction.write(success, success::encode, success.cancelled::get);
        check("image-1".equals(image), "returns completed image identity");
        check(success.events.equals(Arrays.asList("insert", "open", "encode", "close", "publish")), "closes output before publishing");
        check(success.deleted == 0 && success.closed, "keeps only a closed published image");

        for (String failure : new String[]{"insert", "insert-null", "open", "open-null", "encode", "encode-false", "encode-error", "write", "close", "publish"}) {
            Sink sink = new Sink();
            sink.failure = failure;
            boolean rejected = false;
            try { ShareImageTransaction.write(sink, sink::encode, sink.cancelled::get); }
            catch (IOException | OutOfMemoryError expected) { rejected = true; }
            check(rejected, "rejects " + failure);
            check(sink.deleted == (failure.startsWith("insert") ? 0 : 1), "cleans only its inserted row after " + failure);
            if (!failure.startsWith("insert") && !failure.startsWith("open")) check(sink.closed, "closes stream after " + failure);
            if (!"publish".equals(failure)) check(!sink.events.contains("publish"), "does not publish incomplete " + failure);
        }

        Sink preCancelled = new Sink();
        preCancelled.cancelled.set(true);
        try { ShareImageTransaction.write(preCancelled, preCancelled::encode, preCancelled.cancelled::get); }
        catch (CancellationException expected) { }
        check(preCancelled.events.isEmpty(), "cancel before work creates no destination");
        for (String phase : new String[]{"insert", "open", "encode", "close", "publish"}) {
            Sink sink = new Sink();
            sink.cancelAt = phase;
            boolean rejected = false;
            try { ShareImageTransaction.write(sink, sink::encode, sink.cancelled::get); }
            catch (CancellationException expected) { rejected = true; }
            check(rejected && sink.deleted == 1, "cancellation cleans row after " + phase);
            if (!"insert".equals(phase)) check(sink.closed, "cancellation closes stream after " + phase);
        }

        Sink cleanup = new Sink();
        cleanup.failure = "delete";
        try {
            ShareImageTransaction.write(cleanup, output -> { throw new IOException("primary"); }, cleanup.cancelled::get);
            throw new AssertionError("write error was swallowed");
        } catch (IOException expected) {
            check("primary".equals(expected.getMessage()), "retains primary failure");
            check(expected.getSuppressed().length == 1 && cleanup.deleted == 1, "retains cleanup failure without leaking ownership silently");
        }
        System.out.println("PASS: " + checks + " image publication, failure cleanup and cancellation checks");
    }
}

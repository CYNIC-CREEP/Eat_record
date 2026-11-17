package com.eatrecord.app;

/** Monotonic frame intervals, with an optional bound for skipped drawing opportunities. */
final class AnimationFrameClock {
    static final double MAX_PRESENTED_SECONDS = 1d / 30d;
    private long previous = Long.MIN_VALUE;
    private final double maximumStep;

    AnimationFrameClock() {
        this(Double.POSITIVE_INFINITY);
    }

    AnimationFrameClock(double maximumStep) {
        this.maximumStep = maximumStep;
    }

    void start(long now) {
        previous = now;
    }

    void reset() {
        previous = Long.MIN_VALUE;
    }

    double advance(long frameTime) {
        if (previous == Long.MIN_VALUE) {
            previous = frameTime;
            return 0;
        }
        if (frameTime <= previous) return 0;
        double seconds = (frameTime - previous) / 1_000_000_000d;
        previous = frameTime;
        return Math.min(seconds, maximumStep);
    }
}

package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.view.Choreographer;

/** A small, display-synced critically damped spring shared by direct-manipulation UI. */
final class GestureSpring implements Choreographer.FrameCallback {
    interface Listener { void onUpdate(float value); void onEnd(); }
    private final Listener listener;
    private final float responseSeconds;
    private boolean running;
    private float value, velocity, target, origin, initialVelocity;
    private long startNanos;
    private long generation;

    GestureSpring(Listener listener) { this(listener,UiTokens.GESTURE_RESPONSE_SECONDS); }
    GestureSpring(Listener listener,float responseSeconds) {
        this.listener = listener;
        this.responseSeconds = responseSeconds;
    }
    boolean isRunning() { return running; }
    float velocity() { return velocity; }

    void cancel() {
        generation++;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    void start(float current, float destination, float velocityPerSecond) {
        cancel();
        value = origin = current;
        target = destination;
        velocity = initialVelocity = velocityPerSecond;
        // Do not spend the first display frame reporting the unchanged release position.
        startNanos = System.nanoTime();
        long token = generation;
        if (!ValueAnimator.areAnimatorsEnabled()
                || Math.abs(value - target) < .001f && Math.abs(velocity) < .001f) {
            value = target;
            velocity = 0;
            listener.onUpdate(value);
            if (generation == token) listener.onEnd();
            return;
        }
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        long token = generation;
        double seconds = Math.max(0d,(frameTimeNanos - startNanos) / 1_000_000_000d);
        // Damping ratio 1.0: no decorative overshoot or fixed-duration restart.
        double omega = 2d * Math.PI / responseSeconds;
        double displacement = origin - target;
        double b = initialVelocity + omega * displacement;
        double decay = Math.exp(-omega * seconds);
        value = (float) (target + (displacement + b * seconds) * decay);
        velocity = (float) ((initialVelocity - omega * b * seconds) * decay);
        boolean finished = Math.abs(value - target) < .25f && Math.abs(velocity) < 2f
                || seconds >= 2d || !ValueAnimator.areAnimatorsEnabled();
        if (finished) { value = target; velocity = 0; }
        listener.onUpdate(value);
        if (!running || generation != token) return; // An update may detach or retarget the owner.
        if (finished) { running = false; listener.onEnd(); }
        else Choreographer.getInstance().postFrameCallback(this);
    }
}

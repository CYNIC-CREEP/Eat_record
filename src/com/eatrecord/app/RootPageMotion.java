package com.eatrecord.app;

/**
 * Continuous position and requested destination for the three root pages.
 * No view ownership, rendering, clock, preferences or diary writes live here.
 */
final class RootPageMotion {
    private static final int LAST_PAGE = 2;
    private int target, priorTarget, source, direction;
    private float position, origin, displacement, overscroll, animationMin, animationMax;
    private boolean touching;

    RootPageMotion(int index) { reset(index); }

    private static int bound(int index) { return Math.max(0, Math.min(LAST_PAGE, index)); }
    private static float bound(float value) { return Math.max(0, Math.min(LAST_PAGE, value)); }

    float position() { return position; }
    int target() { return target; }
    float overscroll() { return overscroll; }
    /** True for the lifetime of the active grab, including before horizontal slop. */
    boolean isDragging() { return touching; }

    void reset(int index) {
        target = priorTarget = source = bound(index);
        position = origin = target;
        displacement = overscroll = 0;
        direction = 0;
        animationMin = animationMax = position;
        touching = false;
    }

    /** visibleSource is the nearest presented page, not an old transition's from/to pair. */
    void grab(int visibleSource) {
        source = bound(visibleSource);
        priorTarget = target;
        origin = position;
        displacement = overscroll = 0;
        direction = 0;
        touching = true;
    }

    /** The caller also supplies the final UP delta, even if Android omitted its last MOVE. */
    void drag(float deltaPages) {
        if (!touching || !Float.isFinite(deltaPages)) return;
        displacement = deltaPages;
        direction = Math.abs(deltaPages) > .001f ? (deltaPages > 0 ? 1 : -1) : 0;
        double proposed = (double) origin + deltaPages;
        position = (float) Math.max(0d, Math.min(LAST_PAGE, proposed));
        // Root pages have hard boundaries: no translation or elastic feedback outside 0..2.
        overscroll = 0;
    }

    int release(float velocityPagesPerSecond, boolean cancelled) {
        if (!touching) return target;
        if (!Float.isFinite(velocityPagesPerSecond)) velocityPagesPerSecond = 0;
        if (cancelled) {
            target = source;
        } else if (direction == 0) {
            // Touching a moving sheet without dragging resumes its current request.
            target = priorTarget;
        } else {
            boolean opposing = direction * (priorTarget - origin) < 0;
            boolean commit = Math.abs(displacement) > .35f
                    || Math.abs(displacement) > .03f && direction * velocityPagesPerSecond > .55f;
            if (commit) {
                // Same-direction grabs append to the pending destination. Reversing
                // starts at the actual grabbed page, so a rollback cannot lock a pair.
                int requested = bound((opposing ? source : priorTarget) + direction);
                int nearest = bound(Math.round(position));
                target = direction > 0 ? Math.max(requested, nearest) : Math.min(requested, nearest);
            } else {
                target = opposing ? bound(Math.round(position)) : priorTarget;
            }
        }
        touching = false;
        overscroll = 0;
        animationMin = Math.min(position, target);
        animationMax = Math.max(position, target);
        return target;
    }

    /** Old ticks cannot move a new grab, and release momentum cannot overshoot its destination. */
    void animate(float absolutePosition) {
        if (touching || !Float.isFinite(absolutePosition)) return;
        position = Math.max(animationMin, Math.min(animationMax, bound(absolutePosition)));
        overscroll = 0;
    }

    /** Clicking changes only the destination; it never teleports the presented sheets. */
    void select(int index) {
        target = bound(index);
        touching = false;
        displacement = overscroll = 0;
        direction = 0;
        animationMin = Math.min(position, target);
        animationMax = Math.max(position, target);
    }

    void finish() {
        position = origin = target;
        priorTarget = source = target;
        displacement = overscroll = 0;
        direction = 0;
        animationMin = animationMax = position;
        touching = false;
    }
}

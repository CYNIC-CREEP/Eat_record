package com.eatrecord.app;

/** Root-navigation sequences independent of view construction or animation completion timing. */
public final class RootPageMotionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void close(float actual, float wanted, String message) {
        check(Math.abs(actual - wanted) < .0001f, message + ": " + actual + " != " + wanted);
    }
    private static int visible(RootPageMotion motion) { return Math.round(motion.position()); }
    private static void swipe(RootPageMotion motion, float delta, float velocity) {
        motion.grab(visible(motion));
        motion.drag(delta * .2f);
        motion.drag(delta); // The same API accepts an UP endpoint without a final MOVE.
        motion.release(velocity, false);
    }
    private static void advanceSome(RootPageMotion motion, float fraction) {
        motion.animate(motion.position() + (motion.target() - motion.position()) * fraction);
    }
    private static void invariant(RootPageMotion motion, String where) {
        check(Float.isFinite(motion.position()) && motion.position() >= 0 && motion.position() <= 2,
                where + ": finite presentation within three roots");
        check(motion.target() >= 0 && motion.target() <= 2, where + ": requested page within roots");
        check(Float.isFinite(motion.overscroll()) && Math.abs(motion.overscroll()) <= 1,
                where + ": finite bounded edge resistance");
    }
    private static void continuousPair(int start, int direction, float settleFraction) {
        RootPageMotion motion = new RootPageMotion(start);
        swipe(motion, direction * .64f, 0);
        check(motion.target() == start + direction, "first complete swipe chooses adjacent root");
        advanceSome(motion, settleFraction);
        float before = motion.position();
        motion.grab(visible(motion));
        close(motion.position(), before, "grabbing preserves the currently displayed composition");
        motion.drag(direction * .13f);
        motion.drag(direction * .64f);
        check(motion.release(0, false) == start + direction * 2,
                "second quick swipe reaches third root without old onEnd");
        motion.finish();
        close(motion.position(), start + direction * 2, "two swipes settle on exact third root");
        check(!motion.isDragging(), "settlement clears active grab");
    }

    public static void main(String[] args) {
        for (float fraction : new float[]{0, .02f, .25f, .6f, .95f, 1}) {
            continuousPair(0, 1, fraction);
            continuousPair(2, -1, fraction);
        }

        // Former old-pair trap: rollback is still active when another gesture requests Today.
        for (float position : new float[]{1.1f, 1.49f, 1.8f, 1.99f}) {
            RootPageMotion motion = new RootPageMotion(2);
            motion.select(1);
            motion.animate(position);
            float before = motion.position();
            motion.grab(visible(motion));
            motion.drag(-.64f);
            check(motion.release(0, false) == 0, "rollback can continue toward third root before completion");
            check(motion.position() <= before, "rollback continuation follows direction of finger");
            motion.finish();
            close(motion.position(), 0, "rollback continuation lands on Today");
        }

        // Click interruption followed immediately by reversal uses the actual visible root.
        for (float position : new float[]{.1f, .49f, .51f, .99f, 1.4f, 1.8f}) {
            RootPageMotion motion = new RootPageMotion(0);
            motion.select(2);
            motion.animate(position);
            int source = visible(motion);
            motion.grab(source);
            motion.drag(-.65f);
            check(motion.release(0, false) == Math.max(0, source - 1),
                    "reverse chooses neighbour of real grabbed root, not a stale from/to pair");
            motion.finish();
        }

        RootPageMotion motion = new RootPageMotion(0);
        for (int i = 0; i < 24; i++) {
            int direction = i % 4 < 2 ? 1 : -1;
            swipe(motion, direction * .64f, direction * .8f);
            advanceSome(motion, .08f);
            invariant(motion, "alternating rapid swipe " + i);
        }
        motion.finish();
        invariant(motion, "after alternating burst");

        motion.reset(1);
        motion.grab(1); motion.drag(.66f);
        check(motion.release(0, true) == 1, "cancel restores the root owning this grab");
        float cancelledPosition = motion.position();
        motion.drag(-1);
        close(motion.position(), cancelledPosition, "remaining-finger MOVE cannot revive cancelled stream");
        check(motion.release(-9, false) == 1, "terminal UP cannot recommit after cancellation");
        motion.finish();
        swipe(motion, -.64f, 0);
        check(motion.target() == 0, "next swipe works immediately after cancellation");
        motion.finish();

        motion.grab(0); motion.drag(.65f); motion.release(0, false); motion.animate(.75f);
        motion.grab(1); motion.drag(-.15f);
        check(motion.release(0, true) == 1, "cancel during settle restores nearest grabbed root");
        motion.finish();
        motion.grab(1); motion.drag(.04f);
        check(motion.release(1, false) == 2, "short valid fling is accepted");
        motion.finish();
        motion.grab(2); motion.drag(-.1f);
        check(motion.release(0, false) == 2, "short slow movement rests instead of forcing a page");
        motion.finish();

        // A terminal UP can advance from only a short last MOVE with zero release velocity.
        motion.reset(0); motion.grab(0); motion.drag(.08f);
        close(motion.position(), .08f, "short MOVE presentation");
        motion.drag(.67f);
        check(motion.release(0, false) == 1, "coalesced UP distance commits without velocity luck");
        motion.finish();

        // Repeated outward swipes do not latch an edge or create a fourth root.
        for (int index : new int[]{0, 2}) {
            motion.reset(index);
            int outward = index == 0 ? -1 : 1;
            for (int i = 0; i < 12; i++) {
                motion.grab(index); motion.drag(outward * .8f);
                close(motion.overscroll(),0,"blocked root edge never exposes elastic feedback");
                close(motion.position(),index,"outward root drag keeps exact boundary position");
                check(motion.release(outward * 9, false) == index, "edge remains within three roots");
                motion.finish();
            }
            swipe(motion, -outward * .64f, 0);
            check(motion.target() == 1, "inward swipe works after repeated edge attempts");
            motion.finish();
        }

        for(int boundary:new int[]{0,2}){
            motion.reset(boundary);int outward=boundary==0?-1:1;
            motion.grab(boundary);motion.drag(outward*Float.MAX_VALUE);
            close(motion.position(),boundary,"extreme outward root drag remains still");
            close(motion.overscroll(),0,"extreme outward root drag has no feedback");
            motion.release(outward*9,true);motion.release(outward*9,false);motion.finish();
            swipe(motion,-outward*.64f,0);check(motion.target()==1,"cancelled boundary does not swallow the next valid stream");motion.finish();
        }

        motion.reset(0); motion.select(2); motion.animate(.4f);
        motion.select(1); close(motion.position(), .4f, "click retarget never resets current presentation");
        motion.animate(3); close(motion.position(), 1, "click animation cannot overshoot latest request");
        motion.select(0); motion.animate(-9); close(motion.position(), 0, "reverse click clamps to new target");
        motion.finish();

        motion.select(2); motion.animate(1.4f); motion.grab(1); motion.drag(.25f);
        float held = motion.position(), heldOverscroll = motion.overscroll();
        motion.animate(2);
        close(motion.position(), held, "stale spring tick cannot overwrite a new grab");
        close(motion.overscroll(), heldOverscroll, "stale spring tick cannot alter edge state");
        motion.select(0);
        check(!motion.isDragging(), "click takes ownership from active grab");
        check(motion.release(9, false) == 0, "old pointer release cannot override click destination");
        motion.finish();

        motion.reset(1); motion.grab(1); motion.drag(Float.NaN); motion.drag(Float.POSITIVE_INFINITY);
        close(motion.position(), 1, "nonfinite coordinates ignored");
        motion.release(Float.NaN, true); motion.animate(Float.NaN);
        invariant(motion, "nonfinite input recovery");
        motion.grab(1); motion.drag(Float.MAX_VALUE); invariant(motion, "extreme finite displacement");
        motion.release(Float.POSITIVE_INFINITY, false); motion.finish();
        motion.reset(-100); close(motion.position(), 0, "invalid negative reset clamped");
        motion.reset(100); close(motion.position(), 2, "invalid positive reset clamped");

        java.util.Random random = new java.util.Random(28);
        for (int i = 0; i < 300; i++) {
            if (i % 5 == 0) motion.select(random.nextInt(3));
            else {
                motion.grab(visible(motion));
                motion.drag(random.nextFloat() * 2 - 1);
                motion.release(random.nextFloat() * 6 - 3, random.nextInt(6) == 0);
            }
            advanceSome(motion, random.nextFloat());
            invariant(motion, "mixed interaction " + i);
            if (i % 17 == 0) motion.finish();
        }
        motion.finish();
        close(motion.position(), motion.target(), "mixed stream settles on its latest destination");
        check(!motion.isDragging(), "no active pointer after final settlement");
        System.out.println("PASS: " + checks + " root-page continuous navigation checks (pure state; no device/frame-rate claim)");
    }
}

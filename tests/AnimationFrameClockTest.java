package com.eatrecord.app;

/** Regression for page preparation being charged to the first bottom-bar spring frame. */
public final class AnimationFrameClockTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AnimationFrameClock clock = new AnimationFrameClock();
        BottomNavMotion motion = new BottomNavMotion();
        motion.snap(0);
        motion.select(300);
        // Rendering and layer preparation have occupied the UI thread for 600ms.
        clock.reset();
        motion.step(clock.advance(600_000_000L));
        check(motion.position.value == 0, "Preparation cannot consume the visible spring movement");
        check(clock.advance(590_000_000L) == 0, "A stale frame cannot move the clock backwards");
        double interval = clock.advance(616_000_000L);
        check(Math.abs(interval - .016) < .000001, "The next presented frame gets its actual interval");
        motion.step(interval);
        check(motion.position.value > 0 && motion.position.value < 30, "First movement remains near its source");

        double position = motion.position.value;
        double velocity = motion.position.velocity;
        motion.select(120);
        clock.reset();
        motion.step(clock.advance(1_400_000_000L));
        check(motion.position.value == position && motion.position.velocity == velocity,
                "Interrupted preparation retains position and velocity");
        check(motion.position.target == 120, "Preparation retains the latest requested target");
        for (int frame = 1; frame <= 180; frame++) {
            motion.step(clock.advance(1_400_000_000L + frame * 16_666_667L));
        }
        check(Math.abs(motion.position.value - 120) < .01, "Normal frames settle at the accepted target");
        clock.start(2_000_000_000L);
        check(Math.abs(clock.advance(2_020_000_000L) - .020) < .000001,
                "Ordinary unprepared navigation preserves its existing clock interval");
        clock.reset();
        check(clock.advance(9_000_000_000L) == 0, "A long preparation still begins at zero");

        AnimationFrameClock paced = new AnimationFrameClock(1d / 30d);
        check(paced.advance(1_000_000_000L) == 0, "First presented frame establishes its clock");
        check(Math.abs(paced.advance(1_008_333_333L) - 1d / 120d) < .000001,
                "High-refresh frames retain their actual interval");
        check(Math.abs(paced.advance(1_025_000_000L) - 1d / 60d) < .000001,
                "Ordinary 60Hz frames retain their actual interval");
        check(Math.abs(paced.advance(1_625_000_000L) - 1d / 30d) < .000001,
                "A 600ms stall consumes at most one bounded movement step");
        check(Math.abs(paced.advance(1_641_000_000L) - .016) < .000001,
                "The next frame does not replay a stalled backlog");
        check(paced.advance(1_600_000_000L) == 0, "An old callback cannot rewind the paced clock");
        check(Math.abs(paced.advance(1_657_000_000L) - .016) < .000001,
                "An old callback leaves the next real interval unchanged");
        paced.reset();
        double presented = paced.advance(3_000_000_000L);
        long now = 3_000_000_000L;
        for (int frame = 0; frame < 5; frame++) presented += paced.advance(now += 16_000_000L);
        double beforeStall = presented;
        presented += paced.advance(now += 800_000_000L);
        check(presented < .12 && presented > beforeStall,
                "A page cannot jump from its early frames to complete after an 800ms stall");
        check(presented / .480 < .25, "The nominal 480ms page remains near its visible origin");
        for (int frame = 0; frame < 30; frame++) presented += paced.advance(now += 16_000_000L);
        check(presented >= .480, "Following normal frames still complete the page timeline");
        paced.start(5_000_000_000L);
        check(Math.abs(paced.advance(5_700_000_000L) - 1d / 30d) < .000001,
                "A spring's delayed first callback also has a bounded interval");
        System.out.println("PASS: " + checks + " prepared and paced frame-clock checks");
    }
}

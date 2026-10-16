package io.github.jasoncross.ftchelper.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UtilTest {
    private static final double E = 1e-9;

    @Test public void clampKeepsInsideAndTreatsNanAsZero() {
        assertEquals(1, MathUtil.clamp(1.3, -1, 1), E);
        assertEquals(-1, MathUtil.clamp(-7, -1, 1), E);
        assertEquals(0.2, MathUtil.clamp(0.2, -1, 1), E);
        assertEquals(0, MathUtil.clamp(Double.NaN, -1, 1), E);
        assertEquals(0.5, MathUtil.clamp(0.5, 1, -1), E);   // swapped range still works
        assertEquals(10, MathUtil.clamp(12, 0, 10));
    }

    @Test public void deadbandAndScaledDeadband() {
        assertEquals(0, MathUtil.deadband(0.03, 0.05), E);
        assertEquals(-0.4, MathUtil.deadband(-0.4, 0.05), E);
        assertEquals(0, MathUtil.deadbandScaled(0.1, 0.1), E);
        assertEquals(0.5, MathUtil.deadbandScaled(0.55, 0.1), E);
        assertEquals(-1, MathUtil.deadbandScaled(-1, 0.1), E);
        assertEquals(0, MathUtil.deadbandScaled(0.9, 1.0), E);
    }

    @Test public void lerpNearNormalize() {
        assertEquals(2.5, MathUtil.lerp(0, 10, 0.25), E);
        assertTrue(MathUtil.isNear(100, 103, 5));
        assertFalse(MathUtil.isNear(100, 106, 5));
        assertArrayEquals(new double[]{1, 0.5, -0.5, 0}, MathUtil.normalize(new double[]{2, 1, -1, 0}, 1), E);
        assertArrayEquals(new double[]{0.5, -0.2}, MathUtil.normalize(new double[]{0.5, -0.2}, 1), E);
    }

    @Test public void anglesWrapAndTakeTheShortWay() {
        assertEquals(-170, Angles.wrapDegrees(190), E);
        assertEquals(180, Angles.wrapDegrees(-180), E);
        assertEquals(180, Angles.wrapDegrees(-540), E);
        assertEquals(45, Angles.wrapDegrees(45 + 720), E);
        assertEquals(20, Angles.shortestDiff(-170, 170), E);
        assertEquals(-20, Angles.shortestDiff(170, -170), E);
        assertEquals(90, Angles.shortestDiff(90, 0), E);
    }

    @Test public void loopTimerMeasuresHz() {
        long[] now = {0};
        LoopTimer timer = new LoopTimer(() -> now[0]);
        assertEquals(0, timer.update(), E);
        assertEquals(0, timer.getHz(), E);
        for (int i = 0; i < 50; i++) {
            now[0] += 20_000_000L;   // 20 ms
            timer.update();
        }
        assertEquals(0.020, timer.getLastSeconds(), E);
        assertEquals(50, timer.getHz(), 1e-6);
        assertEquals(20, timer.getAverageMs(), 1e-6);
    }

    @Test public void poseStoreHandsOffOnceAndCopies() {
        PoseStore.clear();
        assertNull(PoseStore.consume());
        FieldPose pose = new FieldPose(10, 20, 90);
        PoseStore.save(pose);
        pose.x = 999;   // changing the original does not change the saved copy
        assertTrue(PoseStore.has());
        assertEquals(10, PoseStore.peek().x, E);
        FieldPose out = PoseStore.consume();
        assertEquals(20, out.y, E);
        assertEquals(90, out.headingDeg, E);
        assertFalse(PoseStore.has());
        PoseStore.save(null);
        assertFalse(PoseStore.has());
    }

    @Test public void poseStoreKnowsHowOldThePoseIs() {
        long[] now = {0};
        PoseStore.clockNanos = () -> now[0];
        try {
            PoseStore.clear();
            assertEquals(Double.MAX_VALUE, PoseStore.ageSec(), 0);
            PoseStore.save(new FieldPose(0, 0, 90));
            now[0] += 3_000_000_000L;
            assertEquals(3, PoseStore.ageSec(), 1e-9);
        } finally {
            PoseStore.clockNanos = System::nanoTime;
            PoseStore.clear();
        }
    }
}

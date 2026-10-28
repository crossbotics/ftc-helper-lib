package com.crossbotics.ftchelper.odometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The alpha camera fix's rules: off by default, the frame turn, and every reason to refuse a picture. */
public class CameraPoseFixTest {
    private static final long MS = 1_000_000L;

    private static CameraPoseFix on(double turn) {
        CameraPoseFix.Settings s = new CameraPoseFix.Settings();
        s.enabled = true;
        s.turnDeg = turn;
        return new CameraPoseFix(s);
    }

    @Test public void offByDefault() {
        CameraPoseFix f = new CameraPoseFix(new CameraPoseFix.Settings());
        f.record(1000 * MS, 0, 0);
        CameraPoseFix.Result r = f.offer(5, 5, 1000 * MS, 1010 * MS, 4, 0, 0);
        assertFalse(r.used);
        assertTrue(r.words, r.words.contains("off"));
    }

    @Test public void theTurnMapsTheLimelightFrameAndTheHeading() {
        CameraPoseFix f = on(90);
        double[] p = f.toOurs(10, 0);                 // the Limelight's +x is our +y with a 90 deg turn
        assertEquals(0, p[0], 1e-9);
        assertEquals(10, p[1], 1e-9);
        assertEquals(0, f.ourHeading(f.limelightYaw(0)), 1e-9);
        assertEquals(0, f.limelightYaw(0), 1e-9);     // facing away (our 0) = facing the Limelight's +x here
        assertEquals(90, CameraPoseFix.suggestTurn(0, 0), 1e-9);
        assertEquals(0, CameraPoseFix.suggestTurn(0, 92), 1e-9);   // rounds to the nearest 90
    }

    @Test public void comparesWithThePoseWhenThePictureWasTakenAndBlends() {
        CameraPoseFix f = on(0);
        f.record(1000 * MS, 0, 0);
        f.record(1050 * MS, 10, 0);                   // the robot moved on since the picture
        CameraPoseFix.Result r = f.offer(2, 0, 1000 * MS, 1060 * MS, 4, 5, 0);
        assertTrue(r.words, r.used);
        assertEquals("2 in off at the picture's moment, a fifth of it", 0.4, r.dxIn, 1e-9);
        assertEquals(0, r.dyIn, 1e-9);
    }

    // Review (Codex, Alex): a correction moves the history, so a second old picture does not count it again.
    @Test public void anAppliedCorrectionIsNotCountedTwice() {
        CameraPoseFix.Settings s = new CameraPoseFix.Settings();
        s.enabled = true;
        s.blend = 1;
        CameraPoseFix f = new CameraPoseFix(s);
        f.record(1000 * MS, 0, 0);
        CameraPoseFix.Result r1 = f.offer(10, 0, 1000 * MS, 1010 * MS, 4, 0, 0);
        assertEquals(10, r1.dxIn, 1e-9);
        f.applied(r1.dxIn, r1.dyIn);
        CameraPoseFix.Result r2 = f.offer(10, 0, 1001 * MS, 1011 * MS, 4, 0, 0);
        assertEquals("already fixed: nothing more", 0, r2.dxIn, 1e-9);
    }

    @Test public void refusesOldFewTagsMovingAndJumps() {
        CameraPoseFix f = on(0);
        f.record(1000 * MS, 0, 0);
        assertTrue(f.offer(1, 1, 1000 * MS, 1200 * MS, 4, 0, 0).words.contains("old picture"));
        assertTrue(f.offer(1, 1, 1000 * MS, 1010 * MS, 1, 0, 0).words.contains("tag"));
        assertTrue(f.offer(1, 1, 1000 * MS, 1010 * MS, 4, 30, 0).words.contains("moving"));
        assertTrue(f.offer(1, 1, 1000 * MS, 1010 * MS, 4, 0, 120).words.contains("moving"));
        assertTrue(f.offer(20, 0, 1000 * MS, 1010 * MS, 4, 0, 0).words.contains("jump"));
        assertTrue(f.offer(Double.NaN, 0, 1000 * MS, 1010 * MS, 4, 0, 0).words.contains("no position"));
        assertTrue(f.offer(1, 1, 3000 * MS, 3010 * MS, 4, 0, 0).words.contains("no odometry pose"));
    }
}

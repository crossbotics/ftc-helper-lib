package com.crossbotics.ftchelper.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** ShotTable.extendEnds: the automatic table ends, and "off" exactly as before. */
public class ShotTableEndsTest {
    private static final double E = 1e-6;

    private static ShotTable measured() {
        return new ShotTable().add(27, 2500).add(32, 2550).add(35, 2615).add(41, 2780).add(45, 2918);
    }

    @Test public void theMeasuredTablesExampleNumbers() {
        ShotTable t = measured().extendEnds(4800);
        assertEquals(2230, t.lookup(0).rpm, E);
        assertEquals(2380, t.lookup(15).rpm, E);
        assertEquals(2642.5, t.lookup(36).rpm, E);
        assertEquals(3884, t.lookup(73).rpm, E);
        assertEquals(4781, t.lookup(99).rpm, E);
        assertEquals(4800, t.lookup(130).rpm, E);
        assertFalse("the limit does not prove it reaches", t.reaches(130));
        assertTrue(t.isSpeedLimited(130));
        assertTrue(t.isEstimated(130));
        assertTrue(t.reaches(99));
        assertFalse(t.isSpeedLimited(99));
    }

    @Test public void theLimitIsReachedAboutNinetyNineAndAHalf() {
        ShotTable t = measured().extendEnds(4800);
        double at = 45 + (4800 - 2918) / 34.5;
        assertTrue("just before", t.reaches(at - 0.01));
        assertTrue("exactly there", t.reaches(at));
        assertFalse("past it", t.reaches(at + 0.01));
    }

    // A steep first pair of rows: closer than row 1, a guess under half of row 1's speed does not "reach".
    @Test public void aGuessFarTooSlowCloserThanTheFirstRowDoesNotReach() {
        ShotTable t = new ShotTable().add(10, 1000).add(12, 2000).add(40, 3000).extendEnds(4800);
        assertEquals(400, t.lookup(8.8).rpm, E);
        assertFalse("400 RPM is under half of 1000", t.reaches(8.8));
        assertTrue("exactly half still reaches", t.reaches(9));
        assertTrue("750 RPM reaches", t.reaches(9.5));
        assertTrue("the measured rows still reach", t.reaches(10));
    }

    @Test public void coversStillMeansMeasured() {
        ShotTable t = measured().extendEnds(4800);
        for (double d : new double[] {27, 30, 36, 45}) {
            assertTrue(t.covers(d));
            assertTrue(t.reaches(d));
            assertFalse(t.isEstimated(d));
        }
        for (double d : new double[] {0, 26.9, 45.1, 73}) {
            assertFalse("not measured: " + d, t.covers(d));
            assertTrue(t.isEstimated(d));
            assertTrue(t.reaches(d));
        }
    }

    @Test public void offIsExactlyAsBefore() {
        ShotTable t = measured();
        assertFalse(t.extendsEnds());
        assertTrue(Double.isNaN(t.getExtendMaxRpm()));
        assertEquals(2500, t.lookup(0).rpm, 0);
        assertEquals(2500, t.lookup(Double.NEGATIVE_INFINITY).rpm, 0);
        assertEquals(2918, t.lookup(130).rpm, 0);
        assertEquals(2918, t.lookup(Double.POSITIVE_INFINITY).rpm, 0);
        assertEquals(2642.5, t.lookup(36).rpm, E);
        for (double d = -5; d <= 140; d += 0.5) {
            assertEquals("" + d, t.covers(d), t.reaches(d));
            assertFalse(t.isEstimated(d));
            assertFalse(t.isSpeedLimited(d));
        }
        assertTrue(t.extensionNotes().isEmpty());
    }

    @Test public void onAndOffAgreeInsideTheMeasuredRows() {
        ShotTable off = measured(), on = measured().extendEnds(4800);
        for (double d = 27; d <= 45; d += 0.25) assertEquals(off.lookup(d).rpm, on.lookup(d).rpm, 0);
    }

    @Test public void anUnknownOrImpossibleDistanceIsAsBefore() {
        ShotTable t = measured().extendEnds(4800);
        assertEquals(2500, t.lookup(Double.NaN).rpm, 0);
        assertFalse(t.reaches(Double.NaN));
        assertFalse(t.isEstimated(Double.NaN));
        assertEquals(2918, t.lookup(Double.POSITIVE_INFINITY).rpm, 0);
        assertFalse(t.reaches(Double.POSITIVE_INFINITY));
        assertEquals(2500, t.lookup(-3).rpm, 0);
        assertFalse(t.reaches(-3));
    }

    @Test public void theHoodKeepsItsEndAngle() {
        ShotTable t = new ShotTable().add(24, 2000, 5).add(48, 2600, 15).extendEnds(5000);
        assertEquals(1700, t.lookup(12).rpm, E);
        assertEquals(5, t.lookup(12).hoodDeg, E);
        assertEquals(3200, t.lookup(72).rpm, E);
        assertEquals(15, t.lookup(72).hoodDeg, E);
    }

    @Test public void aSpeedThatWouldFallBelowZeroIsZeroAndNotReached() {
        ShotTable t = new ShotTable().add(40, 1000).add(50, 2000).extendEnds(4000);
        assertEquals(0, t.lookup(10).rpm, E);
        assertFalse(t.reaches(10));
    }

    @Test public void fewerThanTwoRowsStayFlatWithANote() {
        ShotTable t = new ShotTable().add(30, 2500).extendEnds(4800);
        assertEquals(2500, t.lookup(60).rpm, 0);
        assertFalse(t.reaches(60));
        assertEquals("[Only 1 table row: NO speed estimates., Add a second tested row.]", t.extensionNotes().toString());
    }

    @Test public void anEndWhoseSpeedFallsWithDistanceStaysFlatWithANote() {
        ShotTable t = new ShotTable().add(20, 2400).add(30, 2300).add(40, 2700).extendEnds(4800);
        assertEquals("the near end stays flat", 2400, t.lookup(5).rpm, 0);
        assertFalse(t.isEstimated(5));
        assertFalse(t.reaches(5));
        assertEquals("the far end goes on", 3100, t.lookup(50).rpm, E);
        assertEquals("[First 2 rows: speed drops with distance., NO estimate closer than row 1., Check those 2 rows.]",
                t.extensionNotes().toString());

        ShotTable far = new ShotTable().add(20, 2400).add(30, 2700).add(40, 2600).extendEnds(4800);
        assertEquals(2600, far.lookup(60).rpm, 0);
        assertEquals("[Last 2 rows: speed drops with distance., NO estimate past the last row., Check those 2 rows.]",
                far.extensionNotes().toString());
    }

    @Test public void twoRowsAtOneDistanceStopWithTheReason() {
        try {
            new ShotTable().add(30, 2500).add(30, 2600).extendEnds(4800);
            fail("it should stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rows both use"));
        }
        try {
            measured().extendEnds(4800).add(35, 2700);
            fail("a row added later too");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rows both use"));
        }
        new ShotTable().add(30, 2500).add(30, 2600).lookup(30);      // off: allowed, as before
    }

    @Test public void aBadLimitIsRefusedAndALowOneIsNoted() {
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                measured().extendEnds(bad);
                fail("" + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Use more than 0"));
            }
        }
        ShotTable low = measured().extendEnds(2800);
        assertEquals("[Speed limit 2800 RPM is below a tested row., Set extendMaxRpm to 2918 or more.]", low.extensionNotes().toString());
        assertEquals("a measured row is never cut", 2918, low.lookup(45).rpm, 0);
    }
}

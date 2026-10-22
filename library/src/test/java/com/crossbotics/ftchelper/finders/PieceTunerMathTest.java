package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The Piece Tuner's arithmetic: hues are averaged round the colour wheel; the gap between kinds. */
public class PieceTunerMathTest {
    @Test public void redHuesEitherSideOfZeroAverageToRed() {
        List<double[]> rows = Arrays.asList(new double[] {350, 0.8, 15}, new double[] {10, 0.6, 17});
        double[] avg = PieceTunerOpMode.average(rows, true);
        assertTrue("about 0, not 180: " + avg[0], Math.min(avg[0], 360 - avg[0]) < 1e-6);
        assertEquals(0.7, avg[1], 1e-9);
        assertEquals(16, avg[2], 1e-9);
        assertEquals("mm are plain numbers", 180, PieceTunerOpMode.average(rows, false)[0], 1e-9);
    }

    @Test public void theSmallestGapGoesRoundTheWheel() {
        assertEquals(20, PieceTunerOpMode.smallestGap(Arrays.asList(350.0, 10.0, 220.0), true), 1e-9);
        assertEquals(20, PieceTunerOpMode.smallestGap(Arrays.asList(79.0, 59.0), false), 1e-9);
        assertTrue(Double.isNaN(PieceTunerOpMode.smallestGap(Arrays.asList(79.0), false)));
    }
}

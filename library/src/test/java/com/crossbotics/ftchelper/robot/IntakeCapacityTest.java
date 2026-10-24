package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * How many pieces the intake holds (Intake.holds), in all and per kind: the simulator's line, the INIT
 * warning above the game's limit, and the piece count's "full" from it.
 */
public class IntakeCapacityTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    @Test public void aKindNameMatchesEveryKindThatContainsIt() {
        java.util.Map<String, Integer> limits = Collections.singletonMap("NECTAR", 0);
        assertEquals(0, Intake.limitFor(limits, "RED_NECTAR"));
        assertEquals(0, Intake.limitFor(limits, "blue NECTAR"));
        assertEquals(-1, Intake.limitFor(limits, "POLLEN"));
    }

    @Test public void theTotalIsHoldsOnly() {
        assertEquals(4, Intake.motor("intake").holds(4).holds(1, "NECTAR").holdsInAll());
        assertEquals("kind limits alone give no total", 0, Intake.motor("intake").holds(3, "POLLEN").holds(0, "NECTAR").holdsInAll());
        assertEquals(0, Intake.motor("intake").holdsInAll());
    }

    @Test public void theSimulatorLineHasTheLimits() {
        List<String> out = new java.util.ArrayList<>();
        Intake i = Intake.motor("intake").holds(4).holds(0, "NECTAR");
        i.simLines(out, null);
        String line = out.get(out.size() - 1);
        assertTrue(line, line.endsWith("holds=4, limits=NECTAR:0"));
    }

    @Test public void moreThanTheGameAllowsWarnsAtInit() {
        assertTrue(Intake.motor("intake").holds(5).notes().toString().contains("the game allows 4 pieces at a time"));
        assertFalse(Intake.motor("intake").holds(4).notes().toString().contains("the game allows"));
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
    }

    public static class Bot extends RobotDefinition {
        public Intake intake;
        public Pieces pieces;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            intake = add(Intake.motor("intake").holds(3).holds(1, "NECTAR"));
            pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR").startsWith(1, "RED_NECTAR"));
        }
    }

    @Test public void thePieceCountIsFullAtWhatTheIntakeHolds() {
        Bot r = new Bot();
        r.setup(new PretendDevices());
        assertEquals("no max: the intake's holds(3)", 3, r.pieces.maxPieces());
        assertFalse(r.pieces.isFull());
        assertTrue("one NECTAR inside, it holds 1", r.pieces.isFullFor("BLUE_NECTAR"));
        assertFalse("POLLEN still fits", r.pieces.isFullFor("POLLEN"));
    }
}

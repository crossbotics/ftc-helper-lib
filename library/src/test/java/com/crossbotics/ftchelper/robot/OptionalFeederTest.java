package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.tuning.TuningReset;
import org.junit.After;
import org.junit.Test;

/**
 * A launcher whose feeder is optional() (team 8938's robot 1 without its windmill): with the feeder not in
 * the configuration, INIT goes on, the launcher spins, and "fire" sends nothing to the missing servo.
 */
public class OptionalFeederTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
    }

    public static class Bot extends RobotDefinition {
        public Launcher launcher;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                    .feeder(Indexer.crServo("windmill").part("gobilda-2000-torque").optional()));
        }
    }

    @Test public void withoutTheFeederInitGoesOnAndTheWheelStillSpins() {
        PretendDevices d = new PretendDevices().notConfigured("windmill");
        Bot r = new Bot();
        r.setup(d);
        assertTrue("INIT says the feeder is off: " + r.notes(), r.notes().toString().contains("windmill"));
        r.startForTests();
        r.launcher.spinUp();
        r.all().update();
        assertTrue("the wheel spins", Math.abs(d.motor("launcher").power) > 0);
        r.launcher.fire();
        for (int i = 0; i < 5; i++) r.all().update();
        assertFalse("still not in the configuration (nothing asked for it)", d.has("windmill"));
    }

    @Test public void withTheFeederItFeedsAsBefore() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertFalse(r.notes().toString().contains("not in the configuration"));
        assertEquals(0, d.crServo("windmill").power, 1e-9);
    }
}

package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/** The INIT screen: the parts on starting numbers are one short line, so the warnings stay in view. */
public class InitScreenTest {

    @After public void back() {
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    @Test public void theStartingNumberNotesNameTheirPart() {
        assertEquals("Launcher", RobotDefinition.startingPart("Tunables.Launcher: DEFAULT, library defaults: stepRpm 100"));
        assertEquals("Launcher", RobotDefinition.startingPart("Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7. Run the Flywheel Finder"));
        assertEquals("Intake", RobotDefinition.startingPart("Tunables.Intake not written: library defaults used (fine)"));
        assertEquals("Lift", RobotDefinition.startingPart("Tunables.Lift: kD, kF not written: library defaults used (fine)"));
        assertEquals("Lift", RobotDefinition.startingPart("Lift: defaults from my-motor (custom): kP 0.005"));
        assertNull(RobotDefinition.startingPart("\"intake_deploy\" not in the configuration: IntakeDeploy is off (optional part)"));
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Claw { public static double open = 0.25, closed = 0.70; }
    }

    public static class Bot extends RobotDefinition {
        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("left", "right").motor("gobilda-5203-312").wheelMm(96));
            add(Intake.motor("intake").part("gobilda-5203-1150"));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            add(Claw.servo("claw").part("gobilda-2000-torque"));
        }
    }

    @Test public void oneShortLineForEveryPartOnStartingNumbers() {
        Bot r = new Bot();
        r.setup(new PretendDevices());
        assertTrue("the full notes are still there: " + r.notes(), r.notes().toString().contains("defaults from the catalog"));
        PretendTelemetry screen = new PretendTelemetry();
        RobotTelemetry t = new RobotTelemetry(screen.telemetry);
        r.showNotes(t);
        List<String> lines = t.pending();
        String all = lines.toString();
        assertFalse("no long per-part lines on the screen: " + all, all.contains("defaults from the catalog ("));
        int summary = 0;
        for (String l : lines) if (l.startsWith("Starting numbers in use")) summary++;
        assertEquals(all, 1, summary);
        assertTrue(all, all.contains("Intake") && all.contains("Launcher") && all.contains("Claw"));
    }
}

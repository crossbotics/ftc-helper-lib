package com.crossbotics.ftchelper.tuning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.control.PidController;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.core.SettingsFile;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TuningTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File file;

    @Before public void setUp() {
        file = new File(tmp.getRoot(), "settings.properties");
        SettingsFile.useForTests(new SettingsFile(file));
        Tunable.clearForTests();
    }

    @After public void back() {
        SettingsFile.useForTests(null);
        Tunable.clearForTests();
    }

    @Test public void stepsRangeAndSameNameGivesSameTunable() {
        Tunable kp = Tunable.number("Lift/kP", 0.004, 0.001).range(0, 0.01);
        assertEquals("Lift", kp.group());
        kp.increase();
        assertEquals(0.005, kp.get(), 1e-12);
        kp.set(5);
        assertEquals(0.01, kp.get(), 1e-12);
        assertTrue(kp.isChanged());
        assertTrue(Tunable.number("Lift/kP", 99, 1) == kp);
        kp.reset();
        assertFalse(kp.isChanged());
    }

    // Two robots in one app with the same name: number() keeps the first one's default; fromCode()
    // (the robot in five files) takes each robot's own code value, unless the value was tuned.
    @Test public void fromCodeTakesTheCodeValueUnlessTuned() {
        Tunable a = Tunable.fromCode("Auto/leaveInches", 24, 1);
        assertEquals(24, Tunable.number("Auto/leaveInches", 99, 1).get(), 1e-12);
        Tunable b = Tunable.fromCode("Auto/leaveInches", 12, 5);
        assertTrue(a == b);
        assertEquals(12, b.get(), 1e-12);
        assertEquals(12, b.defaultValue(), 1e-12);
        assertEquals(1, b.step(), 1e-12);
        b.set(15);
        Tunable c = Tunable.fromCode("Auto/leaveInches", 24, 1);
        assertEquals(15, c.get(), 1e-12);
        c.reset();
        assertEquals("reset goes to this program's code value", 24, c.get(), 1e-12);
    }

    @Test public void savedValuesComeBackAfterARestart() {
        Tunable kp = Tunable.number("Lift/kP", 0.004, 0.001);
        kp.set(0.006);
        assertTrue(Tunable.saveAll());
        // "Restart": new settings object reading the same file, new tunables.
        SettingsFile.useForTests(new SettingsFile(file));
        Tunable.clearForTests();
        Tunable again = Tunable.number("Lift/kP", 0.004, 0.001);
        assertEquals(0.006, again.get(), 1e-12);
        assertEquals(1, Tunable.countChanged());
        assertTrue(Tunable.resetAll());
        assertEquals(0.004, again.get(), 1e-12);
        assertEquals(0, Tunable.countChanged());
    }

    @Test public void pidTunablesApplyToController() {
        PidTunables gains = PidTunables.of("Arm", 0.01, 0, 0.001, 0.1);
        gains.kP.set(0.02);
        PidController pid = new PidController(0, 0, 0);
        gains.applyTo(pid);
        assertEquals(0.02, pid.getKP(), 1e-12);
        assertEquals(0.1, pid.getKF(), 1e-12);
        assertEquals(0.01 / 8, gains.kP.step(), 1e-12);
    }

    @Test public void menuOnlyOpensInPracticeAndChangesValues() {
        Profile.resetForTests();
        Gamepad g = new Gamepad();
        Tunable a = Tunable.number("A/x", 1.0, 0.5);
        Tunable.number("A/y", 2.0, 1.0);
        TuningMenu menu = new TuningMenu(g);
        menu.toggle();
        assertFalse("competition: stays closed", menu.isOpen());
        Profile.set(Profile.PRACTICE);
        menu.toggle();
        assertTrue(menu.isOpen());
        g.dpad_right = true; menu.update();
        g.dpad_right = false; menu.update();
        assertEquals(1.5, a.get(), 1e-12);
        g.right_bumper = true; g.dpad_left = true; menu.update();   // x10 down
        g.right_bumper = false; g.dpad_left = false; menu.update();
        assertEquals(-3.5, a.get(), 1e-12);
        g.x = true; menu.update();                                  // reset this one
        g.x = false; menu.update();
        assertEquals(1.0, a.get(), 1e-12);
        Profile.set(Profile.COMPETITION);
        Profile.resetForTests();
    }

    // A hidden group (a part not on the robot): the menu skips it and says so; its value stays saved.
    @Test public void theMenuSkipsHiddenGroups() {
        Profile.set(Profile.PRACTICE);
        try {
            Gamepad g = new Gamepad();
            Tunable deploy = Tunable.number("IntakeDeploy/deployed", 0.8, 0.05);
            Tunable kp = Tunable.number("Lift/kP", 0.004, 0.001);
            deploy.increase();
            Tunable.hideGroup("IntakeDeploy", true);
            TuningMenu menu = new TuningMenu(g);
            menu.toggle();
            g.dpad_right = true; menu.update();          // changes the first SHOWN one: Lift/kP
            g.dpad_right = false; menu.update();
            assertEquals(0.005, kp.get(), 1e-12);
            assertEquals(0.85, deploy.get(), 1e-12);
            com.crossbotics.ftchelper.Fakes.FakeTelemetry ft = new com.crossbotics.ftchelper.Fakes.FakeTelemetry();
            com.crossbotics.ftchelper.telemetry.RobotTelemetry t = new com.crossbotics.ftchelper.telemetry.RobotTelemetry(ft.telemetry, () -> 0L);
            menu.addTelemetry(t);
            t.update();
            String screen = ft.lastScreen.toString();
            assertTrue(screen, screen.contains("Not on the robot (hidden): IntakeDeploy"));
            assertFalse(screen, screen.contains("IntakeDeploy/deployed"));
            assertTrue("still saved and listed as changed", Tunable.changedAsCode().contains("IntakeDeploy: deployed = 0.85"));
            Tunable.hideGroup("IntakeDeploy", false);
            assertTrue(Tunable.shown().contains(deploy));
        } finally {
            Profile.resetForTests();
        }
    }

    @Test public void changedValuesAsCodeLines() {
        Tunable a = Tunable.number("Lift/kP", 0.004, 0.0005);
        Tunable.number("Lift/kD", 0.0002, 0.0001);
        Tunable b = Tunable.number("speed", 0.5, 0.1);
        assertTrue(Tunable.changedAsCode().isEmpty());
        a.increase();
        b.decrease();
        assertEquals(java.util.Arrays.asList("Lift: kP = 0.0045", "speed = 0.4"), Tunable.changedAsCode());
    }

    // The battery (option 1 of the battery plan): the menu shows it, warns when low, and notes its volts
    // with each change; the copy lines say "tuned at"; the note is saved and comes back; a reset clears it.
    @Test public void theMenuNotesTheBatteryWithEachChange() {
        Profile.set(Profile.PRACTICE);
        try {
            Gamepad g = new Gamepad();
            Tunable kf = Tunable.number("Lift/kF", 0.05, 0.01);
            double[] volts = {12.31};
            TuningMenu menu = new TuningMenu(g).battery(() -> volts[0]);
            menu.toggle();
            g.dpad_right = true; menu.update();
            g.dpad_right = false; menu.update();
            assertEquals(12.31, kf.tunedVolts(), 1e-9);
            assertEquals(java.util.Arrays.asList("Lift: kF = 0.06   // tuned at 12.3 V"), Tunable.changedAsCode());

            com.crossbotics.ftchelper.Fakes.FakeTelemetry ft = new com.crossbotics.ftchelper.Fakes.FakeTelemetry();
            com.crossbotics.ftchelper.telemetry.RobotTelemetry t = new com.crossbotics.ftchelper.telemetry.RobotTelemetry(ft.telemetry, () -> 0L);
            menu.addTelemetry(t);
            t.update();
            String screen = ft.lastScreen.toString();
            assertTrue(screen, screen.contains("Battery 12.3 V: low"));
            assertTrue(screen, screen.contains("Battery 12.3 V (noted with each change)"));

            assertTrue(Tunable.saveAll());
            Tunable.clearForTests();
            SettingsFile.useForTests(new SettingsFile(file));
            Tunable again = Tunable.number("Lift/kF", 0.05, 0.01);
            assertEquals("the note comes back after a restart", 12.31, again.tunedVolts(), 1e-9);
            again.reset();
            assertTrue("a reset forgets it", Double.isNaN(again.tunedVolts()));
        } finally {
            Profile.resetForTests();
        }
    }

    @Test public void noBatteryReaderMeansNoNote() {
        Tunable a = Tunable.number("Lift/kP", 0.004, 0.0005);
        a.increase();
        a.tunedAt(Double.NaN);
        a.tunedAt(0.4);   // a hub that does not measure
        assertEquals(java.util.Arrays.asList("Lift: kP = 0.0045"), Tunable.changedAsCode());
    }
}

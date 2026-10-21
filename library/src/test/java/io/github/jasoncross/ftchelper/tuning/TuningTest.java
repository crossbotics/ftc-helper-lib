package io.github.jasoncross.ftchelper.tuning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.hardware.Gamepad;
import io.github.jasoncross.ftchelper.control.PidController;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.core.SettingsFile;
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

    @Test public void changedValuesAsCodeLines() {
        Tunable a = Tunable.number("Lift/kP", 0.004, 0.0005);
        Tunable.number("Lift/kD", 0.0002, 0.0001);
        Tunable b = Tunable.number("speed", 0.5, 0.1);
        assertTrue(Tunable.changedAsCode().isEmpty());
        a.increase();
        b.decrease();
        assertEquals(java.util.Arrays.asList("Lift: kP = 0.0045", "speed = 0.4"), Tunable.changedAsCode());
    }
}

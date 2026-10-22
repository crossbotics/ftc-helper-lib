package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.util.FieldPose;
import java.io.File;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The launcher's speed trim (with a fixed speed and with a shot table) and its battery compensation. */
public class LauncherTrimTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private LongSupplier aimClockBefore, padClockBefore;

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        aimClockBefore = Aim.clock;
        padClockBefore = Pad.clock;
        clock.nanos = 10_000_000_000L;
        Aim.clock = clock::get;
        Pad.clock = clock::get;
    }

    @After public void back() {
        Aim.clock = aimClockBefore;
        Pad.clock = padClockBefore;
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    /** A fixed speed, the Flywheel Finder's gains and the voltage they were found at. */
    public static class Tuned {
        public static class Launcher {
            public static double speedRpm = 2600, stepRpm = 100;
            public static double kP = 1, kI = 0.1, kD = 0, kF = 12, nominalVolts = 12.0;
            public static double trimStepPercent = 2, trimMaxPercent = 10;
        }
    }

    /** The same, with battery compensation switched off. */
    public static class NoCompensation {
        public static class Launcher {
            public static double speedRpm = 2600;
            public static double kP = 1, kI = 0.1, kD = 0, kF = 12;
            public static boolean batteryCompensation = false;
        }
    }

    /** No kF: the hub's own gains, nothing to compensate. */
    public static class NoGains {
        public static class Launcher {
            public static double speedRpm = 2600;
        }
    }

    private static RobotDefinition fixedBot(final Class<?> numbers) {
        return new RobotDefinition() {
            @Override protected void define() {
                tunables(numbers);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
    }

    private static Launcher launcherOf(RobotDefinition r) {
        for (Module<?> m : r.modules()) if (m instanceof Launcher) return (Launcher) m;
        throw new AssertionError("no launcher");
    }

    private static String screen(Module<?> m) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        m.addTelemetry(t);
        t.update();
        return ft.lastScreen.toString();
    }

    private static String line(List<String> help, String start) {
        for (String h : help) if (h.startsWith(start)) return h;
        return "";
    }

    @Test public void theTrimScalesAFixedSpeedAndStopsAtItsLimit() {
        RobotDefinition r = fixedBot(Tuned.class);
        r.setup(new RobotTestKit());
        Launcher l = launcherOf(r);
        l.spinUp();
        r.all().update();
        assertEquals(2600, l.targetRpm(), 1e-9);
        l.trimPercent(+3);
        r.all().update();
        assertEquals(2600 * 1.03, l.targetRpm(), 1e-6);
        assertTrue(screen(l), screen(l).contains("Speed trim : +3%"));
        l.adjust(1);                                   // the D-pad's rpm step adds first, then the trim
        r.all().update();
        assertEquals(2700 * 1.03, l.targetRpm(), 1e-6);
        assertTrue(screen(l), screen(l).contains("Speed trim : +100 rpm, +3%"));
        l.trimPercent(+50);                            // Tunables.Launcher.trimMaxPercent = 10
        assertEquals(10, l.speedTrim(), 1e-9);
        assertTrue(screen(l), screen(l).contains("(limit)"));
        l.setTrim(-4);
        assertEquals(-4, l.speedTrim(), 1e-9);
        l.resetTrim();
        r.all().update();
        assertEquals("reset: the trim AND the rpm steps", 2600, l.targetRpm(), 1e-9);
        assertEquals(0, l.speedTrim(), 1e-9);
    }

    @Test public void theTrimScalesTheShotTableAndTheDpadTrimsIt() {
        AimTest.AimBot r = new AimTest.AimBot(AimTest.GoalTunables.class, false);   // odometry distance
        r.setup(new RobotTestKit());
        r.started();
        r.tracker.setPose(new FieldPose(-24, 0, 0));    // 48 in below the goal
        r.tracker.update();
        r.launcher.spinUp();
        r.all().update();
        double table = r.launcher.targetRpm();          // nectar at 48 in: 3000
        assertEquals(3000, table, 1e-6);
        r.launcher.trimPercent(-2);
        r.all().update();
        assertEquals(3000 * 0.98, r.launcher.targetRpm(), 1e-6);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("Speed trim : -2%"));
        // The D-pad trims by a percentage here (the speed comes from the table), and hold-down resets.
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        assertTrue(line(plan.help(), "G2 D-pad up"), line(plan.help(), "G2 D-pad up").contains("speed trim +1%"));
        String down = line(plan.help(), "G2 D-pad down");
        assertTrue(down, down.contains("speed trim -1%") && down.contains("hold 1 s: speed trim 0"));
    }

    @Test public void aTrimIsNeverTheTableTheShotTunerRecords() {
        RobotDefinition r = fixedBot(Tuned.class);
        r.setup(new RobotTestKit());
        Launcher l = launcherOf(r);
        l.trimPercent(+5);
        l.spinUp();
        r.all().update();
        assertEquals(1.05, l.part().getSpeedScale(), 1e-9);
        // The Shot Tuner takes the part and sets speedScale(1); the table it reads stays as written.
        l.part().speedScale(1);
        l.part().setRpm(2600);
        l.part().update();
        assertEquals(2600, l.part().getFlywheel().getTargetRpm(), 1e-9);
    }

    @Test public void aLowBatteryGetsMoreFFromTheTuningVoltage() {
        RobotTestKit kit = new RobotTestKit();
        kit.volts = 10.0;
        RobotDefinition r = fixedBot(Tuned.class);
        r.setup(kit);
        Launcher l = launcherOf(r);
        assertEquals("before the first loop: F as tuned", 12, kit.motors.get("launcher").pidf[3], 1e-9);
        l.spinUp();
        r.all().update();
        assertEquals("12 V tuned, 10 V now: F x 1.2", 12 * 1.2, kit.motors.get("launcher").pidf[3], 1e-6);
        assertEquals("P stays as tuned", 1, kit.motors.get("launcher").pidf[0], 1e-9);
        int reads = kit.voltReads;
        r.all().update();
        assertEquals("not read again within 0.25 s", reads, kit.voltReads);
    }

    @Test public void compensationCanBeSwitchedOffAndNeedsAnF() {
        RobotTestKit kit = new RobotTestKit();
        kit.volts = 10.0;
        RobotDefinition r = fixedBot(NoCompensation.class);
        r.setup(kit);
        launcherOf(r).spinUp();
        r.all().update();
        assertEquals(12, kit.motors.get("launcher").pidf[3], 1e-9);
        assertEquals("never read", 0, kit.voltReads);
        RobotTestKit kit2 = new RobotTestKit();
        kit2.volts = 10.0;
        RobotDefinition r2 = fixedBot(NoGains.class);
        r2.setup(kit2);
        launcherOf(r2).spinUp();
        r2.all().update();
        assertEquals("no kF: the hub's own gains are never sent", null, kit2.motors.get("launcher").pidf);
    }
}

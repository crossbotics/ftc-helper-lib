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
        Launcher.lastTrim.clear();
    }

    @After public void back() {
        Aim.clock = aimClockBefore;
        Pad.clock = padClockBefore;
        SettingsFile.useForTests(null);
        TuningReset.clear();
        Launcher.lastTrim.clear();
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
        l.trimBy(+3);
        r.all().update();
        assertEquals(2600 * 1.03, l.targetRpm(), 1e-6);
        assertTrue(screen(l), screen(l).contains("Speed trim : +3%"));
        l.adjust(1);                                   // the D-pad's rpm step adds first, then the trim
        r.all().update();
        assertEquals(2700 * 1.03, l.targetRpm(), 1e-6);
        assertTrue(screen(l), screen(l).contains("Speed trim : +100 rpm, +3%"));
        l.trimBy(+50);                            // Tunables.Launcher.trimMaxPercent = 10
        assertEquals(10, l.trim(), 1e-9);
        assertTrue(screen(l), screen(l).contains("(limit)"));
        l.setTrim(-4);
        assertEquals(-4, l.trim(), 1e-9);
        l.resetTrim();
        r.all().update();
        assertEquals("reset: the trim AND the rpm steps", 2600, l.targetRpm(), 1e-9);
        assertEquals(0, l.trim(), 1e-9);
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
        r.launcher.trimBy(-2);
        r.all().update();
        assertEquals(3000 * 0.98, r.launcher.targetRpm(), 1e-6);
        assertTrue(screen(r.launcher), screen(r.launcher).contains("Speed trim : -2%"));
        // The D-pad trims by a percentage here (the speed comes from the table), and hold-down resets.
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        assertTrue(line(plan.help(), "G2 D-pad up"), line(plan.help(), "G2 D-pad up").contains("speed trim +1%"));
        String down = line(plan.help(), "G2 D-pad down");
        assertTrue(down, down.contains("speed trim -1%") && down.contains("double-tap: speed trim 0"));
        assertTrue(line(plan.help(), "G2 D-pad up"), line(plan.help(), "G2 D-pad up").contains("hold: speed trim +1%, 4 a second"));
        assertTrue(screen(r.launcher), screen(r.launcher).startsWith("[Speed trim : -2%"));
    }

    /** A fixed speed near the motor's top speed (gobilda-5203-6000: 6000 RPM). */
    public static class NearTop {
        public static class Launcher {
            public static double speedRpm = 5900;
        }
    }

    @Test public void theTrimNeverAsksForMoreThanTheMotorCanDo() {
        RobotDefinition r = fixedBot(NearTop.class);
        r.setup(new RobotTestKit());
        Launcher l = launcherOf(r);
        l.spinUp();
        l.trimBy(+1);                             // 5959: still under the top
        r.all().update();
        assertEquals(5900 * 1.01, l.targetRpm(), 1e-6);
        assertTrue(!l.atMotorLimit());
        l.trimBy(+2);                             // 6077 asked: cut to the motor's 6000
        r.all().update();
        assertEquals(6000, l.targetRpm(), 1e-6);
        assertTrue(l.atMotorLimit());
        assertTrue(screen(l), screen(l).contains("Speed trim : +3% (motor limit)"));
        l.trimBy(+1);                             // no faster: the trim stays where it is
        assertEquals(3, l.trim(), 1e-9);
        l.trimBy(-5);                             // slower still works
        r.all().update();
        assertEquals(5900 * 0.98, l.targetRpm(), 1e-6);
        assertTrue(!l.atMotorLimit());
    }

    @Test public void aHeldDpadStepsFourTimesASecond() {
        RobotDefinition r = fixedBot(Tuned.class);     // fixed speed: the D-pad steps 100 rpm
        r.setup(new RobotTestKit());
        Launcher l = launcherOf(r);
        l.clock = clock::get;
        l.spinUp();
        // The hold starts 0.3 s after the press (Tunables.Buttons.holdSec); its first step 0.2 s later.
        l.repeatStep(1);
        clock.addSec(0.1);
        l.repeatStep(1);
        r.all().update();
        assertEquals("not yet", 2600, l.targetRpm(), 1e-9);
        clock.addSec(0.1);
        l.repeatStep(1);                               // 0.5 s after the press: one step
        clock.addSec(0.25);
        l.repeatStep(1);                               // and one every 0.25 s
        r.all().update();
        assertEquals(2800, l.targetRpm(), 1e-9);
        l.repeatEnd();
        l.repeatStep(-1);                              // a new hold waits again
        r.all().update();
        assertEquals(2800, l.targetRpm(), 1e-9);
    }

    @Test public void lastTimesTrimIsOfferedAtInitAndNeverAppliedByItself() {
        AimTest.AimBot first = new AimTest.AimBot(AimTest.GoalTunables.class, false);
        first.setup(new RobotTestKit());
        first.launcher.trimBy(+3);
        // The next OpMode (the robot app still running): a new robot, the same launcher name.
        AimTest.AimBot r = new AimTest.AimBot(AimTest.GoalTunables.class, false);
        r.setup(new RobotTestKit());
        assertEquals("not applied by itself", 0, r.launcher.trim(), 1e-9);
        assertEquals("+3%", r.launcher.lastTrimOffered());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.launcher.initLoop(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("Trim last time : +3% (double-tap D-pad up after START to use it)"));
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        String up = line(plan.help(), "G2 D-pad up");
        assertTrue(up, up.contains("double-tap: use last time's trim (+3%)"));
        assertTrue(r.launcher.useLastTrim());
        assertEquals(3, r.launcher.trim(), 1e-9);
        assertEquals("used: no longer offered", "", r.launcher.lastTrimOffered());
        r.launcher.resetTrim();
        AimTest.AimBot third = new AimTest.AimBot(AimTest.GoalTunables.class, false);
        third.setup(new RobotTestKit());
        assertEquals("trimmed back to 0 last time: nothing to offer", "", third.launcher.lastTrimOffered());
        assertTrue(!third.launcher.useLastTrim());
    }

    @Test public void aTrimIsNeverTheTableTheShotTunerRecords() {
        RobotDefinition r = fixedBot(Tuned.class);
        r.setup(new RobotTestKit());
        Launcher l = launcherOf(r);
        l.trimBy(+5);
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

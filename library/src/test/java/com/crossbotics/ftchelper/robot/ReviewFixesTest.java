package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The fixes from the v0.17 reviews: START commands wait for the interlock rules, a part whose position
 * is not known waits while the other part is in its zone, a route through a zone counts as a request,
 * and the clearer INIT notes (start angle, battery, numbers outside their range, mirrorForRed in one
 * place, a part's button taken by Controls.java, "done (0 fired)").
 */
public class ReviewFixesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        SafeMotor.freshReadingsForTests(true);
        Turret.forgetAngles();
        RobotDefinition.LAST_AUTO_MIRROR.clear();
    }

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
        Turret.forgetAngles();
        RobotDefinition.LAST_AUTO_MIRROR.clear();
    }

    private RobotTestKit kit;
    private long nowNanos = 0;

    // Every pretend motor moves by its power (40 counts per loop at full power), then one robot loop
    // (20 ms on the rules' clock).
    private void loops(RobotDefinition r, int n) {
        r.interlocks.clock = () -> nowNanos;
        for (int i = 0; i < n; i++) {
            for (FakeMotor m : kit.motors.values()) m.position += m.power * 40;
            nowNanos += 20_000_000L;
            r.all().update();
        }
    }

    private static String screen(java.util.function.Consumer<RobotTelemetry> what) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        what.accept(t);
        t.update();
        return ft.lastScreen.toString();
    }

    // ----------------------------- a servo hood and a lift: START waits -----------------------------

    public static class HoodLift {
        public static class Hood {
            public static double posA = 0.2, posB = 0.7, angleA = 0, angleB = 25, startAngle = 20, travelSec = 0.5;
        }
        public static class Lift {
            public static double min = 0, max = 2600, kD = 0, down = 0, high = 2400;
        }
        public static class HoodLiftRule {
            public static double hoodFrom = 15, hoodTo = 25;   // the hood up high hits the lift's carriage
            public static double liftBelow = 300;              // while the lift is low
        }
    }

    static final class HoodLiftBot extends RobotDefinition {
        Hood hood;
        Lift lift;
        Interlock rule;

        @Override protected void define() {
            tunables(HoodLift.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            hood = add(Hood.servo("hood"));
            lift = add(Lift.slide("lift"));
            rule = keepApart("HoodLiftRule", hood.zone(), lift.zoneBelow());
        }
    }

    // Codex 2: the hood's START move (to its startAngle, in its zone) must not be sent before the rule
    // has looked: the lift starts low (in its zone), so the hood waits. A servo not sent anywhere yet
    // may be anywhere, so it does not move at all until the lift is out (Codex 6, for a servo).
    @Test public void startCommandsWaitForTheInterlockRules() {
        kit = new RobotTestKit();
        HoodLiftBot r = new HoodLiftBot();
        r.setup(kit);
        r.started();
        assertEquals("nothing sent at START", 0, kit.servos.get("hood").writes);
        loops(r, 5);
        assertEquals("still nothing: the lift is low", 0, kit.servos.get("hood").writes);
        assertTrue(r.rule.status(), r.rule.status().startsWith("Hood waits (not sent anywhere yet"));
        assertTrue(r.rule.status(), r.rule.status().contains("move Lift out of its zone first"));
        // The lift goes up, out of its zone: now the hood goes to its startAngle.
        r.lift.goTo("high");
        loops(r, 100);
        assertTrue("lift " + r.lift.position(), r.lift.position() > 300);
        assertTrue(kit.servos.get("hood").writes > 0);
        assertEquals(20, r.hood.targetAngle(), 1e-9);
    }

    // ----------------------------- a turret with a home switch and a lift -----------------------------

    public static class SwitchTurret {
        public static class Turret {
            public static double min = -200, max = 200, kD = 0, homeAngle = -45;
        }
        public static class Lift {
            public static double min = 0, max = 2600, kD = 0, down = 0, high = 2400;
        }
        public static class TurretLift {
            public static double turretFrom = 154, turretTo = 218, liftAbove = 600;
        }
    }

    static final class SwitchBot extends RobotDefinition {
        Turret turret;
        Lift lift;
        Interlock rule;

        @Override protected void define() {
            tunables(SwitchTurret.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            turret = add(Turret.motor("turret", "gobilda-5203-117").homeSwitch("turret_home"));
            lift = add(Lift.slide("lift"));
            rule = keepApart("TurretLift", turret.zone(), lift.zoneAbove());
        }
    }

    // Codex 6: homing sweeps the turret round to its switch. With the lift up it waits (no power, and
    // its homing time limit does not run out), says why, and homes once the lift is down.
    @Test public void aTurretWaitsToHomeWhileTheLiftIsUp() {
        kit = new RobotTestKit();
        SwitchBot r = new SwitchBot();
        r.setup(kit);
        kit.motors.get("lift").position = 1000;                 // the lift is up (in its zone)
        r.started();
        for (int i = 0; i < 50; i++) {
            loops(r, 1);
            assertEquals("no homing power while the lift is up", 0, kit.motors.get("turret").power, 1e-9);
        }
        assertFalse(r.turret.isHomed());
        assertTrue(r.rule.status(), r.rule.status().startsWith("Turret waits (not homed yet): Lift is above 600 counts"));
        assertTrue(screen(r.turret::addTelemetry), screen(r.turret::addTelemetry).contains("homing waits"));
        // The lift comes down (only out of its zone is allowed): then the turret homes.
        r.lift.goTo("down");
        boolean homing = false;
        for (int i = 0; i < 200 && !homing; i++) {
            loops(r, 1);
            homing = kit.motors.get("turret").power != 0;
        }
        assertTrue("homing starts once the lift is down", homing);
        assertTrue("lift " + r.lift.position(), r.lift.position() < 600);
        assertFalse(r.turret.part().getFault(), r.turret.part().getFault().contains("homing failed"));
    }

    // ----------------------------- a route through a zone -----------------------------

    public static class LiftBetween {
        public static class Turret {
            public static double min = -200, max = 200, kD = 0;
        }
        public static class Lift {
            public static double min = 0, max = 2600, kD = 0, down = 0, high = 2400;
        }
        public static class TurretLift {
            public static double turretFrom = 154, turretTo = 218;
            public static double liftFrom = 400, liftTo = 600;   // only this band of the lift's travel
        }
    }

    static final class BetweenBot extends RobotDefinition {
        Turret turret;
        Lift lift;
        Interlock rule;

        @Override protected void define() {
            tunables(LiftBetween.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            turret = add(Turret.motor("turret", "gobilda-5203-117"));
            lift = add(Lift.slide("lift"));
            rule = keepApart("TurretLift", turret.zone(), lift.zone());
        }
    }

    // Codex 8: the lift at 0 going to 2400 crosses its zone (400 to 600). That counts as asking for the
    // zone: it goes (the turret is out of its zone), instead of waiting below it for ever.
    @Test public void aRouteThroughTheZoneIsARequestToEnterIt() {
        kit = new RobotTestKit();
        BetweenBot r = new BetweenBot();
        r.setup(kit);
        r.started();
        r.lift.goTo("high");
        loops(r, 300);
        assertEquals(2400, r.lift.position(), 30);
        assertFalse(r.rule.status(), r.rule.isHolding());
    }

    // ----------------------------- the turret's start angle -----------------------------

    public static class Range10to170 {
        public static class Turret { public static double min = 10, max = 170, kD = 0; }
    }

    public static class StartWrittenOutside {
        public static class Turret { public static double min = 10, max = 170, kD = 0, startAngle = 0; }
    }

    static final class TurretOnly extends RobotDefinition {
        final Class<?> numbers;
        final boolean servo;
        Turret turret;

        TurretOnly(Class<?> numbers, boolean servo) {
            this.numbers = numbers;
            this.servo = servo;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            turret = add(servo ? Turret.servo("turret") : Turret.motor("turret", "gobilda-5203-117"));
        }
    }

    // A robot whose range is 10 to 170 must not stop at INIT because startAngle (default 0) is outside:
    // it counts from the middle and says so. A startAngle WRITTEN outside the range still stops.
    @Test public void noStartAngleOutsideTheRangeUsesTheMiddle() {
        kit = new RobotTestKit();
        TurretOnly r = new TurretOnly(Range10to170.class, false);
        r.setup(kit);
        assertEquals(90, r.turret.angle(), 1e-9);
        assertTrue(r.notes().toString(), r.notes().toString().contains("no startAngle and 0 is outside its range 10 to 170: it counts from 90 deg"));
        try {
            new TurretOnly(StartWrittenOutside.class, false).setup(new RobotTestKit());
            fail("a written startAngle outside the range stops INIT");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("startAngle (0.0) is outside its range"));
        }
    }

    public static class ServoTurret {
        public static class Turret {
            public static double posA = 0.1, angleA = -90, posB = 0.9, angleB = 90;
        }
    }

    // A servo turret turns itself: no PID, power or slow-zone numbers in the tuning menu or the notes.
    @Test public void aServoTurretReadsNoMotorNumbers() {
        kit = new RobotTestKit();
        TurretOnly r = new TurretOnly(ServoTurret.class, true);
        r.setup(kit);
        for (Tunable t : Tunable.all()) assertFalse(t.name(), t.name().startsWith("Turret/k"));
        String notes = r.notes().toString();
        for (String f : new String[] {"kP", "maxPower", "slowZone"}) assertFalse(notes, notes.contains(f));
    }

    public static class Start30 {
        public static class Turret { public static double min = -190, max = 190, kD = 0, startAngle = 30; }
    }

    // The carried angle's INIT line says what to do after moving the turret by hand, and doing it works:
    // an INIT stopped before START forgets the carried angle, so the next INIT uses startAngle.
    @Test public void aCarriedAngleCanBeDroppedByStoppingInInit() {
        kit = new RobotTestKit();
        TurretOnly first = new TurretOnly(Start30.class, false);
        first.setup(kit);
        first.started();
        first.turret.aimAt(-12);
        loops(first, 200);
        first.all().stop();
        kit = new RobotTestKit();
        TurretOnly second = new TurretOnly(Start30.class, false);
        second.setup(kit);
        assertEquals(-12, second.turret.angle(), 1);
        String init = screen(second::initLoop);
        assertTrue(init, init.contains("If the turret was moved by hand since: turn it to startAngle 30 deg, stop this OpMode and INIT again"));
        second.all().stop();                                   // stopped in INIT (no START)
        TurretOnly third = new TurretOnly(Start30.class, false);
        third.setup(new RobotTestKit());
        assertEquals(30, third.turret.angle(), 1e-9);
    }

    // ----------------------------- the launcher's INIT notes -----------------------------

    public static class LauncherNumbers {
        public static class Launcher {
            public static double speedRpm = 2600, kF = 11.7, kP = 1.0, trimMaxPercent = 80;
        }
    }

    public static class LauncherNoKf {
        public static class Launcher {
            public static double speedRpm = 2600, kP = 1.0;
        }
    }

    static final class LauncherBot extends RobotDefinition {
        final Class<?> numbers;
        Launcher launcher;

        LauncherBot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
        }
    }

    @Test public void theLauncherSaysWhatItAssumesOrIgnores() {
        LauncherBot r = new LauncherBot(LauncherNumbers.class);
        r.setup(new RobotTestKit());
        String notes = r.notes().toString();
        assertTrue(notes, notes.contains("Launcher: battery compensation on, nominalVolts not written: assuming 12.8 V "
                + "(the Flywheel Finder shows yours; batteryCompensation = false turns it off)"));
        assertTrue(notes, notes.contains("Tunables.Launcher.trimMaxPercent = 80: at most 50 is used"));
        LauncherBot noKf = new LauncherBot(LauncherNoKf.class);
        noKf.setup(new RobotTestKit());
        assertTrue(noKf.notes().toString(), noKf.notes().toString().contains("kP/kI/kD are not used without kF"));
    }

    // "Fire all: done" when nothing was fired is misleading: the action says how many it fired.
    @Test public void aFiringActionSaysHowManyItFired() {
        LauncherBot r = new LauncherBot(LauncherNumbers.class);
        r.setup(new RobotTestKit());
        r.started();
        Action fireAll = Action.named("Fire all")
                .then(() -> r.launcher.fire(3))
                .then(() -> r.launcher.part().stopShooting());   // as if the launcher gave up at once
        r.run(fireAll);
        r.all().update();
        r.all().update();
        assertEquals("done", fireAll.result());
        assertEquals("Fire all: done (0 fired)", fireAll.status());
        // An action that does not fire says nothing more.
        Action spin = Action.named("Spin").then(r.launcher::spinUp);
        r.run(spin);
        r.all().update();
        assertEquals("Spin: done", spin.status());
    }

    // ----------------------------- numbers outside their range -----------------------------

    public static class ClawTooFar {
        public static class Claw { public static double open = 1.2, closed = 0.3; }
    }

    static final class ClawBot extends RobotDefinition {
        @Override protected void define() {
            tunables(ClawTooFar.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            add(Claw.servo("claw"));
        }
    }

    // Claw.open = 1.2 is not a "tuned value to copy" (1.000): INIT says it is outside 0 to 1.
    @Test public void aNumberOutsideItsRangeIsAClearInitWarning() {
        ClawBot r = new ClawBot();
        r.setup(new RobotTestKit());
        List<String> problems = r.settingProblems();
        assertTrue(problems.toString(), problems.toString().contains(
                "Tunables.Claw.open = 1.2 is outside its allowed range 0 to 1: 1 is used. Fix it in Tunables.java"));
        for (String c : RobotDefinition.changedTunables()) assertFalse(c, c.contains("Claw.open"));
    }

    // ----------------------------- mirrorForRed: one setting -----------------------------

    public static class AimAndOdometryDiffer {
        public static class Aim { public static boolean mirrorForRed = false; }
        public static class Odometry { public static boolean mirrorForRed = true; }
    }

    public static class NoMirror {
        public static class Drive { public static double maxPower = 1; }
    }

    public static class HalfPairs {
        public static class Aim { public static double goalX = 13; }
        public static class Odometry { public static double resetX = -36; }
    }

    static final class Plain extends RobotDefinition {
        final Class<?> numbers;

        Plain(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
        }
    }

    @Test public void mirrorForRedIsOneSettingForTheWholeRobot() {
        Plain differ = new Plain(AimAndOdometryDiffer.class);
        differ.setup(new RobotTestKit());
        assertFalse("the Aim one wins", differ.mirrorForRed());
        assertTrue(differ.settingProblems().toString(), differ.settingProblems().toString().contains("differ: the Aim one is used"));
        // Nothing in Tunables: the autonomous's own mirrorForRed(), and the TeleOp after it uses it too.
        Plain auto = new Plain(NoMirror.class);
        assertTrue("default", auto.mirrorForRed());
        assertEquals("mirrorForRed not written: default true", auto.mirrorSource());
        auto.autoMirror = false;
        assertFalse(auto.mirrorForRed());
        RobotDefinition.LAST_AUTO_MIRROR.put(Plain.class.getName(), false);
        Plain teleOp = new Plain(NoMirror.class);
        assertFalse(teleOp.mirrorForRed());
        assertEquals("the last autonomous's mirrorForRed() = false", teleOp.mirrorSource());
    }

    @Test public void numbersThatGoInPairsAreWarnedAboutAlone() {
        Plain r = new Plain(HalfPairs.class);
        r.setup(new RobotTestKit());
        String p = r.settingProblems().toString();
        assertTrue(p, p.contains("Tunables.Aim: goalX and goalY go together"));
        assertTrue(p, p.contains("Tunables.Odometry: the reset spot needs resetX and resetY"));
    }

    // ----------------------------- the button map -----------------------------

    // A job in Controls.java on a button a part suggested: the part has no button for it, and INIT says so.
    @Test public void aTeamJobOnAPartsButtonIsAnInitNote() {
        LauncherBot r = new LauncherBot(LauncherNumbers.class);
        r.setup(new RobotTestKit());
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> g2.rb.press(() -> { }).says("score high"));
        String notes = plan.notes().toString();
        assertTrue(notes, notes.contains("Launcher fire one; hold: keep firing: no button (G2 RB is your \"score high\" in Controls.java)"));
    }

    // "hold 1 s: ..." words are not given the gesture's name a second time.
    @Test public void aHeldForJobIsNotNamedTwice() {
        Gestures g = new Gestures();
        g.says.put(Gestures.heldForKey(1.0), "hold 1 s: flywheel off");
        g.says.put(Gestures.TAP, "fire");
        assertEquals("hold 1 s: flywheel off; tap: fire", g.help());
        g.says.put(Gestures.heldForKey(1.0), "piece count = 0");
        assertEquals("held 1 s: piece count = 0; tap: fire", g.help());
    }

    // ----------------------------- the pose after an alliance switch -----------------------------

    // Set, then the alliance switched on a turned field: "not set for RED", never "never set".
    @Test public void aPoseUnsetByAnAllianceSwitchIsNotCalledNeverSet() {
        AimTest.AimBot r = new AimTest.AimBot(AimTest.TurnedField.class, false);
        r.setup(new RobotTestKit());
        r.tracker.setPose(new com.crossbotics.ftchelper.util.FieldPose(0, 0, 0));
        r.tracker.update();
        assertEquals("", r.aim().odometryProblem());
        assertTrue(ControlMap.poseAfterSwitch(r, Alliance.RED, null).contains("odometry aim OFF"));
        String p = r.aim().odometryProblem();
        assertTrue(p, p.startsWith("the pose is not set for RED (the alliance was switched)"));
        assertFalse(p, p.contains("never set"));
        r.tracker.setPose(new com.crossbotics.ftchelper.util.FieldPose(0, 0, 0));
        r.tracker.update();
        assertEquals("", r.aim().odometryProblem());
        assertNull(r.poseUnsetBy);
    }
}

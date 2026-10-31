package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testkit.PretendDevices;
import com.crossbotics.ftchelper.testkit.PretendTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.util.List;
import org.junit.After;
import org.junit.Test;

/**
 * A mentor's robot that is not one of the examples (review, 2026-10-22): a servo turret that must move
 * before its first target, a lift on two motors, the team's own per-loop code, misnamed Tunables
 * sections and fields. Uses the public test kit (PretendDevices), the way a team's tests would.
 */
public class TwoMotorsAndOwnPartsTest {

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
        Turret.forgetAngles();
    }

    public static class Numbers {
        public static class Drive { public static boolean calibrated = true; }
        public static class Turret {
            public static double posA = 0.0, angleA = -150, posB = 1.0, angleB = 150, travelSec = 0.6;
        }
        public static class LiftLeft {
            public static double min = 0, max = 2900;
            public static double kP = 0.004, kI = 0, kD = 0, kF = 0.08;
            public static double down = 0, high = 2700;
        }
        public static class Claw { public static double open = 0.25, closed = 0.70; }
        public static class TurretLift {
            public static double turretFrom = 120, turretTo = 240;
            public static double liftLeftAbove = 400;
        }
    }

    public static class Bot extends RobotDefinition {
        public Turret turret;
        public Lift lift;
        public Claw claw;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").motor("gobilda-5203-312").wheelMm(104));
            turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange());
            lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"));
            claw = add(Claw.servo("claw"));
            keepApart("TurretLift", turret.zone(), lift.zoneAbove());
        }
    }

    // ----------------------------- two motors on one lift -----------------------------

    @Test public void bothLiftMotorsGetTheSamePowerAndStopTogether() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        assertEquals("the second motor turns the other way", DcMotorSimple.Direction.REVERSE, d.motor("lift_right").direction);
        assertEquals(DcMotorSimple.Direction.FORWARD, d.motor("lift_left").direction);
        // Turret out of its zone first (it went to the middle, 0 deg, at START).
        r.all().update();
        r.lift.goTo("high");
        r.all().update();
        assertTrue("the lift goes up", d.motor("lift_left").power > 0.5);
        assertEquals("same power", d.motor("lift_left").power, d.motor("lift_right").power, 1e-12);
        // Pause (BACK): both stop now.
        r.pauseAll();
        assertEquals(0, d.motor("lift_left").power, 1e-12);
        assertEquals(0, d.motor("lift_right").power, 1e-12);
        r.resume();
        r.all().update();
        assertTrue(d.motor("lift_right").power > 0.5);
        // STOP: both stop.
        r.all().stop();
        assertEquals(0, d.motor("lift_left").power, 1e-12);
        assertEquals(0, d.motor("lift_right").power, 1e-12);
    }

    @Test public void theSecondMotorIsADeviceOfTheLift() {
        Bot r = new Bot();
        r.describe();
        assertTrue(r.lift.deviceNames().contains("lift_right"));
        assertEquals("lift_right", r.lift.secondMotorName());
        PretendDevices d = new PretendDevices().notConfigured("lift_right");
        List<String> problems = r.checkConfiguration(d);
        assertTrue(problems.toString(), problems.toString().contains("\"lift_right\" (LiftLeft): NOT in the configuration"));
        // Its simulator line ties it to the first motor's slide, turning the other way.
        String lines = String.join("\n", new Bot().simLines());
        assertTrue(lines, lines.contains("motor.lift_right = lift, part=gobilda-5203-435, with=lift_left, mirrored=true"));
    }

    @Test public void aLiftTakesTwoMotorsAtMost() {
        try {
            Lift.slide("a").alsoMotor("b").alsoMotor("c");
            fail("a third motor must stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("already has a second motor"));
        }
    }

    // ----------------------------- the servo turret before its first command -----------------------------

    @Test public void aServoTurretGoesToItsStartAngleAtStartAndTheStickWorks() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        assertTrue("before START it was sent nowhere", Double.isNaN(d.servo("turret").position));
        r.startForTests();
        r.all().update();
        assertEquals("the middle of -150..150 = 0 deg = servo 0.5", 0.5, d.servo("turret").position, 1e-9);
        assertEquals(0, r.turret.angle(), 1e-6);
        // The stick moves it from there.
        r.turret.manual(1);
        r.all().update();
        r.turret.manual(1);
        r.all().update();
        assertTrue("it turned counterclockwise", d.servo("turret").position > 0.5);
    }

    @Test public void aServoTurretStickBeforeAnyCommandStartsFromTheStartAngle() {
        com.crossbotics.ftchelper.mechanism.PositionServo s = new com.crossbotics.ftchelper.mechanism.PositionServo("t",
                new PretendDevices().servo("t").servo).degrees(0, -90, 1, 90).travelTime(0.5);
        com.crossbotics.ftchelper.launcher.Turret t = com.crossbotics.ftchelper.launcher.Turret.servo("t", s).limits(-90, 90).startAngle(30);
        assertTrue(Double.isNaN(t.getAngle()));
        assertEquals(30, t.servoStartAngle(), 1e-9);
        t.manual(0.5);
        assertEquals("the stick starts from the start angle, not from nowhere", 30, t.getTargetAngle(), 1e-9);
        t.update();
        assertFalse(Double.isNaN(t.getAngle()));
    }

    @Test public void theLiftHeldByAnUnknownTurretSaysWhy() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        // Not started: the servo turret was sent nowhere, so it may be anywhere (in its zone too).
        r.lift.goTo("high");
        r.all().update();
        r.all().update();
        String status = r.interlocks().get(0).status();
        assertTrue(status, status.contains("LiftLeft held"));
        assertTrue(status, status.contains("Turret angle unknown"));
        PretendTelemetry screen = new PretendTelemetry();
        RobotTelemetry t = new RobotTelemetry(screen.telemetry, () -> 0L);
        r.all().addTelemetry(t);
        t.update();
        assertTrue(screen.lastScreen.toString(), screen.has("TurretLift : LiftLeft held"));
        assertTrue("one turret line, no NaN: " + screen.lastScreen, screen.has("Turret : not sent anywhere yet"));
        assertTrue("a claw with no command yet is not \"-\" (that is a part not on the robot): " + screen.lastScreen,
                screen.has("Claw : not sent yet"));
    }

    @Test public void theTurretShowsOneLine() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        r.startForTests();
        r.all().update();
        PretendTelemetry screen = new PretendTelemetry();
        RobotTelemetry t = new RobotTelemetry(screen.telemetry, () -> 0L);
        r.all().addTelemetry(t);
        t.update();
        int lines = 0;
        for (String l : screen.lastScreen) if (l.startsWith("Turret :")) lines++;
        assertEquals(screen.lastScreen.toString(), 1, lines);
    }

    // ----------------------------- the team's own parts -----------------------------

    static final class Own implements Subsystem {
        int updates = 0, stops = 0;
        @Override public void update() { updates++; }
        @Override public void stop() { stops++; }
    }

    @Test public void ownPartsAreUpdatedAndStoppedByPauseAndStop() {
        PretendDevices d = new PretendDevices();
        Bot r = new Bot();
        r.setup(d);
        Own own = r.addOwn(new Own());
        Own hack = new Own();
        r.all().add(hack);                 // the old way round: still stopped at pause
        final int[] loops = {0};
        r.everyLoop(() -> loops[0]++);
        r.startForTests();
        r.all().update();
        assertEquals(1, own.updates);
        assertEquals(1, loops[0]);
        r.pauseAll();
        assertEquals("pause stops the team's own part", 1, own.stops);
        assertEquals("and one added with all().add(...)", 1, hack.stops);
        r.all().update();
        assertEquals("not updated while paused", 1, own.updates);
        assertEquals(1, loops[0]);
        r.resume();
        r.all().update();
        assertEquals(2, own.updates);
        r.all().stop();
        assertTrue(own.stops >= 2);
    }

    // ----------------------------- names in Tunables.java -----------------------------

    public static class Misnamed {
        public static class Drive { public static boolean calibrated = true; }
        public static class LiftLeft { public static double min = 0, max = 2900; }
        public static class Lift { public static double high = 2700; }            // the module's kind, not its device
        public static class Clow { public static double open = 0.25, closed = 0.7; }   // a typo of Claw
        public static class Auto { public static double parkInches = 20; }        // the team's own: fine
    }

    public static class MisnamedBot extends RobotDefinition {
        @Override protected void define() {
            tunables(Misnamed.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").noImu());
            add(Lift.slide("lift_left"));
            add(Claw.servo("claw"));
        }
    }

    @Test public void aSectionNamedAfterThePartsKindIsAWarning() {
        List<String> problems = new MisnamedBot().checkTunables();
        String all = problems.toString();
        assertTrue(all, all.contains("Tunables.Lift is not read by any part: your lift's section is Tunables.LiftLeft"));
        assertTrue(all, all.contains("Tunables.Clow is not read by any part: did you mean Tunables.Claw?"));
        assertFalse(all, all.contains("Tunables.Auto"));
        MisnamedBot r = new MisnamedBot();
        r.setup(new PretendDevices());
        assertEquals(2, r.sectionWarnings().size());
    }

    public static class WrongZoneName {
        public static class Drive { public static boolean calibrated = true; }
        public static class Turret { public static double posA = 0.0, angleA = -150, posB = 1.0, angleB = 150; }
        public static class LiftLeft { public static double min = 0, max = 2900; }
        public static class Claw { public static double open = 0.25, closd = 0.7; }
        public static class TurretLift {
            public static double turretFrom = 120, turretTo = 240;
            public static double liftAbove = 400;     // the rule reads liftLeftAbove (device "lift_left")
        }
    }

    public static class WrongZoneBot extends RobotDefinition {
        @Override protected void define() {
            tunables(WrongZoneName.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").noImu());
            Turret t = add(Turret.servo("turret"));
            Lift l = add(Lift.slide("lift_left"));
            keepApart("TurretLift", t.zone(), l.zoneAbove());
        }
    }

    @Test public void anInterlockFieldNamedAfterTheKindGetsADidYouMean() {
        try {
            new WrongZoneBot().setup(new PretendDevices());
            fail("INIT must stop on a field the rule does not read");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Tunables.TurretLift.liftAbove"));
            assertTrue(e.getMessage(), e.getMessage().contains("did you mean liftLeftAbove?"));
        }
    }

    public static class ClawTypo extends RobotDefinition {
        @Override protected void define() {
            tunables(WrongZoneName.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").noImu());
            add(Claw.servo("claw"));
        }
    }

    @Test public void aClawPositionTypoGetsASuggestion() {
        try {
            new ClawTypo().setup(new PretendDevices());
            fail("a claw needs open and closed");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("closd -> closed?"));
        }
    }

    @Test public void anUnknownPositionGetsADidYouMean() {
        PretendDevices d = new PretendDevices();
        com.crossbotics.ftchelper.mechanism.PositionServo s = new com.crossbotics.ftchelper.mechanism.PositionServo("wrist", d.servo("wrist").servo)
                .position("level", 0.5).position("down", 0.1);
        s.goTo("lvel");
        assertTrue(s.getWarning(), s.getWarning().contains("did you mean level?"));
    }

    // ----------------------------- the COMPETITION warning comes first -----------------------------

    @Test public void warnFirstGoesAboveEarlierWarnings() {
        PretendTelemetry screen = new PretendTelemetry();
        RobotTelemetry t = new RobotTelemetry(screen.telemetry, () -> 0L);
        t.warn("Choose the alliance");
        t.warn("Aim odometry never set");
        t.warnFirst("COMPETITION: Intake is OFF");
        t.warnFirst("COMPETITION: Claw is OFF");
        t.update();
        assertEquals("!! COMPETITION: Intake is OFF", screen.lastScreen.get(0));
        assertEquals("!! COMPETITION: Claw is OFF", screen.lastScreen.get(1));
        assertEquals("!! Choose the alliance", screen.lastScreen.get(2));
        assertNotNull(screen.lastScreen);
    }

    // ----------------------------- two robots in one app -----------------------------

    public static class NumbersA {
        public static class Drive { public static boolean calibrated = true; }
        public static class Lift {
            public static double min = 0, max = 3000, kP = 0.004, kI = 0, kD = 0, kF = 0.08, down = 0, high = 2700;
        }
        public static class Claw { public static double open = 0.25, closed = 0.70; }
    }

    public static class NumbersB {
        public static class Drive { public static boolean calibrated = true; }
        public static class Lift {
            public static double min = 0, max = 3000, kP = 0.009, kI = 0, kD = 0, kF = 0.02, down = 0, high = 1500;
        }
        public static class Claw { public static double open = 0.40, closed = 0.90; }
    }

    public static class NumbersNoGains {
        public static class Drive { public static boolean calibrated = true; }
        public static class Lift { public static double min = 0, max = 3000, down = 0, high = 1500; }
        public static class Claw { public static double open = 0.40, closed = 0.90; }
    }

    static final class LiftAndClaw extends RobotDefinition {
        final Class<?> numbers;
        final String part;
        Lift lift;
        Claw claw;

        LiftAndClaw(Class<?> numbers) { this(numbers, "gobilda-5203-435"); }

        LiftAndClaw(Class<?> numbers, String part) {
            this.numbers = numbers;
            this.part = part;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").motor("gobilda-5203-312").wheelMm(104));
            lift = add(Lift.slide("lift").part(part));
            claw = add(Claw.servo("claw"));
        }
    }

    // Review, 2026-10-30: gains not written in Tunables come from the motor: the second robot got the first's.
    @Test public void aSecondRobotWithAnotherMotorGetsItsOwnStartingGains() {
        // One PretendDevices: making a new one forgets every tunable, as a new app would.
        PretendDevices d = new PretendDevices();
        new LiftAndClaw(NumbersNoGains.class, "gobilda-5203-117").setup(d);
        double alone = com.crossbotics.ftchelper.tuning.Tunable.number("Lift/kP", -1, 1).get();
        TuningReset.clear();
        new LiftAndClaw(NumbersNoGains.class, "gobilda-5203-1150").setup(d);
        double first = com.crossbotics.ftchelper.tuning.Tunable.number("Lift/kP", -1, 1).get();
        assertTrue("the test needs two different values: " + alone, Math.abs(first - alone) > 1e-9);
        new LiftAndClaw(NumbersNoGains.class, "gobilda-5203-117").setup(d);
        assertEquals(alone, com.crossbotics.ftchelper.tuning.Tunable.number("Lift/kP", -1, 1).get(), 1e-9);
    }

    // The positions and gains written in Tunables: each robot's own (same names).
    @Test public void aSecondRobotWithTheSameNamesUsesItsOwnNumbers() {
        PretendDevices d = new PretendDevices();
        new LiftAndClaw(NumbersA.class).setup(d);
        LiftAndClaw b = new LiftAndClaw(NumbersB.class);
        b.setup(d);
        assertEquals(1500, com.crossbotics.ftchelper.tuning.Tunable.number("Lift/high", -1, 1).get(), 1e-9);
        assertEquals(0.009, com.crossbotics.ftchelper.tuning.Tunable.number("Lift/kP", -1, 1).get(), 1e-9);
        assertEquals(0.02, com.crossbotics.ftchelper.tuning.Tunable.number("Lift/kF", -1, 1).get(), 1e-9);
        assertEquals(0.90, com.crossbotics.ftchelper.tuning.Tunable.number("Claw/closed", -1, 1).get(), 1e-9);
        b.startForTests();
        b.claw.close();
        b.all().update();
        assertEquals("the servo goes to robot B's number", 0.90, d.servo("claw").position, 1e-9);
    }
}

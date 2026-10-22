package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.safety.SafeMotor;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Interlock rules: two parts never in their zones at once. The turret and lift case (the turret out of
 * the back while the lift is up), the turret going the other way round, both parts in their zones,
 * a servo's time estimate, actions, the Driver Station line and the INIT checks.
 */
public class InterlockTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        SafeMotor.freshReadingsForTests(true);
        Turret.forgetAngles();
    }

    @After public void back() {
        SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
        Turret.forgetAngles();
    }

    // ----------------------------- robots and numbers -----------------------------

    public static class Numbers {
        public static class Turret {
            public static double min = -200, max = 200, kD = 0;
        }
        public static class Lift {
            public static double min = 0, max = 2600, kD = 0;
            public static double down = 0, high = 2400;
        }
        public static class TurretLift {
            public static double turretFrom = 154, turretTo = 218;   // the back, degrees
            public static double liftAbove = 600;                    // counts
        }
    }

    /** A turret whose zone is on its left, with a range wide enough to go the other way round. */
    public static class SideZone {
        public static class Turret {
            public static double min = -270, max = 270, kD = 0;
        }
        public static class Lift {
            public static double min = 0, max = 2600, kD = 0;
            public static double down = 0, high = 2400;
        }
        public static class TurretLift {
            public static double turretFrom = 30, turretTo = 60;
            public static double liftAbove = 600;
        }
    }

    static class TurretLiftBot extends RobotDefinition {
        final Class<?> numbers;
        Turret turret;
        Lift lift;
        Interlock rule;

        TurretLiftBot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            turret = add(Turret.motor("turret", "gobilda-5203-117"));
            lift = add(Lift.slide("lift"));
            rule = keepApart("TurretLift", turret.zone(), lift.zoneAbove());
        }
    }

    private RobotTestKit kit;

    private TurretLiftBot bot(Class<?> numbers) {
        kit = new RobotTestKit();
        TurretLiftBot r = new TurretLiftBot(numbers);
        r.setup(kit);
        r.started();
        return r;
    }

    // Moves every pretend motor by its power (40 counts per loop at full power), then one robot loop.
    private void loops(RobotDefinition r, int n) {
        for (int i = 0; i < n; i++) {
            for (FakeMotor m : kit.motors.values()) m.position += m.power * 40;
            r.all().update();
        }
    }

    private static String screen(RobotDefinition r) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.all().addTelemetry(t);
        t.update();
        return ft.lastScreen.toString();
    }

    // ----------------------------- the turret and the lift -----------------------------

    @Test public void theLiftWaitsBelowItsLineWhileTheTurretIsInTheBack() {
        TurretLiftBot r = bot(Numbers.class);
        r.turret.aimAt(175);                                   // straight back: in its zone
        loops(r, 120);
        assertEquals(175, r.turret.angle(), 2);
        r.lift.goTo("high");
        loops(r, 200);
        // Held two margins (2 x 52 counts) below the line, never in its zone; its target is kept.
        assertTrue("lift " + r.lift.position(), r.lift.position() < 600 - 52 && r.lift.position() > 400);
        assertEquals("high", r.lift.part().getTargetName());
        assertFalse(r.lift.isAtTarget());
        assertTrue(r.rule.isHolding());
        assertTrue(r.rule.status(), r.rule.status().startsWith("Lift held at"));
        assertTrue(r.rule.status(), r.rule.status().contains("Turret is between 154 and 218 deg"));
        assertTrue(screen(r), screen(r).contains("TurretLift : Lift held at"));
        // The turret leaves: the lift goes on by itself.
        r.turret.aimAt(0);
        loops(r, 300);
        assertEquals(2400, r.lift.position(), 30);
        assertFalse(r.rule.isHolding());
        assertFalse(screen(r).contains("TurretLift"));
    }

    @Test public void aTurretCrossingTheBackWaitsAtTheEdgeWhileTheLiftIsUp() {
        TurretLiftBot r = bot(Numbers.class);
        r.lift.goTo("high");
        loops(r, 200);
        assertEquals(2400, r.lift.position(), 30);
        r.lift.goTo("down");
        loops(r, 200);
        r.turret.aimAt(140);
        loops(r, 150);
        r.lift.goTo("high");
        loops(r, 200);
        r.turret.aimAt(190);                                    // from 140 it has to pass 154..218
        loops(r, 150);
        assertTrue("turret " + r.turret.angle(), r.turret.angle() < 154 - 3 && r.turret.angle() > 140);
        assertTrue(r.turret.isHeld());
        assertFalse("never aimed while held", r.turret.isAimed());
        assertTrue(r.rule.status(), r.rule.status().startsWith("Turret held at"));
        assertTrue(r.rule.status(), r.rule.status().contains("Lift is above 600 counts"));
        // The lift comes down: the turret goes on to 190.
        r.lift.goTo("down");
        loops(r, 200);
        assertEquals(190, r.turret.angle(), 2);
        assertTrue(r.turret.isAimed());
        // Now the lift is the one that waits.
        r.lift.goTo("high");
        loops(r, 200);
        assertTrue(r.lift.position() < 600);
    }

    @Test public void theTurretGoesTheOtherWayRoundWhenItsRangeAllows() {
        TurretLiftBot r = bot(SideZone.class);
        r.lift.goTo("high");
        loops(r, 200);
        r.turret.aimAt(90);                                     // 0 -> 90 passes 30..60 on the left
        assertEquals("the same direction the long way: -270", -270, r.turret.part().getTargetAngle(), 1e-6);
        loops(r, 300);
        assertEquals(-270, r.turret.angle(), 3);
        assertFalse(r.turret.isHeld());
    }

    @Test public void bothInTheirZonesMayOnlyMoveOut() {
        TurretLiftBot r = bot(Numbers.class);
        double cpd = r.turret.countsPerDegree();
        // Pushed by hand: the turret at 180 (the back), the lift at 1000 (up).
        kit.motors.get("turret").position = 180 * cpd;
        kit.motors.get("lift").position = 1000;
        loops(r, 2);
        r.turret.aimAt(180);
        r.lift.goTo("high");
        loops(r, 3);
        assertTrue(r.rule.status(), r.rule.status().startsWith("both in their zones"));
        loops(r, 100);
        assertTrue("the lift does not go deeper", r.lift.position() <= 1000 + 5);
        r.turret.aimAt(200);                                    // deeper (no way out above 200): refused
        loops(r, 100);
        assertTrue("turret " + r.turret.angle(), r.turret.angle() <= 180.5);
        // The turret moves out: the lift keeps its zone and goes on up; now the turret waits.
        r.turret.aimAt(0);
        loops(r, 300);
        assertEquals(0, r.turret.angle(), 2);
        assertEquals(2400, r.lift.position(), 30);
        r.turret.aimAt(175);
        loops(r, 200);
        assertTrue(r.turret.angle() < 154);
    }

    @Test public void aStickIsHeldAtTheEdgeToo() {
        TurretLiftBot r = bot(Numbers.class);
        r.turret.aimAt(175);
        loops(r, 120);
        for (int i = 0; i < 200; i++) {
            r.lift.manual(1);                                   // the operator holds the stick up
            loops(r, 1);
        }
        assertTrue("lift " + r.lift.position(), r.lift.position() < 600 - 52);
        assertTrue(r.rule.status(), r.rule.status().startsWith("Lift held at"));
        r.lift.manual(0);
        loops(r, 2);
    }

    @Test public void anActionWaitsForTheOtherPartAndThenFinishes() {
        TurretLiftBot r = bot(Numbers.class);
        r.turret.aimAt(175);
        loops(r, 120);
        Action up = Action.named("Lift up").then(() -> r.lift.goTo("high")).waitUntil("at the top", r.lift::isAtTarget, 30);
        r.run(up);
        loops(r, 200);
        assertTrue("still waiting while the turret is in the back", up.isRunning());
        r.turret.aimAt(0);
        loops(r, 300);
        assertFalse(up.isRunning());
        assertEquals("done", up.result());
    }

    // ----------------------------- a servo: the time estimate -----------------------------

    public static class WristNumbers {
        public static class Wrist {
            public static double folded = 0.2, out = 0.8, travelSec = 0.3;   // full 0..1 move
        }
        public static class Lift {
            public static double min = 0, max = 1200, kD = 0;
            public static double down = 0, high = 1000;
        }
        public static class WristLift {
            public static double wristFrom = 0.5, wristTo = 1.0;     // the wrist folded out
            public static double liftBelow = 300;                    // the lift low
        }
    }

    static class WristBot extends RobotDefinition {
        Wrist wrist;
        Lift lift;
        Interlock rule;

        @Override protected void define() {
            tunables(WristNumbers.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            wrist = add(Wrist.servo("wrist"));
            lift = add(Lift.slide("lift"));
            rule = keepApart("WristLift", wrist.zone(), lift.zoneBelow());
        }
    }

    @Test public void aServoCountsAsEverywhereItMayBeUntilItsTravelTimeIsOver() throws InterruptedException {
        kit = new RobotTestKit();
        WristBot r = new WristBot();
        r.setup(kit);
        r.started();
        loops(r, 2);
        // Before its first command the wrist could be anywhere: with the lift low too, both are "in".
        assertTrue(r.rule.status(), r.rule.status().startsWith("both in their zones"));
        r.wrist.goTo("folded");
        loops(r, 2);
        assertTrue("its first move: still anywhere", r.rule.status().startsWith("both"));
        Thread.sleep(400);
        loops(r, 2);
        // Folded and arrived: the lift (low) keeps its zone; the wrist may not fold out now.
        r.wrist.goTo("out");
        loops(r, 2);
        assertTrue("held at the fence: " + r.wrist.part().getPosition(), r.wrist.part().getPosition() <= 0.5 - 0.02);
        assertTrue(r.rule.status(), r.rule.status().startsWith("Wrist held at"));
        // The lift goes up: the wrist goes out.
        r.lift.goTo("high");
        loops(r, 200);
        assertEquals(0.8, r.wrist.part().getPosition(), 1e-9);
        Thread.sleep(300);                                     // it has had time to get out
        // The lift may not come down until the wrist is folded AND has had time to get there.
        r.lift.goTo("down");
        loops(r, 100);
        assertTrue("lift " + r.lift.position(), r.lift.position() > 300);
        r.wrist.goTo("folded");
        loops(r, 20);
        assertTrue("still moving (0.6 x 0.3 s): the lift waits", r.lift.position() > 300);
        Thread.sleep(300);
        loops(r, 200);
        assertEquals(0, r.lift.position(), 30);
    }

    // ----------------------------- a condition -----------------------------

    static boolean bucketUp = false;

    public static class ArmNumbers {
        public static class Arm {
            public static double min = 0, max = 180, kD = 0, stow = 10, score = 150;
        }
        public static class ArmBucket {
            public static double armBelow = 40;
        }
    }

    static class ArmBot extends RobotDefinition {
        Arm arm;
        Interlock rule;

        @Override protected void define() {
            tunables(ArmNumbers.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            arm = add(Arm.motor("arm", "gobilda-5203-117"));
            rule = keepApart("ArmBucket", arm.zoneBelow(), Zone.when("the bucket is up", () -> bucketUp));
        }
    }

    @Test public void aConditionHoldsThePartBack() {
        kit = new RobotTestKit();
        ArmBot r = new ArmBot();
        r.setup(kit);
        r.started();
        double cpd = r.arm.countsPerUnit();
        kit.motors.get("arm").position = 150 * cpd;
        r.arm.goTo("score");
        loops(r, 5);
        bucketUp = true;
        r.arm.goTo("stow");
        for (int i = 0; i < 300; i++) loops(r, 1);
        assertTrue("arm " + r.arm.position(), r.arm.position() > 40);
        assertTrue(r.rule.status(), r.rule.status().endsWith("the bucket is up"));
        bucketUp = false;
        loops(r, 300);
        assertEquals(10, r.arm.position(), 3);
    }

    // ----------------------------- no rules: as before -----------------------------

    static class NoRules extends RobotDefinition {
        Lift lift;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            lift = add(Lift.slide("lift"));
        }
    }

    @Test public void aRobotWithNoRulesIsNotTouched() {
        kit = new RobotTestKit();
        NoRules r = new NoRules();
        r.setup(kit);
        assertTrue(r.interlocks().isEmpty());
        r.lift.goTo("high");
        loops(r, 200);
        assertEquals(2400, r.lift.position(), 30);
    }

    // ----------------------------- INIT checks -----------------------------

    public static class MissingSection {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
    }

    public static class MissingField {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
        public static class TurretLift { public static double turretFrom = 154, turretTo = 218; }
    }

    public static class EmptyZone {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
        public static class TurretLift { public static double turretFrom = 218, turretTo = 154, liftAbove = 600; }
    }

    public static class OutOfReach {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
        public static class TurretLift { public static double turretFrom = 154, turretTo = 218, liftAbove = 3000; }
    }

    public static class Everywhere {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
        public static class TurretLift { public static double turretFrom = 154, turretTo = 218, liftAbove = -10; }
    }

    public static class BadMargin {
        public static class Turret { public static double min = -200, max = 200, kD = 0; }
        public static class Lift { public static double min = 0, max = 2600, kD = 0; }
        public static class TurretLift { public static double turretFrom = 154, turretTo = 218, liftAbove = 600, liftMargin = -5; }
    }

    private static String initError(Class<?> numbers) {
        try {
            new TurretLiftBot(numbers).setup(new RobotTestKit());
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        fail("no INIT error for " + numbers.getSimpleName());
        return null;
    }

    @Test public void initStopsWithTheReason() {
        String m = initError(MissingSection.class);
        assertTrue(m, m.contains("Tunables.TurretLift is missing") && m.contains("turretFrom = ..., turretTo = ...") && m.contains("liftAbove = ..."));
        m = initError(MissingField.class);
        assertTrue(m, m.contains("Tunables.TurretLift.liftAbove is missing") && m.contains("counts"));
        m = initError(EmptyZone.class);
        assertTrue(m, m.contains("must be less than") && m.contains("empty"));
        m = initError(OutOfReach.class);
        assertTrue(m, m.contains("outside its range"));
        m = initError(Everywhere.class);
        assertTrue(m, m.contains("covers its whole range"));
        m = initError(BadMargin.class);
        assertTrue(m, m.contains("liftMargin must be 0 or more"));
    }

    @Test public void robotJavaMistakesStopAtOnce() {
        try {
            Intake.motor("intake").zone();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Zone.when"));
        }
        RobotDefinition twice = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.mecanum("fl", "fr", "bl", "br"));
                Lift l = add(Lift.slide("lift"));
                keepApart("Self", l.zoneAbove(), l.zoneBelow());
            }
        };
        try {
            twice.describe();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("two different parts"));
        }
    }

    @Test public void theRuleIsOnTheInitScreenAndNotInTheTuningMenu() {
        TurretLiftBot r = bot(Numbers.class);
        assertTrue(r.notes().toString(), r.notes().toString().contains("Interlock TurretLift: Turret between 154 and 218 deg / Lift above 600 counts"));
        for (com.crossbotics.ftchelper.tuning.Tunable t : com.crossbotics.ftchelper.tuning.Tunable.all()) {
            assertFalse("not live: " + t.name(), t.name().startsWith("TurretLift"));
        }
    }
}

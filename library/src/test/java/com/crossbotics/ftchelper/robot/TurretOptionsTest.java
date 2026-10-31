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
 * Every way to build a turret in five files: what turns it (motor, servo, CR servo with an encoder or an
 * absolute encoder), how it knows its angle (start angle, home switch touch or magnetic, carried over
 * from the last program), and its range (+/-90, +/-180, +/-190, a slip ring).
 */
public class TurretOptionsTest {
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

    /** A robot with one turret, built by the test's own line. */
    static final class OneTurret extends RobotDefinition {
        final Class<?> numbers;
        final java.util.function.Supplier<Turret> make;
        Turret turret;

        OneTurret(Class<?> numbers, java.util.function.Supplier<Turret> make) {
            this.numbers = numbers;
            this.make = make;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            turret = add(make.get());
        }
    }

    private RobotTestKit kit;

    private OneTurret bot(Class<?> numbers, java.util.function.Supplier<Turret> make) {
        return bot(numbers, make, new RobotTestKit(), false);
    }

    private OneTurret bot(Class<?> numbers, java.util.function.Supplier<Turret> make, RobotTestKit k, boolean autonomous) {
        kit = k;
        OneTurret r = new OneTurret(numbers, make);
        r.autonomous = autonomous;
        r.setup(kit);
        return r;
    }

    // The turret motor moves by its power (40 counts per loop at full power), then one robot loop.
    private void loops(RobotDefinition r, String motor, int n) {
        for (int i = 0; i < n; i++) {
            FakeMotor m = kit.motors.get(motor);
            if (m != null) m.position += m.power * 40;
            r.all().update();
        }
    }

    private static Turret motorTurret() {
        return Turret.motor("turret", "gobilda-5203-117");
    }

    // ----------------------------- ranges -----------------------------

    public static class Range90 { public static class Turret { public static double min = -90, max = 90, kD = 0; } }
    public static class Range180 { public static class Turret { public static double min = -180, max = 180, kD = 0; } }
    public static class Range190 { public static class Turret { public static double min = -190, max = 190, kD = 0; } }
    public static class SlipRing { public static class Turret { public static double kD = 0, startAngle = 0; } }

    @Test public void plusMinus90StopsAtItsLimit() {
        OneTurret r = bot(Range90.class, TurretOptionsTest::motorTurret);
        r.turret.aimAt(120);
        assertEquals(90, r.turret.part().getTargetAngle(), 1e-9);
        assertTrue(r.turret.isOutOfRange());
    }

    @Test public void plusMinus180GoesTheShortWayInside() {
        OneTurret r = bot(Range180.class, TurretOptionsTest::motorTurret);
        r.turret.aimAt(190);                                   // the same direction as -170
        assertEquals(-170, r.turret.part().getTargetAngle(), 1e-9);
        assertFalse(r.turret.isOutOfRange());
    }

    @Test public void plusMinus190PicksTheSafeWayRound() {
        OneTurret r = bot(Range190.class, TurretOptionsTest::motorTurret);
        kit.motors.get("turret").position = 170 * r.turret.countsPerDegree();
        loops(r, "turret", 1);
        r.turret.aimAt(-175);                                  // = 185: still inside, and no limit on the way
        assertEquals(185, r.turret.part().getTargetAngle(), 1e-6);
        kit.motors.get("turret").position = -170 * r.turret.countsPerDegree();
        loops(r, "turret", 1);
        r.turret.aimAt(175);                                   // = -185 from -170
        assertEquals(-185, r.turret.part().getTargetAngle(), 1e-6);
    }

    @Test public void aSlipRingTurnsRoundAndRoundTheShortWay() {
        OneTurret r = bot(SlipRing.class, () -> motorTurret().continuous());
        r.turret.aimAt(350);
        assertEquals(-10, r.turret.part().getTargetAngle(), 1e-9);
        assertFalse(r.turret.isOutOfRange());
        // Two and a bit turns later: the next target is still the short way, never "out of range".
        kit.motors.get("turret").position = 730 * r.turret.countsPerDegree();
        loops(r, "turret", 1);
        assertEquals(10, r.turret.angle(), 0.5);
        r.turret.aimAt(-20);
        assertEquals(700, r.turret.part().getTargetAngle(), 0.5);
        assertEquals(0, r.turret.part().degreesOutOfRange(180), 1e-9);
    }

    // ----------------------------- start angle, home switch -----------------------------

    public static class Start30 { public static class Turret { public static double min = -190, max = 190, kD = 0, startAngle = 30; } }

    @Test public void withNoSwitchTheStartAngleIsTrustedAndTheInitScreenSaysSo() {
        OneTurret r = bot(Start30.class, TurretOptionsTest::motorTurret);
        assertEquals(30, r.turret.angle(), 1e-9);
        assertTrue(r.turret.isHomed());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.initLoop(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("no angle from a program before: turn it to startAngle 30 deg by hand and INIT again"));
    }

    // A TeleOp started again (no autonomous between): it keeps the angle of the TeleOp before. A program
    // whose turret never finished homing leaves no angle behind (an older one would be stale).
    @Test public void aTeleOpRestartKeepsTheAngleAndAnUnhomedTurretLeavesNone() {
        OneTurret first = bot(Start30.class, TurretOptionsTest::motorTurret, new RobotTestKit(), false);
        first.started();
        first.turret.aimAt(-12);
        loops(first, "turret", 200);
        first.all().stop();
        OneTurret second = bot(Start30.class, TurretOptionsTest::motorTurret, new RobotTestKit(), false);
        assertEquals("kept from the TeleOp before", -12, second.turret.angle(), 1);
        // A turret with a switch that never homed: nothing is carried to the next program.
        OneTurret unhomed = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), new RobotTestKit(), true);
        unhomed.started();
        unhomed.all().stop();
        OneTurret next = bot(Start30.class, TurretOptionsTest::motorTurret, new RobotTestKit(), false);
        assertEquals("startAngle again", 30, next.turret.angle(), 1e-9);
    }

    public static class Switch45 {
        public static class Turret {
            public static double min = -190, max = 190, kD = 0, homeAngle = 45;
        }
    }

    @Test public void aTouchSwitchHomesAtStartNotInInit() {
        OneTurret r = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"));
        double cpd = r.turret.countsPerDegree();
        assertFalse("not homed until the switch is seen", r.turret.isHomed());
        r.all().update();
        assertEquals("nothing moves in INIT", 0, kit.motors.get("turret").power, 1e-9);
        r.started();
        loops(r, "turret", 1);
        assertTrue("turns slowly toward the switch (counterclockwise: from 0 to 45)", kit.motors.get("turret").power > 0);
        assertTrue(kit.motors.get("turret").power <= 0.2 + 1e-9);
        // The turret really started at 20 degrees (the robot was set down a bit off): the switch closes at
        // 25 degrees of travel, and that is 45.
        for (int i = 0; i < 100 && !r.turret.isHomed(); i++) {
            loops(r, "turret", 1);
            kit.switches.get("turret_home")[0] = kit.motors.get("turret").position >= 25 * cpd;
        }
        assertTrue(r.turret.isHomed());
        assertEquals(45, r.turret.angle(), 3);
    }

    @Test public void aMagneticSwitchWorksTooAndFixesDriftEachPass() {
        RobotTestKit k = new RobotTestKit();
        k.digitalNames.add("turret_home");                   // configured as a Digital Device
        OneTurret r = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), k, false);
        double cpd = r.turret.countsPerDegree();
        r.started();
        for (int i = 0; i < 100 && !r.turret.isHomed(); i++) {
            loops(r, "turret", 1);
            k.digital.get("turret_home")[0] = kit.motors.get("turret").position >= 45 * cpd;
        }
        assertTrue(r.turret.isHomed());
        assertEquals(45, r.turret.angle(), 3);
        // Later the encoder has drifted 5 degrees; passing the switch again sets it right.
        r.turret.aimAt(0);
        loops(r, "turret", 150);
        k.digital.get("turret_home")[0] = false;
        loops(r, "turret", 1);
        kit.motors.get("turret").position += 5 * cpd;   // slipped
        loops(r, "turret", 1);
        k.digital.get("turret_home")[0] = true;          // the switch closes: this is 45 again
        loops(r, "turret", 1);
        assertEquals(45, r.turret.angle(), 0.5);
    }

    @Test public void aMissingSwitchTrustsTheStartAngleAndSaysSo() {
        RobotTestKit k = new RobotTestKit();
        k.missing.add("turret_home");
        OneTurret r = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), k, false);
        assertTrue(r.turret.isHomed());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.initLoop(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("NO home switch"));
    }

    public static class NoHomeAngle { public static class Turret { public static double min = -190, max = 190; } }

    @Test public void aSwitchNeedsItsAngle() {
        try {
            bot(NoHomeAngle.class, () -> motorTurret().homeSwitch("turret_home"));
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("homeAngle"));
        }
    }

    // ----------------------------- from autonomous to TeleOp -----------------------------

    @Test public void teleOpStartsFromWhereAutonomousLeftTheTurret() {
        OneTurret auto = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), new RobotTestKit(), true);
        double cpd = auto.turret.countsPerDegree();
        auto.started();
        kit.switches.get("turret_home")[0] = true;          // it starts on the switch
        loops(auto, "turret", 2);
        kit.switches.get("turret_home")[0] = false;
        auto.turret.aimAt(-37);
        loops(auto, "turret", 200);
        assertEquals(-37, auto.turret.angle(), 1);
        auto.all().stop();                                    // the end of autonomous
        // TeleOp: a new INIT resets the encoder to 0, but the turret is still at -37.
        OneTurret teleOp = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), new RobotTestKit(), false);
        assertTrue("no homing again", teleOp.turret.isHomed());
        assertEquals(-37, teleOp.turret.angle(), 1);
        // A new autonomous does not use it (the robot was placed by hand).
        OneTurret again = bot(Switch45.class, () -> motorTurret().homeSwitch("turret_home"), new RobotTestKit(), true);
        assertFalse(again.turret.isHomed());
    }

    public static class NoKeep { public static class Turret { public static double min = -190, max = 190; public static boolean keepAngle = false; } }

    @Test public void keepAngleFalseStartsFromStartAngle() {
        OneTurret auto = bot(NoKeep.class, TurretOptionsTest::motorTurret, new RobotTestKit(), true);
        kit.motors.get("turret").position = 50 * auto.turret.countsPerDegree();
        loops(auto, "turret", 1);
        auto.all().stop();
        OneTurret teleOp = bot(NoKeep.class, TurretOptionsTest::motorTurret, new RobotTestKit(), false);
        assertEquals(0, teleOp.turret.angle(), 1e-9);
    }

    // ----------------------------- a servo turret -----------------------------

    public static class ServoNumbers {
        public static class Turret {
            public static double posA = 0.1, angleA = -90, posB = 0.9, angleB = 90, travelSec = 0.2;
        }
    }

    @Test public void aServoTurretGoesToItsAngleAndArrivesByTime() throws InterruptedException {
        OneTurret r = bot(ServoNumbers.class, () -> Turret.servo("turret"));
        assertTrue(r.turret.isHomed());
        r.started();
        r.turret.aimAt(45);
        r.all().update();
        assertEquals(0.1 + 0.8 * 135 / 180, kit.servos.get("turret").position, 1e-9);
        assertFalse("the first move: not there yet", r.turret.isAimed());
        Thread.sleep(300);
        r.all().update();
        assertTrue(r.turret.isAimed());
        assertEquals(45, r.turret.angle(), 1e-9);
        r.turret.aimAt(120);                                   // past its range
        assertTrue(r.turret.isOutOfRange());
        assertEquals(90, r.turret.part().getTargetAngle(), 1e-9);
    }

    // ----------------------------- CR servo turrets -----------------------------

    public static class CrNumbers { public static class Turret { public static double min = -90, max = 90, kD = 0; } }

    @Test public void aCrServoTurretReadsAThroughBoreOnAnotherMotorsPort() {
        RobotTestKit k = new RobotTestKit();
        k.get(com.qualcomm.robotcore.hardware.DcMotorEx.class, "intake");
        k.motors.get("intake").position = 5000;               // the port's count before INIT: never reset
        OneTurret r = bot(CrNumbers.class, () -> Turret.crServo("turret").encoder("intake", 8192), k, false);
        assertEquals(0, r.turret.angle(), 1e-9);
        assertEquals(5000, k.motors.get("intake").position, 1e-9);
        r.started();
        r.turret.aimAt(30);
        r.all().update();
        assertTrue("power to the CR servo", k.crServos.get("turret").power > 0);
        k.motors.get("intake").position = 5000 + 8192 * 30 / 360.0;
        r.all().update();
        assertEquals(30, r.turret.angle(), 0.1);
    }

    public static class AbsNumbers {
        public static class Turret {
            public static double min = -180, max = 180, kD = 0, encoderOffset = 90;
        }
    }

    @Test public void anAbsoluteEncoderKnowsTheAngleAtOnceAndKeepsCountingPast360() {
        RobotTestKit k = new RobotTestKit();
        k.analog.put("turret_pos", new double[] {3.3 * 120 / 360.0, 3.3});   // reads 120: the turret is at 30
        OneTurret r = bot(AbsNumbers.class, () -> Turret.crServo("turret").analogEncoder("turret_pos").continuous(), k, false);
        assertTrue(r.turret.isHomed());
        assertEquals(30, r.turret.angle(), 0.01);
        // Round past 360 on the encoder (a little each loop): 350 -> 10 is +20, not -340.
        k.analog.get("turret_pos")[0] = 3.3 * 240 / 360.0;
        r.all().update();
        k.analog.get("turret_pos")[0] = 3.3 * 350 / 360.0;
        r.all().update();
        k.analog.get("turret_pos")[0] = 3.3 * 10 / 360.0;
        r.all().update();
        assertEquals(-80, r.turret.angle(), 0.01);              // 10 - 90
        assertEquals(280, r.turret.part().getAngle(), 0.01);   // it counted on: 30 + 230 + 20
    }

    @Test public void robotJavaMistakesStopWithTheReason() {
        try {
            bot(CrNumbers.class, () -> Turret.crServo("turret"));
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs an encoder"));
        }
        try {
            Turret.servo("turret").homeSwitch("x");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no home switch"));
        }
        try {
            motorTurret().encoder("x", 8192);
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Turret.crServo"));
        }
    }

    // Review, 2026-10-30: saving the angle read the encoder first, and a failed read skipped the stop.
    @Test public void aFailedEncoderReadDoesNotKeepTheTurretFromStopping() {
        for (boolean pause : new boolean[] {false, true}) {
            OneTurret r = bot(SlipRing.class, () -> motorTurret().continuous());
            r.startForTests();
            r.turret.aimAt(90);
            loops(r, "turret", 2);
            FakeMotor m = kit.motors.get("turret");
            assertTrue("it is moving", m.power != 0);
            assertTrue("the test needs a homed turret: stop saves its angle", r.turret.part().isHomed());
            m.failRead = true;
            try {
                Thread.sleep(5);                               // its last reading is too old to use again
                r.turret.part().getAngle();
                fail("the test needs a read that fails");
            } catch (RuntimeException | InterruptedException expected) {
                // reading it now fails
            }
            if (pause) r.turret.pause();
            else r.turret.stop();
            assertEquals(pause ? "pause" : "stop", 0, m.power, 1e-9);
            m.failRead = false;
            Turret.forgetAngles();
        }
    }

    @Test public void aTurretHeldByAnActionStopsEvenIfItsEncoderFails() throws Exception {
        OneTurret r = bot(SlipRing.class, () -> motorTurret().continuous());
        r.startForTests();
        r.turret.aimAt(90);
        loops(r, "turret", 2);
        FakeMotor m = kit.motors.get("turret");
        assertTrue(m.power != 0);
        r.run(Action.named("Aim").then(() -> r.turret.aimAt(90)).waitUntil(() -> false, 5));
        r.all().update();
        r.all().update();
        m.failRead = true;
        Thread.sleep(5);
        try {
            r.turret.stop();
        } catch (RuntimeException reported) {
            assertEquals("encoder read failed", reported.getMessage());
        }
        assertEquals(0, m.power, 1e-9);
    }
}

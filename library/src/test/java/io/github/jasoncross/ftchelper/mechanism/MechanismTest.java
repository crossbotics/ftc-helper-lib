package io.github.jasoncross.ftchelper.mechanism;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.FakeMotor;
import io.github.jasoncross.ftchelper.Fakes;
import io.github.jasoncross.ftchelper.safety.JamDetector;
import org.junit.Test;

public class MechanismTest {
    private static final double E = 1e-9;

    /** A pretend lift: each update moves the encoder by power x 40 ticks. */
    private static void simulate(FakeMotor m, PositionMotor lift, int loops) {
        for (int i = 0; i < loops; i++) {
            lift.update();
            m.position += m.power * 40;
        }
    }

    private static PositionMotor lift(FakeMotor m) {
        return new PositionMotor("lift", m.motor)
                .limits(0, 2600)
                .pid(0.004, 0, 0, 0)
                .tolerance(20)
                .position("DOWN", 0).position("LOW", 800).position("HIGH", 2400);
    }

    @Test public void goesToANamedPositionAndHolds() {
        FakeMotor m = new FakeMotor();
        PositionMotor lift = lift(m);
        lift.goTo("HIGH");
        simulate(m, lift, 400);
        assertTrue(lift.isAtTarget());
        assertTrue(lift.isAt("HIGH"));
        assertEquals("HIGH", lift.getTargetName());
        assertEquals(2400, lift.getPosition(), 20);
    }

    @Test public void stickTakesOverAndHoldsWhenReleased() {
        FakeMotor m = new FakeMotor();
        PositionMotor lift = lift(m);
        lift.goTo("HIGH");
        simulate(m, lift, 5);
        lift.manual(0.5);
        simulate(m, lift, 5);
        assertEquals("hand", lift.getTargetName());
        lift.manual(0);
        lift.update();
        assertEquals("hold", lift.getTargetName());   // holds where it was let go
        double heldAt = lift.getPosition();
        simulate(m, lift, 50);
        assertEquals(heldAt, lift.getPosition(), 25);
    }

    @Test public void stepUpAndDownGoThroughTheNamesInOrder() {
        FakeMotor m = new FakeMotor();
        PositionMotor lift = lift(m);
        lift.stepUp();
        assertEquals("LOW", lift.getTargetName());
        lift.stepUp();
        assertEquals("HIGH", lift.getTargetName());
        lift.stepUp();
        assertEquals("HIGH", lift.getTargetName());   // top stays top
        lift.stepDown();
        assertEquals("LOW", lift.getTargetName());
    }

    @Test public void unitsMakeAnArmWorkInDegrees() {
        FakeMotor m = new FakeMotor();
        PositionMotor arm = new PositionMotor("arm", m.motor).units(10, "deg")   // 10 ticks per degree
                .limits(0, 180).pid(0.04, 0, 0, 0).position("UP", 90);
        arm.goTo("UP");
        simulate(m, arm, 300);
        assertEquals(90, arm.getPosition(), 2);
        assertEquals(900, m.position, 20);
    }

    @Test public void limitsStillApplyToTheStick() {
        FakeMotor m = new FakeMotor();
        PositionMotor lift = lift(m);
        lift.manual(-1);
        lift.update();
        assertEquals(0, m.power, E);                   // at 0: cannot go below
    }

    @Test public void jamDetectorNeedsTimeAndPower() {
        JamDetector jam = new JamDetector(50, 0.3);
        assertFalse(jam.update(1.0, 0, 0.0));
        assertFalse(jam.update(1.0, 0, 0.2));
        assertTrue(jam.update(1.0, 0, 0.31));
        jam.reset();
        assertFalse(jam.update(0.05, 0, 1.0));         // not really trying
        assertFalse(jam.update(1.0, 500, 2.0));        // spinning fine
        JamDetector amps = new JamDetector(0, 0.1).setMaxCurrent(5);
        assertFalse(amps.update(1.0, 800, 8, 0.0));
        assertTrue(amps.update(1.0, 800, 8, 0.2));     // too much current for long enough
    }

    @Test public void spinMotorClearsAJamThenGoesBackIn() {
        FakeMotor m = new FakeMotor();
        Fakes.Clock c = new Fakes.Clock();
        SpinMotor intake = new SpinMotor("intake", m.motor).withClock(c::get)
                .powers(1.0, -0.6).jamDetection(50, 0.3, 0.4);
        intake.in();
        m.velocity = 600;
        intake.update();
        assertEquals(1.0, m.power, E);
        m.velocity = 0;                                 // jammed
        for (int i = 0; i < 10; i++) { c.addSec(0.05); intake.update(); }
        assertEquals(SpinMotor.State.UNJAMMING, intake.getState());
        assertEquals(-0.6, m.power, E);
        assertEquals(1, intake.getJamCount());
        m.velocity = -400;
        for (int i = 0; i < 10; i++) { c.addSec(0.05); intake.update(); }
        assertEquals(SpinMotor.State.IN, intake.getState());
        assertEquals(1.0, m.power, E);
        intake.toggleIn();
        intake.update();
        assertEquals(0, m.power, E);
        intake.setPower(0.3);
        intake.update();
        assertEquals(0.3, m.power, E);
    }

    @Test public void positionServoNamesDegreesAndArrivalEstimate() {
        Fakes.FakeServo fs = new Fakes.FakeServo();
        Fakes.Clock c = new Fakes.Clock();
        PositionServo claw = new PositionServo("claw", fs.servo).withClock(c::get)
                .position("OPEN", 0.2).position("CLOSED", 0.8).travelTime(0.5).limits(0.1, 0.9);
        claw.goTo("OPEN");
        assertEquals(0.2, fs.position, E);
        c.addSec(1); claw.update();
        claw.toggle("OPEN", "CLOSED");
        assertEquals(0.8, fs.position, E);
        assertFalse(claw.isDone());                     // 0.6 of travel x 0.5 s = 0.3 s
        c.addSec(0.31);
        assertTrue(claw.isDone());
        claw.set(5);
        assertEquals(0.9, fs.position, E);              // kept inside the limits
        PositionServo hood = new PositionServo("hood", new Fakes.FakeServo().servo).degrees(0.1, 0, 0.9, 25);
        hood.setDegrees(12.5);
        assertEquals(0.5, hood.getPosition(), E);
        assertEquals(12.5, hood.getDegrees(), E);
    }

    @Test public void positionServoCanMoveSlowly() {
        Fakes.FakeServo fs = new Fakes.FakeServo();
        Fakes.Clock c = new Fakes.Clock();
        PositionServo wrist = new PositionServo("wrist", fs.servo).withClock(c::get).maxSpeed(0.5);
        wrist.set(0);
        wrist.update();
        wrist.set(1);
        c.addSec(0.5); wrist.update();
        assertEquals(0.25, fs.position, 1e-6);
        c.addSec(1.5); wrist.update();
        assertEquals(1.0, fs.position, 1e-6);
    }

    @Test public void spinServoRunsForATime() {
        Fakes.FakeCRServo fs = new Fakes.FakeCRServo();
        Fakes.Clock c = new Fakes.Clock();
        SpinServo roller = new SpinServo("roller", fs.servo).withClock(c::get).power(0.8);
        roller.forward();
        assertEquals(0.8, fs.power, E);
        roller.runFor(0.5, -1);
        assertEquals(-1, fs.power, E);
        c.addSec(0.6);
        roller.update();
        assertEquals(0, fs.power, E);
        assertFalse(roller.isRunning());
    }

    // ---------------- review regressions ----------------

    @Test public void sameServoTargetEveryLoopKeepsTheTravelTime() {
        Fakes.FakeServo fs = new Fakes.FakeServo();
        Fakes.Clock c = new Fakes.Clock();
        PositionServo s = new PositionServo("s", fs.servo).withClock(c::get).travelTime(1.0);
        s.set(0);
        c.addSec(2);
        s.set(1);                                  // a full move: about 1 s
        for (int i = 0; i < 10; i++) { c.addSec(0.05); s.set(1); }   // re-sent every loop
        assertFalse(s.isDone());                   // only 0.5 s passed
        c.addSec(0.55);
        assertTrue(s.isDone());
    }

    @Test public void interruptedServoMoveStartsFromWhereItProbablyIs() {
        Fakes.FakeServo fs = new Fakes.FakeServo();
        Fakes.Clock c = new Fakes.Clock();
        PositionServo s = new PositionServo("s", fs.servo).withClock(c::get).travelTime(1.0);
        s.set(0);
        c.addSec(2);
        s.set(1);
        c.addSec(0.5);                             // halfway (about 0.5)
        s.set(0);                                  // back: about 0.5 of travel = 0.5 s, not "instant"
        c.addSec(0.3);
        assertFalse(s.isDone());
        c.addSec(0.25);
        assertTrue(s.isDone());
    }

    @Test(expected = IllegalStateException.class)
    public void unitsAfterLimitsIsAClearError() {
        new PositionMotor("arm", new FakeMotor().motor).limits(0, 90).units(10, "deg");
    }

    @Test public void typoInAPositionNameWarnsInsteadOfCrashing() {
        FakeMotor m = new FakeMotor();
        PositionMotor lift = lift(m);
        lift.goTo("HGIH");
        assertTrue(lift.getWarning().contains("HGIH"));
        assertEquals("none", lift.getTargetName());
        PositionServo claw = new PositionServo("claw", new Fakes.FakeServo().servo).position("OPEN", 0.2);
        claw.goTo("OPNE");
        assertTrue(claw.getWarning().contains("OPNE"));
    }
}

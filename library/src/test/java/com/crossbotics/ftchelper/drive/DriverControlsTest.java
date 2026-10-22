package com.crossbotics.ftchelper.drive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.junit.Test;

public class DriverControlsTest {

    // Pretend wheels: remember the last command.
    static final class Wheels implements DriverControls.Wheels {
        final boolean strafe;
        String mode = "";
        double forward, side, turn, heading, speed;
        boolean stopped;
        Wheels(boolean strafe) { this.strafe = strafe; }
        @Override public void robotCentric(double f, double s, double t, double sp) {
            mode = "robot"; forward = f; side = s; turn = t; speed = sp;
        }
        @Override public void fieldCentric(double f, double r, double t, double h, double sp) {
            mode = "field"; forward = f; side = r; turn = t; heading = h; speed = sp;
        }
        @Override public void tank(double l, double r, double sp) {
            mode = "tank"; forward = l; side = r; speed = sp;
        }
        @Override public boolean canStrafe() { return strafe; }
        @Override public void stop() { stopped = true; }
    }

    private final double[] imu = {0};

    private DriverHeading heading(Double fromAuto) {
        return new DriverHeading(() -> imu[0], () -> imu[0] = 0, fromAuto);
    }

    @Test public void robotCentricByDefaultWithStickUpAsForward() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, heading(null));
        Gamepad g = new Gamepad();
        g.left_stick_y = -1;                         // stick pushed fully UP
        d.update(g);
        assertEquals("robot", w.mode);
        assertEquals(1, w.forward, 1e-9);
        assertEquals(1, w.speed, 1e-9);
        assertFalse(d.isFieldCentric());
    }

    @Test public void fieldCentricUsesTheDriverHeading() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, heading(90.0));   // autonomous ended turned left 90
        assertTrue(d.toggleFieldCentric());
        d.update(new Gamepad());
        assertEquals("field", w.mode);
        assertEquals(90, w.heading, 1e-9);
    }

    @Test public void slowTriggerScalesTheSpeed() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, null).slowSpeed(0.25);
        Gamepad g = new Gamepad();
        g.left_trigger = 1;
        d.update(g);
        assertEquals(0.25, w.speed, 1e-9);
        assertTrue(d.isSlow());
        assertEquals("robot  SLOW", d.status());
    }

    @Test public void noFieldCentricOnTankOrWithoutHeading() {
        DriverControls tank = new DriverControls(new Wheels(false), heading(null));
        tank.setFieldCentric(true);
        assertFalse(tank.isFieldCentric());
        assertFalse(tank.canFieldCentric());
        DriverControls noImu = new DriverControls(new Wheels(true), null);
        assertFalse(noImu.toggleFieldCentric());
    }

    @Test public void holdAndStop() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, null);
        Gamepad g = new Gamepad();
        g.left_stick_y = -1;
        d.update(g);
        d.hold();
        assertEquals(0, w.forward, 1e-9);
        d.stop();
        assertTrue(w.stopped);
    }

    @Test public void customDrivingCommandsReachTheWheels() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, null);
        d.tank(0.5, -0.5, 0.8);
        assertEquals("tank", w.mode);
        assertEquals(0.5, w.forward, 1e-9);
        assertEquals(-0.5, w.side, 1e-9);
        assertTrue(d.isSlow());
        d.drive(1, 0, 0, 1);
        assertEquals("robot", w.mode);
        assertFalse(d.isSlow());
    }

    // A turn assist (whole-robot aim) gets the driver's turn and gives the turn to use; forward and
    // sideways stay the driver's.
    @Test public void aTurnAssistReplacesOnlyTheTurn() {
        Wheels w = new Wheels(true);
        DriverControls d = new DriverControls(w, heading(null));
        d.turnAssist(turn -> turn == 0 ? 0.25 : turn);   // aims while the turn stick rests
        Gamepad g = new Gamepad();
        g.left_stick_y = -1;
        g.left_stick_x = 0.5f;
        d.update(g);
        assertEquals(0.25, w.turn, 1e-9);
        assertEquals(1, w.forward, 1e-9);
        assertTrue(w.side > 0);
        g.right_stick_x = 1;                         // the driver turns: the driver wins
        d.update(g);
        assertEquals(1, w.turn, 1e-9);
        d.turnAssist(null);
        g.right_stick_x = 0;
        d.update(g);
        assertEquals(0, w.turn, 1e-9);
    }
}

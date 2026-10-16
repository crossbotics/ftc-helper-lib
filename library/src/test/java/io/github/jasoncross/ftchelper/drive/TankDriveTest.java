package io.github.jasoncross.ftchelper.drive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.jasoncross.ftchelper.FakeMotor;
import org.junit.Test;

public class TankDriveTest {
    private static final double E = 1e-9;

    @Test public void arcadeMixing() {
        assertArrayEquals(new double[]{1, 1}, DifferentialMixer.arcade(1, 0, 1), E);
        assertArrayEquals(new double[]{0.5, -0.5}, DifferentialMixer.arcade(0, 0.5, 1), E);   // turn right
        assertArrayEquals(new double[]{1, 0}, DifferentialMixer.arcade(1, 1, 1), E);          // scaled together
        assertArrayEquals(new double[]{0.4, 0.4}, DifferentialMixer.arcade(1, 0, 0.4), E);
        assertArrayEquals(new double[2], DifferentialMixer.arcade(Double.NaN, 0, 1), E);
    }

    @Test public void tankMixing() {
        assertArrayEquals(new double[]{0.5, -1}, DifferentialMixer.tank(0.5, -3, 1), E);
        assertArrayEquals(new double[]{0.25, 0.5}, DifferentialMixer.tank(0.5, 1, 0.5), E);
    }

    @Test public void arcadeNeverExceedsLimit() {
        for (int f = -10; f <= 10; f++) for (int t = -10; t <= 10; t++) {
            for (double v : DifferentialMixer.arcade(f / 10.0, t / 10.0, 0.7)) assertTrue(Math.abs(v) <= 0.7 + 1e-12);
        }
    }

    @Test public void twoMotorDriveSetsLeftAndRight() {
        FakeMotor l = new FakeMotor(), r = new FakeMotor();
        DriveConfig cfg = DriveConfig.tankTwoMotor();
        cfg.powerLimit = 0.8;
        TankDrive drive = new TankDrive(cfg, l.motor, r.motor, null, null);
        drive.arcade(1, 0);
        assertEquals(0.8, l.power, E);
        assertEquals(0.8, r.power, E);
        drive.tank(-1, 1, 0.5);
        assertEquals(-0.4, l.power, E);
        assertEquals(0.4, r.power, E);
        assertEquals(2, drive.getMotors().length);
        drive.stop();
        assertEquals(0, l.power, E);
    }

    @Test public void fourMotorDriveSetsBothSides() {
        FakeMotor fl = new FakeMotor(), fr = new FakeMotor(), bl = new FakeMotor(), br = new FakeMotor();
        TankDrive drive = new TankDrive(DriveConfig.tank(), fl.motor, fr.motor, bl.motor, br.motor);
        drive.arcade(0, 1);           // spin right
        assertEquals(1, fl.power, E);
        assertEquals(1, bl.power, E);
        assertEquals(-1, fr.power, E);
        assertEquals(-1, br.power, E);
        assertEquals(4, drive.getMotors().length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tankDriveRefusesMecanumConfig() {
        FakeMotor m = new FakeMotor();
        new TankDrive(DriveConfig.mecanum(), m.motor, m.motor, m.motor, m.motor);
    }

    @Test public void configFactoriesAndValidation() {
        DriveConfig two = DriveConfig.tankTwoMotor();
        assertEquals(DriveConfig.DriveType.TANK, two.type);
        assertFalse(two.hasBackMotors());
        assertNull(two.backLeft);
        assertEquals("left_drive", two.frontLeft);
        two.validate();
        assertTrue(DriveConfig.mecanum().hasBackMotors());
        DriveConfig bad = DriveConfig.mecanum();
        bad.backLeft = null;
        try {
            bad.validate();
            throw new AssertionError("mecanum with 3 motors should fail");
        } catch (IllegalArgumentException expected) { }
        DriveConfig half = DriveConfig.tank();
        half.backRight = null;
        try {
            half.validate();
            throw new AssertionError("only one back motor should fail");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void oneFailingMotorDoesNotStopTheOthersFromStopping() {
        FakeMotor fl = new FakeMotor(), fr = new FakeMotor(), bl = new FakeMotor(), br = new FakeMotor();
        TankDrive drive = new TankDrive(DriveConfig.tank(), fl.motor, fr.motor, bl.motor, br.motor);
        drive.tank(0.5, 0.5);
        fl.failSetPower = true;
        try {
            drive.stop();
            org.junit.Assert.fail("the error must not be hidden");
        } catch (RuntimeException expected) { }
        assertEquals(0, fr.power, 1e-9);
        assertEquals(0, bl.power, 1e-9);
        assertEquals(0, br.power, 1e-9);
    }
}

package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.EncoderDirection;
import java.util.Properties;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.junit.Test;

/**
 * The pretend Pinpoint works out its pose from its pods with what the CODE told it (directions and
 * offsets), like a real one: a pod wired backward that the code does not reverse counts the robot's
 * forward move as backward (the Odometry Tuner's "counted BACKWARD"), and offsets the code has wrong
 * make a spin move the pose.
 */
public class SimOdometryTest {
    private static SimOdometry.Pinpoint pinpoint(String reversed, String offsets) {
        Properties p = new Properties();
        p.setProperty("odometry", "pinpoint pinpoint");
        if (reversed != null) p.setProperty("odometry.reversed", reversed);
        p.setProperty("odometry.offsetsMm", offsets);
        SimOdometry o = new SimOdometry(p);
        java.util.Map<String, com.qualcomm.robotcore.hardware.HardwareDevice> devices = new java.util.HashMap<>();
        o.addDevices(devices, new java.util.HashMap<>());
        lastOdo = o;
        return (SimOdometry.Pinpoint) devices.get("pinpoint");
    }

    private static SimOdometry lastOdo;

    // Drive forward 10 in (heading 0 faces +y), in small steps.
    private static void forward10(SimOdometry o) {
        o.step(0, 0, 0, 0.01);
        for (int i = 1; i <= 100; i++) o.step(0, i * 0.1, 0, 0.01);
    }

    @Test public void aPodTheCodeReversesLikeTheWiringCountsTheRightWay() {
        SimOdometry.Pinpoint pp = pinpoint("x", "0, 0");
        pp.setEncoderDirections(EncoderDirection.REVERSED, EncoderDirection.FORWARD);
        forward10(lastOdo);
        assertTrue("raw counts go down", pp.getEncoderX() < 0);
        assertEquals(10, pp.getPosition().getX(DistanceUnit.INCH), 0.01);
    }

    @Test public void aBackwardPodTheCodeDoesNotReverseCountsBackward() {
        SimOdometry.Pinpoint pp = pinpoint("x", "0, 0");
        pp.setEncoderDirections(EncoderDirection.FORWARD, EncoderDirection.FORWARD);   // the code forgot .reversed("x")
        forward10(lastOdo);
        assertEquals(-10, pp.getPosition().getX(DistanceUnit.INCH), 0.01);
    }

    @Test public void wrongOffsetsInTheCodeMoveThePoseInASpin() {
        SimOdometry.Pinpoint pp = pinpoint(null, "-84, -168");
        pp.setOffsets(-84, -168, DistanceUnit.MM);
        SimOdometry o = lastOdo;
        o.step(0, 0, 0, 0.01);
        for (int i = 1; i <= 360; i++) o.step(0, 0, i, 0.01);
        assertEquals("right offsets: it stays put", 0, Math.hypot(pp.getPosX(DistanceUnit.INCH), pp.getPosY(DistanceUnit.INCH)), 0.05);

        SimOdometry.Pinpoint wrong = pinpoint(null, "-84, -168");
        wrong.setOffsets(84, 168, DistanceUnit.MM);   // the sign the wrong way round
        o = lastOdo;
        o.step(0, 0, 0, 0.01);
        for (int i = 1; i <= 180; i++) o.step(0, 0, i, 0.01);
        assertTrue("wrong offsets: it seems to move", Math.hypot(wrong.getPosX(DistanceUnit.INCH), wrong.getPosY(DistanceUnit.INCH)) > 3);
    }
}

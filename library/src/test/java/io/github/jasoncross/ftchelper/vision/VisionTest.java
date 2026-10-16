package io.github.jasoncross.ftchelper.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.vision.apriltag.AprilTagPoseFtc;
import org.firstinspires.ftc.vision.apriltag.AprilTagSingleDetection;
import org.junit.Test;

public class VisionTest {
    private static final double E = 1e-9;

    @Test public void aimGivesNothingForMissingOldOrUnsolvedTargets() {
        long[] now = {1_000_000_000L};
        AimController aim = new AimController(0.02, 0, () -> now[0]).deadband(1).maxPower(0.5).maxAgeMs(150);
        assertEquals(0, aim.turnPower(null), E);
        TargetObservation old = new TargetObservation(20, "Tag 20", 10, 40, now[0] - 200_000_000L);
        assertEquals(0, aim.turnPower(old), E);
        TargetObservation noBearing = new TargetObservation(20, "Tag 20", Double.NaN, Double.NaN, now[0]);
        assertEquals(0, aim.turnPower(noBearing), E);
        TargetObservation left = new TargetObservation(20, "Tag 20", 10, 40, now[0]);
        assertEquals(0.2, aim.turnPower(left), E);           // target to the left -> positive (CCW)
        TargetObservation close = new TargetObservation(20, "Tag 20", 0.5, 40, now[0]);
        assertEquals(0, aim.turnPower(close), E);
        assertTrue(aim.isAimed());
        assertEquals(0.5, aim.turnPower(new TargetObservation(20, "x", 90, 40, now[0])), E);   // clamped
    }

    @Test public void derivativeOnlyChangesOnANewPicture() {
        long[] now = {0};
        AimController aim = new AimController(0, 1.0, () -> now[0]).maxAgeMs(1000).maxPower(100);
        TargetObservation a = new TargetObservation(1, "a", 10, 0, 0);
        aim.turnPower(a);
        now[0] = 100_000_000L;
        TargetObservation b = new TargetObservation(1, "a", 8, 0, 100_000_000L);   // -2 deg in 0.1 s
        assertEquals(-20, aim.turnPower(b), 1e-6);
        now[0] = 150_000_000L;
        assertEquals(-20, aim.turnPower(b), 1e-6);         // the same picture again: no new derivative
    }

    @Test public void freshAndAge() {
        TargetObservation t = new TargetObservation(5, "Tag 5", 1, 2, 0);
        assertEquals(50, t.ageMs(50_000_000L), E);
        assertTrue(t.isFresh(50_000_000L, 100));
        assertFalse(t.isFresh(150_000_000L, 100));
    }

    @Test public void sourceFindsTargetsByIdAndName() {
        TargetSource s = new TargetSource() {
            final List<TargetObservation> list = Arrays.asList(
                    new TargetObservation(20, "Tag 20", 1, 2, 0),
                    new TargetObservation(TargetObservation.CLUSTER, "Blue Hive", 3, 4, 0));
            public void start() { }
            public List<TargetObservation> targets() { return list; }
            public boolean isWorking() { return true; }
            public String status() { return "ok"; }
            public void close() { }
            public void update() { }
        };
        assertEquals("Tag 20", s.target(20).name);
        assertEquals(3, s.target("Blue Hive").bearingDeg, E);
        assertNull(s.target(99));
    }

    @Test public void webcamDetectionKeepsTheFtcBearingSign() {
        AprilTagPoseFtc pose = new AprilTagPoseFtc(0, 0, 0, 0, 0, 0, 30, 12.5, 0);   // range 30, bearing 12.5
        AprilTagSingleDetection d = new AprilTagSingleDetection(7, 0, 50f, null, null, null, pose, null, null, 123L, DistanceUnit.INCH);
        TargetObservation o = WebcamTags.convert(d);
        assertEquals(7, o.id);
        assertEquals("Tag 7", o.name);
        assertEquals(12.5, o.bearingDeg, E);
        assertEquals(30, o.distanceIn, E);
        assertEquals(123L, o.timeNanos);
    }

    @Test public void limelightDistanceFromPoseInInches() {
        Pose3D pose = new Pose3D(new Position(DistanceUnit.METER, 0.3, 0.4, 0, 0),
                new YawPitchRollAngles(AngleUnit.DEGREES, 0, 0, 0, 0));
        assertEquals(0.5 / 0.0254, LimelightTags.distanceInches(pose), 1e-6);
        assertTrue(Double.isNaN(LimelightTags.distanceInches(null)));
    }
}

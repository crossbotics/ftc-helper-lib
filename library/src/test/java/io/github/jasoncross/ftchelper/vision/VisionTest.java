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
        assertEquals(-0.2, aim.turnPower(left), E);          // target to the left -> turn LEFT = negative (drive: clockwise +)
        assertEquals(0.2, aim.ccwPower(left), E);            // the same, counterclockwise +
        TargetObservation close = new TargetObservation(20, "Tag 20", 0.5, 40, now[0]);
        assertEquals(0, aim.turnPower(close), E);
        assertTrue(aim.isAimed());
        assertEquals(-0.5, aim.turnPower(new TargetObservation(20, "x", 90, 40, now[0])), E);  // clamped
    }

    @Test public void derivativeOnlyChangesOnANewPicture() {
        long[] now = {0};
        AimController aim = new AimController(0, 1.0, () -> now[0]).maxAgeMs(1000).maxPower(100);
        TargetObservation a = new TargetObservation(1, "a", 10, 0, 0);
        aim.turnPower(a);
        now[0] = 100_000_000L;
        TargetObservation b = new TargetObservation(1, "a", 8, 0, 100_000_000L);   // -2 deg in 0.1 s
        assertEquals(-20, aim.ccwPower(b), 1e-6);
        now[0] = 150_000_000L;
        assertEquals(-20, aim.ccwPower(b), 1e-6);          // the same picture again: no new derivative
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
        // Camera space: x right, y down, z forward. 0.3 m right, 0.8 m down (ignored), 0.4 m forward.
        Pose3D pose = new Pose3D(new Position(DistanceUnit.METER, 0.3, 0.8, 0.4, 0),
                new YawPitchRollAngles(AngleUnit.DEGREES, 0, 0, 0, 0));
        assertEquals(0.5 / 0.0254, LimelightTags.distanceInches(pose), 1e-6);   // along the floor
        assertTrue(Double.isNaN(LimelightTags.distanceInches(null)));
    }

    @Test public void limelightPictureTimeIncludesItsOwnLatency() {
        long now = 10_000_000_000L;
        // 20 ms since the hub got it + 25 ms capture + 15 ms processing + 1 ms parse = 61 ms old.
        assertEquals(now - 61_000_000L, LimelightTags.pictureTimeNanos(now, 20, 25, 15, 1));
    }

    @Test public void oldTargetsAreDropped() {
        List<TargetObservation> list = Arrays.asList(
                new TargetObservation(1, "a", 0, 0, 1_000_000_000L),
                new TargetObservation(2, "b", 0, 0, 1_400_000_000L));
        List<TargetObservation> kept = WebcamTags.dropOld(list, 1_600_000_000L, 500);
        assertEquals(1, kept.size());
        assertEquals(2, kept.get(0).id);
    }

    @Test public void aimOffsetIsWhereTheTargetSitsWhenAimed() {
        long[] now = {1_000_000_000L};
        AimController aim = new AimController(0.02, 0, () -> now[0]).deadband(0.5).offset(5);
        // The target 5 deg left of the camera's center = aimed (the launcher points at it).
        assertEquals(0, aim.turnPower(new TargetObservation(20, "t", 5, 40, now[0])), E);
        assertTrue(aim.isAimed());
        // Centered in the camera = the launcher points left of it: turn RIGHT (clockwise = positive).
        assertTrue(aim.turnPower(new TargetObservation(20, "t", 0, 40, now[0] + 1)) > 0);
    }

    // ---- TagGroup: a Limelight's single tags reported as one group target ----

    private static java.util.Map<Integer, double[]> row(double offsetX, double z, boolean upsideDown, int... ids) {
        java.util.Map<Integer, double[]> m = new java.util.HashMap<>();
        TagGroup g = TagGroup.biobuzz().get(0);
        for (int id : ids) {
            double along = g.alongRowIn(id) * (upsideDown ? -1 : 1);   // upside down: printed right = camera left
            m.put(id, new double[] {offsetX + along, -10, z});
        }
        return m;
    }

    @Test public void groupTargetIsTheMiddleOfTheRowFromAnyTwoTags() {
        TagGroup red = TagGroup.biobuzz().get(0);
        assertEquals("RED SCORING", red.name);
        double expectBearing = -Math.toDegrees(Math.atan2(5, 40));      // the middle is 5 in RIGHT: negative
        for (boolean upsideDown : new boolean[] {false, true}) {
            TargetObservation all = LimelightTags.groupTarget(red, row(5, 40, upsideDown, 30, 31, 32, 33), 7);
            TargetObservation two = LimelightTags.groupTarget(red, row(5, 40, upsideDown, 31, 33), 7);
            for (TargetObservation o : new TargetObservation[] {all, two}) {
                assertEquals(TargetObservation.CLUSTER, o.id);
                assertEquals("RED SCORING", o.name);
                assertEquals(expectBearing, o.bearingDeg, 1e-9);
                assertEquals(Math.hypot(5, 40), o.distanceIn, 1e-9);
                assertEquals(7, o.timeNanos);
            }
        }
        assertNull("one tag is not enough", LimelightTags.groupTarget(red, row(5, 40, false, 32), 7));
        assertNull("other tags do not count", LimelightTags.groupTarget(red, row(5, 40, false, 34, 35), 7));
    }

    @Test public void tagGroupsAreChecked() {
        assertEquals(4, TagGroup.biobuzz().size());
        assertTrue(TagGroup.currentSeason().get(3).contains(45));
        assertTrue(Double.isNaN(TagGroup.biobuzz().get(0).alongRowIn(45)));
        String[][] bad = {{"at least 2"}, {"same place"}, {"twice"}};
        int[][] ids = {{1}, {1, 2}, {1, 1}};
        double[][] along = {{0}, {0, 0}, {-1, 1}};
        for (int i = 0; i < bad.length; i++) {
            try {
                new TagGroup("bad", ids[i], along[i]);
                org.junit.Assert.fail("refused: " + bad[i][0]);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(bad[i][0]));
            }
        }
    }
}

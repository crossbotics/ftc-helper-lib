package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import java.io.File;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Shooting on the move in five files: Tunables.Aim.shootOnMove, flightSec, the camera picture's age. */
public class MovingShotAimTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private LongSupplier aimClockBefore;

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        aimClockBefore = Aim.clock;
        clock.nanos = 10_000_000_000L;
        Aim.clock = clock::get;
    }

    @After public void back() {
        Aim.clock = aimClockBefore;
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    /** Odometry like a Pinpoint: pose and FIELD speed, in the device's own frame (X away, Y left). */
    static final class MovingTracker extends PoseTracker {
        double[] device = {0, 0, 0, 0, 0, 0};
        MovingTracker() { super("odo"); }
        @Override public boolean isReady() { return true; }
        @Override public String status() { return "ready"; }
        @Override public String kind() { return "test"; }
        @Override protected double[] read() { return device.clone(); }
        @Override protected void write(double x, double y, double h) { device = new double[]{x, y, h, device[3], device[4], device[5]}; }

        // Speed in the library's frame (x right, y away) and the turn (deg/s, CCW +).
        void moving(double vx, double vy, double turnDegPerSec) {
            device[3] = vy;           // device X = our y
            device[4] = -vx;          // device Y = -our x
            device[5] = turnDegPerSec;
            update();
        }
    }

    // The goal 24 in left of the centre line and 48 in toward the far wall; shooting on the move ON.
    public static class Moving {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static boolean shootOnMove = true;
            public static double flightSec = 0.5;
            public static double maxLeadIn = 30;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    // The same with shooting on the move left out (the default: off).
    public static class Still {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static double flightSec = 0.5;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    /** Mecanum, odometry, a camera on the body and a launcher (whole-robot aim: no turret). */
    static class Bot extends RobotDefinition {
        final MovingTracker tracker = new MovingTracker();
        final Class<?> numbers;
        Camera camera;
        Launcher launcher;

        Bot(Class<?> numbers) {
            this.numbers = numbers;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            odometry(Odometry.custom(d -> tracker));
            camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromDistance(camera));
        }
    }

    // A turret 6 in behind the centre, a camera on the body 8 in in front of it; shooting on the move on.
    public static class Offsets {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static boolean shootOnMove = true;
            public static double flightSec = 0.5;
            public static double shooterForwardIn = -6, shooterLeftIn = 0;
            public static double cameraForwardIn = 8, cameraLeftIn = 0;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    // A fixed launcher 4 in LEFT of the centre (no turret, whole-robot aim), odometry only.
    public static class SideLauncher {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static boolean shootOnMove = true;
            public static double flightSec = 0.5;
            public static double shooterForwardIn = 0, shooterLeftIn = 4;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    /** A turret that follows a camera on the body, or the odometry alone (camera = false). */
    static class TurretBot extends RobotDefinition {
        final MovingTracker tracker = new MovingTracker();
        final Class<?> numbers;
        final boolean withCamera;
        Camera camera;
        Turret turret;
        Launcher launcher;

        TurretBot(Class<?> numbers, boolean withCamera) {
            this.numbers = numbers;
            this.withCamera = withCamera;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            odometry(Odometry.custom(d -> tracker));
            if (withCamera) {
                camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
                turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera).cameraOnBody());
                launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromDistance(camera));
            } else {
                turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).followsOdometry());
                launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromOdometry());
            }
        }
    }

    /** Whole-robot aim with no camera: a fixed launcher, odometry only. */
    static class FixedBot extends RobotDefinition {
        final MovingTracker tracker = new MovingTracker();
        Launcher launcher;

        @Override protected void define() {
            tunables(SideLauncher.class);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            odometry(Odometry.custom(d -> tracker));
            launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromOdometry());
        }
    }

    private static Devices devices() {
        final RobotTestKit kit = new RobotTestKit();
        return new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                return kit.get(type, name);
            }
            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                if (type == Limelight3A.class || type == WebcamName.class) return null;
                return kit.tryGet(type, name);
            }
        };
    }

    private AimTest.FakeCamera cam;

    private Bot bot(Class<?> numbers) {
        Bot r = new Bot(numbers);
        r.setup(devices());
        cam = new AimTest.FakeCamera();
        r.camera.source = cam;
        return r;
    }

    private static void place(Bot r, double x, double y, double heading) {
        r.tracker.setPose(new FieldPose(x, y, heading));
        r.tracker.update();
    }

    private TargetObservation seen(double bearing, double distance) {
        return new TargetObservation(TargetObservation.CLUSTER, "BLUE SCORING", bearing, distance, clock.get());
    }

    @Test public void offByDefaultNothingChanges() {
        Bot r = bot(Still.class);
        place(r, -24, 0, 0);                                  // straight below the goal, facing it
        r.tracker.moving(30, 0, 0);                           // strafing right at 30 in/s
        assertFalse(r.aim().movingShotMode().isOn());
        assertEquals(0, r.aim().fromOdometry().bearingDeg, 1e-9);
        assertEquals(48, r.aim().fromOdometry().distanceIn, 1e-9);
        assertNull(r.aim().lead());
    }

    @Test public void stillRobotAimsAsBefore() {
        Bot r = bot(Moving.class);
        place(r, -24, 0, 0);
        assertTrue(r.aim().movingShotMode().isOn());
        assertEquals(0, r.aim().fromOdometry().bearingDeg, 1e-9);
        assertEquals(48, r.aim().fromOdometry().distanceIn, 1e-9);
        assertFalse(r.aim().isMovingShot());
    }

    @Test public void strafingAcrossTheGoalLeadsTheRightWayAndIsMirroredForRed() {
        Bot r = bot(Moving.class);
        place(r, -24, 0, 0);
        r.tracker.moving(30, 0, 0);                           // BLUE: strafing RIGHT, 0.5 s flight: 15 in
        TargetObservation g = r.aim().fromOdometry();
        double lead = Math.toDegrees(Math.atan2(15, 48));
        assertEquals("aim LEFT of the goal (+)", lead, g.bearingDeg, 1e-6);
        assertEquals(Math.hypot(48, 15), g.distanceIn, 1e-6);
        assertTrue(r.aim().isMovingShot());
        assertEquals(15, r.aim().lead().leadIn, 1e-6);
        // RED, mirrored field: the goal at x +24; the same move mirrored (strafing LEFT) leads to the right.
        Bot red = bot(Moving.class);
        red.setAlliance(Alliance.RED);
        place(red, 24, 0, 0);
        red.tracker.moving(-30, 0, 0);
        assertEquals(-lead, red.aim().fromOdometry().bearingDeg, 1e-6);
        // A button can switch it off: the plain goal again.
        r.aim().movingShotMode().off();
        assertEquals(0, r.aim().fromOdometry().bearingDeg, 1e-9);
    }

    @Test public void wholeRobotAimIsAimedOnlyAtTheLedGoal() {
        Bot r = bot(Moving.class);
        r.aimMode().on();
        place(r, -24, 0, 0);
        r.tracker.moving(30, 0, 0);
        cam.seen = seen(0, 48);                               // the camera: the goal straight ahead
        assertEquals("the lead is ~17 deg: not aimed", Math.toDegrees(Math.atan2(15, 48)), r.aim().errorDeg(), 1e-6);
        assertFalse(r.aim().isAimed());
        cam.seen = seen(-Math.toDegrees(Math.atan2(15, 48)), Math.hypot(48, 15));   // pointing at the virtual goal
        assertTrue("aimed at the led goal", Math.abs(r.aim().errorDeg()) < 2);
        assertTrue(r.aim().isAimed());
    }

    @Test public void anOldPictureIsBroughtForwardByTheTurn() {
        Bot r = bot(Moving.class);
        place(r, -24, 0, 0);
        r.tracker.moving(0, 0, 100);                          // turning LEFT at 100 deg/s, not moving
        cam.seen = seen(10, 48);
        clock.addSec(0.05);                                   // the picture is 50 ms old now
        TargetObservation g = r.aim().goal();
        assertEquals(Aim.Source.CAMERA, Aim.sourceOf(g));
        assertEquals("turned 5 deg left since: the goal is 5 deg less left", 5, g.bearingDeg, 1e-6);
        assertEquals(48, g.distanceIn, 1e-6);
        assertEquals("the picture time stays (a turret uses each picture once)", cam.seen.timeNanos, g.timeNanos);
    }

    @Test public void theLauncherUsesTheLedDistance() {
        Bot r = bot(Moving.class);
        r.started();
        place(r, -24, 0, 0);
        r.tracker.moving(0, 40, 0);                           // driving toward the goal at 40 in/s
        r.launcher.spinUp();
        cam.seen = seen(0, 48);
        r.all().update();
        assertEquals("48 - 40 x 0.5", 28, r.launcher.distanceIn(), 1e-6);
    }

    @Test public void theDriverStationSaysMovingShotAndTheLead() {
        Bot r = bot(Moving.class);
        place(r, -24, 0, 0);
        r.tracker.moving(30, 0, 0);
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.aim().addTelemetry(t);
        t.update();
        String screen = ft.lastScreen.toString();
        assertTrue(screen, screen.contains("moving shot: lead 17.4 deg, 15 in"));
        assertNotNull(r.aim().lead());
    }

    @Test public void aTurretBehindTheCentreWithACameraOnTheBodyAimsFromItsOwnPlace() {
        TurretBot r = new TurretBot(Offsets.class, true);
        r.setup(devices());
        cam = new AimTest.FakeCamera();
        r.camera.source = cam;
        place(r.tracker, -24, 0, 0);
        // The body camera (8 in in front) sees the goal 4 in left, 52 in ahead: (60, 4) from the centre,
        // (66, 4) from the turret 6 in behind it.
        cam.seen = seen(Math.toDegrees(Math.atan2(4, 52)), Math.hypot(52, 4));
        TargetObservation g = r.aim().goal();
        assertEquals("the turret angle, from the turret", Math.toDegrees(Math.atan2(4, 66)), g.bearingDeg, 1e-6);
        assertEquals("the distance from the turret", Math.hypot(66, 4), g.distanceIn, 1e-6);
        // Driving toward the goal at 20 in/s: 10 in shorter (0.5 s flight); still straight on.
        r.tracker.moving(0, 20, 0);
        assertEquals(Math.hypot(56, 4), r.aim().goal().distanceIn, 1e-6);
    }

    @Test public void anOdometryTurretOffCentreAimsFromItsPivot() {
        TurretBot r = new TurretBot(Offsets.class, false);
        r.setup(devices());
        place(r.tracker, -24, 0, 0);                          // the goal 48 in straight ahead of the centre
        TargetObservation g = r.aim().fromOdometry();
        assertEquals(0, g.bearingDeg, 1e-6);
        assertEquals("54 in from the turret 6 in behind the centre", 54, g.distanceIn, 1e-6);
        r.tracker.moving(30, 0, 0);                           // strafing right: lead left, from the turret
        assertEquals(Math.toDegrees(Math.atan2(15, 54)), r.aim().fromOdometry().bearingDeg, 1e-6);
    }

    @Test public void aFixedLauncherToOneSideTurnsTheWholeRobotALittleMore() {
        FixedBot r = new FixedBot();
        r.setup(devices());
        place(r.tracker, -24, 0, 0);                          // goal 48 in straight ahead of the centre
        r.aimMode().on();
        // The launcher is 4 in LEFT: turn RIGHT by asin(4/48) so its line meets the goal.
        assertEquals(-Math.toDegrees(Math.asin(4.0 / 48)), r.aim().errorDeg(), 1e-6);
        assertEquals(Math.sqrt(48 * 48 - 16), r.aim().fromOdometry().distanceIn, 1e-6);
    }

    private static void place(MovingTracker t, double x, double y, double heading) {
        t.setPose(new FieldPose(x, y, heading));
        t.update();
    }

    @Test public void deadWheelsWorkTheSpeedOutFromThePose() {
        // A tracker with no speed of its own (3 numbers, like dead wheels).
        AimTest.PlacedTracker dw = new AimTest.PlacedTracker();
        Fakes.Clock c = new Fakes.Clock();
        c.nanos = 1_000_000_000L;
        dw.useClock(c::get);
        dw.setPose(new FieldPose(0, 0, 0));
        dw.update();
        for (int i = 0; i < 50; i++) {                        // 1 s at 50 loops/s, 0.6 in to the right per loop
            c.addSec(0.02);
            dw.device = new double[]{0, dw.device[1] - 0.6, 0};   // device Y = -our x
            dw.update();
        }
        assertEquals(30, dw.velocityX(), 0.5);
        assertEquals(0, dw.velocityY(), 1e-6);
        assertEquals(30, dw.speedInPerSec(), 0.5);
    }
}

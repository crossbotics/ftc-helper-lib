package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.odometry.PoseTracker;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.TargetObservation;
import com.crossbotics.ftchelper.vision.TargetSource;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Aiming in five files: the camera first and the odometry as a backup (turret, launcher distance,
 * whole-robot aim), several shot tables, and the aim buzz.
 */
public class AimTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final Fakes.Clock clock = new Fakes.Clock();
    private LongSupplier aimClockBefore, padClockBefore;

    @Before public void setUp() {
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        aimClockBefore = Aim.clock;
        padClockBefore = Pad.clock;
        clock.nanos = 10_000_000_000L;
        Aim.clock = clock::get;
        Pad.clock = clock::get;
    }

    @After public void back() {
        Aim.clock = aimClockBefore;
        Pad.clock = padClockBefore;
        ButtonPlan.aimBuzzed = null;
        SettingsFile.useForTests(null);
        TuningReset.clear();
    }

    // ----------------------------- pretend parts -----------------------------

    /** Odometry that stays where it is put (setPose), in the device's own frame like a Pinpoint. */
    static final class PlacedTracker extends PoseTracker {
        double[] device = {0, 0, 0};
        PlacedTracker() { super("odo"); }
        @Override public boolean isReady() { return true; }
        @Override public String status() { return "ready"; }
        @Override public String kind() { return "test"; }
        @Override protected double[] read() { return device.clone(); }
        @Override protected void write(double x, double y, double h) { device = new double[]{x, y, h}; }
    }

    /** A camera that sees what the test says. */
    static final class FakeCamera implements TargetSource {
        TargetObservation seen = null;
        @Override public void start() { }
        @Override public void update() { }
        @Override public List<TargetObservation> targets() {
            return seen == null ? Collections.<TargetObservation>emptyList() : Collections.singletonList(seen);
        }
        @Override public boolean isWorking() { return true; }
        @Override public String status() { return "ok"; }
        @Override public void close() { }
        @Override public String name() { return "fake"; }
        @Override public void addTelemetry(RobotTelemetry t) { }
    }

    // The test kit's motors and servos; no cameras (the test puts a FakeCamera in).
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

    // The goal 24 in left of the centre line and 48 in toward the far wall (written for BLUE).
    public static class GoalTunables {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static double rumbleDeg = 4, toleranceDeg = 2;
        }
        public static class Launcher {
            public static double speedRpm = 2600;
            public static double[][] speedByDistancePollen = {{24, 2000}, {72, 3000}};
            public static double[][] speedByDistanceNectar = {{24, 2500}, {72, 3500}};
        }
    }

    // A field turned half a turn (BIOBUZZ): the same numbers from the red wall.
    public static class TurnedField {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
            public static boolean mirrorForRed = false;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    public static class NoGoalPoint {
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    /** Mecanum, odometry, a camera on the turret, a turret that follows it, a launcher with two tables. */
    static class AimBot extends RobotDefinition {
        final PlacedTracker tracker = new PlacedTracker();
        final Class<?> numbers;
        final boolean withCamera;
        boolean withTurret = true;
        Camera camera;
        Turret turret;
        Launcher launcher;

        AimBot(Class<?> numbers, boolean withCamera) {
            this.numbers = numbers;
            this.withCamera = withCamera;
        }

        @Override protected void define() {
            tunables(numbers);
            drive(Drive.mecanum("fl", "fr", "bl", "br"));
            odometry(Odometry.custom(d -> tracker));
            if (withCamera && !withTurret) {
                // Whole-robot aim: a camera and a launcher, no turret.
                camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
                launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromDistance(camera));
            } else if (withCamera) {
                camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
                turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
                launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromDistance(camera));
            } else {
                turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).followsOdometry());
                launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").speedFromOdometry());
            }
        }
    }

    private FakeCamera cam;

    private AimBot bot(Class<?> numbers, boolean withCamera) {
        return bot(numbers, withCamera, true);
    }

    private AimBot bot(Class<?> numbers, boolean withCamera, boolean withTurret) {
        AimBot r = new AimBot(numbers, withCamera);
        r.withTurret = withTurret;
        r.setup(devices());
        if (withCamera) {
            cam = new FakeCamera();
            r.camera.source = cam;
        }
        return r;
    }

    // Puts the robot at (x, y) facing `heading` (the library's frame: x right, y away, CCW +).
    private static void place(AimBot r, double x, double y, double heading) {
        r.tracker.setPose(new FieldPose(x, y, heading));
        r.tracker.update();
    }

    private TargetObservation seen(double bearing, double distance) {
        return new TargetObservation(TargetObservation.CLUSTER, "BLUE SCORING", bearing, distance, clock.get());
    }

    // ----------------------------- the goal from the odometry -----------------------------

    @Test public void theOdometryWorksOutTheGoalsDirectionAndDistance() {
        AimBot r = bot(GoalTunables.class, false);
        place(r, -24, 0, 0);                                  // straight below the goal, facing away
        TargetObservation g = r.aim().fromOdometry();
        assertTrue(g.fromOdometry());
        assertEquals(0, g.bearingDeg, 1e-9);
        assertEquals(48, g.distanceIn, 1e-9);
        place(r, 24, 48, 0);                                  // level with it, 48 in to its right
        assertEquals("to the left = positive", 90, r.aim().fromOdometry().bearingDeg, 1e-9);
        place(r, 24, 48, 90);                                 // the same, facing left: straight ahead
        assertEquals(0, r.aim().fromOdometry().bearingDeg, 1e-9);
        assertEquals(48, r.aim().fromOdometry().distanceIn, 1e-9);
        assertEquals(Aim.Source.ODOMETRY, r.aim().source());
    }

    @Test public void redMirrorsTheGoalUnlessTheFieldIsTurned() {
        AimBot r = bot(GoalTunables.class, false);
        r.setAlliance(Alliance.RED);
        place(r, 24, 0, 0);                                   // below the mirrored goal (+24, 48)
        assertEquals(0, r.aim().fromOdometry().bearingDeg, 1e-9);
        AimBot turned = bot(TurnedField.class, false);
        turned.setAlliance(Alliance.RED);
        place(turned, -24, 0, 0);                             // the same numbers from the red wall
        assertEquals(0, turned.aim().fromOdometry().bearingDeg, 1e-9);
    }

    @Test public void theCameraComesFirstAndTheOdometryIsTheBackup() {
        AimBot r = bot(GoalTunables.class, true);
        place(r, -24, 0, 0);
        cam.seen = seen(5, 50);
        assertEquals(Aim.Source.CAMERA, r.aim().source());
        assertEquals(5, r.aim().goal().bearingDeg, 1e-9);
        clock.addSec(0.5);                                    // the picture is old now
        assertEquals(Aim.Source.ODOMETRY, r.aim().source());
        assertEquals(0, r.aim().goal().bearingDeg, 1e-9);
    }

    @Test public void noOdometryBackupWithoutAGoalPoint() {
        AimBot r = bot(NoGoalPoint.class, true);
        assertFalse(r.aim().hasGoalPoint());
        assertNull(r.aim().fromOdometry());
        assertEquals(Aim.Source.NONE, r.aim().source());
    }

    // ----------------------------- the turret -----------------------------

    @Test public void theTurretFollowsTheOdometryWhenTheCameraLosesTheGoal() {
        AimBot r = bot(GoalTunables.class, true);
        r.started();
        place(r, 24, 48, 0);                                  // the goal is 90 degrees left
        cam.seen = null;
        r.all().update();
        assertEquals(Aim.Source.ODOMETRY, r.turret.aimSource());
        assertEquals(90, r.turret.part().getTargetAngle(), 1e-9);
        assertEquals("the turret still points forward", 90, r.turret.aimErrorDeg(), 1e-6);
        cam.seen = seen(-10, 48);                             // the camera on the turret sees it: turn BY -10
        r.all().update();
        assertEquals(Aim.Source.CAMERA, r.turret.aimSource());
        assertEquals(-10, r.turret.aimErrorDeg(), 1e-9);
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.turret.addTelemetry(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("following: camera"));
    }

    @Test public void followsOdometryNeedsOdometryAndAGoalPoint() {
        try {
            new RobotDefinition() {
                @Override protected void define() {
                    tunables(GoalTunables.class);
                    drive(Drive.mecanum("fl", "fr", "bl", "br"));
                    add(Turret.motor("turret", "gobilda-5203-117").followsOdometry());
                }
            }.setup(devices());
            fail("expected a stop at INIT");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs an odometry(...) line"));
        }
        try {
            new AimBot(NoGoalPoint.class, false).setup(devices());
            fail("expected a stop at INIT");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Tunables.Aim"));
        }
    }

    // ----------------------------- the launcher -----------------------------

    @Test public void theLauncherTakesItsDistanceFromTheOdometryWhenTheCameraDoesNotSeeTheGoal() {
        AimBot r = bot(GoalTunables.class, true);
        r.started();
        assertEquals("nectar comes first by name", "nectar", r.launcher.tableName());
        r.launcher.spinUp();
        cam.seen = seen(0, 24);
        r.all().update();
        assertEquals(2500, r.launcher.targetRpm(), 1e-9);
        assertEquals(Aim.Source.CAMERA, r.launcher.distanceSource());
        clock.addSec(1);                                      // camera lost: the odometry says 72 in
        place(r, -24, -24, 0);
        r.all().update();
        assertEquals(Aim.Source.ODOMETRY, r.launcher.distanceSource());
        assertEquals(72, r.launcher.distanceIn(), 1e-9);
        assertEquals(3500, r.launcher.targetRpm(), 1e-9);
    }

    @Test public void shotTablesSwitchByNameByButtonAndFromASensor() {
        AimBot r = bot(GoalTunables.class, false);
        r.started();
        r.launcher.spinUp();
        place(r, -24, 0, 0);                                  // 48 in
        r.all().update();
        assertEquals(3000, r.launcher.targetRpm(), 1e-9);     // nectar: halfway 2500..3500
        r.launcher.useTable("POLLEN");                        // case does not matter
        r.all().update();
        assertEquals("pollen", r.launcher.tableName());
        assertEquals(2500, r.launcher.targetRpm(), 1e-9);
        r.launcher.useTable("honey");                         // no such table: kept, and a warning
        assertEquals("pollen", r.launcher.tableName());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.launcher.addTelemetry(t);
        t.update();
        String screen = ft.lastScreen.toString();
        assertTrue(screen, screen.contains("no shot table \"honey\" (Tunables.Launcher has: nectar, pollen)"));
        assertTrue(screen, screen.contains("POLLEN, 48 in (odometry)"));
        r.launcher.nextTable();
        assertEquals("nectar", r.launcher.tableName());
        assertEquals("speedByDistanceNectar", r.launcher.tableField("nectar"));
        // A sensor picks the table: only a NEW answer counts, so a button can still change it.
        final String[] piece = {null};
        r.launcher.tableFrom(() -> piece[0]);
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        piece[0] = "pollen";
        r.all().update();
        assertEquals("pollen", r.launcher.tableName());
        r.launcher.useTable("nectar");                        // the driver overrides
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        piece[0] = null;                                      // nothing loaded
        r.all().update();
        piece[0] = "pollen";                                  // the next piece is pollen again
        r.all().update();
        assertEquals("pollen", r.launcher.tableName());
    }

    // Game pieces pick the table: both NECTAR colours use "nectar"; NONE and UNKNOWN keep the table.
    @Test public void pieceKindsPickTheirShotTable() {
        AimBot r = bot(GoalTunables.class, false);
        r.started();
        final String[] next = {"POLLEN"};
        r.launcher.tableFrom(() -> next[0]).tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR");
        r.all().update();
        assertEquals("pollen", r.launcher.tableName());
        next[0] = "RED_NECTAR";
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        next[0] = Pieces.UNKNOWN;
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        next[0] = Pieces.NONE;
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        next[0] = "POLLEN";
        r.all().update();
        assertEquals("pollen", r.launcher.tableName());
        next[0] = "BLUE_NECTAR";
        r.all().update();
        assertEquals("nectar", r.launcher.tableName());
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.launcher.addTelemetry(t);
        t.update();
        assertFalse(ft.lastScreen.toString(), ft.lastScreen.toString().contains("no shot table"));
    }

    @Test public void startTableAndTheButtonForTheNextTable() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(GoalTunables.class);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000").startTable("Pollen"));
            }
        };
        r.setup(devices());
        Launcher l = (Launcher) r.modules().get(0);
        assertEquals("pollen", l.tableName());
        ButtonPlan plan = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        String help = plan.help().toString();
        assertTrue(help, help.contains("G2 D-pad right: next shot table (nectar, pollen)"));
        try {
            new RobotDefinition() {
                @Override protected void define() {
                    tunables(GoalTunables.class);
                    drive(Drive.tank("l", "r"));
                    add(Launcher.flywheel("launcher", "gobilda-5203-6000").startTable("honey"));
                }
            }.setup(devices());
            fail("expected a stop at INIT");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has no such table (it has: nectar, pollen)"));
        }
    }

    @Test public void aSingleTableIsMainAndStillWorks() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(NoGoalPoint.class);
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        };
        r.setup(devices());
        Launcher l = (Launcher) r.modules().get(0);
        assertEquals("main", l.tableName());
        assertEquals("speedByDistance", l.tableField("main"));
    }

    // ----------------------------- whole-robot aim -----------------------------

    @Test public void wholeRobotAimTurnsTowardTheGoalAndTheDriverWins() {
        AimBot r = bot(GoalTunables.class, true, false);
        Aim aim = r.aim();
        assertEquals("off: the driver's turn as it is", 0.3, aim.assistTurn(0.3), 1e-9);
        r.aimMode().on();
        cam.seen = seen(10, 48);                              // the goal 10 degrees LEFT
        double turn = aim.assistTurn(0);
        assertTrue("turns left = counterclockwise = negative: " + turn, turn < 0);
        assertEquals("the stick wins", 0.5, aim.assistTurn(0.5), 1e-9);
        cam.seen = seen(1, 48);                               // within toleranceDeg (2)
        assertEquals(0, aim.assistTurn(0), 1e-9);
        assertTrue(aim.isAimed());
        cam.seen = null;                                      // camera lost: the odometry
        place(r, 24, 48, 90);                                 // facing the goal
        assertEquals(0, aim.assistTurn(0), 1e-9);
        assertEquals(0, aim.errorDeg(), 1e-9);
        place(r, 24, 48, 80);                                 // 10 degrees right of it: turn left
        assertTrue(aim.assistTurn(0) < 0);
        assertEquals(10, aim.errorDeg(), 1e-6);
    }

    @Test public void wholeRobotAimWithNoGoalDoesNotTurn() {
        AimBot r = bot(NoGoalPoint.class, true, false);
        r.aimMode().on();
        assertEquals(0, r.aim().assistTurn(0), 1e-9);
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        r.aim().addTelemetry(t);
        t.update();
        assertTrue(ft.lastScreen.toString(), ft.lastScreen.toString().contains("Aim: no goal"));
    }

    // ----------------------------- the aim buzz -----------------------------

    @Test public void rumbleWhenAimedBuzzesOnlyCloseAndNotWhilePaused() {
        AimBot r = bot(GoalTunables.class, true, false);
        final int[] buzzes = {0};
        ButtonPlan.aimBuzzed = () -> buzzes[0]++;
        ButtonPlan plan = ControlMap.makePlan(r, false, false, (g1, g2) -> g1.rumbleWhenAimed());
        final Gamepad g1 = new Gamepad(), g2 = new Gamepad();
        plan.compile(r, new ControllerBindings(), new ControllerBindings(), new ArrayList<>(), () -> g1, () -> g2);
        plan.loop(false, false);
        assertEquals("nothing is aiming: no buzz", 0, buzzes[0]);
        r.aimMode().on();
        cam.seen = seen(20, 48);                              // far off: no buzz
        plan.loop(false, false);
        assertEquals(0, buzzes[0]);
        cam.seen = seen(1, 48);                               // close: a buzz
        plan.loop(false, false);
        assertEquals(1, buzzes[0]);
        clock.addSec(0.01);
        cam.seen = seen(1, 48);
        plan.loop(false, false);
        assertEquals("not again before its cooldown", 1, buzzes[0]);
        clock.addSec(1);
        cam.seen = seen(0.5, 48);
        plan.loop(true, false);
        assertEquals("paused: no buzz", 1, buzzes[0]);
        plan.loop(false, false);
        assertEquals(2, buzzes[0]);
    }

    @Test public void rumbleWhenAimedTwiceOnOneGamepadStops() {
        AimBot r = bot(GoalTunables.class, false);
        try {
            ControlMap.makePlan(r, false, false, (g1, g2) -> {
                g1.rumbleWhenAimed();
                g1.rumbleWhenAimed();
            });
            fail("expected a stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rumbleWhenAimed() is there twice"));
        }
    }

    // ----------------------------- review fixes: aiming safety -----------------------------

    private static String screen(java.util.function.Consumer<RobotTelemetry> show) {
        Fakes.FakeTelemetry ft = new Fakes.FakeTelemetry();
        RobotTelemetry t = new RobotTelemetry(ft.telemetry, () -> 0L);
        show.accept(t);
        t.update();
        return ft.lastScreen.toString();
    }

    // P1: a turret that reached its last target is NOT aimed once the goal is lost (no odometry backup),
    // so a launcher gated on aim().isAimed() cannot fire while the chassis turns away.
    @Test public void aFollowingTurretIsNotAimedOnceTheGoalIsLost() {
        AimBot r = bot(NoGoalPoint.class, true);
        r.started();
        clock.nanos = System.nanoTime();                      // the turret part checks pictures on the real clock
        cam.seen = seen(0, 48);                               // straight ahead of the turret's camera
        r.all().update();
        assertTrue("on the goal", r.turret.isAimed());
        assertTrue(r.aim().isAimed());
        clock.addSec(0.5);                                    // the picture is old: the goal is lost
        r.all().update();
        assertTrue("the motor still sits on its last target", r.turret.part().isAimed());
        assertFalse("but that is not aimed at the goal", r.turret.isAimed());
        assertFalse(r.aim().isAimed());
        cam.seen = seen(8, 48);                               // seen again, 8 degrees off: not aimed yet
        assertFalse(r.turret.isAimed());
        r.turret.aimAt(0);                                    // by hand: aimed = where it was told
        assertTrue(r.turret.isAimed());
    }

    // Fable #3: whole-robot aim with a following turret: both would chase the same error. Stop at INIT.
    @Test public void wholeRobotAimWithAFollowingTurretStopsWithAMessage() {
        AimBot r = bot(GoalTunables.class, true);
        try {
            r.aimMode();
            fail("expected a stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("separate modes"));
        }
        AimBot odo = bot(GoalTunables.class, false);
        try {
            ControlMap.makePlan(odo, false, true, (g1, g2) -> g1.rb.toggle(odo.aimMode()));
            fail("expected a stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("separate modes"));
        }
    }

    // Fable #4: the odometry's goal behind the robot: hold, do not swing limit to limit; hysteresis.
    @Test public void aGoalBehindTheRobotHoldsTheTurretInsteadOfSwingingLimitToLimit() {
        AimBot r = bot(GoalTunables.class, false);            // follows the odometry, limits -90..90
        r.started();
        place(r, -24, 96, 0);                                 // the goal straight behind (bearing 180)
        r.all().update();
        assertEquals("holds where it points", 0, r.turret.part().getTargetAngle(), 1e-9);
        assertTrue(r.turret.isOutOfRange());
        assertFalse(r.turret.isAimed());
        assertTrue(screen(r.turret::addTelemetry), screen(r.turret::addTelemetry).contains("goal out of turret range (behind the robot: holding)"));
        for (double h : new double[] {3, -3, 2, -2}) {        // the heading wobbles around "behind"
            place(r, -24, 96, h);
            r.all().update();
            assertEquals("still holding at heading " + h, 0, r.turret.part().getTargetAngle(), 1e-9);
        }
        place(r, -24, 96, 82);                                // bearing 98: 8 past the limit, still holding
        r.all().update();
        assertEquals(0, r.turret.part().getTargetAngle(), 1e-9);
        place(r, -24, 96, 85);                                // bearing 95: back within 5: to the limit
        r.all().update();
        assertEquals(90, r.turret.part().getTargetAngle(), 1e-9);
        place(r, -24, 96, 82);                                // 8 past again: below 10, it stays at the limit
        r.all().update();
        assertEquals(90, r.turret.part().getTargetAngle(), 1e-9);
        place(r, -24, 96, 75);                                // 15 past: holds again
        r.all().update();
        assertTrue(r.turret.isOutOfRange());
        place(r, -24, 96, 150);                               // bearing 30: in range, it follows
        r.all().update();
        assertEquals(30, r.turret.part().getTargetAngle(), 1e-6);
        assertFalse(r.turret.isOutOfRange());
    }

    // Fable #5: TeleOp without autonomous starts at (0, 0): the odometry backup is off until the pose is set.
    @Test public void theOdometryBackupWaitsUntilThePoseWasSet() {
        AimBot r = bot(GoalTunables.class, false);
        r.tracker.update();
        assertFalse(r.tracker.isPoseSet());
        assertNull(r.aim().fromOdometry());
        assertTrue(r.aim().odometryProblem(), r.aim().odometryProblem().contains("never set"));
        r.tracker.setHeading(0);                              // "forward = away from you": not a pose
        assertNull(r.aim().fromOdometry());
        String screen = screen(r.aim()::addTelemetry);
        assertTrue(screen, screen.contains("Aim odometry: the pose was never set"));
        place(r, -24, 0, 0);
        assertTrue(r.tracker.isPoseSet());
        assertEquals(48, r.aim().fromOdometry().distanceIn, 1e-9);
        assertEquals("", r.aim().odometryProblem());
    }

    @Test public void aGoalFartherThanTheFieldAllowsMeansAWrongPose() {
        AimBot r = bot(GoalTunables.class, false);
        place(r, 200, 0, 0);                                  // 229 in from the goal: not on a field
        assertNull(r.aim().fromOdometry());
        assertTrue(r.aim().odometryProblem(), r.aim().odometryProblem().contains("more than the field allows (170)"));
        r.started();
        r.all().update();
        assertEquals("the turret does not aim at a wrong pose", Aim.Source.NONE, r.turret.aimSource());
    }

    @Test public void cameraAndOdometryDisagreeingIsWarned() {
        AimBot r = bot(GoalTunables.class, true);
        place(r, -24, 0, 0);                                  // the odometry: 48 in
        cam.seen = seen(0, 52);                               // 4 in apart: fine
        assertEquals("", r.aim().disagreement());
        cam.seen = seen(0, 60);                               // 12 in apart: warn
        assertTrue(r.aim().disagreement(), r.aim().disagreement().contains("camera says 60 in, odometry 48 in"));
        assertTrue(screen(r.aim()::addTelemetry).contains("Aim: camera says 60 in"));
    }

    @Test public void initShowsTheAllianceAndTheMirrorChoice() {
        AimBot r = bot(GoalTunables.class, false);
        r.setAlliance(Alliance.RED);
        String s = screen(r.aim()::addInitTelemetry);
        assertTrue(s, s.contains("RED, goal x 24, y 48 in (mirrorForRed = true: RED mirrors x)"));
        assertTrue(s, s.contains("the pose was never set"));
        AimBot turned = bot(TurnedField.class, false);
        turned.setAlliance(Alliance.RED);
        place(turned, 0, 0, 0);
        s = screen(turned.aim()::addInitTelemetry);
        assertTrue(s, s.contains("RED, goal x -24, y 48 in (mirrorForRed = false: the same numbers from each wall)"));
        assertFalse(s, s.contains("never set"));
    }
}

package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.crossbotics.ftchelper.core.SettingsFile;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchMemory;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.crossbotics.ftchelper.tuning.TuningReset;
import com.crossbotics.ftchelper.util.FieldPose;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Controls.java and the library's own buttons: moving and freeing them (the button map and its
 * checks), "set pose here" mirrored per alliance, and switching the alliance during TeleOp.
 */
public class LibraryControlsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private RobotModulesTest.LiftBot robot;

    @Before public void setUp() {
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(true);
        SettingsFile.useForTests(new SettingsFile(new File(tmp.getRoot(), "settings.properties")));
        TuningReset.clear();
        MatchMemory.clear();
        robot = new RobotModulesTest.LiftBot();
        robot.setup(new RobotTestKit());
    }

    @After public void back() {
        com.crossbotics.ftchelper.safety.SafeMotor.freshReadingsForTests(false);
        SettingsFile.useForTests(null);
        TuningReset.clear();
        MatchMemory.clear();
    }

    private static String line(List<String> help, String start) {
        for (String h : help) if (h.startsWith(start)) return h;
        return "";
    }

    private ButtonPlan plan(boolean oneDriver, LibraryButtons lib, ControlMap.TeamButtons team) {
        return ControlMap.makePlan(robot, oneDriver, true, lib, ControlMap.defaultJobs(robot), team);
    }

    // ----------------------------- the button map -----------------------------

    @Test public void theDefaultMapShowsEveryLibraryButton() {
        List<String> help = ControlMap.makePlan(robot, false, true, (g1, g2) -> { }).help();
        assertEquals("G1 BACK: pause / go on; hold 1 s: forward = away from you now; after START: debug lines [library]",
                line(help, "G1 BACK"));
        assertEquals("G1 GUIDE: button map; hold 1 s: tuning menu [library]", line(help, "G1 GUIDE"));
        assertEquals("no odometry: START has no \"set pose\"",
                "G1 START: then BACK: debug lines [library]", line(help, "G1 START"));
        assertTrue("gamepad 2's GUIDE is free", line(help, "G2 free").contains("GUIDE"));
    }

    @Test public void aMovedLibraryButtonFreesItsOldOne() {
        ButtonPlan p = plan(false, LibraryButtons.twoDrivers().fieldCentric(null), (g1, g2) -> { });
        assertEquals("", line(p.help(), "G1 Y:"));
        // Field-centric on X: Y is the team's now, and X is the library's.
        ButtonPlan q = plan(false, LibraryButtons.twoDrivers().fieldCentric(new ButtonPlan(false).g1.x),
                (g1, g2) -> g1.y.press(robot.claw::open).says("open"));
        assertEquals("G1 X: field-centric on/off [library]", line(q.help(), "G1 X"));
        assertEquals("G1 Y: open", line(q.help(), "G1 Y"));
        try {
            plan(false, LibraryButtons.twoDrivers().fieldCentric(new ButtonPlan(false).g1.x), (g1, g2) -> g1.x.press(robot.claw::open));
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G1 X is the library's \"field-centric on/off\" button"));
            assertTrue(e.getMessage(), e.getMessage().contains("libraryButtons"));
        }
    }

    @Test public void theReservedButtonsCanAllBeFreed() {
        LibraryButtons lib = LibraryButtons.twoDrivers().pause(null).resetForward(null).debug(null, null)
                .switchAlliance(null, null).setPose(null).slow(null).fieldCentric(null).buttonMap(null).tuningMenu(null);
        ButtonPlan p = plan(false, lib, (g1, g2) -> {
            g1.back.press(robot.claw::open);
            g1.start.press(robot.claw::close);
            g1.lt.press(robot.claw::open);
            g1.y.press(robot.claw::open);
            g1.guide.press(robot.claw::open);
            g1.rt.slow();            // with the library's slow freed, slow() works with two drivers
        });
        assertEquals("G1 BACK: press", line(p.help(), "G1 BACK"));
        assertEquals("G1 GUIDE: press", line(p.help(), "G1 GUIDE"));
        assertEquals("G1 RT: slow (squeeze: the further, the slower)", line(p.help(), "G1 RT"));
        assertEquals(com.crossbotics.ftchelper.gamepad.ControllerBindings.Trigger.RT, p.slowTrigger.trigger());
    }

    @Test public void swappedSticksDriveWithTheRightStick() {
        ButtonPlan p = plan(false, LibraryButtons.twoDrivers().swapSticks(true), (g1, g2) -> g1.leftStickY.use(v -> { }).says("mine"));
        assertEquals("G1 right stick Y: drive [library]", line(p.help(), "G1 right stick Y"));
        assertEquals("G1 left stick X: turn [library]", line(p.help(), "G1 left stick X"));
        assertEquals("G1 left stick Y: mine", line(p.help(), "G1 left stick Y"));
        try {
            plan(false, LibraryButtons.twoDrivers().swapSticks(true), (g1, g2) -> g1.rightStickY.use(v -> { }));
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("G1 right stick Y is the library's \"drive\""));
        }
    }

    @Test public void oneDriverStillWorksWithMovedButtons() {
        // One driver: a library job written for gamepad 2 lands on gamepad 1.
        LibraryButtons lib = LibraryButtons.oneDriver().fieldCentric(new ButtonPlan(false).g2.dpadLeft).moveAllToGamepad1();
        ButtonPlan p = plan(true, lib, (g1, g2) -> { });
        assertEquals("G1 left stick click: slow on/off [library]", line(p.help(), "G1 left stick click"));
        assertTrue(line(p.help(), "G1 D-pad left"), line(p.help(), "G1 D-pad left").startsWith("G1 D-pad left: field-centric on/off [library]"));
        assertEquals("the right stick click is free for the parts", "", line(p.help(), "G1 right stick click: field"));
        assertEquals("G1 RT: in (hold) [Intake]", line(p.help(), "G1 RT"));
    }

    @Test public void twoLibraryJobsOnOneButtonStopAtInit() {
        try {
            plan(false, LibraryButtons.twoDrivers().fieldCentric(new ButtonPlan(false).g1.back), (g1, g2) -> { });
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Two library features are on G1 BACK"));
        }
    }

    // ----------------------------- set pose here -----------------------------

    // Our start tile against the wall, facing the field (written for BLUE).
    public static class ResetSpot {
        public static class Odometry {
            public static double resetX = -36, resetY = -63, resetHeading = 30;
        }
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    // A field turned half a turn: the same numbers from the red wall.
    public static class ResetSpotTurned {
        public static class Odometry {
            public static double resetX = -36, resetY = -63, resetHeading = 30;
            public static boolean mirrorForRed = false;
        }
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    public static class NoResetSpot {
        public static class Odometry {
            public static boolean checked = false;
        }
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    private static AimTest.AimBot odoBot(Class<?> numbers) {
        AimTest.AimBot r = new AimTest.AimBot(numbers, false);
        r.setup(devices());
        return r;
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

    private static void assertPose(String what, double x, double y, double h, FieldPose p) {
        assertEquals(what + " x", x, p.x, 1e-6);
        assertEquals(what + " y", y, p.y, 1e-6);
        assertEquals(what + " heading", h, p.headingDeg, 1e-6);
    }

    @Test public void setPoseHereUsesTheResetSpotMirroredForRed() {
        AimTest.AimBot r = odoBot(ResetSpot.class);
        assertFalse(r.odometry().isPoseSet());
        assertTrue(r.resetPose());
        r.odometry().update();
        assertTrue(r.odometry().isPoseSet());
        assertPose("BLUE", -36, -63, 30, r.pose());
        assertEquals("x -36, y -63 in, heading 30 (BLUE)", r.resetSpotWords());
        r.setAlliance(Alliance.RED);
        assertTrue(r.resetPose());
        r.odometry().update();
        assertPose("RED: mirrored left-right", 36, -63, -30, r.pose());
        assertEquals("x 36, y -63 in, heading -30 (RED, mirrored)", r.resetSpotWords());
        // From code: any spot, mirrored the same way.
        r.resetPose(10, 20, 90);
        r.odometry().update();
        assertPose("RED, from code", -10, 20, -90, r.pose());
    }

    @Test public void aTurnedFieldUsesTheSameNumbersFromTheRedWall() {
        AimTest.AimBot r = odoBot(ResetSpotTurned.class);
        r.setAlliance(Alliance.RED);
        assertTrue(r.resetPose());
        r.odometry().update();
        assertPose("RED, turned field", -36, -63, 30, r.pose());
        assertEquals("x -36, y -63 in, heading 30 (RED, same numbers)", r.resetSpotWords());
    }

    @Test public void withoutAResetSpotNothingChanges() {
        AimTest.AimBot r = odoBot(NoResetSpot.class);
        assertFalse(r.resetPose());
        assertFalse(r.odometry().isPoseSet());
        assertNull(r.resetSpotWords());
        try {
            robot.resetPose(0, 0, 0);   // the lift robot has no odometry
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no odometry"));
        }
        assertFalse(robot.resetPose());
    }

    @Test public void setPoseIsOnTheMapOnlyWithOdometry() {
        AimTest.AimBot r = odoBot(ResetSpot.class);
        Set<LibraryButtons.Job> jobs = ControlMap.defaultJobs(r);
        assertTrue(jobs.contains(LibraryButtons.Job.SET_POSE));
        ButtonPlan p = ControlMap.makePlan(r, false, true, (g1, g2) -> { });
        assertTrue(line(p.help(), "G1 START"), line(p.help(), "G1 START").startsWith("G1 START: hold 1 s: set pose here; then BACK"));
        assertFalse(ControlMap.defaultJobs(robot).contains(LibraryButtons.Job.SET_POSE));
    }

    @Test public void theNoPoseWarningSaysHowToSetIt() {
        AimTest.AimBot r = odoBot(ResetSpot.class);
        assertTrue(r.aim().odometryProblem(), r.aim().odometryProblem().contains("robot.resetPose(...)"));
        r.resetPoseHint = "park on the reset spot and hold G1 START 1 s";
        assertTrue(r.aim().odometryProblem(), r.aim().odometryProblem().contains("hold G1 START 1 s"));
        r.resetPose();
        r.odometry().update();
        assertEquals("", r.aim().odometryProblem());
    }

    @Test public void theInitLineSaysHowTheResetSpotIsUsedForRed() {
        assertEquals("mirrored, x and heading flip (mirrorForRed not written: default true)", odoBot(ResetSpot.class).resetMirrorWords());
        assertEquals("the same numbers from the red wall (Tunables.Odometry.mirrorForRed = false)",
                odoBot(ResetSpotTurned.class).resetMirrorWords());
    }

    // ----------------------------- slow -----------------------------

    @Test public void aTeamSlowTriggerOnGamepadTwoReadsGamepadTwo() {
        ButtonPlan p = plan(false, LibraryButtons.twoDrivers().slow(null), (g1, g2) -> g2.rt.slow());
        com.qualcomm.robotcore.hardware.Gamepad pad1 = new com.qualcomm.robotcore.hardware.Gamepad();
        com.qualcomm.robotcore.hardware.Gamepad pad2 = new com.qualcomm.robotcore.hardware.Gamepad();
        pad1.right_trigger = 1;
        assertEquals("gamepad 1's RT is not the slow trigger", 1, ControlMap.teamSpeed(p, 0.3, pad1, pad2), 1e-9);
        pad1.right_trigger = 0;
        pad2.right_trigger = 1;
        assertEquals("gamepad 2's RT squeezed: slow", 0.3, ControlMap.teamSpeed(p, 0.3, pad1, pad2), 1e-9);
        assertNull(ControlMap.noSlowWarning(LibraryButtons.twoDrivers().slow(null), p));
    }

    @Test public void noSlowAtAllIsAnInitWarning() {
        LibraryButtons lib = LibraryButtons.twoDrivers().slow(null);
        ButtonPlan p = plan(false, lib, (g1, g2) -> { });
        String w = ControlMap.noSlowWarning(lib, p);
        assertTrue(w, w != null && w.startsWith("No slow button"));
        assertNull(ControlMap.noSlowWarning(LibraryButtons.twoDrivers(), plan(false, LibraryButtons.twoDrivers(), (g1, g2) -> { })));
        LibraryButtons one = LibraryButtons.oneDriver();
        assertNull("one driver: slow on/off", ControlMap.noSlowWarning(one, plan(true, one, (g1, g2) -> { })));
    }

    // ----------------------------- switching the alliance -----------------------------

    @Test public void aSwitchMirrorsThePoseOnAMirroredField() {
        AimTest.AimBot r = odoBot(ResetSpot.class);
        r.odometry().setPose(new FieldPose(-30, -50, 20));   // where autonomous (run as BLUE) left it
        r.setAlliance(Alliance.RED);
        String note = ControlMap.poseAfterSwitch(r, Alliance.RED, com.crossbotics.ftchelper.teleop.Key.g1(
                com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn.START));
        assertTrue(note, note.contains("pose mirrored to RED's side"));
        r.odometry().update();
        assertPose("mirrored in place", 30, -50, -20, r.pose());
        assertTrue("still set: the odometry aim keeps working", r.odometry().isPoseSet());
    }

    @Test public void aSwitchOnATurnedFieldTurnsTheOdometryAimOffUntilThePoseIsSet() {
        AimTest.AimBot r = odoBot(ResetSpotTurned.class);
        r.odometry().setPose(new FieldPose(-30, -50, 20));
        r.odometry().update();
        assertNotNull(r.aim().fromOdometry());
        r.setAlliance(Alliance.RED);
        String note = ControlMap.poseAfterSwitch(r, Alliance.RED, com.crossbotics.ftchelper.teleop.Key.g1(
                com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn.START));
        assertEquals("Alliance now RED: odometry aim OFF until the pose is set (park on the reset spot, hold G1 START 1 s)", note);
        assertFalse(r.odometry().isPoseSet());
        assertNull("no odometry backup until the pose is set", r.aim().fromOdometry());
        assertTrue(r.resetPose());
        r.odometry().update();
        assertNotNull("set pose here turns it back on", r.aim().fromOdometry());
        // No pose to fix: nothing to say.
        assertNull(ControlMap.poseAfterSwitch(odoBot(ResetSpot.class), Alliance.RED, null));
    }

    /** The AimBot with whole-robot aim (a camera, no turret). */
    public static class PoseBot extends AimTest.AimBot {
        public PoseBot() {
            super(SwitchTunables.class, true);
            withTurret = false;
        }
    }

    /** A TeleOp on the odometry robot, to call "set pose here" the way its button does. */
    public static class PoseControls extends ControlMap<PoseBot> {
        @Override protected void buttons(PoseBot robot, Pad g1, Pad g2) { }

        PoseBot bot() {
            return robot();
        }

        String setPose() {
            return setPoseJob().get();
        }
    }

    @Test public void setPoseHereWaitsUntilTheRobotStops() {
        PoseControls c = new PoseControls();
        c.bot().setup(devices());
        c.gamepad1 = new com.qualcomm.robotcore.hardware.Gamepad();
        c.gamepad1.left_stick_y = -0.6f;   // driving
        assertTrue(c.setPose(), c.setPose().startsWith("Pose NOT set: stop first"));
        c.gamepad1.left_stick_y = 0;
        c.gamepad1.right_stick_x = 0.4f;   // turning
        assertTrue(c.setPose(), c.setPose().startsWith("Pose NOT set"));
        c.gamepad1.right_stick_x = 0.05f;  // a stick at rest is never quite 0
        assertTrue("still: it goes on (this robot has no reset spot)", c.setPose().startsWith("No reset spot"));
        c.bot().aimMode().on();           // whole-robot aim turns the robot by itself
        assertTrue(c.setPose(), c.setPose().startsWith("Pose NOT set"));
    }

    // The goal tags for each alliance and a goal point (mirrored for RED).
    public static class SwitchTunables {
        public static class Aim {
            public static double goalX = -24, goalY = 48;
        }
        public static class Launcher {
            public static double[][] speedByDistance = {{24, 2000}, {72, 3000}};
        }
    }

    /** The AimBot with a camera, made by ControlMap from its type (a public constructor with no inputs). */
    public static class SwitchBot extends AimTest.AimBot {
        public SwitchBot() {
            super(SwitchTunables.class, true);
        }
    }

    /** A TeleOp for it; the test calls the switch the buttons would call. */
    public static class SwitchControls extends ControlMap<SwitchBot> {
        @Override protected void buttons(SwitchBot robot, Pad g1, Pad g2) { }

        SwitchBot bot() {
            return robot();
        }

        void flip() {
            switchAlliance();
        }

        Alliance now() {
            return alliance();
        }
    }

    @Test public void switchingTheAllianceInTeleOpUpdatesTheCameraTheAimAndMatchMemory() {
        MatchMemory.remember(Alliance.BLUE, null);   // autonomous said BLUE (the wrong one)
        SwitchControls c = new SwitchControls();
        SwitchBot r = c.bot();
        r.setup(devices());
        AimTest.FakeCamera cam = new AimTest.FakeCamera();
        r.camera.source = cam;
        cam.seen = new TargetObservation(TargetObservation.CLUSTER, "RED SCORING", 3, 40, 0, 0);   // right side up: the up CELL
        assertEquals(Alliance.BLUE, r.alliance());
        assertNull("BLUE looks for the blue goal", r.camera.goal());
        r.tracker.setPose(new FieldPose(24, 0, 0));
        r.tracker.update();
        assertEquals("BLUE: the goal is 48 in to the left-front", 45, r.aim().fromOdometry().bearingDeg, 1e-6);

        c.flip();
        assertEquals(Alliance.RED, c.now());
        assertEquals(Alliance.RED, r.alliance());
        TargetObservation g = r.camera.goal();
        assertNotNull("RED looks for the red goal", g);
        assertEquals("RED SCORING", g.name);
        // The pose from autonomous (run as BLUE) is mirrored too, so the aim stays right: the mirror
        // image of BLUE's 45 degrees left.
        assertEquals("the pose is mirrored to RED's side", -24, r.pose().x, 1e-6);
        assertEquals("RED: the goal point and the pose are mirrored", -45, r.aim().fromOdometry().bearingDeg, 1e-6);
        assertEquals(Alliance.RED, MatchMemory.chosen());
        assertEquals("a TeleOp started again gets the corrected one", Alliance.RED, MatchMemory.allianceOr(Alliance.BLUE));

        c.flip();
        assertEquals(Alliance.BLUE, r.alliance());
        assertNull(r.camera.goal());
    }

    // Review, 2026-10-30: BACK wrote 0 into the pose's heading, so aiming at a field point turned wrongly.
    @Test public void forwardForTheDriverLeavesThePoseAndTheAimAlone() {
        PoseControls c = new PoseControls();
        c.bot().setup(devices());
        c.bot().resetPose(-24, 0, 90);
        c.bot().odometry().update();
        com.crossbotics.ftchelper.drive.DriverHeading h = c.createHeading(null);
        assertEquals(90, h.degrees(), 1e-6);
        double bearing = c.bot().aim().fromOdometry().bearingDeg;
        h.reset();
        c.bot().odometry().update();
        assertEquals("forward for the driver is here now", 0, h.degrees(), 1e-6);
        assertEquals("the pose did not change", 90, c.bot().pose().headingDeg, 1e-6);
        assertEquals("the aim did not change", bearing, c.bot().aim().fromOdometry().bearingDeg, 1e-6);
        // A pose set again is true: forward is the field's again.
        c.bot().resetPose(-24, 0, 45);
        c.bot().odometry().update();
        assertEquals(45, h.degrees(), 1e-6);
    }

    @Test public void switchingAllianceKeepsForwardForTheDriver() {
        PoseControls c = new PoseControls();
        c.bot().setup(devices());
        c.bot().resetPose(-24, 0, 60);
        c.bot().odometry().update();
        com.crossbotics.ftchelper.drive.DriverHeading h = c.createHeading(null);
        h.reset();
        assertEquals(0, h.degrees(), 1e-6);
        assertTrue(c.bot().resetMirrorsForRed());
        ControlMap.poseAfterSwitch(c.bot(), Alliance.RED, null);
        c.bot().odometry().update();
        assertEquals("the robot did not turn", 0, h.degrees(), 1e-6);
    }
}

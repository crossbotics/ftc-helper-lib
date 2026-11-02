package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.drive.FieldMap;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchMemory;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Steps;
import com.crossbotics.ftchelper.util.FieldPose;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Routes on the simulator's field, whose HIVE frames are solid: a robot that hit one would be stopped
 * by it and the route would say STALLED. Whole OpModes, in real time.
 */
public class SimRouteTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    public static class Tun {
        public static class Odometry {
            public static double resetX = 0, resetY = -61, resetHeading = 0;
            public static boolean mirrorForRed = false;
        }
    }

    public static class MirrorTun {
        public static class Odometry {
            public static double resetX = -25, resetY = -55, resetHeading = 0;
            public static boolean mirrorForRed = true;
        }
    }

    /** The two HIVE frames, as a team would write them. */
    public static class HiveBot extends RobotDefinition {
        static volatile HiveBot last;

        @Override protected void define() {
            tunables(Tun.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            odometry(com.crossbotics.ftchelper.robot.Odometry.pinpoint("pinpoint").offsetsMm(0, 0));
            fieldMap(FieldMap.margin(13)
                    .keepOut("HIVE frame, near", -19.5, -24.8, 19.5, -15.4)
                    .keepOut("HIVE frame, far", -19.5, 15.4, 19.5, 24.8));
            last = this;
        }
    }

    /** A box on one side only, so RED's mirror image is a different place. */
    public static class MirrorBot extends RobotDefinition {
        static volatile MirrorBot last;

        @Override protected void define() {
            tunables(MirrorTun.class);
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
            odometry(com.crossbotics.ftchelper.robot.Odometry.pinpoint("pinpoint").offsetsMm(0, 0));
            fieldMap(FieldMap.margin(5).keepOut("Pretend box", -44, -48, -10, -42));
            last = this;
        }
    }

    /** The park time cuts the first step short at the near wall; the route must go past both frames. */
    @Autonomous(name = "Route Auto", group = "Test")
    @Disabled
    public static class RouteAuto extends Auto<HiveBot> {
        @Override protected boolean mirrorForRed() { return false; }

        @Override protected void steps(Steps s, HiveBot robot) {
            s.start("Near wall, middle", 0, -61, 0)
             .waitSeconds("Busy", 30)
             .parkWithSeconds(27)
             .routeTo("Home", 0, 40, 0, 0.7);
        }
    }

    @TeleOp(name = "Route TeleOp", group = "Test")
    @Disabled
    public static class RouteTeleOp extends ControlMap<HiveBot> {
        public static boolean oneDriver = false;

        @Override protected void buttons(HiveBot robot, Pad g1, Pad g2) {
            g1.dpadLeft.tap(route("Middle", 0, 0, 0, 0.7)).says("route to the middle");
            g1.dpadRight.tap(route("Far", 0, 40, 0, 0.5)).says("route to the far side");
        }
    }

    @TeleOp(name = "Route Timed", group = "Test")
    @Disabled
    public static class RouteTimed extends ControlMap<HiveBot> {
        public static boolean oneDriver = false;

        @Override protected void buttons(HiveBot robot, Pad g1, Pad g2) {
            atSecondsLeft(118, route("Timed", -56, -36, 0, 0.7));
        }
    }

    @TeleOp(name = "Route Mirror", group = "Test")
    @Disabled
    public static class RouteMirror extends ControlMap<MirrorBot> {
        public static boolean oneDriver = false;

        @Override protected void buttons(MirrorBot robot, Pad g1, Pad g2) {
            g1.dpadLeft.tap(route("Up", -25, -35, 0, 0.6)).says("route up");
        }
    }

    public static class NoMap extends RobotDefinition {
        @Override protected void define() {
            drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right"));
            odometry(com.crossbotics.ftchelper.robot.Odometry.pinpoint("pinpoint").offsetsMm(0, 0));
        }
    }

    @TeleOp(name = "Route No Map", group = "Test")
    @Disabled
    public static class RouteNoMap extends ControlMap<NoMap> {
        public static boolean oneDriver = false;

        @Override protected void buttons(NoMap robot, Pad g1, Pad g2) {
            g1.dpadLeft.tap(route("Up", 0, 0, 0, 0.6));
        }
    }

    private Path robotFile(Class<? extends RobotDefinition> c) throws Exception {
        RobotDefinition r = c.getDeclaredConstructor().newInstance();
        SimFromRobot.Result res = SimFromRobot.merge(null, r.simLines(), c.getName(), "biobuzz-2026");
        assertTrue(res.problems.toString(), res.problems.isEmpty());
        Path file = tmp.newFile().toPath();
        Files.write(file, res.text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String shown(SimRun s) {
        return String.join("\n", s.screen());
    }

    private static boolean waitForText(SimRun s, String text, double sec) throws InterruptedException {
        return SimRun.waitFor(() -> s.screenHas(text), sec);
    }

    // The robot stands on the alliance's reset spot, and the driver holds START 1 s: the pose is set.
    private static void standOnResetSpot(SimRun s, Alliance a, double x, double y) throws InterruptedException {
        FieldPose p = MatchMemory.fieldPose(a, x, y, 0);
        s.robot().setPose(p.x, p.y, p.headingDeg);
        s.g1().start = true;
        assertTrue(shown(s), waitForText(s, "Pose set", 5));
        s.g1().start = false;
        s.waitForLoops(3);
    }

    // Waits, and fails if the robot ever bumps into a HIVE frame on the way.
    private static boolean waitNoHiveBump(SimRun s, BooleanSupplier done, double sec) throws InterruptedException {
        long end = System.nanoTime() + (long) (sec * 1e9);
        while (System.nanoTime() < end) {
            assertFalse("hit: " + s.robot().bumpedInto, s.robot().bumpedInto.contains("HIVE"));
            if (done.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return done.getAsBoolean();
    }

    @Test public void afterTheParkTimeTheAutonomousGoesAroundBothFrames() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route Auto", "Autonomous", RouteAuto.class);
            s.init("Route Auto");
            assertEquals("", s.error());
            s.start();
            assertTrue("reached the far side\n" + shown(s),
                    waitNoHiveBump(s, () -> HiveBot.last != null && HiveBot.last.pose() != null
                            && Math.hypot(HiveBot.last.pose().x, HiveBot.last.pose().y - 40) < 1.5, 25));
            assertEquals("", s.error());
        }
    }

    @Test public void aButtonDrivesAroundTheFrameAndTheSticksWorkAfter() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route TeleOp", "TeleOp", RouteTeleOp.class);
            s.init("Route TeleOp");
            assertTrue(shown(s), s.screenHas("route to the middle"));
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            s.press(v -> s.g1().dpad_left = v);
            assertTrue("it says it drives\n" + shown(s), waitForText(s, "Route: Middle", 2));
            assertTrue("it arrives\n" + shown(s), waitNoHiveBump(s, () -> s.screenHas("Middle: there"), 20));
            FieldPose at = HiveBot.last.pose();
            assertEquals(0, at.x, 1.5);
            assertEquals(0, at.y, 1.5);
            // The driver has the robot again.
            s.g1().left_stick_x = -0.5f;
            assertTrue("the stick moves it", SimRun.waitFor(() -> HiveBot.last.pose().x < -2, 3));
            s.g1().left_stick_x = 0;
            assertEquals("", s.error());
        }
    }

    @Test public void aDriveStickStopsTheRouteAtOnce() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route TeleOp", "TeleOp", RouteTeleOp.class);
            s.init("Route TeleOp");
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            s.press(v -> s.g1().dpad_right = v);
            assertTrue(shown(s), waitForText(s, "Route: Far", 2));
            s.g1().right_stick_x = 0.2f;
            assertTrue(shown(s), waitForText(s, "Far: stopped: a drive stick moved", 2));
            s.g1().right_stick_x = 0;
            assertTrue("the wheels stop", SimRun.waitFor(() -> s.robot().motors.get("front_left").userPower() == 0, 1));
            FieldPose stopped = HiveBot.last.pose();
            SimRun.pause(0.5);
            assertTrue("it does not set off again", Math.hypot(HiveBot.last.pose().x - stopped.x, HiveBot.last.pose().y - stopped.y) < 2);
            assertEquals("", s.error());
        }
    }

    @Test public void pauseEndsTheRouteAndItDoesNotSetOffAgain() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route TeleOp", "TeleOp", RouteTeleOp.class);
            s.init("Route TeleOp");
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            s.press(v -> s.g1().dpad_right = v);
            assertTrue(shown(s), waitForText(s, "Route: Far", 2));
            s.press(v -> s.g1().back = v);
            assertTrue(shown(s), waitForText(s, "PAUSED", 2));
            s.press(v -> s.g1().back = v);
            assertTrue(shown(s), SimRun.waitFor(() -> !s.screenHas("PAUSED"), 2));
            assertFalse(shown(s), s.screenHas("Route: Far"));
            assertTrue("the wheels stay stopped", s.robot().motors.get("front_left").userPower() == 0);
            // While paused a route does not start at all.
            s.press(v -> s.g1().back = v);
            assertTrue(shown(s), waitForText(s, "PAUSED", 2));
            s.press(v -> s.g1().dpad_right = v);
            assertFalse(shown(s), s.screenHas("Route: Far"));
            assertEquals("", s.error());
        }
    }

    @Test public void withoutAPoseItSaysWhyAndDoesNotMove() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route TeleOp", "TeleOp", RouteTeleOp.class);
            s.init("Route TeleOp");
            s.start();
            s.press(v -> s.g1().dpad_left = v);
            assertTrue(shown(s), waitForText(s, "Middle: not started: the pose is not set", 2));
            assertTrue(s.robot().motors.get("front_left").userPower() == 0);
            assertEquals("", s.error());
        }
    }

    @Test public void aTimeStartsTheRoute() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route Timed", "TeleOp", RouteTimed.class);
            s.init("Route Timed");
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            assertTrue("it arrives\n" + shown(s), waitNoHiveBump(s, () -> s.screenHas("Timed: there"), 20));
            assertEquals(-56, HiveBot.last.pose().x, 1.5);
            assertEquals(-36, HiveBot.last.pose().y, 1.5);
        }
    }

    @Test public void aTimedRouteWaitsForTheDriverToLetGo() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route Timed", "TeleOp", RouteTimed.class);
            s.init("Route Timed");
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            s.g1().right_stick_x = 0.2f;
            assertTrue("it waits, it does not end\n" + shown(s), waitForText(s, "Timed: waiting", 4));
            s.g1().right_stick_x = 0;
            assertTrue("let go: it arrives\n" + shown(s), waitNoHiveBump(s, () -> s.screenHas("Timed: there"), 20));
        }
    }

    @Test public void aTimedRouteGivesUpIfTheDriverKeepsDriving() throws Exception {
        try (SimRun s = new SimRun(robotFile(HiveBot.class).toString())) {
            s.sim.ds.register("Route Timed", "TeleOp", RouteTimed.class);
            s.init("Route Timed");
            s.start();
            standOnResetSpot(s, Alliance.BLUE, 0, -61);
            s.g1().right_stick_x = 0.2f;
            assertTrue(shown(s), waitForText(s, "Timed: not started: the driver kept driving", 9));
            s.g1().right_stick_x = 0;
            Thread.sleep(300);
            assertFalse("it stays given up\n" + shown(s), s.screenHas("Route: Timed"));
        }
    }

    @Test public void redDrivesTheMirrorImageOfTheRoute() throws Exception {
        try (SimRun s = new SimRun(robotFile(MirrorBot.class).toString())) {
            MatchMemory.remember(Alliance.RED, null);
            s.sim.ds.register("Route Mirror", "TeleOp", RouteMirror.class);
            s.init("Route Mirror");
            s.start();
            standOnResetSpot(s, Alliance.RED, 25, -55);
            assertEquals(shown(s), 25, MirrorBot.last.pose().x, 1);
            s.press(v -> s.g1().dpad_left = v);
            double[] least = {25};
            assertTrue("it arrives\n" + shown(s), SimRun.waitFor(() -> {
                least[0] = Math.min(least[0], MirrorBot.last.pose().x);
                return s.screenHas("Up: there");
            }, 20));
            assertEquals("RED's spot is x +25", 25, MirrorBot.last.pose().x, 1.5);
            assertEquals(-35, MirrorBot.last.pose().y, 1.5);
            // BLUE's box is -44..-10, so RED's is 10..44: the near way around it passes x 4.5.
            assertTrue("around the mirrored box: x " + least[0], least[0] < 8);
        }
    }

    @Test public void aRouteWithNoMapStopsAtInit() throws Exception {
        try (SimRun s = new SimRun(robotFile(NoMap.class).toString())) {
            s.sim.ds.register("Route No Map", "TeleOp", RouteNoMap.class);
            s.init("Route No Map");
            assertTrue(shown(s) + s.error(), SimRun.waitFor(() -> (shown(s) + s.error()).contains("needs a map"), 3));
        }
    }
}

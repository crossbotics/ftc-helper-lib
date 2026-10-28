package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.MotorPositions;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Tune and check": every check and finder for your robot in one OpMode, always on the Driver Station
 * (TeleOp list, group "Finders"). Nothing to copy: the library adds it.
 *
 * <p>INIT shows a menu. D-pad up/down picks a tool, A opens it (its own INIT screen: read its safety
 * note), START runs it. BACK in the tool's INIT goes back to the menu. To change tools after START:
 * STOP, then INIT again (the menu starts on the last tool).
 *
 * <p>The menu lists only the tools that fit Robot.java: the Hood Finder for a robot with a hood, the
 * Flywheel Finder and Shot Tuner for a robot with a launcher, and so on. It finds your robot by itself:
 * every class in {@code org.firstinspires.ftc.teamcode} (and the folders inside it) that extends
 * {@link RobotDefinition}. Two or more robots: D-pad left/right picks one. No robot in five files: the
 * tools that need no robot (Hardware Check, Servo, Motor and Flywheel finders).
 *
 * <p>Not in the menu: the Aim Tuner and the Characterizer. They need your camera or your lifts, so they
 * keep their own file (docs/guides/finders.md).
 *
 * Docs: docs/guides/finders.md, "Tune and check"
 */
@TeleOp(name = TuneAndCheckOpMode.NAME, group = "Finders")
public class TuneAndCheckOpMode extends LinearOpMode {

    /** The name on the Driver Station. */
    public static final String NAME = "Tune and check";

    // Where the team's code is: TeamCode's package.
    private static final String TEAM_PACKAGE = "org.firstinspires.ftc.teamcode";

    // The robots to use instead of the ones found (the simulator's tests). null = find them.
    private static List<Class<? extends RobotDefinition>> robotsGiven = null;
    // The last tool and robot opened, so the next INIT starts on them.
    private static String lastTool = null;
    private static String lastRobot = null;

    /** Uses these robot classes instead of looking for them (the simulator's tests). null = look again. */
    public static void useRobots(List<Class<? extends RobotDefinition>> robots) {
        robotsGiven = robots;
    }

    // One line of the menu: a check or finder, and how to make it for this robot.
    private static final class Tool {
        final String name;
        final String what;
        final Supplier<LinearOpMode> make;

        Tool(String name, String what, Supplier<LinearOpMode> make) {
            this.name = name;
            this.what = what;
            this.make = make;
        }
    }

    // Gamepad 1's D-pad and A in the last loop: only new presses count.
    private boolean upWas, downWas, leftWas, rightWas, aWas;

    @Override
    public void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        List<Class<? extends RobotDefinition>> robots = robotsGiven != null ? robotsGiven : findRobots();
        int robotAt = Math.max(0, indexOfName(robots, lastRobot));
        while (true) {
            // ---- The menu (INIT) ----
            Class<? extends RobotDefinition> robotClass = robots.isEmpty() ? null : robots.get(robotAt);
            String robotProblem = null;
            List<Tool> tools;
            try {
                tools = toolsFor(robotClass);
            } catch (RuntimeException e) {
                // Robot.java does not load (a MEASURE number, a typo): the tools that need no robot.
                robotProblem = robotClass.getSimpleName() + ".java: " + e.getMessage();
                tools = toolsFor(null);
            }
            int at = 0;
            for (int i = 0; i < tools.size(); i++) if (tools.get(i).name.equals(lastTool)) at = i;
            remember(gamepad1);   // a button still held (BACK, A) must not act at once
            Tool chosen = null;
            while (opModeInInit() && chosen == null) {
                Gamepad g = gamepad1;
                if (pressed(g.dpad_down, downWas)) at = (at + 1) % tools.size();
                if (pressed(g.dpad_up, upWas)) at = (at + tools.size() - 1) % tools.size();
                boolean robotChanged = false;
                if (robots.size() > 1 && pressed(g.dpad_right, rightWas)) {
                    robotAt = (robotAt + 1) % robots.size();
                    robotChanged = true;
                }
                if (robots.size() > 1 && pressed(g.dpad_left, leftWas)) {
                    robotAt = (robotAt + robots.size() - 1) % robots.size();
                    robotChanged = true;
                }
                if (pressed(g.a, aWas)) chosen = tools.get(at);
                remember(g);
                if (robotChanged) break;
                showMenu(t, robots, robotClass, robotProblem, tools, at);
            }
            if (chosen == null) {
                if (isStopRequested()) return;
                if (opModeInInit()) continue;   // another robot: make its menu
                // START with no tool opened: nothing runs.
                while (opModeIsActive()) {
                    t.warn("No tool was opened. Press STOP, then INIT: pick a tool with the D-pad, open it with A.");
                    t.update();
                }
                return;
            }
            // ---- The tool: its own INIT screen, then START runs it ----
            lastTool = chosen.name;
            lastRobot = robotClass == null ? null : robotClass.getName();
            Hosting hosting = new Hosting(this);
            LinearOpMode tool = chosen.make.get();
            if (tool instanceof FinderOpMode) ((FinderOpMode) tool).runInside(hosting);
            else ((HardwareCheckOpMode) tool).runInside(hosting);
            if (!hosting.leftForMenu() || !opModeInInit()) return;
        }
    }

    private static boolean pressed(boolean now, boolean was) {
        return now && !was;
    }

    private void remember(Gamepad g) {
        upWas = g.dpad_up;
        downWas = g.dpad_down;
        leftWas = g.dpad_left;
        rightWas = g.dpad_right;
        aWas = g.a;
    }

    private void showMenu(RobotTelemetry t, List<Class<? extends RobotDefinition>> robots,
                          Class<? extends RobotDefinition> robotClass, String robotProblem, List<Tool> tools, int at) {
        t.section(NAME);
        if (robotClass == null) t.text("No Robot.java found: the tools that need no robot.");
        else t.text("Robot: " + robotClass.getSimpleName() + (robots.size() > 1 ? "  (D-pad left/right = another robot)" : ""));
        if (robotProblem != null) t.warn(robotProblem);
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            t.text((i == at ? "> " : "  ") + tool.name + ": " + tool.what);
        }
        t.text("D-pad up/down = pick, A = open it. Then START.");
        t.text("In the tool's INIT: BACK = back to this menu.");
        t.update();
    }

    // The checks and finders that fit this robot (null: no robot), in the order to use them.
    private List<Tool> toolsFor(final Class<? extends RobotDefinition> robotClass) {
        final RobotDefinition robot = robotClass == null ? null : make(robotClass);
        List<Module<?>> modules = robot == null ? new ArrayList<Module<?>>() : robot.modules();
        final Supplier<RobotDefinition> fresh = () -> robotClass == null ? null : make(robotClass);
        List<Tool> out = new ArrayList<>();
        out.add(new Tool("Hardware Check", "every device found, turns the right way", () -> new HardwareCheckOpMode() {
            @Override protected RobotDefinition robot() { return fresh.get(); }
        }));
        if (!hardwareMap.getAllNames(Servo.class).isEmpty()) {
            out.add(new Tool("Servo Finder", "a servo's ends and positions", () -> new ServoFinderOpMode() { }));
        }
        if (any(modules, Hood.class)) {
            out.add(new Tool("Hood Finder", "the hood's two stops", () -> new HoodFinderOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot == null || any(modules, MotorPositions.class)) {
            out.add(new Tool("Motor Finder", "a lift's or arm's limits and positions", () -> new MotorFinderOpMode() { }));
        }
        if (robot == null || any(modules, Launcher.class)) {
            out.add(new Tool("Flywheel Finder", "the flywheel's gains", () -> new FlywheelFinderOpMode() { }));
        }
        if (hasFeedStop(modules)) {
            out.add(new Tool("Feed Stop Finder", "the feed stop's hold and release", () -> new FeedStopFinderOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot != null) {
            out.add(new Tool("Drive Tuner", "the wheel size, for the autonomous", () -> new DriveTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot != null && robot.odometrySpec() != null) {
            out.add(new Tool("Odometry Tuner", "pod directions and offsets", () -> new OdometryTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (any(modules, Launcher.class)) {
            out.add(new Tool("Shot Tuner", "the shot speed by distance", () -> new ShotTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (any(modules, PieceSensor.class)) {
            out.add(new Tool("Piece Tuner", "what each piece sensor sees", () -> new PieceTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        return out;
    }

    private static boolean hasFeedStop(List<Module<?>> modules) {
        for (Module<?> m : modules) if (m instanceof Launcher && ((Launcher) m).feedStopName() != null) return true;
        return false;
    }

    private static boolean any(List<Module<?>> modules, Class<?> kind) {
        for (Module<?> m : modules) if (kind.isInstance(m)) return true;
        return false;
    }

    // A new robot from its class (each tool gets its own: a robot is set up once per OpMode).
    private static RobotDefinition make(Class<? extends RobotDefinition> c) {
        try {
            return c.getDeclaredConstructor().newInstance().describe();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not make it (" + e + "): it needs a public constructor with no inputs", e);
        }
    }

    private static int indexOfName(List<Class<? extends RobotDefinition>> robots, String name) {
        for (int i = 0; i < robots.size(); i++) if (robots.get(i).getName().equals(name)) return i;
        return -1;
    }

    // ---- Finding the team's robot classes ----

    // Every class in TeamCode's package that extends RobotDefinition, A to Z.
    private List<Class<? extends RobotDefinition>> findRobots() {
        List<String> names = new ArrayList<>();
        try {
            // On the robot: the app's own classes (as the FTC SDK finds the OpModes).
            @SuppressWarnings("deprecation")
            dalvik.system.DexFile dex = new dalvik.system.DexFile(hardwareMap.appContext.getPackageCodePath());
            for (Enumeration<String> e = dex.entries(); e.hasMoreElements(); ) names.add(e.nextElement());
        } catch (Throwable notAndroid) {
            // The simulator (a computer, not Android): the class folders and jars it runs with.
            names.clear();
            classPathNames(names);
        }
        List<Class<? extends RobotDefinition>> out = new ArrayList<>();
        for (String n : names) {
            if (!n.startsWith(TEAM_PACKAGE + ".") || n.contains("$")) continue;
            try {
                Class<?> c = Class.forName(n, false, getClass().getClassLoader());
                if (RobotDefinition.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
                    out.add(c.asSubclass(RobotDefinition.class));
                }
            } catch (Throwable skip) {
                // a class that does not load here: not a robot
            }
        }
        java.util.Collections.sort(out, (a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    private static void classPathNames(List<String> out) {
        String prefix = TEAM_PACKAGE.replace('.', '/') + "/";
        for (String part : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            File where = new File(part, prefix);
            if (where.isDirectory()) collect(where, TEAM_PACKAGE, out);
        }
    }

    private static void collect(File dir, String pkg, List<String> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list);
        for (File f : list) {
            if (f.isDirectory()) collect(f, pkg + "." + f.getName(), out);
            else if (f.getName().endsWith(".class")) {
                out.add(pkg + "." + f.getName().substring(0, f.getName().length() - ".class".length()));
            }
        }
    }
}

package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Module;
import com.crossbotics.ftchelper.robot.MotorPositions;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * "Tune and check": every check and finder for your robot in one OpMode, always on the Driver Station
 * (TeleOp list, group "Finders"). Nothing to copy: the library adds it.
 *
 * <p>INIT shows a menu: the tools, and what the chosen one is for. D-pad up/down chooses a tool, A opens it (its own INIT screen: read its safety
 * note), START runs it. BACK in the tool's INIT goes back to the menu. To change tools after START:
 * STOP, then INIT again (the menu starts on the last tool).
 *
 * <p>The menu lists only the tools that fit Robot.java: the Hood Finder for a robot with a hood, the
 * Flywheel Finder and Shot Tuner for a robot with a launcher, the Group Shot Tuner for a launcher with a camera
 * that sees the goal, and so on. It finds your robot by itself:
 * every class in {@code org.firstinspires.ftc.teamcode} (and the folders inside it) that extends
 * {@link RobotDefinition}. Two or more robots: D-pad left/right picks one. No robot in five files: the
 * tools that need no robot (Hardware Check, Servo, Motor and Flywheel finders).
 *
 * <p>Not in the menu: the Aim Tuner and the Characterizer. They need your camera or your lifts, so they
 * keep their own file (docs/tuning/01-tune-and-check.md).
 *
 * Docs: docs/tuning/01-tune-and-check.md, "Tune and check"
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
        // The screens name the buttons printed on these controllers (A or ×): docs/modules/gamepad.md.
        com.crossbotics.ftchelper.gamepad.ButtonNames.watch(gamepad1, gamepad2);
        // A mistake found at INIT: plain lines on the Driver Station, not the FTC app's red line.
        com.crossbotics.ftchelper.core.InitStop.run(this, this::runBody);
    }

    private void runBody() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        List<Class<? extends RobotDefinition>> robots = robotsGiven != null ? robotsGiven : findRobots();
        int robotAt = Math.max(0, indexOfName(robots, lastRobot));
        while (true) {
            // ---- The menu (INIT) ----
            Class<? extends RobotDefinition> robotClass = robots.isEmpty() ? null : robots.get(robotAt);
            String robotProblem = null;
            List<Tool> tools;
            menuModules = new ArrayList<>();   // toolsFor() fills it when Robot.java loads
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
                // Each button is read ONCE a loop. The gamepad changes on another thread: a button read as up
                // here and as down a moment later (when it is remembered) would be a press that is never seen.
                Gamepad g = gamepad1;
                boolean down = g.dpad_down, up = g.dpad_up, right = g.dpad_right, left = g.dpad_left, a = g.a;
                if (pressed(down, downWas)) at = (at + 1) % tools.size();
                if (pressed(up, upWas)) at = (at + tools.size() - 1) % tools.size();
                boolean robotChanged = false;
                if (robots.size() > 1 && pressed(right, rightWas)) {
                    robotAt = (robotAt + 1) % robots.size();
                    robotChanged = true;
                }
                if (robots.size() > 1 && pressed(left, leftWas)) {
                    robotAt = (robotAt + robots.size() - 1) % robots.size();
                    robotChanged = true;
                }
                if (pressed(a, aWas)) chosen = tools.get(at);
                upWas = up;
                downWas = down;
                leftWas = left;
                rightWas = right;
                aWas = a;
                if (robotChanged) break;
                showMenu(t, robots, robotClass, robotProblem, tools, at);
            }
            if (chosen == null) {
                if (isStopRequested()) return;
                if (opModeInInit()) continue;   // another robot: make its menu
                // START with no tool opened: nothing runs.
                while (opModeIsActive()) {
                    t.warn("No tool was opened, so nothing runs.");
                    t.text("Next: press STOP (■), then INIT.");
                    t.text("D-pad = choose a tool. {A} = open it.");
                    t.update();
                }
                return;
            }
            // ---- The tool: its own INIT screen, then START runs it ----
            lastTool = chosen.name;
            lastRobot = robotClass == null ? null : robotClass.getName();
            Hosting hosting = new Hosting(this, robotClass != null, deviceSections(menuModules), partsInDegrees(menuModules));
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
        t.section(NAME + ": " + (at + 1) + " of " + tools.size());
        if (robotClass == null) {
            t.text("No Robot.java found: the tools that need none.");
        } else {
            t.text("Robot: " + robotClass.getSimpleName());
            if (robots.size() > 1) t.text("D-pad left/right = another robot.");
        }
        if (robotProblem != null) {
            t.warn(robotProblem);
            t.warn("Until you fix it, only some tools are listed.");
        }
        // Only names in the list (it must fit the screen), then what the chosen one is for.
        for (int i = 0; i < tools.size(); i++) t.text((i == at ? "> " : "  ") + tools.get(i).name);
        t.text("This tool: " + tools.get(at).what + ".");
        t.text("A new robot: do them from top to bottom.");
        t.text("D-pad up/down = choose. {A} = open it. Then {PLAY}.");
        t.text("In a tool's INIT: {BACK} = back to this menu.");
        t.update();
    }

    // The parts of the robot in the menu now (empty without one, or when Robot.java does not load).
    private List<Module<?>> menuModules = new ArrayList<>();

    // Each device's class in Tunables.java (tunables("Feeder") makes it differ from the device's name).
    static Map<String, String> deviceSections(List<Module<?>> modules) {
        Map<String, String> out = new java.util.HashMap<>();
        for (Module<?> m : withChildren(modules)) {
            out.put(m.deviceName(), m.name());
            String second = null;
            if (m instanceof com.crossbotics.ftchelper.robot.ServoPositions) second = ((com.crossbotics.ftchelper.robot.ServoPositions<?>) m).secondServoName();
            else if (m instanceof com.crossbotics.ftchelper.robot.Indexer) second = ((com.crossbotics.ftchelper.robot.Indexer) m).secondServoName();
            if (second != null) out.put(second, m.name());
        }
        return out;
    }

    // The motors of parts that count in degrees (an arm, a turret): motor counts are not their numbers.
    static java.util.Set<String> partsInDegrees(List<Module<?>> modules) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Module<?> m : modules) {
            if (m instanceof com.crossbotics.ftchelper.robot.Arm || m instanceof com.crossbotics.ftchelper.robot.Turret) out.add(m.deviceName());
        }
        return out;
    }

    // Each servo part's catalog id (part(...) in Robot.java), its second servo's too (the same servo model).
    static Map<String, String> servoParts(List<Module<?>> modules) {
        Map<String, String> out = new java.util.HashMap<>();
        List<Module<?>> all = withChildren(modules);
        for (Module<?> m : all) {
            if (m.partId() == null) continue;
            String second = null;
            if (m instanceof com.crossbotics.ftchelper.robot.ServoPositions) second = ((com.crossbotics.ftchelper.robot.ServoPositions<?>) m).secondServoName();
            else if (m instanceof com.crossbotics.ftchelper.robot.Indexer) second = ((com.crossbotics.ftchelper.robot.Indexer) m).secondServoName();
            else continue;
            out.put(m.deviceName(), m.partId());
            if (second != null) out.put(second, m.partId());
        }
        return out;
    }

    // Every positional servo part Robot.java names, and whether it is reversed() (also a second servo, a
    // launcher's feeder and hood, a servo hood, a servo turret). The Servo Finder turns each the same way.
    /** Each launcher's flywheel motor and whether Robot.java reverses it (for the Flywheel Finder). */
    static Map<String, Boolean> flywheelDirections(List<Module<?>> modules) {
        Map<String, Boolean> out = new java.util.HashMap<>();
        for (Module<?> m : withChildren(modules)) {
            if (m instanceof Launcher) out.put(m.deviceName(), ((Launcher) m).isReversed());
        }
        return out;
    }

    // Each launcher's gains written in Tunables.java ({kP, kI, kD, kF, nominalVolts or NaN}), by motor name.
    // Only launchers with kP, kI and kF written: the Flywheel Finder holds the wheel with them.
    static Map<String, double[]> flywheelGains(List<Module<?>> modules, Class<?> tunables) {
        Map<String, double[]> out = new java.util.HashMap<>();
        for (Module<?> m : withChildren(modules)) {
            if (!(m instanceof Launcher)) continue;
            double[] g = ((Launcher) m).writtenGains(tunables);
            if (g != null) out.put(m.deviceName(), g);
        }
        return out;
    }

    // The launchers' flywheel motors with speedControl = "BANG_BANG" written in Tunables.java.
    static java.util.Set<String> bangBangFlywheels(List<Module<?>> modules, Class<?> tunables) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (tunables == null) return out;
        for (Module<?> m : withChildren(modules)) {
            if (m instanceof Launcher && "BANG_BANG".equals(FinderOpMode.codeWord(tunables, m.name(), "speedControl"))) {
                out.add(m.deviceName());
            }
        }
        return out;
    }

    static Map<String, Boolean> servoDirections(List<Module<?>> modules) {
        Map<String, Boolean> out = new java.util.HashMap<>();
        for (Module<?> m : withChildren(modules)) {
            if (m instanceof com.crossbotics.ftchelper.robot.ServoPositions) {
                com.crossbotics.ftchelper.robot.ServoPositions<?> s = (com.crossbotics.ftchelper.robot.ServoPositions<?>) m;
                out.put(s.deviceName(), s.isReversed());
                if (s.secondServoName() != null) out.put(s.secondServoName(), s.secondServoMirrored() != s.isReversed());
            } else if (m instanceof com.crossbotics.ftchelper.robot.Indexer && ((com.crossbotics.ftchelper.robot.Indexer) m).isServo()) {
                com.crossbotics.ftchelper.robot.Indexer s = (com.crossbotics.ftchelper.robot.Indexer) m;
                out.put(s.deviceName(), s.isReversed());
                if (s.secondServoName() != null) out.put(s.secondServoName(), s.secondServoMirrored() != s.isReversed());
            } else if (m instanceof Hood && !((Hood) m).isMotor()) {
                out.put(m.deviceName(), ((Hood) m).isReversed());
            } else if (m instanceof com.crossbotics.ftchelper.robot.Turret && ((com.crossbotics.ftchelper.robot.Turret) m).isServoTurret()) {
                out.put(m.deviceName(), ((com.crossbotics.ftchelper.robot.Turret) m).isReversedServo());
            }
        }
        return out;
    }

    // The parts and their launcher children (feeder, hood).
    static List<Module<?>> withChildren(List<Module<?>> modules) {
        List<Module<?>> all = new ArrayList<>(modules);
        for (Module<?> m : modules) {
            if (!(m instanceof Launcher)) continue;
            Launcher l = (Launcher) m;
            if (l.feeder() != null) all.add(l.feeder());
            if (l.hood() != null) all.add(l.hood());
        }
        return all;
    }

    // Each two-servo part's first servo, its second servo and whether it is mirrored (alsoServo... in
    // Robot.java), also a launcher's feeder (a child part).
    static Map<String, Map.Entry<String, Boolean>> servoPairs(List<Module<?>> modules) {
        Map<String, Map.Entry<String, Boolean>> out = new java.util.LinkedHashMap<>();
        List<Module<?>> all = withChildren(modules);
        for (Module<?> m : all) {
            String first = null, second = null;
            boolean mirrored = false;
            if (m instanceof com.crossbotics.ftchelper.robot.ServoPositions) {
                com.crossbotics.ftchelper.robot.ServoPositions<?> s = (com.crossbotics.ftchelper.robot.ServoPositions<?>) m;
                second = s.secondServoName();
                mirrored = s.secondServoMirrored();
                first = s.deviceName();
            } else if (m instanceof com.crossbotics.ftchelper.robot.Indexer) {
                com.crossbotics.ftchelper.robot.Indexer s = (com.crossbotics.ftchelper.robot.Indexer) m;
                second = s.secondServoName();
                mirrored = s.secondServoMirrored();
                first = s.deviceName();
            }
            if (first != null && second != null) out.put(first, new java.util.AbstractMap.SimpleEntry<>(second, mirrored));
        }
        return out;
    }

    // True when a camera in Robot.java has goal(...): the robot can aim at the goal with it.
    private static boolean aimsWithCamera(List<Module<?>> modules) {
        for (Module<?> m : modules) {
            if (m instanceof com.crossbotics.ftchelper.robot.Camera && ((com.crossbotics.ftchelper.robot.Camera) m).knowsGoal()) return true;
        }
        return false;
    }

    // The checks and finders that fit this robot (null: no robot), in the order to use them.
    private List<Tool> toolsFor(final Class<? extends RobotDefinition> robotClass) {
        final RobotDefinition robot = robotClass == null ? null : make(robotClass);
        List<Module<?>> modules = robot == null ? new ArrayList<Module<?>>() : robot.modules();
        menuModules = modules;
        final Supplier<RobotDefinition> fresh = () -> robotClass == null ? null : make(robotClass);
        List<Tool> out = new ArrayList<>();
        out.add(new Tool("Hardware Check", "test each device", () -> new HardwareCheckOpMode() {
            @Override protected RobotDefinition robot() { return fresh.get(); }
        }));
        // Every robot in five files has a drive (define() needs one): each wheel must roll forward.
        if (robot != null) {
            out.add(new Tool("Motor Direction Check", "each wheel rolls forward", () -> new MotorDirectionCheckOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
            // Every motor's encoder cable in its own port (a lift, the flywheel, the drive trust it).
            out.add(new Tool("Encoder Check", "each motor's encoder cable", () -> new EncoderCheckOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (!hardwareMap.getAllNames(Servo.class).isEmpty()) {
            // Two-servo parts (alsoServo... in Robot.java): the second servo moves with the first in the finder.
            final Map<String, Map.Entry<String, Boolean>> pairs = servoPairs(modules);
            final Map<String, Boolean> directions = servoDirections(modules);
            final Map<String, String> partIds = servoParts(modules);
            out.add(new Tool("Servo Finder", "a servo's safe positions", () -> new ServoFinderOpMode() {
                @Override protected Map<String, Map.Entry<String, Boolean>> pairedServos() { return pairs; }
                @Override protected Map<String, Boolean> servoDirections() { return directions; }
                @Override protected Map<String, String> servoParts() { return partIds; }
            }));
        }
        // A launcher's hood (Launcher...hood(...)) is the launcher's child: it counts too.
        if (any(withChildren(modules), Hood.class)) {
            out.add(new Tool("Hood Finder", "the hood's two ends", () -> new HoodFinderOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot == null || any(modules, MotorPositions.class)) {
            out.add(new Tool("Motor Finder", "how far a lift may go", () -> new MotorFinderOpMode() { }));
        }
        if (robot == null || any(modules, Launcher.class)) {
            // Each launcher's motor starts the way Robot.java turns it (reversed() there = reversed here).
            // ...and the finder holds it with the gains in Tunables.java (when written): Y switches to its own.
            final Map<String, Boolean> motorDirs = flywheelDirections(modules);
            final Map<String, double[]> gains = flywheelGains(modules, robot == null ? null : robot.tunablesClass());
            final java.util.Set<String> bang = bangBangFlywheels(modules, robot == null ? null : robot.tunablesClass());
            out.add(new Tool("Flywheel Finder", "hold the flywheel's speed", () -> new FlywheelFinderOpMode() {
                @Override protected java.util.Set<String> bangBangMotors() { return bang; }
                @Override protected Map<String, Boolean> motorDirections() { return motorDirs; }
                @Override protected Map<String, double[]> robotGains() { return gains; }
            }));
        }
        // It tests PID and bang-bang on the launcher's own flywheel, so it needs Robot.java's launcher.
        if (any(modules, Launcher.class)) {
            out.add(new Tool("Flywheel Tuner", "compare PID and bang-bang", () -> new FlywheelTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (hasFeedStop(modules)) {
            out.add(new Tool("Feed Stop Finder", "feed stop HOLD and LET GO", () -> new FeedStopFinderOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (hasTimedFeeder(modules)) {
            out.add(new Tool("Feed Time Finder", "feed time for each shot", () -> new FeedTimeFinderOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot != null) {
            out.add(new Tool("Drive Tuner", "how far the wheels really go", () -> new DriveTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot != null && robot.odometrySpec() != null) {
            out.add(new Tool("Odometry Tuner", "the odometry measures right", () -> new OdometryTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (robot != null) {
            out.add(new Tool("Move Tuner", "moves stop in the right place", () -> new MoveTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        if (any(modules, Launcher.class)) {
            out.add(new Tool("Shot Tuner", "shot speed for each distance", () -> new ShotTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        // Groups of 4 with the Flywheel Tuner's best settings, aimed by the camera: a launcher and a camera with goal(...).
        if (robot != null && any(modules, Launcher.class) && aimsWithCamera(modules)) {
            out.add(new Tool("Group Shot Tuner", "PID or bang-bang, with real balls", () -> new GroupShotTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        // The Camera Fix Check (alpha) is not in the menu: the camera fix is untested and cannot work in
        // BIOBUZZ (its tags move with the HIVEs). A team that tests it runs CameraFixCheckOpMode from its
        // own file (docs/modules/odometry.md, "Camera fix (alpha)"). Look again each new season.
        // The sensors inside a Pieces part count too (the Piece Tuner finds them the same way).
        if (robot != null && !PieceTunerOpMode.sensorsOf(robot).isEmpty()) {
            out.add(new Tool("Piece Tuner", "teach the piece sensors", () -> new PieceTunerOpMode() {
                @Override protected RobotDefinition robot() { return fresh.get(); }
            }));
        }
        return out;
    }

    private static boolean hasFeedStop(List<Module<?>> modules) {
        for (Module<?> m : modules) if (m instanceof Launcher && ((Launcher) m).feedStopName() != null) return true;
        return false;
    }

    private static boolean hasTimedFeeder(List<Module<?>> modules) {
        for (Module<?> m : modules) if (m instanceof Launcher && ((Launcher) m).hasTimedFeeder()) return true;
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

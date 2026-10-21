package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What is on the robot, written once in the team's {@code Robot.java}: the drive and one line per
 * mechanism, with each part's name in the robot configuration. TeleOp ({@link ControlMap}) and every
 * autonomous ({@link Auto}) use it, so a renamed motor is changed in one place.
 *
 * <pre>{@code
 * public class Robot extends RobotDefinition {
 *     public Intake intake;
 *     public Lift lift;
 *     public Claw claw;
 *
 *     @Override protected void define() {
 *         drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
 *                 .motor("gobilda-5203-312").wheelMm(96)
 *                 .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
 *         intake = add(Intake.motor("intake"));
 *         lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
 *         claw = add(Claw.servo("claw"));
 *     }
 * }
 * }</pre>
 *
 * <p>The numbers live in {@code Tunables.java} next to this file (one section per module), the
 * buttons in {@code Controls.java}, steps that use several parts in {@code Actions.java}.
 *
 * <p>Nothing moves in INIT: the modules make their hardware then, and move only after START.
 *
 * Docs: docs/modules/robot.md. Tutorial: docs/tutorials/22-robot-in-five-files.md
 */
public abstract class RobotDefinition {
    private Drive drive = null;
    // The odometry, if Robot.java has an odometry(...) line; the real thing after setup.
    private Odometry odometrySpec = null;
    private com.crossbotics.ftchelper.odometry.PoseTracker tracker = null;
    private final List<Module<?>> modules = new ArrayList<>();
    private final List<Supplier<Action>> atStart = new ArrayList<>();
    private Class<?> tunablesClass = null;
    private boolean tunablesGiven = false;
    private boolean defined = false;
    private boolean built = false;
    private volatile boolean paused = false;
    private Subsystems all = null;
    private Alliance alliance = Alliance.BLUE;
    private TunableSection driveSection = null;
    // Tunables sections no part reads (Tunables.Auto): live in the tuning menu. Null before setup.
    private OtherSections otherSections = null;
    // For the simulator: the chassis box and the whole robot's height (inches; NaN = not given), fixed shapes.
    private double[] chassisIn = null;
    private double heightIn = Double.NaN;
    private final List<Shape> fixed = new ArrayList<>();
    /** Runs the actions; one per robot. */
    final ActionRunner runner = new ActionRunner();

    /**
     * Describe the robot here: one {@link #drive} line and one {@link #add} line per mechanism.
     * Only describe: do not move anything (it runs in INIT, and also without a robot, to write the
     * simulator file).
     */
    protected abstract void define();

    // ----------------------------- for define() -----------------------------

    /**
     * The drivetrain.
     *
     * @param d e.g. {@code Drive.mecanum("front_left", "front_right", "back_left", "back_right")}
     */
    protected final void drive(Drive d) {
        drive = d;
    }

    /**
     * The odometry: what tells the robot where it is on the field (see {@link Odometry}).
     *
     * @param o e.g. {@code Odometry.pinpoint("pinpoint").offsetsMm(-84, -168)}
     */
    protected final void odometry(Odometry o) {
        odometrySpec = o;
    }

    /**
     * Adds a mechanism and hands it back, so it can go in a field: {@code lift = add(Lift.slide("lift"));}
     *
     * @param module the module, e.g. {@code Intake.motor("intake")}
     * @return the same module
     */
    protected final <T extends Module<?>> T add(T module) {
        if (module instanceof Indexer && ((Indexer) module).inLauncher) {
            throw new IllegalStateException("Robot.java: " + module.name() + " already feeds a launcher; "
                    + "do not add it again");
        }
        if (modules.contains(module)) throw new IllegalStateException("Robot.java: " + module.name() + " is added twice");
        modules.add(module);
        return module;
    }

    /**
     * The Tunables class, if it is not called {@code Tunables} in the same folder as Robot.java.
     *
     * @param tunables e.g. {@code MyNumbers.class}
     */
    protected final void tunables(Class<?> tunables) {
        tunablesClass = tunables;
        tunablesGiven = true;
    }

    /**
     * An action that runs once, right after START, in autonomous and in TeleOp (for example a servo
     * that pushes the intake into place). Never in INIT: a robot must not move before START.
     *
     * @param action e.g. {@code () -> Actions.deployIntake(this)}
     */
    protected final void atStart(Supplier<Action> action) {
        atStart.add(action);
    }

    /**
     * The chassis box for the simulator (it bumps into the field and the game pieces): length front
     * to back, width side to side, height from the tiles to its top, in mm.
     */
    protected final void chassisMm(double length, double width, double height) {
        chassisIn = new double[] {length / 25.4, width / 25.4, height / 25.4};
    }

    /** The whole robot's height in mm (the tallest fixed part), for the simulator. */
    protected final void heightMm(double height) {
        heightIn = height / 25.4;
    }

    /**
     * Parts the code does not move, for the simulator: {@code fixed(Shape.box("ramp")...)}, and
     * {@link Shape#passive} parts that belong to no module.
     *
     * @param shapes one or more {@link Shape#box} or passive shapes
     */
    protected final void fixed(Shape... shapes) {
        for (Shape s : shapes) {
            if (!s.type.equals("box") && !s.passive) throw new IllegalArgumentException("Robot.java: fixed(...) takes Shape.box(...) "
                    + "and passive parts; a part the code moves goes in its module's looks(...)");
            if (s.name == null) throw new IllegalArgumentException("Robot.java: a fixed " + s.type + " needs .name(...)");
            fixed.add(s);
        }
    }

    // ----------------------------- set up -----------------------------

    /** Runs define() once (no hardware). Used by setup and by the simulator file generator. */
    public final RobotDefinition describe() {
        if (defined) return this;
        defined = true;
        define();
        if (drive == null) throw new IllegalStateException("Robot.java: define() needs a drive(...) line");
        if (!tunablesGiven) tunablesClass = findTunables();
        checkNames();
        driveSection = TunableSection.of(tunablesClass, "Drive");
        return this;
    }

    /**
     * Makes every module's hardware (INIT). The OpMode classes call this for you.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     */
    public final RobotDefinition setup(HardwareMap hardwareMap) {
        return setup(Devices.of(hardwareMap));
    }

    /** {@link #setup(HardwareMap)} with any {@link Devices} (tests use pretend ones). */
    public final RobotDefinition setup(Devices devices) {
        if (built) throw new IllegalStateException("This robot is already set up: make a new one per OpMode");
        describe();
        built = true;
        // Paused: nothing is updated (so nothing moves). At the end (STOP, or an error) everything is
        // paused first, so every motor stops on brake, then stopped.
        all = new Subsystems() {
            @Override public void update() {
                // A number changed in the tuning menu reaches Tunables.Auto (and the like) at once.
                if (otherSections != null) otherSections.copy();
                if (!paused) super.update();
            }

            @Override public void stop() {
                RuntimeException first = null;
                try { pauseAll(); } catch (RuntimeException e) { first = e; }
                try { super.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
                if (first != null) throw first;
            }
        };
        // The odometry first of all, so every part and action reads this loop's pose.
        if (odometrySpec != null) {
            tracker = odometrySpec.build(devices, TunableSection.of(tunablesClass, "Odometry"), drive);
            all.add(tracker);
        }
        all.add(runner);
        // Cameras first, so the parts that use them see this loop's picture.
        List<Module<?>> ordered = new ArrayList<>();
        for (Module<?> m : modules) if (m instanceof Camera) ordered.add(m);
        for (Module<?> m : modules) if (!(m instanceof Camera)) ordered.add(m);
        for (Module<?> m : ordered) {
            for (Module<?> child : m.children()) buildOne(child, devices);
            buildOne(m, devices);
            all.add(m);
        }
        // The other sections (Tunables.Auto...): in the tuning menu, and their saved values used now,
        // before the autonomous reads them to build its steps.
        Set<String> used = new LinkedHashSet<>();
        used.add("Drive");
        used.add("Odometry");   // Tunables.Odometry: the odometry's scales and "checked", read here
        used.add("Buttons");   // Tunables.Buttons: the gesture times, read by Controls.java's buttons
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) used.add(c.name());
            used.add(m.name());
        }
        otherSections = new OtherSections(tunablesClass, used);
        return this;
    }

    private void buildOne(Module<?> m, Devices devices) {
        m.robot = this;
        m.tunables = TunableSection.of(tunablesClass, m.name());
        m.build(devices, m.tunables);
    }

    // ----------------------------- for the team's code -----------------------------

    /** Every part, for one update() per loop (the OpMode classes do this). Null before {@link #setup}. */
    public final Subsystems all() {
        return all;
    }

    /** The drivetrain's settings (motor names, IMU, autonomous tuning) for EasyTeleOp and EasyAuto. */
    public final RobotConfig robotConfig() {
        describe();
        return drive.config(driveSection);
    }

    /** The odometry as running (null before setup, or with no odometry(...) line). */
    public final com.crossbotics.ftchelper.odometry.PoseTracker odometry() {
        return tracker;
    }

    /** The odometry as written in define() (null if none). */
    public final Odometry odometrySpec() {
        describe();
        return odometrySpec;
    }

    /**
     * Where the robot is now, from the odometry: x inches right, y inches away from your drivers' wall,
     * heading 0 = facing away, counterclockwise + (the same as autonomous start spots). Null if the robot
     * has no odometry. A copy: changing it does not move anything.
     */
    public final com.crossbotics.ftchelper.util.FieldPose pose() {
        return tracker == null ? null : tracker.pose();
    }

    /**
     * True once {@code Tunables.Odometry.checked = true}: the odometry's directions and offsets were checked
     * with the Odometry Tuner. Until then, {@code driveTo} steps do not run on a real robot.
     */
    public final boolean odometryChecked() {
        describe();
        return TunableSection.of(tunablesClass, "Odometry").flag("checked", false);
    }

    /** The drivetrain as written in define(). */
    public final Drive drive() {
        describe();
        return drive;
    }

    /** The mechanisms, in the order they were added (read only). */
    public final List<Module<?>> modules() {
        describe();
        return Collections.unmodifiableList(modules);
    }

    /**
     * Pauses the whole robot: every part stops now with its motors on brake (actions wait), and
     * remembers what it was doing. {@link #resume()} goes on from there. TeleOp's BACK does it.
     */
    public final void pauseAll() {
        if (paused || !built) return;
        paused = true;
        // Every part pauses even if one fails (a motor that throws); the first error is reported after.
        RuntimeException first = null;
        try { runner.pause(); } catch (RuntimeException e) { first = e; }
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) {
                try { c.pause(); } catch (RuntimeException e) { if (first == null) first = e; }
            }
            try { m.pause(); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }

    /** Ends {@link #pauseAll()}: every part goes back to what it was doing. */
    public final void resume() {
        if (!paused) return;
        paused = false;
        runner.resume();
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) c.resume();
            m.resume();
        }
    }

    /** Pauses if running, resumes if paused. */
    public final void togglePause() {
        if (paused) resume();
        else pauseAll();
    }

    /** True while paused. */
    public final boolean isPaused() {
        return paused;
    }

    /** Starts an action (from its first step if it was running). It runs a little every loop. */
    public final void run(Action action) {
        runner.start(action);
    }

    /** Stops an action. Its parts stop safely (a lift holds its height, waiting shots are dropped). */
    public final void cancel(Action action) {
        runner.stop(action, "cancelled");
    }

    /** Stops every running action. */
    public final void cancelAll() {
        runner.stopAll("cancelled");
    }

    /** The alliance for this match (chosen in autonomous; TeleOp remembers it). */
    public final Alliance alliance() {
        return alliance;
    }

    /** Sets the alliance (the OpMode classes do this). */
    public final void setAlliance(Alliance a) {
        if (a != null) alliance = a;
    }

    /** Notes for INIT: missing Tunables sections or fields (the library's defaults are used). */
    public final List<String> notes() {
        describe();
        List<String> out = new ArrayList<>();
        if (tunablesClass == null) {
            out.add("No Tunables class next to Robot.java: every module uses the library's defaults");
            return out;
        }
        if (driveSection != null && !driveSection.note().isEmpty()) out.add(driveSection.note());
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) out.addAll(c.notes());
            out.addAll(m.notes());
        }
        return out;
    }

    /** The Tunables class in use, or null if there is none. */
    public final Class<?> tunablesClass() {
        describe();
        return tunablesClass;
    }

    // ----------------------------- the library's -----------------------------

    /** Right after START: the modules' start (an intake "on at the start") and the atStart actions. */
    final void started() {
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) c.start();
            m.start();
        }
        for (Supplier<Action> a : atStart) runner.start(a.get());
    }

    /** Tunables.Drive.slowSpeed. */
    final double slowSpeed() {
        describe();
        return Drive.slowSpeed(driveSection);
    }

    /** Each module's suggested buttons, in the order they were added. */
    final void suggestButtons(ButtonPlan plan) {
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) {
                plan.source = c.name();
                c.suggestButtons(plan);
            }
            plan.source = m.name();
            m.suggestButtons(plan);
        }
        plan.source = null;
    }

    /**
     * The simulator robot file's lines from the code: the drive, then each module's (see
     * {@code ./gradlew :sim:fromRobot}). Works without a robot.
     */
    public final List<String> simLines() {
        describe();
        List<String> out = new ArrayList<>();
        if (chassisIn != null) {
            out.add("robot.lengthIn = " + Shape.n(chassisIn[0]));
            out.add("robot.widthIn = " + Shape.n(chassisIn[1]));
            out.add("robot.chassisHeightIn = " + Shape.n(chassisIn[2]));
        }
        if (!Double.isNaN(heightIn)) {
            out.add("robot.heightIn = " + Shape.n(heightIn));
            // With shapes the simulator measures the shapes; this keeps the given height in the size rules.
            out.add("robot.tallestIn = " + Shape.n(heightIn));
        }
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) c.simSection = TunableSection.of(tunablesClass, c.name());
            m.simSection = TunableSection.of(tunablesClass, m.name());
        }
        drive.simLines(out);
        if (odometrySpec != null) odometrySpec.simLines(out);
        for (Shape s : fixed) out.add(s.line(s.name, s.parentName(s.name), null));
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) c.simLines(out, TunableSection.of(tunablesClass, c.name()));
            m.simLines(out, TunableSection.of(tunablesClass, m.name()));
        }
        // Two lines with one name (a shape named like another part's): the simulator would keep only
        // the last, and a part could vanish from the size checks. Stop with the name instead.
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String line : out) {
            int eq = line.indexOf('=');
            String key = eq < 0 ? line : line.substring(0, eq).trim();
            if (!keys.add(key)) {
                throw new IllegalStateException("Robot.java: two parts both make \"" + key + "\" in the simulator "
                        + "file: give one of them another name (Shape....name(\"...\"))");
            }
        }
        return out;
    }

    /**
     * The Tunables values changed on this robot (tuning menu) that differ from Tunables.java, as lines
     * to copy back into the code: "Tunables.Lift.kP = 0.0050 (code 0.004)". Empty if none.
     */
    public static List<String> changedTunables() {
        List<String> out = new ArrayList<>();
        for (Tunable t : Tunable.all()) {
            if (t.isChanged()) {
                out.add("Tunables." + t.name().replace('/', '.') + " = " + t.display() + " (code " + t.defaultValue() + ")");
            }
        }
        return out;
    }

    /**
     * The Tunables section a device's module reads: its name in CamelCase ("intake_deploy" ->
     * "IntakeDeploy", "Webcam 1" -> "Webcam1").
     *
     * @param deviceName the configuration name
     */
    public static String sectionName(String deviceName) {
        return Module.sectionFor(deviceName);
    }

    /**
     * Makes the team's robot from an OpMode class's type: {@code Controls extends ControlMap<Robot>}
     * gives a new Robot. The Robot class needs a constructor without inputs (Java gives one when you
     * write none).
     */
    static RobotDefinition newFromTypeOf(Class<?> opMode, Class<?> base) {
        for (Class<?> c = opMode; c != null && c != Object.class; c = c.getSuperclass()) {
            Type t = c.getGenericSuperclass();
            if (t instanceof ParameterizedType && ((ParameterizedType) t).getRawType() == base) {
                Type arg = ((ParameterizedType) t).getActualTypeArguments()[0];
                if (arg instanceof Class) {
                    try {
                        return (RobotDefinition) ((Class<?>) arg).getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not make a " + ((Class<?>) arg).getSimpleName()
                                + ": it needs a public constructor with no inputs (" + e + ")", e);
                    }
                }
            }
        }
        throw new IllegalStateException(opMode.getSimpleName() + " must name its robot class: \"extends "
                + base.getSimpleName() + "<Robot>\"");
    }

    // Tunables in the same package as the team's Robot class.
    private Class<?> findTunables() {
        String cls = getClass().getName();
        int dot = cls.lastIndexOf('.');
        String name = (dot < 0 ? "" : cls.substring(0, dot + 1)) + "Tunables";
        try {
            return Class.forName(name, true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // Every device name once only, and no two modules with the same Tunables section.
    private void checkNames() {
        Set<String> devices = new LinkedHashSet<>(drive.motorNames());
        if (devices.size() != drive.motorNames().size()) {
            throw new IllegalStateException("Robot.java: the drive uses one motor name twice: " + drive.motorNames());
        }
        // A Pinpoint or an OTOS is its own device; dead wheels use motor ports another part may also use.
        if (odometrySpec != null && (odometrySpec.kind == Odometry.Kind.PINPOINT || odometrySpec.kind == Odometry.Kind.OTOS)) {
            for (String d : odometrySpec.deviceNames()) {
                if (!devices.add(d)) throw new IllegalStateException("Robot.java: the device \"" + d + "\" is used twice");
            }
        }
        Set<String> sections = new LinkedHashSet<>();
        sections.add("Drive");
        for (Module<?> m : modules) {
            List<Module<?>> group = new ArrayList<>(m.children());
            group.add(m);
            for (Module<?> g : group) {
                for (String d : g.deviceNames()) {
                    if (!devices.add(d)) throw new IllegalStateException("Robot.java: the device \"" + d + "\" is used twice");
                }
                if (!sections.add(g.name())) {
                    throw new IllegalStateException("Robot.java: two parts read Tunables." + g.name()
                            + ": give one of them .tunables(\"OtherName\")");
                }
            }
        }
    }
}

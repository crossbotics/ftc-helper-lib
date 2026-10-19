package io.github.jasoncross.ftchelper.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.tuning.Tunable;
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
    private final List<Module<?>> modules = new ArrayList<>();
    private final List<Supplier<Action>> atStart = new ArrayList<>();
    private Class<?> tunablesClass = null;
    private boolean tunablesGiven = false;
    private boolean defined = false;
    private boolean built = false;
    private Subsystems all = null;
    private Alliance alliance = Alliance.BLUE;
    private TunableSection driveSection = null;
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
        all = new Subsystems().add(runner);
        // Cameras first, so the parts that use them see this loop's picture.
        List<Module<?>> ordered = new ArrayList<>();
        for (Module<?> m : modules) if (m instanceof Camera) ordered.add(m);
        for (Module<?> m : modules) if (!(m instanceof Camera)) ordered.add(m);
        for (Module<?> m : ordered) {
            for (Module<?> child : m.children()) buildOne(child, devices);
            buildOne(m, devices);
            all.add(m);
        }
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
        drive.simLines(out);
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) c.simLines(out, TunableSection.of(tunablesClass, c.name()));
            m.simLines(out, TunableSection.of(tunablesClass, m.name()));
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

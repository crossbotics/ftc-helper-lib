package io.github.jasoncross.ftchelper.robot;

import io.github.jasoncross.ftchelper.core.Subsystem;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One kind of mechanism in {@code Robot.java}: an {@link Intake}, a {@link Lift}, a {@link Claw}...
 * Each module brings four things: its line in {@code Robot.java}, its section in {@code Tunables.java},
 * its suggested buttons (the standard button map) and its lines in the simulator robot file.
 *
 * <pre>{@code
 * lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));   // in Robot.java's define()
 * }</pre>
 *
 * <p>The module's numbers come from the {@code Tunables.java} section with the device's name in
 * CamelCase: device "intake_deploy" reads {@code Tunables.IntakeDeploy}. Pick another section with
 * {@link #tunables(String)}.
 *
 * <p>{@code <M extends Module<M>>} lets each setting below hand back the module's own type, so
 * {@code Lift.slide("lift").tunables("Slide")} is still a Lift.
 *
 * Docs: docs/modules/robot.md
 */
public abstract class Module<M extends Module<M>> implements Subsystem {
    /** The main device's configuration name, e.g. "lift". */
    final String device;
    // The Tunables.java section, e.g. "Lift".
    private String section;
    // Set when the robot is set up.
    RobotDefinition robot;
    TunableSection tunables;
    // The running action that is using this part now (null = none): see touch().
    Action owner;
    // The main device's parts catalog id (null = not given) and how the part looks (see Shape).
    String partId;
    final List<Shape> looks = new ArrayList<>();
    // While writing the simulator file: this module's Tunables section (another part's shape may need it).
    TunableSection simSection;

    /**
     * @param device the main device's configuration name
     */
    Module(String device) {
        this.device = device;
        this.section = sectionFor(device);
    }

    // "unchecked": Java cannot prove that "this" is an M, but every module class passes itself as M.
    @SuppressWarnings("unchecked")
    final M self() {
        return (M) this;
    }

    /**
     * Reads this module's numbers from another {@code Tunables.java} section.
     *
     * @param sectionName the section's class name, e.g. "Slide"
     * @return this module
     */
    public M tunables(String sectionName) {
        section = sectionName;
        return self();
    }

    /**
     * The main device's parts catalog id: a motor ({@link Motors}, e.g. "gobilda-5203-117") or a servo
     * ({@link Parts#servo}, e.g. "gobilda-2000-speed"). The simulator then moves it like the real one.
     *
     * @param catalogId the id; a typo stops with the list of known ids
     * @return this module
     */
    public M part(String catalogId) {
        partId = checkPart(catalogId);
        return self();
    }

    /**
     * How the part looks and where it sits ({@link Shape}), for the simulator's 3D view and size checks.
     * This part moves them: an intake spins its rollers, a positioner swings its arm.
     *
     * @param shapes one or more, e.g. {@code Shape.roller("offset-tubing-90").atMm(200, 0, 50).lengthMm(240)}
     * @return this module
     */
    public M looks(Shape... shapes) {
        if (!drawsLooks()) {
            throw new UnsupportedOperationException("Robot.java: " + name() + " cannot take shapes yet (Intake, Positioner, "
                    + "Launcher and Indexer can). Draw it in the simulator robot file instead (docs/guides/simulator.md).");
        }
        for (Shape s : shapes) looks.add(s);
        return self();
    }

    /** The name shown on the Driver Station and the Tunables section: "Lift", "IntakeDeploy". */
    @Override
    public String name() {
        return section;
    }

    /** The main device's configuration name, e.g. "lift". */
    public String deviceName() {
        return device;
    }

    /** The modules must not be used before the robot is set up: a clear message instead of a crash. */
    final void needBuilt(Object part) {
        if (part == null) {
            throw new IllegalStateException(name() + " is not set up yet: use it only after INIT "
                    + "(the library sets it up from Robot.java)");
        }
    }

    // ---------------------------------------------------------------------------------------
    // What every module fills in (the library calls these; "package-private": not for team code)
    // ---------------------------------------------------------------------------------------

    /** Makes the hardware and reads the numbers. Called once, in INIT. Must not move anything. */
    abstract void build(Devices devices, TunableSection t);

    /** Called once right after START (for example an intake that is "on at the start"). */
    void start() { }

    /** Every device this module uses, by configuration name (checked: each name once per robot). */
    List<String> deviceNames() {
        return java.util.Collections.singletonList(device);
    }

    /**
     * Stops what an unfinished action told this part to do, safely and without counting as the
     * driver's command (see ActionRunner). Default: nothing (a servo is already where it was sent).
     */
    void halt() { }

    /** Modules inside this one (a launcher's indexer): set up with it, not updated on their own. */
    List<Module<?>> children() {
        return java.util.Collections.emptyList();
    }

    /** Adds this module's suggested buttons to the standard map. */
    void suggestButtons(ButtonPlan plan) { }

    /**
     * The module's lines for the simulator robot file (see {@code ./gradlew :sim:fromRobot}). Each
     * line is "key = value"; the generator adds the "# from Robot.java" marks. Works without hardware:
     * the numbers come from {@code t}.
     */
    void simLines(List<String> out, TunableSection t) { }

    /** Checks a parts catalog id for the main device (default: a motor). */
    String checkPart(String id) {
        Motors.get(id);
        return id;
    }

    /** True for the modules whose {@link #looks} the simulator file draws. */
    boolean drawsLooks() {
        return false;
    }

    /** Shape i's name in the robot file: its own name, or (the shapes with none) the device's name, then name_2, name_3... */
    final String shapeName(int i) {
        Shape s = looks.get(i);
        if (s.name != null) return s.name;
        int unnamed = 0;
        for (int k = 0; k < i; k++) if (looks.get(k).name == null) unnamed++;
        return unnamed == 0 ? device : device + "_" + (unnamed + 1);
    }

    /** The shape another shape rides on with {@code on(thisPart)}: the first one (or the generated one). */
    final String firstShapeName() {
        return looks.isEmpty() ? device : shapeName(0);
    }

    /** The first shape that spins (an intake's roller, a launcher's wheel), or the first shape. */
    final String spinningShapeName() {
        for (int i = 0; i < looks.size(); i++) if (looks.get(i).spins()) return shapeName(i);
        return firstShapeName();
    }

    /**
     * What moves this shape, as robot file words ("motor=intake"), or null for nothing. Default: a
     * spinning shape turns with the main motor.
     */
    String driverFor(Shape s, TunableSection t) {
        if (s.spins()) return "motor=" + device;
        throw new IllegalStateException("Robot.java: " + name() + " cannot move a " + s.type + " shape");
    }

    /** The shape lines of {@link #looks} (a box never moves: no driver). */
    final void lookLines(List<String> out, TunableSection t) {
        for (int i = 0; i < looks.size(); i++) {
            Shape s = looks.get(i);
            String parent = s.onModule != null ? s.onModule.firstShapeName() : s.onShape;
            String driver = s.type.equals("box") || s.passive ? null : driverFor(s, t);
            out.add(s.line(shapeName(i), parent, driver));
        }
    }

    /** Notes for INIT (missing Tunables fields...). */
    List<String> notes() {
        List<String> out = new ArrayList<>();
        if (tunables != null && !tunables.note().isEmpty()) out.add(tunables.note());
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // Actions and the driver
    // ---------------------------------------------------------------------------------------

    /**
     * Every command a team can call goes through here. Inside a running {@link Action} the action
     * claims this part (another action using it stops). Outside one, a command is the driver's (a
     * button or stick): the driver always wins, so an action using this part stops.
     */
    final void touch() {
        if (robot != null) robot.runner.touched(this);
    }

    /** The part's telemetry line; each module adds its own. */
    @Override
    public void addTelemetry(RobotTelemetry t) { }

    // "intake_deploy" -> "IntakeDeploy", "Webcam 1" -> "Webcam1": the Tunables section name.
    static String sectionFor(String device) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : device.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.length() == 0 ? "Part" : sb.toString();
    }

    // A position name looked up without caring about capitals: "HIGH", "High" and "high" are the same.
    static String findName(Iterable<String> names, String wanted) {
        for (String n : names) if (n.equalsIgnoreCase(wanted)) return n;
        return null;
    }

    static String fmt(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }
}

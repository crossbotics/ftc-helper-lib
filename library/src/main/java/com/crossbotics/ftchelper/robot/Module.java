package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.core.Subsystem;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
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
    // A servo part's .fullRange(): its servo gets the seller's whole pulse range (see applyPulse).
    boolean fullRange = false;
    // The INIT note when the servo could not change its pulse range ("" = none).
    private String pulseNote = "";
    // optional(): it may be missing from the configuration. missing: it was, at setup (see MissingPart).
    boolean optional = false;
    boolean missing = false;
    // A missing part whose Tunables section stopped its setup: the problem, for the INIT note ("" = none).
    String missingProblem = "";

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
     * ({@link Parts#servo}, e.g. "gobilda-2000-speed"). The simulator then moves a motor or servo like
     * the real one. For a continuous-rotation (CR) servo the id is only a note in the robot file (the
     * simulator turns every CR servo at one speed).
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
            throw new UnsupportedOperationException("Robot.java: " + name() + " cannot take shapes yet (Intake, Positioner, Lift, Wrist, Claw, "
                    + "Launcher and Indexer can). Draw it in the simulator robot file instead (docs/guides/simulator.md).");
        }
        for (Shape s : shapes) {
            if (s.owner != null && s.owner != this) {
                throw new IllegalStateException("Robot.java: one Shape is in both " + s.owner.name() + "'s and " + name()
                        + "'s looks(...): make a new Shape for each part");
            }
            s.owner = this;
            looks.add(s);
        }
        return self();
    }

    /**
     * This part may not be on the robot yet. If its device (the name in {@code add(...)}, e.g.
     * "intake_deploy") is not in the robot configuration, INIT does not stop: it shows one note, and
     * the part is "off" until it is added. Robots are built in steps: write the whole robot in
     * Robot.java now, and each part starts working the day its device is in the configuration.
     *
     * <pre>{@code
     * intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque").optional());   // Robot.java
     * }</pre>
     *
     * <p>While it is off: every command to it is ignored ({@link #isPresent()} is false), its
     * telemetry line is "-", an {@link Action} skips it (and the waits right after it) and goes on,
     * its buttons show "off" on the button map, and an interlock rule with it is off. Its question
     * methods never hold anything up: it counts as done and at its target, and never as running,
     * ready or full. Only its FIRST device decides: if "intake_deploy" is there, every other device
     * of the part must be too (half a part is a wiring mistake: INIT stops and says which).
     * Not for the drive (a robot always drives).
     *
     * @return this module
     */
    public M optional() {
        optional = true;
        return self();
    }

    /** True if it was marked {@link #optional()} in Robot.java. */
    public boolean isOptional() {
        return optional;
    }

    /**
     * True if the part is on the robot: false only for an {@link #optional()} part whose device was
     * not in the configuration at INIT (true before INIT). Use it for code the library cannot see,
     * e.g. {@code if (robot.intakeDeploy.isPresent()) ...}; buttons can use {@code .needs(part)}.
     */
    public boolean isPresent() {
        return !missing;
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

    /**
     * Every pass of the INIT loop, before START (a camera keeps reading pictures here). Must be quick,
     * never wait and never move anything. May add INIT lines. Default: nothing.
     */
    void initLoop(RobotTelemetry t) { }

    /** Every device this module uses, by configuration name (checked: each name once per robot). */
    List<String> deviceNames() {
        return java.util.Collections.singletonList(device);
    }

    /**
     * Stops what an unfinished action told this part to do, safely and without counting as the
     * driver's command (see ActionRunner). Default: nothing (a servo is already where it was sent).
     */
    void halt() { }

    /**
     * Pause (BACK in TeleOp, and at STOP): stop moving now, motors on brake, but remember what it was
     * doing for {@link #resume()}. Default: nothing (a servo already holds its place).
     */
    void pause() { }

    /** Back to what it was doing before {@link #pause()}. Default: nothing. */
    void resume() { }

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

    /**
     * True if the module writes a shape of its own when it has no looks(...) (an intake's roller, a
     * lift's slide), so another shape can ride on it. False for a Positioner, an Indexer, a camera.
     */
    boolean makesDefaultShape() {
        return true;
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

    /** Every spinning shape's name joined with "+" (an intake's rollers and spinners all take pieces in). */
    final String spinningShapeNames() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < looks.size(); i++) {
            if (looks.get(i).spins()) b.append(b.length() == 0 ? "" : "+").append(shapeName(i));
        }
        return b.length() == 0 ? firstShapeName() : b.toString();
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
            String parent = s.parentName(shapeName(i));
            String driver = s.type.equals("box") || s.passive ? null : driverFor(s, t);
            out.add(s.line(shapeName(i), parent, driver));
        }
    }

    /**
     * True if the part works (with a warning) when this one of its devices is not in the configuration:
     * a camera, a piece sensor, a turret's home switch. Default: false (INIT stops without it).
     */
    boolean canDoWithout(String deviceName) {
        return false;
    }

    /** True if its first device is in the configuration (a part with no device of its own always is). */
    final boolean deviceIn(Devices devices) {
        return deviceNames().isEmpty() || devices.has(device);
    }

    /**
     * A servo part's whole move, position 0 to 1, in seconds, from the parts catalog's speed for its
     * {@code part(...)} (at the hub's 5 V; over its whole turn with {@code .fullRange()}), to 2 decimals.
     * NaN without a catalog servo or when the catalog lacks its turn. A starting value for {@code travelSec}.
     */
    final double catalogFullMoveSec() {
        Parts.ServoPart p = Parts.findServo(partId);
        if (p == null) return Double.NaN;
        double s = p.fullMoveSec(fullRange);
        return Double.isNaN(s) ? s : Math.round(s * 100) / 100.0;
    }

    // "a, b, c" (not String.join: the blocking check reads every ".join(" as a thread wait).
    static String listed(java.util.Collection<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String n : items) sb.append(sb.length() == 0 ? "" : ", ").append(n);
        return sb.toString();
    }

    /**
     * Where a starting value came from, for INIT and the tuning menu: "default from the catalog
     * (gobilda-5203-6000)", "default from my-motor (custom)", or "general default (no part in Robot.java)".
     *
     * @param id the part's catalog or custom id, or null when Robot.java names none
     */
    static String defaultSource(String id) {
        if (id == null) return "general default (no part in Robot.java)";
        Motors.Kind k = Motors.find(id);
        Parts.ServoPart s = Parts.findServo(id);
        boolean custom = (k != null && k.custom) || (s != null && s.custom);
        boolean unknown = (k != null && k.custom && !k.known())
                || (s != null && s.custom && (Double.isNaN(s.rangeDeg) || Double.isNaN(s.secPer60Deg)));
        if (unknown) return "general default (the numbers of " + id + " are not known)";
        return custom ? "default from " + id + " (custom)" : "default from the catalog (" + id + ")";
    }

    /** The id this part's starting values come from (its part(...)); a launcher's motor. Null = none. */
    String defaultSourceId() {
        return partId;
    }

    // "Lift: defaults from the catalog (gobilda-5203-435): kP 0.0056, kD 0.00028. Tune them ..." for INIT.
    final String defaultsNote(String id, java.util.Collection<String> values, String next) {
        return name() + ": " + defaultSource(id).replaceFirst("^default ", "defaults ") + ": " + listed(values) + ". " + next;
    }

    /**
     * Which tool finds this field, for the message when Tunables.java says MEASURE (null = the general
     * words). Modules name theirs: a lift's min is the Motor Finder's.
     *
     * @param field the field name, e.g. "min"
     */
    String toolFor(String field) {
        return null;
    }

    /**
     * The INIT note when a servo part's move time was asked for DEFAULT_FROM_CATALOG but no catalog numbers are
     * known (no part, or an unknown one): the general value is used, and how to do better.
     *
     * @param field   e.g. "travelSec"
     * @param general the value used, seconds
     */
    final String generalServoNote(String field, double general) {
        String why = partId == null ? "no part in Robot.java" : "the numbers of " + partId + " are not known";
        String fix = partId == null ? "Add .part(...) (or Parts.customServo(...))" : "Give them with Parts.customServo(...)";
        return name() + ": " + why + ", so general defaults (" + field + " " + exact(general) + " s). " + fix
                + ", or time the move";
    }

    // A starting value for an INIT note, already rounded: up to 6 decimals, no trailing zeros (0.0056, 11.7).
    static String exact(double v) {
        String s = String.format(java.util.Locale.US, "%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    /** Notes for INIT (missing Tunables fields...). */
    List<String> notes() {
        List<String> out = new ArrayList<>();
        if (tunables != null && !tunables.note().isEmpty()) out.add(tunables.note());
        if (!pulseNote.isEmpty()) out.add(pulseNote);
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // A servo's full range (.fullRange() on Claw, Wrist, Positioner, Hood.servo, Turret.servo, Indexer.servo)
    // ---------------------------------------------------------------------------------------

    // The pulse (microseconds) for positions 0 and 1 with fullRange(): the catalog part's own range, or
    // 500-2500 (what goBILDA, REV and Axon servos take) without a part.
    double[] fullPulse() {
        if (Parts.findServo(partId) != null) {
            Parts.ServoPart s = Parts.servoPart(partId);
            return new double[] {s.fullLowUs(), s.fullHighUs()};
        }
        return new double[] {com.crossbotics.ftchelper.hardware.ServoSetup.FULL_PULSE_LOW_US,
                com.crossbotics.ftchelper.hardware.ServoSetup.FULL_PULSE_HIGH_US};
    }

    // INIT: with fullRange(), widens the servo's pulse. A servo that cannot (not on a REV hub) keeps the
    // SDK's 600-2400 us and INIT says so. Without fullRange() nothing is changed (the SDK's normal range).
    final void applyPulse(com.qualcomm.robotcore.hardware.Servo servo) {
        pulseNote = "";
        // Off (an optional part not on the robot): its stand-in servo needs no pulse range, and no note.
        if (!fullRange || missing) return;
        double[] p = fullPulse();
        if (!com.crossbotics.ftchelper.hardware.ServoSetup.setPulseRange(servo, p[0], p[1])) {
            pulseNote = name() + ": the servo \"" + device + "\" cannot change its pulse range (not on a REV hub): "
                    + "fullRange() does nothing, it turns its normal range (600-2400 us)";
        }
    }

    // The simulator robot file's words for a motor: "part=ID" (catalog), its own numbers (Motors.custom), or,
    // for one whose numbers are not known, the fallback catalog motor (simTodo then says so).
    static String motorWords(String id, String fallbackId) {
        Motors.Kind k = Motors.find(id);
        String w = k == null ? "" : k.simWords();
        if (k != null && k.custom && (Double.isNaN(k.countsPerRev) || Double.isNaN(k.freeRpm))) {
            // A custom motor with a number missing: the fallback's numbers fill the gap (its own win).
            w = "part=" + fallbackId + (w.isEmpty() ? "" : ", " + w);
        }
        return w.isEmpty() ? "part=" + fallbackId : w;
    }

    // "   # TODO ..." for a motor or servo whose numbers the robot file had to guess ("" otherwise).
    static String simTodo(String id) {
        Motors.Kind k = Motors.find(id);
        if (k != null && k.custom && !k.known()) return "   # TODO " + id + "'s numbers (Motors.custom in Robot.java)";
        Parts.ServoPart s = Parts.findServo(id);
        if (s != null && s.custom && (Double.isNaN(s.rangeDeg) || Double.isNaN(s.secPer60Deg))) {
            return "   # TODO " + id + "'s numbers (Parts.customServo in Robot.java)";
        }
        return "";
    }

    // The robot file's words for this part's servo: "part=ID", a custom servo's own numbers over the file's
    // pulse range, or the fallback catalog servo for one whose numbers are not known.
    final String servoWords(String id, String fallbackId) {
        Parts.ServoPart s = Parts.findServo(id);
        if (s == null) return "part=" + fallbackId;
        double[] p = fullRange ? fullPulse() : new double[] {com.crossbotics.ftchelper.hardware.ServoSetup.SDK_PULSE_LOW_US,
                com.crossbotics.ftchelper.hardware.ServoSetup.SDK_PULSE_HIGH_US};
        String w = s.simWords(p[0], p[1]);
        return w.isEmpty() ? "part=" + fallbackId : w;
    }

    // ", pwm=500-2500" for the simulator robot file when fullRange() is on ("" otherwise).
    final String pulseWords() {
        if (!fullRange) return "";
        double[] p = fullPulse();
        return fmt(", pwm=%.0f-%.0f", p[0], p[1]);
    }

    // ---------------------------------------------------------------------------------------
    // Interlock rules (see Interlock): the zone this part must keep out of while another part is in its own
    // ---------------------------------------------------------------------------------------

    /**
     * This part's zone for an interlock rule ({@link Interlock}): between two numbers, read from the rule's
     * Tunables section as {@code <part>From} and {@code <part>To} (in the part's own units).
     *
     * <pre>{@code
     * keepApart("TurretLift", turret.zone(), lift.zoneAbove());   // Robot.java
     * }</pre>
     */
    public Zone zone() {
        return zoneOf(Zone.Kind.BETWEEN);
    }

    /** This part's zone from {@code <part>Above} up (to the top of its range). See {@link #zone()}. */
    public Zone zoneAbove() {
        return zoneOf(Zone.Kind.ABOVE);
    }

    /** This part's zone from {@code <part>Below} down. See {@link #zone()}. */
    public Zone zoneBelow() {
        return zoneOf(Zone.Kind.BELOW);
    }

    private Zone zoneOf(Zone.Kind kind) {
        if (!zoneCapable()) {
            throw new IllegalStateException("Robot.java: " + name() + " has no position an interlock can watch (a turret, lift, "
                    + "arm, claw, wrist, positioner or hood has). Use Zone.when(\"words\", () -> ...) for it instead");
        }
        return Zone.of(kind, this);
    }

    /** True for the parts an interlock can watch and hold back. */
    boolean zoneCapable() {
        return false;
    }

    /** The unit of its zone numbers: "deg", "counts", or "" for a servo position (0 to 1). */
    String zoneUnit() {
        return "";
    }

    /** Its range (in zoneUnit), after INIT: the lowest and highest it can be. */
    double zoneMin() {
        return Double.NEGATIVE_INFINITY;
    }

    double zoneMax() {
        return Double.POSITIVE_INFINITY;
    }

    /** True for an angle that repeats every 360 degrees (a turret). */
    boolean zoneWraps() {
        return false;
    }

    /** False while its position is not known (a turret not homed): it then counts as anywhere in its range. */
    boolean zoneKnown() {
        return true;
    }

    /** Why the position is not known, for the Driver Station ("homing"). */
    String zoneUnknownWords() {
        return "not homed";
    }

    /** Where it may be now, {lowest, highest} (a moving servo: anywhere between where it was and where it goes). */
    double[] zoneSweep() {
        return new double[] {zoneMin(), zoneMax()};
    }

    /** Where it was told to go (NaN = nowhere: no target, or moved by hand now). */
    double zoneTarget() {
        return Double.NaN;
    }

    /** The stick's power now (+ = toward zoneMax), 0 when not moved by hand. */
    double zoneManual() {
        return 0;
    }

    /** The range it may move in this loop (an interlock's fence); +/- infinity = free. */
    void zoneFence(double lo, double hi) { }

    /**
     * The fence, and the most power it may use toward higher / lower positions now (0 to 1): while it
     * moves fast toward the fence, the interlock lowers them (0 = brake) so it stops in time (see
     * Interlock's stop distance). Parts with no power of their own (servos) use lo..hi only.
     */
    void zoneFence(double lo, double hi, double upPower, double downPower) {
        zoneFence(lo, hi);
    }

    /**
     * Set every loop by the interlock rules (after the fence): true while its position is not known (a
     * turret not homed, a servo not sent anywhere yet) AND the other part of a rule is in its zone. It
     * must then not move at all (no homing, no stick, no first servo move): moving from "anywhere" could
     * sweep through the other part. Default: nothing (a part whose position is always known).
     */
    void zoneWait(boolean wait) { }

    /** True while it is homing (looking for its switch: it may sweep its whole range). Default false. */
    boolean zoneHoming() {
        return false;
    }

    /**
     * Its top speed in zoneUnit per second at full power (a motor's free speed through its gears), or NaN
     * if not known (then the interlock uses the fastest speed it has measured).
     */
    double zoneTopSpeed() {
        return Double.NaN;
    }

    /**
     * True if it keeps moving for a moment after it is told to stop (a motor: it coasts). Its speed is
     * then measured every loop for the stop distance. A servo does not (it is never sent past the fence,
     * and its zoneSweep already covers its whole move).
     */
    boolean zoneCoasts() {
        return false;
    }

    /** The default safety margin, in zoneUnit. */
    double zoneDefaultMargin() {
        return 0;
    }

    /** True if it is being kept from going further: a target past the fence, or a stick pushing at its edge. */
    boolean zoneHeld(double fenceLo, double fenceHi, double margin) {
        double target = zoneTarget(), manual = zoneManual();
        double[] s = zoneSweep();
        double eps = Math.max(1e-6, margin / 4);
        if (!Double.isNaN(target) && (target > fenceHi + eps || target < fenceLo - eps)) return true;
        return (manual > 0 && s[1] >= fenceHi - margin) || (manual < 0 && s[0] <= fenceLo + margin);
    }

    // ---------------------------------------------------------------------------------------
    // Actions and the driver
    // ---------------------------------------------------------------------------------------

    /**
     * Every command a team can call goes through here. Inside a running {@link Action} the action
     * claims this part (another action using it stops). Outside one, a command is the driver's (a
     * button or stick): the driver always wins, so an action using this part stops. A part that is not
     * on the robot ({@link #optional()}) is never claimed: the action notes that it skipped it.
     */
    final void touch() {
        if (robot == null) return;
        // A part that is not on the robot: the command goes nowhere, and a running action notes the skip.
        if (missing) robot.runner.skipped(this);
        else robot.runner.touched(this);
    }

    /** An action starts using this part (its first command in that action). Default: nothing. */
    void claimedBy(Action a) { }

    /**
     * A few words for an action that used this part and is done, e.g. a launcher's "0 fired" (the
     * Driver Station then says "Fire all: done (0 fired)"). Default: "" (nothing to add).
     */
    String doneWords() {
        return "";
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

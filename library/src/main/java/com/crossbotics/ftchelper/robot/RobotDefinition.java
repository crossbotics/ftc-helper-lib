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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Docs: docs/modules/robot.md. Tutorial: docs/tutorials/start/01-robot-in-five-files.md
 */
public abstract class RobotDefinition {
    private Drive drive = null;
    // The odometry, if Robot.java has an odometry(...) line; the real thing after setup.
    private Odometry odometrySpec = null;
    private com.crossbotics.ftchelper.odometry.PoseTracker tracker = null;
    // A path follower (the pedro module), if Robot.java has a follower(...) line; the real thing after setup.
    private PathFollower.Maker followerMaker = null;
    private PathFollower follower = null;
    private final List<Module<?>> modules = new ArrayList<>();
    private final List<Supplier<Action>> atStart = new ArrayList<>();
    // The team's own parts and per-loop code (addOwn, everyLoop), updated after the library's parts.
    private final List<com.crossbotics.ftchelper.core.Subsystem> own = new ArrayList<>();
    // Everything the library itself put in all() at setup. Anything else in all() (addOwn, everyLoop, or
    // all().add(...)) is the team's: pause stops it too (see pauseAll).
    private final Set<com.crossbotics.ftchelper.core.Subsystem> libraryParts =
            Collections.newSetFromMap(new java.util.IdentityHashMap<com.crossbotics.ftchelper.core.Subsystem, Boolean>());
    // INIT warnings about Tunables sections no part reads that look meant for a part (see sectionWarnings).
    private final List<String> sectionWarnings = new ArrayList<>();
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
    // Where the goal is (camera first, odometry backup) and whole-robot aim. Made at setup.
    private Aim aim = null;
    // For the simulator: the chassis box and the whole robot's height (inches; NaN = not given), fixed shapes.
    private double[] chassisIn = null;
    private double heightIn = Double.NaN;
    private final List<Shape> fixed = new ArrayList<>();
    /** Runs the actions; one per robot. */
    final ActionRunner runner = new ActionRunner();
    // The interlock rules (keepApart lines), run every loop after the actions, before the parts.
    final Interlock.Rules interlocks = new Interlock.Rules();
    // True in an autonomous (Auto sets it before setup): a turret's angle is not carried over then.
    boolean autonomous = false;

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
     * A path follower from another library, e.g. {@code follower(Pedro.follower(Constants.foresight))} with
     * the ftc-helper-lib "pedro" module. It needs an {@link #odometry} line too (it uses the same pose).
     */
    protected final void follower(PathFollower.Maker maker) {
        followerMaker = maker;
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
     * An interlock rule: these two parts are never in their zones at the same time (see {@link Interlock}).
     * Write it after the parts' {@code add} lines; the numbers go in {@code Tunables.<ruleName>}. Any
     * number of rules, between any parts; a robot with none works as before.
     *
     * <pre>{@code
     * keepApart("TurretLift", turret.zone(), lift.zoneAbove());   // turret out of the back while the lift is up
     * keepApart("WristLift", wrist.zone(), lift.zoneBelow());     // wrist folded before the lift goes low
     * keepApart("ClawLift", claw.zone(), lift.zoneAbove());       // claw closed above a height
     * }</pre>
     *
     * @param ruleName the rule's name, and its Tunables section (e.g. "TurretLift")
     * @param first    one part's zone, e.g. {@code turret.zone()}; it goes first if both want in at once
     * @param second   the other part's zone, e.g. {@code lift.zoneAbove()}, or {@link Zone#when}
     * @return the rule ({@link Interlock#status()} says what it holds)
     */
    protected final Interlock keepApart(String ruleName, Zone first, Zone second) {
        if (ruleName == null || ruleName.trim().isEmpty()) throw new IllegalArgumentException("Robot.java: keepApart needs a rule name");
        if (first == null || second == null) throw new IllegalArgumentException("Robot.java: keepApart(\"" + ruleName + "\", ...) needs two zones");
        if (first.part != null && first.part == second.part) {
            throw new IllegalArgumentException("Robot.java: keepApart(\"" + ruleName + "\"): both zones are " + first.part.name()
                    + "'s; a rule keeps two different parts apart");
        }
        if (first.part == null && second.part == null) {
            throw new IllegalArgumentException("Robot.java: keepApart(\"" + ruleName + "\"): two conditions; at least one zone must be a part's");
        }
        for (Interlock r : interlocks.rules) {
            if (r.name().equals(ruleName)) throw new IllegalArgumentException("Robot.java: two interlock rules are called \"" + ruleName + "\"");
        }
        Interlock rule = new Interlock(ruleName, first, second);
        interlocks.add(rule);
        return rule;
    }

    /** The interlock rules from Robot.java (read only). */
    public final List<Interlock> interlocks() {
        describe();
        return Collections.unmodifiableList(interlocks.rules);
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
     * Your own part, run with the library's parts: its {@code update()} every loop after START (after
     * the library's parts, so it sees this loop's commands), and its {@code stop()} at STOP and whenever
     * the robot pauses (BACK, see {@link #pauseAll()}): so a motor in it stops with the rest. Nothing runs
     * in INIT (make its hardware in its constructor, never move anything there). While paused its
     * {@code update()} does not run; after {@link #resume()} it runs again.
     *
     * <p>Call it in {@code define()} for a part that needs no hardware, or after INIT has set the robot up
     * (in {@code Controls.java}'s {@code buttons(...)} or an autonomous's {@code steps(...)}, which have
     * the {@code hardwareMap}).
     *
     * <pre>{@code
     * // Controls.java, in buttons(robot, g1, g2):
     * robot.addOwn(new Bucket(hardwareMap));   // class Bucket implements Subsystem { update(), stop() }
     * }</pre>
     *
     * <p>A second motor on a lift or an arm needs none of this: {@code Lift.slide("lift_left").alsoMotor("lift_right")}.
     *
     * @param part your part (a class that {@code implements Subsystem}); not null, added once
     * @return the same part
     */
    public final <T extends com.crossbotics.ftchelper.core.Subsystem> T addOwn(T part) {
        if (part == null) throw new IllegalArgumentException("addOwn(null): give it your part");
        if (part instanceof Module) throw new IllegalArgumentException("addOwn: " + part.name() + " is a library part: write add(...) in Robot.java's define()");
        if (own.contains(part)) throw new IllegalStateException("addOwn: " + part.name() + " is added twice");
        own.add(part);
        if (all != null) all.add(part);   // after setup: in the loop from now on
        return part;
    }

    /**
     * Code of your own that runs once every loop after START, after the library's parts (not in INIT, not
     * while paused). It must be quick and never wait (docs/guides/non-blocking.md). For code that moves a
     * motor use {@link #addOwn} instead: pause and STOP call its {@code stop()}, and a Runnable has none.
     *
     * <pre>{@code
     * robot.everyLoop(() -> lastLiftHeight = robot.lift.position());
     * }</pre>
     *
     * @param code what to run, e.g. a lambda {@code () -> ...}
     */
    public final void everyLoop(final Runnable code) {
        if (code == null) throw new IllegalArgumentException("everyLoop(null): give it the code to run");
        addOwn(new com.crossbotics.ftchelper.core.Subsystem() {
            @Override public void update() {
                code.run();
            }

            @Override public String name() {
                return "everyLoop";
            }
        });
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
        // A misspelled Tunables field stops INIT here, before anything is made (see TunableFields).
        checkTunablesNames(devices);
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
        // The path follower right after it: it reads this loop's pose.
        if (followerMaker != null) {
            if (tracker == null) throw new IllegalStateException("Robot.java: follower(...) needs an odometry(...) line too");
            follower = followerMaker.make(devices, this);
            if (follower == null) throw new IllegalStateException("Robot.java: follower(...) made nothing (null)");
            all.add(follower);
        }
        all.add(runner);
        // The interlock rules right after the actions: they see this loop's commands and set each part's
        // fence before the parts move.
        all.add(interlocks);
        // Tunables.Aim (optional): made before the parts, which ask it where the goal is.
        aim = new Aim(this, TunableSection.of(tunablesClass, "Aim"));
        // Cameras first, so the parts that use them see this loop's picture.
        List<Module<?>> ordered = new ArrayList<>();
        for (Module<?> m : modules) if (m instanceof Camera) ordered.add(m);
        for (Module<?> m : modules) if (!(m instanceof Camera)) ordered.add(m);
        for (Module<?> m : ordered) {
            // A part marked optional() whose device is not in the configuration is set up "off" (see
            // MissingPart), with everything inside it (a launcher's feeder and hood).
            boolean off = m.optional && !m.deviceIn(devices);
            for (Module<?> child : m.children()) buildOne(child, devices, off);
            buildOne(m, devices, off);
            // Off: in the list for its "-" telemetry line only; never updated or stopped. Its numbers
            // are left out of the tuning menu (changing them would do nothing); shown again when it is there.
            all.add(m.missing ? new MissingPart.Absent(m) : m);
            for (Module<?> child : m.children()) Tunable.hideGroup(child.name(), child.missing);
            Tunable.hideGroup(m.name(), m.missing);
        }
        // The other sections (Tunables.Auto...): in the tuning menu, and their saved values used now,
        // before the autonomous reads them to build its steps.
        // The interlock rules' numbers (read once: not changeable live, see Interlock).
        for (Interlock r : interlocks.rules) r.build(TunableSection.of(tunablesClass, r.name()), this);
        // Drive, Odometry (scales, "checked"), Buttons (gesture times), Aim (goal point), every part and rule.
        Set<String> used = usedSections();
        usedAtSetup = used;
        otherSections = new OtherSections(tunablesClass, used);
        sectionWarnings.clear();
        sectionWarnings.addAll(sectionProblems(used));
        // Everything so far is the library's; the team's own parts come last (they see this loop's commands).
        libraryParts.addAll(all.all());
        for (com.crossbotics.ftchelper.core.Subsystem s : own) all.add(s);
        return this;
    }

    // Tunables sections no part reads that look meant for one: named like a part's kind (Tunables.Launcher
    // while the launcher's device is "shooter", so it reads Tunables.Shooter), or spelled almost like a
    // section a part reads (Tunables.Liftleft). Their numbers would be quietly ignored. A warning, not a
    // stop: a section no part reads may be the team's own (Tunables.Auto); the part's own required numbers
    // (a lift's min and max, a claw's positions) already stop INIT when its real section is missing.
    private List<String> sectionProblems(Set<String> used) {
        List<String> out = new ArrayList<>();
        if (tunablesClass == null) return out;
        Map<String, Module<?>> byKind = new LinkedHashMap<>();
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) byKind.put(c.getClass().getSimpleName(), c);
            byKind.put(m.getClass().getSimpleName(), m);
        }
        for (Class<?> section : tunablesClass.getDeclaredClasses()) {
            String n = section.getSimpleName();
            if (used.contains(n)) continue;
            Module<?> kind = byKind.get(n);
            if (kind != null) {
                out.add("Tunables." + n + " is not read by any part: your " + n.toLowerCase(java.util.Locale.US) + "'s section is Tunables."
                        + kind.name() + " (its device name \"" + kind.device + "\" in CamelCase). Move the numbers there");
                continue;
            }
            String like = com.crossbotics.ftchelper.util.Spelling.closest(n, used);
            if (like != null) {
                out.add("Tunables." + n + " is not read by any part: did you mean Tunables." + like + "? (capitals count)");
            }
        }
        return out;
    }

    /**
     * Checks {@code Tunables.java} the way INIT does, without a robot (the simulator's {@code :sim:fromRobot}
     * runs it): a field no part reads (INIT stops on it) and a section that looks meant for a part but is
     * read by none (INIT warns). Every part counts as on the robot. Empty when all is fine.
     *
     * @return one line per problem, e.g. "Tunables.Launcher is not read by any part: ..."
     */
    public final List<String> checkTunables() {
        describe();
        List<String> out = new ArrayList<>();
        Devices all = new Devices() {
            @Override public <T> T get(Class<? extends T> type, String name) {
                throw new UnsupportedOperationException("no devices");
            }

            @Override public <T> T tryGet(Class<? extends T> type, String name) {
                return null;
            }

            @Override public boolean has(String name) {
                return true;
            }
        };
        try {
            checkTunablesNames(all);
        } catch (IllegalStateException e) {
            out.add(e.getMessage());
        }
        out.addAll(sectionProblems(usedSections()));
        return out;
    }

    // Every section a part reads (the same set setup builds).
    private Set<String> usedSections() {
        Set<String> used = new LinkedHashSet<>();
        for (Interlock r : interlocks.rules) used.add(r.name());
        used.add("Drive");
        used.add("Odometry");
        used.add("Buttons");
        used.add("Aim");
        for (Module<?> m : modules) {
            for (Module<?> c : m.children()) used.add(c.name());
            used.add(m.name());
        }
        return used;
    }

    // Every section the robot reads: a field that no part reads is a typo (or a leftover): stop with its name
    // and a "did you mean". Two parts sharing a section may hold each other's fields. A missing optional()
    // part's section is not checked (it may be absent or unfinished until the part is on the robot).
    private void checkTunablesNames(Devices devices) {
        Map<String, TunableFields.Allowed> sections = new LinkedHashMap<>();
        Set<String> offSections = new LinkedHashSet<>();
        for (String key : new String[] {"Drive", "Odometry", "Aim", "Buttons"}) sections.put(key, TunableFields.forSection(key));
        for (Module<?> m : modules) {
            boolean off = m.optional && !m.deviceIn(devices);
            List<Module<?>> group = new ArrayList<>(m.children());
            group.add(m);
            for (Module<?> g : group) {
                if (off || (g.optional && !g.deviceIn(devices))) offSections.add(g.name());
                else allow(sections, g.name(), TunableFields.forModule(g));
            }
        }
        for (Interlock r : interlocks.rules) allow(sections, r.name(), TunableFields.forInterlock(r));
        for (Map.Entry<String, TunableFields.Allowed> e : sections.entrySet()) {
            if (offSections.contains(e.getKey())) continue;   // shared with a missing part: lenient, like its setup
            TunableFields.check(TunableSection.of(tunablesClass, e.getKey()), e.getValue());
        }
    }

    private static void allow(Map<String, TunableFields.Allowed> sections, String name, TunableFields.Allowed a) {
        TunableFields.Allowed had = sections.get(name);
        if (had == null) sections.put(name, a);
        else had.add(a);
    }

    private void buildOne(Module<?> m, Devices devices, boolean parentOff) {
        m.robot = this;
        m.tunables = TunableSection.of(tunablesClass, m.name());
        m.missing = parentOff || (m.optional && !m.deviceIn(devices));
        if (!m.missing) {
            try {
                m.build(devices, m.tunables);
            } catch (IllegalArgumentException e) {
                // Its first device is not in the configuration: the usual message, and how to leave it off.
                if (m.deviceIn(devices)) throw e;
                throw new IllegalArgumentException(e.getMessage() + " Not on the robot yet? Add .optional() to "
                        + m.name() + "'s line in Robot.java: the part is then off until \"" + m.device
                        + "\" is in the configuration (docs/modules/robot.md#parts-not-on-the-robot-yet).", e);
            }
            return;
        }
        // Not on the robot: set up on stand-in devices, so every command and question still works. Its
        // Tunables section may be unfinished: no "must write max" stop for a part that is not there.
        m.tunables.lenient = true;
        try {
            m.build(MissingPart.devices(), m.tunables);
        } catch (RuntimeException e) {
            // A mistake in its numbers: set it up on the library's defaults now, and say so at INIT.
            m.missingProblem = e.getMessage() == null ? e.toString() : e.getMessage();
            m.tunables = TunableSection.of(null, m.name());
            m.tunables.lenient = true;
            m.build(MissingPart.devices(), m.tunables);
        }
    }

    /**
     * The INIT note for a part that is not on the robot, e.g. "intake_deploy not in the configuration:
     * IntakeDeploy is off (optional part)". A part inside it (a launcher's feeder) gets none of its own.
     */
    private static String missingNote(Module<?> m) {
        String note = "\"" + m.device + "\" not in the configuration: " + m.name() + " is off (optional part)";
        return m.missingProblem.isEmpty() ? note : note + ". Fix its Tunables before adding it: " + m.missingProblem;
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

    /**
     * Where the goal is (the camera first, the odometry as a backup), whole-robot aim and how far off
     * the aim is. Null before setup. See {@link Aim}.
     */
    public final Aim aim() {
        return aim;
    }

    /**
     * Whole-robot aim (for a robot with no turret): while on, TeleOp turns the robot toward the goal and
     * the driver keeps forward and sideways (the turn stick still wins). One line in Controls.java:
     * {@code g1.rb.toggle(robot.aimMode());}. Off at START. Numbers: {@code Tunables.Aim} (see {@link Aim}).
     */
    public final Mode aimMode() {
        if (aim == null) throw new IllegalStateException("robot.aimMode() is ready after setup (use it in Controls.java's buttons(...))");
        return aim.mode();
    }

    /** The path follower as running (null before setup, or with no follower(...) line). */
    public final PathFollower follower() {
        return follower;
    }

    /** True if Robot.java has a follower(...) line. */
    public final boolean hasFollower() {
        describe();
        return followerMaker != null;
    }

    /**
     * Makes only the odometry from the odometry(...) line, for a tool that runs without the rest of the
     * robot (e.g. Pedro's tuners through the pedro module). Your code calls update() on it each loop.
     */
    public final com.crossbotics.ftchelper.odometry.PoseTracker makeOdometry(HardwareMap hardwareMap) {
        return makeOdometry(Devices.of(hardwareMap));
    }

    /** {@link #makeOdometry(HardwareMap)} with any {@link Devices} (tests use pretend ones). */
    public final com.crossbotics.ftchelper.odometry.PoseTracker makeOdometry(Devices devices) {
        describe();
        if (odometrySpec == null) throw new IllegalStateException("Robot.java: there is no odometry(...) line");
        return odometrySpec.build(devices, TunableSection.of(tunablesClass, "Odometry"), drive);
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

    // ----------------------------- "set pose here" -----------------------------

    // How to set the pose from TeleOp, for the "pose was never set" warning (ControlMap fills it in:
    // "hold G1 START 1 s at the reset spot"). Null = only from code.
    String resetPoseHint = null;
    // The alliance a TeleOp switch made the pose "not set" for (see ControlMap.poseAfterSwitch), null = none.
    Alliance poseUnsetBy = null;

    /**
     * Sets the odometry's pose to the reset spot in {@code Tunables.Odometry}: {@code resetX},
     * {@code resetY} (inches, as seen from your drivers' wall: x to the right of the field's centre
     * line, y from the centre toward the far wall) and {@code resetHeading} (degrees, 0 = facing away
     * from you, counterclockwise +). Written for BLUE; RED uses it mirrored left-right (x and the
     * heading flip), like autonomous's start spot, unless {@code Tunables.Odometry.mirrorForRed = false}
     * (a field turned half a turn: the same numbers from the red wall).
     *
     * <p>Use it when autonomous was stopped early or did not run: park the robot on the spot (e.g. the
     * start tile against your wall), then call it. TeleOp's "set pose here" button (hold G1 START 1 s)
     * does it for you. Never waits.
     *
     * <pre>{@code
     * public static class Odometry {
     *     public static double resetX = -36, resetY = -63, resetHeading = 0;   // our start tile, facing the field
     * }
     * }</pre>
     *
     * @return true if it was set; false (nothing changed) with no odometry or no {@code resetX}/{@code resetY}
     */
    public final boolean resetPose() {
        describe();
        TunableSection t = TunableSection.of(tunablesClass, "Odometry");
        if (tracker == null || !t.has("resetX") || !t.has("resetY")) return false;
        resetPose(t.number("resetX", 0), t.number("resetY", 0), t.has("resetHeading") ? t.number("resetHeading", 0) : 0);
        return true;
    }

    /**
     * Sets the odometry's pose to this spot, written for BLUE (mirrored for RED as in {@link #resetPose()}).
     * Never waits.
     *
     * @param xIn        inches to the right of the field's centre line, seen from your drivers' wall
     * @param yIn        inches from the centre toward the far wall (negative = your half)
     * @param headingDeg 0 = facing away from you, counterclockwise +
     * @throws IllegalStateException if the robot has no odometry (or before setup)
     */
    public final void resetPose(double xIn, double yIn, double headingDeg) {
        if (tracker == null) throw new IllegalStateException("resetPose: Robot.java has no odometry(...) line (or setup has not run)");
        double[] p = resetSpotFor(alliance, xIn, yIn, headingDeg);
        tracker.setPose(new com.crossbotics.ftchelper.util.FieldPose(p[0], p[1], p[2]));
    }

    // A spot written for BLUE, as used on this alliance: {x, y, heading}.
    final double[] resetSpotFor(Alliance a, double x, double y, double h) {
        if (a == Alliance.RED && resetMirrorsForRed()) {
            x = -x;
            h = -h;
        }
        // (+ 0.0 turns a "-0" from mirroring 0 into a plain 0 on the screen)
        return new double[] {x + 0.0, y + 0.0, com.crossbotics.ftchelper.util.Angles.wrapDegrees(h) + 0.0};
    }

    // RED mirrors the spot when mirrorForRed() says so (one setting for the whole robot).
    final boolean resetMirrorsForRed() {
        return mirrorForRed();
    }

    // Which mirroring the reset spot uses for RED, and where that choice comes from, for INIT:
    // "mirrored, x and heading flip (Tunables.Aim.mirrorForRed = true)".
    final String resetMirrorWords() {
        return (mirrorForRed() ? "mirrored, x and heading flip (" : "the same numbers from the red wall (") + mirrorSource() + ")";
    }

    // ----------------------------- mirrorForRed: one setting -----------------------------

    // The autonomous's own mirrorForRed() (when it overrides it), set by Auto at INIT; null = none.
    Boolean autoMirror = null;
    // The last autonomous's own mirrorForRed() per robot class, for the TeleOp after it.
    static final Map<String, Boolean> LAST_AUTO_MIRROR = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Forgets everything the library keeps from one program to the next in one robot app, as if the
     * robot had just been turned on: the alliance, pose and report from the last autonomous
     * ({@link com.crossbotics.ftchelper.match.MatchMemory}), each turret's last angle ({@code keepAngle}),
     * each launcher's last trim, the last autonomous's {@code mirrorForRed()}, and the numbers tuned in
     * the tuning menu (in memory; a saved settings file stays). WHY: a test (a JUnit test, or the
     * simulator running several scenario files) must start the same way each time, whatever ran before
     * it. Never call it in robot code: the next program would lose what the last one left for it.
     *
     * <pre>
     * RobotDefinition.freshRobotApp();   // in a test, before each robot
     * </pre>
     */
    public static void freshRobotApp() {
        com.crossbotics.ftchelper.match.MatchMemory.clear();
        Turret.forgetAngles();
        synchronized (Launcher.lastTrim) {
            Launcher.lastTrim.clear();
        }
        LAST_AUTO_MIRROR.clear();
        com.crossbotics.ftchelper.tuning.Tunable.forgetAllForTests();
    }

    /**
     * True when RED uses BLUE's numbers mirrored left-right (x and headings flip: a mirrored field like
     * DECODE 2025-26); false when RED uses the same numbers from the red wall (a field turned half a
     * turn, like BIOBUZZ 2026-27). ONE setting for the whole robot: the autonomous's routes and start
     * spot, the aim's goal point, and TeleOp's reset spot all use it. Where it comes from, first found:
     * {@code Tunables.Aim.mirrorForRed}, {@code Tunables.Odometry.mirrorForRed}, your autonomous's
     * {@code mirrorForRed()} (in a TeleOp: the last autonomous's), else true. INIT shows the choice.
     */
    public final boolean mirrorForRed() {
        describe();
        TunableSection a = TunableSection.of(tunablesClass, "Aim");
        if (a.has("mirrorForRed")) return a.flag("mirrorForRed", true);
        TunableSection o = TunableSection.of(tunablesClass, "Odometry");
        if (o.has("mirrorForRed")) return o.flag("mirrorForRed", true);
        if (autoMirror != null) return autoMirror;
        Boolean last = LAST_AUTO_MIRROR.get(getClass().getName());
        return last == null || last;
    }

    /** Where {@link #mirrorForRed()} comes from, with its value, e.g. "Tunables.Aim.mirrorForRed = false". */
    final String mirrorSource() {
        describe();
        boolean v = mirrorForRed();
        if (TunableSection.of(tunablesClass, "Aim").has("mirrorForRed")) return "Tunables.Aim.mirrorForRed = " + v;
        if (TunableSection.of(tunablesClass, "Odometry").has("mirrorForRed")) return "Tunables.Odometry.mirrorForRed = " + v;
        if (autoMirror != null) return "your autonomous's mirrorForRed() = " + v;
        if (LAST_AUTO_MIRROR.containsKey(getClass().getName())) return "the last autonomous's mirrorForRed() = " + v;
        return "mirrorForRed not written: default true";
    }

    // The Tunables sections this robot reads (set at INIT), for the out-of-range notes.
    private Set<String> usedAtSetup = null;

    // INIT warnings: two places that say different things, numbers that only work in pairs, and numbers
    // outside their allowed range (Claw.open = 1.2 for a servo).
    final List<String> settingProblems() {
        describe();
        List<String> out = new ArrayList<>();
        if (usedAtSetup != null) out.addAll(com.crossbotics.ftchelper.tuning.Tunable.outsideRangeNotes(usedAtSetup));
        TunableSection a = TunableSection.of(tunablesClass, "Aim");
        TunableSection o = TunableSection.of(tunablesClass, "Odometry");
        if (a.has("goalX") != a.has("goalY")) {
            out.add("Tunables.Aim: goalX and goalY go together; with only " + (a.has("goalX") ? "goalX" : "goalY")
                    + " there is no goal point (the odometry backup is off)");
        }
        // Two cameraOffsets: Tunables.Aim's turns the whole robot; a turret reads its own section's.
        if (a.has("cameraOffset")) {
            for (Module<?> m : modules) {
                if (m instanceof Turret && !TunableSection.of(tunablesClass, m.name()).has("cameraOffset")) {
                    out.add("Tunables.Aim.cameraOffset is for whole-robot aim; the turret reads Tunables." + m.name()
                            + ".cameraOffset (not written: 0). Write it there if the turret needs it");
                }
            }
        }
        if (o.has("resetX") != o.has("resetY") || (o.has("resetHeading") && !(o.has("resetX") && o.has("resetY")))) {
            out.add("Tunables.Odometry: the reset spot needs resetX and resetY (resetHeading is optional); "
                    + "without both there is no reset spot, so set pose here does nothing");
        }
        if (a.has("mirrorForRed") && o.has("mirrorForRed") && a.flag("mirrorForRed", true) != o.flag("mirrorForRed", true)) {
            out.add("Tunables.Aim.mirrorForRed and Tunables.Odometry.mirrorForRed differ: the Aim one is used for everything. "
                    + "Write it once (delete the Odometry one)");
        }
        boolean tun = a.has("mirrorForRed") || o.has("mirrorForRed");
        if (tun && autoMirror != null && autoMirror != mirrorForRed()) {
            out.add("your autonomous's mirrorForRed() is " + autoMirror + " but " + mirrorSource() + ": the routes use "
                    + autoMirror + ", the aim and reset spot " + mirrorForRed() + ". Write it in one place");
        }
        return out;
    }

    /**
     * The reset spot for this alliance in words, e.g. "x 36, y -63 in, heading 0 (RED, mirrored)",
     * or null when {@code Tunables.Odometry} has no {@code resetX}/{@code resetY}.
     */
    final String resetSpotWords() {
        describe();
        TunableSection t = TunableSection.of(tunablesClass, "Odometry");
        if (!t.has("resetX") || !t.has("resetY")) return null;
        double[] p = resetSpotFor(alliance, t.number("resetX", 0), t.number("resetY", 0),
                t.has("resetHeading") ? t.number("resetHeading", 0) : 0);
        String how = alliance == Alliance.RED ? (resetMirrorsForRed() ? ", mirrored" : ", same numbers") : "";
        return Module.fmt("x %.0f, y %.0f in, heading %.0f (%s%s)", p[0], p[1], p[2], alliance, how);
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
     * Robot.java against a robot configuration (the Hardware Check shows it): one line for each device
     * Robot.java names that is NOT in the configuration, and what that means. Empty when all are there.
     *
     * <pre>{@code
     * "lift" (Lift): NOT in the configuration: INIT will stop. Add it, or add .optional() to Lift in Robot.java
     * "intake_deploy" (IntakeDeploy): not in the configuration: optional, so the part is off (fine)
     * }</pre>
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     */
    public final List<String> checkConfiguration(HardwareMap hardwareMap) {
        return checkConfiguration(Devices.of(hardwareMap));
    }

    /** {@link #checkConfiguration(HardwareMap)} with any {@link Devices} (tests use pretend ones). */
    public final List<String> checkConfiguration(Devices devices) {
        describe();
        List<String> out = new ArrayList<>();
        for (String d : drive.motorNames()) {
            if (!devices.has(d)) out.add(stops(d, "Drive", "a robot always needs its drive"));
        }
        if (drive.imuName != null && !devices.has(drive.imuName)) {
            out.add(stops(drive.imuName, "Drive", "or .noImu() on the drive in Robot.java"));
        }
        if (odometrySpec != null) {
            for (String d : odometrySpec.deviceNames()) {
                if (!devices.has(d)) out.add(stops(d, "Odometry", "or leave the odometry(...) line out until it is on the robot"));
            }
        }
        for (Module<?> m : modules) {
            if (m.optional && !m.deviceIn(devices)) {
                out.add(offLine(m));
                continue;
            }
            List<Module<?>> group = new ArrayList<>(m.children());
            group.add(m);
            for (Module<?> g : group) {
                if (g != m && g.optional && !g.deviceIn(devices)) {
                    out.add(offLine(g));
                    continue;
                }
                for (String d : g.deviceNames()) {
                    if (devices.has(d)) continue;
                    if (g.canDoWithout(d)) {
                        out.add("\"" + d + "\" (" + g.name() + "): not in the configuration: INIT goes on without it ("
                                + g.name() + " warns at INIT)");
                    } else {
                        out.add(stops(d, g.name(), d.equals(g.device) ? "or add .optional() to " + g.name() + " in Robot.java" : null));
                    }
                }
            }
        }
        return out;
    }

    private static String stops(String device, String part, String or) {
        return "\"" + device + "\" (" + part + "): NOT in the configuration: INIT will stop. Add it" + (or == null ? "" : ", " + or);
    }

    private static String offLine(Module<?> m) {
        return "\"" + m.device + "\" (" + m.name() + "): not in the configuration: optional, so the part is off (fine)";
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
            if (m.missing) continue;   // not on the robot: nothing to pause
            for (Module<?> c : m.children()) {
                try { c.pause(); } catch (RuntimeException e) { if (first == null) first = e; }
            }
            try { m.pause(); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        // The team's own parts (addOwn, everyLoop, or all().add(...)): they are not updated while paused,
        // so a power they sent would stay on. Stop each one now (its stop() puts its motors to 0).
        for (com.crossbotics.ftchelper.core.Subsystem s : all.all()) {
            if (libraryParts.contains(s)) continue;
            try { s.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }

    /** Ends {@link #pauseAll()}: every part goes back to what it was doing. */
    public final void resume() {
        if (!paused) return;
        paused = false;
        runner.resume();
        for (Module<?> m : modules) {
            if (m.missing) continue;
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
            for (Module<?> m : modules) {
                if (m.missing) out.add(missingNote(m));
                else for (Module<?> c : m.children()) if (c.missing) out.add(missingNote(c));
            }
            return out;
        }
        if (driveSection != null && !driveSection.note().isEmpty()) out.add(driveSection.note());
        for (Module<?> m : modules) {
            // A part that is not on the robot: one note, not its Tunables notes.
            if (m.missing) {
                out.add(missingNote(m));
                continue;
            }
            for (Module<?> c : m.children()) {
                if (c.missing) out.add(missingNote(c));
                else out.addAll(c.notes());
            }
            out.addAll(m.notes());
        }
        if (built) for (Interlock r : interlocks.rules) out.add(r.initLine());
        return out;
    }

    /** The INIT notes of the parts that are off (not on the robot), see {@link #missingNote}. */
    final List<String> offNotes() {
        List<String> out = new ArrayList<>();
        for (Module<?> m : modules) {
            if (m.missing) {
                out.add(missingNote(m));
                continue;
            }
            for (Module<?> c : m.children()) if (c.missing) out.add(missingNote(c));
        }
        return out;
    }

    /**
     * Shows {@link #notes()} at INIT (TeleOp and autonomous). In the COMPETITION profile a part that is
     * off is a loud warning at the top ("!!"), not a quiet note: at an event a misspelled or lost
     * configuration name would otherwise only show as a note, and the part would not work in the match.
     * PRACTICE keeps the note (a part not built yet is normal there).
     */
    final void showNotes(com.crossbotics.ftchelper.telemetry.RobotTelemetry t) {
        boolean loud = !com.crossbotics.ftchelper.core.Profile.current().isPractice();
        List<String> off = offNotes();
        for (String n : notes()) {
            // At the very top of the screen, above every other warning: at an event this is the one to see.
            if (loud && off.contains(n)) t.warnFirst(offWarning(n));
            else t.text(n);
        }
        for (String w : sectionWarnings) t.warn(w);
        for (String w : settingProblems()) t.warn(w);
    }

    /** INIT warnings about misnamed Tunables sections (empty when none); see {@link #checkTunables()}. */
    public final List<String> sectionWarnings() {
        return Collections.unmodifiableList(sectionWarnings);
    }

    // "COMPETITION: IntakeDeploy is OFF ..." from the note "\"intake_deploy\" not in the configuration: IntakeDeploy is off ...".
    static String offWarning(String note) {
        return "COMPETITION: " + note.replace(" is off (optional part)", " is OFF (optional part)")
                + ". On the robot? Check its name in the configuration, then INIT again";
    }

    /** The Tunables class in use, or null if there is none. */
    public final Class<?> tunablesClass() {
        describe();
        return tunablesClass;
    }

    // ----------------------------- the library's -----------------------------

    /** Every pass of the INIT loop (the OpMode classes call this): each module's quick INIT work and lines. */
    final void initLoop(com.crossbotics.ftchelper.telemetry.RobotTelemetry t) {
        if (!built) return;
        for (Module<?> m : modules) if (!m.missing) m.initLoop(t);
    }

    /** Right after START: the modules' start (an intake "on at the start") and the atStart actions. */
    final void started() {
        // The interlock rules first: every part gets its fence (and "wait") from where the parts are now,
        // BEFORE any START command is sent (a hood going to its startAngle, a turret homing, an atStart
        // action). A command into a zone the other part is in then waits at the fence like any other.
        interlocks.update();
        for (Module<?> m : modules) {
            if (m.missing) continue;   // not on the robot: nothing to start
            for (Module<?> c : m.children()) c.start();
            m.start();
        }
        for (Supplier<Action> a : atStart) runner.start(a.get());
    }

    /**
     * For JUnit tests only (with {@link com.crossbotics.ftchelper.testkit.PretendDevices}): what START does
     * in an OpMode (each part's start, like a servo turret going to its start angle, and the
     * {@link #atStart} actions). The OpMode classes do this themselves: never call it in robot code.
     *
     * <pre>{@code
     * robot.setup(devices);     // INIT
     * robot.startForTests();    // START
     * robot.all().update();     // one loop
     * }</pre>
     */
    public final void startForTests() {
        if (!built) throw new IllegalStateException("startForTests(): call setup(devices) first (that is INIT)");
        started();
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
                plan.sourcePart = c;
                c.suggestButtons(plan);
            }
            plan.source = m.name();
            plan.sourcePart = m;
            m.suggestButtons(plan);
        }
        plan.source = null;
        plan.sourcePart = null;
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
        // A camera on a turret turns with it in the simulator too (Camera's line gets on=TURRET), and on
        // a robot with one turret the launcher's flywheel rides it (the shots go where it points).
        Turret onlyTurret = null;
        int turrets = 0;
        for (Module<?> m : modules) if (m instanceof Turret) { onlyTurret = (Turret) m; turrets++; }
        for (Module<?> m : modules) if (m instanceof Launcher) ((Launcher) m).simOnTurret = turrets == 1 ? onlyTurret.device : null;
        for (Module<?> m : modules) {
            if (!(m instanceof Camera)) continue;
            ((Camera) m).simOnTurret = null;
            for (Module<?> t : modules) if (t instanceof Turret && ((Turret) t).carries((Camera) m)) ((Camera) m).simOnTurret = t.device;
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
        sections.add("Aim");   // read by aim(): a part must not use that name
        for (Interlock r : interlocks.rules) sections.add(r.name());
        for (Module<?> m : modules) {
            List<Module<?>> group = new ArrayList<>(m.children());
            group.add(m);
            for (Module<?> g : group) {
                for (String d : g.deviceNames()) {
                    if (!devices.add(d)) throw new IllegalStateException("Robot.java: the device \"" + d + "\" is used twice");
                }
                if (!sections.add(g.name())) {
                    throw new IllegalStateException("Robot.java: two parts (or a part and an interlock rule) read Tunables." + g.name()
                            + ": give one of them another name");
                }
            }
        }
    }
}

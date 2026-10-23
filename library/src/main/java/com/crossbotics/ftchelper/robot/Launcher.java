package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.List;

/**
 * A flywheel launcher: a motor that spins a wheel to a speed, and an {@link Indexer} that feeds one
 * piece at a time once the speed is right.
 *
 * <pre>{@code
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer")));
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
 *         .speedFromDistance(camera));                       // speed from the camera's distance
 *
 * public static class Launcher {                             // Tunables.java
 *     public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 0;
 *     public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}};   // inches, rpm
 *     public static double kP = 1.17, kI = 0.117, kD = 0, kF = 11.70, nominalVolts = 12.6;   // Flywheel Finder
 *     public static double trimStepPercent = 1, trimMaxPercent = 15;   // optional: the speed trim
 * }
 * }</pre>
 *
 * <p><b>First run</b> (no kF, no speedRpm written): the hub's gains start from the parts catalog for the
 * motor ({@link Flywheel#startingPidf}: kF 11.7, kP 1.17, kI 0.117 for a goBILDA 6000 RPM motor) and the
 * speed at 60% of its top speed (3600). INIT says so once; the Flywheel Finder and the Shot Tuner find the
 * real ones. A written number always wins.
 *
 * <p><b>Speed trim</b> (a fix during a match, when every shot is a little short or long: a worn
 * wheel, different balls): {@link #trimBy(double)} makes every shot speed (the table's or the
 * fixed one) that many percent faster or slower, kept to +/- {@code trimMaxPercent} (15) and to the
 * motor's top speed (then the Driver Station says "(motor limit)"). It starts at 0 at every INIT and is
 * the same for every table. The Driver Station shows {@code Speed trim: +3%  READY} as the launcher's
 * first line. The trim used last time is remembered (until the robot app restarts) and offered at
 * INIT: "Trim last time: +3% (double-tap D-pad up after START to use it)"; it is never applied by
 * itself. The Shot Tuner never uses it (tables are recorded clean). If you trim the same way match
 * after match, the table is off: tune it again (or fold the trim into Tunables.java) instead.
 *
 * <p><b>Battery compensation</b> (OFF by default; {@code batteryCompensation = true} with {@code kF}
 * turns it on — not yet proven on a real robot): the hub's F gain is scaled by {@code nominalVolts} / the battery now, so a low battery gets
 * the wheel back to speed after a shot as fast as a full one (see {@link Flywheel#batteryCompensation}).
 *
 * <p>{@link #fire()} spins up if needed and feeds one piece as soon as the wheel is at speed (it gives
 * up after 3 s and says why). The driver can step the speed up and down ({@link #adjust(int)}).
 * With {@link #speedFromDistance}, the speed comes from {@code speedByDistance} (blended between rows)
 * while the goal is seen; when the camera does not see it, from the odometry's distance (with an
 * {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY}, see {@link Aim}); with neither it
 * keeps the last speed. {@link #speedFromOdometry()}: the odometry's distance only (no camera).
 *
 * <p><b>Several shot tables</b>, one per game piece (they fly differently): every
 * {@code speedByDistance...} table in the section is one, named by the rest of its name.
 * <pre>{@code
 * public static double[][] speedByDistancePollen = {{24, 2100}, {48, 2450}, {72, 2800}};   // "pollen"
 * public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};   // "nectar"
 * }</pre>
 * Switch with {@link #useTable(String)} (a button, an action, an autonomous), {@link #nextTable()},
 * or let a sensor choose: {@link #tableFrom(java.util.function.Supplier)}. It starts on the plain
 * {@code speedByDistance} if there is one, else the first by name, or the one given to
 * {@link #startTable(String)}. The Driver Station shows the table in use.
 *
 * <p><b>A hood</b> ({@link #hood(Hood)}): the shot table's rows get a third number, the hood angle
 * ({@code {48, 2550, 12}}), blended between rows like the speed; the launcher waits for the hood to
 * arrive before it feeds. Two-number rows still work (then the hood is set by hand). See {@link Hood}.
 *
 * <p>Suggested buttons (operator): LB spins up / down, RB fires one (hold it: keeps firing). D-pad
 * up/down: with the speed from the distance, the speed trim +/- {@code trimStepPercent} (1%); with a
 * fixed speed, +/- {@code stepRpm}; held, one more step every 0.25 s. Double-tap D-pad down: back to no
 * trim. Double-tap D-pad up: use last time's trim (only when there is one). D-pad right picks the next
 * shot table (with two or more). Each only when that button is free.
 *
 * Docs: docs/modules/robot.md (section "Launcher"), docs/modules/launcher.md
 */
public final class Launcher extends Module<Launcher> implements Toggleable {
    private final String motorId;
    private boolean reversed = false;
    private Indexer indexer = null;
    private Hood hood = null;
    // Shot tables whose rows have a third column (the hood angle), by name.
    private final java.util.Set<String> hoodTables = new java.util.HashSet<>();
    // A note for INIT: tables with hood angles but no hood (they are not used).
    private String hoodNote = "";
    // INIT notes about numbers the launcher reads differently from what is written (or assumes).
    private final java.util.List<String> numberNotes = new java.util.ArrayList<>();
    // "Launcher: defaults from the catalog (...)": "" when every number was written.
    private String catalogNote = "";
    private Camera distanceCamera = null;
    // speedFromOdometry(): the distance from the odometry alone (no camera).
    private boolean distanceFromOdometry = false;
    // The shot tables by name ("main", "pollen"...), the Tunables field each came from, the one in use.
    private final java.util.Map<String, ShotTable> tables = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> tableFields = new java.util.LinkedHashMap<>();
    private String tableName = null;
    private String startTable = null;
    // tableFrom(...): asked every loop; a new answer picks that table.
    // tableFor(...): game-piece kinds and the table each uses ("red_nectar" -> "nectar").
    private final java.util.Map<String, String> kindTables = new java.util.HashMap<>();
    private java.util.function.Supplier<String> tableSupplier = null;
    private String lastSupplied = null;
    private String tableProblem = "";
    // The last distance to the goal (inches; NaN = none yet) and where it came from.
    private double lastDistance = Double.NaN;
    private Aim.Source distanceSource = Aim.Source.NONE;
    private final Mode speedMode = new Mode("Speed from distance", false);
    private final List<String> checkNames = new java.util.ArrayList<>();
    private final List<java.util.function.BooleanSupplier> checks = new java.util.ArrayList<>();

    com.crossbotics.ftchelper.launcher.Launcher part;
    private Flywheel flywheel;
    private Tunable speedRpm, stepRpm;
    private int adjustSteps = 0;
    // The speed trim (percent, 0 = none), one press of the D-pad, and the most it may go each way.
    private double trimPct = 0;
    private double trimStepPct = 1, trimMaxPct = 15;
    private boolean spinning = false;
    private boolean spinAtStart = false;
    // True when the trimmed speed is cut to the motor's top speed (shown as "(motor limit)").
    private boolean atMotorLimit = false;
    // Last time's trim {percent, rpm steps}, offered at INIT until it is used or the trim is changed; null = none.
    private double[] offered = null;
    // A held D-pad: when the next step is due (seconds); NaN = not held.
    private double repeatAt = Double.NaN;
    // The time (nanoseconds); tests use a pretend clock.
    java.util.function.LongSupplier clock = System::nanoTime;

    /**
     * The trim each launcher (by name) had when it was last changed: {percent, rpm steps}. "static": it
     * outlives the OpMode (until the robot app restarts), so the next INIT can offer it. Never applied
     * by itself.
     */
    static final java.util.Map<String, double[]> lastTrim = new java.util.HashMap<>();
    /** A held D-pad steps again every this many seconds (the first repeat 0.5 s after the press). */
    static final double REPEAT_SEC = 0.25;
    // fireWhileHeld: true while the button is held, and how many shots this press has asked for.
    private boolean holding = false;
    private int heldShots = 0;
    private double shotAngleDeg = Double.NaN;

    private Launcher(String motor, String motorId) {
        super(motor);
        Motors.withCounts(motorId, "Launcher.flywheel(\"" + motor + "\", ...)");
        this.motorId = motorId;
    }

    /**
     * A launcher with its flywheel on this motor.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors}), e.g. "gobilda-5203-6000"
     */
    public static Launcher flywheel(String motor, String motorId) {
        return new Launcher(motor, motorId);
    }

    /**
     * The hood that sets how steep the shot leaves ({@link Hood}): its angle comes from the shot table's
     * third column ({@code {inches, rpm, hood}}), and the launcher waits for it before feeding.
     *
     * @param hood e.g. {@code Hood.servo("hood")}
     */
    public Launcher hood(Hood hood) {
        if (hood == null) throw new IllegalArgumentException("Robot.java: " + name() + ".hood(null)");
        this.hood = hood;
        hood.inLauncher = true;
        return this;
    }

    /** The hood given with {@link #hood(Hood)}, or null. */
    public Hood hood() {
        return hood;
    }

    /** The indexer that feeds it. Without one, {@link #fire()} only spins up. */
    public Launcher feeder(Indexer feeder) {
        indexer = feeder;
        feeder.inLauncher = true;
        return this;
    }

    /**
     * Which way a piece leaves the wheel, for the simulator: degrees up from the wheel shape's forward,
     * seen from the side (45 = forward and up, 90 = straight up, 135 = backward and up). For a wheel
     * mounted level on the robot that is the robot's forward; on a turned part (an arm, a tilted
     * slide) the angle turns with it.
     *
     * @param degrees e.g. 147 for a shot up and out of the back
     */
    public Launcher shotAngle(double degrees) {
        if (degrees < 0 || degrees > 180) {
            throw new IllegalArgumentException("Robot.java: shotAngle(" + degrees + "): 0 to 180 degrees (0 forward, 90 up, 180 back)");
        }
        shotAngleDeg = degrees;
        return this;
    }

    /**
     * Spins up at START (in autonomous and TeleOp), so it is at speed when the first shot is wanted.
     * LB (or {@link #idle()}) still lets it coast down.
     */
    public Launcher spinAtStart() {
        spinAtStart = true;
        return this;
    }

    /** The flywheel turns the other way. */
    public Launcher reversed() {
        reversed = true;
        return this;
    }

    /**
     * Another check before each piece is fed, e.g. {@code .requireReady("turret aimed", () -> turret.isAimed())}.
     * While it is false the launcher waits (and gives up after 3 s, saying which check failed).
     *
     * @param what  a few words for the Driver Station
     * @param check true when ready
     */
    public Launcher requireReady(String what, java.util.function.BooleanSupplier check) {
        checkNames.add(what);
        checks.add(check);
        return this;
    }

    /**
     * Sets the speed from this camera's distance to the goal ({@code Tunables...speedByDistance}). With
     * an {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY}, the odometry's distance is
     * used while the camera does not see the goal.
     */
    public Launcher speedFromDistance(Camera camera) {
        distanceCamera = camera;
        return this;
    }

    /**
     * Sets the speed from the odometry's distance to the goal, with NO camera: where the robot is
     * ({@code robot.pose()}) and {@code Tunables.Aim.goalX, goalY} (see {@link Aim}). Needs an
     * {@code odometry(...)} line.
     */
    public Launcher speedFromOdometry() {
        distanceFromOdometry = true;
        return this;
    }

    /**
     * The shot table to start with, by name ("pollen" for {@code speedByDistancePollen}). Default: the
     * plain {@code speedByDistance} if there is one, else the first by name.
     */
    public Launcher startTable(String name) {
        startTable = name == null ? null : name.trim().toLowerCase(java.util.Locale.US);
        return this;
    }

    /**
     * Picks the shot table from your own code every loop, e.g. a sensor that tells which game piece is
     * loaded: {@code .tableFrom(() -> robot.indexer.pieceName())}. Each time its answer CHANGES to a
     * table's name, that table is used (case does not matter); null or "" changes nothing. A button can
     * still pick another table until the answer changes again. Call it in Robot.java (chained) or later.
     *
     * @param tableName gives a table's name ("pollen"), or null when it does not know
     */
    public Launcher tableFrom(java.util.function.Supplier<String> tableName) {
        tableSupplier = tableName;
        lastSupplied = null;
        return this;
    }

    /**
     * Says which shot table some game-piece kinds use, for {@link #tableFrom}: e.g. with
     * {@code .tableFrom(pieces.nextKind()).tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR")} both NECTAR
     * colours use {@code speedByDistanceNectar}. A kind with the same name as a table needs no line
     * ("POLLEN" uses "pollen"). "NONE" and "UNKNOWN" (from {@code Pieces}) never change the table.
     *
     * @param table the table's name ("nectar")
     * @param kinds the kinds that use it ("RED_NECTAR", "BLUE_NECTAR")
     */
    public Launcher tableFor(String table, String... kinds) {
        if (table == null || table.trim().isEmpty() || kinds.length == 0) {
            throw new IllegalArgumentException("Robot.java: " + name() + ".tableFor(table, kinds...): give a table name and at least one kind");
        }
        for (String k : kinds) kindTables.put(k.trim().toLowerCase(java.util.Locale.US), table.trim().toLowerCase(java.util.Locale.US));
        return this;
    }

    @Override
    List<Module<?>> children() {
        List<Module<?>> out = new java.util.ArrayList<>();
        if (indexer != null) out.add(indexer);
        if (hood != null) out.add(hood);
        return out;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (distanceCamera != null && distanceCamera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " takes its speed from a camera that is not "
                    + "added: write camera = add(Camera...) and speedFromDistance(camera)");
        }
        Motors.Kind kind = Motors.get(motorId);
        // A custom motor whose top speed is not known: general starting values, made for a 6000 RPM motor.
        boolean general = Double.isNaN(kind.freeRpm);
        double topRpm = general ? GENERAL_TOP_RPM : kind.freeRpm;
        // No speedRpm written: a starting speed from the parts catalog, 60% of the motor's top speed
        // (startingRpm explains why), never a fixed number that a slow motor could not reach.
        boolean speedWritten = t.has("speedRpm");
        double startRpm = startingRpm(topRpm);
        double codeSpeed = t.number("speedRpm", startRpm);
        if (codeSpeed > topRpm) {
            throw new IllegalStateException("Tunables." + name() + ".speedRpm = " + codeSpeed + " is above the "
                    + motorId + "'s top speed (" + topRpm + " RPM" + (general ? ", the general one: give yours with Motors.custom" : "") + ")");
        }
        speedRpm = t.tunable("speedRpm", startRpm, 50).range(0, topRpm);
        stepRpm = t.tunable("stepRpm", 100, 10).range(0, 1000);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .coast().runUsingEncoder().done();
        flywheelMotor = motor;
        flywheel = new Flywheel(name(), motor)
                .countsPerRev(kind.countsPerRev)
                .maxRpm(topRpm)
                .tolerance(t.number("readyBandRpm", 60));
        numberNotes.clear();
        catalogNote = "";
        // The hub's speed control. A written number always wins. Without kF, the starting gains come
        // from the parts catalog (Flywheel.startingPidf: F = 32767 / the motor's top speed in counts per
        // second, P = F / 10, I = P / 10, D = 0, REV's formula and the Flywheel Finder's), NOT the hub's
        // own defaults (P 10, I 3, F 0: no F, so the wheel only reaches its speed slowly, by I).
        double[] start = startingGains(kind.countsPerRev, topRpm);
        boolean gainsFromCatalog = !t.has("kF");
        double kP = t.has("kP") ? t.number("kP", 0) : start[0];
        double kI = t.has("kI") ? t.number("kI", 0) : start[1];
        double kD = t.has("kD") ? t.number("kD", 0) : start[2];
        double kF = t.has("kF") ? t.number("kF", 0) : start[3];
        flywheel.pidf(kP, kI, kD, kF);
        // Battery compensation: off unless Tunables says batteryCompensation = true (not yet proven
        // on real robots). Optional numbers are read only when written, so no "missing" note.
        // (Only with a written kF: starting values were not tuned at any battery voltage.)
        if (gainsFromCatalog && t.has("batteryCompensation") && t.flag("batteryCompensation", false)) {
            numberNotes.add("Tunables." + name() + ": batteryCompensation needs your own kF (Flywheel Finder): off until then");
        }
        boolean compensate = !gainsFromCatalog && t.has("batteryCompensation") && t.flag("batteryCompensation", false);
        double nominal = t.has("nominalVolts") ? t.number("nominalVolts", Flywheel.DEFAULT_NOMINAL_VOLTS)
                : Flywheel.DEFAULT_NOMINAL_VOLTS;
        if (nominal < 9 || nominal > 14) {
            throw new IllegalStateException("Tunables." + name() + ".nominalVolts = " + nominal
                    + ": the battery voltage the gains were tuned at, 9 to 14 V (the Flywheel Finder shows it)");
        }
        // The reader never waits: the hubs are asked on the library's helper thread (not in the loop).
        if (compensate) flywheel.batteryCompensation(devices.batteryReader(), nominal);
        if (compensate && !t.has("nominalVolts")) {
            numberNotes.add(name() + ": battery compensation on, nominalVolts not written: assuming "
                    + Shape.n(Flywheel.DEFAULT_NOMINAL_VOLTS) + " V (the Flywheel Finder shows yours; "
                    + "batteryCompensation = false turns it off)");
        }
        if (t.has("trimStepPercent")) trimStepPct = Math.abs(t.number("trimStepPercent", 1));
        // Last time's trim (an earlier OpMode since the robot app started): offer it, never apply it.
        double[] last = lastTrim.get(name());
        offered = last != null && (last[0] != 0 || last[1] != 0) ? last.clone() : null;
        if (t.has("trimMaxPercent")) {
            double asked = Math.abs(t.number("trimMaxPercent", 15));
            trimMaxPct = Math.max(0, Math.min(50, asked));
            if (asked > 50) numberNotes.add("Tunables." + name() + ".trimMaxPercent = " + Shape.n(asked) + ": at most 50 is used");
        }
        Feeder feed = indexer != null ? indexer.forLauncher() : new NoFeeder();
        part = new com.crossbotics.ftchelper.launcher.Launcher(flywheel, feed)
                .idleRpm(t.number("idleRpm", 0))
                .shotInterval(t.number("shotIntervalSec", 0.25));
        for (int i = 0; i < checks.size(); i++) part.requireReady(checkNames.get(i), checks.get(i));
        // The hood (built before this module): the launcher sends it the table's angle and waits for it.
        // The hood moves for changes over 1/100 of its range (0.25 deg for 25 deg), so camera jitter
        // does not keep it "moving" (degrees, servo positions or ticks alike).
        if (hood != null) part.hood(hood.part()).hoodDeadband((hood.maxAngle() - hood.minAngle()) / 100);
        // Every speedByDistance... table (one per game piece, if they fly differently). Rows are
        // {inches, rpm} or, with a hood, {inches, rpm, hood angle}: all rows of a table the same.
        java.util.List<String> unusedHood = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Map.Entry<String, double[][]>> e : t.tables("speedByDistance").entrySet()) {
            String field = e.getValue().getKey();
            ShotTable table = new ShotTable();
            int columns = -1;
            for (double[] r : e.getValue().getValue()) {
                if (r == null || r.length < 2) continue;
                if (r.length > 3) {
                    throw new IllegalStateException("Tunables." + name() + "." + field + ": a row has " + r.length
                            + " numbers; each row is {inches, rpm} or {inches, rpm, hood angle}");
                }
                if (columns >= 0 && r.length != columns) {
                    throw new IllegalStateException("Tunables." + name() + "." + field + ": some rows have a hood angle and "
                            + "some do not; give every row the same columns ({inches, rpm} or {inches, rpm, hood})");
                }
                columns = r.length;
                if (r.length == 3 && hood != null) {
                    if (r[2] < hood.minAngle() - 1e-9 || r[2] > hood.maxAngle() + 1e-9) {
                        throw new IllegalStateException("Tunables." + name() + "." + field + ": the row at " + Shape.n(r[0])
                                + " in has hood " + Shape.n(r[2]) + ", outside the hood's range " + Shape.n(hood.minAngle())
                                + " to " + Shape.n(hood.maxAngle()) + " (Tunables." + hood.name() + ")");
                    }
                    table.add(r[0], r[1], r[2]);
                } else {
                    table.add(r[0], r[1]);
                }
            }
            if (table.points().isEmpty()) continue;   // an empty table cannot give a speed
            tables.put(e.getKey(), table);
            tableFields.put(e.getKey(), field);
            if (columns == 3) {
                if (hood != null) hoodTables.add(e.getKey());
                else unusedHood.add(field);
            }
        }
        hoodNote = unusedHood.isEmpty() ? "" : "Tunables." + name() + "." + commas(unusedHood)
                + " has hood angles but Robot.java gives the launcher no .hood(...): they are not used";
        if (hood != null) hood.tableDriven = usesDistance() && !hoodTables.isEmpty();
        if (tables.isEmpty() && usesDistance()) {
            throw new IllegalStateException(name() + (distanceFromOdometry ? ".speedFromOdometry" : ".speedFromDistance")
                    + " needs Tunables." + name() + ".speedByDistance = {{inches, rpm}, ...} (or one table per game piece, "
                    + "like speedByDistancePollen)");
        }
        if (distanceFromOdometry && robot.odometrySpec() == null) {
            throw new IllegalStateException("Robot.java: " + name() + ".speedFromOdometry() needs an odometry(...) line");
        }
        if (distanceFromOdometry && !robot.aim().hasGoalPoint()) {
            throw new IllegalStateException(name() + ".speedFromOdometry() needs the goal on the field: "
                    + "Tunables.Aim { public static double goalX = ..., goalY = ...; } (inches, see docs/modules/robot.md#aim)");
        }
        if (!tables.isEmpty()) {
            String first = tables.containsKey("main") ? "main" : tables.keySet().iterator().next();
            if (startTable != null) {
                if (!tables.containsKey(startTable)) {
                    throw new IllegalStateException("Robot.java: " + name() + ".startTable(\"" + startTable + "\"): Tunables."
                            + name() + " has no such table (it has: " + tableList() + ")");
                }
                first = startTable;
            }
            select(first);
        }
        part.setRpm(speedRpm.get());
        // One INIT note with every starting value from the parts catalog, and the tool that tunes it.
        // The speed only when it is the shot speed (no shot table): with a table, the table gives it.
        List<String> fromCatalog = new java.util.ArrayList<>();
        if (gainsFromCatalog) {
            fromCatalog.add("kF " + exact(round(kF, 1)));
            if (!t.has("kP")) fromCatalog.add("kP " + exact(round(kP, 2)));
            if (!t.has("kI")) fromCatalog.add("kI " + exact(round(kI, 3)));
        }
        boolean speedFromCatalog = !speedWritten && tables.isEmpty();
        if (speedFromCatalog) fromCatalog.add("speed " + exact(startRpm) + " rpm");
        if (!speedWritten) t.fromCatalog("speedRpm");
        if (gainsFromCatalog) t.fromCatalog("kP", "kI", "kD", "kF");
        if (!fromCatalog.isEmpty()) {
            String tools = gainsFromCatalog && speedFromCatalog ? "run the Flywheel Finder, then the Shot Tuner"
                    : gainsFromCatalog ? "run the Flywheel Finder" : "find your shot speed with the Shot Tuner";
            catalogNote = general
                    ? name() + ": the top speed of " + motorId + " is not known, so general defaults (made for a "
                            + exact(GENERAL_TOP_RPM) + " RPM motor: " + listed(fromCatalog) + "). Give it with Motors.custom(...), or "
                            + tools
                    : defaultsNote(motorId, fromCatalog, Character.toUpperCase(tools.charAt(0)) + tools.substring(1));
        }
    }

    @Override
    String defaultSourceId() {
        return motorId;
    }

    @Override
    String toolFor(String field) {
        if (field.equals("speedRpm") || field.startsWith("speedByDistance")) return "the Shot Tuner, docs/guides/finders.md#shot-tuner-the-shot-table";
        if (field.startsWith("k") || field.equals("nominalVolts") || field.equals("shotIntervalSec")) {
            return "the Flywheel Finder, docs/guides/finders.md#flywheel-finder-gains-and-recovery";
        }
        return null;
    }

    /**
     * The launcher's starting speed when Tunables gives none: 60% of the motor's top speed from the parts
     * catalog, to 10 RPM (a goBILDA 6000 RPM motor: 3600). Why 60%: the speed control needs room above the
     * speed to push harder after each shot and on a tired battery (the top speed is for a full 12 V with no
     * load), and a middle speed is a gentle first shot. The Shot Tuner finds the real one.
     *
     * @param kind the flywheel's motor
     * @return RPM
     */
    static double startingRpm(Motors.Kind kind) {
        return startingRpm(kind.freeRpm);
    }

    static double startingRpm(double topRpm) {
        return Math.round(topRpm * STARTING_SPEED_SHARE / 10.0) * 10.0;
    }

    /** The top speed assumed for a custom motor whose speed is not known (a bare FTC motor): 6000 RPM. */
    static final double GENERAL_TOP_RPM = 6000;

    /** The share of the motor's top speed a launcher starts at with no speedRpm written (0.6). */
    static final double STARTING_SPEED_SHARE = 0.6;

    /**
     * The hub's starting speed-control gains for this motor, {P, I, D, F}: {@link Flywheel#startingPidf} with
     * the parts catalog's top speed (free RPM x counts per turn / 60). A goBILDA 6000 RPM motor: {1.17, 0.117, 0, 11.70}.
     */
    static double[] startingGains(Motors.Kind kind) {
        return startingGains(kind.countsPerRev, kind.freeRpm);
    }

    static double[] startingGains(double countsPerRev, double topRpm) {
        return Flywheel.startingPidf(topRpm * countsPerRev / 60.0);
    }

    private static double round(double v, int places) {
        double s = Math.pow(10, places);
        return Math.round(v * s) / s;
    }

    @Override
    void start() {
        if (usesDistance()) speedMode.on();
        if (spinAtStart) {
            spinning = true;
            part.spinUp();
        }
    }

    // ----------------------------- commands -----------------------------

    /** Spins up to the shot speed and keeps it there. */
    public void spinUp() {
        needBuilt(part);
        touch();
        spinning = true;
        part.spinUp();
    }

    /** Back to the idle speed ({@code idleRpm}, 0 = coast to a stop); cancels shots still waiting. */
    public void idle() {
        needBuilt(part);
        touch();
        spinning = false;
        part.idle();
    }

    /** True while it is spun up (or spinning up), false when idle. */
    public boolean isSpinning() {
        return spinning && !missing;
    }

    /** Spins up if idle, idles if spinning. */
    @Override
    public void toggle() {
        if (spinning) idle();
        else spinUp();
    }

    /** Fires one piece (spins up first if needed; feeds as soon as it is at speed). */
    public void fire() {
        fire(1);
    }

    /** Fires {@code count} pieces, one after another. */
    public void fire(int count) {
        needBuilt(part);
        touch();
        askedToFire = true;
        spinning = true;
        part.shoot(count);
    }

    /**
     * For a held button, every loop: fires one piece, then the next as soon as that one is fed, for as
     * long as the button is held (the launcher keeps its time between shots). Pair it with
     * {@link #ceaseFire()} on release: {@code g1.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire)}.
     */
    public void fireWhileHeld() {
        needBuilt(part);
        touch();
        askedToFire = true;
        spinning = true;
        if (!holding) {
            holding = true;
            heldShots = 0;
        }
        if (!part.isShooting()) {
            part.shoot(1);
            heldShots++;
        }
    }

    /**
     * The button is let go: no more shots. The press's first shot still goes (a quick tap fires one,
     * even while the wheel is still spinning up); a later shot still waiting is dropped.
     */
    public void ceaseFire() {
        if (part == null) return;
        touch();
        if (holding && heldShots > 1) part.stopShooting();
        holding = false;
    }

    /**
     * Changes the speed by {@code steps} x {@code stepRpm} (e.g. +1 or -1 from a D-pad). Kept until
     * the program ends; the Tunables value is not changed.
     */
    public void adjust(int steps) {
        // Not touch(): changing the speed is not taking the launcher away from an action.
        if (steps > 0 && atMotorLimit) return;   // already at the motor's top speed: no faster
        adjustSteps += steps;
        remember();
    }

    // ----------------------------- speed trim -----------------------------

    /**
     * Trims the shot speed by this many percent more: {@code trimBy(+2)} = every shot 2% faster
     * than the table (or the fixed speed) says, {@code trimBy(-1)} = 1% slower. Adds up, kept to
     * +/- {@code Tunables.Launcher.trimMaxPercent} (15). From a button, an action
     * ({@code .then(() -> r.launcher.trimBy(+2))}) or code. The same name as the hood's trim
     * ({@link Hood#trimBy(double)}), which is in the hood's angle instead of percent. Starts at 0 at every INIT; the
     * Tunables numbers are not changed. A fix for this match: if you need it every match, tune the table again.
     *
     * @param change percent to add, e.g. +1 or -1
     */
    public void trimBy(double change) {
        // Not touch(): changing the speed is not taking the launcher away from an action.
        if (Double.isNaN(change)) return;
        if (change > 0 && atMotorLimit) return;   // already at the motor's top speed: no faster
        setTrim(trimPct + change);
    }

    /**
     * Sets the speed trim to this many percent (0 = none), kept to +/- {@code trimMaxPercent}.
     *
     * @param percent e.g. 3 for 3% faster, -2 for 2% slower
     */
    public void setTrim(double percent) {
        if (Double.isNaN(percent)) return;
        trimPct = Math.max(-trimMaxPct, Math.min(trimMaxPct, percent));
        remember();
    }

    /** Back to the Tunables speed: the trim to 0, the {@link #adjust(int)} steps to 0, and the hood trim to 0. */
    public void resetTrim() {
        trimPct = 0;
        adjustSteps = 0;
        if (hood != null) hood.resetTrim();
        remember();
    }

    /**
     * Uses the trim from last time (an earlier OpMode since the robot app started), if there is one: the
     * Driver Station offers it at INIT ("Trim last time: +3%"); double-tap D-pad up uses it. Never runs
     * by itself.
     *
     * @return true if there was one to use
     */
    public boolean useLastTrim() {
        if (offered == null) return false;
        double[] o = offered;
        trimPct = Math.max(-trimMaxPct, Math.min(trimMaxPct, o[0]));
        adjustSteps = (int) o[1];
        remember();
        return true;
    }

    /** Last time's trim in words ("+3%"), while it is offered and not used yet; "" when there is none. */
    public String lastTrimOffered() {
        return offered == null ? "" : trimWords(offered[0], (int) offered[1]);
    }

    /** True when the trimmed shot speed is cut to the motor's top speed (more trim up does nothing). */
    public boolean atMotorLimit() {
        return atMotorLimit;
    }

    // A trim change: remember it for the next INIT; the offer from last time is gone (the operator chose).
    private void remember() {
        offered = null;
        lastTrim.put(name(), new double[] {trimPct, adjustSteps});
    }

    // One D-pad step: the trim (speed from the distance) or the rpm step (a fixed speed).
    private void step(int direction) {
        if (usesDistance()) trimBy(direction * trimStepPct);
        else adjust(direction);
    }

    // A held D-pad, every loop: one step 0.5 s after the press (the hold starts after the tap time,
    // Tunables.Buttons.holdSec 0.3 s), then one every REPEAT_SEC.
    void repeatStep(int direction) {
        double now = clock.getAsLong() / 1e9;
        if (Double.isNaN(repeatAt)) repeatAt = now + 0.2;
        if (now >= repeatAt) {
            step(direction);
            repeatAt = now + REPEAT_SEC;
        }
    }

    void repeatEnd() {
        repeatAt = Double.NaN;
    }

    // Double-tap D-pad up: last time's trim if it is offered, else two steps up (what two taps would do).
    private void upTwice() {
        if (!useLastTrim()) {
            step(1);
            step(1);
        }
    }

    /** The speed trim now, percent (0 = none; +3 = 3% faster). */
    public double trim() {
        return trimPct;
    }

    /**
     * Speed from the distance to the goal: on from START when {@link #speedFromDistance} or
     * {@link #speedFromOdometry} is used.
     */
    public Mode speedMode() {
        return speedMode;
    }

    // ----------------------------- shot tables -----------------------------

    /**
     * Uses this shot table from now on, e.g. {@code robot.launcher.useTable("nectar")} for
     * {@code speedByDistanceNectar} (case does not matter). From a button, an action
     * ({@code .then(() -> r.launcher.useTable("nectar"))}) or another part's code. A name with no table
     * keeps the table in use and warns on the Driver Station (it never stops the robot in a match).
     *
     * @param name the table's name: the end of its Tunables name ("pollen"), or "main" for {@code speedByDistance}
     */
    public void useTable(String name) {
        needBuilt(part);
        if (name == null || name.trim().isEmpty()) return;
        String key = name.trim().toLowerCase(java.util.Locale.US);
        if (!tables.containsKey(key)) {
            tableProblem = "no shot table \"" + name.trim() + "\" (Tunables." + name() + " has: " + tableList() + ")";
            return;
        }
        select(key);
    }

    /** The next shot table (after the last, the first again), for a button. */
    public void nextTable() {
        needBuilt(part);
        if (tables.isEmpty()) return;
        java.util.List<String> names = tableNames();
        select(names.get((names.indexOf(tableName) + 1) % names.size()));
    }

    /** The shot table in use ("pollen", "main"), or null if there is none. */
    public String tableName() {
        return tableName;
    }

    /** Every shot table's name, in order ("main" first, then by name). Empty before INIT or with none. */
    public java.util.List<String> tableNames() {
        return new java.util.ArrayList<>(tables.keySet());
    }

    /** The Tunables field a table came from ("speedByDistancePollen"), or null if there is no such table. */
    public String tableField(String name) {
        return name == null ? null : tableFields.get(name.trim().toLowerCase(java.util.Locale.US));
    }

    /**
     * A shot table by name, as Tunables.java has it ("pollen", "main"; case does not matter), or null if
     * there is no such table. The Shot Tuner starts each table's speed from it.
     */
    public ShotTable table(String name) {
        return name == null ? null : tables.get(name.trim().toLowerCase(java.util.Locale.US));
    }

    /** Tunables.Launcher.speedRpm: the speed with no shot table (RPM). NaN before INIT. */
    public double speedRpm() {
        return speedRpm == null ? Double.NaN : speedRpm.get();
    }

    /** The distance to the goal last used for the speed (inches), NaN if none yet. */
    public double distanceIn() {
        return lastDistance;
    }

    /** Where that distance came from last loop: CAMERA, ODOMETRY or NONE (keeps the last speed). */
    public Aim.Source distanceSource() {
        return distanceSource;
    }

    // Switches to a table that exists, and works out the speed again at once from the last distance.
    private void select(String key) {
        tableName = key;
        tableProblem = "";
        part.table(tables.get(key));
        if (!Double.isNaN(lastDistance) && speedMode.isOn()) {
            if (hood != null) part.hoodOffset(hood.trim());
            part.setDistance(lastDistance);
        }
    }

    // True when the shot table in use sets the hood now: a hood, a table with hood angles, the speed from
    // the distance, and a distance known.
    private boolean hoodFromTable() {
        return hood != null && speedMode.isOn() && usesDistance() && tableName != null
                && hoodTables.contains(tableName) && !Double.isNaN(lastDistance);
    }

    private String tableList() {
        return tables.isEmpty() ? "none" : commas(tables.keySet());
    }

    // True if the speed comes from the distance (camera, odometry, or both).
    private boolean usesDistance() {
        return distanceCamera != null || distanceFromOdometry;
    }

    /** True when the wheel is at speed (and every other check passes). */
    public boolean isReady() {
        return part != null && !missing && part.isReady();
    }

    /** True while shots are waiting or a piece is being fed. */
    public boolean isShooting() {
        return part != null && !missing && part.isShooting();
    }

    /** The speed it is aiming for now, RPM. */
    public double targetRpm() {
        return part == null ? 0 : part.getFlywheel().getTargetRpm();
    }

    /** The launcher.Launcher inside, for anything this module does not offer. */
    public com.crossbotics.ftchelper.launcher.Launcher part() {
        return part;
    }

    @Override
    public void update() {
        // A sensor (or other code) that picks the table: a NEW answer picks it.
        if (tableSupplier != null) {
            String now = tableSupplier.get();
            if (now == null ? lastSupplied != null : !now.equals(lastSupplied)) {
                lastSupplied = now;
                if (now != null && !now.trim().isEmpty()) {
                    String key = now.trim().toLowerCase(java.util.Locale.US);
                    // "NONE" (nothing held) and "UNKNOWN" (a piece the sensor cannot name) keep the table.
                    if (!key.equals("none") && !key.equals("unknown")) useTable(kindTables.containsKey(key) ? kindTables.get(key) : key);
                }
            }
        }
        if (speedMode.isOn() && !tables.isEmpty() && usesDistance()) {
            // The camera first (a fresh picture with a distance), then the odometry.
            TargetObservation seen = robot.aim().cameraGoal(distanceCamera);
            if (seen == null || !seen.hasDistance()) seen = robot.aim().fromOdometry();
            distanceSource = Aim.sourceOf(seen);
            if (seen != null && seen.hasDistance()) lastDistance = seen.distanceIn;
            // No goal at all: keep the last distance, so the last speed (a sudden change would spoil
            // the next shot). Before the first one: speedRpm.
            boolean hoodTable = hoodFromTable();
            if (hood != null) {
                // The stick and step() trim while the table in use has hood angles; else they move the hood.
                hood.tableDriven = hoodTables.contains(tableName);
                hood.loop(hoodTable);
            }
            if (!Double.isNaN(lastDistance)) {
                // The hood trim is added to the table's angle (setDistance moves the hood).
                if (hood != null) part.hoodOffset(hood.trim());
                part.setDistance(lastDistance);
                if (hoodTable) hood.afterTable();
            }
        } else {
            if (hood != null) {
                hood.tableDriven = false;
                hood.loop(false);
            }
            part.setRpm(speedRpm.get() + adjustSteps * stepRpm.get());
        }
        // The trim multiplies whichever speed was chosen: +3% = x 1.03. Never past the motor's top speed:
        // the flywheel would cut it anyway, silently, and the trim on the screen would be a lie.
        double scale = 1 + trimPct / 100.0;
        double base = part.getShotRpm(), top = flywheel.getMaxRpm();
        atMotorLimit = base > 0 && base * scale >= top - 1e-6;
        if (base > 0 && base * scale > top) scale = top / base;
        part.speedScale(scale);
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        touch();
        spinning = false;
        part.stop();
    }

    private DcMotorEx flywheelMotor;

    // Shots still waiting when the robot paused: fired after it goes on.
    private int shotsAtPause = 0;

    @Override
    void pause() {
        if (part == null) return;
        // Brake the wheel to a stop (it normally coasts down), and stop the feeder. "spinning" is kept,
        // and so are the shots still waiting (stop() would drop them).
        shotsAtPause = part.getShotsLeft();
        flywheelMotor.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE);
        part.stop();
    }

    @Override
    void resume() {
        if (part == null) return;
        flywheelMotor.setZeroPowerBehavior(com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.FLOAT);
        if (spinning) part.spinUp();
        else part.idle();
        if (shotsAtPause > 0) part.shoot(shotsAtPause);
        shotsAtPause = 0;
    }

    @Override
    void halt() {
        shotsAtPause = 0;                         // a cancel while paused drops them too
        if (part != null) part.stopShooting();   // drop the shots still waiting; keep the speed
    }

    // An action that fires: how many shots the launcher had fired when the action took it, and whether
    // the action asked it to fire (only then does "done" say how many: "Fire all: done (0 fired)").
    private int firedAtClaim = 0;
    private boolean askedToFire = false;

    @Override
    void claimedBy(Action a) {
        firedAtClaim = part == null ? 0 : part.getShotsFired();
        askedToFire = false;
    }

    @Override
    String doneWords() {
        if (part == null || missing || !askedToFire) return "";
        return (part.getShotsFired() - firedAtClaim) + " fired";
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        if (!hoodNote.isEmpty()) out.add(hoodNote);
        if (!catalogNote.isEmpty()) out.add(catalogNote);
        out.addAll(numberNotes);
        return out;
    }

    @Override
    void initLoop(RobotTelemetry t) {
        if (offered != null) {
            t.line("Trim last time", lastTrimOffered() + " (double-tap D-pad up after START to use it)");
        }
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part == null) return;
        if (!tableProblem.isEmpty()) t.warn(name() + ": " + tableProblem);
        // First: the trim and READY, the two things the operator glances at. Always with the speed from
        // the distance (the operator sees it is 0), else when used.
        if (usesDistance() || trimPct != 0 || adjustSteps != 0 || atMotorLimit) {
            t.line("Speed trim", trimWords() + (part.isReady() ? "  READY" : ""));
        }
        if (offered != null) t.line("Trim last time", lastTrimOffered() + " (double-tap D-pad up to use it)");
        // The distance and where it came from, e.g. "48 in (camera)"; "(last seen)" while no goal is seen.
        String where = !speedMode.isOn() || tableName == null ? "" : Double.isNaN(lastDistance) ? "no goal yet"
                : fmt("%.0f in (%s)", lastDistance, distanceSource == Aim.Source.NONE ? "last seen" : distanceSource.words());
        if (tables.size() > 1) {
            t.line("Shot table", tableName.toUpperCase(java.util.Locale.US) + (where.isEmpty() ? "" : ", " + where));
        } else if (!where.isEmpty()) {
            t.line("Shot distance", where);
        }
        part.addTelemetry(t);
    }

    // "+3%", "+200 rpm", "+200 rpm, -2%", "0%"; "(limit)" at trimMaxPercent, "(motor limit)" at the
    // motor's top speed.
    private String trimWords() {
        String w = trimWords(trimPct, adjustSteps);
        if (atMotorLimit) return w + " (motor limit)";
        if (trimPct != 0 && Math.abs(trimPct) >= trimMaxPct) return w + " (limit)";
        return w;
    }

    private String trimWords(double pct, int steps) {
        StringBuilder sb = new StringBuilder();
        if (steps != 0) sb.append(fmt("%+.0f rpm", steps * (stepRpm == null ? 0 : stepRpm.get())));
        if (pct != 0 || sb.length() == 0) sb.append(sb.length() == 0 ? "" : ", ").append(percent(pct, true));
        return sb.toString();
    }

    // 3 -> "+3%", -0.5 -> "-0.5%", 0 -> "0%" (sign = show + or -).
    private static String percent(double v, boolean sign) {
        String n = Math.abs(v - Math.rint(v)) < 1e-9 ? fmt("%.0f", Math.abs(v)) : fmt("%.1f", Math.abs(v));
        return (sign && v > 0 ? "+" : v < 0 ? "-" : "") + n + "%";
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.lb.toggle(this).says("spin up / down");
        op.rb.hold(this::fireWhileHeld, this::ceaseFire).says("fire one; hold: keep firing");
        // With the speed from the table the D-pad trims it by a percentage; with a fixed speed it steps
        // the rpm. Held: one more step every 0.25 s. Double-tap down: back to none.
        String up = usesDistance() ? "speed trim +" + percent(trimStepPct, false) : "speed up";
        String down = usesDistance() ? "speed trim -" + percent(trimStepPct, false) : "speed down";
        // The hold goes first: if another part already has the button, that gives one clear INIT note
        // and the other gestures are not tried.
        op.dpadUp.hold(() -> repeatStep(1), this::repeatEnd).says(up + ", 4 a second");
        if (plan.mine(op.dpadUp) != null) {
            op.dpadUp.tap(() -> step(1)).says(up);
            if (offered != null) op.dpadUp.doubleTap(this::upTwice).says("use last time's trim (" + lastTrimOffered() + ")");
        }
        op.dpadDown.hold(() -> repeatStep(-1), this::repeatEnd).says(down + ", 4 a second");
        if (plan.mine(op.dpadDown) != null) {
            op.dpadDown.tap(() -> step(-1)).says(down)
                    .doubleTap(this::resetTrim).says(usesDistance() ? "speed trim 0" : "back to speedRpm");
        }
        if (tables.size() > 1) op.dpadRight.press(this::nextTable).says("next shot table (" + tableList().toUpperCase(java.util.Locale.US) + ")");   // as the Driver Station shows them
    }

    // Its motor is named in Launcher.flywheel(name, id).
    @Override
    String checkPart(String id) {
        throw new IllegalStateException("Robot.java: the launcher's motor part goes in Launcher.flywheel(\"" + device + "\", \"" + id + "\")");
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        String feederLine = indexer != null ? ", feeder=" + indexer.device : "";
        out.add(fmt("motor.%s = flywheel, %s%s, shotDrop=0.15", device, motorWords(motorId, "gobilda-5203-6000"), feederLine)
                + simTodo(motorId));
        // No looks(...): a common flywheel wheel (its size from the catalog) on top in the middle, or on
        // the robot's one turret (it turns with it: the shots go where the turret points). A launcher with
        // looks(...) says where it sits itself (Shape...on(turret)).
        if (looks.isEmpty()) out.add(guessLine("flywheel", "motor=" + device));
        else lookLines(out, t);
        // A hood with shotAngles(...): the simulated shot angle follows the hood servo.
        TunableSection hoodSection = hood == null ? null : hood.simSection != null ? hood.simSection
                : TunableSection.forFile(robot == null ? null : robot.tunablesClass(), hood.name());
        String hoodWords = hood == null ? "" : hood.shootWords(hoodSection);
        double angle = !hoodWords.isEmpty() ? hood.homeShotAngle(hoodSection) : shotAngleDeg;
        String hoodTodo = hood != null && hoodWords.isEmpty()
                ? "   # TODO the hood: " + (hood.isMotor() ? "the simulator's shot angle does not follow a motor hood yet"
                        : "Hood...shotAngles(atA, atB) in Robot.java, so the shot angle follows it") : "";
        if (Double.isNaN(angle)) {
            out.add(fmt("shoot.%s = shape=%s, angle=45, share=0.5   # TODO the shot angle (or a shot table: tools/launcher)", device, spinningShapeName()));
        } else {
            out.add(fmt("shoot.%s = shape=%s, angle=%s, share=0.5%s%s", device, spinningShapeName(), Shape.n(Math.round(angle * 100) / 100.0),
                    hoodWords, hoodTodo));
        }
    }

    // "a, b, c" (not String.join: the blocking check reads every ".join(" as a thread wait).
    private static String commas(java.util.Collection<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append(sb.length() == 0 ? "" : ", ").append(n);
        return sb.toString();
    }

    // No indexer: shots only spin the wheel.
    private static final class NoFeeder implements Feeder {
        @Override public void feedOne() { }
        @Override public boolean isBusy() { return false; }
        @Override public void update() { }
    }
}

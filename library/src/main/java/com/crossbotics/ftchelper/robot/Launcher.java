package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.ShotTable;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.Collections;
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
 * }
 * }</pre>
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
 * <p>Suggested buttons (operator): LB spins up / down, RB fires one (hold it: keeps firing), D-pad up/down change the speed
 * by {@code stepRpm} (when the D-pad is free and the speed does not come from the distance), D-pad
 * right picks the next shot table (with two or more).
 *
 * Docs: docs/modules/robot.md (section "Launcher"), docs/modules/launcher.md
 */
public final class Launcher extends Module<Launcher> implements Toggleable {
    private final String motorId;
    private boolean reversed = false;
    private Indexer indexer = null;
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
    private boolean spinning = false;
    private boolean spinAtStart = false;
    // fireWhileHeld: true while the button is held, and how many shots this press has asked for.
    private boolean holding = false;
    private int heldShots = 0;
    private double shotAngleDeg = Double.NaN;

    private Launcher(String motor, String motorId) {
        super(motor);
        Motors.get(motorId);
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
        return indexer == null ? Collections.<Module<?>>emptyList() : Collections.<Module<?>>singletonList(indexer);
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (distanceCamera != null && distanceCamera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " takes its speed from a camera that is not "
                    + "added: write camera = add(Camera...) and speedFromDistance(camera)");
        }
        Motors.Kind kind = Motors.get(motorId);
        double codeSpeed = t.number("speedRpm", 2600);
        if (codeSpeed > kind.freeRpm) {
            throw new IllegalStateException("Tunables." + name() + ".speedRpm = " + codeSpeed + " is above the "
                    + motorId + "'s top speed (" + kind.freeRpm + " RPM)");
        }
        speedRpm = t.tunable("speedRpm", 2600, 50).range(0, kind.freeRpm);
        stepRpm = t.tunable("stepRpm", 100, 10).range(0, 1000);
        DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                .coast().runUsingEncoder().done();
        flywheelMotor = motor;
        flywheel = new Flywheel(name(), motor)
                .countsPerRev(kind.countsPerRev)
                .maxRpm(kind.freeRpm)
                .tolerance(t.number("readyBandRpm", 60));
        if (t.has("kF")) {
            flywheel.pidf(t.number("kP", 0), t.number("kI", 0), t.number("kD", 0), t.number("kF", 0));
        }
        Feeder feed = indexer != null ? indexer.forLauncher() : new NoFeeder();
        part = new com.crossbotics.ftchelper.launcher.Launcher(flywheel, feed)
                .idleRpm(t.number("idleRpm", 0))
                .shotInterval(t.number("shotIntervalSec", 0.25));
        for (int i = 0; i < checks.size(); i++) part.requireReady(checkNames.get(i), checks.get(i));
        // Every speedByDistance... table (one per game piece, if they fly differently).
        for (java.util.Map.Entry<String, java.util.Map.Entry<String, double[][]>> e : t.tables("speedByDistance").entrySet()) {
            ShotTable table = new ShotTable();
            for (double[] r : e.getValue().getValue()) if (r.length >= 2) table.add(r[0], r[1]);
            if (table.points().isEmpty()) continue;   // an empty table cannot give a speed
            tables.put(e.getKey(), table);
            tableFields.put(e.getKey(), e.getValue().getKey());
        }
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
        return spinning;
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
        adjustSteps += steps;
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
        if (!Double.isNaN(lastDistance) && speedMode.isOn()) part.setDistance(lastDistance);
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
        return part != null && part.isReady();
    }

    /** True while shots are waiting or a piece is being fed. */
    public boolean isShooting() {
        return part != null && part.isShooting();
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
            if (!Double.isNaN(lastDistance)) part.setDistance(lastDistance);
        } else {
            part.setRpm(speedRpm.get() + adjustSteps * stepRpm.get());
        }
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

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part == null) return;
        if (!tableProblem.isEmpty()) t.warn(name() + ": " + tableProblem);
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

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.lb.toggle(this).says("spin up / down");
        op.rb.hold(this::fireWhileHeld, this::ceaseFire).says("fire one; hold: keep firing");
        if (!usesDistance()) {
            op.dpadUp.press(() -> adjust(1)).says("speed up");
            op.dpadDown.press(() -> adjust(-1)).says("speed down");
        }
        if (tables.size() > 1) op.dpadRight.press(this::nextTable).says("next shot table (" + tableList() + ")");
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
        out.add(fmt("motor.%s = flywheel, part=%s%s, shotDrop=0.15", device, motorId, feederLine));
        if (looks.isEmpty()) out.add(fmt("shape.%s = flywheel, at=-1,0,6, size=4x2x4, motor=%s   # TODO measure", device, device));
        else lookLines(out, t);
        if (Double.isNaN(shotAngleDeg)) {
            out.add(fmt("shoot.%s = shape=%s, angle=45, share=0.5   # TODO the shot angle (or a shot table: tools/launcher)", device, spinningShapeName()));
        } else {
            out.add(fmt("shoot.%s = shape=%s, angle=%s, share=0.5", device, spinningShapeName(), Shape.n(shotAngleDeg)));
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

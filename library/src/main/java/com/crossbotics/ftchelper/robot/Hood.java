package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.mechanism.AngleDevice;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * A launcher's hood: the part that tilts to make the shot leave steeper or flatter. Given to a
 * {@link Launcher}, the hood angle comes from the shot table's third column (blended between rows, like
 * the speed), and the launcher waits for the hood to arrive before it feeds. It works for any hood
 * design: a standard servo or a motor with an encoder, a straight gear or a linkage, any range, either
 * direction.
 *
 * <pre>{@code
 * launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
 *         .hood(Hood.servo("hood"))                          // or Hood.motor("hood", "gobilda-5203-117")
 *         .speedFromDistance(camera));
 *
 * public static class Hood {                                 // Tunables.java
 *     public static double posA = 0.20, posB = 0.65;         // servo positions at the two hard stops (Hood Finder)
 *     public static double angleA = 0, angleB = 25;          // optional: the hood's angle there (degrees)
 *     public static double travelSec = 0.5;                  // stop to stop, seconds (a servo cannot say where it is)
 * }
 * public static class Launcher {
 *     public static double[][] speedByDistance = {{24, 2200, 4}, {48, 2550, 12}, {72, 2900, 18}};  // in, rpm, hood
 * }
 * }</pre>
 *
 * <p><b>Calibration.</b> {@code posA} and {@code posB} are where the hood touches (a hair off) its two
 * hard stops: servo positions 0 to 1, or encoder ticks for {@code Hood.motor}. They are the HARD LIMITS:
 * every angle (a table row, a trim, a stick) is kept between them, so it never pushes on a stop. Both
 * are needed (a safety number never comes from a library default); the Hood Finder finds them.
 * <ul>
 *   <li>With {@code angleA}, {@code angleB}: you work in the hood's angle (any scale, either way round),
 *       on a straight line between the two stops.</li>
 *   <li>Without them: the "angle" IS the servo position (or tick count). The shot table's hood column
 *       then holds positions ({@code {48, 2550, 0.42}}). Fine if you never measure angles.</li>
 *   <li>A linkage or cam is not a straight line: {@code calibration = {{0.20, 0}, {0.35, 8}, {0.50, 17},
 *       {0.65, 25}}} ({position, angle} rows, from the Hood Finder) is blended between rows instead. Its
 *       first and last rows are then the hard limits ({@code posA}/{@code posB} are not used).</li>
 * </ul>
 *
 * <p><b>Ready.</b> A motor hood is ready when its encoder says it is within {@code tolerance}. A servo
 * cannot say where it is, so a servo hood's "arrived" is a time estimate: {@code travelSec} for the whole
 * range, less for a shorter move (time a stop-to-stop move to measure it).
 *
 * <p><b>By hand.</b> Suggested operator stick: left stick Y (up = a bigger angle). While the shot table
 * sets the angle it is a hood TRIM (added to the table, at most +/- {@code trimMax}; back to 0 with the
 * launcher's double-tap D-pad down); otherwise it moves the hood. Your own buttons: {@link #step(int)},
 * {@link #setAngle(double)}, {@link #goToStart()}.
 *
 * <p>Optional Tunables (defaults scale with the range, so they suit degrees, positions and ticks):
 * {@code startAngle} (where it goes at START; default angleA), {@code stepAngle} (1/25 of the range),
 * {@code byHandPerSec} (the range in 2.5 s), {@code trimMax} (1/5 of the range). A motor hood:
 * {@code kP} (0.005 power per tick), {@code maxPower} (0.5), {@code tolerance} (1/50 of the range). Its
 * encoder counts from where it is at INIT: start the robot with the hood at the stop you call 0 ticks.
 * Nothing moves in INIT: the hood goes to {@code startAngle} at START.
 *
 * Docs: docs/modules/robot.md (section "Hood"), lesson 9 (docs/tutorials/start/09-shooter-and-camera.md)
 */
public final class Hood extends Module<Hood> {
    private enum Kind { SERVO, MOTOR }

    private final Kind kind;
    private final String motorId;
    // "Hood: defaults from the catalog (...)": "" when travelSec was written (or no part).
    private String startNote = "";
    private boolean reversed = false;
    // For the simulator: the launcher's shot angle with the hood at angleA and at angleB (NaN = not given).
    double shotAtA = Double.NaN, shotAtB = Double.NaN;
    // True when it is a launcher's hood (the launcher updates it; set by Launcher.hood).
    boolean inLauncher = false;
    // True when the launcher's speed (and so this hood) comes from a table with hood angles: the stick trims.
    boolean tableDriven = false;

    Actuator part;
    // The interlock's fence, in the hood's scale (+/- infinity = none), and the most power a motor hood
    // may use toward a bigger / smaller angle now (1 = no limit, 0 = brake): see Interlock.
    private double fenceLo = Double.NEGATIVE_INFINITY, fenceHi = Double.POSITIVE_INFINITY;
    private double upCap = 1, downCap = 1;
    // An interlock rule says: do not start the first move yet (the other part is in its zone).
    private boolean waitForRule = false;
    // posA, posB (live in the tuning menu), or null with a calibration table.
    private Tunable posA, posB;
    private double angleA = Double.NaN, angleB = Double.NaN;   // NaN, NaN = the angle is the position
    private double[][] calibration = null;                    // {position, angle} rows, or null
    private Tunable travelSec, kP, maxPower;
    private double startAt, stepAngle, byHandPerSec, trimMax, tolerance;
    // The angle by hand (NaN until START) and the trim added to the table's angle.
    private double manualAngle = Double.NaN;
    private double trimAmount = 0;
    // Set every loop by the launcher: the table sets the angle now (else manualAngle does).
    private boolean following = false;
    // The stick: the time of the last loop it was pushed (nanoseconds; -1 = let go).
    private long lastByHand = -1;
    // The time; tests use a pretend clock.
    LongSupplier clock = System::nanoTime;

    private Hood(Kind kind, String device, String motorId) {
        super(device);
        this.kind = kind;
        this.motorId = motorId;
    }

    /**
     * A hood on a standard (positional) servo. Its Tunables section is the name in CamelCase
     * ("hood" -> {@code Hood}).
     *
     * @param servo the servo's configuration name
     */
    public static Hood servo(String servo) {
        return new Hood(Kind.SERVO, servo, null);
    }

    /**
     * A hood on a motor with an encoder: it knows where the hood really is. {@code posA}, {@code posB}
     * (and calibration rows) are then encoder ticks, counted from where the hood is at INIT.
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors}), e.g. "gobilda-5203-117"
     */
    public static Hood motor(String motor, String motorId) {
        Motors.get(motorId);
        return new Hood(Kind.MOTOR, motor, motorId);
    }

    /**
     * A servo hood uses its servo's whole turn: positions 0 to 1 send the servo's full pulse range
     * (the part's, else 500-2500 us) instead of the FTC SDK's normal 600-2400 us. A goBILDA 2000 servo
     * then turns 300 degrees instead of 270. Turn it on BEFORE the Hood Finder (it uses the same range),
     * or find posA and posB again: the same position points somewhere else after the change.
     *
     * @return this hood
     * @throws IllegalStateException on a motor hood (it has no servo)
     */
    public Hood fullRange() {
        if (kind == Kind.MOTOR) throw new IllegalStateException("Robot.java: Hood.motor(\"" + device + "\") has no servo: fullRange() is for Hood.servo(...)");
        fullRange = true;
        return this;
    }

    /**
     * The pulse range (microseconds, {low, high}) its servo gets at INIT with {@link #fullRange()}, or
     * null for the SDK's normal range. The Hood Finder uses the same one.
     */
    public double[] pulseRangeUs() {
        return fullRange ? fullPulse() : null;
    }

    /** It turns the other way. The Hood Finder's numbers are for the hood as set up here. */
    public Hood reversed() {
        reversed = true;
        return this;
    }

    /**
     * For the simulator only: the launcher's shot angle (as in {@code Launcher.shotAngle}: degrees up from
     * the wheel shape's forward) with the hood at {@code angleA} and at {@code angleB}. The simulated shot
     * then leaves steeper or flatter as the hood moves (on a straight line between the two).
     *
     * @param atA the shot angle with the hood at angleA (posA), e.g. 80
     * @param atB the shot angle with the hood at angleB (posB), e.g. 55
     */
    public Hood shotAngles(double atA, double atB) {
        if (!(atA >= 0 && atA <= 180 && atB >= 0 && atB <= 180)) {
            throw new IllegalArgumentException("Robot.java: Hood.shotAngles(" + atA + ", " + atB + "): 0 to 180 degrees (0 forward, 90 up)");
        }
        shotAtA = atA;
        shotAtB = atB;
        return this;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        readCalibration(t, name());
        // Optional numbers: read only when written, so a section without them gets no "missing" note.
        double range = hi() - lo();
        startAt = t.has("startAngle") ? t.number("startAngle", 0) : angleAt(firstPosition());
        if (startAt < lo() - 1e-9 || startAt > hi() + 1e-9) {
            throw new IllegalStateException("Tunables." + name() + ".startAngle = " + startAt + " is outside the hood's range "
                    + Shape.n(lo()) + " to " + Shape.n(hi()));
        }
        stepAngle = t.has("stepAngle") ? Math.abs(t.number("stepAngle", 0)) : range / 25;
        byHandPerSec = t.has("byHandPerSec") ? Math.abs(t.number("byHandPerSec", 0)) : range / 2.5;
        trimMax = t.has("trimMax") ? Math.abs(t.number("trimMax", 0)) : range / 5;
        tolerance = t.has("tolerance") ? Math.abs(t.number("tolerance", 0)) : range / 50;
        if (kind == Kind.SERVO) {
            // travelSec not written: the parts catalog's speed for the servo from part(...), for the move
            // from stop to stop (a goBILDA torque servo turning 0.3 of its range: 0.34 s), else 0.5 s.
            startNote = "";
            double span = posA != null ? Math.abs(posB.get() - posA.get())
                    : Math.abs(calibration[calibration.length - 1][0] - calibration[0][0]);
            double full = catalogFullMoveSec();
            boolean fromCatalog = !t.has("travelSec") && !Double.isNaN(full);
            double catalogTravel = Math.max(0.05, Math.round(full * span * 100) / 100.0);
            travelSec = t.tunable("travelSec", fromCatalog ? catalogTravel : 0.5, 0.02).range(0, 5);
            if (fromCatalog) {
                t.fromCatalog("travelSec");
                startNote = defaultsNote(partId, java.util.Collections.singletonList("travelSec " + exact(catalogTravel) + " s from stop to stop"),
                        "Time it to check (Hood Finder, or by eye)");
            } else if (Double.isNaN(full) && t.isMarked("travelSec")) {
                startNote = generalServoNote("travelSec", 0.5);
                t.fromCatalog("travelSec");
            }
            Servo servo = devices.get(Servo.class, device);
            if (reversed) servo.setDirection(Servo.Direction.REVERSE);
            applyPulse(servo);   // .fullRange(): the servo's whole turn (nothing changes without it)
            part = new Actuator(servo, null);
        } else {
            kP = t.tunable("kP", 0.005, 0.001).range(0, 1);
            maxPower = t.tunable("maxPower", 0.5, 0.05).range(0, 1);
            // The encoder counts from here (the hood at the stop you call 0 ticks); power 0 until START.
            DcMotorEx m = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                    .brake().resetEncoder().runWithoutEncoder().done();
            part = new Actuator(null, m);
        }
    }

    /**
     * Reads posA/posB (+ angleA/angleB) or a calibration table, and checks it. Also used by the simulator
     * file and the Hood Finder (no hardware). Throws with a clear message when something is wrong.
     */
    void readCalibration(TunableSection t, String section) {
        double[][] rows = t.table("calibration");
        if (rows != null) {
            if (t.has("posA") || t.has("posB")) {
                throw new IllegalStateException("Tunables." + section + ": write calibration OR posA/posB, not both "
                        + "(calibration's first and last rows are the stops)");
            }
            calibration = checkRows(rows, section);
            posA = posB = null;
            return;
        }
        // The two stops are safety numbers (past them it pushes on a stop): never a default.
        String unit = kind == Kind.MOTOR ? "encoder ticks" : "servo position";
        t.require("posA", "the " + unit + " at one hard stop of the hood, a hair off it (Hood Finder)");
        t.require("posB", "the " + unit + " at the other hard stop (Hood Finder)");
        double step = kind == Kind.MOTOR ? 5 : 0.005;
        double top = kind == Kind.MOTOR ? 1e7 : 1;
        posA = t.tunable("posA", 0, step).range(kind == Kind.MOTOR ? -top : 0, top);
        posB = t.tunable("posB", 1, step).range(kind == Kind.MOTOR ? -top : 0, top);
        if (posA.get() == posB.get()) {
            throw new IllegalStateException("Tunables." + section + ": posA and posB are the same (" + posA.get()
                    + "): find both stops with the Hood Finder");
        }
        boolean hasA = t.has("angleA"), hasB = t.has("angleB");
        if (hasA != hasB) {
            throw new IllegalStateException("Tunables." + section + ": write both angleA and angleB (the hood's angle at "
                    + "each stop), or neither (then the angle is the " + unit + ")");
        }
        if (hasA) {
            angleA = t.number("angleA", 0);
            angleB = t.number("angleB", 1);
            if (angleA == angleB) throw new IllegalStateException("Tunables." + section + ": angleA and angleB must differ");
        } else {
            angleA = angleB = Double.NaN;
        }
    }

    // A calibration table: at least 2 rows of {position, angle}; sorted by position, the angles all go
    // the same way (up or down), so each angle has exactly one position.
    private double[][] checkRows(double[][] rows, String section) {
        if (rows.length < 2) throw new IllegalStateException("Tunables." + section + ".calibration needs at least 2 rows {position, angle}");
        double[][] sorted = new double[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null || rows[i].length != 2) {
                throw new IllegalStateException("Tunables." + section + ".calibration: every row is {position, angle} (2 numbers)");
            }
            if (kind == Kind.SERVO && (rows[i][0] < 0 || rows[i][0] > 1)) {
                throw new IllegalStateException("Tunables." + section + ".calibration: servo positions are 0 to 1 (" + rows[i][0] + ")");
            }
            sorted[i] = rows[i].clone();
        }
        java.util.Arrays.sort(sorted, (a, b) -> Double.compare(a[0], b[0]));
        int dir = 0;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i][0] == sorted[i - 1][0]) {
                throw new IllegalStateException("Tunables." + section + ".calibration: two rows at position " + sorted[i][0]);
            }
            int d = (int) Math.signum(sorted[i][1] - sorted[i - 1][1]);
            if (d == 0 || (dir != 0 && d != dir)) {
                throw new IllegalStateException("Tunables." + section + ".calibration: the angles must keep going the same "
                        + "way (all up or all down) as the position grows; check the rows near position " + sorted[i][0]);
            }
            dir = d;
        }
        return sorted;
    }

    // ----------------------------- position <-> angle -----------------------------

    // The calibration as rows {position, angle} sorted by position (the two stops, or the table).
    private double[][] rows() {
        if (calibration != null) return calibration;
        double a = posA.get(), b = posB.get();
        double aa = Double.isNaN(angleA) ? a : angleA, ab = Double.isNaN(angleB) ? b : angleB;
        return a < b ? new double[][] {{a, aa}, {b, ab}} : new double[][] {{b, ab}, {a, aa}};
    }

    /** The angle at a servo position (or tick count), blended between calibration rows, kept to the ends. */
    double angleAt(double position) {
        double[][] r = rows();
        if (position <= r[0][0]) return r[0][1];
        for (int i = 1; i < r.length; i++) {
            if (position <= r[i][0]) {
                double t = (position - r[i - 1][0]) / (r[i][0] - r[i - 1][0]);
                return r[i - 1][1] + (r[i][1] - r[i - 1][1]) * t;
            }
        }
        return r[r.length - 1][1];
    }

    /** The servo position (or tick count) for an angle, blended between rows, kept to the ends. */
    double positionAt(double angle) {
        double[][] r = rows();
        boolean up = r[r.length - 1][1] > r[0][1];
        if (up ? angle <= r[0][1] : angle >= r[0][1]) return r[0][0];
        for (int i = 1; i < r.length; i++) {
            if (up ? angle <= r[i][1] : angle >= r[i][1]) {
                double t = (angle - r[i - 1][1]) / (r[i][1] - r[i - 1][1]);
                return r[i - 1][0] + (r[i][0] - r[i - 1][0]) * t;
            }
        }
        return r[r.length - 1][0];
    }

    // The smallest and biggest angle (the hard limits).
    private double lo() {
        double[][] r = rows();
        return Math.min(r[0][1], r[r.length - 1][1]);
    }

    private double hi() {
        double[][] r = rows();
        return Math.max(r[0][1], r[r.length - 1][1]);
    }

    // posA (or the calibration's first row): where angleA is, the default startAngle.
    private double firstPosition() {
        return calibration != null ? calibration[0][0] : posA.get();
    }

    private double clamp(double angle) {
        return Math.max(lo(), Math.min(hi(), angle));
    }

    @Override
    void start() {
        // START: to its startAngle (never in INIT: nothing moves before START).
        manualAngle = startAt;
        part.setDegrees(startAt);
    }

    // ----------------------------- commands -----------------------------

    /**
     * Sends the hood to this angle (kept inside its hard limits). While the shot table sets the angle,
     * the table sets it again at the next loop: use {@link #trimBy(double)} then.
     *
     * @param angle the hood angle, in your scale (degrees, or the position without angleA/angleB)
     */
    public void setAngle(double angle) {
        needBuilt(part);
        if (Double.isNaN(angle)) return;
        touch();
        manualAngle = clamp(angle);
        if (!following) part.setDegrees(manualAngle);
    }

    /** Back to {@code startAngle} (Tunables): where it went at START. */
    public void goToStart() {
        setAngle(startAt);
    }

    /**
     * One step per count ({@code stepAngle}): +1 = a bigger angle, -1 = smaller. While the shot table
     * sets the angle it changes the hood trim; else it moves the hood. For your own buttons:
     * {@code g2.y.press(() -> robot.launcher.hood().step(+1))}.
     *
     * @param steps e.g. +1 or -1
     */
    public void step(int steps) {
        needBuilt(part);
        // The trim only while the table sets the angle NOW (a goal seen): with no goal yet, a trim would
        // change nothing you can see, so the hood itself moves.
        if (following) trimBy(steps * stepAngle);
        else setAngle((Double.isNaN(manualAngle) ? startAt : manualAngle) + steps * stepAngle);
    }

    /**
     * By hand from a stick, every loop: -1 to 1 (up = a bigger angle), {@code byHandPerSec} at full push.
     * While the shot table sets the angle it moves the hood trim; else the hood. 0 does nothing.
     *
     * @param amount the stick, -1 to 1
     */
    public void byHand(double amount) {
        if (part == null) return;
        long now = clock.getAsLong();
        if (amount == 0 || Double.isNaN(amount)) {
            lastByHand = -1;
            return;
        }
        // Seconds since the last loop (0 at the first push; at most 0.1 after a pause), so the speed is
        // the same whether the loop is fast or slow.
        double dt = lastByHand < 0 ? 0 : Math.min(0.1, (now - lastByHand) / 1e9);
        lastByHand = now;
        double change = Math.max(-1, Math.min(1, amount)) * byHandPerSec * dt;
        if (change == 0) return;
        if (following) trimBy(change);   // see step(): the trim only while the table sets the angle now
        else setAngle((Double.isNaN(manualAngle) ? startAt : manualAngle) + change);
    }

    /**
     * Adds to the hood trim (added to the shot table's angle), kept to +/- {@code trimMax}. Starts at 0 at
     * every INIT. Only used while the angle comes from the shot table.
     *
     * @param amount e.g. +1 (degree) or -0.5
     */
    public void trimBy(double amount) {
        if (Double.isNaN(amount)) return;
        setTrim(trimAmount + amount);
    }

    /** Sets the hood trim (0 = none), kept to +/- {@code trimMax}. */
    public void setTrim(double amount) {
        if (Double.isNaN(amount)) return;
        trimAmount = Math.max(-trimMax, Math.min(trimMax, amount));
    }

    /** The hood trim back to 0 (the launcher's double-tap D-pad down does this too). */
    public void resetTrim() {
        trimAmount = 0;
    }

    // ----------------------------- questions -----------------------------

    /** The hood trim now (0 = none), in the hood's scale. */
    public double trim() {
        return trimAmount;
    }

    /**
     * Where the hood is now: measured (a motor) or estimated from the time (a servo), like
     * {@code Turret.angle()}. NaN before its first move (START).
     */
    public double angle() {
        return part == null ? Double.NaN : part.now();
    }

    /** The angle the hood is going to (or is at); NaN before its first move (START). */
    public double targetAngle() {
        return part == null ? Double.NaN : part.getTargetDegrees();
    }

    /** True when the hood is (a motor) or should be (a servo, by time) at its target angle. */
    public boolean isAtTarget() {
        return part != null && (missing || part.isDone());
    }

    /** True while the angle comes from the shot table (the launcher's distance), false when it is set by hand. */
    public boolean followsTable() {
        return following;
    }

    /** The smallest angle it may go to (a hard limit). NaN before INIT. */
    public double minAngle() {
        return part == null ? Double.NaN : lo();
    }

    /** The biggest angle it may go to (a hard limit). NaN before INIT. */
    public double maxAngle() {
        return part == null ? Double.NaN : hi();
    }

    /** Tunables {@code startAngle}: where it goes at START (default angleA). NaN before INIT. */
    public double startAngle() {
        return part == null ? Double.NaN : startAt;
    }

    /** True with angleA/angleB or a calibration table (real angles); false when the angle is the position. */
    public boolean usesAngles() {
        return calibration != null || !Double.isNaN(angleA);
    }

    /** True for {@code Hood.motor}, false for {@code Hood.servo}. */
    public boolean isMotor() {
        return kind == Kind.MOTOR;
    }

    /** True when Robot.java says {@code reversed()}. */
    public boolean isReversed() {
        return reversed;
    }

    /** The hood as an {@link AngleDevice} (what the launcher drives), for anything this module does not offer. */
    public AngleDevice part() {
        return part;
    }

    // ----------------------------- every loop -----------------------------

    /**
     * Every loop, from the launcher, before its setDistance: whether the table sets the angle now. When
     * it does not, the hood holds its angle by hand.
     */
    void loop(boolean fromTable) {
        if (part == null) return;
        following = fromTable;
        if (!fromTable && !Double.isNaN(manualAngle)) part.setDegrees(manualAngle);
    }

    /** After the launcher's setDistance: the angle by hand follows the table's, so leaving the table does not jump. */
    void afterTable() {
        double now = part.getTargetDegrees();
        if (!Double.isNaN(now)) manualAngle = now;
    }

    @Override
    public void update() {
        // In a launcher, the launcher updates it (with the table's angle). On its own: hold the angle by hand.
        if (part == null || inLauncher) return;
        loop(false);
        part.update();
    }

    @Override
    public void stop() {
        if (part != null) part.stop();
    }

    @Override
    void pause() {
        if (part != null) part.stop();   // a motor hood stops; a servo holds where it is
    }

    @Override
    void halt() {
        // A cancelled action: a motor hood holds where it is now (it must not go on to the action's
        // angle). A servo is already where it was sent. Not the driver's command, so no touch().
        if (part == null || kind != Kind.MOTOR) return;
        double now = part.now();
        if (Double.isNaN(now)) return;
        manualAngle = clamp(now);
        part.setDegrees(manualAngle);
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        // In a launcher, the launcher's telemetry shows it (through part()).
        if (part != null && !inLauncher) part.addTelemetry(t);
    }

    // "12.5 deg (table, trim +1.0)  moving", for the Driver Station.
    String words() {
        double a = part.getTargetDegrees();
        if (Double.isNaN(a)) return "not moved yet (goes to " + num(startAt) + " at START)";
        String how = following ? "table" + (trimAmount != 0 ? ", trim " + (trimAmount > 0 ? "+" : "") + num(trimAmount) : "") : "by hand";
        boolean atEnd = a <= lo() + 1e-9 || a >= hi() - 1e-9;
        return num(a) + " (" + how + ")" + (atEnd ? " at its stop" : "") + (part.isDone() ? "" : "  moving");
    }

    // A number in the hood's scale: "12.5 deg", a servo position "0.420", ticks "350".
    private String num(double v) {
        if (usesAngles()) return fmt("%.1f deg", v);
        return kind == Kind.MOTOR ? fmt("%.0f ticks", v) : fmt("%.3f", v);
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        plan.operator().leftStickY.use(this::byHand)
                .says(tableDriven ? "hood trim while it follows the table, else the hood (up = bigger angle)"
                        : "hood angle (up = bigger angle)");
    }

    @Override
    String toolFor(String field) {
        if (field.startsWith("pos") || field.startsWith("angle")) return "the Hood Finder, docs/guides/finders.md#hood-finder";
        return null;
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        if (!startNote.isEmpty()) out.add(startNote);
        return out;
    }

    @Override
    String checkPart(String id) {
        return kind == Kind.MOTOR ? super.checkPart(id) : Parts.servo(id);
    }

    @Override
    List<String> deviceNames() {
        return java.util.Collections.singletonList(device);
    }

    // ----------------------------- the simulator -----------------------------

    // A servo position as the simulated servo sees it: a reversed servo's 0.2 is really 0.8.
    private double physical(double position) {
        return reversed ? 1 - position : position;
    }

    // Reads the calibration for the simulator file (no hardware); false when it is missing or wrong.
    private boolean simCalibration(TunableSection t) {
        try {
            readCalibration(t, name());
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    void simLines(List<String> out, TunableSection t) {
        boolean ok = simCalibration(t);
        if (kind == Kind.MOTOR) {
            double[][] r = ok ? rows() : new double[][] {{0, 0}, {500, 1}};
            out.add(fmt("motor.%s = lift, %s, min=%.0f, max=%.0f, gravity=0", device, motorWords(motorId, "gobilda-5203-117"), r[0][0], r[r.length - 1][0])
                    + simTodo(motorId));
            lookLines(out, t);
            return;
        }
        double h = !ok ? 0.5 : physical(positionAt(t.has("startAngle") ? t.number("startAngle", 0) : angleAt(firstPosition())));
        h = Math.max(0, Math.min(1, h));
        if (partId != null) out.add(fmt("servo.%s = %.3f, %s", device, h, servoWords(partId, "gobilda-2000-torque")) + pulseWords() + simTodo(partId));
        else out.add(fmt("servo.%s = %.3f, part=gobilda-2000-torque", device, h) + pulseWords() + "   # TODO the real servo");
        lookLines(out, t);   // its arm from looks(...); without looks the hood is not drawn
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    @Override
    boolean makesDefaultShape() {
        return false;   // without looks(...) it draws nothing: a shape cannot ride on it
    }

    // An arm shape (Shape.arm()) swings with the hood: its degrees are the hood's angles (Tunables angleA and
    // angleB, or the calibration table, taken as a straight line between its end rows).
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (!s.type.equals("arm")) {
            throw new IllegalStateException("Robot.java: " + name() + " is a hood: it swings an arm shape (Shape.arm()), not a " + s.type);
        }
        if (!simCalibration(t) || !usesAngles()) {
            throw new IllegalStateException("Robot.java: " + name() + "'s arm shape needs the hood's angles in degrees: Tunables."
                    + name() + " posA, posB with angleA, angleB (or a calibration table of {position, angle})");
        }
        double[][] r = rows();
        double p0 = r[0][0], p1 = r[r.length - 1][0], a0 = r[0][1], a1 = r[r.length - 1][1];
        if (kind == Kind.MOTOR) {
            // The motor counts from its min stop (the first row): the shape starts at that row's angle.
            return fmt("motor=%s, ticksPerDeg=%.3f, start=%s", device, (p1 - p0) / (a1 - a0), Shape.n(a0));
        }
        // The angle at the simulated servo's raw 0 and 1 (a reversed servo's 0 is the code's 1).
        double perUnit = (a1 - a0) / (physical(p1) - physical(p0));
        double at0 = a0 - perUnit * physical(p0);
        return "servo=" + device + ", pos0=" + Shape.n(at0) + ", pos1=" + Shape.n(at0 + perUnit);
    }

    /**
     * The words for the launcher's shoot. line: ", hood=hood, hoodAt0=A, hoodAt1=B" (the shot angle with
     * the servo at 0 and at 1), or "" without {@link #shotAngles}, with a motor hood, or with the numbers
     * missing. A calibration table is taken as a straight line between its end rows (close enough here).
     */
    String shootWords(TunableSection t) {
        if (Double.isNaN(shotAtA) || kind == Kind.MOTOR || !simCalibration(t)) return "";
        double pa = physical(firstPosition()), pb = physical(lastPosition());
        double perUnit = (shotAtB - shotAtA) / (pb - pa);
        double at0 = shotAtA - perUnit * pa;
        return ", hood=" + device + ", hoodAt0=" + Shape.n(Math.round(at0 * 100) / 100.0)
                + ", hoodAt1=" + Shape.n(Math.round((at0 + perUnit) * 100) / 100.0);
    }

    // posB (or the calibration's last row).
    private double lastPosition() {
        return calibration != null ? calibration[calibration.length - 1][0] : posB.get();
    }

    /** The shot angle with the hood at startAngle (for the shoot. line's angle=), NaN without shotAngles. */
    double homeShotAngle(TunableSection t) {
        if (Double.isNaN(shotAtA) || !simCalibration(t)) return Double.NaN;
        double a = angleAt(firstPosition()), b = angleAt(lastPosition());
        double h = t.has("startAngle") ? t.number("startAngle", a) : a;
        return shotAtA + (h - a) * (shotAtB - shotAtA) / (b - a);
    }

    // ----------------------------- interlock rules -----------------------------

    @Override boolean zoneCapable() { return true; }

    @Override
    String zoneUnit() {
        if (usesAngles()) return "deg";
        return kind == Kind.MOTOR ? "ticks" : "";
    }

    @Override double zoneMin() { return part == null ? Double.NEGATIVE_INFINITY : lo(); }
    @Override double zoneMax() { return part == null ? Double.POSITIVE_INFINITY : hi(); }

    @Override
    double[] zoneSweep() {
        return part == null ? new double[] {zoneMin(), zoneMax()} : part.sweep();
    }

    @Override double zoneTarget() { return part == null ? Double.NaN : part.asked; }

    @Override
    void zoneFence(double lo, double hi) {
        zoneFence(lo, hi, 1, 1);
    }

    @Override
    void zoneFence(double lo, double hi, double upPower, double downPower) {
        fenceLo = lo;
        fenceHi = hi;
        upCap = Double.isNaN(upPower) ? 1 : Math.max(0, Math.min(1, upPower));
        downCap = Double.isNaN(downPower) ? 1 : Math.max(0, Math.min(1, downPower));
    }

    // A motor hood coasts a little after its power is cut: the interlock measures its speed (encoder).
    @Override boolean zoneCoasts() { return kind == Kind.MOTOR; }

    // A servo hood not sent anywhere yet may be anywhere: its first move waits while the rule says so.
    @Override boolean zoneKnown() { return part == null || missing || !part.unknown(); }

    @Override String zoneUnknownWords() { return "not sent anywhere yet: it goes to its startAngle at START"; }

    @Override
    void zoneWait(boolean wait) {
        waitForRule = wait;
    }

    @Override
    double zoneDefaultMargin() {
        return part == null ? 0 : (hi() - lo()) / 50;
    }

    // ----------------------------- the device -----------------------------

    /**
     * The hood as an {@link AngleDevice}, in the hood's own angles: a servo (time estimate) or a motor
     * (encoder, a simple P control that never pushes past the stops).
     */
    final class Actuator implements AngleDevice {
        private final Servo servo;
        private final DcMotorEx motor;
        private double target = Double.NaN;
        // A servo move: where it may have been when the move started (lowest and highest angle: the same
        // number when it was known, the whole range before the first move), when it started (s), and when
        // it should be there (s). A servo cannot say where it is, so this is all we know.
        private double fromLo = Double.NaN, fromHi = Double.NaN, moveStart = 0, doneAt = 0;
        private double sent = Double.NaN;    // the servo position sent last
        private double power = 0;            // the motor power sent last

        Actuator(Servo servo, DcMotorEx motor) {
            this.servo = servo;
            this.motor = motor;
        }

        private double nowSec() {
            return clock.getAsLong() / 1e9;
        }

        // The angle asked for (inside the hard limits); the interlock's fence may hold it short (see apply).
        private double asked = Double.NaN;

        @Override
        public void setDegrees(double angle) {
            if (Double.isNaN(angle)) return;
            asked = clamp(angle);
            apply(Math.max(fenceLo, Math.min(fenceHi, asked)));
        }

        // Starts a move to this angle (already inside the limits and the fence).
        void apply(double a) {
            if (a == target) return;   // the same angle again (called every loop): keep the move under way
            // An interlock rule says wait: a servo hood not sent anywhere yet may be anywhere, so its first
            // move could sweep through the other part. It starts when the rule lets it (update() tries again).
            if (servo != null && Double.isNaN(target) && waitForRule) return;
            if (servo != null) {
                double now = nowSec();
                // Where it may be now: the whole range before the first move (nobody knows where a servo
                // is before its first command); during a move from a known place, the estimate; during a
                // move from "anywhere" (the first one), still anywhere between: a new target in the middle
                // of the first move must not forget that.
                double[] may = startedKnown() ? new double[] {now(), now()} : sweep();
                // Time: the share of the whole range from the furthest place it may be x travelSec.
                double far = Math.max(Math.abs(a - may[0]), Math.abs(a - may[1]));
                double share = Math.min(1, far / (hi() - lo()));
                fromLo = may[0];
                fromHi = may[1];
                moveStart = now;
                doneAt = now + share * travelSec.get();
                target = a;
                write();
            } else {
                target = a;
            }
        }

        // Sends the servo position for the target (again when a live calibration change moves it).
        private void write() {
            double p = Math.max(0, Math.min(1, positionAt(target)));
            if (p != sent) {
                servo.setPosition(p);
                sent = p;
            }
        }

        @Override
        public double getTargetDegrees() {
            return target;
        }

        /** Where it is now: the encoder (a motor), or the time estimate (a servo). */
        double now() {
            if (motor != null) return angleAt(motor.getCurrentPosition());
            if (Double.isNaN(target)) return Double.NaN;
            double t = nowSec();
            if (t >= doneAt || doneAt <= moveStart) return target;
            // From an unknown place (the first move): no better guess than where it is going.
            if (Double.isNaN(fromLo) || fromLo != fromHi) return target;
            return fromLo + (target - fromLo) * (t - moveStart) / (doneAt - moveStart);
        }

        @Override
        public boolean isDone() {
            if (Double.isNaN(target)) return false;
            // Held short by an interlock's fence: not where it was asked to be.
            if (!Double.isNaN(asked) && Math.abs(asked - target) > 1e-9) return false;
            if (motor != null) return Math.abs(now() - target) <= tolerance;
            return nowSec() >= doneAt;
        }

        // True when the servo's place is known as one point: once a move is over, or during a move that
        // started from a known place (then its estimate). False before the first move and during a move
        // that started from "anywhere".
        private boolean startedKnown() {
            if (Double.isNaN(target)) return false;
            if (nowSec() >= doneAt) return true;
            return !Double.isNaN(fromLo) && fromLo == fromHi;
        }

        /** True while a servo hood has never been sent anywhere (it may be anywhere in its range). */
        boolean unknown() {
            return servo != null && Double.isNaN(target);
        }

        /**
         * Where it may be now, {lowest, highest}: a motor where its encoder says; a servo anywhere between
         * where its move may have started and where it goes, until it should be there (the whole range
         * before its first move, and during it).
         */
        double[] sweep() {
            if (Double.isNaN(target)) return new double[] {lo(), hi()};
            if (motor != null) {
                double n = now();
                return new double[] {n, n};
            }
            if (nowSec() >= doneAt) return new double[] {target, target};
            double a = Double.isNaN(fromLo) ? lo() : fromLo, b = Double.isNaN(fromHi) ? hi() : fromHi;
            return new double[] {Math.min(a, target), Math.max(b, target)};
        }

        @Override
        public void update() {
            // The interlock's fence may have moved: go on toward the angle asked for, as far as it allows.
            if (!Double.isNaN(asked)) apply(Math.max(fenceLo, Math.min(fenceHi, asked)));
            if (Double.isNaN(target)) return;
            if (servo != null) {
                write();
                return;
            }
            // Motor: power toward the target's tick count, kept to maxPower. Never further past a stop.
            double ticks = motor.getCurrentPosition();
            double goal = positionAt(target);
            double p = Math.max(-maxPower.get(), Math.min(maxPower.get(), kP.get() * (goal - ticks)));
            double[][] r = rows();
            if ((ticks <= r[0][0] && p < 0) || (ticks >= r[r.length - 1][0] && p > 0)) p = 0;
            if (Math.abs(now() - target) <= tolerance / 2) p = 0;   // close enough: rest (no buzzing)
            // An interlock rule's caps (see Interlock: it brakes in time near its fence). They are for
            // the ANGLE going up or down; with angles that fall as the ticks grow, + power lowers the angle.
            boolean anglesRise = r[r.length - 1][1] > r[0][1];
            double capUp = anglesRise ? upCap : downCap, capDown = anglesRise ? downCap : upCap;
            p = Math.max(-capDown, Math.min(capUp, p));
            setPower(p);
        }

        private void setPower(double p) {
            if (p != power) {
                motor.setPower(p);
                power = p;
            }
        }

        @Override
        public void stop() {
            if (motor != null) setPower(0);
        }

        @Override
        public String name() {
            return Hood.this.name();
        }

        @Override
        public void addTelemetry(RobotTelemetry t) {
            t.line(name(), words());
            if (motor != null) t.debug(name() + " ticks", "%d", motor.getCurrentPosition());
            else if (Double.isNaN(sent)) t.debug(name() + " pos", "-");   // nothing sent yet
            else t.debug(name() + " pos", "%.3f", sent);
        }
    }
}

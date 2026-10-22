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
 * {@link #setAngle(double)}, {@link #home()}.
 *
 * <p>Optional Tunables (defaults scale with the range, so they suit degrees, positions and ticks):
 * {@code homeAngle} (where it goes at START; default angleA), {@code stepAngle} (1/25 of the range),
 * {@code byHandPerSec} (the range in 2.5 s), {@code trimMax} (1/5 of the range). A motor hood:
 * {@code kP} (0.005 power per tick), {@code maxPower} (0.5), {@code tolerance} (1/50 of the range). Its
 * encoder counts from where it is at INIT: start the robot with the hood at the stop you call 0 ticks.
 * Nothing moves in INIT: the hood goes to {@code homeAngle} at START.
 *
 * Docs: docs/modules/robot.md (section "Hood"), lesson 9 (docs/tutorials/start/09-shooter-and-camera.md)
 */
public final class Hood extends Module<Hood> {
    private enum Kind { SERVO, MOTOR }

    private final Kind kind;
    private final String motorId;
    private boolean reversed = false;
    // For the simulator: the launcher's shot angle with the hood at angleA and at angleB (NaN = not given).
    double shotAtA = Double.NaN, shotAtB = Double.NaN;
    // True when it is a launcher's hood (the launcher updates it; set by Launcher.hood).
    boolean inLauncher = false;
    // True when the launcher's speed (and so this hood) comes from a table with hood angles: the stick trims.
    boolean tableDriven = false;

    Actuator part;
    // posA, posB (live in the tuning menu), or null with a calibration table.
    private Tunable posA, posB;
    private double angleA = Double.NaN, angleB = Double.NaN;   // NaN, NaN = the angle is the position
    private double[][] calibration = null;                    // {position, angle} rows, or null
    private Tunable travelSec, kP, maxPower;
    private double home, stepAngle, byHandPerSec, trimMax, tolerance;
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
        home = t.has("homeAngle") ? t.number("homeAngle", 0) : angleAt(firstPosition());
        if (home < lo() - 1e-9 || home > hi() + 1e-9) {
            throw new IllegalStateException("Tunables." + name() + ".homeAngle = " + home + " is outside the hood's range "
                    + Shape.n(lo()) + " to " + Shape.n(hi()));
        }
        stepAngle = t.has("stepAngle") ? Math.abs(t.number("stepAngle", 0)) : range / 25;
        byHandPerSec = t.has("byHandPerSec") ? Math.abs(t.number("byHandPerSec", 0)) : range / 2.5;
        trimMax = t.has("trimMax") ? Math.abs(t.number("trimMax", 0)) : range / 5;
        tolerance = t.has("tolerance") ? Math.abs(t.number("tolerance", 0)) : range / 50;
        if (kind == Kind.SERVO) {
            travelSec = t.tunable("travelSec", 0.5, 0.02).range(0, 5);
            Servo servo = devices.get(Servo.class, device);
            if (reversed) servo.setDirection(Servo.Direction.REVERSE);
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

    // posA (or the calibration's first row): where angleA is, the default home.
    private double firstPosition() {
        return calibration != null ? calibration[0][0] : posA.get();
    }

    private double clamp(double angle) {
        return Math.max(lo(), Math.min(hi(), angle));
    }

    @Override
    void start() {
        // START: to its home angle (never in INIT: nothing moves before START).
        manualAngle = home;
        part.setDegrees(home);
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

    /** Back to {@code homeAngle} (Tunables). */
    public void home() {
        setAngle(home);
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
        if (tableDriven) trimBy(steps * stepAngle);
        else setAngle((Double.isNaN(manualAngle) ? home : manualAngle) + steps * stepAngle);
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
        if (tableDriven) trimBy(change);
        else setAngle((Double.isNaN(manualAngle) ? home : manualAngle) + change);
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

    /** The angle the hood is going to (or is at); NaN before its first move (START). */
    public double angle() {
        return part == null ? Double.NaN : part.getTargetDegrees();
    }

    /** Where the hood is now: measured (a motor) or estimated from the time (a servo). NaN before START. */
    public double currentAngle() {
        return part == null ? Double.NaN : part.now();
    }

    /** True when the hood is (a motor) or should be (a servo, by time) at its angle. */
    public boolean isArrived() {
        return part != null && part.isDone();
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

    /** Tunables {@code homeAngle}: where it goes at START. NaN before INIT. */
    public double homeAngle() {
        return part == null ? Double.NaN : home;
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
    public void addTelemetry(RobotTelemetry t) {
        // In a launcher, the launcher's telemetry shows it (through part()).
        if (part != null && !inLauncher) part.addTelemetry(t);
    }

    // "12.5 deg (table, trim +1.0)  moving", for the Driver Station.
    String words() {
        double a = part.getTargetDegrees();
        if (Double.isNaN(a)) return "not moved yet (goes to " + num(home) + " at START)";
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
                .says(tableDriven ? "hood trim (up = bigger angle)" : "hood angle (up = bigger angle)");
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
            out.add(fmt("motor.%s = lift, part=%s, min=%.0f, max=%.0f, gravity=0", device, motorId, r[0][0], r[r.length - 1][0]));
            return;
        }
        double h = !ok ? 0.5 : physical(positionAt(t.has("homeAngle") ? t.number("homeAngle", 0) : angleAt(firstPosition())));
        h = Math.max(0, Math.min(1, h));
        if (partId != null) out.add(fmt("servo.%s = %.3f, part=%s", device, h, partId));
        else out.add(fmt("servo.%s = %.3f, part=gobilda-2000-torque   # TODO the real servo", device, h));
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

    /** The shot angle with the hood at home (for the shoot. line's angle=), NaN without shotAngles. */
    double homeShotAngle(TunableSection t) {
        if (Double.isNaN(shotAtA) || !simCalibration(t)) return Double.NaN;
        double a = angleAt(firstPosition()), b = angleAt(lastPosition());
        double h = t.has("homeAngle") ? t.number("homeAngle", a) : a;
        return shotAtA + (h - a) * (shotAtB - shotAtA) / (b - a);
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
        // A servo move: where it started (angle), when (s), and when it should be there (s).
        private double moveFrom = Double.NaN, moveStart = 0, doneAt = 0;
        private double sent = Double.NaN;    // the servo position sent last
        private double power = 0;            // the motor power sent last

        Actuator(Servo servo, DcMotorEx motor) {
            this.servo = servo;
            this.motor = motor;
        }

        private double nowSec() {
            return clock.getAsLong() / 1e9;
        }

        @Override
        public void setDegrees(double angle) {
            if (Double.isNaN(angle)) return;
            double a = clamp(angle);
            if (a == target) return;   // the same angle again (called every loop): keep the move under way
            if (servo != null) {
                double now = nowSec();
                boolean unknown = Double.isNaN(target);
                double from = unknown ? a : now();
                // Time: the share of the whole range this move covers x travelSec (the first move: all of it,
                // since nobody knows where a servo is before its first command).
                double share = unknown ? 1 : Math.abs(a - from) / (hi() - lo());
                moveFrom = from;
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
            if (Double.isNaN(moveFrom) || t >= doneAt || doneAt <= moveStart) return target;
            return moveFrom + (target - moveFrom) * (t - moveStart) / (doneAt - moveStart);
        }

        @Override
        public boolean isDone() {
            if (Double.isNaN(target)) return false;
            if (motor != null) return Math.abs(now() - target) <= tolerance;
            return nowSec() >= doneAt;
        }

        @Override
        public void update() {
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

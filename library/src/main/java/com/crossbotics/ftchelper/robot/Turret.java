package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.crossbotics.ftchelper.hardware.MotorSetup;
import com.crossbotics.ftchelper.mechanism.PositionServo;
import com.crossbotics.ftchelper.match.MatchMemory;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.util.Angles;
import com.crossbotics.ftchelper.vision.TargetObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/**
 * A turret that turns inside its limits (or round and round, with a slip ring), by hand or following
 * the goal with a {@link Camera} or the odometry. Angles in degrees, counterclockwise +, 0 = straight
 * forward.
 *
 * <pre>{@code
 * // Robot.java: what turns it (one of these), then the options
 * turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));   // a motor
 * turret = add(Turret.servo("turret").follows(camera));                                    // a standard servo
 * turret = add(Turret.crServo("turret").encoder("turret_enc", 8192).follows(camera));      // CR servo + Through Bore
 * turret = add(Turret.crServo("turret").analogEncoder("turret_pos").follows(camera));      // CR servo + absolute encoder
 *     // options: .homeSwitch("turret_home")  .continuous()  .reversed()  .cameraOnBody()
 *
 * public static class Turret {                                // Tunables.java
 *     public static double min = -90, max = 90;              // its range: it never turns past these
 *     public static double startAngle = 0;                   // where it points at INIT (no switch)
 *     public static double kP = 0.02, kI = 0, kD = 0.001;
 *     public static double maxPower = 0.8, tolerance = 1.0, cameraOffset = 0;
 * }
 * }</pre>
 *
 * <p><b>How it knows its angle</b> (every way, with the Tunables numbers it needs, is in
 * docs/guides/turret-setup.md):
 * <ul>
 *   <li><b>Encoder + start position</b> (no switch): it trusts {@code startAngle} (default 0 = straight
 *       forward) at INIT. Put the turret there by hand before INIT (a mark, a jig): if it is not, every
 *       angle is off by the difference. The INIT screen says so.</li>
 *   <li><b>Home switch</b> ({@link #homeSwitch}, a touch sensor or a magnetic switch): at START it turns
 *       slowly ({@code homingPower}, toward {@code homeAngle} from {@code startAngle}) until the switch
 *       closes; there it is {@code homeAngle}. Every later pass over the switch fixes any drift. Following
 *       and "aimed" wait until homed. Nothing moves in INIT (the game rules want a robot standing still
 *       after INIT, and the library never moves before START).</li>
 *   <li><b>Absolute encoder</b> ({@link #analogEncoder}): it knows its angle at once ({@code encoderOffset}
 *       = the reading at 0).</li>
 *   <li><b>Servo</b> ({@link #servo}): its angle is where it was sent ({@code posA/angleA, posB/angleB});
 *       "arrived" is a time estimate ({@code travelSec}).</li>
 * </ul>
 *
 * <p><b>From autonomous to TeleOp:</b> an encoder counts from where it was at INIT, and TeleOp's INIT
 * starts it again. So the angle at the end of every program is remembered (until the robot app restarts,
 * at most 5 minutes) and TeleOp starts from it instead of {@code startAngle} (and does not home again).
 * Autonomous always starts from {@code startAngle} or the switch. {@code keepAngle = false} turns this off.
 *
 * <p><b>Range</b>: {@code min}/{@code max} (e.g. -90/90, -180/180, -190/190: past 180 it picks the way
 * round that never passes a limit), or {@link #continuous()} for a slip ring (no limits, always the
 * shortest way).
 *
 * <p><b>Follow mode</b> ({@link #followMode()}, on from START when {@link #follows} or
 * {@link #followsOdometry} is used): every loop it turns toward the goal. The camera first; when the
 * camera does not see the goal and the robot has odometry and {@code Tunables.Aim.goalX, goalY}, toward
 * the goal worked out from where the robot is (see {@link Aim}). The Driver Station says which. A goal
 * just past the range: it waits at the limit and the Driver Station says "goal out of turret range".
 * A goal far past it (more than {@code outOfRangeHold}, 10 degrees, past a limit: behind the robot) from
 * the odometry: it holds where it points, so it does not swing from one limit to the other as the
 * heading wobbles, until the goal is back within 5 degrees of the range. The stick takes over while it
 * is moved, then following goes on.
 *
 * <p><b>Aimed</b> ({@link #isAimed()}) while following means aimed at the goal NOW: the goal is seen
 * this moment (camera or odometry), the turret is homed and in range, not held by an interlock rule
 * ({@link Interlock}), and it points within the tolerance. A launcher gated on it never fires after
 * the goal is lost.
 *
 * <p>A following turret and whole-robot aim ({@code robot.aimMode()}) are separate modes: use one (see
 * {@link Aim}).
 *
 * <p>Suggested buttons: operator right stick X turns it by hand; operator BACK switches following
 * on and off.
 *
 * Docs: docs/modules/robot.md (section "Turret"), docs/guides/turret-setup.md
 */
public final class Turret extends Module<Turret> {
    /** What turns the turret. */
    enum Kind { MOTOR, SERVO, CR_SERVO }

    private final Kind kind;
    private final String motorId;
    private double gearRatio = 1;
    private boolean reversed = false, encoderReversed = false;
    private boolean continuous = false;
    // A home switch's configuration name (null = none); a CR servo's encoder (null = none).
    private String switchName = null;
    private String encoderName = null;
    private double encoderCountsPerRev = 0;
    private boolean analog = false;
    private Camera camera = null;
    private boolean cameraOnTurret = true;
    // followsOdometry(): follow the goal from the odometry alone (no camera).
    private boolean odometryOnly = false;
    // What the following used last loop (for the Driver Station).
    private Aim.Source source = Aim.Source.NONE;
    private double cameraOffsetDeg = 0;
    private final Mode follow = new Mode("Turret follow", false);
    com.crossbotics.ftchelper.launcher.Turret part;
    private Tunable kP, kI, kD;
    private boolean stickMoving = false;
    private double stickPower = 0;
    private double min, max;
    // The odometry's goal is far past the range (behind the robot): hold, do not swing limit to limit.
    private boolean goalBehind = false;
    private double holdPastDeg = 10;
    // Homing and the start angle.
    private boolean hasSwitch = false;
    private double startAngle = 0, homeAngle = 0, homeDirection = 1;
    // The INIT note when startAngle was not written and 0 is outside the range ("" = none).
    private String startNote = "";
    // "Turret: defaults from the catalog (travelSec ...)": a servo turret with part(...) and no travelSec.
    private String travelNote = "";
    // "Turret: using the default range -90 to 90 deg ...": min and max not written ("" = written).
    private String rangeNote = "";
    // Where the angle at INIT came from, for the INIT screen.
    private String angleFrom = "";
    private double toleranceDeg = 1;

    // The angle at the end of the last program, per turret (device name): {angle, System.nanoTime()}.
    // "static": one copy for the whole robot app, so TeleOp sees what autonomous left. See class notes.
    private static final Map<String, double[]> REMEMBERED = new HashMap<>();

    private Turret(String device, Kind kind, String motorId) {
        super(device);
        if (motorId != null) Motors.withCounts(motorId, "Turret.motor(\"" + device + "\", ...)");
        this.kind = kind;
        this.motorId = motorId;
    }

    /**
     * A turret on this motor (its own encoder counts the angle).
     *
     * @param motor   the motor's configuration name
     * @param motorId the motor's parts-catalog id (see {@link Motors})
     */
    public static Turret motor(String motor, String motorId) {
        return new Turret(motor, Kind.MOTOR, motorId);
    }

    /**
     * A turret on a standard (position) servo: it goes to an angle by itself, no encoder and no homing.
     * Tunables: {@code posA, angleA, posB, angleB} (two servo positions and the turret's angle at each,
     * measured with the Servo Finder), {@code travelSec} (seconds from posA to posB). Its range is the
     * angles between them (or {@code min}/{@code max}, if narrower).
     *
     * @param servo the servo's configuration name
     */
    public static Turret servo(String servo) {
        return new Turret(servo, Kind.SERVO, null);
    }

    /**
     * A servo turret uses its servo's whole turn: positions 0 to 1 send the servo's full pulse range (the
     * part's, else 500-2500 us) instead of the FTC SDK's normal 600-2400 us. A goBILDA 2000 servo then
     * turns 300 degrees instead of 270 (more turret angle). Turn it on BEFORE measuring posA/posB with
     * the Servo Finder (Y = full range there too): the same position points somewhere else after the change.
     *
     * @return this turret
     * @throws IllegalStateException on a motor or CR servo turret
     */
    public Turret fullRange() {
        if (kind != Kind.SERVO) {
            throw new IllegalStateException("Robot.java: " + name() + ": fullRange() is for Turret.servo(...) (a positional servo)");
        }
        fullRange = true;
        return this;
    }

    /**
     * A turret on a continuous-rotation (CR) servo. It needs an encoder for its angle:
     * {@link #encoder(String, double)} or {@link #analogEncoder(String)}.
     *
     * @param servo the CR servo's configuration name
     */
    public static Turret crServo(String servo) {
        return new Turret(servo, Kind.CR_SERVO, null);
    }

    /**
     * A CR servo turret's encoder on a motor port (e.g. a REV Through Bore: 8192 counts per turn). The
     * port may also drive another part's motor: only its count is read (and never reset).
     *
     * @param encoderPort  the configuration name of the motor port the encoder is plugged into
     * @param countsPerRev counts per turn of the ENCODER (8192 for a REV Through Bore)
     */
    public Turret encoder(String encoderPort, double countsPerRev) {
        needCr("encoder");
        if (!(countsPerRev > 0)) throw new IllegalArgumentException("Robot.java: " + name() + ".encoder: counts per turn must be more than 0");
        encoderName = encoderPort;
        encoderCountsPerRev = countsPerRev;
        analog = false;
        return this;
    }

    /**
     * A CR servo turret's absolute encoder on an analog port (e.g. an Axon servo's feedback wire, or an
     * absolute encoder: 0 V to its top voltage = one turn). It knows the angle at once: no homing.
     * Tunables: {@code encoderOffset} (its reading, degrees 0 to 360, with the turret at 0).
     *
     * @param analogPort the analog input's configuration name
     */
    public Turret analogEncoder(String analogPort) {
        needCr("analogEncoder");
        encoderName = analogPort;
        analog = true;
        return this;
    }

    private void needCr(String what) {
        if (kind != Kind.CR_SERVO) {
            throw new IllegalStateException("Robot.java: " + name() + "." + what + "(...) is for Turret.crServo (a motor has its own "
                    + "encoder; a servo turret needs none)");
        }
    }

    /**
     * Turns per turret turn of what counts the angle: the MOTOR for {@link #motor} (its gears or belt),
     * the ENCODER for {@link #crServo} (1 = on the turret's axis). Default 1. Not used by a servo turret.
     */
    public Turret gearRatio(double turnsPerTurretTurn) {
        if (!(turnsPerTurretTurn > 0)) throw new IllegalArgumentException("Robot.java: " + name() + ".gearRatio must be more than 0");
        gearRatio = turnsPerTurretTurn;
        return this;
    }

    /** Positive power turns the other way (if + turns it clockwise). A servo turret: 0 and 1 swap. */
    public Turret reversed() {
        reversed = true;
        return this;
    }

    /** A CR servo turret's encoder counts the other way (down while the turret turns counterclockwise). */
    public Turret encoderReversed() {
        needCr("encoderReversed");
        encoderReversed = true;
        return this;
    }

    /**
     * A home switch: a touch sensor (configured as "REV Touch Sensor") or a magnetic switch (a
     * "Digital Device", e.g. the REV Magnetic Limit Switch: it reads LOW near the magnet). The library
     * finds which. Tunables: {@code homeAngle} (the turret's angle where the switch closes: needed),
     * optional {@code homeDirection} (+1 = turn counterclockwise to find it, -1 = clockwise; default:
     * from startAngle toward homeAngle), {@code homingPower} (0.2), {@code homingTimeout} (5 s).
     * Missing from the configuration: an INIT note, and the start angle is trusted.
     *
     * @param switchName the switch's configuration name
     */
    public Turret homeSwitch(String switchName) {
        if (kind == Kind.SERVO) throw new IllegalStateException("Robot.java: a servo turret needs no home switch (its angle is where it was sent)");
        this.switchName = switchName;
        return this;
    }

    /**
     * No limits: a slip ring carries the wires, so it can turn round and round. It always takes the
     * shortest way, and the goal is never "out of range" ({@code min}/{@code max} are not used).
     */
    public Turret continuous() {
        if (kind == Kind.SERVO) throw new IllegalStateException("Robot.java: a servo turret cannot turn round and round");
        continuous = true;
        return this;
    }

    /**
     * Follows this camera's goal (follow mode on from START). The camera turns with the turret. With
     * an {@code odometry(...)} line and {@code Tunables.Aim.goalX, goalY}, the odometry takes over while
     * the camera does not see the goal.
     */
    public Turret follows(Camera c) {
        camera = c;
        return this;
    }

    /**
     * Follows the goal with NO camera: its direction comes from the odometry (where the robot is) and
     * {@code Tunables.Aim.goalX, goalY} (see {@link Aim}). Needs an {@code odometry(...)} line. Follow
     * mode on from START. Assumes the turret turns about the robot's centre.
     */
    public Turret followsOdometry() {
        odometryOnly = true;
        return this;
    }

    // True if it follows anything (a camera, or the odometry).
    private boolean follows() {
        return camera != null || odometryOnly;
    }

    // For Aim: true if Robot.java made it follow the goal (whole-robot aim must not be used with it).
    boolean followsGoal() {
        return follows();
    }

    // For Aim (shooting on the move): true if this camera turns with this turret, so its pictures are
    // seen from where the turret points.
    boolean carries(Camera c) {
        return c != null && camera == c && cameraOnTurret;
    }

    /** The camera is on the robot's body, not on the turret. */
    public Turret cameraOnBody() {
        cameraOnTurret = false;
        return this;
    }

    double countsPerDegree() {
        if (kind == Kind.MOTOR) return Motors.get(motorId).countsPerRev * gearRatio / 360.0;
        if (kind == Kind.CR_SERVO && !analog) return encoderCountsPerRev * gearRatio / 360.0;
        return gearRatio;   // analog: encoder degrees per turret degree
    }

    @Override
    String checkPart(String id) {
        // A servo or CR servo turret's part is a servo; a motor turret's is given in motor(...).
        return Parts.servo(id);
    }

    // A home switch missing from the configuration is an INIT note (the start angle is trusted).
    @Override
    boolean canDoWithout(String deviceName) {
        return deviceName.equals(switchName);
    }

    @Override
    List<String> deviceNames() {
        List<String> out = new ArrayList<>();
        out.add(device);
        if (switchName != null) out.add(switchName);
        // A Through Bore's port may also run another part's motor (only its count is read): not listed.
        if (analog && encoderName != null) out.add(encoderName);
        return out;
    }

    @Override
    void build(Devices devices, TunableSection t) {
        if (camera != null && camera.robot != robot) {
            throw new IllegalStateException("Robot.java: " + name() + " follows a camera that is not added: "
                    + "write camera = add(Camera...) and follows(camera)");
        }
        if (odometryOnly && robot.odometrySpec() == null) {
            throw new IllegalStateException("Robot.java: " + name() + ".followsOdometry() needs an odometry(...) line");
        }
        if (odometryOnly && !robot.aim().hasGoalPoint()) {
            throw new IllegalStateException(name() + ".followsOdometry() needs the goal on the field: "
                    + "Tunables.Aim { public static double goalX = ..., goalY = ...; } (inches, see docs/modules/robot.md#aim)");
        }
        if (kind == Kind.CR_SERVO && encoderName == null) {
            throw new IllegalStateException("Robot.java: " + name() + " is a CR servo turret: it needs an encoder for its angle, "
                    + ".encoder(\"port\", 8192) or .analogEncoder(\"port\") (docs/guides/turret-setup.md)");
        }
        cameraOffsetDeg = t.number("cameraOffset", 0);
        holdPastDeg = Math.max(0, t.number("outOfRangeHold", 10));
        startAngle = t.number("startAngle", 0);
        min = t.number("min", -90);
        max = t.number("max", 90);
        if (!continuous && !(max > min)) {
            throw new IllegalStateException("Tunables." + name() + ": max (" + max + ") must be more than min (" + min + ")");
        }
        startNote = "";
        // The range is not a REQUIRED number (a turret has hard stops of its own or none), but +/-90 is a guess:
        // say so, so nobody runs a turret on it without knowing (not for a servo turret: posA/posB give its range).
        rangeNote = !continuous && kind != Kind.SERVO && !t.has("min") && !t.has("max")
                ? name() + ": using the default range -90 to 90 deg: write your own min and max (turn it by hand to each "
                        + "end and read the angle, docs/guides/turret-setup.md)" : "";
        if (!continuous && kind != Kind.SERVO && (startAngle < min || startAngle > max)) {
            // A startAngle written outside the range is a mistake: stop. Not written (the default 0 is
            // outside a range like 10 to 170): count from the middle of the range, and say so.
            if (t.has("startAngle")) {
                throw new IllegalStateException("Tunables." + name() + ".startAngle (" + startAngle + ") is outside its range " + min + " to " + max);
            }
            startAngle = (min + max) / 2;
            startNote = fmt("%s: no startAngle and 0 is outside its range %s to %s: it counts from %s deg (the middle). "
                    + "Point it there before INIT, or write startAngle", name(), Shape.n(min), Shape.n(max), Shape.n(startAngle));
        }
        toleranceDeg = t.number("tolerance", 1.0);
        if (kind == Kind.SERVO) {
            // A servo turns itself to an angle: no PID, power or slow zone numbers (none read, none noted).
            part = buildServo(devices, t);
            part.tolerance(toleranceDeg).cameraOffset(cameraOffsetDeg);
        } else {
            kP = t.tunable("kP", 0.02, 0.002).range(0, 1);
            kI = t.tunable("kI", 0, 0.0005).range(0, 1);
            kD = t.tunable("kD", 0.001, 0.0002).range(0, 1);
            part = buildPowered(devices, t);
            part.pid(kP.get(), kI.get(), kD.get(), 0)
                    .maxPower(t.number("maxPower", 0.8))
                    .tolerance(toleranceDeg)
                    .slowZone(t.number("slowZone", 10), t.number("slowZonePower", 0.25))
                    .cameraOffset(cameraOffsetDeg);
        }
        if (continuous) part.continuous();
    }

    // A servo turret: two measured points give the line from servo position to angle.
    private com.crossbotics.ftchelper.launcher.Turret buildServo(Devices devices, TunableSection t) {
        for (String f : new String[] {"posA", "angleA", "posB", "angleB"}) {
            t.require(f, "a servo turret's two measured points: servo position posA is angle angleA, posB is angleB (Servo Finder)");
        }
        double posA = t.number("posA", 0), angleA = t.number("angleA", -90), posB = t.number("posB", 1), angleB = t.number("angleB", 90);
        if (posA == posB || angleA == angleB) throw new IllegalStateException("Tunables." + name() + ": posA/posB and angleA/angleB must differ");
        Servo s = devices.get(Servo.class, device);
        if (reversed) s.setDirection(Servo.Direction.REVERSE);
        applyPulse(s);   // .fullRange(): the servo's whole turn (nothing changes without it)
        // travelSec: from posA to posB; the PositionServo wants seconds for a full 0..1 move. Not written:
        // the parts catalog's speed for the servo from part(...) over posA..posB, else 0.6 s.
        travelNote = "";
        double full = catalogFullMoveSec();
        boolean fromCatalog = !t.has("travelSec") && !Double.isNaN(full);
        double catalogTravel = Math.max(0.05, Math.round(full * Math.abs(posB - posA) * 100) / 100.0);
        double travelSec = t.number("travelSec", fromCatalog ? catalogTravel : 0.6);
        if (fromCatalog) {
            t.fromCatalog("travelSec");
            travelNote = defaultsNote(partId, java.util.Collections.singletonList("travelSec " + exact(catalogTravel) + " s from posA to posB"),
                    "Time it to check");
        } else if (Double.isNaN(full) && t.isMarked("travelSec")) {
            travelNote = generalServoNote("travelSec", 0.6);
            t.fromCatalog("travelSec");
        }
        double travel = travelSec / Math.abs(posB - posA);
        PositionServo ps = new PositionServo(name(), s).degrees(posA, angleA, posB, angleB).travelTime(travel)
                .limits(Math.min(posA, posB), Math.max(posA, posB));
        // The range: the measured points' angles, or min/max if narrower.
        double lo = Math.min(angleA, angleB), hi = Math.max(angleA, angleB);
        if (t.has("min")) lo = Math.max(lo, min);
        if (t.has("max")) hi = Math.min(hi, max);
        min = lo;
        max = hi;
        // Where it goes at START, so its angle is known from then on (a servo cannot say where it is):
        // the angle the last program left it at (TeleOp after autonomous: no jump), else startAngle,
        // else the middle of its range.
        if (t.has("startAngle") && (startAngle < min || startAngle > max)) {
            throw new IllegalStateException("Tunables." + name() + ".startAngle (" + startAngle + ") is outside its range " + min + " to " + max);
        }
        double[] kept = remembered(t);
        double first = kept != null ? Math.max(min, Math.min(max, kept[0])) : t.has("startAngle") ? startAngle : (min + max) / 2;
        angleFrom = kept != null
                ? Module.fmt("servo: at START it goes to %.0f deg, where the last program left it", first)
                : t.has("startAngle") ? Module.fmt("servo: at START it goes to startAngle %.0f deg", first)
                : Module.fmt("servo: at START it goes to %.0f deg (the middle of its range: no startAngle)", first);
        return com.crossbotics.ftchelper.launcher.Turret.servo(name(), ps).limits(min, max)
                .servoStickSec(travelSec).startAngle(first);
    }

    // A motor or a CR servo with an encoder.
    private com.crossbotics.ftchelper.launcher.Turret buildPowered(Devices devices, TunableSection t) {
        com.crossbotics.ftchelper.launcher.Turret p;
        if (kind == Kind.MOTOR) {
            DcMotorEx motor = MotorSetup.of(devices.get(DcMotorEx.class, device)).reversed(reversed)
                    .brake().resetEncoder().runWithoutEncoder().done();
            p = new com.crossbotics.ftchelper.launcher.Turret(name(), motor);
        } else {
            final CRServo servo = devices.get(CRServo.class, device);
            servo.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
            DoubleSupplier counts = analog ? analogCounts(devices.get(AnalogInput.class, encoderName))
                    : quadCounts(devices.get(DcMotorEx.class, encoderName));
            p = new com.crossbotics.ftchelper.launcher.Turret(name(), servo::setPower, counts);
            if (encoderReversed) p.encoderReversed();
        }
        p.countsPerDegree(countsPerDegree());
        if (!continuous) p.limits(min, max);
        // Homing numbers only with a switch (no "not written" note for a turret that never homes).
        if (switchName != null) p.homingPower(t.number("homingPower", 0.2)).homingTimeout(t.number("homingTimeout", 5));
        // Where it points now: an absolute encoder knows; else remembered from the last program, or startAngle.
        double[] kept = analog ? null : remembered(t);
        if (analog) {
            double offset = t.number("encoderOffset", 0);
            double now = absoluteAngle(p, offset);
            p.startAngle(now);
            angleFrom = Module.fmt("absolute encoder: %.0f deg (encoderOffset %.0f)", now, offset);
        } else if (kept != null) {
            p.startAngle(kept[0]);
            angleFrom = Module.fmt("%.0f deg, carried from the last program (%.0f s ago; keepAngle = false to turn this off). "
                    + "If the turret was moved by hand since: turn it to startAngle %.0f deg, stop this OpMode and INIT again",
                    kept[0], (System.nanoTime() - kept[1]) / 1e9, startAngle);
        } else {
            p.startAngle(startAngle);
            // A TeleOp with no angle from a program just before (the robot app restarted, more than 5
            // minutes, keepAngle false): the turret may be anywhere, so say it plainly.
            angleFrom = robot.autonomous
                    ? Module.fmt("startAngle %.0f deg: put the turret there by hand before INIT, or every angle is off", startAngle)
                    : Module.fmt("no angle from a program before: turn it to startAngle %.0f deg by hand and INIT again, or every angle is off", startAngle);
        }
        if (switchName != null) {
            t.require("homeAngle", "the turret's angle where the home switch closes (turn it there by hand and read the angle)");
            homeAngle = t.number("homeAngle", 0);
            double dirDefault = Math.signum(homeAngle - startAngle);
            homeDirection = Math.signum(t.number("homeDirection", dirDefault == 0 ? 1 : dirDefault));
            if (homeDirection == 0) homeDirection = 1;
            BooleanSupplier sw = switchReader(devices);
            if (sw == null) {
                angleFrom += "; NO home switch \"" + switchName + "\" in the configuration";
                hasSwitch = false;
            } else {
                p.homeSwitch(sw, homeAngle);
                hasSwitch = true;
                // Already known (absolute, or carried over): no homing at START; the switch still fixes drift.
                if (analog || kept != null) {
                    p.markHomed();
                } else {
                    angleFrom = Module.fmt("home switch at %.0f deg: found at START", homeAngle);
                }
            }
        }
        return p;
    }

    // A touch sensor, or a digital channel (a magnetic switch: LOW = closed). Null if neither is configured.
    private BooleanSupplier switchReader(Devices devices) {
        final TouchSensor touch = devices.tryGet(TouchSensor.class, switchName);
        if (touch != null) return touch::isPressed;
        final DigitalChannel ch = devices.tryGet(DigitalChannel.class, switchName);
        if (ch == null) return null;
        ch.setMode(DigitalChannel.Mode.INPUT);
        return () -> !ch.getState();
    }

    // A Through Bore on a motor port: its count, from where it is now (never reset: the port may run a motor).
    private static DoubleSupplier quadCounts(final DcMotorEx port) {
        final int zero = port.getCurrentPosition();
        return () -> port.getCurrentPosition() - zero;
    }

    // An absolute encoder: degrees 0..360 per encoder turn, unwrapped (it keeps counting past 360).
    private static DoubleSupplier analogCounts(final AnalogInput in) {
        return new DoubleSupplier() {
            double last = Double.NaN, total = 0;

            @Override
            public double getAsDouble() {
                double maxV = in.getMaxVoltage();
                double deg = 360.0 * in.getVoltage() / (maxV > 0 ? maxV : 3.3);
                if (Double.isNaN(last)) total = deg;
                else total += Angles.shortestDiff(deg, last);
                last = deg;
                return total;
            }
        };
    }

    // The angle from the absolute encoder at INIT: (reading - offset) / gear ratio, the equal angle
    // (one encoder turn = 360/ratio turret degrees) nearest startAngle. A geared encoder must start within
    // half an encoder turn of startAngle.
    private double absoluteAngle(com.crossbotics.ftchelper.launcher.Turret p, double offset) {
        double reading = Angles.wrapDegrees(p.getAngle() * gearRatio);   // the raw encoder degrees (offset 0 so far)
        double turn = 360.0 / gearRatio;
        double a = Angles.wrapDegrees(reading - offset) / gearRatio;
        double k = Math.round((startAngle - a) / turn);
        return a + k * turn;
    }

    // The angle the last program ended at, if this program may use it (TeleOp, keepAngle on, fresh).
    private double[] remembered(TunableSection t) {
        if (robot.autonomous || !t.flag("keepAngle", true)) return null;
        synchronized (REMEMBERED) {
            double[] r = REMEMBERED.get(device);
            if (r == null || (System.nanoTime() - r[1]) / 1e9 > MatchMemory.MAX_AGE_SEC) return null;
            return r.clone();
        }
    }

    // Remembers the angle for the next program (end of autonomous, end of TeleOp).
    // For stop and pause: a reading that fails must not keep the motor from stopping.
    private void rememberAngleIfItReads() {
        try {
            rememberAngle();
        } catch (RuntimeException e) {
            synchronized (REMEMBERED) {
                REMEMBERED.remove(device);   // its angle is not known now
            }
        }
    }

    private void rememberAngle() {
        if (part == null || analog) return;
        if (kind == Kind.SERVO) {
            // A servo: where it was sent last (its target, or where it was going), so the next program
            // sends it there at START and it does not jump.
            double a = Double.isNaN(part.getTargetAngle()) ? part.getAngle() : part.getTargetAngle();
            if (Double.isNaN(a)) return;
            synchronized (REMEMBERED) {
                REMEMBERED.put(device, new double[] {a, System.nanoTime()});
            }
            return;
        }
        if (!part.isHomed() || part.isHoming()) {
            // Its angle is not known now (homing did not finish): an older remembered angle is stale too.
            synchronized (REMEMBERED) {
                REMEMBERED.remove(device);
            }
            return;
        }
        synchronized (REMEMBERED) {
            REMEMBERED.put(device, new double[] {part.getAngle(), System.nanoTime()});
        }
    }

    /** Forgets every remembered turret angle (tests, or a tool that knows the turret was moved). */
    public static void forgetAngles() {
        synchronized (REMEMBERED) {
            REMEMBERED.clear();
        }
    }

    // True once START came in this program (an INIT that is stopped before START forgets the angle).
    private boolean started = false;

    @Override
    void start() {
        started = true;
        // Homing at START (never in INIT: nothing moves before START).
        if (hasSwitch && !part.isHomed()) part.home(homeDirection);
        // A servo turret: sent to its start angle now, so its angle is known from the first loop (the
        // stick, following and interlock rules all need it). Not past a command it already has.
        if (kind == Kind.SERVO && Double.isNaN(part.getTargetAngle())) part.aimAt(part.servoStartAngle());
        if (follows()) follow.on();
    }

    @Override
    String toolFor(String field) {
        if (field.equals("cameraOffset")) return "the Aim Tuner, docs/guides/finders.md#aim-tuner-camera-offset";
        if (field.startsWith("pos") || field.startsWith("angle")) return "the Servo Finder and a protractor, docs/guides/turret-setup.md";
        if (field.equals("min") || field.equals("max") || field.equals("homeAngle") || field.equals("encoderOffset")) {
            return "measure it: turn the turret by hand and read the angle, docs/guides/turret-setup.md";
        }
        return null;
    }

    @Override
    List<String> notes() {
        List<String> out = super.notes();
        if (!startNote.isEmpty()) out.add(startNote);
        if (!travelNote.isEmpty()) out.add(travelNote);
        if (!rangeNote.isEmpty()) out.add(rangeNote);
        return out;
    }

    @Override
    void initLoop(RobotTelemetry t) {
        if (part == null) return;
        t.line(name(), angleFrom);
        if (hasSwitch && !part.isHomed()) {
            t.line(name() + " homing", fmt("at START it turns %s slowly to find the switch",
                    homeDirection > 0 ? "counterclockwise" : "clockwise"));
        }
    }

    // ----------------------------- commands -----------------------------

    /** Following the camera's goal: switch it with a button ({@code g2.back.toggle(robot.turret.followMode())}). */
    public Mode followMode() {
        return follow;
    }

    /**
     * Turns to an angle (inside the limits) and stops following.
     *
     * @param deg robot-relative, counterclockwise +, 0 = forward
     */
    public void aimAt(double deg) {
        needBuilt(part);
        touch();
        follow.off();
        goalBehind = false;
        part.aimAt(deg);
    }

    /**
     * By hand, -1 to 1 (a stick, every loop). While it is moved, following pauses.
     *
     * @param power + = counterclockwise
     */
    public void manual(double power) {
        needBuilt(part);
        stickMoving = power != 0;
        stickPower = power;
        if (stickMoving) touch();
        part.manual(power);
    }

    /**
     * True when it points where it should, within the tolerance. While FOLLOWING, that means at the
     * goal now: the goal is seen this moment (a fresh camera picture, or the odometry), the turret is
     * homed and in range, not held by an interlock rule, it reached its target, and the goal is within
     * the tolerance (the larger of Tunables.Turret.tolerance and Tunables.Aim.toleranceDeg). False the
     * moment the goal is lost, so a launcher gated on it ({@code requireReady("aimed", turret::isAimed)})
     * never fires blind.
     */
    public boolean isAimed() {
        if (part != null && missing) return true;   // not on the robot (optional): never holds a shot back
        if (part == null || !part.isAimed()) return false;
        if (!follow.isOn() || !follows()) return true;   // by hand / aimAt(): where it was told
        if (goalBehind || !part.isHomed() || part.isOutOfRange()) return false;
        double e = aimErrorDeg();
        double tol = Math.max(part.getTolerance(), robot == null ? 0 : robot.aim().toleranceDeg());
        return !Double.isNaN(e) && Math.abs(e) <= tol;
    }

    /** True when the goal is past its range (it waits at the limit, or holds while the goal is behind). */
    public boolean isOutOfRange() {
        return part != null && !missing && (part.isOutOfRange() || goalBehind);
    }

    /** True while an interlock rule holds it back from where it should point ({@link Interlock}). */
    public boolean isHeld() {
        return part != null && !missing && part.isHeld();
    }

    /** True once its angle is known (homed on its switch, or no switch). */
    public boolean isHomed() {
        return part != null && (missing || (part.isHomed() && !part.isHoming()));
    }

    /** The angle now, degrees (-180 to 180 for a {@link #continuous()} turret). */
    public double angle() {
        if (part == null) return 0;
        return continuous ? part.getWrappedAngle() : part.getAngle();
    }

    /**
     * How many degrees the turret points away from the goal now (any sign), NaN if the goal is not
     * known (no fresh picture and no odometry). For the gamepad buzz ({@code rumbleWhenAimed()}).
     */
    public double aimErrorDeg() {
        if (part == null) return Double.NaN;
        TargetObservation seen = robot == null ? null : robot.aim().cameraGoal(camera);
        if (seen != null) {
            double error = seen.bearingDeg - cameraOffsetDeg;
            // On the turret: the bearing is from where the turret points. On the body: from the robot's front.
            return cameraOnTurret ? error : Angles.wrapDegrees(error - part.getAngle());
        }
        TargetObservation odo = robot == null || !follows() ? null : robot.aim().fromOdometry();
        return odo == null ? Double.NaN : Angles.wrapDegrees(odo.bearingDeg - part.getAngle());
    }

    // The odometry's goal direction. Far past a limit (behind the robot) the nearest limit would flip
    // from one side to the other as the heading wobbles around "straight behind", swinging the turret
    // all the way across. So: more than holdPastDeg past a limit, hold where it points; go on only when
    // the goal is back within half that (hysteresis: two thresholds, so it does not flicker at one).
    private void followOdometry(double bearingDeg) {
        double past = part.degreesOutOfRange(bearingDeg);
        boolean wasBehind = goalBehind;
        if (!goalBehind && past > holdPastDeg) goalBehind = true;
        else if (goalBehind && past <= holdPastDeg / 2) goalBehind = false;
        if (goalBehind) {
            // Hold where it points now (once, as it starts holding).
            if (!wasBehind && part.isHomed()) part.aimAt(part.getAngle());
            return;
        }
        part.aimAt(bearingDeg);
    }

    /** Where the following got the goal last loop: CAMERA, ODOMETRY or NONE. */
    public Aim.Source aimSource() {
        return source;
    }

    /** The launcher.Turret inside, for anything this module does not offer. */
    public com.crossbotics.ftchelper.launcher.Turret part() {
        return part;
    }

    @Override
    public void update() {
        if (kP != null) part.pid(kP.get(), kI.get(), kD.get(), 0);   // a servo turret has no PID
        if (!follow.isOn()) goalBehind = false;   // only following holds for a goal behind
        // Following waits until the angle is known (homed): a target from an unknown angle means nothing.
        if (follow.isOn() && follows() && !stickMoving && part.isHomed() && !part.isHoming()) {
            // The camera first (a fresh picture of the goal).
            TargetObservation seen = robot.aim().cameraGoal(camera);
            TargetObservation odo = seen == null ? robot.aim().fromOdometry() : null;
            if (seen != null) {
                goalBehind = false;
                part.aimAtTarget(seen, cameraOnTurret);
                source = Aim.Source.CAMERA;
            } else if (odo != null) {
                // The odometry's direction is from the robot's front: it IS the turret angle to go to.
                followOdometry(odo.bearingDeg);
                source = Aim.Source.ODOMETRY;
            } else {
                goalBehind = false;
                source = Aim.Source.NONE;   // nothing new: it holds where it points
            }
        }
        part.update();
    }

    @Override
    public void stop() {
        if (part == null) return;
        // touch() ends an action that has the turret: that can read the encoder too. Stop anyway.
        RuntimeException failed = null;
        try { touch(); } catch (RuntimeException e) { failed = e; }
        if (started) rememberAngleIfItReads();
        else {
            // Stopped in INIT (no START): the drivers may have stopped to put the turret back at its
            // startAngle by hand (the INIT line says so). Forget the carried angle: the next INIT uses startAngle.
            synchronized (REMEMBERED) {
                REMEMBERED.remove(device);
            }
        }
        follow.off();
        goalBehind = false;
        part.stop();
        if (failed != null) throw failed;
    }

    private double pausedTarget = Double.NaN;
    private boolean pausedFollowing = false;

    @Override
    void pause() {
        if (part == null) return;
        rememberAngleIfItReads();
        pausedTarget = part.getTargetAngle();
        pausedFollowing = follow.isOn();
        follow.off();
        part.stop();          // power 0 (it is on brake)
    }

    @Override
    void resume() {
        if (part == null) return;
        if (!Double.isNaN(pausedTarget) && part.isHomed()) part.aimAt(pausedTarget);
        if (pausedFollowing) follow.on();
    }

    @Override
    void halt() {
        // Hold where it points now.
        if (part != null && part.isHomed() && !Double.isNaN(part.getAngle())) part.aimAt(part.getAngle());
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (part == null) return;
        if (follow.isOn() && isOutOfRange()) t.warn("goal out of turret range" + (goalBehind ? " (behind the robot: holding)" : ""));
        // One line: the angle, AIMED (this module's isAimed: while following, at the goal now) and what
        // it follows. The launcher.Turret inside adds only its warnings.
        if (part.isHoming()) t.line(name(), part.isWaitingUnknown() ? "homing waits for an interlock rule (see its line)" : "homing: looking for the switch");
        else if (Double.isNaN(angle())) t.line(name(), fmt("not sent anywhere yet (at START: %.0f deg)", part.servoStartAngle()));
        else t.line(name(), fmt("%.0f deg%s%s", angle(), isAimed() ? "  AIMED" : "", follow.isOn() ? "  following: " + source.words() : ""));
        part.addWarnings(t);
        t.debug(name() + " target", Double.isNaN(part.getTargetAngle()) ? "-" : fmt("%.1f deg", part.getTargetAngle()));
    }

    @Override
    void suggestButtons(ButtonPlan plan) {
        Pad op = plan.operator();
        op.rightStickX.use(v -> manual(-v)).says("by hand (right = clockwise)");
        if (follows()) op.back.toggle(follow).says("follow the goal on/off");
    }

    // ----------------------------- interlock rules -----------------------------

    @Override boolean zoneCapable() { return true; }
    @Override String zoneUnit() { return "deg"; }

    @Override
    double zoneMin() {
        if (continuous) return Double.NEGATIVE_INFINITY;
        return part == null ? min : part.getMin();
    }

    @Override
    double zoneMax() {
        if (continuous) return Double.POSITIVE_INFINITY;
        return part == null ? max : part.getMax();
    }

    @Override boolean zoneWraps() { return true; }
    // A servo turret is known once it was sent somewhere (at START); before that it may be anywhere.
    @Override boolean zoneKnown() { return isHomed() && (missing || kind != Kind.SERVO || !Double.isNaN(part.getAngle())); }

    @Override
    String zoneUnknownWords() {
        if (kind == Kind.SERVO) return "not sent anywhere yet: it goes to its start angle at START";
        return hasSwitch ? "not homed yet" : "no angle yet";
    }

    @Override
    double[] zoneSweep() {
        if (!zoneKnown()) return new double[] {zoneMin(), zoneMax()};
        return part.getSweep();
    }

    @Override
    double zoneTarget() {
        if (part == null || stickMoving) return Double.NaN;
        return part.getTargetAngle();
    }

    @Override double zoneManual() { return stickMoving ? stickPower : 0; }

    @Override
    void zoneFence(double lo, double hi) {
        if (part != null) part.fence(lo, hi);
    }

    @Override
    void zoneFence(double lo, double hi, double upPower, double downPower) {
        if (part != null) part.fence(lo, hi, upPower, downPower);
    }

    @Override boolean zoneHoming() { return part != null && part.isHoming(); }

    @Override
    void zoneWait(boolean wait) {
        if (part != null) part.waitWhileUnknown(wait);
    }

    // A motor or CR servo turret coasts: the interlock measures its speed (its encoder) for the stop
    // distance. A servo turret goes where it is sent (never past the fence).
    @Override boolean zoneCoasts() { return kind != Kind.SERVO; }

    // A motor turret's top speed (degrees a second at full power): the motor's free speed through its
    // gears. A CR servo's is not known: the interlock uses the fastest it has measured.
    @Override
    double zoneTopSpeed() {
        return kind == Kind.MOTOR ? Motors.get(motorId).freeRpm / 60.0 * 360.0 / gearRatio : Double.NaN;
    }

    @Override
    double zoneDefaultMargin() {
        return Math.max(3, 2 * toleranceDeg);
    }

    @Override
    boolean zoneHeld(double fenceLo, double fenceHi, double margin) {
        if (part == null) return false;
        double[] s = zoneSweep();
        return part.isHeld() || (stickMoving && stickPower > 0 && s[1] >= fenceHi - margin)
                || (stickMoving && stickPower < 0 && s[0] <= fenceLo + margin);
    }

    // ----------------------------- the simulator -----------------------------

    @Override
    void simLines(List<String> out, TunableSection t) {
        double lo = t.number("min", -90), hi = t.number("max", 90), start = t.number("startAngle", 0);
        // No startAngle and 0 outside the range: it counts from the middle (as at INIT, see build).
        if (kind != Kind.SERVO && !continuous && !t.has("startAngle") && (start < lo || start > hi)) start = (lo + hi) / 2;
        if (kind == Kind.SERVO) {
            simServoLines(out, t, start);
            return;
        }
        double cpd = countsPerDegree();
        String driven;   // the motor whose shaft is the turret's angle in the simulator
        if (kind == Kind.MOTOR) {
            driven = device;
            if (continuous) out.add(fmt("motor.%s = free, %s", device, motorWords(motorId, "gobilda-5203-117")) + simTodo(motorId));
            else out.add(fmt("motor.%s = lift, %s, min=%.0f, max=%.0f, gravity=0", device, motorWords(motorId, "gobilda-5203-117"),
                    (lo - start) * cpd, (hi - start) * cpd) + simTodo(motorId));
        } else {
            out.add(partId != null ? fmt("crservo.%s =   # %s", device, partId) : fmt("crservo.%s =", device));
            // The simulator turns the encoder's "motor" with the CR servo's power (drivenBy=).
            driven = analog ? device + "_shaft" : encoderName;
            double tpr = analog ? 360 : encoderCountsPerRev;
            String stops = continuous ? "free" : fmt("lift, min=%.0f, max=%.0f, gravity=0", (lo - start) * cpd, (hi - start) * cpd);
            out.add(fmt("motor.%s = %s, ticksPerRev=%s, rpm=%s, drivenBy=%s   # TODO the turret's top speed (encoder turns per minute)",
                    driven, stops, Shape.n(tpr), Shape.n(60 * gearRatio), device));
            if (analog) {
                // The reading at the start: encoderOffset + startAngle (in encoder degrees).
                double offset = t.number("encoderOffset", 0);
                out.add(fmt("analog.%s = %s, offsetDeg=%s", encoderName, driven, Shape.n(((offset + start * gearRatio) % 360 + 360) % 360)));
            }
        }
        if (switchName != null) {
            double home = t.number("homeAngle", 0);
            out.add(fmt("touch.%s = %s, at=%.0f, width=%.0f", switchName, driven, (home - start) * cpd, Math.max(1, 2 * cpd)));
        }
        if (continuous) {
            shapeWords(out, t, fmt("motor=%s, ticksPerDeg=%.3f, start=%s, minDeg=-180, maxDeg=180", driven, cpd, Shape.n(start)));
        } else {
            // start = min: the simulator counts the shape's angle from the motor's min stop.
            shapeWords(out, t, fmt("motor=%s, ticksPerDeg=%.3f, start=%.0f", driven, cpd, lo));
        }
    }

    // What turns its turret shape, while its lines are written ("motor=turret, ticksPerDeg=..."): see driverFor.
    private String simDriver = null;

    // Its turret shape: from looks(...), or the guess (in the middle, on top: see SimGuess).
    private void shapeWords(List<String> out, TunableSection t, String driver) {
        if (looks.isEmpty()) {
            out.add(guessLine("turret", driver));
            return;
        }
        simDriver = driver;
        try {
            lookLines(out, t);
        } finally {
            simDriver = null;
        }
    }

    @Override
    boolean drawsLooks() {
        return true;
    }

    // A turret shape (Shape.turret()) turns with the turret; a box rides it without a driver.
    @Override
    String driverFor(Shape s, TunableSection t) {
        if (!s.type.equals("turret")) {
            throw new IllegalStateException("Robot.java: " + name() + " is a turret: it turns a turret shape (Shape.turret()), not a " + s.type
                    + " (a part that rides on it: .on(turret) in that part's looks)");
        }
        if (simDriver == null) throw new IllegalStateException("Robot.java: " + name() + ": its shapes are written with its own lines");
        return simDriver;
    }

    // A servo turret: the servo and a turret shape whose angle follows the servo's position.
    private void simServoLines(List<String> out, TunableSection t, double start) {
        double posA = t.number("posA", 0), angleA = t.number("angleA", -90), posB = t.number("posB", 1), angleB = t.number("angleB", 90);
        if (posA == posB) posB = posA + 1;
        // The angle per servo position, and the angles at the simulated servo's raw 0 and 1.
        double perPos = (angleB - angleA) / (posB - posA);
        double at0 = angleA - perPos * posA, at1 = at0 + perPos;
        if (reversed) {
            // A reversed servo: the simulated servo's raw 0 is the code's 1.
            double swap = at0;
            at0 = at1;
            at1 = swap;
        }
        out.add(partId != null ? fmt("servo.%s = 0.5, %s", device, servoWords(partId, "gobilda-2000-torque")) + pulseWords() + simTodo(partId)
                : fmt("servo.%s = 0.5, part=gobilda-2000-torque", device) + pulseWords() + "   # TODO the real servo");
        shapeWords(out, t, fmt("servo=%s, pos0=%s, pos1=%s", device, Shape.n(at0), Shape.n(at1)));
    }
}

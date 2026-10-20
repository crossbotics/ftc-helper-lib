package io.github.jasoncross.ftchelper.teleop;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.RobotLog;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.drive.DriverControls;
import io.github.jasoncross.ftchelper.drive.DriverHeading;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.gamepad.ToggleRumble;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.match.MatchMemory;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.tuning.TuningMenu;
import java.util.List;

/**
 * A whole TeleOp where you only write what is special about YOUR robot: its mechanisms, the
 * operator's buttons, and any extra screen lines. Everything every TeleOp needs is done for you:
 *
 * <ul>
 *   <li>Driving on gamepad 1 ({@link DriverControls}): left stick drive, right stick X turn, left
 *       trigger slow. Mecanum and tank. Robot-centric to start; Y = field-centric on/off (mecanum).</li>
 *   <li>Field-centric "forward" continues from autonomous ({@link DriverHeading}); BACK resets it.</li>
 *   <li>The alliance from autonomous ({@link #alliance()}), the 2:00 match timer with gamepad buzzes
 *       at 1:00 and 0:20, the tuning menu on GUIDE (PRACTICE profile only), debug telemetry on
 *       START + BACK, warnings for slow code, a tuned value or the PRACTICE profile.</li>
 *   <li>Every mechanism updated every loop, and EVERYTHING stopped at the end, even after an error.</li>
 * </ul>
 *
 * <pre>{@code
 * @TeleOp(name = "Main TeleOp")
 * public class MainTeleOp extends EasyTeleOp {
 *     private Robot robot;
 *
 *     @Override protected RobotConfig robotConfig() { return new RobotSettings(); }
 *     @Override protected Subsystems createSubsystems() { robot = new Robot(hardwareMap); return robot.all; }
 *
 *     @Override protected void bindButtons(ControllerBindings driver, ControllerBindings operator) {
 *         operator.bindPress(Pad.G2, Btn.RB, robot.claw::toggle);
 *     }
 * }
 * }</pre>
 *
 * <p>Order in each loop: buttons, driving, {@link #everyLoop()}, mechanisms' update(), telemetry.
 * Nothing here waits. Your code must not wait either (docs/guides/non-blocking.md).
 *
 * Docs: docs/modules/teleop.md. Tutorial: docs/tutorials/20-easy-teleop.md.
 */
public abstract class EasyTeleOp extends LinearOpMode {

    private RobotTelemetry t;
    private DriverControls driving;
    private DriverHeading heading;
    private MatchTimer timer;
    private Alliance alliance = Alliance.BLUE;
    private TuningMenu tuning;

    // ----------------------------- you fill these in -----------------------------
    // ("protected": only your OpMode class, which extends this one, can see and fill these in.)

    /**
     * Your robot's settings, shared with autonomous (see {@link RobotConfig}). Return
     * {@code new RobotSettings()}. If you do not have one, override {@link #driveConfig()} and
     * {@link #createImu()} instead.
     */
    protected RobotConfig robotConfig() {
        return null;
    }

    /** The drivetrain settings. Default: {@code robotConfig().drive()}. */
    protected DriveConfig driveConfig() {
        RobotConfig config = robotConfig();
        if (config == null) {
            throw new IllegalStateException("EasyTeleOp needs your robot settings: override robotConfig() "
                    + "(return new RobotSettings()) or driveConfig().");
        }
        return config.drive();
    }

    /**
     * The IMU, for field-centric driving and "forward" from autonomous. Default:
     * {@code robotConfig().imu(...)}. Null = no IMU: robot-centric driving only.
     */
    protected IMU createImu() {
        RobotConfig config = robotConfig();
        return config == null ? null : config.imu(hardwareMap);
    }

    /** Your mechanisms: kept updated every loop and stopped at the end. Default: none. */
    protected Subsystems createSubsystems() {
        return new Subsystems();
    }

    /**
     * Your buttons. Put gamepad 2 buttons on {@code operator}. Extra gamepad 1 buttons go on
     * {@code driver}: those pause while the tuning menu is open (it uses gamepad 1).
     * Already taken on gamepad 1: Y (field-centric; see {@link #fieldCentricButton()}), BACK,
     * START + BACK, GUIDE.
     */
    protected void bindButtons(ControllerBindings driver, ControllerBindings operator) { }

    /**
     * Your own code for every loop, e.g. a stick that moves the lift by hand. Runs after the buttons
     * and driving, before the mechanisms update. Must be quick: never wait here.
     */
    protected void everyLoop() { }

    /** Extra lines for the Driver Station, every loop. The mechanisms' lines are already shown. */
    protected void addTelemetry(RobotTelemetry t) { }

    /**
     * The button map's lines for the help page, or null (default) for none. With a map, a tap of GUIDE
     * shows it during the match and holding GUIDE 1 s opens the tuning menu; without one, GUIDE opens
     * the tuning menu.
     */
    protected List<String> buttonHelp() {
        return null;
    }

    /** How long GUIDE is held to open the tuning menu when there is a button map, seconds. */
    public static final double GUIDE_HOLD_SEC = 1.0;
    private boolean helpOpen = false;
    private List<String> help = null;

    /** Extra lines for the Driver Station during INIT (before START), every INIT loop. */
    protected void addInitTelemetry(RobotTelemetry t) { }

    /**
     * Runs once, right after START, before the first loop. Must be quick: start things here, never
     * wait. Nothing may move before START, so this is the first moment a mechanism may move.
     */
    protected void onStart() { }

    // ----------------------------- optional settings -----------------------------

    /** True to start in field-centric mode. Default false (robot-centric). */
    protected boolean startFieldCentric() {
        return false;
    }

    /**
     * The gamepad 1 button that switches field-centric driving on and off (mecanum with an IMU only).
     * Default Y. Return null for none (then field-centric stays as {@link #startFieldCentric()} says).
     */
    protected Btn fieldCentricButton() {
        return Btn.Y;
    }

    /** Speed at a full squeeze of the slow trigger, 0 to 1. Default 0.3. */
    protected double slowSpeed() {
        return 0.3;
    }

    /**
     * Changes how driving feels, once, in INIT, right after the drive is made. Example:
     * {@code driving.sticks(0.08, 3);} for a bigger deadband and a stronger curve.
     */
    protected void configureDriving(DriverControls driving) { }

    /**
     * Seconds left when both gamepads buzz. Default 60 and 20 (1:00 and 0:20 left). Change it to the
     * moments that matter in this season's game.
     */
    protected double[] buzzAtSecondsLeft() {
        return new double[] {60, 20};
    }

    /**
     * Drives the robot from gamepad 1, every loop (not while the tuning menu is open).
     * Default: the standard controls. Replace it to drive your own way, with
     * {@link #driving()}'s {@code drive(...)} or {@code tank(...)}. Example, two-stick tank style:
     * <pre>{@code
     * @Override protected void drive(Gamepad gamepad) {
     *     driving().tank(-gamepad.left_stick_y, -gamepad.right_stick_y, 1);
     * }
     * }</pre>
     */
    protected void drive(Gamepad gamepad) {
        driving.update(gamepad);
    }

    // ----------------------------- for your code -----------------------------

    /** The alliance autonomous chose (BLUE if no autonomous ran in the last few minutes). */
    protected Alliance alliance() {
        return alliance;
    }

    /** The Driver Station screen helper (warnings, standard and debug lines). */
    protected RobotTelemetry robotTelemetry() {
        return t;
    }

    /** The driving (field-centric on/off, slow mode). */
    protected DriverControls driving() {
        return driving;
    }

    /** The heading compared with the driver, or null if there is no IMU. */
    protected DriverHeading heading() {
        return heading;
    }

    /** The 2:00 match timer (started at START). */
    protected MatchTimer matchTimer() {
        return timer;
    }

    /** True while the tuning menu is open. Gamepad 1 belongs to the menu then: do not read its sticks. */
    protected boolean isTuningMenuOpen() {
        return tuning != null && tuning.isOpen();
    }

    // ----------------------------- the program -----------------------------

    /**
     * The whole TeleOp. The FTC SDK calls this when you press INIT.
     * {@code final}: your class cannot replace it, so the safety steps here always run.
     */
    @Override
    public final void runOpMode() {
        // ---- Setup (INIT) ----
        t = new RobotTelemetry(telemetry);
        BulkReads.auto(hardwareMap);
        IMU imu = createImu();
        // After the IMU is set up: take autonomous's heading, so "forward" carries over.
        heading = imu != null ? new DriverHeading(imu) : null;
        driving = DriverControls.create(hardwareMap, driveConfig(), heading).slowSpeed(slowSpeed());
        driving.setFieldCentric(startFieldCentric());
        configureDriving(driving);
        alliance = MatchMemory.allianceOr(Alliance.BLUE);
        timer = MatchTimer.teleOp();
        for (double s : buzzAtSecondsLeft()) timer.rumbleAt(s, gamepad1, gamepad2);
        tuning = new TuningMenu(gamepad1);
        ToggleRumble confirm = new ToggleRumble(gamepad1);

        // Three groups of buttons, so the driver's can pause while the tuning menu uses gamepad 1.
        ControllerBindings menuKey = new ControllerBindings();
        // BACK alone (on its release): "the robot now faces away from me", with one buzz.
        // START and BACK together, in either order: debug lines on/off, and "forward" is NOT reset.
        boolean[] chord = {false};   // true once START was pressed during this BACK press
        Runnable debugChord = () -> {
            if (gamepad1.start && gamepad1.back && !chord[0]) {
                chord[0] = true;
                t.toggleDebug();
            }
        };
        ControllerBindings driver = new ControllerBindings()
                .bindPress(Pad.G1, Btn.BACK, () -> { chord[0] = false; debugChord.run(); })
                .bindPress(Pad.G1, Btn.START, debugChord)
                .bindRelease(Pad.G1, Btn.BACK, () -> {
                    if (!chord[0] && heading != null) {
                        heading.reset();
                        confirm.play(false);
                    }
                });
        // Y (or your fieldCentricButton()): field-centric on/off (two buzzes = on, one = off).
        // Only where it is possible.
        Btn fieldCentricBtn = fieldCentricButton();
        if (driving.canFieldCentric() && fieldCentricBtn != null) {
            driver.bindPress(Pad.G1, fieldCentricBtn, () -> confirm.play(driving.toggleFieldCentric()));
        }
        ControllerBindings operator = new ControllerBindings();

        Subsystems robot = null;
        // The first error from the program itself, if any (so an error while stopping cannot hide it).
        Throwable failure = null;
        try {
            // Inside the "try": if making the mechanisms fails, the wheels are still stopped below.
            robot = createSubsystems();
            if (robot == null) robot = new Subsystems();
            bindButtons(driver, operator);
            // GUIDE: after bindButtons, so the button map is known.
            help = buttonHelp();
            if (help == null) {
                menuKey.bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
            } else {
                // With a button map: a tap shows it (or closes the tuning menu); holding 1 s opens the
                // tuning menu (PRACTICE profile).
                final long[] downAt = {0};
                final boolean[] held = {false};
                menuKey.bindPress(Pad.G1, Btn.GUIDE, () -> { downAt[0] = System.nanoTime(); held[0] = false; })
                        .bindHold(Pad.G1, Btn.GUIDE, () -> {
                            if (!held[0] && System.nanoTime() - downAt[0] > GUIDE_HOLD_SEC * 1e9) {
                                held[0] = true;
                                helpOpen = false;
                                tuning.toggle();
                            }
                        })
                        .bindRelease(Pad.G1, Btn.GUIDE, () -> {
                            if (held[0]) return;
                            if (tuning.isOpen()) tuning.toggle();
                            else helpOpen = !helpOpen;
                        });
            }

            // ---- INIT loop: warnings and what the drivers need to know. Never waits. ----
            boolean fromAuto = MatchMemory.hasAlliance();
            List<String> autoReport = MatchMemory.lastReport();
            while (opModeInInit()) {
                // The alliance: from autonomous, or chosen here (gamepad 1: X = BLUE, B = RED).
                if (gamepad1.x && alliance != Alliance.BLUE) { alliance = Alliance.BLUE; fromAuto = false; }
                if (gamepad1.b && alliance != Alliance.RED) { alliance = Alliance.RED; fromAuto = false; }
                if (Profile.current().isPractice()) t.warn("PRACTICE profile");
                int changed = Tunable.countChanged();
                if (changed > 0) t.warn(changed + " tuned value(s) differ from the code");
                if (fromAuto) {
                    t.line("Alliance", alliance + " (from autonomous; X = BLUE, B = RED to change)");
                } else {
                    t.warn("Choose the alliance: gamepad 1 X = BLUE, B = RED");
                    t.line("Alliance", alliance + " (chosen here)");
                }
                if (heading != null) {
                    t.line("Forward", heading.isFromAuto() ? "from autonomous" : "the way the robot faces now");
                }
                addInitTelemetry(t);
                if (!autoReport.isEmpty()) {
                    t.section("Autonomous");
                    for (String line : autoReport) t.text(line);
                }
                t.update();
            }
            if (isStopRequested()) return;

            // ---- START ----
            timer.start();
            // The saved heading belongs to this run now; a later TeleOp run must not use it.
            if (heading != null) heading.forgetSaved();
            // A button held through START must not count as a press.
            menuKey.ignoreHeldButtons(gamepad1, gamepad2);
            driver.ignoreHeldButtons(gamepad1, gamepad2);
            operator.ignoreHeldButtons(gamepad1, gamepad2);
            onStart();
            boolean menuWasOpen = false;

            while (opModeIsActive()) {
                menuKey.update(gamepad1, gamepad2);
                operator.update(gamepad1, gamepad2);
                timer.update();
                tuning.update();
                if (tuning.isOpen()) {
                    // Gamepad 1 runs the menu: no driver buttons, and the wheels hold still.
                    // Just opened: run the "release" actions of driver buttons still held, so
                    // nothing started by a held button keeps running while the menu is open.
                    // (chord = true first, so letting go of BACK here does not reset "forward".)
                    if (!menuWasOpen) {
                        chord[0] = true;
                        driver.releaseAll();
                    }
                    menuWasOpen = true;
                    driving.hold();
                } else {
                    // Just closed: a button still held from the menu must not act.
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);
                    menuWasOpen = false;
                    driver.update(gamepad1, gamepad2);
                    drive(gamepad1);
                }
                everyLoop();
                robot.update();

                if (tuning.isOpen()) {
                    tuning.addTelemetry(t);
                } else if (helpOpen && help != null) {
                    // The button map (the robot still drives; GUIDE again closes it).
                    t.section("Buttons (GUIDE: close)");
                    for (String h : help) t.text(h);
                } else {
                    if (!driver.getSlowActionWarning().isEmpty()) t.warn(driver.getSlowActionWarning());
                    if (!operator.getSlowActionWarning().isEmpty()) t.warn(operator.getSlowActionWarning());
                    t.line("Time", timer.format());
                    t.line("Drive", driving.status());
                    robot.addTelemetry(t);
                    addTelemetry(t);
                    // Only when debug is on: reading the IMU takes time, so do not read it for nothing.
                    if (heading != null && t.isDebug()) {
                        t.debug("Heading", "%.1f%s", heading.degrees(), heading.isFromAuto() ? " (from auto)" : "");
                    }
                }
                t.update();
            }
        } catch (Throwable e) {
            // Remember it, then let it go on (the Driver Station shows it).
            failure = e;
            throw e;
        } finally {
            // SAFETY: stop the wheels and every mechanism, each on its own, even if one throws.
            RuntimeException first = null;
            try { driving.stop(); } catch (RuntimeException e) { first = e; }
            try { if (robot != null) robot.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
            // A stop error is shown only if the program had no error of its own; otherwise it goes
            // in the robot log, and the Driver Station shows the program's error (the real cause).
            if (first != null) {
                if (failure == null) throw first;
                RobotLog.ee("ftchelper", first, "stopping failed after an earlier error");
            }
        }
    }
}

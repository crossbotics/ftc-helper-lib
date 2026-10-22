package com.crossbotics.ftchelper.teleop;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.RobotLog;
import com.crossbotics.ftchelper.core.Profile;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.drive.DriveConfig;
import com.crossbotics.ftchelper.drive.DriverControls;
import com.crossbotics.ftchelper.drive.DriverHeading;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ToggleRumble;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.match.Alliance;
import com.crossbotics.ftchelper.match.MatchMemory;
import com.crossbotics.ftchelper.match.MatchTimer;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import com.crossbotics.ftchelper.tuning.Tunable;
import com.crossbotics.ftchelper.tuning.TuningMenu;
import java.util.List;

/**
 * A whole TeleOp where you only write what is special about YOUR robot: its mechanisms, the
 * operator's buttons, and any extra screen lines. Everything every TeleOp needs is done for you:
 *
 * <ul>
 *   <li>Driving on gamepad 1 ({@link DriverControls}): left stick drive, right stick X turn, left
 *       trigger slow. Mecanum and tank. Robot-centric to start; Y = field-centric on/off (mecanum).</li>
 *   <li>Field-centric "forward" continues from autonomous ({@link DriverHeading}); BACK resets it.</li>
 *   <li>The alliance from autonomous ({@link #alliance()}; chosen in INIT if no autonomous ran; a
 *       switch during TeleOp only if you turn it on), the 2:00 match timer with gamepad buzzes at 1:00
 *       and 0:20, the tuning menu on GUIDE (PRACTICE profile only), debug telemetry on BACK + START,
 *       warnings for slow code, a tuned value or the PRACTICE profile.</li>
 *   <li>Every one of these buttons can move or be freed: {@link #libraryButtons(LibraryButtons)}.</li>
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
    // Buzzes to confirm a toggle or a reset (two = on, one = off).
    private ToggleRumble confirm, confirm2;
    // How long each loop takes (shown in the debug lines).
    private final com.crossbotics.ftchelper.util.LoopTimer loopTimer = new com.crossbotics.ftchelper.util.LoopTimer();
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
     * The driver's heading for field-centric driving: from the IMU (with autonomous's saved heading).
     * The robot in five files uses its odometry instead when it has one.
     *
     * @param imu the IMU from {@link #createImu()}, or null
     */
    protected DriverHeading createHeading(IMU imu) {
        return imu != null ? new DriverHeading(imu) : null;
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
     * Already taken on gamepad 1 (unless moved with {@link #libraryButtons(LibraryButtons)}): LT, Y
     * (field-centric), BACK, START (with BACK: debug lines), GUIDE.
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

    // A short note shown for a few seconds at the top of the screen ("Pose set...", "Alliance now RED").
    private String noteText = null;
    private long noteAt = 0;

    /**
     * Shows {@code text} as a warning at the top of the Driver Station for 3 seconds (e.g. to confirm
     * a button's job). A new note replaces the old one. Never waits.
     */
    protected final void showNote(String text) {
        noteText = text;
        noteAt = System.nanoTime();
    }

    // Reset forward: "the robot now faces away from me", with one buzz.
    // Only in field-centric driving (mentor, 2026-10-21: only when the robot is relative to the driver);
    // in robot-centric driving "forward" means nothing, so it does nothing and says why.
    private void resetForward() {
        if (heading == null || driving == null) return;
        if (!driving.isFieldCentric()) {
            showNote(keyWords(LibraryButtons.Job.RESET_FORWARD) + ": \"forward\" is only reset in field-centric driving (turn it on first).");
            return;
        }
        heading.reset();
        confirm.play(false);
    }

    /**
     * What a tap of the pause button (BACK) does, or null (default): then BACK resets "forward". With
     * a job here (a Controls.java TeleOp: pause everything), holding BACK {@link #BACK_HOLD_SEC}
     * resets "forward". The buttons can be moved: {@link #libraryButtons(LibraryButtons)}.
     */
    protected Runnable backTap() {
        return null;
    }

    /**
     * How to read "the robot is paused now", or null (default). With one, a press of the pause button
     * pauses AT ONCE (even though BACK also has "hold: reset forward"), and going on is a tap. Without
     * one, {@link #backTap()} runs when BACK is let go. Controls.java gives one.
     */
    protected java.util.function.BooleanSupplier pauseState() {
        return null;
    }

    /**
     * The "set pose here" job, or null (default: none). It sets the pose and returns the words for the
     * Driver Station ("Pose set: ..."). Controls.java gives one on a robot with odometry. Its button
     * works in INIT too (held 1 s before START), so the pose can be set before the match.
     */
    protected java.util.function.Supplier<String> setPoseJob() {
        return null;
    }

    /**
     * With no {@link #setPoseJob()}: a short note shown when the drivers hold the "set pose here" button
     * anyway (alone, 1 s, then let go), so it does not silently do nothing; null (default) = no note.
     * Controls.java says the robot needs odometry for it.
     */
    protected String setPoseMissingNote() {
        return null;
    }

    // Watching the "set pose here" button on a robot without the job (see setPoseMissingNote).
    private Key noPoseKey = null;
    private String noPoseNote = null;
    private long noPoseDownAt = -1;
    private boolean noPosePaired = false;

    // Every loop (INIT and the match): held alone 1 s and let go = show the note. Part of a pair (START,
    // then BACK: debug lines) or START + A/B = not this.
    private void watchMissingSetPose() {
        if (noPoseKey == null) return;
        if (noPoseKey.isDown(gamepad1, gamepad2)) {
            if (noPoseDownAt < 0) {
                noPoseDownAt = System.nanoTime();
                noPosePaired = false;
            }
            if (pairedNow(noPoseKey)) noPosePaired = true;
            return;
        }
        if (noPoseDownAt >= 0 && !noPosePaired && (System.nanoTime() - noPoseDownAt) / 1e9 >= LibraryButtons.HOLD_SEC) {
            showNote(noPoseNote);
        }
        noPoseDownAt = -1;
    }

    /**
     * Called when the alliance changes during TeleOp ({@link #switchAlliance()}), after
     * {@link #alliance()} has the new one: update what uses it. Default: nothing. Must not wait.
     */
    protected void allianceChanged(Alliance now) { }

    /** How long BACK is held to reset "forward" when a tap of BACK has another job, seconds. */
    public static final double BACK_HOLD_SEC = LibraryButtons.HOLD_SEC;

    /** How long GUIDE is held to open the tuning menu when there is a button map, seconds. */
    public static final double GUIDE_HOLD_SEC = LibraryButtons.HOLD_SEC;
    private boolean helpOpen = false;
    private List<String> help = null;

    // ----------------------------- the library's own buttons -----------------------------

    // Where they are (after libraryButtons), and the thing that runs them every loop.
    private LibraryButtons lib;
    private LibraryKeys keys;
    // Slow on/off (the slow toggle button), and "the alliance was switched in TeleOp".
    private boolean slowOn = false;
    private boolean allianceSwitched = false;

    /**
     * Moves or frees the library's own buttons (pause, slow, field-centric, menus...). Default: nothing
     * changes. Example: {@code lib.fieldCentric(Key.g1(Btn.X)).slow(Key.g1(Trigger.RT));}. Two jobs on
     * one button stop at INIT with the button's name. The table: docs/guides/buttons.md.
     *
     * @param lib the buttons, already at their defaults ({@link LibraryButtons#twoDrivers()})
     */
    protected void libraryButtons(LibraryButtons lib) { }

    /**
     * The defaults before {@link #libraryButtons(LibraryButtons)}: {@link LibraryButtons#twoDrivers()},
     * with field-centric on {@link #fieldCentricButton()}. Controls.java gives the one-driver ones.
     */
    protected LibraryButtons standardLibraryButtons() {
        Btn fc = fieldCentricButton();
        return LibraryButtons.twoDrivers().fieldCentric(fc == null ? null : Key.g1(fc));
    }

    /** Where the library's buttons are (after {@link #libraryButtons(LibraryButtons)}); null before INIT. */
    protected final LibraryButtons libraryKeys() {
        return lib;
    }

    /**
     * The library's jobs this TeleOp has: "pause" only with {@link #backTap()}, "field-centric" only
     * where possible, "set pose" only with {@link #setPoseJob()}...
     *
     * @param withButtonMap true if there is a button map ({@link #buttonHelp()})
     */
    protected final java.util.Set<LibraryButtons.Job> libraryJobs(boolean withButtonMap) {
        java.util.Set<LibraryButtons.Job> jobs = java.util.EnumSet.allOf(LibraryButtons.Job.class);
        if (backTap() == null) jobs.remove(LibraryButtons.Job.PAUSE);
        if (heading == null) jobs.remove(LibraryButtons.Job.RESET_FORWARD);
        if (driving == null || !driving.canFieldCentric()) jobs.remove(LibraryButtons.Job.FIELD_CENTRIC);
        if (!withButtonMap) jobs.remove(LibraryButtons.Job.BUTTON_MAP);
        if (setPoseJob() == null) jobs.remove(LibraryButtons.Job.SET_POSE);
        return jobs;
    }

    /** "G1 BACK" for a job's button ("BACK" alone on gamepad 1), or "(no button)". */
    protected final String keyWords(LibraryButtons.Job job) {
        Key k = lib == null ? null : lib.key(job);
        if (k == null) return "(no button)";
        String one = k.pad == Pad.G1 ? k.name() : k.toString();
        Key w = lib.with(job);
        if (w == null) return one;
        // A pair with the pause button: the other button goes first ("START, then BACK").
        Key pause = lib.key(LibraryButtons.Job.PAUSE);
        if (pause != null && libraryJobs(true).contains(LibraryButtons.Job.PAUSE)) {
            if (pause.equals(k)) return w.name() + ", then " + k.name();
            if (pause.equals(w)) return one + ", then " + w.name();
        }
        return one + " + " + w.name();
    }

    /**
     * Switches the alliance now (BLUE to RED, or RED to BLUE), in case the wrong one was chosen: everything
     * that reads {@link #alliance()} or {@code MatchMemory} gets the new one, both gamepads buzz twice
     * and the Driver Station says so. The switch-alliance buttons call it (both held
     * {@link LibraryButtons#SWITCH_ALLIANCE_SEC} s).
     */
    protected final void switchAlliance() {
        Alliance was = alliance;
        alliance = alliance.other();
        allianceSwitched = true;
        // A TeleOp started again in this match (or the simulator) sees the corrected one too.
        MatchMemory.changeAlliance(alliance);
        if (confirm != null) confirm.play(true);
        if (confirm2 != null) confirm2.play(true);
        showNote("Alliance now " + alliance + " (was " + was + ")");
        // Last, so it may replace the note with a longer one.
        allianceChanged(alliance);
    }

    /**
     * The driving speed the library's slow buttons ask for now, 0 to 1 (1 = full speed): the slow
     * trigger (the further, the slower), a slow button held, or slow on/off.
     */
    protected final double driveSpeed() {
        double slowest = Math.max(0, Math.min(1, slowSpeed()));
        double speed = slowOn ? slowest : 1;
        Key k = lib == null ? null : lib.key(LibraryButtons.Job.SLOW);
        if (k != null) {
            double s = k.isTrigger() ? StickShaping.slowScale(k.pull(gamepad1, gamepad2), slowest)
                    : k.isDown(gamepad1, gamepad2) ? slowest : 1;
            speed = Math.min(speed, s);
        }
        return speed;
    }

    // The buzz for a job's button: on the gamepad it is on.
    private ToggleRumble confirmFor(LibraryButtons.Job job) {
        Key k = lib.key(job);
        return k != null && k.pad == Pad.G2 && confirm2 != null ? confirm2 : confirm;
    }

    // The library's jobs (what each button does), for LibraryKeys.
    private java.util.Map<LibraryButtons.Job, Runnable> libraryJobMap() {
        java.util.Map<LibraryButtons.Job, Runnable> jobs = LibraryKeys.jobs();
        final Runnable tap = backTap();
        if (tap != null) jobs.put(LibraryButtons.Job.PAUSE, tap);
        jobs.put(LibraryButtons.Job.RESET_FORWARD, this::resetForward);
        jobs.put(LibraryButtons.Job.DEBUG, () -> t.toggleDebug());
        jobs.put(LibraryButtons.Job.SLOW_TOGGLE, () -> slowOn = !slowOn);
        jobs.put(LibraryButtons.Job.FIELD_CENTRIC, () -> confirmFor(LibraryButtons.Job.FIELD_CENTRIC).play(driving.toggleFieldCentric()));
        jobs.put(LibraryButtons.Job.BUTTON_MAP, () -> {
            // A tap shows the map (or closes the tuning menu).
            if (tuning.isOpen()) tuning.toggle();
            else helpOpen = !helpOpen;
        });
        jobs.put(LibraryButtons.Job.TUNING_MENU, () -> {
            helpOpen = false;
            tuning.toggle();
        });
        final java.util.function.Supplier<String> pose = setPoseJob();
        if (pose != null) {
            jobs.put(LibraryButtons.Job.SET_POSE, () -> {
                String words = pose.get();
                showNote(words);
                if (words != null && words.startsWith("Pose set")) confirmFor(LibraryButtons.Job.SET_POSE).play(true);
            });
        }
        jobs.put(LibraryButtons.Job.SWITCH_ALLIANCE, this::switchAlliance);
        return jobs;
    }

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
     * A shortcut: {@link #libraryButtons(LibraryButtons)} can put it on any button of either gamepad.
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
        // The speed from the slow trigger (or the slow button you moved it to).
        driving.update(gamepad, driveSpeed());
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

    // "gamepad 1 X = BLUE, B = RED" from the INIT buttons ("" if there are none).
    private String chooseWords() {
        Key blue = lib.initBlue(), red = lib.initRed();
        if (blue == null && red == null) return "";
        boolean samePad = blue != null && red != null && blue.pad == red.pad;
        StringBuilder b = new StringBuilder();
        if (samePad) b.append(blue.pad == Pad.G1 ? "gamepad 1 " : "gamepad 2 ");
        if (blue != null) b.append(samePad ? blue.name() : blue.toString()).append(" = BLUE");
        if (red != null) b.append(blue != null ? ", " : "").append(samePad ? red.name() : red.toString()).append(" = RED");
        return b.toString();
    }

    /**
     * The INIT warning when this TeleOp can pause ({@code jobs} has PAUSE) but no button pauses
     * ({@code lib.pause(null)}), or null. Package-private and static for tests.
     */
    static String noPauseWarning(LibraryButtons lib, java.util.Set<LibraryButtons.Job> jobs) {
        if (!jobs.contains(LibraryButtons.Job.PAUSE) || lib.key(LibraryButtons.Job.PAUSE) != null) return null;
        return "No pause button (libraryButtons freed it): nothing on the gamepads pauses the robot";
    }

    /** The INIT note when field-centric is possible but has no button, or null (tests). */
    static String noFieldCentricNote(LibraryButtons lib, boolean startsFieldCentric) {
        if (lib.key(LibraryButtons.Job.FIELD_CENTRIC) != null) return null;
        return "No field-centric button (libraryButtons): driving stays " + (startsFieldCentric ? "field-centric" : "robot-centric");
    }

    // In INIT: the other button of a pair this key is in (debug, switch alliance) is down too, or
    // START + A/B (pairing a gamepad on the Driver Station): not this key's own job.
    private boolean pairedNow(Key k) {
        for (LibraryButtons.Job j : new LibraryButtons.Job[] {LibraryButtons.Job.DEBUG, LibraryButtons.Job.SWITCH_ALLIANCE}) {
            Key a = lib.key(j), b = lib.with(j);
            if (a == null || b == null) continue;
            if (a.equals(k) && b.isDown(gamepad1, gamepad2)) return true;
            if (b.equals(k) && a.isDown(gamepad1, gamepad2)) return true;
        }
        com.qualcomm.robotcore.hardware.Gamepad g = k.pad == Pad.G1 ? gamepad1 : gamepad2;
        return k.btn == Btn.START && g != null && (g.a || g.b);
    }

    // An INIT alliance button is down (not while START is held on its gamepad: START + A/B pairs a
    // gamepad on the Driver Station).
    private boolean initPressed(Key k) {
        if (k == null || !k.isDown(gamepad1, gamepad2)) return false;
        com.qualcomm.robotcore.hardware.Gamepad g = k.pad == Pad.G1 ? gamepad1 : gamepad2;
        return g != null && !g.start;
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
        heading = createHeading(imu);
        driving = DriverControls.create(hardwareMap, driveConfig(), heading).slowSpeed(slowSpeed());
        driving.setFieldCentric(startFieldCentric());
        configureDriving(driving);
        alliance = MatchMemory.allianceOr(Alliance.BLUE);
        timer = MatchTimer.teleOp();
        for (double s : buzzAtSecondsLeft()) timer.rumbleAt(s, gamepad1, gamepad2);
        tuning = new TuningMenu(gamepad1);
        confirm = new ToggleRumble(gamepad1);
        confirm2 = gamepad2 == null ? null : new ToggleRumble(gamepad2);

        // Two groups of the team's buttons, so the driver's can pause while the tuning menu uses
        // gamepad 1. The library's own buttons (pause, field-centric, menus...) are run by LibraryKeys.
        ControllerBindings driver = new ControllerBindings();
        ControllerBindings operator = new ControllerBindings();

        Subsystems robot = null;
        // The first error from the program itself, if any (so an error while stopping cannot hide it).
        Throwable failure = null;
        try {
            // Inside the "try": if making the mechanisms fails, the wheels are still stopped below.
            robot = createSubsystems();
            if (robot == null) robot = new Subsystems();
            // The library's buttons: the defaults, then the team's moves (checked below).
            lib = standardLibraryButtons();
            libraryButtons(lib);
            driving.swapSticks(lib.sticksSwapped());
            bindButtons(driver, operator);
            // After bindButtons, so the button map is known.
            help = buttonHelp();
            // Checks the library's buttons (two jobs on one button stop here, with its name).
            java.util.Set<LibraryButtons.Job> jobsHere = libraryJobs(help != null);
            java.util.Map<LibraryButtons.Job, Runnable> jobMap = libraryJobMap();
            keys = new LibraryKeys(lib.slots(jobsHere), jobMap, System::nanoTime).pauseAtPress(pauseState());
            // "Set pose here" works in INIT too (held 1 s before START): done before the buzzer.
            Runnable poseJob = jobsHere.contains(LibraryButtons.Job.SET_POSE) ? jobMap.get(LibraryButtons.Job.SET_POSE) : null;
            // No "set pose here" on this robot: a note when its button is held anyway.
            noPoseNote = poseJob == null ? setPoseMissingNote() : null;
            noPoseKey = noPoseNote == null ? null : lib.key(LibraryButtons.Job.SET_POSE);
            Key poseKey = poseJob == null ? null : lib.key(LibraryButtons.Job.SET_POSE);
            long poseDownAt = -1;
            boolean poseBlocked = false, poseSetHere = false;
            // Jobs the TeleOp has but no button does: say so (a freed pause button means nothing
            // can stop the robot from the gamepad).
            String noPause = noPauseWarning(lib, jobsHere);
            String noFieldCentric = driving.canFieldCentric() ? noFieldCentricNote(lib, startFieldCentric()) : null;
            // The alliance switch in TeleOp is opt-in (lib.switchAlliance(...)): say whether it is on.
            String switchWords = lib.key(LibraryButtons.Job.SWITCH_ALLIANCE) == null
                    ? "off in TeleOp (lib.switchAlliance(...) turns it on)"
                    : "in TeleOp: hold " + keyWords(LibraryButtons.Job.SWITCH_ALLIANCE)
                            + (keyWords(LibraryButtons.Job.SWITCH_ALLIANCE).contains(", then") ? ", " : " ")
                            + LibraryButtons.SWITCH_ALLIANCE_SEC + " s to switch";

            // ---- INIT loop: warnings and what the drivers need to know. Never waits. ----
            boolean fromAuto = MatchMemory.hasAlliance();
            List<String> autoReport = MatchMemory.lastReport();
            // The alliance in use: autonomous's, or none until the drivers choose one below.
            MatchMemory.setChosen(fromAuto ? alliance : null);
            boolean chosenHere = false;
            String choose = chooseWords();
            while (opModeInInit()) {
                // The alliance: from autonomous, or chosen here (default gamepad 1: X = BLUE, B = RED).
                // (not while START is held: START+B pairs a gamepad on the Driver Station)
                Alliance before = alliance;
                if (initPressed(lib.initBlue())) { alliance = Alliance.BLUE; fromAuto = false; chosenHere = true; }
                if (initPressed(lib.initRed())) { alliance = Alliance.RED; fromAuto = false; chosenHere = true; }
                if (fromAuto || chosenHere) MatchMemory.setChosen(alliance);
                // The pose was set here for the other alliance: set it again for this one.
                if (alliance != before && poseSetHere) poseJob.run();
                // "Set pose here" held 1 s in INIT (not while START + A/B pairs a gamepad, not as part
                // of a pair like BACK + START). Once per press.
                if (poseKey != null) {
                    boolean down = poseKey.isDown(gamepad1, gamepad2);
                    if (!down) {
                        poseBlocked = false;
                        poseDownAt = -1;
                    } else if (!poseBlocked) {
                        if (pairedNow(poseKey)) {
                            poseBlocked = true;
                        } else if (poseDownAt < 0) {
                            poseDownAt = System.nanoTime();
                        } else if ((System.nanoTime() - poseDownAt) / 1e9 >= LibraryButtons.HOLD_SEC) {
                            poseJob.run();
                            poseSetHere = true;
                            poseBlocked = true;
                        }
                    }
                }
                watchMissingSetPose();
                if (Profile.current().isPractice()) t.warn("PRACTICE profile");
                if (noPause != null) t.warn(noPause);
                if (noteText != null && System.nanoTime() - noteAt < 3e9) t.warn(noteText);
                int changed = Tunable.countChanged();
                if (changed > 0) t.warn(changed + " tuned value(s) differ from the code");
                if (choose.isEmpty()) {
                    t.line("Alliance", alliance + (fromAuto ? " (from autonomous)" : " (no INIT buttons to choose it)"));
                } else if (fromAuto) {
                    t.line("Alliance", alliance + " (from autonomous; " + choose + " to change)");
                } else if (chosenHere) {
                    t.line("Alliance", alliance + " (chosen here)");
                } else {
                    t.warn("Choose the alliance: " + choose);
                    t.line("Alliance", alliance + " until you choose");
                }
                t.line("Switch alliance", switchWords);
                if (heading != null) {
                    t.line("Forward", heading.isFromAuto() ? "from autonomous" : "the way the robot faces now");
                }
                if (noFieldCentric != null) t.text(noFieldCentric);
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
            keys.ignoreHeld(gamepad1, gamepad2);
            driver.ignoreHeldButtons(gamepad1, gamepad2);
            operator.ignoreHeldButtons(gamepad1, gamepad2);
            onStart();
            boolean menuWasOpen = false;
            java.util.function.BooleanSupplier pausedNow = pauseState();
            boolean wasPaused = false;

            while (opModeIsActive()) {
                // The library's buttons (pause, field-centric, menus...); only the menu's while it is open.
                keys.update(gamepad1, gamepad2, tuning.isOpen());
                if (!tuning.isOpen()) watchMissingSetPose();
                // Paused (BACK): no button job of the team's or a part's runs. As the pause begins, a held
                // button's "let go" job runs once (a held intake stops); going on, a button still held
                // from the pause does not count as a press.
                boolean paused = pausedNow != null && pausedNow.getAsBoolean();
                if (paused && !wasPaused) {
                    driver.releaseAll();
                    operator.releaseAll();
                } else if (!paused && wasPaused) {
                    driver.ignoreHeldButtons(gamepad1, gamepad2);
                    operator.ignoreHeldButtons(gamepad1, gamepad2);
                }
                wasPaused = paused;
                if (!paused) operator.update(gamepad1, gamepad2);
                timer.update();
                tuning.update();
                if (tuning.isOpen()) {
                    // Gamepad 1 runs the menu: no driver buttons, and the wheels hold still.
                    // Just opened: run the "release" actions of driver buttons still held, so
                    // nothing started by a held button keeps running while the menu is open.
                    // (The library's own buttons wait until they are let go: LibraryKeys.)
                    if (!menuWasOpen) driver.releaseAll();
                    menuWasOpen = true;
                    driving.hold();
                } else {
                    // Just closed: a button still held from the menu must not act.
                    if (menuWasOpen) driver.ignoreHeldButtons(gamepad1, gamepad2);
                    menuWasOpen = false;
                    if (!paused) driver.update(gamepad1, gamepad2);
                    drive(gamepad1);
                }
                loopTimer.update();
                everyLoop();
                robot.update();

                if (tuning.isOpen()) {
                    tuning.addTelemetry(t);
                } else if (helpOpen && help != null) {
                    // The button map (the robot still drives; GUIDE again closes it).
                    t.section("Buttons (" + keyWords(LibraryButtons.Job.BUTTON_MAP) + ": close)");
                    for (String h : help) t.text(h);
                } else {
                    if (!driver.getSlowActionWarning().isEmpty()) t.warn(driver.getSlowActionWarning());
                    if (noteText != null && System.nanoTime() - noteAt < 3e9) t.warn(noteText);
                    if (allianceSwitched) t.line("Alliance", alliance + " (switched in TeleOp)");
                    if (!operator.getSlowActionWarning().isEmpty()) t.warn(operator.getSlowActionWarning());
                    t.line("Time", timer.format());
                    t.line("Drive", driving.status());
                    robot.addTelemetry(t);
                    addTelemetry(t);
                    // Only when debug is on: reading the IMU takes time, so do not read it for nothing.
                    if (heading != null && t.isDebug()) {
                        t.debug("Heading", "%.1f%s", heading.degrees(), heading.isFromAuto() ? " (from auto)" : "");
                    }
                    // The loop time (debug lines: BACK + START). Lower is better; under 20 ms is good.
                    t.debug("Loop", "%.1f ms (%.0f Hz)", loopTimer.getAverageMs(), loopTimer.getHz());
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

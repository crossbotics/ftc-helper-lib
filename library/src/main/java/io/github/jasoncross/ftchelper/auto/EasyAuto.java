package io.github.jasoncross.ftchelper.auto;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.RobotLog;
import io.github.jasoncross.ftchelper.core.Profile;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.core.Subsystems;
import io.github.jasoncross.ftchelper.drive.AutoDrive;
import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.match.Alliance;
import io.github.jasoncross.ftchelper.match.InitMenu;
import io.github.jasoncross.ftchelper.match.MatchMemory;
import io.github.jasoncross.ftchelper.match.MatchTimer;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;
import io.github.jasoncross.ftchelper.tuning.Tunable;
import io.github.jasoncross.ftchelper.util.Angles;
import io.github.jasoncross.ftchelper.util.FieldPose;

/**
 * The easiest way to write an autonomous: fill in a few methods, and list your steps.
 * (Autonomous steps block on purpose; everything else here keeps the loop running.)
 * Everything else is done for you:
 *
 * <ol>
 *   <li>INIT: sets up the drive, IMU and your mechanisms; shows a menu (alliance, start delay,
 *       your own choices, profile) and where to place the robot; warns about tuned values and the
 *       PRACTICE profile.</li>
 *   <li>START: starts the 30 s timer, waits the chosen delay, runs your steps (mirrored for RED).</li>
 *   <li>End: stops everything (even after an error), remembers the alliance and heading for TeleOp,
 *       and shows what happened to each step.</li>
 * </ol>
 *
 * <p>{@code abstract} means this class is not finished on its own: your class "extends EasyAuto"
 * and fills in the {@code abstract} methods below (marked "you fill these in"). The methods marked
 * "optional" already have a body; write your own version only if you need something different.
 *
 * <pre>{@code
 * @Autonomous(name = "Basket Side", preselectTeleOp = "Main TeleOp")
 * public class BasketSide extends EasyAuto {
 *     private Robot robot;
 *
 *     protected RobotConfig robotConfig() { return new RobotSettings(); }   // shared with TeleOp
 *     protected Subsystems createSubsystems() { robot = new Robot(hardwareMap); return robot.all; }
 *     protected String startPosition() { return "Back wall, 2nd tile from the left, facing in"; }
 *
 *     protected void buildRoutine(AutoRoutine r) {
 *         r.move("Drive out", 24, 0, 0.6)
 *          .step("Lift up", robot.lift.goToStep("HIGH"))
 *          .run("Drop", robot.claw::open)
 *          .thenPark()
 *          .move("Park", 24, 180, 0.8);
 *     }
 * }
 * }</pre>
 *
 * Docs: docs/guides/easy-autos.md (Tutorial 16, docs/tutorials/16-easy-autos.md, builds one step by step)
 */
public abstract class EasyAuto extends LinearOpMode {

    private InitMenu menu;
    private AutoRoutine routine;
    private AutoDrive autoDrive;
    // The mechanisms from createSubsystems(). Never null once made (an empty group if you return null).
    private Subsystems robot;
    // True once START was pressed. Only then is the alliance and heading handed to TeleOp.
    private boolean started = false;

    // ----------------------------- you fill these in -----------------------------
    // ("protected": only your OpMode class, which extends this one, can see and fill these in.)

    /**
     * Your robot's settings, shared with TeleOp (see {@link RobotConfig}). Return
     * {@code new RobotSettings()}. If you do not have one, override {@link #driveConfig()},
     * {@link #autoDriveConfig()} and {@link #createImu()} instead.
     */
    protected RobotConfig robotConfig() {
        return null;
    }

    /** The drivetrain settings. Default: {@code robotConfig().drive()}. */
    protected DriveConfig driveConfig() {
        return needConfig().drive();
    }

    /** The autonomous tuning. Default: {@code robotConfig().auto()}. */
    protected AutoDriveConfig autoDriveConfig() {
        return needConfig().auto();
    }

    /** The IMU, set up for your hub's mounting. Default: {@code robotConfig().imu(...)}. */
    protected IMU createImu() {
        return needConfig().imu(hardwareMap);
    }

    // robotConfig(), or a clear message saying what to fill in.
    private RobotConfig needConfig() {
        RobotConfig config = robotConfig();
        if (config == null) {
            throw new IllegalStateException("EasyAuto needs your robot settings: override robotConfig() "
                    + "(return new RobotSettings()), or driveConfig(), autoDriveConfig() and createImu().");
        }
        return config;
    }

    /** Adds the steps. Runs AFTER INIT, so {@link #alliance()} and {@link #choice(String)} are known. */
    protected abstract void buildRoutine(AutoRoutine routine);

    // ----------------------------- optional -----------------------------

    /**
     * Your mechanisms, kept working during every step. Default: none. Returning null also means none.
     * It is called again (after START) if the drivers change the profile in INIT, so the mechanisms
     * get the new profile's values: make new objects here, do not keep old ones.
     */
    protected Subsystems createSubsystems() {
        return new Subsystems();
    }

    /** Extra INIT choices, e.g. {@code menu.choice("Route", "Near", "Far")}. */
    protected void addChoices(InitMenu menu) { }

    /** Extra lines for the Driver Station during INIT (before START), every INIT loop. */
    protected void addInitTelemetry(RobotTelemetry t) { }

    /** Where to place the robot, shown in INIT so the drive team sets it up right. */
    protected String startPosition() {
        return null;
    }

    /** Seconds kept at the end for the steps after {@code thenPark()}. Default 0. */
    protected double parkReserveSec() {
        return 0;
    }

    /**
     * True (default): routes are written for BLUE and mirrored left/right for RED. Return false for a
     * field that is ROTATED between alliances (then the same robot-relative route works for both).
     * Check each season's field drawing.
     */
    protected boolean mirrorForRed() {
        return true;
    }

    /**
     * Which way the robot faces at the start, compared with "straight away from your driver", in
     * degrees (left = positive). Default 0. TeleOp's field-centric "forward" uses it (see
     * drive/DriverHeading.java). Example: the robot starts turned a quarter turn left, so return 90.
     * Write it for BLUE, like the routes: it is mirrored for RED when {@link #mirrorForRed()} is true.
     */
    protected double startHeadingDeg() {
        return 0;
    }

    /** Longest start delay offered in the menu, seconds. Default 10. */
    protected double maxStartDelaySec() {
        return 10;
    }

    // ----------------------------- for your steps -----------------------------

    /** The alliance chosen in INIT. */
    protected Alliance alliance() {
        return menu.getAlliance();
    }

    /** An INIT choice (from {@link #addChoices}). */
    protected String choice(String label) {
        return menu.get(label);
    }

    /** The drive, for custom steps. */
    protected AutoDrive autoDrive() {
        return autoDrive;
    }

    // ----------------------------- the program -----------------------------

    /**
     * The whole autonomous, from INIT to the end. The FTC SDK calls this when you press INIT.
     * {@code final}: your class cannot replace it, so the safety steps here always run.
     */
    @Override
    public final void runOpMode() {
        // ---- Setup (INIT) ----
        RobotTelemetry t = new RobotTelemetry(telemetry);
        // Bulk reads: each hub answers with all its motor readings in one message instead of one at a
        // time, which makes every loop faster. See hardware/BulkReads.java.
        BulkReads.auto(hardwareMap);
        IMU imu = createImu();
        if (imu == null) {
            throw new IllegalStateException("Autonomous needs the IMU (turn sensor) for its turns, but "
                    + "createImu() / RobotSettings.imu() returned null. Return ImuSetup.get(...) there.");
        }
        autoDrive = new AutoDrive(this, driveConfig(), autoDriveConfig(), imu);
        // Remember the profile (PRACTICE / COMPETITION) now, to notice if the drivers change it in the menu.
        Profile profileAtInit = Profile.current();
        // try/finally: the "finally" part runs however the "try" part ends: finished, stopped,
        // or crashed with an error. So the robot is always stopped at the end.
        // The first error from the program itself, if any (so an error while stopping cannot hide it).
        Throwable failure = null;
        try {
            // Inside the "try": if making the mechanisms fails, the wheels are still stopped below.
            robot = makeSubsystems();
            runAfterSetup(t, imu, profileAtInit);
        } catch (Throwable e) {
            // Remember it, then let it go on (the Driver Station shows it).
            failure = e;
            throw e;
        } finally {
            // Stop EVERYTHING, each part on its own, even if one of them throws.
            // Each part gets its own try/catch: if stopping the mechanisms throws an error, we still
            // stop the wheels. We keep the FIRST error and throw it at the very end, so it is not lost.
            RuntimeException first = null;
            try { if (robot != null) robot.stop(); } catch (RuntimeException e) { first = e; }
            try { if (autoDrive != null) autoDrive.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
            try {
                // Hand the alliance and the final heading to TeleOp (see match/MatchMemory.java),
                // so ONE TeleOp works for both alliances and knows which way the robot ended up facing.
                // Only after START: if STOP was pressed in INIT, the last match's memory stays as it was.
                if (started && menu != null && autoDrive != null) {
                    // Heading compared with the driver = the start direction + how far autonomous turned.
                    // The start heading is written for BLUE, like the routes: mirrored for RED.
                    double start = mirrorForRed() ? alliance().mirror(startHeadingDeg()) : startHeadingDeg();
                    double endHeading = Angles.wrapDegrees(start + autoDrive.headingDeg());
                    MatchMemory.remember(alliance(), new FieldPose(0, 0, endHeading));
                }
            } catch (RuntimeException e) { if (first == null) first = e; }
            // A stop error is shown only if the program had no error of its own; otherwise it goes
            // in the robot log, and the Driver Station shows the program's error (the real cause).
            if (first != null) {
                if (failure == null) throw first;
                RobotLog.ee("ftchelper", first, "stopping failed after an earlier error");
            }
        }
    }

    // Your createSubsystems(), but never null: "return null" counts as "no mechanisms".
    private Subsystems makeSubsystems() {
        Subsystems made = createSubsystems();
        return made != null ? made : new Subsystems();
    }

    // Everything after the drive, IMU and mechanisms are made: the INIT menu, the steps, the report.
    private void runAfterSetup(RobotTelemetry t, IMU imu, Profile profileAtInit) {

        // ---- The INIT menu: alliance, start delay, your own choices, and the profile last ----
        menu = new InitMenu(gamepad1).alliance().delay(maxStartDelaySec());
        addChoices(menu);
        menu.profile();

        // ---- INIT loop: runs until START (or STOP) is pressed. It never waits: one quick pass per loop. ----
        while (opModeInInit()) {
            // read the gamepad and move through the menu
            menu.update();
            // Warnings that stay on top of the screen, so nobody plays a real match by mistake with
            // the practice settings or with test values changed in the tuning menu.
            if (Profile.current().isPractice()) t.warn("PRACTICE profile (change to COMPETITION at events)");
            int changed = Tunable.countChanged();
            if (changed > 0) t.warn(changed + " tuned value(s) differ from the code");
            // tell the drive team where to put the robot
            String where = startPosition();
            if (where != null) t.line("Place robot", where);
            menu.addTelemetry(t);
            addInitTelemetry(t);
            t.update();
        }
        // STOP was pressed during INIT: do nothing (the "finally" in runOpMode still stops everything).
        if (isStopRequested()) return;
        started = true;
        if (Profile.current() != profileAtInit) {
            // The profile changed in INIT: rebuild the drive AND the mechanisms, so profile-dependent
            // values (power limits, speeds) apply. They read their settings when they were made,
            // before the menu, so they still have the old ones.
            autoDrive = new AutoDrive(this, driveConfig(), autoDriveConfig(), imu);
            robot.stop();
            robot = makeSubsystems();
        }

        // ---- START: the match clock begins now ----
        MatchTimer timer = MatchTimer.autonomous();
        timer.start();
        // Build the routine with everything chosen in INIT. Each ".something(...)" sets one thing and
        // hands the routine back, so the settings can be chained like this.
        // "a ? b : c" means "if a then b, otherwise c": mirror for the chosen alliance, or not at all.
        routine = new AutoRoutine(this)
                .drive(autoDrive)
                .subsystems(robot)
                .telemetry(t)
                .timer(timer)
                .mirrorFor(mirrorForRed() ? alliance() : null)
                .reserveForPark(parkReserveSec());
        // The start delay is the first step, so the mechanisms keep updating while we wait.
        if (menu.getDelaySec() > 0) routine.waitFor("Start delay", menu.getDelaySec());
        // Your steps, added after the menu so they can use the alliance and choices.
        buildRoutine(routine);
        // Run every step. BLOCKS (on purpose) until all steps are done or the OpMode stops.
        routine.runAll();
        // Done: stop the mechanisms and wheels now (not only at the very end), so nothing keeps moving.
        robot.stop();
        autoDrive.stop();
        // ---- The report: show what happened to each step until the OpMode ends ----
        while (opModeIsActive()) {
            t.section("Autonomous finished");
            for (String line : routine.getReport()) t.text(line);
            t.update();
            // give the phone a moment for its own work
            idle();
        }
    }
}

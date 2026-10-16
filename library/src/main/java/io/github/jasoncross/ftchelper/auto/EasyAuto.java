package io.github.jasoncross.ftchelper.auto;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.IMU;
import io.github.jasoncross.ftchelper.core.Profile;
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
import io.github.jasoncross.ftchelper.util.FieldPose;

/**
 * The easiest way to write an autonomous: fill in a few methods, and list your steps.
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
 * <pre>{@code
 * @Autonomous(name = "Basket Side", preselectTeleOp = "Main TeleOp")
 * public class BasketSide extends EasyAuto {
 *     private Robot robot;
 *
 *     protected DriveConfig driveConfig() { return RobotSettings.drive(); }
 *     protected AutoDriveConfig autoDriveConfig() { return RobotSettings.auto(); }
 *     protected IMU createImu() { return RobotSettings.imu(hardwareMap); }
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
 * Docs: docs/guides/easy-autos.md
 */
public abstract class EasyAuto extends LinearOpMode {

    private InitMenu menu;
    private AutoRoutine routine;
    private AutoDrive autoDrive;

    // ----------------------------- you fill these in -----------------------------

    /** The drivetrain settings (use the same as TeleOp, e.g. {@code RobotSettings.drive()}). */
    protected abstract DriveConfig driveConfig();

    /** The autonomous tuning. */
    protected abstract AutoDriveConfig autoDriveConfig();

    /** The IMU, set up for your hub's mounting. */
    protected abstract IMU createImu();

    /** Adds the steps. Runs AFTER INIT, so {@link #alliance()} and {@link #choice(String)} are known. */
    protected abstract void buildRoutine(AutoRoutine routine);

    // ----------------------------- optional -----------------------------

    /** Your mechanisms, kept working during every step. Default: none. */
    protected Subsystems createSubsystems() {
        return new Subsystems();
    }

    /** Extra INIT choices, e.g. {@code menu.choice("Route", "Near", "Far")}. */
    protected void addChoices(InitMenu menu) { }

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

    @Override
    public final void runOpMode() {
        RobotTelemetry t = new RobotTelemetry(telemetry);
        BulkReads.auto(hardwareMap);
        IMU imu = createImu();
        autoDrive = new AutoDrive(this, driveConfig(), autoDriveConfig(), imu);
        Subsystems robot = createSubsystems();
        Profile profileAtInit = Profile.current();
        try {
            runAfterSetup(t, imu, robot, profileAtInit);
        } finally {
            // Stop EVERYTHING, each part on its own, even if one of them throws.
            RuntimeException first = null;
            try { robot.stop(); } catch (RuntimeException e) { first = e; }
            try { if (autoDrive != null) autoDrive.stop(); } catch (RuntimeException e) { if (first == null) first = e; }
            try {
                if (menu != null && autoDrive != null) {
                    MatchMemory.remember(alliance(), new FieldPose(0, 0, autoDrive.headingDeg()));
                }
            } catch (RuntimeException e) { if (first == null) first = e; }
            if (first != null) throw first;
        }
    }

    private void runAfterSetup(RobotTelemetry t, IMU imu, Subsystems robot, Profile profileAtInit) {

        menu = new InitMenu(gamepad1).alliance().delay(maxStartDelaySec());
        addChoices(menu);
        menu.profile();

        while (opModeInInit()) {
            menu.update();
            if (Profile.current().isPractice()) t.warn("PRACTICE profile (change to COMPETITION at events)");
            int changed = Tunable.countChanged();
            if (changed > 0) t.warn(changed + " tuned value(s) differ from the code");
            String where = startPosition();
            if (where != null) t.line("Place robot", where);
            menu.addTelemetry(t);
            t.update();
        }
        if (isStopRequested()) return;
        if (Profile.current() != profileAtInit) {
            // The profile changed in INIT: rebuild the drive so profile-dependent values (power limit) apply.
            autoDrive = new AutoDrive(this, driveConfig(), autoDriveConfig(), imu);
        }

        MatchTimer timer = MatchTimer.autonomous();
        timer.start();
        routine = new AutoRoutine(this)
                .drive(autoDrive)
                .subsystems(robot)
                .telemetry(t)
                .timer(timer)
                .mirrorFor(mirrorForRed() ? alliance() : null)
                .reserveForPark(parkReserveSec());
        if (menu.getDelaySec() > 0) routine.waitFor("Start delay", menu.getDelaySec());
        buildRoutine(routine);
        routine.runAll();
        robot.stop();
        autoDrive.stop();
        while (opModeIsActive()) {
            t.section("Autonomous finished");
            for (String line : routine.getReport()) t.text(line);
            t.update();
            idle();
        }
    }
}

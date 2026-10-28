package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.teleop.EasyTeleOp;
import com.crossbotics.ftchelper.teleop.Key;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The team's TeleOp and button map in one file, {@code Controls.java}. Every module on the robot
 * already has its standard buttons; you only write what is different.
 *
 * <pre>{@code
 * @TeleOp(name = "TeleOp")
 * public class Controls extends ControlMap<Robot> {
 *     public static boolean oneDriver = false;   // true: everything on gamepad 1
 *
 *     @Override protected void buttons(Robot robot, Pad g1, Pad g2) {
 *         g2.y.press(Actions.scoreHigh(robot));   // replaces the lift's "high" on Y
 *         g2.rightStickButton.free();              // free: nothing yet
 *     }
 * }
 * }</pre>
 *
 * <p><b>The library's own buttons</b> (gamepad 1 by default): left stick drives (and strafes on
 * mecanum), right stick X turns, LT slow (the further it is pulled, the slower), Y field-centric on/off
 * (mecanum), BACK pauses everything at the press (motors on brake; a tap again to go on), holding BACK
 * 1 s also resets "forward" (field-centric), hold START then press BACK: debug lines (BACK first is
 * just a pause), START held 1 s sets the pose to
 * the reset spot (robots with odometry; sticks let go; also in INIT), GUIDE shows the button map (a
 * tap) or opens the tuning menu (held 1 s, PRACTICE profile). Switching the alliance during TeleOp is
 * off unless you turn it on: {@code lib.switchAlliance(g1.start, g1.back)}. With {@code oneDriver = true}: left stick click slow
 * on/off, right stick click field-centric, and the operator's buttons move to gamepad 1. Every one can
 * be moved or freed in {@link #libraryButtons(LibraryButtons, Pad, Pad)}.
 *
 * <p><b>Checked at INIT:</b> a button with two jobs in this file, or a job on a driving button, stops
 * with the button's name. INIT shows the whole map, the Tunables notes and any tuned values that
 * differ from Tunables.java (lines to copy back).
 *
 * <p>Everything else is {@link EasyTeleOp}: match timer, buzzes, everything stopped at the end.
 *
 * Docs: docs/modules/robot.md (section "Controls")
 */
public abstract class ControlMap<R extends RobotDefinition> extends EasyTeleOp {
    private R robot;
    private Boolean oneDriver = null;
    private ButtonPlan plan;
    private final List<ButtonPlan.StickJob> sticks = new ArrayList<>();
    private boolean pausedSticks = false;
    private boolean menuWasOpen = false;

    /**
     * Your buttons, after the modules' standard ones. A job here replaces a module's suggestion on
     * the same button.
     *
     * <p>It runs once, in INIT. Code of your own for this TeleOp only goes here too:
     * {@code robot.duringInit(...)}, {@code robot.atStart(...)}, {@code robot.everyLoop(...)},
     * {@code robot.atStop(...)} (docs/guides/your-own-code.md).
     *
     * @param robot your robot (its modules are set up)
     * @param g1    the driver's gamepad (the operator's too with {@code oneDriver})
     * @param g2    the operator's gamepad (with {@code oneDriver = true}: gamepad 1, the same as g1)
     */
    protected abstract void buttons(R robot, Pad g1, Pad g2);

    /**
     * Moves or frees the library's own buttons (driving, pause, slow, field-centric, menus, "set pose
     * here", "switch alliance"). Default: nothing changes. Two jobs on one button stop at INIT with the
     * button's name; a job of yours on a library button too.
     *
     * <pre>{@code
     * @Override protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
     *     lib.fieldCentric(g1.x);              // Y is free for buttons(...) now
     *     lib.setPose(g1.dpadDown);            // hold D-pad down 1 s: set pose here
     *     lib.switchAlliance(g1.start, g1.back);   // turn on the alliance switch (hold START, then BACK, 1.5 s)
     * }
     * }</pre>
     *
     * @param lib the library's buttons, at their defaults (one driver: the one-driver ones)
     * @param g1  gamepad 1's buttons, to name them
     * @param g2  gamepad 2's (with {@code oneDriver = true}: gamepad 1, the same as g1)
     */
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) { }

    /** False to switch the modules' suggested buttons off (only Controls.java's jobs). Default true. */
    protected boolean useStandardButtons() {
        return true;
    }

    /** The robot, made from {@code ControlMap<Robot>}'s type. */
    @SuppressWarnings("unchecked")
    protected final R robot() {
        if (robot == null) robot = (R) RobotDefinition.newFromTypeOf(getClass(), ControlMap.class);
        return robot;
    }

    /** True if this class has {@code public static boolean oneDriver = true}. */
    protected final boolean oneDriver() {
        if (oneDriver == null) oneDriver = readOneDriver(getClass());
        return oneDriver;
    }

    // ----------------------------- EasyTeleOp's hooks -----------------------------

    @Override
    protected final RobotConfig robotConfig() {
        return robot().robotConfig();
    }

    @Override
    protected final double slowSpeed() {
        return robot().slowSpeed();
    }

    @Override
    protected final Btn fieldCentricButton() {
        return oneDriver() ? Btn.R_STICK_BTN : Btn.Y;
    }

    @Override
    protected final Subsystems createSubsystems() {
        robot().setAlliance(alliance());
        robot().setup(hardwareMap);
        // With odometry: start where autonomous ended (its whole pose), if it ran in this match.
        if (robot().odometry() != null && startsFromAuto()) robot().odometry().setPose(com.crossbotics.ftchelper.util.PoseStore.peek());
        return robot().all();
    }

    // True if autonomous left a pose from this match.
    private static boolean startsFromAuto() {
        return com.crossbotics.ftchelper.util.PoseStore.peek() != null
                && com.crossbotics.ftchelper.util.PoseStore.ageSec() <= com.crossbotics.ftchelper.match.MatchMemory.MAX_AGE_SEC;
    }

    // With odometry, field-centric "forward" comes from the odometry's heading (it does not drift like
    // a re-zeroed IMU, and carries on from autonomous). BACK (held, field-centric) makes the way the
    // robot faces now 0, keeping x and y.
    @Override
    protected final com.crossbotics.ftchelper.drive.DriverHeading createHeading(com.qualcomm.robotcore.hardware.IMU imu) {
        if (robot().odometrySpec() == null) return super.createHeading(imu);
        return com.crossbotics.ftchelper.drive.DriverHeading.of(
                () -> robot().odometry() == null ? 0 : robot().odometry().pose().headingDeg,
                () -> {
                    if (robot().odometry() == null) return;
                    // Only the heading: x and y stay (and a pose that was never set stays "not set").
                    robot().odometry().setHeading(0);
                },
                startsFromAuto());
    }

    // The library's buttons: the one-driver defaults with oneDriver, then the team's moves.
    @Override
    protected final LibraryButtons standardLibraryButtons() {
        return oneDriver() ? LibraryButtons.oneDriver() : LibraryButtons.twoDrivers();
    }

    @Override
    protected final void libraryButtons(LibraryButtons lib) {
        // Pads only to name the buttons (g1.x...); the jobs go on the real map in bindButtons.
        ButtonPlan names = new ButtonPlan(oneDriver());
        libraryButtons(lib, names.g1, oneDriver() ? names.g1 : names.g2);
        // One driver: there is no gamepad 2, so a library job put there goes on gamepad 1.
        if (oneDriver()) lib.moveAllToGamepad1();
    }

    // "Set pose here" (robots with odometry): the pose becomes Tunables.Odometry's reset spot. Not while
    // the robot drives or aims by itself: the pose would be set at a spot the robot is not on.
    @Override
    protected final java.util.function.Supplier<String> setPoseJob() {
        if (robot().odometrySpec() == null) return null;
        return () -> {
            if (robot().odometry() == null) return "No odometry running";
            if (moving()) return "Pose NOT set: stop first (let go of the drive sticks, aim off), then hold the button again";
            // The alliance may have been chosen in INIT a moment ago: use the one chosen now.
            robot().setAlliance(alliance());
            if (!robot().resetPose()) return "No reset spot: add resetX, resetY, resetHeading to Tunables.Odometry";
            return "Pose set: " + robot().resetSpotWords();
        };
    }

    // No odometry: holding the "set pose here" button says why nothing happens (instead of nothing).
    @Override
    protected final String setPoseMissingNote() {
        if (robot().odometrySpec() != null) return null;
        return "Set pose here needs odometry: this robot has no odometry(...) line in Robot.java (lesson 7)";
    }

    // The drive sticks are pushed (past a small dead zone), or whole-robot aim is on (it turns the robot).
    private boolean moving() {
        if (gamepad1 != null) {
            boolean swapped = libraryKeys() != null && libraryKeys().sticksSwapped();
            double x = swapped ? gamepad1.right_stick_x : gamepad1.left_stick_x;
            double y = swapped ? gamepad1.right_stick_y : gamepad1.left_stick_y;
            double turn = swapped ? gamepad1.left_stick_x : gamepad1.right_stick_x;
            if (Math.abs(x) > STILL || Math.abs(y) > STILL || Math.abs(turn) > STILL) return true;
        }
        return robot().aim() != null && robot().aim().modeOn();
    }

    // How far a drive stick may be off centre and still count as "let go" (0 to 1).
    private static final double STILL = 0.1;

    // The alliance was switched in TeleOp (Camera.goal and the aim read robot.alliance(): they follow).
    @Override
    protected final void allianceChanged(com.crossbotics.ftchelper.match.Alliance now) {
        robot().setAlliance(now);
        Key pose = libraryKeys() == null ? null : libraryKeys().key(LibraryButtons.Job.SET_POSE);
        String note = poseAfterSwitch(robot(), now, pose);
        if (note != null) showNote(note);
    }

    /**
     * The odometry's pose after the alliance was switched in TeleOp (autonomous ran as the wrong one,
     * so the pose is in the wrong alliance's numbers). A mirrored field (the reset spot's mirrorForRed
     * true): the pose is mirrored in place (x and heading flip), like the start spot would have been. A
     * turned field (mirrorForRed false, BIOBUZZ): the pose cannot be fixed, so it counts as "not set"
     * and aiming by odometry stays off until "set pose here". Returns the note for the Driver Station,
     * or null when there is no pose to fix. Package-private and static for tests.
     */
    static String poseAfterSwitch(RobotDefinition robot, com.crossbotics.ftchelper.match.Alliance now, Key poseKey) {
        com.crossbotics.ftchelper.odometry.PoseTracker o = robot.odometry();
        if (o == null || !o.isPoseSet()) return null;
        if (robot.resetMirrorsForRed()) {
            com.crossbotics.ftchelper.util.FieldPose p = o.pose();
            o.setPose(new com.crossbotics.ftchelper.util.FieldPose(-p.x + 0.0, p.y, -p.headingDeg + 0.0));
            return "Alliance now " + now + ": pose mirrored to " + now + "'s side (mirrorForRed = true)";
        }
        o.unsetPose();
        robot.poseUnsetBy = now;
        String how = poseKey == null ? "robot.resetPose()" : "park on the reset spot, hold " + poseKey + " 1 s";
        return "Alliance now " + now + ": odometry aim OFF until the pose is set (" + how + ")";
    }

    @Override
    protected final void bindButtons(ControllerBindings driver, ControllerBindings operator) {
        Set<LibraryButtons.Job> jobs = libraryJobs(true);
        plan = makePlan(robot(), oneDriver(), useStandardButtons(), libraryKeys(), jobs, (g1, g2) -> buttons(robot(), g1, g2));
        plan.compile(robot(), driver, operator, sticks, () -> gamepad1, () -> gamepad2);
        // Whole-robot aim (robot.aimMode()): while it is on, the robot's turn comes from the aim
        // unless the driver moves the turn stick.
        driving().turnAssist(robot().aim()::assistTurn);
        // Reverse drive (robot.reverseDriveMode()): the back is the front while it is on.
        driving().reverseWhen(robot().reverseDriveMode()::isOn);
        // The "pose was never set" warning says how to set it with the button.
        Key pose = jobs.contains(LibraryButtons.Job.SET_POSE) ? libraryKeys().key(LibraryButtons.Job.SET_POSE) : null;
        robot().resetPoseHint = pose == null ? null : "park on the reset spot and hold " + pose + " 1 s";
    }

    // GUIDE (a tap) shows the button map during the match.
    @Override
    protected final List<String> buttonHelp() {
        return plan == null ? null : plan.help(false);   // no free buttons here
    }

    // BACK: pause everything at the press (motors on brake), a tap again: go on. Hold BACK: also reset
    // "forward" (the robot stays paused).
    @Override
    protected final Runnable backTap() {
        return () -> robot().togglePause();
    }

    // So the pause acts at the press, not when BACK is let go (LibraryKeys.pauseAtPress).
    @Override
    protected final java.util.function.BooleanSupplier pauseState() {
        return () -> robot().isPaused();
    }

    @Override
    protected final void drive(Gamepad gamepad) {
        if (robot().isPaused()) {
            driving().hold();      // paused: the wheels hold still too
            return;
        }
        // The slowest of the library's slow buttons and a button given slow() in buttons(...).
        driving().update(gamepad, Math.min(driveSpeed(), teamSpeed(plan, slowSpeed(), gamepad1, gamepad2)));
    }

    // A button given slow() (slow while held), and a trigger given slow() (the further it is pulled,
    // the slower), on whichever gamepad it is (g2.rt.slow() reads gamepad 2). 1 = full speed.
    static double teamSpeed(ButtonPlan plan, double slowSpeed, Gamepad g1, Gamepad g2) {
        double slowest = Math.max(0, Math.min(1, slowSpeed));
        double speed = plan != null && plan.slowHeld ? slowest : 1;
        if (plan != null && plan.slowTrigger != null) {
            speed = Math.min(speed, StickShaping.slowScale(plan.slowTrigger.pull(g1, g2), slowest));
        }
        return speed;
    }

    @Override
    protected final void onStart() {
        robot().setAlliance(alliance());   // it may have been chosen in INIT
        robot().started();
    }

    @Override
    protected final void addTelemetry(RobotTelemetry t) {
        if (robot().isPaused()) t.warn("PAUSED: everything stopped on brake. " + keyWords(LibraryButtons.Job.PAUSE) + " to go on.");
        if (plan != null && !plan.slowWarning().isEmpty()) t.warn(plan.slowWarning());
        if (robot().aim() != null) robot().aim().addTelemetry(t);
        if (robot().reverseDriveMode().isOn()) {
            t.line("Drive", driving().isFieldCentric() ? "REVERSED is on, but field-centric ignores it" : "REVERSED: the back is the front");
        }
        for (Shown s : shown) {
            Object v;
            try {
                v = s.value.get();
            } catch (RuntimeException e) {
                v = "error: " + e.getMessage();
            }
            t.line(s.label, v);
        }
    }

    // ----------------------------- your own lines on the Driver Station -----------------------------

    private final List<Shown> shown = new ArrayList<>();

    private static final class Shown {
        final String label;
        final java.util.function.Supplier<?> value;

        Shown(String label, java.util.function.Supplier<?> value) {
            this.label = label;
            this.value = value;
        }
    }

    /**
     * Shows a value of your own on the Driver Station during the match, read again every loop. Call
     * it in {@code buttons(...)}, e.g. {@code show("Lift ticks", robot.lift::position);} or
     * {@code show("Ready", () -> robot.launcher.isReady());}.
     *
     * @param label the words before the value
     * @param value how to read it (a method reference or a small lambda; it must not wait)
     */
    protected final void show(String label, java.util.function.Supplier<?> value) {
        if (label == null || value == null) throw new IllegalArgumentException("show(label, value) in Controls.java: the label or the value is missing (null), e.g. show(\"Lift\", r.lift::getPosition)");
        shown.add(new Shown(label, value));
    }

    @Override
    protected final void everyLoop() {
        boolean menu = isTuningMenuOpen();
        // Gestures (tap, hold, double-tap...) and "buzz when" watches: every loop.
        if (plan != null) plan.loop(robot().isPaused(), menu);
        // Paused (BACK): the sticks move nothing; each job got 0 once when the pause began.
        if (robot().isPaused()) {
            if (!pausedSticks) for (ButtonPlan.StickJob job : sticks) job.letGo();
            pausedSticks = true;
            return;
        }
        pausedSticks = false;
        for (ButtonPlan.StickJob job : sticks) {
            // The tuning menu uses gamepad 1: its sticks are not the robot's then. The moment it opens,
            // each gamepad 1 stick job gets 0 once ("let go"), so nothing keeps moving from before.
            if (job.isDriverPad() && menu) {
                if (!menuWasOpen) job.letGo();
                continue;
            }
            job.run();
        }
        menuWasOpen = menu;
    }

    // The end: the team's atStop code (robot.atStop), after everything stopped.
    @Override
    protected final void atEnd() {
        if (robot != null) robot.ended();
    }

    @Override
    protected final void addInitTelemetry(RobotTelemetry t) {
        // The modules' INIT work first (a camera's lighting test), with their lines.
        robot().initLoop(t);
        // The alliance may be chosen in INIT: show how the aim's goal point is used for it (mirrorForRed),
        // and a loud warning if the odometry backup cannot work (no pose from autonomous).
        if (robot().aim() != null) {
            robot().setAlliance(alliance());
            robot().aim().addInitTelemetry(t);
        }
        // With odometry: where the pose comes from, and how to set it here.
        if (robot().odometry() != null && plan != null && libraryKeys() != null) {
            Key pose = libraryKeys().key(LibraryButtons.Job.SET_POSE);
            String spot = robot().resetSpotWords();
            if (pose != null) {
                t.line("Set pose", spot == null ? "no reset spot (Tunables.Odometry resetX, resetY, resetHeading)"
                        : "hold " + pose + " 1 s at " + spot + ", now or in the match");
            }
            // Which mirroring the reset spot uses for RED (wrong for the field = the pose on the wrong side).
            t.line("Reset spot for RED", robot().resetMirrorWords());
        }
        // No slow at all (the library's freed and none of the team's): the drivers should know.
        String noSlow = noSlowWarning(libraryKeys(), plan);
        if (noSlow != null) t.warn(noSlow);
        robot().showNotes(t);   // a part that is off: a "!!" warning in COMPETITION
        if (plan != null) for (String n : plan.notes()) t.text(n);
        List<String> changed = RobotDefinition.changedTunables();
        if (!changed.isEmpty()) {
            t.section("Tuned on this robot (copy into Tunables.java)");
            for (String c : changed) t.text(c);
        }
        if (plan != null) {
            t.section(oneDriver() ? "Buttons (one driver)" : "Buttons");
            for (String h : plan.help()) t.text(h);
        }
    }

    /**
     * The INIT warning when nothing makes the robot drive slow: the library's slow (and slow on/off)
     * freed and no {@code slow()} of the team's. Null when there is one. Package-private for tests.
     */
    static String noSlowWarning(LibraryButtons lib, ButtonPlan plan) {
        if (lib == null || plan == null || plan.teamSlow) return null;
        if (lib.key(LibraryButtons.Job.SLOW) != null || lib.key(LibraryButtons.Job.SLOW_TOGGLE) != null) return null;
        return "No slow button (libraryButtons freed it): add one, e.g. g1.rt.slow() in buttons(...)";
    }

    // ----------------------------- the map -----------------------------

    /** The team's part of the map: {@code buttons(robot, g1, g2)}. */
    interface TeamButtons {
        void add(Pad g1, Pad g2);
    }

    /**
     * The whole map with the library's default buttons (tests): see the other makePlan.
     */
    static ButtonPlan makePlan(RobotDefinition robot, boolean oneDriver, boolean standard, TeamButtons team) {
        LibraryButtons lib = oneDriver ? LibraryButtons.oneDriver() : LibraryButtons.twoDrivers();
        return makePlan(robot, oneDriver, standard, lib, defaultJobs(robot), team);
    }

    // The library's jobs a robot has, without an OpMode (tests): field-centric on a mecanum with an IMU
    // or odometry, "set pose" with odometry.
    static Set<LibraryButtons.Job> defaultJobs(RobotDefinition robot) {
        Set<LibraryButtons.Job> jobs = java.util.EnumSet.allOf(LibraryButtons.Job.class);
        boolean heading = robot.drive().imuName != null || robot.odometrySpec() != null;
        if (!(robot.drive().isMecanum() && heading)) jobs.remove(LibraryButtons.Job.FIELD_CENTRIC);
        if (!heading) jobs.remove(LibraryButtons.Job.RESET_FORWARD);
        if (robot.odometrySpec() == null) jobs.remove(LibraryButtons.Job.SET_POSE);
        return jobs;
    }

    /**
     * The whole map: the library's buttons ({@code lib}, only the jobs in {@code jobs}), each module's
     * suggestions, then the team's. Package-private and static so tests can check it without an OpMode.
     */
    static ButtonPlan makePlan(RobotDefinition robot, boolean oneDriver, boolean standard, LibraryButtons lib,
                               Set<LibraryButtons.Job> jobs, TeamButtons team) {
        ButtonPlan plan = new ButtonPlan(oneDriver);
        plan.robot = robot;
        Pad g1 = plan.g1;
        // The driving sticks (swapSticks: the right stick drives, the left stick X turns).
        boolean mecanum = robot.drive().isMecanum();
        if (lib.sticksSwapped()) {
            plan.reserve(g1.rightStickX, mecanum ? "strafe" : "(drive)");
            plan.reserve(g1.rightStickY, "drive");
            plan.reserve(g1.leftStickX, "turn");
        } else {
            plan.reserve(g1.leftStickX, mecanum ? "strafe" : "(drive)");
            plan.reserve(g1.leftStickY, "drive");
            plan.reserve(g1.rightStickX, "turn");
        }
        // Every other library button, with its words (checked: two library jobs on one button stop here).
        for (Map.Entry<Key, String> w : lib.words(jobs).entrySet()) plan.reserve(plan.button(w.getKey()), w.getValue());
        plan.librarySlow = jobs.contains(LibraryButtons.Job.SLOW) ? lib.key(LibraryButtons.Job.SLOW) : null;
        if (standard) robot.suggestButtons(plan);
        plan.source = null;
        // One driver: the team's g2 lines go on gamepad 1 too, like the parts' buttons.
        team.add(plan.g1, oneDriver ? plan.g1 : plan.g2);
        return plan;
    }

    // public static boolean oneDriver in the team's class (or a class above it); false if none.
    static boolean readOneDriver(Class<?> c) {
        for (Class<?> k = c; k != null && k != ControlMap.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField("oneDriver");
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) {
                    throw new IllegalStateException(k.getSimpleName() + ": write oneDriver as "
                            + "\"public static boolean oneDriver = ...;\" (static, true or false)");
                }
                f.setAccessible(true);
                return f.getBoolean(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // not in this class: look in the one above
            }
        }
        return false;
    }
}

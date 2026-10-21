package com.crossbotics.ftchelper.robot;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.crossbotics.ftchelper.core.RobotConfig;
import com.crossbotics.ftchelper.core.Subsystems;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.StickShaping;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.teleop.EasyTeleOp;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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
 * <p><b>Driving</b> (gamepad 1, always): left stick drives (and strafes on mecanum), right stick X
 * turns, LT slow (the further it is pulled, the slower), Y field-centric on/off (mecanum), BACK pauses everything (motors on brake; again
 * to go on), holding BACK 1 s resets "forward", START + BACK debug
 * lines, GUIDE shows the button map (a tap) or opens the tuning menu (held 1 s, PRACTICE profile).
 * With {@code oneDriver = true}: left stick click
 * slow on/off, right stick click field-centric, and the operator's buttons move to gamepad 1.
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
    private boolean slowOn = false;
    private boolean pausedSticks = false;
    private boolean menuWasOpen = false;

    /**
     * Your buttons, after the modules' standard ones. A job here replaces a module's suggestion on
     * the same button.
     *
     * @param robot your robot (its modules are set up)
     * @param g1    the driver's gamepad (the operator's too with {@code oneDriver})
     * @param g2    the operator's gamepad (with {@code oneDriver = true}: gamepad 1, the same as g1)
     */
    protected abstract void buttons(R robot, Pad g1, Pad g2);

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
        return robot().all();
    }

    @Override
    protected final void bindButtons(ControllerBindings driver, ControllerBindings operator) {
        plan = makePlan(robot(), oneDriver(), useStandardButtons(), (g1, g2) -> buttons(robot(), g1, g2));
        plan.compile(robot(), driver, operator, sticks, () -> gamepad1, () -> gamepad2);
        if (oneDriver()) driver.bindPress(ControllerBindings.Pad.G1, Btn.L_STICK_BTN, () -> slowOn = !slowOn);
    }

    // GUIDE (a tap) shows the button map during the match.
    @Override
    protected final List<String> buttonHelp() {
        return plan == null ? null : plan.help(false);   // no free buttons here
    }

    // BACK (a tap): pause everything (motors on brake), a tap again: go on. Hold BACK: reset "forward".
    @Override
    protected final Runnable backTap() {
        return () -> robot().togglePause();
    }

    @Override
    protected final void drive(Gamepad gamepad) {
        if (robot().isPaused()) {
            driving().hold();      // paused: the wheels hold still too
            return;
        }
        if (oneDriver()) driving().update(gamepad, oneDriverSpeed(gamepad));
        else super.drive(gamepad);
    }

    // One driver: the slow speed when slow is on (left stick click, or a button given slow()), and a
    // trigger given slow() slows it the further it is pulled.
    private double oneDriverSpeed(Gamepad gamepad) {
        double slowest = slowSpeed();
        double speed = slowOn || (plan != null && plan.slowHeld) ? slowest : 1;
        if (plan != null && plan.slowTrigger != null) {
            double pull = plan.slowTrigger == ControllerBindings.Trigger.LT ? gamepad.left_trigger : gamepad.right_trigger;
            speed = Math.min(speed, StickShaping.slowScale(pull, slowest));
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
        if (robot().isPaused()) t.warn("PAUSED: everything stopped on brake. BACK to go on.");
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

    @Override
    protected final void addInitTelemetry(RobotTelemetry t) {
        for (String n : robot().notes()) t.text(n);
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

    // ----------------------------- the map -----------------------------

    /** The team's part of the map: {@code buttons(robot, g1, g2)}. */
    interface TeamButtons {
        void add(Pad g1, Pad g2);
    }

    /**
     * The whole map: driving (the library's), each module's suggestions, then the team's.
     * Package-private and static so tests can check it without an OpMode.
     */
    static ButtonPlan makePlan(RobotDefinition robot, boolean oneDriver, boolean standard, TeamButtons team) {
        ButtonPlan plan = new ButtonPlan(oneDriver);
        plan.robot = robot;
        Pad g1 = plan.g1;
        boolean fieldCentric = robot.drive().isMecanum() && robot.drive().imuName != null;
        plan.reserve(g1.leftStickX, robot.drive().isMecanum() ? "strafe" : "(drive)");
        plan.reserve(g1.leftStickY, "drive");
        plan.reserve(g1.rightStickX, "turn");
        if (oneDriver) {
            plan.reserve(g1.leftStickButton, "slow on/off");
            if (fieldCentric) plan.reserve(g1.rightStickButton, "field-centric on/off");
        } else {
            plan.reserve(g1.lt, "slow (squeeze: the further, the slower)");
            if (fieldCentric) plan.reserve(g1.y, "field-centric on/off");
        }
        plan.reserve(g1.back, "pause / go on (hold: forward = away from you now)");
        plan.reserve(g1.start, "debug lines (with BACK)");
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

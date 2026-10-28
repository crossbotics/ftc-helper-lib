package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;

/**
 * The shared frame of every finder: INIT, a loop that never waits, the "copy into your code" lines,
 * saving them to the results file, and stopping everything at the end (even after an error).
 * Each finder fills in its own steps. You do not use this class directly: use a finder such as
 * {@link ServoFinderOpMode}.
 *
 * Docs: docs/modules/finders.md
 */
public abstract class FinderOpMode extends LinearOpMode {

    /** The Driver Station screen. */
    protected RobotTelemetry t;
    /** What the finder found, as code lines. */
    protected FinderResults results;

    /** The finder's name, e.g. "Servo finder". */
    protected abstract String finderName();

    /** Sets up the hardware, in INIT. Nothing may move here. */
    protected abstract void setup();

    /** Binds gamepad 1 buttons. Actions must be quick: never wait. */
    protected abstract void bind(ControllerBindings controls);

    /** Runs once every loop after the buttons: moves things, measures. Never waits. */
    protected abstract void step();

    /** The finder's own screen lines, every loop. */
    protected abstract void show(RobotTelemetry t);

    /** Stops every motor and servo it moves. Called at the end, always. */
    protected abstract void stopAll();

    /**
     * The help page: every button and the steps, a line each (at most 13 lines, about 50 characters
     * each, to fit on the Driver Station). GUIDE (or START held 1 s) shows it, in INIT or while running.
     * Default: a page that says there is no help yet.
     */
    /**
     * True for a finder whose numbers depend on the battery (a power, a gain, a speed): it shows the
     * battery, warns below {@link com.crossbotics.ftchelper.tuning.TuningMenu#LOW_BATTERY_VOLTS}, and
     * says the volts next to its results. Default false (positions and distances do not depend on it).
     */
    protected boolean batteryMatters() {
        return false;
    }

    protected String[] helpLines() {
        return new String[] {"No help page for this finder yet. See its docs."};
    }

    /**
     * Stops what moves, while the help page is open (the finder's buttons pause then). Default:
     * {@link #stopAll()}. A finder overrides it when stopAll() also closes something (a camera)
     * that must keep working.
     */
    protected void pause() {
        stopAll();
    }

    /** What INIT shows (the controls, a safety note). */
    protected void showInit(RobotTelemetry t) {
        t.text(finderName() + ": press START.");
    }

    // The help page, or the normal screen. Toggled by GUIDE.
    private boolean helpOpen = false;
    // GUIDE (the center button) opens and closes the help page, in INIT too.
    private final ControllerBindings helpKey =
            new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, () -> helpOpen = !helpOpen);
    // START held this long also opens/closes help, for controllers whose GUIDE button never
    // reaches the app (some controllers have none; the Driver Hub may keep it for itself).
    private static final double START_HOLD_SEC = 1.0;
    private double startHeldSince = Double.NaN;
    private boolean startHoldUsed = false;
    // True after START and after help closes, until gamepad 1 is let go (see inputsReady()).
    private boolean waitingForNeutral = true;

    /**
     * False after START and after the help page closes, until every stick, trigger and button on
     * gamepad 1 has been let go once. A finder checks it before acting on a stick, trigger or held
     * button that it reads directly, so nothing starts moving because of a finger that was already
     * down (the buttons bound in bind() are protected anyway).
     */
    protected boolean inputsReady() {
        return !waitingForNeutral;
    }

    /** Opens the help page (e.g. when GUIDE stopped a test). Everything pauses as usual. */
    protected void openHelp() {
        helpOpen = true;
        // A GUIDE press (or START hold) still down must not close it again at once.
        helpKey.ignoreHeldButtons(gamepad1, gamepad2);
        if (gamepad1.start) startHoldUsed = true;
    }

    /** True while a help key is down (GUIDE, or START): used to stop a moving test at once. */
    protected boolean helpKeyDown() {
        return gamepad1.guide || gamepad1.start;
    }

    // GUIDE press, or START held START_HOLD_SEC: open or close help.
    private void updateHelpKeys() {
        helpKey.update(gamepad1, gamepad2);
        if (!gamepad1.start) {
            startHeldSince = Double.NaN;
            startHoldUsed = false;
            return;
        }
        double now = System.nanoTime() / 1e9;
        if (Double.isNaN(startHeldSince)) startHeldSince = now;
        if (!startHoldUsed && now - startHeldSince >= START_HOLD_SEC) {
            helpOpen = !helpOpen;
            startHoldUsed = true;
        }
    }

    // True when nothing on gamepad 1 is pressed or pushed.
    private static boolean neutral(com.qualcomm.robotcore.hardware.Gamepad g) {
        double dead = 0.1;
        return Math.abs(g.left_stick_x) < dead && Math.abs(g.left_stick_y) < dead
                && Math.abs(g.right_stick_x) < dead && Math.abs(g.right_stick_y) < dead
                && g.left_trigger < dead && g.right_trigger < dead
                && !g.a && !g.b && !g.x && !g.y && !g.left_bumper && !g.right_bumper
                && !g.dpad_up && !g.dpad_down && !g.dpad_left && !g.dpad_right;
    }

    // "Battery 12.7 V", a warning when it is low, and (with results) that they are for this battery.
    private static void showBattery(RobotTelemetry t, double volts, boolean withResults) {
        if (Double.isNaN(volts)) return;
        double low = com.crossbotics.ftchelper.tuning.TuningMenu.LOW_BATTERY_VOLTS;
        if (volts < low) {
            t.warn(String.format(java.util.Locale.US, "Battery %.1f V: low. Charge it first (%.1f V or more): "
                    + "numbers found now are too strong on a charged battery", volts, low));
        }
        t.line("Battery", String.format(java.util.Locale.US, "%.1f V%s", volts,
                withResults ? " (the numbers below are for about this battery)" : ""));
    }

    private void showHelp(RobotTelemetry t) {
        t.section(finderName() + " help  (GUIDE = back)");
        for (String line : helpLines()) t.text(line);
    }

    // Set while "Tune and check" runs this finder inside itself: INIT, START and STOP are then the host's.
    private Hosting hosting;

    /** Runs this finder inside "Tune and check" ({@link TuneAndCheckOpMode}). You do not call it yourself. */
    public final void runInside(Hosting hosting) {
        this.hosting = hosting;
        hosting.lend(this);
        runOpMode();
    }

    /** opModeInInit(), or the host's when this finder runs inside "Tune and check". */
    protected final boolean inInit() {
        return hosting == null ? opModeInInit() : hosting.inInit(this);
    }

    /** opModeIsActive(), or the host's when this finder runs inside "Tune and check". Use it, not opModeIsActive(). */
    protected final boolean running() {
        return hosting == null ? opModeIsActive() : hosting.running(this);
    }

    private boolean stopRequested() {
        return hosting == null ? isStopRequested() : hosting.stopRequested();
    }

    /**
     * The whole finder. The FTC SDK calls this when you press INIT. {@code final}: a finder cannot
     * skip the safety steps here.
     */
    @Override
    public final void runOpMode() {
        t = new RobotTelemetry(telemetry);
        results = new FinderResults(finderName());
        BulkReads.auto(hardwareMap);
        // The battery, read off the loop (never waits). Every finder notes it at START in the results file.
        java.util.function.DoubleSupplier battery = com.crossbotics.ftchelper.hardware.Hw.batteryReader(hardwareMap);
        ControllerBindings controls = new ControllerBindings();
        Throwable failure = null;
        try {
            setup();
            bind(controls);
            while (inInit()) {
                updateHelpKeys();
                if (helpOpen) {
                    showHelp(t);
                    t.text("Press START on the Driver Station to begin.");
                } else {
                    if (batteryMatters()) showBattery(t, battery.getAsDouble(), false);
                    showInit(t);
                    t.text("GUIDE (center button), or hold START 1 s = help.");
                }
                t.update();
            }
            if (stopRequested()) return;
            // A button held through START must not count as a press.
            controls.ignoreHeldButtons(gamepad1, gamepad2);
            boolean helpWasOpen = false;
            boolean voltsNoted = false;
            while (running()) {
                // GUIDE, or START held 1 s: the help page (and everything stops), while running too.
                updateHelpKeys();
                if (helpOpen) {
                    // Help open: the finder's buttons pause and what moves stops (so nothing moves
                    // while the driver reads). "Release" actions of held buttons run first.
                    if (!helpWasOpen) {
                        controls.releaseAll();
                        pause();
                    }
                    helpWasOpen = true;
                    showHelp(t);
                    t.text("Help open: buttons paused, motors stopped.");
                    t.update();
                    continue;
                }
                // Just closed: a button still held from the help page must not act, and sticks and
                // triggers must be let go before they count again.
                if (helpWasOpen) {
                    controls.ignoreHeldButtons(gamepad1, gamepad2);
                    waitingForNeutral = true;
                }
                helpWasOpen = false;
                if (waitingForNeutral && neutral(gamepad1)) waitingForNeutral = false;
                controls.update(gamepad1, gamepad2);
                // The battery at START (its first reading): rested, before the tests pull it down.
                if (!voltsNoted && !Double.isNaN(battery.getAsDouble())) {
                    results.battery(battery.getAsDouble());
                    voltsNoted = true;
                }
                step();
                if (batteryMatters()) showBattery(t, battery.getAsDouble(), !results.isEmpty());
                if (waitingForNeutral) t.warn("Let go of every stick, trigger and button on gamepad 1 to go on.");
                if (!controls.getSlowActionWarning().isEmpty()) t.warn(controls.getSlowActionWarning());
                show(t);
                t.text("GUIDE (or hold START) = help");
                results.addTelemetry(t);
                if (!results.isEmpty()) t.text("(saved to the hub when you press STOP)");
                t.update();
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            RuntimeException first = null;
            try { stopAll(); } catch (RuntimeException e) { first = e; }
            // Save what was found. The finder is ending, so waiting a few milliseconds for the file is
            // fine here (never in the loop). The result goes to the screen and the robot log.
            if (results != null && !results.isEmpty()) {
                boolean ok = results.saveNow();
                String msg = ok ? "Results saved to " + FinderResults.file.getPath()
                        : "Could not save the results file: copy the lines from the screen.";
                RobotLog.ii("ftchelper", msg);
                telemetry.addLine(msg);
                telemetry.update();
            }
            if (first != null) {
                if (failure == null) throw first;
                RobotLog.ee("ftchelper", first, "stopping failed after an earlier error");
            }
        }
    }
}

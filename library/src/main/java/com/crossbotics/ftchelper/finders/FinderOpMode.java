package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
import com.crossbotics.ftchelper.hardware.BulkReads;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.io.File;

/**
 * The shared frame of every finder: INIT (what moves, how to stop), a loop that never waits, the lines
 * to copy, saving them to the results file, and stopping everything at the end (even after an error).
 * Every screen has the same order: safety, where you are, the one next thing, the numbers, help.
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
     * True for a finder whose numbers depend on the battery (a power, a gain, a speed): it shows the
     * battery, warns below {@link com.crossbotics.ftchelper.tuning.TuningMenu#LOW_BATTERY_VOLTS}, and
     * says the volts next to its results. Default false (positions and distances do not depend on it).
     */
    protected boolean batteryMatters() {
        return false;
    }

    /**
     * The help page: every button and the steps, a line each (at most 13 lines, about 50 characters
     * each, to fit on the Driver Station). GUIDE (or START held 1 s) shows it, in INIT or while running.
     * Default: a page that says there is no help yet.
     */
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

    /**
     * What INIT shows: the safety lines with {@code t.warn} (what moves after START, how to stop it), then
     * what the tool is for and what you need. The frame adds how to start and how to get help.
     */
    protected void showInit(RobotTelemetry t) {
        t.warn("Parts may move. Robot on a block. Hands clear.");
        t.text(finderName());
    }

    /**
     * What is true after everything was told to stop, for the help and STOP screens. A tool with servos or
     * with nothing that moves says its own words.
     */
    protected String stoppedWords() {
        return "Motor power is off. Parts may still roll.";
    }

    /**
     * The sticks, triggers and buttons must be let go before they count again. Call it when what they
     * control changes (a new motor was chosen) and after a test that kept the loop waiting.
     */
    protected final void waitForRelease() {
        waitingForNeutral = true;
        ignoreHeldSoon = true;
    }

    /** True inside "Tune and check" for a robot in five files: the results are then lines for Tunables.java. */
    final boolean hasRobotFiles() {
        return hosting != null && hosting.fiveFiles();
    }

    /** This device's class in Tunables.java: what Robot.java says, else the name in CamelCase. */
    final String sectionOf(String device) {
        String s = hosting == null ? null : hosting.section(device);
        return s != null ? s : com.crossbotics.ftchelper.robot.RobotDefinition.sectionName(device);
    }

    /** True when Robot.java has a part on this device (so its kind is known). */
    final boolean knowsPart(String device) {
        return hosting != null && hosting.section(device) != null;
    }

    /** True when Robot.java says this device's part uses degrees. */
    final boolean usesDegrees(String device) {
        return hosting != null && hosting.inDegrees(device);
    }

    /** Shows a note with "\n" in it as short screen lines (nothing for ""). */
    static void textLines(RobotTelemetry t, String note) {
        if (note.isEmpty()) return;
        for (String line : note.split("\n")) t.text(line);
    }

    /** Shows a warning with "\n" in it as short warning lines (nothing for ""). */
    static void warnLines(RobotTelemetry t, String warning) {
        if (warning.isEmpty()) return;
        for (String line : warning.split("\n")) t.warn(line);
    }

    /**
     * The number written in Tunables.java (not one saved by the tuning menu): {@code section}'s {@code field}.
     *
     * @return the number, or {@code ifNone} when it is not written or says DEFAULT or MEASURE
     */
    static double codeValue(Class<?> tunables, String section, String field, double ifNone) {
        if (tunables == null) return ifNone;
        for (Class<?> c : tunables.getDeclaredClasses()) {
            if (!c.getSimpleName().equals(section)) continue;
            try {
                java.lang.reflect.Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                double v = ((Number) f.get(null)).doubleValue();
                return Double.isNaN(v) ? ifNone : v;   // DEFAULT and MEASURE are written as special NaN numbers
            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException notWritten) {
                return ifNone;
            }
        }
        return ifNone;
    }

    /**
     * Why a launcher cannot fire yet, each in words that cannot be read as a pass ("NOT aimed", never the
     * check's name alone). From {@code Launcher.whyNotReady()}.
     */
    static java.util.List<String> notReadyWords(String whyNotReady) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (whyNotReady.isEmpty()) return out;
        for (String part : whyNotReady.split(", ")) {
            switch (part) {
                case "off":
                case "not spinning": out.add("Flywheel is OFF"); break;
                case "flywheel": out.add("Flywheel NOT at speed"); break;
                case "hood": out.add("Hood NOT in place"); break;
                case "aimed":
                case "aimed at the goal": out.add("NOT aimed at the goal"); break;
                default: out.add("Still waiting for: " + part);
            }
        }
        return out;
    }

    /** False while the lines to copy would only be in the way (a test is moving the robot). */
    boolean showsResults() {
        return true;
    }

    /** Detail that may be off the bottom of the screen: it comes after the lines to copy and the help line. */
    void showDetails(RobotTelemetry t) {
    }

    /** The word written in Tunables.java for {@code section}'s {@code field}, or null. */
    static String codeWord(Class<?> tunables, String section, String field) {
        for (Class<?> c : tunables.getDeclaredClasses()) {
            if (!c.getSimpleName().equals(section)) continue;
            try {
                java.lang.reflect.Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return (String) f.get(null);
            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException notWritten) {
                return null;
            }
        }
        return null;
    }

    /** How a move that waits ended, in plain words (never the Java name). */
    static String howItEnded(com.crossbotics.ftchelper.drive.AutoDrive.Result result) {
        switch (result) {
            case DONE: return "finished";
            case STALLED: return "got stuck";
            case TIMED_OUT: return "took too long";
            case NO_ODOMETRY: return "lost the odometry";
            default: return "was stopped";
        }
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
    // waitForRelease(): the bound buttons held now are ignored, done right after this loop's step().
    private boolean ignoreHeldSoon = false;

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
                && !g.dpad_up && !g.dpad_down && !g.dpad_left && !g.dpad_right
                && !g.back && !g.start && !g.guide && !g.left_stick_button && !g.right_stick_button;
    }

    // "Battery 12.7 V: OK", or a warning and what to do when it is low.
    private static void showBattery(RobotTelemetry t, double volts, boolean okLine) {
        if (Double.isNaN(volts)) return;
        double low = com.crossbotics.ftchelper.tuning.TuningMenu.LOW_BATTERY_VOLTS;
        if (volts < low) {
            t.warn(String.format(java.util.Locale.US, "Battery %.1f V is low (need %.1f V).", volts, low));
            t.warn("Change it, or the numbers will be wrong.");
        } else if (okLine) {
            t.line("Battery", String.format(java.util.Locale.US, "%.1f V: OK", volts));
        }
    }

    // The last screen after STOP: what stopped, then every line that was found (help may have hidden them).
    private void showStopped(boolean hasLines, boolean saved) {
        t.discard();
        t.section(finderName() + ": STOPPED");
        t.text(stoppedWords());
        if (hasLines) {
            results.addTelemetry(t);
            if (saved) {
                t.text("Saved on the Control Hub, in");
                File folder = FinderResults.file.getParentFile();
                t.text("  " + (folder == null ? "" : folder.getName() + "/") + FinderResults.file.getName());
            } else {
                t.text("NOT saved. Write these lines down now.");
            }
        } else if (!results.status().isEmpty()) {
            t.text(results.status());
            t.text("Nothing to copy from this run.");
        }
        t.update();
    }

    private void showHelp(RobotTelemetry t) {
        t.section(finderName() + " help  ({GUIDE} = back)");
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
        // The screens name the buttons printed on these controllers (A or ×): docs/modules/gamepad.md.
        com.crossbotics.ftchelper.gamepad.ButtonNames.watch(gamepad1, gamepad2);
        // A mistake found at INIT: plain lines on the Driver Station (inside "Tune and check", the host shows it).
        if (hosting != null) runBody();
        else com.crossbotics.ftchelper.core.InitStop.run(this, this::runBody);
    }

    private void runBody() {
        t = new RobotTelemetry(telemetry);
        results = new FinderResults(finderName());
        BulkReads.auto(hardwareMap);
        // The battery, read off the loop (never waits). Every finder notes it at START in the results file.
        java.util.function.DoubleSupplier battery = com.crossbotics.ftchelper.hardware.Hw.batteryReader(hardwareMap);
        ControllerBindings controls = new ControllerBindings();
        Throwable failure = null;
        boolean started = false;
        try {
            setup();
            bind(controls);
            while (inInit()) {
                updateHelpKeys();
                if (helpOpen) {
                    showHelp(t);
                    t.text("Press {PLAY} on the Driver Hub to begin.");
                } else {
                    showInit(t);
                    if (batteryMatters()) showBattery(t, battery.getAsDouble(), true);
                    t.text("Press {PLAY} on the Driver Hub to start.");
                    t.text("STOP (■) on the Driver Hub ends it any time.");
                    if (hosting != null) t.text("{BACK} = back to the menu.");
                    t.text("Help: {GUIDE}, or hold {START} 1 s.");
                }
                t.update();
            }
            if (stopRequested()) return;
            started = true;
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
                    t.text("Help is open: the buttons are paused.");
                    t.text(stoppedWords());
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
                if (ignoreHeldSoon) {
                    ignoreHeldSoon = false;
                    controls.ignoreHeldButtons(gamepad1, gamepad2);
                }
                show(t);
                // With lines to copy the screen is full: then the battery shows only when it is low.
                if (batteryMatters()) showBattery(t, battery.getAsDouble(), results.isEmpty());
                // warnFirst: nothing works until this is done, so it is the one next thing.
                if (waitingForNeutral) t.warnFirst("Let go of all sticks and buttons.");
                if (!controls.getSlowActionWarning().isEmpty()) t.warn(controls.getSlowActionWarning());
                if (showsResults()) results.addTelemetry(t);
                t.text("Help: {GUIDE}, or hold {START} 1 s.");
                showDetails(t);
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
            boolean hasLines = results != null && !results.isEmpty();
            boolean ok = hasLines && results.saveNow();
            if (hasLines) {
                RobotLog.ii("ftchelper", ok ? "Results saved to " + FinderResults.file.getPath()
                        : "Could not save the results file");
            }
            // After an error the FTC app shows the error: do not cover it.
            if (failure == null && first == null && started && results != null) showStopped(hasLines, ok);
            if (first != null) {
                if (failure == null) throw first;
                RobotLog.ee("ftchelper", first, "stopping failed after an earlier error");
            }
        }
    }
}

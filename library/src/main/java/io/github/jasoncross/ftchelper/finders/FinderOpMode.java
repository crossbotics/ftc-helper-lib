package io.github.jasoncross.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.hardware.BulkReads;
import io.github.jasoncross.ftchelper.telemetry.RobotTelemetry;

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

    /** What INIT shows (the controls, a safety note). */
    protected void showInit(RobotTelemetry t) {
        t.text(finderName() + ": press START.");
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
        ControllerBindings controls = new ControllerBindings();
        Throwable failure = null;
        try {
            setup();
            bind(controls);
            while (opModeInInit()) {
                showInit(t);
                t.update();
            }
            if (isStopRequested()) return;
            // A button held through START must not count as a press.
            controls.ignoreHeldButtons(gamepad1, gamepad2);
            while (opModeIsActive()) {
                controls.update(gamepad1, gamepad2);
                step();
                if (!controls.getSlowActionWarning().isEmpty()) t.warn(controls.getSlowActionWarning());
                show(t);
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

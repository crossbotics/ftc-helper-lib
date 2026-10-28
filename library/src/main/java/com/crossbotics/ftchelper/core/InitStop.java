package com.crossbotics.ftchelper.core;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.RobotLog;

/**
 * A mistake found at INIT, shown as plain lines on the Driver Station. Without this, the FTC app shows
 * one red line that starts with Java words ("User code threw an uncaught exception: IllegalStateException
 * - ..."), and the real message is easy to miss. With it, the Driver Station shows:
 *
 * <pre>
 * INIT STOPPED: the robot did not start.
 * Tunables.Lift has no max: write max (...)
 * Fix it, then press INIT again. Press STOP now.
 * </pre>
 *
 * and the OpMode waits (nothing moves) until STOP. A mistake after START is not caught: the FTC app
 * shows it as before, after the OpMode has stopped every part. The robot log keeps the full error.
 *
 * Docs: docs/troubleshooting.md, "Driver Station errors"
 */
public final class InitStop {
    /** The OpMode's body, which may stop with an error. */
    public interface Body {
        void run();
    }

    // The simulator shows INIT errors in its own error panel: there, they stop the OpMode as before.
    private static volatile boolean throwInstead = false;

    private InitStop() {
    }

    /**
     * Runs {@code body}. An error before START is shown as plain lines, and this waits for STOP. Any
     * other error goes on as before.
     */
    public static void run(LinearOpMode op, Body body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            if (op.isStarted() || !showAndWait(op, e)) throw e;
        }
    }

    /**
     * Shows {@code e} as plain lines and waits until STOP (BLOCKS: on purpose, nothing else runs). Returns false (shows nothing) in the
     * simulator, where the caller then throws it as before.
     */
    public static boolean showAndWait(LinearOpMode op, RuntimeException e) {
        // The simulator, or a clean stop the SDK asked for (terminateOpModeNow): not a mistake to show.
        if (throwInstead || e.getClass().getName().endsWith("ForceStopException")) return false;
        RobotLog.ee("ftchelper", e, "INIT stopped");
        String why = words(e);
        while (!op.isStopRequested()) {
            op.telemetry.clearAll();
            op.telemetry.addLine("INIT STOPPED: the robot did not start.");
            op.telemetry.addLine(why);
            op.telemetry.addLine("Fix it, then press INIT again. Press STOP now.");
            op.telemetry.update();
            op.idle();
        }
        return true;
    }

    /**
     * The plain words for an error: the library's own messages as they are; anything else (a mistake in
     * your own code) with its kind and where it happened, e.g. "NullPointerException in Robot.java line 42".
     */
    public static String words(Throwable e) {
        String msg = e.getMessage();
        StackTraceElement[] st = e.getStackTrace();
        // The library's messages already say what to change and where.
        boolean ours = msg != null && !msg.isEmpty() && st.length > 0
                && st[0].getClassName().startsWith("com.crossbotics.ftchelper.");
        if (ours) return msg;
        StringBuilder b = new StringBuilder(e.getClass().getSimpleName());
        if (msg != null && !msg.isEmpty()) b.append(": ").append(msg);
        StackTraceElement at = where(e);
        if (at != null && at.getFileName() != null) {
            b.append(" in ").append(at.getFileName()).append(" line ").append(at.getLineNumber());
        }
        return b.toString();
    }

    // The first place in the team's code (TeamCode), else the first place at all.
    private static StackTraceElement where(Throwable e) {
        StackTraceElement[] st = e.getStackTrace();
        for (StackTraceElement s : st) {
            if (s.getClassName().startsWith("org.firstinspires.ftc.teamcode")) return s;
        }
        return st.length > 0 ? st[0] : null;
    }

    /** The simulator only: INIT errors stop the OpMode with the error, as before (its own panel shows it). */
    public static void throwInsteadOfShowing(boolean on) {
        throwInstead = on;
    }
}

package com.qualcomm.robotcore.eventloop.opmode;

import java.lang.reflect.Field;

/**
 * The simulator's way in to an OpMode's start and stop flags. On a robot the FTC app sets them;
 * here the simulator does. This class is in the SDK's package because those flags are package-only.
 * Simulator only: never used on a robot.
 */
public final class SimAccess {
    private SimAccess() { }

    /** START was pressed: opModeIsActive() becomes true and waitForStart() returns. */
    public static void start(OpMode op) {
        op.isStarted = true;
        wake(op);
    }

    /** STOP was pressed: opModeIsActive() becomes false; a waiting waitForStart() returns. */
    public static void stop(OpMode op) {
        op.stopRequested = true;
        wake(op);
    }

    // waitForStart() sleeps on a private lock: wake it up.
    private static void wake(OpMode op) {
        if (!(op instanceof LinearOpMode)) return;
        try {
            Field f = LinearOpMode.class.getDeclaredField("runningNotifier");
            f.setAccessible(true);
            Object lock = f.get(op);
            synchronized (lock) {
                lock.notifyAll();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This FTC SDK version is not supported by the simulator", e);
        }
    }
}

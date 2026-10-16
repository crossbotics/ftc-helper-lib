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

    /** True after START (for running iterative OpModes). */
    public static boolean isStarted(OpMode op) {
        return op.isStarted;
    }

    /** True after STOP (or the OpMode asked to stop). */
    public static boolean isStopRequested(OpMode op) {
        return op.stopRequested;
    }

    /** STOP was pressed: opModeIsActive() becomes false; a waiting waitForStart() returns. */
    public static void stop(OpMode op) {
        op.stopRequested = true;
        wake(op);
    }

    /**
     * What the FTC app does on every pass of its event loop: updates {@code time} and wakes a
     * waitForStart() that is sleeping. Called often, so a START that happens just before the OpMode
     * starts to wait is never lost.
     */
    public static void eventLoop(OpMode op) {
        if (op instanceof LinearOpMode) ((LinearOpMode) op).internalOnEventLoopIteration();
        else op.time = op.getRuntime();
    }

    /**
     * Gives the OpMode the app services it may call: requestOpModeStop() (an OpMode stopping itself)
     * runs {@code onStopRequest}; telemetry refreshes are ignored.
     */
    public static void installServices(OpMode op, final Runnable onStopRequest) {
        op.internalOpModeServices = (org.firstinspires.ftc.robotcore.internal.opmode.OpModeServices)
                java.lang.reflect.Proxy.newProxyInstance(SimAccess.class.getClassLoader(),
                        new Class<?>[] {org.firstinspires.ftc.robotcore.internal.opmode.OpModeServices.class},
                        (proxy, m, a) -> {
                            if (m.getName().equals("requestOpModeStop")) onStopRequest.run();
                            if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (m.getName().equals("equals")) return proxy == a[0];
                            return null;
                        });
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

package com.crossbotics.ftchelper.robot;

import com.crossbotics.ftchelper.launcher.Feeder;
import com.crossbotics.ftchelper.telemetry.RobotTelemetry;
import java.util.function.BooleanSupplier;

/**
 * A feeder that spins (a CR servo or a motor, with no separate feed wheel) and runs backward while the
 * intake pushes pieces out ({@code Launcher.feedWheelOutWith(...)}). WHY: the pieces already in the
 * feeder would stay stuck there; backward, they come out with the others. A feed always wins: the
 * feeder turns backward only when it is not feeding.
 *
 * <p>It sends a power only when it changes (each write takes time on the hub).
 */
final class BackOutFeeder implements Feeder {
    private final Feeder inner;          // the feeder as the launcher sees it (it feeds and stops itself)
    private final Indexer wheel;         // the same feeder's CR servo(s) or motor (runWheel)
    private final BooleanSupplier when;  // true: the intake pushes out and the launcher is not shooting
    private boolean backing = false;     // true while we run it backward

    BackOutFeeder(Feeder inner, Indexer wheel, BooleanSupplier when) {
        this.inner = inner;
        this.wheel = wheel;
        this.when = when;
    }

    @Override
    public void feedOne() {
        // A shot wins: the feed sets its own forward power.
        if (backing) {
            backing = false;
            wheel.runWheel(0);
        }
        inner.feedOne();
    }

    @Override
    public boolean isBusy() {
        return inner.isBusy();
    }

    @Override
    public boolean isJammed() {
        return inner.isJammed();
    }

    @Override
    public void clearJam() {
        inner.clearJam();
    }

    @Override
    public void update() {
        inner.update();
        boolean back = !inner.isBusy() && when.getAsBoolean();
        if (back == backing) return;
        backing = back;
        wheel.runWheel(back ? -wheel.wheelPower() : 0);
    }

    @Override
    public void stop() {
        backing = false;
        inner.stop();
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        if (backing) t.debug(name(), "backward (intake out)");
        else inner.addTelemetry(t);
    }

    /** True while the feeder runs backward (tests). */
    boolean isBacking() {
        return backing;
    }
}

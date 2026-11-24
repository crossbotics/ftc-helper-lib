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
 * <p>It never starts a continuous feed again: after one is stopped it only turns backward, and only while
 * the intake pushes out.
 *
 * <p>It sends a power only when it changes (each write takes time on the hub), and a new power from the
 * tuning menu at once.
 */
final class BackOutFeeder implements Feeder {
    private final Feeder inner;          // the feeder as the launcher sees it (it feeds and stops itself)
    private final Indexer wheel;         // the same feeder's CR servo(s) or motor (runWheel)
    private final BooleanSupplier when;  // true: the intake pushes out and the launcher is not shooting
    private double sent = Double.NaN;    // the backward power we sent last (NaN = we are not backing out)

    BackOutFeeder(Feeder inner, Indexer wheel, BooleanSupplier when) {
        this.inner = inner;
        this.wheel = wheel;
        this.when = when;
    }

    @Override
    public void feedOne() {
        // A shot wins: the feed sets its own forward power, so we only forget ours.
        sent = Double.NaN;
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
        if (inner.isBusy()) {
            // A feed started some other way (the feeder's own feedOne()): it set its own power. Leave it.
            sent = Double.NaN;
            return;
        }
        if (when.getAsBoolean()) {
            double p = -wheel.wheelPower();
            if (p != sent) {          // first time, or a new power from the tuning menu
                sent = p;
                wheel.runWheel(p);
            }
        } else if (!Double.isNaN(sent)) {
            sent = Double.NaN;        // the intake stopped pushing out: stop
            wheel.runWheel(0);
        }
    }

    @Override
    public void stop() {
        sent = Double.NaN;
        inner.stop();
    }

    @Override
    public boolean canFeedContinuously() {
        return inner.canFeedContinuously();
    }

    @Override
    public boolean startContinuous(double power) {
        // The feed sets its own forward power, so we forget ours.
        sent = Double.NaN;
        return inner.startContinuous(power);
    }

    @Override
    public void stopContinuous() {
        sent = Double.NaN;
        inner.stopContinuous();
    }

    @Override
    public boolean isFeedingContinuously() {
        return inner.isFeedingContinuously();
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public void addTelemetry(RobotTelemetry t) {
        inner.addTelemetry(t);   // its own lines (a jam warning, the feed stop) stay
        if (isBacking()) t.debug(name() + " backward", "the intake pushes out");
    }

    /** True while the feeder runs backward (tests). */
    boolean isBacking() {
        return !Double.isNaN(sent);
    }
}

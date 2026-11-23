package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.core.Time;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Output;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Sample;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.qualcomm.robotcore.hardware.DcMotor;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * A pretend weighted flywheel for the Flywheel Tuner's tests: the real {@link Flywheel} code on a
 * {@link FakeMotor}, a motor that speeds up toward the speed its volts hold (one time constant), a wheel that
 * coasts slowly on FLOAT, the hub's own PID (every 10 ms, its reading 20 ms late), and a speed reading that
 * comes in 43 RPM steps, 50 ms late, in loops of 20 to 60 ms. The battery sags under load: it has an inner
 * resistance (more for a used battery), so the motor gets less than the battery's resting volts, and the hub
 * reads the sagged volts. The motor's current comes from its volts and its speed (a goBILDA 6000 RPM motor:
 * 9.2 A stalled at 12 V).
 */
final class FlywheelTunePlant {
    static final double CPR = 28, STEP_RPM = 43, LAG_SEC = 0.05, HUB_LAG_SEC = 0.02, DT = 0.001;

    final FakeMotor m = new FakeMotor();
    final Flywheel flywheel;
    final FlywheelTuneDriver driver;
    final Random random;
    // The wheel: volts = ks + kv x RPM holds a speed; it reaches it with this time constant.
    double ks = 0.2, kv = 0.00216, tau = 2.0;
    double battery = 12.8;
    double rpm = 0, counts = 0, t = 0;
    // The motor's current now, amps; the battery's volts under load now.
    double amps = 0, terminal = 12.8;
    // A wheel that drags (a rubbing part): this many amps more whenever the motor has power.
    double extraAmps = 0;
    // The motor: volts per RPM it makes back when it turns, and its winding resistance (ohms).
    static final double BACK_VOLTS_PER_RPM = 12.0 / 6000, MOTOR_OHMS = 12.0 / 9.2;
    // True = a broken encoder cable: the speed reads 0 and the count stops.
    boolean deadEncoder = false;
    private final ArrayDeque<double[]> past = new ArrayDeque<>();
    private double hubPower = 0, hubNext = 0, hubIntegral = 0;
    private final long[] clock = {0};

    FlywheelTunePlant(long seed, double kvShare, double tauShare, double volts) {
        random = new Random(seed);
        kv *= kvShare;
        tau *= tauShare;
        battery = volts;
        terminal = volts;
        Time.useForTests(() -> clock[0]);
        // As a robot's launcher builds it: R1's numbers (kF 12.08 found at 13.0 V, battery compensation on).
        flywheel = new Flywheel("launcher", m.motor).countsPerRev(CPR).maxRpm(6000).tolerance(100)
                .pidf(10, 0, 0, 12.08).batteryCompensation(() -> terminal, 13.0);
        // The hub reads the battery as it is now: sagged while the motor pulls.
        flywheel.bangBattery(() -> terminal, 13.0);
        flywheel.bangFeedforward(0, Flywheel.CATALOG_VOLTS / 6000);
        m.zeroPower = DcMotor.ZeroPowerBehavior.FLOAT;
        driver = new FlywheelTuneDriver(flywheel);
        past.add(new double[] {0, 0});
    }

    /**
     * The battery's inner resistance with its wires, ohms: a full battery 0.08, more as it runs down (a used one
     * sags more: 0.12 at 10.6 V). Full power from rest on a 12.2 V battery then dips it about 0.8 V.
     */
    double innerOhms() {
        return 0.08 + Math.max(0, 12.6 - battery) * 0.02;
    }

    /** The volts the motor gets now (the battery sags by its inner resistance x the current it gives). */
    private double motorVolts() {
        double p = motorPower();
        // The motor's current I = (p x Vterm - back volts) / R, with Vterm = battery - inner x p x I (the
        // battery gives p x I through the hub's switching): I = (p x battery - back) / (R + inner x p^2).
        double back = BACK_VOLTS_PER_RPM * rpm;
        double i = p == 0 ? 0 : (p * battery - back) / (MOTOR_OHMS + innerOhms() * p * p);
        terminal = battery - innerOhms() * Math.abs(p * i);
        amps = Math.abs(i) + (p != 0 ? extraAmps : 0);
        return p * terminal;
    }

    /** The power the motor has now: the hub's PID, or a plain power. */
    private double motorPower() {
        double p;
        if (m.mode == DcMotor.RunMode.RUN_USING_ENCODER && m.pidf != null) {
            if (t >= hubNext) {
                hubNext = t + 0.01;
                double read = Math.rint(at(t - HUB_LAG_SEC) / STEP_RPM) * STEP_RPM * CPR / 60;
                double err = m.velocityCommand - read;
                hubIntegral += err * 0.01;
                hubPower = Math.max(-1, Math.min(1, (m.pidf[0] * err + m.pidf[1] * hubIntegral + m.pidf[3] * m.velocityCommand) / 32767));
            }
            p = hubPower;
        } else {
            p = m.power;
            hubIntegral = 0;
        }
        return p;
    }

    /** Moves the wheel by sec (1 ms steps). */
    void run(double sec) {
        double end = t + sec;
        while (t < end - 1e-12) {
            double v = motorVolts();
            if (v == 0) {
                rpm -= (rpm / 8 + 60) * DT;         // FLOAT: it coasts slowly
            } else {
                double hold = (v - ks) / kv;
                rpm += (hold - rpm) / tau * DT;
            }
            rpm = Math.max(0, rpm);
            counts += rpm / 60 * CPR * DT;
            t += DT;
            clock[0] = (long) (t * 1e9);
            past.addLast(new double[] {t, rpm});
            while (past.size() > 2 && past.peekFirst()[0] < t - 1) past.removeFirst();
        }
        m.velocity = deadEncoder ? 0 : Math.rint(at(t - LAG_SEC) / STEP_RPM) * STEP_RPM * CPR / 60;
        if (!deadEncoder) m.position = counts;
    }

    // The real speed at an earlier time.
    private double at(double when) {
        double v = 0;
        for (double[] p : past) {
            if (p[0] > when) break;
            v = p[1];
        }
        return v;
    }

    /** One tuner loop: 20 to 60 ms of wheel, a reading, the session's answer done on the flywheel. */
    Output loop(FlywheelTuneSession s) {
        run(0.02 + random.nextDouble() * 0.04);
        double read = m.motor.getVelocity() / CPR * 60;
        Output out = s.update(new Sample(t, read, m.motor.getCurrentPosition(), terminal,
                amps, driver.appliedPower(), flywheel.getFault()));
        driver.apply(out);
        return out;
    }

    void close() {
        Time.useForTests(null);
    }
}

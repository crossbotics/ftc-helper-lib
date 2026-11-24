package com.crossbotics.ftchelper.finders;

import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Output;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import java.util.ArrayList;
import java.util.List;

/**
 * Does what the Flywheel Tuner's session says, on the robot's own {@link Flywheel}: power off, a plain power,
 * or hold a speed with a setting (the launcher's real PID or bang-bang code). The OpMode and the tests use the
 * same one, so the tests run the code the robot runs. It writes only when something changes (each write costs
 * loop time), except "off", which always reaches the motors. A plain power goes to EVERY motor of the wheel
 * (a follower too), so full power and the steady powers measure the wheel the robot really has.
 */
final class FlywheelTuneDriver {
    private final Flywheel flywheel;
    private Output.Kind kind = null;          // what it did last; null = nothing yet
    private FlywheelSetting setting = null;   // the setting on the flywheel now
    private double power = Double.NaN;
    // The motors a plain power goes to (the main motor first), from the last switch to plain power.
    private List<DcMotorEx> motors;

    FlywheelTuneDriver(Flywheel flywheel) {
        this.flywheel = flywheel;
        this.motors = flywheel.getMotors();
    }

    /** One loop: does what the output says. */
    void apply(Output out) {
        switch (out.kind) {
            case POWER:
                if (kind != Output.Kind.POWER) {
                    // The flywheel's own control lets go first (bang-bang writes 0), then plain power.
                    flywheel.stop();
                    motors = flywheel.getMotors();
                    for (DcMotorEx m : motors) {
                        m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                        m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
                    }
                    power = Double.NaN;
                }
                if (out.power != power) {
                    power = Double.NaN;   // until every motor took it
                    for (DcMotorEx m : motors) m.setPower(out.power);
                }
                power = out.power;
                kind = Output.Kind.POWER;
                return;
            case CONTROL:
                if (kind != Output.Kind.CONTROL) {
                    // A change of control needs a stopped flywheel (target 0): it starts from here.
                    flywheel.stop();
                    setting = null;
                }
                if (setting == null || !out.setting.sameAs(setting)) {
                    if (!out.setting.applyTo(flywheel)) {
                        off();
                        throw new IllegalStateException("The flywheel did not take the new speed control.");
                    }
                    setting = out.setting;
                }
                kind = Output.Kind.CONTROL;
                flywheel.setRpm(out.targetRpm);
                flywheel.update();
                return;
            default:
                if (kind != Output.Kind.OFF) off();
        }
    }

    /**
     * Power off, always written: the flywheel's control stops (bang-bang writes 0, PID coasts), and every motor
     * gets plain power 0 as well, in case a plain power was on. Each motor gets its 0 even if another fails.
     */
    void off() {
        kind = Output.Kind.OFF;
        power = Double.NaN;
        RuntimeException first = null;
        try { flywheel.stop(); } catch (RuntimeException e) { first = e; }
        List<DcMotorEx> all = new ArrayList<>(motors);
        for (DcMotorEx m : flywheel.getMotors()) if (!all.contains(m)) all.add(m);
        for (DcMotorEx m : all) {
            try {
                m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            } catch (RuntimeException e) {
                if (first == null) first = e;
            } finally {
                // SAFETY: the 0 goes out even if the mode change failed.
                try { m.setPower(0); } catch (RuntimeException e) { if (first == null) first = e; }
            }
        }
        if (first != null) throw first;
    }

    /** How many motors a plain power goes to (1, or 2 with a follower). */
    int motorCount() {
        return motors.size();
    }

    /** The power the motor had since the last loop: 0 off, the plain power, bang-bang's; NaN when the hub's PID decides. */
    double appliedPower() {
        if (kind == null || kind == Output.Kind.OFF) return 0;
        if (kind == Output.Kind.POWER) return power;
        return flywheel.getSpeedControl() == Flywheel.SpeedControl.BANG_BANG ? flywheel.getCommandedPower() : Double.NaN;
    }
}

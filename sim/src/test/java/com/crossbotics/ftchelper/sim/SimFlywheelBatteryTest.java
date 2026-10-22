package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.finders.FinderMath;
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.qualcomm.robotcore.hardware.DcMotor;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Battery compensation on the simulator's flywheel: the simulator gives each motor power x (battery /
 * 12.8 V), like a real motor, and its hub speed control does not know the battery. Gains found at
 * 12.8 V (as the Flywheel Finder would), then run at 11.0 V: with compensation the wheel gets to its
 * speed and back after a shot faster.
 */
public class SimFlywheelBatteryTest {
    private static final double DT = 0.005;   // 5 ms steps, like a fast robot loop

    // The Flywheel Finder's step 1 on a full battery: full power, the top speed, the gains from it.
    private static double[] gainsAtFullBattery() throws Exception {
        SimRobot robot = SimRobot.load(Paths.get("robots/starter.properties"));
        SimMotor m = robot.motors.get("flywheel");
        m.device.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        m.device.setPower(1);
        for (int i = 0; i < 3 / DT; i++) m.step(DT, 12.8 / 12.8);
        return FinderMath.flywheelPidf(m.device.getVelocity());
    }

    /** {seconds to reach the speed from a stop, seconds to get back after a shot}, at 11.0 V. */
    private static double[] run(boolean compensate) throws Exception {
        double[] g = gainsAtFullBattery();
        SimRobot robot = SimRobot.load(Paths.get("robots/starter.properties"));
        robot.setBattery(11.0);
        SimMotor m = robot.motors.get("flywheel");
        Flywheel fw = new Flywheel("flywheel", m.device).countsPerRev(28).pidf(g[0], g[1], g[2], g[3]);
        if (compensate) fw.batteryCompensation(() -> robot.batteryVolts, 12.8);
        double target = 0.7 * m.maxTicksPerSec;               // 70% of the top speed
        fw.setRpm(target / 28 * 60);
        double reach = Double.NaN, back = Double.NaN;
        double t = 0;
        boolean shot = false;
        double shotAt = 0;
        for (int i = 0; i < 6 / DT; i++, t += DT) {
            fw.update();
            m.step(DT, robot.batteryVolts / 12.8);
            boolean close = Math.abs(m.device.getVelocity() - target) < 0.02 * target;
            if (Double.isNaN(reach) && close) reach = t;
            // 2 s in: a piece goes through (the wheel loses 15% of its speed).
            if (!shot && t >= 2) {
                m.shot();
                shot = true;
                shotAt = t;
            } else if (shot && Double.isNaN(back) && close && t > shotAt + DT) {
                back = t - shotAt;
            }
        }
        return new double[] {reach, back};
    }

    @Test public void aLowBatteryRecoversFasterWithCompensation() throws Exception {
        double[] without = run(false), with = run(true);
        assertTrue("reaches its speed with compensation: " + with[0], !Double.isNaN(with[0]));
        assertTrue("gets back after the shot with compensation: " + with[1], !Double.isNaN(with[1]));
        // Without, the I gain has to make up the missing push: slower (or never within 6 s).
        double withoutReach = Double.isNaN(without[0]) ? 99 : without[0];
        double withoutBack = Double.isNaN(without[1]) ? 99 : without[1];
        assertTrue("spin-up: " + with[0] + " s with, " + withoutReach + " s without", with[0] < withoutReach);
        assertTrue("recovery: " + with[1] + " s with, " + withoutBack + " s without", with[1] < withoutBack);
    }

    @Test public void theHubsVoltageIsReadFromTheRobotConfiguration() throws Exception {
        SimRobot robot = SimRobot.load(Paths.get("robots/starter.properties"));
        robot.setBattery(11.4);
        assertEquals(11.4, Hw.batteryVolts(robot.hardwareMap()), 1e-9);
    }
}

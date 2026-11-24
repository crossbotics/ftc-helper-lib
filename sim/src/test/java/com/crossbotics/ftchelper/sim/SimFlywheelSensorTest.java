package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.launcher.Flywheel;
import com.qualcomm.robotcore.hardware.DcMotor;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

/** The simulator's opt-in speed sensor (steps and lag), slower loops, and a word set by a scenario. */
public class SimFlywheelSensorTest {
    private static final double STEP = 43 / 60.0 * 28;      // 43 RPM in counts per second

    private static SimMotor flywheel() {
        SimMotor m = new SimMotor("flywheel", 0, SimMotor.Kind.FLYWHEEL, 2800, 0.6);
        m.ticksPerRev = 28;
        m.device.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        return m;
    }

    @Test public void offIsTheExactSpeedAsBefore() {
        SimMotor m = flywheel();
        m.device.setPower(1);
        for (int i = 0; i < 100; i++) {
            m.step(0.01, 1);
            assertEquals(m.velocity(), m.device.getVelocity(), 0);
        }
    }

    @Test public void theReadingMovesInSteps() {
        SimMotor m = flywheel();
        m.sensor(43, 0);
        m.device.setPower(1);
        for (int i = 0; i < 100; i++) {
            m.step(0.01, 1);
            double read = m.device.getVelocity();
            assertEquals("a whole number of steps", Math.rint(read / STEP) * STEP, read, 1e-6);
            assertTrue(Math.abs(read - m.velocity()) <= STEP / 2 + 1e-6);
        }
    }

    @Test public void theReadingIsLate() {
        SimMotor m = flywheel();
        m.sensor(0, 0.05);
        m.device.setPower(1);
        double[] trueSpeed = new double[100];
        for (int i = 0; i < 100; i++) {
            m.step(0.01, 1);
            trueSpeed[i] = m.velocity();
            assertEquals("step " + i, i < 5 ? 0 : trueSpeed[i - 5], m.device.getVelocity(), 1e-6);
        }
    }

    @Test public void aReversedMotorReadsThroughItsDirection() {
        SimMotor m = flywheel();
        m.sensor(43, 0.05);
        m.device.setDirection(DcMotor.Direction.REVERSE);
        m.device.setPower(1);
        for (int i = 0; i < 100; i++) m.step(0.01, 1);
        assertTrue(m.device.getVelocity() > 0);
    }

    @Test public void aScenarioSetsItAndPutsItBack() throws Exception {
        try (SimRun s = new SimRun()) {
            SimMotor m = s.robot().motors.get("flywheel");
            SimScenario sc = new SimScenario(s.sim);
            sc.run(Arrays.asList("set sensor flywheel 43 0.05", "set loop 20 60"));
            assertEquals(String.join("\n", sc.report()), 0, sc.failures());
            // At the end of the file: exact again.
            m.device.setPower(1);
            for (int i = 0; i < 20; i++) m.step(0.01, 1);
            assertEquals(m.velocity(), m.device.getVelocity(), 0);
        }
    }

    @Test public void badSensorAndLoopLinesFail() throws Exception {
        try (SimRun s = new SimRun()) {
            SimScenario sc = new SimScenario(s.sim);
            sc.run(Arrays.asList("set sensor nothing 43 0.05", "set sensor flywheel -1 0", "set sensor flywheel 43",
                    "set loop 60 20", "set loop 20"));
            assertEquals(String.join("\n", sc.report()), 5, sc.failures());
        }
    }

    public static class Words {
        public static String speedControl = "PID";
    }

    @Test public void aScenarioCanSetAWordAndPutItBack() throws Exception {
        Field f = Words.class.getField("speedControl");
        Map<Field, Object> before = new LinkedHashMap<>();
        SimTunables.set(f, SimTunables.value(f, "BANG_BANG", "Launcher.speedControl"), before, "Launcher.speedControl");
        assertEquals("BANG_BANG", Words.speedControl);
        SimTunables.restore(before);
        assertEquals("PID", Words.speedControl);
    }

    @Test public void bangBangHoldsThroughTheSensor() throws Exception {
        SimMotor m = flywheel();
        m.sensor(43, 0.05);
        long[] nanos = {1_000_000_000L};
        com.crossbotics.ftchelper.core.Time.useForTests(() -> nanos[0]);
        try {
            Flywheel f = new Flywheel("flywheel", m.device).countsPerRev(28).maxRpm(6000)
                    .bangFeedforward(0, 12.8 / 6000).bangBattery(() -> 12.8, 12.8);
            assertTrue(f.speedControl(Flywheel.SpeedControl.BANG_BANG));
            f.setRpm(2630);
            Random loops = new Random(7);
            double t = 0;
            while (t < 6) {
                f.update();
                assertTrue("never negative", m.userPower() >= 0);
                int ms = 20 + loops.nextInt(41);
                for (int i = 0; i < ms; i++) m.step(0.001, 1);
                nanos[0] += ms * 1_000_000L;
                t += ms / 1000.0;
            }
            assertTrue("no false fault: " + f.getFault(), !f.isFaulted());
            assertEquals(2630, m.velocity() / 28 * 60, 300);
            f.stop();
            assertEquals(0, m.userPower(), 0);
        } finally {
            com.crossbotics.ftchelper.core.Time.useForTests(null);
        }
    }
}

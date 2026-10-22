package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.qualcomm.robotcore.hardware.DcMotor;
import org.junit.Test;

/**
 * A stop the code undoes before the next physics step still happens (a real hub gets every command at
 * once): the motor brakes for as long as it was stopped, and the hub's speed control starts again from
 * nothing. Before, setPower(0) then setPower(x) inside one step was no stop at all.
 */
public class SimMotorTest {
    private static SimMotor spunUp(DcMotor.RunMode mode) {
        SimMotor m = new SimMotor("m", 0, SimMotor.Kind.WHEEL, 2000, 0.12);
        m.device.setMode(mode);
        m.device.setPower(0.8);
        for (int i = 0; i < 100; i++) m.step(0.01, 1);
        return m;
    }

    private static void sleepMs(int ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end) Thread.yield();
    }

    @Test public void aShortStopBetweenTwoStepsBrakes() {
        SimMotor steady = spunUp(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        SimMotor stopped = spunUp(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        double before = stopped.shaftSpeed();
        // at two different places inside the next 10 ms step: a short stop, and a longer one
        for (int ms : new int[] {3, 8}) {
            stopped.device.setPower(0);
            sleepMs(ms);
            stopped.device.setPower(0.8);
            steady.step(0.01, 1);
            stopped.step(0.01, 1);
            assertTrue(ms + " ms stop: slower than without it (" + stopped.shaftSpeed() + " vs " + steady.shaftSpeed() + ")",
                    stopped.shaftSpeed() < steady.shaftSpeed() - 1);
            for (int i = 0; i < 100; i++) { steady.step(0.01, 1); stopped.step(0.01, 1); }
        }
        assertEquals("back to full speed after", before, stopped.shaftSpeed(), 5);
    }

    @Test public void aStopClearsTheHubsSpeedControl() {
        SimMotor m = spunUp(DcMotor.RunMode.RUN_USING_ENCODER);
        m.device.setPower(0.5);                     // slowing: the speed control builds up error
        for (int i = 0; i < 5; i++) m.step(0.01, 1);
        assertTrue(Math.abs(m.integralForTests()) > 0);
        m.device.setPower(0);
        m.device.setPower(0.5);                     // the next move at once, before a physics step
        assertEquals("starts from nothing", 0, m.integralForTests(), 0);
    }
}

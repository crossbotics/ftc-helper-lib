package com.crossbotics.ftchelper.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.Fakes;
import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Hw.batteryReader: the loop gets the latest volts at once; the hubs are asked on another thread. */
public class BatteryReaderTest {

    @Test public void theLoopNeverAsksTheHubsItself() {
        Fakes.Clock c = new Fakes.Clock();
        double[] volts = {12.5};
        int[] asks = {0};
        List<Runnable> queued = new ArrayList<>();
        Hw.BatteryReader r = new Hw.BatteryReader(() -> { asks[0]++; return volts[0]; }, c::get, (key, job) -> queued.add(job));
        assertTrue("no answer yet", Double.isNaN(r.getAsDouble()));
        assertEquals("one question handed to the helper", 1, queued.size());
        assertEquals("the loop did not ask the hubs", 0, asks[0]);
        r.getAsDouble();
        assertEquals("still waiting for the answer: not asked twice", 1, queued.size());
        queued.remove(0).run();                     // the helper thread asks the hubs
        assertEquals(12.5, r.getAsDouble(), 1e-9);
        c.addSec(0.1);
        r.getAsDouble();
        assertEquals("not again before 0.25 s", 0, queued.size());
        c.addSec(0.2);
        volts[0] = 12.0;
        assertEquals("the last answer until the new one comes", 12.5, r.getAsDouble(), 1e-9);
        queued.remove(0).run();
        assertEquals(12.0, r.getAsDouble(), 1e-9);
        // The helper thread is stuck (its answers never come): after 2 s the old answer is not used.
        c.addSec(0.3);
        r.getAsDouble();
        c.addSec(Hw.BATTERY_STALE_SEC);
        assertTrue("too old", Double.isNaN(r.getAsDouble()));
    }

    @Test public void theLibrarysHelperThreadAnswers() throws Exception {
        BackgroundWorker worker = new BackgroundWorker("battery-test");
        try {
            Hw.BatteryReader r = new Hw.BatteryReader(() -> 12.3, System::nanoTime, worker::submitLatest);
            double v = r.getAsDouble();
            long until = System.nanoTime() + 2_000_000_000L;
            while (Double.isNaN(v) && System.nanoTime() < until) {
                Thread.sleep(5);   // a test may wait; the robot's loop never does
                v = r.getAsDouble();
            }
            assertEquals(12.3, v, 1e-9);
        } finally {
            worker.shutdown();
        }
    }
}

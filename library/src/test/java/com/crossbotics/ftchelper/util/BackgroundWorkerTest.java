package com.crossbotics.ftchelper.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class BackgroundWorkerTest {

    @Test public void submitReturnsAtOnceEvenForASlowJob() throws Exception {
        BackgroundWorker w = new BackgroundWorker("test");
        CountDownLatch done = new CountDownLatch(1);
        long start = System.nanoTime();
        w.submit(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }   // a slow "network request"
            done.countDown();
        });
        assertTrue("the caller did not wait", (System.nanoTime() - start) / 1e6 < 50);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        w.shutdown();
    }

    @Test public void submitLatestSkipsOldValuesOfTheSameKey() throws Exception {
        BackgroundWorker w = new BackgroundWorker("test");
        CountDownLatch gate = new CountDownLatch(1);
        List<Integer> sent = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch busy = new CountDownLatch(1);
        w.submit(() -> { busy.countDown(); try { gate.await(); } catch (InterruptedException ignored) { } });   // keep the worker busy
        // Wait until the worker has TAKEN that job, so it is no longer counted as waiting.
        assertTrue(busy.await(2, TimeUnit.SECONDS));
        for (int i = 1; i <= 20; i++) {
            final int value = i;
            w.submitLatest("heading", () -> sent.add(value));
        }
        assertEquals("only one waiting job for the key", 1, w.queued());
        gate.countDown();
        long end = System.nanoTime() + 2_000_000_000L;
        while (sent.isEmpty() && System.nanoTime() < end) Thread.yield();
        Thread.sleep(50);
        assertEquals(1, sent.size());
        assertEquals(20, (int) sent.get(0));                // the newest value
        w.shutdown();
    }

    @Test public void aFailingJobDoesNotStopTheWorker() throws Exception {
        BackgroundWorker w = new BackgroundWorker("test");
        CountDownLatch done = new CountDownLatch(1);
        w.submit(() -> { throw new RuntimeException("boom"); });
        w.submit(done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("boom", w.getLastError());
        w.shutdown();
    }

    @Test public void anErrorWithNoMessageGivesItsName() throws Exception {
        BackgroundWorker w = new BackgroundWorker("test");
        CountDownLatch done = new CountDownLatch(1);
        w.submit(() -> { throw new IllegalStateException(); });
        w.submit(done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("IllegalStateException", w.getLastError());
        w.shutdown();
    }
}

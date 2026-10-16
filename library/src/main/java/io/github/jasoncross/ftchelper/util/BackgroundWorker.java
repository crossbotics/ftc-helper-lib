package io.github.jasoncross.ftchelper.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs slow jobs (a network request, a camera setting) on a separate thread, so the robot's loop
 * never waits for them. Used inside the library (Limelight requests, webcam exposure).
 *
 * <pre>{@code
 * BackgroundWorker worker = new BackgroundWorker("limelight");
 * worker.submitLatest("heading", () -> limelight.updateRobotOrientation(heading)); // returns at once
 * ...
 * worker.shutdown();     // at the end of the OpMode
 * }</pre>
 *
 * <p>{@link #submitLatest} keeps only the NEWEST job for a key: if the robot asks 50 times per second
 * and the network can do 20, the worker sends the latest value and skips the old ones, so the
 * queue never grows. Jobs must not touch motors or servos (do that in the loop).
 *
 * Docs: docs/guides/non-blocking.md
 */
public final class BackgroundWorker {
    private final Map<String, Runnable> pending = new LinkedHashMap<>();
    private final Thread thread;
    private volatile boolean running = true;
    private volatile String lastError = "";
    private int keyCounter = 0;

    /** Starts the worker thread (a daemon: it never keeps the app alive). */
    public BackgroundWorker(String name) {
        thread = new Thread(this::run, "ftchelper-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Queues a job. Returns at once. */
    public void submit(Runnable job) {
        synchronized (pending) {
            pending.put("#" + (keyCounter++), job);
            pending.notifyAll();
        }
    }

    /** Queues a job, replacing a waiting job with the same key (only the newest one runs). Returns at once. */
    public void submitLatest(String key, Runnable job) {
        synchronized (pending) {
            pending.remove(key);       // keep queue order fair: the newest goes to the back
            pending.put(key, job);
            pending.notifyAll();
        }
    }

    /** Jobs waiting to run. */
    public int queued() {
        synchronized (pending) {
            return pending.size();
        }
    }

    /** "" or the last error a job threw (jobs never crash the robot). */
    public String getLastError() {
        return lastError;
    }

    /** Stops the thread. Jobs still waiting are dropped. Returns at once. */
    public void shutdown() {
        running = false;
        synchronized (pending) {
            pending.clear();
            pending.notifyAll();
        }
    }

    /** True until {@link #shutdown()}. */
    public boolean isRunning() {
        return running;
    }

    private void run() {
        while (running) {
            Runnable job;
            synchronized (pending) {
                while (running && pending.isEmpty()) {
                    try {
                        pending.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (!running) return;
                String first = pending.keySet().iterator().next();
                job = pending.remove(first);
            }
            try {
                job.run();
            } catch (RuntimeException e) {
                lastError = String.valueOf(e.getMessage());
            }
        }
    }
}

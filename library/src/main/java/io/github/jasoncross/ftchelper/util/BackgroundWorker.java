package io.github.jasoncross.ftchelper.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs slow jobs (a network request, a camera setting) on a separate thread, so the robot's loop
 * never waits for them. Used inside the library (Limelight requests, webcam exposure).
 *
 * <p>A "thread" is a second line of work that runs at the same time as your OpMode's loop, like
 * a helper who goes off to make a phone call while you keep driving. Your loop hands the helper a
 * job and carries on at once; the helper does the slow waiting.
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
 * <p>Why no motors or servos in a job? Only the loop should control them, so that STOP, limits and
 * safety checks always see everything. Two threads giving a motor orders would fight.
 *
 * Docs: docs/guides/non-blocking.md
 */
public final class BackgroundWorker {
    // The jobs waiting to run, each filed under a key. A LinkedHashMap keeps them in the order
    // they were added, so the oldest waiting job runs first.
    // This list is shared by two threads (your loop adds, the helper takes), so every use of it
    // is inside "synchronized (pending)": only one thread may touch it at a time.
    private final Map<String, Runnable> pending = new LinkedHashMap<>();
    // The helper thread itself.
    private final Thread thread;
    // "volatile" means: when one thread changes this, the other thread sees the change at once
    // (without it, a thread may keep looking at an old copy).
    private volatile boolean running = true;
    private volatile String lastError = "";
    // Counts up to give each plain submit() its own key ("#0", "#1", ...), so none replace each other.
    private int keyCounter = 0;

    /**
     * Starts the worker thread (a daemon: it never keeps the app alive).
     *
     * @param name a short name, shown in error reports, e.g. "limelight"
     */
    public BackgroundWorker(String name) {
        // "this::run" hands the run() method below to the thread, as the work it should do.
        thread = new Thread(this::run, "ftchelper-" + name);
        // A daemon thread is stopped automatically when the app closes, so a forgotten
        // shutdown() cannot keep the robot app running.
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Queues a job. Returns at once.
     *
     * @param job the work to do; {@code () -> ...} is a small piece of code to run later
     */
    public void submit(Runnable job) {
        // "synchronized (pending)": wait our turn so the helper thread is not using the list.
        // This wait is tiny (the helper never holds the list while it runs a slow job).
        synchronized (pending) {
            pending.put("#" + (keyCounter++), job);
            // Wake the helper thread if it is sleeping because the list was empty.
            pending.notifyAll();
        }
    }

    /**
     * Queues a job, replacing a waiting job with the same key (only the newest one runs). Returns at once.
     *
     * @param key a name for this kind of job, e.g. "heading"; a newer job with the same key replaces it
     * @param job the work to do
     */
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
            // Wake the helper so it notices "running" is false and finishes.
            pending.notifyAll();
        }
    }

    /** True until {@link #shutdown()}. */
    public boolean isRunning() {
        return running;
    }

    // The helper thread's work: take the oldest job, run it, repeat, until shutdown().
    // This is the ONLY place in the library allowed to wait, because it runs on its own thread,
    // never on the robot's loop.
    private void run() {
        while (running) {
            Runnable job;
            synchronized (pending) {
                // Nothing to do: sleep until submit/submitLatest/shutdown wakes us.
                // wait() lets go of the list while sleeping, so the loop can add jobs.
                while (running && pending.isEmpty()) {
                    try {
                        pending.wait();
                    } catch (InterruptedException e) {
                        // Someone asked this thread to stop: finish quietly.
                        return;
                    }
                }
                if (!running) return;
                // Take the oldest waiting job out of the list.
                String first = pending.keySet().iterator().next();
                job = pending.remove(first);
            }
            // Run the job OUTSIDE the synchronized part, so the loop can keep adding jobs while
            // this slow one runs.
            try {
                job.run();
            } catch (Exception | LinkageError e) {
                // A failed job must not kill the helper or crash the robot: keep the message
                // for getLastError() and go on with the next job. (LinkageError: a missing piece of
                // the SDK, e.g. an old SDK version. Other serious Java errors still stop the helper.)
                // No message? Use the error's name, so the note is never just "null".
                lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
    }
}

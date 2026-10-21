package com.crossbotics.ftchelper.sim;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A small helper for scripted tests: start the simulator, run an OpMode, press and hold buttons,
 * wait for things, read the screen. Time is real (the OpMode runs on its own thread), so waits are
 * short and every wait has a time limit.
 */
final class SimRun implements AutoCloseable {
    final SimEngine sim;

    SimRun() throws Exception {
        this("robots/starter.properties");
    }

    SimRun(String robotFile) throws Exception {
        sim = new SimEngine(SimRobot.load(Paths.get(robotFile)));
        sim.start();
    }

    Gamepad g1() { return sim.ds.pad1.gamepad; }
    Gamepad g2() { return sim.ds.pad2.gamepad; }
    SimRobot robot() { return sim.robot; }

    /** INIT, then waits until the OpMode has shown its INIT screen (or stopped with an error). */
    SimRun init(String opMode) throws InterruptedException {
        int before = sim.ds.screen.updates();
        sim.ds.init(opMode);
        waitFor(() -> sim.ds.screen.updates() > before || !sim.ds.error().isEmpty()
                || sim.ds.phase() == SimDriverStation.Phase.STOPPED, 3);
        return this;
    }

    /** START, then waits until the OpMode has run a couple of loops (or stopped). */
    SimRun start() throws InterruptedException {
        sim.ds.start();
        int before = sim.ds.screen.updates();
        waitFor(() -> sim.ds.screen.updates() >= before + 2 || sim.ds.phase() != SimDriverStation.Phase.RUNNING, 3);
        return this;
    }

    /**
     * Presses and lets go of a button: set it with the setter, e.g. v -> s.g1().a = v. It holds the
     * button until the OpMode has sent at least two screens (so its loop surely saw it), and waits
     * the same after letting go, so a busy computer cannot make the test miss a press.
     */
    SimRun press(Consumer<Boolean> button) throws InterruptedException {
        button.accept(true);
        waitForLoops(2);
        button.accept(false);
        waitForLoops(2);
        return this;
    }

    /** Waits until the OpMode has sent this many more screens (each loop sends one), at most 3 s. */
    void waitForLoops(int loops) throws InterruptedException {
        int start = sim.ds.screen.updates();
        waitFor(() -> sim.ds.screen.updates() >= start + loops, 3);
    }

    static void pause(double seconds) throws InterruptedException {
        Thread.sleep((long) (seconds * 1000));
    }

    /** Waits until the condition is true; false if it did not happen within the time. */
    static boolean waitFor(BooleanSupplier condition, double seconds) throws InterruptedException {
        long end = System.nanoTime() + (long) (seconds * 1e9);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    List<String> screen() {
        return sim.ds.screen.screen();
    }

    boolean screenHas(String text) {
        for (String line : screen()) if (line.contains(text)) return true;
        return false;
    }

    String error() {
        return sim.ds.error();
    }

    @Override
    public void close() {
        sim.shutdown();
    }
}

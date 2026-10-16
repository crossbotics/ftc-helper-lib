package io.github.jasoncross.ftchelper.sim;

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
        sim = new SimEngine(SimRobot.load(Paths.get("robots/starter.properties")));
        sim.start();
    }

    Gamepad g1() { return sim.ds.pad1.gamepad; }
    Gamepad g2() { return sim.ds.pad2.gamepad; }
    SimRobot robot() { return sim.robot; }

    SimRun init(String opMode) throws InterruptedException {
        sim.ds.init(opMode);
        pause(0.3);
        return this;
    }

    SimRun start() throws InterruptedException {
        sim.ds.start();
        pause(0.3);
        return this;
    }

    /** Presses and lets go of a button: set it with the setter, e.g. g -> g.a = ... */
    SimRun press(Consumer<Boolean> button) throws InterruptedException {
        button.accept(true);
        pause(0.15);
        button.accept(false);
        pause(0.15);
        return this;
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

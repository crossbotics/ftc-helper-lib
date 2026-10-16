package io.github.jasoncross.ftchelper.sim;

/**
 * The simulator's heart: the pretend robot, the pretend Driver Station, and a physics thread that
 * moves the robot 100 times a second. The web page and the tests both drive it.
 */
final class SimEngine {
    final SimRobot robot;
    final SimDriverStation ds;
    private volatile boolean running = false;
    private Thread physics;

    SimEngine(SimRobot robot) {
        this.robot = robot;
        this.ds = new SimDriverStation(robot);
    }

    SimEngine(SimRobot robot, SimDriverStation.Scope scope) {
        this.robot = robot;
        this.ds = new SimDriverStation(robot, scope);
    }

    /** Starts the physics thread. */
    void start() {
        running = true;
        physics = new Thread(() -> {
            long last = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                double dt = Math.min(0.05, (now - last) / 1e9);
                last = now;
                try {
                    robot.step(dt);
                    ds.tick();
                } catch (RuntimeException e) {
                    ds.physicsError("Simulator physics error: " + e);
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "physics");
        physics.setDaemon(true);
        physics.start();
    }

    /** Stops the OpMode and the physics. */
    void shutdown() {
        ds.stop();
        running = false;
        if (physics != null) physics.interrupt();
    }
}

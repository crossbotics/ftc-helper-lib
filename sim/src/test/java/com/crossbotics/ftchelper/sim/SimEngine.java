package com.crossbotics.ftchelper.sim;

/**
 * The simulator's heart: the pretend robot, the pretend Driver Station, and a physics thread that
 * moves the robot 100 times a second. The web page and the tests both drive it.
 */
final class SimEngine {
    final SimRobot robot;
    final SimDriverStation ds;
    private volatile boolean running = false;
    // The physics clock: seconds of simulated time so far, moved on with each step (read it while holding the
    // robot's lock to get the time that goes with the positions). The page uses it to glide between updates.
    private double simSeconds = 0;
    // Counts jumps: the robot or the pieces put somewhere by hand or by a reset. The page does not glide across one.
    final java.util.concurrent.atomic.AtomicInteger jumps = new java.util.concurrent.atomic.AtomicInteger();
    private Thread physics;

    static {
        // OpModes can tell they run here: an untuned autonomous drive runs anyway (EasyAuto).
        System.setProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY, "true");
    }

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
                    synchronized (robot) {
                        robot.step(dt);
                        simSeconds += dt;
                    }
                    ds.tick();
                    placeForInit();
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

    /** Seconds of physics so far (the time that goes with the positions read under the robot's lock). */
    double simSeconds() {
        synchronized (robot) {
            return simSeconds;
        }
    }

    // The spot the robot was last put on for INIT (null = none yet), and for which OpMode run.
    private String placedFor = "";
    private com.crossbotics.ftchelper.util.FieldPose placedAt = null;

    /**
     * In INIT, the robot goes where the program says: an autonomous's start spot (or, without one, its
     * alliance's default); a TeleOp with no autonomous before it, its alliance's default. It moves only
     * when that spot changes, so a robot dragged on the field map stays where it was dropped.
     */
    private void placeForInit() {
        if (ds.phase() != SimDriverStation.Phase.INIT || ds.current() == null) return;
        String run = ds.current().name + "#" + ds.initCount;
        if (!run.equals(placedFor)) {
            placedFor = run;
            placedAt = null;
        }
        boolean auto = "Autonomous".equals(ds.current().kind);
        com.crossbotics.ftchelper.util.FieldPose target;
        if (auto) {
            // An autonomous that names its start spot; one that does not leaves the robot where it is.
            target = com.crossbotics.ftchelper.match.MatchMemory.placement();
            if (target == null) return;
        } else {
            // TeleOp after an autonomous: the robot is where autonomous left it. Without one: once the
            // drivers choose the alliance in INIT, that alliance's default spot.
            if (com.crossbotics.ftchelper.match.MatchMemory.hasAlliance()) return;
            com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
            if (a == null) return;
            target = defaultSpot(a);
        }
        if (placedAt != null && Math.abs(placedAt.x - target.x) < 1e-6 && Math.abs(placedAt.y - target.y) < 1e-6
                && Math.abs(placedAt.headingDeg - target.headingDeg) < 1e-6) return;
        placedAt = target;
        try {
            robot.setPose(target.x, target.y, target.headingDeg);
            jumps.incrementAndGet();
        } catch (IllegalArgumentException e) {
            ds.physicsError("Start spot: " + e.getMessage() + " (" + target + ")");
        }
    }

    /**
     * The robot file's start (written for RED, at the bottom); for BLUE the same spot seen from the blue
     * wall: turned half a turn about the field's centre (BIOBUZZ's field is turned, not mirrored, between
     * the alliances: its CAD has each red part's twin there).
     */
    com.crossbotics.ftchelper.util.FieldPose defaultSpot(com.crossbotics.ftchelper.match.Alliance a) {
        return a == com.crossbotics.ftchelper.match.Alliance.RED
                ? new com.crossbotics.ftchelper.util.FieldPose(robot.startX, robot.startY, robot.startHeading)
                : new com.crossbotics.ftchelper.util.FieldPose(-robot.startX, -robot.startY, robot.startHeading + 180);
    }

    /** Stops the OpMode and the physics. */
    void shutdown() {
        ds.stop();
        ds.close();
        running = false;
        if (physics != null) {
            physics.interrupt();
            try {
                physics.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // the physics world only once the physics thread has stopped using it
        if (physics == null || !physics.isAlive()) robot.closeWorld();
    }
}

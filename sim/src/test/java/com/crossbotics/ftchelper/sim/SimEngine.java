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
    /** The auto builder's run of the Built Auto (watched each physics step). */
    final BuiltRun built = new BuiltRun(this);
    /** Run after each physics step (the test recorder watches for an OpMode that stops by itself). */
    volatile Runnable onStep = null;
    /**
     * True while the page's Edit robot mode is open: nothing moves (no physics step, no OpMode loop), so the
     * robot holds still while the team shapes it. False again goes on from where it was.
     */
    volatile boolean paused = false;

    static {
        // OpModes can tell they run here: an untuned autonomous drive runs anyway (EasyAuto).
        System.setProperty(com.crossbotics.ftchelper.auto.EasyAuto.SIMULATOR_PROPERTY, "true");
    }

    SimEngine(SimRobot robot) {
        this(robot, SimDriverStation.defaultScope());
    }

    SimEngine(SimRobot robot, SimDriverStation.Scope scope) {
        this.robot = robot;
        this.ds = new SimDriverStation(robot, scope);
        ds.beforeStart = this::placeForInit;
        matchClock();
    }

    // The game rules' match clock: a running autonomous is AUTO, a running TeleOp is TELEOP (with the rules
    // file's lengths: 30 s and 2:00 for BIOBUZZ); nothing running is free play.
    private void matchClock() {
        if (robot.pieces == null) return;
        final SimRules rules = robot.field.rules;
        robot.pieces.clock = new SimPieces.MatchClock() {
            @Override public String period() {
                SimDriverStation.Entry e = ds.current();
                if (ds.phase() != SimDriverStation.Phase.RUNNING || e == null) return "";
                return "Autonomous".equals(e.kind) ? "AUTO" : "TELEOP";
            }

            @Override public double secondsLeft() {
                String p = period();
                if (p.isEmpty()) return Double.NaN;
                double length = p.equals("AUTO") ? (rules == null ? 30 : rules.autoSec) : (rules == null ? 120 : rules.teleopSec);
                return Math.max(0, length - ds.runSeconds());
            }
        };
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
                    if (!paused) stepOnce(dt);
                } catch (RuntimeException e) {
                    ds.physicsError("Simulator physics error: " + e);
                }
                synchronized (stepsDone) {
                    stepsDone[0]++;
                    stepsDone.notifyAll();
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

    // One physics step and what goes with it (the OpMode's loop, the INIT spot, the auto builder's run).
    private void stepOnce(double dt) {
        synchronized (robot) {
            robot.step(dt);
            simSeconds += dt;
        }
        ds.tick();
        placeForInit();
        built.tick();
        Runnable watch = onStep;
        if (watch != null) watch.run();
    }

    // Physics steps finished so far (each one: the robot moved, the Driver Station's timers, the INIT spot).
    private final long[] stepsDone = {0};

    /**
     * Waits until the physics has run one whole step that began after this call, at most maxSec. WHY: the
     * physics runs on its own thread, every 10 ms. A scenario line right after INIT ("expect shape
     * intake_arm = -40", START) could run before the physics had seen the INIT at all: the robot not yet
     * on its INIT spot, a dropped intake still up. That happened only sometimes (when the computer was
     * quick, e.g. in the second file of a run), so a test passed alone and failed in a set. Returns at
     * once when the physics is not running.
     */
    void awaitStep(double maxSec) throws InterruptedException {
        Thread p = physics;
        if (!running || p == null || !p.isAlive() || Thread.currentThread() == p) return;
        long deadline = System.nanoTime() + (long) (maxSec * 1e9);
        synchronized (stepsDone) {
            // the step running now may have begun before the call: wait for the one after it too
            long want = stepsDone[0] + 2;
            while (stepsDone[0] < want && running && p.isAlive()) {
                long left = deadline - System.nanoTime();
                if (left <= 0) return;
                stepsDone.wait(Math.max(1, left / 1_000_000));
            }
        }
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
        // from the physics thread and from START: one at a time
        synchronized (placeLock) {
            placeForInitNow();
        }
    }

    private final Object placeLock = new Object();

    // In INIT of an autonomous whose start spot is only in words: what the page says to do ("" = nothing).
    volatile String placeNote = "";
    static final String PLACE_NOTE = "This autonomous gives its start spot in words only, so the robot stays where it is: "
            + "drag it to its start spot on the map. To place it by itself, give the spot in inches in the code: "
            + "s.start(\"...\", x, y, heading) (the Robot panel shows the numbers for where the robot is now).";

    private void placeForInitNow() {
        if (ds.phase() != SimDriverStation.Phase.INIT || ds.current() == null) return;
        String run = ds.current().name + "#" + ds.initCount;
        if (!run.equals(placedFor)) {
            placedFor = run;
            placedAt = null;
        }
        boolean auto = "Autonomous".equals(ds.current().kind);
        com.crossbotics.ftchelper.util.FieldPose target;
        if (auto) {
            // An autonomous that names its start spot (in inches) goes there. One that says it only in words
            // leaves the robot where it is: the page asks the team to drag it there, or to give the spot in
            // inches in the code (placeNote).
            target = com.crossbotics.ftchelper.match.MatchMemory.placement();
            if (target == null) {
                placeNote = PLACE_NOTE;
                return;
            }
            placeNote = "";
        } else {
            placeNote = "";
            // TeleOp after an autonomous: the robot is where autonomous left it. Without one: once the
            // drivers choose the alliance in INIT, that alliance's default spot.
            if (com.crossbotics.ftchelper.match.MatchMemory.hasAlliance()) return;
            com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
            if (a == null) a = allianceUntilChosen(ds);
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
            // The team's own numbers (as written in start(...)), not the field frame's, which may be turned.
            String written = com.crossbotics.ftchelper.match.MatchMemory.placementAsWritten();
            ds.physicsError("Start spot " + (auto && !written.isEmpty() ? written : target.toString()) + ": " + e.getMessage()
                    + (auto ? ". Change the spot in the autonomous's start(...)" : ""));
        }
    }

    /**
     * The alliance a TeleOp works with in INIT before the drivers choose one: the library's TeleOp says
     * "Alliance : BLUE until you choose", so the robot stands on BLUE's spot and the page looks from the
     * blue wall until then (all three agree). Null when an alliance is known or it is not such a TeleOp.
     */
    static com.crossbotics.ftchelper.match.Alliance allianceUntilChosen(SimDriverStation ds) {
        SimDriverStation.Entry e = ds.current();
        if (e == null || ds.phase() != SimDriverStation.Phase.INIT || !"TeleOp".equals(e.kind)) return null;
        if (!com.crossbotics.ftchelper.teleop.EasyTeleOp.class.isAssignableFrom(e.type)) return null;
        if (com.crossbotics.ftchelper.match.MatchMemory.hasAlliance() || com.crossbotics.ftchelper.match.MatchMemory.chosen() != null) return null;
        return com.crossbotics.ftchelper.match.Alliance.BLUE;
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

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
        // Right before START: the INIT spot once more, then (a words-only autonomous) the spot to remember.
        // One lock with the physics thread's placing, so START never lands between its steps.
        ds.beforeStart = () -> {
            synchronized (placeLock) {
                placeForInitNow();
                rememberWordsSpot();
            }
        };
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

    // True if the robot can stand at this pose (nothing overlaps).
    private boolean fits(com.crossbotics.ftchelper.util.FieldPose p) {
        synchronized (robot) {
            return robot.blockedAt(p.x, p.y, p.headingDeg) == null;
        }
    }

    // In INIT of an autonomous whose start spot is only in words: what the page says to do ("" = nothing).
    volatile String placeNote = "";
    private static final String CODE_HINT = " To place it from the code, give the spot in inches: s.start(\"...\", x, y, heading)."
            + " The Robot panel shows the numbers.";
    static final String NOTE_CHOOSE = "This autonomous gives its start spot in words only. Choose the alliance in its INIT "
            + "menu: the robot then goes to that side.";
    static final String NOTE_DRAG = "This autonomous gives its start spot in words only. Drag the robot to its exact start "
            + "spot. At START the simulator remembers it for next time, for both alliances." + CODE_HINT;
    static final String NOTE_SAVED = "The robot is where you started this autonomous last time. Drag it to change the spot."
            + CODE_HINT;
    static final String NOTE_GONE = "The spot you started this autonomous at last time no longer fits (the robot or the "
            + "field changed). Drag the robot to its start spot again." + CODE_HINT;

    // The words-only autonomous in INIT now: its OpMode name (null = none), and the alliance chosen.
    private volatile String wordsKey = null;
    private volatile com.crossbotics.ftchelper.match.Alliance wordsAlliance = null;
    // The saved spots, read once per INIT (not every physics step).
    private java.util.Properties spotCache = new java.util.Properties();

    // A spot is saved as RED's (as the robot file's start is written). BLUE's is the same spot seen from the
    // blue wall: turned half a turn about the field's centre, like defaultSpot. The same turn goes both ways.
    static com.crossbotics.ftchelper.util.FieldPose forAlliance(com.crossbotics.ftchelper.util.FieldPose p,
                                                               com.crossbotics.ftchelper.match.Alliance a) {
        if (a == com.crossbotics.ftchelper.match.Alliance.RED) return p;
        double h = p.headingDeg + 180;
        h = h - 360 * Math.floor((h + 180) / 360);   // -180 to 180
        return new com.crossbotics.ftchelper.util.FieldPose(-p.x, -p.y, h);
    }
    /**
     * True on the page (SimMain): a words-only autonomous's spot at START is saved for next time (one spot
     * per autonomous, used for both alliances: turned half a turn for the other side), in
     * sim-start-spots.properties next to the simulator's settings. Off in tests, so one test cannot move
     * another's robot.
     */
    volatile boolean rememberSpots = false;

    static java.io.File spotsFile() {
        return new java.io.File(com.crossbotics.ftchelper.core.SettingsFile.folder(), "sim-start-spots.properties");
    }

    private static java.util.Properties readSpots() {
        java.util.Properties p = new java.util.Properties();
        java.io.File f = spotsFile();
        if (f.isFile()) {
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                p.load(in);
            } catch (java.io.IOException e) {
                // unreadable: start from the defaults
            }
        }
        return p;
    }

    private com.crossbotics.ftchelper.util.FieldPose savedSpot(String key) {
        return rememberSpots ? parseSpot(spotCache.getProperty(key)) : null;
    }

    /** The saved RED spot of this autonomous (OpMode name), or null. */
    static com.crossbotics.ftchelper.util.FieldPose loadSpot(String key) {
        return parseSpot(readSpots().getProperty(key));
    }

    private static com.crossbotics.ftchelper.util.FieldPose parseSpot(String v) {
        if (v == null) return null;
        try {
            String[] n = v.split(",");
            return new com.crossbotics.ftchelper.util.FieldPose(Double.parseDouble(n[0]), Double.parseDouble(n[1]), Double.parseDouble(n[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // At START of a words-only autonomous: where the robot stands now, for next time. Only when the team moved
    // it (a robot left on the robot file's start keeps following that file).
    private void rememberWordsSpot() {
        String key = wordsKey;
        if (!rememberSpots || key == null || ds.phase() != SimDriverStation.Phase.INIT) return;
        double x, y, h;
        synchronized (robot) {
            x = robot.x;
            y = robot.y;
            h = robot.headingDeg;
        }
        com.crossbotics.ftchelper.util.FieldPose at = placedAt;
        if (at != null && Math.abs(at.x - x) < 0.01 && Math.abs(at.y - y) < 0.01 && Math.abs(at.headingDeg - h) < 0.1) return;
        // Saved as RED's spot (the same turn takes BLUE's spot to RED's).
        com.crossbotics.ftchelper.util.FieldPose red = forAlliance(new com.crossbotics.ftchelper.util.FieldPose(x, y, h),
                wordsAlliance == null ? com.crossbotics.ftchelper.match.Alliance.RED : wordsAlliance);
        saveSpot(key, red);
    }

    /** Saves an autonomous's RED spot (OpMode name -> field inches and degrees) for next time; null forgets it. */
    static void saveSpot(String key, com.crossbotics.ftchelper.util.FieldPose red) {
        java.util.Properties p = readSpots();
        if (red == null) p.remove(key);
        else p.setProperty(key, String.format(java.util.Locale.US, "%.2f,%.2f,%.1f", red.x, red.y, red.headingDeg));
        java.io.File f = spotsFile();
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            p.store(out, "Start spots of autonomous OpModes that give theirs in words only (field inches, degrees)");
        } catch (java.io.IOException e) {
            // not saved: next time it starts from the default spot again
        }
    }

    private void placeForInitNow() {
        if (ds.phase() != SimDriverStation.Phase.INIT || ds.current() == null) return;
        String run = ds.current().name + "#" + ds.initCount;
        if (!run.equals(placedFor)) {
            placedFor = run;
            placedAt = null;
            placeNote = "";
            if (rememberSpots) spotCache = readSpots();   // once per INIT
        }
        boolean auto = "Autonomous".equals(ds.current().kind);
        com.crossbotics.ftchelper.util.FieldPose target;
        if (auto) {
            // An autonomous that names its start spot (in inches) goes there. One that says it only in words
            // leaves the robot where it is: the page asks the team to drag it there, or to give the spot in
            // inches in the code (placeNote).
            target = com.crossbotics.ftchelper.match.MatchMemory.placement();
            if (target == null) {
                // Its spot in words only: the chosen alliance's side, where this auto started last time if
                // the team placed it before (remembered at START), else the robot file's start for that side.
                com.crossbotics.ftchelper.match.Alliance a = com.crossbotics.ftchelper.match.MatchMemory.chosen();
                if (a == null) {
                    wordsKey = null;
                    placeNote = NOTE_CHOOSE;
                    return;
                }
                String key = ds.current().name;
                wordsAlliance = a;
                wordsKey = key;
                com.crossbotics.ftchelper.util.FieldPose saved = savedSpot(key);
                target = saved == null ? defaultSpot(a) : forAlliance(saved, a);
                if (saved != null && !fits(target)) {
                    // The remembered spot no longer fits (a bigger robot, a changed field): forget it.
                    spotCache.remove(key);
                    saveSpot(key, null);
                    target = defaultSpot(a);
                    placeNote = NOTE_GONE;
                } else if (!NOTE_GONE.equals(placeNote)) {
                    placeNote = saved == null ? NOTE_DRAG : NOTE_SAVED;
                }
            } else {
                wordsKey = null;
                placeNote = "";
            }
        } else {
            placeNote = "";
            wordsKey = null;
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

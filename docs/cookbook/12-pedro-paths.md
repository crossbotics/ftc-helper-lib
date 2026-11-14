# Pedro paths

Pedro Pathing drives smooth curves. It needs the `pedro` folder in your project ([add Pedro](../building-autos/05-pedro-paths.md#your-project-add-the-pedro-add-on-once)),
a mecanum drive and tuned odometry. Paths use the same field numbers as `driveTo`.

**The questions on this page:**

- [How do I add Pedro Pathing to Robot.java?](#how-do-i-add-pedro-pathing-to-robotjava)
- [How do I make a straight path and a curve?](#how-do-i-make-a-straight-path-and-a-curve)
- [How do I follow a path in an autonomous?](#how-do-i-follow-a-path-in-an-autonomous)
- [How do I drive two paths with no stop, slow down, or hold at the end?](#how-do-i-drive-two-paths-with-no-stop-slow-down-or-hold-at-the-end)
- [How do I keep facing the goal along a path?](#how-do-i-keep-facing-the-goal-along-a-path)
- [How do I drive first and turn at the end of a path (or turn first)?](#how-do-i-drive-first-and-turn-at-the-end-of-a-path-or-turn-first)
- [How do I start something partway along a path?](#how-do-i-start-something-partway-along-a-path)

## How do I add Pedro Pathing to Robot.java?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: pedro-follower -->
```java title="Robot.java"
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // Pedro needs odometry
// Pedro drives the paths with the drive and odometry lines above. PedroConstants holds the
// numbers from Pedro's Foresight tuner (docs/modules/pedro.md#tuning-pedro).
follower(Pedro.follower(PedroConstants.foresight));
```

**Why:** Pedro uses your drive and odometry lines, so you describe the robot only once.
**More:** [Add Pedro paths: Robot.java](../building-autos/05-pedro-paths.md#robotjava-one-line-and-pedros-numbers-file), [Tuning Pedro](../modules/pedro.md#tuning-pedro).

## How do I make a straight path and a curve?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-line-curve -->
```java title="autos/YourAuto.java"
// Make each path once. A line: from (x1, y1) to (x2, y2). A curve: start, a control point
// that pulls the curve toward it (the robot does not drive through it), end.
static final PedroPath OUT = PedroPath.line(-56, -61, -56, -36).keepHeading(0);
static final PedroPath TO_GOAL = PedroPath.curve(-56, -36, -44, -36, -30, -30).turnTo(0, 45);
```

**Why:** Java makes a path once (`static final`), because it is the same every match.
**More:** [PedroPath](../modules/pedro.md#pedropath-paths-in-our-field-numbers).

## How do I follow a path in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: pedro-follow -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .follow("Out", OUT)                     // brakes to a stop at its end
 .follow("To the goal", TO_GOAL)
 .run("Intake on", robot.intake::in)
 .follow("Back", WATCH_GOAL)
 .parkWithSeconds(4)
 .driveTo("Park", -56, -36, 0, 0.7);     // driveTo works too (it ends any hold first)
```

**Why:** each `follow` brakes to a stop at its end. Start the next path where the last one ended.
**More:** [Add Pedro paths: your first path](../building-autos/05-pedro-paths.md#actionsjava-and-autos-make-paths-then-follow-them), [follow](../modules/pedro.md#follow-the-autonomous-step).

## How do I drive two paths with no stop, slow down, or hold at the end?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-chain -->
```java title="autos/YourAuto.java"
// Two parts with NO stop between them (then), a slow part near the goal (maxSpeed),
// and hold the end point while it scores (holdEnd: at most 5 s).
static final PedroPath IN_ONE_GO = PedroPath.line(-56, -61, -48, -50).keepHeading(0)
        .then(PedroPath.curve(-48, -50, -44, -36, -30, -30)).turnTo(0, 45)
        .maxSpeed(0.6)
        .holdEnd();
```

**Why:** `then` saves the time of a stop, and `holdEnd` pushes back if another robot bumps yours while it scores.
**More:** [PedroPath](../modules/pedro.md#pedropath-paths-in-our-field-numbers), [Hold the end point](../modules/pedro.md#hold-the-end-point).

## How do I keep facing the goal along a path?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-face-point -->
```java title="autos/YourAuto.java"
// Keep facing one field point the whole way (here x -60, y 60: put your goal's x and y).
static final PedroPath WATCH_GOAL = PedroPath.line(-30, -30, -56, -36).facePoint(-60, 60);
```

**Why:** the robot can slide along the path while it keeps the camera or launcher on the goal.
**More:** [PedroPath: the heading](../modules/pedro.md#pedropath-paths-in-our-field-numbers).

## How do I drive first and turn at the end of a path (or turn first)?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-turn-when -->
```java title="autos/YourAuto.java"
// Turn from 0 to 90 only in the last 30% of the line (0.7 to 1): drive first, then turn.
static final PedroPath DRIVE_THEN_TURN = PedroPath.line(-56, -61, -30, -30).turnTo(0, 90, 0.7, 1);
```

**Why:** `turnTo(a, b)` turns all along the part. The two extra numbers say on which stretch it turns (0 = the
part's start, 1 = its end). `0, 0.3` turns first, then drives. `0.7, 1` drives first, then turns.
**More:** [PedroPath: the heading](../modules/pedro.md#pedropath-paths-in-our-field-numbers).

## How do I start something partway along a path?

**Where:** your autonomous, `steps(...)`. Android Studio shows `PathAt` in red. Press Alt+Enter to add the import.

<!-- recipe: pedro-along -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 // PathAt: start things ALONG the path, once each, while the robot keeps driving.
 .follow("To the goal", Paths.IN_ONE_GO,
         PathAt.part(2, "Intake on", robot.intake::in),            // where part 2 starts
         PathAt.inchesLeft(6, "Intake off", robot.intake::stop))   // 6 in before the end
 .waitSeconds("Score (the robot holds still)", 0.5);
```

You can also use `PathAt.fraction(0.5, ...)` (halfway along the whole path), `PathAt.inchesDone(10, ...)`
and `PathAt.near(x, y, 6, ...)`, for example `PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp)`.
In place of a name and a command, you can give an action from your `Actions.java`
(`PathAt.inchesLeft(12, Actions.scoreHigh(robot))`: any action you wrote).

**Why:** the robot does not stop to start a mechanism, and Pedro has no "do this here" of its own. If the
path ends early, the report says which ones did not start (`STALLED, not fired: Intake off`).
**More:** [Do things along the path](../modules/pedro.md#do-things-along-the-path).

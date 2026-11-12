# Steps for autos

Every autonomous step, actions, modes, and keeping time to park. Learn it step by step:
[Building autos](../building-autos/README.md) (every step, with a tested example).

## Auto

```java
@Autonomous(name = "Score and Park", preselectTeleOp = "TeleOp")
public class ScoreAndPark extends Auto<Robot> {
    @Override protected void steps(Steps s, Robot robot) {
        s.start("Back wall, 2nd tile from the left, facing the field", 0)
         .menuYesNo("Score", true)
         .run("Hold the piece", robot.claw::close)
         .forward("Leave the wall", 4, 0.4)
         .onlyIf("Score")
             .run("Lift up", () -> robot.lift.goTo("high"))
             .forward("Drive to the goal", 24, 0.6)
             .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
             .run("Drop", robot.claw::open)
         .end()
         .parkWithSeconds(4)
         .right("Park", 20, 0.7);
    }
}
```

What `Steps` can do:

| Step | What it does |
| --- | --- |
| `start(where, headingDeg)` | where to place the robot (shown in INIT) and which way it faces |
| `start(where, xIn, yIn, headingDeg)` | the same, plus the spot in inches. The simulator puts the robot there in INIT ([The start spot](#the-start-spot)) |
| `menuYesNo(label, yes)`, `menuChoice(label, options...)` | a choice for the drivers in INIT |
| `onlyIf(label)` / `onlyIf(label, option)` ... `end()` | the steps in between run only for that choice. A `start(...)` or `parkWithSeconds(...)` in between is used only for that choice, and the INIT note follows the menu ([choose the start in INIT](../building-autos/06-advanced-autos.md#a-whole-example-one-auto-the-drivers-choose)) |
| `isYes(label)`, `choice(label)` | reads a choice |
| `forward(label, inches, power)`, `back(...)` | drive forward / backward |
| `left(label, inches, power)`, `right(...)` | slide to the robot's left / right, still facing the same way (mecanum only) |
| `move(label, inches, directionDeg, power)` | drive at an angle: counterclockwise, like a heading (0 forward, 90 left, 180 back, -90 right) |
| `turnLeft(label, degrees, power)`, `turnRight(...)` | turn left / right by this many degrees |
| `turn(label, degrees, power)`, `turnTo(label, headingDeg, power)` | turn by an angle / to a heading (counterclockwise: 90 = left, the same rule as a move) |
| `moveWithTurn(label, inches, directionDeg, headingDeg, power)` | drive AND turn at the same time (mecanum only). The direction is fixed on the field, counterclockwise from the start heading (0 = the way the robot faced at the start, 90 = its left then). It ends facing `headingDeg` (0 = the start heading, counterclockwise +). A tank drive cannot: use `forward`, then `turnTo` |
| `aimAtGoal(label, timeoutSec)` | turn to the goal with the camera (or the odometry backup), or wait for a following turret. The report says "not seen" when there was no goal ([Aim](parts/aim.md)) |
| `aimAtGoal(label, timeoutSec, sweepDeg)` | the same, with a sweep when the goal is not in view: up to `sweepDeg` each way, slowly, then aim ([Aim](parts/aim.md)). A turret that follows the goal does not sweep: the step waits for the turret |
| `waitForGoal(label)` | WAIT until the camera sees the goal and the goal is still, then go on ([waitForGoal](#waitforgoal)) |
| `waitForGoal(label, timeoutSec)` | the same, but at most `timeoutSec`: then it goes on anyway, and the report says "not seen in 10 s: went on" (or "seen, never still") |
| `rememberHeading(label)`, `turnBack(label, maxPower)` | save the heading now; later turn back to it (without odometry pods it uses the IMU: better than nothing). The same as `AutoRoutine`'s `rememberHeading` / `returnToHeading` |
| `readyToLaunch(label, timeoutSec)` | spin the launcher up and WAIT until it is ready to fire, then go on. The speed keeps following the distance in the next steps ([Launcher](parts/launcher.md)). To spin up while the next step drives instead: `.run("Spin up", robot.launcher::spinUp)` |
| `driveTo(label, xIn, yIn, headingDeg, power[, toleranceIn][, PathAt...])` | drive to a field point and heading with the odometry, correcting all the way (mecanum; needs `Tunables.Odometry.checked` on a robot) ([driveTo](../building-autos/05-auto-builder-odometry-and-pedro.md#drive-to-a-point-driveto)). [`PathAt`](parts/path-follower.md) starts things on the way |
| `routeTo(label, xIn, yIn, headingDeg, power[, toleranceIn])` | `driveTo`, but around the boxes of `fieldMap(...)`, from wherever the robot is when the step starts ([routes](../modules/odometry.md#keep-out-boxes-and-routes)) |
| `follow(label, path[, timeoutSec][, PathAt...])` | follow a path with the robot's [path follower](parts/path-follower.md) ([pedro](../modules/pedro.md)). [`PathAt`](parts/path-follower.md) starts things along it |
| `run(label, command)` | start something and go on at once |
| `waitSeconds(label, seconds)`, `waitUntil(label, condition, timeoutSec)` | wait (the robot keeps running). A step always has a label (it shows in INIT and the report). An [Action](#action)'s `waitSeconds(seconds)` has none |
| `action(action[, timeout])` | run an [Action](#action) and wait until it is done |
| `alongside(action)` | run an action during the next steps |
| `step(label, AutoStep)` | your own step |
| `parkWithSeconds(s)` | the steps after it get the last s seconds ([Keep time to park](#keep-time-to-park)) |

### waitForGoal

`waitForGoal(label)` waits until the camera sees the goal (from `goal(...)` on a camera; the camera only,
not the odometry backup) and the goal is still. Then it goes on.

- **Still** means in view and moving less than 2 degrees and 2 inches for 1 s (`Tunables.Aim`
  `goalSteadySec`, `goalSteadyDeg`, `goalSteadyIn`). A tipped HIVE can swing.
- **Use it** for a start where the goal is not in view yet, e.g. behind the HIVE: wait for a tip, then
  fire.
- **No time limit.** If the camera never sees the goal, it waits until `parkWithSeconds` takes over (the
  library skips the steps between) or autonomous ends.
- It does not turn the robot.
- An upside-down CELL (a down CELL) is not the goal: it keeps waiting.

### The start spot

The spot in `start(where, xIn, yIn, headingDeg)` is seen from your wall and written for BLUE:

- x = inches right of the center line; y = inches from the center toward the far wall (negative = your
  half).
- RED works like the routes: mirrored, or, on a turned field like BIOBUZZ with `mirrorForRed()` false,
  the same numbers seen from the red wall.
- When both alliances' drivers stand at one wall (not BIOBUZZ, not DECODE), override `alliancesOnOneWall()`
  to return true.
- With odometry, the first step ("Start") sets the pose to the spot (mirrored for RED the same way), and
  at the end the whole pose goes to TeleOp. Without a spot, the pose starts at (0, 0).

### When the steps run

- The library reads the steps in INIT (for the menu, the start position and the park time; nothing runs).
- It reads them again in INIT whenever a menu choice changes. So a `start(...)` inside an `onlyIf`
  follows the menu ([one file, the drivers choose in INIT](../building-autos/06-advanced-autos.md#a-whole-example-one-auto-the-drivers-choose)).
- They run after ▶.
- [`EasyAuto`](../modules/auto.md#easyauto) does everything else: the alliance and delay menu, mirroring for
  RED, the timer, stopping everything, the report.
- When its steps are done, the auto ends at once (even with time left), so the Driver Station moves to its
  TeleOp. TeleOp's INIT shows the report and the alliance (or lets the drivers choose it: X = BLUE,
  B = RED).
- Until `Tunables.Drive.calibrated` is true, it says "AUTONOMOUS IS OFF" and does nothing. In the
  simulator it runs anyway, with a warning.
- `newRobot()` makes the robot from `extends Auto<Robot>`. You never need it. It is `protected` so the
  simulator's [auto builder](../guides/simulator.md#the-auto-builder) (its "Built Auto") can run a plan
  with whichever Robot class your project has.

## Keep time to park

Parking points come at the very end of autonomous, so the steps that park must never run out of time.
`parkWithSeconds(4)` in the list splits the steps in two:

- **The steps before it** (main steps) run while more than 4 s of the 30 s are left. When the 4 s mark
  comes, the step that is running stops at once (a move stops, a wait stops waiting), and the library skips the
  main steps not started yet.
- **The steps after it** (park steps) always run, until autonomous ends.

- The report at the end (Driver Station, and TeleOp's INIT) says which main steps were "cut short (time
  for park)" or "skipped (time for park)".
- **Choose the seconds** from a timed run: how long the park steps take, plus about a second.
- Without `parkWithSeconds`, every step is a main step, and nothing keeps time for the end.
- It is [`EasyAuto`](../guides/easy-autos.md#time-for-parking)'s `thenPark()` and `parkReserveSec()` in one
  call.
- The step that was stopped can leave the robot anywhere, so a straight `driveTo` home can go through a
  field element. With `routeTo`, the park goes around the boxes you list:
  [keep-out boxes and routes](../modules/odometry.md#keep-out-boxes-and-routes).

## Action

An action is a list of steps across several parts. It never blocks (never makes the loop wait). You write
actions in `Actions.java`.

```java
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)
            .then(r.claw::open)
            .waitSeconds(0.3)
            .then(() -> r.lift.goTo("down"));
}
// Controls.java:  g2.y.press(Actions.scoreHigh(robot));    an autonomous:  s.action(Actions.scoreHigh(robot));
```

| Method | What it does |
| --- | --- |
| `named(name)` | makes a new action |
| `then(Runnable...)` | does these at once (several parts together), then goes on |
| `waitSeconds(s)` | waits (the robot keeps running) |
| `waitUntil(what, condition, timeoutSec)` | waits until true. If it times out, the action STOPS and says why |
| `waitUntil(condition, timeoutSec)` | the same, with "the condition" as its words |
| `name()`, `isRunning()`, `status()`, `result()` | its name; whether it runs; its status, like "Score high: step 2 of 5 (lift at the top)"; its result, like "done", "canceled", "stopped: Lift moved by hand" |
| `skipped()` | the parts this run skipped because they are not on the robot ([Parts not on the robot yet](robot-java.md#parts-not-on-the-robot-yet)) |

- **Never blocks:** an action does a little every loop.
- **Parts are claimed:** an action owns the parts it commands while it runs. Another action that commands
  one of them stops it ("taken by ..."). The driver stops it too by moving the part by hand (a
  button, or a stick away from the middle): the driver always wins.
- **Press again to cancel** (a button bound with `press(action)`).
- **A stopped action** (canceled, taken over, timed out) stops what it started, safely: a lift or arm
  holds its height, a turret its angle, the launcher drops shots still waiting, an intake stops. An action that
  finishes keeps its last commands. A failed action in an autonomous says why in the report.
- **A part that is not on the robot yet** (`optional()`): the action skips its commands, and the waits
  right after a step that only used such parts. The action goes on with the other parts.
- Ready-made actions from your parts: [What your robot can do](robot-java.md#what-your-robot-can-do).

## Modes

A `Mode` is a rule a module follows every loop while it is on: `Turret.followMode()`,
`Launcher.speedMode()`, `robot.aimMode()` ([whole-robot aim](parts/aim.md)).

| Method | What it does |
| --- | --- |
| `on()`, `off()`, `toggle()` | switches it |
| `isOn()` | whether it is on |

A button switches it with `g2.rightStickButton.toggle(robot.turret.followMode())`. Modes are for "always" rules,
actions for steps that end.

---

**Look it up:** [`Auto`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Auto.html),
[`Steps`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Steps.html),
[`Action`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Action.html) in
the Javadoc. **The other pages:** [Reference](README.md).

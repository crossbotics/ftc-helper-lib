# Add Pedro paths

Smooth curved paths in autonomous, driven by the Pedro Pathing library.

**You need:**

- A robot with a **mecanum** drive and **checked odometry** ([Add odometry](../add-to-robot/09-odometry.md):
  `Tunables.Odometry.checked = true`).
- A charged battery, clear floor, and a laptop that can join the robot's WiFi (for Pedro's tuner).

## 1. What it is

A **path follower** drives the robot along a line or a curve, and corrects it all the way. **Pedro Pathing**
is a separate library. The library's `pedro` add-on connects it to your robot.

- **Do you need it?** `driveTo` drives a straight line to a point and ends on it. That is enough for many
  autos. Pedro is for curves, for paths with no stop between parts, and for facing a point while the robot
  drives.
- **Paths use the same field numbers as `driveTo`** ([Drive to points](04-drive-to-points.md#field-numbers)): inches, seen from your own drivers' wall.
- **Pedro uses your `drive(...)` and `odometry(...)` lines.** You describe the robot only once.
- **Pedro must measure your robot** with its own tuner: its top speed, how far it slides when it brakes,
  and how hard to correct (step 3). Until then, do not trust a path on the real robot.
- Pedro paths run in **autonomous** steps.

## 2. Add it to the five files

### Your project: add the Pedro add-on once

**Where:** `settings.gradle`, in the project folder (not in TeamCode), under the two `ftc-helper-lib` lines.

```groovy
include ':ftc-helper-lib-pedro'
project(':ftc-helper-lib-pedro').projectDir = new File(rootDir, 'ftc-helper-lib/pedro')
```

**Where:** `TeamCode/build.gradle`, in `dependencies { ... }`, under the `ftc-helper-lib` line.

```groovy
implementation project(':ftc-helper-lib-pedro')
```

Then click **Sync Now**. Wait until it ends with no red errors. Gradle downloads Pedro itself.

### Robot.java: one line, and Pedro's numbers file

**Where:** `Robot.java`, in `define()`, **after** the `odometry(...)` line.

<!-- recipe: pedro-follower -->
```java title="Robot.java"
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // Pedro needs odometry
// Pedro drives the paths with the drive and odometry lines above. PedroConstants holds the
// numbers from Pedro's Foresight tuner (docs/modules/pedro.md#tuning-pedro).
follower(Pedro.follower(PedroConstants.foresight));
```

- Only the `follower(...)` line is new. Keep your own `odometry(...)` line.
- When `Pedro` is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.
- Copy [`PedroConstants.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/pedrobot/PedroConstants.java)
  into TeamCode, next to `Robot.java`. Make its first line (`package ...`) the same as the first line of
  your `Robot.java`. Its numbers are from the simulated example robot. Pedro's tuner replaces them (step 3).

### Tunables.java: nothing new

Pedro's numbers are in `PedroConstants.java`, not in `Tunables.java`. Paths use `Tunables.Drive.maxPower`
and `Tunables.Odometry.checked`, which you already have.

### Controls.java: nothing new

Paths run in autonomous. You need no button.

### Actions.java and autos: make paths, then follow them

**Make each path once**, in your autonomous class, above `steps(...)`.

**Where:** your autonomous class in `autos/`, above `steps(...)`.

<!-- recipe: pedro-line-curve -->
```java title="autos/YourAuto.java"
// Make each path once. A line: from (x1, y1) to (x2, y2). A curve: start, a control point
// that pulls the curve toward it (the robot does not drive through it), end.
static final PedroPath OUT = PedroPath.line(-56, -61, -56, -36).keepHeading(0);
static final PedroPath TO_GOAL = PedroPath.curve(-56, -36, -44, -36, -30, -30).turnTo(0, 45);
```

- `keepHeading(0)`: the robot keeps facing 0 the whole way.
- `turnTo(0, 45)`: it turns from 0 to 45 along the path.

**Face one point the whole way**, for example the goal, so the camera or launcher stays on it:

<!-- recipe: pedro-face-point -->
```java title="autos/YourAuto.java"
// Keep facing one field point the whole way (here x -60, y 60: put your goal's x and y).
static final PedroPath WATCH_GOAL = PedroPath.line(-30, -30, -56, -36).facePoint(-60, 60);
```

**Follow the paths** in the steps.

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

Each `follow` brakes to a stop at its end. Start the next path where the last one ended.

**Two parts with no stop, a slow part, and a hold at the end:**

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

`holdEnd` pushes back when another robot bumps yours while it scores.

**Drive first, then turn** (or turn first):

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-turn-when -->
```java title="autos/YourAuto.java"
// Turn from 0 to 90 only in the last 30% of the line (0.7 to 1): drive first, then turn.
static final PedroPath DRIVE_THEN_TURN = PedroPath.line(-56, -61, -30, -30).turnTo(0, 90, 0.7, 1);
```

The two extra numbers say on which stretch it turns: 0 = the start of the part, 1 = its end. `0, 0.3`
turns first, then drives.

**Start a part along the path**, while the robot keeps driving:

**Where:** a file in `autos/`, inside `steps(...)`. When `PathAt` is red, press Alt+Enter.

<!-- recipe: pedro-along -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 // PathAt: start things ALONG the path, once each, while the robot keeps driving.
 .follow("To the goal", Paths.IN_ONE_GO,
         PathAt.part(2, "Intake on", robot.intake::in),            // where part 2 starts
         PathAt.inchesLeft(6, "Intake off", robot.intake::stop))   // 6 in before the end
 .waitSeconds("Score (the robot holds still)", 0.5);
```

Every way to start a part along the path:

| `PathAt` | Starts it |
| --- | --- |
| `PathAt.fraction(0.5, name, command)` | halfway along the whole path |
| `PathAt.inchesLeft(12, name, command)` | 12 in before the end |
| `PathAt.inchesDone(12, name, command)` | 12 in after the start |
| `PathAt.part(2, name, command)` | where part 2 starts |
| `PathAt.near(x, y, inches, name, command)` | when the robot comes this close to a point |

In place of a name and a command, `PathAt` can take an action from your `Actions.java`:
`PathAt.inchesLeft(12, Actions.scoreHigh(robot))`. `driveTo` takes `PathAt` too. More:
[PathAt](../reference/parts/path-follower.md#pathat).

Try every path in the simulator first. The auto builder draws Pedro paths on the field map, and runs them
([The auto builder: odometry and Pedro](07-auto-builder-odometry-and-pedro.md)).

## 3. Find its numbers

Pedro's **Foresight tuner** measures your robot. It drives the robot by itself, **at full power**. The
PRACTICE limit does not work here. Keep clear of the robot, and keep a hand on STOP.

1. Add Pedro's tuning library and copy its tuner files. The steps:
   [lesson 8, section 7](../tutorials/start/08-paths-with-pedro.md#7-tune-pedro-on-the-robot), steps 1 to 3.
2. Send the code to the robot. Connect the laptop to the robot's WiFi, and open
   `http://192.168.43.1:10158` in a browser.
3. Choose **Foresight Procedure** and follow the page.
4. At the end, open the **Java** tab. In `PedroConstants.java`, replace every `c.something.set(...);` line
   with the lines from the Java tab. Keep the first and last lines of the file.
5. Send the code again.

**Before an event, take the tuner out.** Its web page is not allowed at events (rule R704.D). Comment out
the `tuning` line in `build.dependencies.gradle`, and move `Tuning.java` and the `procedures` folder out of
TeamCode. Your paths still work: they need only `PedroConstants.java`.

## 4. Check it, then tune it

**Check it:**

1. Make a short path: one straight `PedroPath.line(...)` of about 24 in, in open floor.
2. Set `Tunables.Drive.maxPower` low (0.6) for the first runs.
3. Put the robot on the start spot, facing the start heading. Press INIT, then ▶. Keep a hand on STOP.
4. The robot must drive the line and stop near the end. Pedro calls a path done within about 2.5 in and
   11.25 degrees.
5. Then try your real path, still in PRACTICE. Then in COMPETITION. Set `maxPower` back to `DEFAULT`.

**If it goes the wrong way, press STOP.** Then check the table below.

**Tune it:** run Pedro's tuner again after a change to the robot's weight, wheels or motors. To make "done"
tighter, see [When is a path done?](../modules/pedro.md#when-is-a-path-done). The `driveTo` steps in the
same auto use the [Move Tuner](../tuning/10-move-tuner.md) numbers. To try `maxPower` without a new build,
use [Live tuning](../tuning/16-live-tuning.md).

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| `Pedro` or `PedroPath` is red in Android Studio | Add the Pedro add-on (step 2), then Sync |
| INIT stops: `follower(...) needs an odometry(...) line too` | The `follower` line needs the `odometry` line above it |
| INIT stops: `Pedro.follower needs a mecanum drive(...) line` | Pedro needs mecanum. A tank robot uses `turnTo` and `move` |
| Every `follow` says `skipped` in the report | `Tunables.Odometry.checked` is `false`. Do [Add odometry](../add-to-robot/09-odometry.md), step 3 |
| It overshoots and comes back, or shakes | `PedroConstants.java` still has another robot's numbers. Do step 3 |
| It drives the wrong way at once | STOP. Run the Odometry Tuner again, and check the motor directions |
| It stops a little short of the end | Normal: Pedro is done within 2.5 in |
| The report says `STALLED` | The robot was stuck, against a wall or a robot. The next steps still run |
| The report says `timed out` | The path took more than 10 s. Make it shorter, or give it more time: `.follow("To the goal", TO_GOAL, 15)` |
| The tuner's page does not open | The laptop must be on the robot's WiFi. Check the address |

More: [lesson 8, If something goes wrong](../tutorials/start/08-paths-with-pedro.md#if-something-goes-wrong).

## Done when

- `PedroConstants.java` has your robot's numbers from the Foresight tuner.
- A 24 in line ends within about 2.5 in of its end point.
- Your real path runs in COMPETITION, and the report shows no `skipped` or `STALLED`.

---

**Next:** [The auto builder: steps](06-auto-builder-steps.md): build an auto on a picture of the field.

**Look it up:** [PathFollower and PathAt](../reference/parts/path-follower.md): every command.
[Pedro module](../modules/pedro.md): paths, holds and Pedro's numbers.

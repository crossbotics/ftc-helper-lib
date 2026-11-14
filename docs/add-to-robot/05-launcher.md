# Add a launcher

A flywheel that shoots game pieces, and the part that feeds it one piece at a time.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: a flywheel motor with an encoder, and a feeder (a servo arm, a CR servo wheel or a motor).
  In the robot configuration, call them `launcher` and `indexer`.
- Safety glasses, and nobody in front of the launcher when the wheel spins.

## 1. What it is

A **launcher** spins a wheel to a speed, and feeds a piece only when the wheel is at that speed.

- **The speed is in RPM** (turns a minute). The library needs the motor's catalog id to know its top speed.
- **The feeder pushes one piece at a time** into the wheel. The launcher tells it when.
- **It never feeds into a slow wheel.** It waits until the speed is within `readyBandRpm`. When the wheel
  is not ready within 3 s, it gives up the shot and says why: `Launcher gave up: flywheel`.
- **It spins up at ▶** when it has a feeder of its own, so the first shot does not wait.

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class (`public Launcher launcher;`). The line goes
in `define()`, after the drive.

<!-- recipe: launcher -->
```java title="Robot.java"
// A flywheel (in RPM) and the indexer that feeds it one piece at a time.
// "launcher", "indexer" = names in the robot configuration. Numbers: Tunables.Launcher, .Indexer.
// The motor's catalog id is in the brackets (needed). The indexer's .part(...) is optional, recommended.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
```

- The motor's catalog id (`"gobilda-5203-6000"`) is needed. The number is on the motor's label
  ([parts catalog](../guides/parts-catalog.md)).
- `.feeder(...)` is the part that pushes the pieces in. `Indexer.servo` is a servo arm. Use
  `Indexer.crServo(name)` for a CR servo wheel, or `Indexer.motor(name)` for a motor
  ([Indexer](../reference/parts/indexer.md)).
- **Two arms** that pinch the piece from both sides: `Indexer.servo("feeder_left").alsoServoReversed("feeder_right")`.
  Both arms use the same `rest` and `push`.
- **A wheel and an arm:** a CR servo wheel brings each piece up, and the arm pushes it in. Write the arm as
  the feeder, and add `.feedWheel(Indexer.crServo("feeder_wheel"))` ([Feed wheel](../reference/parts/launcher.md#feed-wheel)).
- **A motor that is not in the catalog:** write `Motors.custom("my-motor", 28, 5800)` in place of the id
  (counts per turn, then top RPM).
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

**A feed stop, and an intake that pushes the pieces in.** Add these only when your robot has them:

**Where:** `Robot.java`, in `define()`. The intake line goes **before** the launcher line
([Add an intake](04-intake.md)).

```java title="Robot.java"
intake = add(Intake.motor("intake"));   // first: the launcher's pushedBy(...) needs it
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feedStop("feed_stop", "gobilda-2000-speed")   // a servo that holds the pieces back
        .pushedBy(intake));                            // the intake pushes each piece into the wheel
```

- `.feedStop(servo, part)`: a servo that holds the pieces back until a shot is allowed. The servo's
  catalog id is optional. With it, the Feed Stop Finder works out how long the stop takes to open.
- `.pushedBy(intake)`: for each piece, the intake runs in for a short time (`assistSec`, 0.25 s), even when
  the driver has it off.
- **A hood** changes the shot angle. Add `.hood(Hood.servo("hood"))` to the launcher line
  ([Hood](../reference/parts/hood.md)).

### Tunables.java: its numbers

**Where:** `Tunables.java`, two new sections. A section's name is the device's name in CamelCase:
`"launcher"` reads `Tunables.Launcher`, and `"indexer"` reads `Tunables.Indexer`.

<!-- recipe: tunables-launcher -->
```java title="Tunables.java"
public static class Launcher {                                  // "launcher" in the configuration
    public static double speedRpm = DEFAULT;                    // shot speed without the table (60% of top)
    public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
    public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
    public static double idleRpm = 1500;                        // spins between shots: quicker first shot (0)
    public static double shotIntervalSec = DEFAULT;             // least time between two pieces (0.25)
    // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
    // Then write the Finder's numbers in their place.
    public static double kP = DEFAULT;                          // push per speed error (kF / 10)
    public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
    public static double kD = DEFAULT;                          // brake changes (0)
    public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    // {inches to the goal, RPM}, blended between rows. Example rows: write the Shot Tuner's instead.
    public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
}
```

<!-- recipe: tunables-indexer -->
```java title="Tunables.java"
public static class Indexer {                                   // "indexer" in the configuration
    public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
    public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
    public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
    public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
}
```

- `DEFAULT` means "use the library's starting value". The speed gains (`kP` to `kF`) start from the motor
  in `Launcher.flywheel(...)`. INIT says `Starting gains (worked out, not tuned)` until you write your own.
- `speedRpm` is the shot speed. Its starting value is 60% of the motor's top speed.
- `speedByDistance` is the **shot table**: a speed for each distance to the goal. The launcher uses it only
  with a camera or odometry (`.speedFromDistance(camera)`, [Add a camera](07-camera.md)). Until then, it
  uses `speedRpm`.
- `rest` and `push` are `MEASURE`: INIT stops until you write them (step 3).
- **With a feed stop:** add `feedStopHold`, `feedStopRelease`, `feedStopMoveSec` and `feedStopOpenSec` to
  `Tunables.Launcher`. The Feed Stop Finder gives all four.

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-launcher -->
```java title="Controls.java"
robot.launcher.spinOn(g2.lb);
robot.launcher.fireOn(g2.rb);
robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
robot.launcher.tableOn(g2.dpadRight);
```

| Button | What it does |
| --- | --- |
| LB | spins up, or spins down |
| RB | a tap fires one piece. More taps fire more pieces. Held, it keeps firing |
| D-pad up / down | the speed + / - `stepRpm`. With a shot table, the speed trim + / - 1% |
| D-pad right | the next shot table (only with two tables or more) |

With one gamepad (`oneDriver = true`), these `g2` lines work on gamepad 1
([One gamepad, and a second one when you want it](../controls/01-how-buttons-work.md#one-gamepad-and-a-second-one-when-you-want-it)).

**How it fires:**

- **A tap of RB is CAREFUL:** each piece waits until the wheel is at speed, and `shotIntervalSec` after the
  last piece.
- **RB held is QUICK:** the first piece waits the same way. The next ones go a little sooner. Let go, and no
  more pieces go.
- **Many quick taps** line up shots, at most `maxFiringQueue` (4). More taps do nothing. A nervous driver
  who taps 10 times does not get 10 shots.
- **SPRAY** does not wait for the speed. It is off until you set `sprayEnabled = true`. Then tap RB, press
  it again and hold it: the launcher empties the robot fast. In the last seconds of a period, the launcher
  picks SPRAY by itself.
- **It spins up at ▶** when it has a feeder or a feed stop. A launcher that only the intake feeds
  (`pushedBy` only) waits for LB. Change it with `spinAtStart = true` or `false` in `Tunables.Launcher`.
- **Other buttons:** `robot.launcher.fireOn(g2.y);` puts the fire job on Y. Do not give the fire button a
  double tap or a long press of your own. With one, a quick press waits to see the gesture, and does not
  fire at once.

Every setting: [Firing: shot modes](../reference/parts/launcher.md#firing-shot-modes-feed-stop-piece-path).

### Actions.java and autos: use it in steps

When the intake must run while the launcher fires, an action puts them together.

**Where:** `Actions.java`, one method per action.

<!-- recipe: action-fire-with-intake -->
```java title="Actions.java"
/** Fires 3 pieces with the intake running, to push each one in. Then the intake stops. */
public static Action fireWithIntake(Robot r) {
    return Action.named("Fire with intake")
            .then(r.intake::in, () -> r.launcher.fire(3))
            .waitUntil("the shots", () -> !r.launcher.isShooting(), 9.0)
            .then(r.intake::stop);
}
```

**Where:** `Controls.java`, inside `buttons(...)`. A button runs the action. The second line is a
ready-made action: it needs nothing in `Actions.java`.

<!-- recipe: action-fire-button -->
```java title="Controls.java"
g2.y.press(Actions.fireWithIntake(robot)).says("fire 3 with the intake");
g2.x.press(robot.launcher.fireAllAction()).says("fire all");   // a ready-made action
```

**Where:** an autonomous in `autos/`. The wheel spins up while the robot drives to the shooting spot.

<!-- recipe: action-fire-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .alongside(robot.launcher.readyAction(3))          // spin up while it drives
 .forward("To the shooting spot", 24, 0.5)
 .action(Actions.fireWithIntake(robot), 9)          // waits until it is done (at most 9 s)
 .right("Park", 12, 0.5);
```

**With no camera:** the launcher has no distance, and shoots at `speedRpm`. An auto that shoots from two
spots gives each spot its own speed: `.action(robot.launcher.fireAllAction(2250))` fires every piece at
2250 RPM, then goes back to `speedRpm`.

## 3. Find its numbers

INIT stops on `MEASURE`. The finders still run, because they do not read `Tunables.java`.

1. **Servo Finder** ([how](../tuning/03-servo-finder.md)): find the indexer's two positions. `rest` is out
   of the way. `push` has pushed a piece into the wheel. Put them in `Tunables.Indexer`.
2. **Flywheel Finder** ([how](../tuning/06-flywheel-finder.md)): it finds the speed gains and the
   recovery time after a shot. Until then, the wheel runs on starting gains from the motor, and INIT says
   so: `Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, ...`. Put the gains line in `Tunables.Launcher`. Make `shotIntervalSec` a little
   longer than the recovery time.
3. **Feed Stop Finder** ([how](../tuning/07-feed-stop-finder.md)), only with a feed stop: the hold and
   release positions, and the firing times.
4. **Hood Finder** ([how](../tuning/04-hood-finder.md)), only with a hood: the hood's two stops.
5. **Shot Tuner** ([how](../tuning/13-shot-tuner.md)), after the camera or the odometry: the shot table.

## 4. Check it, then tune it

**Check it:**

1. Put on safety glasses. Keep everybody out of the front of the launcher.
2. Press INIT. Check that no warning names the launcher or the indexer. Press ▶.
3. Press LB to spin up. Read the speed on the Driver Station. It must reach the speed and stay there.
4. Press LB to stop the wheel. Load the pieces by hand. Keep your hands away from the wheel once it spins.
5. Spin up, then tap RB. The feeder must push **one** piece for each tap.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs:

1. **speedRpm:** change it until the shots land where you want, from your usual shooting spot.
2. **The feeder:** when it pushes two pieces at once, make `pushSec` shorter. When the piece does not reach
   the wheel, move `push` further.

Then put the tuned numbers in `Tunables.java`, in place of `DEFAULT`.

## More for your launcher

Add these when your robot needs them.

### Two kinds of pieces: two shot tables

Two pieces that fly differently need two speeds for the same distance. BIOBUZZ has POLLEN (a 2.8 in ball)
and NECTAR (a 3.6 in ball). Each `speedByDistance...` line in `Tunables.Launcher` is a table, and the part
after `speedByDistance` is its name:

**Where:** `Tunables.java`, in `Tunables.Launcher`, in place of the `speedByDistance` line.

```java title="Tunables.java"
public static double[][] speedByDistancePollen = {{36, 1970}, {48, 2250}, {60, 2520}, {72, 2790}};
public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}, {96, 3600}};
```

- `.startTable("pollen")` on the launcher's line in `Robot.java` picks the table at ▶.
- **Change the table:** D-pad right picks the next table (`tableOn`, above). Or one button for each piece:
  `g2.x.press(() -> robot.launcher.useTable("pollen"));`. In an action:
  `.then(() -> r.launcher.useTable("nectar"))`.
- The Driver Station shows the table: `Shot table : POLLEN, 48 in (camera)`.
- The [Shot Tuner](../tuning/13-shot-tuner.md) fills each table: press Y until `> Table`, and pick it with
  the D-pad.

### Speed trim and a low battery

In some matches every shot lands a little short or long (a worn wheel, soft balls). With a shot table,
D-pad up and down make every shot 1% faster or slower. Hold it for more. Double-tap D-pad down to go back
to 0. The Driver Station shows `Speed trim : +3%  READY`.

- The trim starts at 0 at every INIT. At INIT, the Driver Station offers the last trim: double-tap D-pad up
  after ▶ to use it.
- **The same trim match after match:** the table is off. Multiply each speed in the table by the trim
  (+3%: x 1.03), put the new line in `Tunables.java`, and go back to no trim.
- **A low battery** makes the wheel slow to get back to speed after a shot. Under 11.5 V the Driver Station
  warns. Swap the battery. Battery compensation is off, and not yet tested on a robot
  ([Speed trim and battery compensation](../reference/parts/launcher.md#speed-trim-and-battery-compensation)).

### A hood: the shot angle

A **hood** is the curved plate over the flywheel. It sets how steep the piece leaves. Near the goal, a
steep shot drops in. Far away, a flatter shot goes further. With a hood, each row of a shot table has a
third number: `{48, 2550, 11}` (inches, RPM, hood angle).

**Where:** `Robot.java`, the launcher's line. The `.hood(...)` line is new. Add `import com.crossbotics.ftchelper.robot.Hood;`.

<!-- recipe: robot-hood -->
```java title="Robot.java"
// The hood goes on the launcher; its numbers are in Tunables.Hood.
// .part(...): which servos they are (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .hood(Hood.servo("hood").part("gobilda-2000-torque"))
        .speedFromDistance(camera));
```

**Where:** `Tunables.java`, a new section, `Tunables.Hood`.

<!-- recipe: tunables-hood -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // posA and posB: the servo positions a hair off the hood's two hard stops. Never past them.
    public static double posA = MEASURE;                        // servo position at stop A (Hood Finder)
    public static double posB = MEASURE;                        // servo position at stop B (Hood Finder)
    // Optional: the hood's angle at each stop (any scale, e.g. 0 = steep end, 30 = flat end).
    public static double angleA = MEASURE;                      // the angle at stop A (an angle app)
    public static double angleB = MEASURE;                      // the angle at stop B (an angle app)
    public static double travelSec = DEFAULT;                   // stop to stop, seconds (0.5, or the servo's)
    public static double startAngle = DEFAULT;                  // where it goes at ▶ (angle A)
}
```

- **`posA` and `posB` are the hood's two hard stops.** The hood never goes past them, because a servo that
  pushes on a stop gets hot and breaks. The [Hood Finder](../tuning/04-hood-finder.md) finds them.
- **The launcher waits for the hood.** A servo cannot say where it is, so the launcher waits `travelSec`
  for a stop-to-stop move (`Launcher waits for: hood`). A piece fired while the hood moves flies wrong.
- **By hand:** gamepad 2's left stick moves the hood. With a shot table, it trims the hood angle. Use the
  speed trim for shots that are short or long. Use the hood trim for shots that arrive at the wrong slope.
- The [Shot Tuner](../tuning/13-shot-tuner.md) finds the speed and the hood angle together: press Y until
  `> Hood`.

Every setting, a linkage and a motor hood: [Hood](../reference/parts/hood.md).

### Know which piece fires next

Two color sensors let the robot count what it holds and know the next piece: one where the pieces come in,
and one at the "ready to fire" place. Then the next piece picks the shot table, and the drivers do not switch
tables by hand.

**Where:** `Robot.java`, in `define()`, **before** the launcher line (the launcher uses `pieces`). The
field goes at the top of the class (`public Pieces pieces;`).

```java title="Robot.java"
pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
        .comingIn(PieceSensor.color("intake_color"))
        .readyToFire(PieceSensor.color("ready_color"))
        .max(4));
```

Then add these two lines to the launcher's line:

```java title="Robot.java"
.tableFrom(pieces.nextKind())                         // the next piece picks the table
.tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR")      // both NECTAR colors use "nectar"
```

- The [Piece Tuner](../tuning/14-piece-tuner.md) measures each kind's color. Until then, a piece reads
  `UNKNOWN`, and an `UNKNOWN` piece keeps the table in use.
- The count can be wrong (two pieces that touch, a piece that bounces out). The drivers, not the count, keep
  the robot to its piece limit.

Every sensor and command: [Pieces](../reference/parts/pieces.md).

### Try it in the simulator

The goBILDA starter bot has a measured simulator robot.

**Where:** the Terminal, in the `ftc-helper-lib` folder.

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

Open http://localhost:8780, choose "goBILDA Bot TeleOp", press INIT, then ▶. Tap LB and watch the speed go
up, then tap RB. Tap RB before the wheel is at speed, and the piece waits. The turret and camera robots:
[Add a camera: Try it in the simulator](07-camera.md#try-it-in-the-simulator).

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Indexer.rest = MEASURE` | Not measured yet. Do step 3 |
| INIT stops: `speedRpm` is above the motor's top speed | The number is too high for that motor, or the catalog id is wrong |
| `Launcher gave up: flywheel` | The wheel never got to its speed. Check the encoder cable, run the Flywheel Finder, or lower `speedRpm` |
| INIT: `Launcher fire one; hold: keep firing: no button (G2 RB is your "..." in Controls.java)` | One of your own jobs is on the launcher's button. Move your job to a free button |
| Two pieces go in at once | The feeder runs too long. Lower `pushSec` |
| The wheel spins the wrong way | Add `.reversed()` to the launcher in `Robot.java` |
| `Launcher gave up: turret aimed` | The goal is not in view, or out of the turret's range, or `tolerance` is too small |
| `Launcher: no shot table "nectr"` | No `speedByDistance...` line has that name. The warning lists the names there |
| Shots short (or long) in every match | Trim with the D-pad. If you need the same trim again and again, tune the table again |
| INIT stops: `Tunables.Hood has no posA` | Run the Hood Finder, and put its line in `Tunables.Hood` |
| `Launcher waits for: hood`, and it never fires | `travelSec` is very long, or a motor hood cannot reach the angle |
| INIT stops: `the row at 48 in has hood 30, outside the hood's range` | A table row asks past a stop. Fix the row, or the stops |
| `battery 11.2 V: low` | Swap the battery. A low battery makes the wheel slow to get back to speed |

## Done when

- INIT shows no warning for the launcher or the indexer.
- LB spins the wheel up, and the speed holds.
- Each tap of RB fires one piece.
- The gains line from the Flywheel Finder is in `Tunables.Launcher`.

---

**Next:** [Add a turret](06-turret.md), or [Add a camera](07-camera.md) when your robot has no turret.

**Look it up:** [Launcher](../reference/parts/launcher.md): every setting, number and command.

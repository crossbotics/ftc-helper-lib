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

## 3. Find its numbers

INIT stops on `MEASURE`. The finders still run, because they do not read `Tunables.java`.

1. **Servo Finder** ([how](../tuning/03-servo-finder.md)): find the indexer's two positions. `rest` is out
   of the way. `push` has pushed a piece into the wheel. Put them in `Tunables.Indexer`.
2. **Flywheel Finder** ([how](../tuning/06-flywheel-finder.md)): it finds the speed gains and the
   recovery time after a shot. Put the gains line in `Tunables.Launcher`. Make `shotIntervalSec` a little
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

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Indexer.rest = MEASURE` | Not measured yet. Do step 3 |
| INIT stops: `speedRpm` is above the motor's top speed | The number is too high for that motor, or the catalog id is wrong |
| `Launcher gave up: flywheel` | The wheel never got to its speed. Check the encoder cable, run the Flywheel Finder, or lower `speedRpm` |
| INIT: `Launcher fire one; hold: keep firing: no button (G2 RB is your "..." in Controls.java)` | One of your own jobs is on the launcher's button. Move your job to a free button |
| Two pieces go in at once | The feeder runs too long. Lower `pushSec` |
| The wheel spins the wrong way | Add `.reversed()` to the launcher in `Robot.java` |
| `battery 11.2 V: low` | Swap the battery. A low battery makes the wheel slow to get back to speed |

## Done when

- INIT shows no warning for the launcher or the indexer.
- LB spins the wheel up, and the speed holds.
- Each tap of RB fires one piece.
- The gains line from the Flywheel Finder is in `Tunables.Launcher`.

---

**Next:** [Add a turret](06-turret.md), or [Add a camera](07-camera.md) when your robot has no turret.

**Look it up:** [Launcher](../reference/parts/launcher.md): every setting, number and command.

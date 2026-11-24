# Aiming and shooting

The robot does these jobs for the drivers, so they can focus on driving and firing. They all use one
idea: **the camera first, the odometry as a backup** ([Aim](../reference/parts/aim.md)). Odometry is how
the robot tracks its own position on the field.

**The questions on this page:**

- [How do I make the whole robot aim at the goal (no turret)?](#how-do-i-make-the-whole-robot-aim-at-the-goal-no-turret)
- [How do I make the gamepad buzz when the aim is on target?](#how-do-i-make-the-gamepad-buzz-when-the-aim-is-on-target)
- [How do I keep aiming when the camera loses the goal?](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)
- [How do I aim with the odometry only (no camera)?](#how-do-i-aim-with-the-odometry-only-no-camera)
- [How do I make the camera work in any venue's light?](#how-do-i-make-the-camera-work-in-any-venues-light)
- [How do I find game pieces with a HuskyLens?](#how-do-i-find-game-pieces-with-a-huskylens)
- [How do I shoot while the robot is driving?](#how-do-i-shoot-while-the-robot-is-driving)
- [How do I use one shot table per game piece?](#how-do-i-use-one-shot-table-per-game-piece)
- [Every shot is a bit short today. How do I trim the speed (a low battery)?](#every-shot-is-a-bit-short-today-how-do-i-trim-the-speed-a-low-battery)
- [How do I add a hood to my launcher?](#how-do-i-add-a-hood-to-my-launcher)

## How do I make the whole robot aim at the goal (no turret)?

**Where:** `Controls.java`, inside `buttons(...)` (and `Tunables.java` for the numbers, below). The
robot needs a camera with a [`goal(...)`](../add-to-robot/07-camera.md)
(or odometry with a goal point). Do not use it with a turret that follows the goal. Turret-following and
whole-robot aim are separate modes, so INIT stops with a message if both are there.

<!-- recipe: aim-whole-robot -->
```java title="Controls.java"
// Hold RB: the robot turns to face the goal by itself; the driver keeps forward and sideways. (1)!
g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off).says("hold: aim the robot at the goal");
// Or a toggle. Careful: while it is on, the robot turns by itself, even after you let go. (2)!
// g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
```

1. When you let go, the aim is off, and the driver turns again. To turn by hand while you hold RB, move the turn stick. Do not use it with a turret that follows the goal: these are separate modes (INIT stops with a message).
2. A press switches it on, and the next press switches it off. Two buzzes = on.

**Why:** the driver drives, and the robot keeps itself pointed at the goal. Start with hold-to-aim. When
you let go, the robot stops turning by itself, so it never surprises anyone. The stick always wins, so
the driver can take over at once. The toggle (commented out) keeps aiming after you let go.
**More:** [Add a camera: aim the whole robot](../add-to-robot/07-camera.md#controlsjava-its-buttons).

## How do I make the gamepad buzz when the aim is on target?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: aim-rumble -->
```java title="Controls.java"
// Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
// It works for a turret that follows the goal and for whole-robot aim.
g2.rumbleWhenAimed();
```

**Why:** the driver who fires watches the field, not the screen. The buzz tells the driver "now".
**More:** [Add a camera: feel the aim](../add-to-robot/07-camera.md#controlsjava-its-buttons).

## How do I keep aiming when the camera loses the goal?

**Where:** `Robot.java`, inside `define()`, and the goal's place in `Tunables.java`.

<!-- recipe: aim-odometry-backup -->
```java title="Robot.java"
// With an odometry line and Tunables.Aim goalX, goalY, the turret and the speed keep
// working when the camera loses the goal: from where the robot is on the field.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera)
        .startTable("pollen"));                  // the table to start with
```

<!-- recipe: tunables-aim -->
```java title="Tunables.java"
/** Aiming (every line is optional; leave out what you do not use). */
public static class Aim {                                       // a fixed name: always Aim
    // The goal on the field, in inches, seen from your drivers' wall (the same numbers as driveTo). (1)!
    public static double goalX = 13;                            // inches right of center (BIOBUZZ)
    public static double goalY = -13;                           // inches from center, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ: turned, not mirrored (true)
    public static double turnKp = DEFAULT;                      // whole-robot aim: push per degree off (0.02)
    public static double turnKd = DEFAULT;                      // brake near the goal (0.002)
    public static double maxTurnPower = DEFAULT;                // the most it turns by itself (0.5)
    public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
    public static double aimDriveSpeed = DEFAULT;               // aim mode on: forward and strafe at this share (0.5)
    public static double rumbleDeg = DEFAULT;                   // rumbleWhenAimed(): buzz within this (4)
    public static boolean aimOnFire = false;                    // no turret: a fire press turns to the goal first (2)!
}
```

1. With odometry, the aim goes on when the camera loses the goal. In BIOBUZZ, the goal is your HIVE's CELL that is up at the start, 13 in right of the center line and 13 in toward you.
2. Quick turn on fire, for a robot with no turret: with aim mode off and the goal in view, RB turns the robot to the goal, then fires (at most 0.5 s later: `aimOnFireSec`). It is off unless you set it to `true`, because it turns the robot.

**Why:** a robot or a game piece can block a tag, but the odometry still knows where the goal is. The
Driver Station says which one aims (`following: camera` or `following: odometry`). The backup needs the
pose (the robot's position and heading) from autonomous, or from
[set pose here](11-odometry.md#how-do-i-set-the-pose-when-autonomous-did-not-run). Without it, the backup stays off,
and the Driver Station says so. You write `goalX`, `goalY` from your own drivers' wall, like every field
number in your code. Only the simulator's map and its scenario checks use the red wall. `mirrorForRed`
is ONE setting for the whole robot (the routes, the start and reset spots, this goal point). The library
takes the first one it finds of `Tunables.Aim.mirrorForRed`, `Tunables.Odometry.mirrorForRed` and your
autonomous's `mirrorForRed()`. If it finds none, it uses `true` (a mirrored field). BIOBUZZ's turned
field needs `false`: write it once, here. INIT shows the alliance and this choice. The numbers in
`Tunables.Aim` are in the tuning menu too, so copy tuned values back into `Tunables.java`.
**More:** [Add a camera: when the camera loses the goal](../add-to-robot/07-camera.md#when-the-camera-loses-the-goal-the-odometry), [Add odometry](../add-to-robot/09-odometry.md).

## How do I aim with the odometry only (no camera)?

**Where:** `Robot.java`, inside `define()` (the odometry, turret and launcher lines), and a section `Aim`
in `Tunables.java`.

<!-- recipe: aim-odometry-only -->
```java title="Robot.java"
// No camera: the odometry says where the robot is, Tunables.Aim where the goal is. The
// turret follows that direction and the launcher's speed comes from that distance.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).followsOdometry());
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromOdometry()
        .requireReady("turret aimed", () -> turret.isAimed()));
```

<!-- recipe: tunables-aim-odometry-only -->
```java title="Tunables.java"
public static class Aim {                                       // a fixed name: always Aim
    // Where the goal is, seen from YOUR drivers' wall (inches; the same numbers as driveTo).
    // BIOBUZZ: your HIVE's CELL that is up at the start.
    public static double goalX = 13;                            // inches right of center (BIOBUZZ)
    public static double goalY = -13;                           // inches from center, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ's field is turned, not mirrored
}
```

**Why:** you have no camera to buy, mount or tune. But the aim is only as good as the pose. It needs the
start spot from autonomous (or [set pose here](11-odometry.md#how-do-i-set-the-pose-when-autonomous-did-not-run)). A
bump that slips the wheels moves the aim off a little. When you have a camera too, use
[the camera first, the odometry as backup](#how-do-i-keep-aiming-when-the-camera-loses-the-goal).
The library takes it that the turret turns about the robot's center. Whole-robot aim (no turret) works
from the odometry in the same way.
**More:** [Aim](../reference/parts/aim.md), [Turret](../reference/parts/turret.md).

## How do I make the camera work in any venue's light?

**Where:** `Robot.java`, inside `define()`: the camera line.

<!-- recipe: camera-lighting -->
```java title="Robot.java"
// In INIT the Limelight tries these pipelines (your usual one FIRST) and keeps the best one. (1)!
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
// A webcam: Camera.webcam("Webcam 1").autoExposure().goal(...) tries the camera's own
// exposure, then 3, 6 and 12 ms.
```

1. Each venue's lights are different. Set up each pipeline on the Limelight's web page for one kind of light. The Limelight keeps the pipeline that sees the most AprilTags, and the Driver Station says which one.

**Why:** bright stage lights or a dark corner can make a camera miss the AprilTags it saw in your shop.
The test runs in INIT (nothing moves) and takes about a second per setting. The Driver Station says
which setting it kept. Point the robot at the tags in INIT. Its numbers are optional. In
`Tunables.Limelight`, `autoLighting = false` skips the test and uses `pipeline`.
**For a camera tilted up:** add its tilt to the same section: `public static double pitchDeg = 35;`
(degrees up, measured on the robot). Then a goal off to the side reads the right angle, and distances
are along the floor.
**More:** [Vision: venue lighting](../modules/vision.md#venue-lighting), [CameraTilt](../modules/vision.md#cameratilt), [Camera](../reference/parts/camera.md).

## How do I find game pieces with a HuskyLens?

**Where:** `Robot.java`, inside `define()`. First teach the camera, on the camera itself: dial to Color
Recognition, turn on Learn Multiple, point the `+` at a piece, and press the learning button.

<!-- recipe: huskylens -->
```java title="Robot.java"
// At the top of the class: public Camera pieceCamera;   Colors you taught it: ID1, ID2. (1)!
pieceCamera = add(Camera.huskyLens("huskylens").colors().label(1, "POLLEN").label(2, "NECTAR"));
// Or AprilTags: list which real tag each taught number is, and add goal(...). (2)!
// camera = add(Camera.huskyLens("huskylens").tag(1, 34).tag(2, 35).goal("BLUE SCORING", "RED SCORING"));
```

1. A HuskyLens does one job at a time: colors or tags, not both.
2. It says the number you taught it, never the tag's real number.

**Why:** the camera finds the piece itself, so the hub does no picture work. Then
`pieceCamera.target("POLLEN")` gives the bearing to the nearest box, or null. In `Tunables`, set `hfovDeg`
(the camera's side-to-side view angle: measure it). For a rough distance, also set `sizeIn` (the longest
side of the piece it sees). Nobody has tested it on a real robot yet.
**More:** [HuskyLensCamera](../modules/vision.md#huskylenscamera), [Camera](../reference/parts/camera.md).

## How do I shoot while the robot is driving?

**Where:** `Tunables.java` (the numbers, in your `Aim` section), and a button in `Controls.java`
(inside `buttons(...)`) to switch it on and off. The robot needs [odometry](../reference/parts/odometry.md),
because the odometry gives the robot's speed. **It stays off until you tune it on the real robot**
(`shootOnMove = false`). Measure `flightSec` with a slow-motion video, and test shots while driving.
Only then write `true`.

<!-- recipe: tunables-moving-shot -->
```java title="Tunables.java"
// ADD these lines to your Tunables.Aim section (keep goalX, goalY and the others).
public static boolean shootOnMove = false;                  // true only after testing on the real robot
public static double flightSec = MEASURE;                   // seconds in the air (slow-motion video) (1)!
public static double flightSecPerIn = DEFAULT;              // longer shots fly longer: per inch (0)
public static double maxLeadIn = DEFAULT;                   // never lead more than this, inches (24)
// Where the shooter (a turret's pivot) and a camera on the body are from the robot's center. (2)!
public static double shooterForwardIn = MEASURE;            // inches forward of the center (from the edges)
public static double shooterLeftIn = MEASURE;               // inches left of the center (from the edges)
public static double cameraForwardIn = MEASURE;             // inches forward of the center (from the edges)
public static double cameraLeftIn = MEASURE;                // inches left of the center (from the edges)
```

1. Film a shot in slow motion. Count the frames from when the piece leaves the launcher to when it reaches the goal. Divide by the video's frames per second.
2. In inches: forward (+) and left (+). Measure from the robot's edges, not its middle: forward = length / 2 - (from the front edge), left = width / 2 - (from the left edge). A camera on the turret sits at the turret's pivot.

Then a button, in `Controls.java` inside `buttons(...)`:

<!-- recipe: moving-shot-button -->
```java title="Controls.java"
// Shooting on the move on/off (on from ▶ when Tunables.Aim.shootOnMove = true): if
// the shots go wide while driving, switch it off and shoot standing still.
g2.a.toggle(robot.aim().movingShotMode()).says("shoot on the move on/off").rumble();
```

**Why:** a piece shot from a moving robot keeps the robot's speed, so it lands to the side (30 in/s for
0.8 s: 24 in). With `shootOnMove`, the turret (or the whole robot) aims at a point that far the other way. The launcher takes its speed from that point's distance. It also brings the camera's picture forward
to now, because the robot turned and moved since the camera took it. It works with a turret or
whole-robot aim, with a camera on the turret or on the body, or with the odometry alone. Tell it where
the shooter and the camera are. The Driver Station says `Aim : moving shot: lead 12.3 deg, 18 in` while
it leads.
**More:** [Add a camera: shooting while moving](../add-to-robot/07-camera.md#shooting-while-moving), [Aim](../reference/parts/aim.md).

## How do I fire a group without stopping?

**Where:** `Tunables.java`, in your `Launcher` section. Fast groups need a feeder that does not stop between
pieces. The robot needs a CR servo or motor feeder, the speed from the distance, and a camera to aim.

**Safety:** wear eye protection. The robot can turn by itself while you hold fire. Let go to stop.

<!-- recipe: tunables-continuous-fire -->
```java title="Tunables.java"
// ADD these lines to your Tunables.Launcher section.
public static boolean continuousOnHold = true;              // hold fire: feed nonstop, aimed
public static double continuousMaxSec = DEFAULT;            // one hold feeds this long at most, seconds (3)
public static double continuousBoostRpm = DEFAULT;          // extra speed once the feed starts, RPM (0)
public static String speedControl = "PID";                  // "PID" (normal) or "BANG_BANG" (to test)
public static boolean extendTableEnds = false;              // true: guess speeds past the tested rows
```

Then:

1. Tap fire. One piece goes.
2. Hold fire while the goal is in view. The robot aims, waits for the speed, then feeds nonstop.
3. Let go. The feeder stops at once.

If it does not fire, the screen says what is wrong (for example <samp>NOT aimed</samp>) and what to do.
Autonomous still fires one piece at a time. This is not proven on a real robot yet.

**More:** [Continuous fire](../reference/parts/launcher.md#continuous-fire),
[Speed control](../reference/parts/launcher.md#speed-control-pid-or-bang-bang),
[Table ends](../reference/parts/launcher.md#speeds-past-the-ends-of-the-table).

## How do I use one shot table per game piece?

**Where:** `Tunables.java` (the tables, in your `Launcher` section), then `Controls.java` (inside
`buttons(...)`) or `Actions.java` (inside the class) to switch.

<!-- recipe: tunables-shot-tables -->
```java title="Tunables.java"
// IN PLACE OF the speedByDistance line: one table per game piece, {inches to the goal, RPM}. (1)!
// The POLLEN table (example rows: write the Shot Tuner's).
public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};
// The NECTAR table.
public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
```

1. Write this in your `Tunables.Launcher` section. The name after `speedByDistance` is the table's name: `"pollen"` and `"nectar"`. Fill both from the Shot Tuner.

<!-- recipe: shot-table-buttons -->
```java title="Controls.java"
// One button per game piece (the launcher's D-pad right already picks the next table).
g2.x.press(() -> robot.launcher.useTable("pollen")).says("POLLEN shot table");
g2.b.press(() -> robot.launcher.useTable("nectar")).says("NECTAR shot table");
```

<!-- recipe: shot-table-action -->
```java title="Actions.java"
/** An action that picks the NECTAR table, then fires one (Actions.java). */
public static Action fireNectar(Robot r) {
    return Action.named("Fire NECTAR")
            .then(() -> r.launcher.useTable("nectar"))
            .then(r.launcher::fire);
}
```

**Why:** POLLEN (2.8 in) and NECTAR (3.6 in) fly differently, so each needs its own speeds. **The table
at ▶:** a plain `speedByDistance` table (named `main`) if there is one. If not, the first by name: here
`nectar` (n before p). To start on POLLEN, add `.startTable("pollen")` to the launcher line in
`Robot.java` ([like here](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)). The Driver Station's
`Shot table` line says which one is in use. Fill each table with the
[Shot Tuner](../tuning/13-shot-tuner.md) ([its file](15-tuning.md#how-do-i-build-a-shot-table-speed-for-each-distance)).
Press Y until `> Table`, then pick the table you tune with the D-pad. Then copy its line into
`Tunables.java`. To let the robot pick the table by itself, see
[the next piece picks it](05-game-pieces.md#how-do-i-let-the-next-piece-pick-the-shot-table).
**More:** [Add a launcher: two shot tables](../add-to-robot/05-launcher.md#two-kinds-of-pieces-two-shot-tables), [Launcher](../reference/parts/launcher.md).

## Every shot is a bit short today. How do I trim the speed (a low battery)?

**Where:** the D-pad trim is the launcher's usual line in `Controls.java`:
`robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);` ([Buttons to add](../reference/parts/launcher.md#buttons-to-add)).
The numbers go in your `Tunables.Launcher` section. Your own buttons go in `Controls.java`, inside `buttons(...)`.

<!-- recipe: tunables-speed-trim -->
```java title="Tunables.java"
// In your Tunables.Launcher section (change the lines you already have, add the others).
public static double kP = MEASURE;                          // speed control (Flywheel Finder)
public static double kI = MEASURE;                          // speed control (Flywheel Finder)
public static double kD = DEFAULT;                          // speed control (0)
public static double kF = MEASURE;                          // steady power (Flywheel Finder)
public static double nominalVolts = MEASURE;                // battery volts for kF (Flywheel Finder)
public static double trimStepPercent = DEFAULT;             // one D-pad press (1%)
public static double trimMaxPercent = DEFAULT;              // never more than this each way (15%)
// Battery compensation: true = F follows the battery. Try it on a real robot and tell us.
public static boolean batteryCompensation = false;          // (off until proven on a real robot)
```

<!-- recipe: speed-trim -->
```java title="Controls.java"
// The launcher's usual line trims too: speedOn(g2.dpadUp, g2.dpadDown) = +/- 1% a press.
// Your own: a bigger step on a button, and a far shot that sets the trim first.
g2.y.press(() -> robot.launcher.trimBy(+3)).says("speed trim +3%");
g2.a.press(Action.named("Far shot +5%")
        .then(() -> robot.launcher.setTrim(+5), robot.launcher::fire)
        // The launcher fires or gives up within 3 s, so 4 s is always enough.
        .waitUntil("fired", () -> !robot.launcher.isShooting(), 4.0)
        .then(robot.launcher::resetTrim));
// Last match's trim (INIT offers it as "Trim last time"): from your own button too.
g2.x.press(robot.launcher::useLastTrim).says("use last match's trim");
```

1. Hold the D-pad for 4 steps a second. Double-tap D-pad down to go back to 0. The launcher's first line on the Driver Station shows the trim: `Speed trim : +3%  READY`.

**Why:** a worn wheel or softer balls make every shot a little short. The trim fixes it for this match,
for every table. It moves +/- 1% a press, at most 15%, and never past the motor's top speed. It goes
back to 0 at every INIT. At INIT, the Driver Station offers last time's trim (`Trim last time : +3%`).
To use it, double-tap D-pad up after ▶ (or use your own button: `useLastTrim`, in the recipe). The
library never applies it by itself.

Battery compensation scales the F gain by `nominalVolts` / the battery (tuned at 12.6 V, at 11.5 V:
F x 1.10). Nobody has proven it on a real robot yet, so it is off: the box writes
`batteryCompensation = false`. Change it to `true` only when you want to test it. It needs the `kF` and
`nominalVolts` that the Flywheel Finder found together. That is why the box writes the gains as
`MEASURE`. Without battery compensation, `DEFAULT` gains (from the motor) are a good start.

When you need a trim every match, with a charged battery, the cause is the wheel or the balls, not the battery. The table is off. Make the trim part of the table (+3%: every speed x 1.03, `{48, 2550}` becomes
`{48, 2627}`). Copy the lines into `Tunables.java`, and trim back to 0.
**More:** [Add a launcher: speed trim and a low battery](../add-to-robot/05-launcher.md#speed-trim-and-a-low-battery), [Launcher](../reference/parts/launcher.md#speed-trim-and-battery-compensation).

## How do I add a hood to my launcher?

**Where:** `Robot.java`, inside `define()`: add `.hood(...)` to your launcher line. In `Tunables.java`, add
a new section `Hood` (its stops), and a third number in each row of your `Launcher` section's shot table.

<!-- recipe: robot-hood -->
```java title="Robot.java"
// The hood goes on the launcher; its numbers are in Tunables.Hood.
// .part(...): which servos they are (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .hood(Hood.servo("hood").part("gobilda-2000-torque"))
        .speedFromDistance(camera));
```

A new section in `Tunables.java`:

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

Then the shot table:

<!-- recipe: tunables-hood-table -->
```java title="Tunables.java"
// IN PLACE OF your speedByDistance line: {inches to the goal, RPM, hood angle}, blended between rows. (1)!
public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
```

1. This goes in your `Tunables.Launcher` section. The launcher blends the hood angle between rows, like the speed.

**With no measured angles:** leave out `angleA`, `angleB`. The hood then works in servo positions, and
the table's third number is a servo position.

<!-- recipe: tunables-hood-positions -->
```java title="Tunables.java"
// Tunables.Hood: only posA and posB (delete angleA, angleB). Tunables.Launcher, IN PLACE OF
// your speedByDistance line, {inches, RPM, servo position}: the Shot Tuner writes these for you.
public static double[][] speedByDistance = {{24, 2200, 0.24}, {48, 2550, 0.38}, {72, 2900, 0.50}};
```

**A hood on a linkage or cam:** equal servo steps do not make equal angles. Use this `Hood` section in
place of the one above. It has a calibration table instead of `posA`, `posB`, `angleA`, `angleB`.

<!-- recipe: tunables-hood-linkage -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // {servo position, hood angle} rows, blended. The first and last rows are the stops (no posA / posB). (1)!
    // Example rows: write the Hood Finder's in their place.
    public static double[][] calibration = {{0.20, 0}, {0.32, 8}, {0.45, 18}, {0.65, 30}};
}
```

1. The angles must keep going one way: in servo-position order, the angles all go up, or all go down.

**A hood on a motor with an encoder** (it knows where it is): write `Hood.motor` in the launcher line, and
use this `Hood` section in place of the one above.

<!-- recipe: robot-hood-motor -->
```java title="Robot.java"
add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .hood(Hood.motor("hood", "gobilda-5203-117"))   // a motor with an encoder
        .speedFromDistance(camera));
```

<!-- recipe: tunables-hood-motor -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // Encoder ticks at the two stops, counted from where the hood sits at INIT (start the robot
    // with the hood on stop A), and the angles there.
    public static double posA = MEASURE;                        // ticks at stop A, usually 0 (Hood Finder)
    public static double posB = MEASURE;                        // ticks at stop B (Hood Finder)
    public static double angleA = MEASURE;                      // the angle at stop A (an angle app)
    public static double angleB = MEASURE;                      // the angle at stop B (an angle app)
    public static double kP = DEFAULT;                          // power per tick off (0.005)
    public static double maxPower = DEFAULT;                    // the most power (0.5)
    public static double tolerance = DEFAULT;                   // ready within this (1/50 of the range)
}
```

Your own hood buttons, in `Controls.java` inside `buttons(...)`:

<!-- recipe: hood-buttons -->
```java title="Controls.java"
// The launcher's hood already has gamepad 2's left stick Y (up = a bigger angle): a trim
// while the table sets the angle, else the angle itself. Your own: steps and the start angle.
g2.y.press(() -> robot.launcher.hood().step(+1)).says("hood +1 step");
g2.a.press(() -> robot.launcher.hood().step(-1)).says("hood -1 step");
g2.x.press(() -> robot.launcher.hood().goToStart()).says("hood to its start angle");
```

**Why:** close shots need a steep angle, and far ones a flatter one. So each distance gets a speed AND a
hood angle, blended between rows. `posA`, `posB` are hard limits: nothing sends the hood past a stop.
The launcher waits for the hood before it fires. For a servo hood, it waits a share of `travelSec`. For
a motor hood, it reads the encoder. While the camera sees a goal, the table sets the angle, and the
stick (gamepad 2 left Y) is a hood trim. With no goal seen, the stick moves the hood itself, so you
always see what it does. Double-tap D-pad down to clear both trims. A lift brings the same stick
(gamepad 2 left Y). With a hood AND a lift, give one of them another stick
([move a button](03-buttons.md#how-do-i-move-a-parts-button-to-another-button)). INIT says which part has it. Find the stops with the [Hood Finder](15-tuning.md#how-do-i-find-my-hoods-stops-and-angles),
then fill the table with the [Shot Tuner](15-tuning.md#how-do-i-build-a-shot-table-speed-for-each-distance) (press Y until `> Hood`).
**More:** [Add a launcher: a hood](../add-to-robot/05-launcher.md#a-hood-the-shot-angle), [Hood](../reference/parts/hood.md).

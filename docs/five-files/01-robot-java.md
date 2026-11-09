# Robot.java: what is on the robot

**The five files, page 1 of 5.**

`Robot.java` lists what is on your robot: the drive, and each part, with the names from the robot
configuration. It does not say what the buttons do (that is `Controls.java`) or how fast things go
(that is `Tunables.java`).

## The smallest Robot.java

This is a whole `Robot.java` for a robot that only drives. Click a **(+)** to read what a line does.

<!-- recipe: first-robot -->
```java title="Robot.java"
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;

/** The five files, file 1 of 5: what is on the robot. The smallest robot that drives. */
public class Robot extends RobotDefinition { // (1)!

    @Override
    protected void define() { // (2)!
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right") // (3)!
                .motor("gobilda-5203-312").wheel("gobilda-gripforce-mecanum-104") // (4)!
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)); // (5)!
    }
}
```

1. `extends RobotDefinition`: your robot is built from the library's robot. That gives it the drive,
   the safety checks, the screen lines and the simulator.
2. `define()` runs once at INIT. Each line in it adds one thing to the robot.
3. The drive, and its four motor names **in this order**: front left, front right, back left, back right.
   Each name must be exactly the name in the robot configuration (capitals and `_` count).
4. Which motor drives the wheels, and which wheels: their ids in the [parts catalog](../guides/parts-catalog.md).
   The library takes the motor's speed and the wheel's size from there, for speeds and distances.
5. The Control Hub's own IMU (the sensor that knows which way the robot faces), and which way the hub is
   mounted: where its **logo** faces, then where its **USB ports** face.

**The first line of the file** (above the imports) must be:

```text
package org.firstinspires.ftc.teamcode;
```

## Make it your robot

### The drive, attribute by attribute

Every attribute you can put on the drive line. **Needed?** says which you must write: **yes** = the
line does not work without it; recommended = it works without it, but less well; optional = only when
your robot needs it.

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Drive.mecanum(fl, fr, bl, br)` | **yes**, one of the three | a mecanum drive (it can slide sideways) and its four motor names, in this order | — |
| `Drive.tank(left, right)` | **yes**, one of the three | a tank drive, one motor per side | — |
| `Drive.tank(fl, fr, bl, br)` | **yes**, one of the three | a tank drive, two motors per side | — |
| `.motor(id)` | recommended | the drive motor, by its [parts catalog](../guides/parts-catalog.md#motors) id: speeds and distances come from it | Defaults to `gobilda-5203-312` |
| `.wheel(id)` | recommended | the wheel, by its [parts catalog](../guides/parts-catalog.md#wheels-and-rollers) id: its size comes from the catalog | Defaults to a 96 mm wheel |
| `.wheelMm(mm)` | optional | the wheel's diameter in mm, for a wheel that is not in the catalog (in place of `.wheel`) | Defaults to 96 |
| `.imu(name, logo, usb)` | recommended | the IMU's name, and where the hub's logo and USB ports face | Defaults to `"imu"`, `UP`, `FORWARD` |
| `.reversed(names...)` | optional | exactly these motors run reversed: add it after the first drive test, if needed | Defaults to the left side reversed |
| `.gearRatio(r)` | optional | motor turns per wheel turn, when gears, a belt or a chain sit between them | Defaults to 1 |
| `.wheelsAtMm(track, base)` | optional | where the wheels sit, side to side and front to back, mm (the simulator only) | the simulator uses its robot file's numbers |
| `.noImu()` | optional | no IMU: TeleOp drives robot-centric only, and autonomous will not start | Defaults to the hub's IMU |

The drive's numbers (speeds, slow mode, auto turns) are in `Tunables.Drive`:
[every drive number](../guides/tunables.md#drive).

Each box below is one common change.

### Your motor names

Use the names from your robot configuration ([Set up 7](../setup/07-robot-configuration.md)). A starter
bot's names are in [the table for your starter bot](../guides/starter-bots.md#the-configuration-names).

### A tank drive instead

A tank drive (one motor per side, or one name per side) uses `Drive.tank`:

<!-- recipe: drive-tank -->
```java title="Robot.java"
drive(Drive.tank("left_drive", "right_drive")
        .motor("gobilda-5203-312").wheelMm(96)
        .reversed("right_drive")                     // only the motors that run backward
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

### Your motor and wheels

Find your drive motor and your wheels in the parts catalog, and copy their ids:

- [Motors](../guides/parts-catalog.md#motors), for example `gobilda-5203-312` for goBILDA's 312 RPM Yellow Jacket.
- [Wheels and rollers](../guides/parts-catalog.md#wheels-and-rollers), for example `gobilda-gripforce-mecanum-104`,
  `gobilda-mecanum-96`, `rev-mecanum-75` or `gobilda-hogback-96`.

Your wheel is not in the catalog? Measure it across in millimetres and write `.wheelMm(mm)` in place of
`.wheel(id)`.

### How your Control Hub is mounted

Look at the hub on the robot. Write where its logo faces, then where its USB ports face:

<!-- recipe: drive-imu -->
```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // Which way the hub's LOGO faces, then which way its USB PORTS face, on the robot.
        .imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP));
```

**Why it matters:** the robot uses the IMU to know which way it faces. A wrong mounting makes turns and
field-centric driving go the wrong way.

### A wheel turns the wrong way

Leave this until the first drive test. Without a `.reversed(...)` line, the left side runs reversed. If
your robot is built the other way, list **every** reversed drive motor:

<!-- recipe: drive-reversed -->
```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // List EVERY reversed drive motor. Without this line the left side is reversed.
        .reversed("front_right", "back_right")
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

The Motor Direction Check tells you which ones ([Tune and check](../guides/finders.md)).

## Check it

**Build:** in Android Studio, **Build > Make Project** ([the buttons](../setup/06-build-and-install.md#the-buttons)).

**Done when:** the build finishes with no red errors.

The robot cannot drive yet: the buttons are in `Controls.java`, the next file. Red errors? Read only the
first one: it names the file, the line and the problem.

## Add a part

When the robot drives, add your parts **one at a time**, and test each before the next. Each part is two
lines in `Robot.java`:

1. At the top of the class: `public` + the kind of part + a name, for example `public Intake intake;`.
2. In `define()`: the line that makes it, with its name from the robot configuration.

Every part has these attributes:

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `.part(id)` | recommended | which motor or servo it is, by its [parts catalog](../guides/parts-catalog.md) id: the starting numbers and the simulator use its real speed and size | Defaults to general starting numbers |
| `.optional()` | optional | the part may not be on the robot yet: INIT goes on without it | INIT stops if the part is missing |
| `.looks(shapes...)` | optional | how it looks and where it sits, for the simulator | the simulator draws a guess |
| `.tunables("Name")` | optional | read its numbers from another `Tunables.java` section | Defaults to the section named after the part |

Below: the two common parts, each with its usual line and its own attributes. The other parts are in a
list at the end of the page.

### Intake

Rollers or wheels that take pieces in. On one motor:

<!-- recipe: intake-motor -->
```java title="Robot.java"
// At the top of the class: public Intake intake;   Here in define():
// "intake" = the motor's name in the robot configuration. Its numbers: Tunables.Intake.
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
```

On continuous-rotation (CR) servos:

<!-- recipe: intake-cr-servos -->
```java title="Robot.java"
// Two continuous-rotation (CR) servos. The one that spins the wrong way is reversed.
// "left_intake", "right_intake" = the servos' names in the robot configuration. Numbers: Tunables.Intake.
// .part(...): which servo it is (optional, recommended).
intake = add(Intake.crServos("left_intake", "right_intake").part("gobilda-2000-speed")
        .reversed("right_intake"));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Intake.motor(name)` | **yes**, one of the two | an intake on one motor | — |
| `Intake.crServos(names...)` | **yes**, one of the two | an intake on CR servos that run together | — |
| `.holds(n)` | recommended | how many pieces it carries: count them by hand | Defaults to 4 (the simulator's guess) |
| `.withCrServos(names...)` | optional | CR servos that run with the motor, like side wheels next to a roller | — |
| `.reversed(names...)` | optional | these devices run the other way, like the second of two facing servos | — |
| `.holds(n, "KIND")` | optional | how many of one kind it carries; 0 = it cannot take that kind | every kind counts the same |
| `.onAtStart()` | optional | runs in from ▶ until stopped | Defaults to off: a button starts it |
| `.stopsWhen(test)` | optional | never runs in while the test is true, like "the robot is full" ([Pieces](../modules/robot.md#pieces-and-piecesensor)) | it runs whenever asked |

Its numbers: `inPower`, `outPower` ([Tunables: Intake](../guides/tunables.md#intake)).
Every option and command: [Intake](../modules/robot.md#intake).

### Launcher

A flywheel that shoots pieces, and the part that feeds it one piece at a time.

<!-- recipe: launcher -->
```java title="Robot.java"
// A flywheel (in RPM) and the indexer that feeds it one piece at a time.
// "launcher", "indexer" = names in the robot configuration. Numbers: Tunables.Launcher, .Indexer.
// The motor's catalog id is in the brackets (needed). The indexer's .part(...) is optional, recommended.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Launcher.flywheel(motor, id)` | **yes** | a flywheel on this motor; the id is needed for its speed in RPM | — |
| `.feeder(Indexer.servo(name))` | recommended, one of the three | a servo arm pushes each piece in | nothing feeds the wheel |
| `.feeder(Indexer.crServo(name))` | recommended, one of the three | a CR servo wheel feeds the pieces, like goBILDA's "windmill" | nothing feeds the wheel |
| `.feeder(Indexer.motor(name))` | recommended, one of the three | a motor feeds the pieces | nothing feeds the wheel |
| `.reversed()` | optional | the flywheel turns the other way | — |
| `.spinAtStart()` | optional | spins up at ▶, so the first shot goes at once | Defaults to off: it spins up when asked |
| `.hood(...)`, `.feedStop(...)`, `.feedWheel(...)` | optional | a hood, a feed stop, a feed wheel ([Launcher](../modules/robot.md#launcher)) | — |
| `.speedFromDistance(camera)` | optional | the speed follows the camera's distance to the goal | Defaults to one speed, `speedRpm` |

Its numbers: `speedRpm`, `readyBandRpm` and the speed control (`kP`, `kF`, ...)
([Tunables: Launcher](../guides/tunables.md#launcher)); the feeder's in its own section
([Indexer](../guides/tunables.md#indexer)). Every option and command: [Launcher](../modules/robot.md#launcher).

### The other parts

| Part | For | Look it up |
| --- | --- | --- |
| `Lift` | a slide that goes up and down | [Lift](../modules/robot.md#lift) |
| `Arm` | a part that swings on a hinge | [Arm](../modules/robot.md#arm) |
| `Claw` | a servo that opens and closes | [Claw](../modules/robot.md#claw) |
| `Wrist` | a servo that tilts a claw | [Wrist](../modules/robot.md#wrist) |
| `Positioner` | any servo or motor that goes to set positions, like a fold-out | [Positioner](../modules/robot.md#positioner) |
| `Turret` | a part that turns to aim | [Turret](../modules/robot.md#turret) |
| `Camera` | a webcam or Limelight | [Camera](../modules/robot.md#camera) |
| `Lights` | a light strip or Blinkin | [Lights](../modules/robot.md#lights) |

Each part also gets its numbers in `Tunables.java` and its buttons in `Controls.java` (a part has no
buttons until you write its lines there). The next pages show how.

---

**Next:** [Tunables.java](02-tunables-java.md).
**Look it up:** [RobotDefinition and every part](../modules/robot.md). **Go deeper:**
[how the library fits together](../guides/architecture.md).

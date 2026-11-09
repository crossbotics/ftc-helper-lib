# Tunables.java: the numbers

**The five files, page 2 of 5.**

`Tunables.java` holds your robot's **numbers**: how fast it drives, how far the lift goes, how fast the
launcher spins. `Robot.java` says which parts the robot has; `Tunables.java` says their numbers.

**Why a separate file:** you change numbers all season. Here you can change them without touching the
code that works.

## The smallest Tunables.java

This is a whole `Tunables.java` for the robot of the [Robot.java page](01-robot-java.md): only the drive.
Click a **(+)** to read what a line does.

<!-- recipe: first-tunables -->
```java title="Tunables.java"
import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT and MEASURE (1)!

/** The five files, file 2 of 5: the numbers. One section per part, one number per line. */
public class Tunables {
    public static class Drive { // (2)!
        public static double maxPower = 0.5;                        // top wheel power: slow for the first drives (1.0) (3)!
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (0.6) (4)!
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // sideways moves: the Drive Tuner finds it (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then (5)!
    }
}
```

1. This line lets you write the words `DEFAULT` and `MEASURE` in place of a number. Without it, the build
   fails with `cannot find symbol`.
2. A **section**: the numbers of one part. The drive's section is always called `Drive`.
3. A number you chose: half power, so the first drives are slow and safe. The library's own value is in
   brackets at the end of the comment.
4. `DEFAULT`: use the library's starting value (the one in brackets). Leaving the line out does the same,
   but the line shows you every number you can change.
5. Autonomous drives the robot only when this is `true`. Set it after the Drive Tuner, when moves are the
   right length.

**The first line of the file** (above the import) must be:

```text
package org.firstinspires.ftc.teamcode;
```

## How the file works

**One section per part.** The section's name is the part's name in the robot configuration, in
CamelCase: each word starts with a capital letter, with no `_` and no spaces.

| The part in Robot.java | Its section |
| --- | --- |
| the drive | `Drive` (always) |
| `Intake.motor("intake")` | `Intake` |
| `Positioner.servo("intake_deploy")` | `IntakeDeploy` |
| `Launcher.flywheel("shooter", ...)` | `Shooter` |

**One number per line.** Write it as `public static double name = value;`, or
`public static boolean name = true;` for an on/off setting. `public static` is needed: the library cannot
read a number without it. Names count, with their capitals: `maxPower` works, `MaxPower` does not.

**What to write for each number:**

| The number | Write | At INIT |
| --- | --- | --- |
| You know it | the number | it is used |
| A tuning number you do not know yet (a speed, `kP`) | `DEFAULT` | the library's starting value is used |
| A safety limit or a servo position only your robot can give (a lift's `min`, a claw's `open`) | `MEASURE` | the TeleOp and autos **stop** and name the tool that finds it |

**Why `MEASURE` stops:** a guessed limit or servo position can drive a part into the frame and break it.
A guessed tuning number only makes a part slow or soft. More: [DEFAULT and MEASURE](../guides/tunables.md#default-and-measure).

**Your own numbers.** A section that no part reads is yours, for example `Auto` for your autonomous
distances. Your code reads them as `Tunables.Auto.parkInches`.

## The drive's numbers

| Number | Needed? | What it sets | If you leave it out | Found with |
| --- | --- | --- | --- | --- |
| `maxPower` | optional | the most power the wheels get | Defaults to 1.0 | test: lower it for new drivers |
| `practiceMaxPower` | optional | the most power in the PRACTICE profile | Defaults to 0.6 | test |
| `slowSpeed` | optional | the most power while the slow trigger is pulled | Defaults to 0.3 | test |
| `strafeCorrection` | recommended (mecanum) | makes autonomous sideways moves the right length | Defaults to 1.15 | the Drive Tuner |
| `turnKp` | optional | how hard an autonomous turn pushes, per degree off | Defaults to 0.012 | test: turns stop short (more) or wobble (less) |
| `turnKd` | optional | how much an autonomous turn brakes near the end | Defaults to 0.003 | test: turns overshoot (more) |
| `moveMinPower` | optional | the least power that still moves the robot | Defaults to 0.30 | test: it stops just short (more) |
| `calibrated` | **yes**, for autonomous | autonomous drives only when it is `true` | Defaults to `false`: autonomous will not drive | the Drive Tuner, then set it |

Every drive number, with its units: [Tunables: Drive](../guides/tunables.md#drive).

## Tune a number, then copy it back

You find most numbers on the robot, with a finder or by testing. Two ways give you a number:

- **A finder** (Tune and check, on the Driver Hub) measures it and shows the line to copy, for example
  `Tunables.Drive: strafeCorrection = 1.210`. The finders: [Tune and check](../guides/finders.md).
- **The tuning menu** changes a number while the robot runs: PRACTICE profile, after ▶ hold **GUIDE** for
  1 second, D-pad to choose and change, **A** to save ([tuning menu](../guides/tuning.md)).

**Copy every number back into `Tunables.java`, then build and install.** The next INIT lists the numbers
saved on the hub under "Tuned on this robot (copy into Tunables.java)".

**Why:** a number saved only on the Control Hub is lost with a new hub, and it is not in the team's code.
The file is the team's record.

## Check it

**Build:** **Build > Make Project** ([the buttons](../setup/06-build-and-install.md#the-buttons)).

**Done when:** the build has no red errors. At INIT, the Driver Hub shows one note per part with the
numbers it used. A `MEASURE` stops INIT and names the tool that finds the number.

## A part's numbers

When you add a part to `Robot.java`, add its section here. Below: the two common parts' sections. Each
shows every line set to `DEFAULT` or `MEASURE`, so you can see what there is to change.

### Intake

<!-- recipe: tunables-intake -->
```java title="Tunables.java"
public static class Intake {                                    // "intake" in the configuration
    public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
    public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
}
```

| Number | Needed? | What it sets | If you leave it out | Found with |
| --- | --- | --- | --- | --- |
| `inPower` | optional | power while it takes pieces in, 0 to 1 | Defaults to 1.0 | test: pieces are thrown or jam (less) |
| `outPower` | optional | power while it pushes pieces out, 0 to 1 | Defaults to 0.7 | test: pieces do not come out (more) |

### Launcher

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

| Number | Needed? | What it sets | If you leave it out | Found with |
| --- | --- | --- | --- | --- |
| `speedRpm` | recommended | the shot speed, in RPM | Defaults to 60% of the motor's top speed | the Shot Tuner, or test |
| `kP`, `kI`, `kD`, `kF` | recommended | the hub's speed control | Defaults to numbers worked out from the motor | the Flywheel Finder |
| `readyBandRpm` | optional | "at speed" means this close to the target | Defaults to 60 | test: it waits too long to fire (more) |
| `idleRpm` | optional | the speed between shots; 0 = stop | Defaults to 0 | test: the first shot spins up too slowly |
| `stepRpm` | optional | one D-pad press changes the speed by this | Defaults to 100 | the drivers' choice |
| `shotIntervalSec` | optional | the least time between two shots | Defaults to 0.25 | the Flywheel Finder |
| `speedByDistance` | optional | the shot table: `{inches, RPM}` rows, for a robot with a camera | Defaults to none: `speedRpm` always | the Shot Tuner |

The part that feeds the launcher has its own section, named after its device. For a servo feeder:

<!-- recipe: tunables-indexer -->
```java title="Tunables.java"
public static class Indexer {                                   // "indexer" in the configuration
    public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
    public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
    public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
    public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
}
```

`rest` and `push` are **needed**: they are servo positions only your robot can give (the Servo Finder).
Every launcher and feeder number: [Launcher](../guides/tunables.md#launcher),
[Indexer](../guides/tunables.md#indexer).

### The other parts

| Part | Its numbers |
| --- | --- |
| Lift, Arm | [Lift and Arm](../guides/tunables.md#lift-and-arm): `min` and `max` are **needed** |
| Claw, Wrist, Positioner | [Claw, Wrist and Positioner](../guides/tunables.md#claw-wrist-and-positioner): the positions are **needed** |
| Turret | [Turret](../guides/tunables.md#turret) |
| Camera | [Camera](../guides/tunables.md#camera) |
| Lights | [Lights](../guides/tunables.md#lights) |

---

**Next:** `Controls.java` (coming). Until then: [lesson 1, Controls.java](../tutorials/start/01-robot-in-five-files.md#3-controlsjava-the-buttons-and-the-teleop).
**Look it up:** [every number of every part](../guides/tunables.md). **Go deeper:**
[the finders](../guides/finders.md) and [the tuning menu](../guides/tuning.md).

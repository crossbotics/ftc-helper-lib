# Tunables.java: every number, part by part

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** anyone who writes or changes `Tunables.java` in a robot made in five files
([lesson 1](../tutorials/start/01-robot-in-five-files.md)). **Use it:** find your part below, read its
table, and copy only the lines you want to change. Every number has a default, except the ones marked
**REQUIRED**.

This page lists every number the library reads from `Tunables.java`. A script
(`tools/check_tunables.py`) reads the library's code and checks this page against it, so the list is
complete: if a part reads a new number, the check fails until the number is here.

## What Tunables.java is

`Tunables.java` is the file with your robot's **numbers**: how far the lift goes, how fast the launcher
spins, where the claw opens. `Robot.java` says WHICH parts the robot has; `Tunables.java` says their
numbers. Why a separate file: you change numbers all season, and you can change them without touching
the code that works.

```java
public class Tunables {
    public static class Lift {                          // the part's section
        public static double min = 0, max = 2580;       // REQUIRED for a lift (Motor Finder)
        public static double kP = 0.004;                // optional: the default is used if you leave it out
        public static double down = 0, high = 2400;     // a lift's other numbers are named heights
    }
    public static class Auto {                          // your own section: no part reads it
        public static double parkInches = 24;
    }
}
```

- **A section is a part.** `public static class Lift { ... }` holds the numbers of the part whose device
  is called `lift` in the robot configuration. The section's name is the device's name in CamelCase:
  each word starts with a capital letter, with no spaces and no `_`. `lift` reads `Lift`,
  `intake_deploy` reads `IntakeDeploy`, `Webcam 1` reads `Webcam1`. To use another name, write
  `.tunables("Name")` on the part in `Robot.java`.
- **Some sections have fixed names:** `Drive`, `Odometry`, `Aim` and `Buttons`. An
  [interlock rule](#interlock-rules) uses the name you give it in `keepApart("TurretLift", ...)`.
- **A field is one number.** Write it as `public static double name = value;` (`int` works too),
  `public static boolean name = true;` for an on/off setting, and
  `public static double[][] name = {{24, 2200}, {48, 2550}};` for a table. `public static` is needed:
  the library cannot read a field without it.
- **Names count, with their capitals.** `maxPower` is a setting; `MaxPower` and `maxpower` are not.
- **Your own sections** (no part reads them, like `Auto`): your code reads them as
  `Tunables.Auto.parkInches`. Every number in them is in the tuning menu too.

## What INIT checks

When you press INIT, the library reads `Tunables.java` and tells you what it found:

| You wrote | INIT does | Why |
| --- | --- | --- |
| no line for an optional number | uses the default, and shows a note: `Tunables.Lift: kD, kF not written: library defaults used (fine)` | a missing number is not a problem: the default works. Add the line to change it |
| no section at all for a part | uses every default: `Tunables.Intake not written: library defaults used (fine)` | the same |
| no line for a REQUIRED number | **stops**: `Tunables.Lift has no max: write max (the top of the safe range, in counts: Motor Finder, B). A safety limit never comes from a library default.` | a wrong limit breaks the robot. A default could be wrong for your robot |
| a name in Lift, Arm, Claw, Wrist or Positioner that looks like a setting (`Max`, `k_P`, `maxPowr`) | **stops**: `Tunables.Lift.Max looks like "max" (capitals and spelling count)` | in these parts every other name is a named position, so a typo would quietly become a position |
| a name no part reads, in any part's section (`inPowr` in `Intake`, `goalx` in `Aim`, a leftover `spinPower`) | **stops**: `Tunables.Intake.inPowr is not a setting of Intake (did you mean inPower?): the robot would quietly ignore it. Fix the name (capitals count) or delete the line. Known: inPower, outPower` | a misspelled line is never read, so the part would quietly use its default (a safety number too). The "did you mean" appears when the name looks like a real one |
| a section no part reads that is named like a part's kind (`Tunables.Launcher` for the launcher on `shooter`), or spelled almost like a part's section (`Tunables.Liftleft`) | **warns** (`!!`, every profile): `Tunables.Launcher is not read by any part: your launcher's section is Tunables.Shooter (its device name "shooter" in CamelCase). Move the numbers there` | every number in it would be quietly ignored. A warning, not a stop: a section no part reads may be your own (`Tunables.Auto`), and a part's required numbers (a lift's `min`/`max`, a servo turret's `posA`...) already stop INIT when its real section is missing |

The name check covers `Drive`, `Odometry`, `Aim`, `Buttons`, every part on the robot and every
interlock rule. These names are yours to pick and always pass: named positions (any number in Lift, Arm,
Claw, Wrist, Positioner), a piece sensor's kinds (`POLLEN`), a launcher's `speedByDistance...` tables, and
an interlock rule's `<part>From`, `<part>To`, `<part>Above`, `<part>Below`, `<part>Margin`, `<part>StopSec` for its two
parts. A field of the part that your robot does not use (a servo turret's `posA` on a motor turret) passes
too. Your own sections (`Auto`) are never checked, and neither is the section of an `optional()` part
that is not on the robot. Every name in the tables below is the full list.

**Check on the laptop:** `./gradlew :sim:fromRobot` (it writes the simulator's robot file) runs the same
name checks and prints what INIT would stop on or warn about. In a JUnit test, `robot.checkTunables()`
gives the same lines ([testing](testing.md)).

**Measured servo numbers and the servo's range.** Servo positions (named positions, `posA`/`posB`,
`rest`/`push`, a hood's `calibration`) are measured for the pulse range the servo had then. Adding or
removing `.fullRange()` on a part in Robot.java changes where each position points: measure them again
([Full range](../modules/hardware.md#full-range)).

## Units

Each part uses one unit. The table header says which.

| Part | Positions and limits in | Speeds and times in |
| --- | --- | --- |
| Lift | encoder **counts** (ticks), 0 = where it was at INIT (or the bottom switch) | power 0 to 1 |
| Arm, Turret | **degrees**. Arm: 0 = where it was at INIT. Turret: 0 = straight forward, counterclockwise + | power 0 to 1 |
| Claw, Wrist, Positioner, Indexer (servo), Hood (servo) | **servo position, 0 to 1** | seconds |
| Hood (motor) | encoder **counts** | power 0 to 1 |
| Launcher | **RPM** (turns per minute) of the flywheel | seconds |
| Drive, Aim, Odometry | **inches** and **degrees** on the field | power 0 to 1 |
| PieceSensor | hue in **degrees** (0 to 360) or size in **mm** | milliseconds (`Ms`) |

A name that ends in `Sec` is seconds, `Ms` milliseconds, `In` inches, `Mm` millimetres, `Deg` degrees,
`Rpm` RPM, `Percent` percent.

## The Live column: the tuning menu

You can change some numbers while the robot runs: PRACTICE profile, after START hold **GUIDE** for 1 s,
D-pad to choose and change, **A** to save on the Control Hub ([tuning guide](tuning.md)).

| Live says | What it means |
| --- | --- |
| **yes, at once** | in the tuning menu; the robot uses the new value in the same run |
| **yes, next INIT** | in the tuning menu; the robot uses the new value from the next INIT (it reads the number once, at INIT) |
| **no** | not in the tuning menu: change it in `Tunables.java` and install again. Limits are never in the menu, so tuning can never drive a part past a hard stop |

**Copy every tuned value back into `Tunables.java`.** The next INIT lists them under "Tuned on this robot
(copy into Tunables.java)", for example `Tunables.Lift.kP = 0.0050 (code 0.004)`. Why: a value saved
only on the hub is lost with a new Control Hub, and the file is the team's record.

## How to read a finder's lines

The [finders](finders.md) measure numbers on the robot and show the lines to copy. Each line names the
section first:

| The finder shows | Copy into Tunables.java |
| --- | --- |
| `Tunables.Lift: min = 0, max = 2580, P1 = 1200` | in `public static class Lift`: set `min`, `max` and a position. Rename `P1` to a real name (`high`) |
| `Tunables.Claw: P1 = 0.210, P2 = 0.770` | in `Claw`: rename them `open` and `closed` |
| `public static double[][] speedByDistance = {{24, 2200}, {48, 2550}};   // in Tunables.Launcher` | the whole line, into `Launcher` (replace the old one) |
| `Tunables.Odometry: checked = true` | in `Odometry`: `public static boolean checked = true;` |

The finders are: [Servo Finder](finders.md#servo-finder-ends-and-positions),
[Motor Finder](finders.md#motor-finder-limits-positions-top-speed),
[Flywheel Finder](finders.md#flywheel-finder-gains-and-recovery),
[Drive Tuner](finders.md#drive-tuner-wheel-size-and-strafe-correction),
[Odometry Tuner](finders.md#odometry-tuner-directions-offsets-and-a-loop-check),
[Aim Tuner](finders.md#aim-tuner-camera-offset), [Shot Tuner](finders.md#shot-tuner-the-shot-table),
[Hood Finder](finders.md#hood-finder), [Piece Tuner](finders.md#piece-tuner-what-each-sensor-sees).
"Test" in the tables below means: try it on the robot and change it by hand.

---

## Drive

<!-- tunables: Drive -->
Section `Drive` (fixed name). Read once at INIT. Module: [Drive](../modules/robot.md#drive).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `maxPower` | the most power the wheels get (COMPETITION profile) | 0 to 1 | 1.0 | yes, next INIT | test | the robot is too fast for the drivers |
| `practiceMaxPower` | the most power in the PRACTICE profile | 0 to 1 | 0.6 | yes, next INIT | test | new drivers need it slower |
| `slowSpeed` | a power: the most power while the slow trigger is pulled all the way (a little squeeze is a little slower) | 0 to 1 | 0.3 | yes, next INIT | test | fine moves are too slow or too fast |
| `strafeCorrection` | makes a sideways move in autonomous as long as asked (mecanum wheels slip sideways) | times | 1.15 | yes, next INIT | Drive Tuner | sideways moves are short or long |
| `turnKp` | autonomous turn: how hard it turns per degree off | power per degree | 0.012 | yes, next INIT | test | turns stop short (more) or wobble (less) |
| `turnKd` | autonomous turn: brakes the turn as it gets close | power per degree/s | 0.003 | yes, next INIT | test | turns overshoot (more) |
| `moveMinPower` | the least power that still moves the robot (autonomous) | 0 to 1 | 0.30 | yes, next INIT | test | the robot stops just before the end of a move |
| `calibrated` | autonomous drives the robot only when this is `true`. Until then INIT says `AUTONOMOUS IS OFF` (the simulator runs it anyway, and says so) | true/false | false | no | Drive Tuner, then set it | after the Drive Tuner, once moves are right |

## Odometry

<!-- tunables: Odometry -->
Section `Odometry` (fixed name). Read once at INIT. Module: [Odometry](../modules/robot.md#odometry).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `checked` | like `Drive.calibrated`, for the odometry: `driveTo` and path steps run on a real robot only when this is `true`. Until then INIT warns `Odometry not checked` (the simulator runs them anyway) | true/false | false | no | Odometry Tuner | after the Odometry Tuner's loop check passes |
| `linearScalar` | OTOS only: corrects its distances | times | 1.0 | no | Odometry Tuner | the OTOS says 48 in when the robot drove 50 |
| `angularScalar` | OTOS only: corrects its turns | times | 1.0 | no | test (turn 10 times, compare) | the heading drifts after many turns |
| `resetX`, `resetY` | the "set pose here" spot for `resetPose()`: x, y seen from your wall, written for BLUE. Write both: one alone is not used (no reset spot) | inches | none: no reset spot | no | measure on the field | you want a button that resets the pose at a known spot |
| `resetHeading` | the heading at the reset spot | degrees | 0 | no | measure on the field | the robot faces another way at that spot |
| `mirrorForRed` | ONE setting for the whole robot (routes, start and reset spots, the aim's goal point): RED uses BLUE's numbers mirrored (true) or the same numbers from the red wall (false). Write it once, in `Aim` or here: `Aim`'s wins, then this one, then your autonomous's `mirrorForRed()`. INIT shows the choice and where it came from | true/false | `Tunables.Aim.mirrorForRed`, else true | no | the game's field | BIOBUZZ's field is turned half a turn: `false` |

## Aim

<!-- tunables: Aim -->
Section `Aim` (fixed name). Every line is optional. Module: [Aim](../modules/robot.md#aim).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `goalX`, `goalY` | the goal on the field, seen from YOUR drivers' wall like every field number in your code (x right of the centre line, y from the centre toward the far wall), written for BLUE: the odometry backup aims at it when the camera does not see the goal. Write both: one alone is not used (no odometry backup). BIOBUZZ: 13, -13 | inches | none: camera only | no | the field map | once per season (both, or neither) |
| `mirrorForRed` | ONE setting for the whole robot: RED's numbers (the goal point, the routes, the start and reset spots) are BLUE's mirrored (true) or the same numbers from the red wall (false). The first one written wins: this one, `Tunables.Odometry.mirrorForRed`, your autonomous's `mirrorForRed()` | true/false | true | no | the game's field | BIOBUZZ: `false` |
| `cameraMaxAgeMs` | a camera picture older than this does not count as "sees the goal" | ms | 150 | no | test | the camera is slow and the goal flickers to "odometry" |
| `maxGoalDistance` | the odometry backup is off while it says the goal is farther than this (a wrong pose) | inches | 170 | no | the field size | another field size |
| `cameraOdometryAgree` | warn when the camera and the odometry differ by more than this | inches | 6 | no | test | too many warnings with a good pose |
| `turnKp` | whole-robot aim: turn power per degree off | power per degree | 0.02 | yes, at once | test | aim is slow (more) or swings (less) |
| `turnKd` | whole-robot aim: brakes the turn as it gets close | power per degree/s | 0.002 | yes, at once | test | aim overshoots (more) |
| `maxTurnPower` | whole-robot aim: the most turn power | 0 to 1 | 0.5 | yes, at once | test | aim is too fast or too slow |
| `minTurnPower` | whole-robot aim: the least power that still turns the robot | 0 to 1 | 0 | yes, at once | test | aim stops a few degrees short |
| `toleranceDeg` | whole-robot aim counts as aimed within this | degrees | 2 | yes, at once | test | shots miss although "AIMED" (less) |
| `cameraOffset` | whole-robot aim (no turret): a camera that is not on the robot's centre line. A turret has its own, `Tunables.Turret.cameraOffset`; the Aim Tuner prints the line for the one your robot uses | degrees | 0 | yes, at once | test | the robot aims a little to one side |
| `rumbleDeg` | the aim buzz starts this close to the goal | degrees | 4 | yes, at once | test | the drivers want it earlier or later |
| `shootOnMove` | shooting while driving: on at START | true/false | false | no | test | only after tuning it on the real robot |
| `flightSec` | shooting on the move: the piece's time in the air | seconds | 0 | yes, at once | test (film a shot) | moving shots land behind (more) or ahead |
| `flightSecPerIn` | shooting on the move: more flight time per inch of distance | seconds per inch | 0 | yes, at once | test | far moving shots miss but near ones hit |
| `maxLeadIn` | shooting on the move: the most it aims ahead | inches | 24 | yes, at once | test | fast moves need more lead |
| `cameraLatencyMs` | shooting on the move: the camera's delay, instead of its own timestamp | ms | the camera's own timestamp | yes, at once | test | the camera's timestamp is wrong |
| `shooterForwardIn`, `shooterLeftIn` | where the shooter (turret pivot or launcher exit) is from the robot's centre | inches | 0 | no | measure the robot | the shooter is not at the centre |
| `cameraForwardIn`, `cameraLeftIn` | where a camera on the robot's body is from the centre | inches | 0 | no | measure the robot | a body camera, not at the centre |

## Buttons

<!-- tunables: Buttons -->
Section `Buttons` (fixed name). For button gestures (double tap, hold). Module: [ControlMap](../modules/robot.md#controlmap).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `tapGapSec` | the most time between the two taps of a double tap | seconds | 0.35 | yes, at once | test | double taps are missed (more) or single taps wait too long (less) |
| `holdSec` | how long a press is held before it counts as a hold | seconds | 0.3 | yes, at once | test | holds start by accident (more) |

## Intake

<!-- tunables: Intake -->
Section: the device's name in CamelCase (`intake` reads `Intake`, `front_roller` reads `FrontRoller`). Module: [Intake](../modules/robot.md#intake).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `inPower` | power while it takes pieces in | 0 to 1 | 1.0 | yes, at once | test | pieces are thrown or jam (less) |
| `outPower` | power while it pushes pieces out | 0 to 1 | 0.7 | yes, at once | test | pieces do not come out (more) |

## Lift and Arm

<!-- tunables: LiftArm -->
Section: the device's name in CamelCase (`lift` reads `Lift`, `lift_left` reads `LiftLeft`, not `Lift`). With
two motors ([`alsoMotor`](../modules/robot.md#two-motors-on-one-lift-or-arm)) it is the FIRST motor's name. A Lift is in encoder **counts**, an Arm in **degrees**.
Module: [Lift](../modules/robot.md#lift), [Arm](../modules/robot.md#arm).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `min` | the bottom of the safe range: it never goes below | counts / degrees | REQUIRED | no | Motor Finder (X) | the part touches its bottom stop |
| `max` | the top of the safe range: it never goes above. Must be more than `min` | counts / degrees | REQUIRED | no | Motor Finder (B) | the part touches its top stop |
| `kP` | how hard it pushes toward its target, per unit off | power per unit | Lift 0.004, Arm 0.02 | yes, at once | test | slow to arrive (more) or shakes (less) |
| `kI` | a push that grows while it stays short | power | 0 | yes, at once | test | it stops a little short and stays there |
| `kD` | brakes it as it gets close | power | Lift 0.0002, Arm 0.001 | yes, at once | test | it overshoots (more) |
| `kF` | a steady push that holds it up against gravity | power | Lift 0.05, Arm 0 | yes, at once | test | the lift sags (more) |
| `maxPower` | the most power it ever gets | 0 to 1 | Lift 1.0, Arm 0.6 | no | test | it moves too violently |
| `slowZone` | it slows down within this distance of `min` and `max` | counts / degrees | Lift 250, Arm 15 | no | test | it slams into its ends (more) |
| `slowZonePower` | the power at the very end of the slow zone | 0 to 1 | 0.2 | no | test | it stalls near an end (more) |
| `tolerance` | how close counts as "arrived" | counts / degrees | Lift 20, Arm 3 | no | test | actions wait too long (more) |
| `<position>` | every other number is a named position (`down`, `low`, `high`); any name | counts / degrees | Lift: down 0, low 900, high 2400. Arm: stowed 0, score 90 (only when none are written) | yes, at once | Motor Finder (positions P1, P2...) | a new scoring height |

## Claw, Wrist and Positioner

<!-- tunables: ServoPositions -->
Section: the device's name in CamelCase (`claw` reads `Claw`, `intake_deploy` reads `IntakeDeploy`). Module: [Claw](../modules/robot.md#claw),
[Wrist](../modules/robot.md#wrist), [Positioner](../modules/robot.md#positioner).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `travelSec` | how long the servo takes for a full move, from position 0 to 1 (a shorter move: its share): a servo cannot say when it has arrived, so this is the guess | seconds | 0.3 | no | test (time a full move) | actions go on before it arrives (more) |
| `<position>` | every other number is a named position, 0 to 1. A Claw needs `open` and `closed` | servo position 0 to 1 | Claw: open 0.20, closed 0.75. Wrist: down 0.15, level 0.5, up 0.85. Positioner: stowed 0.0, deployed 0.5 (only when none are written) | yes, at once | Servo Finder (P1, P2...) | it does not open, close or reach far enough |

## Turret

<!-- tunables: Turret -->
Section: the device's name in CamelCase (`turret` reads `Turret`). Degrees, 0 = straight forward, counterclockwise +. Which lines
your turret needs: [Setting up a turret](turret-setup.md). Module: [Turret](../modules/robot.md#turret).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `min`, `max` | the range: it never turns past them (not used with `continuous()`) | degrees | min -90, max 90 | no | measure (turn by hand, read the angle) | the wires or the robot allow more or less |
| `startAngle` | where it points at INIT with no switch and no angle from the program before | degrees | 0 (the middle of `min` to `max` when 0 is outside it, with an INIT note) | no | put it there by hand | your start mark is not straight forward |
| `kP` | how hard it turns toward the goal, per degree off | power per degree | 0.02 | yes, at once | test | slow to aim (more) or shakes (less) |
| `kI` | a push that grows while it stays short | power | 0 | yes, at once | test | it stops a degree short |
| `kD` | brakes it as it gets close | power | 0.001 | yes, at once | test | it overshoots (more) |
| `maxPower` | the most power | 0 to 1 | 0.8 | no | test | it swings too hard |
| `tolerance` | how close counts as aimed | degrees | 1.0 | no | test | the launcher waits too long (more) |
| `slowZone` | it slows down within this of `min` and `max` | degrees | 10 | no | test | it hits its ends hard (more) |
| `slowZonePower` | the power at the very end of the slow zone | 0 to 1 | 0.25 | no | test | it stalls near an end (more) |
| `cameraOffset` | the camera is turned this much from the turret's forward (the turret's own; whole-robot aim uses `Tunables.Aim.cameraOffset`) | degrees | 0 | no | Aim Tuner | shots go a little to one side |
| `outOfRangeHold` | an odometry goal this far past a limit, behind the robot, makes it hold where it points | degrees | 10 | no | test | it swings limit to limit when the goal is behind |
| `keepAngle` | TeleOp starts from the angle the last program ended at (within 5 minutes) | true/false | true | no | none | you turn the turret by hand between programs: `false` |
| `homeAngle` | with a home switch: the turret's angle where the switch closes | degrees | REQUIRED with `homeSwitch(...)` | no | measure ([how](turret-setup.md)) | the switch moved |
| `homeDirection` | with a home switch: which way it turns to find it (+1 counterclockwise, -1 clockwise) | +1 / -1 | from `startAngle` toward `homeAngle` | no | none | it looks the long way round |
| `homingPower` | with a home switch: the power while it looks for it | 0 to 1 | 0.2 | no | test | homing is too slow (more) or hits hard |
| `homingTimeout` | with a home switch: stop with a fault if the switch is not found in time | seconds | 5 | no | test | a slow turret needs longer |
| `encoderOffset` | an absolute encoder: its reading with the turret straight forward | degrees | 0 | no | read it at INIT ([how](turret-setup.md)) | the encoder was moved |
| `posA`, `angleA` | a servo turret: one servo position and the turret's angle there | 0 to 1, degrees | REQUIRED for `Turret.servo` | no | Servo Finder | the servo or linkage changed |
| `posB`, `angleB` | a servo turret: a second position and its angle | 0 to 1, degrees | REQUIRED for `Turret.servo` | no | Servo Finder | the same |
| `travelSec` | a servo turret: time from `posA` to `posB` (not 0 to 1) | seconds | 0.6 | no | test (time it) | it fires before it arrives (more) |

## Interlock rules

<!-- tunables: Interlock -->
Section: the rule's name, `keepApart("TurretLift", turret.zone(), lift.zoneAbove())` reads `TurretLift`.
`<part>` is the part's section name (its device's name in CamelCase) with a small first letter: `Turret` -> `turretFrom`,
`LiftLeft` (device `lift_left`) -> `liftLeftAbove`, not `liftAbove` (INIT stops on it and says "did you mean liftLeftAbove?"). Each part's
own units. Read once at INIT, never in the tuning menu: a zone that moved while the parts move could
let them hit. Guide: [interlocks](interlocks.md). Module: [Interlock](../modules/robot.md#interlock).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `<part>From`, `<part>To` | `part.zone()`: the zone between two numbers | the part's units | REQUIRED with `zone()` | no | measure: move the part by hand to the edges | the parts or their places changed |
| `<part>Above` | `part.zoneAbove()`: the zone from there to the top | the part's units | REQUIRED with `zoneAbove()` | no | measure | the same |
| `<part>Below` | `part.zoneBelow()`: the zone from there to the bottom | the part's units | REQUIRED with `zoneBelow()` | no | measure | the same |
| `<part>Margin` | the extra safety distance around the zone (coasting is already covered by the stop distance) | the part's units | turret: the larger of 3 deg and 2 x its `tolerance`; lift, arm: the larger of 2 x its `tolerance` and 1/50 of its range; hood: 1/50 of its range; claw, wrist, positioner: 0.02 | no | test | the parts come too close (more) |
| `<part>StopSec` | a turret, lift or arm: how long it takes to stop from full speed once its power is cut. Its stop distance is its speed x (2 x the slowest of the last 25 loops + half this), see [interlocks](interlocks.md) | seconds, 0 to 2 | 0.15 | no | test: full stick toward the zone, see where it stops | it still ends up past its waiting point (more), or it waits too far out (less) |

## Launcher

<!-- tunables: Launcher -->
Section: the device's name in CamelCase: a launcher on the motor `launcher` reads `Launcher`, one on
`shooter` reads **`Shooter`** (not `Launcher`). Module: [Launcher](../modules/robot.md#launcher).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `speedRpm` | the shot speed when not set by a shot table. At most the motor's top speed | RPM | 2600 | yes, at once | Shot Tuner or test | shots land short or long |
| `stepRpm` | one D-pad press changes a fixed speed by this | RPM | 100 | yes, at once | none | the drivers want bigger or smaller steps |
| `readyBandRpm` | "at speed" means within this of the target | RPM | 60 | no | test | it waits too long to fire (more) or shots vary (less) |
| `idleRpm` | the speed between shots (0 = stop) | RPM | 0 | no | test | the first shot takes too long to spin up |
| `shotIntervalSec` | the least time between two shots | seconds | 0.25 | no | Flywheel Finder (the recovery) | quick shots land short (more) |
| `speedByDistance` | the shot table: rows of `{inches, rpm}`, or `{inches, rpm, hood}` with a [hood](#hood); blended between rows | inches, RPM | none: `speedRpm` | no | Shot Tuner | the field, the balls or the wheel changed |
| `speedByDistance<Name>` | one more table per game piece, e.g. `speedByDistancePollen` (used as "pollen") | inches, RPM | none | no | Shot Tuner | pieces fly differently |
| `kP` | the hub's speed control: push per RPM off. Used only when `kF` is written | hub units | 0 | no | Flywheel Finder | after the Flywheel Finder |
| `kI` | the hub's speed control: slow fix of a steady error | hub units | 0 | no | Flywheel Finder | the same |
| `kD` | the hub's speed control: brakes changes | hub units | 0 | no | Flywheel Finder | the same |
| `kF` | the hub's speed control: the steady power for a speed. Writing it turns the four gains on | hub units | 0 (not written: the SDK's own gains) | no | Flywheel Finder | the same |
| `nominalVolts` | the battery voltage the gains were found at (battery compensation), 9 to 14 | volts | 12.8 (INIT notes that it assumes this) | no | Flywheel Finder | after the Flywheel Finder |
| `batteryCompensation` | with `kF`: scale F by the battery voltage (not yet proven on a real robot) | true/false | false | no | none | to try it: `true`, then the 4-shot test (lesson 9) |
| `trimStepPercent` | one speed trim step | percent | 1 | no | none | the drivers want bigger steps |
| `trimMaxPercent` | the most speed trim, at most 50 | percent | 15 | no | none | you need more trim (better: fix the table) |

## Indexer

<!-- tunables: Indexer -->
Section: the device's name in CamelCase: `Indexer.servo("feeder")` reads **`Feeder`**, `windmill` reads `Windmill`
(not `Indexer`). Module: [Indexer](../modules/robot.md#indexer).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `rest` | a servo indexer: where it waits | 0 to 1 | 0.0 | yes, at once | Servo Finder | it blocks the pieces |
| `push` | a servo indexer: where it pushes a piece into the wheel | 0 to 1 | 0.5 | yes, at once | Servo Finder | pieces are not pushed far enough |
| `pushSec` | a servo indexer: time to push | seconds | 0.25 | yes, at once | test | it comes back before the piece is gone (more) |
| `returnSec` | a servo indexer: time to come back | seconds | 0.25 | yes, at once | test | the next piece jams on it (more) |
| `power` | a motor or CR servo indexer: power while it feeds (- = the other way) | -1 to 1 | 1.0 | yes, at once | test | pieces are fed too hard or too softly |
| `feedSec` | a motor or CR servo indexer: time for one piece | seconds | 0.4 | yes, at once | test | it feeds two (less) or none (more) |

## Hood

<!-- tunables: Hood -->
Section: the device's name in CamelCase (`hood` reads `Hood`, `shooter_hood` reads `ShooterHood`). A servo hood is in servo positions; a motor hood in
encoder counts. "Angle" below is your angle, in degrees or any scale you chose, when `angleA`, `angleB`
(or a `calibration`) are written; without them it is the position itself. Every
default that says "of the range" fits degrees, positions and counts. Module: [Hood](../modules/robot.md#hood).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `posA` | the position a hair off one hard stop: a hard limit | 0 to 1 / counts | REQUIRED, unless `calibration` | yes, at once | Hood Finder | the hood or its servo was moved |
| `posB` | the position a hair off the other hard stop | 0 to 1 / counts | REQUIRED, unless `calibration` | yes, at once | Hood Finder | the same |
| `angleA`, `angleB` | the hood's angle at `posA` and `posB` (both, or neither) | your angle unit | none: the angle is the position | no | Hood Finder | you want the table in degrees |
| `calibration` | instead of `posA`/`posB`: `{position, angle}` rows for a linkage; first and last rows are the stops | position, angle | none | no | Hood Finder | the hood is not a straight gear |
| `startAngle` | where it goes at START | angle | angle A | no | none | another start angle |
| `stepAngle` | one step of `step(n)` | angle | 1/25 of the range | no | none | the drivers want bigger steps |
| `byHandPerSec` | speed with the stick at full | angle per second | the range in 2.5 s | no | test | the stick is too fast |
| `trimMax` | the most hood trim | angle | 1/5 of the range | no | none | you need more trim |
| `tolerance` | a motor hood: how close counts as arrived | angle | 1/50 of the range | no | test | the launcher waits too long (more) |
| `travelSec` | a servo hood: time from stop to stop (`posA` to `posB`) | seconds | 0.5 | yes, at once | test (time it) | it fires before the hood arrives (more) |
| `kP` | a motor hood: power per count off | power per count | 0.005 | yes, at once | test | slow (more) or shakes (less) |
| `maxPower` | a motor hood: the most power | 0 to 1 | 0.5 | yes, at once | test | it hits its stops hard |

## Camera

<!-- tunables: Camera -->
Section: the device's name in CamelCase (`limelight` reads `Limelight`, `Webcam 1` reads `Webcam1`). Read once at INIT; not in the tuning
menu. Module: [Camera](../modules/robot.md#camera).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `pipeline` | a Limelight: the pipeline to use | number | 0 | no | the lighting test at INIT | the test found a better one at this venue |
| `autoLighting` | try a few settings at INIT and keep the best (`autoPipelines` / `autoExposure`) | true/false | true | no | none | `false` to skip the test at a venue you know |
| `exposureMs` | a webcam: a fixed exposure when the test is off (-1 = the camera's own) | ms | -1 | no | the lighting test at INIT | the test found a better one |
| `gain` | a webcam: the gain for a fixed exposure (-1 = the camera's highest) | number | -1 | no | test | pictures are too dark or noisy |
| `lightingSettleMs` | the test: wait after each switch | ms | 300 | no | none | pictures are still changing |
| `lightingPictures` | the test: pictures per setting | count | 10 | no | none | results vary |
| `lightingMinPictures` | the test: pictures with tags a setting needs | count | 3 | no | none | a bad setting wins |
| `lightingAfterStartMs` | the test: keep trying this long after START (no targets meanwhile) | ms | 0 | no | none | rarely |

## PieceSensor

<!-- tunables: PieceSensor -->
Section: each sensor's device name in CamelCase (`intake_color` reads `IntakeColor`). Module:
[Pieces and PieceSensor](../modules/robot.md#pieces-and-piecesensor). (`Pieces` itself reads no numbers.)

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `<KIND>` | one per kind, the same name as in `kinds(...)`, e.g. `POLLEN`: the kind's hue (colour sensor, camera) or the mm read with it (distance sensor) | degrees 0 to 360 / mm | none: that kind reads `UNKNOWN` (INIT says so) | yes, at once | Piece Tuner | new lights at a venue, or a moved sensor |
| `nearMm` | colour and distance sensors: a piece is there below this distance | mm | 40 (colour), 100 (distance) | yes, at once | Piece Tuner | it sees pieces that are not there |
| `hueTolerance` | colour sensor, camera: how far from a kind's hue still counts | degrees | 30 | yes, at once | Piece Tuner | kinds are mixed up (less) |
| `sizeToleranceMm` | distance sensor: how far from a kind's size still counts | mm | 8 | yes, at once | Piece Tuner | kinds are mixed up (less) |
| `minSaturation` | colour sensor, camera: how colourful it must be (0 to 1) | 0 to 1 | 0.35 (camera), 0 (colour) | yes, at once | Piece Tuner | the background counts as a piece (more) |
| `steadyMs` | a reading must stay this long before it counts | ms | 30 | yes, at once | test | flickers count as pieces (more) |
| `gapMm` | colour and distance sensors: a dip this big between two touching pieces counts as two (0 = off) | mm | 8 | yes, at once | test with two touching pieces | two pieces count as one |
| `gain` | colour sensor: its gain | number | the sensor's own | no | test | readings are too dark |

## Lights

<!-- tunables: Lights -->
Section: the device's name in CamelCase (`light` reads `Light`, `blinkin` reads `Blinkin`). Module: [Lights](../modules/robot.md#lights).

| Field | What it does | Units | Default | Live | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `allianceAtInit` | shows the alliance (red or blue) during INIT and flashes it at START. A `robot.lights.allianceAtInit(...)` line in Controls.java wins over this one | true/false | true | no | none | `false`: your rules show in INIT too |

---

## Parts with no Tunables

- **Pieces** (the counter): its numbers are in `Robot.java` (`max(4)`, `startsWith(...)`). Each sensor has its own section (above).
- **Pedro paths** ([pedro module](../modules/pedro.md)): Pedro's own constants file, not `Tunables.java`. Pedro uses `Tunables.Drive.maxPower` (or `practiceMaxPower`).
- **Shapes** (`looks(...)`), **part ids** (`part(...)`) and **gear ratios**: in `Robot.java`, because they describe the robot, not a number you tune.

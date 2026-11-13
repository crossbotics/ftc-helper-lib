# Tunables.java

How the numbers work: sections, names, `DEFAULT` and `MEASURE`, units, what INIT checks, and the tuning
menu. Each part's own numbers are on its page ([Each part's numbers](#each-parts-numbers)). Learn it step
by step: [Tunables.java](../five-files/02-tunables-java.md).

Every number has a default, except the ones marked **REQUIRED**. When Robot.java names the part's motor
or servo, the library takes the default from the [parts catalog](../guides/parts-catalog.md)
([First run](#first-run-what-you-get-before-tuning)). Copy only the lines you want to change.

The part pages list every number the library reads from `Tunables.java`. A script
(`tools/check_tunables.py`) reads the library's code and checks the pages against it, so the list is
complete: if a part reads a new number, the check fails until the number is on its page.

## Each part's numbers

| Section | Part page |
| --- | --- |
| `Drive` (fixed name) | [Drive](parts/drive.md#in-tunablesjava) |
| `Odometry` (fixed name) | [Odometry](parts/odometry.md#in-tunablesjava) |
| `CameraFix` | [Camera](parts/camera.md#camerafix) |
| `Aim` (fixed name) | [Aim](parts/aim.md#in-tunablesjava) |
| `Buttons` (fixed name) | [Controls.java](controls.md#buttons-in-tunablesjava) |
| the intake's device name | [Intake](parts/intake.md#in-tunablesjava) |
| the lift's device name | [Lift](parts/lift.md#in-tunablesjava) |
| the arm's device name | [Arm](parts/arm.md#in-tunablesjava) |
| the claw's device name | [Claw](parts/claw.md#in-tunablesjava) |
| the wrist's device name | [Wrist](parts/wrist.md#in-tunablesjava) |
| the positioner's device name | [Positioner](parts/positioner.md#in-tunablesjava) |
| the turret's device name | [Turret](parts/turret.md#in-tunablesjava) |
| the name in `keepApart("TurretLift", ...)` | [Interlock](parts/interlock.md#in-tunablesjava) |
| the launcher's motor name | [Launcher](parts/launcher.md#in-tunablesjava) |
| the indexer's device name | [Indexer](parts/indexer.md#in-tunablesjava) |
| the hood's device name | [Hood](parts/hood.md#in-tunablesjava) |
| the camera's device name | [Camera](parts/camera.md#in-tunablesjava) |
| each piece sensor's device name | [Pieces](parts/pieces.md#in-tunablesjava) |
| the light's device name | [Lights](parts/lights.md#in-tunablesjava) |

## Find a setting by what you want

This table moved to [Fix it by what you see](../tuning/17-fix-by-symptom.md) in the Tuning section.

## When you add a part

1. Add the part to `Robot.java` (one line, with its part number: `.part("gobilda-5203-435")`). The name
   in quotes (`Lift.slide("lift")`) is the device's name in the robot configuration on the Driver
   Station, exactly. Its section in `Tunables.java` is that name in CamelCase: `"lift"` reads `Lift`.
2. Press INIT. The part works with the library's starting values. INIT shows one note per part with the
   values it used.
3. If INIT stops and asks for a number (a lift's `min` and `max`, a turret's stops), add the part's
   section to `Tunables.java` with that number. The message names the tool that finds it.
4. Optional: write a line for each setting in the part's table, set to `DEFAULT`, with a short comment,
   and `MEASURE` for the numbers only your robot can give. Then the file shows every setting the part
   has, and you can change one when you need to. Leaving a line out does the same as `DEFAULT`. The
   [Cookbook](../cookbook.md) shows each part's section this way.

## What Tunables.java is

`Tunables.java` is the file with your robot's **numbers**: how far the lift goes, how fast the launcher
spins, where the claw opens. `Robot.java` says WHICH parts the robot has. `Tunables.java` says their
numbers. **Why a separate file:** you change numbers all season, and you can change them without
touching the code that works.

```java
public class Tunables {
    public static class Lift {                          // the part's section
        public static double min = 0, max = 2580;       // REQUIRED for a lift (Motor Finder)
        public static double kP = 0.004;                // optional: the default is used if you leave it out
        public static double down = 0, high = 2400;     // a lift's other numbers are named heights
    }
    public static class Team {                          // your own section: no part reads it
        public static double rumbleAtSec = 20;          // your Controls.java buzzes the gamepads then
    }
}
```

- **A section is a part.** `public static class Lift { ... }` holds the numbers of the part whose device
  is called `lift` in the robot configuration. The section's name is the device's name in CamelCase:
  each word starts with a capital letter, with no spaces and no `_`. `lift` reads `Lift`, `intake_deploy`
  reads `IntakeDeploy`, `Webcam 1` reads `Webcam1`, a launcher on the motor `shooter` reads `Shooter`,
  `Indexer.servo("feeder")` reads `Feeder`. It is never the module's kind (`Launcher`, `Indexer`), unless
  that is the device's name. To use another name, write `.tunables("Name")` on the part in `Robot.java`.
- **Some sections have fixed names:** `Drive`, `Odometry`, `Aim` and `Buttons`. An
  [interlock rule](parts/interlock.md) uses the name you give it in `keepApart("TurretLift", ...)`.
- **A field is one number.** Write it as `public static double name = value;` (`int` works too),
  `public static boolean name = true;` for an on/off setting, and
  `public static double[][] name = {{24, 2200}, {48, 2550}};` for a table. `public static` is needed: the
  library cannot read a field without it.
- **Names count, with their capitals.** `maxPower` is a setting. `MaxPower` and `maxpower` are not.
- **A part that is not on the robot** ([`optional()`](robot-java.md#parts-not-on-the-robot-yet), device
  not in the configuration) needs no section. Nothing about its numbers stops INIT, not even a Lift's
  `min`/`max`.
- **Safety:** a tuned position stays inside `min`..`max`. The library reads limits once, and you cannot tune them.
- **An autonomous's distances** are not here: they are in its steps
  ([why](../five-files/05-autos.md#the-numbers-are-in-the-step)).

### Your own sections

A section no part reads, like `Team` with a number your `Controls.java` uses:

- Your code reads it as usual: `Tunables.Team.rumbleAtSec`.
- Every `public static` number in it that is not `final` is in the tuning menu too ("Team/rumbleAtSec").
  The menu writes its value into the field. A TeleOp reads the field each time; an autonomous reads it
  when it builds its steps, at INIT.
- Tune it in the TeleOp and save it (A): your code uses it at once, with no rebuild. **Then copy the good
  value into `Tunables.java`** (INIT lists it). A new Control Hub loses a value saved only on the old hub.
- `final` numbers, true/false settings and tables are not in the menu. They stay as written.
- INIT never checks the names in your own sections.

### FTC Dashboard and the tuning menu

- `public static` fields: FTC Dashboard can show them too, **at home and in practice only**. Rule R704.D
  (2026-27) bans tools like FTC Dashboard at events, so take it out of the robot project before one
  ([tuning guide](../tuning/16-live-tuning.md#ftc-dashboard-practice-only)). The library's tuning menu is
  allowed.
- The menu's names ("Lift/kP") are shared by the whole robot app: keep one Robot class per app.

## What INIT checks

When you press INIT, the library reads `Tunables.java` and tells you what it found:

| You wrote | INIT does | Why |
| --- | --- | --- |
| no line for an optional number | uses the default; the INIT screen does not mention it (the robot log has `Tunables.Lift: kD, kF not written: library defaults used (fine)`) | a missing number is not a problem: the default works. Add the line to change it |
| no section at all for a part | uses every default; the robot log says so (not the screen) | the same |
| no line for a number that has a starting value from the parts catalog (a launcher's gains and speed, a lift's `kP` with `.part(...)`, a servo's `travelSec` with `.part(...)`) | uses it; the robot log has ONE note per part with the values, where they came from and the tool that tunes them: `Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, kP 1.17, kI 0.117, speed 3600 rpm. Run the Flywheel Finder, then the Shot Tuner` | a number worked out for YOUR motor works much better than a fixed one made for another motor. It is still a start: tune it, then write it ([First run](#first-run-what-you-get-before-tuning)) |
| no positions for a Claw, Wrist, Positioner or servo Indexer | uses the library's guesses and says so: `Claw: no positions written: open 0.2, closed 0.75 are library guesses, not your servo's: find yours with the Servo Finder before the first TeleOp` | guessed positions can push a part into the frame |
| no line for a REQUIRED number | **stops**: `Tunables.Lift has no max: write max (the top of the safe range, in counts: Motor Finder, B). A safety limit never comes from a library default.` | a wrong limit breaks the robot. A default could be wrong for your robot |
| a name in Lift, Arm, Claw, Wrist or Positioner that looks like a setting (`Max`, `k_P`, `maxPowr`) | **stops**: `Tunables.Lift.Max looks like "max" (capitals and spelling count)` | in these parts every other name is a named position, so a typo would quietly become a position |
| a name in Lift or Arm close to a gain that is not written (`kPP` with no `kP`) | runs, and INIT's note names it: the library uses it as a position, and the gain keeps its default | the same |
| a name no part reads, in any part's section (`inPowr` in `Intake`, `goalx` in `Aim`, a leftover `spinPower`) | **stops**: `Tunables.Intake.inPowr is not a setting of Intake (did you mean inPower?): the robot would quietly ignore it. Fix the name (capitals count) or delete the line. Known: inPower, outPower` | a misspelled line is never read, so the part would quietly use its default (a safety number too). The "did you mean" appears when the name looks like a real one |
| a section no part reads that is named like a part's kind (`Tunables.Launcher` for the launcher on `shooter`), or spelled almost like a part's section (`Tunables.Liftleft`) | **warns** (`!!`, every profile): `Tunables.Launcher is not read by any part: your launcher's section is Tunables.Shooter (its device name "shooter" in CamelCase). Move the numbers there` (or `did you mean Tunables.LiftLeft?`) | every number in it would be quietly ignored. INIT only warns here: a section no part reads may be your own (`Tunables.Team`), and a part's required numbers (a lift's `min`/`max`, a servo turret's `posA`...) already stop INIT when its real section is missing |

**The name check** covers `Drive`, `Odometry`, `Aim`, `Buttons`, every part on the robot and every
interlock rule. These names are yours to pick and always pass:

- named positions (any number in Lift, Arm, Claw, Wrist, Positioner);
- a piece sensor's kinds (`POLLEN`);
- a launcher's `speedByDistance...` tables;
- an interlock rule's `<part>From`, `<part>To`, `<part>Above`, `<part>Below`, `<part>Margin`,
  `<part>StopSec` for its own two parts.

A field of the part that your robot does not use (a servo turret's `posA` on a motor turret) passes too.
INIT never checks your own sections (`Team`), or the section of an `optional()` part that is not on the robot
(it may be unfinished). The part tables list every name the check knows.

### A name that looks like a setting

In Lift, Arm, Claw, Wrist and Positioner, every number that is not a known setting is a named position.
So these field names stop INIT (the setting of Claw, Wrist and Positioner is `travelSec`):

- **Always:** a known setting with other capitals or an `_` added: `Max`, `MAX` or `MIN` for
  `max`/`min`, `kp`, `KP` or `k_P` for `kP`. (All capitals is a common habit: it stops too.)
- **Only when that setting itself is not written:** for names of 5 letters or more, up to two letters off
  (`maxPowr` or `maxPwr` for `maxPower`). So `slowZone2` next to a written `slowZone` is a position, but
  `slowZon` with no `slowZone` stops INIT.

The message: `Tunables.Lift.Max looks like "max" (capitals and spelling count). Fix the name in Tunables.java; a position must not look like a setting.`
Older versions quietly made it a position called `Max`, and `max` stayed the library default.

### REQUIRED numbers

A safety limit never comes from a library default. These numbers are REQUIRED (none of them for a part
that is `optional()` and not on the robot):

- a Lift's or Arm's `min` and `max`;
- a Hood's `posA` and `posB` (or its `calibration`);
- a servo Turret's `posA`, `angleA`, `posB`, `angleB`;
- a Turret's `homeAngle` with a home switch;
- an interlock rule's zone numbers.

| The mistake | INIT stops with |
| --- | --- |
| `max` not written | `Tunables.Lift has no max: write max (the top of the safe range, in counts: Motor Finder, B). A safety limit never comes from a library default.` (an Arm's are in `deg`; `min` points to Motor Finder X) |
| no section at all | `Tunables.java has no Lift section: ...` |
| `max` not above `min` (Lift or Arm) | `Tunables.Lift: max (...) must be more than min (...)` |
| `min` or `max` not a `public static` number | `Tunables.Lift.max must be a public static number (like "public static double max = 1000;"): ...` |

**Check on the laptop:** `./gradlew :sim:fromRobot` (it writes the simulator's robot file) runs the same
name checks and prints what INIT would stop on or warn about. In a JUnit test, `robot.checkTunables()`
gives the same lines ([testing](../tests/03-checks-before-practice-and-matches.md)).

**Measured servo numbers and the servo's range.** You measure servo positions (named positions,
`posA`/`posB`, `rest`/`push`, a hood's `calibration`) for the pulse range the servo had at the time. Adding or
removing `.fullRange()` on a part in Robot.java changes where each position points: measure them again
([Full range](../modules/hardware.md#full-range)).

## DEFAULT and MEASURE

`DEFAULT` and `MEASURE` are two words you can write in place of a number you do not have yet. Then every
setting is in the file from day one, and the file shows what is still to do. Pick the word like this:

| The number | Write |
| --- | --- |
| You know it | the number |
| A safety limit (a lift's `min`/`max`, a stop) you do not know yet | `MEASURE` |
| A servo position only your robot can give (a fold-out's `stowed`/`deployed`, a claw's `open`/`closed`, a wrist's angles) | `MEASURE` |
| A tuning number (`kP`, a speed, a servo's `travelSec`...) you do not know yet | `DEFAULT` |

**Why positions are `MEASURE`:** the library's guesses (a Positioner's `stowed` 0.0 and `deployed` 0.5)
are not your servo's. With `DEFAULT` the servo goes to a guess at the first command after ▶ (start on the
Driver Hub), and a guess can push the part into the frame. With `MEASURE` your TeleOp and autos stop at
INIT until the [Servo Finder](../tuning/03-servo-finder.md) gives the numbers. A
part that is `optional()` and not plugged in does not stop, so the simulator still runs before the servo
is mounted. A wrong tuning number only makes a part slow or soft, so `DEFAULT` (a starting value from the
part) is fine there.

Add this line at the top of `Tunables.java`, under the `package` line and above `public class Tunables`:

```java
import static com.crossbotics.ftchelper.robot.Starting.*;

public static class Launcher {
    public static double kP = DEFAULT, kI = DEFAULT, kD = DEFAULT, kF = DEFAULT;
    public static double speedRpm = DEFAULT;
}
public static class Lift {
    public static double min = MEASURE, max = MEASURE;   // INIT stops until you write them
    public static double kP = DEFAULT;
    public static double down = 0, high = MEASURE;
}
```

| You write | INIT does |
| --- | --- |
| `DEFAULT` | uses the library's starting value, the same as leaving the line out. The library works it out from the part Robot.java names (the parts catalog, or your own part's numbers) where it can, else it uses its own default. In the PRACTICE profile, INIT shows one short line naming the numbers left at a LIBRARY guess (`Library starting values (DEFAULT): Launcher.stepRpm, Launcher.readyBandRpm`). The parts catalog's numbers (the seller's, such as a servo's speed) are fine as they are, so they go to the robot log only, and the warnings and the button list stay in view. The exception is control gains worked out from the catalog (a flywheel's kF/kP): INIT shows `Starting gains (worked out, not tuned): Launcher` in every profile. The robot log has each value and where it came from (`Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7, kP 1.17, kI 0.117, speed 3600 rpm. Run the Flywheel Finder, then the Shot Tuner`; `default from my-motor (custom)`; for a number with no part value: `Tunables.Launcher: DEFAULT, library defaults: stepRpm 100`), and so does the tuning menu. The tuning menu shows the same source next to the value. Its "copy into Tunables.java" lines are real numbers |
| `DEFAULT` and no part in Robot.java (or a part whose numbers are not known) | uses the general default, and shows a note that says so and how to do better: `Lift: no part in Robot.java, so general defaults (kP, kD, slowZone, tolerance made for a goBILDA 312 RPM motor). Add .part(...) (or Motors.custom(...)), or tune them in the tuning menu` |
| `DEFAULT` on a REQUIRED (safety) number | **stops**: `Tunables.Lift.max = DEFAULT, but it is a safety number: a safety limit never comes from the parts catalog or a library default. Write MEASURE until you know it, then the number (...)` |
| `MEASURE` | **stops** and names the tool that finds it: `Tunables.Lift.min = MEASURE: find it on the robot: the bottom of the safe range, in counts: Motor Finder, X. Write the number in place of MEASURE.` |

- Without the import line, the build fails with `cannot find symbol` and `symbol: variable DEFAULT` (or
  `MEASURE`).
- **The word, or no line:** both give the same starting value. Write the word: then the file shows what is
  still to tune.
- **The older name:** `DEFAULT_FROM_CATALOG` (v0.17.1 to v0.17.4) is the same word. Code that uses it
  keeps working. New code and these docs write `DEFAULT`.
- **Only on number lines.** Use these words only on lines that start `public static double` (an `int`
  cannot hold them). On `int`, `boolean` or `{ }` (table) lines, leave the line out instead.
- **In code:** `Starting.isDefaultFromCatalog(v)` and `Starting.isMeasure(v)` tell them apart from a
  number. Each is its own "not a number" bit pattern.

**When INIT stops on `MEASURE`:** only your TeleOp and your autos stop. The Motor Finder and the
Servo Finder still run: they do not read `Tunables.java`. For a lift's `min` and `max`:

1. On the Driver Station, choose **Motor Finder**, press INIT, then ▶ (start).
2. Put the lift at the bottom. Choose `lift` (D-pad, then A): its count starts at 0 there.
3. Move it with gamepad 1's right stick. Press **X** just above the bottom block (`min`) and **B** just
   below the top block (`max`).
4. It shows the numbers: `Tunables.Lift: min = 0, max = 2580`.
5. Write each number in place of `MEASURE`, build, and INIT your TeleOp again.

- A part that is `optional()` and not on the robot does not stop on `MEASURE`.
- **In the simulator,** INIT does not stop. `./gradlew :sim:fromRobot` writes a stand-in for each
  `MEASURE` in the simulator's robot file (`tunable.IntakeDeploy.deployed = 0.5`), and the simulator uses
  it in place of `MEASURE` ([MEASURE in the simulator](../guides/simulator.md#measure-in-the-simulator)).
  The real robot never reads that file, so it still stops.
- `:sim:newRobot` writes every part's settings this way.
- For a part not in the catalog, [describe it in Robot.java](robot-java.md#parts-not-in-the-catalog).

## First run: what you get before tuning

A new robot runs before any number is tuned. Every number has a safe starting value. When Robot.java
names the motor or servo (`Launcher.flywheel("launcher", "gobilda-5203-6000")`,
`.part("gobilda-5203-435")`), the library works out the starting value from that part's numbers in the
[parts catalog](../guides/parts-catalog.md) (the seller's numbers, with the date they were checked). INIT
shows one note per part with the values it used, where they came from and the tool that tunes them. A
number you write always wins. Writing [`DEFAULT`](#default-and-measure) does the same as leaving the line
out. **With no part named, or a part whose numbers are not known** (`Motors.unknown`, a custom part with
`Double.NaN`), the library uses the general default (the plain one in each part's table), and INIT says so.

> **Always name the part.** Write the part number on each part's line in `Robot.java`:
> `.part("gobilda-5203-1150")` on an intake, lift, claw or wrist, or the motor's id in the brackets on a
> launcher, an arm or a turret (`Launcher.flywheel("launcher", "gobilda-5203-6000")`). The number is on
> the motor's or servo's label; the [parts catalog](../guides/parts-catalog.md) lists them. **Why:**
> `DEFAULT` then starts from that part's real numbers (a lift's gains, a servo's move time, a flywheel's
> speed control), the simulator moves the part like the real one, and the code says what is on the robot.
> For a part not in the catalog, use [`Motors.custom(...)` or `Parts.customServo(...)`](robot-java.md#parts-not-in-the-catalog).
> A robot works without it, but INIT notes "no part in Robot.java, so general defaults".

| Part | Not written | What it starts with | Tune it with |
| --- | --- | --- | --- |
| Launcher | `kF`, `kP`, `kI`, `kD` | REV's formula with the motor's top speed: F = 32767 / (top RPM x counts per turn / 60), P = F / 10, I = P / 10, D = 0. A goBILDA 6000 RPM motor: kF 11.7, kP 1.17, kI 0.117. The library does not use the hub's own gains (the numbers the Control Hub uses when the code gives none: P 10, I 3, F 0): with no F, the wheel only creeps up to speed | [Flywheel Finder](../tuning/06-flywheel-finder.md) |
| Launcher | `speedRpm` | 60% of the motor's top speed, to 10 RPM (6000 RPM motor: 3600). This leaves room above it for the speed control to recover after a shot and on a tired battery, and gives a gentle first shot | [Shot Tuner](../tuning/13-shot-tuner.md) |
| Lift with `.part(...)` | `kP`, `kD`, `slowZone`, `tolerance` | the plain defaults (made for a goBILDA 312 RPM motor, 537.7 counts a turn) scaled to your motor's counts per turn, so it acts the same per motor turn. A 435 RPM motor: kP 0.0056, kD 0.00028, slowZone 179, tolerance 14 | the tuning menu ([Add a lift, step 4](../add-to-robot/01-lift.md#4-check-it-then-tune-it)) |
| Claw, Wrist, Positioner with `.part(...)` | `travelSec` | the servo's catalog speed at the hub's 5 V over its whole move (goBILDA 2000 torque: 270 degrees at 0.25 s per 60 = 1.13 s) | time a move and divide by its distance ([how](parts/claw.md#in-tunablesjava)) |
| Hood, servo Turret with `.part(...)` | `travelSec` | the same, over the move from `posA` to `posB` | time it |
| servo Indexer with `.part(...)` | `pushSec`, `returnSec` | the same, from `rest` to `push`, at least 0.25 s | watch a feed |
| Claw, Wrist, Positioner, servo Indexer | every position | the library's guesses, with a note: they are NOT your servo's | [Servo Finder](../tuning/03-servo-finder.md) before the first TeleOp |

**Never a starting value:** safety numbers stay REQUIRED (a lift's or arm's `min`/`max`, a hood's or servo
turret's stops, interlock zones, a turret's `homeAngle` with a switch). Battery compensation stays off
until you write your own `kF`.

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

A name that ends in `Sec` is seconds, `Ms` milliseconds, `In` inches, `Mm` millimeters, `Deg` degrees,
`Rpm` RPM, `Percent` percent.

## The "In tuning menu" column

You can change some numbers while the robot runs. In the PRACTICE profile, after ▶, hold **GUIDE** for 1 s.
Use the D-pad to choose and change, and **A** to save on the Control Hub ([tuning guide](../tuning/16-live-tuning.md)). The
numbers you tune most are [`Tunable`](../modules/tuning.md)s ("Lift/kP").

| In tuning menu says | What it means |
| --- | --- |
| **yes, at once** | in the tuning menu; the robot uses the new value in the same run |
| **yes, next INIT** | in the tuning menu; the robot uses the new value from the next INIT (it reads the number once, at INIT) |
| **no** | not in the tuning menu: change it in `Tunables.java` and install again. Limits are never in the menu, so tuning can never drive a part past a hard stop |

**Copy every tuned value back into `Tunables.java`.** The code is the record. The next INIT lists them
under "Tuned on this robot (copy into Tunables.java)", for example `Tunables.Lift.kP = 0.0050 (code 0.004)`.
**Why:** a new Control Hub loses a value saved only on the old one, and the file is the team's record.

## How to read a finder's lines

The [finders](../tuning/README.md) measure numbers on the robot and show the lines to copy. Each line
names the section first:

| The finder shows | Copy into Tunables.java |
| --- | --- |
| `Tunables.Lift: min = 0, max = 2580, P1 = 1200` | in `public static class Lift`: set `min`, `max` and a position. Rename `P1` to a real name (`high`) |
| `Tunables.Claw: P1 = 0.210, P2 = 0.770` | in `Claw`: rename them `open` and `closed` |
| `public static double[][] speedByDistance = {{24, 2200}, {48, 2550}};   // in Tunables.Launcher` | the whole line, into `Launcher` (replace the old one) |
| `Tunables.Odometry: checked = true` | in `Odometry`: `public static boolean checked = true;` |

The finders are: [Servo Finder](../tuning/03-servo-finder.md),
[Motor Finder](../tuning/05-motor-finder.md),
[Flywheel Finder](../tuning/06-flywheel-finder.md),
[Drive Tuner](../tuning/08-drive-tuner.md),
[Odometry Tuner](../tuning/09-odometry-tuner.md),
[Aim Tuner](../tuning/12-aim-tuner.md), [Shot Tuner](../tuning/13-shot-tuner.md),
[Hood Finder](../tuning/04-hood-finder.md), [Piece Tuner](../tuning/14-piece-tuner.md).
"Test" in the part tables means: try it on the robot and change it by hand.

## Parts with no Tunables

- **Pieces** (the counter): its numbers are in `Robot.java` (`max(4)`, `startsWith(...)`). Each sensor
  has its own section ([Pieces](parts/pieces.md#in-tunablesjava)).
- **Pedro paths** ([pedro module](../modules/pedro.md)): Pedro uses its own constants file, not
  `Tunables.java`. It also uses `Tunables.Drive.maxPower` (or `practiceMaxPower`).
- **Shapes** (`looks(...)`), **part ids** (`part(...)`) and **gear ratios**: in `Robot.java`, because they
  describe the robot, not a number you tune.

---

**Look it up:** [`Starting` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Starting.html)
(`DEFAULT`, `MEASURE`). **The other pages:** [Reference](README.md).

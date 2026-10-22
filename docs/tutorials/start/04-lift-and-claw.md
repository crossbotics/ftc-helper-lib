# Lesson 4: An intake, a lift and a claw

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** add three mechanisms to your robot (the lesson robot is the worked example), give them safe
numbers, and tune them on the robot.

**You need:**

- **Your own robot** from the earlier lessons (its five files in your TeamCode): a drive that works
  ([lesson 2](02-drive.md)) and the buttons from [lesson 3](03-buttons.md). The lesson robot is the
  worked example: read its lines, then make the same change in your files.
- On the robot: an intake motor, a lift motor with an encoder, a touch sensor at the bottom of the
  lift, and a claw servo. In the robot configuration they are called `intake`, `lift`,
  `lift_bottom` and `claw`.
- **Hard stops** on the lift at the top and the bottom (see step 4).
- About an hour, most of it for finding and tuning numbers.

The finished files: [`Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Robot.java)
and [`Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Tunables.java).

## 1. Three lines in Robot.java

**Did the Quick start?** The intake line (and its field) is there already: add only the lift and the
claw.

```java
// Lesson 4: the mechanisms.
intake = add(Intake.motor("intake"));                                             // Tunables.Intake
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom")); // Tunables.Lift
claw = add(Claw.servo("claw"));                                                    // Tunables.Claw
```

These go in `define()`, after the drive. Each line adds one **module**: a ready-made part class (a
mechanism the library already knows how to run).

- `Intake.motor("intake")`: a roller on one motor. It runs in or out.
- `Lift.slide("lift")`: a slide that goes up and down on one motor and holds named heights.
  - `.part("gobilda-5203-435")` says which motor it is, by its id in the
    [parts catalog](../../guides/parts-catalog.md). The simulator uses it to move like the real one.
  - `.bottomSwitch("lift_bottom")` is the touch sensor at the bottom (step 3 says why it matters).
- `Claw.servo("claw")`: a claw on one servo, with an `open` and a `closed` position.

Also add the fields at the top of the class, so the other files can reach the parts as `robot.lift`,
and an import for each part class at the top of the file (with the other imports):

```java
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Lift;

public class Robot extends RobotDefinition {
    public Intake intake;
    public Lift lift;
    public Claw claw;
    ...
```

A name still red? Alt+Enter (Option+Enter on a Mac) on it adds the import.

**Why:** each line says only *what* is on the robot. How the lift holds a height, how the claw
toggles, which buttons they use: the module already does that.

## 2. Their numbers in Tunables.java

Each module reads the section with its device's name in CamelCase (each word starts with a capital,
no `_`): `"lift"` reads `Tunables.Lift`.

```java
public static class Intake {
    public static double inPower = 1.0, outPower = 0.7;
}

public static class Lift {
    public static double min = 0, max = 2600;
    public static double kP = DEFAULT_FROM_CATALOG, kI = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, kF = DEFAULT_FROM_CATALOG;
    public static double down = 0, low = 900, high = 2400;
}

public static class Claw {
    public static double open = 0.20, closed = 0.75;
}
```

(`DEFAULT_FROM_CATALOG` comes from the line `import static com.crossbotics.ftchelper.robot.Starting.*;` at
the top of the lesson robot's `Tunables.java`: copy it too.)

What each number means:

- **Intake:** the power going in and going out, 0 to 1.
- **Lift heights are in encoder counts.** An encoder is a sensor on the motor that counts how far
  the shaft has turned. 0 is the bottom; bigger numbers are higher.
  - `min`, `max`: the safe range. The lift never goes outside it. You cannot change these from the
    tuning menu, so tuning can never push the lift past a stop.
  - `kP`, `kI`, `kD`, `kF`: how hard it pushes to reach and hold a height (step 6). These are the
    **PID** numbers (P = push harder the further away it is, D = brake as it gets close, I = fix a
    small error that stays). `kF` is the steady push that keeps the lift from sagging. **For the first
    run they say `DEFAULT_FROM_CATALOG`**: "not set yet, use the library's default". Robot.java names
    the lift's motor (`.part("gobilda-5203-435")`), so the library works them out for that motor from
    the parts catalog, and INIT says so: `Lift: defaults from the catalog (gobilda-5203-435): kP
    0.0056, kD 0.00028, slowZone 179, tolerance 14. Tune them live in the tuning menu`. Step 6 tunes
    them; then you write the numbers in their place. (Without `.part(...)` INIT says it uses general
    defaults, made for a goBILDA 312 RPM motor.)
  - `down`, `low`, `high`: **named heights**. Every other number in the section is a height, and you
    can pick any names. Your code then says `robot.lift.goTo("high")`.
- **Claw:** servo positions, 0 to 1. A claw needs both `open` and `closed` (INIT stops without them).

A number you leave out uses the library's default, and INIT tells you which. The lift has a few
more you can add: `maxPower` (1.0), `slowZone` (where it slows down near an end: about half a motor
turn, 179 counts on the 435 RPM motor), `slowZonePower` (0.2) and `tolerance` ("close enough" to a
height: 14 counts on the 435 RPM motor).

## 3. The buttons they bring

You write no buttons for them. With two drivers, all three are on gamepad 2:

| Button (gamepad 2) | Does |
| --- | --- |
| RT (hold) / LT (hold) | intake in / intake out |
| A / X / Y | lift to the lowest, second and highest height (`down`, `low`, `high`) |
| D-pad up / down | lift to the next height up / down |
| Left stick (up and down) | lift by hand; let go and it holds there |
| B | claw open / close |

INIT shows this map on the Driver Station. A button you give another job in `Controls.java`
replaces the module's suggestion, and INIT then says which job lost its button (`... no button (G2 A is
your "..." in Controls.java)`).

## 4. Why the bottom switch matters (homing)

The encoder only counts *changes*. It does not know where the bottom is. So the library needs a
known 0. **Homing** means finding that 0.

- **With a bottom switch:** pressing the switch sets the count to 0.
  Until the switch has been pressed once, the lift only goes **down, slowly** (at most 0.3 power),
  and the height buttons do nothing. The screen says `Lift: push it DOWN to the switch to home it`.
  If the lift starts resting on the switch, it homes on the first loop. Every later press fixes any
  drift (a slipping belt, a skipped tooth).
- **Without a switch:** 0 is wherever the lift is at INIT. Press INIT with the lift all the way down,
  every time (also for TeleOp after an autonomous that left it up). If it was 300 counts up,
  every height is 300 counts too high, and so is `max`. INIT reminds you.

**Hard stops vs software limits.** `min` and `max` are *software* limits: the program stops the motor
there. Software can be wrong: a bad number, a slipped encoder, a bug. Rule R105 says the robot must
stay inside its size limits *without software*, and inspectors check the maximum mechanical
extension. So the lift needs **physical blocks** at both ends. Set `min` and `max` just inside them:
the software stops the lift before the block, and the block holds if the software is wrong.

## 5. Find the real numbers with the finders

The numbers above are safe guesses. Measure the real ones before you drive the lift up with code:
a wrong `max` can pull the lift off its rails. Add the two finders to TeamCode, each in its own file
next to `Robot.java`. `ServoFinder.java`, the whole file:

```java
package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.finders.ServoFinderOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode {
}
```

`MotorFinder.java`, the whole file:

```java
package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.finders.MotorFinderOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Motor Finder", group = "Finders")
public class MotorFinder extends MotorFinderOpMode {
}
```

- **Servo Finder, for the claw:**
  1. Choose `claw` (D-pad, A).
  2. A servo cannot tell where it is, so choose a start point near where it sits now (D-pad; nothing
     moves), then press A: it moves there. X and B do nothing before this.
  3. Nudge it with the D-pad to each end, just before it strains. Press X at one end and B at the other.
  4. Press A fully open without straining, and A again fully closed on a game piece.

  Details: [Servo Finder](../../guides/finders.md#servo-finder-ends-and-positions).

  **Not enough turn?** On the hub's normal signal a goBILDA 2000 servo turns 270 degrees over 0 to 1,
  not the 300 its seller lists (the hub sends a narrower pulse than the servo can take). If your claw or
  wrist needs more, add `.fullRange()` in Robot.java (`Claw.servo("claw").fullRange()`) and press **Y**
  in the Servo Finder before choosing the servo. Decide this BEFORE you measure: the numbers change with
  the range. Most claws do not need it. ([Why](../../modules/hardware.md#full-range).)
- **Motor Finder, for the lift:** put the lift at the bottom first (the count starts at 0 there).
  Move it slowly with gamepad 1's **right** stick (up and down; the left stick does nothing here). Press X just before the bottom block and B just before the top
  block: those are `min` and `max`. Press A at each height you want.

Each finder shows a line that starts with `Tunables.`. It says which section of `Tunables.java` the
numbers go in:

```text
Tunables.Claw: P1 = 0.210, P2 = 0.770
Tunables.Lift: min = 0, max = 2580, P1 = 900, P2 = 2400
```

Copy the numbers into those sections, and give each P a real name:

```java
public static class Claw {
    public static double open = 0.210, closed = 0.770;
}

public static class Lift {
    public static double min = 0, max = 2580;
    public static double kP = DEFAULT_FROM_CATALOG, kI = DEFAULT_FROM_CATALOG, kD = DEFAULT_FROM_CATALOG, kF = DEFAULT_FROM_CATALOG;
    public static double down = 0, low = 900, high = 2400;
}
```

If the Motor Finder also says `Robot.java: add .reversed() to the "lift" part`, do that: the motor
counts down as the lift goes up. The other lines (`PositionServo.create(...)`) are for the building-block
classes of the "under the hood" tutorials, not the five files: skip them. The lines are also saved in `finder-results.txt` on the Control Hub. Steps and
buttons: [Finding your numbers](../../guides/finders.md#motor-finder-limits-positions-top-speed)
and [tutorial 21](../21-finders.md).

## 6. Tune the lift live

`kP` to `kF` and the named heights change **while the robot runs**, so you do not rebuild for each
try.

1. INIT the TeleOp with the **PRACTICE** profile, then press START. (The profile is chosen in any
   autonomous's INIT menu, on its last line, and the hub remembers it:
   [lesson 2, section 5](02-drive.md#5-the-numbers-tunablesdrive).)
2. **Hold GUIDE for 1 second:** the tuning menu opens on gamepad 1 (the wheels hold still).
3. D-pad up/down chooses a number (`Lift/kP`, `Lift/high`, `Claw/open`...), right/left changes it
   (hold a bumper for 10 times bigger steps). Use gamepad 2 to send the lift between heights and watch.
4. A saves on the Control Hub. **B shows the changed values as code**, like `Lift: kP = 0.005`
   (that is `kP = 0.005` in `Tunables.Lift`).
5. **Copy them into `Tunables.java`** (`kP = 0.005` in place of `kP = DEFAULT_FROM_CATALOG` in `Lift`: a
   written number replaces the default). The code is the record: a value saved only on the hub is lost
   with a new Control Hub and invisible to your teammates. Until you copy them, INIT lists them under
   "Tuned on this robot (copy into Tunables.java)".

The recipe (from [tutorial 07](../07-pid-lift.md#6-the-tuning-recipe)). Change one number, then send
the lift between `down`, `low` and `high`:

1. **P.** Raise `kP` until the lift moves quickly to each height. If it shakes or bounces around the
   height, go back down a step or two.
2. **D.** If it goes past the height and comes back, raise `kD` a little at a time until it stops
   doing that. Too much makes it slow or buzzy.
3. **F.** If it stops a little low and sags, raise `kF` until it holds still.
4. **I**, only if a small error stays: a very small `kI`.

Good: it reaches each height in under a second, goes past by very little, and holds still.

### A lift with two motors

Many slides have a motor on each side. Name the second one on the same line:

```java
lift = add(Lift.slide("lift_left").alsoMotorReversed("lift_right"));
```

Both motors now get the very same power, in the same loop, every time: buttons, the stick, stop, pause
and the limits. **Why one line, not your own code?** Two motors on one slide must never fight; code
that copies the power every loop misses the moments the library stops the lift (pause, STOP).

- Only `lift_left`'s encoder is read: plug the encoder cable into that motor.
- Its numbers are in `Tunables.LiftLeft` (the FIRST motor's name in CamelCase), not `Tunables.Lift`. If
  you write `Tunables.Lift` by mistake, INIT warns: `Tunables.Lift is not read by any part: your lift's
  section is Tunables.LiftLeft`.
- **Reversed or not?** Motors that face each other (mirror images, the usual way) turn opposite ways
  for "up": `alsoMotorReversed`. Check with the chain or belt OFF: push the stick up and watch both
  motors; each must turn the way that lifts. One wrong: use `alsoMotor` instead.

## Try it in the simulator

From the `ftc-helper-lib` folder, write a simulator robot file (the text file that describes the robot
to the simulator) from the lesson robot's `Robot.java`, then run it:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

For your own robot: `./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then
`./gradlew :sim:run -PteamCode=../TeamCode`. Open http://localhost:8780 and pick the TeleOp: for the
lesson robot "Lesson Bot TeleOp"; for your own robot, your own TeleOp's name (the `name = "..."` in
`Controls.java`). INIT, read the button map, START. The lesson robot's buttons, on gamepad 2: Y lifts,
A lowers, B opens and closes the claw, hold RT to run the intake (your own robot: the buttons on its
button map). The Control Hub panel shows the lift motor's count and the
`lift_bottom` switch: pressed at the bottom, so the lift homes at once. Before the first B the claw's
line says `Claw : not sent yet`: nothing moves until you ask. The first B opens it (in the simulator
the claw starts open, so it seems to do nothing); the next B closes it. ([Simulator guide](../../guides/simulator.md).)

## Check it on the robot

1. Wheels off the floor, hand near STOP. Lift at the bottom, on its switch.
2. INIT: no warnings about missing devices. START.
3. Gamepad 2 left stick up a little: the lift goes **up**. If it goes down, add `.reversed()` to the lift.
4. X, then Y, then A: each height is reached and held; the telemetry line shows `OK`.
5. B a few times: the claw opens and closes without humming (a humming servo is pushing on something).
6. RT: the intake pulls in. LT: it pushes out.

## If something goes wrong

| Problem | Why, and the fix |
| --- | --- |
| The lift only goes down, slowly | Not homed yet. Push it down to the switch once. Check the switch with the Hardware Check. |
| Every height is too high (no switch) | It was not at the bottom at INIT. Lower it, INIT again. |
| The lift goes down when it should go up | The motor counts the other way: `.reversed()` in `Robot.java`. |
| It sags below a height | `kF` too small (step 6). |
| It shakes at a height | `kP` too big, or `kD` too big. |
| The claw hums when closed | `closed` pushes too far: lower it a little with the Servo Finder or the tuning menu. |
| A tuned value is gone after a new install | It was only on the hub. Copy the B list into `Tunables.java`. |

## Under the hood

The modules use helpers you can also use on their own: PID control in
[tutorial 07](../07-pid-lift.md), limits, homing and the slow zone in [tutorial 08](../08-safe-lift.md),
and `PositionMotor`, `PositionServo` and `SpinMotor` in [tutorial 14](../14-mechanisms.md).
Every method of the modules: [the robot module](../../modules/robot.md#lift).

## Next

[Lesson 5: Actions: several parts together](05-actions.md).

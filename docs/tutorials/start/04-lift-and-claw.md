# Lesson 4: An intake, a lift and a claw

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** add three mechanisms to the lesson robot, give them safe numbers, and tune them on the robot.

**You need:**

- The lesson robot from the earlier lessons: a drive that works ([lesson 1](../22-robot-in-five-files.md)).
- On the robot: an intake motor, a lift motor with an encoder, a touch sensor at the bottom of the
  lift, and a claw servo. In the robot configuration they are called `intake`, `lift`,
  `lift_bottom` and `claw`.
- **Hard stops** on the lift at the top and the bottom (see step 4).
- About an hour, most of it for finding and tuning numbers.

The finished files: [`Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Robot.java)
and [`Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Tunables.java).

## 1. Three lines in Robot.java

```java
// Lesson 4: the mechanisms.
intake = add(Intake.motor("intake"));                                             // Tunables.Intake
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom")); // Tunables.Lift
claw = add(Claw.servo("claw"));                                                    // Tunables.Claw
```

These go in `define()`, after the drive. Each line adds one **module**: a mechanism the library
already knows how to run.

- `Intake.motor("intake")`: a roller on one motor. It runs in or out.
- `Lift.slide("lift")`: a slide that goes up and down on one motor and holds named heights.
  - `.part("gobilda-5203-435")` says which motor it is, by its id in the
    [parts catalog](../../guides/parts-catalog.md). The simulator uses it to move like the real one.
  - `.bottomSwitch("lift_bottom")` is the touch sensor at the bottom (step 3 says why it matters).
- `Claw.servo("claw")`: a claw on one servo, with an `open` and a `closed` position.

Also add the fields at the top of the class, so the other files can reach the parts as `robot.lift`:

```java
public Intake intake;
public Lift lift;
public Claw claw;
```

**Why:** each line says only *what* is on the robot. How the lift holds a height, how the claw
toggles, which buttons they use: the module already does that.

## 2. Their numbers in Tunables.java

Each module reads the section with its device's name in CamelCase: `"lift"` reads `Tunables.Lift`.

```java
public static class Intake {
    public static double inPower = 1.0, outPower = 0.7;
}

public static class Lift {
    public static double min = 0, max = 2600;
    public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
    public static double down = 0, low = 900, high = 2400;
}

public static class Claw {
    public static double open = 0.20, closed = 0.75;
}
```

What each number means:

- **Intake:** the power going in and going out, 0 to 1.
- **Lift heights are in encoder counts.** An encoder is a sensor on the motor that counts how far
  the shaft has turned. 0 is the bottom; bigger numbers are higher.
  - `min`, `max`: the safe range. The lift never goes outside it. You cannot change these from the
    tuning menu, so tuning can never push the lift past a stop.
  - `kP`, `kI`, `kD`, `kF`: how hard it pushes to reach and hold a height (step 6). `kF` is the steady
    push that keeps the lift from sagging.
  - `down`, `low`, `high`: **named heights**. Every other number in the section is a height, and you
    can pick any names. Your code then says `robot.lift.goTo("high")`.
- **Claw:** servo positions, 0 to 1. A claw needs both `open` and `closed` (INIT stops without them).

A number you leave out uses the library's default, and INIT tells you which. The lift has a few
more you can add: `maxPower` (1.0), `slowZone` (250 counts from an end where it slows down),
`slowZonePower` (0.2) and `tolerance` (20 counts: "close enough" to a height).

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
replaces the module's suggestion.

## 4. Why the bottom switch matters (homing)

The encoder only counts *changes*. It does not know where the bottom is. So the library needs a
known 0.

- **With a bottom switch:** pressing the switch sets the count to 0. This is called **homing**.
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
a wrong `max` can pull the lift off its rails. Add the two finders to TeamCode (the whole file
each):

```java
@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode {
}
```

```java
@TeleOp(name = "Motor Finder", group = "Finders")
public class MotorFinder extends MotorFinderOpMode {
}
```

- **Servo Finder, for the claw:** choose `claw`, move it until it is fully open without straining,
  and note the number. Then fully closed on a game piece. Those are `open` and `closed`.
- **Motor Finder, for the lift:** put the lift at the bottom first (the count starts at 0 there).
  Move it slowly with the stick. Press X just before the bottom block and B just before the top
  block: those are `min` and `max`. Press A at each height you want. If the finder says
  `.reversed()`, add `.reversed()` to the lift in `Robot.java`: the motor counts down as it goes up.

The finders print code for the older helpers (`.limits(0, 2580)`, `.position("P1", 1200)`).
Put the numbers into `Tunables.java` instead: `min = 0, max = 2580`, and give P1 a name such as
`low = 1200`. Steps and buttons: [Finding your numbers](../../guides/finders.md#motor-finder-limits-positions-top-speed)
and [tutorial 21](../21-finders.md).

## 6. Tune the lift live

`kP` to `kF` and the named heights change **while the robot runs**, so you do not rebuild for each
try.

1. INIT the TeleOp with the **PRACTICE** profile.
2. **Hold GUIDE for 1 second:** the tuning menu opens on gamepad 1 (the wheels hold still).
3. D-pad up/down chooses a number (`Lift/kP`, `Lift/high`, `Claw/open`...), right/left changes it
   (hold a bumper for 10 times bigger steps). Use gamepad 2 to send the lift between heights and watch.
4. A saves on the Control Hub. **B shows the changed values as code**, like `Lift: kP = 0.005`
   (that is `kP = 0.005` in `Tunables.Lift`).
5. **Copy them into `Tunables.java`.** The code is the record: a value saved only on the hub is lost
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

## Try it in the simulator

From the `ftc-helper-lib` folder, write a simulator robot file from the lesson robot's `Robot.java`,
then run it:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

For your own robot: `./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then
`./gradlew :sim:run -PteamCode=../TeamCode`. Open http://localhost:8780, pick "Lesson Bot TeleOp",
INIT, read the button map, START. On gamepad 2: Y lifts, A lowers, B opens and closes the claw,
hold RT to run the intake. The Control Hub panel shows the lift motor's count and the
`lift_bottom` switch: pressed at the bottom, so the lift homes at once. ([Simulator guide](../../guides/simulator.md).)

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

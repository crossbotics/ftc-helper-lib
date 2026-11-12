# Tutorial 14: Mechanism helpers

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** one program that runs a lift, a wrist, an intake and a spinner with the library's ready-made helpers.
The main track's parts are made from these helpers: [An intake, a lift and a claw](start/04-lift-and-claw.md).

**You will learn:** the four ready-made mechanism helpers, how each one connects to buttons in one line,
and how a `Subsystems` group runs them all. The four helpers are a motor that goes to positions, a motor
that spins, a servo with positions, and a continuous servo that spins round and round like a small
motor.

**You need:** some of these parts: motor `lift` (+ touch `lift_bottom`), servo `wrist`, motor `intake`,
continuous servo `spinner`. Delete the lines for parts you do not have. Do Tutorials 03 and 08 first.

**Full program:** [`Tut14Mechanisms.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut14Mechanisms.java)
**Reference:** [mechanism.md](../modules/mechanism.md)

---

## 1. Why helpers?

In tutorials 07, 08 and 12 you built a lift by hand: PID, limits, homing, named heights, hand control.
That is about 100 lines, and every season's robot needs it again. `PositionMotor` does that work in one
setup chain, and it is tested. You keep the understanding, and you skip the typing and the bugs.

## 2. A lift: `PositionMotor`

```java
PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
        .limits(0, 2600)                          // never below 0 or above 2600 ticks (1)!
        .lowerSwitch(hardwareMap, "lift_bottom")  // stops at the bottom and sets 0 there (2)!
        .slowZone(250, 0.2)                       // gentle near the ends (3)!
        .pid(0.004, 0, 0.0002, 0.05)              // from tutorial 07 (4)!
        .position("DOWN", 0)
        .position("LOW", 900)
        .position("HIGH", 2400);
```

1. Positions are encoder ticks. The position at INIT counts as 0, so start with the lift DOWN.
   The limits stop a bug or a wrong number from driving the lift into the end of its travel.
2. When the lift presses the switch, the position resets to 0. That fixes counting errors. Until the
   lift presses it once, the lift only goes DOWN, slowly. When `lift_bottom` is not in your
   configuration, the lift runs without a switch, and telemetry shows a warning.
3. Within 250 ticks of an end, the power drops to at most 0.2, so the lift does not slam. FTC
   robots also need a physical hard stop: [safety.md](../modules/safety.md).
4. kP, kI, kD, kF. The kF of 0.05 is a small steady push up that holds the lift against gravity.

Each line is one decision you know from earlier tutorials. The buttons:

```java
.bindPress(Pad.G1, Btn.DPAD_UP, lift::stepUp)          // next named height up
.bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
```

`lift::stepUp` is a method reference: a short way to write `() -> lift.stepUp()`. The actions only
CHOOSE a target. `all.update()` does the moving, so no button ever makes the robot wait.

For hand control, call `lift.manual(stick)` every loop. Push the stick: you drive the lift. Let go: the
lift **holds** where you left it (PID), instead of sagging.

## 3. A wrist: `PositionServo`

```java
PositionServo wrist = PositionServo.create(hardwareMap, "wrist")
        .position("STOW", 0.15)
        .position("LEVEL", 0.50)
        .position("DROP", 0.85)
        .maxSpeed(1.5);                           // at most 1.5 positions per second: no swinging
```

Servo positions go from 0 to 1. Find yours by testing. A sudden snap could fling a game piece. With
`maxSpeed(1.5)`, STOW to DROP (0.7) takes about half a second instead.

`wrist::next` and `wrist::previous` on the bumpers walk through the positions in the order you added
them. A servo does not move until your code tells it to. Here, `wrist.goTo("STOW")` does that right after
▶ (start on the Driver Hub).

## 4. An intake: `SpinMotor`

```java
SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);
...
.bindHold(Pad.G1, Btn.B, intake::out)
.bindRelease(Pad.G1, Btn.B, intake::stop)
```

`powers(1.0, -0.7)`: full power to pull pieces in, and a bit gentler out. So the intake pushes a piece
out gently and does not fire it. `in()`, `out()` and `stop()` are safe to call every loop, so the
trigger logic is plain. While the driver holds B, B's bindings are in charge, so the trigger leaves the
intake alone:

```java
if (!gamepad1.b) {
    if (gamepad1.right_trigger > 0.2) intake.in(); else intake.stop();
}
```

A jam is when a game piece gets stuck, and the intake stops turning while it has power. Add
`.jamDetection(80, 0.3, 0.4)` (with the encoder cable), and the intake backs a jam out by itself.

## 5. A spinner: `SpinServo`

```java
SpinServo spinner = SpinServo.create(hardwareMap, "spinner").power(0.8);
```

A continuous-rotation servo spins round and round like a small motor, instead of going to a position.
`power(0.8)` is its speed when your code tells it to go forward. In the configuration, set the port to
**Continuous Rotation Servo**.

## 6. One group for everything

```java
Subsystems all = new Subsystems().add(lift, wrist, intake, spinner);
...
all.update();             // every loop: PID, limits, slow moves, jam checks
all.addTelemetry(t);      // each part adds its own lines
...
all.stop();               // in finally
```

`all.stop()` stops the lift, the intake and the spinner. A normal servo like the wrist stays where it is.

## Run it

1. Put the robot on a block, with the lift at the bottom. Press ▶.
2. Telemetry shows each part. When the lift says "push it DOWN to home it", do that with the stick.
3. Press Y: the lift goes to HIGH. Push the stick: you take over. Let go: the lift holds.
4. Press the bumpers: the wrist walks through its positions slowly.
5. Right trigger: the intake runs in. Hold B: it runs out.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "No DcMotorEx named ..." | The program names a part you do not have. Delete its lines, and take it out of `all.add(...)`. |
| Lift only goes down, slowly | The lift waits for homing. Push it to the switch. |
| Lift goes the wrong way | Add `.reversed()` right after `create(...)`. |
| Wrist swings too fast / too slow | Change `maxSpeed`. |
| Spinner does not spin | The configuration says normal Servo for the port. Set it to Continuous Rotation Servo. |

## Try this

1. Add `.tunable("Lift")` at the end of the lift chain and tune it with tutorial 15.
2. Make the wrist go to "DROP" automatically when the lift reaches HIGH (`if (lift.isAt("HIGH"))`).
3. Use `PositionMotor` with `.units(...)` for an arm in degrees.

## Next

[Tutorial 15: Telemetry, profiles and tuning](15-telemetry-profiles-tuning.md)

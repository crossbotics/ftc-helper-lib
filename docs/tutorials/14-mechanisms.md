# Tutorial 14: Mechanism helpers

**You will learn:** the four ready-made mechanism helpers (a motor that goes to positions, a motor
that spins, a servo with positions, a continuous servo), how each connects to buttons in one line,
and how a `Subsystems` group runs them all.

**You need:** whatever you have of: motor `lift` (+ touch `lift_bottom`), servo `wrist`, motor
`intake`, continuous servo `spinner`. Delete the lines for parts you do not have. Tutorials 03, 08.

**Full program:** [`Tut14Mechanisms.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut14Mechanisms.java)
**Reference:** [mechanism.md](../modules/mechanism.md)

---

## 1. Why helpers?

In tutorials 07, 08 and 12 you built a lift by hand: PID, limits, homing, named heights, hand
control. That is about 100 lines, and every season's robot needs it again. `PositionMotor` is that
work, tested, in one setup chain. You keep the understanding; you skip the typing and the bugs.

## 2. A lift: `PositionMotor`

```java
PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
        .limits(0, 2600)                          // never below 0 or above 2600 ticks
        .lowerSwitch(hardwareMap, "lift_bottom")  // stops at the bottom and sets 0 there
        .slowZone(250, 0.2)                       // gentle near the ends
        .pid(0.004, 0, 0.0002, 0.05)              // from tutorial 07
        .position("DOWN", 0)
        .position("LOW", 900)
        .position("HIGH", 2400);
```

Each line is one decision you understand from earlier tutorials. Buttons:

```java
.bindPress(Pad.G1, Btn.DPAD_UP, lift::stepUp)        // next position up
.bindPress(Pad.G1, Btn.Y, () -> lift.goTo("HIGH"))
```

Hand control, every loop: `lift.manual(stick)`. Push the stick: you drive it. Let go: it **holds**
where you left it (PID), instead of sagging.

## 3. A wrist: `PositionServo`

```java
PositionServo wrist = PositionServo.create(hardwareMap, "wrist")
        .position("STOW", 0.15)
        .position("LEVEL", 0.50)
        .position("DROP", 0.85)
        .maxSpeed(1.5);                           // at most 1.5 positions per second: no swinging
```

`wrist::next` and `wrist::previous` on the bumpers walk through the positions in the order you
added them. A servo does not move until you tell it to: here, `wrist.goTo("STOW")` right after START.

## 4. An intake: `SpinMotor`

```java
SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);
...
.bindHold(Pad.G1, Btn.B, intake::out)
.bindRelease(Pad.G1, Btn.B, intake::stop)
```

`in()`, `out()` and `stop()` are safe to call every loop, so the trigger logic is plain:

```java
if (!gamepad1.b) {
    if (gamepad1.right_trigger > 0.2) intake.in(); else intake.stop();
}
```

Add `.jamDetection(80, 0.3, 0.4)` (with the encoder cable) and a jam backs itself out.

## 5. A spinner: `SpinServo`

```java
SpinServo spinner = SpinServo.create(hardwareMap, "spinner").power(0.8);
```

The port must be set to **Continuous Rotation Servo** in the configuration.

## 6. One group for everything

```java
Subsystems all = new Subsystems().add(lift, wrist, intake, spinner);
...
all.update();             // every loop: PID, limits, slow moves, jam checks
all.addTelemetry(t);      // each part adds its own lines
...
all.stop();               // in finally
```

## Run it

1. Robot on a block, lift at the bottom. START.
2. Telemetry shows each part. If the lift says "push it DOWN to home it", do so with the stick.
3. Y: lift to HIGH. Push the stick: you take over. Let go: it holds.
4. Bumpers: the wrist walks through its positions slowly.
5. Right trigger: intake in. B held: out.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "No DcMotorEx named ..." | a part you do not have: delete its lines, and from `all.add(...)` |
| Lift only goes down, slowly | it is waiting to be homed: push it to the switch |
| Lift goes the wrong way | add `.reversed()` right after `create(...)` |
| Wrist swings too fast / too slow | change `maxSpeed` |
| Spinner does not spin | the port is configured as a normal Servo |

## Try this

1. Add `.tunable("Lift")` at the end of the lift chain and tune it with tutorial 15.
2. Make the wrist go to "DROP" automatically when the lift reaches HIGH (`if (lift.isAt("HIGH"))`).
3. Use `PositionMotor` with `.units(...)` for an arm in degrees.

## Next

[Tutorial 15: Telemetry, profiles and tuning](15-telemetry-profiles-tuning.md)

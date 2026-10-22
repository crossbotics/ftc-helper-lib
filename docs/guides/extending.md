# Extending the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** students and mentors who add something new to the library. **You need:** comfort with Java
classes, and the library building on your laptop ([Contributing](../contributing.md#build-and-test)).

The library is meant to grow every season. This page shows the four places new things plug in,
with a complete example for each, and the rules that keep it easy for the next team.

Each place is an interface: a short list of methods a class promises to have. A class that
`implements` it writes those methods (each marked `@Override`), and then it plugs in anywhere the
library expects one.

## Where does my new code go?

```mermaid
flowchart TB
    Q{"Is it true for ANY FTC game?"} -- "no: this game only<br/>(tag ids, field spots, scoring)" --> T["TeamCode"]
    Q -- "yes" --> Q2{"Does a helper already almost do it?"}
    Q2 -- yes --> O["Add an option to that helper<br/>(a new setup method)"]
    Q2 -- no --> N["A new class in the library,<br/>implementing one of the interfaces below"]
```

## 1. A new mechanism: implement `Subsystem`

```java
/** A two-servo gripper that closes both sides together. */
public class DualGripper implements Subsystem {
    private final PositionServo left, right;
    private boolean closed = false;   // the state; the commands below only change this

    public DualGripper(HardwareMap hw) {
        left = PositionServo.create(hw, "grip_left").position("OPEN", 0.2).position("CLOSED", 0.7);
        // reversed(): the right servo is mounted mirrored, so the same numbers work for both
        right = PositionServo.create(hw, "grip_right").reversed().position("OPEN", 0.2).position("CLOSED", 0.7);
    }

    public void close() { closed = true; }
    public void open() { closed = false; }

    // update() turns the state into servo moves, every loop. "a ? b : c" = if a then b, else c.
    @Override public void update() {
        String p = closed ? "CLOSED" : "OPEN";
        left.goTo(p); right.goTo(p);
        left.update(); right.update();
    }

    @Override public void addTelemetry(RobotTelemetry t) { t.line("Gripper", closed ? "CLOSED" : "open"); }

    /** Autonomous step. */
    public AutoStep closeStep() {
        // close, then wait until both should have arrived, at most 1.0 s
        return ctx -> { close(); ctx.waitUntil(() -> left.isDone() && right.isDone(), 1.0); };
    }
}
```

It now works in `Subsystems`, in `EasyAuto`, with buttons, and shows telemetry like every other part.

## 2. A new autonomous action: implement `AutoStep`

```java
AutoStep alignWithWall = ctx -> {
    ElapsedTime timer = new ElapsedTime();   // a stopwatch, starts now
    try {                                    // try/finally: "finally" always runs
        while (ctx.isActive() && !wallSensor.isPressed() && timer.seconds() < 2.0) {   // a way out!
            drive.setPowers(new double[]{-0.2, -0.2, -0.2, -0.2});   // back up slowly
            ctx.loop();                                             // mechanisms + telemetry keep running
        }
    } finally {
        drive.stop();                                               // even if something throws
    }
};
routine.step("Back into the wall", alignWithWall);
```

Rules: check `ctx.isActive()` in every loop, call `ctx.loop()` (or `waitFor`/`waitUntil`) while
waiting, have a way out (a time limit), and stop the motors you started.

## 3. A new way to feed the launcher: implement `Feeder`

```java
/** A gate servo that opens briefly to let one piece roll in. */
public class GateFeeder implements Feeder {
    private final Servo gate;
    private double closeAt = -1;   // when to close the gate, seconds; -1 = not feeding
    public GateFeeder(HardwareMap hw) { gate = hw.get(Servo.class, "gate"); }

    // Never waits: feedOne() opens the gate and notes the time; update() closes it 0.2 s later.
    @Override public void feedOne() { if (!isBusy()) { gate.setPosition(0.8); closeAt = now() + 0.2; } }
    @Override public boolean isBusy() { return closeAt > 0; }
    @Override public void update() { if (closeAt > 0 && now() >= closeAt) { gate.setPosition(0.1); closeAt = -1; } }
    @Override public void stop() { gate.setPosition(0.1); closeAt = -1; }
    private static double now() { return System.nanoTime() / 1e9; }   // nanoseconds -> seconds
}
```

`new Launcher(flywheel, new GateFeeder(hardwareMap))`: everything else (ready checks, timing,
steps, telemetry) is unchanged.

## 4. A new camera or target type: implement `TargetSource`

Return `TargetObservation`s with **bearing positive to the LEFT**, distance in inches (or NaN),
and the picture time in `System.nanoTime()`. Then `AimController`, `Turret.aimBy` and
`Launcher.setDistance` all work with it. Look at `LimelightTags` as the model.

## The rules (so the next team can use it)

1. **One sign convention**: forward +, strafe right +, turn clockwise + for drive commands;
   headings, turret angles and bearings counterclockwise + ([Signs and units](../conventions.md)).
2. **Nothing blocks TeleOp**: commands are quick; work happens in `update()`. Only autonomous steps wait.
3. **Hardware setup leaves motors at 0 power.** Nothing moves before START.
4. **Safety stays on in every profile.** Limits, timeouts, jam checks.
5. **Testable**: put the logic in a part with no hardware, or take a clock (`LongSupplier`, a method
   that returns the time) and devices in the constructor (the code that makes the object), so tests can
   use pretend ones (see `library/src/test`).
6. **The six steps**: code, Javadoc, test, module page, tutorial, CHANGELOG ([Contributing](../contributing.md)).
7. **No game-specific numbers** in the library.

## Versions

Adding something new = a new middle number (0.4.0 -> 0.5.0). Changing how something old works =
a new first number, and a "Changed" note in the CHANGELOG that says what teams must change. Before
1.0, a change that breaks old code also only moves the middle number (v0.11.0 renamed the package), with
the steps for teams in the CHANGELOG.

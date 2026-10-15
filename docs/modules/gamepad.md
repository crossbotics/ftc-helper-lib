# Gamepad module

Package: `org.octobots.ftchelper.gamepad`

Turns gamepad buttons, sticks and triggers into robot actions, and gives the driver feedback
by rumble (vibration).

| Class | Use it to |
| --- | --- |
| [`ControllerBindings`](#controllerbindings) | say what each button does, in one place |
| [`StickShaping`](#stickshaping) | make sticks feel smooth: deadband, curves, slow-mode trigger |
| [`ToggleRumble`](#togglerumble) | buzz to confirm a toggle (2 buzzes = ON, 1 = OFF) |
| [`AimRumble`](#aimrumble) | buzz when an error is small, e.g. "aimed at the target" |

---

## ControllerBindings

### Why

Without it, every button needs code like this, to run once per press and not 50 times per second:

```java
boolean aWasDown = false;
...
if (gamepad2.a && !aWasDown) claw.setPosition(OPEN);
aWasDown = gamepad2.a;
```

With it, you write each button's job once, at the top, and the class does the rest:

```java
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G2, Btn.A, () -> claw.setPosition(OPEN));
...
controls.update(gamepad1, gamepad2);   // every loop
```

All of a robot's buttons are in one list, so it is easy to see what each button does
and to spot two jobs on one button.

### Names

- `Pad.G1` = `gamepad1` (usually the driver). `Pad.G2` = `gamepad2` (usually the operator).
- `Btn`: `A`, `B`, `X`, `Y`, `LB`, `RB` (bumpers), `BACK`, `START`, `GUIDE`, `L_STICK_BTN`,
  `R_STICK_BTN` (stick clicks), `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `TOUCHPAD`,
  `M1`, `M2` (extra paddles, see below).
- `Trigger.LT`, `Trigger.RT` = left and right trigger.

Add these imports to use the short names:

```java
import org.octobots.ftchelper.gamepad.ControllerBindings;
import org.octobots.ftchelper.gamepad.ControllerBindings.Btn;
import org.octobots.ftchelper.gamepad.ControllerBindings.Pad;
import org.octobots.ftchelper.gamepad.ControllerBindings.Trigger;
```

### The `() -> ...` part

`() -> claw.setPosition(OPEN)` is a **lambda**: a small piece of code you hand over to run later.
For more than one line, use braces:

```java
.bindPress(Pad.G2, Btn.B, () -> {
    claw.setPosition(OPEN);
    liftTarget = 0;
})
```

To change a variable inside a lambda, make it a field of your OpMode class (not a local variable
inside `runOpMode`).

### Methods

All `bind...` methods return the same `ControllerBindings`, so you can chain them.

| Method | Runs the action... |
| --- | --- |
| `bindPress(pad, btn, action)` | once, when the button goes down |
| `bindRelease(pad, btn, action)` | once, when the button comes up |
| `bindHold(pad, btn, action)` | every loop while the button is down |
| `bindToggle(pad, btn, onAction, offAction)` | each press flips ON/OFF. Starts OFF. Either action may be `null`. |
| `bindTriggerPress(pad, trigger, action)` | once, when the trigger goes past the threshold |
| `bindTriggerHold(pad, trigger, action)` | every loop while the trigger is past the threshold |
| `bindTriggerAxis(pad, trigger, use)` | every loop, with the trigger's value 0 to 1. Example: `v -> intake.setPower(v)` |
| `setTriggerThreshold(value)` | how far a trigger must be pulled to count as pressed. Default 0.5. |
| `registerExtraButton(pad, btn, reader)` | for controllers with extra paddles. `btn` must be `M1` or `M2`; `reader` tells how to read it. |

| Method | What it does |
| --- | --- |
| `update(gamepad1, gamepad2)` | **Call once every loop.** Reads the gamepads and runs the actions. |
| `ignoreHeldButtons(gamepad1, gamepad2)` | Call once right after `waitForStart()`. For a button or trigger held through START, NOTHING bound to it runs (press, release, hold or toggle) until it is let go once. Stops surprises. Trigger axis bindings are not affected. |
| `isDown(pad, btn)` | True if the button was down at the last `update`. |
| `clear()` | Removes all bindings. |

### Rules

- **Several actions can share a button.** They run in the order you added them.
  Example: `bindPress` and `bindHold` on the same button both work.
- **Actions must be quick.** Never use `sleep()` or a waiting loop inside an action:
  the whole robot freezes while it waits. Set a variable or a target instead, and let your loop do the work.
- A trigger and the bumper above it are separate. `bindTriggerPress(G1, RT, ...)` does not fire on `RB`.

### Tip: the SDK also has edge checks

FTC SDK 11 added `gamepad1.aWasPressed()`, `aWasReleased()` and so on. They are fine for one or two
buttons. `ControllerBindings` is better when you have many buttons, toggles, or two actions on one
button, and it keeps the whole button map in one place.

---

## StickShaping

### `StickShaping.shape(value, deadband, exponent)`

The usual one-line stick setup: a deadband with a smooth start, then a curve.

```java
double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
```

| Parameter | Meaning |
| --- | --- |
| `value` | the stick, -1 to 1 |
| `deadband` | values smaller than this become 0, so a stick that rests at 0.02 does not creep. 0.05 is usual. |
| `exponent` | 1 = straight (no curve). 2 = "squared": half stick gives quarter power, for fine control. 3 = softer still. |

### `StickShaping.curve(value, exponent)`

Only the curve part. The sign is kept: -0.5 squared gives -0.25.

### `StickShaping.slowScale(trigger, slowest)`

Turns a trigger into a speed multiplier. This is last season's "trigger brake".

```java
double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.25);
drive.drive(forward, strafe, turn, speed);
```

Not pressed gives 1.0 (full speed). Fully pressed gives `slowest` (0.25 = quarter speed).
Half pressed is in between (0.625).

---

## ToggleRumble

Two quick buzzes mean ON, one means OFF, so the driver knows a mode changed without looking.

```java
ToggleRumble confirm = new ToggleRumble(gamepad1);
controls.bindToggle(Pad.G1, Btn.Y,
        () -> { slowMode = true;  confirm.play(true);  },
        () -> { slowMode = false; confirm.play(false); });
```

| Method | What it does |
| --- | --- |
| `new ToggleRumble(gamepad)` | the gamepad to buzz |
| `setPattern(strength, stepMs, gapMs)` | the feel. Defaults: 0.8, 120 ms buzz, 80 ms gap. |
| `play(on)` | `true` = two buzzes, `false` = one buzz. A new one replaces one still playing. |
| `isPlaying()` | true while it still buzzes. Use it to hold back other rumble (see below). |

---

## AimRumble

Buzzes when an error is inside a threshold. Closer to zero = stronger and longer buzzes.
Last season this told the driver "you are aimed at the goal". (It was called `RumbleNotifier`.)

```java
AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(2.5);
...
// every loop:
if (!confirm.isPlaying()) aimFeel.update(headingErrorDeg);
```

| Method | What it does |
| --- | --- |
| `new AimRumble(gamepad)` | the gamepad to buzz |
| `setThreshold(value)` | buzzing starts when the error is this small. Default 2.5. |
| `setFeel(minStrength, maxStrength, minPulseMs, maxPulseMs, minCooldownMs, maxCooldownMs)` | "min" values are used at the edge of the threshold, "max" values at zero error. Defaults: 0.10, 0.65, 120, 200, 120, 350. The cooldown is the time from the START of one buzz to the start of the next, so at the edge (120 ms buzz, 120 ms cooldown) the buzzes run together. |
| `setEnabled(on)` / `isEnabled()` | turn buzzing on or off (for a driver who does not want it) |
| `update(error)` | call every loop. Returns true if it started a buzz. `NaN` (no target) does nothing. |

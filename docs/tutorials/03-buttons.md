# Tutorial 03: Buttons

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a test program that shows every kind of button job on telemetry.
The main track puts buttons in one file for you: [Buttons](start/03-buttons.md).

**You will learn:** every kind of button job (press, hold, release, toggle, triggers), how to
give the driver feedback with rumble, how to step through a list with the D-pad, and why the
"held through START" guard matters.

**You need:** a Control Hub and gamepads 1 and 2. No motors: everything shows on telemetry.

**Full program:** [`Tut03Buttons.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut03Buttons.java)
**Reference:** [gamepad.md](../modules/gamepad.md)

---

## 1. The problem buttons have

The loop runs about 100 times per second. If you write

```java
if (gamepad1.a) presses++;
```

one quick tap adds about 10, because A stays "down" for about 10 trips around the loop. You usually want
**one action per press**. Doing that by hand needs an extra variable for every button.
`ControllerBindings` does it for you, for every button at once, and keeps all the button jobs
in one list that is easy to read.

## 2. The kinds of jobs

```java
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.A, () -> presses++)          // once, when A goes down
        .bindHold(Pad.G1, Btn.B, () -> holdLoops++)         // every loop while B is down
        .bindRelease(Pad.G1, Btn.X, () -> releases++)       // once, when X comes up
        .bindToggle(Pad.G1, Btn.Y,
                () -> { slowMode = true; confirm.play(true); },     // 1st press: ON
                () -> { slowMode = false; confirm.play(false); });  // 2nd press: OFF
```

Which one to use:

| You want | Use | Example |
| --- | --- | --- |
| do something once | `bindPress` | open the claw, go to a set height |
| keep doing it while held | `bindHold` | run the intake while held |
| something when let go | `bindRelease` | stop the intake when the button is let go |
| a mode that flips on/off | `bindToggle` | slow mode, field-centric |

`Pad.G1` is gamepad 1 (the driver); `Pad.G2` is gamepad 2 (the operator).

A job must be quick. Never wait or sleep inside one: the whole robot freezes until it ends.
`bindToggle` starts OFF. The curly brackets `{ }` hold two steps in one lambda.

### Two jobs, one action

```java
.bindPress(Pad.G1, Btn.A, () -> presses++)
.bindPress(Pad.G2, Btn.A, () -> presses++)
```

Either driver can do it. You can also put two jobs on ONE button; they run in the order you added them.

## 3. Feedback with rumble

A toggle is invisible: the driver cannot tell if slow mode is on without looking at the screen.
`ToggleRumble` buzzes the gamepad: **two buzzes = ON, one buzz = OFF**.

```java
ToggleRumble confirm = new ToggleRumble(gamepad1);
...
() -> { slowMode = true; confirm.play(true); }
```

Use the same rule for every toggle on your robot, so drivers learn it once.

## 4. Choosing from a list with the D-pad

```java
static final String[] HEIGHT_NAMES = {"Floor", "Low", "Middle", "High"};
private int heightIndex = 0;
...
.bindPress(Pad.G1, Btn.DPAD_UP, () -> heightIndex = Math.min(heightIndex + 1, HEIGHT_NAMES.length - 1))
.bindPress(Pad.G1, Btn.DPAD_DOWN, () -> heightIndex = Math.max(heightIndex - 1, 0))
```

`HEIGHT_NAMES` is an **array**: a list of values in one variable. `heightIndex` is the
place in the list, counting from 0, so `HEIGHT_NAMES[0]` is "Floor".
`Math.min` and `Math.max` stop the number at the ends of the list, so it can never point past it.
Tutorial 12 turns this into a neat `LiftHeights` list with `up()` and `down()`.

## 5. Triggers

Triggers are not on/off; they give a number from 0 (let go) to 1 (fully pulled).

```java
.bindTriggerPress(Pad.G1, Trigger.RT, () -> shots++)          // once per pull past halfway
.bindTriggerAxis(Pad.G1, Trigger.LT, value -> leftTrigger = value)   // the value, every loop
```

- `bindTriggerPress` / `bindTriggerHold` treat the trigger as a button: "pressed" means past 0.5.
  Change that with `.setTriggerThreshold(0.8)` if drivers fire by accident.
- `bindTriggerAxis` hands you the number every loop. `value -> ...` is a lambda with one input.

## 6. The "held through START" guard

```java
waitForStart();
controls.ignoreHeldButtons(gamepad1, gamepad2);
```

Drivers often hold a button while the match starts. Without this line, a held button's press
job runs in the very first loop: a surprise action at the start of the match. With it, **any
button held at START does nothing until it is let go once.**

## 7. The loop

```java
while (opModeIsActive()) {
    controls.update(gamepad1, gamepad2);   // once per loop, before you use the results
    ...telemetry...
}
```

Without `controls.update(...)` no button does anything. This program has no `try`/`finally`:
it has no motors to stop.

## Run it

1. INIT. **Hold A**, press START, keep holding: the A count stays 0. Let go and press: 1.
2. Tap A quickly many times: exactly one per tap.
3. Hold B: the "loops held" count runs up fast. That is `bindHold`.
4. Press and hold X: nothing. Let go: +1. That is `bindRelease`.
5. Y: two buzzes and "Slow mode : yes". Y again: one buzz, "no".
6. D-pad up and down: the height name changes and stops at Floor and High.
7. Pull the right trigger past halfway: one shot per pull. Squeeze the left trigger: the number follows it.
8. Gamepad 2's A also counts.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Nothing happens | Is `controls.update(gamepad1, gamepad2)` inside the loop? Is the gamepad assigned (Start+A for gamepad 1, Start+B for gamepad 2)? |
| A counts many times per press | That job uses `bindHold`. Use `bindPress`. |
| No rumble | Some gamepads have no rumble motors. Logitech F310 does not rumble. |
| "Cannot assign a value to final variable" | The variable must be a **field** (see Tutorial 01). |

## Try this

1. Make the right bumper toggle a "turbo" mode with its own rumble confirmation.
2. Make the D-pad list wrap around: up from High goes to Floor. (Hint: `% HEIGHT_NAMES.length`. `%` gives the remainder after dividing, so it wraps 4 back to 0.)
3. Use `bindHold` and `bindRelease` together on gamepad 2's A: hold = "intake running", let go = "intake stopped".
4. Change the trigger threshold to 0.9 and feel the difference.

## Next

[Tutorial 04: Drive a mecanum robot](04-drive-mecanum.md) or
[Tutorial 05: Drive a standard (tank) robot](05-drive-tank.md)

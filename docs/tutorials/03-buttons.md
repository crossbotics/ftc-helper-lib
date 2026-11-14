# Tutorial 03: Buttons

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, each button is one line in `Controls.java`: [How buttons work](../controls/01-how-buttons-work.md).
Build the buttons by hand here with `ControllerBindings`, the layer under `Controls.java`, to see how a
press becomes one action.

**You will build:** a test program that shows every kind of button job on telemetry.

**You need:** a Control Hub and gamepads 1 and 2. You need no motors.

**Full program:** [`Tut03Buttons.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut03Buttons.java)

---

## 1. The problem buttons have

```java
if (gamepad1.a) presses++;
```

The loop runs about 100 times per second, and a quick tap keeps A "down" for about 10 of them. So this
line adds about 10 per tap. To get **one action per press** by hand, you need an extra variable for
every button. `ControllerBindings` does it for every button at once, and keeps all the jobs in one list.

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

| You want | Use | Example |
| --- | --- | --- |
| do something once | `bindPress` | open the claw, go to a set height |
| keep doing it while the button is held | `bindHold` | run the intake |
| something when the driver lets go | `bindRelease` | stop the intake |
| a mode that flips on/off | `bindToggle` | slow mode, field-centric |

- `Pad.G1` is gamepad 1 (the driver). `Pad.G2` is gamepad 2 (the operator). Bind the same job to both,
  and either driver can do it.
- A job must be quick. Never wait or sleep inside one: the whole robot freezes until the job ends.
- `bindToggle` starts OFF. The curly brackets `{ }` hold two steps in one lambda.
- Two jobs on ONE button run in the order you added them.

## 3. Feedback with rumble

A toggle is invisible: the driver must look at the screen to see whether slow mode is on.
`ToggleRumble` buzzes the gamepad: **two buzzes = ON, one buzz = OFF**. Use the same rule for every
toggle on your robot, so drivers learn it once.

```java
ToggleRumble confirm = new ToggleRumble(gamepad1);
```

## 4. Choosing from a list with the D-pad

```java
static final String[] HEIGHT_NAMES = {"Floor", "Low", "Middle", "High"};
private int heightIndex = 0;
...
.bindPress(Pad.G1, Btn.DPAD_UP, () -> heightIndex = Math.min(heightIndex + 1, HEIGHT_NAMES.length - 1))
.bindPress(Pad.G1, Btn.DPAD_DOWN, () -> heightIndex = Math.max(heightIndex - 1, 0))
```

`HEIGHT_NAMES` is an **array**: a list of values in one variable. `heightIndex` is the place in the
list, counting from 0, so `HEIGHT_NAMES[0]` is "Floor". `Math.min` and `Math.max` stop the number at the
ends of the list.

## 5. Triggers

Triggers give a number from 0 (let go) to 1 (fully pulled).

```java
.bindTriggerPress(Pad.G1, Trigger.RT, () -> shots++)          // once per pull past halfway
.bindTriggerAxis(Pad.G1, Trigger.LT, value -> leftTrigger = value)   // the value, every loop
```

- `bindTriggerPress` / `bindTriggerHold` treat the trigger as a button: "pressed" means past 0.5.
  When drivers fire by accident, change that with `.setTriggerThreshold(0.8)`.
- `bindTriggerAxis` hands you the number every loop. `value -> ...` is a lambda with one input.

## 6. The "held through ▶" guard

```java
waitForStart();
controls.ignoreHeldButtons(gamepad1, gamepad2);
```

Drivers often hold a button while the match starts (▶ is the start button on the Driver Hub). Without
this line, a held button's press job runs in the very first loop: a surprise action at the start of the
match. With it, **a button held at ▶ does nothing until the driver lets go of it once.**

## 7. The loop

```java
while (opModeIsActive()) {
    controls.update(gamepad1, gamepad2);   // once per loop, before you use the results
    ...telemetry...
}
```

Without `controls.update(...)`, no button does anything. This program has no `try`/`finally`,
because it has no motors to stop.

## Run it

1. INIT. **Hold A**, press ▶, keep holding: the A count stays 0. Let go and press: 1.
2. Tap A many times: exactly one per tap. Gamepad 2's A also counts.
3. Hold B: the count runs up fast (`bindHold`). Hold X: nothing. Let go: +1 (`bindRelease`).
4. Y: two buzzes and "Slow mode : yes". Y again: one buzz, "no".
5. D-pad up and down: the height name changes and stops at Floor and High.
6. Right trigger past halfway: one shot per pull. Left trigger: the number follows it.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Nothing happens | Check that `controls.update(gamepad1, gamepad2)` is inside the loop. Check that the gamepad is assigned (Start+A for gamepad 1, Start+B for gamepad 2). |
| A counts many times per press | That job uses `bindHold`. Use `bindPress`. |
| No rumble | Some gamepads have no rumble motors. The Logitech F310 does not rumble. |
| "Cannot assign a value to final variable" | The variable must be a **field** ([Tutorial 01](01-opmode-basics.md#a-field-for-what-a-button-changes)). |

## Try this

1. Make the right bumper toggle a "turbo" mode with its own rumble confirmation.
2. Make the D-pad list wrap around: up from High goes to Floor. (Hint: `% HEIGHT_NAMES.length`. `%` gives
   the remainder after dividing, so it wraps 4 back to 0.)
3. Use `bindHold` and `bindRelease` together on gamepad 2's A: hold = "intake running", let go =
   "intake stopped".

---

**Next:** [Tutorial 04: Drive a mecanum robot](04-drive-mecanum.md) or
[Tutorial 05: Drive a standard (tank) robot](05-drive-tank.md).
**Look it up:** [gamepad.md](../modules/gamepad.md).

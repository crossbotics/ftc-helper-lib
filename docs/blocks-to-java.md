# From Blocks to Java: where each block went

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

This page is for you if you have made programs with blocks (Scratch, FTC Blocks, or similar) but not
with Java. It shows where each block you know went in the robot's five files. Read it before or
together with [Lesson 3: Buttons](tutorials/start/03-buttons.md).

**Before this:** the [Quick start](quick-start.md) (you have the five files in your TeamCode). New to
Android Studio? [Start from zero](start-from-zero.md) first.

## The key idea: you never write the loop

In Scratch, a game is a `forever` loop: every time round, it checks the keys and moves the sprite.
A robot program is the same inside: a **loop** that runs many times a second. Each time round (each
"loop"), it reads the gamepads, tells every motor what to do, and updates the Driver Station.

**With this library, you do not write that loop.** The library runs it for you. Your job is
different:

1. `buttons(...)` in `Controls.java` runs **once**, at INIT.
2. Each line in it **plugs a job into a button**, like snapping a script under a
   `when [space] key pressed` hat block. Nothing runs yet.
3. After START, the library's loop watches the buttons and **runs the jobs** at the right moment.

```java
@Override
protected void buttons(Robot robot, Pad g1, Pad g2) {
    g2.a.press(robot.claw::open).says("open claw");          // when G2 A pressed: open the claw
    g2.rt.hold(robot.intake::in, robot.intake::stop);        // while G2 RT held: in; let go: stop
}
```

(The claw line needs a claw in `Robot.java`; the Quick start robot has only an intake.)

**Why it is built this way:** in one loop, many jobs share the robot. If one job stops to wait, the
whole robot stops: no driving, no other button. So **a job must never wait.** Waits happen only inside
an **Action** (a list of steps that runs a little every loop, [lesson 5](tutorials/start/05-actions.md))
and in **autonomous steps** ([lesson 6](tutorials/start/06-first-autonomous.md)).

## The map: block to Java

| Block | In Java with this library | Where |
| --- | --- | --- |
| `forever` / `repeat until` / `while opModeIsActive()` | Written for you: the library's loop. Of your lines, only `hold(...)`, sticks' `use(...)`, `rumbleWhen(...)` and `show(...)` run every loop. | inside `ControlMap` (the library) |
| `when green flag clicked` (the match starts) | `atStart(() -> Actions.deployIntake(this));` | `Robot.java`, in `define()` |
| `when [key] pressed` | `g2.a.press(job);` | `Controls.java`, in `buttons(...)` |
| `when [key] released` | `g2.a.release(job);` | `Controls.java` |
| `if <key pressed?> then ... else stop` (while held) | `g2.rt.hold(robot.intake::in, robot.intake::stop);` | `Controls.java` |
| on / off switch (toggle) | `g2.b.toggle(robot.claw);` | `Controls.java` |
| `wait 0.5 seconds` (in TeleOp) | **Not** `sleep(500)`. In an Action: `Action.named("Spit out").then(r.intake::out).waitSeconds(0.5).then(r.intake::stop)` | `Actions.java` |
| `wait until <...>` | `.waitUntil("lift at the top", r.lift::isAtTarget, 2.0)` (2.0 = give up after 2 s) | `Actions.java` |
| a stack of blocks, run in order | An Action: `Action.named("...").then(a).waitSeconds(t).then(b)`. In an autonomous: `s.start(...).move(...).action(...)` | `Actions.java`, `autos/` |
| `define my block` (a custom block) | `public static Action spitOut(Robot r) { return ...; }` | `Actions.java` |
| the slot inside a C-block | a **lambda** `() -> { a(); b(); }`, or a **method reference** `robot.intake::out` | anywhere a job goes |
| `set [maxPower] to 0.4` (a number variable) | `public static double maxPower = 0.4;` read as `Tunables.Drive.maxPower` | `Tunables.java` |
| `set motor power` / `run motor` | `robot.intake.in()`, `robot.lift.goTo("high")`. The drive motors need no code. | jobs and Actions |
| `joystick` (a reporter block) | `g2.leftStickY.use(robot.lift::manual);` (the stick's value, -1 to 1, every loop) | `Controls.java` |
| `if <sensor> then rumble` | `g2.rumbleWhen(robot.intake::isRunning);` (a buzz each time it becomes true) | `Controls.java` |
| `say` / `show variable` | `show("Lift ticks", robot.lift::position);` (the value on the Driver Station, every loop) | `Controls.java`, in `buttons(...)` |
| `stop all` | Tap gamepad 1 **BACK** (pause; tap again to go on), or **STOP** on the Driver Station. | the drive team |

Notes on the table:

- **Autonomous steps DO run in order and wait**, like a stack of blocks. `s.move(...)` finishes
  before the next step starts. **Why the difference:** in an autonomous, no driver is waiting for a
  button; the library still keeps every part working while a step waits.
- **Sticks give numbers.** Up and right are `+`, from -1 to 1. `robot.lift::manual` gets that number
  every loop.
- **A number in `Tunables.java` can be changed live:** in the PRACTICE profile, hold GUIDE for 1 s to
  open the tuning menu ([Lesson 1](tutorials/22-robot-in-five-files.md#2-tunablesjava-the-numbers)).
  Then copy the good value back into `Tunables.java`.
- `atStart(...)` comes from the example robot
  [`flywheelbot/Robot.java`](../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot/Robot.java):
  its `deployIntake` Action drops the intake into place once, right after START (never in INIT).
- `show(...)` goes inside `buttons(...)`, like `rumbleWhen`, as a line of its own. It is in the
  TeleOp (`Controls.java`) only.

## Never sleep() or a while loop in a button job

This compiles, but it is wrong:

```java
// WRONG: the whole robot freezes for half a second
g2.dpadLeft.press(() -> { robot.intake.out(); sleep(500); robot.intake.stop(); });
```

`sleep(500)` stops the library's loop for 0.5 s. During that time, nothing else runs: no driving, no
other button, no safety checks. A `while (...) { }` loop in a job does the same, maybe forever. The
Driver Station then shows a warning like this:

```text
... took 512 ms: the whole robot stopped that long. No sleep() or while loop in a button job: use an Action with waitSeconds (lesson 5)
```

Do this instead (the Quick start's `spitOut` in `Actions.java`):

```java
public static Action spitOut(Robot r) {
    return Action.named("Spit out")
            .then(r.intake::out)
            .waitSeconds(0.5)
            .then(r.intake::stop);
}
```

and in `Controls.java`: `g2.dpadLeft.press(Actions.spitOut(robot)).says("spit out");`.
**Why it works:** the Action waits "inside itself". Each loop it checks the time and lets the loop go
on, so the robot keeps driving.

## Java punctuation: a short guide

- **`;`** ends a statement (one command), like a full stop. Every line inside `buttons(...)` ends with
  one.
- **A chain goes on over several lines.** A line that starts with `.` continues the line above it:

  ```java
  return Action.named("Spit out")     // no ; here: the statement goes on
          .then(r.intake::out)
          .waitSeconds(0.5)
          .then(r.intake::stop);       // the ; is at the very end
  ```

- **`{ }`** hold a group of lines together, like the inside of a C-block. Every `{` has its own `}`.
- **`"quotes"`** are text, used as it is: a name on the Driver Station, a motor name, a lift height
  (`"high"`). Words without quotes are names in the code (`robot`, `intake`, `goTo`).
- **`return`** hands a result back. `spitOut` builds an Action and `return`s it, so the button can
  use it.
- **`r` or `robot`?** Only a name. A method names its inputs in its first line:
  `spitOut(Robot r)` calls its robot `r`; `buttons(Robot robot, Pad g1, Pad g2)` calls it `robot`. It
  is the same robot. **Why short `r`:** Actions use it on every line.
- **`this`** means "this object, the one this code is in". In `Robot.java`, `this` is the robot, so
  `atStart(() -> Actions.deployIntake(this))` hands the robot to the Action.
- **`() ->`** starts a lambda: code you hand over now, to run later. **`::`** is a method
  reference: `robot.intake::out` is the short way to write `() -> robot.intake.out()`.

## Next

- [Lesson 3: Buttons](tutorials/start/03-buttons.md): every kind of button job.
- [Lesson 5: Actions](tutorials/start/05-actions.md): steps that wait, safely.
- [The button menu](guides/buttons.md): every way a button can work.

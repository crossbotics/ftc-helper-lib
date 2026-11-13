# What the drivers see and feel

Words on the button map, buzzes, lights, and values on the screen.

The drivers watch the robot, not the Driver Hub. A buzz in their hands or a light on the robot tells them
"done" or "ready", and they do not look away. The screen is for the pit and for practice.

## Feedback on a button

**Where:** `Controls.java`, at the end of a button line.

| You want | Write | What the drivers get |
| --- | --- | --- |
| Words on the button map | `.says("fire one")` | the line `G2 RB: fire one` at INIT and when the driver taps GUIDE |
| A buzz when the job runs | `.rumble()` | one short buzz (a hold: when it starts) |
| More buzzes | `.rumble(2)` (1 to 3) | that many short buzzes |
| On / off you can feel | `.toggle(part).rumble()` | two buzzes = on, one buzz = off (a claw: two = closed). The same for a [mode](01-how-buttons-work.md#modes) |

`.says(...)` and `.rumble()` belong to the gesture right before them.
`g2.rb.tap(a).says("one").doubleTap(b).says("three").rumble()` buzzes on the double tap only.

**Always add `.says(...)`.** The button map is the only list of every button that the drivers can see.

## Feedback from the robot

These lines are not tied to a button. Each one is a line of its own inside `buttons(...)`, ends with `;`,
and is not on the button map.

**Where:** `Controls.java`, inside `buttons(...)`.

| You want | Write | What the drivers get |
| --- | --- | --- |
| A buzz when something becomes true | `g2.rumbleWhen(robot.launcher::isReady);` | a buzz each time the test becomes true (`rumbleWhen(test, 2)`: two buzzes) |
| A color on the robot | `robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);` | a light while the test is true ([Lights](../reference/parts/lights.md)) |
| A value on the Driver Hub | `show("Lift ticks", robot.lift::position);` | the value, read again every loop |

**`show(...)`:** you see what the robot "thinks" while you drive. The value must be fast to read: use a
method reference or a small lambda. With the Quick start robot, try `show("Intake on", robot.intake::isRunning);`.

**In the simulator,** a buzz shakes the gamepad picture. A real controller or a phone buzzes too.

## The button map and the TeleOp's INIT screen

- **At INIT** the Driver Hub shows the whole button map, one line per button, with the part that gave it
  (`G2 A: down [Lift]`). Then it lists the free buttons (`G2 free: ...`). Read it before every match.
- **In the match,** tap GUIDE to see the map again. The robot still drives. Tap GUIDE again to close it.
- **The alliance:** in INIT, gamepad 1 X = BLUE, B = RED. After an autonomous, its alliance is already set.
- **The INIT menu:** D-pad up/down picks **Alliance** or **Profile**, and left/right changes it. A practice
  that starts straight in TeleOp can switch to PRACTICE here. When your own INIT code uses gamepad 1's
  D-pad, turn the menu off in `Controls.java`: `@Override protected boolean initMenu() { return false; }`.

## Done when

- Each button line has a `.says(...)`, and the button map shows every button.
- The drivers feel a buzz for an on / off switch that matters in a match or is easy to miss, like a
  mode they cannot see.
- A light or a buzz tells the drivers when the robot is ready to score.

---

**Next:** [Move the library's buttons](04-move-library-buttons.md).

**Look it up:** [Controls.java reference](../reference/controls.md).
**Go deeper:** [the cookbook's button recipes](../cookbook.md#buttons-and-gestures).

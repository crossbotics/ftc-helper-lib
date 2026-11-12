# Move the library's buttons

Put the library's own jobs (driving, pause, the menus) on other buttons, or free them.

**Why move one:** some drivers want slow on the right trigger, or a part needs Y. When you move the
library's job, its button is free for your own line in `buttons(...)`.

## Move or free a button

The library's own jobs (driving, pause, menus...) have default buttons. You can move every one to another
button, or free it (`null`: no button). Do it in one method next to `buttons(...)`.

**Where:** `Controls.java`, next to `buttons(...)`.

```java title="Controls.java"
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.a);                  // field-centric on/off: A instead of Y
    lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
    lib.pause(g1.x);                         // pause on X (BACK still resets forward; lib.resetForward(null) frees it)
    lib.switchAlliance(g1.start, g1.back);   // turn ON the alliance switch: hold START, then BACK, 1.5 s
}
```

Add `import com.crossbotics.ftchelper.teleop.LibraryButtons;` at the top. In your own `EasyTeleOp`, write
`libraryButtons(LibraryButtons lib)`, with `Key.g1(Btn.X)` instead of `g1.x`.

## Every library button

| Job | One gamepad (the start) | Two gamepads | How it works | Move it with |
| --- | --- | --- | --- | --- |
| Drive / strafe, turn | left stick, right stick X | same | every loop | `lib.swapSticks(true)`: right stick drives, left stick X turns |
| Slow | none | G1 LT | the further you pull it, the slower (a button: slow while you hold it) | `lib.slow(key)` |
| Slow on/off | G1 left stick click | none | a press | `lib.slowToggle(key)` |
| Field-centric on/off | G1 right stick click | G1 Y | a double tap (mecanum with an IMU or odometry) | `lib.fieldCentric(key)` |
| Pause / go on | G1 BACK | same | pauses at the press, and a tap goes on | `lib.pause(key)` |
| Reset "forward" | G1 BACK | same | held 1 s, field-centric only (a tap if its button has no other quick job) | `lib.resetForward(key)` |
| Debug lines | G1 START + BACK | same | hold START, then press BACK. It runs when you let go | `lib.debug(key, with)` (`with` null: one button) |
| Switch alliance | **off** | off | START, then BACK, both held 1.5 s, after you turn it on | `lib.switchAlliance(g1.start, g1.back)` turns it on |
| Set pose here | G1 START | same | held 1 s with the drive sticks let go (robots with odometry). It also works in INIT | `lib.setPose(key)` |
| Button map | G1 GUIDE | same | a tap | `lib.buttonMap(key)` |
| Tuning menu | G1 GUIDE | same | held 1 s (a tap if its button has no other quick job) | `lib.tuningMenu(key)` |
| Choose the alliance (INIT only) | G1 X = BLUE, G1 B = RED | same | a press, before ▶ (start on the Driver Hub) | `lib.allianceInInit(blue, red)` |

**BACK, step by step.** A press of BACK pauses the robot **at once**: every motor stops on brake. So a
driver who grabs BACK in a panic gets the stop. When you let go fast, the robot stays paused, and a second
tap goes on. When you hold BACK 1 s, it also resets "forward" (only with field-centric on; robot-centric
shows a note instead). The robot then stays paused until the next tap. Going on is always a tap, at the
release, so holding BACK never starts the robot.

**START + BACK: press START first.** Hold START, then press BACK: when you let go, you get the debug lines.
Held 1.5 s, the pair switches the alliance, if the switch is on. The robot does not pause. **BACK first is
always a plain pause.** BACK stops the robot the moment it goes down. When START joins a moment later, it
does nothing, so the robot stays paused (a tap of BACK goes on). The reason: the pause has to act at the
press. A pair that came later would have to un-pause the robot. Then the robot would stop for a moment and
drive on, and that is a surprise. While the pair is down, START's "set pose" does nothing. When you press
both in the same instant, it counts as START first. This applies only to pairs with the pause button in
them. Any other pair works in either order.

**START + A or START + B** pairs a gamepad on the Driver Station. The library cancels START's own job
("set pose here") for that press, so pairing a gamepad never moves the pose.

**Set pose here** works only with the drive sticks let go and whole-robot aim off. Otherwise the note says
"Pose NOT set: stop first". It also works in INIT: hold START 1 s before the match, so the pose is set
before the buzzer.

## Switching the alliance in TeleOp

The switch is off until you turn it on. Without it, you choose the alliance in INIT with X = BLUE,
B = RED. Turn it on with one line in `libraryButtons(...)`:

```java
lib.switchAlliance(g1.start, g1.back);   // hold START, then BACK, 1.5 s: BLUE <-> RED
```

A fast press of both (START first) still gives the debug lines. Use the switch when autonomous ran as the
wrong alliance. When the alliance switches, the goal flips, and so does the odometry's pose:

- On a mirrored field (the reset spot's `mirrorForRed` true), the library mirrors the pose in place: x and
  the heading flip.
- On a field turned half a turn (`mirrorForRed = false`, BIOBUZZ), the library cannot fix the pose. The
  odometry aim goes off ("Alliance now RED: odometry aim OFF until the pose is set"). It stays off until you
  park on the reset spot and hold START 1 s ("set pose here").

INIT's `Switch alliance` line says whether the switch is on.

## What INIT checks

**INIT names the jobs you freed.** No pause button gives a warning: nothing on the gamepads pauses the
robot. No slow at all gives a warning in `Controls.java`. No field-centric button gives a line: driving
stays as it starts. With one gamepad, a library job that you put on `g2` goes on gamepad 1.

**INIT checks these** (the message names the button):

- two library jobs on one button. One quick job and one held job may share a button, like BACK's pause and
  reset.
- the slow trigger with another job.
- a pair on two gamepads.
- the tuning menu on a button that the menu itself uses (D-pad, A, B, X, Y, LB, RB).
- a job of yours on a library button.

## Done when

- INIT shows no stop message about the library's buttons.
- The button map (a tap of GUIDE) shows each library job on the button you chose.

---

**Next:** [Building autos](../building-autos/README.md).

**Look it up:** [Moving the library's buttons](../reference/controls.md#moving-the-librarys-buttons) (the reference).
**Go deeper:** [the cookbook's button recipes](../cookbook.md#buttons-and-gestures).

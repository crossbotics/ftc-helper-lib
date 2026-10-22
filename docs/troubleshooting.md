# Troubleshooting

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Find your problem below. Each answer says what is wrong and what to do. Tip: copy the exact error
text and search this page for it (Ctrl+F, or Cmd+F on a Mac).

## Build problems

**`Project directory '.../ftc-helper-lib/library' does not exist`**
The submodule folder is empty: the project was cloned (copied from GitHub) without the library in
it. Run in the Terminal tab:
```bash
git submodule update --init
```

**`Cannot resolve symbol 'crossbotics'` / red imports**
Gradle does not know about the library yet. Check steps 2 and 3 in
[Add it to a project](01-add-to-project.md), then **File > Sync Project with Gradle Files**.

**`Plugin [id: 'com.android.library'] was not found`**
Your project's top `build.gradle` (the one in the project folder, not in TeamCode) must list the
Android library plugin. Every FtcRobotController
project does (`id 'com.android.library' version '...' apply false`). If yours is very old, update the SDK.

**Errors in the library after an FTC SDK update**
See [A new FTC SDK version](02-keep-up-to-date.md#a-new-ftc-sdk-version).

## Driver Station errors

**`No DcMotorEx named "..." in the active robot configuration`** (or `No Servo named "intake_deploy" ...`)
The name in code and the name in the Driver Station configuration differ. The message lists the
names that do exist. Fix the spelling, or pick the right configuration on the Driver Station.
**The part is not on the robot yet?** (The message then ends `Not on the robot yet? Add .optional() ...`.)
Add `.optional()` to its line in Robot.java: INIT then goes on, says the part is off, and its
buttons and actions skip it until it is in the configuration
([parts not on the robot yet](modules/robot.md#parts-not-on-the-robot-yet)). The
[Hardware Check](modules/testing.md#hardwarecheckopmode) with `robot()` lists every Robot.java device
the configuration is missing.

**`libraryButtons: G1 BACK has two library jobs: ... Move or free one`**
(also `has two long (held) library jobs`, `is the slow trigger and also ...`, `cannot open the tuning
menu`.) In `libraryButtons(...)` a library job was moved onto a button that already has one. Pick
another button, or free the other job with `null` ([the button menu](guides/buttons.md#moving-the-librarys-buttons)).

**`Tunables.Lift has no max: write max (the top of the safe range, in counts: Motor Finder, B). A safety limit never comes from a library default.`**
(also `has no min`, `Tunables.java has no Lift section`, or `Arm` in `deg`.) On purpose: a lift or arm
must never use a library default for its safe range. Run the [Motor Finder](guides/finders.md), then
write `min` and `max` in that section of `Tunables.java`.

**`Tunables.Lift.max must be a public static number (like "public static double max = 1000;"): ...`**
The field is there but written another way: not `public`, or not a number (for example a `String`).
Write it exactly like `public static double max = 2900;`. (Without `static`, it counts as missing: you
get `has no max` instead.)

**`Tunables.Lift: max (...) must be more than min (...)`**
The two numbers are swapped or equal. `max` is the top of the safe range, `min` the bottom.

**`Tunables.Lift.Max looks like "max" (capitals and spelling count). Fix the name in Tunables.java; a position must not look like a setting.`**
A field name is a known setting with other capitals or an `_` (`Max`, `MAX`, `MIN`, `kp`, `KP`,
`k_P`): this always stops INIT. Or it is up to two letters off a setting (`maxPowr`, `maxPwr` for
`maxPower`) that the section does not write itself: then too. (`slowZone2` next to a written
`slowZone` is a position, not a typo.)
Without this stop it would quietly become a named position, and the real setting would stay the
library's default. Fix the spelling. If it really is a position, give it a name that does not look
like a setting. The known names: [Lift](modules/robot.md#lift).

**`Tunables.Intake.inPowr is not a setting of Intake (did you mean inPower?): the robot would quietly ignore it. ...`**
(any part: `Tunables.Aim.goalx`, `Tunables.Launcher.nominalVolt`, `Tunables.TurretLift.turetFrom`.) A field
in that section is not a name the part reads: a typo, other capitals, or a leftover from an older robot.
Before, the line was quietly skipped and the part used its default. Fix the name (the "did you mean" is
the closest real one; `Known:` lists them all, and so does the [Tunables reference](guides/tunables.md)),
or delete the line. A number you keep for your own code goes in your own section (`Tunables.Auto`).

**`AutoDriveConfig.calibrationConfirmed is false`**, or **"AUTONOMOUS IS OFF: the drive is not tuned yet"** (EasyAuto, Auto)
On purpose: a robot with untuned numbers can drive much too far. Tune `AutoDrive`, then set it to `true`: [Tune AutoDrive](modules/drive.md#tune-autodrive). With the
robot in five files: the Drive Tuner, then `Tunables.Drive.calibrated = true`.

## Warnings on the Driver Station (`!! ...` at the top)

| Warning | Meaning |
| --- | --- |
| `lift: push it DOWN to the switch to home it` | homing (finding where 0 is): drive it down to the switch once |
| `lift: no switch "lift_bottom" in the configuration` | the switch name is wrong or missing: it runs without the switch |
| `claw: no position called "OPNE"` | a typo in a position name: the command was ignored |
| `homing failed: switch not found` (turret) | the switch never closed: wire, magnet gap, or the wrong direction in `home(...)` |
| `follower encoder not reading: follower switched off` (flywheel) | the second flywheel motor has no encoder cable: plug it in (or use one motor) |
| `Launcher gave up: flywheel` (or `hood`, `Turret`...) | it was not ready in time: that part is the problem |
| `Aim odometry: the pose was never set ...` | TeleOp ran without autonomous, so the robot does not know where it is: aiming uses the camera only. Run autonomous first, or park on the reset spot and hold gamepad 1 START 1 s ("set pose here", `Tunables.Odometry.resetX, resetY, resetHeading`; [lesson 7](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended)) |
| `No reset spot: add resetX, resetY, resetHeading to Tunables.Odometry` | START was held 1 s ("set pose here") but `Tunables.Odometry` has no spot: add the three numbers (inches and degrees from your wall, for BLUE) |
| `Alliance now RED (was BLUE)` | the alliance switch is on (`lib.switchAlliance(...)`) and someone held START, then BACK, 1.5 s: the alliance switched. Hold them again to switch back ([ControlMap](modules/robot.md#controlmap)) |
| `Alliance now RED: odometry aim OFF until the pose is set (...)` | the alliance was switched on a turned field (`mirrorForRed = false`): the pose from autonomous cannot be fixed. Park on the reset spot and hold START 1 s ([buttons guide](guides/buttons.md#switching-the-alliance-in-teleop)) |
| `Set pose here needs odometry: ...` | START was held 1 s, but the robot has no `odometry(...)` line in Robot.java, so it cannot know where it is ([lesson 7](tutorials/start/07-odometry.md)). Nothing changed |
| `Launcher : WAITING_READY  left 1  waits for: turret aimed` | the launcher holds the piece until that check is true (here: the turret on the goal). `Launcher gave up: ...` shows for 5 s when it waited too long |
| `Pose NOT set: stop first ...` | "set pose here" was held while the robot drove (a drive stick pushed) or whole-robot aim was on. Stop, then hold START 1 s again |
| `No pause button (libraryButtons freed it): ...` | `lib.pause(null)` in `libraryButtons`: nothing pauses the robot. Put it on a button: `lib.pause(g1.back)` |
| `No slow button (libraryButtons freed it): ...` | `lib.slow(null)` with no `slow()` of your own: add one, e.g. `g1.rt.slow()` |
| `COMPETITION: "intake_deploy" not in the configuration: IntakeDeploy is OFF (optional part). On the robot? ...` | the same as the note below, loud because the COMPETITION profile is on: at an event a part that is off is most likely a misspelled or lost configuration name. Fix the name (or plug it in) and INIT again; if the part really is not built, it is safe to play |
| `"intake_deploy" not in the configuration: IntakeDeploy is off (optional part)` | a note, not a problem: the part is marked `optional()` and not on the robot yet. Its commands, buttons (`- off: ... not on the robot` on the map) and action steps are skipped. Plugged in now? Add it to the configuration and INIT again ([parts not on the robot yet](modules/robot.md#parts-not-on-the-robot-yet)) |
| `Launcher: battery 11.2 V: low. The flywheel gets back to speed slower: charge or swap it` | swap in a charged battery. The launcher's battery compensation helps, but shots still wait longer |
| `Aim odometry: ... more than the field allows` / `Aim: camera says ... odometry ...` | the pose is wrong or drifted, or `Tunables.Aim.goalX, goalY` are not where the camera measures to |
| `goal out of turret range (behind the robot: holding)` | the goal is behind the robot: turn the robot toward it |
| `PRACTICE profile` | set COMPETITION at events |
| `N tuned value(s) differ from the code` | saved tuning values are in use: copy them into the code, then reset |
| `... button job took N ms: the whole robot stopped that long. ...` or `a button job took N ms: ...` | a button job waited: see "A job took N ms" under [Robot behavior](#robot-behavior) |
| `Lift update took N ms: update() must not wait or sleep` | a part's `update()` took more than 25 ms: no wait or `sleep()` in `update()`. It goes away 5 s after the last slow update |
| `intake_color: not found: no colour sensor called ...` (or distance sensor) | a piece sensor's name is not in the robot configuration: it sees nothing, and Pieces counts nothing with it. Fix the name |
| `ready_color: no new reading for N s` | a piece sensor (or its camera) stopped answering: a loose cable, or a camera that stopped streaming. It sees nothing until readings come back |
| `Intake: held back by stopsWhen for N s (the count says full)` | the piece count says full. Not really full? Hold the in-button (RT) 1 s to pull in anyway, or hold G2's left stick button 1 s: count = 0 ([Pieces](modules/robot.md#pieces-and-piecesensor)) |

**`EasyTeleOp needs your robot settings` / `EasyAuto needs your robot settings`**
Add `robotConfig()` to the OpMode: `return new RobotSettings();` (starter kit) or
`return RobotSettings.config();` (tutorials). See [RobotConfig](modules/core.md#robotconfig).

**`Autonomous needs the IMU (turn sensor)`**
`RobotSettings.imu()` returned null. Return `ImuSetup.get(...)` there: every Control Hub has an IMU.

## Robot behavior

**A servo turns less than its seller says (a goBILDA 2000 servo: about 270 degrees, not 300)**
The hub sends 600 to 2400 microseconds for positions 0 to 1 (the FTC SDK's normal range); the servo's
full turn needs 500 to 2500. Add `.fullRange()` to the part in Robot.java (`ServoSetup...fullRange()`
or `PositionServo...fullRange()` without the five files), then measure its positions again: they move
once the range changes ([Full range](modules/hardware.md#full-range)). INIT says `cannot change its pulse
range` for a servo that is not on a REV hub.

**My servo positions are off after adding (or removing) `.fullRange()`**
Positions are measured for one pulse range. Find them again with the Servo Finder (Y while choosing the
servo = full range) or the Hood Finder, and copy the new numbers into `Tunables.java`.

**Every shot is a little short (or long) today**
The wheel is worn, the balls are softer, or the battery is lower than when you tuned. In the match: the
operator's D-pad up/down trims every shot speed by 1% (`Speed trim: +3%` on the Driver Station; held:
4 steps a second; double-tap D-pad down = 0). If it happens every match, the table is off: multiply its
speeds by the trim (or tune it again with the Shot Tuner) and copy the new line into `Tunables.java`
([Lesson 9](tutorials/start/09-shooter-and-camera.md#speed-trim-and-a-low-battery)).
Check that `kF` and `nominalVolts` (from the Flywheel Finder) are in `Tunables.Launcher`: they turn on the
battery compensation.

**The robot paused when I used START + BACK** (the debug lines, or the alliance switch)
BACK was pressed first. BACK is the pause button, and it pauses the moment it goes down; START joining
later does not undo that, so you get a plain pause (and no debug lines or switch). Tap BACK to go on.
Next time **hold START first, then press BACK** ([the button menu](guides/buttons.md#moving-the-librarys-buttons)).

**Y does nothing (no field-centric) in an EasyTeleOp**
Field-centric needs mecanum wheels and the IMU. A tank drivetrain, or `imu()` returning null, keeps
it off on purpose.

**Field-centric "forward" is wrong at the start of TeleOp**
The robot did not start autonomous facing straight away from its driver. Set `startHeadingDeg()` in
the autonomous (written for BLUE), or press BACK after turning the robot to face away from you (in a
Controls.java TeleOp, with field-centric on, hold BACK for 1 s: the press pauses the robot, the hold
also resets "forward"; tap BACK again to go on).

**The robot drives diagonally, spins, or a wheel fights the others**
A motor direction or a motor name is wrong. [Check motor directions](modules/drive.md#check-motor-directions).

**Pushing the stick up drives backward**
You forgot the minus: forward is `-gamepad1.left_stick_y`. (The gamepad reports stick up as a
negative number.) With the robot in five files, every direction is flipped: reverse the other side ([Lesson 2](tutorials/start/02-drive.md#7-check-the-motor-directions)).

**Field-centric goes the wrong way**
1. The IMU orientation (logo/USB) does not match the hub on the robot ([ImuSetup](modules/hardware.md#imusetup)).
2. The heading was not 0 while facing away from the driver. Point the robot away and press your reset button.

**A button action runs over and over**
- In `Controls.java`: the job is a `hold(...)` (every loop while held) where you wanted `press(...)`
  (once per press).
- Old style (`ControllerBindings`): `bindHold` where you wanted `bindPress`.

**A button does nothing**
- In `Controls.java`: is it on the right gamepad (`g1` = the driver, `g2` = the operator)?
- Is it a hold where you pressed (or a press you hold)? `heldFor(1.0, ...)` needs 1 s; a `tap` waits
  to know it is not a double-tap.
- Was it held through START (or while the tuning menu closed)? Let it go and press again.
- Is the robot paused (PAUSED on the screen)? Tap BACK.
- Old style (`ControllerBindings`): is `controls.update(gamepad1, gamepad2)` in the loop? Is it the
  right pad (`Pad.G1` = gamepad1, `Pad.G2` = gamepad2)? After `ignoreHeldButtons`, let it go and press again.

**A job took N ms: the whole robot stopped that long**
A button job (over 20 ms is reported) has a `sleep()` or a `while` loop that waits. The program
does one thing at a time: while the job waits, nothing else runs, not even driving. Fix: make the
steps an [Action](modules/robot.md#action) and wait with `waitSeconds(...)` (the robot keeps running
while it waits). Without the five files: set a target or a state and let the loop do the work
([Non-blocking code](guides/non-blocking.md)).

**The robot freezes when I press a button**
An action has a `sleep()` or a waiting loop. Actions must be quick. **Why:** the program does one
thing at a time, so while it waits, nothing else runs (not even driving). Set a target or a state
variable instead and let the loop do the work ([Non-blocking code](guides/non-blocking.md)).

**A lift only goes down, and slowly, at the start**
It is not homed yet. Push it down until the lower switch is pressed; then it works normally.
See [Homing](modules/safety.md#homing-with-a-lower-switch).

**A lift will not move**
- `getAppliedPower()` is 0 while you push the stick: a limit stopped it. Check `getPosition()` and
  your limits; it may think it is at the bottom or top.
- The motor was turned on with the lift half up, so 0 is in the wrong place. Lower it and restart,
  or use a lower limit switch for homing.
- The motor direction is wrong (positive power must go up). Check with a low power.

**The lift stops early or goes past the top**
Re-measure the top position with `lift.getPosition()` on telemetry. A slipping belt or string
changes the count: add a lower limit switch so it re-zeroes each time it reaches the bottom.

**AutoDrive moves the wrong distance**
Tune `wheelDiameterIn` and `strafeCorrection`: [Tune AutoDrive](modules/drive.md#tune-autodrive).

**AutoDrive turns overshoot or stop short**
Overshoot: raise `turnKd` or lower `turnKp`. Stop short: raise `turnKp` a little or set `turnMinPower`.

**AutoDrive stops early with "robot stuck"**
It was blocked, or it moved too slowly. If it was not blocked, raise `moveMinPower` or `stallTimeSec`.

**The loop is slow (telemetry shows under ~30 Hz, that is, under 30 loops per second)**
Turn on [bulk reads](modules/hardware.md#bulkreads), send fewer telemetry lines, and remove any `sleep()`.

## Autonomous choice and git problems

**The robot ran the wrong autonomous plan, or started in the wrong place**
The first option of each INIT menu line runs when nobody changes it, and the "Place robot" note says
where the robot must stand. Read the menu and the note out loud before START:
[match day checklist](guides/autos.md#match-day).

**An INIT menu option does nothing**
The option is spelled differently in `menuChoice(...)` and in `onlyIf(...)` (capitals count). Test each
option in the simulator: [Several autonomous modes](guides/autos.md#test-each-one-in-the-simulator).

**`modified: ftc-helper-lib (new commits)` in `git status`**
Your library folder is at another version than the project expects. If you did not mean to update it:
`git submodule update`. See [Keep it up to date](02-keep-up-to-date.md#after-a-teammate-updates-it).

**The build broke after a library update**
Read the CHANGELOG's **Changed** list, or go back to the old version:
[Go back to the old version](02-keep-up-to-date.md#go-back-to-the-old-version).

**`rejected`, `CONFLICT`, `divergent branches` or another git message**
See [Git and GitHub: problems](guides/github.md#problems).

## Still stuck?

Write down what you did, what you expected and what happened, and ask the programming lead.
If it is a library bug, add a failing test for it: [Contributing](contributing.md).

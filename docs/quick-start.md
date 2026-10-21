# Quick start: a driving robot in 30 minutes

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

The shortest hands-on path: get five files into your robot project, change the few lines marked
**STEP**, build, and drive. Then the [main track lessons](tutorials/README.md#start-here-the-main-track)
explain each part and add mechanisms.

**You need:** your robot project in Android Studio, a Control Hub and Driver Station, gamepad 1, and a
block to put the robot on (wheels off the ground). **Before this:** nothing.

Words used on this page:

- **Configuration:** the list of motors, servos and sensors you set up on the Driver Station, each
  with a name. Your code finds each part by that name.
- **INIT** and **START:** the two buttons on the Driver Station. INIT gets the program ready (nothing
  may move yet); START begins the match part.
- **TeleOp:** the program the drivers drive with. **Autonomous:** the program that runs on its own.

## 1. Add the library

Follow [Add it to a project](01-add-to-project.md) (about 10 minutes). After it, your robot project has
an `ftc-helper-lib` folder next to `TeamCode`, and a test build passes.

## 2. Get the five files

Pick **one** way.

### A. From your robot configuration (best if it is already set up)

The library writes the five files for you, with each part already in them, from the configuration you
made on the Driver Station. **Why:** the names are copied for you, so they cannot have typos.

1. Copy the configuration file off the Control Hub: it is `/sdcard/FIRST/<name>.xml` (for example
   `MyRobot.xml`). Use the REV Hardware Client, or Android Studio's **Device Explorer** (View > Tool
   Windows > Device Explorer) with the hub connected. Save it in your robot project, e.g.
   `MyRobot.xml` next to `TeamCode`.
2. From the `ftc-helper-lib` folder:

   ```bash
   ./gradlew :sim:newRobot -Pconfig=../MyRobot.xml -PteamCode=../TeamCode
   ```

   It writes `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java` and `autos/LeaveStart.java`
   into TeamCode. It never overwrites your work: if any of the five files is already there, it
   writes nothing and stops with `... is already there: nothing written (move it away first)`. Every guess it is not sure of is a
   `TODO` line in `Robot.java` (a comment that marks something still to do): **check each TODO** like
   the STEP lines below.

### B. Copy the template (no configuration yet, or you want to see every line)

Copy the five files from
[`examples/.../quickstart/`](../examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart):
`Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java`, and `autos/FirstAuto.java` (into a
folder `autos`), into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`. Then in **each** file:

1. Change the `package` line at the top to `package org.firstinspires.ftc.teamcode;` (in `autos/FirstAuto.java`:
   `package org.firstinspires.ftc.teamcode.autos;`, and its imports
   `org.firstinspires.ftc.teamcode.Robot` and `org.firstinspires.ftc.teamcode.Tunables`).
   **Why:** the `package` line must match the folder the file is in, or it does not build.
2. Delete the `@Disabled` lines (in `Controls.java` and `FirstAuto.java`), so they show on the Driver Station.
   (`@Disabled` hides the examples on a real robot.)

A simulator test runs this template on every library change, so it always builds and drives.

## 3. Change the STEP lines

Open each file and change only the lines marked `// STEP`, in order:

| STEP | File | Change | Why |
| --- | --- | --- | --- |
| 1 | `Robot.java` | `Drive.mecanum(...)` or `Drive.tank(...)`, and the motor names **exactly** as in the configuration, in the order front left, front right, back left, back right (a two-motor tank: left, right) | the library works out each wheel's power from its place; a typo stops at INIT with the list of real names |
| 2 | `Robot.java` | `.motor("gobilda-5203-312")` and `.wheelMm(96)`: your drive motor ([its catalog id](guides/parts-catalog.md)) and wheel size | autonomous turns inches into encoder counts with them (an encoder counts how far the motor shaft turned) |
| 3 | `Robot.java` | `.imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)`: which way the Control Hub's logo and USB ports face | the IMU (a sensor in the hub) measures the heading, the way the robot faces; field-centric driving and autonomous turns use it |
| 4 | `Robot.java` | only if a wheel turns the wrong way (step 5 below): `.reversed(...)` listing **every** reversed drive motor | the list replaces the default (the left side reversed) |
| 5 | `Robot.java` | your mechanisms: keep, delete or add `add(...)` lines (and a field for each). No intake? Also delete the `g2.a` line in `Controls.java` and `spitOut` in `Actions.java` | each part brings its numbers and buttons |
| 6 | `Tunables.java` | leave `maxPower` and `practiceMaxPower` at 0.4 for now | a slow first drive is a safe first drive |
| 7 | `Controls.java` | the TeleOp's name; `oneDriver = true` if one person drives and runs the mechanisms | the name is what you pick on the Driver Station; one driver puts every button on gamepad 1 |
| 8 | `autos/FirstAuto.java` | the autonomous's name, where the drive team puts the robot, and how far it drives | it only drives after the Drive Tuner ([lesson 6](tutorials/start/06-first-autonomous.md)) |

## 4. Build and check the hardware

1. Build (the green hammer in Android Studio), then Run to install it on the Control Hub.
   **Why:** the robot runs the app that is on the hub, not the code on your laptop.
2. Add the small **Hardware Check** OpMode from [tutorial 19](tutorials/19-testing-procedures.md#1-the-3-line-opmode)
   (it is not one of the five files), build and install again, then run it: every device is found, and
   each motor turns and counts. **Why:** it tests each part alone, before your code moves them together.

## 5. Drive

1. **Robot on a block, wheels off the ground.** INIT your TeleOp, START. **Why:** a wrong motor
   direction can make the robot spin or drive off the table.
2. Left stick up: every wheel must roll the way that moves the robot forward. A wheel rolls backward?
   Fix STEP 4 ([how to check each wheel](tutorials/start/02-drive.md#7-check-the-motor-directions)).
3. On the floor: left stick drives (and strafes on mecanum), right stick turns, LT slows down.
4. Tap BACK: everything stops (PAUSED); tap again to go on. Tap GUIDE to see every button.

It drives? Raise `maxPower` and `practiceMaxPower` in `Tunables.java` (1.0 and 0.6 are common).
`practiceMaxPower` is used in the PRACTICE profile (the gentle mode for the shop), `maxPower` in
COMPETITION.

## Try it in the simulator first (optional)

No robot nearby? The simulator runs your code on a laptop with a 3D field. In the Terminal, go into
the `ftc-helper-lib` folder (`cd ftc-helper-lib`) and run:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

Then open http://localhost:8780, pick your TeleOp, INIT, START, set gamepad 1's list to "Keyboard", and drive with the keyboard
(W A S D, arrows). The simulator builds the robot from your code, so it cannot find a wrong motor direction: only
the real robot can. ([Simulator guide](guides/simulator.md).)

## If something goes wrong

| Problem | Fix |
| --- | --- |
| The build cannot find `com.crossbotics.ftchelper` | step 1 is not finished: [Add it to a project](01-add-to-project.md) |
| A `package` error | the `package` line of a copied file still says `com.crossbotics.ftchelper.examples...` |
| The TeleOp is not on the Driver Station | the `@Disabled` line is still there, or the app was not reinstalled |
| INIT stops with a list of names | a name in `Robot.java` does not match the configuration (capitals count) |
| It spins when it should drive straight | a wheel direction is wrong: STEP 4 |
| Nothing moves | it is paused (tap BACK) |
| `newRobot` says `is already there: nothing written` | one of the five files is already in TeamCode: move it away (or delete it), then run the command again |

## Next

[Lesson 2: Drive](tutorials/start/02-drive.md) explains the drive line and the directions in detail; then
the [main track](tutorials/README.md#start-here-the-main-track) adds mechanisms, buttons, actions and
the first autonomous.

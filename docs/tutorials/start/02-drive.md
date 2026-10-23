# Lesson 2: Drive

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**Goal:** write the one `drive(...)` line in `Robot.java`, drive the robot with gamepad 1, and check that
every wheel turns the right way.

**You need:** nothing at first: start in the simulator. Then a drivetrain (mecanum or tank), a block to lift its wheels off
the ground, and gamepad 1.

**The code:** the lesson robot's [`Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Robot.java)
and [`Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Tunables.java).
Every method: [the robot module, Drive](../../modules/robot.md#drive). Before this: [Lesson 1](01-robot-in-five-files.md).

## 1. Pick the drive type and name the motors

This is the lesson robot's drive line. Your own `Robot.java` (from the Quick start) already has one:
check yours against it, line by line.

```java
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

- `Drive.mecanum(fl, fr, bl, br)`: four mecanum wheels. Their rollers sit at 45 degrees, so the robot
  can also **strafe** (move sideways without turning).
- `Drive.tank(left, right)`: a standard drive with one motor per side. `Drive.tank(fl, fr, bl, br)`:
  two motors per side. A tank robot cannot strafe.
- The names in quotes are the motor names in the robot configuration on the Driver Station. The order
  matters: front left, front right, back left, back right. **Why:** the library works out each wheel's
  power from its place. Two names swapped make a robot that slides diagonally when it should strafe.

A two-motor tank robot (like goBILDA's starter bot) looks like this:

```java
drive(Drive.tank("left_drive", "right_drive").motor("gobilda-5203-312").wheelMm(96));
```

## 2. Say which motor and which wheel

- `.motor(id)`: the drive motor by its id in the [parts catalog](../../guides/parts-catalog.md), for
  example `"gobilda-5203-312"` (goBILDA 312 RPM), `"gobilda-5203-435"` or `"rev-hd-hex-20"`. A typo stops
  at once with the list of ids. The default is `"gobilda-5203-312"`.
- `.wheelMm(mm)`: the wheel diameter in millimetres (default 96). goBILDA mecanum wheels are 96 or 104.
- `.wheel(id)`: the wheel by its catalog id instead, for example `"gobilda-hogback-96"`. The diameter
  comes from the catalog. (The catalog has no mecanum wheels yet: use `wheelMm` for those.)
- `.gearRatio(r)`: motor turns per wheel turn, if gears or a belt sit between them. Default 1.

**Why:** TeleOp does not need these, the sticks just set power. But autonomous does: "drive 24 inches"
becomes encoder counts from the motor's counts per turn, the gears and the wheel size (lesson 6). An
**encoder** is a sensor on the motor that counts how far its shaft turned. The
simulator uses them too.

## 3. Say which motors are reversed

Motors on the two sides of a robot face opposite ways. So the same power turns one side forward and the
other side backward. One side must be **reversed** in the code.

- The default: the left side is reversed (front left and back left). That fits most goBILDA and REV chassis.
- `.reversed("right_drive")`: exactly these motors are reversed, and no others. List **every** reversed
  motor. `.reversed()` with no names reverses none.

You will find out which is right in step 7. Do not guess more than once: check.

## 4. Say how the Control Hub is mounted (the IMU)

The **IMU** is a sensor inside the Control Hub that measures which way the robot faces (its heading).
Field-centric driving and autonomous turns use it.

```java
.imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)
```

- The first value is the IMU's name in the configuration (usually `"imu"`).
- `LogoFacingDirection`: which way the REV logo on the hub points. `UsbFacingDirection`: which way the
  USB ports point. Each is `UP`, `DOWN`, `FORWARD`, `BACKWARD`, `LEFT` or `RIGHT`, as seen from the
  robot (forward = the robot's front).
- The default is `"imu"`, logo `UP`, USB `FORWARD`. A hub mounted on its side might be
  `.imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP)`.
- `.noImu()`: no IMU. TeleOp then drives robot-centric only, and autonomous refuses to start.

**Why it matters:** with the wrong directions the heading is wrong, so field-centric driving goes the
wrong way and autonomous turns go wrong. Robot.java needs these imports at the top (an `import`
line tells Java where to find a name: here `Drive` and the two direction lists):

```java
import com.crossbotics.ftchelper.robot.Drive;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
```

A name in red in Android Studio? Click it and press Alt+Enter (Option+Enter on a Mac) to add its import.

## 5. The numbers: Tunables.Drive

```java
public static class Drive {
    public static double maxPower = DEFAULT_FROM_CATALOG;       // top wheel power (1.0)
    public static double practiceMaxPower = DEFAULT_FROM_CATALOG; // top wheel power in PRACTICE (0.6)
    public static double slowSpeed = DEFAULT_FROM_CATALOG;      // top power with the slow trigger held (0.3)
    public static double strafeCorrection = DEFAULT_FROM_CATALOG; // sideways moves: Drive Tuner (1.15)
    public static double turnKp = DEFAULT_FROM_CATALOG;         // auto turns: push per degree off (0.012)
    public static double turnKd = DEFAULT_FROM_CATALOG;         // auto turns: brake near the end (0.003)
    public static double moveMinPower = DEFAULT_FROM_CATALOG;   // auto: least power that moves it (0.30)
    public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
}
```

`DEFAULT_FROM_CATALOG` = "use the library's default": the number in brackets at the end of the line.
It needs the `import static` line at the top of the file ([lesson 1](01-robot-in-five-files.md#2-tunablesjava-the-numbers)).
Write your own number in its place to change it.

A **profile** is a set of limits for the whole robot: PRACTICE (gentle, for the shop) or COMPETITION
(full power, for matches).

- `maxPower`: the most power any wheel gets in the COMPETITION profile (0.1 to 1).
- `practiceMaxPower`: the same in the PRACTICE profile. **Why:** in the shop, new drivers and new code
  should not run at full speed. You choose the profile in any autonomous's INIT menu (the last line);
  the hub remembers it. A hub that was never set uses COMPETITION.
- `slowSpeed`: the speed at a full squeeze of the slow trigger (0.3 = 30%).
- `strafeCorrection` to `moveMinPower`, and `calibrated`, are for autonomous. You set them with the Drive Tuner in lesson 6.
  Until `calibrated = true`, autonomous does not run.

These are read at INIT. In the tuning menu (PRACTICE profile, hold GUIDE 1 s) you can change them; the
new value is used from the next INIT.

## 6. The driving you get for free

`Controls.java` (lesson 3) is the TeleOp. You write no driving code: gamepad 1 already drives.

| Input (gamepad 1) | Two drivers (`oneDriver = false`) | One driver (`oneDriver = true`) |
| --- | --- | --- |
| Left stick | drive forward/back; mecanum: also strafe | the same |
| Right stick X | turn | the same |
| Slow | LT: the further you pull it, the slower (down to `slowSpeed`) | left stick click: slow on/off |
| Field-centric on/off (mecanum with an IMU) | Y | right stick click |
| BACK | tap: pause everything; tap again: go on | the same |
| BACK held 1 s | field-centric on: "forward" = the way the robot faces now (one buzz). Robot-centric: nothing. Either way the robot is paused (BACK pauses at the press): tap BACK to go on | the same |

- **Robot-centric** (the start): stick up = the way the robot faces. **Field-centric**: stick up = away
  from the driver, whichever way the robot faces. Two buzzes = field-centric on, one buzz = off.
- **"Forward"** for field-centric is the way the robot faced at INIT, or what autonomous left behind.
  If it is wrong, turn field-centric on, point the robot straight away from you and hold BACK for
  1 second (one buzz). In robot-centric driving, holding BACK does nothing (no buzz), and the Driver
  Station shows for 3 s: `BACK: "forward" is only reset in field-centric driving (turn it on first).`
  **Why:** robot-centric driving does not use "forward", so a reset there would only surprise you later.
- **Pause:** a tap of BACK stops every part on **brake** (the motors hold still instead of coasting), and the wheels hold still. The Driver Station
  says PAUSED. Tap BACK again and each part goes on with what it was doing. **Why:** a quick, safe stop
  that does not end the program.
- The sticks have a small dead zone (a resting stick is never exactly 0) and a gentle curve, so small
  stick moves give fine control.

## 7. Check the motor directions

Do this on every new robot, and after any motor or wire change. **Put the robot on a block, wheels off
the ground.**

1. **One motor at a time:** run the Hardware Check ([testing procedures](../19-testing-procedures.md#2-use-it)).
   D-pad up/down picks a motor, the right stick sets its power. Does the encoder count up with positive
   power? That shows the motor and its wires work.
2. **With the code's directions:** copy
   [`MotorDirectionCheck.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/MotorDirectionCheck.java)
   into TeamCode (next to Robot.java; in Android Studio, copy it from `ftc-helper-lib/examples/...` and
   paste it on the `teamcode` folder, as in [Start from zero](../../start-from-zero.md#5-copy-a-file-into-teamcode-and-change-one-line)).
   Then change three things in your copy:
   - **Line 2,** the `package` line. Before: `package com.crossbotics.ftchelper.examples;`. After:

     ```java
     package org.firstinspires.ftc.teamcode;
     ```

     **Why:** the `package` line must match the folder the file is in, or it does not build.
   - **Delete the line `@Disabled`.** **Why:** with it, the Driver Station hides the OpMode.
   - **Make its `config()` read your Robot.java.** Find these lines and change the `return` line:

     ```java
     static DriveConfig config() {
         return new Robot().robotConfig().drive();   // the same names and directions as Robot.java
     }
     ```

     `Robot` needs no `import` line: it is in the same folder.

   Hold X, Y, A, B: each spins one wheel at 30% (front left, front right, back left, back right; a
   two-motor tank: X = left, Y = right). **Every wheel must roll the way that moves the robot forward.**
3. **A wheel rolls backward?** Change `.reversed(...)` in Robot.java so that motor's setting flips:
   add its name to the list, or take it out. Remember the list replaces the default (the left side). For
   example, if the lesson robot's back right wheel rolls backward, it needs
   `.reversed("front_left", "back_left", "back_right")`.
4. **The IMU:** run your TeleOp, hold gamepad 1's small **START** button, then press **BACK** while you
   still hold START (this turns on the extra **debug lines**
   on the Driver Station: more numbers for checking, like powers and the `Heading`; it does
   not pause),
   and turn the robot LEFT by hand. The `Heading` line must go UP. If it goes down or barely moves, fix
   the logo and USB directions. (The Hardware Check also shows the IMU, but it always assumes logo up,
   USB forward, so it only proves your directions if the hub is mounted that way.)

## Try it in the simulator

From the `ftc-helper-lib` folder, write the lesson robot's simulator file from its Robot.java, then run it:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

Open http://localhost:8780, pick "Lesson Bot TeleOp", INIT, START. Set gamepad 1's list to "Keyboard". On the keyboard: left stick W A S D,
right stick the arrows, LT is Z, Y is I, BACK is Backspace. Drive, strafe, turn, pull LT, switch to
field-centric and turn the robot around. For your own robot: `./gradlew :sim:fromRobot -PteamCode=../TeamCode`,
then `./gradlew :sim:run -PteamCode=../TeamCode`. ([Simulator guide](../../guides/simulator.md).)

The simulator builds its robot from your code, so it **cannot** find a wrong motor direction. Only the
real robot can (step 7).

## Check it on the robot

For the first drive, set `maxPower` and `practiceMaxPower` to 0.4. Raise them when everything works.

1. Wheels off the ground: stick up, every wheel rolls forward.
2. On the floor: stick up = forward, stick down = back, right stick right = turn clockwise.
3. Mecanum: left stick right = strafe right, in a straight line.
4. Pull LT: the robot slows down the further you pull.
5. Mecanum with an IMU: Y (two buzzes), turn the robot 90 degrees, push the stick up: it still goes away from you.
6. Tap BACK: everything stops and the Driver Station says PAUSED. Tap again: it drives.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Stops at INIT with a list of names | A motor name in Robot.java does not match the configuration (capitals too). |
| Spins or drifts when it should go straight | A motor direction is wrong: step 7. |
| Pushing the stick up drives backward | Every direction is flipped: reverse the other side. |
| Slides diagonally when it should strafe | Two motor names are on the wrong wheels (e.g. front_left and back_left swapped). |
| Field-centric goes the wrong way or circles | The logo/USB directions do not match the hub, or "forward" is wrong: with field-centric on, hold BACK 1 s while facing away. |
| Y does nothing | Field-centric needs mecanum and an IMU. With one driver it is the right stick click. |
| Everything stopped and will not move | It is paused: tap BACK. |
| Slow even without LT | You are in the PRACTICE profile (`practiceMaxPower`), or `maxPower` is low. |

## Under the hood

How the driving works inside, built by hand: [Tutorial 04: Drive a mecanum robot](../04-drive-mecanum.md)
and [Tutorial 05: Drive a standard (tank) robot](../05-drive-tank.md).

## Next

On your robot: [Lesson 2b: Check the hardware](../19-testing-procedures.md) (every motor, servo and sensor,
by hand; its page is also tutorial 19 of the under-the-hood track, so its file is called
`19-testing-procedures.md`). Then [Lesson 3: Buttons (Controls.java)](03-buttons.md).

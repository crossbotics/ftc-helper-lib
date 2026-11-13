# Before you tune: the Tune and check OpMode

One OpMode holds every tool. Read this page once before your first finder.

**You need:** a robot with its configuration done, and a block to lift it.

## The Tune and check menu

The library adds one OpMode by itself: **Tune and check** (TeleOp list, group "Finders"). You have no
file to copy. It holds the Hardware Check and every finder that fits your Robot.java.

1. Pick **Tune and check** and press INIT. A menu shows.

   ![The Driver Hub's TeleOp list with Tune and check under Finders, then press INIT](../images/hub-tune-and-check-opmode.svg)

2. D-pad up/down picks a tool. **A** opens it, and you see its own INIT screen. Read its safety note.
3. Press **▶** (start) on the Driver Hub. The tool runs, the same as on its own.
4. For another tool, press STOP, then INIT. The menu starts on the last tool. In a tool's INIT,
   **BACK** goes back to the menu.

The menu lists only the tools that fit your robot. It finds Robot.java by itself: any class in
`org.firstinspires.ftc.teamcode` that extends `RobotDefinition`. With two robots, D-pad left/right picks one.

| Tool | In the menu when |
| --- | --- |
| Hardware Check | always |
| Motor Direction Check | there is a Robot.java |
| Encoder Check | there is a Robot.java |
| Servo Finder | the configuration has a servo |
| Hood Finder | the robot has a hood |
| Motor Finder | the robot has a lift or an arm (or there is no Robot.java) |
| Flywheel Finder | the robot has a launcher (or there is no Robot.java) |
| Feed Stop Finder | the launcher has a `.feedStop(...)` |
| Drive Tuner | there is a Robot.java |
| Odometry Tuner | Robot.java has an `odometry(...)` line |
| Shot Tuner | the robot has a launcher |
| Piece Tuner | the robot has a piece sensor |

Two tools are not in the menu: the **Aim Tuner** and the **Characterizer**. They need your camera or your
lifts, so they keep their own file ([Aim Tuner](11-aim-tuner.md), [Characterizer](14-characterizer.md)). Your own finder files still work: keep them or delete them.

## Before you start

- Put the robot on a block for the Servo, Motor and Flywheel finders (wheels off the floor).
- Wear eye protection for the Flywheel Finder, Shot Tuner and Aim Tuner. Keep everybody out of the line of fire.
- Use a charged battery (12.5 V or more) for the Drive Tuner, the Characterizer, the Flywheel Finder and
  the Shot Tuner. Their numbers are powers and speeds, so they depend on the battery. Their screens show
  the battery and warn when it is low. Every finder writes the battery at the start (▶) in the results file
  (`== Drive Tuner, 2026-10-24 14:05, battery 12.8 V ==`).
- The simplest way is **Tune and check** (above). It has every finder that fits your robot, with no file
  to copy. Or use one file per finder. The finders are in the starter kit (`ServoFinder`, `MotorFinder`,
  `FlywheelFinder`, `DriveTuner`, `Characterizer`) and in the examples (`ExampleShotTuner`,
  `ExampleAimTuner`: put your launcher and camera in them). Remove `@Disabled` and build. Then they are in
  the TeleOp list under "Finders".
- The robot in five files: the Servo, Motor and Flywheel finders need no code. The Drive Tuner, the
  Odometry Tuner and the Shot Tuner need one line that names your robot. The line makes your `Robot`, so
  the tuner uses its parts. Examples: the lesson robot's
  [DriveTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/DriveTuner.java),
  the turret robot's [ShotTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/ShotTuner.java):

  ```java
  @Override protected RobotDefinition robot() { return new Robot(); }
  ```

## Help on the robot

Press **GUIDE** (the center button) in any finder, in INIT or while it runs. A page shows every button and
the steps. The Hardware Check has no help page. When your gamepad has no GUIDE button, or GUIDE does
nothing, hold the gamepad's **START** for 1 second instead.
While help is open, the finder's buttons pause and its motors stop. GUIDE again goes back.
Then let go of every stick, trigger and button once: until then, the finder ignores them.
Check once on your own Driver Hub and controllers that the help page fits and is easy to read.

The screens show the button names on YOUR controller (× ○ □ △ on a PlayStation controller). These pages
use the Logitech names ([every name](../controls/01-how-buttons-work.md#the-buttons)). **▶** on a screen is
always the Driver Hub's start button, never a gamepad button.

## Put the lines into your code

Every finder ends with lines for your code. The robot uses the files, not the finder's screen. You cannot
copy and paste from the Driver Hub, so there are two ways:

- **Type them.** First take a photo of the Driver Hub's screen with a phone. Then type each line from the
  photo into the file on the laptop. The photo keeps the numbers after you press STOP, and you can zoom in
  to read each digit. This is fine for a few numbers. Check each digit twice.
- **Copy them from the results file.** When you press STOP, the finder saves its lines on the Control Hub.
  Get that file on the laptop, then copy and paste
  ([Get the results file on the laptop](#get-the-results-file-on-the-laptop)). This is best for long lines,
  like a shot table.

Each finder shows two kinds of lines. Use the kind that fits your code:

| Your code | Copy the lines that start with | Example |
| --- | --- | --- |
| the robot in five files ([lessons](../tutorials/README.md#start-here-the-main-track)) | `Tunables.` or `Robot.java:` | `Tunables.Lift: min = 0, max = 2580, P1 = 1200` = in `Tunables.java`, section `Lift`, set those three numbers |
| the older helpers | the rest (`PositionMotor.create(...)`, `c.wheelDiameterIn = ...`) | `PositionMotor.create(hardwareMap, "lift").limits(0, 2580)` |

The section is the device's name in CamelCase (each word starts with a capital, with no spaces): `lift` ->
`Lift`, `intake_deploy` -> `IntakeDeploy`. When you gave the part its own section with
`.tunables("Name")` in Robot.java, copy the numbers into that section instead.
The finders call positions P1, P2...: give them real names (`low`, `high`, `open`, `closed`).

### Get the results file on the laptop

When you press STOP, every finder adds its lines to `/sdcard/FIRST/ftc-helper/finder-results.txt` on the
Control Hub, with the date. The Driver Station says whether it worked. The newest results are at the end.

1. Connect the laptop to the Control Hub: a USB cable, or the robot's Wi-Fi.
2. Open the file with one of these:
   - the **REV Hardware Client**: its file manager, in the folder `FIRST/ftc-helper/`
   - **Android Studio**: **View > Tool Windows > Device Explorer**, then `sdcard/FIRST/ftc-helper/`
3. Copy the lines you need into `Tunables.java` or `Robot.java`.

## Done when

- **Tune and check** is in the TeleOp list, and its menu shows the tools for your robot.
- You know where the results file is.

---

**Next:** [Wiring checks: motors and encoders](02-wiring-checks.md).

**Look it up:** [finders module](../modules/finders.md), [How to read a finder's lines](../reference/tunables.md#how-to-read-a-finders-lines).

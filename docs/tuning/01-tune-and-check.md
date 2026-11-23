# Before you tune: the Tune and check OpMode

One OpMode holds every tool. Read this page once before your first finder.

**You need:** a robot with its configuration done, and a block to lift it.

## The Tune and check menu

The library adds one OpMode by itself: **Tune and check** (TeleOp list, group "Finders"). You have no
file to copy. It holds the Hardware Check and every finder that fits your Robot.java.

1. Pick **Tune and check** and press INIT. A menu shows.

   ![The Driver Hub's TeleOp list with Tune and check under Finders, then press INIT](../images/hub-tune-and-check-opmode.svg)

2. D-pad up/down chooses a tool. The line `This tool:` says what it is for. **A** opens it, and you see its
   own INIT screen. Read the `!!` lines: they say what moves, and how to stop it.
3. Press **▶** (start) on the Driver Hub. The tool runs, the same as on its own.
4. For another tool, press STOP, then INIT. The menu starts on the last tool. In a tool's INIT,
   **BACK** goes back to the menu.

The menu lists only the tools that fit your robot. It finds Robot.java by itself: any class in
`org.firstinspires.ftc.teamcode` that extends `RobotDefinition`. With two robots, D-pad left/right chooses one.

| Tool | In the menu when |
| --- | --- |
| Hardware Check | always |
| Motor Direction Check | there is a Robot.java |
| Encoder Check | there is a Robot.java |
| Servo Finder | the configuration has a servo |
| Hood Finder | the robot has a hood |
| Motor Finder | the robot has a lift or an arm (or there is no Robot.java) |
| Flywheel Finder | the robot has a launcher (or there is no Robot.java) |
| Flywheel Tuner | the robot has a launcher (experimental) |
| Feed Stop Finder | the launcher has a `.feedStop(...)` |
| Feed Time Finder | the launcher's feeder is a CR servo or a motor, with no feed stop, pushing parts or feed wheel |
| Drive Tuner | there is a Robot.java |
| Odometry Tuner | Robot.java has an `odometry(...)` line |
| Move Tuner | there is a Robot.java |
| Shot Tuner | the robot has a launcher |
| Group Shot Tuner | the robot has a launcher and a camera with `goal(...)` (experimental) |
| Piece Tuner | the robot has a piece sensor |

Two tools are not in the menu: the **Aim Tuner** and the **Characterizer**. They need your camera or your
lifts, so they keep their own file ([Aim Tuner](12-aim-tuner.md), [Characterizer](15-characterizer.md)). Your own finder files still work: keep them or delete them.

## Before you start

- Put the robot on a block for the Servo, Motor and Flywheel finders (wheels off the floor).
- Wear safety glasses for each tool that spins the flywheel or shoots. Keep everybody out of the line of fire.
- A held stick or button starts nothing. The screen says `Let go of all sticks and buttons.` at ▶, after
  help, and when what you control changes. Let go, then press again.
- **STOP** on the Driver Hub ends every tool. The motors get no power. Parts can still roll to a stop, and a
  servo can still finish its move.
- Use a charged battery (12.5 V or more) for the Drive Tuner, the Move Tuner, the Characterizer, the Flywheel Finder,
  the Shot Tuner and the Group Shot Tuner. Their numbers are powers and speeds, so they depend on the battery. Their screens show
  the battery and warn when it is low. Every finder writes the battery at the start (▶) in the results file
  (`== Drive Tuner, 2026-10-24 14:05, battery 12.8 V ==`).
- The simplest way is **Tune and check** (above). It has every finder that fits your robot, with no file
  to copy. Or use one file per finder. The finders are in the starter kit (`ServoFinder`, `MotorFinder`,
  `FlywheelFinder`, `DriveTuner`, `Characterizer`) and in the examples (`ExampleShotTuner`,
  `ExampleAimTuner`: put your launcher and camera in them). Remove `@Disabled` and build. Then they are in
  the TeleOp list under "Finders".
- The robot in five files: the Servo, Motor and Flywheel finders need no code. The Drive Tuner, the
  Odometry Tuner, the Move Tuner and the Shot Tuner need one line that names your robot. The line makes your `Robot`, so
  the tuner uses its parts. Examples: `lessonbot`'s
  [DriveTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/DriveTuner.java),
  the turret robot's [ShotTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/ShotTuner.java):

  ```java
  @Override protected RobotDefinition robot() { return new Robot(); }
  ```

## Help on the robot

Press **GUIDE** (the center button) in any finder, in INIT or while it runs. A page shows every button and
the steps. The Hardware Check has a help page too. When your gamepad has no GUIDE button, or GUIDE does
nothing, hold the gamepad's **START** for 1 second instead.
While help is open, the finder's buttons pause and its motor power is off. A part can still roll to a stop.
GUIDE again goes back. Nothing starts again by itself.
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

Each tool shows one kind of line, the one that fits your code:

| Your code | The tool shows | Put it in |
| --- | --- | --- |
| the robot in five files ([The five files](../five-files/README.md)), in Tune and check | `Open Tunables.java, find class Lift.` and then `public static double max = 2580;` | that class in `Tunables.java`. Replace the line with the same name. When there is none, add the line |
| the robot in five files, a change that is not a number | `Open Robot.java:` and then what to change | `Robot.java` |
| the older helpers, with no Robot.java | `For a project with no Robot.java:` and then `PositionMotor.create(...)` | your own Java code |

Over the lines, the screen says **DONE**, or **NOT DONE** and what is still missing. Copy a line only when the
screen says DONE. The results file says the same.

The tool takes the class from Robot.java: `.tunables("Name")` gives class `Name`. Without it, the class is the
device's name in CamelCase (each word starts with a capital, with no spaces): `intake_deploy` ->
`IntakeDeploy`. A Servo, Motor or Flywheel finder file of your own does not read Robot.java: use Tune and
check to get lines for `Tunables.java`.
The finders call positions P1, P2...: give them real names (`low`, `high`, `open`, `closed`).

### Get the results file on the laptop

When you press STOP, every finder adds its lines to `/sdcard/FIRST/ftc-helper/finder-results.txt` on the
Control Hub, with the date. The STOPPED screen shows the lines again, and says whether the save worked. The newest results are at the end.

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

# Aim Tuner: camera offset

Make the turret or the robot aim at the middle of the goal, when the camera sits off to one side.

**In Tune and check:** no. It has its own file: it needs your camera.
**You need:** its own file, safety glasses, pieces, the target 4-6 ft away, and the
[camera lighting](10-camera-lighting.md) set up.

## Get to the Aim Tuner

1. On the Driver Hub, open the TeleOp list and pick your Aim Tuner file (group "Finders"). Its name is the
   one in its `@TeleOp(name = ...)` line, like **Aim Tuner**. Press **INIT**.

   ![The Driver Hub's TeleOp list with Aim Tuner under Finders, then press INIT](../images/hub-aim-tuner-opmode.svg)

2. The Aim Tuner's INIT screen shows, with the camera's status. Press **▶** (start) on the Driver Hub.

   ![The Aim Tuner's INIT screen: Camera : STREAMING 30 fps. Press start](../images/hub-aim-tuner-go-start.svg)

## What the offset is

The camera finds the goal, and the turret or the robot turns until the goal is in the camera's center. But
the camera is not on the launcher. So when the goal is in the camera's center, the launcher points a
little to the side. The **offset** is the angle that fixes this: the robot aims until the goal sits that
far from the camera's center.

![Top view: the camera sits left of the launcher, so the goal looks a little right of the camera's center when the launcher points at it. The angle is bigger for a near goal than for a far one. Shots land left: press D-pad right](../images/aim-camera-offset.svg)

The sign of the offset depends on where the camera sits:

![Three top views: a camera left of the launcher gives a negative offset, a camera right of it gives a positive offset, and a camera on the launcher's line gives 0](../images/aim-offset-examples.svg)

The Aim Tuner finds this angle by test shots. It does not change how fast or how smoothly the robot turns
(for the whole robot, that is `turnKp` and `turnKd` in `Tunables.Aim`: [Fix it by what you see](17-fix-by-symptom.md)).

## See what the camera sees

Before you tune, check the camera's picture. The goal's tag must be in the picture, sharp, and not too
dark or too bright.

- **A webcam:** the Driver Hub can show its picture.
  1. Use the PRACTICE profile ([profiles](../guides/profiles.md)). In COMPETITION the live view is off,
     because it slows the robot.
  2. Press INIT. Do not press ▶ yet.
  3. On the Driver Hub, open the menu (the three dots, top right) and choose **Camera Stream**. Tap the
     picture to get a new one.
  4. Close it, then press ▶.

  A webcam in Robot.java does this by itself. In an Aim Tuner file of your own with the older helpers, add
  `.liveView(true)` to the webcam.
- **A Limelight:** it sends no picture to the Driver Hub. Leave it where it is on the robot, and plug its
  USB-C cable into a laptop instead of the Control Hub. Open http://limelight.local:5801: the page shows
  the live picture, with the tags it finds marked. Plug it back into the Control Hub after.
  ([Limelight lighting](../guides/limelight-lighting.md) has more.)
- **No picture? Use the numbers.** While the tuner runs, the screen shows `Bearing : ... deg (left = +)`
  when the camera sees the goal, and `No target in view.` when it does not. Turn the robot a little to the
  left: the bearing must go down.

## Steps

**Before you start:** the Aim Tuner uses the older helpers' pieces, not `robot()`. It needs your
`createCamera()`, and `createTurret()` or `robotConfig()` (to aim by turning the robot), and
`createLauncher()` for test shots ([reference](../modules/finders.md#aimtuneropmode)). For a robot in five
files, `robotConfig()` is `return new Robot().robotConfig();`. The Characterizer is the same: it needs
`robotConfig()`.

1. Park the robot 4-6 ft from the target, facing it. For a turret with a home switch, press **X** first.
2. Hold **A**, and the robot aims.

   ![Hold A: the Driver Hub shows Still to turn : 0.1 deg](../images/hub-aim-tuner-step-aim.svg)

3. Still holding **A**, shoot (**RB**). LB stops the flywheel.

   ![Still holding A, press RB when the Driver Hub shows Launcher : ready](../images/hub-aim-tuner-step-shoot.svg)

4. When shots go LEFT of the middle, press D-pad RIGHT (aim more to the right). When shots go right, press
   LEFT.

   ![Shots go right, so press D-pad left: the Driver Hub shows Offset : -1.5 deg and Tunables.Aim: cameraOffset = -1.5](../images/hub-aim-tuner-step-offset.svg)

5. Repeat until shots go in the middle, at two or three distances. Copy the offset line
   (five files, with a turret: `Tunables.Turret: cameraOffset = -1.5`; aiming by turning the whole robot:
   `Tunables.Aim: cameraOffset = -1.5`).
   When the offset that works changes with distance, the camera is off to the side of the launcher.
   Move the camera onto the launcher's line (or as close as you can), or tune at the distance you shoot
   from most.

## Fix a mistake

- **Pressed the D-pad the wrong way:** press it the other way. Each press changes the offset by 0.5 degrees
  (left and right) or 2 degrees (up and down), so the opposite press undoes it.

**Start over:** press STOP, then INIT, and open the tool again. It starts empty: nothing carries over. The lines saved at STOP stay in the results file, the newest at the end.

## Copy the numbers

The robot uses `Tunables.java` and `Robot.java`, not the finder's screen. Copy each line:

| The finder shows | Put it in |
| --- | --- |
| `Tunables.Turret: cameraOffset = -1.5` (a turret) | `Tunables.Turret` |
| `Tunables.Aim: cameraOffset = -1.5` (the whole robot aims) | `Tunables.Aim` |

## Done when

shots go in the middle at two or three distances, and the `cameraOffset` line is copied.

---

**Next:** [Shot Tuner: the shot table](12-shot-tuner.md).

**Look it up:** [Aim](../reference/parts/aim.md), [AimTunerOpMode](../modules/finders.md#aimtuneropmode).

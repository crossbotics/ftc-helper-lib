---
id: AIM-01
title: Turret follows the goal with the camera
area: aim
priority: high
where: both
minutes: 40
needs: [turret, limelight|webcam]
kit_bots: []
since: v0.16.0
docs: [tutorials/start/09-shooter-and-camera.md#6-a-turret-that-follows-it, tutorials/start/09-shooter-and-camera.md#check-it-on-the-robot]
---

## Why

A turret that follows the goal takes the aiming off the drivers. It must turn the short way, never go
past its limits, and say clearly when the goal is out of range. Every turret is built differently
(gear ratio, range, where the camera is), so we need other turrets.

## Safety

- Hard stops on the turret just past `min` and `max`. Check them by hand first.
- Cables slack over the whole range. The flywheel off (do not fire in this test).
- Safety glasses if the launcher has pieces in it.

## Setup

- `Turret.motor(...).gearRatio(...).follows(camera)` in `Robot.java`, `Tunables.Turret` with `min`, `max`.
- A goal tag (AprilTag) you can see: the field, or a printed tag at the right height.
- The turret pointed straight forward before INIT (0 degrees is where it points at INIT).
- Simulator: lesson 9's turret robot (`turretbot`) commands, then "TurretBot TeleOp".

## Steps

1. INIT your TeleOp, START. Drive (or turn the robot) until the camera sees the goal.
2. Watch the turret turn to the goal. Read the turret line on the Driver Station.
3. Turn the robot slowly 90 degrees one way, then the other. The turret keeps on the goal.
4. Turn the robot until the goal is past a turret limit: the turret waits at the limit, and the Driver Station says "goal out of turret range".
5. Gamepad 2 right stick X: the turret turns by hand. Let go: following goes on.
6. Gamepad 2 BACK: following off. BACK again: on.

## Pass rule

In steps 2 and 3 the turret stays within its `tolerance` (1.5 degrees by default) of the goal while
the robot turns slowly, it never goes past `min` or `max` (0 times), and step 4 shows the message.

## Record

- camera (Limelight or webcam), on the turret or on the body
- gear ratio, `min`, `max`, `kP` you used
- how far off it settled (degrees, from the Driver Station or by eye)
- any shaking or overshoot, and when

## If your robot is different

A camera on the robot's body needs `.cameraOnBody()`: say if you used it. A turret with a different
range (say 0 to 270) is very useful. If the turret turns away from the goal, `.reversed()` fixes it:
say whether the docs made that clear.

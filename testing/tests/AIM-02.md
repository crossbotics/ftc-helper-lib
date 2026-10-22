---
id: AIM-02
title: Odometry backup aim when the camera is covered
area: aim
priority: high
where: real
minutes: 40
needs: [turret, limelight|webcam, pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#9-when-the-camera-loses-the-goal-the-odometry]
---

## Why

When a tag is hidden, the turret should keep aiming from the odometry. A wrong goal point, a wrong
alliance or a pose that was never set would aim at the wrong place, so the library has checks. We
need to see them work on other robots.

## Safety

- As AIM-01: hard stops, slack cables, flywheel off.

## Setup

- AIM-01 passed. ODO-01 passed.
- `Tunables.Aim`: `goalX`, `goalY` (inches, seen from your own drivers' wall; BIOBUZZ: `goalX = 13, goalY = -13`, your HIVE's CELL that is up at the start), and for BIOBUZZ `mirrorForRed = false`.
- An autonomous that ends near the goal, or the "set pose here" spot (ODO-02).

## Steps

1. Run your autonomous (or set the pose with START held 1 s), then INIT TeleOp. Read INIT's `Aim:` line (alliance, goal, mirrorForRed).
2. START. Point the turret at the goal with the camera seeing it (`following: camera`).
3. Cover the camera with your hand. The turret keeps pointing at the goal, and the Driver Station says `following: odometry`.
4. With the camera still covered, drive about 3 ft sideways. The turret follows the goal.
5. Uncover the camera. Read any `Aim: camera says ... odometry ...` warning.
6. Run TeleOp with no autonomous and no pose set: it must say `Aim odometry: the pose was never set`, and covering the camera must stop following.

## Pass rule

With the camera covered, the turret points within 5 degrees of the goal (by eye or by the Driver
Station) after the 3 ft drive, and in step 6 it does not follow from the odometry (0 times).

## Record

- odometry kind, camera kind
- `goalX`, `goalY` you used
- how far off the odometry aim was after step 4 (degrees)
- the camera/odometry disagreement it showed in step 5 (inches), if any

## If your robot is different

Use your own goal point if you test on a different field or a practice goal. If your turret robot
has no camera (only odometry), use `.followsOdometry()` and skip step 3; say so. On RED, check that
the `Aim:` line matches the field.

---
id: SHOT-17
title: The speed from the camera eases in and keeps the wheel ready
area: shooter
priority: medium
where: both
minutes: 10
needs: [flywheel, webcam]
kit_bots: []
since: unreleased
docs: [reference/parts/launcher.md]
---

## Why

With `speedFromDistance(camera)`, the flywheel speed comes from the shot table and the camera's distance. The
distance wobbles a little from picture to picture. Before, each wobble could change the speed enough to restart
"ready", so firing waited. With `distanceSmoothSec` set, the speed eases into each new distance. We want
to know that the wheel stays ready while the robot moves a little, and that the speed still follows a real move.

## Safety

- Eye protection. Nobody in front of the launcher.
- Feed a piece with the robot's feeder, never with fingers.

## Setup

- A launcher with `speedFromDistance(camera)` and a shot table (`speedByDistance`). A webcam or a Limelight.
- `distanceSmoothSec = 0.3` in the Launcher part of Tunables.java (it is off by default).
- The goal's AprilTags in view, about 40 in away.

## Steps

1. In TeleOp, spin the wheel up. Wait for `READY` on the Launcher line.
2. Drive slowly toward the goal and back, 6 in each way, for 10 s. Watch the `READY` on the Launcher line and the
   `Shot distance` line.
3. Stop. Drive 24 in farther from the goal at once, and stop. Count how long until the target speed on the
   Launcher line stops changing.
4. Set `distanceSmoothSec = 0` (off) and do step 2 again.
5. Simulator: do steps 1 to 3 with a robot file that has a camera.

## Pass rule

In step 2, `READY` stays on most of the time, and `Shot distance` changes smoothly. In step 3 the target speed
settles within about 1 s. Step 4 shows `READY` dropping out more often than step 2 (if your table is steep).

## Record

- the robot, the camera, and the library version
- the shot table rows near 40 in
- what `READY` did in steps 2 and 4
- the time in step 3

## If your robot is different

A robot with `speedFromOdometry()` (no camera): the odometry's distance is used at once; the test does not apply. A robot without the
speed from the distance: the test does not apply.

---
id: ODO-03
title: driveTo a field point - how close does it stop?
area: odometry
priority: medium
where: real
minutes: 30
needs: [mecanum, pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.16.0
docs: [tutorials/start/07-odometry.md#6-drive-to-field-points, tutorials/start/07-odometry.md#check-it-on-the-robot]
---

## Why

`driveTo` drives straight to a field point while it turns. How close it stops depends on your drive
numbers and your odometry. We want real numbers from many robots, not only ours.

## Safety

- PRACTICE profile and `Tunables.Drive.practiceMaxPower` 0.4 for the first runs.
- The robot drives by itself: a clear path, everyone out of it, a hand on STOP.

## Setup

- ODO-01 passed (`Tunables.Odometry.checked = true`).
- An autonomous with one `driveTo` step, for example lesson 7's "To the Goal" (start at -56, -61; drive to -30, -30 facing 45).
- Tape on the floor at the start spot and at the target point.

## Steps

1. Put the robot exactly on the start spot. INIT the autonomous, check for the odometry "ready" line.
2. START. Let it drive to the point and stop.
3. Measure the robot's centre against the target point (x and y error), and its heading error.
4. Do it 3 times. Write each.
5. Run it once more and gently push the robot sideways during the drive: it should come back on course.
6. Read the report at the end of autonomous.

## Pass rule

Each of the 3 runs ends within 1.5 in of the point and 3 degrees of the heading, and the push in
step 5 does not stop it from arriving.

## Record

- start spot and target point you used
- the 3 errors (x, y in inches; heading in degrees)
- the report line for the `driveTo` step (done, STALLED, TIMED_OUT...)
- the drive numbers you changed (`turnKp`, `moveMinPower`...), if any

## If your robot is different

Use any start spot and point that fit your space; about 3-4 ft of driving is enough. A heavy robot or
a robot with small wheels may need `moveMinPower`: say what you changed. Tank robots cannot use
`driveTo` (INIT stops with a message): report "could not run" only if that message was unclear.

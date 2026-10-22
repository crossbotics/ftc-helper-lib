---
id: DRV-02
title: Field-centric driving and resetting "forward"
area: drive
priority: medium
where: real
minutes: 15
needs: [mecanum]
kit_bots: []
since: v0.16.0
docs: [tutorials/start/02-drive.md#6-the-driving-you-get-for-free, guides/buttons.md#moving-the-librarys-buttons]
---

## Why

Field-centric driving (stick up = away from the driver, whichever way the robot faces) depends on
the IMU or the odometry heading. A wrong hub mounting (`.imu(...)` line) makes it drive the wrong
way. We want to know it works on robots built differently from ours.

## Safety

- Clear floor, about 6 x 6 ft. Low power (`practiceMaxPower` 0.4, PRACTICE profile).
- A hand near STOP.

## Setup

- A mecanum robot in five files, with the `.imu(...)` line set for how your hub is mounted.
- Two drivers: gamepad 1 drives. One driver: the same buttons (see the button table in the buttons guide).

## Steps

1. INIT your TeleOp with the robot facing away from you, START.
2. Press Y on gamepad 1 (one driver: right stick click) to switch field-centric on. The Driver Station says it.
3. Turn the robot 90 degrees left with the right stick. Push the left stick up: the robot should drive away from you, not to its own front.
4. Turn the robot to face you. Push the left stick up: it still drives away from you.
5. Turn the robot 45 degrees. Hold gamepad 1 BACK for 1 second: the robot pauses at the press, and after 1 s "forward" becomes the way it faces now. Let go, then tap BACK to go on.
6. Push the left stick up: the robot now drives the way it faced when you held BACK.

## Pass rule

In steps 3 and 4 the robot drives away from the driver within about 10 degrees, and after the reset
in step 5 "forward" is within about 10 degrees of the way the robot faced.

## Record

- how the Control Hub is mounted (logo and USB directions in your `.imu(...)` line)
- the angle error you saw in steps 3, 4 and 6 (about, in degrees)
- what the Driver Station said when you held BACK

## If your robot is different

With odometry, field-centric uses the odometry heading instead of the IMU: say which you have. If
you moved field-centric or pause to other buttons (`libraryButtons`), use yours and say which.
Tank drives cannot strafe, so this test is for mecanum only.

---
id: ODO-04
title: TeleOp starts where autonomous ended
area: odometry
priority: medium
where: both
minutes: 15
needs: [pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.16.0
docs: [tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended]
---

## Why

The pose (where the robot is and which way it faces) should carry from autonomous into TeleOp, so
field-centric driving and the odometry aim are right from the first second of TeleOp.

## Safety

- Real robot: the autonomous drives by itself. Clear floor, hand on STOP, PRACTICE profile.

## Setup

- A robot with odometry and any autonomous that drives and ends somewhere you can measure.
- Simulator: lesson 7's commands (`odometrybot`, "Odometry Bot To the Goal", then "Odometry Bot TeleOp"), or your own robot.

## Steps

1. Run your autonomous to the end.
2. Within 5 minutes, INIT your TeleOp. Read the `Pose` line.
3. Compare it with where the robot really is (tape measure, or the simulator's line under the field map).
4. START and push the left stick up with field-centric on: the robot drives away from the driver.
5. Wait more than 5 minutes after an autonomous, then INIT TeleOp again: the pose should NOT carry over.

## Pass rule

The TeleOp pose in step 2 is within 1 in and 2 degrees of where the autonomous ended, and after 5
minutes (step 5) it does not carry over (1 of 1).

## Record

- the autonomous's end pose and TeleOp's first pose
- what TeleOp showed in step 5
- the odometry kind

## If your robot is different

Any autonomous works, as long as it ends with the robot standing still. In the simulator, remember
the page's line uses the field seen from the red wall: on BLUE the signs flip and the heading differs
by 180.

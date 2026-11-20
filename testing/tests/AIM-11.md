---
id: AIM-11
title: Whole-robot aim keeps the goal centered while the robot strafes
area: aim
priority: medium
where: both
minutes: 15
needs: [webcam]
kit_bots: []
since: v0.45.0
docs: [reference/parts/aim.md#whole-robot-aim, modules/drive.md]
---

## Why

With whole-robot aim on (`robot.aimMode()`), the robot turns to face the goal while the driver drives. At full
speed the robot moved faster than the aim could turn: the goal drifted off, and the robot only turned back to
it when it stopped. Now forward and strafe go at `Tunables.Aim.aimDriveSpeed` (0.5) while aim is on. We want
to know that the goal stays near the middle while the robot strafes, and that 0.5 is a good default.

## Safety

- Clear space on both sides of the robot. Keep clear while it drives.
- The flywheel does not need to spin.

## Setup

- A robot with no turret, a camera with `.goal(...)`, and an aim button (`robot.aimMode()` in Controls.java).
- The goal (or its tags) in view, about 48 in away.

## Steps

1. Press ▶. Turn aim on. Wait for `AIMED`.
2. Strafe left with the stick fully over for 2 s, then right for 2 s. Read the `Aim : ... deg off` line while
   the robot moves.
3. Set `aimDriveSpeed = 1` (the tuning menu, or Tunables.java) and do step 2 again.
4. Turn aim off and strafe: full speed again.

## Pass rule

In step 2 the robot strafes at about half speed, and the error stays within 5 degrees while it moves. In step
3 the error grows more while it moves. In step 4 the robot strafes at full speed.

## Record

- the robot's drive (mecanum, tank) and its top speed
- the largest error in steps 2 and 3
- the `aimDriveSpeed` the drivers like best

## If your robot is different

A tank robot cannot strafe: drive forward and back across the goal's line instead. A robot with a turret that
follows the goal does not use whole-robot aim: skip this test.

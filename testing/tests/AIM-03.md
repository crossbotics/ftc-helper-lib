---
id: AIM-03
title: Whole-robot aim - the robot turns to the goal while you drive
area: aim
priority: high
where: both
minutes: 30
needs: [limelight|webcam|pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#10-no-turret-aim-the-whole-robot]
---

## Why

Most robots have no turret: they aim by turning. Whole-robot aim turns the robot to the goal while
the driver drives, and the turn stick always wins. Drive trains turn very differently, so the numbers
(`turnKp`, `minTurnPower`) need testing on many robots.

## Safety

- Clear floor, PRACTICE profile. The robot turns by itself while aim is on.
- Hand near STOP, and the driver ready to let go of the button.

## Setup

- A camera with `.goal(...)`, or odometry with `Tunables.Aim.goalX`, `goalY`.
- In `Controls.java`: `g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();`
- Simulator: lesson 9's `flywheelbot` commands, then "FlywheelBot TeleOp".

## Steps

1. INIT your TeleOp, START. Drive until the goal is in view (or the pose is set, with odometry).
2. Turn the robot about 45 degrees away from the goal. Press RB: the robot turns to face the goal.
3. With aim on, drive forward and sideways (mecanum) or forward only (tank). The robot keeps facing the goal.
4. Push the turn stick: the robot turns as the stick says. Let go: the aim takes over again.
5. Hide the goal (cover the camera, no odometry): it must not turn by itself (`Aim: no goal`).
6. Press RB again: aim off.

## Pass rule

In step 2 the robot ends within `toleranceDeg` (2 degrees by default) of the goal within 2 seconds,
and in step 5 it does not turn by itself (0 degrees).

## Record

- drive type, camera or odometry
- `turnKp`, `maxTurnPower`, `minTurnPower` you ended with
- how far off it stopped (degrees) and how long it took (seconds)
- any shaking, and when

## If your robot is different

A tank drive can only drive forward while it aims: that is expected. If it stops a few degrees
short, try `minTurnPower` and say what worked. If you put aim on another button, say which.
A robot with a turret that follows the goal must NOT use this (INIT stops): that message is worth
reporting if it was unclear.

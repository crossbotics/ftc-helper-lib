---
id: AIM-10
title: An upside-down webcam aims the right way
area: aim
priority: medium
where: real
minutes: 15
needs: [webcam]
kit_bots: []
since: v0.45.0
docs: [reference/parts/camera.md#a-webcam-upside-down, add-to-robot/07-camera.md]
---

## Why

A webcam mounted upside down sees an upside-down picture. Left and right swap, and every tag reads upside
down. Without help, the robot aims the wrong way and takes the down CELL for the goal. With `.upsideDown()`
on the webcam's line in Robot.java, the library turns each reading back. We want to know that left is left
again, and that the goal is the up CELL.

## Safety

- Robot on the floor, wheels free to turn. Keep clear of the robot while aim mode turns it.
- The flywheel does not need to spin: switch it off (or leave the launcher out of the test).

## Setup

- A webcam mounted upside down, with `.goal("BLUE SCORING", "RED SCORING")` and an aim button
  (`robot.aimMode()` in Controls.java).
- In Robot.java: `Camera.webcam("Webcam 1").upsideDown()`.
- A HIVE (or its tags) in view, with its up CELL known.

## Steps

1. INIT, facing the HIVE's middle about 48 in away. Read `Goal : ..., NN in, N.N deg` on the Driver Station.
   It must name the up CELL.
2. Turn the robot by hand so the goal is about 20 degrees to its LEFT. Read the line again.
3. Press ▶. Turn aim mode on. Watch which way the robot turns.
4. Take `.upsideDown()` out, install, and do steps 1 and 2 again (the old way, for comparison).

## Pass rule

In step 1 the goal is the up CELL. In step 2 the angle is + (left), within 3 degrees of the true one. In step
3 the robot turns toward the goal and stops on it. In step 4 the angle has the wrong sign, or the goal is the
down CELL or not seen.

## Record

- the camera (model), its tilt, its height
- the goal names and angles in each step, and the true angles
- which way the robot turned in step 3

## If your robot is different

A HuskyLens or a Limelight cannot use `.upsideDown()` (a Limelight has its own setting on its web page). A
camera on a turret: the turret turns toward the goal in step 3, not the robot.

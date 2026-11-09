---
id: AIM-09
title: A tilted camera reads the goal's side angle right
area: aim
priority: medium
where: real
minutes: 15
needs: [limelight|webcam]
kit_bots: []
since: unreleased
docs: [modules/vision.md#cameratilt, tutorials/start/09-shooter-and-camera.md#5-a-camera-that-knows-the-goal]
---

## Why

A camera tilted up to see a high goal measures in its own tilted view. A goal off to the side then reads
too few degrees, and the distance reads too long. With `pitchDeg` (the camera's tilt) in its Tunables
section, the library turns the numbers flat. We want to know the side angle and the distance it shows
match the real ones on the floor.

## Safety

- Robot disabled or in INIT only. Nothing needs to move.

## Setup

- A camera tilted up (20 degrees or more) with `.goal(...)`. Measure the tilt with a phone's level app
  on the camera's face.
- In `Tunables.java`, the camera's section (e.g. `Webcam1`): `public static double pitchDeg = 35;` (your tilt).
- A tape measure. Tape on the floor: a line straight out from the goal's middle.

## Steps

1. Put the robot facing the goal's middle, about 48 in away. INIT. Read `Goal : ..., NN in, N.N deg` on
   the Driver Station. Then roll the robot straight back 12 in (tape measure) and read it again.
2. Without moving the robot's place, turn it so the goal is about 25 degrees to one side (mark the floor:
   a 25 degree line from the camera). Read the line again.
3. Do step 2 to the other side.
4. Set `pitchDeg = 0`, install, and do step 2 again (the old way, for comparison).

## Pass rule

In step 1 the distance grows by 12 in, within 2 in (along the floor; a camera without `pitchDeg` grows
less). The number itself is not checked: each camera measures to its own point on the goal.
In steps 2 and 3 the angle is within 3 degrees of the true one, with + to the left. In step 4 the angle is
smaller than in step 2.

## Record

- the camera (model), its tilt, its height
- the distances and angles read in each step, and the true ones

## If your robot is different

A camera tilted less than 15 degrees shows only a small difference: say your tilt. A HuskyLens does not
use `pitchDeg` yet.

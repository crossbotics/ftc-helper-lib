---
id: AIM-05
title: The aim goes to our HIVE's up CELL, never a down CELL
area: aim
priority: high
where: both
minutes: 25
needs: [limelight|webcam]
kit_bots: []
since: unreleased
docs: [modules/robot.md#camera, modules/vision.md#taggroup]
---

## Why

Only the up CELL of a HIVE can be scored in, and a tip swaps the two CELLs. The camera tells them apart
by how the tags are turned: the up CELL's tags read right side up, the down CELL's upside down. If this
goes wrong, the robot aims at the wrong CELL, or at the underside of a down CELL from under the HIVE.
We want to know it works with real tags, with each camera, and after a tip.

## Safety

- Nobody in front of the launcher. Do not fire in this test.
- A turret that follows the goal moves by itself: fingers and wires away.

## Setup

- A robot with `goal("BLUE SCORING", "RED SCORING")` on its camera. Its TeleOp.
- A HIVE (or its two CELL tag groups, tags 30-37 for red), or the simulator: lesson 9's turret robot
  ([the commands](../../docs/tutorials/start/09-shooter-and-camera.md#try-it-in-the-simulator)).

## Steps

1. INIT TeleOp, choose RED (gamepad 1 B), START. Stand where the camera sees both red CELLs (simulator:
   x 57, y -13, facing the HIVE). The `Goal` line names the up CELL's group (at the start "RED AUDIENCE").
2. Tip the HIVE (on the field, or put pieces in the up CELL in the simulator). The `Goal` line now names
   the other group ("RED SCORING").
3. Go close to the HIVE, so only the down CELL's tags are in view (simulator: x 28, y -13, facing the
   HIVE, before the tip). The `Goal` line says `not seen (RED SCORING is upside down (a down CELL))`,
   and a turret that follows the goal does not turn to it.
4. Choose BLUE and do steps 1 and 3 from the blue side (simulator: x -57, y 13, facing the HIVE).
5. With the other camera kind (webcam or Limelight), if you have one: step 1 again.

## Pass rule

In steps 1, 2 and 4 the `Goal` line always names the up CELL's group. In step 3 there is no goal and
nothing turns toward the down CELL.

## Record

- camera kind (webcam or Limelight) and where it is on the robot
- the `Goal` line in each step
- anything that aimed at a down CELL, and from where

## If your robot is different

A robot without a turret: watch the `Goal` line and LOCKED only (whole-robot aim on with RB). A camera
that is mounted turned (rolled) on the robot: say how much; a camera turned more than 90 degrees would
read every tag upside down.

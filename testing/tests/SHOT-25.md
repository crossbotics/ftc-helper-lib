---
id: SHOT-25
title: Group Shot Tuner: any button stops it; a bad group is never scored
area: shooter
priority: high
where: real
minutes: 90
needs: [flywheel, webcam]
kit_bots: []
since: unreleased
docs: [tuning/14-group-shot-tuner.md]
---

## Why

The Group Shot Tuner shoots real balls by itself after a countdown, and turns the robot to the goal. Any button
must stop it at once. A group that was not aimed, or that lost the goal, must never be scored. Its result is only
worth copying when a hold of the fire button in TeleOp really scores every ball.

## Safety

- Safety glasses for everybody near the robot. Nobody in front of the robot.
- The robot turns by itself to aim, and the sticks drive it on the "place the robot" screen: clear the floor.
- Keep a person ready on STOP.

## Setup

- A robot in five files with a launcher whose speed comes from the camera (`speedFromDistance(camera)`), a camera
  with `goal(...)`, and a feeder that can feed without a break (a roller, a motor, or spinning servos).
- The shot table and the aim tuned. The Flywheel Tuner done (or not: then it tests today's settings).
- 12 balls, a charged battery, the goal on the field.

## Steps

1. Tune and check, Group Shot Tuner. In INIT, choose your alliance. Read the `Aim offset` line. Press ▶.
2. Drive to the first spot until `Camera distance` says OK. Press A. Load one ball. Press A, and press X during
   the countdown.
3. Press A, then A again. While the wheel speeds up, press B.
4. Press A, then A again, and let the check shot fly. Enter where it went. Do all 4 check shots.
5. Load 4 balls. Press A. During the group, turn the robot away from the goal by hand.
6. Load 4 balls again. Press A, and let the group finish. Enter where the 4 went.
7. Finish all 3 spots. Copy the lines from page 2 into `Tunables.java` (only when page 1 says DONE).
8. In TeleOp, hold the fire button with 4 balls from each spot.

## Pass rule

Step 2: nothing moves. Step 3: the power goes off at once and the screen says STOPPED; nothing starts again until A.
Step 5: the screen says `NOT counted` and asks for the same group again; it never asks where those balls went.
Step 6 asks where the 4 went. Step 8: with the copied lines, every ball scores from each spot.

## Record

- the motor, the wheel, the feeder, the table's rows, the aim offset, the battery's volts
- page 1 (the result), page 2 (the lines) and page 3 (each setting's score)
- each `NOT counted` line and what happened then
- how many of the 4 balls scored from each spot in step 8

## If your robot is different

A robot whose turret aims: the tool says it cannot run there. Say so in the record.

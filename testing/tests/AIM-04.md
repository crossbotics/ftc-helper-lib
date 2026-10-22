---
id: AIM-04
title: Aim Tuner - find the camera offset from real shots
area: aim
priority: medium
where: real
minutes: 40
needs: [flywheel, limelight|webcam]
kit_bots: []
since: v0.16.0
docs: [guides/finders.md#aim-tuner-camera-offset]
---

## Why

A camera that is not exactly on the launcher's line makes every shot go a little to one side. The Aim
Tuner finds the offset with real shots. We want to know the steps are clear and the offset works at
more than one distance.

## Safety

- Safety glasses for everyone. Nobody in front of the launcher.
- Hard stops on the turret (if any).

## Setup

- Flywheel gains found (SHOT-01). A goal you can shoot at, and 10-15 game pieces.
- The Aim Tuner OpMode for your robot ([finders guide](../../docs/guides/finders.md#before-you-start)).

## Steps

1. Park the robot 4-6 ft from the goal, facing it. A turret with a home switch: press X first.
2. Hold A: it aims. Fire with RB.
3. Shots go left of the middle: D-pad RIGHT. Shots go right: D-pad LEFT. Fire again.
4. Repeat until shots go in the middle. Write the offset.
5. Move to a second distance (about 2 ft farther). Fire 5 with the same offset.
6. Copy the line it prints into `Tunables.java`: `Tunables.Turret: cameraOffset = ...` with a turret, `Tunables.Aim: cameraOffset = ...` for whole-robot aim.

## Pass rule

At the first distance at least 4 of 5 shots go in after tuning, and at the second distance at least
3 of 5 go in with the same offset.

## Record

- camera place (on the turret, on the body, how far from the launcher's line)
- the offset you ended with (degrees)
- hits out of 5 at each distance, and the two distances
- how many tries it took

## If your robot is different

Without a turret the robot itself turns to aim: hold A the same way. If the offset that works
changes with distance, your camera is to the side of the launcher: say how far, and which offset
worked where.

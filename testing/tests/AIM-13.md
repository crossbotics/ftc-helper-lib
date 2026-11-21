---
id: AIM-13
title: The Shot Tuner aims and sees like a match
area: aim
priority: high
where: real
minutes: 20
needs: [flywheel, webcam]
kit_bots: []
since: unreleased
docs: [tuning/13-shot-tuner.md]
---

## Why

A shot table made from shots that were not aimed, or with another camera setting, does not fit the match. Now the
Shot Tuner uses the robot's own camera and aim: the lighting test in INIT, the alliance chosen in INIT (only its
goal counts), and RB turns the robot to the goal (toleranceDeg, cameraOffset, the angle aim) before each shot.

## Safety

- Eye protection. Nobody in the line of fire.

## Setup

- A robot with whole-robot aim: a camera with `goal(...)` in Robot.java, no turret that follows the goal.
- Open the Shot Tuner. Point the camera at a tag.

## Steps

1. In INIT, press B (RED) or X (BLUE). Check the `Alliance` line. Check the camera's lighting line runs.
2. Press START. Press LB. Turn the robot about 15 degrees away from the goal.
3. Press RB. The robot turns to the goal, then shoots.
4. Turn the robot away from every goal. Press RB: no shot, and the screen says the goal is not in view.
5. Hold Y 0.5 s with a tag in view: the lighting test runs again.

## Pass rule

10 RB presses from 10-20 degrees off: 10 shots, each fired within toleranceDeg (the Aim line); 0 shots with no goal
in view; 0 uses of the other alliance's goal.

## Record

- the alliance, the camera, toleranceDeg, cameraOffset, angleAim, the aim error shown before each shot

## If your robot is different

A Limelight: the same (its lighting test is the pipeline test). A turret that follows the goal: the turret aims; the tuner shoots where it points, as before. No camera with
`goal(...)`: it uses the first target with a distance, as before.

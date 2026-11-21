---
id: AIM-12
title: Aim into the opening from an angle (angle aim on and off)
area: aim
priority: medium
where: both
minutes: 15
needs: [webcam, flywheel]
kit_bots: []
since: v0.46.0
docs: [reference/parts/aim.md#aim-into-the-opening]
---

## Why

A goal's opening can be in front of its AprilTags (BIOBUZZ: the CELL's opening is about 3 in in front of the
tags under it). From an angle, aiming at the tags sends the shot toward the back edge of the opening. The angle
aim (`angleAim`, `goalFrontIn`) points at the opening instead. We want to know that it turns the right way,
by about the right amount, and that it does nothing straight on.

## Safety

- Eye protection. Nobody in front of the launcher or near the goal.

## Setup

- Whole-robot aim (or a turret that follows the goal) with a webcam and a `goal(...)` in Robot.java.
- In `Tunables.Aim`: `angleAim = true`, `goalFrontIn = 3` (BIOBUZZ). A button for
  `robot.aim().angleAimMode()` (for example `g1.a.toggle(...)`).
- Tape marks: straight in front of the goal at about 36 in, and 45 degrees to each side at the same distance.

## Steps

1. Straight on: aim (aim mode, or RB with quick turn on fire), angle aim on. Read the `Goal` line's degrees.
   Switch the angle aim off and read it again.
2. 45 degrees to the left of straight on: the same, on and off. Fire 5 pieces with it on and 5 with it off.
3. 45 degrees to the right: the same.
4. Simulator: the same three spots, with a robot file that has a webcam.

## Pass rule

Straight on, the degrees are the same on and off. At 45 degrees, the angle aim turns the robot a few degrees
toward the front of the goal (the side the opening faces), the other way on the other side. More pieces go in
with it on than off, from both sides.

## Record

- the robot, the camera, the distance, and the library version
- the `Goal` line's degrees in each step, on and off
- how many of 5 went in, on and off, on each side

## If your robot is different

A Limelight: the angle aim does nothing yet (it cannot tell which way the tags face); skip the test. A goal
whose opening is right behind its tags: leave `goalFrontIn = 0`.

---
id: SHOT-19
title: lockSpeedWhileFiring keeps a waiting shot's speed
area: shooter
priority: low
where: both
minutes: 10
needs: [flywheel, webcam]
kit_bots: []
since: unreleased
docs: [reference/parts/launcher.md]
---

## Why

With the speed from the camera's distance, a shot that waits for the wheel could see its speed move with the
camera's distance and wait longer. With `lockSpeedWhileFiring = true` its speed stays fixed until it is gone.

## Safety

- Eye protection. Nobody in front of the launcher.

## Setup

- A launcher with `speedFromDistance(camera)` and a shot table. `lockSpeedWhileFiring = true` in the Launcher part
  of Tunables.java.

## Steps

1. In TeleOp, switch the wheel off (or let it slow), aim at the goal, and press fire. While it spins up, drive
   2 in toward the goal and back (more than 4 in lets the lock go, on purpose). Watch the target speed on the Launcher line.
2. Let the shot go. Drive again and watch the target speed.
3. Simulator: the same with a robot file that has a webcam.

## Pass rule

In step 1 the target speed does not change until the piece is gone (2 in moves). Driving 12 in during a
waiting shot changes it (the lock lets go after 4 in). In step 2 it follows the distance again.

## Record

- the robot, the camera, the library version
- the target speeds seen in steps 1 and 2

## If your robot is different

No speed from the distance: the setting does nothing; skip the test.

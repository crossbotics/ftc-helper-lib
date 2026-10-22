---
id: SOM-02
title: Shooting on the move - strafe past the goal and score
area: moving-shot
priority: high
where: real
minutes: 60
needs: [flywheel, pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#14-extra-shooting-while-moving, modules/vision.md#movingshot]
---

## Why

This is the real test: does aiming at the "virtual goal" make shots score while the robot drives?
Only real shots can tell. If it does not help, we will say so in the docs.

## Safety

- Safety glasses. Nobody on the field side where shots go, and nobody in the robot's path.
- PRACTICE profile, slow speed first. A driver who keeps turning slow while firing.

## Setup

- SOM-01 passed. A clear lane about 6 ft long, parallel to the goal, at a distance you shoot from often.
- 15 or more pieces. A friend to count hits.

## Steps

1. With `shootOnMove = false`, drive slowly sideways along the lane and fire 5 while moving. Count hits.
2. Set `shootOnMove = true`. Drive the same lane at the same speed, fire 5. Count hits.
3. Misses on the side the robot drives toward: `flightSec` is too short. On the other side: too long. Change it and repeat step 2.
4. Drive faster (about twice the speed) and fire 5.
5. Optional: put the on/off on a button (`g2.a.toggle(robot.aim().movingShotMode())`) and check it switches.

## Pass rule

At slow speed, at least 2 more hits out of 5 with it on than off, or 4 of 5 with it on.

## Record

- drive speed (about, in/s: lane length divided by time)
- hits out of 5: off slow, on slow, on fast
- the `flightSec` you ended with, and how many changes it took
- which side the misses went before the fix

## If your robot is different

A tank drive cannot strafe: drive past the goal along a curve and say so. A turret robot and a
whole-robot-aim robot both count. If driving and turning at once makes shots go wide, say how fast
you turned.

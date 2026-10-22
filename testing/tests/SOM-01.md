---
id: SOM-01
title: Shooting on the move - standing still nothing changes
area: moving-shot
priority: high
where: real
minutes: 30
needs: [flywheel, pinpoint|otos|dead-wheels]
kit_bots: []
since: unreleased
docs: [tutorials/start/09-shooter-and-camera.md#14-extra-shooting-while-moving]
---

## Why

Shooting on the move is off by default until it is tested on a real robot. The first check is the
safest one: turned on, with the robot standing still, it must change nothing. Then the measured
flight time, which every later step needs.

## Safety

- Safety glasses. Nobody between the robot and the goal.
- The robot stands still in this test.

## Setup

- Odometry checked (ODO-01), aim working (AIM-01, AIM-02 or AIM-03), a shot table.
- A phone that films slow motion (240 frames a second).

## Steps

1. With `shootOnMove = false`, fire 5 from a fixed spot. Count hits.
2. Measure `flightSec`: film a shot from the side in slow motion, count the frames from leaving the launcher to reaching the goal, divide by 240. Do it near and far.
3. Set `shootOnMove = true`, `flightSec` (and `flightSecPerIn` if far shots fly longer), and `shooterForwardIn`, `shooterLeftIn` (and `cameraForwardIn`, `cameraLeftIn` for a camera on the body).
4. From the same spot, standing still, fire 5 again. Count hits. The `Aim: moving shot` line should not show while still.
5. Push the robot sideways by hand (or drive slowly): the `Aim: moving shot: lead ... deg, ... in` line appears.

## Pass rule

Standing still, the hits with it on are within 1 of the hits with it off (out of 5), and the lead
shown in step 5 is in the direction opposite to the motion.

## Record

- hits out of 5, off and on
- flight time near and far (frames and seconds), and the distances
- your `flightSec`, `flightSecPerIn`, `shooterForwardIn`, `shooterLeftIn`
- the lead line while moving (degrees, inches)

## If your robot is different

Works with a turret or with whole-robot aim (no turret): say which. A camera on the turret is taken to
sit at the pivot. If your camera is somewhere else, measure `cameraForwardIn`, `cameraLeftIn` and say
where it is.

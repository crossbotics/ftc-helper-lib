---
id: SHOT-11
title: The glitch filter keeps the flywheel speed steady
area: shooter
priority: medium
where: real
minutes: 20
needs: [limelight|webcam]
kit_bots: []
since: v0.37.0
docs: [modules/vision.md#distancefilter, guides/tunables.md#launcher]
---

## Why

A camera sometimes gives one picture with a wrong distance. The launcher now uses the middle of the
last 5 pictures, and a big jump must show in 3 pictures in a row. We want to know the speed stays
steady with real cameras and venue light, and that it still follows a real move fast enough.

## Safety

- Clear floor, PRACTICE profile. The flywheel spins: keep hands away.

## Setup

- A launcher with `.speedFromDistance(camera)` and a `speedByDistance` table.
- Debug lines on (so the launcher line shows the distance in use).

## Steps

1. Spin up with the goal in view, standing still, about 60 in away. Watch the launcher's distance for 30 s.
2. Wave a hand through the camera's view a few times. Watch the target speed.
3. Drive straight toward the goal, then away, at a normal speed. Watch the distance follow.
4. Turn away from the goal for 2 s, drive 3 ft, then face the goal again.

## Pass rule

In steps 1 and 2 the target speed never jumps by more than one table step for a moment. In step 3
the distance follows within about 0.2 s. In step 4 the new distance is used within about 0.2 s of
the goal coming back into view.

## Record

- camera model and the venue light
- any jump you still saw (how big, how long)
- the `distanceMedianOf`, `distanceJumpIn`, `distanceJumpAgreeIn` you ended with, if you changed them

## If your robot is different

With odometry, the odometry's distance is used while the camera is still collecting its first 3
pictures: say if you see a jump between the two.

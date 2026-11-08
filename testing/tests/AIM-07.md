---
id: AIM-07
title: Do the lighting test again from a button
area: aim
priority: medium
where: real
minutes: 15
needs: [limelight|webcam]
kit_bots: []
since: v0.37.0
docs: [modules/robot.md#camera, modules/vision.md#venue-lighting]
---

## Why

The lighting test picks the camera setting in INIT, but only if a tag is in view then. A robot that
starts with no tag in view (behind the HIVE, or facing a wall) keeps the first setting. `relight()`
runs the test again from a button. We want to know it finds a good setting in the match, and how
long the camera is "blind" while it tries.

## Safety

- Clear floor, PRACTICE profile. Aim (if on) stops while the test runs.

## Setup

- A camera with `.autoExposure()` (webcam) or `.autoPipelines(...)` (Limelight), and `.goal(...)`.
- In `Controls.java`: `g1.y.heldFor(0.5, robot.camera::relight).rumble().says("camera: lighting test again (aim at a tag)");`

## Steps

1. INIT with the camera facing away from every tag. START.
2. Turn the robot until the goal's tags are in view. Hold Y for half a second.
3. Watch the Driver Station: `... lighting: trying 1 of 4 ...`, then the setting it chose.
4. Check that `Goal` shows the goal again.
5. Face away from every tag and hold Y again. Wait 5 s.

## Pass rule

In step 3 it chooses a setting within 5 s and the goal is seen again. In step 5 it says
`NO TAGS SEEN: kept ...` with the setting from step 3 (not a different one).

## Record

- camera model, and the venue light (gym, classroom, event)
- the setting it chose, and how long it took (seconds)
- if the goal was seen better after the test than before

## If your robot is different

If you put the test on another button, say which. A Limelight tries pipelines, not exposures.

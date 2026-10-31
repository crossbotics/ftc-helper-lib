---
id: AIM-06
title: A HuskyLens finds taught tags and pieces, and the robot aims with it
area: aim
priority: high
where: both
minutes: 30
needs: [huskylens]
kit_bots: []
since: unreleased
docs: [modules/vision.md#huskylenscamera, modules/robot.md#camera]
---

## Why

The HuskyLens code has run only against pretend I2C answers. We need to know that a real camera answers,
that its boxes read as the code expects, and that the robot aims the right way. We also want to know how
far away it finds a tag, and how good its rough distance is.

## Safety

- Nobody in front of the launcher. Do not fire in this test.
- A turret or a whole-robot aim moves by itself: fingers and wires away.

## Setup

- A HuskyLens on an I2C port (bus 1, 2 or 3, not bus 0), configured as `HuskyLens`, named `huskylens`.
- Two or more AprilTags of one tag group, taught with Learn Multiple on (write down the order).
- In Robot.java: `Camera.huskyLens("huskylens").tag(1, 34).tag(2, 35).goal("BLUE SCORING", "RED SCORING")`, with your
  own numbers. In Tunables: `hfovDeg` ([measure it](../../docs/modules/vision.md#huskylenscamera)) and `sizeIn`.
- Simulator: add `huskylens.h = name=huskylens, forward=8, up=10, pitch=45, hfov=60, taught=34|35|36|37` to the
  robot file, and stand at x 50, y -13, facing the HIVE.

## Steps

1. Run the Hardware Check and pick the HuskyLens. `Answers` says true. Each tag in view shows as a `Sees` line
   with its taught number. Show a tag you did not teach: its line says taught number 0. Cover the lens: the lines go away.
2. INIT TeleOp, choose the alliance of your tags, START. Hold the tags in view: the `Goal` line names the group, with a
   distance and a bearing.
3. Move the tags to the robot's LEFT. The bearing grows positive. Move them to the right: it goes negative.
4. Measure the distance with a tape at 24, 48 and 72 in. Write what the `Goal` line says at each. Find how far away the tags
   stop being found.
5. Turn the tags upside down (or turn the camera). The `Goal` line says `not seen (... is upside down ...)`.
6. Whole-robot aim on, or a turret that follows the goal: it turns to face the tags and stops.
7. Pull the camera's cable while it runs. Within about 2 s the Driver Station says `not answering`. Plug it in again: it comes back. Also run the Hardware Check with the cable off: `Answers` says NO.
8. Change `.goal(...)` to `.colors().label(1, "POLLEN")`, teach it one piece's color, and read
   `target("POLLEN")` in a telemetry line as you move the piece.

## Pass rule

Steps 1, 2, 3, 5 and 6 work as written, the loop stays smooth, and step 7 shows a warning and nothing crashes.

## Record

- HuskyLens model and firmware version, where it is on the robot, the `hfovDeg` you measured
- the distances in step 4, and how far it finds a tag
- what step 7 showed
- how fast the loop runs with the camera on and off (Driver Station loop time)

## If your robot is different

No launcher or turret: skip step 6 and watch the `Goal` line. Only one tag: step 5 cannot work, because a
single tag has no "right side up"; say so.

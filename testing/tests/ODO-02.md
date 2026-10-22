---
id: ODO-02
title: Set pose here - hold START 1 s on the reset spot
area: odometry
priority: high
where: both
minutes: 20
needs: [pinpoint|otos|dead-wheels]
kit_bots: []
since: unreleased
docs: [tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended, guides/buttons.md#moving-the-librarys-buttons]
---

## Why

When TeleOp runs without an autonomous first, the robot does not know where it is. "Set pose here"
fixes that with one held button. It must only work when the robot stands still, and never when a
driver pairs a gamepad (START + A or START + B).

## Safety

- Real robot: the robot does not move in this test, but keep the sticks let go when you hold START.

## Setup

- A robot with an odometry line, and the reset spot in `Tunables.Odometry` (`resetX`, `resetY`, `resetHeading`; BIOBUZZ also `mirrorForRed = false`).
- Simulator: your own robot from `Robot.java` (`./gradlew :sim:fromRobot -PteamCode=../TeamCode`), or the odometry robot from lesson 7.
- A tape measure (real robot).

## Steps

1. Put the robot on the reset spot (for example the start tile against your wall, facing the field). In the simulator, drag it there on the field map.
2. INIT your TeleOp (with no autonomous before it). Read INIT's `Set pose: hold G1 START 1 s at ...` line.
3. Hold gamepad 1 START for 1 second, sticks let go. Gamepad 1 buzzes twice and the Driver Station says `Pose set: ...`.
4. START the TeleOp. Drive about 4 ft away and back to the spot. Read the pose: it should be close to the reset spot.
5. Push the left stick a little and hold START at the same time: it must say `Pose NOT set: stop first`.
6. Hold START, then press A (as if pairing a gamepad): the pose must NOT be set.
7. Choose RED in INIT (X = BLUE, B = RED) and repeat step 3 on the red side: the pose should use the same numbers from the red wall (BIOBUZZ).

## Pass rule

Step 3 sets the pose within 1 in and 2 degrees of the spot you wrote; steps 5 and 6 do not set it
(0 of 2); the RED pose in step 7 matches the reset spot for RED that INIT shows.

## Record

- the reset spot you used (x, y, heading)
- the pose shown after step 3 and after step 4
- what steps 5 and 6 showed
- the INIT line `Reset spot for RED: ...`

## If your robot is different

If you moved "set pose" to another button (`lib.setPose(...)` in `libraryButtons`), use that one and
say which. On a field that is a mirror image (not BIOBUZZ), leave `mirrorForRed` out and check that
RED mirrors x and the heading.

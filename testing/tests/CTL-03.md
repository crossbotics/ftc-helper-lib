---
id: CTL-03
title: Switch the alliance during TeleOp (turned on by you)
area: controls
priority: medium
where: both
minutes: 20
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [guides/buttons.md#switching-the-alliance-in-teleop]
---

## Why

If autonomous ran as the wrong alliance, the drivers can switch it in TeleOp, but only if the team
turned it on. It takes 1.5 s on purpose, so it never happens by accident. With odometry on BIOBUZZ,
the pose cannot be fixed, so the odometry aim must switch off until the pose is set again.

## Safety

- Real robot: the robot does not need to move. Sticks let go.

## Setup

- `lib.switchAlliance(g1.back, g1.start);` in `libraryButtons(...)` in `Controls.java`.
- A camera with `.goal(...)` or odometry aim makes the test better (optional).

## Steps

1. INIT. Read the `Switch alliance` line. Choose BLUE (X). START.
2. Hold START, then BACK, for 1.5 s. Both gamepads buzz twice and the screen says `Alliance now RED (was BLUE)`.
3. Tap START, then BACK quickly: that is the debug lines, not a switch.
4. With a camera: the goal it looks for switched to RED's.
5. With odometry on BIOBUZZ (`mirrorForRed = false`): the note says the odometry aim is OFF until the pose is set. Hold START 1 s on the reset spot: it comes back.
6. Without the `switchAlliance` line: holding START, then BACK does not switch (the INIT line says it is off).

## Pass rule

The switch happens only after the full 1.5 s hold (1 of 1), never on a quick press (0 of 3 tries),
and never without the `switchAlliance` line (0 of 1).

## Record

- what the screen said in steps 2 and 5
- whether the goal (camera) followed
- how many quick presses you tried in step 3

## If your robot is different

With no camera and no odometry, steps 4 and 5 do not apply: say so. If you put the switch on other
buttons, use those.

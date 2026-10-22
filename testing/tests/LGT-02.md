---
id: LGT-02
title: The alliance on the light at INIT
area: lights
priority: medium
where: both
minutes: 15
needs: [lights]
kit_bots: []
since: v0.17.0
docs: [modules/robot.md#the-alliance-at-init, tutorials/start/09-shooter-and-camera.md#13-a-light-for-the-drivers]
---

## Why

A wrong alliance pick in INIT makes the robot aim at the other alliance's goal all match. The drive
team sees the light from the field, not the Driver Station's small text, so before START the light
shows the alliance. We want to know it is easy to read on real lights, from the drive team's place.

## Safety

- Nothing moves in this test until step 5. Then the usual: a clear floor and a hand on STOP.

## Setup

- A light in `Robot.java` (`Lights.gobilda("light")` or `Lights.blinkin("blinkin")`), with no `allianceAtInit` line in its Tunables section.
- Your own rules in `Controls.java` (any, for example lesson 9 section 13's).
- Simulator: the light is a dot on the robot (V2 example robot, or your own robot from `Robot.java`).

## Steps

1. Restart the robot app (so no alliance is remembered). INIT your TeleOp. Before you pick: the light blinks white once a second.
2. Press X on gamepad 1 (BLUE): solid blue. Press B (RED): solid red.
3. Stand where the drive team stands (about 6 ft away, from the side of the robot). Can you tell red from blue? From the front and the back?
4. START: a 1 s flash of the alliance, then your rules.
5. Run an autonomous on RED to its end, then INIT TeleOp: the light is red at once (TeleOp took the alliance from autonomous).
6. Write `public static boolean allianceAtInit = false;` in the light's Tunables section (named after its device, e.g. `Tunables.Light`). INIT: now your rules show in INIT, and no flash at START.

## Pass rule

Each state shows the right colour (4 of 4: blinking white, blue, red, the flash at START), and two
people can tell red from blue from 6 ft away.

## Record

- light model, and where it sits on the robot
- whether red and blue were easy to tell apart, and from where
- any colour that looked wrong (and which `allianceColors(...)` fixed it, if you tried)
- what step 6 showed

## If your robot is different

If your light shows red or blue badly, try `robot.lights.allianceColors(LightColor.PURPLE, LightColor.ORANGE);`
in `Controls.java` (blue's colour first) and say whether it is better. A REV Blinkin with a strip of
your own: say the strip and its length.

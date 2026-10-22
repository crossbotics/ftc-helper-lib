---
id: SET-02
title: A part that is not on the robot yet - optional() and the COMPETITION warning
area: setup
priority: high
where: both
minutes: 25
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [modules/robot.md#parts-not-on-the-robot-yet, guides/simulator.md#a-part-not-on-the-robot-yet]
---

## Why

Robots are built in steps. With `.optional()`, a part whose device is not in the configuration yet is
simply off: INIT goes on, its buttons are off, and actions and autonomous skip it. At an event the same
thing could hide a misspelled configuration name, so in the COMPETITION profile it is a loud warning.

## Safety

- Real robot: the other parts move as usual. Clear floor, a hand on STOP.

## Setup

- A part in `Robot.java` you can take out of the configuration (an intake, a servo). Add `.optional()` to its line, for example `intakeDeploy = add(Positioner.servo("intake_deploy").optional());`.
- An action or an autonomous step that uses it, and a button that uses it.
- Simulator: your robot from `Robot.java` (`:sim:fromRobot`), then add `missing = intake_deploy` (your device's name) as a line in `simulator/robot.properties`.
- Real robot: a copy of the configuration on the Driver Station without that device (or with its name changed).

## Steps

1. INIT your TeleOp (PRACTICE profile). Read the note: `"intake_deploy" not in the configuration: IntakeDeploy is off (optional part)`. INIT does not stop.
2. Open the button map (GUIDE): the part's buttons say `- off: IntakeDeploy not on the robot`.
3. START. Press the part's button: nothing moves, no error.
4. Run the action (or the autonomous): it goes on, and its report says `skipped IntakeDeploy (not on the robot)`.
5. Choose the COMPETITION profile (the last line of an autonomous's INIT menu; it is saved on the hub). INIT TeleOp again: the very first line is `!! COMPETITION: "intake_deploy" not in the configuration: IntakeDeploy is OFF ...`, and the button map says `!! OFF`.
6. Put the device back in the configuration (simulator: delete the `missing` line). INIT: the part works, with no code change.
7. Take `.optional()` off and remove the device again. INIT must stop and name the device. Put it back. Set the profile back to PRACTICE.

## Pass rule

INIT goes on with the part off (steps 1 to 4, no error), the COMPETITION warning is the first line of
the screen (1 of 1), and without `optional()` a missing device stops INIT (1 of 1).

## Record

- the part (kind and device name) and simulator or real robot
- the INIT lines in PRACTICE and COMPETITION (copy them)
- the action's report line from step 4
- anything that moved, or an error, while the part was off

## If your robot is different

Any part but the drive and the odometry can be optional. A part with several devices (a lift with a
switch, a launcher with a feeder): only the first device decides. Try removing a second device too:
INIT must stop and name it (half a part is a wiring mistake). Say what you tried.

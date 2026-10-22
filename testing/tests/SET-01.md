---
id: SET-01
title: Hardware Check finds every device and each motor counts up
area: setup
priority: high
where: real
minutes: 20
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.16.0
docs: [tutorials/19-testing-procedures.md, modules/testing.md#hardwarecheckopmode]
---

## Why

The Hardware Check is the first thing every team runs. It must find every device in your robot
configuration, on any robot, with no code from you. If it misses a device or shows a wrong hint, every
later lesson goes wrong.

## Safety

- Robot on a block: wheels off the ground. A drive motor can spin at 50% power in this test.
- Keep fingers out of gears, chains and lifts while a motor runs.

## Setup

- A robot with its configuration made on the Driver Station (any robot: a kit bot is fine).
- The 3-line Hardware Check OpMode in TeamCode ([how](../../docs/tutorials/19-testing-procedures.md#1-the-3-line-opmode)).
- A charged battery.

## Steps

1. Build and install. On the Driver Station, choose the TeleOp "Hardware Check", INIT, then START.
2. Count the devices in your configuration. Count the devices the Hardware Check lists (D-pad up and down goes through them).
3. For each motor: choose it, push gamepad 1's right stick up a little. Watch which way it turns and whether its encoder count goes UP.
4. For each servo: choose it, press X (it goes to 0.5), then D-pad right and left. It moves a little each press.
5. Read the battery line and the IMU line. (The IMU is the turn sensor inside the Control Hub. "Yaw" is the way the robot faces, in degrees.) Turn the robot left by hand: the yaw goes up.
6. Read each hint the screen shows for a device. Is it right for your robot?

## Pass rule

Every device in the configuration is in the list (the same count, for example 9 of 9), every motor
moves when you push the stick, and the battery line shows a voltage within 0.3 V of a meter (or of
the Driver Station's own battery number).

## Record

- devices in the configuration, devices the Hardware Check listed
- any device it did not find (its type in the configuration)
- motors whose encoder counted DOWN with positive power
- the battery voltage it showed
- any hint that was wrong or confusing

## If your robot is different

Any robot works: the point is that the check finds every kind of device. Unusual devices are the most
useful to report: a second Limelight, an I2C sensor the library does not know (I2C: the small 4-pin
ports on the hub, used by colour and distance sensors), or an Expansion Hub (a second hub, joined to
the Control Hub by a cable, for more motors and servos). A part marked `optional()` in `Robot.java` that
is not in the configuration should show as `optional, so the part is off (fine)`: say if it did not.
If a device type is missing from the list, say its type exactly as the configuration names it.

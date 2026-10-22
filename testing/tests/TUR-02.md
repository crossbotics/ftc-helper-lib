---
id: TUR-02
title: Turret with no switch - start angle and the angle carried to TeleOp
area: turret
priority: high
where: both
minutes: 30
needs: [turret]
kit_bots: []
since: unreleased
docs: [guides/turret-setup.md#2-how-does-it-know-its-angle, guides/turret-setup.md#from-autonomous-to-teleop]
---

## Why

A motor turret with no home switch only knows its angle if it starts where `startAngle` says. After
autonomous it is somewhere else, so TeleOp starts from the angle the last program ended at (for 5
minutes). If any of this goes wrong, every shot goes wide by the same angle.

## Safety

- The turret moves by itself while it follows the goal. Fingers and wires away from it.
- Hard stops at the wire limits.

## Setup

- A turret with no `.homeSwitch(...)` line, and `min`, `max` in `Tunables.Turret`.
- A mark or a small jig that holds the turret at `startAngle` (0 = straight forward, the default).
- An autonomous that turns the turret (it follows the goal, or a step turns it).
- Simulator: lesson 9's turret robot ([the commands](../../docs/tutorials/start/09-shooter-and-camera.md#try-it-in-the-simulator)): "TurretBot Shoot from Start", then "TurretBot TeleOp".

## Steps

1. Put the turret on its mark. INIT the autonomous: `Turret : startAngle 0 deg: put the turret there by hand before INIT, or every angle is off`.
2. Run the autonomous to the end. Write the turret's last angle.
3. Within 5 minutes, INIT TeleOp: `Turret : ... deg, carried from the last program (... s ago ...)`. The angle matches step 2.
4. START TeleOp, point the turret straight forward with the stick, and check the angle reads about 0.
5. Stop. Restart the robot app (or wait more than 5 minutes). Turn the turret 20 degrees by hand. INIT TeleOp: it must say `no angle from a program before: turn it to startAngle 0 deg by hand and INIT again`.
6. Write `min = 10, max = 170` (a range without 0) and no `startAngle`. INIT: no stop, and a note says it counts from the middle of the range (90). Put your numbers back.

## Pass rule

The carried angle in step 3 is within 2 degrees of where autonomous ended, step 4 reads within 3
degrees of the real angle, and step 5 shows the "no angle from a program before" line (1 of 1).

## Record

- motor, gear ratio, `min`, `max`, `startAngle`
- autonomous end angle and TeleOp's carried angle
- the real angle vs the Driver Station in step 4
- the lines shown in steps 5 and 6

## If your robot is different

With `keepAngle = false` in `Tunables.Turret`, TeleOp never carries the angle: say if you use it and why.
A turret with an absolute encoder (`.analogEncoder(...)`) knows its angle at INIT: check that its
`absolute encoder: ... deg` line is right instead of steps 1 and 5.

---
id: TUR-04
title: Slip-ring turret - round and round, always the short way
area: turret
priority: low
where: real
minutes: 30
needs: [turret]
kit_bots: []
since: v0.17.0
docs: [guides/turret-setup.md#3-how-far-can-it-turn]
---

## Why

A turret with a slip ring (a part that carries the wires through a turning joint) has no wire limits.
With `.continuous()` the library drops `min` and `max`, always turns the shortest way, and is never "out
of range". Few teams have one, so every report helps.

## Safety

- Check the slip ring by hand first: turn the turret 3 full turns each way with the robot on. No device may drop off.
- The turret can now turn without end: fingers, cables and other parts away from it.

## Setup

- `.continuous()` on the turret's line in `Robot.java`, and no `min`/`max` in `Tunables.Turret` (they are ignored; remove them).
- A way to know its angle at START: a home switch ([TUR-01](TUR-01.md)), an absolute encoder, or the start mark ([TUR-02](TUR-02.md)).
- A goal the camera can see (or odometry aim), to test following.

## Steps

1. INIT, START. Read the turret line: angles show from -180 to 180.
2. Point it at 170 with the stick, then ask for -170 (a button with `robot.turret.aimAt(-170)`, or follow a goal there). It turns 20 degrees through the back, not 340 the other way.
3. With following on, drive the robot in 3 full circles around the goal. The turret keeps on the goal and never says `goal out of turret range`.
4. Stop. Check the slip ring's wires and the turret's angle against a mark.

## Pass rule

Every move in step 2 takes the short way (2 of 2 tries, each way once), and after 3 circles the angle
is within 3 degrees of the real one with no device lost (0 drop-outs).

## Record

- slip ring model, turret motor or servo, gear ratio
- how it knows its angle (switch, absolute encoder, start mark)
- the angle error after 3 circles (degrees)
- any device that dropped out (the Driver Station or the hub's lights)

## If your robot is different

A CR servo turret with an absolute encoder on a slip ring is a common build: say which you have. If
your turret can turn more than one turn but not forever, do not use `.continuous()`: write `min` and
`max` (for example -190 to 190) instead, and test that with [TUR-02](TUR-02.md).

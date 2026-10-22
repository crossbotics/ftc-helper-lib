---
id: HOOD-03
title: Hood follows the shot table, and the hood trim
area: hood
priority: medium
where: both
minutes: 40
needs: [flywheel, hood-servo|hood-motor]
kit_bots: []
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#a-hood-the-shot-angle, guides/finders.md#shot-tuner-the-shot-table]
---

## Why

With a hood, each table row has a third number: the hood. The launcher blends it between rows like the
speed. The operator's left stick adds a hood trim. INIT must stop on a row outside the hood's stops.

## Safety

- Safety glasses if you fire. The hood moves at START (to `startAngle`).

## Setup

- HOOD-01 or HOOD-02 done. A table with three numbers per row, `{inches, rpm, hood}`.
- Speed from distance (camera or odometry).
- Simulator: your robot from `Robot.java` (`:sim:fromRobot` writes the hood), or `turretbot` from lesson 9.

## Steps

1. INIT, START. Drive to a distance halfway between two rows. Read `Hood: ... (table)`: it should be halfway between the two rows' hood numbers.
2. Gamepad 2 left stick up for a moment: `Hood: ... (table, trim +...)`.
3. Double-tap D-pad down: both trims (speed and hood) go back to 0.
4. Shot Tuner: Y until `> Hood`, change the hood with the D-pad, fire, mark HIT/MISS, X saves the speed and hood together.
5. Put a row with a hood number past a stop in `Tunables.java`. INIT: it must stop with a message naming the row.

## Pass rule

In step 1 the hood is within 1 degree (or 0.01 servo position) of halfway, step 3 clears both trims,
and step 5 stops INIT (1 of 1) with the row's distance in the message.

## Record

- the two rows and the hood value shown in step 1
- the trim step size you saw (degrees per moment of stick)
- the INIT message in step 5
- in the simulator: did the shot angle change with the hood?

## If your robot is different

Without `angleA`, `angleB` the hood works in servo positions (or ticks): the table's third number is a
position then. Say which you use. A linkage with a `calibration` table is a good extra test.

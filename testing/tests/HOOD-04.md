---
id: HOOD-04
title: Hood at START, and the stick with no goal in view
area: hood
priority: medium
where: both
minutes: 20
needs: [hood-servo|hood-motor]
kit_bots: []
since: v0.17.0
docs: [modules/robot.md#hood, guides/tunables.md]
---

## Why

At START the hood goes to its `startAngle`. While the shot table sets the angle, the operator's stick
is a small trim; while no goal is seen (no distance yet), the same stick moves the hood itself, so the
operator always sees the stick do something. A hood can also be part of an interlock rule. We want to
know this is clear to drivers on robots with a servo hood and with a motor hood.

## Safety

- The hood moves at START. Fingers away from it. Flywheel off for steps 1 to 4.
- Hard stops at both ends (`posA`, `posB` measured with the Hood Finder: HOOD-01 or HOOD-02).

## Setup

- A hood with its `Tunables.Hood` numbers (`posA`, `posB`, optional `angleA`, `angleB`) and `startAngle`.
- A shot table with a hood column (`{inches, rpm, hood}`) and speed from the distance.
- Simulator: the V2 example robot (a servo hood, commands in [ILK-01](ILK-01.md)), or your robot from `Robot.java`.

## Steps

1. INIT: the hood does not move, and the launcher's hood line says it goes to its start angle at START.
2. START: the hood goes to `startAngle`. Read the hood's line (named after its device, e.g. `Hood : 0.0 deg (by hand)`, or `(table)` once a goal is seen).
3. With the goal out of view (camera covered, or the robot turned away): push gamepad 2 left stick up for 1 s. The hood itself moves (the angle changes, `by hand`), and stops at its stop.
4. Turn to the goal: the table takes over (`(table)`), and the same stick now adds a trim (`table, trim +...`).
5. Double-tap gamepad 2 D-pad down: the trim goes back to 0.
6. In your code, if you use the hood's methods: `angle()` is where it is now, `targetAngle()` where it is going, `isAtTarget()` whether it is there, `goToStart()` sends it to `startAngle`. Build: the old names (`home()`, `homeAngle`, `isArrived()`) must not be used anywhere.

## Pass rule

At START the hood ends within 1 degree (or 0.01 servo position) of `startAngle` (1 of 1), in step 3 the
stick moves the hood with no goal in view (1 of 1), and in step 4 the table sets it again.

## Record

- servo or motor hood, and its range (degrees or positions)
- `startAngle`, and where the hood really ended at START
- the hood lines in steps 2, 3 and 4 (copy them)
- whether the drivers understood "by hand" vs "table"

## If your robot is different

A hood with no `angleA`, `angleB` works in servo positions (or ticks): the numbers on the screen are
positions then. If your hood and another part can hit, put it in an interlock rule
(for example `keepApart("HoodTurret", launcher.hood().zone(), turret.zone());` in `Robot.java`, see the
[interlocks guide](../../docs/guides/interlocks.md))
and also run [ILK-01](ILK-01.md).

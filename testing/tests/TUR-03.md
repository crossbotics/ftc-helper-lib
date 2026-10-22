---
id: TUR-03
title: Servo turret - two measured points and its start angle
area: turret
priority: medium
where: real
minutes: 30
needs: [turret]
kit_bots: []
since: v0.17.0
docs: [guides/turret-setup.md#1-what-turns-it, guides/finders.md#servo-finder-ends-and-positions, modules/hardware.md#full-range]
---

## Why

A turret on a standard servo has no encoder and no PID: the library turns an angle into a servo
position from two measured points. A servo cannot say where it is, so at START it is sent to its start
angle. A small, cheap turret is common on new teams' robots; we have only tried it in the simulator.

## Safety

- The servo moves at START. Fingers and wires away from it.
- Listen for a humming servo at the ends: that is a servo pushing on a stop. Back off at once.

## Setup

- `Turret.servo("turret")` in `Robot.java` (add `.follows(camera)` to test the aim too).
- `Tunables.Turret`: `posA`, `angleA`, `posB`, `angleB` from the [Servo Finder](../../docs/guides/finders.md#servo-finder-ends-and-positions) and a phone angle app, and `travelSec` (time a move from posA to posB).
- Optional: `.fullRange()` on the line for the servo's whole turn ([MEC-02](MEC-02.md)). Measure posA and posB after turning it on.

## Steps

1. INIT. Nothing moves. Read the turret's INIT lines.
2. START. The turret goes to its `startAngle` (or the middle of its range if you wrote none).
3. Turn it with the stick (gamepad 2 right stick X) to 0, then to each end. Measure the real angle each time with the phone app.
4. Turn it to 4 angles between the ends (by stick, or with `robot.turret.aimAt(degrees)` on a button). Measure each.
5. With `.follows(camera)`: let it follow the goal. It reads AIMED only after `travelSec` has passed since its last move.

## Pass rule

At each of the 6 angles in steps 3 and 4 the real angle is within 3 degrees of the Driver Station's,
and the servo never hums at an end (0 times).

## Record

- servo model, gears, and whether you used `.fullRange()`
- `posA`, `angleA`, `posB`, `angleB`, `travelSec`
- each angle: Driver Station vs measured
- how long AIMED took after a move (about)

## If your robot is different

A servo turret on a linkage (not straight gears) turns unevenly between the ends: say how far off the
middle angles were. A continuous-rotation servo with an encoder is a different kind:
`Turret.crServo(...)`, see the [turret setup guide](../../docs/guides/turret-setup.md#1-what-turns-it).

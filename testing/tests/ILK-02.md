---
id: ILK-02
title: Interlock stop distance - a fast part brakes before its zone
area: interlocks
priority: high
where: both
minutes: 30
needs: [lift|turret]
kit_bots: []
since: v0.17.0
docs: [guides/interlocks.md#stop-distance-a-fast-part-brakes-in-time, guides/tunables.md]
---

## Why

A motor does not stop the moment it is told to: the robot only decides once per loop, and a braking
motor still coasts. The rule works out each part's stop distance from its speed and the slowest recent
loops, and slows it down in time. We tested it in the simulator (a turret at about 680 degrees a
second); real lifts and turrets are heavier and slower to stop.

## Safety

- ILK-01 passed first, on this robot, with slow powers.
- Real robot: raise the power in steps (0.3, 0.6, then full). Stop at once if a part ends inside its zone.
- Fingers away from both parts. A hand on STOP.

## Setup

- An interlock rule that works at low power (ILK-01).
- The part that moves fast toward its zone (usually the turret or the lift) at its normal `maxPower`.
- Simulator: the V2 example robot as in ILK-01 (its turret is fast).
- Optional: `<part>StopSec` in the rule's Tunables section (for example `turretStopSec = 0.15`, the default).

## Steps

1. INIT, START. Put the other part in its zone (V2: wrist folded).
2. Push the fast part's stick fully toward its zone and hold it. Watch where it stops. Read its angle or counts on the Driver Station.
3. Write how far outside its zone it stopped. It should slow down near its waiting point, then stop there (two margins outside the zone).
4. Do it 5 times, from different starting places (near the zone and far from it).
5. Make the loops slow on purpose: turn on a camera, or run the simulator with other programs busy. Repeat step 2 three times.
6. If it ever ended inside the zone, or past its waiting point: raise `<part>StopSec` (say 0.25), INIT again, and repeat.

## Pass rule

In all 8 tries (steps 4 and 5) the part stops outside its zone (0 times inside), within about one margin
of its waiting point.

## Record

- the fast part, its motor and gear ratio, its `maxPower`
- the zone edge, its waiting point, and where it stopped each time
- the `StopSec` you ended with (and the margin, if you set one)
- simulator or real robot; slow loops: how you made them

## If your robot is different

A servo part needs no stop distance (it is never sent past its fence): test a motor part. A lift that
moves fast downward with gravity is a good test: say which way it moved. If your part has no motor id in
`Robot.java` (`.part(...)` or a motor id), the rule learns its top speed from the fastest it has seen:
say if the first runs stopped later than the later ones.

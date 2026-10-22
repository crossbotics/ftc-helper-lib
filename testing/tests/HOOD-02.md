---
id: HOOD-02
title: Motor hood - stops by hand, and it arrives before firing
area: hood
priority: high
where: real
minutes: 30
needs: [hood-motor, flywheel]
kit_bots: []
since: v0.17.0
docs: [guides/finders.md#hood-finder, modules/robot.md#hood]
---

## Why

A motor hood knows where it is (its encoder), so the launcher waits until it arrives. Its ticks count
from where it is at INIT, so the start position matters. We have not tested a motor hood on a robot
yet.

## Safety

- Flywheel off for steps 1-4. Low `maxPower` (0.3) at first. Fingers away from the hood.
- Hard stops on the hood at both ends.

## Setup

- `.hood(Hood.motor("hood", "motor-id"))` on the launcher.
- The Hood Finder OpMode.

## Steps

1. Put the hood on the stop you want as 0 ticks (stop A). Start the Hood Finder: nothing is powered.
2. Move the hood by hand to near stop A (a hair off), press X. To near stop B, press B.
3. Copy `posA`, `posB` (ticks) into `Tunables.Hood`. Set `maxPower = 0.3`.
4. In your TeleOp (hood on stop A at INIT): START. Move the hood with gamepad 2 left stick to each end. It must stop before each stop.
5. With a shot table that has a hood column, spin up and fire one at two distances: the launcher waits for `hood` until the hood arrives (`Launcher waits for: hood`).

## Pass rule

The hood stops within 2% of its range before each stop (never stalls on a stop), and in step 5 no
piece is fed while the hood is more than `tolerance` away (0 of 2).

## Record

- hood motor, gearing, range in ticks
- the `posA`, `posB` you got, and `kP`, `maxPower`, `tolerance` you used
- how long the hood took to arrive in step 5 (seconds)
- what happened if the hood was NOT on stop A at INIT (try it once, carefully, at low power)

## If your robot is different

A hood that is not near a stop at INIT will count from the wrong place: that is how it is built
today. If your robot cannot start on a stop, tell us why: we may add homing.

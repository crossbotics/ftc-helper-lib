---
id: HOOD-01
title: Hood Finder on a servo hood - stops and angles
area: hood
priority: high
where: real
minutes: 30
needs: [hood-servo]
kit_bots: []
since: unreleased
docs: [guides/finders.md#hood-finder, tutorials/start/09-shooter-and-camera.md#a-hood-the-shot-angle]
---

## Why

The hood's two stops (`posA`, `posB`) are hard limits: the hood never goes past them. The Hood Finder
finds them without code. A servo pushing on a stop gets hot and breaks, so this must be clear and
safe on hoods built differently from ours.

## Safety

- Flywheel off. Fingers away from the hood while it moves.
- Listen for a humming servo: back off at once.

## Setup

- `.hood(Hood.servo("hood"))` on the launcher in `Robot.java` (no `Tunables.Hood` numbers needed yet).
- The Hood Finder OpMode ([recipe](../../docs/cookbook.md#how-do-i-find-my-hoods-stops-and-angles)).
- A phone angle app (optional, for angles).

## Steps

1. Start the Hood Finder. Choose a START POINT near where the hood sits (D-pad; nothing moves), press A.
2. Nudge toward one stop (D-pad right/left 0.01, up/down 0.05, hold RB for finer). Stop when it JUST touches, back off a hair, press X.
3. Do the same at the other stop, press B. The screen shows `Tunables.Hood: posA = ..., posB = ...`.
4. Optional: lay the phone on the hood at each stop and type the angle (hold LB + D-pad) before X or B.
5. Copy the line(s) into `Tunables.java`. Time a stop-to-stop move for `travelSec`.
6. In your TeleOp: START sends the hood to `startAngle` (in `Tunables.Hood`). Gamepad 2 left stick moves it: push it hard both ways.

## Pass rule

In step 6 the hood never touches a stop (0 hums or stalls in 10 pushes each way), and it stays
between `posA` and `posB`.

## Record

- hood design (servo, gears or linkage, range in degrees)
- the `Tunables.Hood` line it printed
- `travelSec` you measured
- anything that hummed, and when

## If your robot is different

A linkage (equal servo steps do not make equal angles): add points in between with A to get a
`calibration` line. A hood that goes the other way round is fine: say so. A CR-servo hood is not
supported yet: report "could not run" and what you have.

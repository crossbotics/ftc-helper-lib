---
id: SHOT-23
title: Untuned bang-bang starts from your kF and reaches the speed
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel]
kit_bots: []
since: v0.48.0
docs: [reference/parts/launcher.md]
---

## Why

With `speedControl = "BANG_BANG"` and no `bangKv`, the flywheel starts from the team's written `kF`, not
the catalog's guess. A `kF` found on the real wheel must get the wheel to its speed before the Flywheel
Tuner is done.

## Safety

- Eye protection. Robot on a block, nobody in front of the launcher, no game pieces loaded.
- Keep a person ready on STOP.

## Setup

- A launcher with a tuned `kF` (Flywheel Finder) and `nominalVolts` in the Launcher part of Tunables.java.
- `speedControl = "BANG_BANG"`, and no `bangKv` line.

## Steps

1. INIT TeleOp. Read the notes about the wheel power.
2. Spin the flywheel at your shortest shot's speed. Watch the speed and READY for 5 s.
3. Do step 2 at your longest shot's speed.
4. Add `bangKv` with any number. INIT again and read the notes.

## Pass rule

Step 1 says "Wheel power starts from your kF (bangKv)." and that it is not tuned. In steps 2 and 3 the wheel
shows READY and stays within the ready band. Step 4 shows no note about the wheel power.

## Record

- the motor, `kF`, `nominalVolts`, the battery volts at the start
- the speeds you set and the speeds the screen showed after 5 s
- the notes from steps 1 and 4 (copy them)

## If your robot is different

With no `kF` line, step 1 must say "Wheel power is a catalog guess (bangKv)." instead.

---
id: SHOT-06
title: First spin-up with no flywheel numbers
area: shooter
priority: high
where: both
minutes: 10
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: v0.17.1
docs: [guides/tunables.md#first-run-what-you-get-before-tuning, tutorials/start/09-shooter-and-camera.md#the-first-spin-up-with-no-numbers]
---

## Why

A new launcher should work before anyone has run the Flywheel Finder. With no `kP` to `kF` and no
`speedRpm` in `Tunables.java`, the library starts from numbers worked out from the motor in the parts
catalog (kF 11.7, kP 1.17, kI 0.117 and 3600 RPM for a goBILDA 6000 RPM motor). We want to know these
starting values spin YOUR wheel to its speed.

## Safety

- Safety glasses. Nobody in front of the launcher. No pieces loaded.
- Robot on a block or held so it cannot move.

## Setup

- `Launcher.flywheel("name", "motor-id")` in `Robot.java`, with the real motor's catalog id.
- In `Tunables.java`, the launcher's section with NO `kP`, `kI`, `kD`, `kF` and NO `speedRpm` lines (comment
  them out for this test, and put them back after).
- A charged battery (12.5 V or more).

## Steps

1. INIT your TeleOp. Read the note `Launcher: starting values from the parts catalog (...)` and write it down.
2. START. Press LB to spin up. Watch the speed on the Driver Station for 10 seconds.
3. Note how long it takes to show READY, and the speed it settles at.
4. Spin down. Put your own lines back in `Tunables.java`.

## Pass rule

INIT shows the starting-values note, and in step 2 the speed reaches READY within 3 s and stays within
3% of the target.

## Record

- flywheel motor (catalog id), gearing, wheel (size, weight if known)
- the INIT note (its kF, kP, kI and speed)
- time to READY (seconds), and the speed it settled at (RPM)
- the battery voltage

## If your robot is different

A geared flywheel or a belt: the catalog speed is the motor's, so say how it is built. A flywheel with
no encoder cable cannot use this test: report "could not run".

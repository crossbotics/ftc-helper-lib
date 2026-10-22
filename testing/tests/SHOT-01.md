---
id: SHOT-01
title: Flywheel Finder - gains that hold the speed
area: shooter
priority: high
where: real
minutes: 30
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: v0.16.0
docs: [guides/finders.md#flywheel-finder-gains-and-recovery, tutorials/start/09-shooter-and-camera.md#2-its-numbers]
---

## Why

The Flywheel Finder works out the speed-control numbers (the gains) from one full-power spin. Flywheels
differ a lot (motor, gearing, wheel weight). We want to know its gains hold the speed on your
flywheel, with no tuning by hand.

## Safety

- Safety glasses. Nobody in front of the launcher. No pieces loaded until step 5.
- Robot on a block or held so it cannot move.

## Setup

- `Launcher.flywheel("name", "motor-id")` in `Robot.java` (or any flywheel motor with an encoder cable).
- The "Flywheel Finder" OpMode (starter kit).
- A charged battery (12.5 V or more).

## Steps

1. Start "Flywheel Finder". Choose the flywheel motor.
2. Let go of A, then hold A: full power until the speed stops rising. Wrong way? Let go, press X (reverse), try again.
3. Press B: it holds a speed. Use the D-pad to set the speed you shoot at.
4. Watch the measured speed for 10 seconds. Use RB (more F) or LB (less F) only if it stays above or below.
5. Feed one piece by hand. Read the recovery time.
6. Copy the `Tunables.Launcher: kP = ..., kF = ..., nominalVolts = ...` line into `Tunables.java`. Spin up in your TeleOp with LB and read the speed.

## Pass rule

In step 4 the measured speed stays within 3% of the target, and in step 6 your TeleOp reaches the
speed and shows READY within 1.5 s of pressing LB.

## Record

- flywheel motor (catalog id), gearing, wheel (size, weight if known)
- the line it printed (gains and `nominalVolts`)
- the speed error in step 4 (RPM), and whether you used RB or LB
- recovery time after a piece (seconds)
- time to READY in step 6

## If your robot is different

Two motors on one flywheel, a geared flywheel or a belt: say how it is built. A catapult or a
launcher with no encoder cannot use this test: report "could not run" with what you have.

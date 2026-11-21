---
id: SHOT-21
title: The Flywheel Finder holds with the gains in Tunables.java
area: shooter
priority: medium
where: real
minutes: 15
needs: [flywheel]
kit_bots: []
since: unreleased
docs: [tuning/06-flywheel-finder.md]
---

## Why

A team with gains in Tunables.java (for example kP 10, kI 0, kF 14.68) wants to check and fine-tune kF for THOSE
gains. Before, step 2 held the wheel with the finder's own gains (with a kI), so the kF it found did not fit the
robot. Now, from Tune and check, step 2 holds with the gains in Tunables.java, and the list starts on the flywheel.

## Safety

- Eye protection. Hands and loose clothing away from the wheel.

## Setup

- A launcher with kP, kI and kF in its part of Tunables.java.
- Open Tune and check, then the Flywheel Finder.

## Steps

1. Check that the list starts on the flywheel motor, not a drive wheel. Press A.
2. Check the `Gains` line: it shows the kP, kI and kF from Tunables.java.
3. Press B (no step 1 first). Set the target to your 36 in speed with the D-pad.
4. Wait 3 s. Read `Error`. Press RB or LB until it stays within 30 RPM.
5. Check the Tunables line at the bottom: the same kP and kI, the new kF.

## Pass rule

The list starts on the flywheel; step 2 starts without step 1; the Tunables line keeps kP and kI and shows the
new kF; with that kF in Tunables.java, TeleOp holds the same speed within 30 RPM.

## Record

- the gains in Tunables.java, the kF found, the error before and after, the battery volts

## If your robot is different

No gains in Tunables.java: the finder uses its own, as before (step 1 first). Your own finder class (not Tune and
check): override `robotGains()`.

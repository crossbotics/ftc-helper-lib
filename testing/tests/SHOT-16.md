---
id: SHOT-16
title: Flywheel Finder: a reversed launcher spins right, a heavy wheel is seen
area: shooter
priority: medium
where: both
minutes: 10
needs: [flywheel]
kit_bots: []
since: unreleased
docs: [tuning/06-flywheel-finder.md, modules/finders.md#flywheelfinderopmode]
---

## Why

A launcher with `.reversed()` in Robot.java spins the other way from the motor's normal direction. The Flywheel
Finder in Tune and check now starts the motor that way too. Before, it started forward: the wheel spun
backward, and a piece fed in jammed on it and stopped it. We want to know that the wheel spins the same way in
the finder as in TeleOp. It also warns when a heavy wheel still speeds up at the end of step 1's 5 s.

## Safety

- Eye protection. Nobody in front of the launcher.
- Feed a piece with the robot's feeder or a push stick, never with fingers.

## Setup

- A launcher with `.reversed()` in Robot.java (`Launcher.flywheel(...).reversed()`).
- 2 pieces.

## Steps

1. In TeleOp, spin the wheel up. Look at which way the wheel spins (a tape mark on it helps).
2. Open Tune and check, then the Flywheel Finder. Choose the launcher's motor. Read the `Direction` line.
3. Let go of A, then hold A. Look at which way the wheel spins.
4. Press B (hold a speed). Feed one piece with the feeder or a stick.
5. Hold A for more than 5 s in step 3. Note if the screen says `Still speeding up at 5 s`, and if the
   `Speed` line still went up at the end.
6. Simulator: do steps 2 and 3 with the robot file of a robot with `.reversed()`.

## Pass rule

In step 2 the line reads `Direction : reversed, as in Robot.java`. In step 3 the wheel spins the same way as in
step 1. In step 4 the piece leaves the launcher, and the wheel does not stop. The results have no line to add
`.reversed()` to Robot.java. In step 5 the warning shows only when the speed still went up at the end.

## Record

- the motor, the wheel, and the library version
- the `Direction` line in step 2
- what the piece did in step 4
- the top speed, and if the warning showed in step 5 (and any weight added to the wheel)

## If your robot is different

A launcher without `.reversed()`: the line reads `Direction : normal, as in Robot.java`, and the test is the
same. A Flywheel Finder of your own (not from Tune and check) starts forward, as before.

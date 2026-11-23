---
id: SHOT-24
title: The Flywheel Tuner stops at any button and finds both controls' numbers
area: shooter
priority: high
where: real
minutes: 60
needs: [flywheel]
kit_bots: []
since: unreleased
docs: [tuning/06-flywheel-tuner.md]
---

## Why

The Flywheel Tuner runs the flywheel by itself for a long time. Any button must stop it at once, and it must
never start again without a new press. Its numbers are only worth copying when the wheel really holds them
on the robot.

## Safety

- Safety glasses. Robot on a block for runs 1 and 2, nobody in front of the launcher, no balls in the robot.
- The driving check drives the robot: take it off the block, and clear the floor 5 ft around it.
- Keep a person ready on STOP.

## Setup

- A robot in five files with a launcher. Its `kF` found with the Flywheel Finder.
- A FULL battery (12.6 V or more) and a used battery (12.0 to 12.4 V).

## Steps

1. Tune and check, Flywheel Tuner. INIT, then ▶. Press A. During the countdown, press X.
2. Press A again. When the wheel spins, press B. Watch the wheel and the screen.
3. Press A again, and let run 1 finish. Write down the test number where it stopped in step 2.
4. Press STOP, turn the robot off, put in the used battery, open the Flywheel Tuner again. Press A.
5. Do both driving checks. Copy the lines from pages 2 and 3 into `Tunables.java`.
6. In TeleOp, try the PID numbers, then the bang-bang numbers (`speedControl = "BANG_BANG"`). Spin up at your
   middle shot distance and watch READY.

## Pass rule

Step 1: the wheel never moves. Step 2: the power goes off at once, the wheel rolls to a stop, and the screen
says STOPPED and "go on from test N". Step 3 goes on from that test. Step 4 says run 2 and goes on by itself.
Step 6: with each set of numbers the wheel reaches READY and holds it.

## Record

- the motor, the wheel (its weight), `kF`, `nominalVolts`, both batteries' volts
- the four lines of page 1 (the result), and pages 2 and 3
- any setting that failed, and why (the screen's "Why:" line)
- whether the motor felt hot after run 1

## If your robot is different

With no shot table, the tuner uses `speedRpm` and 10% either side. Say so in the record.

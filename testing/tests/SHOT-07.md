---
id: SHOT-07
title: Many quick taps on the fire button
area: shooter
priority: medium
where: both
minutes: 10
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: v0.24.0
docs: [guides/tunables.md#launcher, tutorials/start/09-shooter-and-camera.md#4-the-two-safety-rules]
---

## Why

Drivers tap the fire button fast: some mean "fire four", some just want it to fire NOW. Each
tap on RB (or on a button that calls `launcher.fire()`) adds one shot, but at most `maxFiringQueue` shots wait (default 4). We want to
know that the limit feels right to real drivers, and that extra taps never fire pieces later.

## Safety

- Safety glasses. Nobody in front of the launcher.
- Robot on a block or held so it cannot move.

## Setup

- The launcher's own RB button (or a button of yours that calls `robot.launcher::fire`).
- Each burst must start from a stopped wheel: before each one, STOP the OpMode, wait until the wheel
  stops, then INIT and START again.
- 6 pieces loaded (or as many as the robot holds).

## Steps

1. START. With the wheel stopped, tap the button 4 times fast, all within about half a second (the
   limit counts shots that WAIT: while the wheel spins up, nothing is fed yet). Count the pieces that fire.
2. STOP, reload, INIT and START again. Tap the button 10 times fast. Count the pieces that fire.
3. Set `maxFiringQueue = 1` in `Tunables.java` and build the code again (or change it in the tuning
   menu). Repeat step 2.
4. Put your own number back in `Tunables.java`.

## Pass rule

Step 1 fires 4 pieces. Step 2 fires 4 pieces (or fewer if the robot held fewer). Step 3 fires 1
piece. Taps after a piece was fed may add shots again: that is not a fail.

## Record

- the pieces fired in each step
- the time from the first tap to the first piece (seconds)
- what your drivers think of the default of 4

## If your robot is different

No feeder (pieces pushed in some other way): report "could not run".

---
id: SIM-04
title: Record a test in the simulator, break it, update it
area: simulator
priority: high
where: sim
minutes: 40
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [tutorials/start/10-test-your-robot-code.md, guides/simulator.md#record-a-test]
---

## Why

The simulator's recorder writes a test from what a driver does on the page. We need to know if
students can record a test of their own robot, if the suggested checks make sense, and if a recorded
test passes when it is run again (a test that fails for no reason is worse than none).

## Setup

- Your robot project with the simulator running your code: `./gradlew :sim:run -PteamCode=../TeamCode`.
- Your robot drives in the simulator, and has an autonomous.

## Steps

1. Follow [start lesson 10](../../docs/tutorials/start/10-test-your-robot-code.md), section 2: record a
   test of your autonomous (INIT, choose the alliance, START, wait for its end, Stop recording).
2. Read the suggested checks. Write down any check that looks wrong or missing. Save it.
3. Press **Run it now**. Then run it 4 more times (**Run it again**).
4. Record a TeleOp test: drive forward 2 seconds, turn, let go, Stop recording, Save, Run it now (5 times).
5. Change one number on purpose (for example `Tunables.Drive.maxPower` to 0.6), restart the simulator,
   and run both tests.
6. For a test that fails: **The change was on purpose: update the test**, **Save the update**, **Run it again**.
7. Run all tests with one command: `./gradlew :sim:scenario` (from the `ftc-helper-lib` folder).
8. Put the number back.

## Pass rule

Each recorded test passes 5 runs out of 5 before the change, the change makes at least one fail, and
after the update it passes again.

## Record

- robot (drive kind, parts), OpMode names
- how many of the 5 runs passed, for each test, before the change
- each suggested check that looked wrong or was missing
- which test failed after the change, and the lines the update changed
- the result of `./gradlew :sim:scenario`
- anything in the window that was hard to understand

## If your robot is different

Any robot that runs in the simulator works. No autonomous yet? Record two TeleOp tests (driving, and
your intake or main mechanism). What matters: a recorded test passes again and again, and fails only
when the robot really changed.

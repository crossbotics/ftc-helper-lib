---
id: SIM-05
title: A simulator test stands in for a number not measured yet (set tunable)
area: simulator
priority: medium
where: sim
minutes: 15
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.18.2
docs: [tutorials/start/10-test-your-robot-code.md, guides/simulator.md#scenario-files-test-without-a-browser]
---

## Why

A number written as `MEASURE` in Tunables.java stops INIT until it is measured on the robot. The
scenario line `set tunable Section.field VALUE` lets a simulator test run before that. We need to know
that it works on your robot's code, that it changes nothing for the next test file, and that a typo
gives a message a student understands.

## Setup

- Your robot project with the simulator running your code, and one test of yours in `simulator/scenarios/`
  (record one first: [start lesson 10](../../docs/tutorials/start/10-test-your-robot-code.md)).

## Steps

1. Pick one number in your Tunables.java (for example a servo position). Write down its value.
2. Change it to `MEASURE` in Tunables.java. Run your test: `./gradlew :sim:scenario`. INIT stops and names the number.
3. Add `set tunable Section.field VALUE` before `init` in the test (your section and field, the value you wrote down). Run it again: it passes.
4. Make a second test file without that line (copy the first, delete the line). Run both with one command: the second one stops at INIT again (the number did not carry over).
5. Make a typo in the name (for example one letter wrong). Run it: read the message.
6. Put the number back in Tunables.java and delete the `set tunable` line.

## Pass rule

3 runs out of 3: with the line the test passes, the next file without it stops at INIT, and the typo
message names the right field ("did you mean ...").

## Record

- the section and field you used, and its type (a number, or true/false)
- the result of each run (passed, or the message)
- the typo message, and whether it was clear

## If your robot is different

Any robot with a Tunables.java works. No `MEASURE` lines? Step 2 makes one.

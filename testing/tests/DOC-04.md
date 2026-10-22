---
id: DOC-04
title: Lesson 10 from start to finish - record, break and update a test
area: docs
priority: medium
where: sim
minutes: 45
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.1
docs: [tutorials/start/10-test-your-robot-code.md]
---

## Why

Lesson 10 teaches simulator tests with the new "Record a test" button. Most students never wrote a
test before. We need to know if the lesson is clear enough to follow alone, on a robot that is not
ours.

## Setup

- Your robot project with the library v0.17.1 or newer, and the simulator working (SIM-01).
- No robot needed.

## Steps

1. Start a timer. Follow [lesson 10](../../docs/tutorials/start/10-test-your-robot-code.md) from the top,
   exactly as written, with your own robot (or its "Try it with goBILDA's starter bot").
2. Record one TeleOp test and one autonomous test, and save both.
3. Run each one: both should say `PASSED`.
4. Change one number in `Tunables.java` that the test should notice (for example a slower drive), build,
   and run the tests again: at least one should say `FAILED`.
5. Use "The change was on purpose: update the test", save, and run again: `PASSED`.
6. Put the number back. Run all your tests with the one command from section 8.
7. Write each sentence or step that did not match your screen, or that you had to read twice.

## Pass rule

Both tests pass, fail after the change in step 4, and pass again after the update, within 45 minutes,
with no help from outside the lesson.

## Record

- time (minutes)
- which robot (kit or your own)
- each step that did not match your screen, and each sentence you had to read twice
- which checks the recorder ticked for you, and any you changed
- did a test ever fail when you changed nothing (how often)

## If your robot is different

Any robot works: the tests check what your robot does. If your robot has no autonomous yet, record
two TeleOp tests instead and say so.

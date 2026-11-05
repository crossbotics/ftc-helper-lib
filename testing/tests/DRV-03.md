---
id: DRV-03
title: Motor Direction Check - each drive wheel rolls forward
area: drive
priority: high
where: both
minutes: 10
needs: []
kit_bots: []
since: v0.30.0
docs: [guides/finders.md#motor-direction-check-each-wheel-rolls-forward, modules/drive.md#check-motor-directions]
---

## Why

A drive motor that turns the wrong way makes the robot drive in circles, strafe the wrong way, or fight
itself. The check must spin each motor exactly as Robot.java sets it up.

## Setup

- A robot in five files with a drive. Its simulator robot file is fine for steps 1 to 4.
- **The robot on a block, wheels off the ground.**

## Safety

- The wheels spin. Keep hands and cables clear of them.

## Steps

1. INIT **Tune and check**. **Motor Direction Check** must be in the menu, after the Hardware Check.
2. Open it, press START. Nothing may move until you hold a button.
3. Hold X. Only the front-left motor may run (tank: the left one). The screen must say its name, and
   "(reversed)" if Robot.java reverses it. Let go: it must stop.
4. Do the same with Y, A and B.
5. On the real robot: every wheel must roll the way that drives the robot forward. If one does not, add its
   name to `reversed(...)` on the drive line (or take it out), INIT again and check that wheel again.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- Which motors needed `reversed(...)`.

## If your robot is different

A 2-motor tank drive has only X (left) and Y (right).

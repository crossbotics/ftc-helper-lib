---
id: SIM-12
title: A fire action with its own flywheel speed (auto builder flywheel box)
area: shooter
priority: medium
where: sim
minutes: 10
needs: [flywheel]
kit_bots: [gobilda]
since: unreleased
docs: [guides/simulator.md#the-auto-builder, guides/autonomous-builder.md#4-actions-several-parts-together]
---

## Why

A robot with no camera shoots at one speed, `speedRpm`. An autonomous that shoots from two spots needs
a speed for each. `robot.launcher.fireAllAction(rpm)` fires at that speed, then goes back to `speedRpm`.

## Setup

- SIM-01 works on this laptop.
- A robot file for a robot with a flywheel launcher (the goBILDA starter bot).

## Steps

1. Open the simulator and press **Auto builder**. **Simple steps**. Add an action step and pick
   `robot.launcher.fireAllAction()`. A **flywheel** box must show next to it, empty ("usual").
2. Type 2000 in the flywheel box. The Java on the page must say `.action(robot.launcher.fireAllAction(2000), ...)`.
3. Empty the box. The Java must say `fireAllAction()` again.
4. Type a speed again and press **Run**. While it fires, the Driver Station's launcher target must be
   that speed. After the action, it must be back at `speedRpm`.
5. Copy the Java into a new file in TeamCode, build, and pick it in the builder's "Start from one of your
   autos". The step must load with the same speed in its box.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- The robot file and the speeds you used.

## If your robot is different

Any robot with a `Launcher` works. A robot with a camera and a shot table: the fire action's own speed
wins over the table while it fires, then the table is used again.

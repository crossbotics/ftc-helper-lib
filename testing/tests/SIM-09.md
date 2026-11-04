---
id: SIM-09
title: Edit an auto on the builder map (add a point between two, drag to turn)
area: simulator
priority: medium
where: sim
minutes: 15
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [guides/simulator.md#the-auto-builder]
---

## Why

A team fixes its autonomous on the map, not in the numbers. Putting a point between two steps, and
dragging a point to change the turn before it, must work on a tank drive and a mecanum drive, and must
never move the other points.

## Setup

- SIM-01 works on this laptop.
- Your robot from `Robot.java`, or a kit bot's robot file.

## Steps

1. Open the simulator and press **Auto builder**. In **Simple steps**, press **Start over**.
2. With "drive there" picked, tap the map 3 times to make 3 drives that turn a corner.
3. Read the step numbers on the map. Each one must be easy to read (white on a dark badge).
4. Move the mouse over the line of the first drive. A white dot with a plus must slide along the line.
5. Click. A new point must appear there, and the steps list must have one more drive. The route must not change.
6. Drag the new point to the side. The line must bend there. Points 2 and 3 (now 3 and 4) must stay where they were.
7. Drag the last point to the side. The robot must turn to drive to it (a tank drive: the turn step before it changes).
8. Press **Undo** 3 times. The plan must go back to the 3 drives from step 2.
9. Press **Run**. The robot must drive the plan.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- Your robot: tank or mecanum.
- Any step where a point moved that you did not drag, or a new point did not appear.

## If your robot is different

A mecanum drive can drive sideways: in step 7, a drive with no turn before it changes its direction (a
strafe) instead of a turn.

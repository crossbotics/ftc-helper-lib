---
id: SIM-11
title: Auto builder taps drive the way the robot faces (a spot behind backs up)
area: simulator
priority: medium
where: sim
minutes: 10
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.28.0
docs: [guides/simulator.md#the-auto-builder]
---

## Why

A tap on the map adds a drive to that spot. A spot behind the robot must back up to it, not turn the
robot around first: it saves time, and it is what a driver would do.

## Setup

- SIM-01 works on this laptop.
- One mecanum robot file and one tank robot file (the goBILDA starter bot is tank; a Strafer or the
  Pedro example robot is mecanum).

## Steps

1. Open the simulator with the mecanum robot and press **Auto builder**. **Simple steps**, "Tap adds:
   drive there". Tap a spot straight ahead of the robot. The new step must be a move forward (direction 0).
2. Tap a spot behind the robot. The new step must be one move with direction 180 (it backs up), and no turn.
3. Open the tank robot. Tap a spot behind the robot and a little to one side. The builder must add a
   small turn (under 90 degrees) and a move with direction 180: the robot backs up to the spot.
4. Tap a spot ahead and to one side. The builder must add a turn and a move forward.
5. Press **Pedro path** (the mecanum robot), "Tap adds: a line", and tap a spot behind the robot. The new
   part's heading must be **Drive backwards along it**. Tap a spot ahead of that point: **Face along the path**.
6. Press **Run** for the Simple steps of step 3. The robot must back up to the tapped spot.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- The robot files you used.
- Any tap that turned the robot around (more than 90 degrees) to drive forward.

## If your robot is different

Any mecanum and any tank robot file work. On crossbotics.com's path builder (no robot file), steps 1 to 5
work the same with its Mecanum / Tank switch.

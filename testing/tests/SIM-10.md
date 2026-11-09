---
id: SIM-10
title: Edit a Pedro path on the builder map (a point between two, when it turns)
area: simulator
priority: medium
where: sim
minutes: 15
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.27.0
docs: [guides/simulator.md#the-auto-builder]
---

## Why

A Pedro path is fixed on the map. Putting a point between two, and choosing when the robot turns on a
part, must change only that part.

## Setup

- SIM-01 works on this laptop.
- A robot file for a mecanum robot with odometry. The Pedro example robot works:
  `-Probot=` its robot file, or the `pedrobot` example.

## Steps

1. Open the simulator and press **Auto builder**. Press **Pedro path**, then **Load the Pedro lesson's example**.
2. Move the mouse over part 2 (the curve). A white dot with a plus must slide along it.
3. Click. A new point must appear there, and the list must have one more part. The curve must keep its shape.
4. Drag the new point to the side. Only the parts next to it may change.
5. On the last part, choose **Turn from one heading to another**, then **drive, then turn (last 30%)**.
   The robot outlines on the map must keep the first heading until near the end of the part.
6. Drag the yellow knob at the end of the last part. The end heading in the list must change.
7. Choose **turn to face** in "Tap adds", select part 1, and tap a spot on the field. Part 1 must now end
   facing that spot (its yellow knob points at it).
8. Right-click on the field. The menu must offer "Line to here", "Curve to here" and "turn to face here".
9. Press **Run**. The robot must follow the path and end facing the end heading.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- The robot file you used.
- Any step where a part changed that you did not touch.

## If your robot is different

A tank robot cannot run Pedro paths: the builder says so, and **Run** stays off. Steps 1 to 6 still work.

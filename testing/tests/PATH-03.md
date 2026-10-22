---
id: PATH-03
title: Path Builder web page - draw a path, copy the code, run it
area: paths
priority: medium
where: sim
minutes: 30
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.16.0
docs: [tutorials/start/08-paths-with-pedro.md#5-write-your-first-path]
---

## Why

The Pedro Path Builder (https://crossbotics.com/ftc-helper/path-builder/) draws the BIOBUZZ field,
shows where the robot would hit things, and writes the `PedroPath` code. We want to know the code it
writes drives the same path in the simulator, and that the page is easy to use.

## Setup

- A browser (a laptop is best; also try a phone if you can).
- For step 5: the Pedro example robot in the simulator (see PATH-01).

## Steps

1. Open the Path Builder. Make a path with a line and a curve that goes around a field element.
2. Drag the points until the page shows no red (no hits).
3. Copy the code it writes.
4. Paste it into the Pedro example autonomous (`examples/.../pedrobot/autos/CurveToTheGoal.java`, the `TO_GOAL` line) in your library folder (put the old line back afterwards), or into your own autonomous.
5. Run it in the simulator (PATH-01's commands). Compare the orange plan on the simulator's map with the drawing in the Path Builder.

## Pass rule

The code builds the first time, and the simulator's orange plan matches the Path Builder's drawing
within about 2 in at every point; no hit in the simulator where the builder showed none.

## Record

- browser and device
- the code you copied
- any place where the simulator and the builder disagreed (where, how much)
- what was hard to do on the page

## If your robot is different

Your robot's size matters for hits: if the builder lets you set the robot size, use yours. A robot
bigger than 18 x 18 in that hits in the simulator but not in the builder is a good report.

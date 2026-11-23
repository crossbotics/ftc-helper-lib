---
id: SHOT-22
title: continuousOnHold feeds an aimed group and stops when you let go
area: shooter
priority: high
where: both
minutes: 20
needs: [flywheel, webcam|limelight]
kit_bots: []
since: v0.48.0
docs: [reference/parts/launcher.md]
---

## Why

Holding fire can run the feeder with no break. It must never start without the goal and the aim, and it must
stop as soon as you let go.

## Safety

- Eye protection. Nobody in front of the launcher.
- The robot can turn by itself while you hold fire. Keep room around it.
- Let go of fire to stop. Keep a person ready on STOP.

## Setup

- A launcher with a CR servo or motor feeder, `speedFromDistance(camera)` and a shot table.
- `continuousOnHold = true` in the Launcher part of Tunables.java.
- Four game pieces for each group.

## Steps

1. In TeleOp, face away from the goal and hold fire for 1 second. Let go.
2. Face the goal and tap fire.
3. Load four pieces, aim, and hold fire until they are gone.
4. Load four again. Hold fire, and let go after the first piece.
5. Hold fire again. While it feeds, open the button list and close it. Keep holding for 1 second.
6. Hold fire again. While it feeds, press pause, then press it again. Keep holding for 1 second.

## Pass rule

In step 1 nothing feeds and the screen says the goal is NOT seen, step 2 fires 1 piece, step 3 feeds with no
pause, and in 3 of 3 stops (steps 4 to 6) the feeder stops at once and does not start again by itself.

## Record

- the robot, the camera, the feeder, the library version
- pieces fired in each step, and the screen's words for each stop
- how many of the four pieces scored in step 3

## If your robot is different

A push servo cannot feed with no break. INIT must say so, and a tap must still fire one piece.

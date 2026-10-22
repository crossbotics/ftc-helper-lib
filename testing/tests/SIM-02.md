---
id: SIM-02
title: Your own robot in the simulator, from Robot.java
area: simulator
priority: high
where: sim
minutes: 40
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.16.0
docs: [guides/simulator.md#a-robot-file-from-robotjava, tutorials/start/01-robot-in-five-files.md#try-it-in-the-simulator]
---

## Why

`:sim:fromRobot` writes a simulator robot file from your own `Robot.java`. Robots are all different,
so this is where we learn which parts the simulator gets wrong or cannot draw.

## Setup

- Your robot in five files in your TeamCode, with the library in the robot project.
- SIM-01 works on this laptop.

## Steps

1. In the `ftc-helper-lib` folder: `./gradlew :sim:fromRobot -PteamCode=../TeamCode`.
2. Open `simulator/robot.properties` in your robot project. Read the `TODO` lines; fill in the robot's size if you know it.
3. `./gradlew :sim:run -PteamCode=../TeamCode`, open http://localhost:8780.
4. INIT your TeleOp, read the button map, START, drive, and use every part.
5. Run your autonomous. Read the report at the end.
6. Draw your parts with `looks(...)` shapes ([how](../../docs/tutorials/start/01-robot-in-five-files.md#draw-your-robot-from-robotjava)) if you want pieces to be picked up and shot.

## Pass rule

`:sim:fromRobot` finishes with no error, and every part in `Robot.java` (all of them, for example 6 of
6) shows in the simulator's Motors or Servos panel and responds to its button.

## Record

- your parts (the `add(...)` lines, or a list)
- any part that did not show or did not move
- errors from `:sim:fromRobot` (copy them)
- what was hard in step 2 or step 6

## If your robot is different

That is the point of this test: describe what is unusual (a part the library has no module for, a
custom `Module`, odd device names). Things the simulator cannot do are listed in the
[simulator guide](../../docs/guides/simulator.md#not-simulated): say if you hit one.

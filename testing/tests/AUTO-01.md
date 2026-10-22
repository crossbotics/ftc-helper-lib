---
id: AUTO-01
title: Choose the start spot in the INIT menu
area: autos
priority: medium
where: both
minutes: 30
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [guides/autos.md#what-the-init-menu-can-choose, guides/autos.md#example-2-one-opmode-the-drivers-choose-in-init]
---

## Why

One autonomous can start from two spots: a `Start` line in the INIT menu picks the steps AND the start
(`start(...)` inside an `onlyIf`). The "Place robot" note, the simulator's robot and the odometry's
start follow the choice while the drivers change it. If the note shows the wrong spot, the drive team
puts the robot in the wrong place.

## Safety

- Real robot: the autonomous drives by itself. Clear floor, everyone out of its way, a hand on STOP, PRACTICE profile.

## Setup

- An autonomous with a menu line and a `start(...)` for each option, like [example 2](../../docs/guides/autos.md#example-2-one-opmode-the-drivers-choose-in-init): `s.menuChoice("Start", "Near", "Far")`, then `s.onlyIf("Start", "Near").start("NEAR: ...", ...)...end()` and the same for "Far".
- Simulator: your robot from `Robot.java` (`./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then `./gradlew :sim:run -PteamCode=../TeamCode`).

## Steps

1. INIT the autonomous. Read `Place robot : NEAR: ...` (the first option is chosen at INIT).
2. With the D-pad, go down to the `Start` line and change it to Far. The note changes to `FAR: ...` at once. Simulator: the robot on the field map moves to the far spot.
3. Change it back to Near, then to Far again. The note follows every change.
4. Put the robot on the far spot. START. Read the report at the end: the far steps ran, the near ones did not.
5. With odometry: INIT a TeleOp right after and read the `Pose` line: it starts from where the far route ended.
6. Spell an option differently in `onlyIf` (say "far"). INIT, choose Far: nothing in that block runs. Say whether the screen helped you find it. Put it back.

## Pass rule

The "Place robot" note matches the chosen option every time it changes (at least 3 changes, 3 of 3),
and the run in step 4 starts from the far spot and runs only the far steps.

## Record

- your two `start(...)` lines and notes
- simulator or real robot; with or without odometry
- the report lines from step 4
- what step 6 showed

## If your robot is different

Any drive works. Without odometry the start spot is only the note and the simulator's place: check the
note. A `parkWithSeconds(...)` inside an `onlyIf` follows the menu the same way: try it if you use one.

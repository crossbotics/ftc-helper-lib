---
id: SHOT-09
title: A feed wheel stages each piece before the push arm
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel]
kit_bots: [gobilda-strafer]
since: v0.29.0
docs: [modules/robot.md#firing-shot-modes-feed-stop-piece-path, guides/tunables.md#indexer]
---

## Why

Some launchers have two feed parts: a CR servo wheel moves each piece up to a servo arm, and the arm pushes it
into the flywheel. The wheel must run before the arm pushes, or the arm pushes nothing.

## Setup

- A launcher with `.feeder(Indexer.servo("feeder_arm"))` and `.feedWheel(Indexer.crServo("feeder_wheel"))`,
  or its simulator robot file (`./gradlew :sim:fromRobot`).
- 2 or more pieces loaded.
- `Tunables.FeederWheel`: `alwaysOn = false`, `stageSec = 0.2`, `stopSec = 0.3`.

## Safety

- The flywheel spins fast: hands and loose clothes away from it. Wear eye protection near a real launcher.

## Steps

1. Press ▶ (start on the Driver Hub). Spin up. The feed wheel must not turn yet.
2. Fire one piece. The feed wheel must start first. The arm must push about 0.2 s later, while the wheel still
   turns. The wheel must stop about 0.3 s after the push starts. The piece must leave.
3. Set `stopSec = -0.1` in the tuning menu. Fire again. The wheel must stop just before the arm pushes. The piece still goes: the wheel ran 0.1 s.
4. Set `alwaysOn = true` in `Tunables.java` and INIT again. After ▶ with the flywheel on, the wheel must
   turn all the time. A fire must push at once.
5. Press LB (the flywheel coasts down). The wheel must stop.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The `stageSec` and `stopSec` that worked best on your robot.

## If your robot is different

A launcher with a feed wheel but no arm does not start: `feedWheel(...)` needs `feeder(...)` too.

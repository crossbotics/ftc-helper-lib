---
id: SHOT-13
title: Two CR servo feed wheels spin together, opposite ways
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel, intake]
kit_bots: []
since: v0.42.0
docs: [reference/parts/indexer.md, reference/parts/indexer.md#in-tunablesjava, cookbook/04-mechanisms.md#how-do-i-feed-with-two-wheels-one-on-each-side]
---

## Why

Two wheels, one on each side of the piece, squeeze it into the launcher. If one wheel stops or turns the
wrong way, the piece twists or jams.

## Setup

- A flywheel launcher with `.feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right"))`, or its
  simulator robot file. Pieces to fire. No kit bot has two feed wheels, so use your own robot. For the
  simulator, make its robot file from your Robot.java ([your robot file](../../docs/simulator/02-your-robot-file.md)).
- A motor feeder (`Indexer.motor(...)`) works the same way for step 5: test it too if you have one.
- `Tunables.FeederLeft`: `power` and `feedSec` (start with the defaults).
- For step 5: `.feedWheelOutWith(intake)` on the launcher, and an intake that can push out.

## Safety

- Eye protection. Nobody in front of the launcher. Keep fingers out of the wheels.

## Steps

1. INIT. Neither wheel must move.
2. Press ▶ (start on the Driver Hub). Fire with no piece. Both wheels must start at the same moment and pull
   in (toward the flywheel), then stop together after `feedSec`.
3. Fire one piece. It must go in straight, with no twist.
4. Fire all the pieces, one at a time. Each one must go in, and the next must wait.
5. Only with `.feedWheelOutWith(intake)`: make the intake push out. Both wheels must turn backward (away from
   the flywheel). Make the intake stop or pull in. Both wheels must stop.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The servos, `power` and `feedSec`, and whether you needed `.reversed()` or `.alsoServo(...)`.

## If your robot is different

Both wheels pull out: add `.reversed()`. One pulls out: one servo is upside down, so use `.alsoServo(...)` in
place of `.alsoServoReversed(...)`.

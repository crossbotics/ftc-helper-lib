---
id: AUTO-02
title: Word steps and the one angle rule (90 = left)
area: autos
priority: high
where: both
minutes: 20
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: unreleased
docs: [five-files/05-autos.md#more-steps, conventions.md#driving]
---

## Why

Every angle in an autonomous counts counterclockwise: 90 = left, for a move's direction, a turn and a
heading. The word steps (`forward`, `back`, `left`, `right`, `turnLeft`, `turnRight`) need no angle at all.
If a move went the mirror way, the robot could drive into a wall or a partner.

## Safety

- Real robot: the autonomous drives by itself. Clear floor, everyone out of its way, a hand on STOP, PRACTICE profile.

## Setup

- An autonomous with these steps (a tank drive: leave out the two lines marked mecanum):

```java
s.start("Middle of a tile, facing the field", 0)
 .forward("Forward", 12, 0.4)
 .turnLeft("Turn left", 90, 0.4)
 .turnRight("Turn right", 90, 0.4)
 .left("Slide left", 12, 0.4)          // mecanum
 .move("Angle 90", 12, -90, 0.4)       // mecanum: -90 = right, back to the middle
 .back("Back", 12, 0.4);
```

- Simulator: your robot from `Robot.java` (`./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then `./gradlew :sim:run -PteamCode=../TeamCode`).

## Steps

1. Put the robot in the middle of a tile, with room on every side. INIT, ▶.
2. Watch each step: forward, a quarter turn to the LEFT, a quarter turn back, a slide to the LEFT, a slide back to the right, then back.
3. Read the report at the end: every step says `done`.
4. Simulator: open the auto builder, and load this auto with **Load an existing auto**. The steps show as forward, turn left, turn right, left, right, back. Press **Copy the Java**: the word steps come back as words.

## Pass rule

Every step goes the way its word (or its angle, counterclockwise) says, 3 runs of 3, and the robot ends
within 3 inches of where it started (1 tile = 23.5 in).

## Record

- simulator or real robot; mecanum or tank
- any step that went the other way, and which
- the Java the auto builder gave back in step 4

## If your robot is different

A tank drive cannot slide: leave out `left` and the `move` at -90, and the test is forward, the two turns
and back. An auto from before this version: a `move` with 90 now goes LEFT; the CHANGELOG says how to update it.

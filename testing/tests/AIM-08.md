---
id: AIM-08
title: Quick turn on fire
area: aim
priority: medium
where: real
minutes: 15
needs: [limelight|webcam]
kit_bots: []
since: v0.37.0
docs: [modules/robot.md#aim, tutorials/start/09-shooter-and-camera.md#10-no-turret-aim-the-whole-robot]
---

## Why

A robot with no turret must point at the goal to score. With `Tunables.Aim.aimOnFire = true`, a fire
press turns the robot to the goal first, then fires. We want to know it turns quickly, fires once
aimed, and gives the turn stick back after the shot.

## Safety

- Clear floor, PRACTICE profile. The robot turns by itself on a fire press: keep hands away.

## Setup

- A robot with no turret, a camera with `.goal(...)`, and a launcher on RB.
- In `Tunables.java`: `public static class Aim { public static boolean aimOnFire = true; }`.
- Whole-robot aim tuned first (`turnKp`, `maxTurnPower`, `toleranceDeg`).

## Steps

1. START. Aim mode off. Face about 15 degrees to one side of the goal, with the goal in view.
2. Tap RB once. Watch the robot turn and fire.
3. After the shot, move the turn stick. Check that the robot turns as the stick says.
4. Face away from the goal (not in view). Tap RB.
5. Face 15 degrees off again. Tap RB and move the turn stick at once.

## Pass rule

In step 2 the robot turns to the goal and fires within 0.5 s, and the shot is aimed. In step 3 the
stick turns the robot. In step 4 it fires at once with no turn. In step 5 the stick wins.

## Record

- how long from the press to the shot (seconds, from a video if you can)
- how many degrees off the robot started, and if the shot scored
- `turnKp`, `maxTurnPower`, `toleranceDeg` used

## If your robot is different

If the fire button is not RB, say which. A tank drive turns the same way.

---
id: SIM-08
title: Change the shot angle in Edit robot and move the robot with numbers
area: simulator
priority: medium
where: sim
minutes: 15
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [guides/simulator.md#edit-robot-shape-your-robot, guides/simulator.md#the-page]
---

## Why

A team sets the launch angle in `Robot.java`. Edit robot must show that angle, move the arrow when it changes,
and give the line to copy. The Control Hub's move controls must put the robot where the numbers say.

## Setup

- SIM-01 works on this laptop.
- A robot with a launcher, from your `Robot.java` or a kit bot.

## Steps

1. Press **Edit robot**. Under **Shot angle**, check the number is the one in your `.shotAngle(...)`.
2. Change it by 20 degrees. The arrow must move. **Copy into Robot.java** must show the new `.shotAngle(...)`.
3. Press **Try it**, then **Back to driving**. Check that the **Shot arc** layer shows the new angle.
4. Press **Edit robot**, then **Undo all**. The number goes back.
5. In the Control Hub, type x 0, y -40, heading 90. The robot must go there, and the `s.start` line must say the same.
6. Tick **Snap**. Press a **Face** button. The robot must turn and line up with the wall or the tiles.
7. Press the **?** next to Gamepads. The help must open.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- Your robot: its launcher, and the angle in `Robot.java`.
- Any step where the number, the arrow or the robot did not match.

## If your robot is different

A launcher with a hood or a shot table gets its angle from that part: the panel says so, with no box to type in.
No launcher: skip steps 1 to 4.

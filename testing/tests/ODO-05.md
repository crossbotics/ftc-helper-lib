---
id: ODO-05
title: A route goes around keep-out boxes, in autonomous and in TeleOp
area: odometry
priority: high
where: both
minutes: 30
needs: [pinpoint|otos|dead-wheels]
kit_bots: []
since: unreleased
docs: [modules/odometry.md#keep-out-boxes-and-routes]
---

## Why

A route has run only in the simulator. On a real robot the pose drifts and the robot slides. We need to know
that the margin is big enough, that the robot really misses the box, and that a drive stick stops it at once.

## Safety

- The robot drives by itself. Clear floor, nobody near the robot, PRACTICE profile.
- Use power 0.3 for the first runs, with your thumb on a drive stick.
- If the odometry is wrong, the robot can go the wrong way. Be ready to press STOP.

## Setup

- A mecanum robot with `Tunables.Odometry.checked = true` and `Tunables.Drive.calibrated = true`.
- A box of your own on the floor, or a real field element. Measure it. In Robot.java:
  `fieldMap(FieldMap.margin(13).keepOut("Box", x1, y1, x2, y2))`, with a margin for your robot.
- An autonomous with a long wait, then `.parkWithSeconds(25).routeTo("Home", x, y, 0, 0.3)`, on the far side of the box.
- In Controls.java: `g1.dpadLeft.tap(route("Home", x, y, 0, 0.3))` and `atSecondsLeft(100, route("Timed", x, y, 0, 0.3))`.
- Simulator: `SimRouteTest` has robots with the two HIVE frames, or use your own robot's file.

## Steps

1. Run the autonomous with the robot on the far side of the box. After 5 s, the wait stops. The robot goes around the
   box to the spot, and the report says `Home: done`.
2. Measure how far the robot stayed from the box, and how far from the spot it ended.
3. In TeleOp, set the pose (hold START 1 s on the reset spot). Tap D-pad left. The robot goes around the box, and the
   Driver Station says `Home: there`. The other parts still work while it drives.
4. Tap D-pad left again and move a drive stick. The robot stops at once and the stick drives it. The Driver Station
   says `Home: stopped: a drive stick moved`.
5. Tap D-pad left and tap BACK. Everything stops. Tap BACK again. The robot does not set off.
6. Start with the robot just inside the margin. Tap D-pad left. It first goes out by the nearest side, away from the box.
7. Stand in the way with a piece of foam, or hold the robot back with your hand. It stops, and says `STALLED`.
8. Let the time reach the `atSecondsLeft` mark with the sticks let go. The route starts by itself.
9. Run it again and hold a drive stick at the mark. The gamepad buzzes and the Driver Station says `Timed: waiting`.
   Let go within 5 s: the route starts. Run it again and hold the stick for over 5 s: `Timed: not started: the driver kept driving`.
10. Add a `route` to a spot inside the margin, right next to the box. The robot goes to just outside the margin, then straight in.

## Pass rule

Steps 1, 3, 4, 5, 7 and 9 work as written, and the robot never touches the box (3 of 3 runs at power 0.3, then 3 of 3 at 0.7).

## Record

- the odometry kind, the robot's size and the margin
- how far from the box it passed, and how far from the spot it ended, at each power
- how long the route took, with how many corners (for the park time)
- anything that surprised you

## If your robot is different

A tank drive cannot use routes: INIT stops and says so. Without a field, tape a box on the floor and use its numbers.

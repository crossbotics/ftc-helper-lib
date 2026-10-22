---
id: SOM-03
title: Shooting on the move in the simulator - strafe and compare
area: moving-shot
priority: medium
where: sim
minutes: 40
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#14-extra-shooting-while-moving, guides/simulator.md#clean-shots-scatter-and-the-hit-rate]
---

## Why

Before anyone tunes shooting on the move on a real robot (SOM-01, SOM-02), the simulator can show
whether the lead goes the right way and how much it helps. Its shots fly with real air and its verdicts
(clean, rim, miss) come from the same judge as the turret shot simulator.

## Setup

- SIM-01 works on this laptop.
- The V2 example robot (a turret that follows the goal, odometry, a shot table, shooting on the move off). In the `ftc-helper-lib` folder: `./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.v2bot.Robot -Pout=build/v2bot.properties`, then `./gradlew :sim:run -PteamCode=none -Probot=build/v2bot.properties`. Open http://localhost:8780.
- Or your own turret robot with odometry and a shot table (`:sim:fromRobot -PteamCode=../TeamCode`).

## Steps

1. Choose "V2 Bot TeleOp", pick the alliance (gamepad 1 X = BLUE, B = RED), START. Open the Match panel (Control Hub, "Match"): it shows each shot's verdict.
2. Drive to a spot about 4 ft from your goal where the turret follows it (`following: camera`; lesson 9's [Try it in the simulator](../../docs/tutorials/start/09-shooter-and-camera.md#try-it-in-the-simulator) says where the tags can be seen). Write the spot (the line under the field map).
3. Shooting on the move is off. Standing still, fire 4 (hold gamepad 2 RB). Count clean shots.
4. Load 4 pieces again (drive the intake over pieces on the field, or restart the simulator) and go back to the spot. Strafe slowly past the goal (keyboard A or D, or the left stick) and fire 4 while moving. Count clean shots.
5. Stop the simulator. In `examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot/Tunables.java`, class `Aim`, change `shootOnMove = false` to `true` and add `public static double flightSec = 0.8;` (your own robot: the same two lines in its `Tunables.Aim`). Start the simulator again (the Setup commands).
6. Repeat step 3 (standing still: no `Aim: moving shot` line) and step 4 at the same speed. While moving, read `Aim: moving shot: lead ... deg, ... in`. Count clean shots.
7. Misses on the side the robot drives toward: raise `flightSec`; on the other side: lower it (it changes at once in the tuning menu, GUIDE held 1 s). Repeat the strafe.
8. Put the example's `Tunables.java` back as it was.

## Pass rule

Standing still, the clean shots with it on are within 1 of what they were before (out of 4), and while
strafing it gives at least 2 more clean shots out of 4 than with it off, or 3 of 4 with it on.

## Record

- the robot (V2 example or your own) and the alliance
- clean shots out of 4: still, strafing off, strafing on (and after changing `flightSec`)
- the lead the Driver Station showed while strafing (degrees, inches)
- the `flightSec` that worked best in the simulator

## If your robot is different

A robot with no turret aims the whole robot instead (`g1.rb.toggle(robot.aimMode())`): the same steps
work, say so. The simulator's `flightSec` is a starting point only: measure the real one on the robot
(SOM-01 step 2).

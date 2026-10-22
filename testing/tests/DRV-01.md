---
id: DRV-01
title: First drive with the five files - wheels, sticks, slow and pause
area: drive
priority: high
where: real
minutes: 30
needs: [mecanum|tank]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.16.0
docs: [tutorials/start/02-drive.md#7-check-the-motor-directions, quick-start.md#5-drive]
---

## Why

Driving is the first thing a team sees work. We want to know that the drive line in `Robot.java`
works on many drivetrains, and that the motor-direction steps in lesson 2 find a wrong wheel.

## Safety

- First run with the robot on a block, wheels off the ground.
- Keep `maxPower` and `practiceMaxPower` at 0.4 in `Tunables.java` for this test.
- A clear floor and a hand near the Driver Station STOP button.

## Setup

- The robot in five files with your drive line (from the Quick start or a template).
- Your TeleOp built and installed.

## Steps

1. Robot on a block. INIT your TeleOp, then START.
2. Push the left stick up. Every wheel must roll the way that moves the robot forward. Fix any wheel with lesson 2 section 7, and count how many fixes you needed.
3. Put the robot on the floor. Left stick up for about 2 seconds: it drives straight.
4. Right stick right: it turns clockwise. Mecanum only: left stick right strafes right.
5. Pull LT all the way while driving: the robot goes slower. (One driver, `oneDriver = true`: click the left stick for slow on and off.)
6. Press BACK: the robot stops at once and the Driver Station says PAUSED. Tap BACK again: it drives again.
7. Tap GUIDE: the button map shows. Tap GUIDE again to close it.

## Pass rule

The robot drives straight within about 6 in of drift over 5 ft, turns the right way, LT slows it,
and BACK stops it within 0.5 s of the press.

## Record

- drive type and motors (for example "mecanum, 4 x goBILDA 312")
- how many wheels needed a direction fix, and which steps you used
- drift over 5 ft (inches, left or right)
- how fast BACK stopped the robot (about)

## If your robot is different

Any drivetrain the library can drive is in scope: 2-motor or 4-motor tank, mecanum, a kit bot's
drive. If your robot has a drivetrain the library does not offer (swerve, H-drive), report that as
"could not run" and say what it is. If your driver uses other buttons (`libraryButtons` in
`Controls.java`), use those and say which.

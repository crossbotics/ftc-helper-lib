---
id: SHOT-10
title: Fire with the flywheel off, no firing queue, and the feed wheel backing out
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel]
kit_bots: [gobilda-strafer]
since: v0.34.0
docs: [modules/robot.md#firing-shot-modes-feed-stop-piece-path, guides/tunables.md#launcher]
---

## Why

Drivers switch the flywheel off to save the battery, and must still be able to fire. With no queue, one
press must give one shot. A piece stuck in the feeder must come out when the intake pushes out.

## Setup

- A robot in five files with a flywheel launcher (and, for step 5, a feed wheel with
  `.feedWheelOutWith(intake)`), or its simulator robot file. Pieces to fire.

## Safety

- Eye protection. Nobody in front of the launcher.

## Steps

1. INIT the TeleOp, press ▶. Switch the flywheel off with its on/off button: it slows to a stop.
2. Press the fire button once: the wheel spins up, one piece is fired, and the wheel slows to a stop again.
3. Switch the flywheel on, fire once: after the shot it keeps spinning.
4. Set `Tunables.Launcher.maxFiringQueue = 0`, INIT again. Press fire 3 times fast: one piece only. Press
   again after the shot: one more.
5. Put a piece between the feed wheel and the arm. Make the intake push out (its out gesture): the feed
   wheel turns backward and the piece comes out. Intake back in: the feed wheel stops.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- Any step that did something else.

## If your robot is different

No feed wheel: skip step 5. A launcher with `spinAtStart()` starts on: switch it off first (step 1).

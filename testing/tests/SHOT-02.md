---
id: SHOT-02
title: Never feeds a slow flywheel, and gives up after 3 s
area: shooter
priority: high
where: both
minutes: 20
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: v0.16.0
docs: [tutorials/start/09-shooter-and-camera.md#4-the-two-safety-rules, tutorials/start/09-shooter-and-camera.md#3-the-buttons-it-brings]
---

## Why

The launcher's two safety rules are the reason teams can trust the fire button: a piece is only fed
when the wheel is at speed, and the launcher gives up after 3 s instead of firing late. Different
feeders (a CR servo, a servo, a roller) must all follow them.

## Safety

- Safety glasses. Nobody in front of the launcher.
- Real robot: a few pieces only, a clear space where shots land.

## Setup

- A launcher with a feeder in `Robot.java` and its gains (SHOT-01).
- Simulator: lesson 9's goBILDA bot (`-Probot=sim/robots/starter-bot-gobilda.properties`, "goBILDA Bot TeleOp").

## Steps

1. INIT your TeleOp, START. Load 3 pieces.
2. Without spinning up, tap RB (fire one). The wheel spins up and the piece waits; it is fed only when the speed is READY.
3. Hold RB: pieces fire one after another, each when the wheel is back at speed. Let go: no more are fed.
4. Make it unable to reach the speed. Real robot: STOP, power the robot off, unplug the flywheel motor's encoder cable, power on (the speed now reads 0, and the wheel may spin at full power: unload every piece first and watch the feeder instead). Simulator: lower the battery on the page (Robot tab) until the wheel cannot reach READY (if it still reaches READY, say so and skip step 5). INIT, START, tap RB.
5. After about 3 s the Driver Station says `Launcher gave up: flywheel` and the feeder does not move. Real robot: power off and plug the encoder cable back in.

## Pass rule

No piece is fed before READY (0 of 3), and in step 5 it gives up within 3.5 s and feeds nothing.

## Record

- feeder type (CR servo, servo, roller motor) and `feedSec` or `pushSec`
- pieces fed per RB tap (should be 1)
- time between shots while RB is held (seconds)
- what the Driver Station said in step 5

## If your robot is different

If your feeder pushes two pieces at once, lower `feedSec` (or `pushSec`) and say what worked. A
launcher with extra "ready" checks (`requireReady(...)`) should name them when it gives up: report the
words you saw.

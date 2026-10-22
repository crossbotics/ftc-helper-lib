---
id: SIM-01
title: Drive a kit bot in the simulator
area: simulator
priority: high
where: sim
minutes: 30
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.16.0
docs: [guides/simulator.md#start-it, guides/starter-bots.md]
---

## Why

The simulator is how most teams will try the library first. It must start on Windows, Mac, Linux and
Chromebooks, with the laptop setup as written. This is the best first test for any team.

## Setup

- A laptop with Android Studio (it brings Java). The library folder (in your robot project, or cloned on its own).
- The [laptop setup](../../docs/guides/simulator.md#laptop-setup-once) done once.

## Steps

1. In a terminal in the `ftc-helper-lib` folder, run `./gradlew :sim:run -Probot=sim/robots/starter-bot-gobilda.properties` (or the REV, AndyMark or Studica file). Windows: `gradlew.bat`.
2. Open http://localhost:8780.
3. Choose the kit bot's TeleOp (for example "goBILDA Bot TeleOp"), INIT, START.
4. Set gamepad 1 to "Keyboard". Drive with W A S D and the arrows for a minute.
5. Try a real controller if you have one (choose it in the list, press a button once).
6. Run the intake and (goBILDA) spin up with LB and fire with RB.

## Pass rule

The page opens within 5 minutes of the first command (after the first download), the robot drives
with the keyboard, and nothing shows an error in red.

## Record

- computer and operating system, browser
- which kit bot
- how long the first `./gradlew` took (minutes)
- any step in the laptop setup that did not work as written, and what fixed it
- controller tried (and whether it worked)

## If your robot is different

This test uses a kit bot file, so your robot does not matter. If you build one of the kit bots, say
whether the simulator's robot behaves like yours (speed, size, which button does what).

---
id: PATH-01
title: The Pedro example path in the simulator
area: paths
priority: medium
where: sim
minutes: 20
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.16.0
docs: [building-autos/05-pedro-paths.md#4-check-it-then-tune-it]
---

## Why

[Add Pedro paths](../../docs/building-autos/05-pedro-paths.md) says "try every path in the simulator first". This checks that the
commands and what the docs say you will see are right on your laptop. No robot needed.

## Setup

- A laptop with the library and the simulator set up ([laptop setup](../../docs/guides/simulator.md#laptop-setup-once)).
- A terminal in the `ftc-helper-lib` folder.

## Steps

1. Run `./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.pedrobot.Robot -Pout=build/pedrobot.properties`.
2. Run `./gradlew :sim:run -PteamCode=none -Probot=build/pedrobot.properties` and open http://localhost:8780.
3. Choose "Pedro Bot Curve to the Goal", choose RED in the INIT menu, INIT, START.
4. Watch: a short line, then a curve to the goal without stopping, turning to 45 degrees. The intake starts about halfway.
5. Look at the field map (Control Hub, Robot tab): the orange dashed plan and the green trail.
6. Read the report at the end.

## Pass rule

The report says `done` for every step (all of them, for example 5 of 5), the green trail stays
within about 2 in of the orange plan, and the intake starts within about 6 in of halfway.

## Record

- your computer (Windows, Mac, Linux, Chromebook) and browser
- the report lines
- how long the two Gradle commands took the first time (minutes)
- anything on screen that did not match the docs

## If your robot is different

This test uses the library's example robot, so your robot does not matter. If you have Pedro in your
own project, also try your own autonomous ([Add Pedro paths](../../docs/building-autos/05-pedro-paths.md)) and say so.

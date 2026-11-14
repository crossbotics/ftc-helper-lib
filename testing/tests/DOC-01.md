---
id: DOC-01
title: Set up and a first drive from a blank robot project
area: docs
priority: high
where: both
minutes: 60
needs: [mecanum|tank]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.16.0
docs: [setup/README.md, pick-your-start/README.md]
---

## Why

A student who has never used the library should be able to follow Set up, Pick your start and The five
files alone, to a robot that drives. Every place you get stuck is a docs fix for us.

## Safety

- Real robot: on a block for the first drive (the pages say when), low power.

## Setup

- A robot project in Android Studio that builds (the FTC SDK's FtcRobotController), with no ftc-helper-lib yet.
- Best: a student who has not used the library before does it, and a second person writes notes without helping.
- A timer.

## Steps

1. Start the timer. Follow [Set up](../../docs/setup/README.md) from the top, then each page's **Next:** link, exactly as written.
2. Each time you are stuck for more than 2 minutes, write the step and the words that confused you.
3. Real robot: build, install, run the Hardware Check, drive. Simulator: [Start it and drive](../../docs/simulator/01-start-and-drive.md).
4. Stop the timer when the robot drives (or the simulator robot drives).

## Pass rule

The robot drives within 60 minutes, with no help from someone who knows the library (0 times asked).

## Record

- time to a driving robot (minutes)
- each place you got stuck (step and words), and what got you going again
- Windows, Mac, Linux or Chromebook; real robot or simulator
- which way you got the five files (from the configuration, a template, a kit bot)

## If your robot is different

Any robot that drives is fine; a kit bot can use [Pick your template](../../docs/templates.md). The
simulator alone also counts (no robot): say so. A student with no Java at all is the most useful tester.

---
id: SET-03
title: DEFAULT, MEASURE and a part not in the catalog
area: setup
priority: medium
where: sim
minutes: 20
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.1
docs: [guides/tunables.md#default-and-measure, modules/robot.md#parts-not-in-the-catalog]
---

## Why

A new robot's `Tunables.java` can name every setting from day one: `DEFAULT` for "use the
library's default" and `MEASURE` for "a number to find on the robot". A motor or servo the catalog does
not have can be described in Robot.java (`Motors.custom`, `Parts.customServo`). We want to know the INIT
messages make clear what was used, where it came from and what to do next.

## Safety

- Simulator only: nothing moves on a real robot.

## Setup

- Your robot's five files, or `./gradlew :sim:newRobot` from your configuration (it writes the words).
- `import static com.crossbotics.ftchelper.robot.Starting.*;` at the top of `Tunables.java`.

## Steps

1. Write `DEFAULT` for a few numbers (a launcher's `kF`, a lift's `kP`, a claw's `travelSec`).
   `./gradlew :sim:fromRobot`, start the simulator, INIT. Write down each "defaults from ..." note.
2. Write `MEASURE` for one number (a lift's `max`, or a claw position). INIT: write down the message.
3. Write `DEFAULT` for a lift's `max`. INIT: write down the message. Put your number back.
4. In Robot.java, give one part a motor of your own: `Motors.custom("my-motor", 28, 5000)` (or a servo:
   `Parts.customServo("my-servo", 270, 0.16)`). `:sim:fromRobot` again: find its numbers in the robot file.
   INIT: write down its note.
5. Change it to `Motors.unknown("my-motor")` on a lift's `.part(...)`. INIT: write down the note.

## Pass rule

Step 1 lists each value and where it came from; steps 2 and 3 stop INIT with a message that names the
number and what to do (the tool for MEASURE, "a safety number" for step 3); step 4 shows the custom numbers
in the robot file and "(custom)" in the note; step 5 says "general defaults" and how to do better.

## Record

- each message, as shown
- anything in a message that was unclear, and what you did next

## If your robot is different

No lift or launcher: use the parts you have (any Claw, Wrist, Hood, servo Turret or Indexer reads a
`travelSec`-like time).

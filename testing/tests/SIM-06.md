---
id: SIM-06
title: The simulator runs code with MEASURE numbers (stand-ins in the robot file)
area: simulator
priority: medium
where: sim
minutes: 15
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.18.3
docs: [guides/simulator.md#measure-in-the-simulator, guides/tunables.md#default-and-measure]
---

## Why

A number written as `MEASURE` in Tunables.java stops INIT on the robot until it is measured. In the
simulator, `:sim:fromRobot` writes a stand-in for it in the robot file, so the code runs before the robot
is built. We need to know that the simulator uses the stand-in and says so, that the real number wins
once it is written, and that the robot file stays right after each `:sim:fromRobot`.

## Setup

- Your robot project with the simulator running your code (`./gradlew :sim:run`), written in five files.

## Steps

1. Pick one number in your Tunables.java (for example a servo position). Write down its value, then change it to `MEASURE`.
2. Run `./gradlew :sim:fromRobot`. Open `simulator/robot.properties`: find the `tunable.Section.field = ...` line for it.
3. Start the simulator, choose your TeleOp, INIT. Read the note under the Driver Hub ("... = MEASURE: the simulator uses the stand-in ..."). START: the part goes to the stand-in.
4. Run one of your tests (`./gradlew :sim:scenario`): the `init` line shows the same note, and the test runs past INIT.
5. Put your number back in Tunables.java and run `./gradlew :sim:fromRobot` again: the `tunable.` line is gone.

## Pass rule

3 runs out of 3: INIT goes on with the note, the part goes to the stand-in, and the line is gone after
the number is written.

## Record

- the section and field, the stand-in value written, and whether it was a sensible value for your part
- the note text you saw, and whether it was clear
- anything the stand-in made the robot do that looked wrong

## If your robot is different

Any robot with a Tunables.java works. No `MEASURE` lines? Step 1 makes one.

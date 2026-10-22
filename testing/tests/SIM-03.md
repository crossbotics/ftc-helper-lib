---
id: SIM-03
title: Build an autonomous in the simulator with the auto builder
area: simulator
priority: high
where: sim
minutes: 45
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [guides/simulator.md#the-auto-builder, guides/autos.md#test-each-one-in-the-simulator]
---

## Why

The auto builder draws an autonomous on the field, runs it with your real robot code, shows where the
robot left the plan, and writes the Java and a scenario file. It must read any team's robot and give
Java that builds the first time.

## Setup

- SIM-01 works on this laptop.
- Your robot from `Robot.java` (`./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then `./gradlew :sim:run -PteamCode=../TeamCode`), or a kit bot's robot file.
- A robot project where you can add one autonomous file.

## Steps

1. Open http://localhost:8780 and press **Auto builder** over the 3D view. Check the Robot class it picked ("Best fit"), the drive, and **TeleOp next** (your robot's TeleOp).
2. Pick RED or BLUE. Pick a start spot from the list. Press **Write it for me** for the note.
3. Simple steps: add a drive, a turn, an action from your `Actions.java` and "park from here". Fix every warning the checks show.
4. Press **Run**. Watch the purple plan robot and your robot. Read the result table: each step's word (done, STALLED...) and how far off the plan it ended.
5. If it offers a fix with **Apply** (a 0.3 s wait before a turn), apply it and Run again.
6. **Copy the Java**. Paste it into a new file in your TeamCode. Build it.
7. **Save the scenario** and run it: `./gradlew :sim:scenario -Pfile=...` (each line prints ok or FAIL).

## Pass rule

The copied Java builds the first time (0 errors), every step ends `done` in the run, and the saved
scenario passes (every line ok).

## Record

- the robot (your own or a kit bot) and its drive
- whether "Best fit" and "TeleOp next" were right
- the result table after the last run (copy it)
- any build error in step 6, and any FAIL in step 7
- what was hard to find on the page

## If your robot is different

A mecanum robot with odometry can also try the **Pedro path** mode (needs a `follower(...)` line for
"follow"). A tank robot cannot strafe: the checks should say so. The saved scenario's
`expect step N done` lines need the v0.17.0 simulator or newer.

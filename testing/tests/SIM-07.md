---
id: SIM-07
title: Start and stop the simulator with the buttons in Android Studio
area: simulator
priority: high
where: sim
minutes: 20
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.18.3
docs: [01-add-to-project.md#step-7-the-simulator-buttons-once, guides/simulator.md#start-it]
---

## Why

The **Simulator** and **Simulator tests** buttons start the simulator with no Terminal and no laptop
setup. The Stop button must stop the simulator completely, or the next Run cannot use the page's port.
We tested them on a Mac. We need Windows, Linux and Chromebook results most.

## Setup

- Your robot project in Android Studio, with the library in `ftc-helper-lib` and your code in five files.
- The buttons added once: `./gradlew :sim:installButtons` in the `ftc-helper-lib` folder, then close and open the project.
- At least one test in `simulator/scenarios` (record one: start lesson 10), for step 6.

## Steps

1. In the list next to the green Run button, pick **Simulator**. Click Run.
2. Wait for `Open http://localhost:8780` in the Run window. Write down how long it took. Check that the line `Robot file made from Robot.java: simulator/robot.properties` is above it.
3. Open http://localhost:8780. Choose your TeleOp, INIT, START, drive for 10 seconds.
4. Click the red square Stop button. Reload the page: it must not open now.
5. Click Run again: the simulator starts again (no "address already in use"). Stop it.
6. Pick **Simulator tests** in the list and click Run: every test runs, and the Run window ends with `== N of N passed` (or says which failed).
7. Windows only: after step 4, open the Task Manager, **Details** tab. Count the `java.exe` lines before step 1 and after step 4.

## Pass rule

3 runs out of 3: the page opens, Stop closes it within 5 seconds, and the next Run starts again.
On Windows: no more `java.exe` lines after Stop than before Run, except one or two Gradle helpers that close themselves within 3 hours.

## Record

- computer and operating system, Android Studio version
- the time from Run to `Open http://localhost:8780` (first run and second run)
- whether the buttons showed in the list at once or only after closing and opening the project
- anything left running after Stop, and any red lines in the Run window

## If your robot is different

Any robot project with the library works. No five files (no Robot.java)? The simulator starts with
your `simulator/robot.properties` as it is (step 2 shows no "Robot file made" line).

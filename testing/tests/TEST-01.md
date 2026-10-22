---
id: TEST-01
title: Test kit - JUnit tests of your Robot.java on the laptop
area: laptop-tests
priority: medium
where: sim
minutes: 40
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [modules/testkit.md, guides/testing.md#test-your-robotjava-with-pretend-devices, tutorials/13-testing.md#7-test-your-robot-in-five-files]
---

## Why

The test kit gives your robot in five files pretend motors and servos, so a JUnit test on the laptop
can do INIT, START and a loop, and check what each motor was sent. A typo in `Tunables.java` then fails
on the laptop in a second, not at INIT on the field. It must work in any team's project with no extra
Gradle lines.

## Setup

- Your robot in five files in TeamCode, with the library in the robot project.
- TeamCode set up for tests once ([tutorial 13, step 3](../../docs/tutorials/13-testing.md#3-set-up-teamcode-for-tests-once): the JUnit line and `returnDefaultValues`).
- No robot needed: this test runs on the laptop.

## Steps

1. Make `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/RobotTest.java`: copy the whole file from the [testing guide](../../docs/guides/testing.md#test-your-robotjava-with-pretend-devices). Change the lift test to one of your parts and its configuration name.
2. Run `./gradlew :TeamCode:testDebugUnitTest` from the robot project folder (Windows: `gradlew.bat`). All tests pass.
3. Misspell a field in `Tunables.java` (say `inPowr` for `inPower`). Run again: `tunablesNamesAreRight` fails and names the field. Fix it.
4. Change a motor's name in `Robot.java` to one the test does not use. Run again: read the message. Put it back.
5. Write one test of your own: a command to one part, one loop (`robot.all().update()`), then check the power or position the pretend device was sent.
6. Time a full run of the tests.

## Pass rule

The tests run with no extra Gradle lines beyond tutorial 13's (0 added), step 3 fails with the field's
name in the message, and the whole run takes under 60 s after the first build.

## Record

- your parts (kinds) and which one your own test checks
- any Gradle line you had to add, and the error that made you add it
- the failure message from step 3 and step 4 (copy them)
- how long the tests took (seconds)

## If your robot is different

Any robot in five files works. The test kit cannot show gravity, time passing (an action's
`waitSeconds`) or cameras: use the simulator for those. A robot with odometry can use `PretendPinpoint`
to put the robot where the test wants: say if you tried it.

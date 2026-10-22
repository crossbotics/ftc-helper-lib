# Lesson 10: Test your robot code (advanced)

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** a few automatic tests that drive your robot in the simulator and check that it still does
its job: the autonomous finishes and scores, TeleOp drives and picks up. You make them by **recording**:
you drive on the simulator page, and the simulator writes the test for you. Then one command runs them
all, after every change to your code.

**This lesson is advanced.** Do it when your robot drives, has an autonomous ([lesson 6](06-first-autonomous.md)),
and you already try your changes in the simulator (lessons 1 to 9 show how). Nothing in lessons 1 to 9
needs it.

**You need:** your robot project with the simulator running your code
(`./gradlew :sim:run -PteamCode=../TeamCode`, [lesson 1](01-robot-in-five-files.md)). About 45 minutes.

**Reference:** [Scenario files](../../guides/simulator.md#scenario-files-test-without-a-browser) (every
command and check), [Record a test](../../guides/simulator.md#record-a-test) (the recorder).

---

## 1. Why tests?

You change one number for the lift, and the autonomous stops scoring. You find out at the next event.
A **test** finds it in a minute, on your laptop: it does what a driver would do (INIT, START, sticks,
buttons) and then checks the result ("the autonomous finished", "3 pieces were shot").

A test does not prove the robot is good. It proves that **what worked yesterday still works today**.
That is the break you did not mean to make: a typo, a wrong button, a number changed in the wrong
section. The test catches it before the robot does.

## 2. Record a test

1. Start the simulator with your code and open http://localhost:8780.
2. Over the 3D view, open **Tests** and choose **Record a test**. A red bar says `Recording`. The
   field starts fresh: the robot on its start spot, every game piece back, your numbers as in
   `Tunables.java`. **Why fresh:** the test always starts this way, so it must be recorded this way too.
3. Choose your OpMode and press **INIT**. In the INIT menu, choose the alliance (gamepad 1 **B** for RED,
   **X** for BLUE).
4. Press **START** and do the job, as in a match: drive to a piece, pick it up, shoot. For an
   autonomous: do nothing, let it run to its end.
5. Press **Stop recording** in the red bar. A window opens: **Your test: choose the checks**.
6. Leave the ticked checks ticked (section 5 says why). Type a name, e.g. `main-teleop`, and press
   **Save**. The test is now `simulator/scenarios/main-teleop.sim` in your robot project, next to
   `TeamCode`. Commit it with your code.
7. Press **Run it now**. The simulator plays your test back from a fresh field (hands off: the test
   drives) and shows **PASSED**, or **FAILED** with the line that failed.

The recorder writes down every gamepad button and stick (from the keyboard, a real controller or the
on-screen gamepad), how long you waited, INIT, START, STOP, and the robot or a piece moved by hand. A
quick tap becomes a `press`; a button held down becomes `hold` and `release`; a stick becomes
`hold g1.left_stick_y -0.5`, in steps of 0.25 (nobody holds a stick at exactly 0.53 twice).

**Record from now** (in the same menu) starts from where the robot is, without a fresh field. It is
for a quick try; a test from INIT is more reliable.

## 3. Read the test, line by line

A test is a short text file. You can read it and change it. This is the goBILDA starter bot's
autonomous test, made by the recorder ([`sim/scenarios/recorded-gobilda-auto.sim`](../../../sim/scenarios/recorded-gobilda-auto.sim)):

```text
# recorded-gobilda-auto.sim: made by the simulator's test recorder on 2026-10-22, from "goBILDA Bot Launch and Back Up".
init "goBILDA Bot Launch and Back Up"
wait 1
press g1.b
wait 0.92
start
# The checks
expect within 6 phase STOPPED
expect within 3 launched >= 3
expect within 3 points "red" >= 13
expect step 1 done
expect step 2 done
expect error none
expect bumped none
```

| Line | What it does |
| --- | --- |
| `# ...` | a note for people; the test skips it |
| `init "goBILDA Bot Launch and Back Up"` | INIT this OpMode (the name exactly as on the Driver Station) |
| `wait 1` | wait 1 second (waits before START are cut to 1 s: they change nothing) |
| `press g1.b` | press and let go of gamepad 1's B: RED in the INIT menu |
| `start` | START |
| `expect within 6 phase STOPPED` | **a check:** within 6 s, the OpMode stops by itself (the autonomous ends) |
| `expect within 3 launched >= 3` | within 3 s, at least 3 pieces were shot |
| `expect within 3 points "red" >= 13` | RED has at least 13 points |
| `expect step 1 done` | the autonomous report's first step says `done` |
| `expect error none` | no error on the Driver Station |
| `expect bumped none` | the robot does not push against a wall or a field element at the end |

Every `expect` line is a check. The test **passes** when every check passes. The report shows each
line as `ok` or `FAIL`, with the value it saw: `FAIL 13 expect within 3 launched >= 3 -> was 2`.

## 4. How close is close enough? Tolerances

A robot never stops at exactly the same spot twice, not even in the simulator. So a check says how
close is close enough:

| Check | Means | Use it for |
| --- | --- | --- |
| `expect robot y = -36 3` | y is -36, give or take 3 inches (between -39 and -33) | where the robot ends |
| `expect robot heading = 90 5` | 90 degrees, give or take 5 | which way it faces |
| `expect launched >= 3` | 3 or more | counts: pieces, points |
| `expect time < 25` | less than 25 s since START | "fast enough" |
| `expect within 4 stored >= 1` | wait up to 4 s for it to become true | anything that takes time |

The number after `=` is the value; the next number is the **tolerance** (how far off it may be).
Without a tolerance, `=` means exactly, and that almost never works for a measured number.

The recorder picks loose numbers: 3 inches, 5 degrees, `>=` for counts, and `within` 1.5 times the
time it took. **Too strict?** The test fails for no reason, and soon nobody trusts it. **Too loose?** It
never fails, even when the robot is broken. If a test fails now and then for no reason, make the
tolerance a little bigger (in the window before you save, or in the file).

## 5. Check goals, not details

The window shows two lists:

- **Goal checks** (ticked): what the test is for. The autonomous finishes, its steps are done, the
  pieces are shot or scored, no error. For a driving test with no pieces: where the robot ends.
- **Details** (not ticked): the exact spot after an autonomous, the time, lines on the Driver Station
  screen. Tick one only if it is what you want to test.

**Why:** a detail changes every time you improve something. A test that checks "the screen says
`Launcher : 2600 rpm`" fails when you tune the speed, but nothing is broken. A test that checks
"3 pieces were shot" fails only when the shooting is broken. Fewer, better checks make a test you trust.

## 6. A few good tests

You do not need many tests. Start with these:

- **One test per autonomous.** Goal: it finishes, every step is `done`, it scores.
- **TeleOp basics:** drive forward and turn (the robot ends near the spot), and one test per job the
  drivers use most (pick up a piece: `stored >= 1`; shoot: `launched >= 1`).

That is 3 to 5 tests. Each takes 10 to 30 seconds to run. Add one when a bug surprised you, so it can
never come back unnoticed.

## 7. After a planned change: update the test

You made the robot faster on purpose. Now the TeleOp test fails: the robot ends 12 inches further
than the test says. The robot is right; the test is out of date.

1. **Tests > Open a test**, choose it, **Run it now**: FAILED.
2. Read the failed lines. Ask: **did I mean to change this?**
   - **No:** the test caught a break. Fix the code, not the test.
   - **Yes:** press **The change was on purpose: update the test**.
3. The simulator runs the test once more and measures. It shows each failed check with its old and
   its new number. Only the failed checks change: the steps, the tolerances and the other checks stay.
4. Press **Save the update**, then **Run it again**: PASSED.

A check of words (`phase`, `step`, `screen`) cannot take a new number: the window lists it, and you
change it or delete it by hand. The file gets a note with the date and the lines that changed; the old
numbers are in git.

## 8. Run all your tests with one command

From the `ftc-helper-lib` folder:

```bash
./gradlew :sim:scenario -PteamCode=../TeamCode
```

With no file named, it runs every test in your `simulator/scenarios` folder, each from a fresh robot
(nothing carries over from one test to the next), and ends with a list (paths shortened here):

```text
== 3 of 3 passed
  PASSED  ../simulator/scenarios/launch-and-back-up.sim
  PASSED  ../simulator/scenarios/main-teleop.sim
  PASSED  ../simulator/scenarios/pick-up.sim
```

One test: `-Pfile=../simulator/scenarios/main-teleop.sim`. The simulator's automatic tests
(`./gradlew :sim:testDebugUnitTest`) also run every file in that folder. Run your tests before you
commit, and before every event.

## 9. Write or change a line by hand (optional)

A test is plain text, so you can add a line the recorder did not: open the file in Android Studio
(`simulator/scenarios/`) and type it. For example, check that the claw is closed:

```text
expect servo claw target = 0.75 0.01
```

**Button names here are the FTC names, not the Java names:** `g2.dpadLeft` in `Controls.java` is
`g2.dpad_left` in a test. Every command, every check and the name list:
[Scenario files](../../guides/simulator.md#scenario-files-test-without-a-browser).

## 10. For programmers: tests on the laptop without the simulator (optional)

The simulator tests drive the whole robot, in real time. A **JUnit test** checks one piece of your
code in a fraction of a second, with pretend motors and servos: "after `Actions.scoreHigh`, the lift
was sent to 2400 ticks". They need a little Java. How: [tutorial 13, Testing](../13-testing.md#7-test-your-robot-in-five-files)
and the [test kit](../../modules/testkit.md); an example: [`V2botTest.java`](../../../examples/src/test/java/com/crossbotics/ftchelper/examples/v2bot/V2botTest.java).

## Try it with goBILDA's starter bot

No robot project yet? Try the recorder with goBILDA's starter bot, from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

1. Open http://localhost:8780. **Tests > Record a test**.
2. Choose "goBILDA Bot Launch and Back Up", INIT, press gamepad 1 **B** (RED), START. Wait until the
   Driver Station says `Autonomous finished`.
3. **Stop recording.** Look at the goal checks: it finishes, steps 1 and 2 are done, 3 pieces shot.
4. With `-PteamCode=none` there is no robot project, so **Download** saves the file in your browser's
   downloads. Press **Run it now**: PASSED.
5. Run the same test without the page:
   `./gradlew :sim:scenario -PteamCode=none -Pfile=sim/scenarios/recorded-gobilda-auto.sim`.
6. Record a TeleOp test the same way: "goBILDA Bot TeleOp", B, START, hold the left stick forward for
   a second, let go, **Stop recording**. The goal is where the robot ends.

## If something goes wrong

| What you see | Why, and what to do |
| --- | --- |
| The test fails on the first run after recording | Something in the run was not the same: a check too strict (see section 4), or the robot was still moving when you stopped recording. Loosen the tolerance, or record again and wait a moment before **Stop recording**. |
| `FAIL ... robot y = ... 3 -> was ...` a little more than 3 off | A fast robot ends a few inches off from run to run. Make the tolerance 5. |
| It fails now and then, then passes | The same: too strict. A test you cannot trust is worse than no test. |
| `true only after 5.3 s` | It happens, only later than `within` allows: slower on purpose? Update the test (section 7). |
| `waited 3 s: the OpMode shows nothing in INIT` | The OpMode sends no telemetry in INIT, so the test cannot count its loops. It still runs. |
| `unknown command` or a check says `too short` | A line typed by hand has a mistake: the report names it. See [Scenario files](../../guides/simulator.md#scenario-files-test-without-a-browser). |
| The page says `a test is running` | A test drives the robot: wait for it, or press **Stop the test** in the blue bar. |
| Save says `no robot project` | The simulator was started with `-PteamCode=none`: Download instead, and put the file in your robot project's `simulator/scenarios` folder. |
| The auto builder's Run is not in the test | A test runs your own OpModes: copy the builder's plan into your code (its Code tab), then record that autonomous. |

## Where next

- [The simulator guide](../../guides/simulator.md): every command and check a test can use.
- [Tutorial 13: Testing](../13-testing.md): JUnit tests for programmers.
- [Testing procedures](../../guides/testing.md): what to check on the real robot, and in what order.

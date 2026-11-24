# Scenario files

A scenario file (`.sim`) is a test: a short script that drives the simulator like a person and checks what
happens. The page's recorder writes one for you ([Record a test](../tests/01-record-a-test.md)). A team's
tests are in `simulator/scenarios/` in the robot project; the library's own are in `sim/scenarios/`.

## Run them

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/main-teleop.sim
./gradlew :sim:scenario                      # in a robot project: every test in simulator/scenarios
```

**Each file starts like a robot right after you turn it on**: a fresh simulator, the field at the match
start, and nothing kept from the file before. It keeps none of these: the alliance and the autonomous
report, a turret's angle, a launcher's trim, numbers tuned in the tuning menu or saved on the pretend
Control Hub, a Flywheel Tuner session. The Tunables numbers are the ones in your code. When you run several files, the command ends
with a list of which passed.

## Every command and check

```text
# lines starting with # are notes
robot ../robots/starter-bot-rev.properties  # optional: run this scenario with this robot file (path from
                                       # the scenario's folder) instead of the usual one
init "Main TeleOp"                     # INIT an OpMode (waits for its first screen)
start                                  # press ▶
press g1.a                             # press and let go (held until the OpMode surely saw it)
hold g1.left_stick_y -1                # set a stick or button and keep it
release g1.left_stick_y                # let go (release g1.all / release all)
wait 0.5                               # seconds
loops 3                                # wait for 3 OpMode loops
push 24 0 2                            # roll the robot by hand: forward in/s, right in/s, seconds
set battery 12.0
set touch lift_bottom on               # hold a switch pressed (off = let go)
set pose -56 -61.55 0                  # put the robot somewhere (x y heading)
set autotimer off                      # no 30 s autonomous stop
set teletimer off                      # no 2:00 TeleOp stop
set gamepad 1 ps                       # gamepad 1 is a PS controller: the screens say × ○ □ △ (letters = A B X Y;
                                       # the recorder does not write it: add it by hand)
set preload off                        # the robot holds no preload (now and after each reset); on: it does
set field start                        # every game piece back where the match starts it (the robot stays)
set device intake_deploy missing       # take a device out of the configuration from the next INIT
                                       # (a part not on the robot yet); present = put it back
set encoder lift none                  # a wiring fault for the Encoder Check: lift's port has no encoder
                                       # cable; set encoder lift flywheel = it has the flywheel's cable
                                       # (swapped); set encoder lift own = right again. Works at once
set sensor launcher 43 0.05            # a flywheel's speed reading: steps of 43 RPM, 0.05 seconds late
                                       # (off until set); set sensor launcher off = exact again
set loop 20 60                         # each loop takes 20 to 60 milliseconds; set loop off = fast again
set tunable IntakeDeploy.deployed 0.6  # a Tunables number for this file only (before init; see below)
expect screen has "Ready"              # or: screen not "..."
expect value "Lift" > 500              # the number on the screen line that starts with Lift
expect time < 25                       # seconds since ▶ (e.g. how long an autonomous took)
expect touch lift_bottom pressed       # or: open
expect error none                      # or: error has "..."
expect phase RUNNING                   # IDLE | INIT | RUNNING | STOPPED
expect step 3 done                     # the autonomous report's 3rd step (after "Start"): done, STALLED ...
expect motor lift power > 0.1          # power | ticks | velocity | amps; > < >= <= =  (= VALUE TOLERANCE)
expect motor launcher lowest >= 0      # the lowest power the code set since INIT (a brake anywhere in the run)
expect servo claw target = 0.53 0.01   # target | actual
expect servo feeder_left power = 0     # a continuous servo (crservo.NAME): the power the code set
expect light status = GREEN            # what a light (light.NAME = gobilda or blinkin) shows: GREEN, RED, off ...
expect shape intake_arm < 0            # a shape's angle (arm, turret), inches (slide) or gap (claw)
expect robot y > -30                   # x | y | heading | imu | shots
expect bumped none                     # or: bumped "Red HIVE"
expect camera "Webcam 1" sees 31       # a tag id in the picture; or: sees none | sees "RED SCORING" (webcam only:
                                       # a Limelight reports single tags; LimelightTags makes the groups)
expect size most ok                    # size start|match|most ok|fail (see "Robot shapes")
expect stored >= 1                     # game pieces in the robot; also: launched, scored, cell, tips, left (see "Game pieces")
expect points "red" >= 20              # the alliance's match points now ("blue" too)
expect clean >= 1                      # shots that went cleanly into an up CELL (see "Game pieces")
set piece POLLEN 30 -20                # put a game piece on the field (x y [z])
expect within 2 motor lift ticks > 500 # wait up to 2 s for it to become true
expect within 30 phase STOPPED         # e.g. wait for an autonomous to finish
stop
print screen                           # also: print motors | servos | cameras | size | pieces | robot | cad (not counted as checks)
```

The simulator ignores a note after `#` (outside quotes). A misspelled check fails with a message, and it
never passes. Button presses wait for the OpMode's loops. The simulator counts the loops by the OpMode's
screen updates, or by `loop()` calls in an iterative OpMode. It cannot count the loops of an OpMode that
never sends telemetry. Then each step waits 3 s, and the report says so.

Gamepad names: `g1.`/`g2.` plus `a b x y lb rb lt rt back start guide ls rs`, `dpad_up` (or `up`),
`left_stick_x` (or `lx`), `right_trigger` (or `rt`) and the other SDK names. The report shows each line
as ok or FAIL, with the value it saw. The command fails when any check failed.

## Button names

**Button names in a scenario are not the Java names.** A scenario uses the FTC SDK's names (or the
short names). `Controls.java` uses the library's names. These are the ones that differ:

| In `Controls.java` (Java) | In a scenario |
| --- | --- |
| `g2.dpadUp`, `dpadDown`, `dpadLeft`, `dpadRight` | `g2.dpad_up` (or `g2.up`), `dpad_down` (`down`), `dpad_left` (`left`), `dpad_right` (`right`) |
| `leftStickButton`, `rightStickButton` | `left_stick_button` (or `ls`), `right_stick_button` (or `rs`) |
| `leftStickX`, `leftStickY`, `rightStickX`, `rightStickY` | `left_stick_x` (`lx`), `left_stick_y` (`ly`), `right_stick_x` (`rx`), `right_stick_y` (`ry`) |
| `lb`, `rb` | `lb`, `rb` (or `left_bumper`, `right_bumper`) |
| `lt`, `rt` | `lt`, `rt` (or `left_trigger`, `right_trigger`); a value from 0 to 1 |
| `a b x y back start` | the same |

So `g2.dpadLeft.press(...)` in Java is `press g2.dpad_left` in a scenario.

## Another number for one test: set tunable

**Another number for one test: `set tunable`.** A Tunables number written as `MEASURE` stops INIT until
you measure it on the robot. In the simulator, the robot file's stand-in takes its place
([MEASURE in the simulator](../simulator/02-your-robot-file.md#5-measure-numbers-and-missing-parts)), so most tests need nothing. A test that wants
another value sets it: `set tunable IntakeDeploy.deployed 0.6` sets `Tunables.IntakeDeploy.deployed` to
0.6 for this file only. It wins over the stand-in. `set tunable IntakeDeploy.deployed MEASURE` checks
that INIT stops.

- It sets a word too: `set tunable Launcher.speedControl BANG_BANG`.
- Put it before `init` (or after `stop`), because the parts read their numbers at INIT. While an OpMode is
  in INIT or running, the line fails.
- It works for numbers (`double`, `int`, `float`, `long`) and `true`/`false`. `MEASURE` and `DEFAULT` work too.
- It sets the field in the Tunables class that your Robot.java uses. The simulator finds that class the way
  INIT finds it: `Tunables` next to Robot.java, or the class you give with `tunables(...)`. It also sets the
  field in every other class named Tunables that has it.
- At the end of the file, the number goes back to the value in your code. The next file starts fresh.
- A misspelled name fails and names what exists: `Tunables.IntakeDeploy has no field "deployd" (did you
  mean deployed?)`.

When you measure the real number, write it in Tunables.java. Delete the `set tunable` line when it only
stood in for the `MEASURE`.

## A real robot's speed reading: set sensor and set loop

The simulator's speed reading is exact, and its loops are fast. A real hub's reading jumps in steps and
arrives late, and a camera makes loops longer. `set sensor` and `set loop` copy that, so you can test a
flywheel's speed control. Only what your program reads changes. The wheel's physics and the hub's own speed
control still use the true speed. Both end with the file.

## Check a show(...) line

**Check a `show(...)` line.** A `show("Intake on", ...)` line is on the screen as `Label : value`
(the label, a space, a colon, a space, the value). Check it with its words:

```text
expect screen has "Intake on : true"
```

`expect value "Intake on" = true` does not work. `expect value` compares numbers only, and the
scenario stops with `"true" is not a number. To check words on the screen
(true/false, a name), use: expect screen has "Label : true"`.

## The recorder

Learn it first: [Record a test](../tests/01-record-a-test.md). The page writes a test for you. Open **Tests**, over the 3D view:

- **Record a test**: the field starts fresh. The running OpMode stops, the robot goes to its start spot,
  every piece goes back, and the Tunables numbers are the ones the code wrote. A test runs with the code,
  not with numbers tuned on the page. A red bar shows the time. Choose an OpMode, INIT, choose the
  alliance, press ▶, and do the job. Then press **Stop recording**. **Record from now** starts with the
  robot where it is (a `set pose` line). It is faster to start, but less reliable.
- What it writes:
  - `init`, `start`, `stop`.
  - every gamepad change from any source (keyboard, controller, on-screen, phone) as `press` (a tap
    shorter than 0.35 s), `hold` and `release`. A stick changes in steps of 0.25, and a sweep counts where
    it ended. When three or more let go at once, it writes `release g1.all`.
  - the time between them as `wait`. Before ▶, a wait is at most 1 s, because the wait changes nothing.
  - the robot or a piece moved by hand (`set pose`, `set piece`, `push`, `set field start`).
  - the page's settings that differ from a fresh simulator (`set battery`, `set preload off`,
    `set scatter`, `set autotimer off`).
  - An autonomous that ends by itself in the middle of the recording becomes
    `expect within T phase STOPPED`.

  It writes only commands of the [scenario language](scenario-files.md). The auto
  builder's Run is not one of them, so record your own autonomous instead.
- **Stop recording** opens the checks. The simulator suggests them from what it sees now.
  - **Goal checks** (ticked): the autonomous finishes (`phase STOPPED`, within 1.5 times the time it
    took), each step of its report that is `done`, pieces shot, scored or picked up and the points (`>=`),
    no error, no bumping into anything, a TeleOp still running. For a driving test with no pieces: where
    the robot ends.
  - **Details** (not ticked): where the robot ends (x and y give or take 3 in, the heading 5 degrees), the
    time since ▶ (`<` 1.5 times), and Driver Station lines. A Driver Station line check keeps only the
    line's steady part: the recorder cuts the line off from the first number on, because numbers change
    from run to run.

  You can change every check's numbers, operator and `within` time before you save. The checks that wait
  come first, then the others.
- **Save** writes `simulator/scenarios/NAME.sim` in the robot project (letters, digits, `-` and `_`).
  It asks before it replaces a file. The file gets a header: made by the recorder, the date, the OpMode,
  and how to run it. When the page runs another robot file than `simulator/robot.properties`, the test gets
  a `robot` line. When you started with `-PteamCode=none` (no robot project), use **Download** instead.
- **Run it now** runs the test on the page while you watch, from a fresh start like `:sim:scenario`. The
  page's gamepads and buttons wait, and **Stop the test** ends it. The result: PASSED, or FAILED with each
  failed line and the value it saw.
- **Run all tests** (in the Tests menu) runs every test in `simulator/scenarios`, one after another. Each
  runs exactly like `:sim:scenario`: a new simulator of its own, with the test's `robot` line. The page's
  robot waits meanwhile. The bar says `Running all tests: 2 of 5`, and **Stop the test** ends the run. The
  result: passed or failed for each test, with the failed lines. Click a test's name to open it, to run it
  alone or update it. The run uses the code that the simulator started with. So after a code change, stop
  the simulator and start it again first.
- **Open a test** lists the tests in `simulator/scenarios` (or opens a file from the computer) and runs one.
  It can fail after a change that you made **on purpose**. Then **The change was on purpose: update the
  test** runs it once more and measures. A `within` may then wait up to 3 times as long, to see how long it
  takes now. The page then shows each failed check with its new number:
  - `=` takes the value seen.
  - `>=`/`>` and `<=`/`<` take the value seen as the new limit.
  - `time <` and a late `within` take 1.5 times what it took.

  The steps, the tolerances and the checks that passed stay. A word check (`phase`, `step`, `screen`,
  `error`) cannot take a number, so the page lists it for you to change by hand. Press **Save the update**,
  then run the test again.

The page reads and writes only `.sim` files in `simulator/scenarios`, by a plain name (no folders). It
does this only for a page on the computer that runs the simulator. A phone joined with `-Plan` cannot.

# The simulator: a pretend Control Hub and Driver Hub

Try OpModes without a robot. Your real OpMode code runs on the laptop; the motors, servos, switches,
turn sensor (IMU) and the robot on the field are pretend, with simple physics. Good for: checking
buttons and screens, testing the finders, learning the controls, catching crashes before a meeting.
Not for: exact speeds, real shots, camera pictures.

```mermaid
flowchart LR
    P["Browser page<br/>Driver Hub + gamepads + hardware panel"] <--> S["Simulator (Java on the laptop)<br/>your OpMode + pretend hardware + physics"]
    C["Real controller<br/>(USB or Bluetooth)"] --> P
    K["Keyboard"] --> P
    Ph["Phone on the same WiFi<br/>(touch gamepad)"] --> S
```

## Start it

From the ftc-helper-lib folder:

```bash
./gradlew :sim:run
```

Then open **http://localhost:8780** in a browser. Ctrl+C in the terminal stops it. It needs what
building the library needs: Java and the Android SDK, which Android Studio installs (if Gradle says
"SDK location not found", see [Contributing](../contributing.md)). Windows, macOS and Linux; a
Chromebook with Linux turned on.

Options:

```bash
./gradlew :sim:run -Probot=sim/robots/my-robot.properties -Plan
```

`-Probot` uses another robot file. `-Plan` lets phones and tablets on the same WiFi join (Windows asks
about the firewall the first time). With `-Plan`, anyone on that WiFi can open the page and run
OpModes in the simulator: use it on a team or home network, not open school WiFi.

## The page

**Driver Hub** (left): choose an OpMode, then INIT, START, STOP, like the real one. The screen shows
the telemetry about as wide as a real Driver Hub. OpModes marked "(hidden on robot)" have `@Disabled`:
the simulator shows them anyway. Autonomous stops by itself after 30 s (the box turns it off).
An error in the OpMode shows in red, with the line where it happened.

**Gamepads** (middle): each gamepad has its own source:

| Source | How |
| --- | --- |
| On-screen | click or touch the buttons; drag the sticks; slide the triggers. **Sticky** (the box under the gamepads): a click holds a button until the next click and sticks stay where you leave them, so you can hold two things at once with one mouse. |
| Keyboard | left stick W A S D, right stick arrows, D-pad T F G H, A/B/X/Y K L J I, LB/RB Q E, LT/RT Z C, BACK Backspace, START Enter, GUIDE M |
| Controller | plug in a USB or Bluetooth controller, choose it, press any of its buttons once (a browser rule). Xbox, PS4, PS5 everywhere; Logitech F310 on Windows (on a Mac set its back switch to "D"). Chrome and Edge work best. |
| Phone / other page | start with `-Plan`, open `http://<computer address>:8780/?phone=2` on the phone (the page shows the address): the phone becomes gamepad 2 (`?phone=1`: gamepad 1). Set that gamepad here to "Phone / other page"; if you forget, the page says another page is using it. |

If both gamepads are set to Keyboard, the same keys work both. A browser tab in the background sends
gamepad updates only once a second, and after 2.5 s without news the simulator lets go of every
button (like a pulled cable): keep the simulator tab visible while you drive.

The GUIDE (PS) button often does not reach the browser (Windows and macOS keep it): use the on-screen
GUIDE, the M key, or hold START for 1 second (the finders accept that too).

**Control Hub** (right): the field from above (the drivers are at the bottom), "push by hand"
buttons (for the Drive Tuner's push test), every motor (power, count, speed, current; "STOP" when it
is at a hard stop), every servo (where it is and where it is going), the switches (click to hold
one pressed), and the battery voltage (lower it to see a weaker robot).

## The robot file

The robot file describes the pretend robot: every device with the SAME name as the robot
configuration, the motors and servos from the parts catalog, the robot's size, where it starts, and the
season's field. The library's example is `sim/robots/starter.properties`; a team keeps its own in its
robot project as `simulator/robot.properties` (used automatically, see below).

```properties
field = biobuzz-2026                  # the season's field (sim/fields/biobuzz-2026.json)
robot.lengthIn = 18                   # size, for bumping into field elements
robot.widthIn = 18
start.x = -56                         # where it starts (inches from the field center; y away from the drivers)
start.y = -61.55                      # BIOBUZZ G304: touching the wall, outside the LOADING ZONE
start.heading = 0                     # degrees, counterclockwise; 0 = facing away from the drivers
drive.type = mecanum
drive.motors = front_left, front_right, back_left, back_right
drive.part = gobilda-5203-312         # from the parts catalog: speed, encoder counts, current
drive.wheelDiameterIn = 3.90          # the REAL wheel (the Drive Tuner should find it)
imu = imu
motor.lift = lift, part=gobilda-5203-312, min=0, max=2750, gravity=0.06
motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder, shotDrop=0.15
servo.claw = 0.5, part=gobilda-2000-torque
touch.lift_bottom = lift, below=20
```

## The parts catalog

`sim/parts/parts.json` lists the common FTC motors and servos with their real part numbers and the
numbers from the seller's page (and the date they were checked): goBILDA Yellow Jacket (5203 and 5204,
every ratio), REV HD Hex (bare, UltraPlanetary combinations, spur gearboxes) and Core Hex, AndyMark
NeveRest, goBILDA 2000 series servos, REV Smart Robot Servos and Axon servos. A robot file names a part
by its `id` (`part=gobilda-5203-312`); the simulator takes the top speed, encoder counts per turn and
current (motors) or speed and range (servos) from it. Numbers the seller does not publish are left
empty, with a note. Real ratios are used (an UltraPlanetary "5:1" is 5.23:1). Servo speeds use the
seller's row closest to 5 V (a Control Hub servo port gives about 5 V; the headline number is often 6 V).

Motor kinds: `free` (just spins), `lift` (hard stops, gravity), `flywheel` (coasts; while its `feeder`
motor runs, a piece goes through every 0.3 s and slows it down). `ticksPerRev=` sets a motor's encoder
counts per turn (default 537.7; flywheel 28). On a robot, `setPower` in RUN_USING_ENCODER aims for the
top speed of the motor TYPE chosen in the configuration; here it is the robot file's `maxTicksPerSec`. The wheel motors are on the left/right sides of the robot like a
real one (the left ones mounted mirrored), so motor directions in your code matter here too.

## Your own robot code

When the library is a submodule of a robot project (`ftc-helper-lib/` next to `TeamCode/`), run the
simulator from the `ftc-helper-lib` folder: it builds **that project's TeamCode** too, lists only its
OpModes, and uses `simulator/robot.properties` from the robot project. Iterative OpModes
(`init()`/`loop()`) and LinearOpModes both run.

- Another TeamCode folder: `-PteamCode=<folder>`; the library's own examples instead: `-PteamCode=none`.
- TeamCode uses other libraries (Road Runner, Pedro Pathing, FTC Dashboard...)? Put their Gradle lines in
  `simulator/sim-dependencies.gradle` in the robot project, for example:

```groovy
repositories { maven { url = 'https://maven.brott.dev/' } }
dependencies { testImplementation 'com.acmerobotics.dashboard:dashboard:0.4.16' }
```

## Scenario files: test without a browser

A scenario file (`.sim`) drives the simulator like a person and checks what happens. Anyone can run
one, and every scenario also runs as an automatic test (the library's in `sim/scenarios/`, a team's in
`simulator/scenarios/`):

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/main-teleop.sim
```

```text
# lines starting with # are notes
init "Main TeleOp"                     # INIT an OpMode (waits for its first screen)
start                                  # START
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
expect screen has "Ready"              # or: screen not "..."
expect value "Lift" > 500              # the number on the screen line that starts with Lift
expect time < 25                       # seconds since START (e.g. how long an autonomous took)
expect touch lift_bottom pressed       # or: open
expect error none                      # or: error has "..."
expect phase RUNNING
expect motor lift power > 0.1          # power | ticks | velocity | amps; > < >= <= =  (= VALUE TOLERANCE)
expect servo claw target = 0.53 0.01   # target | actual
expect robot y > -30                   # x | y | heading | imu | shots
expect bumped none                     # or: bumped "Red HIVE"
expect within 2 motor lift ticks > 500 # wait up to 2 s for it to become true
expect within 30 phase STOPPED         # e.g. wait for an autonomous to finish
stop
print screen                           # also: print motors | servos | robot (not counted as checks)
```

A note after `#` is ignored (outside quotes). A misspelled check fails with a message, never passes.
Button presses wait for the OpMode's loops, counted by its screen updates (or by `loop()` calls in an
iterative OpMode); an OpMode that never sends telemetry cannot be counted, so each step then waits 3 s
and the report says so.

Gamepad names: `g1.`/`g2.` plus `a b x y lb rb lt rt back start guide ls rs`, `dpad_up` (or `up`),
`left_stick_x` (or `lx`), `right_trigger` (or `rt`) and the other SDK names. The report shows each line
as ok or FAIL with the value it saw; the command fails if any check failed.

## Fields: one file per season

`sim/fields/<season>.json` holds a season's field: solid elements (the robot bumps into them), floor
zones and tape lines, things overhead, and the AprilTags with their position and the way they face. The
robot file picks one (`field = biobuzz-2026`). The page draws it; the robot turns red and the panel names
the element when it bumps into one. A start position that overlaps a wall or an element is refused when
the robot file loads. Limits: a bump stops the whole move (the robot does not slide along a wall), and
its wheels keep turning, so wheel encoders keep counting while it pushes (like a real robot): an
encoder-driven autonomous can "arrive" while pinned. Solid footprints are taken from the floor up to
29 in, so a short robot may be stopped a little early by a leaning part. A new season is a new file: the BIOBUZZ file is made from the official
field CAD by a script (in the team's design repo), and the same kind of script works for the next CAD.

Coordinates: inches from the field center, +x to the right and +y away from the drivers (drawn at the
bottom), z up from the tiles; headings and tag facing in degrees, counterclockwise from +x for tags.

## Not simulated

Cameras (webcam and Limelight): an OpMode that needs one stops with "No WebcamName named..." (the
field file has the AprilTags, so a simulated camera is the next step). Game pieces are not simulated
yet. The Shot and Aim tuners work without a camera when `createCamera()` returns null. Physics is
simple: real numbers (gains, speeds, shot tables) must still be found on the robot.

Differences to know: the screen here updates as fast as the OpMode sends (a real Driver Station
redraws about 4 times a second, so a flickering line looks steadier there). STOP works like on a
robot: a `sleep()` ends at once, and an OpMode that ignores STOP for 3 s is cut off from the hardware
(restart the simulator to run another).

## Automatic tests

`sim/src/test/.../FinderSafetyTest.java` runs whole OpModes with scripted button presses, on every
push. It checks, for example, that help stops the motors, that a stick held through help does not
count, that the Drive Tuner's push test finds the real wheel size, and that every example starts and
stops with all motors off. Its first run found a real bug: every finder crashed at INIT in v0.7.2.

```bash
./gradlew :sim:testDebugUnitTest
```

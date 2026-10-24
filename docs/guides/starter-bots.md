# The four BIOBUZZ starter bots: robot files and code

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** a team with one of these starter bots (roads 1 and 2 of the [Quick start](../quick-start.md#pick-your-road)),
or anyone who wants a ready robot to try in the [simulator](simulator.md). This page has each starter
bot's [configuration names](#the-configuration-names), what its code does differently, and
[what to change when you changed the robot](#you-changed-your-starter-bot).

Each seller in FTC (REV, goBILDA, AndyMark, Studica) gives away the design of a simple "starter bot" for
the season. For 2026-27 (BIOBUZZ) we wrote one robot file for each, so the simulator has four real,
different robots to test with. They are also worked examples for the robot-file skill
([robot-file-skill.md](robot-file-skill.md)): every number has a note saying where it came from.

| Robot file | Drive | Intake | Scores with | Size in the simulator |
| --- | --- | --- | --- | --- |
| [starter-bot-rev.properties](../../sim/robots/starter-bot-rev.properties) | 6 wheels, 2 motors | flap wheels (Core Hex) | flywheel + CR-servo feeder | 17.5 x 17.2 in, 15.0 in tall (20.0 in wide with the poker out) |
| [starter-bot-gobilda.properties](../../sim/robots/starter-bot-gobilda.properties) | 6 wheels, drop center, 2 motors | Gecko wheels (motor + 2 CR servos) | flywheel + CR-servo "windmill" | 17.8 x 16.8 in, 12.0 in tall |
| [starter-bot-andymark.properties](../../sim/robots/starter-bot-andymark.properties) | 4 wheels, belts, 2 motors | rubber-band rollers (CR servo) | rubber-band catapult | 17.2 x 17.7 in, 17.5 in tall |
| [starter-bot-studica.properties](../../sim/robots/starter-bot-studica.properties) | 2 driven wheels + 2 omni | 4 wheels (motor) | flywheel + servo flap | 14.0 x 15.0 in, 13.0 in tall |

**Code for each one:** every kit bot is also a robot in five files with the seller's configuration names:
[`gobildabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot),
[`revbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot),
[`andymarkbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) and
[`studicabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot). To copy one into
your TeamCode: [Pick your template](../templates.md). The library has no catapult part (AndyMark) and no
gear between a flywheel motor and its wheel (Studica): those two files use the closest part and say so.
Each file names each part's motor or servo (`.part(...)`, or the motor's id in a launcher's brackets),
so the starting values are those parts' real numbers. The parts that are not in the parts
catalog (Studica's Maverick motors and Multi-Mode Smart Servo, AndyMark's High Speed programmable servo)
are described with `Motors.custom(...)` and `Parts.customServo(...)` and the seller's numbers.

**The autonomous programs** (`autos/`), the same for all four: each starts at the simulator robot file's
start spot (back against your wall, 56 in left of the centre). `mirrorForRed()` returns false: BIOBUZZ's
field is turned half a turn between the alliances (not mirrored), so the same route, driven from each
alliance's own wall, works for both.

**Shooting in the simulator:** only the goBILDA bot shoots. The REV, AndyMark and Studica files have no
`shoot.` line (their sellers do not publish the launch direction and angle), so their launchers spin but
no piece flies. Their flywheels may also not reach "ready" in the simulator: the stand-in motor numbers
are guesses (see each robot below).

All four fit the 18 in start cube and 18 x 24 x 29 in at full reach. Each has a scenario in
`sim/scenarios/starter-bot-*.sim` (it loads the robot with a `robot ../robots/...` line, checks the size
rules, rolls the robot forward by hand and checks for no error), so they run in the automatic tests.

To drive one in the simulator (`-Probot=` picks the robot file):

```bash
./gradlew :sim:run -Probot=sim/robots/starter-bot-gobilda.properties
```

To run its scenario (`-Pfile=` picks the scenario file):

```bash
./gradlew :sim:scenario -Pfile=sim/scenarios/starter-bot-gobilda.sim
```

**How sure are the numbers?** goBILDA and AndyMark publish STEP CAD files: we measured them on a laptop
(the bounding box of each part) and wrote down only the numbers, so their sizes and part positions are
measured. REV's CAD is on Onshape (needs a sign-in) and Studica's STEP download sits behind a browser
check, so their sizes are worked out from the part lengths in the build guides and the photos, and marked
"est." in the file. No seller's CAD is in this repository: the licences were not checked, and the robot
files hold only our own numbers.

**Same for all four:** the start pose is x = -56 with the back against the red wall (field file: inside
face at y = -70.585); the game-piece capacity is not stated by any seller, so `holds=4` is the game's limit;
motor directions come from the sellers' code where it has them.

## The configuration names

Make the robot configuration on the Driver Station with these names ([how](../quick-start.md#2-make-the-robot-configuration)).
They are the names in each seller's own code, so the template runs on the starter bot as it is. Use the
port each wire is really in. Leave the `imu` line as it is (the Control Hub's own IMU). A motor type
that is not in the Driver Station's list: pick the closest one (the library takes the motor's numbers
from `Robot.java`).

**goBILDA:**

| Name | Kind | What it is |
| --- | --- | --- |
| `left_drive`, `right_drive` | Motor (goBILDA 5202/3/4 series) | the drive motors (312 RPM) |
| `intake` | Motor (goBILDA 5202/3/4 series) | the intake roller (117 RPM) |
| `launcher` | Motor (goBILDA 5202/3/4 series) | the flywheel (a 312 RPM motor with its gearbox taken off) |
| `left_intake_servo`, `right_intake_servo` | Continuous Rotation Servo | the two corner wheels |
| `windmill` | Continuous Rotation Servo | the feeder |

**REV:**

| Name | Kind | What it is |
| --- | --- | --- |
| `leftDrive`, `rightDrive` | Motor (REV UltraPlanetary HD Hex) | the drive motors (20:1) |
| `intakeMotor` | Motor (REV Core Hex) | the intake |
| `flywheel` | Motor (REV HD Hex) | the flywheel (no gearbox) |
| `crServo` | Continuous Rotation Servo | the feeder roller |
| `pollenServo` | Servo | the pollen pusher ("poker") |

**AndyMark:**

| Name | Kind | What it is |
| --- | --- | --- |
| `left_drive`, `right_drive` | Motor (the closest NeveRest type) | the drive motors (NeveRest Hex 19.2:1) |
| `rollerI` | Continuous Rotation Servo | the intake rollers |
| `rollerF` | Continuous Rotation Servo | the flower roller (the "stick" version has a `stick` Servo instead) |
| `catapult` | Motor (the closest NeveRest type) | the catapult (NeveRest Hex 50.9:1) |

**Studica:**

| Name | Kind | What it is |
| --- | --- | --- |
| `leftDrive`, `rightDrive` | Motor (the closest type to a Maverick 50.9:1) | the drive motors |
| `Intake` | Motor | the intake (Maverick 3.7:1) |
| `FlyWheel` | Motor | the flywheel (Maverick 1:1) |
| `fwActuator` | Servo | the flap that pushes each piece into the wheels |

Capitals count: Studica's `Intake` and `FlyWheel` start with a capital letter.

## REV DUO FTC Starter Bot

Sources, read 2026-10-16: [the starter bot page](https://www.revrobotics.com/duo/ftc-starter-bot/), its
[build guide (PDF, 126 pages)](https://www.revrobotics.com/content/technical-resources/DUO/FTC-Kickoff-Concepts/2026-27/2026-27_REV_DUO_FTC_Starter_Bot-Build_Guide_1567.pdf),
and the [TeleOp code and configuration page](https://docs.revrobotics.com/ftc-kickoff-concepts/home/programming-teleop-and-auto).
CAD (not used, sign-in): [Onshape](https://cad.onshape.com/documents/9cb9eb90e973fd37d10ee178/w/b9c02b0f4e09998f08becbd2/e/a55c018c62564f3ea8812a47).

Modelled: tank drive (`leftDrive`, `rightDrive`: 20:1 UltraPlanetary HD Hex, 90 mm wheels, 1:1 chain);
the intake roller (`intakeMotor`, Core Hex); the flywheel (`flywheel`, HD Hex at 1:1, two 90 mm grip
wheels) on its frame; the feeder roller (`crServo`, Smart Robot Servo in CR mode); the pollen pusher
("poker", `pollenServo`) as an arm on the left side. Collects POLLEN.

Not modelled or not published:
- The shot: its CR-servo feeder could be the launcher's `feeder=`, but the flywheel's target speed and the
  launch angle are not on REV's pages, so there is no `shoot.` line.
- Sizes: all estimated (no CAD measured). The pollen pusher's preset positions are not on the page; the
  model swings it 0 to 90 degrees out to the side (a guess).
- The pusher really swings up and down out to the side; the simulator's arm always points forward from its
  hinge, so it is drawn turning about "up" instead.

### REV bot code

[`revbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot) is this robot in five
files ([lesson 1](../tutorials/start/01-robot-in-five-files.md) walks through them). The configuration
names are the ones in REV's own code, so it runs on that robot as it is. What the comments in the code
do not say:

- **Drive:** REV's code does not reverse a drive motor, so the library's default (the left side reversed)
  is a guess. If the robot spins when you push the stick forward, change it
  ([lesson 2, step 3](../tutorials/start/02-drive.md#3-say-which-motors-are-reversed)). REV does not say
  which way each motor turns: check them all on your robot.
- **Intake:** REV's code takes POLLEN in with the right bumper and pushes it out with the right trigger.
  REV's page does not say which power sign is "in": if the intake pushes out when it should take in, add
  `.reversed()` to its line.
- **Launcher:** the HD Hex motor runs at 1:1 (its gearbox is off), so `"rev-hd-hex"` gives it the 6000 RPM
  motor's numbers. The feeder roller is a Smart Robot Servo V2 (Balanced) in continuous (CR) mode.
  REV's code spins the flywheel "to the target velocity", but the number is not on the page:
  `speedRpm = 3000` is a guess, and so is the feeder's 0.4 s per piece. Find kF and kP with the
  [Flywheel Finder](finders.md#flywheel-finder-gains-and-recovery), then the speed by watching shots.
- **Fire all (Y):** spins up, then feeds the pieces one by one as soon as the wheel is at speed. It uses
  only the launcher, so the driver can still run the intake. With fewer than 4 pieces, the feeder only
  turns a few more times.
- **Poker:** a Smart Robot Servo V2 (Balanced) with two spots, as in REV's code: A = `down` (out over a
  FLOWER, to comb POLLEN out of it), B = `up` (folded along the side, the start). REV's page has no
  numbers for them, so both are `MEASURE`: INIT stops until you measure them with the
  [Servo Finder](finders.md#servo-finder-ends-and-positions) and write them in `Tunables.java`.
  **Why:** a guess could swing the poker into the frame. (The simulator's REV robot file has stand-ins
  for them, so it runs before you measure.) Nothing moves at INIT, so the code does not fold it: fold the poker by hand
  before INIT, so the robot fits the 18 in start cube. It first moves when A, B or an action sends it.
- **Buttons:** one driver on gamepad 1, with the library's standard buttons (LB flywheel on/off, RB fire
  one, D-pad up/down speed +/- 100 RPM), so this robot drives like the other examples. REV's code puts
  the intake on RB and RT and runs the feeder by hand on the D-pad: change a button in `Controls.java` if
  your drivers want REV's layout.
- **Autonomous** (`autos/`): REV's own autonomous is not on its page, so this is a simple first one: fire the preloaded pieces, then drive off the start line in the last 3 s.

## goBILDA FTC StarterBot

Sources, read 2026-10-16: [the resource guide page](https://www.gobilda.com/ftc-starter-bot-resource-guide-2026-2027-season/),
its [assembly instructions (PDF, 37 pages)](https://www.gobilda.com/content/user_manuals/3200-2627-0003_assembly-instructions.min.pdf),
[example code (ZIP)](https://www.gobilda.com/content/downloads/3200-2627-0003_example-code.zip) and
[STEP CAD (ZIP)](https://www.gobilda.com/content/step_files/3200-2627-0003.zip), measured.

Modelled: drop-center 6-wheel tank drive (`left_drive`, `right_drive`: 19.2:1 Yellow Jacket, 96 mm wheels,
30T to 30T gears); the intake (`intake`, 50.9:1, seven 48 mm Gecko wheels) and the two flat 72 mm Gecko
wheels at the front corners (`left_intake_servo`, `right_intake_servo`, CR servos); the launcher (`launcher`,
a 19.2:1 converted to 1:1, one 96 mm Hogback wheel) under the hood gridplate; the `windmill` feeder CR servo.
Collects POLLEN and **shoots**: the launcher's `feeder=windmill` (the CR servo), and the shot leaves up and
backward at 147 degrees from forward (the hood rises 33 degrees toward the back in the CAD). That is why
the simulator's shot arc says "shoots backward, 33° up" for it.

Its autonomous, **goBILDA Bot Launch and Back Up**, starts with the robot's back against your wall, left
of centre (x -56, y -63, facing the field: the simulator puts it there at INIT), fires the preload up and
back over the robot, then drives `Away from the wall` (forward, `Tunables.Auto.backInches`).
The robot is 15.1 in long, so at y -63 its centre is 7.5 in from the wall's inside face (y -70.6).
goBILDA's code launches for 10 s; here the launch ends as soon as the last piece is fed.

### goBILDA bot code

[`gobildabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot) is this robot in
five files. Part by part:

- Drive: one motor per side; the right one runs reversed, as in goBILDA's code.
- Intake: the roller's motor is the 117 RPM Yellow Jacket (50.9:1, 5203-2402-0051). The two corner
  wheels are goBILDA Speed servos in CR mode; they run with the roller, as in goBILDA's code.
- Launcher: a 312 RPM motor with its gearbox taken off (1:1, 28 counts per turn). That is the
  6000 RPM motor's numbers, so its id is `gobilda-5203-6000`.
- Windmill: a 2000 Series Torque servo (2000-0025-0002) in CR mode. It runs reversed, as in goBILDA's
  code (`Direction.REVERSE`).
- Each part names its motor or servo from goBILDA's build guide, so the starting values and the
  simulator use that part's real numbers.

**The TeleOp buttons** (`oneDriver = true`: one driver does everything on gamepad 1, like goBILDA's TeleOp):

- Left stick forward/back drives, right stick X turns. Left stick click: slow on/off.
- RT runs the intake in, LT out, while held.
- LB spins the flywheel up and down. RB fires one piece when the wheel is at speed; hold RB to keep firing.
- Y fires all four preloads (`Actions.fireAll`). Hold RT at the same time to help push them in: the action
  uses only the launcher, so the intake stays free for the driver.
- D-pad up/down: flywheel speed +/- 50 RPM.

One difference from goBILDA's code: there, RB also spins the wheel up. Here LB spins it up too, so the
driver can spin up early and fire the moment the shot is lined up. (RB alone also works: it spins up
first, then fires.)

Not modelled or not published:
- The exit angle and speed of a shot are not published (estimated from the hood; `share=0.5` is the default).
- The 100T/20T gear pair on the intake: which gear is on the motor is not clear from the guide. The
  simulator does not use it anyway (a roller only needs to turn).
- The lower 264 mm roller of small "Intake Roller" parts at the front is not drawn.
- Which side `left_intake_servo` is on is not stated.

## AndyMark Robits BIOBUZZ StarterBot

Sources, read 2026-10-16: [the kit page](https://andymark.com/products/robits-biobuzz-starterbot) (parts
list), [the StarterBot page](https://andymark.com/pages/2026-2027-robits-starterbot-base) with the
[assembly guide](https://cdn.shopify.com/s/files/1/0644/2303/5052/files/BIOBUZZ_StarterBot_Guide_REV1.pdf?v=1790188781),
[user manual](https://cdn.shopify.com/s/files/1/0644/2303/5052/files/FTC_BIOBUZZ_Robits_StarterBot_User_Manual.pdf?v=1789231495),
[TeleOp code](https://cdn.shopify.com/s/files/1/0644/2303/5052/files/RobitsStarterBotTeleop_051df575-88c0-401f-9b66-629c8754c6b8.java?v=1789231494)
and [STEP CAD](https://s3.amazonaws.com/andymark-files/CAD%20Files/Robits%20BIOBUZZ%20Robot.STEP) (roller
flower version), measured by subassembly.

Modelled: skid-steer tank drive (`left_drive`, `right_drive`: NeveRest 19.2:1, 3 in Stealth and omni
wheels, 40T to 40T, belts); the intake rollers (`rollerI`, CR servo) and the intake frame; the hopper; the
electronics and Control Hub; the launcher frame on the left side (`catapult` motor, NeveRest 50.9:1, as a
free-spinning motor); the flower roller on the right side (`rollerF`, CR servo); the `stick` servo of the
other flower version (not drawn). Collects POLLEN.

Not modelled or not published:
- The shot: a catapult wound by a motor and thrown by rubber bands is not one of the simulator's parts.
  Its release angle (set by the hood) is not published.
- The NeveRest Hex gearmotors (am-5442 / am-5550) are not in the parts catalog; the NeveRest Orbital 20 and
  50 are used (same motor and ratio; AndyMark's page gives only "~344 RPM" and "~130 RPM").
- The drivetrain has its own guide (ROBITS Starter Chassis), which we did not read; the 1:1 gears and the
  wheel base come from the CAD, the track width is estimated (each side is in its own CAD frame).
- The intake rollers' exact place inside the intake subassembly is estimated.

### AndyMark bot code

[`andymarkbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) is this robot
in five files ([lesson 1](../tutorials/start/01-robot-in-five-files.md) walks through them). The
configuration names are the ones in AndyMark's own code (RobitsStarterBotTeleop), so it runs on that robot
as it is. What the comments in the code do not say:

- **Drive:** the kit's NeveRest Hex 19.2:1 is the same motor and ratio as the NeveRest Orbital 20, so the
  code uses `"am-neverest-orbital-20"`. The right motor runs reversed, as AndyMark's code sets it.
- **Intake:** `rollerI` runs reversed (REVERSE in AndyMark's code) and the flower roller `rollerF` runs
  with it. Their servo is the kit's 4-wire Programmable Servo, High Speed (am-4955a), in continuous mode.
  It is not in the parts catalog, so `Parts.customServo("am-4955a", 300, 0.05)` describes it with
  AndyMark's numbers: 300 degrees, 0.05 s per 60 degrees at 6 V (the only speed listed;
  [am-4955](https://andymark.com/am-4955), read 2026-10-23). AndyMark's code runs the rollers at full
  power both ways.
- **The catapult** (a NeveRest Hex 50.9:1, am-5443, the same as the NeveRest Orbital 50; REVERSE in
  AndyMark's code) winds rubber bands and lets go once per turn. The library has no catapult part. The
  closest real part is an `Indexer` on a motor: it runs the motor at a set power (`power`, 0.65 from
  AndyMark's code) for a set time (`feedSec`) per press. Tune `feedSec` so one press is one turn of the
  catapult, which is one throw, and the arm stops ready for the next. The motor turns at about 130 RPM
  with no load, so one turn at 0.65 power takes about 0.7 s, more with the rubber bands pulling: 0.8 s is
  a starting guess. The library does not know when the catapult is wound or lets go (there is no sensor
  on it), so it cannot wait for "ready" the way a flywheel launcher does. AndyMark's own code runs the
  motor for as long as the trigger is held.
- **Throw all (Y):** throws up to 4 pieces (the game's limit), one turn each. The hopper feeds the next
  piece by gravity, so there is a short wait between throws (`Tunables.Auto.secondsBetweenThrows`).
- **The flower "stick" version:** the flower mechanism has a second version with a `stick` servo instead
  of the roller. For that one, take `"rollerF"` out of the intake line and add the commented `stick`
  line in `Robot.java` (`Tunables.Stick`: in = 0.46, out = 0.65). AndyMark does not say which of the
  kit's servos the stick has: use the one on yours (`"am-4955a"` if it is a High Speed servo too).
- **Buttons:** one driver on gamepad 1, with the library's standard buttons (RT intake in, LT out, RB
  throws one piece), so this robot drives like the other examples. AndyMark's code puts the intake on the
  bumpers (LB in, RB out) and runs the catapult while RT is held: change a button in `Controls.java` if
  your drivers want AndyMark's layout.
- **Autonomous** (`autos/`): AndyMark's own autonomous is not on its page, so this is a simple first one: throw the preloaded pieces (AndyMark's manual says to shoot from against the field wall, the launcher in line with the goal), then drive off the start line in the last 3 s.

## Studica FTC Starter Bot

Sources, read 2026-10-16: [the resource guide page](https://www.studica.com/ftc-starter-bot-resource-guide-2026-2027),
its [build guide (PDF, 64 pages, version 1.0 of 12 Sept 2026)](https://www.studica.com/images/uploaded/Resources/SR/2026-2027%20Starter%20Kit%20Build%20Guide.pdf),
the Blocks code picture on that page, and the
[Maverick motor page](https://www.studica.com/maverick-planetary-dc-gear-motor-wencoder-5091-ratio-gearbox)
(speed and encoder counts for every ratio). The pages are behind a browser check, so they were read in a
browser. STEP CAD: [ZIP](https://www.studica.com/images/uploaded/StepFiles/Starter%20Bot%202026-2027.zip), not downloaded.

Modelled: tank drive (`leftDrive`, `rightDrive`: Maverick 50.9:1 through 36T bevel gears to 100 mm
wheels, omni wheels behind); the intake (`Intake`, Maverick 3.7:1, four 50 mm wheels); the flywheel
(`FlyWheel`, Maverick 1:1, three 100 mm wheels); the hood; the `fwActuator` servo (positions 0 and 0.2 in
the code). The Maverick motors are not in the parts catalog: `rpm=` and `ticksPerRev=` from Studica's table.
Collects POLLEN.

Not modelled or not published:
- The shot: the servo flap could be the feeder (`feeder=` a servo feeds while it is more than 0.15 from
  where it starts), but the launch direction and angle are not published, so there is no `shoot.` line.
- The flywheel turns at half the motor's speed (13T to 26T bevel gears); the simulator takes the rim speed
  straight from the motor, so a `shoot.` line would need `share=` halved.
- Sizes: all estimated (no CAD measured). The Multi-Mode Smart Servo is not in the parts catalog and its
  speed is not on the kit page, so this file moves it at `servo.speed`. (Studica's
  [servo page](https://docs.wsr.studica.com/en/latest/docs/Motors/servo-motors.html), read 2026-10-23:
  300 degrees, 0.25 s per 60 degrees at 4.8 V. `studicabot/Robot.java` uses these.)

### Studica bot code

[`studicabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot) is this robot
in five files ([lesson 1](../tutorials/start/01-robot-in-five-files.md) walks through them). The
configuration names are the ones in Studica's own Blocks code, so it runs on that robot as it is. What the
comments in the code do not say:

- **The Maverick motors and the Multi-Mode Smart Servo** are not in the parts catalog, so
  `Motors.custom(...)` and `Parts.customServo(...)` describe them with Studica's own numbers (speed at
  12 V and encoder counts per output turn from the
  [Maverick page](https://www.studica.com/maverick-planetary-dc-gear-motor-wencoder-5091-ratio-gearbox);
  the servo from the [servo page](https://docs.wsr.studica.com/en/latest/docs/Motors/servo-motors.html),
  both read 2026-10-23). The starting values and the simulator then use the real parts.
- **Drive:** two driven 100 mm wheels at the front (Maverick 50.9:1, bevel gears 1:1) and two free omni
  wheels at the back. The library reverses the left side by default, as Studica's code does.
- **Intake:** Studica's code runs it at power 1 (in) on the right bumper and -1 (out) on the left bumper.
- **Launcher:** three 100 mm wheels on a Maverick 1:1, through 13T to 26T bevel gears, so the wheels turn
  at half the motor's speed. The library's launcher cannot take a gear between the motor and the wheel:
  its speed numbers (`speedRpm`, the D-pad's +/- 100) are the MOTOR's, not the wheel's. Studica's code
  runs the flywheel at the right trigger's power, with no set speed; the motor's top speed is 6100 RPM,
  so 4000 is a starting guess with room to speed up. Tune it by watching shots.
- **The flap** (`fwActuator`) pushes each piece into the wheels: rest = 0 and push = 0.2, from Studica's
  code (X and A). The flap goes to rest at START, and your servo's horn may sit differently from
  Studica's: measure both with the [Servo Finder](finders.md#servo-finder-ends-and-positions) BEFORE the
  first TeleOp. The push and return times are guesses.
- **Fire all (Y):** spins up, then pushes the pieces in one by one with the flap as soon as the wheel is
  at speed. It uses only the launcher, so the driver can still run the intake.
- **Buttons:** one driver on gamepad 1, one stick to drive, and the library's standard buttons, so this
  robot drives like the other examples. Studica's code drives "tank style" (each stick runs one side),
  puts the intake on the bumpers, the flywheel on RT and the flap on X and A: change a button in
  `Controls.java` if your drivers want Studica's layout.
- **Autonomous** (`autos/`): Studica's own autonomous is not on its page, so this is a simple first one: fire the preloaded pieces, then drive off the start line in the last 3 s.

## You changed your starter bot

This is road 2 of the [Quick start](../quick-start.md#road-2-a-changed-starter-bot). Start from your
starter bot's template ([way C](../quick-start.md#c-copy-your-starter-bots-template)), then make your
changes **one at a time**: change, build, check. **Why:** when something breaks, you know which change
did it.

**Keep the seller's names** for every part you did not change. **Why:** the template, the starter bot's
robot file and the notes on this page all use them.

### A part added

1. **The configuration:** a name for its port (small letters, `_` between words: `intake_deploy`).
2. **`Robot.java`:** a field at the top of the class, and an `add(...)` line in `define()`, with
   `.part(...)` for its motor or servo ([the parts](../modules/robot.md), [the parts catalog](parts-catalog.md),
   short recipes in the [cookbook](../cookbook.md)). Not built yet? Add `.optional()`: the code then runs
   without it ([parts not on the robot yet](../modules/robot.md#parts-not-on-the-robot-yet)).
3. **`Tunables.java`:** its section, named after the device in CamelCase (`intake_deploy` reads
   `Tunables.IntakeDeploy`). Write `MEASURE` for each position and limit, and `DEFAULT` for the rest
   ([DEFAULT and MEASURE](tunables.md#default-and-measure)). **Why:** INIT then stops until a finder gives
   the real number, so a guess never pushes the part into the frame.
4. **Buttons:** each part brings its own buttons. Move one, or add a job, in `Controls.java`
   ([lesson 3](../tutorials/start/03-buttons.md)). Several parts together: an action in `Actions.java`
   ([lesson 5](../tutorials/start/05-actions.md)).

### A part taken off

1. Delete its `add(...)` line and its field in `Robot.java`, and its section in `Tunables.java`.
2. Build. Android Studio marks in red every line that still uses it (in `Controls.java`, `Actions.java`
   and `autos/`). Delete or change each one.
3. Take its name out of the configuration.

### A motor, servo or wheel changed

1. Change its `.part(...)` (a drive: `.motor(...)` and `.wheelMm(...)`). The `DEFAULT` numbers follow the
   new part. Numbers that are written in `Tunables.java` (a flywheel's `kF`, a speed) may now be wrong:
   measure them again with the finder.
2. A drive motor changed: the Hardware Check and the motor directions again, and later the Drive Tuner
   again.
3. The configuration: change that port's motor type.

### A part moved, or a new shape

The robot code does not change, unless a servo's positions changed (then the Servo Finder again). Only
the simulator's robot file changes: the part's place and size
([Edit robot](simulator.md#edit-robot-shape-your-robot) in the simulator page, or `looks(...)` in
`Robot.java`: [shapes](../modules/robot.md#shapes-and-parts)).

### The simulator's robot file

Start from your starter bot's measured file: copy `sim/robots/starter-bot-<seller>.properties` to
`simulator/robot.properties` in your robot project. After each change, run `./gradlew :sim:fromRobot`
from the `ftc-helper-lib` folder: it adds a line for each new part, marked `TODO measure`
([a robot file from Robot.java](simulator.md#a-robot-file-from-robotjava)). The lines of the copied file
are yours, so it does not delete them: for a part you took off, delete its lines (and its `shape.` lines)
by hand. It stops and says why if a line in the file and your code disagree: fix that line by hand.
Then measure the robot and write the real sizes on the `TODO` lines.

### Your names on the Driver Station

Change the names in `@TeleOp(name = ...)` and `@Autonomous(name = ..., preselectTeleOp = ...)`, and
`group = "Examples"`, to your own. `preselectTeleOp` must be the TeleOp's name exactly.

### A worked example: team 8938's robot 1

Team 8938 started from goBILDA's starter bot ([its pages](https://crossbotics.com/teams/8938-octobots/)).
What they changed, and what each change needed:

| Change | Configuration | Code |
| --- | --- | --- |
| an intake that folds up at the start, pushed over by a servo at START | `intake_deploy` (Servo) added | a `Positioner` with `.optional()`; `Tunables.IntakeDeploy`: `stowed` and `deployed` are `MEASURE` (Servo Finder); an action that pushes it over at START, and X to push it again |
| corner wheels driven from the intake through gears, not by servos | `left_intake_servo`, `right_intake_servo` taken out | the `.withCrServos(...)` part of the intake line deleted; the new wheels are only shapes for the simulator |
| a flywheel on a sliding mount and a ramp that sends shots out of the back | none | only shapes and the shot angle (`.shotAngle(147)`) for the simulator |
| new buttons (B: intake on/off) | none | `Controls.java` |

Their order on the robot was the Quick start's: the Hardware Check, the Servo Finder, the Flywheel
Finder, then (on a block, at 0.4 power) the motor directions and the first drive, each mechanism, then
the Drive Tuner and the autonomous.

## What the simulator could not express (input for its next stages)

1. **A gear between a flywheel motor and its wheel.** Studica's 2:1 bevel gears: the rim speed comes
   straight from the motor, so the stand-in is `share=` halved on the `shoot.` line (`gear=` on a flywheel
   shape does not change the shot).
2. **A catapult.** AndyMark's rubber-band catapult (wound by a motor, released at the top of each turn).
   A stand-in is possible: the motor as a `flywheel` with `feeder=` itself and a fixed `speed=` (in/s) on
   the `shoot.` line, once a real shot is filmed. The AndyMark file does not do this yet.
3. **An arm that reaches sideways and swings up/down** (REV's poker): `axis=up` only swings an arm flat;
   swinging it up and down while it reaches sideways needs a second hinge.
4. **Shot direction and angle** are not published by any seller; a shot needs them.
5. **Motors not in the catalog:** Studica Maverick (every ratio) and the AndyMark NeveRest Hex gearmotors.
   Studica's Multi-Mode Smart Servo and AndyMark's programmable servos are not in it either.

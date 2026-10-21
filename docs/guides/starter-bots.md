# The four BIOBUZZ starter bots as test robots

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** anyone who wants a ready robot to try in the [simulator](simulator.md), or an example robot
file. **You need:** the simulator running ([Start it](simulator.md#start-it)).

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
backward at 147 degrees from forward (the hood rises 33 degrees toward the back in the CAD).

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
  speed is not on the page.

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

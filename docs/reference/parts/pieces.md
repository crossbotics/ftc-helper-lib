# Pieces and PieceSensor

Which game piece is at a spot, how many the robot holds, and which one fires next. `Pieces` keeps the count.
Each `PieceSensor` sees one spot: a color sensor, a distance sensor, or a second webcam.

## In Robot.java

```java title="Robot.java"
public Pieces pieces;                                             // at the top of Robot.java
pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")  // your kinds: also Tunables names
        .comingIn(PieceSensor.color("intake_color"))              // sees each piece come in
        .readyToFire(PieceSensor.color("ready_color"))            // optional: the next to fire
        .max(4)                                                   // BIOBUZZ G407: at most 4
        .startsWith(4, "POLLEN"));                                // the preload
intake = add(Intake.motor("intake").part("gobilda-5203-1150").stopsWhen(() -> pieces.isFull()));   // no 5th piece
```

**Pieces:**

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Pieces.kinds(names...)` | **yes** | your kinds of game piece. They are also the names in `Tunables.java` | - |
| `.comingIn(sensor)` | recommended | the sensor that sees each piece come in | nothing counts in |
| `.readyToFire(sensor)` | optional | the sensor where the next piece waits. It sees each one leave | the count follows the feeds |
| `.max(n)` | optional | the robot is "full" at this number. BIOBUZZ G407: at most 4 | the intake's `holds(n)` ([Intake](intake.md)) |
| `.startsWith(n, "KIND")` | optional | the preload | empty |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |

**PieceSensor** (one spot each):

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `PieceSensor.color(name)` | **yes**, one of the three | a color sensor | - |
| `PieceSensor.distance(name)` | **yes**, one of the three | a distance sensor | - |
| `PieceSensor.camera(webcamName)` | **yes**, one of the three | a second webcam | - |
| `.kinds(names...)` | only for a sensor alone | the kinds it tells apart. In `Pieces`, it gets the kinds from `Pieces.kinds(...)` | - |
| `.box(left, top, right, bottom)` | optional, camera only | the box of the picture to read, from the center: -1 = left or bottom edge, 1 = right or top edge | the middle quarter (-0.25, 0.25, 0.25, -0.25) |

A sensor alone, with no count: `add(PieceSensor.color("x").kinds("POLLEN"))`.

### The sensors

| Sensor | Part (checked 2026-10-21) | Tells apart | Cannot |
| --- | --- | --- | --- |
| `PieceSensor.color(name)` | REV Color Sensor V3, REV-31-1557 ($20.75): color + distance 1-10 cm (Broadcom APDS-9151) | every color: yellow POLLEN, red and blue NECTAR | see a piece more than ~5 cm away; mount it 1-2 cm from the piece |
| `PieceSensor.distance(name)` | REV 2m Distance Sensor, REV-31-1505 ($31.50): 5-200 cm, 1 mm resolution, 25° cone (VL53L0X) | sizes: a 3.6 in NECTAR is 20 mm bigger than a 2.8 in POLLEN | colors (red from blue NECTAR); read closer than 5 cm |
| `PieceSensor.camera(webcamName)` | a second UVC webcam, SDK `PredominantColorProcessor` | colors, from farther away | distance: "is one there" means "the box is colorful" (`minSaturation`); a gray background helps |

**BIOBUZZ numbers** (game manual TU01, section 9.8):

- POLLEN: about 2.8 in (7.1 cm) yellow balls (AndyMark am-5851_yellow).
- NECTAR: about 3.6 in (9.1 cm) red or blue balls (am-5852_red, am-5852_blue).
- They are "not perfectly spherical and may vary in size".
- G407: a robot may not CONTROL more than 4 at a time.
- G408: never control the OPPONENT's NECTAR. So a team that picks up NECTAR needs color, not only size.

### Where to put the sensors

- **The intake sensor:** put it where each piece passes once and is already held (past the rollers). Then a
  piece that bounces out does not count, and the intake does not stop with a piece half in.
- The sensor sees two touching pieces as one, unless they are different kinds, or the distance dips between
  them (`gapMm`; check it with two touching pieces). A gap between pieces (a spacer, or a short indexer step)
  is safer.
- **The ready sensor:** put it where the next piece waits, so it sees each one leave.

### A second webcam

When the robot also has an AprilTag webcam:

- Each camera gets its own VisionPortal (the SDK object that runs a camera). The color camera uses 320x240 pictures and no live view.
- The Driver Station can show one portal, unless the code splits the view with
  `VisionPortal.makeMultiPortalView`.
- Two webcams share the Control Hub's USB. Plug them into different USB ports (one is USB 3.0), or use a
  Limelight for the AprilTags.
- When a camera is missing or fails to start, the library warns, and the camera sees nothing. The OpMode
  goes on.

## In Tunables.java

<!-- tunables: PieceSensor -->
One section per sensor, named after its device in CamelCase (`intake_color` reads `IntakeColor`).
`Pieces` itself reads no numbers.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE`
means you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-piece-sensor -->
```java title="Tunables.java"
public static class IntakeColor {                               // "intake_color" in the configuration
    public static double POLLEN = MEASURE;                      // the kind's hue, degrees (Piece Tuner)
    public static double nearMm = DEFAULT;                      // a piece is there below this distance (40)
    public static double hueTolerance = DEFAULT;                // how far from a kind's hue still counts (30)
    public static double minSaturation = DEFAULT;               // how colorful it must be, 0 to 1 (0)
    public static double steadyMs = DEFAULT;                    // a reading must stay this long (30)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `<KIND>` | one per kind, the same name as in `kinds(...)`, e.g. `POLLEN`: the kind's hue (color sensor, camera) or the mm the sensor reads with it (distance sensor) | degrees 0 to 360 / mm | none: that kind reads `UNKNOWN` (INIT says so) | yes, at once | Piece Tuner | new lights at a venue, or a moved sensor |
| `nearMm` | color and distance sensors: a piece is there below this distance | mm | 40 (color), 100 (distance) | yes, at once | Piece Tuner | it sees pieces that are not there |
| `hueTolerance` | color sensor, camera: how far from a kind's hue still counts | degrees | 30 | yes, at once | Piece Tuner | kinds are mixed up (less) |
| `sizeToleranceMm` | distance sensor: how far from a kind's size still counts | mm | 8 | yes, at once | Piece Tuner | kinds are mixed up (less) |
| `minSaturation` | color sensor, camera: how colorful it must be (0 to 1) | 0 to 1 | 0.35 (camera), 0 (color) | yes, at once | Piece Tuner | the background counts as a piece (more) |
| `steadyMs` | a reading must stay this long before it counts | ms | 30 | yes, at once | test | flickers count as pieces (more) |
| `gapMm` | color and distance sensors: a dip this big between two touching pieces counts as two (0 = off) | mm | 8 | yes, at once | test with two touching pieces | two pieces count as one |
| `gain` | color sensor: its gain | number | the sensor's own | no | test | readings are too dark |

A section with tuned numbers:

```java
public static class IntakeColor {
    public static double POLLEN = 75, RED_NECTAR = 8, BLUE_NECTAR = 215;   // hues (degrees), from the Piece Tuner
    public static double nearMm = 40;                                      // a piece is there below this
}
```

**When a piece "is there":**

- A piece is there when the distance is below `nearMm`.
- A color sensor with no distance reading (not a REV Color Sensor V3) uses its color instead: a piece is
  there when the saturation is at least `minSaturation`, and at least 0.2 (gray is "empty").
- For a camera, a piece is there when the box is at least `minSaturation` colorful.
- A piece that is there but matches no kind's number reads `UNKNOWN`.

Find the numbers with the **Piece Tuner** ([`PieceTunerOpMode`](../../modules/finders.md#piecetuneropmode)).
It shows each sensor's readings and writes the lines to copy into `Tunables.java`. When you change a
number in the tuning menu and save it, copy it back into `Tunables.java`. The saved value is only on this
Control Hub, and the file is the record.

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.pieces.count()`.

**Pieces:**

| Command | What it does |
| --- | --- |
| `pieceIn(kind)`, `pieceOut()` | changes the count by hand (a claw, a button) |
| `clear()` | sets the count to 0 |
| `set(kinds...)` | sets the whole list by hand |

| Question | What it tells you |
| --- | --- |
| `count()`, `count(kind)` | how many it holds (a piece being fed counts until it has gone); how many of one kind |
| `next()` | the next to fire: a kind, `Pieces.UNKNOWN`, or `Pieces.NONE` (empty). During a feed: the one after it |
| `nextIs(kind)` | whether the next is that kind |
| `isFull()`, `isEmpty()` | whether it is full; whether it is empty |
| `isFullFor(kind)` | whether no more of that kind fits (the intake's `holds(n, "KIND")`) |
| `held()` | the list, next to fire first |
| `atIntake()`, `atReady()` | what each sensor sees now |
| `maxPieces()` | the number where it is full |
| `comingInSensor()`, `readyToFireSensor()` | its two sensors |
| `nextKind()` | `next` as a `Supplier<String>`: for a part that asks every loop |
| `whenFull()`, `whenEmpty()`, `whenNextIs(kind)` | conditions (`BooleanSupplier`) for lights, `rumbleWhen`, `stopsWhen`, an action's wait |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

**PieceSensor:**

| Question | What it tells you |
| --- | --- |
| `kind()` | the kind it sees now (after `steadyMs`) |
| `sees(kind)`, `seesPiece()` | whether it sees that kind; whether it sees any piece |
| `rawKind()` | the kind of this reading, before the steady check |
| `isWorking()` | whether it gives new readings |
| `hueDeg()`, `saturation()`, `distanceMm()`, `measured()` | the raw readings |
| `hasDistance()`, `measuresSize()` | whether it has a distance reading; whether it tells kinds apart by size |
| `kindNames()`, `sensorType()`, `problem()` | its kinds; its type; what is wrong, if anything |
| `arrivals()`, `readings()` | how many pieces it saw come; how many readings it had |

### How it counts

- **In:** each new piece the intake sensor sees counts one IN, with its kind. A piece is new when the spot
  was empty, or a different kind is right behind. With color and distance sensors, it is also new when the
  distance went up and down again by `gapMm`: two touching pieces of the same kind.
- **Out:** each feed of an [`Indexer`](indexer.md) (alone or in a launcher) counts one OUT, the oldest
  first. The robot fires in the order the pieces came in. It counts a piece out only once it has gone: the
  ready sensor sees it leave the slot, or, without a ready sensor, the feed ends without a jam.
- A jammed feed, or one stopped part way, counts nothing. With a ready sensor, a feed while the slot is empty
  counts nothing either.
- **Pushing out:** while the intake pushes out (`out()` or a held `reverse()`), each piece that leaves past
  the intake sensor counts one OUT (the newest), and nothing counts in. So backing a jam out and taking it in
  again counts right.
- **The ready sensor**, if there is one, has the last word on the next piece. It corrects the kind, and it adds a piece
  it sees that was never counted.
- After a shot, Pieces trusts the ready sensor again once the slot reads empty, or a new steady reading
  begins (the next piece). The end of the feed is not enough: the sensor's answer lags a little (`steadyMs`),
  and its old reading would put the piece just fired back in the count. After a jammed feed (nothing went),
  Pieces trusts the sensor again at once.

**The count is a helper, and it can be wrong.** A piece that bounces out, or two pieces read as one, make
it wrong. So the drivers are responsible for G407 (at most 4) until the robot has a hardware stop. When the
count is wrong:

- Hold the operator's **left stick button** for 1 s: count = 0 ([Buttons to add](#buttons-to-add)).
- To pull in when the count says full, hold the intake's in-button (RT) for 1 s. The Driver Station warns when
  the intake has been held back for more than 2 s.

**A sensor that is missing or stops answering** never stops the OpMode:

- When the robot configuration has no color or distance sensor of that name, INIT warns, and the sensor
  sees nothing.
- When a sensor (or camera) has no new reading for 0.5 s, it warns ("no new reading"). It then sees nothing
  until readings come back (`isWorking()` is false), and Pieces counts nothing with it.
- The loop never waits for a sensor. A REV 2m Distance Sensor reading can take up to 0.2 s, so the library
  reads it on a helper thread, and throws away a reading older than 0.25 s.

### Hooks for other parts

A shot table per kind, picked by the next piece:

```java
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera)
        .tableFrom(pieces.nextKind())                          // the next piece picks the table...
        .tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR"));     // ...both NECTAR colors use "nectar"
```

- "POLLEN" uses the `pollen` table by its name. "NONE" and "UNKNOWN" keep the table in use. A driver's
  button still works until the next piece changes ([Launcher](launcher.md)).
- Lights (`import com.crossbotics.ftchelper.robot.LightColor;` in Controls.java):
  `robot.lights.show(LightColor.GREEN).when(robot.pieces.whenFull());` and
  `robot.lights.show(LightColor.ORANGE).when(robot.pieces.whenEmpty());` ([Lights](lights.md)).
- To never feed with nothing in: `Launcher...requireReady("a piece to fire", () -> !pieces.isEmpty())`.
- To stop the intake when full: `.stopsWhen(() -> pieces.isFull())` on the intake ([Intake](intake.md)).

### Not done yet, and the simulator

- The count does not carry over from the autonomous into the TeleOp. The TeleOp starts with
  `startsWith(...)`. A ready sensor corrects an empty count.
- The simulator ([robot file](../robot-file.md#game-pieces): `color.NAME = at=ready`,
  `distance.NAME = at=intake, acrossMm=150`) simulates color and distance sensors. It does not simulate the camera.

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. But
  with one driver, the left stick button is the library's slow switch: give `clear` another button.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-pieces -->
```java title="Controls.java"
g2.leftStickButton.heldFor(1.0, robot.pieces::clear).says("piece count = 0");
```

---

**Look it up:** [`Pieces`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Pieces.html)
and [`PieceSensor`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/PieceSensor.html)
in the Javadoc (every method). **The other parts:** [Reference](../README.md).

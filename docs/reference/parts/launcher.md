# Launcher

A flywheel that shoots game pieces, and the [Indexer](indexer.md) that feeds it one piece at a time. It
can also have a [Hood](hood.md), a feed stop, a feed wheel, and parts that push the pieces in. Learn it step
by step: [Robot.java, add a part](../../five-files/01-robot-java.md#launcher).

## In Robot.java

```java title="Robot.java"
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera).requireReady("turret aimed", () -> turret.isAimed()));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Launcher.flywheel(motor, id)` | **yes** | a flywheel on this motor. The id is the motor's catalog id: the library needs it for the speed in RPM | - |
| `.feeder(Indexer.servo(name))` | recommended, one of the three | a servo arm pushes each piece in ([Indexer](indexer.md)) | nothing feeds the wheel |
| `.feeder(Indexer.crServo(name))` | recommended, one of the three | a CR servo wheel feeds the pieces, like goBILDA's "windmill" | nothing feeds the wheel |
| `.feeder(Indexer.motor(name))` | recommended, one of the three | a motor feeds the pieces | nothing feeds the wheel |
| `.reversed()` | optional | the flywheel turns the other way | - |
| `.speedFromDistance(camera)` | optional | the launcher sets the speed from the camera's distance to the goal. With odometry and `Tunables.Aim.goalX, goalY`, it uses the odometry's distance while the camera does not see the goal ([Aim](aim.md)) | one speed, `speedRpm` |
| `.speedFromOdometry()` | optional | the launcher sets the speed from the odometry's distance only (no camera) | one speed, `speedRpm` |
| `.requireReady(what, check)` | optional | one more check before each feed, for example "the turret is aimed" | - |
| `.spinAtStart()`, `.spinAtStart(on)` | optional | spins up at ▶ (autonomous and TeleOp), ready for the first shot. `spinAtStart(false)` waits for LB. `spinAtStart` in Tunables wins over both | on with a separate feeder (an indexer or a feed stop): a spinning wheel cannot throw a piece the feeder has not pushed in. Else off |
| `.hood(Hood)` | optional | the launcher's [hood](hood.md). The launcher takes its angle from the shot table's third column, and waits for it before it feeds | no hood |
| `.feedStop(servo)` | optional | a [feed stop](#feed-stop): a servo that holds the pieces back until a shot is allowed | no feed stop |
| `.feedStop(servo, part)` | optional | the same, with the servo's catalog id (`"gobilda-2000-speed"`). The Feed Stop Finder then works out `feedStopMoveSec` | no part: the finder cannot work out the open time |
| `.pushedBy(intake...)` | optional | the parts that push a piece into the wheel, in order ([piece path](#piece-path)). Add the intake **before** the launcher | nothing pushes |
| `.feedWheel(Indexer)` | optional | a [feed wheel](#feed-wheel): a CR servo or motor that moves each piece up to the `feeder` (a push arm). It needs `.feeder(...)` too | no feed wheel |
| `.feedWheelOutWith(intake)` | optional | the feed wheel runs backward while that intake pushes out (`out()`), so the pieces in the feeder come out too. A shot that is being fed wins. It needs `.feedWheel(...)`, or a feeder that spins (`Indexer.crServo` or `Indexer.motor`): with no feed wheel, the feeder itself runs backward | - |
| `.startTable(name)` | optional | the shot table at the start | the plain `speedByDistance` (named `main`, shown as `MAIN`), if there is one. Else the first by name: `nectar` before `pollen` |
| `.tableFrom(() -> name)` | optional | code (a game-piece sensor) picks the table. Each time its answer changes to a table's name, the launcher uses that table. null, "NONE" and "UNKNOWN" change nothing | the drivers pick the table |
| `.tableFor(table, kinds...)` | optional | which table some game-piece kinds use, for example `.tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR")`. A kind named like a table ("POLLEN") needs no line | - |
| `.shotAngle(deg)` | optional | for the simulator: which way a piece leaves, in degrees up from the wheel shape's forward. For a wheel mounted level, that is the robot's forward. 135 = up and out of the back | - |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the motor's name in CamelCase |

For a motor not in the catalog, use `Motors.custom(...)` ([Parts not in the catalog](../robot-java.md#parts-not-in-the-catalog)).

## In Tunables.java

<!-- tunables: Launcher -->
The section's name is the flywheel motor's name in CamelCase: a launcher on the motor `launcher` reads
`Launcher`, one on `shooter` reads **`Shooter`** (not `Launcher`). Its feeder has its own section: a feeder
on `feeder` reads `Feeder` ([Indexer](indexer.md)).

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE`
means you must measure it. A few lines have example numbers ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-launcher -->
```java title="Tunables.java"
public static class Launcher {                                  // "launcher" in the configuration
    public static double speedRpm = DEFAULT;                    // shot speed without the table (60% of top)
    public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
    public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
    public static double idleRpm = 1500;                        // spins between shots: quicker first shot (0)
    public static double shotIntervalSec = DEFAULT;             // least time between two pieces (0.25)
    // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
    // Then write the Finder's numbers in their place.
    public static double kP = DEFAULT;                          // push per speed error (kF / 10)
    public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
    public static double kD = DEFAULT;                          // brake changes (0)
    public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    // {inches to the goal, RPM}, blended between rows. Example rows: write the Shot Tuner's instead.
    public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `speedRpm` | the shot speed when no shot table sets it. At most the motor's top speed | RPM | from the parts catalog: 60% of the motor's top speed, to 10 RPM (6000 RPM motor: 3600) | yes, at once | Shot Tuner or test | shots land short or long |
| `stepRpm` | one D-pad press changes a fixed speed by this | RPM | 100 | yes, at once | none | the drivers want bigger or smaller steps |
| `readyBandRpm` | "at speed" means within this of the target | RPM | 60 | no | test | it waits too long to fire (more) or shots vary (less) |
| `shotDropRpm` | a drop of more than this after "at speed" counts as a shot: the Shot Tuner's shot count, `stopOnShot`, the feed stop and the Feed Stop Finder's test shot use it. Keep it more than `readyBandRpm`: at or under it, INIT shows a note, because the wheel's normal wobble can count as a shot | RPM | 150 | no (at INIT) | Flywheel Finder ("Shots seen" and "Shot drop") | it misses shots (less) or counts shots with no piece (more) |
| `ballDropRpm` | the speed ONE real ball takes off the wheel. Only the [Flywheel Tuner](../../tuning/06-flywheel-tuner.md) and the [Group Shot Tuner](../../tuning/14-group-shot-tuner.md) use it, for their pretend shot. Shot counting never uses it | RPM | not set: the tuners use `shotDropRpm` | no (at INIT) | Flywheel Finder ("Shot drop") | the tuners time the way back after a shot that is too small (more) |
| `idleRpm` | the speed between shots (0 = stop) | RPM | 0 | no | test | the first shot takes too long to spin up |
| `shotIntervalSec` | the least time between two shots | seconds | 0.25 | no | Flywheel Finder (the recovery) | quick shots land short (more) |
| `maxFiringQueue` | a tap on RB or `fire()`: the most shots that may wait at once. The launcher ignores more taps. The piece being fed now does not count. 0 = no queue: the launcher ignores a press while a shot is on its way (waiting or being fed). `fire(n)` and actions have no limit | shots | 4 | no | none | taps fire more (or fewer) shots than the drivers mean (0: one press, one shot) |
| `speedByDistance` | the shot table: rows of `{inches, rpm}`, or `{inches, rpm, hood}` with a [hood](hood.md). The launcher blends between rows | inches, RPM | none: `speedRpm` | no | Shot Tuner | the field, the balls or the wheel changed |
| `speedByDistance<Name>` | one more table per game piece, e.g. `speedByDistancePollen` (its name is "pollen") | inches, RPM | none | no | Shot Tuner | pieces fly differently |
| `kP` | the hub's speed control: push per speed error | hub units | from the parts catalog: kF / 10 (6000 RPM motor: 1.17) | no | Flywheel Finder | after the Flywheel Finder |
| `kI` | the hub's speed control: slow fix of a steady error | hub units | from the parts catalog: kP / 10 (6000 RPM motor: 0.117) | no | Flywheel Finder | the same |
| `kD` | the hub's speed control: brakes changes | hub units | 0 | no | Flywheel Finder | the same |
| `kF` | the hub's speed control: the steady power for a speed | hub units | from the parts catalog: 32767 / the motor's top speed in counts per second (6000 RPM motor: 11.7) | no | Flywheel Finder | the same |
| `nominalVolts` | the battery voltage the gains were found at (battery compensation), 9 to 14 | volts | 12.8 (INIT notes that it assumes this) | no | Flywheel Finder | after the Flywheel Finder |
| `batteryCompensation` | with a written `kF`: scales F by the battery voltage (nobody has proven it on a real robot yet). Without your own `kF` it stays off, with a note | true/false | false | no | none | to try it: `true`, then the 4-shot test ([below](#speed-trim-and-battery-compensation)) |
| `trimStepPercent` | one speed trim step | percent | 1 | no | none | the drivers want bigger steps |
| `trimMaxPercent` | the most speed trim, at most 50 | percent | 15 | no | none | you need more trim (better: fix the table) |
| `redTrimPercent` | makes every shot speed this much faster on the RED alliance (the field halves differ a little) | percent | 0 | no | test, at an event | shots on one half land short or long |
| `blueTrimPercent` | the same on the BLUE alliance | percent | 0 | no | test, at an event | the same |
| `quickBandRpm` | QUICK mode: after a volley's first piece, the launcher feeds within this of the speed | RPM | 3 x `readyBandRpm` | no | firing stats (the drop) | QUICK shots land short (less) or it waits too long (more) |
| `quickMaxWaitSec` | QUICK mode: after a volley's first piece, the launcher feeds after this wait at most | seconds | 0.3 | no | firing stats (the recovery) | the same |
| `sprayEnabled` | allows SPRAY: no wait for the speed, at the end of TeleOp and when QUICK will not fit before the park in autonomous | true/false | false | no | none | the drivers want it |
| `sprayLastSec` | with `sprayEnabled`: the last seconds of TeleOp that SPRAY (0 = never by itself) | seconds | 5 | no | none | the drivers want more or less |
| `sprayOnTapHold` | with `sprayEnabled`: a tap, then a hold of the fire button sprays while held | true/false | true | no | none | the drivers spray by accident (false) |
| `spinAtStart` | spins the wheel up at ▶, so it is at speed for the first shot. Wins over `spinAtStart(...)` in `Robot.java` | true/false | true with a separate feeder (an indexer or a feed stop), else false | no | none | save the battery (false), or a robot without a feeder of its own whose drivers want it (true) |
| `fallbackDistanceIn` | with the speed from the distance: the distance to use before the camera has seen any goal | inches | none: `speedRpm` | no | none | the first shot of the match has no goal in view |
| `distanceMedianOf` | with the speed from the camera's distance: the launcher uses the middle of this many pictures, so one or two bad pictures never change the speed ([DistanceFilter](../../modules/vision.md#distancefilter)); 1 = off | pictures, 1-15 | 5 | no | none | the speed follows the distance too slowly (lower), or glitches still get through (higher) |
| `distanceJumpIn` | a distance change bigger than this needs 3 pictures that agree before the launcher uses it (except after the goal was out of view for 0.5 s); 0 = no jump check | inches | 12 | no | none | real moves are held back (higher), or glitch runs get through (lower) |
| `distanceJumpAgreeIn` | for the jump check: how close each of the 3 pictures must be to the one before | inches | 4 | no | none | a real jump is never used: the camera's numbers jump about more (higher) |
| `distanceSmoothSec` | with the speed from the camera's distance: the speed eases into each new camera distance over about this time (the odometry's is used at once), so small moves of the camera's distance do not restart "ready". The cost: while the robot drives toward or away from the goal, the speed lags (at 10 in/s and 0.3 s, about 3 in behind) | seconds, 0-3 | 0 (off) | no | test | firing waits while the camera's distance wobbles (0.2 to 0.3), or shots fired on the move land long or short (less) |
| `feedStopHold` | with `.feedStop(...)`: the servo position that holds the pieces back | servo position 0-1 | 0 (a guess, INIT says so) | no | Feed Stop Finder | after the Feed Stop Finder |
| `feedStopRelease` | with `.feedStop(...)`: the servo position that lets a piece through | servo position 0-1 | 0.5 (a guess) | no | Feed Stop Finder | the same |
| `feedStopMoveSec` | with `.feedStop(...)`: how long it takes to open; the indexer and the pushing parts wait this long | seconds | 0.1 | no | Feed Stop Finder (with the servo's part) | a slow servo is still part-closed when the piece comes (more) |
| `feedStopOpenSec` | with `.feedStop(...)`: the backup time it stays open when the wheel sees no shot | seconds | 0.5 | no | Feed Stop Finder (test shots) | pieces get stuck (more) |
| `assistSec` | with `.pushedBy(...)`: how long the parts push each piece in, at most (less when the wheel sees the shot) | seconds | 0.25 | no | test | pieces do not reach the wheel (more) |
| `ejectRpm` | `ejectAction()`: the low speed that pushes one piece out | RPM | 600 | no | test | the piece does not clear the robot (more) or flies too far (less) |
| `ejectSec` | `ejectAction()`: how long the eject lasts | seconds | 1 | no | test | the piece is still in the robot at the end (more) |
| `ejectFeedSec` | `ejectAction()` with a spinning feeder (CR servo or motor): it runs only this long, so it pushes one piece and not the next one too; 0 = its own `feedSec` | seconds | 0 | no | test | two pieces come out (less), or none does (more) |
| `extendTableEnds` | lets the shot table guess past its tested rows: each end keeps the line of its two rows. The screen says "estimated" there ([table ends](#speeds-past-the-ends-of-the-table)) | true/false | false | no | test | the goal is a little outside your tested distances (true) |
| `extendMaxRpm` | with `extendTableEnds`: the fastest speed a guess may ask for. The trim and the boost cannot go past it there | RPM | 0.8 x the motor's top speed (6000 RPM motor: 4800) | no | test | far guesses never reach speed (less) |
| `speedControl` | who holds the wheel's speed: `"PID"` (the hub, steady) or `"BANG_BANG"` (full power, holding power or no power). INIT stops on another word ([speed control](#speed-control-pid-or-bang-bang)) | text | "PID" | no | test | the wheel gets back to speed too slowly after a shot (try `"BANG_BANG"`) |
| `bangKs` | bang-bang holding power: the volts that beat friction. Holding volts = `bangKs` + `bangKv` x RPM | volts | 0 | no | Flywheel Tuner | the wheel holds too slow at every speed (more) |
| `bangKv` | bang-bang holding power: the volts for each RPM of speed | volts for each RPM | from your `kF` (below); with no `kF`, 12 / the motor's top speed (6000 RPM motor: 0.002). INIT says which, and that it is not tuned | no | Flywheel Tuner | the wheel holds too slow (more) or too fast (less) |
| `bangFullBelowRpm` | bang-bang: full power starts this far below the wanted speed | RPM | 200 | no | Flywheel Tuner | it gets back to speed too slowly (less) or overshoots (more) |
| `bangCoastAboveRpm` | bang-bang: the power goes to 0 this far above the wanted speed, and the wheel coasts | RPM | 75 | no | Flywheel Tuner | it runs too fast (less) or coasts too often (more) |
| `bangHysteresisRpm` | bang-bang: full power and coasting end this much later than they start, so a jumpy reading does not switch them on and off. Less than `bangFullBelowRpm` | RPM | 50 | no | Flywheel Tuner | the power switches on and off too often (more) |
| `bangMaxPower` | bang-bang: the most power | 0.3 to 1 | 1 | no | test | belts slip, or the battery dips too far (less) |
| `continuousOnHold` | holding the fire button feeds one aimed group with no break. A tap still fires one ([continuous fire](#continuous-fire)) | true/false | false | no | test | you need fast groups, and they still score (true) |
| `continuousMaxSec` | continuous fire: the most seconds one hold may feed | seconds, at most 10 | 3 | no | test: feed a full load | the feed ends before your last piece (more) |
| `continuousFeedPower` | continuous fire: the feeder's power during a group. Use the same direction as the feeder's `power` | power | the feeder's `power` | no | test | full pace still misses after the wheel is tuned (less: the last thing to try) |
| `continuousBoostRpm` | continuous fire: speed added when the feed starts. It is never added before the first piece, and it ends with the group | RPM | 0 | no | test | later pieces land short and the wheel slows down (more); they land long (less) |
| `lockSpeedWhileFiring` | with the speed from the distance: while a tapped shot (CAREFUL) waits for "ready" and feeds, its speed stays fixed. The camera's distance can still move then, but it does not move that shot's speed or restart "ready"; `Shot distance` shows the distance the shot uses. It lets go if the distance moves more than 4 in (the robot drove: the old speed would miss). A held stream (QUICK) and SPRAY keep following the distance | true/false | false | no | none | firing waits, or a shot's speed changes while it waits (true) |

**The shot table** (`speedByDistance`):

- Rows are `{inches, rpm}`. The launcher blends the speed between rows. With no goal in view, it keeps the
  last speed.
- The camera's distance goes through a [glitch filter](../../modules/vision.md#distancefilter) first:
  `distanceMedianOf`, `distanceJumpIn`, `distanceJumpAgreeIn`. With `distanceSmoothSec` (off by default), the
  speed then eases into each new camera distance. Why: on a steep part of the table, a 1 in wobble of the camera's
  distance changes the speed by more than half the ready band, and "ready" starts again. Eased, the speed moves in
  small steps the wheel follows, so it stays ready. With no goal in view it holds the last speed; a new sighting
  after 0.5 s comes in at once.
- One table per game piece: `speedByDistancePollen`, `speedByDistanceNectar`. Any name after
  `speedByDistance` works.
- With a [hood](hood.md), a row is `{inches, rpm, hood}`. The launcher blends the hood between rows like the
  speed. Rows with two numbers still work: then you set the hood by hand.

```java
public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};   // Tunables.Launcher
public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
// a button: g2.x.press(() -> robot.launcher.useTable("nectar"));   an action: .then(() -> r.launcher.useTable("nectar"))
```

The Driver Station shows `Shot table: POLLEN, 48 in (camera)`. In place of `camera` it says `odometry`, or
`last seen` while it sees no goal. With one table, it shows `Shot distance: 48 in (camera)`.

**The first run** ([First run](../tunables.md#first-run-what-you-get-before-tuning)):

- `kP`, `kI`, `kD` and `kF` are the hub's speed control. The [Flywheel Finder](../../tuning/README.md)
  finds them, and `nominalVolts`, the battery voltage they were found at.
- The library starts the ones you do not write from the parts catalog, for the motor in
  `Launcher.flywheel(...)`. It does not use the hub's own gains. F = 32767 / its top speed in counts per
  second, P = F / 10, I = P / 10, D = 0. A 6000 RPM
  motor gets kF 11.7, kP 1.17, kI 0.117.
- When you do not write `speedRpm`, it is 60% of the motor's top speed (3600).
- INIT says `Starting gains (worked out, not tuned): Launcher` until you write your own (the Flywheel
  Finder). The robot log has the values: `Launcher: defaults from the catalog (gobilda-5203-6000): kF 11.7,
  kP 1.17, kI 0.117, speed 3600 rpm. Run the Flywheel Finder, then the Shot Tuner`.
- A written number always wins. `DEFAULT` in place of a number is the same as no line.

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.launcher.fire()`.

| Command | What it does |
| --- | --- |
| `spinUp()` | spins up to the shot speed |
| `idle()` | goes back to `idleRpm` |
| `toggle()` | spins up, or goes back to idle |
| `fire()` | spins up if needed, and feeds as soon as it is at speed. It gives up after 3 s and says why. Each `fire()` adds one shot, up to `maxFiringQueue` waiting (Tunables, default 4). The launcher ignores more taps. With `maxFiringQueue = 0` there is no queue: the launcher ignores a press while a shot is on its way. A press of RB counts the same way |
| `fire(n)` | fires n pieces. No limit |
| - | When it fires while idle (the driver switched the wheel off), it spins up for the shots and goes back to idle after the last one |
| `fireWhileHeld()`, `ceaseFire()` | the launcher's RB job. (With `continuousOnHold`, see [continuous fire](#continuous-fire).) Every press fires at once, like a tap on `fire()` (one more shot, up to `maxFiringQueue`). While held, it feeds the next piece as soon as the last is fed (QUICK). A tap, then a second press within `Buttons.tapGapSec` that is held, sprays while held (SPRAY, with `sprayEnabled` and `sprayOnTapHold`). On release, the hold adds no more shots, and the taps' shots still go. When you put your own double tap or long press on the same button, a press no longer fires at once |
| `fireAction(n)`, `fireAllAction()` | ready-made [actions](../steps.md#action): fire n pieces ("Fire 2"), or every piece the robot holds ("Fire all": a `Pieces` part's count, else the intake's `holds(...)`, else 4). It is done when the last one has left. Its time limit is 3 s + 2 s a piece. `.action(robot.launcher.fireAction(1))` in an autonomous, `g1.y.press(robot.launcher.fireAllAction())` in TeleOp. It runs only the launcher: the intake keeps doing what it does |
| `fireAction(n, rpm)`, `fireAllAction(rpm)` | the same, at this flywheel speed (RPM), then back to the usual one (`speedRpm` or the shot table). A stopped action goes back too. For a robot with **no camera** that shoots from a spot it knows: `.action(robot.launcher.fireAllAction(2250))`. The auto builder's **flywheel** box on a fire action writes it. A speed above the motor's top speed stops INIT |
| `part().continuous()` | the group of continuous fire, for your own code or a tuner: [ContinuousFire](../../modules/launcher.md#continuousfire) |
| `shotSight()` | what a group judges by: the goal, the filtered distance and the strict aim |
| `continuousOnHold()`, `isFiringContinuously()` | whether the option is on; whether a group waits or feeds |
| `nonstopFeedPower()` | the feeder power a group feeds with: `continuousFeedPower` when written, else the feeder's own (NaN for a feeder that cannot feed nonstop). The Group Shot Tuner tries 90% and 80% of it |
| `usesDistance()` | whether Robot.java gives the speed a distance (`speedFromDistance(camera)`, `speedFromOdometry()`) |
| `readyAction(timeoutSec)` | a ready-made action: spins up, and is done when ready ("Ready to launch") |
| `ejectAction()`, `eject()` | pushes one piece out at a low speed (`ejectRpm` 600, `ejectSec` 1) ("Eject"). It feeds once the wheel is near that speed (a wheel at shooting speed slows first). Then the wheel goes back to what it was doing |
| `useSpeed(rpm)`, `useNormalSpeed()` | uses one speed from now on (the speed trim still applies) until `useNormalSpeed()`. For most code, the fire actions above are better: they go back by themselves |
| `adjust(steps)` | changes the speed by +/- steps x `stepRpm` (not saved) |
| `step(direction)` | the launcher's own D-pad job: one step (+1 / -1). With a shot table it changes the trim, else the speed by `stepRpm` |
| `repeatStep(direction)`, `repeatEnd()` | while held: a step after 0.5 s, then 4 a second, and `repeatEnd()` on release (`g2.dpadUp.hold(() -> robot.launcher.repeatStep(1), robot.launcher::repeatEnd)`) |
| `trimBy(change)`, `setTrim(percent)`, `resetTrim()` | changes the [speed trim](#speed-trim-and-battery-compensation): every shot speed (table or fixed) +/- that many percent, for example `trimBy(+2)`. The launcher keeps it within +/- `trimMaxPercent` and the motor's top speed. `resetTrim()` also clears `adjust` |
| `useLastTrim()` | uses the trim from last time (a double-tap of D-pad up does this). The launcher never applies it by itself |
| `useTable(name)`, `nextTable()` | picks the shot table to use ("pollen" for `speedByDistancePollen`; capitals do not matter); picks the next one. With a name that has no table, it warns and keeps the one in use |
| `shotMode(mode)` | sets the [shot mode](#firing-shot-modes-feed-stop-piece-path) by hand: `ShotMode.CAREFUL`, `QUICK`, `SPRAY`. `null` = the launcher chooses (the default) |
| `fireOn(button)`, `spinOn(button)` | in `Controls.java`: puts RB's whole fire job (or LB's spin up / down) on another button, for example `robot.launcher.fireOn(g2.y)`. `null` = no button. INIT shows no note for the moved job (a job you push off the new button still gets one). Call it again to move it again |
| `speedOn(up, down)`, `tableOn(button)` | puts the speed steps and the next table on buttons ([Buttons to add](#buttons-to-add)) |

| Question | What it tells you |
| --- | --- |
| `isReady()` | whether it is at speed and every check is OK |
| `writtenGains(tunables)` | the gains written in Tunables.java, {kP, kI, kD, kF, nominalVolts or NaN}, or null (the Flywheel Finder in Tune and check holds with them) |
| `isShooting()` | whether it fires now |
| `isSpinning()` | whether it is spun up (or spins up); false when idle |
| `targetRpm()` | the speed it goes to |
| `maxFiringQueue()` | the most shots `fire()` (or a press of RB) lets wait at once |
| `isSprayingOnHold()` | whether it sprays now, from a tap then a hold |
| `sprayOnTapHold()` | whether a tap then a hold sprays |
| `sprayEnabled()` | whether SPRAY is allowed |
| `shotModeNow()` | the shot mode in use |
| `fixedSpeedRpm()` | the speed from `useSpeed(...)` |
| `trim()` | the speed trim, in percent |
| `atMotorLimit()` | whether the trimmed speed is cut to the motor's top speed ("(motor limit)"). More trim up does nothing |
| `lastTrimOffered()` | the trim used last time (since the robot app started), offered at INIT: "+3%", or "" if none |
| `allianceTrim()` | this alliance's trim from Tunables (`redTrimPercent`, `blueTrimPercent`), in percent |
| `speedMode()` | the [mode](../steps.md#modes) that sets the speed from the distance to the goal |
| `tableName()`, `tableNames()`, `tableField(name)` | the table in use; all tables; a table's field name |
| `table(name)` | a shot table as `Tunables.java` has it (null if none). The Shot Tuner starts from it |
| `speedRpm()` | `Tunables.Launcher.speedRpm`. The Shot Tuner starts from it |
| `distanceIn()`, `distanceSource()` | the distance to the goal; where it came from (camera or odometry) |
| `spinsAtStart()` | whether it spins up at ▶ |
| `hood()` | its [hood](hood.md) |
| `feeder()`, `feedWheel()` | its feeder; its feed wheel |
| `feedStopName()` | its feed stop's name |
| `feedStopPart()`, `feedStopMoveSec()` | its feed stop's catalog servo (null if Robot.java gives none); `feedStopMoveSec` as read at INIT |
| `feedTest(hold, release, moveSec, backupSec)` | for the Feed Stop Finder only: a [test shot](../../modules/finders.md#feedtest) with those numbers |
| `hasTimedFeeder()` | true for a CR servo or motor feeder with no feed stop, pushing parts or feed wheel: the Feed Time Finder is for it |
| `feedTimeTest(limitSec)` | for the Feed Time Finder only: a [test shot](../../modules/finders.md#feedtimetest) that times the feeder until the shot |
| `pushers()` | the parts that push the pieces in |
| `isEjecting()` | whether it ejects now |
| `part()` | the plain [launcher](../../modules/launcher.md#launcher) inside |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

In an auto: `.readyToLaunch("Ready", 3)` before the drive to the shooting spot, then
`.action(robot.launcher.fireAllAction())` ([what an auto can do](../../building-autos/01-what-an-auto-can-do.md)).

## Firing: shot modes, feed stop, piece path

Before each piece, the launcher picks how strict it is. You do not need to set it.

| Mode | Waits for the speed | Feed stop | When |
| --- | --- | --- | --- |
| CAREFUL | every piece: within `readyBandRpm` for 0.15 s, and every check | opens for each piece | a tap of the fire button; autonomous while there is time |
| QUICK | the first piece like CAREFUL; the next ones within `quickBandRpm`, or after `quickMaxWaitSec` | opens for each piece | a held fire button (without `continuousOnHold`); autonomous when CAREFUL will not fit before the park time |
| SPRAY | no: the wheel only has to turn at half its speed or more | stays open until the last piece | only with `sprayEnabled = true`: the last `sprayLastSec` of TeleOp, and autonomous when QUICK will not fit either |

In autonomous, the launcher counts the time to the park (`parkWithSeconds(...)`,
[keep time to park](../steps.md#keep-time-to-park)): the pieces still to fire x the time a piece takes in each
mode. It measures that time from the last volleys, or else estimates it. While it fires, the Driver Station says `Fire: QUICK (12 s left)`.
`robot.launcher.shotMode(ShotMode.CAREFUL)` sets a mode by hand (`null` = choose again).

### Continuous fire

A group of pieces goes fastest when the feeder does not stop between them. With `continuousOnHold = true`,
holding the fire button does that. It is off unless you write it, and it is **not proven on a real robot yet**.

**Safety:** the robot can turn by itself from the press to the end of the group. The feeder runs until you let
go or something below ends it. Let go of the button to stop.

| What you do | What the robot does |
| --- | --- |
| press | spins up and turns to the goal at once. It feeds nothing yet |
| let go before `holdSec` (0.3 seconds) | fires one piece, like a tap. The tap's own aim rule applies |
| hold | waits until it sees the goal, is aimed and the wheel is at speed. Then it feeds with no break |
| tap, then hold | SPRAY, as before (with `sprayEnabled`) |
| press in the last `sprayLastSec` of TeleOp | SPRAY, as before. A group that already started stays a group |

**It starts feeding only when all of these are true.** No time limit lets a bad aim through. Aim mode being on
does not let it through either. After 3 seconds it gives up and says what is wrong.

- The camera sees the goal now. The odometry backup does not count: a pose can drift.
- The distance is in the shot table, or in its [estimated part](#speeds-past-the-ends-of-the-table).
- The aim is inside `toleranceDeg`.
- The wheel is steady at its speed, and the hood and your other checks are ready.

When the feed starts, the robot keeps that table, distance, speed and aim offset to the end. The aim keeps
turning to the goal. The driver's turn stick wins.

**These end the group.** The feed stops in that loop. It never starts again by itself: press fire again.

| What ended it | What the screen says |
| --- | --- |
| you let go, pressed the off button, or stopped the robot | nothing: you asked for it |
| pause | the pause warning. Going on does not bring the group back |
| the button list or the tuning menu opened | <samp>Firing stopped: button list is open</samp>, <samp>Firing stopped: tuning menu is open</samp> |
| the camera's light test started | <samp>Firing stopped: camera is testing the light</samp> |
| an eject, or the intake runs backward | <samp>Firing stopped: pushing a piece out</samp>, <samp>Firing stopped: intake is running backward</samp> |
| another table, alliance or speed setting | <samp>Firing stopped: shot table changed</samp> (or the alliance, or the speed setting) |
| another fire command | <samp>Firing stopped: another shot was asked for</samp> |
| your code canceled the action | <samp>Firing stopped: action was canceled</samp> |
| the feeder jammed, or something else stopped it | <samp>Firing stopped: feeder is jammed</samp>, <samp>Firing stopped: feeder was turned off</samp> |
| the speed sensor failed (bang-bang) | <samp>Firing stopped: bad wheel speed reading</samp> |
| the wheel fell under half its speed | <samp>Firing stopped: wheel below half speed</samp> |
| the camera did not see the goal for more than 0.5 seconds (the odometry backup does not count) | <samp>Firing stopped: goal NOT seen</samp> |
| the aim was off for more than 0.3 seconds (`toleranceDeg` + `lockMarginDeg`) | <samp>Firing stopped: NOT aimed</samp> |
| the distance changed by more than 4 inches | <samp>Firing stopped: distance changed too much</samp> |
| `continuousMaxSec` passed | <samp>Firing stopped: time limit reached</samp> |

The screen's next line says what to do.

**With a feed stop:** the stop closes in the same loop as the feed stops. A ball that is in the stop at that
moment can be pinched. No robot with a feed stop has tried continuous fire yet: watch the first groups, and
tell us what you see.

**It needs:**

- a feeder that can run nonstop: `Indexer.crServo(...)` or `Indexer.motor(...)`. A push servo cannot. INIT says
  so, and a hold fires nothing.
- `speedFromDistance(camera)` or `speedFromOdometry()`, and a way to aim.

**It cannot count balls.** The wheel has no time to be "ready" between two pieces. So a group adds nothing to
the shot count, the firing stats or a `Pieces` counter's feeds. It also cannot see a jam on a CR servo.
Autonomous still fires one piece at a time.

### Feed stop

Add `.feedStop("feed_stop")` only if the robot has one.

- It goes to `feedStopHold` at ▶. It never moves in INIT: nothing moves before the match.
- It opens to `feedStopRelease` for a piece. It closes when the wheel sees the shot (its speed drops), or
  after `feedStopOpenSec` (0.5 s).
- The indexer and the pushing parts wait `feedStopMoveSec` (0.1 s) while it opens.
- In CAREFUL and QUICK it closes after each piece. Then a running intake cannot push a piece into a wheel
  that is not ready. In SPRAY it stays open until the last piece.
- A roller or belt indexer (a CR servo, a motor) stops when the stop closes.
- The launcher sends it a new position only when the position changes.
- Find `feedStopHold`, `feedStopRelease`, `feedStopMoveSec` and `feedStopOpenSec` with the **Feed Stop
  Finder** (in [Tune and check](../../tuning/01-tune-and-check.md#the-tune-and-check-menu)). For
  `feedStopMoveSec`, give the servo's part: `.feedStop("feed_stop", "gobilda-2000-speed")`.

### Piece path

Add `.pushedBy(intake)` only if a part must push the pieces into the wheel.

- For each piece, the intake runs in for at most `assistSec` (0.25 s), or until the wheel sees the shot. It
  does this even when the driver has it off. Then it goes back to what it was doing.
- **Add the intake before the launcher** in `define()` (`intake = add(...)` first): `pushedBy(intake)`
  needs it.
- When the driver pushes out, that still wins.
- A robot that needs no push leaves it out, and nothing extra runs. (Last season's intake ran 250 ms after
  each shot, always.)

```java
intake = add(Intake.motor("intake"));   // first: the launcher's pushedBy(...) needs it
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feedStop("feed_stop", "gobilda-2000-speed")   // only if the robot has one; the part is optional
        .pushedBy(intake));                // only if the intake must push the pieces in
public static double feedStopHold = 0.15, feedStopRelease = 0.55;   // Tunables.Launcher: examples, yours differ (Feed Stop Finder)
public static boolean sprayEnabled = true;                          // optional: SPRAY at the end
public static double redTrimPercent = 0, blueTrimPercent = 0;       // optional
// autonomous: .readyToLaunch("Ready", 3) before the drive to the shooting spot, then .action(robot.launcher.fireAllAction())
```

### Feed wheel

Add `.feedWheel(Indexer.crServo("feeder_wheel"))`, with a push arm as the `feeder`. The wheel moves the pieces
up to the arm. The arm pushes each one into the flywheel, then comes back. The wheel's numbers go in its
own section (`Tunables.FeederWheel`, [Indexer](indexer.md)). With `.feedWheelOutWith(intake)` it also runs
backward while the intake pushes out. Then a piece stuck between the wheel and the arm comes back out.
With no feed wheel, `.feedWheelOutWith(intake)` works on a feeder that spins (a CR servo or a motor) too: then the
feeder itself runs backward. A shot fired while the intake pushes out runs the feeder forward at once. The
piece may have backed off a little, so it may need a longer `feedSec`.

It runs in one of two ways, set with `alwaysOn`:

- **Only while firing** (`alwaysOn = false`, the default): for each piece, the wheel starts and runs for
  `stageSec` (0.2 s), so the piece gets to the arm. Then the arm pushes while the wheel still turns. The wheel
  stops `stopSec` (0.3 s) after the push starts. A `stopSec` below 0 stops it before the push; 0 stops it
  as the push starts. The next shot waits until the wheel stops.
- **Always on** (`alwaysOn = true`): the wheel runs all the time the launcher is spun up (not while it
  idles at `idleRpm`). So a piece is always at the arm. The arm pushes at once (no `stageSec`).

More:

- With a feed stop, the arm also waits for the stop to open (`feedStopMoveSec`).
- The launcher runs the feed wheel: the wheel's own `feedOne()` and `stop()` do nothing.
- A canceled fire stops the wheel (only while firing), and the arm never pushes.
- In the simulator, a push sends a piece only if the wheel ran right before it.

**A squeeze feeder** is a common kind. The feed wheel pinches the piece against pads on the arm. So the
arm's `rest` position also sets the gap the piece is squeezed through. When the gap is too wide, the piece
slips. When it is too tight, the piece jams, or the wheel stalls. Find it with the Servo Finder's smallest step (0.001) and a real piece. To draw it
in the simulator, give the arm `looks(Shape.arm()...angle("rest", 0).angle("push", 40))` (its angle at the
two positions; it tilts with the servo), and give the wheel a roller:
`looks(Shape.roller("gobilda-gecko-48-rex-15a")...axis("up"))`.

```java
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("feeder_arm").part("gobilda-2000-torque"))        // pushes up, comes back
        .feedWheel(Indexer.crServo("feeder_wheel").part("gobilda-2000-speed")));  // stages each piece
public static class FeederWheel { public static boolean alwaysOn = false; public static double stageSec = 0.2, stopSec = 0.3; }
```

### Firing stats and alliance trim

**Firing stats** are debug lines, in the PRACTICE profile only (at an event they are noise). For each piece:
its mode, the speed when it was fed, the drop, the time back to ready, and the time since the last piece.
Then one line per volley with the average and the worst ([ShotStats](../../modules/launcher.md#shotstats)).
Use them to set `quickBandRpm`, `quickMaxWaitSec` and `shotIntervalSec`.

**Alliance trim:** the two field halves are not always the same. So `redTrimPercent` and `blueTrimPercent`
(0 at first) make every shot on that alliance a little faster or slower, on top of the speed trim. You keep
one table and two small trims.

## Speeds past the ends of the table

Without this, a goal farther than your last row gets the last row's speed, and the aim does not lock there.
`extendTableEnds = true` continues the line of the two rows at each end:

- The screen says <samp>estimated</samp> outside your tested rows. The aim can lock there.
- The guess never goes past `extendMaxRpm`. Past that point the screen says <samp>estimated, speed limited</samp>
  and <samp>Drive closer to the goal.</samp> The aim does not lock there.
- Closer than your first row, a guess under half of the first row's speed is too slow to trust. The aim does
  not lock there.
- A hood keeps the angle of the end row.
- An end stays flat when its speed drops as the distance grows, or the table has one row. INIT says so.
- Two rows with the same distance stop INIT. Delete one.

Delete any end rows that you made up and did not shoot. Do it in the same change that turns this on.

## Speed control: PID or bang-bang

`speedControl = "PID"` is the normal one: the hub holds the speed. `speedControl = "BANG_BANG"` is for trying
a faster recovery after each shot. It is **not proven on a real robot yet**. Each keeps its own numbers.

| The wheel is | Bang-bang gives |
| --- | --- |
| well below its speed | full power (`bangMaxPower`). From rest the power eases in |
| near its speed | holding power from `bangKs` and `bangKv`, corrected for the battery |
| above its speed | no power. It coasts. It never reverses |

- **The starting `bangKv` comes from your `kF`** when you wrote a `kF` and no `bangKv`. Your `kF` was found on
  your wheel, so it is a better start than the catalog. The math:
  `bangKv` = `kF` x counts per turn / 60 / 32767 x `nominalVolts`. For example, `kF` 12.08 with 28 counts and
  13.0 V gives 0.002237. With no `nominalVolts` line, the math uses the library's starting value.
- **With no written `kF`, the starting `bangKv` is a guess** from the motor's catalog speed.
- **Both starts are not tuned for bang-bang.** INIT says which one it uses. The wheel may not hold the exact
  speed until the [Flywheel Tuner](../../tuning/06-flywheel-tuner.md) finds your numbers. A `bangKv` that you
  write always wins. PID does not use any of this.
- **It needs a fast program.** After a gap over 0.08 seconds it skips full power once, and the screen says
  <samp>Program too slow</samp>. A program that is completely stuck cannot stop the motor.
- **It watches the speed sensor.** Power with no speed for 0.5 seconds, a backward speed or a bad number turns
  the power off and stops feeding. Holding fire does not clear this. Fix the wires, then switch the flywheel
  off and on. If it already went off by itself after your shot, one press to switch it on is enough. This check
  cannot catch a wrong speed that looks believable.
- **An eject waits longer,** up to 10 seconds, because the wheel only coasts down.

To go back, write `public static String speedControl = "PID";`.

## Speed trim and battery compensation

**Speed trim** is a fix during a match, when every shot is a bit short or long (a worn wheel, softer
balls).

- `trimBy(+2)` makes every shot speed 2% faster than the table (or `speedRpm`) says.
- It starts at 0 at every INIT, and it is the same for every shot table.
- It stops at +/- `trimMaxPercent` (15), and at the motor's top speed (`(motor limit)`).
- It is the launcher's first line on the Driver Station: `Speed trim: +3%  READY`.
- The launcher keeps the trim used last time until the robot app restarts. INIT offers it:
  `Trim last time: +3% (double-tap D-pad up after ▶ to use it)`. The launcher never applies it by itself.
- The Shot Tuner never uses it, so it records clean tables.

With `kF` set (and battery compensation on, below, or a charged battery), a trim you need match after match
means the wheel or the balls changed: the table is off. Put the trim into the table, then trim back to 0.
For example, for a +3% trim all season, multiply every speed by 1.03. So `{48, 2550}` becomes `{48, 2627}` (2550 x 1.03 = 2626.5), in
every row of every table. Or tune the table again with the Shot Tuner.

**Battery compensation** is OFF by default. `batteryCompensation = true` with `kF` set turns it on.

- The F gain gives the same POWER for a speed, and the same power spins the wheel slower on a weaker
  battery. The hub's I gain makes up for that only slowly.
- So every 0.25 s the launcher reads the battery and gives the hub F x (`nominalVolts` / volts). Tuned at
  12.6 V, at 11.5 V it is F x 1.10. Then the wheel gets back to speed after a shot as fast at 11.5 V as on a
  full battery.
- "volts" is the highest reading of the last second. So a dip while the drive pulls hard does not raise F.
- The library asks the hubs on its helper thread (`Devices.batteryReader()`), so the loop never waits.
- Under 11.5 V the Driver Station warns.
- `nominalVolts` comes from the Flywheel Finder. Without it the launcher uses 12.8 V
  (`Flywheel.DEFAULT_NOMINAL_VOLTS`, a charged battery under load).
- Details: [Flywheel](../../modules/launcher.md#battery-compensation).

> **Battery compensation: OFF by default. Nobody has proven it on a real robot yet.** Testing teams: please
> try it and tell us what you see.
> The RPM control already fixes a low battery by itself, but slowly (the small I gain). Compensation
> only makes that faster: it helps the first shot after spin-up and quick shots in a row. It cannot help
> when the battery is too low for the speed you ask (the motor is already at full power).
> - **Turn it on:** `public static boolean batteryCompensation = true;` in `Tunables.Launcher` (with `kF`
>   set, and `nominalVolts` from the Flywheel Finder; without it INIT says it assumes 12.8 V).
> - **Test it on your robot:** with a battery at about 11.5 V, fire 4 shots fast and watch the
>   launcher's measured RPM against its target on the Driver Station. Do it with compensation off, then
>   on. If you see no difference, leave it off. If the wheel overshoots after a battery swap, turn it
>   off and tell us.

```java
public static double kP = 1.17, kI = 0.117, kD = 0, kF = 11.70, nominalVolts = 12.6;   // Tunables.Launcher
public static double trimStepPercent = 1, trimMaxPercent = 15;   // optional (the defaults)
// a button: g2.y.press(() -> robot.launcher.trimBy(+3));   an action: .then(() -> r.launcher.setTrim(+5))
```

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.
- Its feeder (an indexer in the launcher) needs no button: RB feeds.

<!-- recipe: buttons-launcher -->
```java title="Controls.java"
robot.launcher.spinOn(g2.lb);
robot.launcher.fireOn(g2.rb);
robot.launcher.speedOn(g2.dpadUp, g2.dpadDown);
robot.launcher.tableOn(g2.dpadRight);
```

| Line | What the button does |
| --- | --- |
| `spinOn(g2.lb)` | spins up / down |
| `fireOn(g2.rb)` | a tap fires one (more taps: more shots). Held, it keeps firing (with `continuousOnHold`: one aimed group, [continuous fire](#continuous-fire)). With SPRAY on, a tap then a hold sprays. It calls `fireWhileHeld()`, then `ceaseFire()` on release |
| `speedOn(g2.dpadUp, g2.dpadDown)` | a tap is one speed step. Held, one more step after 0.5 s, then every 0.25 s (4 a second). With a shot table, the steps trim the speed in percent (`trimStepPercent`); with a fixed speed, +/- `stepRpm`. A double-tap of `down` goes back to no trim (and sets the hood trim back to 0). When last match's trim is offered at INIT, a double-tap of `up` uses it |
| `tableOn(g2.dpadRight)` | picks the next shot table. It works only with two tables or more |

Each one puts its own words on the button map. Each works only when the button is free.

---

**Look it up:** [`Launcher` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Launcher.html)
(every method). **The other parts:** [Reference](../README.md).

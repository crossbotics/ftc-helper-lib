# Indexer

A part that feeds one game piece at a time: a servo arm, a CR servo wheel, or a motor. Usually it is the
feeder of a [Launcher](launcher.md) (`.feeder(...)`). Learn it step by step:
[Robot.java, add a part](../../five-files/01-robot-java.md#launcher).

## In Robot.java

```java title="Robot.java"
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));   // in a launcher (usual)
feeder = add(Indexer.servo("feeder").part("gobilda-2000-speed"));       // on its own
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Indexer.servo(name)` | **yes**, one of the three | a servo arm that pushes each piece, then comes back | - |
| `Indexer.crServo(name)` | **yes**, one of the three | a CR servo that feeds for `feedSec` per piece | - |
| `Indexer.motor(name)` | **yes**, one of the three | a motor that feeds for `feedSec` per piece | - |
| `.part(id)` | recommended | which servo or motor it is, from the [parts catalog](../../guides/parts-catalog.md). The library takes a servo indexer's `pushSec` and `returnSec` from it | `pushSec` 0.25 |
| `.reversed()` | optional | it turns the other way | - |
| `.alsoServoReversed(name)` | optional, servo only | a second arm on the other side. The two pinch together behind the piece and push it in ([two servos on one part](claw.md)) | one servo |
| `.alsoServo(name)` | optional, servo only | a second servo mounted facing the same way (it turns the same way) | one servo |
| `.fullRange()` | optional, servo only | the servo's whole turn ([full range](claw.md)). Measure `rest` and `push` again after you turn it on | the normal range |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

A servo indexer goes to `rest` at ▶. A [feed wheel](launcher.md#feed-wheel) is an indexer too:
`.feedWheel(Indexer.crServo("feeder_wheel"))` on the launcher.

## In Tunables.java

<!-- tunables: Indexer -->
The section's name is the device's name in CamelCase: `Indexer.servo("feeder")` reads **`Feeder`**,
`windmill` reads `Windmill` (not `Indexer`).

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE`
means you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

A servo indexer:

<!-- recipe: tunables-indexer -->
```java title="Tunables.java"
public static class Indexer {                                   // "indexer" in the configuration
    public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
    public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
    public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
    public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `rest` | a servo indexer: where it waits | 0 to 1 | 0.0 | yes, at once | Servo Finder | it blocks the pieces |
| `push` | a servo indexer: where it pushes a piece into the wheel | 0 to 1 | 0.5 | yes, at once | Servo Finder | it does not push pieces far enough |
| `pushStep` | a servo indexer: one step of `adjustPush(...)` (a D-pad trim of `push` in TeleOp) | servo position | 0.01 | yes, at once | test | a step is too big or too small |
| `pushSec` | a servo indexer: time to push | seconds | 0.25 (with `.part(...)`: the servo's catalog time from `rest` to `push`, at least 0.25) | yes, at once | test | it comes back before the piece is gone (more) |
| `alsoTrim` | a servo indexer with a second servo (`alsoServoReversed`): a raw position the library adds for it, so the two arms meet | raw position, -0.2 to 0.2 | 0 | yes, at the next move | test: watch both arms | one arm pushes further than the other |
| `returnSec` | a servo indexer: time to come back | seconds | 0.25 (with `.part(...)`: the same as `pushSec`) | yes, at once | test | the next piece jams on it (more) |
| `power` | a motor or CR servo indexer: power while it feeds (- = the other way) | -1 to 1 | 1.0 | yes, at once | test | it feeds pieces too hard or too softly |
| `feedSec` | a motor or CR servo indexer: time for one piece | seconds | 0.4 | yes, at once | test | it feeds two (less) or none (more) |
| `alwaysOn` | a feed wheel (`Launcher.feedWheel(...)`): true = it runs all the time the launcher is spun up (not while it idles); false = only while firing | true / false | false | no (at INIT) | test | the pieces wait too long for the wheel (true) or it uses battery for nothing (false) |
| `stageSec` | a feed wheel, only while firing: it runs this long before the arm pushes (the piece gets to the arm; with a feed stop, the arm also waits for it to open) | seconds | 0.2 | yes, at once | test | the arm pushes before the piece is there (more) |
| `stopSec` | a feed wheel, only while firing: it stops this long after the arm starts to push. Below 0 = before the push; 0 = as the push starts | seconds, -2 to 2 | 0.3 | yes, at once | test | it pushes the next piece in behind (less) or the piece stops on the arm (more) |

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.feeder.feedOne()`. In a launcher, use the
launcher's `fire()` instead: the launcher runs its indexer.

| Command | What it does |
| --- | --- |
| `feedOne()` | feeds one piece |
| `clearJam()` | clears a jam |
| `adjustPush(steps)` | a servo indexer: moves `push` by steps x `pushStep` (a D-pad trim in TeleOp) |
| `onFeed(runnable)` | runs your code each time a feed really starts |
| `stop()` | stops |

| Question | What it tells you |
| --- | --- |
| `isBusy()` | whether it still moves: a feed, or the arm coming back |
| `isFeeding()` | whether a feed runs: true from its start until it ends (normally, jammed or stopped) |
| `isJammed()` | whether it is jammed |
| `pushNow()` | the `push` position in use (with the trim) |
| `isServo()`, `isReversed()` | whether it is a servo indexer; whether it turns the other way |
| `secondServoName()` | the second servo's name |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

**With [Pieces](pieces.md):** the counter counts the piece out only once it has gone: its ready sensor sees
it leave, or the feed ends without a jam. When a feed stops part way or jams, the count stays as it was.

## Buttons to add

An indexer in a launcher needs no button: the launcher's RB feeds. An indexer on its own needs a line in
`Controls.java`:

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

([How to read a button line](../controls.md#reading-a-button-line).)

<!-- recipe: buttons-indexer -->
```java title="Controls.java"
g2.rb.press(robot.feeder::feedOne).says("feed one");   // an indexer on its own, not in a launcher
```

---

**Look it up:** [`Indexer` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Indexer.html)
(every method). **The other parts:** [Reference](../README.md).

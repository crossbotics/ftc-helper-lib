# PathFollower and PathAt

A **PathFollower** is a small plug that a path-following add-on fills in. With it, the core library never
needs that other library. The [pedro module](../../modules/pedro.md) is one add-on. **PathAt** starts things
along a path while the robot drives. Learn it step by step:
[Follow a curved path: Pedro](../../building-autos/05-pedro-paths.md).

## In Robot.java

```java title="Robot.java"
follower(Pedro.follower(PedroConstants.foresight));   // in define()
```

Then `follow(label, path)` steps in autos ([Steps for autos](../steps.md)).

## What it does

- The library updates it every loop, right after the odometry.
- It drives the wheels only while a path runs.

## Commands

A path-following add-on must give these. Your autos use them through `follow` steps.

| Command | What it does |
| --- | --- |
| `follow(path, mirrorX)` | starts a path (mirrored for RED when routes are mirrored) |
| `cancel()` | stops now |
| `check(path)` | at INIT: stops with an error (throws) when it cannot follow this kind of path |

| Question | What it tells you |
| --- | --- |
| `isBusy()` | whether the path still runs |
| `remainingIn()` | inches left on the WHOLE path (all its parts), for the "stuck" check. Because it counts the whole path, `PathAt.inchesLeft` and `fraction` are right |
| `isReady()` | whether its pose source is ready. While it is not, the step ends `NO_ODOMETRY` |
| `totalIn()` | optional (it has a default): the path's length |
| `partNow()` | optional: the part it drives now (from 1) |
| `parts(path)` | optional: how many parts a path has |

[PathAt](#pathat) uses the three optional ones. When an add-on does not give them, PathAt takes the distance
left at the start as the length, and `PathAt.part(n)` fires at the end.

## PathAt

PathAt starts things ALONG a `follow` (or `driveTo`) step, while the robot keeps driving. Each one fires
once. More: [pedro module, Do things along the path](../../modules/pedro.md#do-things-along-the-path).

| Command | Fires |
| --- | --- |
| `PathAt.inchesDone(in, label, command)` | after `in` inches of the path |
| `PathAt.inchesLeft(in, label, command)` | when `in` inches are left |
| `PathAt.fraction(f, label, command)` | when that much of the WHOLE path is done (0.5 = halfway, all parts counted) |
| `PathAt.part(n, label, command)` | where part `n` of a `then` path starts (INIT stops if the path has fewer parts) |
| `PathAt.near(x, y, withinIn, label, command)` | when the robot is within `withinIn` of a field point (mirrored for RED) |

For example `PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp)` (your own part names).

Each also takes an [Action](../steps.md#action) from your `Actions.java` instead of a label and a command:
`PathAt.inchesLeft(12, Actions.scoreHigh(robot))`, or any action you wrote. `name()` gives its label.

---

**Look it up:** [`PathFollower`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/PathFollower.html)
and [`PathAt`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/PathAt.html) in the
Javadoc (every method). **The other parts:** [Reference](../README.md).

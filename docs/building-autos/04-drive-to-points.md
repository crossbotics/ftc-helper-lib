# Drive to points on the field

Drive to points on the field with `driveTo`, and go around field parts with `routeTo`.

**You need:** odometry: the `odometry(...)` line in `Robot.java`, and the Odometry Tuner passed
([Add odometry](../add-to-robot/09-odometry.md)).

**Odometry** is a sensor set (a Pinpoint, an OTOS, or dead wheels) that tracks where the robot is on the
field, all the time. With it, a step can say **where to go** on the field, not only how far to drive.

## Drives and points: the difference

| | `forward`, `turn`, ... (pages 1 to 3) | `driveTo` (this page) |
| --- | --- | --- |
| You say | "24 in forward from here" | "go to x -30, y -30, facing 45" |
| On the way | it does not correct | it checks where the robot is, and corrects all the time |
| After a bump or wheel slip | every later step is off | the robot still ends at the point |
| You change one step | the steps after it move too | the other points stay where they are |
| Needs | any drive | odometry, and a **mecanum** drive |

## Field numbers

Every point uses the same **field numbers** as the start spot: x, y and heading, in inches and degrees,
seen from your drivers' wall, written for BLUE. The picture and the words: [page 1, field
numbers](01-what-an-auto-can-do.md#field-numbers).

**Give the spot** in every auto that uses `driveTo`: `start(note, x, y, heading)`. The odometry starts
there. Without a spot, the odometry starts at (0, 0), and every point is wrong.

**Find the numbers:** in the simulator, drag the robot on the field map before ▶. The numbers are under the
map. Or use the auto builder ([page 7](07-auto-builder-odometry-and-pedro.md)): it writes them for you.

## Drive to a point: driveTo

The odometry robot's auto, a compiled file in the library:
[`odometrybot/autos/ToTheGoal.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/autos/ToTheGoal.java).

```java
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .run("Intake on", robot.intake::in)
         .driveTo("To the goal", -30, -30, 45, 0.6)    // x, y, heading, power
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         .driveTo("Park", -56, -36, 0, 0.7);
```

- `driveTo(name, x, y, heading, power)`: drives and turns at the same time, to the point and the heading.
  The step is done when the robot is within 1 inch and 2 degrees.
- `driveTo(name, x, y, heading, power, inches)`: your own "close enough", in inches. Use a larger number for a
  point the robot only drives through.

**If a driveTo does not finish** (stuck, or out of time), the next steps still run, from where the robot is.
Read the report after each practice run.

**Its heading is the field's.** `turnTo`'s 0 is the way the robot faced at the start. `driveTo`'s 0 is
"away from your wall". They are the same only when the robot starts facing the field. With odometry, give
headings in `driveTo`.

**Red and blue** work like the start spot ([page 2](02-put-steps-together.md#red-and-blue)):

- `mirrorForRed()` true: RED flips x and the heading. `(-30, -30, 45)` becomes `(30, -30, -45)`.
- `mirrorForRed()` false (BIOBUZZ): RED uses the same numbers, seen from the red wall.

## Go around field parts: routeTo

A `driveTo` is one straight line. When the park time comes, the running step stops, wherever the robot is.
A straight line home from there can go through a field part.

So list the field parts in `Robot.java`, after the `odometry(...)` line:

```java
fieldMap(FieldMap.margin(13)   // needs: import com.crossbotics.ftchelper.drive.FieldMap;
        .keepOut("HIVE frame, near", -19.5, -24.8, 19.5, -15.4)
        .keepOut("HIVE frame, far", -19.5, 15.4, 19.5, 24.8));
```

- `keepOut(name, x1, y1, x2, y2)`: a box, from two opposite corners, in field numbers.
- `margin(13)`: the robot's center stays this far from every box, in inches.

Then write `routeTo` where you wrote `driveTo`. It finds its way around the boxes when the step starts:

```java
         .parkWithSeconds(6)
         .routeTo("Park", -56, -36, 0, 0.8);
```

The whole file: [`odometrybot/autos/ParkAround.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/autos/ParkAround.java).
The auto builder does not make `routeTo` steps yet: write them in the file.

## Done when

- Your auto has a start spot (`start(note, x, y, heading)`).
- It drives to a field point with `driveTo`, in the simulator, as BLUE and as RED.
- The report says `done` for every point.

## Problems

| Problem | Why, and the fix |
| --- | --- |
| `needs odometry: add an odometry(...) line` | `Robot.java` has no odometry line ([lesson 7](../tutorials/start/07-odometry.md)). |
| The report says `skipped: Tunables.Odometry.checked is false` | On a real robot, `driveTo` waits for the Odometry Tuner. Run its loop test, then set `checked = true`. The simulator runs them anyway. |
| `needs a mecanum drive` | `driveTo` slides sideways. On a tank drive, use `turnTo`, then `forward`. |
| Every point is off by the same amount | The start spot in `start(...)` is not where the robot starts on the field, or it is missing. |
| `NO_ODOMETRY` in the report | The odometry stopped answering on the way. Check its cable. |
| `STALLED` near a field part | The straight line goes through it. Use `routeTo` with a `fieldMap`. |
| RED goes to the wrong side | `mirrorForRed()` is wrong for this field. |

**What each word in the report means** (`done`, `STALLED`, ...): [Match day](09-match-day.md#after-the-match-read-the-report).


---

**Next:** [Add Pedro paths](05-pedro-paths.md): curved paths, with no stop between parts.

**Look it up:** [driveTo](../modules/odometry.md#driveto), [keep-out boxes and routes](../modules/odometry.md#keep-out-boxes-and-routes).
**Go deeper:** [the odometry module](../modules/odometry.md).

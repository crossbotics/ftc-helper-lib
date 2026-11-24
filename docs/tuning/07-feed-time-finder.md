# Feed Time Finder

Find how long the feeder must run to get one piece into the wheel: `feedSec`.

**In Tune and check:** yes, when the launcher's feeder is a CR servo or a motor, with no feed stop, no
pushing parts (`.pushedBy(...)`) and no feed wheel.
**You need:** the launcher and its feeder in Robot.java, 5 pieces or more, and safety glasses for the test shots.

A robot with a feed stop uses the [Feed Stop Finder](07-feed-stop-finder.md) instead. Its test shots time
the same thing, for `feedStopOpenSec`. A robot with pushing parts or a feed wheel gets neither finder for
`feedSec`: tune it by hand ([fix it by what you see](17-fix-by-symptom.md)).

## Why test shots

A CR servo or motor feeder runs for `feedSec` per piece, then stops. The time a piece needs changes with
where it sits. A piece near the wheel gets there fast. A piece far back takes longer. `feedSec` must be long
enough for the piece that starts farthest back.

One test shot does not tell you that. Fire several, from every place a piece can sit in a match. The
finder keeps each time and gives `feedSec` from the longest.

## Get to the Feed Time Finder

1. On the Driver Hub, open the TeleOp list and pick **Tune and check** (group "Finders"). Press **INIT**.
2. The menu shows. Press **D-pad down** until the arrow (`>`) is on **Feed Time Finder**, then press **A**.
3. The Feed Time Finder's INIT screen shows. Nothing moves yet. Press **▶** (start) on the Driver Hub.

## Steps

1. Wear safety glasses, and keep everybody out of the line of fire.
2. Load one piece where a piece sits in a match.
3. Press **LB**. The wheel spins up to your `speedRpm`. Keep your fingers away from the wheels.
4. When the screen says `ready`, press **RB**. The feeder runs at its `power`. The finder times the piece
   from the start of the feed to the shot (the wheel's speed drops). The feeder stops at the shot.
5. Do it 5 times or more. Load each piece in a different place, and the farthest back place more than
   once. The screen shows the times:

   ```
   Test shots : 0.31, 0.42, 0.28, 0.45, 0.33 s  (5)
   Longest : 0.45 s. feedSec = that + 0.1 s
   feedSec in use now : 0.40 s
   ```

6. After 5 shots, the screen says DONE and shows `public static double feedSec = 0.55;` and its class (your
   feeder's). There is no line to copy before 5 shots. Copy it when you tested every place.
7. Press **LB** to stop the wheel.

**Why the extra 0.1 s:** a low battery and a worn wheel make the feed slower.

## When the finder warns

- **`No shot seen in 3 s`:** the finder saw no drop in the wheel's speed. When no piece came out, check that a piece was loaded, and that
  the feeder turns the right way (its `power` sign, or `.reversed()` in Robot.java). This test shot does not
  count. A heavy wheel that slows by less than `shotDropRpm` (150 by default) gives this warning too, even when the
  piece fired: make `shotDropRpm` smaller.
- **`Times differ by ... s: 2 may feed at once.`:** a `feedSec` long enough for the slowest piece can push the next piece in
  too, when it sits close. Set `stopOnShot = true` in the feeder's section. Each feed then stops at the
  shot, and `feedSec` is only the time limit
  ([stop on the shot](../reference/parts/indexer.md#in-robotjava)). With `stopOnShot = true`, this warning
  does not show.
- **`Test shot stopped part way`:** the wheel went off (LB, help, or STOP) before the shot. That test shot
  does not count.
- **`Almost 3 s: feedSec cannot be longer.`:** `feedSec` cannot be more than 3 s. Check the feeder's `power`, and what slows
  the piece.

## Fix a mistake

- **A bad test shot** (no piece, or two pieces loaded): press **X**. It clears all the times. Then do the test
  shots again.
- A test shot that saw no shot does not count. Its time is not used.

**Start over:** press STOP, then INIT, and open the tool again. It starts empty. The lines saved at STOP stay
in the results file, the newest at the end.

## Put the numbers in your code

The robot uses `Tunables.java`, not the finder's screen. Take a photo of the screen, and type the line into
the file. Or copy it from the [results file](01-tune-and-check.md#get-the-results-file-on-the-laptop):

| The finder shows | Put it in |
| --- | --- |
| `Open Tunables.java, find class Feeder.` and `public static double feedSec = 0.55;` | your feeder's section (the name in its `.tunables("...")`, or the feeder's own name) |

## Done when

- `feedSec` is in `Tunables.java`.
- In TeleOp, each press of fire sends one piece, from every place a piece can sit.

---

**Next:** [Drive Tuner: wheel size and strafe correction](08-drive-tuner.md).

**Look it up:** [Indexer](../reference/parts/indexer.md), [Launcher](../reference/parts/launcher.md).

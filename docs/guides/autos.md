# Several autonomous modes, and choosing the right one

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** teams with the [robot in five files](../tutorials/start/01-robot-in-five-files.md) that need
more than one autonomous. **You need:** one autonomous that works
([lesson 6](../tutorials/start/06-first-autonomous.md)). Every step method is in
[the autonomous builder](autonomous-builder.md); this page is about having **many** autos and picking
the right one at the field.

**Why this matters.** At an event you play with a new partner every match. Sometimes you start near the
goal, sometimes far. Sometimes your partner needs the space you usually drive through. A team that can
choose its plan in a few seconds scores more. A team that runs the wrong plan scores nothing, or hits its
partner. So: few autos, clear names, shared steps, and a checklist.

Contents: [how autos show up](#how-autos-show-up-on-the-driver-station) ·
[what the INIT menu can choose](#what-the-init-menu-can-choose) ·
[new OpMode or INIT choice?](#a-new-opmode-or-an-init-choice) ·
[shared steps](#build-them-from-shared-steps) · [names and groups](#names-and-groups) ·
[example 1: two OpModes](#example-1-two-opmodes-from-the-same-pieces) ·
[example 2: one OpMode with a menu](#example-2-one-opmode-the-drivers-choose-in-init) ·
[example 3: near and far](#example-3-near-and-far-starts) ·
[test in the simulator](#test-each-one-in-the-simulator) · [match day](#match-day)

The examples are one tested file:
[`ChooseAutoRecipes.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook/ChooseAutoRecipes.java).
A simulator test INITs every autonomous in it, and checks that the INIT menu moves the robot and picks
the steps.

## How autos show up on the Driver Station

There are two ways to offer several plans. You can mix them.

| | **One OpMode per plan** | **One OpMode with an INIT menu** |
| --- | --- | --- |
| What the drivers see | one line per plan in the Autonomous list | one line; after INIT, a menu on the screen |
| How they choose | they pick a name from the list | they press INIT, then use the D-pad |
| Good for | plans that are really different | small changes to one plan |
| Risk | too many names: someone picks the wrong one | someone forgets to change a menu line |

**One OpMode** is one Java class with `@Autonomous(...)` on it. Each class is one line in the
Driver Station's Autonomous list. `@Disabled` hides a class from the list (handy for old autos you want
to keep).

**The INIT menu** comes free with every `Auto`. After INIT, the Driver Station shows it. The drivers
use gamepad 1: D-pad up/down chooses a line, D-pad left/right changes it. Then they press START.

## What the INIT menu can choose

These are all the lines today, in the order they appear:

| Line | Where it comes from | Values |
| --- | --- | --- |
| `Alliance` | always there | BLUE or RED. **X** = BLUE and **B** = RED, from any line. It starts at the alliance of the last autonomous. |
| `Start delay` | always there | 0 to 10 s in 1 s steps. Change the top with `maxStartDelaySec()`. The delay waits before the first step. |
| your choice lines | `menuChoice(label, options...)` | the options you list. **The first one is chosen at INIT.** |
| your yes/no lines | `menuYesNo(label, startYes)` | YES or NO, starting at `startYes` |
| `Profile` | always there (last) | PRACTICE or COMPETITION. Saved on the hub. |

Your choice lines can choose:

- **which steps run** (the route, the plan, how many pieces): `onlyIf(label, option) ... end()`, or plain
  Java with `s.choice(label)` and `s.isYes(label)`;
- **where the robot starts** (the note, the spot and the heading): a `start(...)` inside an `onlyIf`.
  The "Place robot" note and the simulator's robot change as soon as the drivers change the line;
- **the park time**: a `parkWithSeconds(...)` inside an `onlyIf`.

**Why does the start follow the menu?** The list of steps is read again in INIT each time a menu line
changes (no step runs then). So the note the drivers read is always the note for the start they chose.
With odometry, the robot's position also starts at that spot.

The menu cannot ask for a number (like "drive 30 inches"). Put numbers in `Tunables.Auto` and change them
in the tuning menu ([lesson 6](../tutorials/start/06-first-autonomous.md#3-route-numbers-live-in-tunablesauto)).

## A new OpMode or an INIT choice?

| The difference between the plans | Use | Why |
| --- | --- | --- |
| Alliance (red or blue) | nothing: it is in the menu | the route is mirrored (or not) for you ([red and blue](autonomous-builder.md#1-placing-the-robot)) |
| Wait for a partner at the start | nothing: `Start delay` | already in the menu |
| Wait for a partner in the middle | `menuYesNo` + `onlyIf` | one line to change |
| Park near or far, leave or stay | `menuChoice` + `onlyIf` | small change, the rest is the same |
| Score 1 piece or 2 (more cycles) | `menuChoice` (or two OpModes) | same start, same opening |
| A different start spot, same idea | `menuChoice` for the start | the note on the screen says where to place the robot |
| A completely different route, nothing shared | a new OpMode | a menu full of branches is hard to read and to test |
| A test or practice routine (drive a square, tune a path) | a new OpMode, in its own group | it must never be run in a match by mistake |
| A plan you have never tested | nothing yet | test it first; untested plans do not go on the Driver Station |

A good rule: **a menu line for anything the drivers decide at the field** (with this partner, in this
match). **A new OpMode for anything the programmers decide at the pits.**

Keep the number of choices small. Two or three menu lines are fine. Six lines means many combinations,
and you cannot test them all.

## Build them from shared steps

Never copy a route into a second file. When you fix a number in one copy, the other copy keeps the bug.
Instead, write each **piece** of a route once, as a method, and build every autonomous from the pieces.

Make a new file `Routes.java` in your `autos` folder. Each method takes the step list `s` and your
`robot`, adds its steps, and hands the list back:

<!-- recipe: autos-routes -->
```java
/** The pieces every autonomous is made from (Routes.java). Write each piece once. */
public static class Routes {
    /** Score the piece the robot starts with: hold it, drive to the goal, score. */
    public static Steps scorePreload(Steps s, Robot robot) {
        return s.run("Hold the preload", robot.claw::close)
                .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** One more piece: to the pieces, grab one, back to the goal, score. */
    public static Steps cycle(Steps s, Robot robot) {
        return s.move("To the pieces", Tunables.Auto.pickupInches, 270, 0.6)   // 270 = left
                .run("Grab", robot.claw::close)
                .waitSeconds("Let the claw close", 0.3)
                .move("Back to the goal", Tunables.Auto.pickupInches, 90, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** The park. It keeps the last 3 seconds for itself, so it always runs. */
    public static Steps park(Steps s) {
        return s.parkWithSeconds(3)
                .move("Park", Tunables.Auto.parkInches, 90, 0.7);
    }
}
```

In your own `Routes.java`, write `public class Routes {` (no `static`: that word is only there because
the example lives inside another class), and add the `package` and `import` lines like in
`ScoreAndPark.java` ([lesson 6](../tutorials/start/06-first-autonomous.md#2-the-file-line-by-line)).

- `public static Steps scorePreload(Steps s, Robot robot)`: a method anyone can call (`public`), with no
  object needed (`static`). It returns the list, so you could chain more steps after it.
- **The numbers stay in `Tunables.Auto`.** Every autonomous that uses `scorePreload` uses the same
  `toGoalInches`. Tune it once, and every auto gets it.
- **Actions are shared too.** `Actions.scoreHigh(robot)` is the same action the TeleOp button runs
  ([lesson 5](../tutorials/start/05-actions.md)).

**Parameters: one piece, many versions.** When two routes differ only by a number, give the method a
parameter instead of writing two methods:

```java
/** Score the preload, then "cycles" more pieces (0, 1 or 2). */
public static Steps scoreMany(Steps s, Robot robot, int cycles) {
    scorePreload(s, robot);
    for (int i = 0; i < cycles; i++) cycle(s, robot);   // the same piece, "cycles" times
    return s;
}
```

**Mirroring for the alliance is already done.** Write every piece for BLUE. On RED, moves and turns are
mirrored for you (or not, on a field turned half a turn like BIOBUZZ: `mirrorForRed()` false). Do not
write a red copy of a piece. If one thing really is different on RED (a different AprilTag id), read the
alliance with `alliance()` inside the autonomous, or with `alliance().pick(blueThing, redThing)`.

## Names and groups

The drivers read the Autonomous list in a hurry, often on a small screen. Make the right line easy to
find.

```java
@Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Main TeleOp")
```

- **Start the name with the start spot**, then the plan: `Near: Preload + Park`,
  `Far: Preload + Park`. The drivers first look at where the robot is, then at the plan.
- **Say what it scores, not how the code works.** `Near: Preload + Cycle + Park` is better than
  `AutoV3Fixed`.
- **No alliance in the name.** One OpMode works for both alliances (the menu has the alliance). Two
  files `Red Near` and `Blue Near` are twice the work, and one day they will not match.
- **`group`** puts OpModes together. OpModes with the same group are listed together on the Driver
  Station; OpModes with no group come first. Use `group = "Autos"` for match autos and
  `group = "Test"` for test routines, so a test is never picked by mistake. (Check how your Driver
  Station sorts the list once, and name things so the match autos are easy to find.)
- **`preselectTeleOp`** must be your TeleOp's exact name. When autonomous ends, the Driver Station has
  the TeleOp chosen, so the drivers only press INIT.
- **Hide old autos** with `@Disabled` (or delete them; git keeps the old version, see
  [Git and GitHub](github.md)). A short list is a safe list.
- **The "Place robot" note** starts with the start spot in CAPITALS: `"NEAR: against our wall, 2nd tile
  left of centre, facing the field"`. The drivers check the note against the robot before START.

## Example 1: two OpModes from the same pieces

Two plans from the same start: "score the preload and park" (safe), and "score the preload, one more
piece, and park" (more points, more risk). Two files, each only a few lines, because the pieces are in
`Routes.java`:

<!-- recipe: autos-two-opmodes -->
```java
/** NearPreloadPark.java: score the preload, then park. */
@Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public static class NearPreloadPark extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ: the field is turned half a turn, one route for both alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.park(s);
    }
}

/** NearCycle.java: score the preload, one more piece, then park. Only one line is new. */
@Autonomous(name = "Near: Preload + Cycle + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public static class NearCycle extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

In your code each class is its own file (`NearPreloadPark.java`, `NearCycle.java`) in `autos/`, with
`public class` (no `static`) and no `@Disabled`.

- The steps are not chained with dots here. Each line calls a piece, and the piece adds its steps to the
  same list `s`. The result is the same list as one long chain.
- **When to choose this way:** the drivers pick "Near: Preload + Park" when the partner robot needs the
  pieces, and "Near: Preload + Cycle + Park" when the path is free.

## Example 2: one OpMode, the drivers choose in INIT

The same plans (and a "Park only" plan for a robot with a problem), and two start spots, in **one**
OpMode:

<!-- recipe: autos-choose-in-init -->
```java
/** ChooseInInit.java: ONE autonomous; the drivers choose the start and the plan in INIT. */
@Autonomous(name = "Choose in INIT", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public static class ChooseInInit extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        // The menu lines. The FIRST option of each is chosen at INIT: make it your safest plan.
        s.menuChoice("Start", "Near", "Far")
         .menuChoice("Plan", "Preload + Park", "Preload + Cycle + Park", "Park only");

        // The start spot follows the "Start" line: the note and the simulator's robot change with it.
        s.onlyIf("Start", "Near")
             .start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0)
         .end()
         .onlyIf("Start", "Far")
             .start("FAR: against our wall, 2nd tile right of centre, facing the field", 36, -61, 0)
             .move("Across to the near side", Tunables.Auto.farToNearInches, 270, 0.7)
         .end();

        // The plan: plain Java "if", because the pieces are methods. choice(...) is the option chosen.
        String plan = s.choice("Plan");
        if (!plan.equals("Park only")) Routes.scorePreload(s, robot);
        if (plan.equals("Preload + Cycle + Park")) Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

What the drivers see after INIT:

```text
Place robot : NEAR: against our wall, 2nd tile left of centre, facing the field
--- Choose (D-pad), then START ---
> Alliance: BLUE
  Start delay: 0 s
  Start: Near
  Plan: Preload + Park
  Profile: COMPETITION
X = BLUE   B = RED
```

D-pad down twice, then right: `Start: Far`, and the first line at once says `Place robot : FAR: ...`.

- `onlyIf("Start", "Far") ... end()`: the steps between run only when "Far" is chosen. A `start(...)`
  in there is the start only for "Far".
- `String plan = s.choice("Plan")` keeps the chosen text in a variable called `plan`.
  `plan.equals("Park only")` is true when the two texts are the same. `!` means "not".
- **Spell the options exactly the same** in `menuChoice` and in `onlyIf`/`equals`, capitals included.
  A typo means a branch that never runs. Test every option once in the simulator.
- **The first option is the default.** If nobody touches the menu, the robot runs the first option of
  every line. Make that your safest plan.

## Example 3: near and far starts

A different start spot is the most common reason for a second autonomous. You have two good ways:

**A. Two OpModes** (`Near: Preload + Park`, `Far: Preload + Park`). The drivers pick the line that
matches where the robot is. Each file has its own `start(...)`. The far one adds its extra steps, then
calls the same pieces:

```java
s.start("FAR: against our wall, 2nd tile right of centre, facing the field", 36, -61, 0)
 .move("Across to the near side", Tunables.Auto.farToNearInches, 270, 0.7);
Routes.scorePreload(s, robot);
Routes.park(s);
```

**B. One OpMode with a `Start` line** (example 2). One file to keep right. The "Place robot" note
changes with the choice, so the drivers can check it.

Choose A when the far route is long and different; choose B when the far route is "the near route plus a
few steps". Either way, **the start spot is in the note, in capitals, so the drivers can check it**.

## Test each one in the simulator

Run every autonomous, with every menu option, in the simulator before it goes on the robot. A route
that drives into a wall in the simulator will do it on the field too.

**By hand.** Make the robot file and start the simulator (from the `ftc-helper-lib` folder):

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

Open http://localhost:8780, choose the autonomous, press INIT. Change the menu with the on-screen
D-pad. Watch the "Place robot" line and the robot on the field map move when you change the start.
Press START and read the report at the end. ([Simulator guide](simulator.md).)

**With a scenario file** (no clicking, the same checks every time). Make one file per plan, for example
`simulator/scenarios/choose-far-cycle.sim` in your robot project:

```text
# "Choose in INIT" with Start: Far and Plan: Preload + Cycle + Park
init "Choose in INIT"
press g1.x                             # BLUE
press g1.dpad_down                     # to "Start delay"
press g1.dpad_down                     # to "Start"
press g1.dpad_right                    # Near -> Far
expect screen has "Place robot : FAR"
press g1.dpad_down                     # to "Plan"
press g1.dpad_right                    # -> Preload + Cycle + Park
expect screen has "Plan: Preload + Cycle + Park"
start
expect within 30 screen has "Autonomous finished"
expect screen has "To the pieces: done"
expect screen has "Park: done"
expect error none
```

Run it:

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/choose-far-cycle.sim
```

Each line prints `ok` or `FAIL`. The `press` lines follow the menu order: Alliance, Start delay, your
lines in the order you wrote them, Profile. Write one scenario per option (near, far, each plan). Run
all of them after every change to `Routes.java`: a change to one piece changes every auto that uses it.
More scenario lines: [scenario files](simulator.md#scenario-files-test-without-a-browser).

## Match day

**Before the event**

- **Pick a default autonomous** and its default menu settings. It is the plan the drive team runs
  unless the match talk with the partner changes it. Write it on the drive team's card.
- **Remove what you will not run.** `@Disabled` on old and untested autos, test routines in
  `group = "Test"`.
- **Every auto on the list has been run** on the real field (or a practice field), as BLUE and as RED,
  with each menu option, and in the COMPETITION profile.
- **Commit and push** the code you will run, and tag it (see
  [Git and GitHub](github.md#before-an-event-backups-and-a-tag)). Then you can always get back to it.

**At the field, every match** (a checklist for the drive team)

1. Talk with the partner: who starts where, who goes where, who needs a delay.
2. Choose the autonomous on the Driver Station. Read its name out loud.
3. Press **INIT**. Read the menu out loud: alliance, start delay, start, plan.
4. Set the **Alliance** (X = BLUE, B = RED). If your robot has lights, check that they show the right color.
5. Check that **Profile** says **COMPETITION** (a `!! PRACTICE profile` warning at the top means it does not).
6. Place the robot where the **Place robot** note says. A second person checks the note against the robot.
7. Look for warnings on the screen (a camera, the odometry, tuned values that differ from the code).
8. Hands off. Press START when the match starts.

**After the match**

- Read the report (on the Driver Station, and again on TeleOp's INIT screen). Write down any step that
  says `timed out`, `STALLED` or `skipped`.
- Fix numbers in `Tunables.Auto` at the pits, test, commit.

**Common mistakes**

| What happens | Why, and the fix |
| --- | --- |
| The robot ran the wrong plan | the default option ran because nobody changed the menu. Read the menu out loud before START. |
| The robot started in the wrong place | the note was not checked. Start the note with the spot in capitals; a second person checks it. |
| A menu option does nothing | the option is spelled differently in `menuChoice` and `onlyIf`. Test every option in the simulator. |
| One auto is fixed, another is not | the route was copied. Move it into `Routes.java`. |
| The drivers cannot find the auto | too many on the list. `@Disabled` the old ones; use groups. |
| TeleOp is not chosen after autonomous | `preselectTeleOp` must match the TeleOp's name exactly. |

## Next

- Every step method, the report, and more recipes: [The autonomous builder](autonomous-builder.md).
- Short answers: [Cookbook: several autos and choosing one](../cookbook.md#several-autos-and-choosing-one).
- Keep your code safe between practices: [Git and GitHub for FTC teams](github.md).

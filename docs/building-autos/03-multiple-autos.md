# Having multiple autos

Keep more than one plan, and pick the right one at the field.

At an event you play with a new partner in every match. Sometimes you start near the goal, sometimes far.
Sometimes your partner needs the space you usually drive through. So you need a few plans, with clear
names, that the drive team can pick fast.

## One file per plan

The simple way: **each plan is its own auto file.** Each file is one line in the Driver Hub's Autonomous
list. The drivers pick the plan by its name.

- **The auto builder works with it.** Build each plan in the builder ([page 6](06-auto-builder-steps.md)),
  and copy each one into its own file in `autos/`. Later, **Load** can read each file again.
- **Start from a copy.** Copy a working auto file, give the class and the `@Autonomous` name a new name,
  and change the steps.
- **Red and blue need no second file.** The Alliance line in the INIT menu does that
  ([red and blue](02-put-steps-together.md#red-and-blue)).
- **Waiting for a partner at the start needs no second file.** The Start delay line does that.

**When it gets too big:** many files that share the same steps, or more plans than the drivers can pick
from. Then put the shared steps in one place, or give the drivers a choice in INIT:
[Advanced autos](08-advanced-autos.md#one-auto-with-choices-or-several-autos).

## Near and far starts

A different start spot is the most common reason for a second plan. Two good ways:

- **Two files**, `Near: Preload + Park` and `Far: Preload + Park`. Each has its own `start(...)`. The far
  one adds its first moves, then the same steps. Pick this when the far route is long and different.
- **One file with a Start line** in the INIT menu ([Advanced autos](08-advanced-autos.md#a-whole-example-one-auto-the-drivers-choose)).
  Pick this when the far route is "the near route, plus a few steps". The builder cannot write it.

**Check the far route against the field.** On BIOBUZZ's field a FLOWER stands on your wall between the two
start spots. A robot that slides along the wall from the far spot drives into it. So the far route drives
off the wall first. Watch every new route in the simulator.

## Names on the Driver Station

The drivers read the Autonomous list in a hurry, on a small screen.

```java
@Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Main TeleOp")
```

- **Start the name with the start spot**, then the plan: `Near: Preload + Park`. The drivers look at
  where the robot is first.
- **Say what it scores**, not how the code works. `Near: Preload + Cycle + Park`, not `AutoV3Fixed`.
- **No alliance in the name.** One file works for both alliances. Two files `Red Near` and `Blue Near`
  are twice the work, and one day they do not match.
- **`group`** lists autos together. `group = "Autos"` for match autos, `group = "Test"` for test routines.
- **`preselectTeleOp`**: your TeleOp's exact name. After the auto, the Driver Station has it chosen.
- **Hide old autos** with `@Disabled`, or delete them. A short list is a safe list.
- **Start the note with the spot in CAPITALS**: `"NEAR: against our wall, ..."`. A second person checks
  the note against the robot.

## Done when

- Each of your plans is one line on the Driver Hub list, with a clear name.
- Each plan runs in the simulator, as BLUE and as RED.

## Problems

| Problem | Why, and the fix |
| --- | --- |
| The drivers picked the wrong auto | The names look alike. Start each name with the start spot, and say what it scores. |
| The drivers cannot find the auto | Too many on the list. Add `@Disabled` to the old ones, and use groups. |
| A fix in one auto is missing in another | The two files share steps. Fix both, or put the shared steps in `Routes.java` ([Advanced autos](08-advanced-autos.md#routes)). |
| Two autos have the same name | Each `@Autonomous(name = ...)` must be different. The Driver Hub shows only one. |

---

**Next:** [Drive to points on the field](04-drive-to-points.md): with odometry. No odometry yet? Go on to [the auto builder](06-auto-builder-steps.md).

**Look it up:** [every step](../reference/steps.md#auto). **Go deeper:**
[cookbook: several autos and choosing one](../cookbook.md#several-autos-and-choosing-one).

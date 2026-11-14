# Building autos

An **auto** (autonomous) is the program the robot runs by itself in the first 30 seconds of a match.
You write it as a **list of steps**. These pages show every step you can use and how to put steps together.
They also show how to keep multiple autos, and how to build one on a picture of the field.

| # | Page | You learn |
| --- | --- | --- |
| 1 | [What an auto can do](01-what-an-auto-can-do.md) | Every step, in groups: drive, turn, wait, parts, aim and shoot. Which steps wait, and which do not. |
| 2 | [Put steps together](02-put-steps-together.md) | Start a part and drive at the same time. Keep time to park. The INIT menu. Red and blue. |
| 3 | [Having multiple autos](03-multiple-autos.md) | One file per plan. Near and far starts. Good names. |
| 4 | [Drive to points on the field](04-drive-to-points.md) | With odometry: `driveTo` a field point, and `routeTo` around field parts. |
| 5 | [Add Pedro paths](05-pedro-paths.md) | Set up the Pedro add-on. Lines and curves, no stop between parts, face a point, start parts along a path. |
| 6 | [The auto builder: steps](06-auto-builder-steps.md) | Build an auto on the field in the simulator, run it with your code, and copy the Java. |
| 7 | [The auto builder: odometry and Pedro](07-auto-builder-odometry-and-pedro.md) | Build `driveTo` steps and Pedro paths on the field map. |
| 8 | [Advanced autos](08-advanced-autos.md) | Beyond the auto builder: choices in INIT, shared pieces of a route, a number for RED, waits for a part, your own step. |
| 9 | [Match day](09-match-day.md) | The setup card, the checks before each match, and the report after it. |

**Where the numbers go.** Each auto keeps its own numbers in its steps: `.forward("To the goal", 24, 0.6)`.
`Tunables.java` keeps the robot's numbers only (speeds, positions, gains).
**Why:** a change to one auto never changes another, and the auto builder can read and change the numbers.
More: [the numbers are in the step](../five-files/05-autos.md#the-numbers-are-in-the-step).

**Start with page 1.** If you like to see the field before you read code, read pages 6 and 7 (the auto
builder) first.

**Next:** [What an auto can do](01-what-an-auto-can-do.md).

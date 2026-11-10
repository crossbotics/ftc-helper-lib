# The five files: your robot's code

**Before this:** [Set up](../setup/README.md) is done. The library is in your project, and the robot
configuration has your motors' names.

Your robot's code is five files in the `teamcode` folder. Each file has one job:

| File | What it holds | You change it when |
| --- | --- | --- |
| [`Robot.java`](01-robot-java.md) | What is on the robot: the drive and each part, with their names | you add, remove or change a part |
| [`Tunables.java`](02-tunables-java.md) | The numbers: speeds, positions, powers | you measure or tune a number |
| [`Controls.java`](03-controls-java.md) | The buttons, and the TeleOp | you change what a button does |
| [`Actions.java`](04-actions-java.md) | Steps that use several parts together, like "score" | you want one button or step to do several things |
| [`autos/`](05-autos.md) | The autonomous programs, one file each | you write or change an autonomous |

## Start small, then add

These pages build the **smallest robot that works first: it drives.** Then you add one part at a time,
and test each one before the next.

**Why:** when one part is new, a problem can only be in that part. When five parts are new, it can be
anywhere.

## Where your five files come from

You do not type them from nothing. You copy them, then change them:

| You have | Copy |
| --- | --- |
| A seller's starter bot | its five files ([Pick your template](../templates.md)). They already have every part. |
| A robot like one of our examples | that example's five files ([Pick your template](../templates.md)) |
| Your own design | the smallest files on these pages, then add your parts |

With a starter bot's files, these pages still explain every line you see.

## Done when

At the end of the five files pages, the robot drives in TeleOp and runs a short autonomous.

**Next:** [Robot.java](01-robot-java.md).

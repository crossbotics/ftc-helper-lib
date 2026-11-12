# The five files: your robot's code

Your robot's code is five files in the `teamcode` folder. Each file has one job:

| File | What it holds | You change it when |
| --- | --- | --- |
| [`Robot.java`](01-robot-java.md) | What is on the robot: the drive and each part, with their names | you add, remove or change a part |
| [`Tunables.java`](02-tunables-java.md) | The numbers: speeds, positions, powers | you measure or tune a number |
| [`Controls.java`](03-controls-java.md) | The buttons, and the TeleOp | you change what a button does |
| [`Actions.java`](04-actions-java.md) | Steps that use several parts together, like "score" | you want one button or step to do several things |
| [`autos/`](05-autos.md) | The autonomous programs, one file each | you write or change an autonomous |

## Five files? But two autos make more than five

Yes. "Five files" counts the five jobs, not the exact number of files. The fifth one, `autos/`, is a
folder. It has one file for each autonomous, so a robot with three autos has seven files.

You can also see a few small files next to these, for example a finder (a small program that measures
one number for you), or `Routes.java` for routes that several autos share
([Advanced autos](../building-autos/06-advanced-autos.md)). They do not hold any of the robot's
parts, numbers or buttons. Those always stay in the four files and the `autos/` folder.

## Start small, then add

These pages build the **smallest robot that works first: it drives.** Then you add one part at a time,
and test each one before the next.

**Why:** when only one part is new, a problem can only be in that part. When five parts are new, the
problem can be anywhere.

## Where your five files come from

You do not type them from nothing. You copy them from
[Pick your start](../pick-your-start/README.md) (a starter bot, a common setup, or a blank slate), then
change them. When you start from a starter bot's or an example's files, these pages still explain every
line you see.

## Done when

At the end of the five files pages, the robot drives in TeleOp and runs a short autonomous.

**Next:** [Robot.java](01-robot-java.md).

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
([Advanced autos](../building-autos/08-advanced-autos.md)). They do not hold any of the robot's
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

## Java you will meet

The five files use a few Java ideas again and again. Read the list once. Come back to it when you meet one.

| You see | Its name | What it means |
| --- | --- | --- |
| `class Robot extends RobotDefinition` | **extends** | "built on": your class gets everything the library class does. You fill in the methods it leaves open (here `define()`), each marked `@Override`. |
| `ControlMap<Robot>`, `Auto<Robot>` | **generics** | the name in angle brackets says which class the library class works with: your `Robot`. |
| `Drive.tank(...).motor(...).wheelMm(96)` | **method chaining** | each method gives back the same thing, so the next one adds to it. Read it left to right as one sentence. |
| `() -> r.launcher.fire(4)` | **lambda** | a small piece of code you give now, to run later (when a button is pressed, or an action gets to that step). `() ->` means "no inputs, run this". |
| `r.intake::in` | **method reference** | the short way to write `() -> r.intake.in()`. |
| `Runnable` | | Java's name for "code with no inputs that can run later". A lambda or a method reference is one. |

**A name is red in Android Studio:** the file needs an `import` line. Click the name and press **Alt+Enter**
(**Option+Enter** on a Mac). If it offers two, pick the one in `com.crossbotics.ftchelper.robot`.

## Done when

At the end of the five files pages, the robot drives in TeleOp and runs a short autonomous.

**Check yourself:**

- Where do you change the launcher's speed? (`Tunables.java`, section `Launcher`.)
- The operator wants the claw on X, not B. Which file? (`Controls.java`: `g2.x.toggle(robot.claw)`.)
- An action lifts the lift, and the operator moves the lift stick. What happens? (The action stops: the
  driver always wins.)
- Why does `define()` not move anything? (It runs in INIT, and the library moves nothing before ▶.)

**Commit after each step that works,** so you can always go back ([Git and GitHub](../guides/github.md)).

**Next:** [Robot.java](01-robot-java.md).

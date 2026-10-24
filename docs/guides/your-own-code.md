# Your own code at INIT, START, every loop and STOP

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** students who want to write their own code in the steps of an FTC OpMode, and students who
want to write a whole OpMode themselves. **You need:** the five files
([lesson 1](../tutorials/start/01-robot-in-five-files.md)) and a first look at
[non-blocking code](non-blocking.md).

## The five steps of every FTC OpMode

Every FTC program (an **OpMode**) goes through the same five steps. The library runs them for you, and
you can add your own code to each one:

| FTC step | When | What the library does | Your code |
| --- | --- | --- | --- |
| `init` | once, when the driver presses INIT | makes every part, checks the robot configuration and `Tunables.java` | `atInit(...)` |
| `init_loop` | again and again, until START | the alliance choice, the INIT checks, the Driver Hub screen | `duringInit(...)` |
| `start` | once, when the driver presses START | each part's start (an intake "on at the start") | `atStart(...)` |
| `loop` | again and again, until STOP | the buttons, driving, every part and action moves a little | `everyLoop(...)` |
| `stop` | once, at STOP, at the end of an autonomous, or after an error | stops the wheels and every part | `atStop(...)` |

Two rules for all of them:

- **Nothing moves before START.** Code in `atInit` and `duringInit` may read sensors and show
  things. It must not give a motor power or move a servo. **Why:** the game rules say the robot
  stays still until the match starts, and a robot that moves in INIT can hurt someone setting it up.
- **Never wait.** Each piece of code must be quick: no `sleep()`, no `while` that waits for
  something. **Why:** while your code waits, the whole robot freezes, STOP included
  ([Non-blocking code](non-blocking.md)). To do things one after another, use an
  [action](../tutorials/start/05-actions.md).

## Road 1: your code in the five files

This is the usual road: you keep everything the library does (the buttons, the menus, the safety
stops) and add your own code where you need it.

**For every OpMode** (TeleOp and every autonomous): put it in `Robot.java`'s `define()`.

```java title="Robot.java"
public int piecesScored = 0;   // a number of your own (your actions add to it)

@Override
protected void define() {
    drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right").motor("gobilda-5203-312").wheelMm(96));
    lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
    claw = add(Claw.servo("claw"));

    atInit(() -> piecesScored = 0);                                   // INIT, once
    duringInit(() -> show("Lift (counts)", lift.position()));         // INIT loop: check it before the match
    everyLoop(() -> show("Pieces scored", piecesScored));             // every loop after START
    atStop(() -> RobotLog.ii("team", "scored " + piecesScored));      // the end, after the motors stop
}
```

**For one TeleOp only:** put it in `Controls.java`'s `buttons(...)`. It runs once, in INIT.

```java title="Controls.java"
@Override
protected void buttons(Robot robot, Pad g1, Pad g2) {
    g2.rb.press(Actions.scoreHigh(robot)).says("score high");
    robot.everyLoop(() -> robot.show("Lift (counts)", robot.lift.position()));
}
```

**For one autonomous only:** put it in its `atInit(robot)`. (Not in `steps(...)`: the library reads
the steps several times in INIT, so the code would be added several times.)

```java title="autos/ScoreAndPark.java"
@Override
protected void atInit(Robot robot) {
    robot.duringInit(() -> robot.show("Claw", "closed on the preload?"));
    robot.atStop(() -> RobotLog.ii("team", "auto ended"));
}
```

- `atStart(...)` takes an [action](../tutorials/start/05-actions.md), for example
  `atStart(() -> Actions.deployIntake(this))`.
- `show("label", value)` puts one line on the Driver Hub, under the library's warnings. Call it in
  every loop: the screen starts empty each loop.
- Code that moves a motor every loop belongs in a part of your own: `addOwn(...)`
  ([the robot module](../modules/robot.md)). **Why:** STOP and the pause button then stop its motor
  too.
- If one `atStop` fails, the others still run, and the Driver Station shows the first error.
- `atStop` runs at every end, also when STOP is pressed in INIT. `wasStarted()` tells them apart:
  `atStop(() -> { if (wasStarted()) savedCount = piecesScored; })`.
- In `Controls.java`, write `robot.show(...)`: plain `show(...)` there is the TeleOp's own `show`, which
  takes a value to read again every loop (`show("Lift", robot.lift::position)`).

## Road 2: your own OpMode, with the library's robot

Some students want to write the whole OpMode themselves, the way FTC teaches it: their own `INIT`,
their own loop, their own buttons. You can, and still use your `Robot.java`, `Tunables.java` and
`Actions.java`. Five lines connect them:

| In your OpMode | What it does |
| --- | --- |
| `robot.init(this)` | INIT: makes every part, turns on bulk reads (faster loops), runs the INIT checks. Moves nothing. |
| `robot.initLoop()` | one pass of the INIT loop: the parts' INIT work, your `duringInit`, then it sends the screen |
| `robot.start()` | START: each part's start and your `atStart` actions (nothing if STOP was pressed in INIT) |
| `robot.update()` | one loop: every part and action moves a little, your `everyLoop`, then it sends the screen |
| `robot.stop()` | STOP: stops the wheels and every part, then your `atStop`. Put it in a `finally`. |

Two more help you drive: `robot.driveWith(gamepad1)` drives with the sticks (the left trigger drives
slowly), and `robot.driveWith(forward, strafeRight, turnRight)` drives with numbers from -1 to 1.

```java title="OwnTeleOp.java"
@TeleOp(name = "Own TeleOp")
public class OwnTeleOp extends LinearOpMode {

    @Override
    public void runOpMode() {
        Robot robot = new Robot();
        robot.init(this);                       // INIT: makes the parts, checks the numbers; moves nothing
        int scored = 0;
        boolean aBefore = false;

        while (opModeInInit()) {
            robot.show("Press START", "A scores high");
            robot.initLoop();                   // sends the screen
        }

        try {
            robot.start();                      // (does nothing if STOP was pressed in INIT)
            while (opModeIsActive()) {
                robot.driveWith(gamepad1);

                // A: only when it goes down, not every loop while it is held
                if (gamepad1.a && !aBefore) {
                    robot.run(Actions.scoreHigh(robot));   // runs a little each loop: never waits
                    scored++;
                }
                aBefore = gamepad1.a;
                if (gamepad1.right_trigger > 0.5) robot.intake.in();
                else robot.intake.stop();

                robot.show("Scored", scored);
                robot.update();                 // every part moves a little, then the screen is sent
            }
        } finally {
            robot.stop();                       // always: stops the wheels and every part
        }
    }
}
```

The whole file is in the lesson robot's example:
[OwnTeleOp.java](https://github.com/crossbotics/ftc-helper-lib/blob/main/examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/OwnTeleOp.java).
Try it in the simulator: **Lesson Bot: own OpMode**.

- **Call `robot.update()` once in every loop.** Without it, no part moves and no action runs.
- **Do not call `telemetry.update()` yourself.** `robot.update()` and `robot.initLoop()` send the
  screen, with your own `telemetry.addData(...)` lines and your `robot.show(...)` lines.
- **Keep `robot.stop()` in a `finally`.** **Why:** it then runs even after an error, so no motor
  keeps its power.

**What you keep:** every part's safety (lift and arm limits, soft stops, jam back-out), the actions,
the interlock rules, the INIT checks of `Tunables.java`, bulk reads, and the simulator.

**The same as the library's OpModes:** in a TeleOp, the alliance and (with odometry) the pose come
from the autonomous that just ran. An OpMode marked `@Autonomous` starts every part from its start
position. INIT shows the PRACTICE and "tuned values differ" warnings.

**What you do yourself now:** the buttons (the library's taps, double-taps and holds are in
[Use only what you need](use-only-what-you-need.md#controllerbindings)), the alliance choice, the
pause button, field-centric driving, the tuning menu, the button list on the Driver Hub, and the
match timer's buzzes. For an autonomous: the steps one after another, the alliance choice, the
RED/BLUE mirroring, the start spot, and handing the alliance and the end pose to the TeleOp (the
library's `Auto` does all of these for you).

## Which road?

| You want | Road |
| --- | --- |
| Show a sensor in INIT, count something, save a number at the end | Road 1 |
| Your own button logic, but the library's buttons, menus and safety for the rest | Road 1 (`buttons(...)`, actions) |
| To learn how an FTC OpMode works from the inside | Road 2 |
| A TeleOp written the way your old code was, with the library's parts | Road 2 |

You can mix them: one team TeleOp on road 1 and a practice OpMode on road 2, both with the same
`Robot.java`.

## See also

- [What runs every loop](every-loop.md): the library's own steps, in order.
- [Non-blocking code](non-blocking.md): why nothing may wait.
- [Use only what you need](use-only-what-you-need.md): single pieces of the library in your own code,
  without `Robot.java`.

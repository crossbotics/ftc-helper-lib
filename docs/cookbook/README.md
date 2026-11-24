# Cookbook: "How do I...?"

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Short answers to the questions students ask most, one page per topic. Each recipe has one question, a few
lines of code to copy, one sentence on **why**, and links to the page that explains more.

**To find your question:** use the list below, or the site's search with a word like `lift`,
`double-tap` or `park`.

**The code works.** Every code block in the cookbook is a copy of real Java files in
[`examples/.../cookbook`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook). Each time
the library changes, we build those files and run them in the simulator. A check
(`tools/check_docs.py`) makes sure that the pages and the files say the same thing.

**If you are new:** do [Pick your start](../pick-your-start/README.md) and [The five files](../five-files/README.md) first. They put the five files in your robot
project, and these recipes go into those files. If you never coded before, read
[Words you will meet](01-the-basics.md#words-you-will-meet) first.

## The questions

**Start here**
- [Coming from Blocks: where did my blocks go?](01-the-basics.md#coming-from-blocks-where-did-my-blocks-go) (if, loop, sleep, REVERSE, variables)
- [Where does each line go? The files, whole](01-the-basics.md#where-does-each-line-go-the-files-whole)
- [Words you will meet](01-the-basics.md#words-you-will-meet)
- Whole robots to start from: the [templates](../templates.md) (ten whole example robots) and the
  [kit robots](../guides/starter-bots.md) (goBILDA, REV, AndyMark, Studica starter bots)

**Driving**
- [How do I set up a mecanum drive?](02-driving.md#how-do-i-set-up-a-mecanum-drive)
- [How do I set up a tank (standard) drive?](02-driving.md#how-do-i-set-up-a-tank-standard-drive)
- [A wheel turns the wrong way. How do I fix it?](02-driving.md#a-wheel-turns-the-wrong-way-how-do-i-fix-it)
- [How do I tell the robot how the Control Hub is mounted?](02-driving.md#how-do-i-tell-the-robot-how-the-control-hub-is-mounted)
- [How do I make the robot slower (or faster)?](02-driving.md#how-do-i-make-the-robot-slower-or-faster)
- [How do I let one driver do everything?](02-driving.md#how-do-i-let-one-driver-do-everything)
- [How do I put slow mode on a trigger?](02-driving.md#how-do-i-put-slow-mode-on-a-trigger)
- [Driving that is already built in](02-driving.md#driving-that-is-already-built-in) (field-centric, pause, "forward")

**Buttons and gestures**
- [How do I make a button do something once?](03-buttons.md#how-do-i-make-a-button-do-something-once)
- [How do I run something only while a button is held?](03-buttons.md#how-do-i-run-something-only-while-a-button-is-held)
- [How do I switch something on and off with one button?](03-buttons.md#how-do-i-switch-something-on-and-off-with-one-button)
- [How do I put a tap, a hold and a double-tap on one button?](03-buttons.md#how-do-i-put-a-tap-a-hold-and-a-double-tap-on-one-button)
- [How do I make a button safe ("hold for 1 second")?](03-buttons.md#how-do-i-make-a-button-safe-hold-for-1-second)
- [How do I use two buttons together (like a shift key)?](03-buttons.md#how-do-i-use-two-buttons-together-like-a-shift-key)
- [How do I make the gamepad buzz?](03-buttons.md#how-do-i-make-the-gamepad-buzz)
- [How do I control a part with a stick?](03-buttons.md#how-do-i-control-a-part-with-a-stick)
- [How do I move a part's button to another button?](03-buttons.md#how-do-i-move-a-parts-button-to-another-button)
- [How do I put the launcher's fire button on another button?](03-buttons.md#how-do-i-put-the-launchers-fire-button-on-another-button)
- [How do I let the parts add their usual buttons by themselves?](03-buttons.md#how-do-i-let-the-parts-add-their-usual-buttons-by-themselves)
- [How do I move the library's own buttons (slow, field-centric, pause)?](03-buttons.md#how-do-i-move-the-librarys-own-buttons-slow-field-centric-pause)
- [How do I change the double-tap and hold times?](03-buttons.md#how-do-i-change-the-double-tap-and-hold-times)
- [How do I switch the alliance during TeleOp?](03-buttons.md#how-do-i-switch-the-alliance-during-teleop)
- [Buttons that are already built in](03-buttons.md#buttons-that-are-already-built-in) (button map, pause, switch alliance, set pose)

**Mechanisms**
- [How do I add an intake on a motor?](04-mechanisms.md#how-do-i-add-an-intake-on-a-motor)
- [How do I add an intake on CR servos?](04-mechanisms.md#how-do-i-add-an-intake-on-cr-servos)
- [How do I make the intake run from the start?](04-mechanisms.md#how-do-i-make-the-intake-run-from-the-start)
- [How do I add a lift with set heights?](04-mechanisms.md#how-do-i-add-a-lift-with-set-heights)
- [My lift goes down when it should go up. How do I fix it?](04-mechanisms.md#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)
- [How do I run one lift on two motors?](04-mechanisms.md#how-do-i-run-one-lift-on-two-motors)
- [My motor isn't in the catalog](04-mechanisms.md#my-motor-isnt-in-the-catalog)
- [How do I add an arm that moves in degrees?](04-mechanisms.md#how-do-i-add-an-arm-that-moves-in-degrees)
- [How do I add a claw?](04-mechanisms.md#how-do-i-add-a-claw)
- [How do I run a claw on two servos?](04-mechanisms.md#how-do-i-run-a-claw-on-two-servos)
- [How do I add a wrist?](04-mechanisms.md#how-do-i-add-a-wrist)
- [How do I add any servo with named positions (a gate, a fold-out)?](04-mechanisms.md#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)
- [How do I get my servo's full range?](04-mechanisms.md#how-do-i-get-my-servos-full-range)
- [How do I send a part to a position from a button?](04-mechanisms.md#how-do-i-send-a-part-to-a-position-from-a-button)
- [My robot doesn't have this part yet. How do I keep its code?](04-mechanisms.md#my-robot-doesnt-have-this-part-yet-how-do-i-keep-its-code)
- [How do I add a launcher (flywheel and feeder)?](04-mechanisms.md#how-do-i-add-a-launcher-flywheel-and-feeder)
- [How do I feed with two wheels, one on each side?](04-mechanisms.md#how-do-i-feed-with-two-wheels-one-on-each-side)
- [How do I add a turret that follows the goal?](04-mechanisms.md#how-do-i-add-a-turret-that-follows-the-goal)
- [How do I fire only when aimed, at the right speed?](04-mechanisms.md#how-do-i-fire-only-when-aimed-at-the-right-speed)

**Game pieces** (what is in the robot, and what fires next)
- [How do I know which piece is in the robot, and how many?](05-game-pieces.md#how-do-i-know-which-piece-is-in-the-robot-and-how-many)
- [How do I stop the intake when the robot is full?](05-game-pieces.md#how-do-i-stop-the-intake-when-the-robot-is-full)
- [How do I tell pieces apart by size, or with a second camera?](05-game-pieces.md#how-do-i-tell-pieces-apart-by-size-or-with-a-second-camera)
- [How do I use the count (buzz, LEDs, the shot table)?](05-game-pieces.md#how-do-i-use-the-count-buzz-leds-the-shot-table)
- [How do I let the next piece pick the shot table?](05-game-pieces.md#how-do-i-let-the-next-piece-pick-the-shot-table)
- [How do I tune the piece sensors?](05-game-pieces.md#how-do-i-tune-the-piece-sensors)

**Aiming and shooting** (the robot helps the drivers)
- [How do I make the whole robot aim at the goal (no turret)?](06-aiming-and-shooting.md#how-do-i-make-the-whole-robot-aim-at-the-goal-no-turret)
- [How do I make the gamepad buzz when the aim is on target?](06-aiming-and-shooting.md#how-do-i-make-the-gamepad-buzz-when-the-aim-is-on-target)
- [How do I keep aiming when the camera loses the goal?](06-aiming-and-shooting.md#how-do-i-keep-aiming-when-the-camera-loses-the-goal)
- [How do I aim with the odometry only (no camera)?](06-aiming-and-shooting.md#how-do-i-aim-with-the-odometry-only-no-camera)
- [How do I make the camera work in any venue's light?](06-aiming-and-shooting.md#how-do-i-make-the-camera-work-in-any-venues-light)
- [How do I find game pieces with a HuskyLens?](06-aiming-and-shooting.md#how-do-i-find-game-pieces-with-a-huskylens)
- [How do I shoot while the robot is driving?](06-aiming-and-shooting.md#how-do-i-shoot-while-the-robot-is-driving)
- [How do I fire a group without stopping?](06-aiming-and-shooting.md#how-do-i-fire-a-group-without-stopping)
- [How do I use one shot table per game piece?](06-aiming-and-shooting.md#how-do-i-use-one-shot-table-per-game-piece)
- [Every shot is a bit short today. How do I trim the speed (a low battery)?](06-aiming-and-shooting.md#every-shot-is-a-bit-short-today-how-do-i-trim-the-speed-a-low-battery)
- [How do I add a hood to my launcher?](06-aiming-and-shooting.md#how-do-i-add-a-hood-to-my-launcher)

**Turrets and interlocks** (any turret; parts that share space)
- [How do I home my turret with a switch?](07-turrets-and-interlocks.md#how-do-i-home-my-turret-with-a-switch)
- [How do I set up a turret on a servo?](07-turrets-and-interlocks.md#how-do-i-set-up-a-turret-on-a-servo)
- [How do I set up a slip-ring turret (round and round)?](07-turrets-and-interlocks.md#how-do-i-set-up-a-slip-ring-turret-round-and-round)
- [How do I stop two parts from hitting each other?](07-turrets-and-interlocks.md#how-do-i-stop-two-parts-from-hitting-each-other)
- [How do I pick up and score with a lift, a wrist and a claw?](07-turrets-and-interlocks.md#how-do-i-pick-up-and-score-with-a-lift-a-wrist-and-a-claw)

**Actions** (several steps or parts together)
- [How do I do several things one after the other?](08-actions.md#how-do-i-do-several-things-one-after-the-other)
- [How do I repeat steps several times?](08-actions.md#how-do-i-repeat-steps-several-times)
- [How do I move two parts at the same time?](08-actions.md#how-do-i-move-two-parts-at-the-same-time)
- [How do I wait half a second (like sleep in Blocks)?](08-actions.md#how-do-i-wait-half-a-second-like-sleep-in-blocks)
- [How do I wait until something is true?](08-actions.md#how-do-i-wait-until-something-is-true)
- [How do I start an action from a button?](08-actions.md#how-do-i-start-an-action-from-a-button)
- [How do I stop every running action?](08-actions.md#how-do-i-stop-every-running-action)
- [How do I run an action when the OpMode starts?](08-actions.md#how-do-i-run-an-action-when-the-opmode-starts)
- [How do I use the same action in an autonomous?](08-actions.md#how-do-i-use-the-same-action-in-an-autonomous)

**Autonomous**
- [How do I make my first autonomous (drive 2 feet)?](09-autonomous.md#how-do-i-make-my-first-autonomous-drive-2-feet)
- [How do I drive forward, sideways and turn?](09-autonomous.md#how-do-i-drive-forward-sideways-and-turn)
- [Does my autonomous need a RED version?](09-autonomous.md#does-my-autonomous-need-a-red-version)
- [How do I let the drivers choose before the match?](09-autonomous.md#how-do-i-let-the-drivers-choose-before-the-match)
- [How do I make sure there is time to park?](09-autonomous.md#how-do-i-make-sure-there-is-time-to-park)
- [How do I run a mechanism while the robot drives?](09-autonomous.md#how-do-i-run-a-mechanism-while-the-robot-drives)
- [How do I wait for my alliance partner?](09-autonomous.md#how-do-i-wait-for-my-alliance-partner)
- [How do I write a step the list cannot say?](09-autonomous.md#how-do-i-write-a-step-the-list-cannot-say)
- [How do I change a distance in an autonomous?](09-autonomous.md#how-do-i-change-a-distance-in-an-autonomous)
- [How do I share the same first steps in several autos?](09-autonomous.md#how-do-i-share-the-same-first-steps-in-several-autos)
- [How do I make the start delay shorter, or use a different number on RED?](09-autonomous.md#how-do-i-make-the-start-delay-shorter-or-use-a-different-number-on-red)

**Several autos and choosing one** (more: [Building autos 3: having multiple autos](../building-autos/03-multiple-autos.md))
- [How do I build several autos from the same pieces?](10-several-autos.md#how-do-i-build-several-autos-from-the-same-pieces)
- [How do I make two autos that differ by one piece?](10-several-autos.md#how-do-i-make-two-autos-that-differ-by-one-piece)
- [How do I let the drivers choose the start spot and the plan in INIT?](10-several-autos.md#how-do-i-let-the-drivers-choose-the-start-spot-and-the-plan-in-init)

**Odometry and driveTo** (where is the robot on the field?)
- [How do I add a goBILDA Pinpoint?](11-odometry.md#how-do-i-add-a-gobilda-pinpoint)
- [How do I add a SparkFun OTOS?](11-odometry.md#how-do-i-add-a-sparkfun-otos)
- [How do I add dead wheels?](11-odometry.md#how-do-i-add-dead-wheels)
- [How do I tune the odometry?](11-odometry.md#how-do-i-tune-the-odometry)
- [How do I drive to a point on the field?](11-odometry.md#how-do-i-drive-to-a-point-on-the-field)
- [How do I get around field elements on the way to a spot?](11-odometry.md#how-do-i-get-around-field-elements-on-the-way-to-a-spot)
- [How do I show where the robot is?](11-odometry.md#how-do-i-show-where-the-robot-is)
- [How do I use the robot's position in an action?](11-odometry.md#how-do-i-use-the-robots-position-in-an-action)
- [How do I set the pose when autonomous did not run?](11-odometry.md#how-do-i-set-the-pose-when-autonomous-did-not-run)

**Pedro paths** (smooth curves, mecanum only)
- [How do I add Pedro Pathing to Robot.java?](12-pedro-paths.md#how-do-i-add-pedro-pathing-to-robotjava)
- [How do I make a straight path and a curve?](12-pedro-paths.md#how-do-i-make-a-straight-path-and-a-curve)
- [How do I follow a path in an autonomous?](12-pedro-paths.md#how-do-i-follow-a-path-in-an-autonomous)
- [How do I drive two paths with no stop, slow down, or hold at the end?](12-pedro-paths.md#how-do-i-drive-two-paths-with-no-stop-slow-down-or-hold-at-the-end)
- [How do I keep facing the goal along a path?](12-pedro-paths.md#how-do-i-keep-facing-the-goal-along-a-path)
- [How do I drive first and turn at the end of a path (or turn first)?](12-pedro-paths.md#how-do-i-drive-first-and-turn-at-the-end-of-a-path-or-turn-first)
- [How do I start something partway along a path?](12-pedro-paths.md#how-do-i-start-something-partway-along-a-path)

**Telemetry** (lines on the Driver Station)
- [How do I show a value on the Driver Station screen?](13-telemetry.md#how-do-i-show-a-value-on-the-driver-station-screen)
- [How do I round a number on the Driver Station?](13-telemetry.md#how-do-i-round-a-number-on-the-driver-station)
- [How do I show lines from my own autonomous step?](13-telemetry.md#how-do-i-show-lines-from-my-own-autonomous-step)

**Lights** (a color the drivers can see)
- [How do I add a colored light for the drivers?](14-lights.md#how-do-i-add-a-colored-light-for-the-drivers)
- [How do I change a color on a REV Blinkin?](14-lights.md#how-do-i-change-a-color-on-a-rev-blinkin)
- [How do I show a color when something is true?](14-lights.md#how-do-i-show-a-color-when-something-is-true)
- [How do I show the alliance on the light before the match?](14-lights.md#how-do-i-show-the-alliance-on-the-light-before-the-match)
- [How do I flash the light when something happens?](14-lights.md#how-do-i-flash-the-light-when-something-happens)
- [How do I flash the light when an action is done?](14-lights.md#how-do-i-flash-the-light-when-an-action-is-done)
- [How do I blink the light in the last 20 seconds?](14-lights.md#how-do-i-blink-the-light-in-the-last-20-seconds)

**Tuning and finders** (getting the numbers right)
- [Where do the numbers go?](15-tuning.md#where-do-the-numbers-go)
- [How do I find a servo's open and closed numbers?](15-tuning.md#how-do-i-find-a-servos-open-and-closed-numbers)
- [How do I find a lift's limits and heights?](15-tuning.md#how-do-i-find-a-lifts-limits-and-heights)
- [How do I change a number while the robot runs?](15-tuning.md#how-do-i-change-a-number-while-the-robot-runs)
- [How do I tune the drive for autonomous?](15-tuning.md#how-do-i-tune-the-drive-for-autonomous)
- [How do I set the flywheel numbers?](15-tuning.md#how-do-i-set-the-flywheel-numbers)
- [How do I build a shot table (speed for each distance)?](15-tuning.md#how-do-i-build-a-shot-table-speed-for-each-distance)
- [How do I find my hood's stops and angles?](15-tuning.md#how-do-i-find-my-hoods-stops-and-angles)

**Simulator** (try your code with no robot)
- [How do I start the simulator?](16-simulator.md#how-do-i-start-the-simulator)
- [How do I see my own robot in the simulator?](16-simulator.md#how-do-i-see-my-own-robot-in-the-simulator)
- [How do I make the simulator draw my part?](16-simulator.md#how-do-i-make-the-simulator-draw-my-part)
- [How do I test my code with a script?](16-simulator.md#how-do-i-test-my-code-with-a-script)

## Not here?

- Every class and method: the [reference](../reference/README.md) and the [module pages](../../README.md#what-is-inside).
- Something goes wrong: [Troubleshooting](../troubleshooting.md).
- Learn it step by step: [The five files](../five-files/README.md), then [Add to your robot](../add-to-robot/README.md).
- A question the cookbook does not answer: [tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

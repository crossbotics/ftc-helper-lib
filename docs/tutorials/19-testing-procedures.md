# Check the hardware (tutorial 19)

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

With the five files, the Hardware Check is already in the **Tune and check** menu: [Wiring checks](../tuning/02-wiring-checks.md).
Build it by hand here to see that a whole library OpMode can be a 3-line file. Or build it to run it as its own OpMode on a robot without the five files.

**You will build:** a 3-line OpMode. With it, you test every motor, servo and sensor on the robot by hand.

**You need:** any robot.

**Full program:** [`Tut19HardwareCheck.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut19HardwareCheck.java) (only 3 lines)

---

## 1. The 3-line OpMode

The OpMode itself is 3 lines. With the lines every Java file needs, the whole file is below. Make a new
Java file `HardwareCheck.java` in your `teamcode` folder
([how to make a file](../start-from-zero.md#make-a-new-file-or-folder)). Replace everything in it with:

```java
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.testing.HardwareCheckOpMode;

@TeleOp(name = "Hardware Check", group = "Tests")
public class HardwareCheck extends HardwareCheckOpMode {
}
```

- **File name:** `HardwareCheck.java`, the same as the class name after `public class`.
- **The `package` line** says the file is in the `teamcode` folder.
- **The two `import` lines** say where `TeleOp` (from the FTC SDK) and `HardwareCheckOpMode` (from
  this library) are. **Why:** without them, the build says `cannot find symbol`.
- **No `@Disabled` line:** the Driver Station lists the OpMode.

`extends` means "is a kind of": this OpMode is a `HardwareCheckOpMode`, so it does everything that class
does. The library wrote the OpMode, and you only give it a name. The name in `@TeleOp(name = ...)` can be
anything. The tutorial's own copy is "Tutorial 19: Hardware Check".

## 2. Use it

Put the robot on a block, so the wheels are off the ground. Run the OpMode. It lists every device in
the active configuration, and the battery voltage.

- **D-pad up/down**: choose a device. When you change the device, the last motor stops.
- **Motor**: right stick = power (50% max; hold RB for 100%). **Stick up = positive power.** The SDK
  reads a stick pushed up as a negative number. The Hardware Check flips it for you, like the library's
  driving. A = zero the encoder.
  **On a lift or an arm:** no limits apply here, and RB gives full power. Use short, gentle taps. Know
  where its hard stop (the end of its travel) is, and keep hands clear.
- **Servo**: a servo cannot tell where it is. So first choose a **start point** near where the servo
  sits now: D-pad right/left = +/- 0.01, bumpers +/- 0.1, X = 0.5. Nothing moves yet. Keep hands clear,
  then press A: the servo moves to the start point. After that, the same buttons move it.
- **Sensors**: the screen shows live values.

What to check on each device, and the fix when it is wrong: [Hardware Check](../tests/03-checks-before-practice-and-matches.md#3-hardware-check-after-a-wiring-or-configuration-change).

## 3. The checks, in order

The fixed order (laptop, Hardware Check, mechanisms, floor, autonomous, event) and the printable
checklists: [Checks before practice and matches](../tests/03-checks-before-practice-and-matches.md).

## Try it in the simulator

**Where:** the Terminal tab, in the `ftc-helper-lib` folder.

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

Open http://localhost:8780 and choose "Tutorial 19: Hardware Check". Pick `lift` with the D-pad, and push
gamepad 1's right stick up: the lift rises and its count goes up. The simulator builds its robot from
your code, so it cannot find a wrong wire: only the real robot can. More: [Simulator](../simulator/README.md).

## Try this

1. Make your own OpMode that runs each drive motor forward for 1 second in turn, and shows its encoder
   change.
2. Write a test (tutorial 13) for something that once went wrong on your robot.

## After this

Two more tutorials build the TeleOp and the finders by hand. When you build something new for the
library, read [Extending the library](../guides/extending.md).

---

**Next:** [Tutorial 20: Easy TeleOp](20-easy-teleop.md)
**Look it up:** [testing.md](../modules/testing.md)

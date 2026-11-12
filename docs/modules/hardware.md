# Hardware module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.hardware`

Learn it hands-on: [02 Motors and servos](../tutorials/02-motors-and-servos.md), [06 A fast, clear loop](../tutorials/06-fast-clear-loop.md).

Gets motors, servos, sensors and the IMU (the turn sensor in the Control Hub) from the robot
configuration and sets them up, each in one readable chain. When a name is wrong, the error says
which names do exist. Use it whenever your own code needs a device. The
[robot in five files](../reference/robot-java.md) does this for its parts, so there you only type the names. Start with tutorial 02.

| Class | Use it to |
| --- | --- |
| [`Hw`](#hw) | get any device, with a helpful error if the name is wrong |
| [`MotorSetup`](#motorsetup) | get and set up a motor |
| [`ServoSetup`](#servosetup) | get and set up a servo |
| [`ImuSetup`](#imusetup) | start the IMU and read the heading |
| [`BulkReads`](#bulkreads) | make your loop faster by reading a hub's encoders and simple sensors at once |

---

## Hw

Gets devices by their configuration name (the names you typed in the Driver Station's
**Configure Robot** screen).

### `Hw.get(hardwareMap, type, name)`

Gets a device. When the name is wrong, it stops the OpMode with a message that lists the names that DO exist.

```java
// (where to look, what kind of device, its name in the robot configuration)
DcMotorEx intake = Hw.get(hardwareMap, DcMotorEx.class, "intake");
TouchSensor bottom = Hw.get(hardwareMap, TouchSensor.class, "lift_bottom");
```

| Parameter | Meaning |
| --- | --- |
| `hardwareMap` | the OpMode's `hardwareMap` |
| `type` | the kind of device, for example `DcMotorEx.class`, `Servo.class`, `TouchSensor.class` |
| `name` | the configuration name. Type it exactly: capitals and spaces matter. |

**Returns** the device. **If the name is wrong**, the Driver Station shows something like:

```
No DcMotorEx named "intak" in the active robot configuration. Names of this type that do
exist: [front_left, front_right, intake, lift]. Check the spelling and the active
configuration on the Driver Station.
```

The SDK's own `hardwareMap.get` only says that it could not find "intak".

### `Hw.tryGet(hardwareMap, type, name)`

The same as `get`, but it returns `null` instead of stopping when the device is not there.
Use it for optional parts, like a sensor that is only on the practice robot. **Always check for null.**

```java
TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
// null = not on this robot. bottom::isPressed hands over "how to read it" (a method reference).
if (bottom != null) lift.setLowerSwitch(bottom::isPressed);
```

### `Hw.missingMessage(hardwareMap, type, name)`

Gives the error text that `get` uses. You need it only when your own code writes that message.

### `Hw.batteryVolts(hardwareMap)`

The battery's voltage now: the lowest hub reading, or NaN with no voltage sensor. Each call asks the hubs
and **waits** for them for a few milliseconds. That is fine in INIT, but not in the loop. In the loop, use
`Hw.batteryReader` (below).

### `Hw.batteryReader(hardwareMap)`

The battery voltage for code that runs every loop. It gives a `DoubleSupplier` (an object that hands
back a number when you ask). It answers at once with the latest reading. Then it asks the hubs again on
the library's helper thread, at most every `BATTERY_READ_SEC` (0.25 s) ([non-blocking](../guides/non-blocking.md)).
It gives NaN before the first answer, with no voltage sensor, or when the last answer is older than
`BATTERY_STALE_SEC` (2 s). The flywheel's [battery compensation](launcher.md#battery-compensation) uses it.

```java
DoubleSupplier battery = Hw.batteryReader(hardwareMap);   // once, in INIT
double volts = battery.getAsDouble();                      // every loop: never waits
```

---

## MotorSetup

Gets a motor and sets it up in one chain. The chain applies the settings in the order you write them.
The **encoder** is the motor's sensor that counts how far the motor has turned. Its cable goes to the
hub's encoder port.

```java
DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
        .reversed()          // mounted mirrored: positive power should still mean "up"
        .brake()             // at power 0, hold still instead of letting the lift drop
        .resetEncoder()      // here counts as 0 (reset BEFORE the run mode)
        .runUsingEncoder()
        .done();             // finish: power 0, and hand back the motor
```

| Method | What it does |
| --- | --- |
| `MotorSetup.get(hardwareMap, name)` | Starts the chain. A wrong name gives a clear error (like `Hw.get`). |
| `MotorSetup.of(motor)` | Starts the chain for a motor you already have. |
| `.forward()` | Positive power turns the normal way (the default). |
| `.reversed()` | Positive power turns the other way. Use it for a mirrored motor. |
| `.reversed(boolean)` | `true` = reversed, `false` = forward. Use it with a setting variable. |
| `.brake()` | At power 0 the motor holds still. Use it for drive wheels, lifts, arms. |
| `.coast()` | At power 0 the motor spins freely. Use it for flywheels. |
| `.resetEncoder()` | The encoder count becomes 0. **Put it before a run mode** (see below). |
| `.runUsingEncoder()` | Power sets a speed. The hub uses the encoder to keep that speed. It needs the encoder cable. |
| `.runWithoutEncoder()` | Power goes straight to the motor. You can still read the encoder. |
| `.done()` | Sets power to 0 and returns the `DcMotorEx`. Always end with this. |

**Common mistakes**

- `.runUsingEncoder().resetEncoder()` in that order: the reset puts the motor in
  STOP_AND_RESET_ENCODER mode. The motor does not move until your code sets a new run mode.
  Always reset first, then choose the run mode.
- `runUsingEncoder()` without an encoder cable: the motor jumps to full speed or does not move.
  Use `runWithoutEncoder()` when there is no cable.

---

## ServoSetup

```java
Servo claw = ServoSetup.get(hardwareMap, "claw")
        .reversed()
        .range(0.20, 0.75)   // only this part of the travel: it can never hit the frame
        .done();
```

| Method | What it does |
| --- | --- |
| `ServoSetup.get(hardwareMap, name)` | Starts the chain. A wrong name gives a clear error. |
| `ServoSetup.of(servo)` | Starts the chain for a servo you already have. |
| `.reversed()` | Positions 0 and 1 swap ends. |
| `.range(min, max)` | Uses only part of the travel. After this, 0 means `min` and 1 means `max`. |
| `.fullRange()` | Uses the servo's whole turn: 0 to 1 sends a 500 to 2500 microsecond pulse instead of 600 to 2400 ([below](#full-range)). |
| `.pulseRange(lowUs, highUs)` | The same with your own pulse numbers (100 to 3000 us, low first). |
| `.done()` | Returns the `Servo`. |
| `ServoSetup.setPulseRange(servo, lowUs, highUs)` | The same for any servo. It gives false when it cannot change the pulse (not a REV hub servo). |
| `ServoSetup.checkPulse(lowUs, highUs)` | Stops with a clear message when a number is outside 100 to 3000 us, or when low is not below high. |
| `SDK_PULSE_LOW_US`, `SDK_PULSE_HIGH_US`, `FULL_PULSE_LOW_US`, `FULL_PULSE_HIGH_US` | 600, 2400 (the SDK's normal pulse) and 500, 2500 (full range), microseconds. |

**Why use `range`:** it keeps the servo away from the frame. Find the positions where the claw is fully
open and fully closed (for example 0.20 and 0.75), and put them in `range`. Now `setPosition(0)` is "open",
`setPosition(1)` is "closed", and no code can push the servo into the frame.

**Note:** setup does not move the servo. The servo moves the first time you call `setPosition`.

### Full range

A servo goes where the hub's **pulse** tells it. A signal that lasts 600 microseconds (us) means position 0,
and 2400 us means position 1. That is the FTC SDK's normal range (`PwmControl.PwmRange.defaultRange`).
Most FTC servos take a wider pulse, 500 to 2500 us. So with the normal range, the servo never uses part of its turn:

| Servo | Seller's range | 0 to 1 on the normal 600-2400 us | with `.fullRange()` |
| --- | --- | --- | --- |
| goBILDA 2000 Series (Torque, Speed, Super Speed) | 300 deg over 500-2500 us | 270 deg | 300 deg |
| goBILDA 2000 Series 5-Turn | 1800 deg over 500-2500 us | 1620 deg | 1800 deg |
| REV Smart Robot Servo (V1, V2) | 270 deg over 500-2500 us | 243 deg | 270 deg |
| Axon MAX MK2, MINI MK2 | 360 deg max over 500-2500 us | 324 deg | 360 deg |

```java
Servo wrist = ServoSetup.get(hardwareMap, "wrist").fullRange().done();   // 0 to 1 = 300 degrees
```

- **Nothing changes unless you ask.** Without `.fullRange()` the library never touches the pulse range.
- **Measure positions again after you switch.** Position 0.5 is still the middle, but every other number
  points somewhere else. A measured number (positions, `posA`/`posB`, `angleA`/`angleB`, a calibration table)
  is right only for the range you measured it with. The Servo Finder has the same switch (Y while you
  choose a servo). The Hood Finder uses the hood's own setting.
- **Only REV hub servos** (Control Hub, Expansion Hub, Servo Hub: the SDK's `ServoImplEx`) can change
  the range. On another servo, `setPulseRange` gives false and nothing changes.
- **It lasts until the next INIT:** the hub goes back to its normal range when an OpMode starts.
- **Check your servo's own numbers:** a servo programmed to a smaller range (an Axon's left and right
  limits, a REV servo in its Servo Limit Mode) turns less. Past 500 or 2500 us, most servos stop short or
  buzz. Stay inside the range the seller gives.

The [parts catalog](../guides/parts-catalog.md) lists each servo's pulse range and both degrees.

---

## ImuSetup

The IMU is a sensor inside the Control Hub that measures turning. The library uses it for
field-centric driving and autonomous turns.

### `ImuSetup.get(hardwareMap, name, logo, usb)`

Gets the IMU, tells it how the hub is mounted, and sets the heading to 0.
If the IMU does not start, it stops the OpMode with a clear message. So you never get a broken IMU.

```java
// How the hub sits on the robot: logo pointing UP, USB ports toward the robot's FRONT.
IMU imu = ImuSetup.get(hardwareMap, "imu",
        RevHubOrientationOnRobot.LogoFacingDirection.UP,
        RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
```

| Parameter | Meaning |
| --- | --- |
| `name` | the IMU's configuration name. It is `"imu"` unless you changed it. |
| `logo` | which way the REV logo printed on the hub points: `UP`, `DOWN`, `FORWARD`, `BACKWARD`, `LEFT`, `RIGHT` |
| `usb` | which way the hub's USB ports point (same choices) |

"Forward" means the front of the robot. **If these are wrong, the heading is wrong** and
field-centric driving goes the wrong way.

**To check:** run an OpMode that shows `ImuSetup.headingDeg(imu)`. Turn the robot left by hand.
The number must go UP. Turn it right: the number must go DOWN.

### `ImuSetup.headingDeg(imu)`

The robot's heading in degrees, from -180 to 180. Turning left makes it bigger.
It is 0 in the direction the robot pointed at setup, or at the last `imu.resetYaw()`.

---

## BulkReads

Each time your code reads an encoder or sensor, it sends a message to the hub and waits for the
answer (about 2 milliseconds). With 6 encoders, that is 12 ms each loop. A **bulk read** gets
everything from a hub in one message, so the loop runs faster.

### `BulkReads.auto(hardwareMap)`

One line in INIT. It is safe for every robot. Start with this.

```java
BulkReads.auto(hardwareMap);
```

In AUTO mode, the hub does a new bulk read when you read any value a second time. So your loop is
fastest when it reads each value once. That is why `SafeMotor` (and so every Lift, Arm and
`PositionMotor`) and the launcher's `Turret` read each encoder about once per loop. They use a reading
again for 3 ms, so it is at most 3 ms old. `EasyTeleOp`, `EasyAuto` and the finders call `BulkReads.auto` for you.
To see what each part reads every loop, read [what runs every loop](../guides/every-loop.md#bulk-reads).

### `BulkReads.manual(hardwareMap)` and `BulkReads.clear(hubs)`

The fastest way. You clear the saved readings yourself at the top of every loop.
**If you forget `clear`, the readings never change,** and the robot does strange things.

```java
// List<LynxModule> = a list of the hubs (a LynxModule is one Control or Expansion Hub)
List<LynxModule> hubs = BulkReads.manual(hardwareMap);
waitForStart();
while (opModeIsActive()) {
    BulkReads.clear(hubs);   // first line of the loop, every time
    ...
}
```

Use `LoopTimer` ([util](util.md)) to see how much faster your loop gets.

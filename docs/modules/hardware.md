# Hardware module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.hardware`

Learn it hands-on: [02 Motors and servos](../tutorials/02-motors-and-servos.md), [06 A fast, clear loop](../tutorials/06-fast-clear-loop.md).

Gets motors, servos, sensors and the IMU (the turn sensor in the Control Hub) from the robot
configuration and sets them up, each in one readable chain. When a name is wrong, the error says
which names do exist. Use it whenever your own code needs a device. The
[robot in five files](robot.md) does this for its parts, so there you only type the names. Start with tutorial 02.

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

Gets a device, or stops the OpMode with a message that lists the names that DO exist.

```java
// (where to look, what kind of device, its name in the robot configuration)
DcMotorEx intake = Hw.get(hardwareMap, DcMotorEx.class, "intake");
TouchSensor bottom = Hw.get(hardwareMap, TouchSensor.class, "lift_bottom");
```

| Parameter | Meaning |
| --- | --- |
| `hardwareMap` | the OpMode's `hardwareMap` |
| `type` | the kind of device, e.g. `DcMotorEx.class`, `Servo.class`, `TouchSensor.class` |
| `name` | the configuration name. Exact: capitals and spaces matter. |

**Returns** the device. **If the name is wrong**, the Driver Station shows something like:

```
No DcMotorEx named "intak" in the active robot configuration. Names of this type that do
exist: [front_left, front_right, intake, lift]. Check the spelling and the active
configuration on the Driver Station.
```

Compare: the SDK's own `hardwareMap.get` only says it could not find "intak".

### `Hw.tryGet(hardwareMap, type, name)`

Same, but returns `null` instead of stopping when the device is not there.
Use it for optional parts (a sensor that is only on the practice robot). **Always check for null.**

```java
TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
// null = not on this robot. bottom::isPressed hands over "how to read it" (a method reference).
if (bottom != null) lift.setLowerSwitch(bottom::isPressed);
```

### `Hw.missingMessage(hardwareMap, type, name)`

Gives the error text that `get` uses. You rarely need it.

### `Hw.batteryVolts(hardwareMap)`

The battery's voltage now (the lowest hub reading; NaN with no voltage sensor). Each call asks the hubs
and **waits** for them (a few milliseconds): fine in INIT, not in the loop. In the loop use
`Hw.batteryReader` (below).

### `Hw.batteryReader(hardwareMap)`

The battery for code that runs every loop: a `DoubleSupplier` that answers at once with the latest
reading and asks the hubs again (at most every `BATTERY_READ_SEC`, 0.25 s) on the library's helper
thread ([non-blocking](../guides/non-blocking.md)). NaN before the first answer, with no voltage sensor,
or when the last answer is older than `BATTERY_STALE_SEC` (2 s). The flywheel's
[battery compensation](launcher.md#battery-compensation) uses it.

```java
DoubleSupplier battery = Hw.batteryReader(hardwareMap);   // once, in INIT
double volts = battery.getAsDouble();                      // every loop: never waits
```

---

## MotorSetup

Gets a motor and sets it up in one chain. The settings are applied in the order you write them.
The **encoder** is the motor's sensor that counts how far it has turned; its cable goes to the
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
| `MotorSetup.get(hardwareMap, name)` | Starts the chain. Wrong name = clear error (like `Hw.get`). |
| `MotorSetup.of(motor)` | Starts the chain for a motor you already have. |
| `.forward()` | Positive power turns the normal way (the default). |
| `.reversed()` | Positive power turns the other way. Use for a mirrored motor. |
| `.reversed(boolean)` | `true` = reversed, `false` = forward. Handy with a setting variable. |
| `.brake()` | At power 0 the motor holds still. Use for drive wheels, lifts, arms. |
| `.coast()` | At power 0 the motor spins freely. Use for flywheels. |
| `.resetEncoder()` | Encoder count becomes 0. **Put it before a run mode** (see below). |
| `.runUsingEncoder()` | Power sets a speed; the hub uses the encoder to keep that speed. Needs the encoder cable. |
| `.runWithoutEncoder()` | Power goes straight to the motor. You can still read the encoder. |
| `.done()` | Sets power to 0 and returns the `DcMotorEx`. Always end with this. |

**Common mistakes**

- `.runUsingEncoder().resetEncoder()` in that order: the reset puts the motor in
  STOP_AND_RESET_ENCODER mode, and it will not move until a new run mode is set.
  Always reset first, then choose the run mode.
- `runUsingEncoder()` without an encoder cable: the motor jumps to full speed or does not move.
  Use `runWithoutEncoder()` if there is no cable.

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
| `ServoSetup.get(hardwareMap, name)` | Starts the chain. Wrong name = clear error. |
| `ServoSetup.of(servo)` | Starts the chain for a servo you already have. |
| `.reversed()` | Positions 0 and 1 swap ends. |
| `.range(min, max)` | Uses only part of the travel. After this, 0 means `min` and 1 means `max`. |
| `.fullRange()` | Uses the servo's whole turn: 0 to 1 sends 500 to 2500 microseconds instead of 600 to 2400 ([below](#full-range)). |
| `.pulseRange(lowUs, highUs)` | The same with your own pulse numbers (100 to 3000 us, low first). |
| `.done()` | Returns the `Servo`. |
| `ServoSetup.setPulseRange(servo, lowUs, highUs)` | The same for any servo; returns false when it cannot change its pulse (not a REV hub servo). |
| `ServoSetup.checkPulse(lowUs, highUs)` | Stops with a clear message for numbers outside 100 to 3000 us or low not below high. |
| `SDK_PULSE_LOW_US`, `SDK_PULSE_HIGH_US`, `FULL_PULSE_LOW_US`, `FULL_PULSE_HIGH_US` | 600, 2400 (the SDK's normal pulse) and 500, 2500 (full range), microseconds. |

**Why use `range`?** Find the positions where the claw is fully open and fully closed (for
example 0.20 and 0.75) and put them in `range`. Now `setPosition(0)` is "open",
`setPosition(1)` is "closed", and no code can ever push the servo into the frame.

**Note:** setup does not move the servo. It moves the first time you call `setPosition`.

### Full range

A servo goes where the hub's **pulse** tells it: a signal that lasts 600 microseconds (us) means position 0,
2400 us means position 1. That is the FTC SDK's normal range (`PwmControl.PwmRange.defaultRange`).
Most FTC servos take a wider pulse, 500 to 2500 us, so on the normal range part of their turn is never used:

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
- **Measure positions after switching.** Position 0.5 is still the middle, but every other number points
  somewhere else. Measured numbers (positions, `posA`/`posB`, `angleA`/`angleB`, a calibration table)
  are right only for the range they were measured with. The Servo Finder has the same switch (Y while
  choosing a servo); the Hood Finder uses the hood's own.
- **Only REV hub servos** (Control Hub, Expansion Hub, Servo Hub: the SDK's `ServoImplEx`) can change
  it. On another servo `setPulseRange` returns false and nothing changes.
- **It lasts until the next INIT:** the hub goes back to its normal range when an OpMode starts.
- **Check your servo's own numbers:** a servo programmed to a smaller range (an Axon's left and right
  limits, a REV servo in its Servo Limit Mode) turns less. Past 500 or 2500 us most servos stop short or
  buzz: stay inside what the seller gives.

The [parts catalog](../guides/parts-catalog.md) lists each servo's pulse range and both degrees.

---

## ImuSetup

The IMU is a sensor inside the Control Hub that measures turning. The library uses it for
field-centric driving and autonomous turns.

### `ImuSetup.get(hardwareMap, name, logo, usb)`

Gets the IMU, tells it how the hub is mounted, and sets the heading to 0.
If the IMU does not start, it stops the OpMode with a clear message (it does not hand you a broken IMU).

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

How to check: run an OpMode that shows `ImuSetup.headingDeg(imu)`. Turn the robot left by hand.
The number must go UP. Turn it right: the number goes DOWN.

### `ImuSetup.headingDeg(imu)`

The robot's heading in degrees, from -180 to 180. Turning left makes it bigger.
It is 0 where the robot pointed when the IMU was set up or last reset with `imu.resetYaw()`.

---

## BulkReads

Each time your code reads an encoder or sensor, it sends a message to the hub and waits for the
answer (about 2 milliseconds). With 6 encoders that is 12 ms each loop. A **bulk read** gets
everything from a hub in one message.

### `BulkReads.auto(hardwareMap)`

One line in INIT. Safe for everyone. Start with this.

```java
BulkReads.auto(hardwareMap);
```

In AUTO mode, a new bulk read happens when you read any value a second time. So reading each
value once per loop gives the best speed. That is why `SafeMotor` (so every Lift, Arm and
`PositionMotor`) and the launcher's `Turret` read each encoder about once per loop: a reading is
used again for 3 ms (so it is at most 3 ms old). `EasyTeleOp`, `EasyAuto` and the finders call `BulkReads.auto` for you. What
each part reads every loop: [what runs every loop](../guides/every-loop.md#bulk-reads).

### `BulkReads.manual(hardwareMap)` and `BulkReads.clear(hubs)`

The fastest way. You clear the saved readings yourself at the top of every loop.
**If you forget `clear`, the readings never change** and the robot acts very strangely.

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

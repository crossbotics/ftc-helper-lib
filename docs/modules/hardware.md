# Hardware module

Package: `io.github.jasoncross.ftchelper.hardware`

Learn it hands-on: [02 Motors and servos](../tutorials/02-motors-and-servos.md), [06 A fast, clear loop](../tutorials/06-fast-clear-loop.md).

Gets motors, servos, sensors and the IMU from the robot configuration and sets them up,
each in one readable chain. Gives clear errors when a name is wrong.

| Class | Use it to |
| --- | --- |
| [`Hw`](#hw) | get any device, with a helpful error if the name is wrong |
| [`MotorSetup`](#motorsetup) | get and set up a motor |
| [`ServoSetup`](#servosetup) | get and set up a servo |
| [`ImuSetup`](#imusetup) | start the IMU and read the heading |
| [`BulkReads`](#bulkreads) | make your loop faster |

---

## Hw

Gets devices by their configuration name (the names you typed in the Driver Station's
**Configure Robot** screen).

### `Hw.get(hardwareMap, type, name)`

Gets a device, or stops the OpMode with a message that lists the names that DO exist.

```java
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
if (bottom != null) lift.setLowerSwitch(bottom::isPressed);
```

### `Hw.missingMessage(hardwareMap, type, name)`

Gives the error text that `get` uses. You rarely need it.

---

## MotorSetup

Gets a motor and sets it up in one chain. The settings are applied in the order you write them.

```java
DcMotorEx lift = MotorSetup.get(hardwareMap, "lift")
        .reversed()
        .brake()
        .resetEncoder()
        .runUsingEncoder()
        .done();
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
        .range(0.20, 0.75)
        .done();
```

| Method | What it does |
| --- | --- |
| `ServoSetup.get(hardwareMap, name)` | Starts the chain. Wrong name = clear error. |
| `ServoSetup.of(servo)` | Starts the chain for a servo you already have. |
| `.reversed()` | Positions 0 and 1 swap ends. |
| `.range(min, max)` | Uses only part of the travel. After this, 0 means `min` and 1 means `max`. |
| `.done()` | Returns the `Servo`. |

**Why use `range`?** Find the positions where the claw is fully open and fully closed (for
example 0.20 and 0.75) and put them in `range`. Now `setPosition(0)` is "open",
`setPosition(1)` is "closed", and no code can ever push the servo into the frame.

**Note:** setup does not move the servo. It moves the first time you call `setPosition`.

---

## ImuSetup

The IMU is a sensor inside the Control Hub that measures turning. The library uses it for
field-centric driving and autonomous turns.

### `ImuSetup.get(hardwareMap, name, logo, usb)`

Gets the IMU, tells it how the hub is mounted, and sets the heading to 0.
If the IMU does not start, it stops the OpMode with a clear message (it does not hand you a broken IMU).

```java
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

Each time your code reads an encoder or sensor, the phone sends a message to the hub and waits
(about 2 milliseconds). With 6 encoders that is 12 ms each loop. A **bulk read** gets
everything from a hub in one message.

### `BulkReads.auto(hardwareMap)`

One line in INIT. Safe for everyone. Start with this.

```java
BulkReads.auto(hardwareMap);
```

In AUTO mode, a new bulk read happens when you read any value a second time. So reading each
value once per loop gives the best speed.

### `BulkReads.manual(hardwareMap)` and `BulkReads.clear(hubs)`

The fastest way. You clear the saved readings yourself at the top of every loop.
**If you forget `clear`, the readings never change** and the robot acts very strangely.

```java
List<LynxModule> hubs = BulkReads.manual(hardwareMap);
waitForStart();
while (opModeIsActive()) {
    BulkReads.clear(hubs);   // first line of the loop, every time
    ...
}
```

Use `LoopTimer` ([util](util.md)) to see how much faster your loop gets.

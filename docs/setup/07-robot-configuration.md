# Set up 7: make the robot configuration

**Set up, page 7 of 9.** Do it once per robot, and again when you add a part.

**Only using the simulator on this laptop?** Skip this page. The simulator needs no hubs. Go to [Every practice](08-every-practice.md).

The **robot configuration** tells the Control Hub which device is in which port, and its name. Your
code finds each device by that name.
**Why it matters:** the names in the code must be exactly the same. Capitals and `_` count.

## Pick the names first

| Your robot | The names |
| --- | --- |
| A starter bot (goBILDA, REV, AndyMark, Studica) | The seller's names: [the table for your starter bot](../guides/starter-bots.md#the-configuration-names). Add a name for each part you added. |
| Your own robot | Your own. Short, small letters, `_` between words: `front_left`, `lift`, `claw`. |

## Steps

1. Connect the Driver Hub to the Control Hub.
2. Open the **⋮** menu > **Configure Robot** > **New**.
3. Pick the Control Hub (and the Expansion Hub, if the robot has one).
4. For each port with a device on it:
   1. Pick its kind: a motor type, **Servo**, **Continuous Rotation Servo**, or a sensor.
   2. Type its name.

   A motor type is not in the list? Pick the closest one. The library takes the motor's real numbers
   from your code.
5. Leave the **imu** line as it is. It is the Control Hub's own IMU (the sensor that knows which way
   the robot faces).
6. **Save**. Give the configuration a name, for example `MyRobot`.
7. **Activate** it.

## Done when

The Driver Hub shows your configuration's name at the top, and each port with a device has a name.

**Next:** pick the code you start from: [Quick start: pick your road](../quick-start.md#pick-your-road).
Keep [Every practice](08-every-practice.md) open: you use it from now on.

**A wrong name** stops the program at INIT, and the Driver Hub then lists the names that do exist.
More: [hardware names](../modules/hardware.md).

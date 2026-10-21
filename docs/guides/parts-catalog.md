# Parts catalog: motors, servos, wheels and rollers

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

<!-- Made by tools/parts_page.py from sim/parts/parts.json. Edit parts.json, then run the script. -->

The simulator knows these FTC motors and servos. A robot file names a part by its **id** (`part=gobilda-5203-312`), and the simulator takes the part's real numbers from here: how fast it turns, how many encoder counts it gives per turn, and its current (motors), or its range and speed (servos).

Every number comes from the seller's page or the seller's own documents (motors and servos read on **2026-10-16**; wheels and rollers on the date in their Checked column; the link is in each row). A dash (—) means the seller does not publish that number. 39 motors and 13 servos. See also: [the simulator guide](simulator.md).

## Use a part

In the robot file (`simulator/robot.properties` in a robot project):

```properties
drive.part = gobilda-5203-312                       # all four wheel motors
motor.lift = lift, part=gobilda-5203-117, min=0, max=2750
motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder
servo.claw = 0.5, part=gobilda-2000-torque          # start position, then the part
```

Numbers written in the robot file win over the part's numbers (for example `ticksPerRev=` on a motor). A typo in an id stops the simulator with a list of the ids it knows.

## Motors

Free speed and current at 12 V. **Counts per turn** are encoder counts per turn of the output shaft, as the FTC SDK reports them. **Top speed** is what `getVelocity()` shows at full power with no load: free speed × counts per turn ÷ 60. On a real robot, `setVelocity()` and RUN_USING_ENCODER use the motor TYPE chosen in the robot configuration; pick the matching type there.

### goBILDA 5203 Yellow Jacket (REX shaft)

| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) | Stall torque (kg-cm) | Stall current (A) |
|---|---|---|---|---|---|---|---|---|
| `gobilda-5203-6000` | [goBILDA 5203 Yellow Jacket 6000 RPM](https://www.gobilda.com/5203-series-yellow-jacket-motor-1-1-ratio-24mm-length-8mm-rex-shaft-6000-rpm-3-3-5v-encoder/) | 5203-2402-0001 | 1:1 | 6000 | 28 | 2800 | 1.47 | 9.2 |
| `gobilda-5203-1620` | [goBILDA 5203 Yellow Jacket 1620 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-3-7-1-ratio-1620-rpm-3-3-5v-encoder/) | 5203-2402-0003 | 3.71:1 | 1620 | 103.8 | 2803 | 5.4 | 9.2 |
| `gobilda-5203-1150` | [goBILDA 5203 Yellow Jacket 1150 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-5-2-1-ratio-24mm-length-8mm-rex-shaft-1150-rpm-3-3-5v-encoder/) | 5203-2402-0005 | 5.18:1 | 1150 | 145.1 | 2781 | 7.9 | 9.2 |
| `gobilda-5203-435` | [goBILDA 5203 Yellow Jacket 435 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-13-7-1-ratio-24mm-length-8mm-rex-shaft-435-rpm-3-3-5v-encoder/) | 5203-2402-0014 | 13.73:1 | 435 | 384.5 | 2788 | 18.7 | 9.2 |
| `gobilda-5203-312` | [goBILDA 5203 Yellow Jacket 312 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-19-2-1-ratio-24mm-length-8mm-rex-shaft-312-rpm-3-3-5v-encoder/) | 5203-2402-0019 | 19.2:1 | 312 | 537.7 | 2796 | 24.3 | 9.2 |
| `gobilda-5203-223` | [goBILDA 5203 Yellow Jacket 223 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-26-9-1-ratio-24mm-length-8mm-rex-shaft-223-rpm-3-3-5v-encoder/) | 5203-2402-0027 | 26.85:1 | 223 | 751.8 | 2794 | 38 | 9.2 |
| `gobilda-5203-117` | [goBILDA 5203 Yellow Jacket 117 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-50-9-1-ratio-24mm-length-8mm-rex-shaft-117-rpm-3-3-5v-encoder/) | 5203-2402-0051 | 50.9:1 | 117 | 1425.1 | 2779 | 68.4 | 9.2 |
| `gobilda-5203-84` | [goBILDA 5203 Yellow Jacket 84 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-71-2-1-ratio-24mm-length-8mm-rex-shaft-84-rpm-3-3-5v-encoder/) | 5203-2402-0071 | 71.16:1 | 84 | 1993.6 | 2791 | 93.6 | 9.2 |
| `gobilda-5203-60` | [goBILDA 5203 Yellow Jacket 60 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-99-5-1-ratio-24mm-length-8mm-rex-shaft-60-rpm-3-3-5v-encoder/) | 5203-2402-0100 | 99.51:1 | 60 | 2786.2 | 2786 | 133.2 | 9.2 |
| `gobilda-5203-43` | [goBILDA 5203 Yellow Jacket 43 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-139-1-ratio-24mm-length-8mm-rex-shaft-43-rpm-3-3-5v-encoder/) | 5203-2402-0139 | 139.14:1 | 43 | 3895.9 | 2792 | 185 | 9.2 |
| `gobilda-5203-30` | [goBILDA 5203 Yellow Jacket 30 RPM](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-188-1-ratio-24mm-length-8mm-rex-shaft-30-rpm-3-3-5v-encoder/) | 5203-2402-0188 | 188.61:1 | 30 | 5281.1 | 2641 | 250 | 9.2 |

### goBILDA 5204 Yellow Jacket (D-shaft)

| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) | Stall torque (kg-cm) | Stall current (A) |
|---|---|---|---|---|---|---|---|---|
| `gobilda-5204-1620` | [goBILDA 5204 Yellow Jacket 1620 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-3-7-1-ratio-80mm-length-8mm-rex-shaft-1620-rpm-3-3-5v-encoder/) | 5204-8002-0003 | 3.71:1 | 1620 | 103.8 | 2803 | 5.4 | 9.2 |
| `gobilda-5204-1150` | [goBILDA 5204 Yellow Jacket 1150 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-5-2-1-ratio-80mm-length-8mm-rex-shaft-1150-rpm-3-3-5v-encoder/) | 5204-8002-0005 | 5.18:1 | 1150 | 145.1 | 2781 | 7.9 | 9.2 |
| `gobilda-5204-435` | [goBILDA 5204 Yellow Jacket 435 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-13-7-1-ratio-80mm-length-8mm-rex-shaft-435-rpm-3-3-5v-encoder/) | 5204-8002-0014 | 13.73:1 | 435 | 384.5 | 2788 | 18.7 | 9.2 |
| `gobilda-5204-312` | [goBILDA 5204 Yellow Jacket 312 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-19-2-1-ratio-80mm-length-8mm-rex-shaft-312-rpm-3-3-5v-encoder/) | 5204-8002-0019 | 19.2:1 | 312 | 537.7 | 2796 | 24.3 | 9.2 |
| `gobilda-5204-223` | [goBILDA 5204 Yellow Jacket 223 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-26-9-1-ratio-80mm-length-8mm-rex-shaft-223-rpm-3-3-5v-encoder/) | 5204-8002-0027 | 26.85:1 | 223 | 751.8 | 2794 | 38 | 9.2 |
| `gobilda-5204-117` | [goBILDA 5204 Yellow Jacket 117 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-50-9-1-ratio-80mm-length-8mm-rex-shaft-117-rpm-3-3-5v-encoder/) | 5204-8002-0051 | 50.9:1 | 117 | 1425.1 | 2779 | 68.4 | 9.2 |
| `gobilda-5204-84` | [goBILDA 5204 Yellow Jacket 84 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-71-2-1-ratio-80mm-length-8mm-rex-shaft-84-rpm-3-3-5v-encoder/) | 5204-8002-0071 | 71.16:1 | 84 | 1993.6 | 2791 | 93.6 | 9.2 |
| `gobilda-5204-60` | [goBILDA 5204 Yellow Jacket 60 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-99-5-1-ratio-80mm-length-8mm-rex-shaft-60-rpm-3-3-5v-encoder/) | 5204-8002-0100 | 99.51:1 | 60 | 2786.2 | 2786 | 133.2 | 9.2 |
| `gobilda-5204-43` | [goBILDA 5204 Yellow Jacket 43 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-139-1-ratio-80mm-length-8mm-rex-shaft-43-rpm-3-3-5v-encoder/) | 5204-8002-0139 | 139.14:1 | 43 | 3895.9 | 2792 | 185 | 9.2 |
| `gobilda-5204-30` | [goBILDA 5204 Yellow Jacket 30 RPM (80 mm shaft)](https://www.gobilda.com/5204-series-yellow-jacket-planetary-gear-motor-188-1-ratio-80mm-length-8mm-rex-shaft-30-rpm-3-3-5v-encoder/) | 5204-8002-0188 | 188.61:1 | 30 | 5281.1 | 2641 | 250 | 9.2 |

### REV HD Hex

| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) | Stall torque (kg-cm) | Stall current (A) |
|---|---|---|---|---|---|---|---|---|
| `rev-hd-hex` | [REV HD Hex Motor (no gearbox)](https://www.revrobotics.com/rev-41-1291/) | REV-41-1291 | 1:1 | 6000 | 28 | 2800 | 1.07 | 8.5 |
| `rev-hd-hex-up3` | [REV HD Hex + UltraPlanetary 3:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1601 | 2.9:1 | 2071.4 | 81.1 | 2800 | 3.1 | 8.5 |
| `rev-hd-hex-up4` | [REV HD Hex + UltraPlanetary 4:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1602 | 3.62:1 | 1657.9 | 101.33 | 2800 | 3.87 | 8.5 |
| `rev-hd-hex-up5` | [REV HD Hex + UltraPlanetary 5:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1603 | 5.23:1 | 1147 | 146.46 | 2800 | 5.61 | 8.5 |
| `rev-hd-hex-up12` | [REV HD Hex + UltraPlanetary 12:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1601 + REV-41-1602 | 10.48:1 | 572.4 | 293.52 | 2800 | 11.23 | 8.5 |
| `rev-hd-hex-up15` | [REV HD Hex + UltraPlanetary 15:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1601 + REV-41-1603 | 15.15:1 | 396 | 424.23 | 2800 | 16.22 | 8.5 |
| `rev-hd-hex-up20` | [REV HD Hex + UltraPlanetary 20:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1602 + REV-41-1603 | 18.93:1 | 317 | 530.05 | 2800 | 20.19 | 8.5 |
| `rev-hd-hex-up60` | [REV HD Hex + UltraPlanetary 60:1 (nominal)](https://www.revrobotics.com/rev-41-1600/) | REV-41-1291 + REV-41-1601 + REV-41-1602 + REV-41-1603 | 54.83:1 | 109.5 | 1535.32 | 2802 | 58.63 | 8.5 |
| `rev-hd-hex-40` | [REV HD Hex Motor 40:1 Spur Gearbox](https://www.revrobotics.com/rev-41-1301/) | REV-41-1301 | 40:1 | 150 | 1120 | 2800 | 42.83 | 8.5 |
| `rev-hd-hex-20` | [REV HD Hex Motor 20:1 Spur Gearbox](https://docs.revrobotics.com/duo-build/motion/motors/hd-hex-motor) | REV-41-1298 | 20:1 | 300 | 560 | 2800 | 21.41 | 8.5 |

### REV Core Hex

| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) | Stall torque (kg-cm) | Stall current (A) |
|---|---|---|---|---|---|---|---|---|
| `rev-core-hex` | [REV Core Hex Motor](https://www.revrobotics.com/rev-41-1300/) | REV-41-1300 | 72:1 | 125 | 288 | 600 | 32.63 | 4.4 |

### AndyMark NeveRest

| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) | Stall torque (kg-cm) | Stall current (A) |
|---|---|---|---|---|---|---|---|---|
| `am-neverest-orbital-1` | [AndyMark NeveRest Orbital 1:1](https://andymark.com/products/neverest-orbital-gearmotors) | am-5185a / am-5185b | 1:1 | 6600 | 28 | 3080 | — | 11 |
| `am-neverest-orbital-3-7` | [AndyMark NeveRest Orbital 3.7:1](https://andymark.com/products/neverest-orbital-gearmotors) | am-3461a / am-3461b | 3.7:1 | 1784 | 103.6 | 3080 | — | 11 |
| `am-neverest-orbital-20` | [AndyMark NeveRest Orbital 19.2:1](https://andymark.com/products/neverest-orbital-gearmotors) | am-3637b | 19.2:1 | 344 | 537.6 | 3082 | — | 11 |
| `am-neverest-orbital-50` | [AndyMark NeveRest Orbital 50.9:1](https://andymark.com/products/neverest-orbital-gearmotors) | am-4609a | 50.9:1 | 130 | 1425.2 | 3088 | — | 11 |
| `am-neverest-orbital-264` | [AndyMark NeveRest Orbital 263.7:1](https://andymark.com/products/neverest-orbital-gearmotors) | am-4608a / am-4608b | 263.7:1 | 25 | 7383.6 | 3076 | — | 11 |
| `am-neverest-classic-40` | [AndyMark NeveRest Classic 40](https://andymark.com/products/neverest-classic-40-gearmotor) | am-2964a / am-2964b | 40:1 | 160 | 1120 | 2987 | 25.21 | 11.5 |
| `am-neverest-classic-60` | [AndyMark NeveRest Classic 60](https://andymark.com/products/neverest-classic-60-gearmotor) | am-3103 | 60:1 | 105 | 1680 | 2940 | 37.8 | 11.5 |

## Servos

A Control Hub servo port gives about 5 V, so the simulator uses the seller's speed row closest to 5 V (**Speed used**); the headline number on the seller's page is often at 6 V. **Range** is how far positions 0 to 1 turn it. **Continuous** = it can be switched to continuous rotation (a CR servo).

### goBILDA 2000 Series servos

| id | Part | Part number | Range (°) | Speed used (s per 60°) | Headline speed (s per 60°) | Torque (kg-cm) | Stall current (A) | Continuous |
|---|---|---|---|---|---|---|---|---|
| `gobilda-2000-torque` | [goBILDA 2000 Series Dual Mode Servo (Torque)](https://www.gobilda.com/2000-series-dual-mode-servo-25-2-torque/) | 2000-0025-0002 | 300 | 0.25 at 4.8 V | 0.2 at 6 V | 21.6 | 2.5 | yes |
| `gobilda-2000-speed` | [goBILDA 2000 Series Dual Mode Servo (Speed)](https://www.gobilda.com/2000-series-dual-mode-servo-25-3-speed/) | 2000-0025-0003 | 300 | 0.11 at 4.8 V | 0.09 at 6 V | 9.3 | 2.5 | yes |
| `gobilda-2000-super-speed` | [goBILDA 2000 Series Dual Mode Servo (Super Speed)](https://www.gobilda.com/2000-series-dual-mode-servo-25-4-super-speed/) | 2000-0025-0004 | 300 | 0.055 at 4.8 V | 0.043 at 6 V | 4.7 | 2.5 | yes |
| `gobilda-2000-5turn-torque` | [goBILDA 2000 Series 5-Turn Dual Mode Servo (Torque)](https://www.gobilda.com/2000-series-5-turn-dual-mode-servo-25-2-torque/) | 2000-0025-0502 | 1800 | 0.25 at 4.8 V | 0.2 at 6 V | 21.6 | 2.5 | yes |
| `gobilda-2000-5turn-speed` | [goBILDA 2000 Series 5-Turn Dual Mode Servo (Speed)](https://www.gobilda.com/2000-series-5-turn-dual-mode-servo-25-3-speed/) | 2000-0025-0503 | 1800 | 0.11 at 4.8 V | 0.09 at 6 V | 9.3 | 2.5 | yes |
| `gobilda-2000-5turn-super-speed` | [goBILDA 2000 Series 5-Turn Dual Mode Servo (Super Speed)](https://www.gobilda.com/2000-series-5-turn-dual-mode-servo-25-4-super-speed/) | 2000-0025-0504 | 1800 | 0.055 at 4.8 V | 0.043 at 6 V | 4.7 | 2.5 | yes |

### REV Smart Robot Servos

| id | Part | Part number | Range (°) | Speed used (s per 60°) | Headline speed (s per 60°) | Torque (kg-cm) | Stall current (A) | Continuous |
|---|---|---|---|---|---|---|---|---|
| `rev-smart-servo` | [REV Smart Robot Servo (V1)](https://docs.revrobotics.com/rev-crossover-products/servo/srs) | REV-41-1097 | 270 | 0.13 at 6 V | 0.13 at 6 V | 13.5 | 2 | yes |
| `rev-smart-servo-v2-balanced` | [REV Smart Robot Servo V2 - Balanced](https://www.revrobotics.com/Smart-servo-v2/) | REV-41-3334 | 270 | 0.14 at 6 V | 0.14 at 6 V | 13.5 | 2.1 | yes |
| `rev-smart-servo-v2-ultraspeed` | [REV Smart Robot Servo V2 - UltraSpeed](https://www.revrobotics.com/Smart-servo-v2/) | REV-41-3336 | 270 | 0.043 at 6 V | 0.043 at 6 V | 5.6 | 2.9 | yes |

### Axon servos

| id | Part | Part number | Range (°) | Speed used (s per 60°) | Headline speed (s per 60°) | Torque (kg-cm) | Stall current (A) | Continuous |
|---|---|---|---|---|---|---|---|---|
| `axon-max-mk2` | [Axon MAX MK2](https://docs.axon-robotics.com/servos/max) | — | 360 | 0.14 at 4.8 V | 0.115 at 6 V | 34 | 4 | yes |
| `axon-max-plus` | [Axon MAX+](https://docs.axon-robotics.com/archive/max) | — | — | 0.14 at 4.8 V | 0.115 at 6 V | 34 | 4 | yes |
| `axon-mini-mk2` | [Axon MINI MK2](https://docs.axon-robotics.com/servos/mini) | — | 360 | 0.11 at 4.8 V | 0.09 at 6 V | 25 | 3.8 | yes |
| `axon-mini-plus` | [Axon MINI+](https://docs.axon-robotics.com/archive/mini) | — | — | 0.11 at 4.8 V | 0.09 at 6 V | 25 | 3.8 | yes |

A servo with no range here (—) moves at the robot file's `servo.speed` (positions per second) in the simulator; its range is set by programming it.

## Wheels and rollers

For `Robot.java` (not the robot file): a `Shape` names one of these and takes its size from here, and `Drive.wheel(id)` its diameter ([robot module, Shapes](../modules/robot.md#shapes-and-parts)). **Width** is along the axle; a dash (—) means the seller does not give it, so the shape gives its length (`lengthMm`).

| id | Part | Part number | Diameter (mm) | Width (mm) | Checked | Notes |
|---|---|---|---|---|---|---|
| `gobilda-boot-72-rex` | [goBILDA Boot-Wheel 72 mm (8 mm REX bore, 30A)](https://www.gobilda.com/3615-series-boot-wheel-8mm-rex-bore-72mm-diameter-30a-durometer) | 3615-4008-0072 | 72 | — | 2026-10-20 | Silicone rubber (30A) on an acetal hex core, 14 g. Read on goBILDA's page and Core Electronics (GB-3615-4008-0072) through a web search; the page gives no width: give lengthMm. |
| `gobilda-gecko-48-rex` | [goBILDA GripForce Gecko Wheel 48 mm (8 mm REX bore, 30A)](https://www.gobilda.com/gripforce-gecko-wheel-8mm-rex-bore-48mm-diameter-30a-durometer/) | 3613-4008-0048 | 48 | — | 2026-10-20 | The page gives the diameter, not the width. Not measured yet: goBILDA's STEP CAD of its BIOBUZZ starter bot (3200-2627-0003) has these wheels, so it can be measured there as for the 72 and 96 mm wheels. |
| `gobilda-gecko-72` | [goBILDA GripForce Gecko Wheel 72 mm (14 mm bore, 30A)](https://www.gobilda.com/gripforce-gecko-wheel-14mm-bore-72mm-diameter-30a-durometer/) | 3613-0014-0072 | 72 | 24 | 2026-10-20 | widthMm measured in goBILDA's STEP CAD of its BIOBUZZ starter bot (3200-2627-0003, see sim/robots/starter-bot-gobilda.properties); the page gives the diameter only. |
| `gobilda-hogback-96` | [goBILDA Hogback Traction Wheel 96 mm (50A)](https://www.gobilda.com/hogback-traction-wheel-96mm-diameter-50a-durometer/) | 3626-0014-0096 | 96 | 24 | 2026-10-20 | widthMm measured in goBILDA's STEP CAD of its BIOBUZZ starter bot (3200-2627-0003, see sim/robots/starter-bot-gobilda.properties); the page gives the diameter only. |
| `offset-tubing-60` | [Offset Surgical Tubing Roller 60 mm](https://legacy.offsetrobotics.com/product/offset-surgical-tubing-rollers/) | OSTR-HEX-07-00 | 60 | — | 2026-10-20 | 55A silicone tubing pieces, 6 per size (the 'Both' pack: 6 x 60 mm, 6 x 90 mm, 1 OFFSET HEXAHUB, 6 M3 screws). SKU OSTR-HEX-07-00 is for the product (choose 60 mm, 90 mm or both). Read on the seller's legacy page, which shows the text; offsetrobotics.com/product/offset-surgical-tubing-rollers needs a browser. The page gives the diameter only; how long a roller is depends on how many hubs are on the shaft. |
| `offset-tubing-90` | [Offset Surgical Tubing Roller 90 mm](https://legacy.offsetrobotics.com/product/offset-surgical-tubing-rollers/) | OSTR-HEX-07-00 | 90 | — | 2026-10-20 | 55A silicone tubing pieces, 6 per size (the 'Both' pack: 6 x 60 mm, 6 x 90 mm, 1 OFFSET HEXAHUB, 6 M3 screws). SKU OSTR-HEX-07-00 is for the product (choose 60 mm, 90 mm or both). Read on the seller's legacy page, which shows the text; offsetrobotics.com/product/offset-surgical-tubing-rollers needs a browser. The page gives the diameter only; how long a roller is depends on how many hubs are on the shaft. |

## A part that is not in the list

Three ways, from quickest to most shared:

**1. Write its numbers in the robot file.** Use the seller's page.

```properties
# A motor: free speed (RPM) and encoder counts per output turn; or maxTicksPerSec= directly.
motor.arm = lift, rpm=90, ticksPerRev=1993.6, min=0, max=1200
# The drivetrain: counts per turn and top speed (counts per second).
drive.ticksPerRev = 537.7
drive.maxTicksPerSec = 2796
# A servo: its range (degrees) and seconds per 60 degrees (use the 4.8-5 V number).
servo.gate = 0.5, rangeDeg=180, secPer60=0.12
```

**2. Keep the team's own parts file.** Good when several robot files use the part, or to keep its link and notes. Put a JSON file next to the robot file and name it there:

```properties
parts = parts.json
```

It has the same form as the library's catalog. Only `id` and the numbers the simulator uses are needed (a motor: `freeRpm` and `countsPerRev`; a servo: `rangeDeg` and `secPer60Deg`); the rest is for people. Give each part its own id: an id that is already in the library is refused, so a typo can't quietly replace a real part.

```json
{
  "motors": [
    {
      "id": "team-arm-motor",
      "name": "Our arm motor (bought used)",
      "url": "https://www.example.com/the-seller-page",
      "freeRpm": 90,
      "countsPerRev": 1993.6,
      "stallCurrentA": 9.2,
      "checked": "2026-10-16"
    }
  ],
  "servos": [
    {
      "id": "team-gate-servo",
      "name": "Our gate servo",
      "rangeDeg": 180,
      "secPer60Deg": 0.12,
      "voltage": 4.8
    }
  ]
}
```

Then `motor.arm = lift, part=team-arm-motor` works like a library part.

**3. Add it to the library's catalog** (for every team): add it to `sim/parts/parts.json` with the seller's link and the date you checked it, run `python3 tools/parts_page.py` to update this page, and open a pull request ([contributing](../contributing.md)).

## What each number is for

| Field | Used for |
|---|---|
| `id` | the name a robot file uses (`part=...`) |
| `freeRpm` | motors: top speed with no load (with `countsPerRev`, the top speed in counts per second) |
| `countsPerRev` | motors: encoder counts per turn of the output shaft (`getCurrentPosition()` steps) |
| `stallCurrentA`, `freeCurrentA` | motors: the current shown in the Control Hub panel |
| `stallTorqueKgCm` | motors: shown here; the simple physics does not use it yet |
| `rangeDeg`, `secPer60Deg`, `byVoltage` | servos: how fast a position change takes |
| `torqueKgCm`, `stallCurrentA`, `continuousCapable` | servos: shown here; not used by the physics |
| `name`, `partNumber`, `seller`, `url`, `checked`, `notes` | people: what it is and where the numbers came from |

## Notes on each part

From the catalog, for checking a number against the seller's page.

- `gobilda-5203-6000`: Bare motor (1:1), no gearbox. 8 mm REX output shaft, 24 mm long. Weight 391g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-1620`: Nominal ratio 3.7:1; gearRatio is the exact value of goBILDA's listed formula (1+(46/17)). 8 mm REX output shaft, 24 mm long. Weight 396g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-1150`: Nominal ratio 5.2:1; gearRatio is the exact value of goBILDA's listed formula (1+(46/11)). 8 mm REX output shaft, 24 mm long. Weight 399g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-435`: Nominal ratio 13.7:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))). 8 mm REX output shaft, 24 mm long. Weight 438g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-312`: Nominal ratio 19.2:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/11))). 8 mm REX output shaft, 24 mm long. Weight 437g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports). Shown 'Sold Out' on 2026-10-16.
- `gobilda-5203-223`: Nominal ratio 26.9:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/11))) * (1+(46/11))). 8 mm REX output shaft, 24 mm long. Weight 437g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-117`: Nominal ratio 50.9:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))) * (1+(46/17)). 8 mm REX output shaft, 24 mm long. Weight 481g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-84`: Nominal ratio 71.2:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))) * (1+(46/11)). 8 mm REX output shaft, 24 mm long. Weight 486g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-60`: Nominal ratio 99.5:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/11))) * (1+(46/11)). 8 mm REX output shaft, 24 mm long. Weight 486g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-43`: Nominal ratio 139:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/11))) * (1+(46/11))) * (1+(46/11)). 8 mm REX output shaft, 24 mm long. Weight 485g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5203-30`: Nominal ratio 188:1; gearRatio is the exact value of goBILDA's listed formula (((((1+(46/17))) * (1+(46/17))) * (1+(46/17))) * (1+(46/17))). 8 mm REX output shaft, 24 mm long. Weight 524g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-1620`: Nominal ratio 3.7:1; gearRatio is the exact value of goBILDA's listed formula (1+(46/17)). 8 mm REX output shaft, 80 mm long. Weight 411g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-1150`: Nominal ratio 5.2:1; gearRatio is the exact value of goBILDA's listed formula (1+(46/11)). 8 mm REX output shaft, 80 mm long. Weight 412g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-435`: Nominal ratio 13.7:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))). 8 mm REX output shaft, 80 mm long. Weight 435g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-312`: Nominal ratio 19.2:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/11))). 8 mm REX output shaft, 80 mm long. Weight 458g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-223`: Nominal ratio 26.9:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/11))) * (1+(46/11))). 8 mm REX output shaft, 80 mm long. Weight 458g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-117`: Nominal ratio 50.9:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))) * (1+(46/17)). 8 mm REX output shaft, 80 mm long. Weight 465g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-84`: Nominal ratio 71.2:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/17))) * (1+(46/11)). 8 mm REX output shaft, 80 mm long. Weight 501g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-60`: Nominal ratio 99.5:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/17))) * (1+(46/11))) * (1+(46/11)). 8 mm REX output shaft, 80 mm long. Weight 501 (page omits unit). countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-43`: Nominal ratio 139:1; gearRatio is the exact value of goBILDA's listed formula (((1+(46/11))) * (1+(46/11))) * (1+(46/11)). 8 mm REX output shaft, 80 mm long. Weight 501g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `gobilda-5204-30`: Nominal ratio 188:1; gearRatio is the exact value of goBILDA's listed formula (((((1+(46/17))) * (1+(46/17))) * (1+(46/17))) * (1+(46/17))). 8 mm REX output shaft, 80 mm long. Weight 543g. countsPerRev is goBILDA's 'Encoder Resolution ... PPR at the Output Shaft' (= 28 x ratio; these are the counts the FTC SDK reports).
- `rev-hd-hex`: Page: 6000 rpm, 0.105 Nm stall, 8.5 A stall, 400 mA no-load, 28 counts/rev at the motor, 15 W max. Out of stock on 2026-10-16. Note: REV-41-1301 is the 40:1 spur version, not the bare motor. Since 1 Jan 2025 the bare motor ships with the UltraPlanetary pinion, which does NOT fit the 20:1/40:1 spur gearboxes (REV-41-1064/-1065).
- `rev-hd-hex-up3`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm (6000/ratio) and stall torque (0.105 Nm x ratio, no gearbox loss, the same method REV's own table uses) are COMPUTED, not listed by REV. Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up4`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm (6000/ratio) and stall torque (0.105 Nm x ratio, no gearbox loss, the same method REV's own table uses) are COMPUTED, not listed by REV. Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up5`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm and stall torque (0.55 Nm) from REV's UltraPlanetary table (https://docs.revrobotics.com/duo-build/motion/motors/hd-hex-motor). Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up12`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm (6000/ratio) and stall torque (0.105 Nm x ratio, no gearbox loss, the same method REV's own table uses) are COMPUTED, not listed by REV. Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up15`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm (6000/ratio) and stall torque (0.105 Nm x ratio, no gearbox loss, the same method REV's own table uses) are COMPUTED, not listed by REV. Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up20`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm and stall torque (1.98 Nm) from REV's UltraPlanetary table (https://docs.revrobotics.com/duo-build/motion/motors/hd-hex-motor). Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-up60`: Actual ratio from REV cartridge pages / https://docs.revrobotics.com/rev-crossover-products/ultraplanetary/cartridge-details: 3:1=84:29 (2.89), 4:1=76:21 (3.61), 5:1=68:13 (5.23). countsPerRev COMPUTED = 28 x actual ratio. freeRpm and stall torque (5.75 Nm) from REV's UltraPlanetary table (https://docs.revrobotics.com/duo-build/motion/motors/hd-hex-motor). Currents are the bare HD Hex motor's. Sold as kit REV-41-1600 (motor + 3:1, 4:1, 5:1 cartridges) or cartridges separately.
- `rev-hd-hex-40`: Page marks it Discontinued but in stock on 2026-10-16: 150 rpm, 594.7 oz-in (4.2 Nm). countsPerRev 1120 from https://docs.revrobotics.com/duo-control/sensors/encoders/motor-based-encoders. Actual (vs nominal 40:1) ratio not published. Currents from the bare motor page (REV-41-1291).
- `rev-hd-hex-20`: Product page www.revrobotics.com/rev-41-1298/ returns Not Found on 2026-10-16; values from REV docs (https://docs.revrobotics.com/duo-build/motion/motors/hd-hex-motor: 300 rpm, 297.4 oz-in / 2.1 Nm) and https://docs.revrobotics.com/duo-control/sensors/encoders/motor-based-encoders (560 counts). The 20:1 gearbox alone (REV-41-1064) is listed Discontinued, and the current bare HD Hex pinion does not fit it. Kept for older robots only.
- `rev-core-hex`: Page: 125 RPM, 3.2 N-m stall, 4.4 A stall, 72:1, 4 counts/rev at the motor, 288 at the output. Free (no-load) current NOT given on the page -> null. 5 mm female hex output, 90-degree layout.
- `am-neverest-orbital-1`: Page gives ratio 1:1 and speed only as '~6600 RPM' (approximate). Stall current 11 A and 14 W max power are listed for the family. Stall torque and free current NOT on the page -> null. countsPerRev DERIVED: page says 7 pulses/rev at the motor, 'multiply the ratio by 7'; x4 quadrature = 28 x ratio (same convention as AndyMark's Classic 40 page: 7 ppr <-> 1120 ticks). Exact ratio not published. Part numbers: variants by power connector (a = Anderson Powerpole, b = JST-VH-2). 6 mm D shaft.
- `am-neverest-orbital-3-7`: Page gives ratio 3.7:1 and speed only as '~1784 RPM' (approximate). Stall current 11 A and 14 W max power are listed for the family. Stall torque and free current NOT on the page -> null. countsPerRev DERIVED: page says 7 pulses/rev at the motor, 'multiply the ratio by 7'; x4 quadrature = 28 x ratio (same convention as AndyMark's Classic 40 page: 7 ppr <-> 1120 ticks). Exact ratio not published. Part numbers: variants by power connector (a = Anderson Powerpole, b = JST-VH-2). 6 mm D shaft.
- `am-neverest-orbital-20`: Page gives ratio 19.2:1 and speed only as '~344 RPM' (approximate). Stall current 11 A and 14 W max power are listed for the family. Stall torque and free current NOT on the page -> null. countsPerRev DERIVED: page says 7 pulses/rev at the motor, 'multiply the ratio by 7'; x4 quadrature = 28 x ratio (same convention as AndyMark's Classic 40 page: 7 ppr <-> 1120 ticks). Exact ratio not published. Part numbers: variants by power connector (a = Anderson Powerpole, b = JST-VH-2). 6 mm D shaft.
- `am-neverest-orbital-50`: Page gives ratio 50.9:1 and speed only as '~130 RPM' (approximate). Stall current 11 A and 14 W max power are listed for the family. Stall torque and free current NOT on the page -> null. countsPerRev DERIVED: page says 7 pulses/rev at the motor, 'multiply the ratio by 7'; x4 quadrature = 28 x ratio (same convention as AndyMark's Classic 40 page: 7 ppr <-> 1120 ticks). Exact ratio not published. Part numbers: variants by power connector (a = Anderson Powerpole, b = JST-VH-2). 6 mm D shaft.
- `am-neverest-orbital-264`: Page gives ratio 263.7:1 and speed only as '~25 RPM' (approximate). Stall current 11 A and 14 W max power are listed for the family. Stall torque and free current NOT on the page -> null. countsPerRev DERIVED: page says 7 pulses/rev at the motor, 'multiply the ratio by 7'; x4 quadrature = 28 x ratio (same convention as AndyMark's Classic 40 page: 7 ppr <-> 1120 ticks). Exact ratio not published. Part numbers: variants by power connector (a = Anderson Powerpole, b = JST-VH-2). 6 mm D shaft.
- `am-neverest-classic-40`: Tagged Discontinued but purchasable on 2026-10-16. Page: 160 RPM no-load, stall 1.823 ft-lb (2.472 N-m), stall 11.5 A, ticks/rev 1120. The page lists BOTH 'Free Current 0.5 A' and 'No Load Current 0.4 A' (conflict); 0.4 A used. 'Ratio' printed as '40:01:00' (spreadsheet typo).
- `am-neverest-classic-60`: Tagged Discontinued but purchasable on 2026-10-16. Page: 105 RPM no-load, stall 2.734 ft-lb (3.707 N-m), stall 11.5 A, free current 0.5 A, ticks/rev 1680.
- `gobilda-2000-torque`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 300:1, weight 60 g, PWM 500-2500 us (0.150 deg/us); continuous mode PWM 900-2100 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `gobilda-2000-speed`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 135:1, weight 58 g, PWM 500-2500 us (0.15 deg/us); continuous mode PWM 1000-2000 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `gobilda-2000-super-speed`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 67:1, weight 58 g, PWM 500-2500 us (0.15 deg/us); continuous mode PWM 1000-2000 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `gobilda-2000-5turn-torque`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 300:1, weight 64 g, PWM 500-2500 us (0.90 deg/us); continuous mode PWM 900-2100 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `gobilda-2000-5turn-speed`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 135:1, weight 60 g, PWM 500-2500 us (0.90 deg/us); continuous mode PWM 900-2100 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `gobilda-2000-5turn-super-speed`: Headline values at 6.0 V; byVoltage has goBILDA's 4.8/6.0/7.4 V numbers. Gear ratio 67:1, weight 58 g, PWM 500-2500 us (0.90 deg/us); continuous mode PWM 1000-2000 us. Continuous rotation is switched on with the 3102 Series Dual Mode Servo Programmer (sold separately).
- `rev-smart-servo`: Product page www.revrobotics.com/rev-41-1097/ returns Not Found on 2026-10-16 (replaced by Smart Robot Servo V2); values from REV's SRS docs page. Default 270 deg over the full pulse range; modes: Servo Limit Mode (custom angular limits) and Continuous Mode, set with the SRS Programmer (sold separately). Voltage 4.8-7.4 V (6 V typical); only 6 V performance given. Docs list gears as brass in the table but 'metal gears' in the features; a search summary of the old product page said 0.14 s/60 deg - NOT verified (page gone).
- `rev-smart-servo-v2-balanced`: Steel gears, 62 g, 4.8-7.4 V (6 V nominal), 500-2500 us. Default 270 deg; up to 280 deg programmable in angular mode; continuous mode. Modes set with the SRS Programmer (sold separately). No 4.8 V figures and no free current on the page.
- `rev-smart-servo-v2-ultraspeed`: Steel gears, 60 g, 4.8-7.4 V (6 V nominal), 500-2500 us. Default 270 deg; up to 280 deg programmable in angular mode; continuous mode. Modes set with the SRS Programmer (sold separately). No 4.8 V figures and no free current on the page.
- `axon-max-mk2`: Specs from Axon's docs (https://docs.axon-robotics.com/servos/max; product page https://axon-robotics.com/products/max has no spec table). 85 g, H25T spline, 4.8-8.4 V, 500-2500 us, 360 deg max range; modes: servo, continuous rotation (swerve 'coming soon'), set with Programmer MK2. Axon lists 4.8 V as the REV Control/Expansion Hub servo voltage and 6.0 V as the REV Servo Power Module/Servo Hub. No part number published (sold only through distributors) -> null. 'Idle' current is in freeCurrentA.
- `axon-max-plus`: Axon's archive page says the MAX+ is discontinued and replaced by the MAX MK2; its spec table is identical to the MK2's. Dual mode (servo/continuous) if bought after 25 Sep 2022. Range of motion NOT given on the archive page -> null. No part number published -> null.
- `axon-mini-mk2`: Specs from Axon's docs (https://docs.axon-robotics.com/servos/mini; product page https://axon-robotics.com/products/mini has no spec table). 65 g, H25T spline, 4.8-8.4 V, 500-2500 us, 360 deg max range; modes: servo, continuous rotation (swerve 'coming soon'), set with Programmer MK2. Axon lists 4.8 V as the REV Control/Expansion Hub servo voltage and 6.0 V as the REV Servo Power Module/Servo Hub. No part number published (sold only through distributors) -> null. 'Idle' current is in freeCurrentA.
- `axon-mini-plus`: Axon's archive page says the MINI+ is discontinued and replaced by the MINI MK2; its spec table is identical to the MK2's. Dual mode (servo/continuous). Range of motion NOT given on the archive page -> null. No part number published -> null.

Catalog notes: Every number was read from the seller's product page or the seller's own documentation on the 'checked' date (URL per part). Values marked COMPUTED/DERIVED in a part's notes are worked out from the seller's numbers. null = the seller does not publish it. Motor values at 12 V; servo headline values at 6.0 V with the full table in byVoltage. countsPerRev = encoder counts per output-shaft revolution as the FTC SDK reports them (x4 quadrature). Torque converted to kg-cm with 1 N-m = 10.197 kg-cm where the seller gives N-m. A part added here is also added to library/.../robot/Parts.java (wheels, servos) or Motors.java (motors); PartsCatalogTest and MotorsCatalogTest check they match.

# Tuning

Find your robot's numbers in the right order, then fix what you see on the field.

Every robot program is full of numbers: where a servo opens, how high a lift may go, how fast a flywheel
spins, how big the wheels really are. When you guess them, you waste practice time and break parts. The
**finders** measure them for you and show the lines to copy into `Tunables.java` and `Robot.java`.

## The tuning order

Go from top to bottom. Skip a tool for a part your robot does not have: the **Tune and check** menu leaves
it out.

```mermaid
flowchart TB
    subgraph G1["Wiring"]
        H["Wiring checks<br/>devices, wheels, encoders"]
    end
    subgraph G2["Parts that move"]
        direction LR
        S["Servo Finder<br/>ends + positions"] --> HO["Hood Finder<br/>two stops"] --> M["Motor Finder<br/>limits + positions"]
    end
    subgraph G3["The launcher"]
        direction LR
        F["Flywheel Finder<br/>gains + recovery"] --> FS["Feed Stop Finder<br/>hold + release"]
    end
    subgraph G4["Driving"]
        direction LR
        D["Drive Tuner<br/>wheel size + strafe"] --> O["Odometry Tuner<br/>pods + offsets"]
    end
    subgraph G5["Scoring"]
        direction LR
        L["Camera lighting<br/>a setup, not a tool"] --> A["Aim Tuner<br/>camera offset"] --> T["Shot Tuner<br/>shot table"] --> CF["Camera Fix Check<br/>alpha"]
    end
    subgraph G6["Last"]
        direction LR
        P["Piece Tuner<br/>sensors"] --> C["Characterizer<br/>how it moves"]
    end
    G1 --> G2 --> G3 --> G4 --> G5 --> G6
```

| # | Page | It finds | Why it comes here |
| --- | --- | --- | --- |
| 1 | [Before you tune: Tune and check](01-tune-and-check.md) | nothing: the menu, safety, help | read it once |
| 2 | [Wiring checks](02-wiring-checks.md) | Hardware Check, Motor Direction, Encoder | every other number trusts the wiring, so it comes first |
| 3 | [Servo Finder](03-servo-finder.md) | ends and positions | a servo pushed past its end breaks, so find the ends before code moves it |
| 4 | [Hood Finder](04-hood-finder.md) | a hood's two stops | the Shot Tuner writes hood positions, so the hood needs its stops first |
| 5 | [Motor Finder](05-motor-finder.md) | limits and positions | a wrong limit breaks parts, so do it before you drive a mechanism with code |
| 6 | [Flywheel Finder](06-flywheel-finder.md) | gains and recovery | the shot table is only true when the wheel holds its speed |
| 7 | [Feed Stop Finder](07-feed-stop-finder.md) | hold and release | the launcher must feed one piece at a time before you tune shots |
| 8 | [Drive Tuner](08-drive-tuner.md) | wheel size and strafe | autonomous moves need the right distances. Autonomous stays off until it passes |
| 9 | [Odometry Tuner](09-odometry-tuner.md) | pod directions and offsets | `driveTo` and the shot distance need a true pose. Path steps stay off until it passes |
| 10 | [Camera lighting](10-camera-lighting.md) (a setup, not a tool) | the camera setting for each kind of light | the camera must see the tags well before you aim or tune shots |
| 11 | [Aim Tuner (own file)](11-aim-tuner.md) | camera offset | aim first, so you build the shot table with straight shots |
| 12 | [Shot Tuner](12-shot-tuner.md) | the shot table | it needs a steady wheel, straight aim and a true distance: everything above |
| 13 | [Camera Fix Check (alpha)](13-camera-fix-check.md) | the camera's turn | only with a `cameraFix(camera)` line |
| 14 | [Piece Tuner](14-piece-tuner.md) | what each sensor sees | nothing moves, so it can also come earlier |
| 15 | [Characterizer (own file)](15-characterizer.md) | how the motors really move | it needs a robot that drives and lifts that work, so it comes last |

**After the finders:**

- [The tuning menu](16-tuning-menu.md): change a number while the robot runs, from the gamepad.
- [Fix it by what you see](17-fix-by-symptom.md): "the lift sags" -> which number to change, and which way.

---

**Next:** [Before you tune: Tune and check](01-tune-and-check.md).

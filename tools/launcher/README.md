# The launcher program

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

`launcher_lab.py` works out how a robot's flywheel launcher throws, from the robot's CAD, and writes a shot table
for the simulator (`shoot.NAME = ..., table=FILE`, the path from the robot file; docs/guides/simulator.md, "How shots fly, and shot tables").

1. Read the CAD with `tools/cad/robot_cad.py read` and make its `frame.json` (skills/robot-file/cad.md).
2. Write `LAUNCHER.json` (the format is at the top of `launcher_lab.py`): which parts are the wheel, where the ball
   comes in, the motor speeds to table, and `"picture": "launcher.svg"`.
3. Run it: `python tools/launcher/launcher_lab.py LAUNCHER.json` (a minute or two).
4. Look at the picture: the grey lines are the parts the ball can touch in its plane, the blue circle the wheel, the
   orange dot where the ball starts, green paths shots, red paths feeds that did not become shots. If the start is
   inside a part (it warns), or the paths go somewhere the real ball does not, fix `entry` and `wheelTurn`.
5. Ask the team which way the wheel turns when both ways shoot (it warns), and compare the table with a few filmed
   shots.

The ball model and its ranges (stiffness, grip) are assumptions until someone measures them: a kitchen-scale
squeeze test for stiffness, and filmed shots for the rest. Needs the same Python as tools/cad (build123d, numpy).

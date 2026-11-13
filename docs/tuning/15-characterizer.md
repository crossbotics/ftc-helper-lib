# Characterizer: how the robot really moves

Measure how each motor really behaves, for feedforward and for the simulator.

**In Tune and check:** no. It has its own file: it needs your lifts.
**You need:** its own file, a charged battery, 5 ft of clear floor.

## Get to the Characterizer

1. On the Driver Hub, open the TeleOp list and pick **Characterizer** (group "Finders"). Press **INIT**.

   ![The Driver Hub's TeleOp list with Characterizer under Finders, then press INIT](../images/hub-characterizer-go-opmode.svg)

2. The Characterizer's INIT screen shows. Nothing moves yet. Clear the floor, then press **▶** (start) on
   the Driver Hub.

   ![The Characterizer's INIT screen: Drive test: about 5 ft clear in front and behind. Press start](../images/hub-characterizer-go-start.svg)

## Steps

Run the **Characterizer** last, when the robot drives and its lifts work. It measures how each motor really
behaves: the power to start moving, the power per speed, how fast it speeds up, and for a lift the power
that holds it against gravity. You use these numbers in two places: for feedforward in your code (see
[Feedforward](../modules/control.md#feedforward)), and as real numbers for the
[simulator](../guides/simulator.md#real-numbers-the-characterizer).

1. In `Characterizer.java`, list your lifts/arms with soft limits a little inside the hard stops
   (counts from the bottom), and your flywheels.
2. Drive test: about 5 ft clear in front and behind, on tiles. Turn test: room to turn.
3. Lifts start at the BOTTOM. An arm's high limit stays below straight up.
4. The D-pad chooses a test, and **A** runs it.

   ![Press D-pad, then A: the arrow on Drive (forward and back)](../images/hub-characterizer-step-choose.svg)

5. **B** stops the test now. The tool stops by itself when something turns the wrong way, goes past a
   limit, or pushes without moving.

   ![While a test runs, the screen shows Drive (forward and back): ramp + (B = stop). To stop it now, press B](../images/hub-characterizer-step-stop.svg)

6. Run each test 2-3 times on a charged battery.

   ![Press A to run it again: the Driver Hub shows Drive (forward and back): done. Fit r2 0.981 (good)](../images/hub-characterizer-step-again.svg)

Details: [finders module](../modules/finders.md#characterizeropmode).

## Fix a mistake

- **Stop a test:** press **B** (or **GUIDE**). The robot stops, and that test gives no numbers.
- **A bad test** (the robot hit something, or the lift was not at the bottom): run the same test again with
  **A**. Its new lines replace the old ones.

**Start over:** press STOP, then INIT, and open the tool again. It starts empty: nothing carries over. The lines saved at STOP stay in the results file, the newest at the end.

## Put the numbers in your code

The robot uses `Tunables.java` and `Robot.java`, not the finder's screen. Take a photo of the screen,
and type each line into the file. Or copy it from the [results file](01-tune-and-check.md#get-the-results-file-on-the-laptop):

| The finder shows | Put it in |
| --- | --- |
| the feedforward numbers | your code ([Feedforward](../modules/control.md#feedforward)) |
| the motor numbers | the simulator's robot file ([real numbers](../guides/simulator.md#real-numbers-the-characterizer)) |

## Done when

each test ran 2-3 times and the numbers agree.

---

**Next:** [Live tuning: change numbers while the robot runs](16-live-tuning.md).

**Look it up:** [CharacterizerOpMode](../modules/finders.md#characterizeropmode).

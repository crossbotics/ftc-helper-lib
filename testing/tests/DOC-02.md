---
id: DOC-02
title: Follow one "Add to your robot" page from start to end
area: docs
priority: medium
where: both
minutes: 90
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.16.0
docs: [add-to-robot/README.md]
---

## Why

Each "Add to your robot" page adds one part to your own robot, a few lines at a time. We check the
pages on our robots. Yours will show where a page assumes something about the robot that is not true
for you.

## Safety

- Follow the page's own safety notes (its "Check it" step). Low power for the first runs.

## Setup

- The page's "You need" list done (it says which).
- Pick the part your team needs next ([Add to your robot](../../docs/add-to-robot/README.md)).

## Steps

1. Write the page name. Start a timer.
2. Read the page top to bottom once without changing code.
3. Do it: make each change in your own five files, as the page says.
4. Do its "Check it" step, in the simulator first when the page has a simulator part.
5. Each time you are stuck for more than 2 minutes, write the section and the words.
6. Stop the timer when its last check passes.

## Pass rule

Every check in the page's "Check it" step passes (all of them, for example 5 of 5), and every
"Done when" line is true.

## Record

- page name, time it took (minutes)
- each place you got stuck (section and words)
- any code on the page that did not build (copy the error)
- what your robot has that the page's example does not (or the other way round)

## If your robot is different

The pages explain with example code and example robots. Write what you had to change because your
robot is different: that is the most useful part of the report.

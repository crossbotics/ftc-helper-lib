---
id: CTL-02
title: Move or free the library's buttons
area: controls
priority: medium
where: both
minutes: 25
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [guides/buttons.md#moving-the-librarys-buttons, tutorials/start/03-buttons.md#7-pause-with-back-and-the-other-library-buttons]
---

## Why

Every library button (slow, field-centric, pause, menus) can move to another button or be freed for
your own job. INIT must catch two jobs on one button and say which button.

## Safety

- Real robot: low power. Check that SOME button still pauses the robot before you drive.

## Setup

- Your `Controls.java`. Inside its class, just after the closing `}` of the `buttons(...)` method, add `@Override protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) { }` (the whole method is in the [buttons guide](../../docs/guides/buttons.md#moving-the-librarys-buttons)), and `import com.crossbotics.ftchelper.teleop.LibraryButtons;` at the top of the file.

## Steps

1. Move one library job your drivers want elsewhere, for example `lib.slow(g1.rt);` or `lib.fieldCentric(g1.x);`.
2. INIT: read the button map. The job is on its new button; the old one is listed as free.
3. START and check the job works on the new button and the old one does nothing.
4. Put one of your own jobs on the same button as a library job. INIT must stop and name the button.
5. Free the pause: `lib.pause(null);`. INIT must warn that nothing pauses the robot. Put it back.

## Pass rule

The moved job works on its new button (1 of 1), step 4 stops INIT with the button's name, and step 5
shows the warning.

## Record

- the lines you wrote in `libraryButtons`
- the INIT messages from steps 4 and 5 (copy them)
- whether the button map was easy to read

## If your robot is different

Move whatever your drivers really want moved: that is the best test. One driver (`oneDriver = true`):
library jobs you put on `g2` go on gamepad 1: check that.

# Possible later work

Ideas the mentor has put off on purpose. Not planned yet: each needs the mentor's go-ahead before
anyone starts. Newest at the end. When one is done or dropped, remove it here and say so in the
CHANGELOG.

| Idea | Why it waits | Notes |
| --- | --- | --- |
| **Swerve drive support** (deferred 2026-10-21) | No seller makes FTC swerve pods; a team must build them first, and the work cannot be tested on a robot before that. | Plan, options and effort: [swerve.md](swerve.md). Start with option 2 when a team has a working prototype pod. |
| **Road Runner module** | Pedro Pathing came first; Road Runner can plug in the same way (`PathFollower`). | Same pattern as the `pedro` folder: one Robot.java line, `follow` steps, our odometry. |
| **`alongside` that ends at a path distance** | Asked for by the FTC-veteran review ("Alex") for cycle autonomous. | E.g. start the shooter 12 in before a path ends, or at the join between two parts. |
| **Live tuning of `PedroConstants`** | Pedro reads its numbers at INIT; changing them needs a rebuild today. | Would need Pedro's config values in the tuning menu; copy-back to the file as for every tuned value. |
| **Pose and path drawing on the real robot** | FTC Dashboard and similar tools are banned at events this season (R704.D), so only for practice. | Last season's `DecodeFieldDrawing`. The simulator and the path builder cover most of the need. |
| **Camera positions mixed into odometry** | BIOBUZZ's AprilTags are on moving HIVE cells, so not this season. | `Odometry.custom(...)` is the hook for a later season with fixed tags. |

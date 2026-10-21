# Keep the library up to date

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Your robot project remembers ONE exact version of the library (one git commit).
It does not change by itself. This is good: the robot never changes the night before an event
because someone changed the library. You choose when to update.

All commands run in the **Terminal** tab of Android Studio, in the robot project folder.

## See which version you have

```bash
git submodule status
```

It prints the commit and, if there is one, the version tag, for example
` 3f2c1a9 ftc-helper-lib (v0.1.0)`.

The [CHANGELOG](../CHANGELOG.md) says what changed in each version.

## Get the newest version

```bash
git submodule update --remote ftc-helper-lib
```

Then:

1. **Sync** Gradle (the yellow bar, or **File > Sync Project with Gradle Files**).
2. **Build** (hammer icon). Fix anything the CHANGELOG says changed.
3. **Test on the robot.**
4. Commit, so the whole team gets the same version:

```bash
git add ftc-helper-lib
git commit -m "Update ftc-helper-lib"
```

Teammates then run `git pull` and `git submodule update` to get it.

## Use a specific version

Each release has a tag like `v0.1.0`. To use exactly that one:

```bash
cd ftc-helper-lib
git fetch --tags
git checkout v0.1.0
cd ..
git add ftc-helper-lib
git commit -m "Use ftc-helper-lib v0.1.0"
```

Use this to go back to an older version if a new one causes a problem.

## Rule of thumb for the season

- **Before an event:** do not update. Use the version you tested.
- **After an event:** update, test, commit.

## After a teammate updates it

`git pull` gets their commit, but your `ftc-helper-lib` folder still has the old files until you run:

```bash
git submodule update
```

If `git status` shows `modified: ftc-helper-lib (new commits)` and you did not mean to change it,
this command also puts it back to the version the project expects.

## A new FTC SDK version

The FTC SDK changes every season (and sometimes during one). The library is built against one SDK
version (now **12.0.0**, the 2026-27 release, set in `library/build.gradle`), but your robot app always uses the SDK
version in YOUR project's `build.dependencies.gradle`: the library is only compiled against 12.0.0 and
brings no SDK of its own.

SDK 12.0 changed AprilTag detections (single tags and "clusters"). The library's `WebcamTags` is written
for the SDK 12 types, so its vision classes need SDK 12 or newer; your own AprilTag code may need the
fix in the SDK's release notes.

So after you update the SDK:

1. Build. If it builds and works, you are done.
2. If the library does not build with the new SDK, check for a newer library version
   (`git submodule update --remote ftc-helper-lib`).
3. Still broken? Report it, or fix it: [Contributing](contributing.md).

## Change the library from inside the robot project

You can edit the library's files right in Android Studio. But those changes are inside the
submodule, so they need their own commit and push to the library repository.
Follow [Contributing](contributing.md). If you only change it in your robot project, the change
is lost the next time someone updates.

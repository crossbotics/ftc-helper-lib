#!/usr/bin/env python3
"""Makes a release in steps that stop at the first problem. Run from the repo root.

    python3 tools/release.py prepare vX.Y.Z           CHANGELOG heading, the tests' "since", the catalogue
      (then read the changes, run the tests and commit them)
    python3 tools/release.py publish vX.Y.Z notes.md  checks, tag, push, GitHub release, docs-live
    python3 tools/release.py check vX.Y.Z             only the checks of what the release holds (GitHub runs this on a tag)
    python3 tools/release.py check-live vX.Y.Z        only: does docs-live hold the same files as the tag?

If publish stops part way, fix the problem and run the same command: it goes on with the steps not done yet.
publish --dry-run prints the outward steps and does none. See docs/contributing.md, "Releasing a version".
"""
import datetime
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "CHANGELOG.md"
TESTS = ROOT / "testing/tests"
# The parts of a GitHub release's notes, in their order (docs/contributing.md).
NOTES_PARTS = ["What's new", "Before you update", "Full change list"]
# Every check GitHub runs that needs no Gradle.
CHECKS = [["python3", "tools/check_docs.py"], ["python3", "tools/parts_page.py", "--check"], ["python3", "tools/check_blocking.py"],
          ["python3", "tools/check_tunables.py"], ["node", "tools/check_builder.mjs"], ["python3", "tools/check_tests.py"]]


def stop(words):
    sys.exit("release: " + words)


def git(*args):
    r = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    if r.returncode != 0:
        stop(f"git {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout.strip()


def git_ok(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True).returncode == 0


def run(*args):
    if subprocess.run(list(args), cwd=ROOT).returncode != 0:
        stop(f"{' '.join(args)} failed. Fix it and run this step again.")


def check_version(version):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", version):
        stop(f"the version must look like v0.23.0, not {version}")


def unreleased_lines(text):
    """The lines of the "Unreleased" part with words on them; None if there is no such heading."""
    m = re.search(r"^## Unreleased[^\n]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if m is None:
        return None
    # Its "### Added" headings and its bold titles are always there: only the rest is a change.
    return [line for line in m.group(1).splitlines()
            if line.strip() and not line.startswith("#") and not re.fullmatch(r"\*\*[^*]+\*\*", line.strip())]


def release_problems(version):
    """What is wrong with the files of this release, as words ([] = nothing)."""
    out = []
    text = CHANGELOG.read_text()
    if not re.search(r"^## %s - \d{4}-\d{2}-\d{2}\s*$" % re.escape(version), text, re.M):
        out.append(f'CHANGELOG.md has no heading "## {version} - YYYY-MM-DD": the Update button would show no changes')
    if unreleased_lines(text):
        out.append('CHANGELOG.md still has words under "Unreleased": they belong to a version')
    for test in sorted(TESTS.glob("*.md")):
        if re.search(r"^since:\s*unreleased\s*$", test.read_text(), re.M):
            out.append(f"{test.relative_to(ROOT)} still says since: unreleased")
    return out


def notes_problems(text):
    """What is missing from a GitHub release's notes."""
    places = [text.find(part) for part in NOTES_PARTS]
    out = [f'the notes have no "{part}" part' for part, place in zip(NOTES_PARTS, places) if place < 0]
    found = [place for place in places if place >= 0]
    if not out and found != sorted(found):
        out.append(f"the notes' parts must be in this order: {', '.join(NOTES_PARTS)}")
    return out


def prepare(version):
    check_version(version)
    if git("status", "--porcelain"):
        stop("the working folder has changes: commit them first, so this step's changes stand alone")
    if git_ok("rev-parse", "--verify", "--quiet", f"refs/tags/{version}"):
        stop(f"{version} is already a tag")
    text = CHANGELOG.read_text()
    words = unreleased_lines(text)
    if words is None:
        stop('CHANGELOG.md has no "## Unreleased" heading')
    if not words:
        stop('nothing is under "Unreleased" in CHANGELOG.md: there is nothing to release')
    # Before any file changes, so a test list with a problem stops here and this step can simply run again.
    run("python3", "tools/check_tests.py")
    today = datetime.date.today().isoformat()
    CHANGELOG.write_text(re.sub(r"^## Unreleased[^\n]*$", f"## {version} - {today}", text, count=1, flags=re.M))
    run("python3", "tools/check_tests.py", "--set-since", version)
    problems = release_problems(version)
    if problems:
        stop("; ".join(problems))
    print(f"Prepared {version}. Now read the changes, run the tests (see AGENTS.md), commit, and run:")
    print(f"  python3 tools/release.py publish {version} notes.md")


def check(version):
    check_version(version)
    problems = release_problems(version)
    if problems:
        stop("; ".join(problems))
    print(f"{version}: the changelog and the test list are ready.")


def check_live(version):
    check_version(version)
    git("fetch", "--quiet", "origin", "docs-live")
    tag_tree = git("rev-parse", f"{version}^{{tree}}")
    live_tree = git("rev-parse", "FETCH_HEAD^{tree}")
    if tag_tree != live_tree:
        stop(f"docs-live does not hold the same files as {version}: the site's docs would not match the release")
    print(f"docs-live holds the same files as {version}.")


def publish(version, notes_path, dry_run):
    """Each outward step looks first if it is done, so after a failure this same command goes on from there."""
    check_version(version)
    notes = pathlib.Path(notes_path).resolve()
    if not notes.is_file():
        stop(f"no notes file at {notes_path}")
    problems = notes_problems(notes.read_text())
    if problems:
        stop("; ".join(problems))
    if git("status", "--porcelain"):
        stop("the working folder has changes: commit them first")
    if git("rev-parse", "--abbrev-ref", "HEAD") != "main":
        stop("a release is made from main")
    git("fetch", "--quiet", "origin", "main", "docs-live")
    if not git_ok("merge-base", "--is-ancestor", "origin/main", "HEAD"):
        stop("origin/main has commits this checkout does not: pull them first")
    tagged = git_ok("rev-parse", "--verify", "--quiet", f"refs/tags/{version}")
    if tagged and git("rev-parse", f"{version}^{{commit}}") != git("rev-parse", "HEAD"):
        stop(f"{version} is a tag on another commit. A release this script started goes on from that commit: check it out")
    check(version)
    for c in CHECKS:
        run(*c)
    if dry_run:
        print(f"Dry run: all checks pass. {'The tag exists: it would go on with the steps not done yet' if tagged else f'It would tag {version}'}: "
              "push main and the tag, make the GitHub release, and move docs-live.")
        return
    if not tagged:
        git("tag", "-a", version, "-m", version)
    # One push for both, so a tag never lands without its commit. A push of what is already there does nothing.
    git("push", "--atomic", "origin", "main", version)
    seen = subprocess.run(["gh", "release", "view", version, "--json", "isDraft", "-q", ".isDraft"], cwd=ROOT, capture_output=True, text=True)
    if seen.returncode == 0:
        if seen.stdout.strip() == "true":
            stop(f"the GitHub release {version} is a draft, so teams get no message. Publish it on GitHub, then run this again")
        print(f"The GitHub release {version} is already there.")
    else:
        run("gh", "release", "create", version, "--verify-tag", "--title", version, "--notes-file", str(notes))
    if git("rev-parse", f"{version}^{{tree}}") == git("rev-parse", "origin/docs-live^{tree}"):
        print("docs-live already holds the release.")
    else:
        # A merge that holds exactly the tag's files, not a force push: docs-live keeps its history, and the
        # site's sync-docs accepts it (the tag is an ancestor).
        commit = git("commit-tree", f"{version}^{{tree}}", "-p", "origin/docs-live", "-p", f"{version}^{{commit}}",
                     "-m", f"docs-live: start from {version}")
        git("push", "origin", f"{commit}:refs/heads/docs-live")
    check_live(version)
    print(f"Released {version}. The site is separate: deploy it with its scripts/deploy.sh.")


def main(args):
    dry_run = "--dry-run" in args
    args = [a for a in args if a != "--dry-run"]
    if len(args) == 2 and args[0] == "prepare":
        prepare(args[1])
    elif len(args) == 2 and args[0] == "check":
        check(args[1])
    elif len(args) == 2 and args[0] == "check-live":
        check_live(args[1])
    elif len(args) == 3 and args[0] == "publish":
        publish(args[1], args[2], dry_run)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])

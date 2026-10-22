#!/usr/bin/env bash
# Makes (or updates) the GitHub labels the issue forms and the triage use. Run it once, from the repo
# root, by someone with write access to the repository (a mentor):
#
#   tools/make_labels.sh                      # the repo of the current folder
#   tools/make_labels.sh crossbotics/ftc-helper-lib
#
# It needs the GitHub CLI (gh), signed in (gh auth login). --force updates a label that is already there,
# so running it twice is safe. It never deletes a label.
#
# Why: an issue form only adds labels that exist, and the coverage table on crossbotics.com finds test
# reports by the "test-report" label. The triage helper (.github/scripts/triage-labels.sh) may only use
# the labels listed here.
set -euo pipefail

REPO="${1:-}"
REPO_ARGS=()
if [[ -n "$REPO" ]]; then REPO_ARGS=(--repo "$REPO"); fi

label() {   # label NAME COLOR DESCRIPTION
  gh label create "$1" --color "$2" --description "$3" --force "${REPO_ARGS[@]}"
}

# Added by the forms.
label "test-report"          "1d76db" "A team ran a test from the test list (testing/tests)"
label "docs-feedback"        "0e8a16" "Something wrong, unclear or missing in the docs"
label "bug"                  "d73a4a" "The library or the simulator does something wrong"
label "needs-triage"         "fbca04" "New: nobody has looked at it yet"

# Added by the triage helper (or a mentor).
label "bot-triaged"          "c5def5" "The triage helper checked it: a mentor still reads it"
label "needs-info"           "fbca04" "We asked the reporter for something missing"
label "needs-human"          "b60205" "The triage helper could not decide: a mentor must look"
label "possible-duplicate"   "cfd3d7" "Looks like an earlier issue (linked in a comment)"
label "unknown-test-id"      "e99695" "The test ID is not in the test list"
label "result-pass"          "0e8a16" "Test report: pass"
label "result-fail"          "d93f0b" "Test report: fail"
label "result-partial"       "fbca04" "Test report: some steps passed"
label "result-could-not-run" "bfdadc" "Test report: could not run the test"
label "where-sim"            "c2e0c6" "Ran in the simulator"
label "where-real"           "0052cc" "Ran on a real robot"
label "kit-bot"              "d4c5f9" "Ran on a seller's starter bot"
label "bug-suspect"          "d73a4a" "A test fail that may be a library bug"
label "safety"               "b60205" "Something moved in an unsafe way: read first"
label "privacy-check"        "5319e7" "May contain a student name or a face: a mentor checks"
label "docs-wrong"           "0e8a16" "Docs: does not match the code or the robot"
label "docs-unclear"         "0e8a16" "Docs: hard to follow"
label "docs-missing"         "0e8a16" "Docs: a step or a fact is not there"
label "docs-broken-example"  "0e8a16" "Docs: example code does not build or run"

# One per area of the test list (testing/vocabulary.json "areas"; tools/check_tests.py checks they match).
label "area-setup"           "ededed" "Setup and Hardware Check"
label "area-drive"           "ededed" "Driving"
label "area-odometry"        "ededed" "Odometry"
label "area-paths"           "ededed" "Paths (Pedro)"
label "area-autos"           "ededed" "Autonomous and the INIT menu"
label "area-aim"             "ededed" "Aiming"
label "area-turret"          "ededed" "Turret"
label "area-shooter"         "ededed" "Shooter and shot tables"
label "area-hood"            "ededed" "Hood"
label "area-moving-shot"     "ededed" "Shooting on the move"
label "area-pieces"          "ededed" "Game pieces and sensors"
label "area-lights"          "ededed" "Lights"
label "area-interlocks"      "ededed" "Interlock rules"
label "area-mechanisms"      "ededed" "Lifts, arms, claws and servos"
label "area-controls"        "ededed" "Buttons and controls"
label "area-simulator"       "ededed" "Simulator"
label "area-laptop-tests"    "ededed" "Laptop tests (JUnit and the test kit)"
label "area-docs"            "ededed" "Docs and lessons"

echo "Labels done."

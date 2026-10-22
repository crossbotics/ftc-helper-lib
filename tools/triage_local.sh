#!/usr/bin/env bash
# Runs one of the triage helper's scripts (.github/scripts/triage-*.sh) from your own computer, for
# MANUAL triage (no ANTHROPIC_API_KEY secret needed). It gives the scripts what GitHub Actions would:
# the repository name and the issue number. Needs the GitHub CLI signed in (gh auth login).
#
#   tools/triage_local.sh 12 gh issue view 12 --comments     # read (any issue)
#   tools/triage_local.sh 12 labels --add result-pass --remove needs-triage
#   tools/triage_local.sh 12 comment "Thanks for running AIM-01! ..."
set -euo pipefail
[[ $# -ge 2 && "$1" =~ ^[0-9]+$ ]] || { echo "usage: tools/triage_local.sh ISSUE gh|labels|comment ARGS..." >&2; exit 2; }
ISSUE=$1; WHAT=$2; shift 2
HERE="$(cd "$(dirname "$0")/.." && pwd)"
export GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-crossbotics/ftc-helper-lib}"
EVENT=$(mktemp); trap 'rm -f "$EVENT"' EXIT
printf '{"issue":{"number":%s}}' "$ISSUE" > "$EVENT"
export GITHUB_EVENT_PATH="$EVENT"
case "$WHAT" in
  gh|labels|comment) exec "$HERE/.github/scripts/triage-$WHAT.sh" "$@" ;;
  *) echo "gh, labels or comment" >&2; exit 2 ;;
esac

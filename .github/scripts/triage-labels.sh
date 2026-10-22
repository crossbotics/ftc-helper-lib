#!/usr/bin/env bash
# Adds or removes labels on THE issue that started the workflow (its number comes from the event, not
# from the helper), and only labels listed in tools/make_labels.sh. It cannot close, lock, assign or
# edit an issue, and cannot remove the labels the forms add (test-report, docs-feedback, bug).
#
#   triage-labels.sh --add result-pass --add area-aim --remove needs-triage
set -euo pipefail
ISSUE=$(jq -r '.issue.number // empty' "${GITHUB_EVENT_PATH:?GITHUB_EVENT_PATH not set}")
[[ "$ISSUE" =~ ^[0-9]+$ ]] || { echo "no issue number in the event" >&2; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"
ALLOWED=$(sed -n 's/^label "\([a-z0-9-]*\)".*/\1/p' "$HERE/../../tools/make_labels.sh")
KEEP=" test-report docs-feedback bug "

args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --add|--remove)
      [[ $# -ge 2 ]] || { echo "$1 needs a label" >&2; exit 2; }
      if ! grep -qxF -- "$2" <<<"$ALLOWED"; then echo "skipped unknown label: $2" >&2; shift 2; continue; fi
      if [[ "$1" == "--remove" && "$KEEP" == *" $2 "* ]]; then echo "skipped: $2 cannot be removed" >&2; shift 2; continue; fi
      if [[ "$1" == "--add" ]]; then args+=(--add-label "$2"); else args+=(--remove-label "$2"); fi
      shift 2
      ;;
    *) echo "only --add LABEL and --remove LABEL" >&2; exit 2 ;;
  esac
done
[[ ${#args[@]} -gt 0 ]] || { echo "nothing to do" >&2; exit 0; }
gh issue edit "$ISSUE" --repo "${GITHUB_REPOSITORY:?}" "${args[@]}"

#!/usr/bin/env bash
# Posts ONE comment on THE issue that started the workflow (its number comes from the event). The
# workflow allows one call per run (CLAUDE_CODE_SCRIPT_CAPS). The text is cut at 2500 characters,
# "@" is changed so nobody is pinged, and a footer says it is an automatic helper.
#
#   triage-comment.sh "Thanks! ..."
set -euo pipefail
ISSUE=$(jq -r '.issue.number // empty' "${GITHUB_EVENT_PATH:?GITHUB_EVENT_PATH not set}")
[[ "$ISSUE" =~ ^[0-9]+$ ]] || { echo "no issue number in the event" >&2; exit 1; }
[[ $# -eq 1 && -n "$1" ]] || { echo "usage: triage-comment.sh \"text\"" >&2; exit 2; }
body="${1:0:2500}"
body="${body//@/@ }"
footer=$'\n\n---\n<sub>Automatic first look by the triage helper (it follows .github/triage.md). A mentor reads every report.</sub>'
gh issue comment "$ISSUE" --repo "${GITHUB_REPOSITORY:?}" --body "$body$footer"

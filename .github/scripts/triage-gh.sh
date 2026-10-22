#!/usr/bin/env bash
# READ-ONLY GitHub access for the triage helper (.github/workflows/triage.yml). It runs the GitHub CLI
# for a short list of read commands only, always on this repository:
#
#   triage-gh.sh issue view 12 [--comments]
#   triage-gh.sh issue list [--label L] [--state open|closed|all] [--search "words"] [--limit N]
#   triage-gh.sh search issues "words" [--state open|closed] [--limit N]
#   triage-gh.sh label list
#
# Anything else is refused. Why: the issue text is written by anyone, so the helper must not be able
# to change the repository, read other repositories, or open web pages.
set -euo pipefail
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY not set}"

refuse() { echo "triage-gh.sh: refused: $*" >&2; exit 2; }

# Checks the rest of the arguments against flags that take a value / flags that do not.
check_flags() {   # check_flags "VALUE_FLAGS" "BARE_FLAGS" args...
  local value_flags=" $1 " bare_flags=" $2 "; shift 2
  while [[ $# -gt 0 ]]; do
    if [[ "$value_flags" == *" $1 "* ]]; then
      [[ $# -ge 2 ]] || refuse "$1 needs a value"
      if [[ "$1" == "--limit" && ! "$2" =~ ^[0-9]{1,3}$ ]]; then refuse "--limit must be a number"; fi
      if [[ "$1" == "--state" && ! "$2" =~ ^(open|closed|all)$ ]]; then refuse "--state must be open, closed or all"; fi
      shift 2
    elif [[ "$bare_flags" == *" $1 "* ]]; then
      shift
    else
      refuse "flag not allowed: $1"
    fi
  done
}

case "${1:-} ${2:-}" in
  "issue view")
    [[ "${3:-}" =~ ^[0-9]+$ ]] || refuse "issue view needs an issue number"
    num="$3"; shift 3
    check_flags "" "--comments" "$@"
    exec gh issue view "$num" --repo "$REPO" "$@"
    ;;
  "issue list")
    shift 2
    check_flags "--label --state --search --limit" "" "$@"
    exec gh issue list --repo "$REPO" "$@"
    ;;
  "search issues")
    [[ -n "${3:-}" && "${3:0:1}" != "-" ]] || refuse "search issues needs the words to search for"
    words="$3"; shift 3
    check_flags "--state --limit" "" "$@"
    exec gh search issues "$words" --repo "$REPO" "$@"
    ;;
  "label list")
    shift 2
    check_flags "" "" "$@"
    exec gh label list --repo "$REPO" --limit 200
    ;;
  *)
    refuse "only: issue view, issue list, search issues, label list"
    ;;
esac

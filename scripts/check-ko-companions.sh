#!/usr/bin/env bash
# Checks that every Markdown document has a Korean .ko.md companion (ADR-0004/ADR-0005),
# and flags a companion whose file is older than its English original as *possibly* stale
# (ADR-0007). The staleness check is a heuristic, not a proof, and cannot tell a meaning
# change from a formatting-only touch.
#
# Adopting projects with an existing CI pipeline are expected to run this in CI by default
# (ADR-0009), gating on the missing-companion count. Pass --missing-only in CI: a fresh
# checkout gives every file close to the same mtime, so the staleness heuristic is
# meaningless there and would only produce noise.
#
# Excludes vendored Spec Kit assets (.specify/templates|scripts|workflows|integrations,
# and any .claude/skills/speckit-*/.agents/skills/speckit-* skill) -- upstream content this
# project does not author and that is replaced wholesale on every version bump (ADR-0014).
# .specify/memory/ and any non-speckit-prefixed skill remain mandatory.
set -euo pipefail

cd "$(dirname "$0")/.."

check_stale=1
if [ "${1:-}" = "--missing-only" ]; then
  check_stale=0
fi

missing=0
stale=0

while IFS= read -r -d '' f; do
  ko="${f%.md}.ko.md"
  if [ ! -f "$ko" ]; then
    echo "MISSING companion: $ko"
    missing=$((missing + 1))
  elif [ "$check_stale" -eq 1 ] && [ "$f" -nt "$ko" ]; then
    echo "POSSIBLY STALE (English newer than its .ko.md): $f"
    stale=$((stale + 1))
  fi
done < <(find . \
  \( -path '*/node_modules' -o -path '*/.git' -o -path '*/build' -o -path '*/dist' -o -path '*/out' \
     -o -path '*/.specify/templates' -o -path '*/.specify/scripts' \
     -o -path '*/.specify/workflows' -o -path '*/.specify/integrations' \
     -o -path '*/skills/speckit-*' \) -prune \
  -o -type f -name '*.md' ! -name '*.ko.md' -print0)

echo "$missing missing companion(s), $stale possibly-stale companion(s)."
[ "$missing" -eq 0 ]

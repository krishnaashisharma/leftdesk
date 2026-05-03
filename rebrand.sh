#!/usr/bin/env bash
# LeftDesk Rebranding Script
# Replaces RustDesk references with LeftDesk across the codebase
set -euo pipefail

DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

ROOT="$(cd "$(dirname "$0")" && pwd)"
COUNT=0

do_sed() {
  local pattern="$1" file="$2"
  if $DRY_RUN; then
    grep -n "$pattern" "$file" 2>/dev/null | head -3 | while read -r line; do
      echo "  [DRY] $file: $line"
      COUNT=$((COUNT+1))
    done
  else
    sed -i '' "$pattern" "$file" 2>/dev/null || sed -i "$pattern" "$file" 2>/dev/null || true
    COUNT=$((COUNT+1))
  fi
}

echo "=== LeftDesk Rebranding (dry_run=$DRY_RUN) ==="

# Rust source files
while IFS= read -r -d '' f; do
  do_sed 's/RustDesk/LeftDesk/g' "$f"
  do_sed 's/rustdesk/leftdesk/g' "$f"
done < <(find "$ROOT/src" -name "*.rs" -print0 2>/dev/null)

# Flutter dart files
while IFS= read -r -d '' f; do
  do_sed 's/RustDesk/LeftDesk/g' "$f"
  do_sed 's/rustdesk/leftdesk/g' "$f"
done < <(find "$ROOT/flutter/lib" -name "*.dart" -print0 2>/dev/null)

echo "Rebranding complete. Files processed: $COUNT"

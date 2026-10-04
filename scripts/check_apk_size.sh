#!/usr/bin/env bash
# Fails the build if the APK exceeds the PRD size budget (section 4 / 8.4).
set -euo pipefail
LIMIT_MB=${LIMIT_MB:-40}
apk=${1:-}
if [[ -z "$apk" ]]; then
  apk=$(find app -path '*/build/outputs/apk/*' -name '*.apk' 2>/dev/null | head -1 || true)
fi
if [[ -z "$apk" || ! -f "$apk" ]]; then
  echo "No APK found yet; size check skipped (the Android module is not built)."
  exit 0
fi
bytes=$(wc -c < "$apk" | tr -d ' ')
limit=$((LIMIT_MB * 1024 * 1024))
printf '%s: %.1f MB (limit %d MB)\n' "$apk" "$(echo "$bytes / 1048576" | bc -l)" "$LIMIT_MB"
if (( bytes > limit )); then
  echo "FAIL: APK is over the ${LIMIT_MB} MB budget." >&2
  exit 1
fi

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/bump_build.sh [major|minor|patch|X.Y.Z]

Defaults to "patch" if no argument is provided.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

if command -v rg >/dev/null 2>&1; then
  version_line="$(rg -n '^\s*version\s*=' build.gradle.kts | head -n 1 || true)"
else
  version_line="$(grep -nE '^\s*version\s*=' build.gradle.kts | head -n 1 || true)"
fi

if [[ -z "$version_line" ]]; then
  echo "ERROR: version line not found in build.gradle.kts" >&2
  exit 1
fi

current_version="$(printf '%s' "$version_line" | sed -E 's/.*"([^"]+)".*/\1/')"
if [[ ! "$current_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: unsupported version format: $current_version" >&2
  exit 1
fi

bump="${1:-patch}"
if [[ "$bump" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  new_version="$bump"
else
  IFS='.' read -r major minor patch <<<"$current_version"
  case "$bump" in
    major)
      major=$((major + 1))
      minor=0
      patch=0
      ;;
    minor)
      minor=$((minor + 1))
      patch=0
      ;;
    patch)
      patch=$((patch + 1))
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  new_version="$major.$minor.$patch"
fi

python3 - <<PY
import pathlib
import re
import sys

path = pathlib.Path("build.gradle.kts")
text = path.read_text()
pattern = r'(^\\s*version\\s*=\\s*")([^"]+)(".*$)'
match = re.search(pattern, text, flags=re.M)
if not match:
    sys.exit("ERROR: version line not found in build.gradle.kts")

new_version = "$new_version"
updated = re.sub(pattern, lambda m: f'{m.group(1)}{new_version}{m.group(3)}', text, flags=re.M)
path.write_text(updated)
PY

echo "Version bumped: $current_version -> $new_version"
./gradlew buildPlugin
echo "Built: build/distributions/terminal-pinned-tab-guard-$new_version.zip"

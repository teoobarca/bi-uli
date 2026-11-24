#!/usr/bin/env bash
# Publishes build/web/ (scripts/web.sh, checked by scripts/web-check.py) to GitHub Pages: one commit on gh-pages.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
[ -f build/web/sw.js ] || { echo "build/web is not complete: run scripts/web.sh first." >&2; exit 1; }
REV=$(git rev-parse --short HEAD)
git diff --quiet HEAD || { echo "Commit first: the published build should match a commit." >&2; exit 1; }
grep -q "name=\"uli-rev\" content=\"$REV\"" build/web/index.html || { echo "build/web is not from $REV: run scripts/web.sh." >&2; exit 1; }
git fetch -q origin
TREE=$(GIT_INDEX_FILE=$(mktemp -u) bash -c 'git --work-tree=build/web add -A -f . && B=$(printf "" | git hash-object -w --stdin) && git update-index --add --cacheinfo 100644,$B,.nojekyll && git write-tree; rm -f "$GIT_INDEX_FILE"')
PARENT=$(git rev-parse -q --verify origin/gh-pages || true)
COMMIT=$(git commit-tree "$TREE" ${PARENT:+-p "$PARENT"} -m "Web version $REV")
git push -q origin "$COMMIT:refs/heads/gh-pages"
echo "Published $REV: https://teoobarca.github.io/bi-uli/"

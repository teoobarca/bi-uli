#!/usr/bin/env bash
# The web app: production build into build/web/, ready to serve as static files.
# Needs JDK 21 and uvx (fonttools, to cut the fonts down to what the app uses).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
OUT="$ROOT/build/web"
./gradlew -q :shared:wasmJsBrowserDistribution
mkdir -p "$OUT"
rsync -a --delete --delete-excluded --exclude '*.map' shared/build/dist/wasmJs/productionExecutable/ "$OUT/"

# Fonts: only the axes the app sets (Theme.kt: wght, ROND) and only the characters it can show. Everything on screen
# comes from the repo (questions, Kotlin sources), so the set is known; a character the font lacks fails the build,
# because Skia would fetch a fallback font from Google for it. U+2060 (word joiner) gets an
# empty glyph of no width.
uvx --quiet --from 'fonttools==4.60.1' python -I - "$ROOT" "$OUT" <<'PY'
import io, pathlib, sys
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph
from fontTools.varLib import instancer
root, out = map(pathlib.Path, sys.argv[1:])
text = (root / "content/questions.json").read_text("utf-8") + "".join(p.read_text("utf-8") for p in (root / "shared/src").rglob("*.kt"))
chars = {c for c in map(ord, text) if c >= 32} | set(range(32, 127)) | set(range(0xA0, 0x180)) | {0x2022, 0x2026, 0x200B, 0x2060}
for name, pin in (("google_sans_flex.ttf", {"wdth": 100, "opsz": None, "slnt": None}), ("jetbrains_mono.ttf", {})):
    f = TTFont(out / name)
    if pin:
        pinned = io.BytesIO()
        instancer.instantiateVariableFont(f, pin).save(pinned)
        f = TTFont(pinned)
    blank = f.getBestCmap().get(0x200B)
    if blank is None:
        blank = "wordjoiner"
        f["glyf"], f["hmtx"], f["gvar"], f["post"]  # read before the glyph order changes
        f.setGlyphOrder(f.getGlyphOrder() + ["wordjoiner"])
        f["glyf"]["wordjoiner"] = Glyph()
        f["hmtx"]["wordjoiner"] = (0, 0)
        f["gvar"].variations["wordjoiner"] = []
        if "HVAR" in f:
            del f["HVAR"]  # the mono font: one width at every weight
    for t in f["cmap"].tables:
        if t.isUnicode():
            t.cmap[0x2060] = blank
    have = set(f.getBestCmap())
    missing = sorted(c for c in {ord(c) for c in text if ord(c) >= 32} - have)
    if missing:
        sys.exit(f"{name} lacks {[hex(c) for c in missing]}")
    o = subset.Options(notdef_outline=True)
    s = subset.Subsetter(o)
    s.populate(unicodes=chars)
    s.subset(f)
    f.save(out / name)
    print(f"  {name}: {(out / name).stat().st_size // 1024} kB")
PY

# what loads before the first screen, for the splash's bar (resources/web.js)
BYTES=$(cd "$OUT" && stat -f%z ./*.wasm ./*.ttf questions.json | awk '{s+=$1} END {print s}')
REV=$(git rev-parse --short HEAD)$(git diff --quiet HEAD || echo +)
perl -0pi -e "s|<title>|<meta name=\"uli-bytes\" content=\"$BYTES\">\n  <meta name=\"uli-rev\" content=\"$REV\">\n  <title>|" "$OUT/index.html"

# The service worker: every file comes from this build's cache, so the app starts the same on a weak signal and offline.
# A new build installs beside it (fetched past the HTTP cache, so builds never mix) and takes over once the app is closed.
CONTENT=$(cd "$OUT" && find . -type f ! -name sw.js | LC_ALL=C sort | xargs shasum -a 256 | shasum -a 256 | cut -c1-12)
FILES=$(cd "$OUT" && find . -type f ! -name sw.js ! -name '.*' | sed 's|^\./||' | LC_ALL=C sort | python3 -c 'import json,sys; print(json.dumps([l.strip() for l in sys.stdin]))')
cat > "$OUT/sw.js" <<EOF
// Written by scripts/web.sh: this build's files from the cache, a new build takes over once the app is closed.
const CACHE = "uli-$CONTENT";
const FILES = $FILES;
self.addEventListener("install", (e) => e.waitUntil(caches.open(CACHE).then((c) => c.addAll(["./", ...FILES].map((f) => new Request(f, { cache: "no-cache" }))))));
self.addEventListener("activate", (e) => e.waitUntil(caches.keys()
  .then((keys) => Promise.all(keys.filter((k) => k.startsWith("uli-") && k !== CACHE).map((k) => caches.delete(k))))
  .then(() => self.clients.claim())));
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin || !url.pathname.startsWith(new URL("./", self.registration.scope).pathname)) return;
  const key = e.request.mode === "navigate" ? "./" : e.request;
  e.respondWith(caches.open(CACHE).then((c) => c.match(key)).then((hit) => hit || fetch(e.request)));
});
EOF
echo "build/web: $(du -sk "$OUT" | cut -f1) kB"

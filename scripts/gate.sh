#!/usr/bin/env bash
# Gate: unit tests, debug build, content check, no em dashes in UI copy. Non-zero exit on any failure.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}

echo "== unit tests + assembleDebug + web compile"
./gradlew -q :app:testDebugUnitTest :app:assembleDebug :shared:compileKotlinWasmJs

echo "== content check"
python3 content/check.py

echo "== em dash check (res/ and Kotlin sources)"
hits=$(grep -rn $'—' app/src shared/src --include='*.kt' --include='*.xml' --include='*.html' --include='*.js' || true)
if [ -n "$hits" ]; then
  echo "Found an em dash:"
  echo "$hits"
  exit 1
fi

echo "== mono without ligatures (JetBrains Mono would draw != as a single sign)"
theme=shared/src/commonMain/kotlin/dev/hamper/uli/ui/theme/Theme.kt
grep -q 'const val MONO_FEATURES = ".*liga 0.*calt 0' "$theme" || { echo "MONO_FEATURES must switch off liga and calt"; exit 1; }
# Every mono style is built in Theme.kt (monoStyle / monoSpan, both with MONO_FEATURES); nobody else sets the family.
hits=$(grep -rnE 'import dev\.hamper\.uli\.ui\.theme\.Mono$|fontFamily = Mono\b' shared/src --include='*.kt' | grep -v "ui/theme/Theme.kt" || true)
if [ -n "$hits" ]; then
  echo "Mono set outside monoStyle/monoSpan:"
  echo "$hits"
  exit 1
fi
mono_defs=$(grep -c 'fontFamily = Mono, fontFeatureSettings = MONO_FEATURES' "$theme")
mono_all=$(grep -c 'fontFamily = Mono' "$theme")
[ "$mono_defs" = "$mono_all" ] || { echo "A Mono style in Theme.kt lacks MONO_FEATURES"; exit 1; }

echo "gate OK"

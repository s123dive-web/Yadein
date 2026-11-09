#!/usr/bin/env bash
# Checks a minified APK for screens that would crash with "Cannot create an instance of class <ViewModel>".
#
# Now and then a CI build leaves out the Hilt bindings of some @HiltViewModel classes (seen in v0.1.69: Settings and six
# others crashed on open). R8 then sees no `new` for those classes, makes them abstract, and hiltViewModel() falls back
# to the default factory, which fails. This script fails when any @HiltViewModel class in the sources is missing from
# the APK, is abstract, or has no Hilt map key (its obfuscated name as a string constant).
#
# Usage: scripts/verify-hilt-viewmodels.sh <apk> <mapping.txt>
set -euo pipefail

apk=$1
mapping=$2
dexdump="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools/36.0.0/dexdump"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

unzip -q -o "$apk" 'classes*.dex' -d "$work"
# Class descriptor + access flags for every class, and every string constant (one per line).
for dex in "$work"/classes*.dex; do
  "$dexdump" -l plain "$dex" 2>/dev/null | grep -E "^  (Class descriptor|Access flags)" | paste - -
done > "$work/classes.txt"
for dex in "$work"/classes*.dex; do strings -a -n 1 "$dex"; done > "$work/strings.txt"

# Fully qualified names of the @HiltViewModel classes in the sources.
viewmodels=$(
  grep -rl --include='*.kt' '^@HiltViewModel' app feature core | while read -r file; do
    pkg=$(sed -n 's/^package \([a-zA-Z0-9_.]*\).*/\1/p' "$file" | head -1)
    awk '/^@HiltViewModel/ {grab=1; next} grab && /class [A-Za-z0-9_]+/ {match($0, /class [A-Za-z0-9_]+/); print substr($0, RSTART+6, RLENGTH-6); grab=0}' "$file" \
      | sed "s/^/$pkg./"
  done | sort -u
)
[ -n "$viewmodels" ] || { echo "No @HiltViewModel classes found; is this the repository root?"; exit 1; }

failures=0
count=0
for fqn in $viewmodels; do
  count=$((count + 1))
  obf=$(grep -E "^${fqn//./\\.} -> " "$mapping" | sed -E 's/.* -> (.*):$/\1/' | head -1 || true)
  obf=${obf:-$fqn}
  descriptor="L${obf//.//};"
  line=$(grep -F "'$descriptor'" "$work/classes.txt" | head -1 || true)
  if [ -z "$line" ]; then
    echo "MISSING   $fqn ($obf): not in the APK"; failures=$((failures + 1)); continue
  fi
  flags=$(sed -E 's/.*Access flags *: (0x[0-9a-f]+).*/\1/' <<<"$line")
  if (( flags & 0x0400 )); then
    echo "ABSTRACT  $fqn ($obf): no Hilt binding creates it, the screen would crash"; failures=$((failures + 1)); continue
  fi
  # The Hilt map key is the class's binary name as a string constant (strings may prefix one length byte).
  if ! grep -qxE ".?${obf//./\\.}" "$work/strings.txt"; then
    echo "NO KEY    $fqn ($obf): not in @HiltViewModelMap, the screen would crash"; failures=$((failures + 1)); continue
  fi
done

if (( failures > 0 )); then
  echo "$failures of $count ViewModels are not wired by Hilt in $apk"
  exit 1
fi
echo "All $count @HiltViewModel classes are wired by Hilt in $apk"

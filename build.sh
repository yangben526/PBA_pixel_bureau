#!/bin/sh
set -eu
cd "$(dirname "$0")"

GREEN=$(printf '\033[0;32m')
RED=$(printf '\033[0;31m')
NC=$(printf '\033[0m')

if [ -z "${ANDROID_HOME:-}" ]; then
  for cand in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/usr/local/share/android-sdk"; do
    if [ -d "$cand" ]; then
      export ANDROID_HOME="$cand"
      break
    fi
  done
fi

if [ ! -f local.properties ] && [ -n "${ANDROID_HOME:-}" ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

# Terminal.app often has no JAVA_HOME; Homebrew JDK is not on Apple's java_home list.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  for cand in \
    /opt/homebrew/opt/openjdk@21 \
    /opt/homebrew/opt/openjdk@17 \
    /opt/homebrew/opt/openjdk \
    /usr/local/opt/openjdk@21 \
    /usr/local/opt/openjdk@17 \
    /usr/local/opt/openjdk
  do
    if [ -x "$cand/bin/java" ]; then
      export JAVA_HOME="$cand"
      break
    fi
  done
fi

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
  printf '%serror: no Java 17+ runtime. Install with: brew install openjdk@21%s\n' "$RED" "$NC" >&2
  printf 'Then either export JAVA_HOME="/opt/homebrew/opt/openjdk@21" or rerun this script.\n' >&2
  exit 1
fi

if [ ! -x ./gradlew ]; then
  printf '%serror: ./gradlew not found in %s%s\n' "$RED" "$(pwd)" "$NC" >&2
  exit 1
fi

echo "JAVA_HOME=${JAVA_HOME:-}"
echo "ANDROID_HOME=${ANDROID_HOME:-}"
java -version

# Configuration-cache instrumentation of AGP AnalyticsEnabledValueSource can
# poison a reused Gradle 8.14 daemon. Always skip CC; retry once after --stop.
GRADLE_ARGS="assembleDebug --console=plain --no-configuration-cache"

run_gradle() {
  ./gradlew $GRADLE_ARGS "$@"
}

if ! run_gradle "$@"; then
  echo "Gradle failed; stopping daemons and retrying without configuration cache..." >&2
  ./gradlew --stop >/dev/null 2>&1 || true
  rm -rf .gradle/configuration-cache
  run_gradle "$@"
fi

printf '%sBuild complete: %s/app/build/outputs/apk/debug/app-debug.apk%s\n' "$GREEN" "$(pwd)" "$NC"

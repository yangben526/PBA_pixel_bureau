#!/bin/sh
set -eu
cd "$(dirname "$0")"

GREEN=$(printf '\033[0;32m')
RED=$(printf '\033[0;31m')
YELLOW=$(printf '\033[0;33m')
NC=$(printf '\033[0m')

colorize() {
  sed -E -e "s/ FAILED/${RED} FAILED${NC}/g" \
         -e "s/^e:.*/${RED}&${NC}/" \
         -e "s/^w:.*/${YELLOW}&${NC}/" \
         -e "s/^BUILD SUCCESSFUL.*/${GREEN}&${NC}/" \
         -e "s/.*(Failure|error:).*/${RED}&${NC}/I" \
         -e "s/.*Success.*/${GREEN}&${NC}/"
}

APK=app/build/outputs/apk/debug/app-debug.apk

if [ -z "${ANDROID_HOME:-}" ]; then
  for cand in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/usr/local/share/android-sdk"; do
    if [ -d "$cand" ]; then
      export ANDROID_HOME="$cand"
      break
    fi
  done
fi
if [ -n "${ANDROID_HOME:-}" ]; then
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

if [ ! -f "$APK" ]; then
  ./build.sh
fi

if ! command -v adb >/dev/null 2>&1; then
  printf '%sadb not found on PATH. Install Android platform-tools or add $ANDROID_HOME/platform-tools to PATH.%s\n' "$RED" "$NC" >&2
  exit 1
fi

DEVS=$(adb devices | tail -n +2 | awk '$2 == "device" { print $1 }')

if [ -z "$DEVS" ]; then
  printf '%sNo device connected. Enable USB debugging and plug in your phone.%s\n' "$RED" "$NC" >&2
  adb devices
  exit 1
fi

install_one() {
  echo "Installing on $1 ..."
  set +e
  log=$(adb -s "$1" install -r "$APK" 2>&1)
  status=$?
  set -e
  printf '%s\n' "$log" | colorize
  if [ "$status" -ne 0 ]; then
    printf '%sInstall failed on %s.%s\n' "$RED" "$1" "$NC" >&2
    return 1
  fi
  adb -s "$1" shell am start -n app.pixelbureau/.MainActivity
}

if [ "${1:-}" = "--all" ]; then
  for s in $DEVS; do
    install_one "$s"
  done
  printf '%sPixel Bureau installed on all connected devices.%s\n' "$GREEN" "$NC"
  exit 0
fi

COUNT=$(printf '%s\n' $DEVS | wc -l | tr -d '[:space:]')
if [ -n "${ANDROID_SERIAL:-}" ]; then
  SERIAL="$ANDROID_SERIAL"
elif [ "$COUNT" -eq 1 ]; then
  SERIAL="$DEVS"
else
  SERIAL=$(printf '%s\n' $DEVS | grep -v ':' | head -1 || true)
  if [ -z "$SERIAL" ]; then
    SERIAL=$(printf '%s\n' $DEVS | head -1)
  fi
  echo "Multiple devices found; using $SERIAL (set ANDROID_SERIAL to override, or pass --all)."
fi

install_one "$SERIAL"
printf '%sPixel Bureau installed and launched on %s.%s\n' "$GREEN" "$SERIAL" "$NC"

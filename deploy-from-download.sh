#!/usr/bin/env bash
set -euo pipefail
DOWNLOAD="${DOWNLOAD:-/storage/emulated/0/Download}"
ZIP="$DOWNLOAD/AndroidMacroRecorder.zip"
STAGE="$DOWNLOAD/AndroidMacroRecorder-stage"
REPO="${REPO:-$HOME/Android-Macro-Recorder}"
[ -f "$ZIP" ] || { echo "ERROR: missing $ZIP"; exit 1; }
rm -rf "$STAGE"
unzip -q -o "$ZIP" -d "$STAGE"
if [ -d "$STAGE/AndroidMacroRecorder" ]; then SRC="$STAGE/AndroidMacroRecorder"
elif [ -d "$STAGE/app" ]; then SRC="$STAGE"
else echo "ERROR: unexpected zip layout"; find "$STAGE" -maxdepth 2; exit 1; fi
if [ ! -d "$REPO/.git" ]; then
  echo "==> $REPO is not a git repo yet, cloning it"
  git clone https://github.com/renatoehzproyectos/Android-Macro-Recorder.git "$REPO"
fi
cd "$REPO"
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a "$SRC"/. "$REPO"/
bash "$REPO/push-and-get-apk.sh" "$REPO"

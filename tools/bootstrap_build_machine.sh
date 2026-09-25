#!/usr/bin/env bash
# Provision THIS machine as a release-build machine for the Interslavic keyboard.
#
# Run on macOS, Linux or Git Bash on Windows. It changes nothing
# it is not asked to: it CHECKS prerequisites, RESTORES the signing key from an
# independently obtained backup, WRITES keystore.properties for this OS,
# and BUILDS + COMPARES the hash. It never installs anything, prompts for a
# password itself, or prints passwords.
#
#   ./tools/bootstrap_build_machine.sh              # check only
#   ./tools/bootstrap_build_machine.sh --key /path/to/backup.tar.gpg --archive-sha256 HASH
#   ./tools/bootstrap_build_machine.sh --build
#
# Docs: docs/DRILL_keystore_restore.md

set -euo pipefail
cd "$(dirname "$0")/.."
REPO="$PWD"

ARCHIVE_SHA=""
CERT_FP=5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a
ALIAS=medzuslovjansky

KEYARCHIVE=""; DOBUILD=0
while [ $# -gt 0 ]; do
  case "$1" in
    --key)
      [ -n "${2:-}" ] && [ -f "$2" ] || {
        echo "--key requires an existing local encrypted backup file"; exit 2;
      }
      KEYARCHIVE="$2"; shift 2 ;;
    --archive-sha256)
      [[ "${2:-}" =~ ^[0-9a-fA-F]{64}$ ]] || {
        echo "--archive-sha256 requires 64 hexadecimal characters"; exit 2;
      }
      ARCHIVE_SHA=$(printf '%s' "$2" | tr 'A-F' 'a-f'); shift 2 ;;
    --build) DOBUILD=1; shift ;;
    *) echo "unknown argument: $1"; exit 2 ;;
  esac
done
[ -z "$KEYARCHIVE" ] || [ -n "$ARCHIVE_SHA" ] || {
  echo "--key requires --archive-sha256 from a trusted private record"; exit 2;
}
[ -z "$ARCHIVE_SHA" ] || [ -n "$KEYARCHIVE" ] || {
  echo "--archive-sha256 requires --key"; exit 2;
}

ok(){ printf '  \033[32mOK\033[0m   %s\n' "$*"; }
bad(){ printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAIL=1; }
warn(){ printf '  \033[33m!!\033[0m   %s\n' "$*"; }
FAIL=0

sha(){ if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1
       else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

case "$(uname -s)" in
  Darwin) OS=mac;   KEYDIR="$HOME/keystores" ;;
  Linux)  OS=linux; KEYDIR="$HOME/keystores" ;;
  *)      OS=win;   KEYDIR="$HOME/keystores" ;;
esac
echo "== machine: $(hostname) / $OS / $(uname -m) =="

echo "-- prerequisites --"
if command -v java >/dev/null; then
  JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
  # 21 is not a preference: a different major can change the build output, and
  # this project's whole F-Droid path rests on the APK being reproducible.
  [ "$JV" = "21" ] && ok "JDK $JV" || bad "JDK $JV - JDK 21 is required"
else bad "java (JDK 21)"; fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
  ok "ANDROID_HOME=$ANDROID_HOME"
  [ -d "$ANDROID_HOME/build-tools/34.0.0" ] || bad "build-tools/34.0.0"
  [ -d "$ANDROID_HOME/platforms/android-34" ] || bad "platforms/android-34"
else bad "ANDROID_HOME (Android SDK)"; fi

for t in git gpg tar; do command -v $t >/dev/null && ok "$t" || bad "$t"; done

# Do not restore secrets on a machine that cannot build yet.
if [ "$FAIL" != "0" ] && { [ -n "$KEYARCHIVE" ] || [ "$DOBUILD" = "1" ]; }; then
  exit 1
fi

echo "-- signing key --"
JKS="$KEYDIR/$ALIAS-release.jks"
if [ -f "$JKS" ]; then
  ok "key present: $JKS"
elif [ -n "$KEYARCHIVE" ]; then
  # Obtain this archive independently of the machine being recovered.
  umask 077
  mkdir -p "$KEYDIR"; TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  cp "$KEYARCHIVE" "$TMP/$ALIAS-keystore-backup.tar.gpg"
  GOT=$(sha "$TMP/$ALIAS-keystore-backup.tar.gpg")
  [ "$GOT" = "$ARCHIVE_SHA" ] && ok "archive checksum matches" || { bad "archive checksum: $GOT"; exit 1; }
  echo "  enter the password in pinentry (from your password manager, never on the command line):"
  ( cd "$TMP" && gpg -d "$ALIAS-keystore-backup.tar.gpg" | tar x ) || { bad "decryption"; exit 1; }
  RESTORED=$(find "$TMP" -type f -name "$ALIAS-release.jks")
  [ -n "$RESTORED" ] && [ -f "$RESTORED" ] || { bad "expected exactly one key in the archive"; exit 1; }
  cp "$RESTORED" "$JKS"
  chmod 600 "$JKS"
  [ -f "$JKS" ] && ok "key restored" || { bad "no .jks in the archive"; exit 1; }
  rm -rf "$TMP"
  trap - EXIT
else
  bad "key missing; use --key ARCHIVE --archive-sha256 HASH"
fi

if [ -f "$JKS" ] && command -v keytool >/dev/null; then
  # </dev/null is not optional: with no password keytool PROMPTS, and a
  # verification script that can block is one nobody will run.
  FP=$(keytool -list -v -keystore "$JKS" -alias "$ALIAS" </dev/null 2>/dev/null \
       | grep -i 'SHA256:' | head -1 | tr -d ' ' | cut -d: -f2- | tr -d ':' | tr 'A-Z' 'a-z') || FP=""
  if [ -n "$FP" ]; then
    [ "$FP" = "$CERT_FP" ] && ok "certificate fingerprint matches" || bad "FINGERPRINT MISMATCH: $FP"
  else warn "keytool needs a password - run it interactively to verify the fingerprint"; fi
fi

echo "-- keystore.properties --"
KP="$REPO/android-app/keystore.properties"
if [ -f "$KP" ]; then
  ok "present"
  # Compare by whether the file RESOLVES, not by string: on Windows the properties
  # file legitimately says C:/Users/... while bash computes /c/Users/... - the same
  # file in two notations. A checker that cries wolf there is worse than no checker.
  SF=$(grep '^storeFile=' "$KP" | cut -d= -f2- | tr -d '\r') || SF=""
  SFR="$SF"
  command -v cygpath >/dev/null && SFR=$(cygpath -u "$SF" 2>/dev/null || echo "$SF")
  if [ -f "$SFR" ]; then ok "storeFile resolves to an existing file"
  else warn "storeFile does NOT point to an existing file ($SF) - check the local path"; FAIL=1; fi
  for property in storePassword keyPassword; do
    tr -d '\r' < "$KP" | grep -Eq "^$property=.+" || bad "empty $property - fill it locally from your password manager"
  done
  tr -d '\r' < "$KP" | grep -qx "keyAlias=$ALIAS" || bad "keyAlias must be $ALIAS"
elif [ -n "$KEYARCHIVE" ]; then
  # Passwords are deliberately left blank: this script must never see them.
  umask 077
  printf 'storeFile=%s\nstorePassword=\nkeyAlias=%s\nkeyPassword=\n' "$JKS" "$ALIAS" > "$KP"
  warn "SCAFFOLD created - enter both passwords from your password manager: $KP"
  FAIL=1
else
  bad "keystore.properties missing; --key creates a scaffold, check-only mode writes nothing"
fi

if [ "$DOBUILD" = "1" ] && [ "$FAIL" = "0" ]; then
  echo "-- build --"
  ( cd android-app && ./gradlew --no-daemon clean assembleRelease ) || exit 1
  APK=android-app/app/build/outputs/apk/release/app-release.apk
  SIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
  [ -f "$SIGNER" ] || SIGNER="$SIGNER.bat"
  CERTS=$("$SIGNER" verify --print-certs "$APK") || { bad "invalid APK signature"; exit 1; }
  APK_FP=$(printf '%s\n' "$CERTS" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  [ "$APK_FP" = "$CERT_FP" ] || { bad "APK FINGERPRINT MISMATCH: $APK_FP"; exit 1; }
  ok "APK signature verified and certificate fingerprint matches"
  GOT=$(sha "$APK")
  EXP=$(grep -oE '^\| SHA-256 \| `[0-9a-f]{64}`' docs/INSTALL.md | grep -oE '[0-9a-f]{64}')
  echo "  built   : $GOT"
  echo "  expected: ${EXP:-?}   (from docs/INSTALL.md)"
  if [ "$GOT" = "$EXP" ]; then
    ok "IDENTICAL - build reproducible on this machine"
  else
    warn "DIFFERENT. The key may be correct while the build differs between machines."
    warn "F-Droid also compares against Binaries and AllowedAPKSigningKeys - investigate before merging."
    FAIL=1
  fi
fi

echo
[ "$FAIL" = "0" ] && echo "done." || echo "checks failed - see above."
exit $FAIL

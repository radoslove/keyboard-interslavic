#!/usr/bin/env bash
# Provision THIS machine as a release-build machine for the Interslavic keyboard.
#
# Run it on any of hp / mc / len / com (Git Bash on Windows). It changes nothing
# it is not asked to: it CHECKS prerequisites, RESTORES the signing key from an
# off-machine backup, WRITES keystore.properties with the right path for this OS,
# and BUILDS + COMPARES the hash. It never installs anything and never touches a
# password.
#
#   ./tools/bootstrap_build_machine.sh              # check only
#   ./tools/bootstrap_build_machine.sh --key hetz   # + restore key from that host
#   ./tools/bootstrap_build_machine.sh --key hetz --build
#
# Docs: docs/DRILL_keystore_restore.md

set -uo pipefail
cd "$(dirname "$0")/.."
REPO="$PWD"

ARCHIVE_SHA=01a836bc3fd60df222334d058eabffd0b735806c3a77b4ecc1fff853d6f43728
CERT_FP=5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a
ALIAS=medzuslovjansky

# Plain case, not an associative array: macOS still ships bash 3.2, where
# `declare -A` is a syntax error - and mc is a mac.
HOST_LIST="hetz host ubu rpi4 rpi5"
host_addr(){
  case "$1" in
    hetz) echo claude@100.91.132.98 ;;
    host) echo claude@100.71.15.84  ;;
    ubu)  echo rado@100.77.136.55   ;;
    rpi4) echo rado@100.68.191.75   ;;
    rpi5) echo rado@100.125.186.102 ;;
    *)    echo "" ;;
  esac
}

KEYHOST=""; DOBUILD=0
while [ $# -gt 0 ]; do
  case "$1" in
    --key)   KEYHOST="${2:-}"; shift 2 ;;
    --build) DOBUILD=1; shift ;;
    *) echo "nieznany argument: $1"; exit 2 ;;
  esac
done

ok(){ printf '  \033[32mOK\033[0m   %s\n' "$*"; }
bad(){ printf '  \033[31mBRAK\033[0m %s\n' "$*"; FAIL=1; }
warn(){ printf '  \033[33m!!\033[0m   %s\n' "$*"; }
FAIL=0

sha(){ if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1
       else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

case "$(uname -s)" in
  Darwin) OS=mac;   KEYDIR="$HOME/keystores" ;;
  Linux)  OS=linux; KEYDIR="$HOME/keystores" ;;
  *)      OS=win;   KEYDIR="$HOME/keystores" ;;
esac
echo "== maszyna: $(hostname) / $OS / $(uname -m) =="

echo "-- wymagania --"
if command -v java >/dev/null; then
  JV=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
  # 21 is not a preference: a different major can change the build output, and
  # this project's whole F-Droid path rests on the APK being reproducible.
  [ "$JV" = "21" ] && ok "JDK $JV" || warn "JDK $JV - budowano na 21; inny major moze zmienic wynik"
else bad "java (JDK 21)"; fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
  ok "ANDROID_HOME=$ANDROID_HOME"
  [ -d "$ANDROID_HOME/build-tools/34.0.0" ] || bad "build-tools/34.0.0"
  [ -d "$ANDROID_HOME/platforms/android-34" ] || bad "platforms/android-34"
else bad "ANDROID_HOME (Android SDK)"; fi

for t in git gpg tar; do command -v $t >/dev/null && ok "$t" || bad "$t"; done

echo "-- klucz podpisujacy --"
JKS="$KEYDIR/$ALIAS-release.jks"
if [ -f "$JKS" ]; then
  ok "klucz na miejscu: $JKS"
elif [ -n "$KEYHOST" ]; then
  SRC=$(host_addr "$KEYHOST")
  [ -z "$SRC" ] && { echo "nieznany host: $KEYHOST (dostepne: $HOST_LIST)"; exit 2; }
  # The point of the drill is that NOTHING comes from hp. Keep it that way.
  [ "$KEYHOST" = "hp" ] && { echo "hp jest zrodlem oryginalu - to nie dowodzi niczego"; exit 2; }
  mkdir -p "$KEYDIR"; TMP=$(mktemp -d)
  echo "  pobieram z $KEYHOST ..."
  scp -q "$SRC:~/keystore-backup/$ALIAS-keystore-backup.tar.gpg" "$TMP/" || { bad "scp z $KEYHOST"; exit 1; }
  GOT=$(sha "$TMP/$ALIAS-keystore-backup.tar.gpg")
  [ "$GOT" = "$ARCHIVE_SHA" ] && ok "suma archiwum zgodna" || { bad "suma archiwum: $GOT"; exit 1; }
  echo "  haslo podaj w pinentry (z menedzera hasel, nigdy w linii polecen):"
  ( cd "$TMP" && gpg -d "$ALIAS-keystore-backup.tar.gpg" | tar x ) || { bad "odszyfrowanie"; exit 1; }
  find "$TMP" -name '*.jks' -exec cp {} "$JKS" \;
  [ -f "$JKS" ] && ok "klucz odtworzony" || { bad "brak .jks w archiwum"; exit 1; }
  rm -rf "$TMP"
else
  warn "brak klucza; uruchom z --key hetz|host|ubu|rpi4|rpi5"
fi

if [ -f "$JKS" ] && command -v keytool >/dev/null; then
  # </dev/null is not optional: with no password keytool PROMPTS, and a
  # verification script that can block is one nobody will run.
  FP=$(keytool -list -v -keystore "$JKS" -alias "$ALIAS" </dev/null 2>/dev/null \
       | grep -i 'SHA256:' | head -1 | tr -d ' ' | cut -d: -f2- | tr -d ':' | tr 'A-Z' 'a-z')
  if [ -n "$FP" ]; then
    [ "$FP" = "$CERT_FP" ] && ok "odcisk certyfikatu zgodny" || bad "ODCISK SIE NIE ZGADZA: $FP"
  else warn "keytool poprosi o haslo - uruchom interaktywnie, zeby sprawdzic odcisk"; fi
fi

echo "-- keystore.properties --"
KP="$REPO/android-app/keystore.properties"
if [ -f "$KP" ]; then
  ok "istnieje"
  # Compare by whether the file RESOLVES, not by string: on Windows the properties
  # file legitimately says C:/Users/... while bash computes /c/Users/... - the same
  # file in two notations. A checker that cries wolf there is worse than no checker.
  SF=$(grep '^storeFile=' "$KP" | cut -d= -f2- | tr -d '')
  SFR="$SF"
  command -v cygpath >/dev/null && SFR=$(cygpath -u "$SF" 2>/dev/null || echo "$SF")
  if [ -f "$SFR" ]; then ok "storeFile rozwiazuje sie do istniejacego pliku"
  else warn "storeFile NIE wskazuje istniejacego pliku ($SF) - to JEST ta pulapka"; FAIL=1; fi
else
  # Passwords are deliberately left blank: this script must never see them.
  printf 'storeFile=%s\nstorePassword=\nkeyAlias=%s\nkeyPassword=\n' "$JKS" "$ALIAS" > "$KP"
  warn "utworzony SZKIELET - wpisz oba hasla z menedzera hasel: $KP"
  FAIL=1
fi

if [ "$DOBUILD" = "1" ] && [ "$FAIL" = "0" ]; then
  echo "-- build --"
  ( cd android-app && ./gradlew --no-daemon clean assembleRelease ) || exit 1
  APK=android-app/app/build/outputs/apk/release/app-release.apk
  GOT=$(sha "$APK")
  EXP=$(grep -oE '^\| SHA-256 \| `[0-9a-f]{64}`' docs/INSTALL.md | grep -oE '[0-9a-f]{64}')
  echo "  zbudowany : $GOT"
  echo "  oczekiwany: ${EXP:-?}   (z docs/INSTALL.md)"
  if [ "$GOT" = "$EXP" ]; then
    ok "IDENTYCZNY - build reprodukowalny na tej maszynie"
  else
    warn "ROZNY. Klucz moze byc dobry, a build nieodtwarzalny miedzy maszynami."
    warn "To samo porownanie robi F-Droid (Binaries + AllowedAPKSigningKeys) - zbadaj PRZED merge'em."
  fi
fi

echo
[ "$FAIL" = "0" ] && echo "gotowe." || echo "sa braki - patrz wyzej."
exit $FAIL

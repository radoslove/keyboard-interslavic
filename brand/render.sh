#!/usr/bin/env bash
# Render brand SVGs to PNG with macOS Quick Look (no Pillow / ImageMagick / Chrome needed).
# Usage:  bash brand/render.sh            -> brand/out/*.png (store assets) + brand/out/mipmap-*/ (Android launcher PNGs)
# Notes:  qlmanage -s N = longest side N px; square SVGs come out N x N, 1024x500 SVG comes out 1024x500.
#         Output name is <name>.svg.png; we strip the ".svg".
set -euo pipefail
cd "$(dirname "$0")"
SRC=src
OUT=out
mkdir -p "$OUT"

render() {  # render <svg> <size> <outdir> <outname>
  local svg=$1 size=$2 dir=$3 name=$4
  mkdir -p "$dir"
  qlmanage -t -s "$size" -o "$dir" "$svg" >/dev/null 2>&1
  mv -f "$dir/$(basename "$svg").png" "$dir/$name"
}

# --- Store assets (Google Play) ---
render $SRC/icon_a_caron.svg     512  $OUT icon_a_caron_512.png
render $SRC/icon_b_hearts.svg    512  $OUT icon_b_hearts_512.png
render $SRC/icon_c_keycap.svg    512  $OUT icon_c_keycap_512.png
render $SRC/feature_graphic.svg  1024 $OUT feature_graphic_1024x500.png
sips -c 500 1024 $OUT/feature_graphic_1024x500.png >/dev/null   # square canvas -> centre band 1024x500
render $SRC/radoslove_avatar.svg 512  $OUT radoslove_avatar_512.png

# --- Android launcher icons (variant chosen via ICON=a|b|c; default b = owner's pick 2026-09-03) ---
ICON=${ICON:-b}
case $ICON in
  a) FULL=$SRC/icon_a_caron.svg;  FG=$SRC/fg_a_caron.svg ;;
  b) FULL=$SRC/icon_b_hearts.svg; FG=$SRC/fg_b_hearts.svg ;;
  c) FULL=$SRC/icon_c_keycap.svg; FG=$SRC/fg_c_keycap.svg ;;
  *) echo "ICON must be a, b or c" >&2; exit 1 ;;
esac
# legacy (API 24-25): 48dp -> mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192
# adaptive foreground (API 26+): 108dp -> mdpi 108, hdpi 162, xhdpi 216, xxhdpi 324, xxxhdpi 432
for pair in mdpi:48:108 hdpi:72:162 xhdpi:96:216 xxhdpi:144:324 xxxhdpi:192:432; do
  IFS=: read -r dpi legacy adaptive <<<"$pair"
  render "$FULL" "$legacy"   "$OUT/mipmap-$dpi" ic_launcher.png
  render "$FULL" "$legacy"   "$OUT/mipmap-$dpi" ic_launcher_round.png
  render "$FG"   "$adaptive" "$OUT/mipmap-$dpi" ic_launcher_foreground.png
done

echo "rendered into brand/$OUT (ICON=$ICON)"
for f in $OUT/*.png; do printf '%-40s %s\n' "$(basename "$f")" "$(file -b "$f" | cut -d, -f2)"; done

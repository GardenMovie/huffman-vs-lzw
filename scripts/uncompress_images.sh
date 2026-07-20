#!/usr/bin/env bash
# Convert every image under a folder (recursively, one subfolder at a time) to
# raw uncompressed PPM. It does NOT run the codecs, it just produces the raw
# PPMs the codecs expect as input (e.g. before running run_datasets.sh).
#
# Usage:
#   scripts/uncompress_images.sh <folder>
#
# For each image (*.jpg/*.jpeg/*.png) found anywhere under <folder>, writes a
# sibling <name>.ppm next to it. Existing .ppm files are left alone.
set -u

FOLDER="${1:-}"
if [ -z "$FOLDER" ] || [ ! -d "$FOLDER" ]; then
  echo "usage: $0 <folder>" >&2
  exit 1
fi

command -v magick >/dev/null 2>&1 || { echo "error: ImageMagick 'magick' not found" >&2; exit 1; }

shopt -s nullglob nocaseglob globstar

converted=0; failed=0
for img in "$FOLDER"/**/*.jpg "$FOLDER"/**/*.jpeg "$FOLDER"/**/*.png; do
  ppm="${img%.*}.ppm"
  if [ -f "$ppm" ]; then
    echo "[skip] exists: $ppm" >&2
    continue
  fi
  if magick "$img" "$ppm" 2>/dev/null; then
    converted=$((converted+1))
    echo "[ok] $img -> $ppm" >&2
  else
    failed=$((failed+1))
    echo "[fail] convert failed: $img" >&2
  fi
done

echo "[done] converted $converted, failed $failed" >&2

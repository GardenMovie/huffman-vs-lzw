#!/usr/bin/env bash
# Run Huffman vs LZW over every folder in datasets/, one report per folder.
#
# Image folders (illustration-images, real-images) ship both the source
# jpg/png AND a pre-converted uncompressed .ppm. We ALWAYS benchmark the .ppm
# (compressing an already-compressed jpg/png tells you nothing). Every other
# folder holds raw bytes (.bin/.txt/.csv/.c/.h) fed to the codec directly.
set -u

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJ" || exit 1

OUTDIR="$PROJ/results"
mkdir -p "$OUTDIR"
SUMMARY="$OUTDIR/summary.txt"

# Folders whose payload is the .ppm files, not the source images.
is_image_folder() {
  case "$1" in
    illustration-images|real-images) return 0 ;;
    *) return 1 ;;
  esac
}

shopt -s nullglob
: > "$SUMMARY"
{
  echo "Huffman vs LZW across datasets/"
  echo "Generated: $(date -Iseconds)"
  echo
} >> "$SUMMARY"

for dir in datasets/*/; do
  folder=$(basename "$dir")

  if is_image_folder "$folder"; then
    files=( "$dir"*.ppm )
    kind="raw .ppm (from source jpg/png)"
  else
    files=( "$dir"* )
    kind="raw files"
  fi

  if [ ${#files[@]} -eq 0 ]; then
    echo "[skip] $folder: no input files" >&2
    continue
  fi

  out="$OUTDIR/$folder.txt"
  {
    echo "Dataset: $folder   ($kind, ${#files[@]} file(s))"
    echo
  } > "$out"

  echo "[run] $folder (${#files[@]} files)..." >&2
  java -cp out compression.BatchReport "${files[@]}" >> "$out" 2>&1

  # Pull this folder's TOTALS block into the combined summary.
  {
    echo "### $folder  (${#files[@]} file(s), $kind)"
    sed -n '/^TOTALS across/,$p' "$out"
    echo
  } >> "$SUMMARY"

  echo "[done] $folder -> $out" >&2
done

echo "[all done] per-folder reports in $OUTDIR/, combined totals in $SUMMARY" >&2

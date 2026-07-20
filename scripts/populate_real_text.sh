#!/usr/bin/env bash
#
# populate_real_text.sh — build the entire real-text/ dataset (see docs/datasets.md).
#
# The real-text set contrasts three kinds of natural, human-authored text that
# stress the compressors differently:
#
#   prose   — Moby-Dick: skewed character frequencies (helps Huffman) plus
#             repeated words/whitespace (helps LZW). One book sliced into a
#             log-spaced ladder of whole-line prefixes to plot behavior vs. size.
#   code    — source code: indentation, keywords and identifiers recur (helps
#             LZW) while the skewed byte histogram still helps Huffman. Two
#             self-contained files at opposite ends of the size range, used whole.
#   struct  — diamonds CSV: repetitive delimiters and quoted keys favor LZW, the
#             skewed byte frequencies still help Huffman. One CSV sliced into a
#             log-spaced ladder of header+row prefixes.
#
# Every slice preserves validity: prose slices begin at the Gutenberg START
# marker, CSV slices keep the header plus the first N whole data rows, and code
# files are never sliced.
#
# Sources (public domain / public):
#   Moby-Dick        — Project Gutenberg ebook #2701
#   code_sort.c      — Linux kernel lib/sort.c (SPDX GPL-2.0), ~11 KB
#   code_stb_image.h — stb single-header image loader v2.30 (public domain), ~283 KB
#   diamonds.csv     — ggplot2 `diamonds` dataset (53 940 rows)
#
# Usage:
#   ./populate_real_text.sh

set -euo pipefail

OUT_DIR="$(dirname "$0")/../datasets/real-text"
SRC="$OUT_DIR/.src"
mkdir -p "$SRC"

dl() { curl -fsSL "$1" -o "$2"; }

# --- prose: Moby-Dick slices ------------------------------------------------

echo "downloading prose source..."
dl "https://www.gutenberg.org/files/2701/2701-0.txt" "$SRC/moby.txt"

python3 - "$OUT_DIR" "$SRC" <<'PY'
import os, sys

out_dir, src = sys.argv[1], sys.argv[2]

with open(os.path.join(src, "moby.txt"), "rb") as f:
    data = f.read()

# Start every slice from the Gutenberg START marker so each file is a valid,
# self-contained prefix of the book (per docs/datasets.md provenance table).
marker = b"*** START OF THE PROJECT GUTENBERG EBOOK 2701 ***"
i = data.find(marker)
if i == -1:
    sys.exit("START marker not found in source; Gutenberg format may have changed")
body = data[i:]

# Whole-line prefixes at a log-spaced size ladder (~4 KB -> full ~1.2 MB).
targets = [4 * 1024, 16 * 1024, 64 * 1024, 256 * 1024, 1024 * 1024, len(body)]

def line_prefix(target):
    if target >= len(body):
        return body
    cut = body.rfind(b"\n", 0, target)   # trim back to a whole line
    return body[: cut + 1] if cut != -1 else body[:target]

for n, target in enumerate(targets, start=1):
    slice_bytes = line_prefix(target)
    name = f"moby_{n:02d}.txt"
    p = os.path.join(out_dir, name)
    with open(p, "wb") as f:
        f.write(slice_bytes)
    print(f"  {len(slice_bytes):>9} B  {name}")

print("done: Moby-Dick slices written to", out_dir)
PY

# --- code: whole source files -----------------------------------------------

echo "downloading source code..."
dl "https://raw.githubusercontent.com/torvalds/linux/master/lib/sort.c"        "$OUT_DIR/code_sort.c"
dl "https://raw.githubusercontent.com/nothings/stb/master/stb_image.h"         "$OUT_DIR/code_stb_image.h"

for f in code_sort.c code_stb_image.h; do
    printf '  %9d B  %s\n' "$(wc -c < "$OUT_DIR/$f")" "$f"
done

echo "done: code files written to $OUT_DIR"

# --- struct: diamonds CSV slices --------------------------------------------

echo "downloading structured-data source..."
dl "https://raw.githubusercontent.com/tidyverse/ggplot2/main/data-raw/diamonds.csv" "$SRC/diamonds.csv"

python3 - "$OUT_DIR" "$SRC" <<'PY'
import os, sys

out_dir, src = sys.argv[1], sys.argv[2]

# Read raw lines and preserve the source's exact bytes/quoting (ggplot2 quotes
# every string field, e.g. "Ideal","E"). Re-serializing through csv would drop
# those quotes, so we just take the header + first N raw data lines verbatim.
with open(os.path.join(src, "diamonds.csv"), "rb") as f:
    lines = f.read().splitlines(keepends=True)
header, body = lines[0], lines[1:]

def csv_slice(nrows):
    return header + b"".join(body[:nrows])

# Log-spaced row ladder (~3 KB -> full ~2.8 MB), header included in each slice.
row_counts = [58, 387, 2530, 13639, len(body)]
for n, nrows in enumerate(row_counts, start=1):
    data = csv_slice(nrows)
    name = f"struct_diamonds_{n:02d}.csv"
    with open(os.path.join(out_dir, name), "wb") as f:
        f.write(data)
    print(f"  {len(data):>9} B  {name}")

print("done: diamonds slices written to", out_dir)
PY

echo "done: real-text dataset written to $OUT_DIR"

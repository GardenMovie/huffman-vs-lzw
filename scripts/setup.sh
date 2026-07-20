#!/usr/bin/env bash
#
# setup.sh — one-shot project setup for the Huffman vs. LZW comparison.
#
# Does four things:
#   1. Creates the five dataset folders (docs/datasets.md describes them).
#   2. Optionally fills the generable/downloadable ones via scripts/populate_*.sh.
#   3. Creates the out/ folder for compiled classes.
#   4. Compiles src/compression/*.java (only if a Java compiler is present).
#
# The two image folders (illustration-images/, real-images/) have no populate
# script — their source images are downloaded by hand, then converted to raw
# PPM with scripts/uncompress_images.sh (see docs/datasets.md).
#
# Usage:
#   ./setup.sh                 # make folders + out/, compile Java. No downloads.
#   ./setup.sh --populate      # also fill random/, uniform/, real-text/
#   ./setup.sh --populate-all  # alias for --populate
#   ./setup.sh -h | --help
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

POPULATE=0
for arg in "$@"; do
    case "$arg" in
        --populate|--populate-all) POPULATE=1 ;;
        -h|--help)
            sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *)
            echo "unknown argument: $arg (try --help)" >&2
            exit 2 ;;
    esac
done

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

# --- 1. dataset folders -----------------------------------------------------
say "creating dataset folders"
DATASET_DIRS=(uniform illustration-images real-images real-text random)
for d in "${DATASET_DIRS[@]}"; do
    mkdir -p "datasets/$d"
    printf '  datasets/%s\n' "$d"
done

# --- 2. populate (optional) -------------------------------------------------
if [ "$POPULATE" -eq 1 ]; then
    say "populating generable / downloadable datasets"
    # random/ and uniform/ are generated locally; real-text/ is downloaded.
    # The image folders are intentionally skipped (manual source downloads).
    for script in populate_random.sh populate_uniform.sh populate_real_text.sh; do
        printf '\n  -- %s --\n' "$script"
        bash "scripts/$script"
    done
    echo
    echo "  note: illustration-images/ and real-images/ are not auto-populated."
    echo "        add source images under those folders, then run:"
    echo "            scripts/uncompress_images.sh datasets/illustration-images"
    echo "            scripts/uncompress_images.sh datasets/real-images"
else
    echo
    echo "  (skipping downloads — pass --populate to fill random/, uniform/, real-text/)"
fi

# --- 3. out/ folder ---------------------------------------------------------
say "creating out/ folder"
mkdir -p out
echo "  out/"

# --- 4. compile Java --------------------------------------------------------
if command -v javac >/dev/null 2>&1; then
    say "compiling Java sources"
    javac -d out src/compression/*.java
    echo "  compiled to out/ ($(javac -version 2>&1))"
else
    say "skipping Java compilation"
    echo "  no 'javac' on PATH — install a JDK, then: javac -d out src/compression/*.java"
fi

say "setup complete"

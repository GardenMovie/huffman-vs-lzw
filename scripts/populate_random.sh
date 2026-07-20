#!/usr/bin/env bash
#
# populate_random.sh — regenerate the datasets/random/ incompressible control set.
#
# Creates ~20 files of true random bytes with log-spaced sizes (~256 B to ~4 MB),
# the worst-case input for both Huffman and LZW (see docs/datasets.md).
#
# Usage:
#   ./populate_random.sh          # seed 42 (default)
#   ./populate_random.sh 123      # custom seed
#
# The seed controls the size distribution and jitter. Note: byte contents come
# from os.urandom and are NOT reproducible across runs — only the sizes are.

set -euo pipefail

SEED="${1:-42}"
OUT_DIR="$(dirname "$0")/../datasets/random"
N=20

mkdir -p "$OUT_DIR"

python3 - "$SEED" "$OUT_DIR" "$N" <<'PY'
import os, random, sys

seed, out_dir, n = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
random.seed(seed)

lo, hi = 256, 4 * 1024 * 1024
for i in range(1, n + 1):
    # log-uniform size for a wide spread of magnitudes
    frac = (i - 1) / (n - 1)
    size = int(round(lo * (hi / lo) ** frac))
    # jitter so sizes aren't perfectly smooth
    size = max(1, int(size * random.uniform(0.7, 1.3)))
    with open(os.path.join(out_dir, f"rand_{i:03d}.bin"), "wb") as f:
        f.write(os.urandom(size))
print(f"wrote {n} files to {out_dir} (seed {seed})")
PY

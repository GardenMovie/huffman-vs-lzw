#!/usr/bin/env bash
#
# populate_uniform.sh — regenerate the datasets/uniform/ trivially-compressible set.
#
# Creates ~10 low-entropy files with log-spaced sizes (~1 KB to ~4 MB), the
# best-case input for both Huffman and LZW (see docs/datasets.md). The set
# varies TWO axes — the kind of redundancy and the file size — so different
# patterns exercise each algorithm's strength:
#
#   single-byte runs  -> tiny Huffman tree, instant LZW run   (both extreme)
#   2/4-byte cycles   -> short LZW dictionary match           (LZW-favoring)
#   skewed few-symbol -> skewed histogram                     (Huffman-favoring)
#   block runs        -> RLE-like long runs of one byte       (both)
#
# Usage:
#   ./populate_uniform.sh          # seed 42 (default)
#   ./populate_uniform.sh 123      # custom seed
#
# Fully reproducible: both sizes and byte contents derive from the seed.

set -euo pipefail

SEED="${1:-42}"
OUT_DIR="$(dirname "$0")/../datasets/uniform"
N=10

mkdir -p "$OUT_DIR"

python3 - "$SEED" "$OUT_DIR" "$N" <<'PY'
import os, random, sys

seed, out_dir, n = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
random.seed(seed)

lo, hi = 1024, 4 * 1024 * 1024

# cycle of redundancy patterns; each returns `size` bytes
def single(size, rng):        # one repeated byte
    return bytes([rng.randrange(256)]) * size

def cycle2(size, rng):        # ABABAB...
    a, b = rng.randrange(256), rng.randrange(256)
    return (bytes([a, b]) * (size // 2 + 1))[:size]

def cycle4(size, rng):        # ABCDABCD...
    pat = bytes(rng.randrange(256) for _ in range(4))
    return (pat * (size // 4 + 1))[:size]

def skewed(size, rng):        # few symbols, heavily skewed frequencies
    syms = [rng.randrange(256) for _ in range(4)]
    weights = [70, 20, 8, 2]  # one dominant symbol -> Huffman's sweet spot
    return bytes(rng.choices(syms, weights=weights, k=size))

def blocks(size, rng):        # long runs of one byte, then another (RLE-like)
    out = bytearray()
    while len(out) < size:
        run = min(rng.randint(256, 4096), size - len(out))
        out += bytes([rng.randrange(256)]) * run
    return bytes(out)

patterns = [
    ("single", single), ("cycle2", cycle2), ("cycle4", cycle4),
    ("skewed", skewed), ("blocks", blocks),
]

for i in range(1, n + 1):
    frac = (i - 1) / (n - 1)
    size = int(round(lo * (hi / lo) ** frac))
    size = max(1, int(size * random.uniform(0.7, 1.3)))
    name, fn = patterns[(i - 1) % len(patterns)]
    with open(os.path.join(out_dir, f"unif_{i:03d}_{name}.bin"), "wb") as f:
        f.write(fn(size, random))
print(f"wrote {n} files to {out_dir} (seed {seed})")
PY

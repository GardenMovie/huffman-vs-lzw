# Datasets — Population Guidelines

Benchmark inputs for the Huffman vs. LZW comparison. The five folders are chosen
to span the **full compressibility spectrum**, from "cannot be compressed at all"
to "compresses enormously." Each algorithm's strengths and weaknesses show up in
a different part of that spectrum, which is exactly what the report needs to
contrast.

> **Golden rule (see `project.md`):** always benchmark on **raw / uncompressed**
> bytes. Never feed already-compressed formats (JPEG, PNG, ZIP, MP3...) — their
> redundancy is already gone, so both algorithms show ~0% gain and the comparison
> is meaningless. Convert images to **PPM/BMP** and use **plain `.txt`** first.

## Summary

| Folder                | Files | Size each             | Expected result                          |
|-----------------------|-------|-----------------------|------------------------------------------|
| `uniform/`            | 10    | 1 KB–5.4 MB           | Both compress *massively* (best case)    |
| `illustration-images/`| 15 raw PPM (+15 originals) | ~6 MB–124 MB (PPM) | Huffman strong; LZW very strong (flats)  |
| `real-images/`        | 15 raw PPM (+15 originals) | ~6 MB–64 MB (PPM)  | Modest gains; may expand (photo noise)   |
| `real-text/`          | 13    | ~2 KB–2.8 MB          | Both compress well; LZW usually wins     |
| `random/`             | 20    | ~277 B–3 MB           | Neither compresses; slight expansion     |

Keep counts modest. These are size/behavior-scaling curves, not statistics — a
**log-spaced spread of sizes** per folder matters far more than raw file count.

---

## `random/` — incompressible control (worst case)

- **What:** true random bytes (`/dev/urandom` / `os.urandom`).
- **How many:** ~20 is plenty. 100 is overkill — every random file tells the same
  story, so extra files add runtime and noise, not information.
- **Sizes:** log-spaced from ~256 B to ~4 MB so you can plot overhead & timing vs.
  input size across several orders of magnitude.
- **Why:** uniform symbol frequencies defeat Huffman; no repeated substrings
  defeat LZW. Output should be **≥ input** — Huffman ≈ 1:1 plus tree overhead,
  LZW slightly expanding. This is the theoretical floor every other folder is
  measured against.

## `uniform/` — trivially compressible control (best case)

- **What:** highly redundant, low-entropy data: files of a single repeated byte,
  short repeating patterns (`ABABAB...`), long runs, a handful of distinct symbols.
- **How many:** ~10, varied patterns × varied sizes.
- **Sizes:** 1 KB up to a few MB.
- **Why:** the opposite extreme from `random/`. Huffman collapses to a tiny tree;
  LZW's dictionary captures the pattern almost immediately. Both should hit huge
  ratios — this is the ceiling, and it isolates each algorithm's fixed overhead.

## `illustration-images/` — synthetic / flat-color images

- **What:** illustrations, digital art, and flat-color wallpapers — large flat
  regions and relatively few distinct colors. Each image is present as its
  original `wallhaven-*.jpg`/`.png` download **plus** a raw `wallhaven-*.ppm`
  conversion. **Benchmark only the `.ppm` files** (per the golden rule — the
  originals are already-compressed formats and are kept purely as the conversion
  source).
- **How many:** 15 images, each as one original + one PPM (30 files total). Raw
  PPM sizes span ~6 MB to ~124 MB.
- **Why:** the sweet spot for **LZW** — big flat runs become long dictionary
  matches. Huffman also does well (skewed color histogram). Per project memory,
  flat wallpapers compress hugely here.

## `real-images/` — photographs

- **What:** photographic wallpapers — noise, gradients, texture. Each image is
  present as its original `wallhaven-*.jpg`/`.png` download **plus** a raw
  `wallhaven-*.ppm` conversion. **Benchmark only the `.ppm` files** (per the
  golden rule — the originals are already-compressed formats and are kept purely
  as the conversion source).
- **How many:** 15 images, each as one original + one PPM (30 files total). Raw
  PPM sizes span ~6 MB to ~64 MB.
- **Why:** sensor noise makes byte values near-random locally, so gains are modest
  and some files may **expand**. Per project memory, photo-heavy sets can grow —
  report both winners and losers rather than averaging them into one number.

## `real-text/` — natural language & structured text

- **What:** books/articles (plain `.txt`), source code, CSV/JSON logs. UTF-8/ASCII.
- **How many:** 13, mixing prose, code, and structured data.
- **Sizes:** ~2 KB to ~2.8 MB.
- **Why:** the classic case. Skewed character frequencies help **Huffman**;
  repeated words/tokens/whitespace help **LZW** (usually the winner on text).
  Code and CSV are more repetitive than prose — good intra-folder contrast.

---

## Reproducibility note

For `random/` and `uniform/`, seed the generator if you want the exact dataset
regenerable for the report. Real images/text should be committed (or listed with
a source) since they can't be regenerated. Record file provenance so results are
auditable.

### Image provenance (`illustration-images/`, `real-images/`)

The images come from a curated collection on **wallhaven.cc**; each file is named
by its wallhaven ID (`wallhaven-<id>.jpg`/`.png`), with a raw `.ppm` conversion
alongside it. **Detailed per-file provenance is TBD and will be filled in by the
maintainer.** Only the `.ppm` conversions are fed to the benchmark; the original
`.jpg`/`.png` downloads are kept solely as the conversion source.

### `real-text/` provenance

Thirteen raw, uncompressed files (`.txt` / `.csv` / `.c` / `.h`). Sizes interleave
into one log-spaced curve (~2 KB → ~2.8 MB) covering prose, structured data, and
source code.

| Files                        | Type   | Source                                                                 |
|------------------------------|--------|------------------------------------------------------------------------|
| `moby_01.txt`–`moby_06.txt`  | prose  | *Moby-Dick* — Project Gutenberg ebook #2701 (files begin with the `*** START OF THE PROJECT GUTENBERG EBOOK 2701 ***` marker) |
| `struct_diamonds_01.csv`–`05.csv` | CSV | ggplot2 `diamonds` dataset (header `"carat","cut","color","clarity",…`; full set is 53 940 rows) |
| `code_sort.c`                | source | Linux kernel `lib/sort.c` (SPDX `GPL-2.0`; "fast, small, non-recursive O(n log n) sort for the Linux kernel") |
| `code_stb_image.h`           | source | stb single-header image loader `stb_image.h` v2.30 (`nothings.org/stb`, public domain) |

The prose and CSV sets are each a single source file sliced into log-spaced
prefixes (trimmed to whole lines/rows; each CSV slice keeps the header). Re-fetch
the source files and re-slice to regenerate.

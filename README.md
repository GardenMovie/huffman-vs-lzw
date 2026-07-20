# Huffman vs. LZW Compression

Two generic, byte-stream **lossless** compressors implemented from scratch in Java —
**Huffman coding** (statistical, frequency-based) and **LZW** (dictionary-based) —
plus a CLI and a benchmark harness that compares them on time, space, and
compression ratio across a spectrum of inputs (raw images, text, and random data).

This is a data-structures & algorithms course project: implement both algorithms,
then compare them theoretically and experimentally to conclude which is more
effective and under what conditions.

## Repository layout

| Path | What it is |
|------|-----------|
| `src/compression/` | Java sources: the two codecs, a shared interface, a CLI, and benchmark harnesses. |
| `scripts/` | Shell scripts that regenerate the benchmark datasets and run the batch benchmarks. |
| `docs/` | Project brief, dataset guidelines, testing workflow, and collected results. |

## Quick start

```sh
# One-shot setup: create dataset folders + out/, then compile the Java sources.
# Add --populate to also fill the random/, uniform/, and real-text/ datasets.
./setup.sh                 # folders + out/ + compile
./setup.sh --populate      # also download/generate the datasets

# Compress / decompress a single file
java -cp out compression.Main huffman encode input.ppm out.huf
java -cp out compression.Main lzw     encode input.txt out.lzw

# Benchmark both algorithms on the same files
java -cp out compression.Benchmark file1 file2 ...
```

See [`docs/README.md`](docs/README.md) for full usage and [`docs/project.md`](docs/project.md)
for the assignment brief.

## Datasets

The benchmark inputs (raw `.ppm` images and `.bin` data, ~2.7 GB) are **not** stored
in the repository — they are large and some are regenerable.

Rebuild the synthetic/text sets in one step with `./setup.sh --populate`, or run the
individual scripts in [`scripts/`](scripts/) (`populate_*.sh` for the
synthetic/text sets, 

Images are found in this [Google Drive](https://drive.google.com/drive/folders/1QZAxA2YY_95jZG-Lrqbe5C0cx_RCYGC1?usp=sharing), use `uncompress_images.sh` to convert source images to raw PPM. 

See [`docs/datasets.md`](docs/datasets.md) for the population guidelines.
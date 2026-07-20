#!/usr/bin/env python3
"""Convert the per-dataset benchmark .txt reports in results/ into CSVs.

Produces one <dataset>.csv per dataset report (per-file, per-algo rows) plus
a summary.csv with the totals per dataset/algo.

Usage: make_csv.py [RESULTS_DIR]   (default: ../results relative to this script)
"""
import csv, re, glob, os, sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS = sys.argv[1] if len(sys.argv) > 1 else os.path.join(SCRIPT_DIR, "..", "results")

def num(s):
    return s.replace(",", "")

# --- per-dataset CSVs ---
# a data row starts with a filename, then algo, then 5 numeric cols,
# struct entries + struct bytes, then ok
row_re = re.compile(
    r"^(\S.*?)\s+(Huffman|LZW)\s+"
    r"([\d,]+)\s+([\d,]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+"
    r"([\d,]+)\s+([\d,]+)\s+(\w+)\s*$"
)

for path in sorted(glob.glob(os.path.join(RESULTS, "*.txt"))):
    name = os.path.basename(path)
    if name == "summary.txt":
        continue
    dataset = name[:-4]
    rows = []
    for line in open(path):
        m = row_re.match(line.rstrip("\n"))
        if not m:
            continue
        f, algo, orig, enc, ratio, enc_ms, dec_ms, s_ent, s_byt, ok = m.groups()
        rows.append([f, algo, num(orig), num(enc), ratio, enc_ms, dec_ms,
                     num(s_ent), num(s_byt), ok])
    out = os.path.join(RESULTS, dataset + ".csv")
    with open(out, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "algo", "original_bytes", "encoded_bytes",
                    "ratio", "encode_ms", "decode_ms",
                    "struct_entries", "struct_bytes", "ok"])
        w.writerows(rows)
    print(f"{out}: {len(rows)} rows")

# --- summary CSV (totals per dataset/algo) ---
# totals row: the two struct columns are "avg/peak" strings, not plain numbers
tot_re = re.compile(
    r"^(Huffman|LZW)\s+([\d,]+)\s+([\d,]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+"
    r"(\d+)/(\d+)\s+(\d+)/(\d+)\s*$"
)
summary_path = os.path.join(RESULTS, "summary.txt")
if os.path.exists(summary_path):
    summary_rows = []
    cur_ds = None
    for line in open(summary_path):
        line = line.rstrip("\n")
        h = re.match(r"^###\s+(\S+)", line)
        if h:
            cur_ds = h.group(1)
            continue
        m = tot_re.match(line)
        if m and cur_ds:
            (algo, tin, tout, ratio, enc_ms, dec_ms,
             ent_avg, ent_peak, byt_avg, byt_peak) = m.groups()
            summary_rows.append([cur_ds, algo, num(tin), num(tout), ratio, enc_ms, dec_ms,
                                 ent_avg, ent_peak, byt_avg, byt_peak])

    out = os.path.join(RESULTS, "summary.csv")
    with open(out, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["dataset", "algo", "total_in_bytes", "total_out_bytes",
                    "ratio", "encode_ms", "decode_ms",
                    "struct_entries_avg", "struct_entries_peak",
                    "struct_bytes_avg", "struct_bytes_peak"])
        w.writerows(summary_rows)
    print(f"{out}: {len(summary_rows)} rows")

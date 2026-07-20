package compression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Bulk runner for a whole directory of inputs. For every file passed, it runs
 * Huffman and LZW once each (single pass — this is a bulk report, not a timing
 * microbenchmark), prints one row per algorithm, verifies the lossless
 * round-trip, and at the end prints aggregate totals per algorithm.
 *
 * <p>Output goes to stdout so the caller can redirect it to a text file.
 *
 * <pre>
 *   java compression.BatchReport &lt;file1&gt; [file2 ...] &gt; results.txt
 * </pre>
 */
public final class BatchReport {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java compression.BatchReport <file1> [file2 ...]");
            System.exit(1);
        }

        List<Codec> codecs = List.of(new HuffmanCodec(), new LzwCodec());

        long[] totalIn = new long[codecs.size()];
        long[] totalOut = new long[codecs.size()];
        double[] totalEncodeMs = new double[codecs.size()];
        double[] totalDecodeMs = new double[codecs.size()];
        long[] sumStructEntries = new long[codecs.size()];
        int[] peakStructEntries = new int[codecs.size()];
        long[] sumStructBytes = new long[codecs.size()];
        int[] peakStructBytes = new int[codecs.size()];
        int filesOk = 0;
        int filesTotal = 0;

        System.out.printf("%-28s %-8s %12s %12s %8s %10s %10s %10s %10s %5s%n",
                "file", "algo", "original", "encoded", "ratio", "encode ms", "decode ms",
                "struct ent", "struct byt", "ok");
        System.out.println("-".repeat(120));

        for (String arg : args) {
            Path path = Path.of(arg);
            byte[] data;
            try {
                data = Files.readAllBytes(path);
            } catch (IOException e) {
                System.out.printf("%-28s  (skipped: %s)%n", path.getFileName(), e.getMessage());
                continue;
            }
            filesTotal++;
            boolean allOkForFile = true;

            for (int c = 0; c < codecs.size(); c++) {
                Codec codec = codecs.get(c);

                long t0 = System.nanoTime();
                byte[] encoded = codec.encode(data);
                double encodeMs = (System.nanoTime() - t0) / 1_000_000.0;

                long t1 = System.nanoTime();
                byte[] decoded = codec.decode(encoded);
                double decodeMs = (System.nanoTime() - t1) / 1_000_000.0;

                boolean ok = Arrays.equals(data, decoded);
                allOkForFile &= ok;

                int structEntries = codec.structureEntries(data);
                int structBytes = codec.structureBytes(data);

                double ratio = data.length == 0 ? 0 : (double) encoded.length / data.length;
                System.out.printf("%-28s %-8s %,12d %,12d %8.3f %10.1f %10.1f %,10d %,10d %5s%n",
                        truncate(path.getFileName().toString(), 28), codec.name(),
                        data.length, encoded.length, ratio, encodeMs, decodeMs,
                        structEntries, structBytes, ok ? "yes" : "NO");

                totalIn[c] += data.length;
                totalOut[c] += encoded.length;
                totalEncodeMs[c] += encodeMs;
                totalDecodeMs[c] += decodeMs;
                sumStructEntries[c] += structEntries;
                peakStructEntries[c] = Math.max(peakStructEntries[c], structEntries);
                sumStructBytes[c] += structBytes;
                peakStructBytes[c] = Math.max(peakStructBytes[c], structBytes);
            }
            if (allOkForFile) {
                filesOk++;
            }
        }

        System.out.println();
        System.out.println("=".repeat(100));
        System.out.printf("TOTALS across %d file(s)  (%d/%d round-tripped correctly)%n",
                filesTotal, filesOk, filesTotal);
        System.out.printf("%-8s %14s %14s %8s %12s %12s %14s %14s%n",
                "algo", "total in", "total out", "ratio", "encode ms", "decode ms",
                "struct ent a/p", "struct byt a/p");
        System.out.println("-".repeat(104));
        for (int c = 0; c < codecs.size(); c++) {
            double ratio = totalIn[c] == 0 ? 0 : (double) totalOut[c] / totalIn[c];
            double avgEntries = filesTotal == 0 ? 0 : (double) sumStructEntries[c] / filesTotal;
            double avgBytes = filesTotal == 0 ? 0 : (double) sumStructBytes[c] / filesTotal;
            System.out.printf("%-8s %,14d %,14d %8.3f %12.1f %12.1f %14s %14s%n",
                    codecs.get(c).name(), totalIn[c], totalOut[c], ratio,
                    totalEncodeMs[c], totalDecodeMs[c],
                    String.format("%.0f/%d", avgEntries, peakStructEntries[c]),
                    String.format("%.0f/%d", avgBytes, peakStructBytes[c]));
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private BatchReport() {
    }
}

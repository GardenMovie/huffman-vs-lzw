package compression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Benchmark harness: runs Huffman and LZW on the same input files and reports,
 * for each, the compression ratio, encode/decode time, and an approximation of
 * the auxiliary structure size (Huffman: the 256-entry frequency header;
 * LZW: peak dictionary entry count). It also verifies a lossless round-trip.
 *
 * <pre>
 *   java compression.Benchmark &lt;file1&gt; [file2 ...]
 * </pre>
 */
public final class Benchmark {

    private static final int WARMUP = 2;
    private static final int RUNS = 5;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java compression.Benchmark <file1> [file2 ...]");
            System.exit(1);
        }

        List<Codec> codecs = List.of(new HuffmanCodec(), new LzwCodec());

        for (String arg : args) {
            Path path = Path.of(arg);
            byte[] data = Files.readAllBytes(path);
            System.out.printf("%n=== %s (%,d bytes) ===%n", path.getFileName(), data.length);
            System.out.printf("%-10s %12s %8s %10s %10s %10s %10s %8s%n",
                    "algorithm", "encoded", "ratio", "encode ms", "decode ms",
                    "struct ent", "struct byt", "ok");

            for (Codec codec : codecs) {
                run(codec, data);
            }
        }
    }

    private static void run(Codec codec, byte[] data) {
        byte[] encoded = null;
        byte[] decoded = null;

        for (int i = 0; i < WARMUP; i++) {
            encoded = codec.encode(data);
            decoded = codec.decode(encoded);
        }

        double encodeMs = time(RUNS, () -> codec.encode(data));
        // Encode once more to have a stable buffer to decode in the timed loop.
        final byte[] enc = codec.encode(data);
        double decodeMs = time(RUNS, () -> codec.decode(enc));

        encoded = enc;
        decoded = codec.decode(enc);
        boolean ok = Arrays.equals(data, decoded);

        double ratio = data.length == 0 ? 0 : (double) encoded.length / data.length;
        System.out.printf("%-10s %,12d %8.3f %10.2f %10.2f %,10d %,10d %8s%n",
                codec.name(), encoded.length, ratio, encodeMs, decodeMs,
                codec.structureEntries(data), codec.structureBytes(data), ok ? "yes" : "NO");
    }

    /** Median wall-clock milliseconds over {@code runs} executions. */
    private static double time(int runs, Runnable task) {
        double[] samples = new double[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            task.run();
            samples[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(samples);
        return samples[runs / 2];
    }

    private Benchmark() {
    }
}

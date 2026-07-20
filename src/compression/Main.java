package compression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point. Compress or decompress a file with either algorithm.
 *
 * <pre>
 *   java compression.Main &lt;huffman|lzw&gt; &lt;encode|decode&gt; &lt;input&gt; &lt;output&gt;
 * </pre>
 *
 * Examples:
 * <pre>
 *   java compression.Main huffman encode image.bmp image.huf
 *   java compression.Main huffman decode image.huf image.out.bmp
 *   java compression.Main lzw     encode notes.txt notes.lzw
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            usage();
            System.exit(1);
        }

        Codec codec = codecFor(args[0]);
        String mode = args[1].toLowerCase();
        Path input = Path.of(args[2]);
        Path output = Path.of(args[3]);

        byte[] data = Files.readAllBytes(input);
        byte[] result;

        long start = System.nanoTime();
        switch (mode) {
            case "encode" -> result = codec.encode(data);
            case "decode" -> result = codec.decode(data);
            default -> {
                usage();
                System.exit(1);
                return;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        Files.write(output, result);

        System.out.printf("%s %s: %,d bytes -> %,d bytes", codec.name(), mode, data.length, result.length);
        if (mode.equals("encode") && data.length > 0) {
            double ratio = (double) result.length / data.length;
            System.out.printf(" (ratio %.3f, %.1f%% saved)", ratio, (1 - ratio) * 100);
        }
        System.out.printf(" in %d ms%n", elapsedMs);
    }

    private static Codec codecFor(String name) {
        return switch (name.toLowerCase()) {
            case "huffman" -> new HuffmanCodec();
            case "lzw" -> new LzwCodec();
            default -> {
                System.err.println("Unknown algorithm: " + name);
                usage();
                System.exit(1);
                yield null; // unreachable
            }
        };
    }

    private static void usage() {
        System.err.println("Usage: java compression.Main <huffman|lzw> <encode|decode> <input> <output>");
    }

    private Main() {
    }
}

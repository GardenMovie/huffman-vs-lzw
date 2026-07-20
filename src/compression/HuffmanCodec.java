package compression;

import java.io.ByteArrayOutputStream;
import java.util.PriorityQueue;

/**
 * Statistical, frequency-based lossless compression (Huffman coding).
 *
 * <p>Encoding:
 * <ol>
 *   <li>Count the frequency of every byte value (0..255) in the input.</li>
 *   <li>Build a binary tree, merging the two lowest-frequency nodes repeatedly,
 *       so frequent bytes end up near the root with short codes.</li>
 *   <li>Assign a prefix-free bit code to each byte and emit the bitstream.</li>
 * </ol>
 *
 * <p>The encoded stream is self-contained: it carries the 256-entry frequency
 * table and the original length, so the decoder rebuilds the identical tree
 * without any side channel.
 *
 * <p>Format:
 * <pre>
 *   [8 bytes]  original length N (big-endian long)
 *   [256 * 4]  frequency of each byte value (big-endian ints)
 *   [...]      packed bitstream, N symbols' worth of codes
 * </pre>
 */
public final class HuffmanCodec implements Codec {

    @Override
    public String name() {
        return "Huffman";
    }

    // ---- tree node ----------------------------------------------------------

    private static final class Node {
        final int symbol;      // byte value 0..255, or -1 for internal nodes
        final long freq;
        final Node left;
        final Node right;

        Node(int symbol, long freq) {
            this(symbol, freq, null, null);
        }

        Node(int symbol, long freq, Node left, Node right) {
            this.symbol = symbol;
            this.freq = freq;
            this.left = left;
            this.right = right;
        }

        boolean isLeaf() {
            return left == null && right == null;
        }
    }

    // ---- encoding -----------------------------------------------------------

    @Override
    public byte[] encode(byte[] input) {
        long[] freq = new long[256];
        for (byte b : input) {
            freq[b & 0xFF]++;
        }

        Node root = buildTree(freq);
        // codes[s] = bit string for symbol s (null if the symbol never appears)
        String[] codes = new String[256];
        if (root != null) {
            buildCodes(root, "", codes);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLong(out, input.length);
        for (int s = 0; s < 256; s++) {
            writeInt(out, (int) freq[s]);
        }

        BitWriter bits = new BitWriter(out);
        for (byte b : input) {
            bits.writeCode(codes[b & 0xFF]);
        }
        bits.flush();

        return out.toByteArray();
    }

    /**
     * Edge case: a single distinct symbol has no natural bit code (the tree is a
     * lone leaf). We give it the 1-bit code "0" so encoding still produces output.
     */
    private static void buildCodes(Node node, String prefix, String[] codes) {
        if (node.isLeaf()) {
            codes[node.symbol] = prefix.isEmpty() ? "0" : prefix;
            return;
        }
        buildCodes(node.left, prefix + "0", codes);
        buildCodes(node.right, prefix + "1", codes);
    }

    private static Node buildTree(long[] freq) {
        // Tie-break on an insertion sequence so the ordering is deterministic and
        // the decoder (which rebuilds from the same freq table) gets the same tree.
        PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> {
            int c = Long.compare(a.freq, b.freq);
            if (c != 0) return c;
            return Integer.compare(a.symbol, b.symbol);
        });
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                pq.add(new Node(s, freq[s]));
            }
        }
        if (pq.isEmpty()) {
            return null; // empty input
        }
        while (pq.size() > 1) {
            Node a = pq.poll();
            Node b = pq.poll();
            // Internal nodes carry symbol = min child symbol purely for a stable tie-break.
            pq.add(new Node(Math.min(a.symbol, b.symbol), a.freq + b.freq, a, b));
        }
        return pq.poll();
    }

    // ---- structure metrics --------------------------------------------------

    /** Distinct byte values present = number of leaves in the Huffman tree. */
    @Override
    public int structureEntries(byte[] input) {
        boolean[] seen = new boolean[256];
        int distinct = 0;
        for (byte b : input) {
            int s = b & 0xFF;
            if (!seen[s]) {
                seen[s] = true;
                distinct++;
            }
        }
        return distinct;
    }

    /** The frequency header is always 256 four-byte ints, regardless of input. */
    @Override
    public int structureBytes(byte[] input) {
        return 256 * 4;
    }

    // ---- decoding -----------------------------------------------------------

    @Override
    public byte[] decode(byte[] encoded) {
        int pos = 0;
        long length = readLong(encoded, pos);
        pos += 8;

        long[] freq = new long[256];
        for (int s = 0; s < 256; s++) {
            freq[s] = readInt(encoded, pos) & 0xFFFFFFFFL;
            pos += 4;
        }

        byte[] out = new byte[(int) length];
        if (length == 0) {
            return out;
        }

        Node root = buildTree(freq);

        // Single-symbol input: the tree is one leaf, so the bitstream carries no
        // navigable path. Just emit that symbol N times.
        if (root.isLeaf()) {
            java.util.Arrays.fill(out, (byte) root.symbol);
            return out;
        }

        BitReader bits = new BitReader(encoded, pos);
        for (int i = 0; i < length; i++) {
            Node node = root;
            while (!node.isLeaf()) {
                node = bits.readBit() == 0 ? node.left : node.right;
            }
            out[i] = (byte) node.symbol;
        }
        return out;
    }

    // ---- bit I/O ------------------------------------------------------------

    private static final class BitWriter {
        private final ByteArrayOutputStream out;
        private int current;   // buffer for up to 8 pending bits
        private int nBits;     // how many bits currently buffered

        BitWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        void writeCode(String code) {
            for (int i = 0; i < code.length(); i++) {
                current = (current << 1) | (code.charAt(i) - '0');
                if (++nBits == 8) {
                    out.write(current);
                    current = 0;
                    nBits = 0;
                }
            }
        }

        void flush() {
            if (nBits > 0) {
                current <<= (8 - nBits); // pad the final byte with zeros on the right
                out.write(current);
                current = 0;
                nBits = 0;
            }
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private int bytePos;
        private int bitPos; // 0..7, MSB first

        BitReader(byte[] data, int startByte) {
            this.data = data;
            this.bytePos = startByte;
        }

        int readBit() {
            int bit = (data[bytePos] >> (7 - bitPos)) & 1;
            if (++bitPos == 8) {
                bitPos = 0;
                bytePos++;
            }
            return bit;
        }
    }

    // ---- primitive serialization helpers ------------------------------------

    private static void writeLong(ByteArrayOutputStream out, long v) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (v >> (i * 8)) & 0xFF);
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        for (int i = 3; i >= 0; i--) {
            out.write((v >> (i * 8)) & 0xFF);
        }
    }

    private static long readLong(byte[] data, int pos) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (data[pos + i] & 0xFFL);
        }
        return v;
    }

    private static int readInt(byte[] data, int pos) {
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v = (v << 8) | (data[pos + i] & 0xFF);
        }
        return v;
    }
}

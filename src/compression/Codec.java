package compression;

/**
 * A lossless, byte-stream compressor. Both Huffman and LZW implement this so
 * the CLI and benchmark harness can treat them interchangeably.
 */
public interface Codec {

    /** Human-readable name, e.g. "Huffman" or "LZW". */
    String name();

    /** Compress raw input bytes into a self-contained encoded byte array. */
    byte[] encode(byte[] input);

    /** Reverse {@link #encode(byte[])}, recovering the original bytes exactly. */
    byte[] decode(byte[] encoded);

    /**
     * Number of entries in the auxiliary structure this codec builds for
     * {@code input}: Huffman → distinct byte values (tree leaves); LZW → peak
     * dictionary entry count. This is the "space complexity" of the structure,
     * independent of the header bytes actually written.
     */
    int structureEntries(byte[] input);

    /**
     * Bytes this codec's structure adds to the encoded stream: Huffman → the
     * fixed 1024-byte frequency header; LZW → 0 (the dictionary is rebuilt by
     * the decoder and never transmitted).
     */
    int structureBytes(byte[] input);
}

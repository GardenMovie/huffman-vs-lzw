package compression;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dictionary-based lossless compression (Lempel-Ziv-Welch).
 *
 * <p>Encoding builds a dictionary of byte sequences on the fly: it emits the
 * code for the longest known prefix of the remaining input, then adds that
 * prefix + the next byte as a new entry. The decoder rebuilds the identical
 * dictionary as it reads, so the dictionary is never transmitted.
 *
 * <p>Codes are written with a <b>variable bit width</b>: it starts at 9 bits
 * (the 256 single-byte entries need codes 0..255, plus room to grow) and widens
 * by one bit each time the dictionary fills the current width, up to a cap of
 * {@value #MAX_BITS} bits. When the cap is reached the dictionary is frozen
 * (no reset), which keeps encoder and decoder trivially in sync.
 *
 * <p>Format:
 * <pre>
 *   [8 bytes]  original length N (big-endian long) — lets us return an empty
 *              array for empty input without special bitstream cases
 *   [...]      packed variable-width code stream
 * </pre>
 */
public final class LzwCodec implements Codec {

    private static final int FIRST_CODE = 256;   // codes 0..255 are the single bytes
    private static final int START_BITS = 9;
    private static final int MAX_BITS = 16;       // dictionary caps at 65536 entries

    @Override
    public String name() {
        return "LZW";
    }

    // ---- encoding -----------------------------------------------------------

    @Override
    public byte[] encode(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLong(out, input.length);
        if (input.length == 0) {
            return out.toByteArray();
        }

        // Dictionary: byte sequence -> code. Seeded with all single bytes.
        Map<String, Integer> dict = new HashMap<>();
        for (int i = 0; i < FIRST_CODE; i++) {
            dict.put(String.valueOf((char) i), i);
        }
        int nextCode = FIRST_CODE;
        int codeBits = START_BITS;

        BitWriter bits = new BitWriter(out);
        // We build sequences as Strings of chars in 0..255 — a compact key that
        // never collides because each char holds exactly one byte value.
        StringBuilder current = new StringBuilder();
        current.append((char) (input[0] & 0xFF));

        for (int i = 1; i < input.length; i++) {
            char next = (char) (input[i] & 0xFF);
            String candidate = current.toString() + next;
            if (dict.containsKey(candidate)) {
                current.append(next);
            } else {
                bits.write(dict.get(current.toString()), codeBits);
                if (nextCode < (1 << MAX_BITS)) {
                    dict.put(candidate, nextCode++);
                    // Widen once the next code to be *emitted* wouldn't fit.
                    if (nextCode > (1 << codeBits) && codeBits < MAX_BITS) {
                        codeBits++;
                    }
                }
                current = new StringBuilder();
                current.append(next);
            }
        }
        bits.write(dict.get(current.toString()), codeBits);
        bits.flush();

        return out.toByteArray();
    }

    // ---- structure metrics --------------------------------------------------

    /**
     * Peak dictionary size: replays the same dictionary-building loop as
     * {@link #encode(byte[])} (without emitting codes) and returns the final
     * entry count, capped at {@value #MAX_BITS}-bit's worth of entries.
     */
    @Override
    public int structureEntries(byte[] input) {
        if (input.length == 0) {
            return FIRST_CODE;
        }
        Map<String, Integer> dict = new HashMap<>();
        for (int i = 0; i < FIRST_CODE; i++) {
            dict.put(String.valueOf((char) i), i);
        }
        int nextCode = FIRST_CODE;

        StringBuilder current = new StringBuilder();
        current.append((char) (input[0] & 0xFF));
        for (int i = 1; i < input.length; i++) {
            char next = (char) (input[i] & 0xFF);
            String candidate = current.toString() + next;
            if (dict.containsKey(candidate)) {
                current.append(next);
            } else {
                if (nextCode < (1 << MAX_BITS)) {
                    dict.put(candidate, nextCode++);
                }
                current = new StringBuilder();
                current.append(next);
            }
        }
        return nextCode;
    }

    /** The dictionary is rebuilt by the decoder, so it costs no header bytes. */
    @Override
    public int structureBytes(byte[] input) {
        return 0;
    }

    // ---- decoding -----------------------------------------------------------

    @Override
    public byte[] decode(byte[] encoded) {
        long length = readLong(encoded, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (length == 0) {
            return out.toByteArray();
        }

        // Dictionary as a list indexed by code; seeded with the single bytes.
        List<String> dict = new ArrayList<>();
        for (int i = 0; i < FIRST_CODE; i++) {
            dict.add(String.valueOf((char) i));
        }
        int codeBits = START_BITS;

        BitReader bits = new BitReader(encoded, 8);
        int code = bits.read(codeBits);
        String previous = dict.get(code);
        writeString(out, previous);

        while (out.size() < length) {
            // Widen in lockstep with the encoder: the read width must grow at the
            // same dictionary size the encoder grew its write width.
            if (dict.size() + 1 > (1 << codeBits)
                    && codeBits < MAX_BITS
                    && dict.size() < (1 << MAX_BITS)) {
                codeBits++;
            }

            code = bits.read(codeBits);
            String entry;
            if (code < dict.size()) {
                entry = dict.get(code);
            } else {
                // Special case: code not yet in the dictionary. It must be the
                // previous string plus its own first character (the "cScSc" case).
                entry = previous + previous.charAt(0);
            }
            writeString(out, entry);

            if (dict.size() < (1 << MAX_BITS)) {
                dict.add(previous + entry.charAt(0));
            }
            previous = entry;
        }

        return out.toByteArray();
    }

    // ---- bit I/O (variable width, MSB first) --------------------------------

    private static final class BitWriter {
        private final ByteArrayOutputStream out;
        private long buffer;
        private int nBits;

        BitWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        void write(int value, int width) {
            buffer = (buffer << width) | (value & ((1L << width) - 1));
            nBits += width;
            while (nBits >= 8) {
                nBits -= 8;
                out.write((int) (buffer >> nBits) & 0xFF);
            }
        }

        void flush() {
            if (nBits > 0) {
                out.write((int) (buffer << (8 - nBits)) & 0xFF);
                buffer = 0;
                nBits = 0;
            }
        }
    }

    private static final class BitReader {
        private final byte[] data;
        private int bytePos;
        private long buffer;
        private int nBits;

        BitReader(byte[] data, int startByte) {
            this.data = data;
            this.bytePos = startByte;
        }

        int read(int width) {
            while (nBits < width) {
                int next = bytePos < data.length ? (data[bytePos++] & 0xFF) : 0;
                buffer = (buffer << 8) | next;
                nBits += 8;
            }
            nBits -= width;
            return (int) (buffer >> nBits) & ((1 << width) - 1);
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static void writeString(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < s.length(); i++) {
            out.write(s.charAt(i) & 0xFF);
        }
    }

    private static void writeLong(ByteArrayOutputStream out, long v) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (v >> (i * 8)) & 0xFF);
        }
    }

    private static long readLong(byte[] data, int pos) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (data[pos + i] & 0xFFL);
        }
        return v;
    }
}

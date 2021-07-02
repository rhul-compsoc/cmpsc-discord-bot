package uk.co.hexillium.rhul.compsoc.crypto;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A helper for preventing button forgeries.  This will become redundant due to Discord's decision to actually implement
 *  a form of security on their buttons.  Currently, the Discord `hash` is not a required feild for clients to send,
 *  and so validation is currently not performed, and spoofing/forgery is still possible.
 */
public class HMAC {
    private static final byte INNER_PAD = 0x5c;
    private static final byte OUTER_PAD = 0x36;

    private final byte[] sk; //secret key
    private final byte[] ik; //inner key
    private final byte[] ok; //outer key
    private final MessageDigest md;

    public HMAC(byte[] secretKey, String hashFunction) throws NoSuchAlgorithmException {
        md = MessageDigest.getInstance(hashFunction);
        if (md.getDigestLength() < secretKey.length){
            sk = md.digest(secretKey);
            md.reset();
        } else {
            this.sk = secretKey;
        }
        ik = new byte[sk.length];
        ok = new byte[sk.length];
        for (int i = 0; i < sk.length; i++){
            ik[i] = (byte) (sk[i] ^ INNER_PAD);
            ok[i] = (byte) (sk[i] ^ OUTER_PAD);
        }
    }

    public HMAC(byte[] secretKey) throws NoSuchAlgorithmException {
        this(secretKey, "SHA-256");
    }

    /**
     * This method provides protection against tampering and fraudulent creation of messages.  Given the same parameters,
     * this will always be the same.  However, an adversary who does not know the secret key will not be able
     * to create this signature data.
     * <br><br>
     * This does not protect against replay attacks which feature the same message content, userID and channelID.
     * This does not provide any timeout security, other than if a timeout is included in the message, any tampering becomes evident.
     * <br><br>
     * The general contract for this is hash(outer_key || hash(inner_key || message || metadata))
     * @param message  The data that should be protected from modification and forgery
     * @param userID  The userID that this should be limited to.  If no such restriction is present,
     *                this should be set to some constant value.  The verify function will need to be presented
     *                with this value, even if it is not a private action, and therefore any private action should
     *                this parameter.
     *                <br> <br>
     *                This is used to prevent a user other than the intended user from sending this message.
     * @param channelID  The channelID that this should be limited to.  This prevents forgeries being executed under
     *                   channel contexts from which they were not sent.
     * @return a byte[] of verification data that may be used to validate the authenticity and integrity of this message.
     */
    public byte[] sign(byte[] message, long userID, long channelID){
        ByteBuffer inner = ByteBuffer.allocate(sk.length + message.length + (8 * 2));

        inner.put(ik);
        inner.put(message);
        inner.putLong(userID);
        inner.putLong(channelID);

        byte[] innerHash = md.digest(inner.array());

        md.update(ok);
        md.update(innerHash);

        return md.digest();
    }

    public static byte[] toByteArray(String str){
        return fromBase4096String(str);
    }

    public static String fromByteArray(byte[] bytes){
        return toBase4096String(bytes);
    }

    public boolean verify(byte[] message, byte[] signature, long userID, long channelID){
        return Arrays.equals(signature, sign(message, userID, channelID));
    }


//    //some quick and dirty fuzzing code for the 4096 conversion
//    public static void main(String[] args) {
//        Random rand = ThreadLocalRandom.current();
//        for (;;) {
//            byte[] data = new byte[rand.nextInt(2)+0];
//            rand.nextBytes(data);
//
//            String in = toBase4096String(data);
//            byte[] out = fromBase4096String(in);
//
//            if (!Arrays.equals(data, out)) {
//                System.out.println("data: " + Arrays.toString(data));
//                System.out.println("in: " + in);
//                System.out.println("out: " + Arrays.toString(out));
//                throw new RuntimeException("Fuzzing failed");
//            }
//        }
//    }

    /** Convert a byte[] into a base-4096 string.  Will hold 32 bytes in 23 chars. To find the number of chars =  (bytes * (2/3)) + 1
     *
     * @author cat
     * @param data the byte[] to be converted
     * @return returns a base-4096 representation.  Is not guaranteed to be safe to use in URLs and other encoding structures
     */
    public static String toBase4096String(byte[] data) {
        StringBuilder str = new StringBuilder(data.length);
        //States for state machine called per-byte
        final int BUFFER_EMPTY = 0;
        final int BUFFER_IN_1BYTE = 1;
        final int BUFFER_IN_2BYTES = 2;

        byte[] convBuffer = new byte[3]; //24 bits = 3 bytes / 2 b4096
        int state = BUFFER_EMPTY;
        for (int i = 0; i < data.length; i++) {
            byte input = data[i];

            switch (state) {
                case BUFFER_EMPTY:
                    Arrays.fill(convBuffer, (byte) 0x00);
                    convBuffer[0] = input;
                    state = BUFFER_IN_1BYTE;
                    break;
                case BUFFER_IN_1BYTE:
                    convBuffer[1] = input;
                    state = BUFFER_IN_2BYTES;
                    break;
                case BUFFER_IN_2BYTES:
                    convBuffer[2] = input;
                    //buffer full now, emit b4096
                    int combined = ((convBuffer[0] & 0xFF) << 16) | ((convBuffer[1] & 0xFF) << 8) | (convBuffer[2] & 0xFF);
                    char first = (char) ((combined >>> 12) & 0xFFF);
                    char second = (char) ((combined) & 0xFFF);
                    //append
                    str.append(first).append(second);
                    state = BUFFER_EMPTY;
                    break;
            }
        }
        //All input consumed... check state to see what needs to happen now
        //Append remaining buffered bytes and padding signal
        final char NO_PADDING = (char) 0xFF10;
        final char ONE_PADDING = (char) 0xFF11;
        final char TWO_PADDING = (char) 0xFF12;

        switch (state) {
            case BUFFER_EMPTY:
                str.append(NO_PADDING);
                break;
            case BUFFER_IN_1BYTE:
            {
                int combined = ((convBuffer[0] & 0xFF) << 16) | 0x0000;
                char first = (char) ((combined >>> 12) & 0xFFF);
                char second = (char) ((combined) & 0xFFF);
                //append
                str.append(first).append(second);
            }
            str.append(TWO_PADDING); //This is not a mistake. 1 byte in buffer -> 2 spurious bytes during decode
            break;
            case BUFFER_IN_2BYTES:
            {
                int combined = ((convBuffer[0] & 0xFF) << 16) | ((convBuffer[1] & 0xFF) << 8) | 0x00;
                char first = (char) ((combined >>> 12) & 0xFFF);
                char second = (char) ((combined) & 0xFFF);
                //append
                str.append(first).append(second);
            }
            str.append(ONE_PADDING);
            break;
        }

        return str.toString();
    }

    /** Returns the byte[] represented by a base-4096 string.
     *
     * @see HMAC#toBase4096String(byte[])
     * @author cat
     * @param str The base-4069 string
     * @return the byte[] that is represented by the string
     * @throws IllegalArgumentException if the String was not a base-4069 string.
     */
    public static byte[] fromBase4096String(String str) {
        if (str.length() == 0) return new byte[] {};
        if ((str.length() - 1) % 2 != 0) throw new IllegalArgumentException("String length is unexpected");

        final char NO_PADDING = (char) 0xFF10;
        final char ONE_PADDING = (char) 0xFF11;
        final char TWO_PADDING = (char) 0xFF12;
        //Determine padding declaration
        int bytesInLastChar = 0;
        switch (str.charAt(str.length()-1)) {
            case NO_PADDING:
                bytesInLastChar = 0; //divides evenly, no handling
                break;
            case ONE_PADDING:
                bytesInLastChar = 2; //Last character only encodes two bytes
                break;
            case TWO_PADDING:
                bytesInLastChar = 1; //Last character only encodes one byte
                break;
            default:
                throw new IllegalArgumentException("Padding declaration missing");
        }

        //Determine byte out buffer
        int normalCoded = str.length() - 1;
        //Original data is 3 / 2 the string length, plus padding handling
        int size = (normalCoded + normalCoded / 2);
        //This overestimates by 0-2. Subtract these extras depending on padding
        if (bytesInLastChar == 2) size -= 1;
        if (bytesInLastChar == 1) size -= 2;
        byte[] out = new byte[size];
        int currOut = 0;
        //Parse string
        for (int i = 0; i < normalCoded; i += 2) {
            char first = str.charAt(i);
            char second = str.charAt(i+1);

            //combine both chars 12 bits together
            int combined = (first & 0xFFF) << 12 | (second & 0xFFF);
            //bit twiddle out 3 bytes
            out[currOut] = (byte) ((combined >>> 16) & 0xFF);
            currOut++;
            if (i == normalCoded - 2 && bytesInLastChar == 1) break; //last char pair, only 1 byte output
            out[currOut] = (byte) ((combined >>> 8) & 0xFF);
            currOut++;
            if (i == normalCoded - 2 && bytesInLastChar == 2) break; //last char pair, only 2 byte output
            out[currOut] = (byte) ((combined >>> 0) & 0xFF);
            currOut++;
        }
        return out;
    }
}
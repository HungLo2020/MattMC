package mattmc.nbt;

import java.io.IOException;

/**
 * Exception thrown when NBT deserialization (reading) fails.
 * Provides additional context about what was being deserialized.
 */
public class NBTDeserializationException extends IOException {
    
    private final String tagName;
    private final byte tagType;
    private final long streamPosition;
    
    /**
     * Create a deserialization exception with context.
     * 
     * @param message The error message
     * @param tagName The name of the tag being deserialized (may be null if not yet read)
     * @param tagType The type ID of the tag being deserialized
     * @param streamPosition Approximate position in the stream (or -1 if unknown)
     * @param cause The underlying IOException
     */
    public NBTDeserializationException(String message, String tagName, byte tagType, long streamPosition, IOException cause) {
        super(buildMessage(message, tagName, tagType, streamPosition), cause);
        this.tagName = tagName;
        this.tagType = tagType;
        this.streamPosition = streamPosition;
    }
    
    /**
     * Create a deserialization exception with context.
     * 
     * @param message The error message
     * @param tagName The name of the tag being deserialized (may be null if not yet read)
     * @param tagType The type ID of the tag being deserialized
     * @param streamPosition Approximate position in the stream (or -1 if unknown)
     */
    public NBTDeserializationException(String message, String tagName, byte tagType, long streamPosition) {
        super(buildMessage(message, tagName, tagType, streamPosition));
        this.tagName = tagName;
        this.tagType = tagType;
        this.streamPosition = streamPosition;
    }
    
    private static String buildMessage(String message, String tagName, byte tagType, long streamPosition) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to deserialize NBT");
        sb.append(" [tag type: ").append(getTagTypeName(tagType)).append(" (").append(tagType).append(")]");
        if (tagName != null && !tagName.isEmpty()) {
            sb.append(" '").append(tagName).append("'");
        }
        if (streamPosition >= 0) {
            sb.append(" at position ~").append(streamPosition);
        }
        sb.append(": ").append(message);
        return sb.toString();
    }
    
    private static String getTagTypeName(byte type) {
        return switch (type) {
            case 0 -> "TAG_END";
            case 1 -> "TAG_BYTE";
            case 3 -> "TAG_INT";
            case 4 -> "TAG_LONG";
            case 5 -> "TAG_FLOAT";
            case 6 -> "TAG_DOUBLE";
            case 7 -> "TAG_BYTE_ARRAY";
            case 8 -> "TAG_STRING";
            case 9 -> "TAG_LIST";
            case 10 -> "TAG_COMPOUND";
            case 12 -> "TAG_LONG_ARRAY";
            default -> "UNKNOWN";
        };
    }
    
    public String getTagName() {
        return tagName;
    }
    
    public byte getTagType() {
        return tagType;
    }
    
    public long getStreamPosition() {
        return streamPosition;
    }
}

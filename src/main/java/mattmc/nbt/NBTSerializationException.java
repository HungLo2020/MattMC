package mattmc.nbt;

import java.io.IOException;

/**
 * Exception thrown when NBT serialization (writing) fails.
 * Provides additional context about what was being serialized.
 */
public class NBTSerializationException extends IOException {
    
    private final String tagName;
    private final String tagType;
    
    /**
     * Create a serialization exception with context.
     * 
     * @param message The error message
     * @param tagName The name of the tag being serialized (may be empty for root)
     * @param tagType The type of tag being serialized
     * @param cause The underlying IOException
     */
    public NBTSerializationException(String message, String tagName, String tagType, IOException cause) {
        super(buildMessage(message, tagName, tagType), cause);
        this.tagName = tagName;
        this.tagType = tagType;
    }
    
    /**
     * Create a serialization exception with context.
     * 
     * @param message The error message
     * @param tagName The name of the tag being serialized (may be empty for root)
     * @param tagType The type of tag being serialized
     */
    public NBTSerializationException(String message, String tagName, String tagType) {
        super(buildMessage(message, tagName, tagType));
        this.tagName = tagName;
        this.tagType = tagType;
    }
    
    private static String buildMessage(String message, String tagName, String tagType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to serialize NBT");
        if (tagType != null && !tagType.isEmpty()) {
            sb.append(" [").append(tagType).append("]");
        }
        if (tagName != null && !tagName.isEmpty()) {
            sb.append(" '").append(tagName).append("'");
        }
        sb.append(": ").append(message);
        return sb.toString();
    }
    
    public String getTagName() {
        return tagName;
    }
    
    public String getTagType() {
        return tagType;
    }
}

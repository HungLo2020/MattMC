package net.vulkanic;

import java.nio.ByteBuffer;

/**
 * Represents a GPU buffer for storing vertex, index, or uniform data.
 * 
 * Buffers can be mapped to CPU memory for data upload or download.
 */
public interface VulkanicBuffer {
    /**
     * Uploads data to the buffer.
     * 
     * @param data the data to upload
     */
    void upload(ByteBuffer data);
    
    /**
     * Uploads data to a portion of the buffer.
     * 
     * @param offset the offset in bytes
     * @param data the data to upload
     */
    void uploadSubData(int offset, ByteBuffer data);
    
    /**
     * Gets the size of the buffer in bytes.
     * 
     * @return the buffer size
     */
    int getSize();
    
    /**
     * Releases resources associated with this buffer.
     */
    void close();
}

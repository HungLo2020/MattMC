package frnsrc.sodium;

public interface VertexSerializer {
    void serialize(long srcBuffer, long dstBuffer, int count);
}

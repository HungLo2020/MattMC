package frnsrc.sodium;

import java.util.Iterator;

public interface ChunkRenderListIterable {
    Iterator<ChunkRenderList> iterator(boolean reverse);

    default Iterator<ChunkRenderList> iterator() {
        return this.iterator(false);
    }
}

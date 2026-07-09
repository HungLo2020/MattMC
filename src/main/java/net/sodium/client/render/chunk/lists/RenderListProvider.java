package net.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.TaskQueueType;
import net.sodium.client.render.viewport.Viewport;

import java.util.ArrayDeque;
import java.util.Map;

public interface RenderListProvider {
    ObjectArrayList<ChunkRenderList> getUnsortedRenderLists();

    Map<TaskQueueType, ArrayDeque<RenderSection>> getTaskLists();

    boolean needsRevisitForPendingUpdates();

    boolean orderIsSorted();

    default SortedRenderLists createRenderLists(Viewport viewport) {
        var sectionPos = viewport.getChunkCoord();
        var unsorted = this.getUnsortedRenderLists();

        return NativeRenderListSorter.prepareRenderLists(unsorted, sectionPos);
    }
}

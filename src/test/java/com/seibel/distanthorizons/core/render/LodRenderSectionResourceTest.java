package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native center/neighbor ownership on non-successful aggregate build paths. */
class LodRenderSectionResourceTest {
    private static final long POS = DhSectionPos.encode((byte) 6, 0, 0);

    @BeforeAll static void initializeConfigInRuntimeOrder() {
        ThreadPresetConfigEventHandler.INSTANCE.toString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void discardedNeighborLoadClosesCenterAndEverySuccessfulNativeNeighbor() throws Exception {
        int threads = Config.Common.MultiThreading.numberOfThreads.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        PriorityTaskPicker picker = new PriorityTaskPicker();
        PriorityTaskPicker.Executor executor = picker.createExecutor("native-section-owner-test");
        CountDownLatch release = new CountDownLatch(1);
        Field renderPool = ThreadPoolUtil.class.getDeclaredField("renderSectionLoadThreadPool");
        renderPool.setAccessible(true);
        Object oldPool = renderPool.get(null);
        renderPool.set(null, executor);
        try (ColumnRenderSource center = source(); ColumnRenderSource north = source();
             ColumnRenderSource south = source(); ColumnRenderSource west = source()) {
            CountDownLatch started = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            LodRenderSection section = section();
            Method load = LodRenderSection.class.getDeclaredMethod("getRenderSourceForPosAsync", long.class, EDhDirection.class);
            load.setAccessible(true);
            CompletableFuture<ColumnRenderSource> pending = (CompletableFuture<ColumnRenderSource>) load.invoke(section, POS, EDhDirection.EAST);
            CompletableFuture<ColumnRenderSource>[] neighbors = new CompletableFuture[] {
                CompletableFuture.completedFuture(north), CompletableFuture.completedFuture(south),
                CompletableFuture.completedFuture(west), pending
            };
            CompletableFuture<Void> aggregate = section.buildRenderDataAsync(center, neighbors, new CompletableFuture<>());
            assertFalse(aggregate.isDone());
            assertTrue(center.renderDataContainer.size() > 0);
            executor.clearQueue();
            assertThrows(CompletionException.class, aggregate::join);
            for (ColumnRenderSource owned : new ColumnRenderSource[]{center, north, south, west}) {
                assertEquals(0, owned.renderDataContainer.size(), "actual native storage must be closed after failed allOf");
            }
            section.close();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
            renderPool.set(null, oldPool);
            Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(threads);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelledOwnerReleasesAllAlreadyLoadedNativeSources() {
        try (ColumnRenderSource center = source(); ColumnRenderSource north = source();
             ColumnRenderSource south = source(); ColumnRenderSource west = source(); ColumnRenderSource east = source()) {
            CompletableFuture<ColumnRenderSource>[] neighbors = new CompletableFuture[] {
                CompletableFuture.completedFuture(north), CompletableFuture.completedFuture(south),
                CompletableFuture.completedFuture(west), CompletableFuture.completedFuture(east)
            };
            CompletableFuture<Void> owner = new CompletableFuture<>();
            owner.cancel(false);
            try (LodRenderSection section = section()) {
                section.buildRenderDataAsync(center, neighbors, owner).join();
            }
            for (ColumnRenderSource owned : new ColumnRenderSource[]{center, north, south, west, east}) {
                assertEquals(0, owned.renderDataContainer.size());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedNeighborStillReleasesNeighborsWhichCompleteLater() {
        try (ColumnRenderSource center = source(); ColumnRenderSource late = source()) {
            CompletableFuture<ColumnRenderSource> last = new CompletableFuture<>();
            CompletableFuture<ColumnRenderSource>[] neighbors = new CompletableFuture[] {
                CompletableFuture.failedFuture(new IllegalStateException("read failed")),
                CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null), last
            };
            LodRenderSection section = section();
            CompletableFuture<Void> aggregate = section.buildRenderDataAsync(center, neighbors, new CompletableFuture<>());
            assertFalse(aggregate.isDone());
            assertTrue(center.renderDataContainer.size() > 0);
            last.complete(late);
            assertThrows(CompletionException.class, aggregate::join);
            assertEquals(0, center.renderDataContainer.size());
            assertEquals(0, late.renderDataContainer.size());
            section.close();
        }
    }

    private static ColumnRenderSource source() { return ColumnRenderSource.createEmpty(POS, 1, 0); }
    private static LodRenderSection section() {
        return new LodRenderSection(POS, mock(LodQuadTree.class), mock(IDhClientLevel.class),
            mock(FullDataSourceProviderV2.class), new AtomicInteger());
    }
}

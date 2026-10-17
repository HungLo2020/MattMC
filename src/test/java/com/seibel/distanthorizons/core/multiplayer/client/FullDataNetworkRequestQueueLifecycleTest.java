package com.seibel.distanthorizons.core.multiplayer.client;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.multiplayer.NetworkOwnerTestSupport;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadReceiver;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static com.seibel.distanthorizons.core.multiplayer.NetworkOwnerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FullDataNetworkRequestQueueLifecycleTest {
    private static final long POS = DhSectionPos.encode((byte) 6, 2, 3);

    @Test void blockedDecodeRemainsPendingAndDiscardReleasesDtoWithoutCachingSuccess() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.scheduler.blockPicker();
            AtomicInteger consumed = new AtomicInteger();
            var result = fixture.queue.submitRequest(POS, source -> { consumed.incrementAndGet(); source.close(); });
            fixture.queue.tick(new DhBlockPos2D(0, 0));
            fixture.response.complete(new FullDataSourceResponseMessage(fixture.payload));
            assertEquals(1, fixture.scheduler.network.getQueueSize());
            assertFalse(result.isDone(), "receiving bytes is not successful decoding/consumption");
            fixture.scheduler.network.clearQueue();
            assertTrue(result.isCancelled(), "discard must terminate the owner's future");
            assertEquals(0, consumed.get());
            fixture.assertDtoReturned();
            assertFalse(fixture.succeeded());
            assertEquals(0, fixture.queue.getWaitingTaskCount());
            var retry = fixture.queue.submitRequest(POS, FullDataSourceV2::close);
            assertFalse(retry.isDone(), "cancelled positions remain eligible for another request");
            retry.cancel(false);
        }
    }

    @Test void successWaitsForActualConsumerCompletion() throws Exception {
        try (var fixture = new Fixture()) {
            CountDownLatch consumerEntered = new CountDownLatch(1);
            CountDownLatch releaseConsumer = new CountDownLatch(1);
            AtomicReference<FullDataSourceV2> received = new AtomicReference<>();
            try {
                var result = fixture.queue.submitRequest(POS, source -> {
                    received.set(source);
                    consumerEntered.countDown();
                    try { assertTrue(releaseConsumer.await(3, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new RuntimeException(e); }
                    finally { source.close(); }
                });
                fixture.queue.tick(new DhBlockPos2D(0, 0));
                fixture.response.complete(new FullDataSourceResponseMessage(fixture.payload));
                assertTrue(consumerEntered.await(3, TimeUnit.SECONDS), "real DTO decoded into a native source");
                assertTrue(received.get().nativeSource().isLive());
                assertFalse(result.isDone(), "consumer still owns unfinished work");
                assertFalse(fixture.succeeded());
                releaseConsumer.countDown();
                assertEquals(AbstractFullDataNetworkRequestQueue.ERequestResult.SUCCEEDED, result.get(3, TimeUnit.SECONDS));
                await(fixture::succeeded);
                fixture.assertDtoReturned();
                assertFalse(received.get().nativeSource().isLive());
            } finally { releaseConsumer.countDown(); }
        }
    }

    @Test void consumerFailureCannotPublishSuccessAndReturnsDto() throws Exception {
        try (var fixture = new Fixture()) {
            AtomicReference<FullDataSourceV2> received = new AtomicReference<>();
            var result = fixture.queue.submitRequest(POS, source -> {
                received.set(source);
                throw new IllegalStateException("consumer failed");
            });
            fixture.queue.tick(new DhBlockPos2D(0, 0));
            fixture.response.complete(new FullDataSourceResponseMessage(fixture.payload));
            await(() -> fixture.scheduler.network.getCompletedTaskCount() == 1);
            assertNotNull(received.get(), "the actual DTO decode ran");
            assertNotEquals(AbstractFullDataNetworkRequestQueue.ERequestResult.SUCCEEDED, result.getNow(null));
            assertFalse(fixture.succeeded());
            fixture.assertDtoReturned();
            assertFalse(received.get().nativeSource().isLive(), "failed handoff must close the unowned native source");
            result.cancel(false);
            received.get().close();
        }
    }

    @Test void requestCancellationDuringQueuedDecodeDoesNotConsumeOrCachePosition() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.scheduler.blockPicker();
            AtomicInteger consumed = new AtomicInteger();
            var result = fixture.queue.submitRequest(POS, source -> { consumed.incrementAndGet(); source.close(); });
            fixture.queue.tick(new DhBlockPos2D(0, 0));
            fixture.response.complete(new FullDataSourceResponseMessage(fixture.payload));
            assertTrue(result.cancel(false), "request remains cancellable until consumption succeeds");
            fixture.scheduler.network.clearQueue();
            fixture.scheduler.network.shutdownNow();
            fixture.scheduler.unblockPicker();
            fixture.assertDtoReturned();
            assertEquals(0, consumed.get());
            assertFalse(fixture.succeeded());
        }
    }

    @Test void rejectedDecodeClosesDtoOnceAndNeverCachesSuccess() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.scheduler.network.shutdown();
            var result = fixture.queue.submitRequest(POS, source -> fail("rejected decode cannot consume"));
            fixture.queue.tick(new DhBlockPos2D(0, 0));
            fixture.response.complete(new FullDataSourceResponseMessage(fixture.payload));
            assertTrue(result.isCancelled());
            fixture.assertDtoReturned();
            assertFalse(fixture.succeeded());
            assertEquals(0, fixture.queue.getWaitingTaskCount());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NetworkOwnerTestSupport scheduler = new NetworkOwnerTestSupport();
        final ClientNetworkState state = mock(ClientNetworkState.class);
        final NetworkSession session = mock(NetworkSession.class);
        final CompletableFuture<FullDataSourceResponseMessage> response = new CompletableFuture<>();
        final FullDataPayload payload = new FullDataPayload();
        final FullDataSourceV2DTO dto;
        final Object dtoReference;
        final TestQueue queue;

        Fixture() throws Exception {
            var level = mock(DhClientLevel.class);
            var wrapper = mock(IClientLevelWrapper.class);
            when(wrapper.getDhIdentifier()).thenReturn("owner-lifecycle-test");
            when(level.getLevelWrapper()).thenReturn(wrapper);
            when(level.getClientLevelWrapper()).thenReturn(wrapper);
            when(level.getTargetPosForGeneration()).thenReturn(new DhBlockPos2D(0, 0));
            when(state.isReady()).thenReturn(true);
            when(state.getSession()).thenReturn(session);
            when(session.sendRequest(any(FullDataSourceRequestMessage.class), eq(FullDataSourceResponseMessage.class))).thenReturn(response);
            var receiver = mock(FullDataPayloadReceiver.class);
            setField(state, "fullDataPayloadReceiver", receiver);
            try (var source = FullDataSourceV2.createEmpty(POS)) {
                dto = spy(FullDataSourceV2DTO.CreateFromDataSource(source, EDhApiDataCompressionMode.UNCOMPRESSED));
            }
            dtoReference = field(dto, "phantomReference");
            when(receiver.decodeDataSource(payload)).thenReturn(dto);
            payload.beaconBeams = List.of();
            queue = new TestQueue(state, level);
        }

        boolean succeeded() { return ((Set<?>) field(queue, "succeededPositions")).contains(POS); }
        void assertDtoReturned() {
            assertFalse(FullDataSourceV2DTO.ARRAY_LIST_POOL.phantomRefToCheckout.containsKey(dtoReference), "DTO's real pooled checkout was returned");
            verify(dto, times(1)).close();
        }
        @Override public void close() throws Exception {
            queue.close();
            scheduler.close();
            if (FullDataSourceV2DTO.ARRAY_LIST_POOL.phantomRefToCheckout.containsKey(dtoReference)) dto.close();
        }
    }

    private static final class TestQueue extends AbstractFullDataNetworkRequestQueue {
        TestQueue(ClientNetworkState state, DhClientLevel level) {
            super(state, level, false, new ConfigEntry.Builder<Boolean>().set(false).build());
        }
        @Override protected int getRequestRateLimit() { return 1000; }
        @Override protected boolean isSectionAllowedToGenerate(long pos, DhBlockPos2D target) { return true; }
        @Override protected boolean onBeforeRequest(long pos, CompletableFuture<ERequestResult> future) { return true; }
        @Override protected String getQueueName() { return "owner lifecycle test"; }
    }
}

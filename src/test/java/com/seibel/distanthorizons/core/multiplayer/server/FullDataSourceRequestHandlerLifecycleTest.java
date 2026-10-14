package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.level.ServerLevelModule;
import com.seibel.distanthorizons.core.multiplayer.NetworkOwnerTestSupport;
import com.seibel.distanthorizons.core.multiplayer.config.SessionConfig;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadSender;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.repo.BeaconBeamRepo;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static com.seibel.distanthorizons.core.multiplayer.NetworkOwnerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FullDataSourceRequestHandlerLifecycleTest {
    private static final long POS = DhSectionPos.encode((byte) 6, 2, 3);

    @Test void missingSyncExecutorReturnsRealPermitAndTerminalReply() throws Exception {
        try (var fixture = new Fixture()) {
            setField(ThreadPoolUtil.class, "fileHandlerThreadPool", null);
            fixture.handler.queueLodSyncForRequestMessage(fixture.player, fixture.request, fixture.limits);
            assertEquals(0, pending(fixture.limits.syncOnLoginRateLimiter));
            verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
        }
    }

    @Test void droppedSyncReadReturnsRealPermitAndTerminalReply() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.scheduler.blockPicker();
            fixture.handler.queueLodSyncForRequestMessage(fixture.player, fixture.request, fixture.limits);
            assertEquals(1, pending(fixture.limits.syncOnLoginRateLimiter));
            assertEquals(1, fixture.scheduler.io.getQueueSize());
            fixture.scheduler.io.clearQueue();
            assertEquals(0, pending(fixture.limits.syncOnLoginRateLimiter), "discard must release the acquired permit");
            verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
            verify(fixture.provider, never()).get(anyLong());
        }
    }

    @Test void droppedSyncCompressionClosesNativeSourceAndReturnsPermit() throws Exception {
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            // Occupy compression's worker independently while the IO stage runs.
            when(fixture.provider.getTimestampForPos(POS)).thenReturn(10L);
            when(fixture.provider.get(POS)).thenAnswer(invocation -> {
                fixture.scheduler.network.shutdown();
                return source;
            });
            fixture.handler.queueLodSyncForRequestMessage(fixture.player, fixture.request, fixture.limits);
            await(() -> fixture.scheduler.io.getCompletedTaskCount() == 1);
            assertFalse(source.nativeSource().isLive(), "rejected downstream work must close its source");
            verify(source, times(1)).close();
            assertEquals(0, pending(fixture.limits.syncOnLoginRateLimiter));
            verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
        }
    }

    @Test void cancelledGeneratedReadRemovesBothMapsAndClosesLateNativeSource() throws Exception {
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            CompletableFuture<FullDataSourceV2> read = new CompletableFuture<>();
            when(fixture.provider.getAsync(POS)).thenReturn(read);
            when(fixture.provider.isFullyGenerated(any())).thenReturn(true);
            fixture.handler.queueWorldGenForRequestMessage(fixture.player, fixture.request, fixture.limits);
            fixture.handler.cancelRequest(fixture.request.futureId);
            read.complete(source);
            assertFalse(source.nativeSource().isLive(), "source arriving after cancellation has no owner");
            verify(source, times(1)).close();
            assertEquals(0, pending(fixture.limits.generationRequestRateLimiter));
            assertMapsEmpty(fixture.handler);
        }
    }

    @Test void droppedGeneratedCompressionClosesSourceReturnsPermitAndRemovesIds() throws Exception {
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            fixture.scheduler.blockPicker();
            CompletableFuture<FullDataSourceV2> read = new CompletableFuture<>();
            when(fixture.provider.getAsync(POS)).thenReturn(read);
            when(fixture.provider.isFullyGenerated(any())).thenReturn(true);
            fixture.handler.queueWorldGenForRequestMessage(fixture.player, fixture.request, fixture.limits);
            read.complete(source);
            var tick = FullDataSourceRequestHandler.class.getDeclaredMethod("tick");
            tick.setAccessible(true);
            tick.invoke(fixture.handler);
            assertEquals(1, fixture.scheduler.network.getQueueSize());
            fixture.scheduler.network.clearQueue();
            fixture.handler.cancelRequest(fixture.request.futureId);
            fixture.scheduler.network.shutdownNow();
            assertFalse(source.nativeSource().isLive());
            verify(source, times(1)).close();
            assertEquals(0, pending(fixture.limits.generationRequestRateLimiter));
            assertMapsEmpty(fixture.handler);
            verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
        }
    }

    @Test void syncCancelDiscardAndShutdownCloseQueuedSourceExactlyOnce() throws Exception {
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            CountDownLatch blockerEntered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(fixture.provider.getTimestampForPos(POS)).thenReturn(10L);
            when(fixture.provider.get(POS)).thenAnswer(invocation -> {
                fixture.scheduler.network.execute(() -> {
                    blockerEntered.countDown();
                    try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                return source;
            });
            try {
                fixture.handler.queueLodSyncForRequestMessage(fixture.player, fixture.request, fixture.limits);
                assertTrue(blockerEntered.await(3, TimeUnit.SECONDS));
                assertEquals(1, fixture.scheduler.network.getQueueSize());
                fixture.handler.cancelRequest(fixture.request.futureId);
                fixture.scheduler.network.clearQueue();
                fixture.scheduler.network.shutdownNow();
                fixture.handler.close();
                verify(source, times(1)).close();
                assertFalse(source.nativeSource().isLive());
                assertEquals(0, pending(fixture.limits.syncOnLoginRateLimiter));
                assertTrue(((Map<?, ?>) field(fixture.handler, "syncRequestsByFutureId")).isEmpty());
                verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
            } finally { release.countDown(); }
        }
    }

    @Test void discardedGeneratedReadReturnsPermitAndRemovesBothIndexes() throws Exception {
        try (var fixture = new Fixture()) {
            var read = new CompletableFuture<FullDataSourceV2>();
            when(fixture.provider.getAsync(POS)).thenReturn(read);
            fixture.handler.queueWorldGenForRequestMessage(fixture.player, fixture.request, fixture.limits);
            assertEquals(1, pending(fixture.limits.generationRequestRateLimiter));
            read.cancel(false);
            assertEquals(0, pending(fixture.limits.generationRequestRateLimiter));
            assertMapsEmpty(fixture.handler);
            verify(fixture.session, times(1)).sendMessage(any(AbstractNetworkMessage.class));
        }
    }

    @Test void immediateGeneratedReadCompletionCannotLeaveStaleIndexes() throws Exception {
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            when(fixture.provider.getAsync(POS)).thenReturn(CompletableFuture.completedFuture(source));
            when(fixture.provider.isFullyGenerated(any())).thenReturn(true);
            fixture.handler.queueWorldGenForRequestMessage(fixture.player, fixture.request, fixture.limits);
            fixture.handler.cancelRequest(fixture.request.futureId);
            fixture.handler.close();
            verify(source, times(1)).close();
            assertMapsEmpty(fixture.handler);
            assertEquals(0, pending(fixture.limits.generationRequestRateLimiter));
        }
    }

    @Test void successfulSyncReleasesPermitAndClosesSourceExactlyOnce() throws Exception {
        AtomicReference<FullDataPayload> sentPayload = new AtomicReference<>();
        try (var fixture = new Fixture(); var resource = FullDataSourceV2.createEmpty(POS)) {
            var source = spy(resource);
            when(fixture.provider.getTimestampForPos(POS)).thenReturn(10L);
            when(fixture.provider.get(POS)).thenReturn(source);
            doAnswer(invocation -> {
                sentPayload.set(invocation.getArgument(0));
                ((Runnable) invocation.getArgument(1)).run();
                return null;
            }).when(fixture.player.fullDataPayloadSender).sendInChunks(any(), any());
            fixture.handler.queueLodSyncForRequestMessage(fixture.player, fixture.request, fixture.limits);
            await(() -> fixture.scheduler.network.getCompletedTaskCount() == 1);
            FullDataPayload payload = sentPayload.get();
            assertNotNull(payload, "success requires the actual compressed payload to reach the sender");
            assertTrue(payload.dtoBuffer.isReadable());
            verify(fixture.player.fullDataPayloadSender, times(1)).sendInChunks(same(payload), any());
            var response = ArgumentCaptor.forClass(AbstractNetworkMessage.class);
            verify(fixture.session, times(1)).sendMessage(response.capture());
            FullDataSourceResponseMessage success = assertInstanceOf(FullDataSourceResponseMessage.class, response.getValue());
            assertSame(payload, success.payload);
            assertEquals(fixture.request.futureId, success.futureId);
            verify(source, times(1)).close();
            assertFalse(source.nativeSource().isLive());
            assertEquals(0, pending(fixture.limits.syncOnLoginRateLimiter));
            assertTrue(((Map<?, ?>) field(fixture.handler, "syncRequestsByFutureId")).isEmpty());
        } finally {
            // The boundary sender is mocked, so this fixture retains the encoded buffer.
            FullDataPayload payload = sentPayload.get();
            if (payload != null) payload.dtoBuffer.release();
        }
    }

    private static void assertMapsEmpty(FullDataSourceRequestHandler handler) {
        assertTrue(((Map<?, ?>) field(handler, "requestGroupsByPos")).isEmpty());
        assertTrue(((Map<?, ?>) field(handler, "requestGroupsByFutureId")).isEmpty());
    }

    private static int pending(Object limiter) {
        return ((AtomicInteger) field(field(limiter, "concurrencyLimiter"), "pendingTasks")).get();
    }

    private static final class Fixture implements AutoCloseable {
        final NetworkOwnerTestSupport scheduler = new NetworkOwnerTestSupport();
        final GeneratedFullDataSourceProvider provider = mock(GeneratedFullDataSourceProvider.class);
        final NetworkSession session = mock(NetworkSession.class);
        final ServerPlayerState player = mock(ServerPlayerState.class);
        final ServerPlayerState.RateLimiterSet limits = player.new RateLimiterSet();
        final FullDataSourceRequestMessage request;
        final FullDataSourceRequestHandler handler;

        Fixture() {
            var wrapper = mock(IServerLevelWrapper.class);
            when(wrapper.getDhIdentifier()).thenReturn("owner-lifecycle-test");
            var level = mock(AbstractDhServerLevel.class);
            when(level.getServerLevelWrapper()).thenReturn(wrapper);
            when(level.getLevelWrapper()).thenReturn(wrapper);
            var module = mock(ServerLevelModule.class);
            setField(module, "fullDataFileHandler", provider);
            setField(level, "serverside", module);
            level.beaconBeamRepo = mock(BeaconBeamRepo.class);
            var config = mock(SessionConfig.class);
            when(config.getSynchronizeOnLoad()).thenReturn(true);
            when(config.isDistantGenerationEnabled()).thenReturn(true);
            setField(player, "sessionConfig", config);
            setField(player, "networkSession", session);
            setField(player, "fullDataPayloadSender", mock(FullDataPayloadSender.class));
            request = new FullDataSourceRequestMessage(wrapper, POS, null);
            request.setSession(session);
            handler = new FullDataSourceRequestHandler(level);
            ((ThreadPoolExecutor) field(handler, "tickerThread")).shutdownNow();
        }

        @Override public void close() throws Exception { handler.close(); scheduler.close(); }
    }
}

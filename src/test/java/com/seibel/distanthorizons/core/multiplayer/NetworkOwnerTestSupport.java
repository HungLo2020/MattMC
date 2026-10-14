package com.seibel.distanthorizons.core.multiplayer;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real one-slot scheduler; only the world and transport/storage boundaries are mocked. */
public final class NetworkOwnerTestSupport implements AutoCloseable {
    static { com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler.INSTANCE.toString(); }
    public final PriorityTaskPicker picker = new PriorityTaskPicker();
    public final PriorityTaskPicker.Executor io;
    public final PriorityTaskPicker.Executor network;
    private final IMinecraftSharedWrapper installedSharedWrapper;
    private final List<PriorityTaskPicker.Executor> executors = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final int oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
    private final double oldRatio = Config.Common.MultiThreading.threadRunTimeRatio.get();
    private final Object oldWorld = field(SharedApi.class, "currentWorld");
    private final Object oldIo = field(ThreadPoolUtil.class, "fileHandlerThreadPool");
    private final Object oldNetwork = field(ThreadPoolUtil.class, "networkCompressionThreadPool");

    public NetworkOwnerTestSupport() {
        installedSharedWrapper = initializeCompressionConfig();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(1.0);
        AbstractDhWorld world = mock(AbstractDhWorld.class);
        setField(world, "environment", EWorldEnvironment.SERVER_ONLY);
        setField(SharedApi.class, "currentWorld", world);
        io = executor("Network owner IO test");
        network = executor("Network owner compression test");
        setField(ThreadPoolUtil.class, "fileHandlerThreadPool", io);
        setField(ThreadPoolUtil.class, "networkCompressionThreadPool", network);
    }

    private static IMinecraftSharedWrapper initializeCompressionConfig() {
        IMinecraftSharedWrapper installed = null;
        if (SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class, true) == null) {
            installed = mock(IMinecraftSharedWrapper.class);
            when(installed.getDelayedSetupComplete()).thenReturn(true);
            when(installed.getInstallationDirectory()).thenReturn(Path.of("run").toFile());
            SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, installed);
        }
        try {
            // FullDataPayload uses this config on the compression worker. Initialize
            // it while its environment dependency is bound, before any worker starts.
            assertNotNull(Config.Common.LodBuilding.dataCompression.get());
            return installed;
        } catch (RuntimeException | Error failure) {
            if (installed != null) SingletonInjector.INSTANCE.unbind(IMinecraftSharedWrapper.class, installed);
            throw failure;
        }
    }

    private PriorityTaskPicker.Executor executor(String name) {
        var result = picker.createExecutor(name);
        executors.add(result);
        return result;
    }

    public void blockPicker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        executor("Network owner blocker").execute(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS), "real picker worker entered blocker");
    }

    public void unblockPicker() { release.countDown(); }

    public static void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (!condition.getAsBoolean()) Thread.sleep(1);
        });
    }

    public static Object field(Object owner, String name) {
        try { return findField(owner, name).get(owner instanceof Class<?> ? null : owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    public static void setField(Object owner, String name, Object value) {
        try { findField(owner, name).set(owner instanceof Class<?> ? null : owner, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Field findField(Object owner, String name) throws NoSuchFieldException {
        Class<?> type = owner instanceof Class<?> cls ? cls : owner.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @Override public void close() throws Exception {
        release.countDown();
        for (var executor : executors) { executor.shutdownNow(); executor.awaitTermination(3, TimeUnit.SECONDS); }
        for (var executor : executors) Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
        setField(ThreadPoolUtil.class, "fileHandlerThreadPool", oldIo);
        setField(ThreadPoolUtil.class, "networkCompressionThreadPool", oldNetwork);
        setField(SharedApi.class, "currentWorld", oldWorld);
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(oldRatio);
        if (installedSharedWrapper != null) SingletonInjector.INSTANCE.unbind(IMinecraftSharedWrapper.class, installedSharedWrapper);
    }
}

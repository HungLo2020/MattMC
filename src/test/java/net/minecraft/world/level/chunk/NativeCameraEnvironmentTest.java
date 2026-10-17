package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeCameraEnvironmentTest {
    private static class CustomCache extends net.minecraft.client.multiplayer.ClientChunkCache {
        CustomCache() { super(null, 0); }
    }
    private static class CustomChunk extends LevelChunk {
        CustomChunk() { super(null, null); }
        @Override public LevelChunkSection[] getSections() { throw new AssertionError("Custom sections queried by native admission"); }
        @Override public ChunkPos getPos() { throw new AssertionError("Custom position queried by native admission"); }
    }
    private static final int TAGS = 6 | (24 << 5);
    private static final Vector3f FORWARD = new Vector3f(0, 0, 1), LEFT = new Vector3f(1, 0, 0), UP = new Vector3f(0, 1, 0);
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); assertTrue(NativeBlockRegistry.ready());
    }
    private record Fixture(LevelChunk chunk, NativeClientResidency world, long id) {
        LevelChunkSection section(int i) { return chunk.getSectionsForRead()[i]; }
        int query(Vec3 position) { return query(position, TAGS); }
        int query(Vec3 position, int tags) {
            return world.cameraEnvironment(0, 31, position, FORWARD, LEFT, UP, 0.1, 1., tags);
        }
        void refresh() { world.refreshWorldInputs(chunk, 0, id); }
    }
    private static Fixture fixture() throws Exception {
        var chunk = mock(LevelChunk.class, CALLS_REAL_METHODS);
        var level = mock(ClientLevel.class);
        doReturn(level).when(chunk).getLevel();
        set(ChunkAccess.class, chunk, "levelHeightAccessor", level);
        doReturn(0).when(chunk).getMinY(); doReturn(32).when(chunk).getHeight();
        doReturn(new ChunkPos(0, 0)).when(chunk).getPos();
        var sections = new LevelChunkSection[2];
        for (int i = 0; i < 2; i++) sections[i] = new LevelChunkSection(
            new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
        set(ChunkAccess.class, chunk, "sections", sections);
        var world = NativeClientResidency.create(3); assertNotNull(world);
        long id = world.publish(0, chunk); world.admitted(); world.addEmpty(chunk, 0, id);
        var fixture = new Fixture(chunk, world, id);
        chunk.setNativeSectionArrayEscapeListener(fixture::refresh);
        return fixture;
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    @Test void primaryMutationAndCrossSectionAboveReadNeedNoJavaRepacking() throws Exception {
        var f = fixture(); var point = new Vec3(0.5, 15.95, 0.5);
        assertEquals(5, f.query(point));
        f.section(0).setBlockState(0, 15, 0, Blocks.WATER.defaultBlockState());
        assertEquals(5, f.query(point));
        f.section(1).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        assertEquals(1, f.query(point));
        assertEquals(5, f.query(point, 0));
        f.section(1).setBlockState(0, 0, 0, Blocks.AIR.defaultBlockState());
        assertEquals(5, f.query(point));
    }
    @Test void nearPlaneCornersFindPowderAndLavaInOriginalOrder() throws Exception {
        var f = fixture(); var point = new Vec3(0.95, 0.95, 0.95);
        f.section(0).setBlockState(1, 1, 1, Blocks.POWDER_SNOW.defaultBlockState());
        assertEquals(2, f.query(point));
        f.section(0).setBlockState(0, 0, 1, Blocks.LAVA.defaultBlockState());
        f.section(0).setBlockState(0, 1, 1, Blocks.LAVA.defaultBlockState());
        assertEquals(0, f.query(point));
    }
    @Test void mutableArrayEscapeRetiresBeforeCallerReplacesEntries() throws Exception {
        var f = fixture(); f.section(0).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        assertEquals(1, f.query(new Vec3(0.5, 0.5, 0.5)));
        var array = f.chunk.getSections();
        assertEquals(-1, f.query(new Vec3(0.5, 0.5, 0.5)));
        array[0] = null;
        assertEquals(-1, f.query(new Vec3(0.5, 0.5, 0.5)));
    }
    @Test void paletteReplacementDeclinesUntilNewBindingIsPublished() throws Exception {
        var f = fixture(); var point = new Vec3(0.5, 0.5, 0.5);
        f.section(0).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        assertEquals(1, f.query(point));
        var states = f.section(0).getStates();
        states.installGenerated(0, List.of(Blocks.LAVA.defaultBlockState()), new long[0]);
        assertEquals(-1, f.query(point));
        f.refresh();
        assertEquals(0, f.query(point));
    }
    @Test void nativeGenerationReplacementRetiresOnlyTheReplacedOwner() throws Exception {
        var f = fixture(); var point = new Vec3(0.5, 0.5, 0.5);
        f.section(0).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        var replacement = new PalettedContainer<>(Blocks.LAVA.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        f.section(0).getStates().installNativeGenerated(replacement.nativeLiveBlocks());
        assertEquals(-1, f.query(point));
        f.refresh(); assertEquals(0, f.query(point));
    }
    @Test void successfulNetworkReplacementRetiresBindingButFailedFreshReadPreservesIt() throws Exception {
        var f = fixture(); var point = new Vec3(0.5, 0.5, 0.5);
        f.section(0).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        var source = new PalettedContainer<>(Blocks.LAVA.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(0); // Fresh palette differs from the four-bit live owner.
            assertThrows(IndexOutOfBoundsException.class, () -> f.section(0).getStates().read(buffer));
            assertEquals(1, f.query(point));
            buffer.clear(); source.write(buffer); f.section(0).getStates().read(buffer);
            assertEquals(-1, f.query(point));
            f.refresh(); assertEquals(0, f.query(point));
        } finally { buffer.release(); }
    }
    @Test void centerDropAndCustomSlotDoNotBecomeStaleWorldReads() throws Exception {
        var f = fixture(); var point = new Vec3(0.5, 0.5, 0.5);
        f.section(0).setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        f.world.center(100, 100); assertEquals(5, f.query(point));
        f.world.center(0, 0); assertEquals(1, f.query(point));
        f.world.publish(0, true); assertEquals(-1, f.query(point));
        f.world.dropped(0); assertEquals(5, f.query(point));
        assertEquals(5, f.query(new Vec3(0.5, 32.5, 0.5)));
    }
    @Test void tagProjectionFollowsRebindingAndDeclinesUnboundHolders() throws Exception {
        var fluids = new net.minecraft.world.level.material.Fluid[]{
            net.minecraft.world.level.material.Fluids.EMPTY, net.minecraft.world.level.material.Fluids.FLOWING_WATER,
            net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.FLOWING_LAVA,
            net.minecraft.world.level.material.Fluids.LAVA};
        var type = net.minecraft.core.Holder.Reference.class;
        var tags = type.getDeclaredField("tags"); tags.setAccessible(true);
        var bind = type.getDeclaredMethod("bindTags", java.util.Collection.class); bind.setAccessible(true);
        Object[] previous = new Object[5];
        for (int i = 0; i < 5; i++) previous[i] = tags.get(fluids[i].builtInRegistryHolder());
        try {
            for (var fluid : fluids) bind.invoke(fluid.builtInRegistryHolder(), List.of());
            assertEquals(0, NativeClientResidency.cameraFluidTags());
            bind.invoke(fluids[2].builtInRegistryHolder(), List.of(net.minecraft.tags.FluidTags.WATER));
            assertEquals(4, NativeClientResidency.cameraFluidTags());
            tags.set(fluids[0].builtInRegistryHolder(), null);
            net.minecraft.core.Holder.TAG_GENERATION.incrementAndGet();
            assertEquals(-1, NativeClientResidency.cameraFluidTags());
        } finally {
            for (int i = 0; i < 5; i++) tags.set(fluids[i].builtInRegistryHolder(), previous[i]);
            net.minecraft.core.Holder.TAG_GENERATION.incrementAndGet();
        }
    }
    @Test void customCacheDeclinesBeforeTouchingItsUninitializedStockStorage() {
        var cache = mock(CustomCache.class, CALLS_REAL_METHODS);
        assertEquals(-1, cache.nativeCameraEnvironment(Vec3.ZERO, FORWARD, LEFT, UP, 0.1, 1., TAGS));
    }
    @Test void customChunkAdmissionDoesNotAddGetterCallbacks() {
        var chunk = mock(CustomChunk.class, CALLS_REAL_METHODS);
        var world = NativeClientResidency.create(3); assertNotNull(world);
        long id = world.publish(0, true);
        world.refreshWorldInputs(chunk, 0, id);
        assertEquals(-1, world.cameraEnvironment(0, 31, Vec3.ZERO, FORWARD, LEFT, UP, 0.1, 1., TAGS));
    }
}

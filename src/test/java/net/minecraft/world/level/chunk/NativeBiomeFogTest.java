package net.minecraft.world.level.chunk;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.hooks.FogColorHooks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.util.color.FastCubicSampler;
import net.sodium.fabric.SodiumFogColorHook;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeBiomeFogTest {
    @BeforeAll static void load() { NativeLiveBiomeSectionTest.load(); }

    private static LevelChunk chunk(int x, int z, PalettedContainerFactory factory) throws Exception {
        LevelChunk chunk = mock(LevelChunk.class, CALLS_REAL_METHODS);
        doReturn(new ChunkPos(x, z)).when(chunk).getPos();
        var sections = new LevelChunkSection[3];
        for (int section = 0; section < 3; section++) {
            sections[section] = new LevelChunkSection(factory);
            @SuppressWarnings("unchecked")
            var biomes = (PalettedContainer<Holder<Biome>>) sections[section].getBiomes();
            for (int y = 0; y < 4; y++) for (int k = 0; k < 4; k++) for (int i = 0; i < 4; i++) {
                int id = Math.floorMod(x * 157 + z * 113 + section * 71 + y * 31 + k * 11 + i, 512);
                biomes.set(i, y, k, NativeLiveBiomeSectionTest.values.get(id));
            }
        }
        Field field = ChunkAccess.class.getDeclaredField("sections");
        field.setAccessible(true); field.set(chunk, sections);
        return chunk;
    }

    @Test void directFogAndSkyMatchScalarSamplingAcrossMutationsClampingAndUnload() throws Exception {
        var factory = new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
            Blocks.AIR.defaultBlockState(), null, NativeLiveBiomeSectionTest.strategy,
            NativeLiveBiomeSectionTest.values.get(0), null);
        var world = NativeBiomeWorld.create(factory, -16, 48); assertNotNull(world);
        world.range(0, 0, 3);
        var chunks = new HashMap<ChunkPos, LevelChunk>();
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            var chunk = chunk(x, z, factory); chunks.put(chunk.getPos(), chunk); world.replace(chunk);
        }
        BiomeManager manager = new BiomeManager((x, y, z) -> {
            LevelChunk chunk = chunks.get(new ChunkPos(x >> 2, z >> 2));
            if (chunk == null) return factory.defaultBiome();
            int relativeY = Math.clamp(y, -4, 7) + 4;
            return chunk.getSection(relativeY >> 2).getBiomes().get(x & 3, relativeY & 3, z & 3);
        }, 0);
        Vec3[] positions = {new Vec3(1.25, .5, 1.75), new Vec3(1.75, .75, 1.25), new Vec3(-.1, -100, -.9),
            new Vec3(3.999, 100, 3.125), new Vec3(7.25, .5, 7.5)};
        for (int step = 0; step < 4; step++) {
            for (Vec3 position : positions) {
                Vec3 fog = FastCubicSampler.sampleColor(position,
                    (x, y, z) -> manager.getNoiseBiomeAtQuart(x, y, z).value().getFogColor(), Function.identity());
                Vec3 sky = FastCubicSampler.sampleColor(position,
                    (x, y, z) -> manager.getNoiseBiomeAtQuart(x, y, z).value().getSkyColor(), Function.identity());
                assertEquals(fog, world.sampleFog(position)); assertEquals(sky, world.sample(position));
            }
            if (step == 0) {
                @SuppressWarnings("unchecked")
                var biomes = (PalettedContainer<Holder<Biome>>) chunks.get(new ChunkPos(0,0)).getSection(0).getBiomes();
                biomes.set(0, 2, 0, NativeLiveBiomeSectionTest.values.get(17));
            } else if (step == 1) {
                chunks.remove(new ChunkPos(0,0)); world.remove(0,0);
            } else if (step == 2) {
                var replacement = chunk(0,0,factory); chunks.put(replacement.getPos(),replacement); world.replace(replacement);
            }
        }
        var resident = chunks.get(new ChunkPos(0,0));
        resident.setNativeSectionArrayEscapeListener(() -> world.replace(resident));
        resident.getSections();
        assertNull(world.sampleFog(positions[0])); assertNull(world.sample(positions[0]));
    }

    @Test void fogHookAdmitsItsExactWorldAndDeclinesDifferentBiomeSources() {
        var level = mock(ClientLevel.class);
        var chunks = mock(ClientChunkCache.class);
        var manager = new BiomeManager(level, 0);
        when(level.getBiomeManager()).thenReturn(manager); when(level.getChunkSource()).thenReturn(chunks);
        var position = new Vec3(.25, .5, .75); var nativeColor = new Vec3(.1, .2, .3);
        when(chunks.sampleNativeFog(position)).thenReturn(nativeColor);
        var hook = new SodiumFogColorHook();
        assertSame(nativeColor, hook.sampleFogColor(level, manager, position, (x,y,z) -> fail("Unexpected scalar fetch")));
        verify(chunks).sampleNativeFog(position);
        clearInvocations(chunks);
        var customized = new SodiumFogColorHook() {
            @Override public Vec3 sampleFogColor(BiomeManager source, Vec3 pos, net.minecraft.util.CubicSampler.Vec3Fetcher rgb) {
                return Vec3.ZERO;
            }
        };
        assertSame(Vec3.ZERO, customized.sampleFogColor(level,manager,position,(x,y,z) -> fail("Custom hook owns sampling")));
        verifyNoInteractions(chunks);
        var custom = new BiomeManager((x,y,z) -> NativeLiveBiomeSectionTest.values.get(5),0);
        assertFalse(custom.usesNoiseBiomeSource(level));
        Vec3 expected = Vec3.fromRGB24(NativeLiveBiomeSectionTest.values.get(5).value().getFogColor());
        assertEquals(expected, hook.sampleFogColor(level,custom,position,(x,y,z) -> fail("Sodium uses its biome source")));
        verifyNoInteractions(chunks);
        when(chunks.sampleNativeFog(position)).thenReturn(null);
        doReturn(NativeLiveBiomeSectionTest.values.get(9)).when(level).getNoiseBiome(anyInt(),anyInt(),anyInt());
        assertEquals(Vec3.fromRGB24(NativeLiveBiomeSectionTest.values.get(9).value().getFogColor()),
            hook.sampleFogColor(level,manager,position,(x,y,z) -> fail("Sodium uses its biome source")));
    }

    @Test void existingFogHookOverrideKeepsItsOriginalDispatchWithWorldContext() {
        var position = new Vec3(1,2,3); var expected = new Vec3(.2,.3,.4);
        var manager = new BiomeManager((x,y,z) -> NativeLiveBiomeSectionTest.values.get(0),0);
        var fetcher = (net.minecraft.util.CubicSampler.Vec3Fetcher)(x,y,z) -> expected;
        FogColorHooks custom = new FogColorHooks() {
            @Override public Vec3 sampleFogColor(BiomeManager source, Vec3 pos, net.minecraft.util.CubicSampler.Vec3Fetcher rgb) {
                assertSame(manager,source); assertSame(position,pos); assertSame(fetcher,rgb); return expected;
            }
        };
        assertSame(expected, custom.sampleFogColor(mock(ClientLevel.class),manager,position,fetcher));
    }

    @Test void fogEnvironmentObservesHookChangesAtTheSameCameraAndTick() {
        var level = mock(ClientLevel.class, CALLS_REAL_METHODS);
        doReturn(42L).when(level).getGameTime();
        doReturn(0F).when(level).getTimeOfDay(anyFloat());
        doReturn(0F).when(level).getRainLevel(anyFloat());
        doReturn(0F).when(level).getThunderLevel(anyFloat());
        doReturn(0).when(level).getSkyColor(any(), anyFloat());
        var manager = new BiomeManager((x,y,z) -> NativeLiveBiomeSectionTest.values.get(0), 0);
        doReturn(manager).when(level).getBiomeManager();
        var effects = mock(net.minecraft.client.renderer.DimensionSpecialEffects.class);
        when(effects.getBrightnessDependentFogColor(any(), anyFloat())).thenAnswer(call -> call.getArgument(0));
        doReturn(effects).when(level).effects();
        var camera = mock(net.minecraft.client.Camera.class);
        when(camera.getPosition()).thenReturn(new Vec3(12, 64, 12));
        var environment = mock(net.minecraft.client.renderer.fog.environment.AirBasedFogEnvironment.class, CALLS_REAL_METHODS);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        FogColorHooks changingHook = new FogColorHooks() {
            @Override public Vec3 sampleFogColor(BiomeManager source, Vec3 pos, net.minecraft.util.CubicSampler.Vec3Fetcher rgb) {
                return calls.incrementAndGet() == 1 ? new Vec3(1,0,0) : new Vec3(0,0,1);
            }
        };
        try (var hooks = mockStatic(net.minecraft.hooks.HookRegistry.class)) {
            hooks.when(net.minecraft.hooks.HookRegistry::getFogColorHooks).thenReturn(java.util.List.of(changingHook));
            int first = environment.getBaseColor(level, camera, 0, .5F);
            int second = environment.getBaseColor(level, camera, 0, .5F);
            assertNotEquals(first, second, "A live hook change must be visible even at the same camera and tick");
            assertEquals(2, calls.get());
        }
    }

    @Test void skyConsumerObservesHookChangesAtTheSameCameraAndTick() {
        var level = mock(ClientLevel.class, CALLS_REAL_METHODS);
        doReturn(42L).when(level).getGameTime();
        doReturn(0F).when(level).getTimeOfDay(anyFloat());
        doReturn(0F).when(level).getRainLevel(anyFloat());
        doReturn(0F).when(level).getThunderLevel(anyFloat());
        doReturn(0).when(level).getSkyFlashTime();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        net.minecraft.hooks.SkyColorHooks changingHook = new net.minecraft.hooks.SkyColorHooks() {
            @Override public Vec3 sampleSkyColor(net.minecraft.world.level.Level source, Vec3 pos, net.minecraft.util.CubicSampler.Vec3Fetcher rgb) {
                return calls.incrementAndGet() == 1 ? new Vec3(1,0,0) : new Vec3(0,0,1);
            }
        };
        try (var hooks = mockStatic(net.minecraft.hooks.HookRegistry.class)) {
            hooks.when(net.minecraft.hooks.HookRegistry::getSkyColorHooks).thenReturn(java.util.List.of(changingHook));
            var position = new Vec3(12,64,12);
            assertNotEquals(level.getSkyColor(position,.5F), level.getSkyColor(position,.5F));
            assertEquals(2,calls.get());
        }
    }

    @Test void virtualBiomeEffectsRemainLiveAndAreNotCapturedByRegistryAdmission() {
        var registry = new net.minecraft.core.MappedRegistry<Biome>(net.minecraft.core.registries.Registries.BIOME,
            com.mojang.serialization.Lifecycle.stable());
        var effects = new net.minecraft.world.level.biome.MutableBiomeEffectsFixture();
        var custom = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5F).downfall(.5F)
            .specialEffects(effects).mobSpawnSettings(net.minecraft.world.level.biome.MobSpawnSettings.EMPTY)
            .generationSettings(net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY).build();
        for (int i = 0; i < 9; i++) {
            net.minecraft.core.Registry.register(registry, net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("virtual_biome_" + i)),
                i == 8 ? custom : NativeLiveBiomeSectionTest.values.get(i).value());
        }
        registry.freeze(); var strategy = Strategy.createForBiomes(registry.asHolderIdMap());
        NativeLiveBiomeSection.register(strategy, registry);
        assertNull(NativeLiveBiomeSection.binding(strategy)); assertEquals(0, effects.reads());
        var container = new PalettedContainer<>(strategy.globalMap().byId(8), strategy);
        assertNull(container.nativeLiveBiomes());
        var manager = new BiomeManager((x,y,z) -> container.get(0,0,0), 0);
        var hook = new SodiumFogColorHook(); var position = new Vec3(.25,.5,.75);
        Vec3 first = hook.sampleFogColor(manager,position,(x,y,z) -> fail("Sodium uses the provider"));
        Vec3 second = hook.sampleFogColor(manager,position,(x,y,z) -> fail("Sodium uses the provider"));
        assertEquals(432,effects.reads()); assertNotEquals(first,second);
    }
}

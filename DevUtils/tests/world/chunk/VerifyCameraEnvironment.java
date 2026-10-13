package net.minecraft.client;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.*;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.Vec3;

/** Actual Camera, ClientLevel, chunk lookup and palettes on both sides. Unsafe
 * constructs only the disconnected client shell; query code is never replaced. */
public final class VerifyCameraEnvironment {
    static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static <T> T allocate(Class<T> type) throws Exception {
        var unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
    static void set(Class<?> type, Object target, String name, Object value) throws Exception { field(type, name).set(target, value); }
    static void image(String name) throws Exception {
        try (var in = ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new AssertionError(name);
            System.out.println("CLASS " + name + " " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
    }
    static Fluid[] FLUIDS;
    static void tags(int water, int lava) throws Exception {
        var bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class); bind.setAccessible(true);
        for (int i = 0; i < FLUIDS.length; i++) {
            var keys = new ArrayList<>();
            if ((water & (1 << i)) != 0) keys.add(FluidTags.WATER);
            if ((lava & (1 << i)) != 0) keys.add(FluidTags.LAVA);
            bind.invoke(FLUIDS[i].builtInRegistryHolder(), keys);
        }
    }
    static class Fixture {
        final ClientLevel level;
        final ClientChunkCache cache;
        final LevelChunk[][] chunks = new LevelChunk[3][3];
        final Camera[] cameras = new Camera[2048];
        final OptionInstance<Integer> fov;
        final Window window;
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            var minecraft = allocate(Minecraft.class);
            window = allocate(Window.class); window.setWidth(1920); window.setHeight(1080);
            var options = allocate(Options.class); fov = allocate(OptionInstance.class); set(OptionInstance.class, fov, "value", 70);
            set(Options.class, options, "fov", fov); set(Minecraft.class, minecraft, "window", window);
            set(Minecraft.class, minecraft, "options", options); set(Minecraft.class, null, "instance", minecraft);
            level = allocate(ClientLevel.class);
            var dimension = new DimensionType(OptionalLong.empty(), true, false, false, true, 1., true, false,
                -16, 48, 48, null, null, 0, Optional.empty(), null);
            set(Level.class, level, "dimensionTypeRegistration", Holder.direct(dimension));
            cache = allocate(ClientChunkCache.class); set(ClientChunkCache.class, cache, "level", level);
            set(ClientLevel.class, level, "chunkSource", cache);
            set(ClientChunkCache.class, cache, "emptyChunk", allocate(EmptyLevelChunk.class));
            var storageClass = Class.forName("net.minecraft.client.multiplayer.ClientChunkCache$Storage");
            var ctor = storageClass.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            var storage = ctor.newInstance(cache, 3); set(ClientChunkCache.class, cache, "storage", storage);
            var replace = storageClass.getDeclaredMethod("replace", int.class, LevelChunk.class); replace.setAccessible(true);
            var random = new Random(81285103);
            var states = new net.minecraft.world.level.block.state.BlockState[] {
                Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL, 5),
                Blocks.LAVA.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL, 7)
            };
            for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
                var chunk = allocate(LevelChunk.class);
                set(LevelChunk.class, chunk, "level", level); set(ChunkAccess.class, chunk, "levelHeightAccessor", level);
                set(ChunkAccess.class, chunk, "chunkPos", new ChunkPos(x, z));
                var sections = new LevelChunkSection[3];
                for (int row = 0; row < 3; row++) {
                    sections[row] = new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),
                        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
                    for (int i = 0; i < 4096; i++) {
                        var state = states[random.nextInt(states.length)];
                        sections[row].setBlockState(i & 15, i >> 8, i >> 4 & 15, state);
                    }
                }
                set(ChunkAccess.class, chunk, "sections", sections);
                replace.invoke(storage, Math.floorMod(z, 7) * 7 + Math.floorMod(x, 7), chunk);
                chunks[z + 1][x + 1] = chunk;
            }
            double[] fractions = {0., Math.nextDown(1.), 0.01, 0.5, 8.0F / 9.0F, Math.nextDown((double)(8.0F/9.0F)), 0.99};
            for (int i = 0; i < cameras.length; i++) {
                var camera = new Camera(); set(Camera.class, camera, "level", level); set(Camera.class, camera, "initialized", true);
                camera.setPosition(random.nextInt(64) - 24 + fractions[i % fractions.length],
                    random.nextInt(52) - 18 + fractions[(i / 7) % fractions.length],
                    random.nextInt(64) - 24 + fractions[(i / 49) % fractions.length]);
                camera.setRotation(random.nextFloat() * 720 - 360, random.nextFloat() * 180 - 90);
                cameras[i] = camera;
            }
        }
    }
    static class CustomOptions extends Options {
        CustomOptions() { super(null, null); }
        @Override public OptionInstance<Integer> fov() { throw new AssertionError("Projection read before water admission"); }
    }
    static void lazyCameraCases(Fixture fixture) throws Exception {
        tags(6, 24);
        var section = fixture.chunks[1][1].getSection(1);
        section.setBlockState(0, 0, 0, Blocks.WATER.defaultBlockState());
        var camera = new Camera(); set(Camera.class, camera, "level", fixture.level); set(Camera.class, camera, "initialized", true);
        camera.setPosition(0.5, 0.5, 0.5);
        var minecraft = Minecraft.getInstance(); var options = minecraft.options;
        try {
            set(Minecraft.class, null, "instance", null);
            if (camera.getFluidInCamera() != FogType.WATER) throw new AssertionError("Water requires projection");
            set(Minecraft.class, null, "instance", minecraft);
            set(Minecraft.class, minecraft, "options", allocate(CustomOptions.class));
            if (camera.getFluidInCamera() != FogType.WATER) throw new AssertionError("Custom projection called");
            section.setBlockState(0, 0, 0, Blocks.AIR.defaultBlockState());
            section.setBlockState(1, 0, 0, Blocks.WATER.defaultBlockState());
            ((BlockPos.MutableBlockPos)camera.getBlockPosition()).set(1, 0, 0);
            if (camera.getFluidInCamera() != FogType.WATER) throw new AssertionError("Mutable camera block position ignored");
            camera.reset();
            if (camera.getFluidInCamera() != FogType.NONE) throw new AssertionError("Reset camera queried world");
        } finally {
            set(Minecraft.class, null, "instance", minecraft); set(Minecraft.class, minecraft, "options", options);
        }
        System.out.println("LAZY_CAMERA_CASES 4");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        FLUIDS = new Fluid[]{Fluids.EMPTY, Fluids.FLOWING_WATER, Fluids.WATER, Fluids.FLOWING_LAVA, Fluids.LAVA};
        boolean current = args[0].equals("current");
        for (var name : List.of("net.minecraft.client.Camera", "net.minecraft.client.Camera$NearPlane",
            "net.minecraft.client.multiplayer.ClientChunkCache", "net.minecraft.client.multiplayer.ClientChunkCache$Storage",
            "net.minecraft.client.multiplayer.ClientLevel", "net.minecraft.world.level.Level",
            "net.minecraft.world.level.chunk.LevelChunk", "net.minecraft.world.level.chunk.LevelChunkSection",
            "net.minecraft.world.level.chunk.PalettedContainer", "net.minecraft.world.level.material.FlowingFluid")) image(name);
        if (current) for (var name : List.of("net.minecraft.world.level.chunk.NativeClientResidency",
            "net.minecraft.client.Camera$NativeProjection",
            "net.minecraft.world.level.chunk.NativeClientResidency$CameraTags",
            "net.minecraft.world.level.chunk.NativeLiveBlockSection", "net.minecraft.world.level.chunk.NativeChunkInputs")) image(name);
        var fixture = new Fixture(); tags(6, 24);
        if (fixture.window.getWidth() != 1920 || fixture.window.getHeight() != 1080) throw new AssertionError("Projection inputs");
        Method nativeQuery = current ? ClientChunkCache.class.getMethod("nativeCameraEnvironment", Vec3.class,
            org.joml.Vector3f.class, org.joml.Vector3f.class, org.joml.Vector3f.class,
            double.class, double.class, int.class) : null;
        if (args.length > 2 && args[2].equals("benchmark")) {
            var bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            long thread = Thread.currentThread().threadId();
            for (int pass = -5; pass < 8; pass++) {
                long bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime(), checksum = 0;
                for (int i = 0; i < 1_048_576; i++) checksum += fixture.cameras[i & 2047].getFluidInCamera().ordinal();
                long nanos = System.nanoTime() - start; bytes = bean.getThreadAllocatedBytes(thread) - bytes;
                if (pass >= 0) System.out.println("CAMERA_TIMING " + nanos + " " + bytes + " " + checksum);
            }
        } else {
            var path = Path.of(args[1]); int cases = 0;
            try (var out = current ? null : new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)));
                 var in = current ? new DataInputStream(new GZIPInputStream(Files.newInputStream(path))) : null) {
                for (int tagMode = 0; tagMode < 4; tagMode++) {
                    tags(tagMode == 0 ? 6 : tagMode == 1 ? 0 : tagMode == 2 ? 31 : 24,
                        tagMode == 0 ? 24 : tagMode == 1 ? 0 : tagMode == 2 ? 31 : 6);
                    for (int fov : new int[]{30, 70, 110}) for (int height : new int[]{1080, 1920}) {
                        set(OptionInstance.class, fixture.fov, "value", fov); fixture.window.setHeight(height);
                        for (var camera : fixture.cameras) {
                            if (current) {
                                int water = tagMode == 0 ? 6 : tagMode == 1 ? 0 : tagMode == 2 ? 31 : 24;
                                int lava = tagMode == 0 ? 24 : tagMode == 1 ? 0 : tagMode == 2 ? 31 : 6;
                                double halfHeight = Math.tan(fov * (float)(Math.PI / 180.0) / 2.0) * 0.05F;
                                int nativeResult = (int)nativeQuery.invoke(fixture.cache, camera.getPosition(), camera.getLookVector(),
                                    camera.getLeftVector(), camera.getUpVector(), halfHeight, 1920.0 / height, water | (lava << 5));
                                if (nativeResult < 0) throw new AssertionError("Native source declined case " + cases);
                            }
                            int got = camera.getFluidInCamera().ordinal();
                            if (current) { int expected = in.readUnsignedByte(); if (got != expected) throw new AssertionError("case " + cases + ": " + got + " != " + expected); }
                            else out.writeByte(got);
                            cases++;
                        }
                    }
                }
                if (current && in.read() != -1) throw new AssertionError("trailing oracle data");
            }
            System.out.println("CAMERA_CASES " + cases);
            lazyCameraCases(fixture);
        }
        if (current) {
            var path = Path.of(System.getProperty("mattmc.rust.natives.dir"), "mattmc_rust-linux-x64.so").toRealPath();
            if (!Files.readString(Path.of("/proc/self/maps")).contains(path.toString())) throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))) + " " + path);
        }
    }
}

package net.sodium.client.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.sodium.api.util.ModelQuadUtil;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gui.SodiumGameOptions;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeStaticBlockModelCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeshingCorpusReplayTest {
    private static final boolean DIAGNOSTICS_ENABLED =
            Boolean.getBoolean("mattmc.meshingCorpusReplay.diagnostics");
    private static final byte[] MAGIC = new byte[] {'M', 'M', 'C', 'M', 'C', 'O', 'R', 'P'};
    private static final int HEADER_SIZE = 48;
    private static final int SECTION_HEADER_SIZE = 44;
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int COMPACT_HEADER_VERSION_OFFSET = 0;
    private static final int COMPACT_HEADER_ACTIVE_COUNT_OFFSET = 4;
    private static final int COMPACT_HEADER_MIN_X_OFFSET = 8;
    private static final int COMPACT_HEADER_MIN_Y_OFFSET = 12;
    private static final int COMPACT_HEADER_MIN_Z_OFFSET = 16;
    private static final int COMPACT_HEADER_ACTIVE_INDICES_ADDRESS_OFFSET = 24;
    private static final int COMPACT_HEADER_PADDED_STATE_IDS_ADDRESS_OFFSET = 32;
    private static final int COMPACT_HEADER_PADDED_LIGHT_WORDS_ADDRESS_OFFSET = 40;
    private static final int COMPACT_HEADER_BLOCK_IDS_ADDRESS_OFFSET = 48;
    private static final int COMPACT_HEADER_SEED_LOS_ADDRESS_OFFSET = 56;
    private static final int COMPACT_HEADER_SEED_HIS_ADDRESS_OFFSET = 64;
    private static final int COMPACT_HEADER_TINTS_ADDRESS_OFFSET = 72;
    private static final int COMPACT_HEADER_FLUID_TINTS_ADDRESS_OFFSET = 80;
    private static final int COMPACT_HEADER_FLUID_FLOW_X_ADDRESS_OFFSET = 88;
    private static final int COMPACT_HEADER_FLUID_FLOW_Z_ADDRESS_OFFSET = 96;
    private static final int COMPACT_HEADER_FLUID_BLOCK_IDS_ADDRESS_OFFSET = 104;
    private static final int COMPACT_HEADER_FLAGS_ADDRESS_OFFSET = 112;

    @BeforeAll
    static void installDefaultSodiumOptionsForNativeBufferAllocation() throws Exception {
        try {
            SodiumClientMod.options();
            return;
        } catch (IllegalStateException ignored) {
            // Headless tests do not run Sodium's full client initializer.
        }

        Field configField = SodiumClientMod.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        configField.set(null, SodiumGameOptions.defaults());
    }

    @Test
    void replaySchemaV2CorpusThroughCurrentRustCompactMesher() throws Exception {
        String input = System.getProperty("mattmc.meshingCorpusReplay.input", "");
        if (input.isBlank()) {
            return;
        }
        String fixtureFilter = System.getProperty("mattmc.meshingCorpusReplay.fixture", "");
        int warmupIterations = intProperty("mattmc.meshingCorpusReplay.warmup", 8);
        int measurementIterations = Math.max(1, intProperty("mattmc.meshingCorpusReplay.measure", 25));
        long warmupTargetNanos = secondsProperty("mattmc.meshingCorpusReplay.warmupSeconds", 0.0D);
        long measurementTargetNanos = secondsProperty("mattmc.meshingCorpusReplay.measureSeconds", 0.0D);
        JsonArray fixtures = new JsonArray();
        for (JsonObject section : readSections(Path.of(input))) {
            JsonElement rawElement = section.getAsJsonObject("payload").get("raw_input");
            if (rawElement == null || rawElement.isJsonNull() || !rawElement.isJsonObject()) {
                fixtures.add(skipped(section, "Corpus section has no replayable raw_input"));
                continue;
            }
            JsonObject raw = rawElement.getAsJsonObject();
            String fixture = raw.get("fixture").getAsString();
            if (!fixtureFilter.isBlank() && !fixtureFilter.equals(fixture)) {
                continue;
            }
            long prepareStart = System.nanoTime();
            installNativeCache(raw);
            try (CompactSnapshot snapshot = new CompactSnapshot(raw)) {
                long prepareNanos = System.nanoTime() - prepareStart;
                fixtures.add(replay(raw, snapshot, prepareNanos, warmupIterations, measurementIterations,
                        warmupTargetNanos, measurementTargetNanos));
            } finally {
                NativeStaticBlockModelCache.clear();
            }
        }
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No replayable corpus sections matched fixture filter: " + fixtureFilter);
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("schema", "mattmc-current-rust-meshing-corpus-replay-v2");
        result.addProperty("headless_guard", "no Minecraft, ClientLevel, GLFW, OpenGL, Vulkan, or window API is referenced by this test");
        result.addProperty("warmup_iterations", warmupIterations);
        result.addProperty("measurement_iterations", measurementIterations);
        result.addProperty("warmup_target_nanos", warmupTargetNanos);
        result.addProperty("measurement_target_nanos", measurementTargetNanos);
        result.add("fixtures", fixtures);
        String output = System.getProperty("mattmc.meshingCorpusReplay.output", "");
        if (!output.isBlank()) {
            Files.createDirectories(Path.of(output).toAbsolutePath().getParent());
            Files.writeString(Path.of(output), result + "\n");
        }
        assertEquals("ok", result.get("status").getAsString());
    }

    private static JsonObject skipped(JsonObject section, String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unsupported");
        result.addProperty("schema", "mattmc-current-rust-meshing-corpus-fixture-v2");
        result.addProperty("fixture", section.get("name").getAsString());
        result.addProperty("reason", reason);
        result.add("pass_material_counts", new JsonObject());
        result.add("semantic_fingerprint", new JsonObject());
        return result;
    }

    private static JsonObject replay(JsonObject raw, CompactSnapshot snapshot, long prepareNanos,
            int warmupIterations, int measurementIterations, long warmupTargetNanos, long measurementTargetNanos) {
        ReplayInvocation coldCore = replayOnce(raw, snapshot);
        ReplayInvocation coldFull = replayFullOnce(raw);
        TimedSamples warmup = collectSamples(raw, snapshot, warmupIterations, warmupTargetNanos);
        TimedSamples measurement = collectSamples(raw, snapshot, measurementIterations, measurementTargetNanos);
        long[] coreTimes = measurement.coreTimes;
        long[] fullTimes = measurement.fullTimes;
        ReplayInvocation validation = replayOnce(raw, snapshot);
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("schema", "mattmc-current-rust-meshing-corpus-fixture-v2");
        result.addProperty("fixture", raw.get("fixture").getAsString());
        result.addProperty("prepare_nanos", prepareNanos);
        result.addProperty("cold_core_nanos", coldCore.fullNanos);
        result.addProperty("cold_full_nanos", coldFull.fullNanos);
        result.addProperty("core_nanos", median(coreTimes));
        result.addProperty("full_nanos", median(fullTimes));
        result.addProperty("median_ns", median(fullTimes));
        result.addProperty("measurement_iterations", measurementIterations);
        result.addProperty("warmup_iterations", warmupIterations);
        result.addProperty("actual_measurement_iterations", measurement.size());
        result.addProperty("actual_warmup_iterations", warmup.size());
        result.addProperty("measurement_elapsed_nanos", measurement.elapsedNanos);
        result.addProperty("warmup_elapsed_nanos", warmup.elapsedNanos);
        result.addProperty("measurement_target_nanos", measurementTargetNanos);
        result.addProperty("warmup_target_nanos", warmupTargetNanos);
        result.add("warmup_raw_core_times_ns", longArray(warmup.coreTimes));
        result.add("warmup_raw_full_times_ns", longArray(warmup.fullTimes));
        result.add("raw_core_times_ns", longArray(coreTimes));
        result.add("raw_full_times_ns", longArray(fullTimes));
        result.addProperty("native_solid_committed_quads", validation.nativeSolidQuads);
        result.addProperty("native_cutout_committed_quads", validation.nativeCutoutQuads);
        result.addProperty("native_translucent_committed_quads", validation.nativeTranslucentQuads);
        result.addProperty("solid_vertices", validation.solidVertices);
        result.addProperty("cutout_vertices", validation.cutoutVertices);
        result.addProperty("translucent_vertices", validation.translucentVertices);
        result.addProperty("solid_quads", validation.solidQuads);
        result.addProperty("cutout_quads", validation.cutoutQuads);
        result.addProperty("translucent_quads", validation.translucentQuads);
        result.addProperty("raw_vertex_hash", validation.rawVertexHash);
        result.addProperty("raw_index_hash", hashIndexPattern(validation.totalQuads()));
        if (validation.nativeProfile != null) {
            result.add("native_profile", nativeProfile(validation.nativeProfile));
        }
        result.add("semantic_fingerprint", semanticFingerprint(validation));
        result.add("pass_material_counts", passMaterialCounts(validation));
        return result;
    }

    private static TimedSamples collectSamples(JsonObject raw, CompactSnapshot snapshot, int minIterations,
            long targetNanos) {
        List<Long> coreTimes = new ArrayList<>();
        List<Long> fullTimes = new ArrayList<>();
        long start = System.nanoTime();
        while (coreTimes.size() < minIterations || System.nanoTime() - start < targetNanos) {
            coreTimes.add(replayOnce(raw, snapshot).fullNanos);
            fullTimes.add(replayFullOnce(raw).fullNanos);
        }
        return new TimedSamples(toLongArray(coreTimes), toLongArray(fullTimes), System.nanoTime() - start);
    }

    private static ReplayInvocation replayFullOnce(JsonObject raw) {
        long start = System.nanoTime();
        try (CompactSnapshot snapshot = new CompactSnapshot(raw)) {
            return replayOnce(raw, snapshot).withFullNanos(System.nanoTime() - start);
        }
    }

    private static ReplayInvocation replayOnce(JsonObject raw, CompactSnapshot snapshot) {
        long fullStart = System.nanoTime();
        NativeSectionMeshBuilder solid = NativeSectionMeshBuilder.create(4096);
        NativeSectionMeshBuilder cutout = NativeSectionMeshBuilder.create(4096);
        NativeSectionMeshBuilder translucent = NativeSectionMeshBuilder.create(4096);
        BuiltSectionMeshParts solidMesh = null;
        BuiltSectionMeshParts cutoutMesh = null;
        BuiltSectionMeshParts translucentMesh = null;
        try {
            int sectionIndex = raw.get("section_y").getAsInt();
            solid.start(sectionIndex);
            cutout.start(sectionIndex);
            translucent.start(sectionIndex);
            long coreStart = System.nanoTime();
            int[] counts = NativeSectionMeshBuilder.appendCompactNativeSectionAllPassesEncoded(solid, cutout,
                    translucent, snapshot.address, ChunkMeshFormats.COMPACT.getNativeFormat(), sectionIndex,
                    false, 0L);
            long coreNanos = System.nanoTime() - coreStart;
            int solidVertices = solid.totalVertexCount();
            int cutoutVertices = cutout.totalVertexCount();
            int translucentVertices = translucent.totalVertexCount();
            solidMesh = solid.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, true, false);
            cutoutMesh = cutout.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, true, false);
            translucentMesh = translucent.finishMesh(ChunkMeshFormats.COMPACT.getNativeFormat(), 0, false, true, false);
            long fullNanos = System.nanoTime() - fullStart;
            long[] nativeProfile = DIAGNOSTICS_ENABLED ? sumProfiles(solid.copyProfile(),
                    cutout.copyProfile(), translucent.copyProfile()) : null;
            return new ReplayInvocation(coreNanos, fullNanos, counts[0], counts[1], counts[2],
                    solidVertices, cutoutVertices, translucentVertices, quadCount(solidMesh), quadCount(cutoutMesh),
                    quadCount(translucentMesh), hashMeshes(solidMesh, cutoutMesh, translucentMesh),
                    hashMeshesCanonical(solidMesh, cutoutMesh, translucentMesh),
                    semanticFieldHashes(solidMesh, cutoutMesh, translucentMesh), nativeProfile);
        } finally {
            free(solidMesh);
            free(cutoutMesh);
            free(translucentMesh);
            solid.close();
            cutout.close();
            translucent.close();
        }
    }

    private static void installNativeCache(JsonObject raw) {
        NativeStaticBlockModelCache.clear();
        Set<Integer> registeredStates = new HashSet<>();
        for (var element : raw.getAsJsonArray("model_bundle")) {
            JsonObject model = element.getAsJsonObject();
            JsonArray quads = model.getAsJsonArray("quads");
            int modelId = model.get("model_id").getAsInt();
            NativeStaticBlockModelCache.register(modelId, (address, index) ->
                    writeCapturedQuad(address, quads.get(index).getAsJsonObject()), quads.size());
            int selectorId = model.get("selector_id").getAsInt();
            NativeStaticBlockModelCache.registerSelector(selectorId, 0, (address, index) ->
                    NativeChunkMeshEncoder.writeNativeModelSelectorEntry(address, modelId, 1), 1);
            int stateId = model.get("native_state_id").getAsInt();
            registeredStates.add(stateId);
            int offsetType = optionalInt(model, "offset_type", 0);
            float maxHorizontalOffset = optionalFloat(model, "max_horizontal_offset", 0.25F);
            float maxVerticalOffset = optionalFloat(model, "max_vertical_offset", 0.2F);
            NativeStaticBlockModelCache.registerState(stateId, selectorId, model.get("state_flags").getAsInt(),
                    model.get("material_bits").getAsInt(), model.get("pass_id").getAsInt(),
                    model.get("block_emission").getAsInt(), 0, model.get("block_id").getAsInt(),
                    0, -1, -1, stateId + 1, 0, 0.0F, 0, offsetType, maxHorizontalOffset, maxVerticalOffset,
                    model.get("tint_type").getAsInt());
        }
        for (var element : raw.getAsJsonArray("padded_compact_grid")) {
            JsonObject entry = element.getAsJsonObject();
            int stateId = entry.get("native_state_id").getAsInt();
            if (registeredStates.add(stateId)) {
                NativeStaticBlockModelCache.registerState(stateId, -1, 1, 0, -1, 0, 0, -1, 0, -1, -1, 0);
            }
        }
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private static float optionalFloat(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static void writeCapturedQuad(long address, JsonObject quad) {
        JsonArray words = quad.getAsJsonArray("vertices");
        NativeChunkMeshEncoder.writeStaticModelQuadRecord(address,
                quad.get("material_bits").getAsInt(),
                quad.get("pass_id").getAsInt(),
                quad.get("cull_face").getAsInt(),
                quad.get("normal_face").getAsInt(),
                quad.get("packed_normal").getAsInt(),
                (byte) quad.get("block_emission").getAsInt(),
                (byte) quad.get("render_type").getAsInt(),
                quad.get("shade").getAsBoolean(),
                quad.get("flags").getAsInt(),
                quad.get("light_face").getAsInt(),
                quad.get("tint_index").getAsInt(),
                quad.get("has_ao").getAsBoolean(),
                x(words, 0), y(words, 0), z(words, 0), color(words, 0), u(words, 0), v(words, 0), light(words, 0),
                x(words, 1), y(words, 1), z(words, 1), color(words, 1), u(words, 1), v(words, 1), light(words, 1),
                x(words, 2), y(words, 2), z(words, 2), color(words, 2), u(words, 2), v(words, 2), light(words, 2),
                x(words, 3), y(words, 3), z(words, 3), color(words, 3), u(words, 3), v(words, 3), light(words, 3));
    }

    private static float x(JsonArray words, int vertex) {
        return Float.intBitsToFloat(word(words, vertex, ModelQuadUtil.POSITION_INDEX));
    }

    private static float y(JsonArray words, int vertex) {
        return Float.intBitsToFloat(word(words, vertex, ModelQuadUtil.POSITION_INDEX + 1));
    }

    private static float z(JsonArray words, int vertex) {
        return Float.intBitsToFloat(word(words, vertex, ModelQuadUtil.POSITION_INDEX + 2));
    }

    private static int color(JsonArray words, int vertex) {
        return word(words, vertex, ModelQuadUtil.COLOR_INDEX);
    }

    private static float u(JsonArray words, int vertex) {
        return Float.intBitsToFloat(word(words, vertex, ModelQuadUtil.TEXTURE_INDEX));
    }

    private static float v(JsonArray words, int vertex) {
        return Float.intBitsToFloat(word(words, vertex, ModelQuadUtil.TEXTURE_INDEX + 1));
    }

    private static int light(JsonArray words, int vertex) {
        return word(words, vertex, ModelQuadUtil.LIGHT_INDEX);
    }

    private static int word(JsonArray words, int vertex, int offset) {
        return words.get(ModelQuadUtil.vertexOffset(vertex) + offset).getAsInt();
    }

    private static String hashMeshes(BuiltSectionMeshParts... meshes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (BuiltSectionMeshParts mesh : meshes) {
                if (mesh == null) {
                    updateInt(digest, 0);
                    continue;
                }
                ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate();
                updateInt(digest, buffer.remaining());
                while (buffer.hasRemaining()) {
                    digest.update(buffer.get());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hashMeshesCanonical(BuiltSectionMeshParts... meshes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int quadStride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride() * 4;
            for (BuiltSectionMeshParts mesh : meshes) {
                if (mesh == null) {
                    updateInt(digest, 0);
                    continue;
                }

                ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                if (bytes.length % quadStride != 0) {
                    throw new IllegalStateException("Compact mesh byte length is not quad aligned: " + bytes.length);
                }

                int quadCount = bytes.length / quadStride;
                updateInt(digest, quadCount);
                List<byte[]> quads = new ArrayList<>(quadCount);
                for (int quad = 0; quad < quadCount; quad++) {
                    quads.add(Arrays.copyOfRange(bytes, quad * quadStride, (quad + 1) * quadStride));
                }
                quads.sort(MeshingCorpusReplayTest::compareUnsigned);
                for (byte[] quad : quads) {
                    digest.update(quad);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int compare = Byte.compareUnsigned(left[i], right[i]);
            if (compare != 0) {
                return compare;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static JsonObject semanticFieldHashes(BuiltSectionMeshParts... meshes) {
        JsonObject hashes = new JsonObject();
        hashes.addProperty("position", hashMeshField(0, 8, meshes));
        hashes.addProperty("color", hashMeshField(8, 4, meshes));
        hashes.addProperty("texture", hashMeshField(12, 4, meshes));
        hashes.addProperty("light_material", hashMeshField(16, 4, meshes));
        return hashes;
    }

    private static String hashMeshField(int vertexOffset, int bytesPerVertex, BuiltSectionMeshParts... meshes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int vertexStride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride();
            int quadStride = vertexStride * 4;
            for (BuiltSectionMeshParts mesh : meshes) {
                if (mesh == null) {
                    updateInt(digest, 0);
                    continue;
                }

                ByteBuffer buffer = mesh.getVertexData().getDirectBuffer().duplicate();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                if (bytes.length % quadStride != 0) {
                    throw new IllegalStateException("Compact mesh byte length is not quad aligned: " + bytes.length);
                }

                int quadCount = bytes.length / quadStride;
                updateInt(digest, quadCount);
                List<byte[]> records = new ArrayList<>(quadCount);
                for (int quad = 0; quad < quadCount; quad++) {
                    byte[] record = new byte[bytesPerVertex * 4];
                    int writeOffset = 0;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        System.arraycopy(bytes, quad * quadStride + vertex * vertexStride + vertexOffset,
                                record, writeOffset, bytesPerVertex);
                        writeOffset += bytesPerVertex;
                    }
                    records.add(record);
                }
                records.sort(MeshingCorpusReplayTest::compareUnsigned);
                for (byte[] record : records) {
                    digest.update(record);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int quadCount(BuiltSectionMeshParts mesh) {
        return mesh == null ? 0 : mesh.getVertexData().getLength() / ChunkMeshFormats.COMPACT.getVertexFormat().getStride() / 4;
    }

    private static JsonObject semanticFingerprint(ReplayInvocation invocation) {
        JsonObject fingerprint = new JsonObject();
        String rawVertexHash = invocation.rawVertexHash;
        String canonicalVertexHash = invocation.canonicalVertexHash;
        String rawIndexHash = hashIndexPattern(invocation.totalQuads());
        fingerprint.addProperty("capture_kind", "headless-current-rust-compact-snapshot-replay");
        fingerprint.addProperty("raw_vertex_hash", rawVertexHash);
        fingerprint.addProperty("raw_index_hash", rawIndexHash);
        fingerprint.addProperty("ordered_semantic_hash", rawVertexHash);
        fingerprint.addProperty("canonical_semantic_hash", canonicalVertexHash);
        fingerprint.addProperty("normalized_semantic_hash", canonicalVertexHash);
        fingerprint.addProperty("translucent_metadata_hash", rawIndexHash);
        fingerprint.add("field_hashes", invocation.fieldHashes.deepCopy());
        return fingerprint;
    }

    private static JsonObject passMaterialCounts(ReplayInvocation invocation) {
        JsonObject counts = new JsonObject();
        counts.addProperty("solid_quads", invocation.solidQuads);
        counts.addProperty("cutout_quads", invocation.cutoutQuads);
        counts.addProperty("translucent_quads", invocation.translucentQuads);
        counts.addProperty("total_quads", invocation.totalQuads());
        return counts;
    }

    private static long[] sumProfiles(long[]... profiles) {
        long[] values = new long[NativeSectionMeshBuilder.Profile.METRIC_COUNT];
        for (long[] profile : profiles) {
            for (int i = 0; i < Math.min(values.length, profile.length); i++) {
                values[i] += profile[i];
            }
        }
        return values;
    }

    private static JsonObject nativeProfile(long[] values) {
        JsonObject result = new JsonObject();
        JsonObject stages = new JsonObject();
        for (int i = 0; i < NativeSectionMeshBuilder.Profile.STAGE_COUNT; i++) {
            stages.addProperty(NativeSectionMeshBuilder.Profile.STAGE_NAMES[i], values[i]);
        }
        JsonObject counts = new JsonObject();
        for (int i = 0; i < NativeSectionMeshBuilder.Profile.COUNT_COUNT; i++) {
            counts.addProperty(NativeSectionMeshBuilder.Profile.COUNT_NAMES[i],
                    values[NativeSectionMeshBuilder.Profile.STAGE_COUNT + i]);
        }
        result.add("stages_nanos", stages);
        result.add("counts", counts);
        return result;
    }

    private static JsonArray longArray(long[] values) {
        JsonArray array = new JsonArray();
        for (long value : values) {
            array.add(value);
        }
        return array;
    }

    private static long[] toLongArray(List<Long> values) {
        long[] array = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static long median(long[] values) {
        if (values.length == 0) {
            return 0L;
        }
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name, "");
        if (value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static long secondsProperty(String name, double defaultValue) {
        String value = System.getProperty(name, "");
        double seconds = value.isBlank() ? defaultValue : Double.parseDouble(value);
        if (seconds <= 0.0D) {
            return 0L;
        }
        return (long) (seconds * 1_000_000_000.0D);
    }

    private static String hashIndexPattern(int quads) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] label = "shared-quad-index-pattern-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update(label);
            updateInt(digest, quads);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) value);
        digest.update((byte) (value >>> 8));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 24));
    }

    private static void free(BuiltSectionMeshParts mesh) {
        if (mesh != null) {
            mesh.getVertexData().free();
        }
    }

    private static List<JsonObject> readSections(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IllegalArgumentException("Invalid corpus magic: " + path);
            }
        }
        int version = buffer.getInt();
        if (version != 2) {
            throw new IllegalArgumentException("Expected schema v2 corpus, got " + version);
        }
        int sectionCount = buffer.getInt();
        if (sectionCount < 1) {
            throw new IllegalArgumentException("Corpus contains no sections");
        }
        buffer.position(HEADER_SIZE);
        int metadataLength = buffer.getInt();
        buffer.position(buffer.position() + metadataLength);
        List<JsonObject> sections = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            int nameLength = buffer.getInt();
            int categoryLength = buffer.getInt();
            int classificationLength = buffer.getInt();
            buffer.getLong();
            buffer.getLong();
            buffer.getLong();
            long payloadLength = buffer.getLong();
            buffer.position(buffer.position() + 32 + nameLength + categoryLength + classificationLength);
            byte[] compressed = new byte[Math.toIntExact(payloadLength)];
            buffer.get(compressed);
            sections.add(JsonParser.parseString(inflate(compressed)).getAsJsonObject());
        }
        return sections;
    }

    private static String inflate(byte[] compressed) throws IOException {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class CompactSnapshot implements AutoCloseable {
        private final ByteBuffer buffer;
        private final long address;
        private final long activeIndicesAddress;
        private final long paddedStateIdsAddress;
        private final long paddedLightWordsAddress;
        private final long blockIdsAddress;
        private final long seedLosAddress;
        private final long seedHisAddress;
        private final long tintsAddress;
        private final long fluidTintsAddress;
        private final long fluidFlowXAddress;
        private final long fluidFlowZAddress;
        private final long fluidBlockIdsAddress;
        private final long flagsAddress;

        private CompactSnapshot(JsonObject raw) {
            long offset = NativeChunkMeshEncoder.COMPACT_SECTION_SNAPSHOT_HEADER_STRIDE;
            long activeIndicesOffset = offset;
            offset += (long) SECTION_BLOCKS * NativeChunkMeshEncoder.COMPACT_SECTION_ACTIVE_INDEX_STRIDE;
            offset = align(offset, Integer.BYTES);
            long paddedStateIdsOffset = offset;
            offset += (long) NativeChunkMeshEncoder.COMPACT_SECTION_PADDED_BLOCK_COUNT * Integer.BYTES;
            long paddedLightWordsOffset = offset;
            offset += (long) NativeChunkMeshEncoder.COMPACT_SECTION_PADDED_BLOCK_COUNT * Integer.BYTES;
            long blockIdsOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long seedLosOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long seedHisOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long tintsOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long fluidTintsOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long fluidFlowXOffset = offset;
            offset += (long) SECTION_BLOCKS * Float.BYTES;
            long fluidFlowZOffset = offset;
            offset += (long) SECTION_BLOCKS * Float.BYTES;
            long fluidBlockIdsOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            long flagsOffset = offset;
            offset += (long) SECTION_BLOCKS * Integer.BYTES;
            int totalBytes = (int) align(offset, Long.BYTES);
            this.buffer = MemoryUtil.memCalloc(totalBytes).order(ByteOrder.nativeOrder());
            this.address = MemoryUtil.memAddress(this.buffer);
            this.activeIndicesAddress = this.address + activeIndicesOffset;
            this.paddedStateIdsAddress = this.address + paddedStateIdsOffset;
            this.paddedLightWordsAddress = this.address + paddedLightWordsOffset;
            this.blockIdsAddress = this.address + blockIdsOffset;
            this.seedLosAddress = this.address + seedLosOffset;
            this.seedHisAddress = this.address + seedHisOffset;
            this.tintsAddress = this.address + tintsOffset;
            this.fluidTintsAddress = this.address + fluidTintsOffset;
            this.fluidFlowXAddress = this.address + fluidFlowXOffset;
            this.fluidFlowZAddress = this.address + fluidFlowZOffset;
            this.fluidBlockIdsAddress = this.address + fluidBlockIdsOffset;
            this.flagsAddress = this.address + flagsOffset;
            this.writeHeader(raw);
            this.populate(raw);
        }

        private void writeHeader(JsonObject raw) {
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_VERSION_OFFSET,
                    NativeChunkMeshEncoder.COMPACT_SECTION_SNAPSHOT_VERSION);
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_ACTIVE_COUNT_OFFSET, 0);
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_MIN_X_OFFSET, raw.get("origin_x").getAsInt());
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_MIN_Y_OFFSET, raw.get("origin_y").getAsInt());
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_MIN_Z_OFFSET, raw.get("origin_z").getAsInt());
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_ACTIVE_INDICES_ADDRESS_OFFSET, this.activeIndicesAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_PADDED_STATE_IDS_ADDRESS_OFFSET, this.paddedStateIdsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_PADDED_LIGHT_WORDS_ADDRESS_OFFSET, this.paddedLightWordsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_BLOCK_IDS_ADDRESS_OFFSET, this.blockIdsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_SEED_LOS_ADDRESS_OFFSET, this.seedLosAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_SEED_HIS_ADDRESS_OFFSET, this.seedHisAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_TINTS_ADDRESS_OFFSET, this.tintsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_FLUID_TINTS_ADDRESS_OFFSET, this.fluidTintsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_FLUID_FLOW_X_ADDRESS_OFFSET, this.fluidFlowXAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_FLUID_FLOW_Z_ADDRESS_OFFSET, this.fluidFlowZAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_FLUID_BLOCK_IDS_ADDRESS_OFFSET, this.fluidBlockIdsAddress);
            MemoryUtil.memPutLong(this.address + COMPACT_HEADER_FLAGS_ADDRESS_OFFSET, this.flagsAddress);
        }

        private void populate(JsonObject raw) {
            for (var element : raw.getAsJsonArray("padded_compact_grid")) {
                JsonObject entry = element.getAsJsonObject();
                int index = (entry.get("y").getAsInt() * NativeChunkMeshEncoder.COMPACT_SECTION_PADDED_LENGTH
                        + entry.get("z").getAsInt()) * NativeChunkMeshEncoder.COMPACT_SECTION_PADDED_LENGTH
                        + entry.get("x").getAsInt();
                MemoryUtil.memPutInt(this.paddedStateIdsAddress + (long) index * Integer.BYTES,
                        entry.get("native_state_id").getAsInt());
                MemoryUtil.memPutInt(this.paddedLightWordsAddress + (long) index * Integer.BYTES,
                        entry.get("light_word").getAsInt());
            }
            int activeIndex = 0;
            for (var element : raw.getAsJsonArray("active_blocks")) {
                JsonObject entry = element.getAsJsonObject();
                int localIndex = entry.get("local_index").getAsInt();
                MemoryUtil.memPutShort(this.activeIndicesAddress
                        + (long) activeIndex * NativeChunkMeshEncoder.COMPACT_SECTION_ACTIVE_INDEX_STRIDE,
                        (short) localIndex);
                MemoryUtil.memPutInt(this.blockIdsAddress + (long) localIndex * Integer.BYTES,
                        entry.get("block_id").getAsInt());
                MemoryUtil.memPutInt(this.seedLosAddress + (long) localIndex * Integer.BYTES,
                        entry.get("seed_lo").getAsInt());
                MemoryUtil.memPutInt(this.seedHisAddress + (long) localIndex * Integer.BYTES,
                        entry.get("seed_hi").getAsInt());
                MemoryUtil.memPutInt(this.tintsAddress + (long) localIndex * Integer.BYTES,
                        entry.get("tint").getAsInt());
                MemoryUtil.memPutInt(this.fluidTintsAddress + (long) localIndex * Integer.BYTES,
                        entry.get("fluid_tint").getAsInt());
                MemoryUtil.memPutFloat(this.fluidFlowXAddress + (long) localIndex * Float.BYTES,
                        entry.get("fluid_flow_x").getAsFloat());
                MemoryUtil.memPutFloat(this.fluidFlowZAddress + (long) localIndex * Float.BYTES,
                        entry.get("fluid_flow_z").getAsFloat());
                MemoryUtil.memPutInt(this.fluidBlockIdsAddress + (long) localIndex * Integer.BYTES,
                        entry.get("fluid_block_id").getAsInt());
                MemoryUtil.memPutInt(this.flagsAddress + (long) localIndex * Integer.BYTES,
                        entry.get("flags").getAsInt());
                activeIndex++;
            }
            MemoryUtil.memPutInt(this.address + COMPACT_HEADER_ACTIVE_COUNT_OFFSET, activeIndex);
        }

        private static long align(long offset, int alignment) {
            long mask = alignment - 1L;
            return (offset + mask) & ~mask;
        }

        @Override
        public void close() {
            MemoryUtil.memFree(this.buffer);
        }
    }

    private record ReplayInvocation(long coreNanos, long fullNanos, int nativeSolidQuads, int nativeCutoutQuads,
                                    int nativeTranslucentQuads, int solidVertices, int cutoutVertices,
                                    int translucentVertices, int solidQuads, int cutoutQuads,
                                    int translucentQuads, String rawVertexHash, String canonicalVertexHash,
                                    JsonObject fieldHashes, long[] nativeProfile) {
        private int totalQuads() {
            return this.solidQuads + this.cutoutQuads + this.translucentQuads;
        }

        private ReplayInvocation withFullNanos(long replacementFullNanos) {
            return new ReplayInvocation(this.coreNanos, replacementFullNanos, this.nativeSolidQuads,
                    this.nativeCutoutQuads, this.nativeTranslucentQuads, this.solidVertices, this.cutoutVertices,
                    this.translucentVertices, this.solidQuads, this.cutoutQuads, this.translucentQuads,
                    this.rawVertexHash, this.canonicalVertexHash, this.fieldHashes, this.nativeProfile);
        }
    }

    private record TimedSamples(long[] coreTimes, long[] fullTimes, long elapsedNanos) {
        private int size() {
            return this.fullTimes.length;
        }
    }
}

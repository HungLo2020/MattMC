package net.vulkanic;

import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend-neutral owner for legacy OpenGL-style Vulkanic compatibility state.
 *
 * <p>This class owns semantic state only: object names, bindings, uniform
 * payloads, vertex declarations, framebuffer attachment names, and
 * fixed-function choices. It deliberately does not own native OpenGL/Vulkan
 * handles, allocations, image layouts, descriptor pools, command buffers, or
 * synchronization. Backends mirror this state into native implementation
 * objects as needed, but immutable GAL requests are captured from this shared
 * state before backend execution.</p>
 */
public final class VulkanicCompatibilityState {
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_COPY_READ_BUFFER = 0x8F36;
    private static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    private static final int GL_TEXTURE_1D = 0x0DE0;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_3D = 0x806F;
    private static final int GL_PACK_ROW_LENGTH = 0x0D02;
    private static final int GL_PACK_ALIGNMENT = 0x0D05;
    private static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    private static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    private static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_READ_ONLY = 0x88B8;
    private static final int GL_WRITE_ONLY = 0x88B9;
    private static final int GL_BACK = 0x0405;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_TEXTURE0 = 0x84C0;

    private final Object lock = new Object();
    private final Map<Integer, ProgramState> programs = new ConcurrentHashMap<>();
    private final Map<Integer, VaoState> vaos = new ConcurrentHashMap<>();
    private final Map<Integer, FramebufferState> framebuffers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> bufferBindings = new HashMap<>();
    private final Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings = new HashMap<>();
    private final Map<TextureBindingKey, Integer> textureBindings = new HashMap<>();
    private final Map<Integer, Integer> textureUnitBindings = new HashMap<>();
    private final Map<Integer, Integer> samplerBindings = new HashMap<>();
    private final Map<Integer, ImageUnitBindingState> imageUnitBindings = new HashMap<>();
    private final Map<Integer, Long> textureGenerations = new HashMap<>();
    private final Map<Integer, Long> bufferGenerations = new HashMap<>();
    private final FixedFunctionState fixedFunction = new FixedFunctionState();
    private final PixelStoreState pixelStore = new PixelStoreState();
    private long programVersion;
    private long vertexInputVersion;
    private long framebufferVersion;
    private long resourceBindingVersion;
    private long fixedFunctionVersion;
    private ProgramStateView v2ProgramSnapshot = new ProgramState(0);
    private VaoSnapshot v2VaoSnapshot = new VaoState(0).copy();
    private FramebufferSnapshot v2FramebufferSnapshot = new FramebufferState(0).copy();
    private Map<Integer, Integer> v2BufferBindings = Map.of();
    private Map<IndexedBufferKey, BufferRangeState> v2IndexedBufferBindings = Map.of();
    private Map<Integer, Integer> v2Texture2DByUnit = Map.of();
    private Map<Integer, Integer> v2TextureUnitBindings = Map.of();
    private Map<TextureBindingKey, Integer> v2TextureBindingsByKey = Map.of();
    private Map<Integer, Integer> v2SamplerBindings = Map.of();
    private Map<Integer, ImageUnitBindingState> v2ImageUnitBindings = Map.of();
    private Map<Integer, Long> v2TextureGenerations = Map.of();
    private Map<Integer, Long> v2BufferGenerations = Map.of();
    private List<VulkanicPassResourceModel.BindingSnapshot> v2BindingSnapshots = List.of();
    private FixedFunctionSnapshot v2FixedFunctionSnapshot = fixedFunction.copy();
    private volatile V2GraphicsState v2GraphicsState = rebuildV2GraphicsStateUnlocked();
    private final Object v2CommandEncoderLock = new Object();
    private VulkanicGalV2.GraphicsEncoderState v2CommandEncoderState;
    private GraphicsSnapshot cachedGraphicsSnapshot;
    private String cachedGraphicsSnapshotLabel;
    private int cachedGraphicsSnapshotProgram;
    private int cachedGraphicsSnapshotVao;
    private int cachedGraphicsSnapshotDrawFramebuffer;
    private long cachedGraphicsSnapshotProgramVersion = Long.MIN_VALUE;
    private long cachedGraphicsSnapshotVertexInputVersion = Long.MIN_VALUE;
    private long cachedGraphicsSnapshotFramebufferVersion = Long.MIN_VALUE;
    private long cachedGraphicsSnapshotResourceBindingVersion = Long.MIN_VALUE;
    private long cachedGraphicsSnapshotFixedFunctionVersion = Long.MIN_VALUE;
    private int currentProgram;
    private int currentVao;
    private int boundReadFramebuffer;
    private int boundDrawFramebuffer;
    private int activeTextureUnitIndex;

    public VulkanicCompatibilityState() {
        synchronized (lock) {
            refreshV2ProgramUnlocked();
            refreshV2VertexInputUnlocked();
            refreshV2FramebufferUnlocked();
            refreshV2FixedFunctionUnlocked();
            refreshV2ResourcesUnlocked();
            rebuildV2GraphicsStateUnlocked();
        }
    }

    private void registerTexture(int texture) {
        if (texture > 0) {
            textureGenerations.putIfAbsent(texture, 0L);
        }
    }

    private void registerBuffer(int buffer) {
        if (buffer > 0) {
            bufferGenerations.putIfAbsent(buffer, 0L);
        }
    }

    private void incrementTextureGeneration(int texture) {
        if (texture > 0) {
            textureGenerations.merge(texture, 1L, Long::sum);
        }
    }

    private void incrementBufferGeneration(int buffer) {
        if (buffer > 0) {
            bufferGenerations.merge(buffer, 1L, Long::sum);
        }
    }

    private long textureGenerationUnlocked(int texture) {
        return texture > 0 ? textureGenerations.getOrDefault(texture, 0L) : VulkanicPassResourceModel.UNKNOWN_GENERATION;
    }

    private long bufferGenerationUnlocked(int buffer) {
        return buffer > 0 ? bufferGenerations.getOrDefault(buffer, 0L) : VulkanicPassResourceModel.UNKNOWN_GENERATION;
    }

    private void advanceProgramVersionUnlocked() {
        programVersion++;
        refreshV2ProgramUnlocked();
        refreshV2ResourceGenerationsUnlocked();
        rebuildV2GraphicsStateUnlocked();
    }

    private void advanceUniformContentVersionUnlocked(boolean shapeChanged) {
        programVersion++;
        if (shapeChanged) {
            refreshV2ProgramUnlocked();
            refreshV2ResourceGenerationsUnlocked();
            rebuildV2GraphicsStateUnlocked();
        }
    }

    private void advanceVertexInputVersionUnlocked() {
        vertexInputVersion++;
        refreshV2VertexInputUnlocked();
        refreshV2VertexResourceGenerationsUnlocked();
        rebuildV2GraphicsStateUnlocked();
    }

    private void advanceFramebufferVersionUnlocked() {
        framebufferVersion++;
        refreshV2FramebufferUnlocked();
        refreshV2ResourceGenerationsUnlocked();
        rebuildV2GraphicsStateUnlocked();
    }

    private void advanceResourceBindingVersionUnlocked() {
        resourceBindingVersion++;
        refreshV2ResourcesUnlocked();
        rebuildV2GraphicsStateUnlocked();
    }

    private void advanceFixedFunctionVersionUnlocked() {
        fixedFunctionVersion++;
        refreshV2FixedFunctionUnlocked();
        rebuildV2GraphicsStateUnlocked();
    }

    private void refreshV2ProgramUnlocked() {
        v2ProgramSnapshot = program(currentProgram);
    }

    private void refreshV2VertexInputUnlocked() {
        v2VaoSnapshot = vao(currentVao).copy();
    }

    private void refreshV2FramebufferUnlocked() {
        v2FramebufferSnapshot = framebuffer(boundDrawFramebuffer).copy();
    }

    private void refreshV2FixedFunctionUnlocked() {
        v2FixedFunctionSnapshot = fixedFunction.copy();
    }

    private void refreshV2ResourcesUnlocked() {
        Map<Integer, Integer> texture2DByUnit = new LinkedHashMap<>();
        for (Map.Entry<TextureBindingKey, Integer> entry : textureBindings.entrySet()) {
            if (entry.getKey().target() == GL_TEXTURE_2D) {
                texture2DByUnit.put(entry.getKey().unit(), entry.getValue());
            }
        }
        v2BufferBindings = Map.copyOf(bufferBindings);
        v2IndexedBufferBindings = Map.copyOf(indexedBufferBindings);
        v2Texture2DByUnit = Map.copyOf(texture2DByUnit);
        v2TextureUnitBindings = Map.copyOf(textureUnitBindings);
        v2TextureBindingsByKey = Map.copyOf(textureBindings);
        v2SamplerBindings = Map.copyOf(samplerBindings);
        v2ImageUnitBindings = Map.copyOf(imageUnitBindings);
        v2TextureGenerations = graphicsTextureGenerations(texture2DByUnit, v2FramebufferSnapshot);
        v2BufferGenerations = graphicsBufferGenerations(v2VaoSnapshot);
        v2BindingSnapshots = bindingSnapshotsFor(v2GraphicsStateViewUnlocked(List.of()));
    }

    private void refreshV2ResourceGenerationsUnlocked() {
        v2TextureGenerations = graphicsTextureGenerations(v2Texture2DByUnit, v2FramebufferSnapshot);
        v2BufferGenerations = graphicsBufferGenerations(v2VaoSnapshot);
        v2BindingSnapshots = bindingSnapshotsFor(v2GraphicsStateViewUnlocked(List.of()));
    }

    private void refreshV2VertexResourceGenerationsUnlocked() {
        v2BufferGenerations = graphicsBufferGenerations(v2VaoSnapshot);
    }

    private V2GraphicsState rebuildV2GraphicsStateUnlocked() {
        v2GraphicsState = v2GraphicsStateViewUnlocked(v2BindingSnapshots);
        return v2GraphicsState;
    }

    private V2GraphicsState v2GraphicsStateViewUnlocked(
        List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshots
    ) {
        return new V2GraphicsState(
            currentProgram,
            v2ProgramSnapshot,
            currentVao,
            v2VaoSnapshot,
            boundDrawFramebuffer,
            v2FramebufferSnapshot,
            v2BufferBindings,
            v2IndexedBufferBindings,
            v2Texture2DByUnit,
            v2TextureUnitBindings,
            v2TextureBindingsByKey,
            v2SamplerBindings,
            v2ImageUnitBindings,
            v2TextureGenerations,
            v2BufferGenerations,
            bindingSnapshots,
            v2FixedFunctionSnapshot,
            "gal-v2-mutation-state",
            programVersion,
            vertexInputVersion,
            framebufferVersion,
            resourceBindingVersion,
            fixedFunctionVersion
        );
    }

    public void bindProgram(int programId) {
        synchronized (lock) {
            currentProgram = programId;
            if (programId > 0) {
                programs.computeIfAbsent(programId, ProgramState::new);
            }
            advanceProgramVersionUnlocked();
        }
    }

    public void deleteProgram(int programId) {
        synchronized (lock) {
            programs.remove(programId);
            if (currentProgram == programId) {
                currentProgram = 0;
            }
            advanceProgramVersionUnlocked();
        }
    }

    public void setUniformInt(int location, int... values) {
        synchronized (lock) {
            updateUniformUnlocked(location, UniformValue.ints(values));
        }
    }

    public void setUniformFloat(int location, float... values) {
        synchronized (lock) {
            updateUniformUnlocked(location, UniformValue.floats(values));
        }
    }

    public void setUniformMatrix(int location, int columns, int rows, boolean transpose, float[] values) {
        synchronized (lock) {
            updateUniformUnlocked(location, UniformValue.matrix(columns, rows, transpose, values));
        }
    }

    public void setUniformMatrix(int location, int columns, int rows, boolean transpose, FloatBuffer values) {
        FloatBuffer duplicate = values.duplicate();
        float[] copy = new float[duplicate.remaining()];
        duplicate.get(copy);
        setUniformMatrix(location, columns, rows, transpose, copy);
    }

    public void setActiveTextureUnit(int legacyUnitConstant) {
        setActiveTextureUnitIndex(Math.max(0, legacyUnitConstant - GL_TEXTURE0));
    }

    public void setActiveTextureUnitIndex(int unitIndex) {
        synchronized (lock) {
            activeTextureUnitIndex = Math.max(0, unitIndex);
        }
    }

    public void bindTexture(int target, int texture) {
        synchronized (lock) {
            registerTexture(texture);
            textureUnitBindings.remove(activeTextureUnitIndex);
            textureBindings.put(new TextureBindingKey(activeTextureUnitIndex, target), texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindTexture(int unitIndex, int target, int texture) {
        synchronized (lock) {
            int unit = Math.max(0, unitIndex);
            registerTexture(texture);
            textureUnitBindings.remove(unit);
            textureBindings.put(new TextureBindingKey(unit, target), texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindTexture2D(int texture) {
        bindTexture(GL_TEXTURE_2D, texture);
    }

    public void bindTextureUnit(int unit, int texture) {
        synchronized (lock) {
            int unitIndex = Math.max(0, unit);
            registerTexture(texture);
            textureBindings.keySet().removeIf(key -> key.unit() == unitIndex);
            textureUnitBindings.put(unitIndex, texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindSampler(int unit, int sampler) {
        synchronized (lock) {
            samplerBindings.put(Math.max(0, unit), sampler);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        synchronized (lock) {
            registerTexture(texture);
            imageUnitBindings.put(
                Math.max(0, unit),
                new ImageUnitBindingState(Math.max(0, unit), texture, level, layered, layer, access, format)
            );
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindSamplers(int first, int[] samplers) {
        Objects.requireNonNull(samplers, "samplers");
        synchronized (lock) {
            for (int i = 0; i < samplers.length; i++) {
                samplerBindings.put(Math.max(0, first + i), samplers[i]);
            }
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void deleteTexture(int texture) {
        synchronized (lock) {
            textureBindings.values().removeIf(value -> value == texture);
            imageUnitBindings.values().removeIf(value -> value.texture() == texture);
            incrementTextureGeneration(texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void bindBuffer(int target, int buffer) {
        synchronized (lock) {
            registerBuffer(buffer);
            bufferBindings.put(target, buffer);
            if (target == GL_ELEMENT_ARRAY_BUFFER) {
                vao(currentVao).elementBuffer = buffer;
                advanceVertexInputVersionUnlocked();
            }
        }
    }

    public void bindBuffer(VulkanicBufferTarget target, int buffer) {
        bindBuffer(target.toLegacyGlTarget(), buffer);
    }

    public void bindBufferBase(int target, int index, int buffer) {
        bindBufferRange(target, index, buffer, 0L, Long.MAX_VALUE);
    }

    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        bindBufferRange(target, index, buffer, offset, size, null);
    }

    public void bindNamedBufferRange(int target, int index, int buffer, long offset, long size, String semanticName) {
        bindBufferRange(target, index, buffer, offset, size, semanticName);
    }

    private void bindBufferRange(int target, int index, int buffer, long offset, long size, String semanticName) {
        synchronized (lock) {
            registerBuffer(buffer);
            indexedBufferBindings.put(
                new IndexedBufferKey(target, index),
                new BufferRangeState(buffer, offset, size, semanticName)
            );
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void deleteBuffer(int buffer) {
        synchronized (lock) {
            bufferBindings.values().removeIf(value -> value == buffer);
            indexedBufferBindings.values().removeIf(value -> value.buffer() == buffer);
            for (VaoState vao : vaos.values()) {
                if (vao.elementBuffer == buffer) {
                    vao.elementBuffer = 0;
                    advanceVertexInputVersionUnlocked();
                }
                if (vao.vertexBindings.values().removeIf(binding -> binding.buffer() == buffer)) {
                    vao.markLayoutDirty();
                    advanceVertexInputVersionUnlocked();
                }
                if (vao.attributes.values().removeIf(attribute -> attribute.capturedBuffer() == buffer)) {
                    vao.markLayoutDirty();
                    advanceVertexInputVersionUnlocked();
                }
            }
            incrementBufferGeneration(buffer);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void markBufferStorageReplaced(int buffer) {
        synchronized (lock) {
            incrementBufferGeneration(buffer);
            advanceResourceBindingVersionUnlocked();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void markTextureStorageReplaced(int texture) {
        synchronized (lock) {
            incrementTextureGeneration(texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void markBoundTextureStorageReplaced(int target) {
        synchronized (lock) {
            Integer texture = textureBindings.get(new TextureBindingKey(activeTextureUnitIndex, target));
            if (texture == null) {
                texture = textureUnitBindings.get(activeTextureUnitIndex);
            }
            incrementTextureGeneration(texture == null ? 0 : texture);
            advanceResourceBindingVersionUnlocked();
        }
    }

    public void markBoundBufferStorageReplaced(int target) {
        synchronized (lock) {
            incrementBufferGeneration(bufferBindings.getOrDefault(target, 0));
            advanceResourceBindingVersionUnlocked();
            advanceVertexInputVersionUnlocked();
        }
    }

    public long textureGeneration(int texture) {
        synchronized (lock) {
            return textureGenerationUnlocked(texture);
        }
    }

    public long bufferGeneration(int buffer) {
        synchronized (lock) {
            return bufferGenerationUnlocked(buffer);
        }
    }

    public VulkanicGalV2.UniformPayload captureExplicitGalV2UniformPayload(int programId, String semanticKey) {
        synchronized (lock) {
            ProgramState program = program(programId);
            return VulkanicGalV2.uniformPayloadForExplicitProgram(
                programId,
                program.uniformPayloadVersion,
                program.uniformsByLocation,
                semanticKey
            );
        }
    }

    public void setPixelStore(int pname, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Pixel-store value must be >= 0, got: " + value);
        }
        synchronized (lock) {
            switch (pname) {
                case GL_PACK_ROW_LENGTH -> pixelStore.packRowLength = value;
                case GL_PACK_ALIGNMENT -> {
                    requirePixelStoreAlignment(value, "GL_PACK_ALIGNMENT");
                    pixelStore.packAlignment = value;
                }
                case GL_UNPACK_ROW_LENGTH -> pixelStore.unpackRowLength = value;
                case GL_UNPACK_SKIP_ROWS -> pixelStore.unpackSkipRows = value;
                case GL_UNPACK_SKIP_PIXELS -> pixelStore.unpackSkipPixels = value;
                case GL_UNPACK_ALIGNMENT -> {
                    requirePixelStoreAlignment(value, "GL_UNPACK_ALIGNMENT");
                    pixelStore.unpackAlignment = value;
                }
                default -> {
                }
            }
        }
    }

    private static void requirePixelStoreAlignment(int value, String name) {
        if (value != 1 && value != 2 && value != 4 && value != 8) {
            throw new IllegalArgumentException(name + " must be one of {1,2,4,8}, got: " + value);
        }
    }

    public void bindVertexArray(int vao) {
        synchronized (lock) {
            currentVao = Math.max(0, vao);
            if (vao > 0) {
                vaos.computeIfAbsent(vao, VaoState::new);
            }
            advanceVertexInputVersionUnlocked();
        }
    }

    public void deleteVertexArray(int vao) {
        synchronized (lock) {
            vaos.remove(vao);
            if (currentVao == vao) {
                currentVao = 0;
            }
            advanceVertexInputVersionUnlocked();
        }
    }

    public void enableVertexAttribArray(int index) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            if (vao.enabledAttributes.contains(index)) {
                return;
            }
            vao.enabledAttributes.add(index);
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void disableVertexAttribArray(int index) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            if (!vao.enabledAttributes.remove(Integer.valueOf(index))) {
                return;
            }
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void setVertexAttribPointer(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            int divisor = previous == null ? 0 : previous.divisor();
            int binding = index;
            int capturedBuffer = bufferBindings.getOrDefault(GL_ARRAY_BUFFER, 0);
            VertexAttributeState nextAttribute = new VertexAttributeState(
                index,
                binding,
                size,
                type,
                normalized,
                integer,
                0,
                divisor,
                capturedBuffer
            );
            VertexBindingState previousBinding = vao.vertexBindings.get(binding);
            VertexBindingState nextBinding = new VertexBindingState(binding, capturedBuffer, Math.max(0L, pointer), stride, divisor);
            if (nextAttribute.equals(previous) && nextBinding.equals(previousBinding)) {
                return;
            }
            if (!sameVertexAttributeLayout(previous, nextAttribute) || !sameVertexBindingLayout(previousBinding, nextBinding)) {
                vao.markLayoutDirty();
            }
            vao.attributes.put(index, nextAttribute);
            vao.vertexBindings.put(binding, nextBinding);
            advanceVertexInputVersionUnlocked();
        }
    }

    public void setVertexAttribFormat(int index, int size, int type, boolean normalized, boolean integer, int relativeOffset) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            int binding = previous == null ? index : previous.binding();
            int divisor = previous == null ? 0 : previous.divisor();
            int capturedBuffer = previous == null ? 0 : previous.capturedBuffer();
            VertexAttributeState nextAttribute = new VertexAttributeState(
                index,
                binding,
                size,
                type,
                normalized,
                integer,
                relativeOffset,
                divisor,
                capturedBuffer
            );
            if (nextAttribute.equals(previous)) {
                return;
            }
            vao.attributes.put(index, nextAttribute);
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void setVertexAttribBinding(int index, int binding) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            if (previous == null) {
                vao.attributes.put(index, new VertexAttributeState(index, binding, 4, 0x1406, false, false, 0, 0, 0));
                vao.markLayoutDirty();
                advanceVertexInputVersionUnlocked();
                return;
            }
            VertexAttributeState nextAttribute = previous.withBinding(binding);
            if (nextAttribute.equals(previous)) {
                return;
            }
            vao.attributes.put(index, nextAttribute);
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void bindVertexBuffer(int binding, int buffer, long offset, int stride) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexBindingState previous = vao.vertexBindings.get(binding);
            int divisor = previous == null ? 0 : previous.divisor();
            VertexBindingState nextBinding = new VertexBindingState(binding, buffer, offset, stride, divisor);
            if (nextBinding.equals(previous)) {
                return;
            }
            if (!sameVertexBindingLayout(previous, nextBinding)) {
                vao.markLayoutDirty();
            }
            vao.vertexBindings.put(binding, nextBinding);
            advanceVertexInputVersionUnlocked();
        }
    }

    public void setVertexAttribDivisor(int index, int divisor) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            if (previous == null) {
                vao.attributes.put(index, new VertexAttributeState(index, index, 4, 0x1406, false, false, 0, divisor, 0));
                vao.markLayoutDirty();
                advanceVertexInputVersionUnlocked();
                return;
            }
            VertexAttributeState nextAttribute = previous.withDivisor(divisor);
            if (nextAttribute.equals(previous)) {
                return;
            }
            vao.attributes.put(index, nextAttribute);
            VertexBindingState binding = vao.vertexBindings.get(previous.binding());
            if (binding != null) {
                vao.vertexBindings.put(previous.binding(), binding.withDivisor(divisor));
            }
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void setVertexAttribDefault(int index, float v0, float v1, float v2, float v3) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            float[] previous = vao.defaultAttributes.get(index);
            if (previous != null
                && previous.length == 4
                && Float.compare(previous[0], v0) == 0
                && Float.compare(previous[1], v1) == 0
                && Float.compare(previous[2], v2) == 0
                && Float.compare(previous[3], v3) == 0) {
                return;
            }
            vao.defaultAttributes.put(index, new float[] {v0, v1, v2, v3});
            vao.markLayoutDirty();
            advanceVertexInputVersionUnlocked();
        }
    }

    public void bindFramebuffer(int target, int framebuffer) {
        synchronized (lock) {
            switch (target) {
                case GL_READ_FRAMEBUFFER -> boundReadFramebuffer = framebuffer;
                case GL_DRAW_FRAMEBUFFER -> boundDrawFramebuffer = framebuffer;
                case GL_FRAMEBUFFER -> {
                    boundReadFramebuffer = framebuffer;
                    boundDrawFramebuffer = framebuffer;
                }
                default -> {
                    boundReadFramebuffer = framebuffer;
                    boundDrawFramebuffer = framebuffer;
                }
            }
            if (framebuffer > 0) {
                framebuffers.computeIfAbsent(framebuffer, FramebufferState::new);
            }
            advanceFramebufferVersionUnlocked();
        }
    }

    public int boundDrawFramebuffer() {
        synchronized (lock) {
            return boundDrawFramebuffer;
        }
    }

    public void deleteFramebuffer(int framebuffer) {
        synchronized (lock) {
            framebuffers.remove(framebuffer);
            if (boundReadFramebuffer == framebuffer) {
                boundReadFramebuffer = 0;
            }
            if (boundDrawFramebuffer == framebuffer) {
                boundDrawFramebuffer = 0;
            }
            advanceFramebufferVersionUnlocked();
        }
    }

    public void framebufferTexture(int target, int attachment, int texture, int level) {
        synchronized (lock) {
            int framebuffer = target == GL_READ_FRAMEBUFFER ? boundReadFramebuffer : boundDrawFramebuffer;
            namedFramebufferTexture(framebuffer, attachment, texture, level);
        }
    }

    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        synchronized (lock) {
            FramebufferState state = framebuffers.computeIfAbsent(framebuffer, FramebufferState::new);
            state.attachments.put(attachment, new AttachmentState(attachment, texture, level));
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setDrawBuffer(int mode) {
        synchronized (lock) {
            framebuffer(boundDrawFramebuffer).drawBuffers = List.of(mode);
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setNamedDrawBuffers(int framebuffer, int[] buffers) {
        synchronized (lock) {
            framebuffer(framebuffer).drawBuffers = Arrays.stream(buffers).boxed().toList();
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setNamedReadBuffer(int framebuffer, int mode) {
        synchronized (lock) {
            framebuffer(framebuffer).readBuffer = mode;
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setReadBuffer(int mode) {
        synchronized (lock) {
            framebuffer(boundReadFramebuffer).readBuffer = mode;
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setDrawBuffers(int[] buffers) {
        synchronized (lock) {
            framebuffer(boundDrawFramebuffer).drawBuffers = Arrays.stream(buffers).boxed().toList();
            advanceFramebufferVersionUnlocked();
        }
    }

    public void setViewport(int x, int y, int width, int height) {
        synchronized (lock) {
            fixedFunction.viewport = Optional.of(new VulkanicGalExecutionRequest.Viewport(x, y, width, height, 0.0F, 1.0F));
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setScissor(int x, int y, int width, int height) {
        synchronized (lock) {
            fixedFunction.scissor = Optional.of(new VulkanicGalExecutionRequest.Scissor(x, y, width, height));
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setScissorTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.scissorTestEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setStencilTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.stencilTestEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setBlendEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.blendEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setBlendFunction(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        synchronized (lock) {
            fixedFunction.blendSrcRgb = srcRgb;
            fixedFunction.blendDstRgb = dstRgb;
            fixedFunction.blendSrcAlpha = srcAlpha;
            fixedFunction.blendDstAlpha = dstAlpha;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setBlendEquation(int rgb, int alpha) {
        synchronized (lock) {
            fixedFunction.blendEquationRgb = rgb;
            fixedFunction.blendEquationAlpha = alpha;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setDepthTest(boolean enabled, int func) {
        synchronized (lock) {
            fixedFunction.depthTestEnabled = enabled;
            fixedFunction.depthFunc = func;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setDepthTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.depthTestEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setDepthFunc(int func) {
        synchronized (lock) {
            fixedFunction.depthFunc = func;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setDepthWriteMask(boolean enabled) {
        synchronized (lock) {
            fixedFunction.depthWriteMask = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setCull(boolean enabled, int mode) {
        synchronized (lock) {
            fixedFunction.cullEnabled = enabled;
            fixedFunction.cullFaceMode = mode;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setCullEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.cullEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setCullFaceMode(int mode) {
        synchronized (lock) {
            fixedFunction.cullFaceMode = mode;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
        synchronized (lock) {
            fixedFunction.colorMaskR = r;
            fixedFunction.colorMaskG = g;
            fixedFunction.colorMaskB = b;
            fixedFunction.colorMaskA = a;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setStencilFunc(int face, int func, int ref, int mask) {
        synchronized (lock) {
            fixedFunction.stencilFuncs.put(face, new StencilFuncState(face, func, ref, mask));
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setStencilOp(int face, int sfail, int dpfail, int dppass) {
        synchronized (lock) {
            fixedFunction.stencilOps.put(face, new StencilOpState(face, sfail, dpfail, dppass));
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setStencilWriteMask(int face, int mask) {
        synchronized (lock) {
            fixedFunction.stencilWriteMasks.put(face, mask);
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setLogicOpEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.logicOpEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setLogicOp(int opcode) {
        synchronized (lock) {
            fixedFunction.logicOp = opcode;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setPolygonMode(int face, int mode) {
        synchronized (lock) {
            fixedFunction.polygonFace = face;
            fixedFunction.polygonMode = mode;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setPolygonOffsetEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.polygonOffsetEnabled = enabled;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public void setPolygonOffset(float factor, float units) {
        synchronized (lock) {
            fixedFunction.polygonOffsetFactor = factor;
            fixedFunction.polygonOffsetUnits = units;
            advanceFixedFunctionVersionUnlocked();
        }
    }

    public GraphicsSnapshot captureGraphics(VulkanicGalExecutionRequest.GraphicsDrawRequest request) {
        synchronized (lock) {
            String label = request.semanticIdentity().label();
            if (cachedGraphicsSnapshot != null
                && currentProgram == cachedGraphicsSnapshotProgram
                && currentVao == cachedGraphicsSnapshotVao
                && boundDrawFramebuffer == cachedGraphicsSnapshotDrawFramebuffer
                && vertexInputVersion == cachedGraphicsSnapshotVertexInputVersion
                && framebufferVersion == cachedGraphicsSnapshotFramebufferVersion
                && resourceBindingVersion == cachedGraphicsSnapshotResourceBindingVersion
                && fixedFunctionVersion == cachedGraphicsSnapshotFixedFunctionVersion) {
                if (programVersion != cachedGraphicsSnapshotProgramVersion) {
                    cachedGraphicsSnapshot = cachedGraphicsSnapshot.withProgram(
                        program(currentProgram).copy(),
                        label,
                        programVersion
                    );
                    cachedGraphicsSnapshotLabel = label;
                    cachedGraphicsSnapshotProgramVersion = programVersion;
                    return cachedGraphicsSnapshot;
                }
                if (!Objects.equals(label, cachedGraphicsSnapshotLabel)) {
                    cachedGraphicsSnapshot = cachedGraphicsSnapshot.withSemanticIdentity(label);
                    cachedGraphicsSnapshotLabel = label;
                }
                return cachedGraphicsSnapshot;
            }
            VaoSnapshot vao = vao(currentVao).copy();
            FramebufferSnapshot framebuffer = framebuffer(boundDrawFramebuffer).copy();
            Map<Integer, Integer> texture2DByUnit = new LinkedHashMap<>();
            for (Map.Entry<TextureBindingKey, Integer> entry : textureBindings.entrySet()) {
                if (entry.getKey().target() == GL_TEXTURE_2D) {
                    texture2DByUnit.put(entry.getKey().unit(), entry.getValue());
                }
            }
            Map<Integer, Long> capturedTextureGenerations = graphicsTextureGenerations(texture2DByUnit, framebuffer);
            Map<Integer, Long> capturedBufferGenerations = graphicsBufferGenerations(vao);
            GraphicsSnapshot snapshot = new GraphicsSnapshot(
                currentProgram,
                program(currentProgram).copy(),
                currentVao,
                vao,
                boundDrawFramebuffer,
                framebuffer,
                Map.copyOf(bufferBindings),
                Map.copyOf(indexedBufferBindings),
                Map.copyOf(texture2DByUnit),
                Map.copyOf(textureUnitBindings),
                Map.copyOf(textureBindings),
                Map.copyOf(samplerBindings),
                Map.copyOf(imageUnitBindings),
                capturedTextureGenerations,
                capturedBufferGenerations,
                fixedFunction.copy(),
                request.semanticIdentity().label(),
                programVersion,
                vertexInputVersion,
                framebufferVersion,
                resourceBindingVersion,
                fixedFunctionVersion
            );
            cachedGraphicsSnapshot = snapshot;
            cachedGraphicsSnapshotLabel = label;
            cachedGraphicsSnapshotProgram = currentProgram;
            cachedGraphicsSnapshotVao = currentVao;
            cachedGraphicsSnapshotDrawFramebuffer = boundDrawFramebuffer;
            cachedGraphicsSnapshotProgramVersion = programVersion;
            cachedGraphicsSnapshotVertexInputVersion = vertexInputVersion;
            cachedGraphicsSnapshotFramebufferVersion = framebufferVersion;
            cachedGraphicsSnapshotResourceBindingVersion = resourceBindingVersion;
            cachedGraphicsSnapshotFixedFunctionVersion = fixedFunctionVersion;
            return snapshot;
        }
    }

    private Map<Integer, Long> graphicsTextureGenerations(
        Map<Integer, Integer> texture2DByUnit,
        FramebufferSnapshot framebuffer
    ) {
        Map<Integer, Long> generations = new LinkedHashMap<>();
        texture2DByUnit.values().forEach(texture -> putTextureGeneration(generations, texture));
        textureUnitBindings.values().forEach(texture -> putTextureGeneration(generations, texture));
        textureBindings.values().forEach(texture -> putTextureGeneration(generations, texture));
        imageUnitBindings.values().forEach(image -> putTextureGeneration(generations, image.texture()));
        framebuffer.attachments().values().forEach(attachment -> putTextureGeneration(generations, attachment.texture()));
        return Map.copyOf(generations);
    }

    private Map<Integer, Long> graphicsBufferGenerations(VaoSnapshot vao) {
        Map<Integer, Long> generations = new LinkedHashMap<>();
        bufferBindings.values().forEach(buffer -> putBufferGeneration(generations, buffer));
        indexedBufferBindings.values().forEach(range -> putBufferGeneration(generations, range.buffer()));
        putBufferGeneration(generations, vao.elementBuffer());
        vao.vertexBindings().values().forEach(binding -> putBufferGeneration(generations, binding.buffer()));
        return Map.copyOf(generations);
    }

    private void putTextureGeneration(Map<Integer, Long> generations, int texture) {
        if (texture > 0) {
            generations.put(texture, textureGenerationUnlocked(texture));
        }
    }

    private void putBufferGeneration(Map<Integer, Long> generations, int buffer) {
        if (buffer > 0) {
            generations.put(buffer, bufferGenerationUnlocked(buffer));
        }
    }

    public Optional<VulkanicGalV2.ExplicitGraphicsDrawRequest> tryCaptureGalV2GraphicsDraw(
        VulkanicGalExecutionRequest.GraphicsDrawRequest request,
        boolean eagerResourceDeclarations
    ) {
        Optional<VulkanicGalV2.ExplicitGraphicsDrawRequest> captured =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(v2GraphicsState, request, eagerResourceDeclarations);
        if (captured.isEmpty()) {
            resetGalV2CommandEncoder();
            return Optional.empty();
        }
        synchronized (v2CommandEncoderLock) {
            VulkanicGalV2.GraphicsCommandStreamResult encoded =
                VulkanicGalV2.encodeGraphicsCommandStream(captured.orElseThrow(), v2CommandEncoderState);
            v2CommandEncoderState = encoded.nextState();
            return Optional.of(captured.orElseThrow().withCommandStream(encoded.stream()));
        }
    }

    public void resetGalV2CommandEncoder() {
        synchronized (v2CommandEncoderLock) {
            v2CommandEncoderState = null;
        }
    }

    public ComputeSnapshot captureCompute(VulkanicGalExecutionRequest.ComputeDispatchRequest request) {
        synchronized (lock) {
            Map<Integer, Integer> texture2DByUnit = new LinkedHashMap<>();
            for (Map.Entry<TextureBindingKey, Integer> entry : textureBindings.entrySet()) {
                if (entry.getKey().target() == GL_TEXTURE_2D) {
                    texture2DByUnit.put(entry.getKey().unit(), entry.getValue());
                }
            }
            return new ComputeSnapshot(
                currentProgram,
                program(currentProgram).copy(),
                Map.copyOf(bufferBindings),
                Map.copyOf(indexedBufferBindings),
                Map.copyOf(texture2DByUnit),
                Map.copyOf(textureUnitBindings),
                Map.copyOf(textureBindings),
                Map.copyOf(samplerBindings),
                Map.copyOf(imageUnitBindings),
                Map.copyOf(textureGenerations),
                Map.copyOf(bufferGenerations),
                request.semanticIdentity().label()
            );
        }
    }

    public VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot compatibilitySnapshotFor(
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        return compatibilitySnapshotFor(request, true);
    }

    public VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot compatibilitySnapshotFor(
        VulkanicGalExecutionRequest.GraphicsDrawRequest request,
        boolean eagerResourceDeclarations
    ) {
        boolean audit = VulkanPerfAudit.isEnabled();
        long start = audit ? System.nanoTime() : 0L;
        GraphicsSnapshot snapshot = captureGraphics(request);
        recordCapturePhase(audit, "gal.graphics.capture.state", start);

        start = audit ? System.nanoTime() : 0L;
        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInput = snapshot.vertexInputSnapshot(request);
        recordCapturePhase(audit, "gal.graphics.capture.vertex", start);

        start = audit ? System.nanoTime() : 0L;
        List<VulkanicPassResourceModel.BindingSnapshot> bindings = eagerResourceDeclarations
            ? snapshot.bindingSnapshots()
            : List.of();
        recordCapturePhase(audit, "gal.graphics.capture.bindings", start);

        start = audit ? System.nanoTime() : 0L;
        List<VulkanicPassResourceModel.ResourceUse> resourceUses;
        if (eagerResourceDeclarations) {
            VulkanicPassResourceModel.PassExecutionPlan vertexPlan =
                VulkanicLegacyCompatibilityAdapter.planDraw(new VulkanicLegacyCompatibilityAdapter.DrawSnapshot(
                    request.semanticIdentity().label(),
                    vertexInput.vertexBuffers(),
                    vertexInput.indexBuffer(),
                    List.of(),
                    List.of(),
                    drawCommandSnapshot(request.command()),
                    false,
                    false
                ));
            resourceUses = new ArrayList<>(vertexPlan.orderedUses());
        } else {
            resourceUses = List.of();
        }
        recordCapturePhase(audit, "gal.graphics.capture.vertex_resources", start);

        start = audit ? System.nanoTime() : 0L;
        if (eagerResourceDeclarations) {
            for (VulkanicPassResourceModel.BindingSnapshot binding : bindings) {
                resourceUses.add(binding.resourceUse());
            }
        }
        recordCapturePhase(audit, "gal.graphics.capture.resources", start);
        return new VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot(
            Optional.empty(),
            vertexInput,
            resourceUses,
            Optional.empty(),
            bindings,
            Optional.of(snapshot),
            "frontend-shared-compatibility-draw"
        );
    }

    private static void recordCapturePhase(boolean audit, String name, long startNanos) {
        if (audit) {
            VulkanPerfAudit.recordPhase(name, System.nanoTime() - startNanos);
        }
    }

    public void validateResourceGenerations(VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateResourceGenerations(snapshot.descriptorBindings());
    }

    public VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot compatibilitySnapshotFor(
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        ComputeSnapshot snapshot = captureCompute(request);
        return new VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot(
            Optional.empty(),
            request.resourcePlan().orderedUses(),
            Optional.empty(),
            snapshot.bindingSnapshots(),
            Optional.of(snapshot),
            "frontend-shared-compatibility-compute"
        );
    }

    public void validateResourceGenerations(VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateResourceGenerations(snapshot.descriptorBindings());
    }

    public VulkanicGalExecutionRequest.TransferCompatibilitySnapshot compatibilitySnapshotFor(
        VulkanicGalExecutionRequest.TransferRequest request
    ) {
        Objects.requireNonNull(request, "request");
        synchronized (lock) {
            ArrayList<VulkanicPassResourceModel.CanonicalResourceReference> sources = new ArrayList<>();
            ArrayList<VulkanicPassResourceModel.CanonicalResourceReference> destinations = new ArrayList<>();
            String label = request.semanticIdentity().label();
            switch (request.operation()) {
                case VulkanicGalExecutionRequest.CopyBufferSubData op -> {
                    sources.add(boundBufferRef("copy-buffer-source", op.readTarget(), op.readOffset(), op.size(),
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC, label));
                    destinations.add(boundBufferRef("copy-buffer-destination", op.writeTarget(), op.writeOffset(), op.size(),
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.CopyNamedBufferSubData op -> {
                    sources.add(bufferRef("named-copy-buffer-source", op.readBuffer(), op.readOffset(), op.size(),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC, OptionalInt.empty()));
                    destinations.add(bufferRef("named-copy-buffer-destination", op.writeBuffer(), op.writeOffset(), op.size(),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, OptionalInt.empty()));
                }
                case VulkanicGalExecutionRequest.CopyImageSubData op -> {
                    sources.add(textureRef("copy-image-source", op.srcName(), OptionalInt.of(op.srcTarget()), op.srcLevel(), op.srcZ(), op.depth(),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(textureRef("copy-image-destination", op.dstName(), OptionalInt.of(op.dstTarget()), op.dstLevel(), op.dstZ(), op.depth(),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.CopyTextureSubImage2D op -> {
                    sources.add(framebufferRef("copy-texture-sub-image-source", boundReadFramebuffer,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(textureRef("copy-texture-sub-image-destination", op.texture(), OptionalInt.empty(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.CopyTexImage2D op -> {
                    sources.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-source",
                        boundReadFramebuffer, VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(boundTextureRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        op.target(), op.level(), 0, 1, VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.CopyTexSubImage2D op -> {
                    sources.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-source",
                        boundReadFramebuffer, VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(boundTextureRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        op.target(), op.level(), 0, 1, VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.BlitFramebuffer op -> {
                    sources.add(framebufferRef("blit-framebuffer-source", boundReadFramebuffer,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(framebufferRef("blit-framebuffer-destination", boundDrawFramebuffer,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.BlitNamedFramebuffer op -> {
                    sources.add(framebufferRef("blit-named-framebuffer-source", op.readFramebuffer(),
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    destinations.add(framebufferRef("blit-named-framebuffer-destination", op.drawFramebuffer(),
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ReadPixelsPointer op -> {
                    sources.add(framebufferRef("read-pixels-source", boundReadFramebuffer,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    int packBuffer = bufferBindings.getOrDefault(GL_PIXEL_PACK_BUFFER, 0);
                    if (packBuffer > 0) {
                        destinations.add(bufferRef("read-pixels-pack-buffer", packBuffer, op.pixels(), 1L,
                            VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                            VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST,
                            OptionalInt.of(GL_PIXEL_PACK_BUFFER)));
                    }
                }
                case VulkanicGalExecutionRequest.ReadPixelsFloatArray op -> {
                    sources.add(framebufferRef("read-pixels-source", boundReadFramebuffer,
                        VulkanicPassResourceModel.Access.READ, VulkanicResourceUsage.TRANSFER_SRC));
                    int packBuffer = bufferBindings.getOrDefault(GL_PIXEL_PACK_BUFFER, 0);
                    if (packBuffer > 0) {
                        destinations.add(bufferRef("read-pixels-pack-buffer", packBuffer, 0L, 1L,
                            VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                            VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST,
                            OptionalInt.of(GL_PIXEL_PACK_BUFFER)));
                    }
                }
                case VulkanicGalExecutionRequest.BufferSubData op -> {
                    destinations.add(boundBufferRef("buffer-sub-data-destination", op.target(), op.offset(), payloadSize(op.payload()),
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.NamedBufferSubData op -> {
                    destinations.add(bufferRef("named-buffer-sub-data-destination", op.buffer(), op.offset(), payloadSize(op.payload()),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, OptionalInt.empty()));
                }
                case VulkanicGalExecutionRequest.UploadTexture1D op -> {
                    addPixelUnpackSourceIfBound(sources, 0L);
                    destinations.add(boundTextureRef("upload-texture-1d-destination", op.target(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.UploadTexture2D op -> {
                    addPixelUnpackSourceIfBound(sources, 0L);
                    destinations.add(boundTextureRef("upload-texture-2d-destination", op.target(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.UploadTexture2DSubImagePointer op -> {
                    addPixelUnpackSourceIfBound(sources, op.pixels());
                    destinations.add(boundTextureRef("upload-texture-2d-sub-image-destination", op.target(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.UploadTexture2DSubImageBuffer op -> {
                    addPixelUnpackSourceIfBound(sources, 0L);
                    destinations.add(boundTextureRef("upload-texture-2d-sub-image-destination", op.target(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.UploadTexture3D op -> {
                    addPixelUnpackSourceIfBound(sources, 0L);
                    destinations.add(boundTextureRef("upload-texture-3d-destination", op.target(), op.level(), 0, Math.max(1, op.depth()),
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.ClearTexImageInt op -> {
                    destinations.add(textureRef("clear-texture-image-destination", op.texture(), OptionalInt.empty(), op.level(), 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearBufferSubDataInt op -> {
                    destinations.add(boundBufferRef("clear-buffer-sub-data-destination", op.target(), op.offset(), op.size(),
                        VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.ClearBufferFloat op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        boundDrawFramebuffer, VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearBufferInt op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        boundDrawFramebuffer, VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearBufferUint op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        boundDrawFramebuffer, VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearNamedFramebufferFloat op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        op.framebuffer(), VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearNamedFramebufferInt op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        op.framebuffer(), VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.ClearNamedFramebufferUint op -> {
                    destinations.add(framebufferRef(request.kind().name().toLowerCase(java.util.Locale.ROOT) + "-destination",
                        op.framebuffer(), VulkanicPassResourceModel.Access.WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
                case VulkanicGalExecutionRequest.GenerateMipmap op -> {
                    destinations.add(boundTextureRef("generate-mipmap-destination", op.target(), 0, 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.READ_WRITE, VulkanicResourceUsage.TRANSFER_DST, label));
                }
                case VulkanicGalExecutionRequest.GenerateTextureMipmap op -> {
                    destinations.add(textureRef("generate-texture-mipmap-destination", op.texture(), OptionalInt.empty(), 0, 0, 1,
                        VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                        VulkanicPassResourceModel.Access.READ_WRITE, VulkanicResourceUsage.TRANSFER_DST));
                }
            }
            return new VulkanicGalExecutionRequest.TransferCompatibilitySnapshot(
                sources,
                destinations,
                pixelStore.copy(),
                "frontend-shared-compatibility-transfer"
            );
        }
    }

    public void validateResourceGenerations(VulkanicGalExecutionRequest.TransferCompatibilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            for (VulkanicPassResourceModel.CanonicalResourceReference reference : snapshot.allResources()) {
                validateResourceGeneration(reference);
            }
        }
    }

    private void validateResourceGenerations(List<VulkanicPassResourceModel.BindingSnapshot> bindings) {
        synchronized (lock) {
            for (VulkanicPassResourceModel.BindingSnapshot binding : bindings) {
                if (binding.resourceReference().isEmpty()) {
                    continue;
                }
                validateResourceGeneration(binding.resourceReference().get());
            }
        }
    }

    public void validateResourceGenerations(VulkanicGalV2.ResourceSet resourceSet) {
        Objects.requireNonNull(resourceSet, "resourceSet");
        synchronized (lock) {
            for (VulkanicGalV2.ResourceBinding binding : resourceSet.bindings()) {
                if (binding.resourceReference().isEmpty()) {
                    continue;
                }
                validateResourceGeneration(binding.resourceReference().get());
            }
        }
    }

    private void validateResourceGeneration(VulkanicPassResourceModel.CanonicalResourceReference reference) {
        if (reference.generation() == VulkanicPassResourceModel.UNKNOWN_GENERATION
            || reference.legacyId().isEmpty()) {
            return;
        }
        long currentGeneration = switch (reference.bindingKind()) {
            case SAMPLED_TEXTURE, STORAGE_IMAGE -> textureGenerationUnlocked(reference.legacyId().getAsInt());
            case BUFFER_RANGE, TEXEL_BUFFER -> bufferGenerationUnlocked(reference.legacyId().getAsInt());
            case ATTACHMENT -> VulkanicPassResourceModel.UNKNOWN_GENERATION;
        };
        if (currentGeneration != VulkanicPassResourceModel.UNKNOWN_GENERATION
            && currentGeneration != reference.generation()) {
            throw new IllegalStateException(
                "Stale GAL resource reference for " + reference.resource().logicalName()
                    + " (" + reference.resource().stableKey()
                    + "): captured generation " + reference.generation()
                    + " but current generation is " + currentGeneration
            );
        }
    }

    private static long payloadSize(java.nio.ByteBuffer payload) {
        return Math.max(1L, payload.remaining());
    }

    private void addPixelUnpackSourceIfBound(
        List<VulkanicPassResourceModel.CanonicalResourceReference> sources,
        long offset
    ) {
        int unpackBuffer = bufferBindings.getOrDefault(GL_PIXEL_UNPACK_BUFFER, 0);
        if (unpackBuffer > 0) {
            sources.add(bufferRef("pixel-unpack-buffer-source", unpackBuffer, Math.max(0L, offset), 1L,
                VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE,
                VulkanicPassResourceModel.Access.READ,
                VulkanicResourceUsage.TRANSFER_SRC,
                OptionalInt.of(GL_PIXEL_UNPACK_BUFFER)));
        }
    }

    private VulkanicPassResourceModel.CanonicalResourceReference boundBufferRef(
        String logicalName,
        int target,
        long offset,
        long size,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        String label
    ) {
        int buffer = bufferBindings.getOrDefault(target, 0);
        VulkanicPassResourceModel.ResourceKind kind = usage == VulkanicResourceUsage.TRANSFER_SRC
            ? VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE
            : VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION;
        if (buffer <= 0) {
            return bufferRef(logicalName, 0, offset, size, kind, access, usage, OptionalInt.of(target));
        }
        return bufferRef(logicalName, buffer, offset, size, kind, access, usage, OptionalInt.of(target));
    }

    private VulkanicPassResourceModel.CanonicalResourceReference bufferRef(
        String logicalName,
        int buffer,
        long offset,
        long size,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        OptionalInt bindingUnit
    ) {
        long normalizedSize = Math.max(1L, size);
        return VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
            logicalName,
            kind,
            buffer > 0 ? "legacy-buffer:" + buffer : "legacy-buffer:unbound:" + logicalName,
            Math.max(0L, offset),
            normalizedSize,
            access,
            usage,
            bindingUnit,
            buffer > 0 ? OptionalInt.of(buffer) : OptionalInt.empty(),
            bufferGenerationUnlocked(buffer)
        );
    }

    private VulkanicPassResourceModel.CanonicalResourceReference boundTextureRef(
        String logicalName,
        int target,
        int level,
        int layer,
        int layerCount,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        String label
    ) {
        int texture = boundTextureForTarget(target);
        return textureRef(logicalName, texture, OptionalInt.of(target), level, layer, layerCount, kind, access, usage);
    }

    private int boundTextureForTarget(int target) {
        Integer texture = textureBindings.get(new TextureBindingKey(activeTextureUnitIndex, target));
        if (texture == null) {
            texture = textureUnitBindings.get(activeTextureUnitIndex);
        }
        return texture == null ? 0 : texture;
    }

    private VulkanicPassResourceModel.CanonicalResourceReference textureRef(
        String logicalName,
        int texture,
        OptionalInt target,
        int level,
        int layer,
        int layerCount,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage
    ) {
        return new VulkanicPassResourceModel.CanonicalResourceReference(
            VulkanicPassResourceModel.ResourceIdentity.of(
                logicalName,
                kind,
                texture > 0 ? "legacy-texture:" + texture : "legacy-texture:unbound:" + logicalName
            ),
            VulkanicPassResourceModel.BindingKind.STORAGE_IMAGE,
            access,
            VulkanicPassResourceModel.Subresource.color(Math.max(0, level), 1, Math.max(0, layer), Math.max(1, layerCount)),
            usage,
            target.isPresent()
                ? targetClassForLegacyTarget(target.getAsInt())
                : VulkanicPassResourceModel.TargetClass.UNKNOWN,
            VulkanicPassResourceModel.FormatClass.COLOR,
            textureGenerationUnlocked(texture),
            texture > 0 ? OptionalInt.of(texture) : OptionalInt.empty(),
            target,
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            layerCount > 1
        );
    }

    private static VulkanicPassResourceModel.CanonicalResourceReference framebufferRef(
        String logicalName,
        int framebuffer,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage
    ) {
        VulkanicPassResourceModel.ResourceKind kind = usage == VulkanicResourceUsage.TRANSFER_SRC
            ? VulkanicPassResourceModel.ResourceKind.READBACK_SOURCE
            : VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION;
        return new VulkanicPassResourceModel.CanonicalResourceReference(
            VulkanicPassResourceModel.ResourceIdentity.of(logicalName, kind, "legacy-framebuffer:" + framebuffer),
            VulkanicPassResourceModel.BindingKind.ATTACHMENT,
            access,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            usage,
            VulkanicPassResourceModel.TargetClass.UNKNOWN,
            VulkanicPassResourceModel.FormatClass.COLOR,
            VulkanicPassResourceModel.UNKNOWN_GENERATION,
            OptionalInt.of(framebuffer),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty(),
            false
        );
    }

    private static VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot drawCommandSnapshot(
        VulkanicGalExecutionRequest.GraphicsDrawCommand command
    ) {
        return switch (command.kind()) {
            case ARRAYS -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.arrays(
                command.firstVertex(),
                command.vertexCount(),
                command.instanceCount()
            );
            case INDEXED -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.indexed(
                Math.toIntExact(command.indexByteOffset() / command.indexType().bytesPerIndex()),
                command.indexCount(),
                command.baseVertex(),
                command.instanceCount()
            );
            case MULTI_INDEXED_BASE_VERTEX -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.indexed(
                command.indexedDraws().get(0).firstIndex(),
                command.indexedDraws().stream().mapToInt(VulkanicGalExecutionRequest.IndexedDraw::indexCount).sum(),
                command.indexedDraws().get(0).baseVertex(),
                command.instanceCount()
            );
        };
    }

    public VulkanicGalExecutionRequest.DynamicStateSnapshot dynamicStateSnapshot() {
        synchronized (lock) {
            return new VulkanicGalExecutionRequest.DynamicStateSnapshot(fixedFunction.viewport, fixedFunction.scissor);
        }
    }

    private ProgramState program(int programId) {
        return programs.computeIfAbsent(programId, ProgramState::new);
    }

    private void updateUniformUnlocked(int location, UniformValue value) {
        ProgramState program = program(currentProgram);
        UniformValue previous = program.uniformsByLocation.get(location);
        if (uniformValueEquals(previous, value)) {
            return;
        }
        boolean shapeChanged = !uniformShapeEquals(previous, value);
        program.uniformsByLocation.put(location, value);
        program.advanceUniformContentKey(shapeChanged);
        advanceUniformContentVersionUnlocked(shapeChanged);
    }

    private static boolean uniformValueEquals(UniformValue left, UniformValue right) {
        if (left == null) {
            return false;
        }
        return uniformShapeEquals(left, right)
            && Arrays.equals(left.ints(), right.ints())
            && Arrays.equals(left.floats(), right.floats());
    }

    private static boolean uniformShapeEquals(UniformValue left, UniformValue right) {
        if (left == null || right == null) {
            return false;
        }
        return left.type().equals(right.type())
            && left.transpose() == right.transpose()
            && left.columns() == right.columns()
            && left.rows() == right.rows()
            && left.ints().length == right.ints().length
            && left.floats().length == right.floats().length;
    }

    private VaoState vao(int vao) {
        return vaos.computeIfAbsent(vao, VaoState::new);
    }

    private FramebufferState framebuffer(int framebuffer) {
        return framebuffers.computeIfAbsent(framebuffer, FramebufferState::new);
    }

    public sealed interface GraphicsStateView permits V2GraphicsState, GraphicsSnapshot {
        int programId();
        ProgramStateView program();
        int vaoId();
        VaoSnapshot vao();
        int drawFramebuffer();
        FramebufferSnapshot framebuffer();
        Map<Integer, Integer> bufferBindings();
        Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings();
        Map<Integer, Integer> texture2DByUnit();
        Map<Integer, Integer> textureUnitBindings();
        Map<TextureBindingKey, Integer> textureBindingsByKey();
        Map<Integer, Integer> samplerBindings();
        Map<Integer, ImageUnitBindingState> imageUnitBindings();
        Map<Integer, Long> textureGenerations();
        Map<Integer, Long> bufferGenerations();
        FixedFunctionSnapshot fixedFunction();
        String semanticIdentity();
        long programVersion();
        long vertexInputVersion();
        long framebufferVersion();
        long resourceBindingVersion();
        long fixedFunctionVersion();

        default VulkanicGalExecutionRequest.VertexInputSnapshot vertexInputSnapshot(
            VulkanicGalExecutionRequest.GraphicsDrawRequest request
        ) {
            return vertexInputSnapshotFor(this, request);
        }

        default List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshots() {
            return bindingSnapshotsFor(this);
        }
    }

    public sealed interface ProgramStateView permits ProgramSnapshot, ProgramState {
        int programId();
        Map<Integer, UniformValue> uniformsByLocation();
        String uniformContentKey();
        String shapeKey();
    }

    public record V2GraphicsState(
        int programId,
        ProgramStateView program,
        int vaoId,
        VaoSnapshot vao,
        int drawFramebuffer,
        FramebufferSnapshot framebuffer,
        Map<Integer, Integer> bufferBindings,
        Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings,
        Map<Integer, Integer> texture2DByUnit,
        Map<Integer, Integer> textureUnitBindings,
        Map<TextureBindingKey, Integer> textureBindingsByKey,
        Map<Integer, Integer> samplerBindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
        Map<Integer, Long> textureGenerations,
        Map<Integer, Long> bufferGenerations,
        List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshots,
        FixedFunctionSnapshot fixedFunction,
        String semanticIdentity,
        long programVersion,
        long vertexInputVersion,
        long framebufferVersion,
        long resourceBindingVersion,
        long fixedFunctionVersion
    ) implements GraphicsStateView {
        public V2GraphicsState {
            program = Objects.requireNonNull(program, "program");
            vao = Objects.requireNonNull(vao, "vao");
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            bufferBindings = Map.copyOf(bufferBindings);
            indexedBufferBindings = Map.copyOf(indexedBufferBindings);
            texture2DByUnit = Map.copyOf(texture2DByUnit);
            textureUnitBindings = Map.copyOf(textureUnitBindings);
            textureBindingsByKey = Map.copyOf(textureBindingsByKey);
            samplerBindings = Map.copyOf(samplerBindings);
            imageUnitBindings = Map.copyOf(imageUnitBindings);
            textureGenerations = Map.copyOf(textureGenerations);
            bufferGenerations = Map.copyOf(bufferGenerations);
            bindingSnapshots = List.copyOf(bindingSnapshots);
            fixedFunction = Objects.requireNonNull(fixedFunction, "fixedFunction");
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

        @Override
        public List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshots() {
            return bindingSnapshots;
        }
    }

    public record GraphicsSnapshot(
        int programId,
        ProgramSnapshot program,
        int vaoId,
        VaoSnapshot vao,
        int drawFramebuffer,
        FramebufferSnapshot framebuffer,
        Map<Integer, Integer> bufferBindings,
        Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings,
        Map<Integer, Integer> texture2DByUnit,
        Map<Integer, Integer> textureUnitBindings,
        Map<TextureBindingKey, Integer> textureBindingsByKey,
        Map<Integer, Integer> samplerBindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
        Map<Integer, Long> textureGenerations,
        Map<Integer, Long> bufferGenerations,
        FixedFunctionSnapshot fixedFunction,
        String semanticIdentity,
        long programVersion,
        long vertexInputVersion,
        long framebufferVersion,
        long resourceBindingVersion,
        long fixedFunctionVersion
    ) implements GraphicsStateView {
        public GraphicsSnapshot {
            program = Objects.requireNonNull(program, "program");
            vao = Objects.requireNonNull(vao, "vao");
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            bufferBindings = Map.copyOf(bufferBindings);
            indexedBufferBindings = Map.copyOf(indexedBufferBindings);
            texture2DByUnit = Map.copyOf(texture2DByUnit);
            textureUnitBindings = Map.copyOf(textureUnitBindings);
            textureBindingsByKey = Map.copyOf(textureBindingsByKey);
            samplerBindings = Map.copyOf(samplerBindings);
            imageUnitBindings = Map.copyOf(imageUnitBindings);
            textureGenerations = Map.copyOf(textureGenerations);
            bufferGenerations = Map.copyOf(bufferGenerations);
            fixedFunction = Objects.requireNonNull(fixedFunction, "fixedFunction");
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

        GraphicsSnapshot withSemanticIdentity(String semanticIdentity) {
            if (Objects.equals(this.semanticIdentity, semanticIdentity)) {
                return this;
            }
            return new GraphicsSnapshot(
                programId,
                program,
                vaoId,
                vao,
                drawFramebuffer,
                framebuffer,
                bufferBindings,
                indexedBufferBindings,
                texture2DByUnit,
                textureUnitBindings,
                textureBindingsByKey,
                samplerBindings,
                imageUnitBindings,
                textureGenerations,
                bufferGenerations,
                fixedFunction,
                semanticIdentity,
                programVersion,
                vertexInputVersion,
                framebufferVersion,
                resourceBindingVersion,
                fixedFunctionVersion
            );
        }

        GraphicsSnapshot withProgram(ProgramSnapshot program, String semanticIdentity, long programVersion) {
            return new GraphicsSnapshot(
                programId,
                program,
                vaoId,
                vao,
                drawFramebuffer,
                framebuffer,
                bufferBindings,
                indexedBufferBindings,
                texture2DByUnit,
                textureUnitBindings,
                textureBindingsByKey,
                samplerBindings,
                imageUnitBindings,
                textureGenerations,
                bufferGenerations,
                fixedFunction,
                semanticIdentity,
                programVersion,
                vertexInputVersion,
                framebufferVersion,
                resourceBindingVersion,
                fixedFunctionVersion
            );
        }

    }

    private static VulkanicGalExecutionRequest.VertexInputSnapshot vertexInputSnapshotFor(
        GraphicsStateView state,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        ArrayList<VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot> vertexBuffers = new ArrayList<>();
        for (VertexBindingState binding : state.vao().vertexBindings().values()) {
            vertexBuffers.add(new VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot(
                binding.binding(),
                binding.buffer() == 0 ? "default-attribute-buffer" : "legacy-buffer:" + binding.buffer(),
                binding.offset(),
                binding.stride(),
                binding.buffer() == 0
            ));
        }
        Optional<VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot> indexBuffer = Optional.empty();
        if (request.command().kind() != VulkanicGalExecutionRequest.DrawCommandKind.ARRAYS && state.vao().elementBuffer() > 0) {
            indexBuffer = Optional.of(new VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot(
                "legacy-buffer:" + state.vao().elementBuffer(),
                0,
                request.command().indexType().bytesPerIndex()
            ));
        }
        return new VulkanicGalExecutionRequest.VertexInputSnapshot(vertexBuffers, indexBuffer);
    }

    private static List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshotsFor(GraphicsStateView state) {
        ArrayList<VulkanicPassResourceModel.BindingSnapshot> bindings = new ArrayList<>();
        int order = 0;
        for (Map.Entry<Integer, Integer> entry : state.texture2DByUnit().entrySet()) {
            int unit = entry.getKey();
            int texture = entry.getValue();
            if (texture <= 0) {
                continue;
            }
            boolean storageAlias = state.imageUnitBindings().values().stream()
                .anyMatch(image -> image.texture() == texture);
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                "texture-unit-" + unit,
                VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                "legacy-texture:" + texture,
                VulkanicPassResourceModel.Access.READ,
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                VulkanicResourceUsage.SAMPLED_READ,
                state.semanticIdentity() + ":texture-unit:" + unit,
                storageAlias,
                order++
            );
            bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                "Sampler" + unit,
                use,
                OptionalInt.of(unit),
                OptionalInt.of(state.samplerBindings().getOrDefault(unit, 0)),
                Optional.of(sampledTextureReference(
                    use,
                    texture,
                    unit,
                    state.samplerBindings().getOrDefault(unit, 0),
                    OptionalInt.of(GL_TEXTURE_2D),
                    state.textureGenerations()
                ))
            ));
        }
        for (Map.Entry<Integer, Integer> entry : state.textureUnitBindings().entrySet()) {
            int unit = entry.getKey();
            if (state.texture2DByUnit().containsKey(unit)) {
                continue;
            }
            int texture = entry.getValue();
            if (texture <= 0) {
                continue;
            }
            boolean storageAlias = state.imageUnitBindings().values().stream()
                .anyMatch(image -> image.texture() == texture);
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                "texture-unit-" + unit,
                VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                "legacy-texture:" + texture,
                VulkanicPassResourceModel.Access.READ,
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                VulkanicResourceUsage.SAMPLED_READ,
                state.semanticIdentity() + ":direct-texture-unit:" + unit,
                storageAlias,
                order++
            );
            bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                "Sampler" + unit,
                use,
                OptionalInt.of(unit),
                OptionalInt.of(state.samplerBindings().getOrDefault(unit, 0)),
                Optional.of(sampledTextureReference(
                    use,
                    texture,
                    unit,
                    state.samplerBindings().getOrDefault(unit, 0),
                    OptionalInt.empty(),
                    state.textureGenerations()
                ))
            ));
        }
        order = addTargetAwareSamplerBindings(
            bindings,
            state.textureBindingsByKey(),
            state.texture2DByUnit(),
            state.textureUnitBindings(),
            state.samplerBindings(),
            state.imageUnitBindings(),
            state.textureGenerations(),
            state.semanticIdentity(),
            order
        );
        for (Map.Entry<IndexedBufferKey, BufferRangeState> entry : state.indexedBufferBindings().entrySet()) {
            BufferRangeState range = entry.getValue();
            if (range.buffer() <= 0) {
                continue;
            }
            String bindingName = range.semanticNameOrDefault("Buffer" + entry.getKey().index());
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
                "buffer-binding-" + entry.getKey().target() + "-" + entry.getKey().index(),
                "legacy-buffer:" + range.buffer(),
                Math.max(0L, range.offset()),
                range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                state.semanticIdentity() + ":indexed-buffer:" + entry.getKey().target() + ":" + entry.getKey().index(),
                order++
            );
            bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                bindingName,
                use,
                OptionalInt.of(entry.getKey().index()),
                OptionalInt.empty(),
                Optional.of(VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
                    use.resource().logicalName(),
                    use.resource().kind(),
                    use.resource().stableKey(),
                    Math.max(0L, range.offset()),
                    range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                    use.access(),
                    use.usage(),
                    OptionalInt.of(entry.getKey().index()),
                    OptionalInt.of(range.buffer()),
                    state.bufferGenerations().getOrDefault(range.buffer(), 0L)
                ))
            ));
        }
        addStorageImageBindings(bindings, state.imageUnitBindings(), state.textureGenerations(), state.semanticIdentity(), order);
        return bindings;
    }

    public record ComputeSnapshot(
        int programId,
        ProgramSnapshot program,
        Map<Integer, Integer> bufferBindings,
        Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings,
        Map<Integer, Integer> texture2DByUnit,
        Map<Integer, Integer> textureUnitBindings,
        Map<TextureBindingKey, Integer> textureBindingsByKey,
        Map<Integer, Integer> samplerBindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
        Map<Integer, Long> textureGenerations,
        Map<Integer, Long> bufferGenerations,
        String semanticIdentity
    ) {
        public ComputeSnapshot {
            program = Objects.requireNonNull(program, "program");
            bufferBindings = Map.copyOf(bufferBindings);
            indexedBufferBindings = Map.copyOf(indexedBufferBindings);
            texture2DByUnit = Map.copyOf(texture2DByUnit);
            textureUnitBindings = Map.copyOf(textureUnitBindings);
            textureBindingsByKey = Map.copyOf(textureBindingsByKey);
            samplerBindings = Map.copyOf(samplerBindings);
            imageUnitBindings = Map.copyOf(imageUnitBindings);
            textureGenerations = Map.copyOf(textureGenerations);
            bufferGenerations = Map.copyOf(bufferGenerations);
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

        List<VulkanicPassResourceModel.BindingSnapshot> bindingSnapshots() {
            ArrayList<VulkanicPassResourceModel.BindingSnapshot> bindings = new ArrayList<>();
            int order = 0;
            for (Map.Entry<Integer, Integer> entry : texture2DByUnit.entrySet()) {
                int unit = entry.getKey();
                int texture = entry.getValue();
                if (texture <= 0) {
                    continue;
                }
                boolean storageAlias = imageUnitBindings.values().stream()
                    .anyMatch(image -> image.texture() == texture);
                VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                    "compute-texture-unit-" + unit,
                    VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                    "legacy-texture:" + texture,
                    VulkanicPassResourceModel.Access.READ,
                    VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                    VulkanicResourceUsage.SAMPLED_READ,
                    semanticIdentity + ":compute-texture-unit:" + unit,
                    storageAlias,
                    order++
                );
                bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                    "Sampler" + unit,
                    use,
                    OptionalInt.of(unit),
                    OptionalInt.of(samplerBindings.getOrDefault(unit, 0)),
                    Optional.of(sampledTextureReference(
                        use,
                        texture,
                        unit,
                        samplerBindings.getOrDefault(unit, 0),
                        OptionalInt.of(GL_TEXTURE_2D),
                        textureGenerations
                    ))
                ));
            }
            for (Map.Entry<Integer, Integer> entry : textureUnitBindings.entrySet()) {
                int unit = entry.getKey();
                if (texture2DByUnit.containsKey(unit)) {
                    continue;
                }
                int texture = entry.getValue();
                if (texture <= 0) {
                    continue;
                }
                boolean storageAlias = imageUnitBindings.values().stream()
                    .anyMatch(image -> image.texture() == texture);
                VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                    "compute-texture-unit-" + unit,
                    VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                    "legacy-texture:" + texture,
                    VulkanicPassResourceModel.Access.READ,
                    VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                    VulkanicResourceUsage.SAMPLED_READ,
                    semanticIdentity + ":compute-direct-texture-unit:" + unit,
                    storageAlias,
                    order++
                );
                bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                    "Sampler" + unit,
                    use,
                    OptionalInt.of(unit),
                    OptionalInt.of(samplerBindings.getOrDefault(unit, 0)),
                    Optional.of(sampledTextureReference(
                        use,
                        texture,
                        unit,
                        samplerBindings.getOrDefault(unit, 0),
                        OptionalInt.empty(),
                        textureGenerations
                    ))
                ));
            }
            order = addTargetAwareSamplerBindings(
                bindings,
                textureBindingsByKey,
                texture2DByUnit,
                textureUnitBindings,
                samplerBindings,
                imageUnitBindings,
                textureGenerations,
                semanticIdentity,
                order
            );
            for (Map.Entry<IndexedBufferKey, BufferRangeState> entry : indexedBufferBindings.entrySet()) {
                BufferRangeState range = entry.getValue();
                if (range.buffer() <= 0) {
                    continue;
                }
                String bindingName = range.semanticNameOrDefault("Buffer" + entry.getKey().index());
                VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
                    "compute-buffer-binding-" + entry.getKey().target() + "-" + entry.getKey().index(),
                    "legacy-buffer:" + range.buffer(),
                    Math.max(0L, range.offset()),
                    range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                    semanticIdentity + ":compute-indexed-buffer:" + entry.getKey().target() + ":" + entry.getKey().index(),
                    order++
                );
                bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                    bindingName,
                    use,
                    OptionalInt.of(entry.getKey().index()),
                    OptionalInt.empty(),
                    Optional.of(VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
                        use.resource().logicalName(),
                        use.resource().kind(),
                        use.resource().stableKey(),
                        Math.max(0L, range.offset()),
                        range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                        use.access(),
                        use.usage(),
                        OptionalInt.of(entry.getKey().index()),
                        OptionalInt.of(range.buffer()),
                        bufferGenerations.getOrDefault(range.buffer(), 0L)
                    ))
                ));
            }
            addStorageImageBindings(bindings, imageUnitBindings, textureGenerations, semanticIdentity, order);
            return bindings;
        }
    }

    private static void addStorageImageBindings(
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
        Map<Integer, Long> textureGenerations,
        String semanticIdentity,
        int order
    ) {
        for (ImageUnitBindingState image : imageUnitBindings.values()) {
            if (image.texture() <= 0) {
                continue;
            }
            boolean sampledAlias = bindings.stream()
                .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-texture:" + image.texture()));
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                "image-unit-" + image.imageUnit(),
                VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE,
                "legacy-texture:" + image.texture(),
                imageAccess(image.access()),
                image.layered()
                    ? VulkanicPassResourceModel.Subresource.color(image.level(), 1, 0, Math.max(1, image.layer() + 1))
                    : VulkanicPassResourceModel.Subresource.color(image.level(), 1, Math.max(0, image.layer()), 1),
                imageUsage(image.access()),
                semanticIdentity + ":image-unit:" + image.imageUnit(),
                sampledAlias,
                order++
            );
            bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                "Image" + image.imageUnit(),
                use,
                OptionalInt.of(image.imageUnit()),
                OptionalInt.empty(),
                Optional.of(VulkanicPassResourceModel.CanonicalResourceReference.storageImage(
                    use.resource().logicalName(),
                    use.resource().stableKey(),
                    image.texture(),
                    image.level(),
                    image.layered(),
                    image.layer(),
                    use.access(),
                    use.usage(),
                    OptionalInt.of(image.imageUnit()),
                    OptionalInt.of(image.access()),
                    OptionalInt.of(image.format()),
                    textureGenerations.getOrDefault(image.texture(), 0L)
                ))
            ));
        }
    }

    private static VulkanicPassResourceModel.Access imageAccess(int access) {
        return switch (access) {
            case GL_READ_ONLY -> VulkanicPassResourceModel.Access.READ;
            case GL_WRITE_ONLY -> VulkanicPassResourceModel.Access.WRITE;
            default -> VulkanicPassResourceModel.Access.READ_WRITE;
        };
    }

    private static VulkanicResourceUsage imageUsage(int access) {
        return access == GL_READ_ONLY ? VulkanicResourceUsage.SAMPLED_READ : VulkanicResourceUsage.STORAGE_READ_WRITE;
    }

    private static int addTargetAwareSamplerBindings(
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        Map<TextureBindingKey, Integer> textureBindingsByKey,
        Map<Integer, Integer> texture2DByUnit,
        Map<Integer, Integer> textureUnitBindings,
        Map<Integer, Integer> samplerBindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
        Map<Integer, Long> textureGenerations,
        String semanticIdentity,
        int order
    ) {
        for (Map.Entry<TextureBindingKey, Integer> entry : textureBindingsByKey.entrySet()) {
            TextureBindingKey key = entry.getKey();
            if (texture2DByUnit.containsKey(key.unit()) || textureUnitBindings.containsKey(key.unit())) {
                continue;
            }
            int texture = entry.getValue();
            if (texture <= 0) {
                continue;
            }
            boolean storageAlias = imageUnitBindings.values().stream()
                .anyMatch(image -> image.texture() == texture);
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                "texture-unit-" + key.unit() + "-target-" + key.target(),
                VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                "legacy-texture:" + texture,
                VulkanicPassResourceModel.Access.READ,
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                VulkanicResourceUsage.SAMPLED_READ,
                semanticIdentity + ":texture-unit:" + key.unit() + ":target:" + key.target(),
                storageAlias,
                order++
            );
            bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                "Sampler" + key.unit(),
                use,
                OptionalInt.of(key.unit()),
                OptionalInt.of(samplerBindings.getOrDefault(key.unit(), 0)),
                Optional.of(sampledTextureReference(
                    use,
                    texture,
                    key.unit(),
                    samplerBindings.getOrDefault(key.unit(), 0),
                    OptionalInt.of(key.target()),
                    textureGenerations
                ))
            ));
        }
        return order;
    }

    private static VulkanicPassResourceModel.CanonicalResourceReference sampledTextureReference(
        VulkanicPassResourceModel.ResourceUse use,
        int texture,
        int unit,
        int sampler,
        OptionalInt target,
        Map<Integer, Long> textureGenerations
    ) {
        return VulkanicPassResourceModel.CanonicalResourceReference.sampledTexture(
            use.resource().logicalName(),
            use.resource().stableKey(),
            texture,
            target,
            target.isPresent()
                ? targetClassForLegacyTarget(target.getAsInt())
                : VulkanicPassResourceModel.TargetClass.UNKNOWN,
            use.subresource(),
            OptionalInt.of(unit),
            sampler > 0 ? OptionalInt.of(sampler) : OptionalInt.empty(),
            textureGenerations.getOrDefault(texture, 0L)
        );
    }

    private static VulkanicPassResourceModel.TargetClass targetClassForLegacyTarget(int target) {
        return switch (target) {
            case GL_TEXTURE_2D -> VulkanicPassResourceModel.TargetClass.TEXTURE_2D;
            case GL_TEXTURE_3D -> VulkanicPassResourceModel.TargetClass.TEXTURE_3D;
            default -> VulkanicPassResourceModel.TargetClass.UNKNOWN;
        };
    }

    public record ProgramSnapshot(
        int programId,
        Map<Integer, UniformValue> uniformsByLocation,
        String uniformContentKey,
        String shapeKey
    ) implements ProgramStateView {
        public ProgramSnapshot {
            uniformsByLocation = Map.copyOf(uniformsByLocation);
            uniformContentKey = Objects.requireNonNull(uniformContentKey, "uniformContentKey");
            shapeKey = Objects.requireNonNull(shapeKey, "shapeKey");
        }
    }

    public record VaoSnapshot(
        int vao,
        int elementBuffer,
        Map<Integer, VertexAttributeState> attributes,
        Map<Integer, VertexBindingState> vertexBindings,
        Map<Integer, float[]> defaultAttributes,
        List<Integer> enabledAttributes,
        String shapeKey
    ) {
        public VaoSnapshot {
            attributes = Map.copyOf(attributes);
            vertexBindings = Map.copyOf(vertexBindings);
            defaultAttributes = copyFloatMap(defaultAttributes);
            enabledAttributes = List.copyOf(enabledAttributes);
            shapeKey = Objects.requireNonNull(shapeKey, "shapeKey");
        }
    }

    public record FramebufferSnapshot(
        int framebuffer,
        Map<Integer, AttachmentState> attachments,
        List<Integer> drawBuffers,
        int readBuffer,
        String shapeKey
    ) {
        public FramebufferSnapshot {
            attachments = Map.copyOf(attachments);
            drawBuffers = List.copyOf(drawBuffers);
            shapeKey = Objects.requireNonNull(shapeKey, "shapeKey");
        }
    }

    public record UniformValue(String type, int[] ints, float[] floats, boolean transpose, int columns, int rows) {
        public UniformValue {
            type = Objects.requireNonNull(type, "type");
            ints = ints == null ? new int[0] : Arrays.copyOf(ints, ints.length);
            floats = floats == null ? new float[0] : Arrays.copyOf(floats, floats.length);
        }

        static UniformValue ints(int... values) {
            return new UniformValue("int" + values.length, values, new float[0], false, values.length, 1);
        }

        static UniformValue floats(float... values) {
            return new UniformValue("float" + values.length, new int[0], values, false, values.length, 1);
        }

        static UniformValue matrix(int columns, int rows, boolean transpose, float[] values) {
            return new UniformValue("mat" + columns + "x" + rows, new int[0], values, transpose, columns, rows);
        }
    }

    public record VertexAttributeState(
        int index,
        int binding,
        int size,
        int type,
        boolean normalized,
        boolean integer,
        int relativeOffset,
        int divisor,
        int capturedBuffer
    ) {
        VertexAttributeState withBinding(int binding) {
            return new VertexAttributeState(index, binding, size, type, normalized, integer, relativeOffset, divisor, capturedBuffer);
        }

        VertexAttributeState withDivisor(int divisor) {
            return new VertexAttributeState(index, binding, size, type, normalized, integer, relativeOffset, divisor, capturedBuffer);
        }
    }

    public record VertexBindingState(int binding, int buffer, long offset, int stride, int divisor) {
        VertexBindingState withDivisor(int divisor) {
            return new VertexBindingState(binding, buffer, offset, stride, divisor);
        }
    }

    public record AttachmentState(int attachment, int texture, int level) {
    }

    public record BufferRangeState(int buffer, long offset, long size, String semanticName) {
        public BufferRangeState(int buffer, long offset, long size) {
            this(buffer, offset, size, null);
        }

        String semanticNameOrDefault(String fallback) {
            return semanticName == null || semanticName.isBlank() ? fallback : semanticName;
        }
    }

    public record ImageUnitBindingState(
        int imageUnit,
        int texture,
        int level,
        boolean layered,
        int layer,
        int access,
        int format
    ) {
        public ImageUnitBindingState {
            if (imageUnit < 0) {
                throw new IllegalArgumentException("imageUnit must be >= 0");
            }
            if (texture < 0) {
                throw new IllegalArgumentException("texture must be >= 0");
            }
            if (level < 0) {
                throw new IllegalArgumentException("level must be >= 0");
            }
        }
    }

    public record IndexedBufferKey(int target, int index) {
    }

    public record TextureBindingKey(int unit, int target) {
    }

    public record StencilFuncState(int face, int func, int ref, int mask) {
    }

    public record StencilOpState(int face, int sfail, int dpfail, int dppass) {
    }

    public record FixedFunctionSnapshot(
        Optional<VulkanicGalExecutionRequest.Viewport> viewport,
        Optional<VulkanicGalExecutionRequest.Scissor> scissor,
        boolean blendEnabled,
        int blendSrcRgb,
        int blendDstRgb,
        int blendSrcAlpha,
        int blendDstAlpha,
        int blendEquationRgb,
        int blendEquationAlpha,
        boolean depthTestEnabled,
        int depthFunc,
        boolean depthWriteMask,
        boolean cullEnabled,
        int cullFaceMode,
        boolean scissorTestEnabled,
        boolean stencilTestEnabled,
        boolean logicOpEnabled,
        int logicOp,
        int polygonFace,
        int polygonMode,
        boolean polygonOffsetEnabled,
        float polygonOffsetFactor,
        float polygonOffsetUnits,
        boolean colorMaskR,
        boolean colorMaskG,
        boolean colorMaskB,
        boolean colorMaskA,
        Map<Integer, StencilFuncState> stencilFuncs,
        Map<Integer, StencilOpState> stencilOps,
        Map<Integer, Integer> stencilWriteMasks,
        String shapeKey
    ) {
        public FixedFunctionSnapshot {
            viewport = Objects.requireNonNull(viewport, "viewport");
            scissor = Objects.requireNonNull(scissor, "scissor");
            stencilFuncs = Map.copyOf(stencilFuncs);
            stencilOps = Map.copyOf(stencilOps);
            stencilWriteMasks = Map.copyOf(stencilWriteMasks);
            shapeKey = Objects.requireNonNull(shapeKey, "shapeKey");
        }
    }

    private static Map<Integer, float[]> copyFloatMap(Map<Integer, float[]> source) {
        Map<Integer, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, float[]> entry : source.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static final class ProgramState implements ProgramStateView {
        private final int programId;
        private final Map<Integer, UniformValue> uniformsByLocation = new LinkedHashMap<>();
        private String uniformContentKey = "uniform-values:empty";
        private String uniformShapeKey;
        private long uniformPayloadVersion;

        private ProgramState(int programId) {
            this.programId = programId;
            this.uniformShapeKey = programUniformShapeKey(programId, uniformsByLocation);
        }

        private ProgramSnapshot copy() {
            return new ProgramSnapshot(programId, uniformsByLocation, uniformContentKey, uniformShapeKey);
        }

        @Override
        public int programId() {
            return programId;
        }

        @Override
        public Map<Integer, UniformValue> uniformsByLocation() {
            return uniformsByLocation;
        }

        @Override
        public String uniformContentKey() {
            return uniformContentKey;
        }

        @Override
        public String shapeKey() {
            return uniformShapeKey;
        }

        private void advanceUniformContentKey(boolean shapeChanged) {
            uniformPayloadVersion++;
            uniformContentKey = "uniform-values:program=" + programId + ":version=" + uniformPayloadVersion;
            if (shapeChanged) {
                uniformShapeKey = programUniformShapeKey(programId, uniformsByLocation);
            }
        }
    }

    private static final class VaoState {
        private final int vao;
        private int elementBuffer;
        private final Map<Integer, VertexAttributeState> attributes = new LinkedHashMap<>();
        private final Map<Integer, VertexBindingState> vertexBindings = new LinkedHashMap<>();
        private final Map<Integer, float[]> defaultAttributes = new LinkedHashMap<>();
        private final List<Integer> enabledAttributes = new ArrayList<>();
        private boolean layoutDirty = true;
        private String cachedLayoutShapeKey;

        private VaoState(int vao) {
            this.vao = vao;
        }

        private void markLayoutDirty() {
            layoutDirty = true;
        }

        private String layoutShapeKey() {
            if (layoutDirty || cachedLayoutShapeKey == null) {
                cachedLayoutShapeKey = vertexLayoutShapeKey(attributes, vertexBindings, defaultAttributes, enabledAttributes);
                layoutDirty = false;
            }
            return cachedLayoutShapeKey;
        }

        private VaoSnapshot copy() {
            return new VaoSnapshot(
                vao,
                elementBuffer,
                attributes,
                vertexBindings,
                defaultAttributes,
                enabledAttributes,
                layoutShapeKey()
            );
        }
    }

    private static final class FramebufferState {
        private final int framebuffer;
        private final Map<Integer, AttachmentState> attachments = new LinkedHashMap<>();
        private List<Integer> drawBuffers;
        private int readBuffer;

        private FramebufferState(int framebuffer) {
            this.framebuffer = framebuffer;
            this.drawBuffers = List.of(framebuffer == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
            this.readBuffer = framebuffer == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0;
        }

        private FramebufferSnapshot copy() {
            return new FramebufferSnapshot(
                framebuffer,
                attachments,
                drawBuffers,
                readBuffer,
                framebufferShapeKey(framebuffer, attachments, drawBuffers, readBuffer)
            );
        }
    }

    private static final class FixedFunctionState {
        private Optional<VulkanicGalExecutionRequest.Viewport> viewport = Optional.empty();
        private Optional<VulkanicGalExecutionRequest.Scissor> scissor = Optional.empty();
        private boolean blendEnabled;
        private int blendSrcRgb = 1 /* GL_ONE */;
        private int blendDstRgb = 0 /* GL_ZERO */;
        private int blendSrcAlpha = 1 /* GL_ONE */;
        private int blendDstAlpha = 0 /* GL_ZERO */;
        private int blendEquationRgb = 0x8006 /* GL_FUNC_ADD */;
        private int blendEquationAlpha = 0x8006 /* GL_FUNC_ADD */;
        private boolean depthTestEnabled;
        private int depthFunc = 0x0201 /* GL_LESS */;
        private boolean depthWriteMask = true;
        private boolean cullEnabled;
        private int cullFaceMode = 0x0405 /* GL_BACK */;
        private boolean scissorTestEnabled;
        private boolean stencilTestEnabled;
        private boolean logicOpEnabled;
        private int logicOp = 0x1503 /* GL_COPY */;
        private int polygonFace = 0x0408 /* GL_FRONT_AND_BACK */;
        private int polygonMode = 0x1B02 /* GL_FILL */;
        private boolean polygonOffsetEnabled;
        private float polygonOffsetFactor;
        private float polygonOffsetUnits;
        private boolean colorMaskR = true;
        private boolean colorMaskG = true;
        private boolean colorMaskB = true;
        private boolean colorMaskA = true;
        private final Map<Integer, StencilFuncState> stencilFuncs = new HashMap<>();
        private final Map<Integer, StencilOpState> stencilOps = new HashMap<>();
        private final Map<Integer, Integer> stencilWriteMasks = new HashMap<>();

        private FixedFunctionSnapshot copy() {
            return new FixedFunctionSnapshot(
                viewport,
                scissor,
                blendEnabled,
                blendSrcRgb,
                blendDstRgb,
                blendSrcAlpha,
                blendDstAlpha,
                blendEquationRgb,
                blendEquationAlpha,
                depthTestEnabled,
                depthFunc,
                depthWriteMask,
                cullEnabled,
                cullFaceMode,
                scissorTestEnabled,
                stencilTestEnabled,
                logicOpEnabled,
                logicOp,
                polygonFace,
                polygonMode,
                polygonOffsetEnabled,
                polygonOffsetFactor,
                polygonOffsetUnits,
                colorMaskR,
                colorMaskG,
                colorMaskB,
                colorMaskA,
                stencilFuncs,
                stencilOps,
                stencilWriteMasks,
                fixedFunctionShapeKey(this)
            );
        }
    }

    private static String programUniformShapeKey(int programId, Map<Integer, UniformValue> uniformsByLocation) {
        StringBuilder builder = new StringBuilder(128);
        builder.append("program=").append(programId).append(';');
        uniformsByLocation.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                UniformValue value = entry.getValue();
                builder.append("uniform[")
                    .append(entry.getKey())
                    .append("]=")
                    .append(value.type())
                    .append(":transpose=")
                    .append(value.transpose())
                    .append(":cols=")
                    .append(value.columns())
                    .append(":rows=")
                    .append(value.rows())
                    .append(":ints=")
                    .append(value.ints().length)
                    .append(":floats=")
                    .append(value.floats().length)
                    .append(';');
            });
        return sha256Hex(builder.toString());
    }

    private static String vertexLayoutShapeKey(
        Map<Integer, VertexAttributeState> attributes,
        Map<Integer, VertexBindingState> vertexBindings,
        Map<Integer, float[]> defaultAttributes,
        List<Integer> enabledAttributes
    ) {
        StringBuilder builder = new StringBuilder(256);
        ArrayList<Integer> sortedEnabled = new ArrayList<>(enabledAttributes);
        sortedEnabled.sort(Integer::compareTo);
        Integer previousAttribute = null;
        builder.append("enabled=");
        for (Integer attribute : sortedEnabled) {
            if (attribute == null || attribute.equals(previousAttribute)) {
                continue;
            }
            previousAttribute = attribute;
            builder.append(attribute).append(',');
        }
        builder.append(';');
        attributes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("attr[")
                .append(entry.getKey())
                .append("]=")
                .append(vertexAttributeShapeKey(entry.getValue()))
                .append(';'));
        vertexBindings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("binding[")
                .append(entry.getKey())
                .append("]=stride:")
                .append(entry.getValue().stride())
                .append(":divisor:")
                .append(entry.getValue().divisor())
                .append(';'));
        defaultAttributes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("default[")
                .append(entry.getKey())
                .append("]=")
                .append(Arrays.toString(entry.getValue()))
                .append(';'));
        return sha256Hex(builder.toString());
    }

    private static boolean sameVertexAttributeLayout(VertexAttributeState left, VertexAttributeState right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.index() == right.index()
            && left.binding() == right.binding()
            && left.size() == right.size()
            && left.type() == right.type()
            && left.normalized() == right.normalized()
            && left.integer() == right.integer()
            && left.relativeOffset() == right.relativeOffset()
            && left.divisor() == right.divisor();
    }

    private static boolean sameVertexBindingLayout(VertexBindingState left, VertexBindingState right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.binding() == right.binding()
            && left.stride() == right.stride()
            && left.divisor() == right.divisor();
    }

    private static String vertexAttributeShapeKey(VertexAttributeState attribute) {
        return "index=" + attribute.index()
            + ":binding=" + attribute.binding()
            + ":size=" + attribute.size()
            + ":type=" + attribute.type()
            + ":normalized=" + attribute.normalized()
            + ":integer=" + attribute.integer()
            + ":relativeOffset=" + attribute.relativeOffset()
            + ":divisor=" + attribute.divisor();
    }

    private static String framebufferShapeKey(
        int framebuffer,
        Map<Integer, AttachmentState> attachments,
        List<Integer> drawBuffers,
        int readBuffer
    ) {
        StringBuilder builder = new StringBuilder(128);
        builder.append("framebuffer=").append(framebuffer)
            .append(";drawBuffers=").append(drawBuffers)
            .append(";readBuffer=").append(readBuffer)
            .append(';');
        attachments.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("attachment[")
                .append(entry.getKey())
                .append("]=")
                .append(entry.getValue())
                .append(';'));
        return sha256Hex(builder.toString());
    }

    private static String fixedFunctionShapeKey(FixedFunctionState state) {
        return sha256Hex(
            "viewport=" + state.viewport
                + ";scissor=" + state.scissor
                + ";blend=" + state.blendEnabled + ':' + state.blendSrcRgb + ':' + state.blendDstRgb + ':' + state.blendSrcAlpha + ':' + state.blendDstAlpha
                + ";blendEq=" + state.blendEquationRgb + ':' + state.blendEquationAlpha
                + ";depth=" + state.depthTestEnabled + ':' + state.depthFunc + ':' + state.depthWriteMask
                + ";cull=" + state.cullEnabled + ':' + state.cullFaceMode
                + ";scissorTest=" + state.scissorTestEnabled
                + ";stencil=" + state.stencilTestEnabled + ':' + state.stencilFuncs + ':' + state.stencilOps + ':' + state.stencilWriteMasks
                + ";logic=" + state.logicOpEnabled + ':' + state.logicOp
                + ";polygon=" + state.polygonFace + ':' + state.polygonMode + ':' + state.polygonOffsetEnabled + ':' + state.polygonOffsetFactor + ':' + state.polygonOffsetUnits
                + ";colorMask=" + state.colorMaskR + ':' + state.colorMaskG + ':' + state.colorMaskB + ':' + state.colorMaskA
        );
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >>> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static final class PixelStoreState {
        private int packRowLength;
        private int packAlignment = 4;
        private int unpackRowLength;
        private int unpackSkipRows;
        private int unpackSkipPixels;
        private int unpackAlignment = 4;

        private VulkanicGalExecutionRequest.TransferPixelStoreSnapshot copy() {
            return new VulkanicGalExecutionRequest.TransferPixelStoreSnapshot(
                packRowLength,
                packAlignment,
                unpackRowLength,
                unpackSkipRows,
                unpackSkipPixels,
                unpackAlignment
            );
        }
    }
}

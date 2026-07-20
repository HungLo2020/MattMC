package net.vulkanic;

import java.nio.FloatBuffer;
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
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_3D = 0x806F;
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
    private final FixedFunctionState fixedFunction = new FixedFunctionState();
    private int currentProgram;
    private int currentVao;
    private int boundReadFramebuffer;
    private int boundDrawFramebuffer;
    private int activeTextureUnitIndex;

    public void bindProgram(int programId) {
        synchronized (lock) {
            currentProgram = programId;
            if (programId > 0) {
                programs.computeIfAbsent(programId, ProgramState::new);
            }
        }
    }

    public void deleteProgram(int programId) {
        synchronized (lock) {
            programs.remove(programId);
            if (currentProgram == programId) {
                currentProgram = 0;
            }
        }
    }

    public void setUniformInt(int location, int... values) {
        synchronized (lock) {
            program(currentProgram).uniformsByLocation.put(location, UniformValue.ints(values));
        }
    }

    public void setUniformFloat(int location, float... values) {
        synchronized (lock) {
            program(currentProgram).uniformsByLocation.put(location, UniformValue.floats(values));
        }
    }

    public void setUniformMatrix(int location, int columns, int rows, boolean transpose, float[] values) {
        synchronized (lock) {
            program(currentProgram).uniformsByLocation.put(location, UniformValue.matrix(columns, rows, transpose, values));
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
            textureUnitBindings.remove(activeTextureUnitIndex);
            textureBindings.put(new TextureBindingKey(activeTextureUnitIndex, target), texture);
        }
    }

    public void bindTexture(int unitIndex, int target, int texture) {
        synchronized (lock) {
            int unit = Math.max(0, unitIndex);
            textureUnitBindings.remove(unit);
            textureBindings.put(new TextureBindingKey(unit, target), texture);
        }
    }

    public void bindTexture2D(int texture) {
        bindTexture(GL_TEXTURE_2D, texture);
    }

    public void bindTextureUnit(int unit, int texture) {
        synchronized (lock) {
            int unitIndex = Math.max(0, unit);
            textureBindings.keySet().removeIf(key -> key.unit() == unitIndex);
            textureUnitBindings.put(unitIndex, texture);
        }
    }

    public void bindSampler(int unit, int sampler) {
        synchronized (lock) {
            samplerBindings.put(Math.max(0, unit), sampler);
        }
    }

    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        synchronized (lock) {
            imageUnitBindings.put(
                Math.max(0, unit),
                new ImageUnitBindingState(Math.max(0, unit), texture, level, layered, layer, access, format)
            );
        }
    }

    public void bindSamplers(int first, int[] samplers) {
        Objects.requireNonNull(samplers, "samplers");
        synchronized (lock) {
            for (int i = 0; i < samplers.length; i++) {
                samplerBindings.put(Math.max(0, first + i), samplers[i]);
            }
        }
    }

    public void deleteTexture(int texture) {
        synchronized (lock) {
            textureBindings.values().removeIf(value -> value == texture);
            imageUnitBindings.values().removeIf(value -> value.texture() == texture);
        }
    }

    public void bindBuffer(int target, int buffer) {
        synchronized (lock) {
            bufferBindings.put(target, buffer);
            if (target == GL_ELEMENT_ARRAY_BUFFER) {
                vao(currentVao).elementBuffer = buffer;
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
        synchronized (lock) {
            indexedBufferBindings.put(new IndexedBufferKey(target, index), new BufferRangeState(buffer, offset, size));
        }
    }

    public void deleteBuffer(int buffer) {
        synchronized (lock) {
            bufferBindings.values().removeIf(value -> value == buffer);
            indexedBufferBindings.values().removeIf(value -> value.buffer() == buffer);
            for (VaoState vao : vaos.values()) {
                if (vao.elementBuffer == buffer) {
                    vao.elementBuffer = 0;
                }
                vao.vertexBindings.values().removeIf(binding -> binding.buffer() == buffer);
                vao.attributes.values().removeIf(attribute -> attribute.capturedBuffer() == buffer);
            }
        }
    }

    public void bindVertexArray(int vao) {
        synchronized (lock) {
            currentVao = Math.max(0, vao);
            if (vao > 0) {
                vaos.computeIfAbsent(vao, VaoState::new);
            }
        }
    }

    public void deleteVertexArray(int vao) {
        synchronized (lock) {
            vaos.remove(vao);
            if (currentVao == vao) {
                currentVao = 0;
            }
        }
    }

    public void enableVertexAttribArray(int index) {
        synchronized (lock) {
            vao(currentVao).enabledAttributes.add(index);
        }
    }

    public void disableVertexAttribArray(int index) {
        synchronized (lock) {
            vao(currentVao).enabledAttributes.remove(Integer.valueOf(index));
        }
    }

    public void setVertexAttribPointer(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            int divisor = previous == null ? 0 : previous.divisor();
            int binding = index;
            int capturedBuffer = bufferBindings.getOrDefault(GL_ARRAY_BUFFER, 0);
            vao.attributes.put(index, new VertexAttributeState(
                index,
                binding,
                size,
                type,
                normalized,
                integer,
                Math.toIntExact(pointer),
                divisor,
                capturedBuffer
            ));
            vao.vertexBindings.put(binding, new VertexBindingState(binding, capturedBuffer, 0L, stride, divisor));
        }
    }

    public void setVertexAttribFormat(int index, int size, int type, boolean normalized, boolean integer, int relativeOffset) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            int binding = previous == null ? index : previous.binding();
            int divisor = previous == null ? 0 : previous.divisor();
            int capturedBuffer = previous == null ? 0 : previous.capturedBuffer();
            vao.attributes.put(index, new VertexAttributeState(
                index,
                binding,
                size,
                type,
                normalized,
                integer,
                relativeOffset,
                divisor,
                capturedBuffer
            ));
        }
    }

    public void setVertexAttribBinding(int index, int binding) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            if (previous == null) {
                vao.attributes.put(index, new VertexAttributeState(index, binding, 4, 0x1406, false, false, 0, 0, 0));
                return;
            }
            vao.attributes.put(index, previous.withBinding(binding));
        }
    }

    public void bindVertexBuffer(int binding, int buffer, long offset, int stride) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexBindingState previous = vao.vertexBindings.get(binding);
            int divisor = previous == null ? 0 : previous.divisor();
            vao.vertexBindings.put(binding, new VertexBindingState(binding, buffer, offset, stride, divisor));
        }
    }

    public void setVertexAttribDivisor(int index, int divisor) {
        synchronized (lock) {
            VaoState vao = vao(currentVao);
            VertexAttributeState previous = vao.attributes.get(index);
            if (previous == null) {
                vao.attributes.put(index, new VertexAttributeState(index, index, 4, 0x1406, false, false, 0, divisor, 0));
                return;
            }
            vao.attributes.put(index, previous.withDivisor(divisor));
            VertexBindingState binding = vao.vertexBindings.get(previous.binding());
            if (binding != null) {
                vao.vertexBindings.put(previous.binding(), binding.withDivisor(divisor));
            }
        }
    }

    public void setVertexAttribDefault(int index, float v0, float v1, float v2, float v3) {
        synchronized (lock) {
            vao(currentVao).defaultAttributes.put(index, new float[] {v0, v1, v2, v3});
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
        }
    }

    public void setDrawBuffer(int mode) {
        synchronized (lock) {
            framebuffer(boundDrawFramebuffer).drawBuffers = List.of(mode);
        }
    }

    public void setNamedDrawBuffers(int framebuffer, int[] buffers) {
        synchronized (lock) {
            framebuffer(framebuffer).drawBuffers = Arrays.stream(buffers).boxed().toList();
        }
    }

    public void setNamedReadBuffer(int framebuffer, int mode) {
        synchronized (lock) {
            framebuffer(framebuffer).readBuffer = mode;
        }
    }

    public void setReadBuffer(int mode) {
        synchronized (lock) {
            framebuffer(boundReadFramebuffer).readBuffer = mode;
        }
    }

    public void setDrawBuffers(int[] buffers) {
        synchronized (lock) {
            framebuffer(boundDrawFramebuffer).drawBuffers = Arrays.stream(buffers).boxed().toList();
        }
    }

    public void setViewport(int x, int y, int width, int height) {
        synchronized (lock) {
            fixedFunction.viewport = Optional.of(new VulkanicGalExecutionRequest.Viewport(x, y, width, height, 0.0F, 1.0F));
        }
    }

    public void setScissor(int x, int y, int width, int height) {
        synchronized (lock) {
            fixedFunction.scissor = Optional.of(new VulkanicGalExecutionRequest.Scissor(x, y, width, height));
        }
    }

    public void setScissorTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.scissorTestEnabled = enabled;
        }
    }

    public void setStencilTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.stencilTestEnabled = enabled;
        }
    }

    public void setBlendEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.blendEnabled = enabled;
        }
    }

    public void setBlendFunction(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        synchronized (lock) {
            fixedFunction.blendSrcRgb = srcRgb;
            fixedFunction.blendDstRgb = dstRgb;
            fixedFunction.blendSrcAlpha = srcAlpha;
            fixedFunction.blendDstAlpha = dstAlpha;
        }
    }

    public void setBlendEquation(int rgb, int alpha) {
        synchronized (lock) {
            fixedFunction.blendEquationRgb = rgb;
            fixedFunction.blendEquationAlpha = alpha;
        }
    }

    public void setDepthTest(boolean enabled, int func) {
        synchronized (lock) {
            fixedFunction.depthTestEnabled = enabled;
            fixedFunction.depthFunc = func;
        }
    }

    public void setDepthTestEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.depthTestEnabled = enabled;
        }
    }

    public void setDepthFunc(int func) {
        synchronized (lock) {
            fixedFunction.depthFunc = func;
        }
    }

    public void setDepthWriteMask(boolean enabled) {
        synchronized (lock) {
            fixedFunction.depthWriteMask = enabled;
        }
    }

    public void setCull(boolean enabled, int mode) {
        synchronized (lock) {
            fixedFunction.cullEnabled = enabled;
            fixedFunction.cullFaceMode = mode;
        }
    }

    public void setCullEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.cullEnabled = enabled;
        }
    }

    public void setCullFaceMode(int mode) {
        synchronized (lock) {
            fixedFunction.cullFaceMode = mode;
        }
    }

    public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
        synchronized (lock) {
            fixedFunction.colorMaskR = r;
            fixedFunction.colorMaskG = g;
            fixedFunction.colorMaskB = b;
            fixedFunction.colorMaskA = a;
        }
    }

    public void setStencilFunc(int face, int func, int ref, int mask) {
        synchronized (lock) {
            fixedFunction.stencilFuncs.put(face, new StencilFuncState(face, func, ref, mask));
        }
    }

    public void setStencilOp(int face, int sfail, int dpfail, int dppass) {
        synchronized (lock) {
            fixedFunction.stencilOps.put(face, new StencilOpState(face, sfail, dpfail, dppass));
        }
    }

    public void setStencilWriteMask(int face, int mask) {
        synchronized (lock) {
            fixedFunction.stencilWriteMasks.put(face, mask);
        }
    }

    public void setLogicOpEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.logicOpEnabled = enabled;
        }
    }

    public void setLogicOp(int opcode) {
        synchronized (lock) {
            fixedFunction.logicOp = opcode;
        }
    }

    public void setPolygonMode(int face, int mode) {
        synchronized (lock) {
            fixedFunction.polygonFace = face;
            fixedFunction.polygonMode = mode;
        }
    }

    public void setPolygonOffsetEnabled(boolean enabled) {
        synchronized (lock) {
            fixedFunction.polygonOffsetEnabled = enabled;
        }
    }

    public void setPolygonOffset(float factor, float units) {
        synchronized (lock) {
            fixedFunction.polygonOffsetFactor = factor;
            fixedFunction.polygonOffsetUnits = units;
        }
    }

    public GraphicsSnapshot captureGraphics(VulkanicGalExecutionRequest.GraphicsDrawRequest request) {
        synchronized (lock) {
            VaoSnapshot vao = vao(currentVao).copy();
            FramebufferSnapshot framebuffer = framebuffer(boundDrawFramebuffer).copy();
            Map<Integer, Integer> texture2DByUnit = new LinkedHashMap<>();
            for (Map.Entry<TextureBindingKey, Integer> entry : textureBindings.entrySet()) {
                if (entry.getKey().target() == GL_TEXTURE_2D) {
                    texture2DByUnit.put(entry.getKey().unit(), entry.getValue());
                }
            }
            return new GraphicsSnapshot(
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
                fixedFunction.copy(),
                request.semanticIdentity().label()
            );
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
                request.semanticIdentity().label()
            );
        }
    }

    public VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot compatibilitySnapshotFor(
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        GraphicsSnapshot snapshot = captureGraphics(request);
        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInput = snapshot.vertexInputSnapshot(request);
        List<VulkanicPassResourceModel.BindingSnapshot> bindings = snapshot.bindingSnapshots();
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
        List<VulkanicPassResourceModel.ResourceUse> resourceUses = new ArrayList<>(vertexPlan.orderedUses());
        for (VulkanicPassResourceModel.BindingSnapshot binding : bindings) {
            resourceUses.add(binding.resourceUse());
        }
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

    private VaoState vao(int vao) {
        return vaos.computeIfAbsent(vao, VaoState::new);
    }

    private FramebufferState framebuffer(int framebuffer) {
        return framebuffers.computeIfAbsent(framebuffer, FramebufferState::new);
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
        FixedFunctionSnapshot fixedFunction,
        String semanticIdentity
    ) {
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
            fixedFunction = Objects.requireNonNull(fixedFunction, "fixedFunction");
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInputSnapshot(
            VulkanicGalExecutionRequest.GraphicsDrawRequest request
        ) {
            ArrayList<VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot> vertexBuffers = new ArrayList<>();
            for (VertexBindingState binding : vao.vertexBindings().values()) {
                vertexBuffers.add(new VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot(
                    binding.binding(),
                    binding.buffer() == 0 ? "default-attribute-buffer" : "legacy-buffer:" + binding.buffer(),
                    binding.offset(),
                    binding.stride(),
                    binding.buffer() == 0
                ));
            }
            Optional<VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot> indexBuffer = Optional.empty();
            if (request.command().kind() != VulkanicGalExecutionRequest.DrawCommandKind.ARRAYS && vao.elementBuffer() > 0) {
                indexBuffer = Optional.of(new VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot(
                    "legacy-buffer:" + vao.elementBuffer(),
                    0,
                    request.command().indexType().bytesPerIndex()
                ));
            }
            return new VulkanicGalExecutionRequest.VertexInputSnapshot(vertexBuffers, indexBuffer);
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
                    "texture-unit-" + unit,
                    VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                    "legacy-texture:" + texture,
                    VulkanicPassResourceModel.Access.READ,
                    VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                    VulkanicResourceUsage.SAMPLED_READ,
                    semanticIdentity + ":texture-unit:" + unit,
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
                        OptionalInt.of(GL_TEXTURE_2D)
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
                    "texture-unit-" + unit,
                    VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                    "legacy-texture:" + texture,
                    VulkanicPassResourceModel.Access.READ,
                    VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                    VulkanicResourceUsage.SAMPLED_READ,
                    semanticIdentity + ":direct-texture-unit:" + unit,
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
                        OptionalInt.empty()
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
                semanticIdentity,
                order
            );
            for (Map.Entry<IndexedBufferKey, BufferRangeState> entry : indexedBufferBindings.entrySet()) {
                BufferRangeState range = entry.getValue();
                if (range.buffer() <= 0) {
                    continue;
                }
                VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
                    "buffer-binding-" + entry.getKey().target() + "-" + entry.getKey().index(),
                    "legacy-buffer:" + range.buffer(),
                    Math.max(0L, range.offset()),
                    range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                    semanticIdentity + ":indexed-buffer:" + entry.getKey().target() + ":" + entry.getKey().index(),
                    order++
                );
                bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                    "Buffer" + entry.getKey().index(),
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
                        OptionalInt.of(entry.getKey().index())
                    ))
                ));
            }
            addStorageImageBindings(bindings, imageUnitBindings, semanticIdentity, order);
            return bindings;
        }
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
                        OptionalInt.of(GL_TEXTURE_2D)
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
                        OptionalInt.empty()
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
                semanticIdentity,
                order
            );
            for (Map.Entry<IndexedBufferKey, BufferRangeState> entry : indexedBufferBindings.entrySet()) {
                BufferRangeState range = entry.getValue();
                if (range.buffer() <= 0) {
                    continue;
                }
                VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
                    "compute-buffer-binding-" + entry.getKey().target() + "-" + entry.getKey().index(),
                    "legacy-buffer:" + range.buffer(),
                    Math.max(0L, range.offset()),
                    range.size() == Long.MAX_VALUE ? 1L : Math.max(1L, range.size()),
                    semanticIdentity + ":compute-indexed-buffer:" + entry.getKey().target() + ":" + entry.getKey().index(),
                    order++
                );
                bindings.add(new VulkanicPassResourceModel.BindingSnapshot(
                    "Buffer" + entry.getKey().index(),
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
                        OptionalInt.of(entry.getKey().index())
                    ))
                ));
            }
            addStorageImageBindings(bindings, imageUnitBindings, semanticIdentity, order);
            return bindings;
        }
    }

    private static void addStorageImageBindings(
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        Map<Integer, ImageUnitBindingState> imageUnitBindings,
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
                    OptionalInt.of(image.format())
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
                    OptionalInt.of(key.target())
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
        OptionalInt target
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
            sampler > 0 ? OptionalInt.of(sampler) : OptionalInt.empty()
        );
    }

    private static VulkanicPassResourceModel.TargetClass targetClassForLegacyTarget(int target) {
        return switch (target) {
            case GL_TEXTURE_2D -> VulkanicPassResourceModel.TargetClass.TEXTURE_2D;
            case GL_TEXTURE_3D -> VulkanicPassResourceModel.TargetClass.TEXTURE_3D;
            default -> VulkanicPassResourceModel.TargetClass.UNKNOWN;
        };
    }

    public record ProgramSnapshot(int programId, Map<Integer, UniformValue> uniformsByLocation) {
        public ProgramSnapshot {
            uniformsByLocation = Map.copyOf(uniformsByLocation);
        }
    }

    public record VaoSnapshot(
        int vao,
        int elementBuffer,
        Map<Integer, VertexAttributeState> attributes,
        Map<Integer, VertexBindingState> vertexBindings,
        Map<Integer, float[]> defaultAttributes,
        List<Integer> enabledAttributes
    ) {
        public VaoSnapshot {
            attributes = Map.copyOf(attributes);
            vertexBindings = Map.copyOf(vertexBindings);
            defaultAttributes = copyFloatMap(defaultAttributes);
            enabledAttributes = List.copyOf(enabledAttributes);
        }
    }

    public record FramebufferSnapshot(
        int framebuffer,
        Map<Integer, AttachmentState> attachments,
        List<Integer> drawBuffers,
        int readBuffer
    ) {
        public FramebufferSnapshot {
            attachments = Map.copyOf(attachments);
            drawBuffers = List.copyOf(drawBuffers);
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

    public record BufferRangeState(int buffer, long offset, long size) {
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
        Map<Integer, Integer> stencilWriteMasks
    ) {
        public FixedFunctionSnapshot {
            viewport = Objects.requireNonNull(viewport, "viewport");
            scissor = Objects.requireNonNull(scissor, "scissor");
            stencilFuncs = Map.copyOf(stencilFuncs);
            stencilOps = Map.copyOf(stencilOps);
            stencilWriteMasks = Map.copyOf(stencilWriteMasks);
        }
    }

    private static Map<Integer, float[]> copyFloatMap(Map<Integer, float[]> source) {
        Map<Integer, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, float[]> entry : source.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static final class ProgramState {
        private final int programId;
        private final Map<Integer, UniformValue> uniformsByLocation = new LinkedHashMap<>();

        private ProgramState(int programId) {
            this.programId = programId;
        }

        private ProgramSnapshot copy() {
            return new ProgramSnapshot(programId, uniformsByLocation);
        }
    }

    private static final class VaoState {
        private final int vao;
        private int elementBuffer;
        private final Map<Integer, VertexAttributeState> attributes = new LinkedHashMap<>();
        private final Map<Integer, VertexBindingState> vertexBindings = new LinkedHashMap<>();
        private final Map<Integer, float[]> defaultAttributes = new LinkedHashMap<>();
        private final List<Integer> enabledAttributes = new ArrayList<>();

        private VaoState(int vao) {
            this.vao = vao;
        }

        private VaoSnapshot copy() {
            return new VaoSnapshot(vao, elementBuffer, attributes, vertexBindings, defaultAttributes, enabledAttributes);
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
            return new FramebufferSnapshot(framebuffer, attachments, drawBuffers, readBuffer);
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
                stencilWriteMasks
            );
        }
    }
}

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
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_TEXTURE0 = 0x84C0;

    private final Object lock = new Object();
    private final Map<Integer, ProgramState> programs = new ConcurrentHashMap<>();
    private final Map<Integer, VaoState> vaos = new ConcurrentHashMap<>();
    private final Map<Integer, FramebufferState> framebuffers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> bufferBindings = new HashMap<>();
    private final Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings = new HashMap<>();
    private final Map<TextureBindingKey, Integer> textureBindings = new HashMap<>();
    private final Map<Integer, Integer> samplerBindings = new HashMap<>();
    private final Map<Integer, ImageUnitBindingState> imageUnitBindings = new HashMap<>();
    private final FixedFunctionState fixedFunction = new FixedFunctionState();
    private int currentProgram;
    private int currentVao;
    private int boundReadFramebuffer;
    private int boundDrawFramebuffer;
    private int activeTextureUnitIndex;

    public void bindSampler(int unit, int sampler) {
        synchronized (lock) {
            samplerBindings.put(Math.max(0, unit), sampler);
        }
    }

    public void deleteTexture(int texture) {
        synchronized (lock) {
            textureBindings.values().removeIf(value -> value == texture);
            imageUnitBindings.values().removeIf(value -> value.texture() == texture);
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
            textureBindingsByKey = Map.copyOf(textureBindingsByKey);
            samplerBindings = Map.copyOf(samplerBindings);
            imageUnitBindings = Map.copyOf(imageUnitBindings);
            fixedFunction = Objects.requireNonNull(fixedFunction, "fixedFunction");
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

    }

    public record ComputeSnapshot(
        int programId,
        ProgramSnapshot program,
        Map<Integer, Integer> bufferBindings,
        Map<IndexedBufferKey, BufferRangeState> indexedBufferBindings,
        Map<Integer, Integer> texture2DByUnit,
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
            textureBindingsByKey = Map.copyOf(textureBindingsByKey);
            samplerBindings = Map.copyOf(samplerBindings);
            imageUnitBindings = Map.copyOf(imageUnitBindings);
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
        }

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

    }

    public record VertexBindingState(int binding, int buffer, long offset, int stride, int divisor) {
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
        private List<Integer> drawBuffers = List.of(GL_COLOR_ATTACHMENT0);
        private int readBuffer = GL_COLOR_ATTACHMENT0;

        private FramebufferState(int framebuffer) {
            this.framebuffer = framebuffer;
        }

        private FramebufferSnapshot copy() {
            return new FramebufferSnapshot(framebuffer, attachments, drawBuffers, readBuffer);
        }
    }

    private static final class FixedFunctionState {
        private Optional<VulkanicGalExecutionRequest.Viewport> viewport = Optional.empty();
        private Optional<VulkanicGalExecutionRequest.Scissor> scissor = Optional.empty();
        private boolean blendEnabled;
        private int blendSrcRgb;
        private int blendDstRgb;
        private int blendSrcAlpha;
        private int blendDstAlpha;
        private int blendEquationRgb;
        private int blendEquationAlpha;
        private boolean depthTestEnabled;
        private int depthFunc;
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

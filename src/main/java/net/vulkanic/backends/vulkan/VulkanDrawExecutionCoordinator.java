package net.vulkanic.backends.vulkan;

import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicSpirvModule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves legacy OpenGL-shaped draw state into immutable Vulkan draw execution plans.
 *
 * <p>This coordinator owns semantic draw validation, legacy VAO/program snapshots,
 * vertex-input planning, topology selection, index range math, and draw command
 * shape. Native handles, pipeline creation, descriptor materialization, command
 * recording, and synchronization remain in {@link VulkanBackend.NativeSpine}.</p>
 */
final class VulkanDrawExecutionCoordinator {
    DrawExecutionPlan planLegacyDraw(
        SemanticDrawRequest request,
        @Nullable LegacyProgramSnapshot program,
        DrawResourceSnapshot resources,
        LegacyRenderStateSnapshot renderState
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(renderState, "renderState");

        PipelineDescriptor descriptor = null;
        LegacyVaoSnapshot vao = resources.vertexArray();
        VertexStreamPlan vertexStream = VertexStreamPlan.noProgram(vao.vertexBuffersForDraw());
        if (program != null && program.linked() && !program.spirvModules().isEmpty()) {
            PipelineDescriptor.VertexInputState vertexInput = planLegacyVertexInput(program, vao);
            if (vertexInput != null) {
                descriptor = createLegacyProgramPipelineDescriptor(program, request.mode(), renderState, vertexInput);
                vertexStream = new VertexStreamPlan(vertexInput, vao.vertexBuffersForDraw());
            }
        }

        IndexStreamPlan indexStream = request.indexed()
            ? planIndexStream(request)
            : null;
        DrawCommandPlan command = request.indexed()
            ? DrawCommandPlan.indexed(indexStream.firstIndex(), request.indexCount(), request.baseVertex(), request.instanceCount())
            : DrawCommandPlan.arrays(request.firstVertex(), request.vertexCount(), request.instanceCount());
        return new DrawExecutionPlan(
            request,
            program == null ? 0 : program.programId(),
            descriptor,
            vertexStream,
            indexStream,
            command
        );
    }

    @Nullable
    PipelineDescriptor.VertexInputState planLegacyVertexInput(LegacyProgramSnapshot program, LegacyVaoSnapshot vao) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(vao, "vao");
        List<LegacyVertexAttributeSnapshot> attributes = vao.enabledAttributes().stream()
            .sorted(Comparator.comparingInt(LegacyVertexAttributeSnapshot::index))
            .toList();
        if (attributes.isEmpty()
            && program.vertexInputs().isEmpty()
            && program.attributeLocationsByName().isEmpty()) {
            return null;
        }

        Map<Integer, String> reflectedInputTypesByLocation = program.vertexInputs().stream()
            .collect(Collectors.toMap(
                ReflectedVertexInputSnapshot::location,
                ReflectedVertexInputSnapshot::typeName,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        LinkedHashMap<Integer, PipelineDescriptor.VertexInputBinding> bindings = new LinkedHashMap<>();
        List<PipelineDescriptor.VertexInputAttribute> vertexAttributes = new ArrayList<>(attributes.size());
        Set<Integer> providedLocations = new LinkedHashSet<>();
        for (LegacyVertexAttributeSnapshot attribute : attributes) {
            LegacyVertexBindingSnapshot binding = vao.binding(attribute.binding());
            int stride = binding != null && binding.stride() > 0
                ? binding.stride()
                : attributeByteSize(attribute.size(), attribute.type());
            bindings.putIfAbsent(attribute.binding(), new PipelineDescriptor.VertexInputBinding(
                attribute.binding(),
                stride,
                attribute.divisor() > 0
                    ? PipelineDescriptor.VertexInputRate.INSTANCE
                    : PipelineDescriptor.VertexInputRate.VERTEX
            ));
            vertexAttributes.add(new PipelineDescriptor.VertexInputAttribute(
                attribute.index(),
                attribute.binding(),
                legacyVertexAttributeFormatForShaderInput(
                    attribute.type(),
                    attribute.size(),
                    attribute.normalized(),
                    attribute.integer(),
                    reflectedInputTypesByLocation.get(attribute.index())
                ),
                attribute.offset()
            ));
            providedLocations.add(attribute.index());
        }

        boolean needsDefaultBinding = false;
        for (ReflectedVertexInputSnapshot input : program.vertexInputs()) {
            if (providedLocations.contains(input.location())) {
                continue;
            }

            vertexAttributes.add(new PipelineDescriptor.VertexInputAttribute(
                input.location(),
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                defaultVertexAttributeFormatForGlslType(input.typeName()),
                0
            ));
            providedLocations.add(input.location());
            needsDefaultBinding = true;
        }
        for (Map.Entry<String, Integer> entry : program.attributeLocationsByName().entrySet()) {
            if (providedLocations.contains(entry.getValue())) {
                continue;
            }
            PipelineDescriptor.VertexAttributeFormat defaultFormat =
                defaultVertexAttributeFormatForKnownAttribute(entry.getKey());
            if (defaultFormat == null) {
                continue;
            }

            vertexAttributes.add(new PipelineDescriptor.VertexInputAttribute(
                entry.getValue(),
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                defaultFormat,
                0
            ));
            providedLocations.add(entry.getValue());
            needsDefaultBinding = true;
        }

        if (needsDefaultBinding) {
            bindings.putIfAbsent(
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                new PipelineDescriptor.VertexInputBinding(
                    VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                    16,
                    PipelineDescriptor.VertexInputRate.INSTANCE
                )
            );
        }

        return new PipelineDescriptor.VertexInputState(new ArrayList<>(bindings.values()), vertexAttributes);
    }

    IndexStreamPlan planIndexStream(SemanticDrawRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.indexed()) {
            throw new IllegalArgumentException("Index stream requires an indexed draw request");
        }
        VulkanicIndexType indexType = Objects.requireNonNull(request.indexType(), "indexType");
        if ((request.indexByteOffset() % indexType.bytesPerIndex()) != 0L) {
            throw new IllegalArgumentException("Index offset must align to index type size. offset="
                + request.indexByteOffset() + ", bytesPerIndex=" + indexType.bytesPerIndex());
        }
        long firstIndexLong = request.indexByteOffset() / indexType.bytesPerIndex();
        if (firstIndexLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Computed firstIndex exceeds int range: " + firstIndexLong);
        }
        return new IndexStreamPlan(indexType, request.indexByteOffset(), (int) firstIndexLong, request.indexCount());
    }

    void validateBoundIndexRange(BoundIndexStream boundIndex, IndexStreamPlan plan) {
        Objects.requireNonNull(boundIndex, "boundIndex");
        Objects.requireNonNull(plan, "plan");
        if (boundIndex.sizeBytes() <= 0) {
            throw new IllegalArgumentException("index buffer size must be > 0, got: " + boundIndex.sizeBytes());
        }
        long requiredBytes = (long) plan.indexType().bytesPerIndex() * ((long) plan.firstIndex() + plan.indexCount());
        if (requiredBytes > boundIndex.sizeBytes()) {
            throw new IllegalStateException(
                "Indexed draw exceeds bound index buffer range: firstIndex=" + plan.firstIndex()
                    + ", indexCount=" + plan.indexCount()
                    + ", indexType=" + plan.indexType()
                    + ", requiredBytes=" + requiredBytes
                    + ", boundSizeBytes=" + boundIndex.sizeBytes()
                    + ", buffer=0x" + Long.toHexString(boundIndex.bufferHandle())
            );
        }
    }

    private static PipelineDescriptor createLegacyProgramPipelineDescriptor(
        LegacyProgramSnapshot program,
        int mode,
        LegacyRenderStateSnapshot renderState,
        PipelineDescriptor.VertexInputState vertexInputState
    ) {
        PipelineDescriptor.ResourceLayout resourceLayout = program.resourceLayout();
        if (resourceLayout == null) {
            resourceLayout = new PipelineDescriptor.ResourceLayout(List.of());
        }

        java.util.Optional<PipelineDescriptor.BlendState> blendState = java.util.Optional.empty();
        if (renderState.blendEnabled()) {
            SourceFactor srcRgb = sourceFactorFromLegacyGl(renderState.blendSrcRgb());
            DestFactor dstRgb = destFactorFromLegacyGl(renderState.blendDstRgb());
            SourceFactor srcAlpha = sourceFactorFromLegacyGl(renderState.blendSrcAlpha());
            DestFactor dstAlpha = destFactorFromLegacyGl(renderState.blendDstAlpha());
            if (srcRgb != null && dstRgb != null && srcAlpha != null && dstAlpha != null) {
                blendState = java.util.Optional.of(new PipelineDescriptor.BlendState(srcRgb, dstRgb, srcAlpha, dstAlpha));
            }
        }

        PipelineDescriptor.PortableState portableState = new PipelineDescriptor.PortableState(
            ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + program.programId()),
            ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + program.programId() + "/vertex"),
            ResourceLocation.fromNamespaceAndPath("vulkanic", "legacy_program/" + program.programId() + "/fragment"),
            Map.of(),
            Set.of(),
            List.of(),
            List.of(),
            blendState,
            renderState.depthTestEnabled() ? legacyDepthFunction(renderState.depthFunc()) : DepthTestFunction.NO_DEPTH_TEST,
            renderState.polygonMode(),
            renderState.cullFaceEnabled(),
            renderState.cullFaceMode(),
            renderState.colorMaskR() || renderState.colorMaskG() || renderState.colorMaskB(),
            renderState.colorMaskA(),
            renderState.depthWriteMask(),
            renderState.logicOp(),
            DefaultVertexFormat.EMPTY,
            legacyVertexMode(mode),
            renderState.polygonOffsetFactor(),
            renderState.polygonOffsetUnits()
        );

        return PipelineDescriptor.fromPortableStateAndSpirvModules(portableState, program.spirvModules())
            .withResourceLayout(resourceLayout)
            .withVertexInputState(vertexInputState);
    }

    private static PipelineDescriptor.VertexAttributeFormat defaultVertexAttributeFormatForKnownAttribute(String attributeName) {
        return switch (attributeName) {
            case "vPosition", "irisExtra" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT;
            case "iris_color" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
            case "aScale", "aTranslateSubChunk" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT;
            case "aTranslateChunk" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT;
            case "aMaterial" -> PipelineDescriptor.VertexAttributeFormat.R32_SINT;
            default -> null;
        };
    }

    static PipelineDescriptor.VertexAttributeFormat defaultVertexAttributeFormatForGlslType(String typeName) {
        return switch (typeName) {
            case "float" -> PipelineDescriptor.VertexAttributeFormat.R32_SFLOAT;
            case "vec2" -> PipelineDescriptor.VertexAttributeFormat.R32G32_SFLOAT;
            case "vec3" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT;
            case "vec4" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
            case "int" -> PipelineDescriptor.VertexAttributeFormat.R32_SINT;
            case "ivec2" -> PipelineDescriptor.VertexAttributeFormat.R32G32_SINT;
            case "ivec3" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT;
            case "ivec4" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SINT;
            case "uint" -> PipelineDescriptor.VertexAttributeFormat.R32_UINT;
            case "uvec2" -> PipelineDescriptor.VertexAttributeFormat.R32G32_UINT;
            case "uvec3" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_UINT;
            case "uvec4" -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT;
            default -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
        };
    }

    static PipelineDescriptor.VertexAttributeFormat legacyVertexAttributeFormatForShaderInput(
        int type,
        int size,
        boolean normalized,
        boolean integer,
        @Nullable String reflectedTypeName
    ) {
        boolean shaderRequiresIntegerInput = reflectedTypeName != null && isIntegerGlslVertexInputType(reflectedTypeName);
        boolean effectiveInteger = integer || (shaderRequiresIntegerInput && isIntegerCompatibleVertexAttributeType(type));
        return toVertexAttributeFormat(type, size, normalized, effectiveInteger);
    }

    static int attributeByteSize(int size, int type) {
        if (size <= 0) {
            throw new IllegalArgumentException("attribute size must be > 0");
        }
        int bytesPerComponent = switch (type) {
            case VulkanicAPI.GL_BYTE, VulkanicAPI.GL_UNSIGNED_BYTE -> 1;
            case VulkanicAPI.GL_SHORT, VulkanicAPI.GL_UNSIGNED_SHORT, VulkanicAPI.GL_HALF_FLOAT -> 2;
            case VulkanicAPI.GL_INT, VulkanicAPI.GL_UNSIGNED_INT, VulkanicAPI.GL_FLOAT -> 4;
            case VulkanicAPI.GL_DOUBLE -> 8;
            default -> throw new IllegalArgumentException("Unsupported vertex attribute GL type: " + type);
        };
        return size * bytesPerComponent;
    }

    static VertexFormat.Mode legacyVertexMode(int mode) {
        return switch (mode) {
            case VulkanicAPI.GL_LINES -> VertexFormat.Mode.LINES;
            case 0x0003 -> VertexFormat.Mode.LINE_STRIP;
            case 0x0005 -> VertexFormat.Mode.TRIANGLE_STRIP;
            case VulkanicAPI.GL_TRIANGLE_FAN -> VertexFormat.Mode.TRIANGLE_FAN;
            case 0x0007 -> VertexFormat.Mode.QUADS;
            default -> VertexFormat.Mode.TRIANGLES;
        };
    }

    private static DepthTestFunction legacyDepthFunction(int func) {
        return switch (func) {
            case 0x0202 -> DepthTestFunction.EQUAL_DEPTH_TEST;
            case 0x0203 -> DepthTestFunction.LEQUAL_DEPTH_TEST;
            case 0x0204 -> DepthTestFunction.GREATER_DEPTH_TEST;
            case 0x0201 -> DepthTestFunction.LESS_DEPTH_TEST;
            default -> DepthTestFunction.LESS_DEPTH_TEST;
        };
    }

    private static boolean isIntegerGlslVertexInputType(String typeName) {
        return typeName.equals("int")
            || typeName.equals("ivec2")
            || typeName.equals("ivec3")
            || typeName.equals("ivec4")
            || typeName.equals("uint")
            || typeName.equals("uvec2")
            || typeName.equals("uvec3")
            || typeName.equals("uvec4");
    }

    private static boolean isIntegerCompatibleVertexAttributeType(int type) {
        return type == VulkanicAPI.GL_UNSIGNED_BYTE
            || type == VulkanicAPI.GL_BYTE
            || type == VulkanicAPI.GL_UNSIGNED_SHORT
            || type == VulkanicAPI.GL_SHORT
            || type == VulkanicAPI.GL_UNSIGNED_INT
            || type == VulkanicAPI.GL_INT;
    }

    private static PipelineDescriptor.VertexAttributeFormat toVertexAttributeFormat(
        int type,
        int size,
        boolean normalized,
        boolean integer
    ) {
        return switch (type) {
            case VulkanicAPI.GL_UNSIGNED_BYTE -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8_UINT : PipelineDescriptor.VertexAttributeFormat.R8_UNORM;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8_UNORM;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8_UNORM;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Unsupported unsigned-byte attribute size: " + size);
            };
            case VulkanicAPI.GL_BYTE -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8_SINT : PipelineDescriptor.VertexAttributeFormat.R8_SNORM;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8_SNORM;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8_SNORM;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SINT : PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_SNORM;
                default -> throw new IllegalArgumentException("Unsupported byte attribute size: " + size);
            };
            case VulkanicAPI.GL_UNSIGNED_SHORT -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16_UINT : PipelineDescriptor.VertexAttributeFormat.R16_USCALED;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16_USCALED;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16_USCALED;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_UINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_USCALED;
                default -> throw new IllegalArgumentException("Unsupported unsigned-short attribute size: " + size);
            };
            case VulkanicAPI.GL_SHORT -> switch (size) {
                case 1 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16_SINT : PipelineDescriptor.VertexAttributeFormat.R16_SSCALED;
                case 2 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16_SSCALED;
                case 3 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16_SSCALED;
                case 4 -> integer ? PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SINT : PipelineDescriptor.VertexAttributeFormat.R16G16B16A16_SSCALED;
                default -> throw new IllegalArgumentException("Unsupported short attribute size: " + size);
            };
            case VulkanicAPI.GL_INT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_SINT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_SINT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SINT;
                default -> throw new IllegalArgumentException("Unsupported int attribute size: " + size);
            };
            case VulkanicAPI.GL_UNSIGNED_INT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_UINT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_UINT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_UINT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT;
                default -> throw new IllegalArgumentException("Unsupported unsigned-int attribute size: " + size);
            };
            case VulkanicAPI.GL_FLOAT -> switch (size) {
                case 1 -> PipelineDescriptor.VertexAttributeFormat.R32_SFLOAT;
                case 2 -> PipelineDescriptor.VertexAttributeFormat.R32G32_SFLOAT;
                case 3 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT;
                case 4 -> PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT;
                default -> throw new IllegalArgumentException("Unsupported float attribute size: " + size);
            };
            default -> throw new IllegalArgumentException("Unsupported vertex attribute GL type: " + type);
        };
    }

    private static SourceFactor sourceFactorFromLegacyGl(int factor) {
        return VulkanicBlendFactor.fromLegacyGlConstant(factor)
            .map(VulkanDrawExecutionCoordinator::toSourceFactor)
            .orElse(null);
    }

    private static DestFactor destFactorFromLegacyGl(int factor) {
        return VulkanicBlendFactor.fromLegacyGlConstant(factor)
            .flatMap(VulkanDrawExecutionCoordinator::toDestFactor)
            .orElse(null);
    }

    private static SourceFactor toSourceFactor(VulkanicBlendFactor factor) {
        return switch (factor) {
            case ZERO -> SourceFactor.ZERO;
            case ONE -> SourceFactor.ONE;
            case SRC_COLOR -> SourceFactor.SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> SourceFactor.ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> SourceFactor.DST_COLOR;
            case ONE_MINUS_DST_COLOR -> SourceFactor.ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> SourceFactor.SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> SourceFactor.ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> SourceFactor.DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> SourceFactor.ONE_MINUS_DST_ALPHA;
            case SRC_ALPHA_SATURATE -> SourceFactor.SRC_ALPHA_SATURATE;
            case CONSTANT_COLOR -> SourceFactor.CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR -> SourceFactor.ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA -> SourceFactor.CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA -> SourceFactor.ONE_MINUS_CONSTANT_ALPHA;
        };
    }

    private static java.util.Optional<DestFactor> toDestFactor(VulkanicBlendFactor factor) {
        return switch (factor) {
            case ZERO -> java.util.Optional.of(DestFactor.ZERO);
            case ONE -> java.util.Optional.of(DestFactor.ONE);
            case SRC_COLOR -> java.util.Optional.of(DestFactor.SRC_COLOR);
            case ONE_MINUS_SRC_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_SRC_COLOR);
            case DST_COLOR -> java.util.Optional.of(DestFactor.DST_COLOR);
            case ONE_MINUS_DST_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_DST_COLOR);
            case SRC_ALPHA -> java.util.Optional.of(DestFactor.SRC_ALPHA);
            case ONE_MINUS_SRC_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_SRC_ALPHA);
            case DST_ALPHA -> java.util.Optional.of(DestFactor.DST_ALPHA);
            case ONE_MINUS_DST_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_DST_ALPHA);
            case CONSTANT_COLOR -> java.util.Optional.of(DestFactor.CONSTANT_COLOR);
            case ONE_MINUS_CONSTANT_COLOR -> java.util.Optional.of(DestFactor.ONE_MINUS_CONSTANT_COLOR);
            case CONSTANT_ALPHA -> java.util.Optional.of(DestFactor.CONSTANT_ALPHA);
            case ONE_MINUS_CONSTANT_ALPHA -> java.util.Optional.of(DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            case SRC_ALPHA_SATURATE -> java.util.Optional.empty();
        };
    }

    record SemanticDrawRequest(
        String source,
        boolean indexed,
        int mode,
        int firstVertex,
        int vertexCount,
        long indexByteOffset,
        int indexCount,
        @Nullable VulkanicIndexType indexType,
        int instanceCount,
        int baseVertex
    ) {
        SemanticDrawRequest {
            Objects.requireNonNull(source, "source");
            if (firstVertex < 0 || vertexCount < 0 || indexByteOffset < 0L || indexCount < 0 || instanceCount < 1) {
                throw new IllegalArgumentException("Invalid draw request arguments: " + source);
            }
            if (indexed && indexType == null) {
                throw new IllegalArgumentException("Indexed draw requires index type");
            }
            if (!indexed && indexType != null) {
                throw new IllegalArgumentException("Non-indexed draw must not carry index type");
            }
        }

        static SemanticDrawRequest arrays(String source, int mode, int firstVertex, int vertexCount, int instanceCount) {
            return new SemanticDrawRequest(source, false, mode, firstVertex, vertexCount, 0L, 0, null, instanceCount, 0);
        }

        static SemanticDrawRequest indexed(
            String source,
            int mode,
            int indexCount,
            VulkanicIndexType indexType,
            long indexByteOffset,
            int instanceCount,
            int baseVertex
        ) {
            return new SemanticDrawRequest(source, true, mode, 0, 0, indexByteOffset, indexCount, indexType, instanceCount, baseVertex);
        }
    }

    record DrawResourceSnapshot(
        LegacyVaoSnapshot vertexArray,
        @Nullable IndexBufferSnapshot indexBuffer
    ) {
        DrawResourceSnapshot {
            Objects.requireNonNull(vertexArray, "vertexArray");
        }
    }

    record IndexBufferSnapshot(int bufferId, long bufferHandle, int sizeBytes) {
    }

    record LegacyRenderStateSnapshot(
        boolean blendEnabled,
        int blendSrcRgb,
        int blendDstRgb,
        int blendSrcAlpha,
        int blendDstAlpha,
        boolean depthTestEnabled,
        int depthFunc,
        boolean depthWriteMask,
        boolean cullFaceEnabled,
        int cullFaceMode,
        boolean colorMaskR,
        boolean colorMaskG,
        boolean colorMaskB,
        boolean colorMaskA,
        LogicOp logicOp,
        PolygonMode polygonMode,
        float polygonOffsetFactor,
        float polygonOffsetUnits
    ) {
        LegacyRenderStateSnapshot {
            Objects.requireNonNull(logicOp, "logicOp");
            Objects.requireNonNull(polygonMode, "polygonMode");
        }
    }

    record LegacyProgramSnapshot(
        int programId,
        boolean linked,
        @Nullable String debugLabel,
        List<VulkanicSpirvModule> spirvModules,
        List<ReflectedVertexInputSnapshot> vertexInputs,
        Map<String, Integer> attributeLocationsByName,
        @Nullable PipelineDescriptor.ResourceLayout resourceLayout
    ) {
        LegacyProgramSnapshot {
            spirvModules = List.copyOf(spirvModules);
            vertexInputs = List.copyOf(vertexInputs);
            attributeLocationsByName = Map.copyOf(attributeLocationsByName);
        }
    }

    record ReflectedVertexInputSnapshot(int location, String typeName) {
        ReflectedVertexInputSnapshot {
            Objects.requireNonNull(typeName, "typeName");
        }
    }

    record LegacyVaoSnapshot(
        List<LegacyVertexAttributeSnapshot> enabledAttributes,
        List<LegacyVertexBindingSnapshot> bindings,
        List<VertexBufferBindingPlan> vertexBuffersForDraw
    ) {
        LegacyVaoSnapshot {
            enabledAttributes = List.copyOf(enabledAttributes);
            bindings = List.copyOf(bindings);
            vertexBuffersForDraw = List.copyOf(vertexBuffersForDraw);
        }

        @Nullable
        LegacyVertexBindingSnapshot binding(int binding) {
            for (LegacyVertexBindingSnapshot snapshot : bindings) {
                if (snapshot.binding() == binding) {
                    return snapshot;
                }
            }
            return null;
        }
    }

    record LegacyVertexAttributeSnapshot(
        int index,
        int binding,
        int size,
        int type,
        boolean normalized,
        boolean integer,
        int offset,
        int divisor
    ) {
    }

    record LegacyVertexBindingSnapshot(int binding, int stride, long offset, int divisor, int bufferId) {
    }

    record VertexBufferBindingPlan(int binding, int bufferId, long offset, boolean defaultAttributeBuffer) {
    }

    record VertexStreamPlan(
        @Nullable PipelineDescriptor.VertexInputState vertexInputState,
        List<VertexBufferBindingPlan> vertexBuffers
    ) {
        VertexStreamPlan {
            vertexBuffers = List.copyOf(vertexBuffers);
        }

        static VertexStreamPlan noProgram(List<VertexBufferBindingPlan> vertexBuffers) {
            return new VertexStreamPlan(null, vertexBuffers);
        }
    }

    record IndexStreamPlan(VulkanicIndexType indexType, long indexByteOffset, int firstIndex, int indexCount) {
        IndexStreamPlan {
            Objects.requireNonNull(indexType, "indexType");
        }
    }

    record BoundIndexStream(long bufferHandle, int sizeBytes, VulkanicIndexType indexType) {
        BoundIndexStream {
            Objects.requireNonNull(indexType, "indexType");
        }
    }

    record DrawCommandPlan(
        boolean indexed,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount,
        int baseVertex,
        int instanceCount
    ) {
        static DrawCommandPlan arrays(int firstVertex, int vertexCount, int instanceCount) {
            return new DrawCommandPlan(false, firstVertex, vertexCount, 0, 0, 0, instanceCount);
        }

        static DrawCommandPlan indexed(int firstIndex, int indexCount, int baseVertex, int instanceCount) {
            return new DrawCommandPlan(true, 0, 0, firstIndex, indexCount, baseVertex, instanceCount);
        }
    }

    record DrawExecutionPlan(
        SemanticDrawRequest request,
        int programId,
        @Nullable PipelineDescriptor descriptor,
        VertexStreamPlan vertexStream,
        @Nullable IndexStreamPlan indexStream,
        DrawCommandPlan command
    ) {
        DrawExecutionPlan {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(vertexStream, "vertexStream");
            Objects.requireNonNull(command, "command");
        }
    }
}

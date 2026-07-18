package net.vulkanic.backends.vulkan;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicUniformReflectionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure shader interpretation and variant-planning policy for the Vulkan backend.
 *
 * <p>This planner owns the OpenGL-compatibility interpretation layer: source
 * rebinding, reflected logical resources, standalone-uniform classification,
 * vertex-input requirements, and immutable plan records. It does not invoke
 * native compilers or create Vulkan objects.</p>
 */
final class VulkanShaderVariantPlanner {
    private static final Pattern GLSL_BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern GLSL_LINE_COMMENT_PATTERN = Pattern.compile("(?m)//.*$");
    private static final Pattern GLSL_VERTEX_INPUT_DECLARATION_PATTERN = Pattern.compile(
        "(?m)(^\\s*)(?:layout\\s*\\(([^)]*)\\)\\s*)?((?:[A-Za-z0-9_]+\\s+)*)(?:in|attribute)\\s+([A-Za-z0-9_]+)\\s+([A-Za-z_][A-Za-z0-9_]*)(\\s*\\[[^]]+\\])?\\s*;"
    );
    private static final Pattern GLSL_FRAGMENT_OUTPUT_DECLARATION_PATTERN = Pattern.compile(
        "(?m)(^\\s*)(?:layout\\s*\\(([^)]*)\\)\\s*)?(?:out)\\s+([A-Za-z0-9_]+)\\s+([A-Za-z_][A-Za-z0-9_]*)(\\s*\\[[^]]+\\])?\\s*;"
    );
    private static final Pattern GLSL_LAYOUT_LOCATION_PATTERN = Pattern.compile("\\blocation\\s*=\\s*(\\d+)");
    private static final Pattern GLSL_UNIFORM_BLOCK_PATTERN = Pattern.compile("(?m)(?:layout\\s*\\(([^)]*)\\)\\s*)?uniform\\s+(\\w+)\\s*\\{");
    private static final String GLSL_UNIFORM_QUALIFIER_PATTERN =
        "(?:(?:lowp|mediump|highp|readonly|writeonly|coherent|volatile|restrict)\\s+)*";
    private static final Pattern GLSL_STANDALONE_UNIFORM_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\(([^)]*)\\)\\s*)?" + GLSL_UNIFORM_QUALIFIER_PATTERN
            + "uniform\\s+(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*\\])?(?:\\s*=\\s*[^;]+)?\\s*;"
    );
    private static final Pattern GLSL_LAYOUT_SET_PATTERN = Pattern.compile("\\bset\\s*=\\s*(\\d+)\\b");
    private static final Pattern GLSL_LAYOUT_BINDING_PATTERN = Pattern.compile("\\bbinding\\s*=\\s*(\\d+)\\b");
    private static final Pattern GLSL_LOCAL_SIZE_PATTERN = Pattern.compile("\\blocal_size_([xyz])\\s*=\\s*(\\d+)\\b");

    private VulkanShaderVariantPlanner() {
    }

    static RenderPipelineSourcePlan planRenderPipelineSources(
        RenderPipeline renderPipeline,
        String vertexSourceWithDefines,
        String fragmentSourceWithDefines
    ) {
        String reboundVertex = injectExplicitVulkanBindings(renderPipeline, ShaderType.VERTEX, vertexSourceWithDefines);
        String reboundFragment = injectExplicitVulkanBindings(renderPipeline, ShaderType.FRAGMENT, fragmentSourceWithDefines);
        List<String> standaloneUniformDeclarations = collectStandaloneUniformDeclarations(List.of(reboundVertex, reboundFragment));
        int standaloneUniformBindingIndex = standaloneUniformBlockBindingIndex(renderPipeline);
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> vertexInputs =
            collectRenderPipelineVertexInputs(renderPipeline, vertexSourceWithDefines);
        List<ReflectedFragmentOutput> fragmentOutputs = collectFragmentOutputs(stripGlslComments(reboundFragment));
        return new RenderPipelineSourcePlan(
            reboundVertex,
            reboundFragment,
            standaloneUniformDeclarations,
            standaloneUniformBindingIndex,
            vertexInputs,
            fragmentOutputs
        );
    }

    static PipelineDescriptor applyRenderPipelineVariantPlan(
        PipelineDescriptor descriptor,
        RenderPipeline renderPipeline,
        RenderPipelineSourcePlan plan
    ) {
        return withSupplementalRenderPipelineVertexInputState(descriptor, renderPipeline, plan.vertexInputs());
    }

    static LinkedProgramReflectionPlan planLinkedProgramReflection(List<ShaderStageSourceRequest> shaderSources) {
        LinkedHashMap<String, VulkanShaderProgramCoordinator.ReflectedUniform> activeUniforms = new LinkedHashMap<>();
        Set<String> activeUniformNames = new LinkedHashSet<>();
        Set<String> activeUniformBlocks = new LinkedHashSet<>();
        Map<String, VulkanShaderProgramCoordinator.ExplicitDescriptorBinding> explicitBindings = new LinkedHashMap<>();
        List<String> standaloneUniformDeclarations = collectStandaloneUniformDeclarations(
            shaderSources.stream()
                .map(ShaderStageSourceRequest::preparedSource)
                .filter(source -> source != null && !source.isBlank())
                .toList()
        );
        int[] computeWorkGroupSize = new int[]{1, 1, 1};
        List<ReflectedFragmentOutput> fragmentOutputs = new ArrayList<>();

        for (ShaderStageSourceRequest shaderSource : shaderSources) {
            if (shaderSource.preparedSource() == null || shaderSource.preparedSource().isBlank()) {
                continue;
            }

            String normalizedSource = stripGlslComments(shaderSource.preparedSource());
            if (shaderSource.stage() == VulkanicShaderStage.COMPUTE) {
                computeWorkGroupSize = parseComputeWorkGroupSize(normalizedSource);
            } else if (shaderSource.stage() == VulkanicShaderStage.FRAGMENT) {
                fragmentOutputs.addAll(collectFragmentOutputs(normalizedSource));
            }

            Matcher blockMatcher = GLSL_UNIFORM_BLOCK_PATTERN.matcher(normalizedSource);
            while (blockMatcher.find()) {
                String blockName = normalizeIrisUniformBlockName(blockMatcher.group(2));
                activeUniformBlocks.add(blockName);
                parseExplicitDescriptorBinding(blockMatcher.group(1))
                    .ifPresent(binding -> explicitBindings.putIfAbsent(blockName, binding));
            }

            Set<String> activeStandaloneUniformNames =
                ShadercSpirvCompiler.collectActiveStandaloneUniformNames(normalizedSource);
            Set<String> activeStandaloneUniformNamesIncludingOpaque =
                ShadercSpirvCompiler.collectActiveStandaloneUniformNamesIncludingOpaque(normalizedSource);
            if (!activeStandaloneUniformNames.isEmpty()) {
                activeUniformBlocks.add(ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME);
            }

            Matcher uniformMatcher = GLSL_STANDALONE_UNIFORM_PATTERN.matcher(normalizedSource);
            while (uniformMatcher.find()) {
                String uniformTypeName = uniformMatcher.group(2);
                String uniformName = uniformMatcher.group(3);
                int arraySize = uniformMatcher.group(4) == null ? 1 : Integer.parseInt(uniformMatcher.group(4));
                boolean opaqueUniform = isOpaqueStandaloneUniformType(uniformTypeName);
                if (isSamplerStandaloneUniformType(uniformTypeName)
                    && !activeStandaloneUniformNamesIncludingOpaque.contains(uniformName)) {
                    continue;
                }
                if (!opaqueUniform && !activeStandaloneUniformNames.contains(uniformName)) {
                    continue;
                }

                activeUniformNames.add(uniformName);
                parseExplicitDescriptorBinding(uniformMatcher.group(1))
                    .ifPresent(binding -> explicitBindings.putIfAbsent(uniformName, binding));
                activeUniforms.putIfAbsent(
                    uniformName,
                    new VulkanShaderProgramCoordinator.ReflectedUniform(
                        uniformName,
                        arraySize,
                        VulkanicUniformReflectionType.fromGlslTypeName(uniformTypeName)
                            .map(VulkanicUniformReflectionType::toLegacyGlConstant)
                            .orElse(0)
                    )
                );
            }
        }

        List<String> reflectedUniformNames = List.copyOf(activeUniformNames);
        List<VulkanShaderProgramCoordinator.ReflectedUniform> reflectedUniforms = List.copyOf(activeUniforms.values());
        List<String> reflectedUniformBlocks = List.copyOf(activeUniformBlocks);
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> reflectedResourceBindings =
            buildReflectedResourceBindings(reflectedUniformBlocks, reflectedUniforms, explicitBindings);
        return new LinkedProgramReflectionPlan(
            reflectedUniformNames,
            reflectedUniforms,
            reflectedUniformBlocks,
            reflectedResourceBindings,
            standaloneUniformDeclarations,
            computeWorkGroupSize,
            List.copyOf(fragmentOutputs),
            activeUniforms
        );
    }

    static LinkedShaderStagePlan planLinkedShaderStage(
        ShaderStageSourceRequest shaderSource,
        Map<String, Integer> attributeLocationsByName,
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> activeResourceBindings,
        List<String> standaloneUniformDeclarations,
        SourceNormalizer sourceNormalizer
    ) {
        String reboundSource = shaderSource.preparedSource();
        if (shaderSource.stage() == VulkanicShaderStage.VERTEX) {
            for (Map.Entry<String, Integer> entry : attributeLocationsByName.entrySet()) {
                reboundSource = injectExplicitVertexInputLocation(reboundSource, entry.getKey(), entry.getValue());
            }
            reboundSource = injectExplicitRemainingVertexInputLocations(reboundSource);
        }
        reboundSource = injectExplicitReflectedResourceBindings(reboundSource, activeResourceBindings);

        boolean usesGeneratedStandaloneBlock = shaderHasStandaloneUniformBlockMembers(reboundSource);
        int standaloneUniformBindingIndex = usesGeneratedStandaloneBlock
            ? standaloneUniformBlockBindingIndex(activeResourceBindings)
            : -1;
        List<String> standaloneDeclarationsForStage = usesGeneratedStandaloneBlock
            ? standaloneUniformDeclarations
            : null;

        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> vertexInputs = List.of();
        if (shaderSource.stage() == VulkanicShaderStage.VERTEX) {
            String normalizedSource = sourceNormalizer.normalize(
                shaderSource.stage(),
                reboundSource,
                shaderSource.sourceName(),
                standaloneDeclarationsForStage,
                standaloneUniformBindingIndex
            );
            vertexInputs = collectExplicitVertexInputDeclarations(normalizedSource);
        }
        return new LinkedShaderStagePlan(
            shaderSource.shaderId(),
            shaderSource.stage(),
            shaderSource.sourceName(),
            reboundSource,
            usesGeneratedStandaloneBlock,
            standaloneDeclarationsForStage == null ? List.of() : standaloneDeclarationsForStage,
            standaloneUniformBindingIndex,
            vertexInputs
        );
    }

    static PipelineDescriptor withCompatibilityVertexInputState(PipelineDescriptor descriptor) {
        if (descriptor.getVertexInputState() != null) {
            return descriptor;
        }

        PipelineDescriptor.VertexInputState vertexInputState =
            createRenderPipelineVertexInputState(descriptor.getPortableState().vertexFormat(), List.of());
        return vertexInputState == null ? descriptor : descriptor.withVertexInputState(vertexInputState);
    }

    static String injectExplicitVulkanBindings(RenderPipeline renderPipeline, ShaderType shaderType, String shaderSource) {
        String reboundSource = shaderSource;
        if (shaderType == ShaderType.VERTEX) {
            reboundSource = injectExplicitVertexInputLocations(renderPipeline, reboundSource);
            reboundSource = injectExplicitRemainingVertexInputLocations(reboundSource);
        }
        if (isParticlePipeline(renderPipeline)) {
            reboundSource = injectExplicitParticleStageLocations(shaderType, reboundSource);
        }
        int bindingIndex = 0;

        for (String samplerName : renderPipeline.getSamplers()) {
            reboundSource = injectExplicitNamedUniformBinding(reboundSource, samplerName, bindingIndex++);
        }

        for (RenderPipeline.UniformDescription uniform : renderPipeline.getUniforms()) {
            reboundSource = switch (uniform.type()) {
                case UNIFORM_BUFFER -> injectExplicitUniformBlockBinding(reboundSource, uniform.name(), bindingIndex++);
                case TEXEL_BUFFER -> injectExplicitNamedUniformBinding(reboundSource, uniform.name(), bindingIndex++);
            };
        }

        reboundSource = injectExplicitUniformBlockBinding(
            reboundSource,
            ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
            bindingIndex
        );

        return reboundSource;
    }

    private static boolean isParticlePipeline(RenderPipeline renderPipeline) {
        String path = renderPipeline.getLocation().getPath();
        return path.contains("particle");
    }

    private static String injectExplicitParticleStageLocations(ShaderType shaderType, String shaderSource) {
        String reboundSource = shaderSource;
        if (shaderType == ShaderType.VERTEX) {
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "out", "sphericalVertexDistance", 0);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "out", "cylindricalVertexDistance", 1);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "out", "texCoord0", 2);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "out", "vertexColor", 3);
        } else if (shaderType == ShaderType.FRAGMENT) {
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "in", "sphericalVertexDistance", 0);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "in", "cylindricalVertexDistance", 1);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "in", "texCoord0", 2);
            reboundSource = injectExplicitShaderInterfaceLocation(reboundSource, "in", "vertexColor", 3);
        }
        return reboundSource;
    }

    private static String injectExplicitShaderInterfaceLocation(
        String shaderSource,
        String storageQualifier,
        String variableName,
        int location
    ) {
        Pattern layoutPattern = Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*((?:[A-Za-z0-9_]+\\s+)*)(?:"
                + Pattern.quote(storageQualifier)
                + ")\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(variableName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            String reboundLayoutBody = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody).find()
                ? GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody).replaceFirst("location = " + location)
                : layoutBody + ", location = " + location;

            return layoutMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + reboundLayoutBody + ") "
                        + layoutMatcher.group(3)
                        + storageQualifier
                        + " "
                        + layoutMatcher.group(4)
                        + " "
                        + variableName
                        + (layoutMatcher.group(5) == null ? "" : layoutMatcher.group(5))
                        + ";"
                )
            );
        }

        Pattern plainPattern = Pattern.compile(
            "(?m)(^\\s*)((?:[A-Za-z0-9_]+\\s+)*)(?:"
                + Pattern.quote(storageQualifier)
                + ")\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(variableName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(location = " + location + ") "
                    + plainMatcher.group(2)
                    + storageQualifier
                    + " "
                    + plainMatcher.group(3)
                    + " "
                    + variableName
                    + (plainMatcher.group(4) == null ? "" : plainMatcher.group(4))
                    + ";"
            )
        );
    }

    private static PipelineDescriptor withSupplementalRenderPipelineVertexInputState(
        PipelineDescriptor descriptor,
        RenderPipeline renderPipeline,
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> reflectedInputs
    ) {
        PipelineDescriptor.VertexInputState vertexInputState =
            createRenderPipelineVertexInputState(renderPipeline, reflectedInputs);
        return vertexInputState == null ? descriptor : descriptor.withVertexInputState(vertexInputState);
    }

    static List<VulkanShaderProgramCoordinator.ReflectedVertexInput> collectRenderPipelineVertexInputs(
        RenderPipeline renderPipeline,
        String vertexSource
    ) {
        String reboundSource = injectExplicitVulkanBindings(
            renderPipeline,
            ShaderType.VERTEX,
            vertexSource
        );
        String normalizedSource = ShadercSpirvCompiler.normalizeForVulkan(
            VulkanicShaderStage.VERTEX,
            reboundSource,
            renderPipeline.getVertexShader().toString()
        );
        return collectExplicitVertexInputDeclarations(normalizedSource);
    }

    @Nullable
    static PipelineDescriptor.VertexInputState createRenderPipelineVertexInputState(
        RenderPipeline renderPipeline,
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> reflectedInputs
    ) {
        return createRenderPipelineVertexInputState(renderPipeline.getVertexFormat(), reflectedInputs);
    }

    @Nullable
    static PipelineDescriptor.VertexInputState createRenderPipelineVertexInputState(
        VertexFormat vertexFormat,
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> reflectedInputs
    ) {
        List<VertexFormatElement> elements = vertexFormat.getElements();
        List<String> attributeNames = vertexFormat.getElementAttributeNames();
        LinkedHashSet<Integer> providedLocations = new LinkedHashSet<>();
        List<PipelineDescriptor.VertexInputAttribute> attributes = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            VertexFormatElement element = elements.get(i);
            int location = vertexFormat.getShaderAttributeLocation(i);
            attributes.add(new PipelineDescriptor.VertexInputAttribute(
                location,
                0,
                VulkanPipelineFormatClassifier.toPipelineVertexElementFormat(element, attributeNames.get(i)),
                vertexFormat.getOffset(element)
            ));
            providedLocations.add(location);
        }

        boolean needsDefaultBinding = false;
        for (VulkanShaderProgramCoordinator.ReflectedVertexInput input : reflectedInputs) {
            if (providedLocations.contains(input.location())) {
                continue;
            }
            attributes.add(new PipelineDescriptor.VertexInputAttribute(
                input.location(),
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                defaultVertexAttributeFormatForGlslType(input.typeName()),
                0
            ));
            providedLocations.add(input.location());
            needsDefaultBinding = true;
        }
        for (CompatibilityFallbackVertexInput fallback : renderPipelineCompatibilityFallbackInputs(vertexFormat)) {
            if (providedLocations.contains(fallback.location())) {
                continue;
            }
            attributes.add(new PipelineDescriptor.VertexInputAttribute(
                fallback.location(),
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                fallback.format(),
                0
            ));
            providedLocations.add(fallback.location());
            needsDefaultBinding = true;
        }

        if (!needsDefaultBinding) {
            return null;
        }

        List<PipelineDescriptor.VertexInputBinding> bindings = new ArrayList<>(2);
        bindings.add(new PipelineDescriptor.VertexInputBinding(
            0,
            vertexFormat.getVertexSize(),
            PipelineDescriptor.VertexInputRate.VERTEX
        ));
        bindings.add(new PipelineDescriptor.VertexInputBinding(
            VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
            16,
            PipelineDescriptor.VertexInputRate.INSTANCE
        ));
        return new PipelineDescriptor.VertexInputState(bindings, attributes);
    }

    private static List<CompatibilityFallbackVertexInput> renderPipelineCompatibilityFallbackInputs(VertexFormat vertexFormat) {
        List<String> attributeNames = vertexFormat.getElementAttributeNames();
        if (attributeNames.contains("iris_Entity")) {
            return List.of(defaultFloatFallbackVertexInput(8));
        }
        if (attributeNames.contains("a_Position") && attributeNames.contains("a_LightAndData")) {
            return List.of(defaultFloatFallbackVertexInput(8));
        }
        if (attributeNames.contains("Normal")
            && attributeNames.contains("Color")
            && attributeNames.contains("Position")
            && !attributeNames.contains("UV0")) {
            return List.of(
                defaultFloatFallbackVertexInput(3),
                defaultFloatFallbackVertexInput(4),
                defaultFloatFallbackVertexInput(5)
            );
        }
        if (attributeNames.contains("UV0")
            && attributeNames.contains("Position")
            && !attributeNames.contains("Color")) {
            return List.of(new CompatibilityFallbackVertexInput(
                2,
                PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT
            ));
        }
        return List.of();
    }

    private static CompatibilityFallbackVertexInput defaultFloatFallbackVertexInput(int location) {
        return new CompatibilityFallbackVertexInput(
            location,
            PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT
        );
    }

    private record CompatibilityFallbackVertexInput(
        int location,
        PipelineDescriptor.VertexAttributeFormat format
    ) {
    }

    private static String injectExplicitVertexInputLocations(RenderPipeline renderPipeline, String shaderSource) {
        String reboundSource = shaderSource;
        List<String> attributeNames = renderPipeline.getVertexFormat().getElementAttributeNames();
        for (int location = 0; location < attributeNames.size(); location++) {
            int shaderLocation = renderPipeline.getVertexFormat().getShaderAttributeLocation(location);
            for (String attributeName : shaderAttributeAliases(attributeNames.get(location))) {
                reboundSource = injectExplicitVertexInputLocation(reboundSource, attributeName, shaderLocation);
            }
        }
        reboundSource = injectIrisEntityExtensionInputLocations(renderPipeline.getVertexFormat(), reboundSource);
        reboundSource = injectSodiumChunkInputLocations(reboundSource);
        return reboundSource;
    }

    private static List<String> shaderAttributeAliases(String attributeName) {
        return switch (attributeName) {
            case "Position", "Color", "Normal", "UV0", "UV1", "UV2" -> List.of(attributeName, "iris_" + attributeName);
            default -> List.of(attributeName);
        };
    }

    private static String injectIrisEntityExtensionInputLocations(VertexFormat vertexFormat, String shaderSource) {
        boolean isIrisEntityFormat = vertexFormat.getElementAttributeNames().contains("iris_Entity");
        boolean hasEntityExtensionInput = shaderSource.contains("iris_Entity") || isIrisEntityFormat;
        if (!hasEntityExtensionInput) {
            return shaderSource;
        }

        VertexFormat entityFormat = IrisVertexFormats.ENTITY;
        String reboundSource = shaderSource;
        List<String> entityAttributes = entityFormat.getElementAttributeNames();
        for (String attributeName : List.of("iris_Entity", "mc_midTexCoord", "at_tangent")) {
            int attributeIndex = entityAttributes.indexOf(attributeName);
            if (attributeIndex >= 0) {
                reboundSource = injectExplicitVertexInputLocation(
                    reboundSource,
                    attributeName,
                    entityFormat.getShaderAttributeLocation(attributeIndex)
                );
            }
        }
        if (isIrisEntityFormat) {
            reboundSource = injectExplicitVertexInputLocation(reboundSource, "mc_Entity", 8);
            reboundSource = injectExplicitVertexInputLocation(reboundSource, "at_midBlock", 9);
        }
        return reboundSource;
    }

    private static String injectSodiumChunkInputLocations(String shaderSource) {
        if (!shaderSource.contains("a_Position") && !shaderSource.contains("a_LightAndData")) {
            return shaderSource;
        }

        String reboundSource = shaderSource;
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "a_Position", 0);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "a_Color", 1);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "a_TexCoord", 2);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "a_LightAndData", 3);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "iris_Normal", 10);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "mc_Entity", 11);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "mc_midTexCoord", 12);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "at_tangent", 13);
        reboundSource = injectExplicitVertexInputLocation(reboundSource, "at_midBlock", 14);
        return reboundSource;
    }

    static boolean shouldInspectIrisEntityVertexInterface(String shaderSource) {
        return shaderSource.contains("iris_Entity")
            && (shaderSource.contains("iris_Position") || shaderSource.contains("Position"))
            && (shaderSource.contains("mc_Entity") || shaderSource.contains("at_midBlock") || shaderSource.contains("iris_Normal"));
    }

    static String collectVertexInputSummary(String shaderSource) {
        List<String> inputs = new ArrayList<>();
        Matcher matcher = GLSL_VERTEX_INPUT_DECLARATION_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            String layoutBody = matcher.group(2);
            String location = "?";
            if (layoutBody != null) {
                Matcher locationMatcher = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody);
                if (locationMatcher.find()) {
                    location = locationMatcher.group(1);
                }
            }
            inputs.add(matcher.group(5) + ":" + matcher.group(4) + "@loc" + location);
        }
        return inputs.toString();
    }

    static String injectExplicitVertexInputLocation(String shaderSource, String attributeName, int location) {
        Pattern layoutPattern = Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*((?:[A-Za-z0-9_]+\\s+)*)(?:in|attribute)\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(attributeName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            String reboundLayoutBody = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody).find()
                ? GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody).replaceFirst("location = " + location)
                : layoutBody + ", location = " + location;

            return layoutMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + reboundLayoutBody + ") "
                        + layoutMatcher.group(3)
                        + "in "
                        + layoutMatcher.group(4)
                        + " "
                        + attributeName
                        + (layoutMatcher.group(5) == null ? "" : layoutMatcher.group(5))
                        + ";"
                )
            );
        }

        Pattern plainPattern = Pattern.compile(
            "(?m)(^\\s*)((?:[A-Za-z0-9_]+\\s+)*)(?:in|attribute)\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(attributeName)
                + "(\\s*\\[[^]]+\\])?\\s*;"
        );
        Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(location = " + location + ") "
                    + plainMatcher.group(2)
                    + "in "
                    + plainMatcher.group(3)
                    + " "
                    + attributeName
                    + (plainMatcher.group(4) == null ? "" : plainMatcher.group(4))
                    + ";"
            )
        );
    }

    static String injectExplicitRemainingVertexInputLocations(String shaderSource) {
        Set<Integer> occupiedLocations = new java.util.TreeSet<>();

        Matcher occupiedMatcher = GLSL_VERTEX_INPUT_DECLARATION_PATTERN.matcher(shaderSource);
        while (occupiedMatcher.find()) {
            String layoutBody = occupiedMatcher.group(2);
            if (layoutBody == null) {
                continue;
            }

            Matcher locationMatcher = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody);
            if (!locationMatcher.find()) {
                continue;
            }

            int location = Integer.parseInt(locationMatcher.group(1));
            reserveVertexInputLocations(occupiedLocations, location, occupiedMatcher.group(4));
        }

        Matcher declarationMatcher = GLSL_VERTEX_INPUT_DECLARATION_PATTERN.matcher(shaderSource);
        StringBuffer rewritten = new StringBuffer();
        while (declarationMatcher.find()) {
            String layoutBody = declarationMatcher.group(2);
            if (layoutBody != null && GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody).find()) {
                declarationMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(declarationMatcher.group(0)));
                continue;
            }

            int location = nextAvailableVertexInputLocation(occupiedLocations);
            reserveVertexInputLocations(occupiedLocations, location, declarationMatcher.group(4));

            String layout = layoutBody == null
                ? "layout(location = " + location + ") "
                : "layout(" + layoutBody + ", location = " + location + ") ";
            String replacement = declarationMatcher.group(1)
                + layout
                + declarationMatcher.group(3)
                + "in "
                + declarationMatcher.group(4)
                + " "
                + declarationMatcher.group(5)
                + (declarationMatcher.group(6) == null ? "" : declarationMatcher.group(6))
                + ";";
            declarationMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        declarationMatcher.appendTail(rewritten);
        return rewritten.toString();
    }

    static List<VulkanShaderProgramCoordinator.ReflectedVertexInput> collectExplicitVertexInputDeclarations(String shaderSource) {
        LinkedHashMap<Integer, VulkanShaderProgramCoordinator.ReflectedVertexInput> inputsByLocation = new LinkedHashMap<>();
        Matcher matcher = GLSL_VERTEX_INPUT_DECLARATION_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            String layoutBody = matcher.group(2);
            if (layoutBody == null) {
                continue;
            }

            Matcher locationMatcher = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody);
            if (!locationMatcher.find()) {
                continue;
            }

            int location = Integer.parseInt(locationMatcher.group(1));
            String typeName = matcher.group(4);
            int span = vertexInputLocationSpan(typeName);
            for (int i = 0; i < span; i++) {
                inputsByLocation.putIfAbsent(
                    location + i,
                    new VulkanShaderProgramCoordinator.ReflectedVertexInput(location + i, vertexInputColumnType(typeName))
                );
            }
        }

        return List.copyOf(inputsByLocation.values());
    }

    private static List<ReflectedFragmentOutput> collectFragmentOutputs(String shaderSource) {
        LinkedHashMap<Integer, ReflectedFragmentOutput> outputsByLocation = new LinkedHashMap<>();
        Matcher matcher = GLSL_FRAGMENT_OUTPUT_DECLARATION_PATTERN.matcher(shaderSource);
        int implicitLocation = 0;
        while (matcher.find()) {
            String layoutBody = matcher.group(2);
            int location = implicitLocation;
            if (layoutBody != null) {
                Matcher locationMatcher = GLSL_LAYOUT_LOCATION_PATTERN.matcher(layoutBody);
                if (locationMatcher.find()) {
                    location = Integer.parseInt(locationMatcher.group(1));
                }
            }
            String typeName = matcher.group(3);
            String name = matcher.group(4);
            outputsByLocation.putIfAbsent(location, new ReflectedFragmentOutput(location, name, typeName));
            implicitLocation = Math.max(implicitLocation + 1, location + 1);
        }
        return List.copyOf(outputsByLocation.values());
    }

    private static int nextAvailableVertexInputLocation(Set<Integer> occupiedLocations) {
        int location = 0;
        while (occupiedLocations.contains(location)) {
            location++;
        }
        return location;
    }

    private static void reserveVertexInputLocations(Set<Integer> occupiedLocations, int firstLocation, String typeName) {
        int count = vertexInputLocationSpan(typeName);
        for (int i = 0; i < count; i++) {
            occupiedLocations.add(firstLocation + i);
        }
    }

    private static int vertexInputLocationSpan(String typeName) {
        return switch (typeName) {
            case "mat2", "mat2x2", "dmat2", "dmat2x2" -> 2;
            case "mat3", "mat2x3", "mat3x2", "dmat3", "dmat2x3", "dmat3x2" -> 3;
            case "mat4", "mat2x4", "mat3x4", "mat4x2", "mat4x3",
                "dmat4", "dmat2x4", "dmat3x4", "dmat4x2", "dmat4x3" -> 4;
            default -> 1;
        };
    }

    private static String vertexInputColumnType(String typeName) {
        return switch (typeName) {
            case "mat2", "mat2x2", "mat3x2", "mat4x2" -> "vec2";
            case "mat3", "mat2x3", "mat3x3", "mat4x3" -> "vec3";
            case "mat4", "mat2x4", "mat3x4", "mat4x4" -> "vec4";
            case "dmat2", "dmat2x2", "dmat3x2", "dmat4x2" -> "dvec2";
            case "dmat3", "dmat2x3", "dmat3x3", "dmat4x3" -> "dvec3";
            case "dmat4", "dmat2x4", "dmat3x4", "dmat4x4" -> "dvec4";
            default -> typeName;
        };
    }

    private static PipelineDescriptor.VertexAttributeFormat defaultVertexAttributeFormatForGlslType(String typeName) {
        return VulkanDrawExecutionCoordinator.defaultVertexAttributeFormatForGlslType(typeName);
    }

    static String injectExplicitUniformBlockBinding(String shaderSource, String blockName, int bindingIndex) {
        for (String candidateBlockName : uniformBlockBindingAliases(blockName)) {
            Pattern layoutPattern = Pattern.compile(
                "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*uniform\\s+"
                    + Pattern.quote(candidateBlockName)
                    + "\\s*\\{"
            );
            Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
            if (layoutMatcher.find()) {
                String layoutBody = layoutMatcher.group(2);
                if (layoutBody.contains("binding") || layoutBody.contains("set")) {
                    return shaderSource;
                }

                return layoutMatcher.replaceFirst(
                    Matcher.quoteReplacement(
                        layoutMatcher.group(1)
                            + "layout(" + layoutBody + ", set = 0, binding = " + bindingIndex + ") uniform "
                            + candidateBlockName
                            + " {"
                    )
                );
            }

            Pattern plainPattern = Pattern.compile(
                "(?m)(^\\s*)uniform\\s+" + Pattern.quote(candidateBlockName) + "\\s*\\{"
            );
            Matcher plainMatcher = plainPattern.matcher(shaderSource);
            if (!plainMatcher.find()) {
                continue;
            }

            return plainMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    plainMatcher.group(1)
                        + "layout(set = 0, binding = " + bindingIndex + ") uniform "
                        + candidateBlockName
                        + " {"
                )
            );
        }

        return shaderSource;
    }

    private static List<String> uniformBlockBindingAliases(String blockName) {
        String normalizedName = normalizeIrisUniformBlockName(blockName);
        if (normalizedName.equals(blockName) && isIrisWrappedPipelineUniformBlock(blockName)) {
            return List.of(blockName, "iris_" + blockName);
        }
        return List.of(blockName);
    }

    static String normalizeIrisUniformBlockName(String blockName) {
        if (blockName == null || !blockName.startsWith("iris_")) {
            return blockName;
        }

        String unprefixed = blockName.substring("iris_".length());
        return isIrisWrappedPipelineUniformBlock(unprefixed) ? unprefixed : blockName;
    }

    private static boolean isIrisWrappedPipelineUniformBlock(String blockName) {
        return "DynamicTransforms".equals(blockName)
            || "Projection".equals(blockName)
            || "Globals".equals(blockName)
            || "Fog".equals(blockName)
            || "Lighting".equals(blockName);
    }

    static String injectExplicitNamedUniformBinding(String shaderSource, String uniformName, int bindingIndex) {
        Pattern layoutPattern = Pattern.compile(
            "(?m)(^\\s*)layout\\s*\\(([^)]*)\\)\\s*(" + GLSL_UNIFORM_QUALIFIER_PATTERN
                + ")uniform\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(uniformName)
                + "\\s*;"
        );
        Matcher layoutMatcher = layoutPattern.matcher(shaderSource);
        if (layoutMatcher.find()) {
            String layoutBody = layoutMatcher.group(2);
            if (layoutBody.contains("binding") || layoutBody.contains("set")) {
                return shaderSource;
            }

            return layoutMatcher.replaceFirst(
                Matcher.quoteReplacement(
                    layoutMatcher.group(1)
                        + "layout(" + layoutBody + ", set = 0, binding = " + bindingIndex + ") "
                        + layoutMatcher.group(3)
                        + "uniform "
                        + layoutMatcher.group(4)
                        + " "
                        + uniformName
                        + ";"
                )
            );
        }

        Pattern plainPattern = Pattern.compile(
            "(?m)(^\\s*)(" + GLSL_UNIFORM_QUALIFIER_PATTERN + ")uniform\\s+([A-Za-z0-9_]+)\\s+"
                + Pattern.quote(uniformName)
                + "\\s*;"
        );
        Matcher plainMatcher = plainPattern.matcher(shaderSource);
        if (!plainMatcher.find()) {
            return shaderSource;
        }

        return plainMatcher.replaceFirst(
            Matcher.quoteReplacement(
                plainMatcher.group(1)
                    + "layout(set = 0, binding = " + bindingIndex + ") "
                    + plainMatcher.group(2)
                    + "uniform "
                    + plainMatcher.group(3)
                    + " "
                    + uniformName
                    + ";"
            )
        );
    }

    private static String injectExplicitReflectedResourceBindings(
        String shaderSource,
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> activeResourceBindings
    ) {
        String reboundSource = shaderSource;
        for (VulkanShaderProgramCoordinator.ReflectedResourceBinding resourceBinding : activeResourceBindings) {
            if (resourceBinding.type() == PipelineDescriptor.ResourceType.UNIFORM_BUFFER) {
                reboundSource = injectExplicitUniformBlockBinding(
                    reboundSource,
                    resourceBinding.name(),
                    resourceBinding.binding()
                );
            } else if (resourceBinding.type() == PipelineDescriptor.ResourceType.SAMPLER
                || resourceBinding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                || resourceBinding.type() == PipelineDescriptor.ResourceType.STORAGE_IMAGE) {
                reboundSource = injectExplicitNamedUniformBinding(
                    reboundSource,
                    resourceBinding.name(),
                    resourceBinding.binding()
                );
            }
        }

        return reboundSource;
    }

    static List<String> collectStandaloneUniformDeclarations(List<String> shaderSources) {
        return ShadercSpirvCompiler.collectActiveStandaloneUniformDeclarations(shaderSources);
    }

    static boolean shaderHasStandaloneUniformBlockMembers(String shaderSource) {
        if (shaderSource == null || shaderSource.isBlank()) {
            return false;
        }
        return ShadercSpirvCompiler.hasActiveStandaloneUniformBlockMembers(stripGlslComments(shaderSource));
    }

    private static boolean isOpaqueStandaloneUniformType(String uniformTypeName) {
        return uniformTypeName.contains("sampler")
            || uniformTypeName.contains("image")
            || uniformTypeName.equals("atomic_uint");
    }

    private static boolean isSamplerStandaloneUniformType(String uniformTypeName) {
        return uniformTypeName.contains("sampler");
    }

    static int standaloneUniformBlockBindingIndex(RenderPipeline renderPipeline) {
        return renderPipeline.getSamplers().size() + renderPipeline.getUniforms().size();
    }

    private static int standaloneUniformBlockBindingIndex(
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> activeResourceBindings
    ) {
        for (VulkanShaderProgramCoordinator.ReflectedResourceBinding resourceBinding : activeResourceBindings) {
            if (ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME.equals(resourceBinding.name())) {
                return resourceBinding.binding();
            }
        }

        Set<VulkanShaderProgramCoordinator.DescriptorSlot> usedSlots = new LinkedHashSet<>();
        for (VulkanShaderProgramCoordinator.ReflectedResourceBinding resourceBinding : activeResourceBindings) {
            usedSlots.add(new VulkanShaderProgramCoordinator.DescriptorSlot(resourceBinding.set(), resourceBinding.binding()));
        }
        return VulkanShaderProgramCoordinator.nextUnusedBinding(0, usedSlots, 0);
    }

    private static Optional<VulkanShaderProgramCoordinator.ExplicitDescriptorBinding> parseExplicitDescriptorBinding(
        @Nullable String layoutBody
    ) {
        if (layoutBody == null || layoutBody.isBlank()) {
            return Optional.empty();
        }

        Matcher bindingMatcher = GLSL_LAYOUT_BINDING_PATTERN.matcher(layoutBody);
        if (!bindingMatcher.find()) {
            return Optional.empty();
        }

        int set = 0;
        Matcher setMatcher = GLSL_LAYOUT_SET_PATTERN.matcher(layoutBody);
        if (setMatcher.find()) {
            set = Integer.parseInt(setMatcher.group(1));
        }
        int binding = Integer.parseInt(bindingMatcher.group(1));
        return Optional.of(new VulkanShaderProgramCoordinator.ExplicitDescriptorBinding(set, binding));
    }

    private static List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> buildReflectedResourceBindings(
        List<String> activeUniformBlocks,
        List<VulkanShaderProgramCoordinator.ReflectedUniform> activeUniforms,
        Map<String, VulkanShaderProgramCoordinator.ExplicitDescriptorBinding> explicitBindings
    ) {
        List<VulkanShaderProgramCoordinator.ReflectedResourceRequest> requests = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        boolean hasGeneratedStandaloneUniformBlock = false;

        for (String blockName : activeUniformBlocks) {
            if (blockName == null || blockName.isBlank() || blockName.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(blockName)) {
                continue;
            }
            if (ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME.equals(blockName)) {
                hasGeneratedStandaloneUniformBlock = true;
                continue;
            }
            requests.add(new VulkanShaderProgramCoordinator.ReflectedResourceRequest(
                blockName,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER
            ));
        }

        for (VulkanShaderProgramCoordinator.ReflectedUniform uniform : activeUniforms) {
            Optional<VulkanicUniformReflectionType> reflectionType =
                VulkanicUniformReflectionType.fromLegacyGlConstant(uniform.legacyType());
            if (reflectionType.isEmpty() || (!reflectionType.get().isSampler() && !reflectionType.get().isImage())) {
                continue;
            }
            String uniformName = uniform.name();
            if (uniformName == null || uniformName.isBlank() || uniformName.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(uniformName)) {
                continue;
            }
            boolean comparisonSampler = reflectionType.get() == VulkanicUniformReflectionType.SAMPLER_1D_SHADOW
                || reflectionType.get() == VulkanicUniformReflectionType.SAMPLER_2D_SHADOW
                || reflectionType.get() == VulkanicUniformReflectionType.SAMPLER_CUBE_SHADOW;
            requests.add(new VulkanShaderProgramCoordinator.ReflectedResourceRequest(
                uniformName,
                reflectionType.get().isImage()
                    ? PipelineDescriptor.ResourceType.STORAGE_IMAGE
                    : comparisonSampler
                    ? PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                    : PipelineDescriptor.ResourceType.SAMPLER
            ));
        }

        if (hasGeneratedStandaloneUniformBlock) {
            requests.add(new VulkanShaderProgramCoordinator.ReflectedResourceRequest(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER
            ));
        }

        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> bindings = new ArrayList<>(requests.size());
        Set<VulkanShaderProgramCoordinator.DescriptorSlot> usedSlots = new LinkedHashSet<>();
        for (VulkanShaderProgramCoordinator.ExplicitDescriptorBinding explicitBinding : explicitBindings.values()) {
            usedSlots.add(new VulkanShaderProgramCoordinator.DescriptorSlot(explicitBinding.set(), explicitBinding.binding()));
        }
        int nextBinding = 0;

        for (VulkanShaderProgramCoordinator.ReflectedResourceRequest request : requests) {
            VulkanShaderProgramCoordinator.ExplicitDescriptorBinding explicitBinding = explicitBindings.get(request.name());
            int set = explicitBinding != null ? explicitBinding.set() : 0;
            int binding;
            if (explicitBinding != null) {
                binding = explicitBinding.binding();
            } else {
                binding = VulkanShaderProgramCoordinator.nextUnusedBinding(set, usedSlots, nextBinding);
            }

            usedSlots.add(new VulkanShaderProgramCoordinator.DescriptorSlot(set, binding));
            if (set == 0) {
                nextBinding = Math.max(nextBinding, binding + 1);
            }
            bindings.add(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(request.name(), request.type(), set, binding));
        }

        return List.copyOf(bindings);
    }

    private static int[] parseComputeWorkGroupSize(String shaderSource) {
        int[] localSize = new int[]{1, 1, 1};
        Matcher matcher = GLSL_LOCAL_SIZE_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            int value = Math.max(1, Integer.parseInt(matcher.group(2)));
            switch (matcher.group(1)) {
                case "x" -> localSize[0] = value;
                case "y" -> localSize[1] = value;
                case "z" -> localSize[2] = value;
                default -> {
                }
            }
        }
        return localSize;
    }

    private static String stripGlslComments(String shaderSource) {
        return GLSL_LINE_COMMENT_PATTERN.matcher(
            GLSL_BLOCK_COMMENT_PATTERN.matcher(shaderSource).replaceAll("")
        ).replaceAll("");
    }

    record ShaderStageSourceRequest(int shaderId, VulkanicShaderStage stage, String sourceName, String preparedSource) {
    }

    record RenderPipelineSourcePlan(
        String vertexSource,
        String fragmentSource,
        List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex,
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> vertexInputs,
        List<ReflectedFragmentOutput> fragmentOutputs
    ) {
        RenderPipelineSourcePlan {
            standaloneUniformDeclarations = List.copyOf(standaloneUniformDeclarations);
            vertexInputs = List.copyOf(vertexInputs);
            fragmentOutputs = List.copyOf(fragmentOutputs);
        }
    }

    record LinkedProgramReflectionPlan(
        List<String> activeUniformNames,
        List<VulkanShaderProgramCoordinator.ReflectedUniform> activeUniforms,
        List<String> activeUniformBlocks,
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> activeResourceBindings,
        List<String> standaloneUniformDeclarations,
        int[] computeWorkGroupSize,
        List<ReflectedFragmentOutput> fragmentOutputs,
        Map<String, VulkanShaderProgramCoordinator.ReflectedUniform> activeUniformsByName
    ) {
        LinkedProgramReflectionPlan {
            activeUniformNames = List.copyOf(activeUniformNames);
            activeUniforms = List.copyOf(activeUniforms);
            activeUniformBlocks = List.copyOf(activeUniformBlocks);
            activeResourceBindings = List.copyOf(activeResourceBindings);
            standaloneUniformDeclarations = List.copyOf(standaloneUniformDeclarations);
            computeWorkGroupSize = computeWorkGroupSize.clone();
            fragmentOutputs = List.copyOf(fragmentOutputs);
            activeUniformsByName = Map.copyOf(activeUniformsByName);
        }
    }

    record LinkedShaderStagePlan(
        int shaderId,
        VulkanicShaderStage stage,
        String sourceName,
        String reboundSource,
        boolean usesGeneratedStandaloneBlock,
        List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex,
        List<VulkanShaderProgramCoordinator.ReflectedVertexInput> vertexInputs
    ) {
        LinkedShaderStagePlan {
            standaloneUniformDeclarations = List.copyOf(standaloneUniformDeclarations);
            vertexInputs = List.copyOf(vertexInputs);
        }
    }

    record ReflectedFragmentOutput(int location, String name, String typeName) {
    }

    @FunctionalInterface
    interface SourceNormalizer {
        String normalize(
            VulkanicShaderStage stage,
            String source,
            String sourceName,
            @Nullable List<String> standaloneUniformDeclarations,
            int standaloneUniformBindingIndex
        );
    }
}

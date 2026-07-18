package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicSpirvModule;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Normalizes the contract between fragment shader outputs and active Vulkan
 * render targets. This planner owns interpretation only: it does not create
 * Vulkan render passes, pipelines, framebuffers, shader modules, descriptors,
 * barriers, or native handles.
 */
final class VulkanFragmentRenderTargetInterfacePlanner {
    private static final int GL_NONE = 0;
    private static final int GL_BACK = 0x0405;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;

    private VulkanFragmentRenderTargetInterfacePlanner() {
    }

    static FragmentOutputInterface fragmentOutputInterface(List<VulkanicSpirvModule> modules) {
        Objects.requireNonNull(modules, "modules");
        Map<Integer, FragmentOutputDeclaration> outputsByLocation = new LinkedHashMap<>();
        for (VulkanicSpirvModule module : modules) {
            if (module.stage() != net.vulkanic.VulkanicShaderStage.FRAGMENT) {
                continue;
            }
            for (VulkanicSpirvModule.FragmentOutput output : module.fragmentOutputs()) {
                outputsByLocation.putIfAbsent(
                    output.location(),
                    new FragmentOutputDeclaration(
                        output.location(),
                        output.name(),
                        output.typeName(),
                        numericClassForGlslType(output.typeName()),
                        sourceKind(output.name()),
                        module.sourceName()
                    )
                );
            }
        }
        return new FragmentOutputInterface(outputsByLocation.values().stream()
            .sorted(Comparator.comparingInt(FragmentOutputDeclaration::location))
            .toList());
    }

    static RenderTargetInterface renderTargetInterface(VulkanRenderPassCompatibilityKey compatibilityKey) {
        Objects.requireNonNull(compatibilityKey, "compatibilityKey");
        List<ColorAttachmentInterface> colors = new ArrayList<>(compatibilityKey.colorFormats().size());
        List<Integer> drawBuffers = new ArrayList<>(compatibilityKey.colorFormats().size());
        for (int colorIndex = 0; colorIndex < compatibilityKey.colorFormats().size(); colorIndex++) {
            int drawBuffer = GL_COLOR_ATTACHMENT0 + colorIndex;
            drawBuffers.add(drawBuffer);
            colors.add(new ColorAttachmentInterface(
                colorIndex,
                drawBuffer,
                compatibilityKey.colorFormats().get(colorIndex),
                numericClassForVkFormat(compatibilityKey.colorFormats().get(colorIndex)),
                Optional.empty(),
                Optional.empty(),
                compatibilityKey.feedbackLoop()
            ));
        }
        return new RenderTargetInterface(
            compatibilityKey.dependencyProfile().name(),
            colors,
            compatibilityKey.depthFormat(),
            compatibilityKey.hasDepthAttachment(),
            compatibilityKey.hasStencilAttachment(),
            compatibilityKey.feedbackLoop(),
            DrawBufferRoutingSnapshot.fromDrawBuffers(drawBuffers.stream().mapToInt(Integer::intValue).toArray())
        );
    }

    static RenderTargetInterface renderTargetInterface(
        String targetKind,
        List<ColorAttachmentInterface> colorAttachments,
        int depthFormat,
        boolean hasDepthAttachment,
        boolean hasStencilAttachment,
        boolean feedbackLoop,
        DrawBufferRoutingSnapshot drawBuffers
    ) {
        return new RenderTargetInterface(
            targetKind,
            colorAttachments,
            depthFormat,
            hasDepthAttachment,
            hasStencilAttachment,
            feedbackLoop,
            drawBuffers
        );
    }

    static FragmentRenderTargetCompatibilityResult plan(
        FragmentOutputInterface fragmentOutputs,
        RenderTargetInterface renderTarget
    ) {
        Objects.requireNonNull(fragmentOutputs, "fragmentOutputs");
        Objects.requireNonNull(renderTarget, "renderTarget");
        List<OutputToAttachmentMapping> mappings = new ArrayList<>();
        List<CompatibilityDiagnostic> diagnostics = new ArrayList<>();
        boolean compatible = true;

        for (FragmentOutputDeclaration output : fragmentOutputs.outputs()) {
            OptionalInt routedAttachment = renderTarget.drawBuffers().attachmentIndexForOutputLocation(output.location());
            if (routedAttachment.isEmpty()) {
                mappings.add(OutputToAttachmentMapping.unmapped(output, "draw-buffer-none-or-missing"));
                diagnostics.add(CompatibilityDiagnostic.warning(
                    "ShaderOutputNotConsumed",
                    output.location(),
                    output.name(),
                    "Fragment output is not routed to an active color attachment."
                ));
                continue;
            }

            ColorAttachmentInterface attachment = renderTarget.colorAttachmentByIndex(routedAttachment.getAsInt());
            if (attachment == null) {
                mappings.add(OutputToAttachmentMapping.unmapped(output, "framebuffer-missing-attachment"));
                diagnostics.add(CompatibilityDiagnostic.error(
                    "MissingColorAttachment",
                    output.location(),
                    output.name(),
                    "Draw buffer routes output to missing color attachment " + routedAttachment.getAsInt() + "."
                ));
                compatible = false;
                continue;
            }

            NumericClass outputClass = output.numericClass();
            NumericClass attachmentClass = attachment.numericClass();
            boolean numericCompatible = numericClassesCompatible(outputClass, attachmentClass);
            mappings.add(new OutputToAttachmentMapping(
                output,
                Optional.of(attachment),
                numericCompatible,
                numericCompatible ? "" : "numeric-class-mismatch"
            ));
            if (!numericCompatible) {
                diagnostics.add(CompatibilityDiagnostic.error(
                    "ShaderOutputAttachmentTypeMismatch",
                    output.location(),
                    output.name(),
                    "Fragment output " + outputClass + " cannot write attachment " + attachment.colorIndex()
                        + " with numeric class " + attachmentClass + "."
                ));
                compatible = false;
            }
        }

        BlendAttachmentPlan blendPlan = new BlendAttachmentPlan(renderTarget.colorAttachments().stream()
            .map(attachment -> new BlendAttachmentRequirement(
                attachment.colorIndex(),
                attachment.drawBuffer(),
                attachment.format(),
                attachment.numericClass()
            ))
            .toList());
        InterfaceCompatibilityKey compatibilityKey = new InterfaceCompatibilityKey(
            fragmentOutputs.cacheKey(),
            renderTarget.cacheKey(),
            mappings.stream().map(OutputToAttachmentMapping::cacheKey).toList(),
            blendPlan.cacheKey()
        );
        FragmentVariantRequirement variantRequirement = new FragmentVariantRequirement(
            fragmentOutputs.cacheKey(),
            renderTarget.drawBuffers(),
            renderTarget.colorAttachments().size()
        );
        return new FragmentRenderTargetCompatibilityResult(
            compatible,
            fragmentOutputs,
            renderTarget,
            mappings,
            blendPlan,
            variantRequirement,
            compatibilityKey,
            diagnostics
        );
    }

    static FragmentRenderTargetCompatibilityResult plan(
        List<VulkanicSpirvModule> modules,
        VulkanRenderPassCompatibilityKey compatibilityKey
    ) {
        return plan(fragmentOutputInterface(modules), renderTargetInterface(compatibilityKey));
    }

    private static boolean numericClassesCompatible(NumericClass output, NumericClass attachment) {
        return output == NumericClass.UNKNOWN
            || attachment == NumericClass.UNKNOWN
            || output == attachment;
    }

    private static FragmentOutputSourceKind sourceKind(String name) {
        if (name.startsWith("gl_FragData[")) {
            return FragmentOutputSourceKind.LEGACY_FRAG_DATA;
        }
        if ("gl_FragColor".equals(name)) {
            return FragmentOutputSourceKind.LEGACY_FRAG_COLOR;
        }
        if (name.startsWith("iris_FragData")) {
            return FragmentOutputSourceKind.LEGACY_FRAG_DATA;
        }
        return FragmentOutputSourceKind.EXPLICIT_OUT;
    }

    private static NumericClass numericClassForGlslType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return NumericClass.UNKNOWN;
        }
        String normalized = typeName.trim();
        if (normalized.startsWith("u")) {
            return NumericClass.UINT;
        }
        if (normalized.startsWith("i") || normalized.startsWith("b")) {
            return NumericClass.INT;
        }
        if (normalized.startsWith("float") || normalized.startsWith("vec") || normalized.startsWith("mat")
            || normalized.startsWith("double") || normalized.startsWith("dvec")) {
            return NumericClass.FLOAT;
        }
        return NumericClass.UNKNOWN;
    }

    static NumericClass numericClassForVkFormat(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_R8_UINT,
                 VK10.VK_FORMAT_R8G8_UINT,
                 VK10.VK_FORMAT_R8G8B8A8_UINT,
                 VK10.VK_FORMAT_R16_UINT,
                 VK10.VK_FORMAT_R16G16_UINT,
                 VK10.VK_FORMAT_R16G16B16A16_UINT,
                 VK10.VK_FORMAT_R32_UINT,
                 VK10.VK_FORMAT_R32G32_UINT,
                 VK10.VK_FORMAT_R32G32B32_UINT,
                 VK10.VK_FORMAT_R32G32B32A32_UINT -> NumericClass.UINT;
            case VK10.VK_FORMAT_R8_SINT,
                 VK10.VK_FORMAT_R8G8_SINT,
                 VK10.VK_FORMAT_R8G8B8A8_SINT,
                 VK10.VK_FORMAT_R16_SINT,
                 VK10.VK_FORMAT_R16G16_SINT,
                 VK10.VK_FORMAT_R16G16B16A16_SINT,
                 VK10.VK_FORMAT_R32_SINT,
                 VK10.VK_FORMAT_R32G32_SINT,
                 VK10.VK_FORMAT_R32G32B32_SINT,
                 VK10.VK_FORMAT_R32G32B32A32_SINT -> NumericClass.INT;
            case VK10.VK_FORMAT_UNDEFINED -> NumericClass.UNKNOWN;
            default -> NumericClass.FLOAT;
        };
    }

    enum NumericClass {
        FLOAT,
        INT,
        UINT,
        UNKNOWN
    }

    enum FragmentOutputSourceKind {
        EXPLICIT_OUT,
        LEGACY_FRAG_COLOR,
        LEGACY_FRAG_DATA
    }

    record FragmentOutputDeclaration(
        int location,
        String name,
        String typeName,
        NumericClass numericClass,
        FragmentOutputSourceKind sourceKind,
        String sourceName
    ) {
        FragmentOutputDeclaration {
            if (location < 0) {
                throw new IllegalArgumentException("fragment output location must be non-negative");
            }
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(typeName, "typeName");
            Objects.requireNonNull(numericClass, "numericClass");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(sourceName, "sourceName");
        }
    }

    record FragmentOutputInterface(List<FragmentOutputDeclaration> outputs) {
        FragmentOutputInterface {
            outputs = outputs.stream()
                .sorted(Comparator.comparingInt(FragmentOutputDeclaration::location))
                .toList();
        }

        FragmentOutputInterfaceKey cacheKey() {
            return new FragmentOutputInterfaceKey(outputs.stream()
                .map(output -> new FragmentOutputKey(
                    output.location(),
                    output.name(),
                    output.typeName(),
                    output.numericClass(),
                    output.sourceKind()
                ))
                .toList());
        }
    }

    record DrawBufferRoutingSnapshot(List<DrawBufferRoute> routes) {
        DrawBufferRoutingSnapshot {
            routes = List.copyOf(routes);
        }

        static DrawBufferRoutingSnapshot fromDrawBuffers(int[] drawBuffers) {
            Objects.requireNonNull(drawBuffers, "drawBuffers");
            List<DrawBufferRoute> routes = new ArrayList<>(drawBuffers.length);
            for (int outputLocation = 0; outputLocation < drawBuffers.length; outputLocation++) {
                int drawBuffer = drawBuffers[outputLocation];
                routes.add(new DrawBufferRoute(
                    outputLocation,
                    drawBuffer,
                    attachmentIndexForDrawBuffer(drawBuffer)
                ));
            }
            return new DrawBufferRoutingSnapshot(routes);
        }

        OptionalInt attachmentIndexForOutputLocation(int outputLocation) {
            for (DrawBufferRoute route : routes) {
                if (route.outputLocation() == outputLocation) {
                    return route.colorAttachmentIndex();
                }
            }
            return OptionalInt.empty();
        }

        private static OptionalInt attachmentIndexForDrawBuffer(int drawBuffer) {
            if (drawBuffer == GL_NONE) {
                return OptionalInt.empty();
            }
            if (drawBuffer == GL_BACK) {
                return OptionalInt.of(0);
            }
            if (drawBuffer >= GL_COLOR_ATTACHMENT0) {
                return OptionalInt.of(drawBuffer - GL_COLOR_ATTACHMENT0);
            }
            return OptionalInt.empty();
        }
    }

    record DrawBufferRoute(int outputLocation, int drawBuffer, OptionalInt colorAttachmentIndex) {
        DrawBufferRoute {
            if (outputLocation < 0) {
                throw new IllegalArgumentException("outputLocation must be non-negative");
            }
            Objects.requireNonNull(colorAttachmentIndex, "colorAttachmentIndex");
        }
    }

    record ColorAttachmentInterface(
        int colorIndex,
        int drawBuffer,
        int format,
        NumericClass numericClass,
        Optional<VulkanicRenderPassDescriptor.LoadOp> loadOp,
        Optional<VulkanicRenderPassDescriptor.StoreOp> storeOp,
        boolean feedbackLoop
    ) {
        ColorAttachmentInterface {
            if (colorIndex < 0) {
                throw new IllegalArgumentException("colorIndex must be non-negative");
            }
            if (format == VK10.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("color attachment format must be defined");
            }
            Objects.requireNonNull(numericClass, "numericClass");
            loadOp = Objects.requireNonNull(loadOp, "loadOp");
            storeOp = Objects.requireNonNull(storeOp, "storeOp");
        }
    }

    record RenderTargetInterface(
        String targetKind,
        List<ColorAttachmentInterface> colorAttachments,
        int depthFormat,
        boolean hasDepthAttachment,
        boolean hasStencilAttachment,
        boolean feedbackLoop,
        DrawBufferRoutingSnapshot drawBuffers
    ) {
        RenderTargetInterface {
            Objects.requireNonNull(targetKind, "targetKind");
            colorAttachments = colorAttachments.stream()
                .sorted(Comparator.comparingInt(ColorAttachmentInterface::colorIndex))
                .toList();
            Objects.requireNonNull(drawBuffers, "drawBuffers");
            if (!hasDepthAttachment && depthFormat != VK10.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("depthFormat must be undefined without a depth attachment");
            }
        }

        @Nullable
        ColorAttachmentInterface colorAttachmentByIndex(int colorIndex) {
            for (ColorAttachmentInterface attachment : colorAttachments) {
                if (attachment.colorIndex() == colorIndex) {
                    return attachment;
                }
            }
            return null;
        }

        RenderTargetInterfaceKey cacheKey() {
            return new RenderTargetInterfaceKey(
                targetKind,
                colorAttachments.stream()
                    .map(attachment -> new ColorAttachmentKey(
                    attachment.colorIndex(),
                    attachment.drawBuffer(),
                    attachment.format(),
                    attachment.numericClass(),
                    attachment.loadOp(),
                    attachment.storeOp(),
                    attachment.feedbackLoop()
                ))
                .toList(),
                depthFormat,
                hasDepthAttachment,
                hasStencilAttachment,
                feedbackLoop,
                drawBuffers
            );
        }
    }

    record OutputToAttachmentMapping(
        FragmentOutputDeclaration output,
        Optional<ColorAttachmentInterface> attachment,
        boolean numericCompatible,
        String note
    ) {
        OutputToAttachmentMapping {
            Objects.requireNonNull(output, "output");
            attachment = Objects.requireNonNull(attachment, "attachment");
            Objects.requireNonNull(note, "note");
        }

        static OutputToAttachmentMapping unmapped(FragmentOutputDeclaration output, String note) {
            return new OutputToAttachmentMapping(output, Optional.empty(), true, note);
        }

        OutputAttachmentMappingKey cacheKey() {
            return new OutputAttachmentMappingKey(
                output.location(),
                attachment.map(ColorAttachmentInterface::colorIndex).orElse(-1),
                attachment.map(ColorAttachmentInterface::format).orElse(VK10.VK_FORMAT_UNDEFINED),
                numericCompatible,
                note
            );
        }
    }

    record BlendAttachmentPlan(List<BlendAttachmentRequirement> attachments) {
        BlendAttachmentPlan {
            attachments = List.copyOf(attachments);
        }

        BlendAttachmentPlanKey cacheKey() {
            return new BlendAttachmentPlanKey(attachments);
        }
    }

    record BlendAttachmentRequirement(
        int colorIndex,
        int drawBuffer,
        int format,
        NumericClass numericClass
    ) {
    }

    record FragmentRenderTargetCompatibilityResult(
        boolean compatible,
        FragmentOutputInterface fragmentOutputs,
        RenderTargetInterface renderTarget,
        List<OutputToAttachmentMapping> mappings,
        BlendAttachmentPlan blendPlan,
        FragmentVariantRequirement variantRequirement,
        InterfaceCompatibilityKey compatibilityKey,
        List<CompatibilityDiagnostic> diagnostics
    ) {
        FragmentRenderTargetCompatibilityResult {
            Objects.requireNonNull(fragmentOutputs, "fragmentOutputs");
            Objects.requireNonNull(renderTarget, "renderTarget");
            mappings = List.copyOf(mappings);
            Objects.requireNonNull(blendPlan, "blendPlan");
            Objects.requireNonNull(variantRequirement, "variantRequirement");
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    record FragmentVariantRequirement(
        FragmentOutputInterfaceKey fragmentOutputInterface,
        DrawBufferRoutingSnapshot drawBuffers,
        int colorAttachmentCount
    ) {
    }

    record CompatibilityDiagnostic(
        Severity severity,
        String code,
        int outputLocation,
        String outputName,
        String message
    ) {
        static CompatibilityDiagnostic warning(String code, int outputLocation, String outputName, String message) {
            return new CompatibilityDiagnostic(Severity.WARNING, code, outputLocation, outputName, message);
        }

        static CompatibilityDiagnostic error(String code, int outputLocation, String outputName, String message) {
            return new CompatibilityDiagnostic(Severity.ERROR, code, outputLocation, outputName, message);
        }
    }

    enum Severity {
        WARNING,
        ERROR
    }

    record InterfaceCompatibilityKey(
        FragmentOutputInterfaceKey fragmentOutputs,
        RenderTargetInterfaceKey renderTarget,
        List<OutputAttachmentMappingKey> outputMappings,
        BlendAttachmentPlanKey blendAttachments
    ) {
        InterfaceCompatibilityKey {
            outputMappings = List.copyOf(outputMappings);
            Objects.requireNonNull(fragmentOutputs, "fragmentOutputs");
            Objects.requireNonNull(renderTarget, "renderTarget");
            Objects.requireNonNull(blendAttachments, "blendAttachments");
        }
    }

    record FragmentOutputInterfaceKey(List<FragmentOutputKey> outputs) {
        FragmentOutputInterfaceKey {
            outputs = List.copyOf(outputs);
        }
    }

    record FragmentOutputKey(
        int location,
        String name,
        String typeName,
        NumericClass numericClass,
        FragmentOutputSourceKind sourceKind
    ) {
    }

    record RenderTargetInterfaceKey(
        String targetKind,
        List<ColorAttachmentKey> colorAttachments,
        int depthFormat,
        boolean hasDepthAttachment,
        boolean hasStencilAttachment,
        boolean feedbackLoop,
        DrawBufferRoutingSnapshot drawBuffers
    ) {
        RenderTargetInterfaceKey {
            colorAttachments = List.copyOf(colorAttachments);
        }
    }

    record ColorAttachmentKey(
        int colorIndex,
        int drawBuffer,
        int format,
        NumericClass numericClass,
        Optional<VulkanicRenderPassDescriptor.LoadOp> loadOp,
        Optional<VulkanicRenderPassDescriptor.StoreOp> storeOp,
        boolean feedbackLoop
    ) {
    }

    record OutputAttachmentMappingKey(
        int outputLocation,
        int colorIndex,
        int format,
        boolean numericCompatible,
        String note
    ) {
    }

    record BlendAttachmentPlanKey(List<BlendAttachmentRequirement> attachments) {
        BlendAttachmentPlanKey {
            attachments = List.copyOf(attachments);
        }
    }
}

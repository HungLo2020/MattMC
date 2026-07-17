package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.GpuTexture;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-internal descriptor/resource binding policy planner.
 *
 * <p>This class decides what a descriptor binding should reference, but does
 * not create Vulkan resources, emit barriers, allocate transient buffers, or
 * materialize {@code VkWriteDescriptorSet} structures.</p>
 */
final class VulkanDescriptorBindingPlanner {
    private static final int GL_TEXTURE_COMPARE_FUNC = 0x884D;

    DescriptorBindingPlan plan(PlanRequest request) {
        Objects.requireNonNull(request, "request");
        List<PipelineDescriptor.ResourceBinding> layoutBindings = normalizeDescriptorLayoutBindings(request.layoutBindings());
        List<DescriptorPlanEntry> entries = new ArrayList<>(layoutBindings.size());
        Set<Integer> storageImageTextureIds = collectStorageImageTextureIds(layoutBindings, request.bindings());
        boolean cacheable = true;

        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, request.bindings());
            DescriptorPlanEntry entry = switch (effectiveBinding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> planSamplerBinding(
                    effectiveBinding,
                    request,
                    storageImageTextureIds
                );
                case UNIFORM_BUFFER -> {
                    UniformBufferEntry uniformEntry = planUniformBufferBinding(effectiveBinding, request);
                    if (uniformEntry.requiresTransientCopy()) {
                        cacheable = false;
                    }
                    yield uniformEntry;
                }
                case STORAGE_IMAGE -> planStorageImageBinding(effectiveBinding, request);
                case TEXEL_BUFFER -> planTexelBufferBinding(effectiveBinding, request);
            };
            entries.add(entry);
        }

        return new DescriptorBindingPlan(entries, cacheable);
    }

    List<PipelineDescriptor.ResourceBinding> normalizeDescriptorLayoutBindings(
        List<PipelineDescriptor.ResourceBinding> bindings
    ) {
        return List.copyOf(bindings);
    }

    PipelineDescriptor.ResourceBinding withEffectiveBindingType(
        PipelineDescriptor.ResourceBinding binding,
        @Nullable PipelineResourceBindings bindings
    ) {
        return binding;
    }

    Set<Integer> collectStorageImageTextureIds(
        List<PipelineDescriptor.ResourceBinding> layoutBindings,
        PipelineResourceBindings bindings
    ) {
        Set<Integer> storageImageTextureIds = new HashSet<>();
        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, bindings);
            if (effectiveBinding.type() != PipelineDescriptor.ResourceType.STORAGE_IMAGE) {
                continue;
            }
            PipelineResourceBindings.StorageImageBinding imageBinding =
                bindings.getStorageImageBindingOrNull(effectiveBinding.name());
            if (imageBinding != null && imageBinding.texture() > 0) {
                storageImageTextureIds.add(imageBinding.texture());
            }
        }
        return storageImageTextureIds;
    }

    private SamplerEntry planSamplerBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request,
        Set<Integer> storageImageTextureIds
    ) {
        PipelineResourceBindings.SamplerBinding samplerBinding = request.bindings().getSamplerBindingOrNull(binding.name());
        if (samplerBinding == null) {
            throw new DescriptorValidationException("Missing sampler binding for '" + binding.name() + "'");
        }

        if (!(samplerBinding.textureView() instanceof VulkanTextureView vulkanTextureView)) {
            throw new IllegalArgumentException(
                "Sampler binding '" + binding.name() + "' requires VulkanTextureView on Vulkan backend");
        }

        VulkanTextureView resolvedTextureView = vulkanTextureView;
        LegacyTextureStorageSnapshot sampledTexture = request.textureLookup().snapshotForView(resolvedTextureView);

        boolean wantsComparisonSampler = binding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER;
        boolean canUseComparisonSampler = hasDepthAspect(sampledTexture);
        String depthFallbackBindingName = null;
        if (wantsComparisonSampler && !canUseComparisonSampler) {
            for (String fallbackBindingName : depthFallbackBindings(binding.name())) {
                PipelineResourceBindings.SamplerBinding fallbackSamplerBinding =
                    request.bindings().getSamplerBindingOrNull(fallbackBindingName);
                if (fallbackSamplerBinding == null
                    || !(fallbackSamplerBinding.textureView() instanceof VulkanTextureView fallbackView)) {
                    continue;
                }
                LegacyTextureStorageSnapshot fallbackTexture = request.textureLookup().snapshotForView(fallbackView);
                if (!hasDepthAspect(fallbackTexture)) {
                    continue;
                }
                resolvedTextureView = fallbackView;
                sampledTexture = fallbackTexture;
                canUseComparisonSampler = true;
                depthFallbackBindingName = fallbackBindingName;
                break;
            }
        }

        long requestedImageViewHandle = resolvedTextureView.getVkImageViewHandle();
        long descriptorImageViewHandle = requestedImageViewHandle;
        int descriptorBaseMipLevel = Math.max(0, resolvedTextureView.getBaseMipLevel());
        int descriptorMipLevelCount = Math.max(1, resolvedTextureView.getMipLevelCount());
        boolean remappedToDefaultView = false;

        if (sampledTexture != null
            && sampledTexture.defaultViewHandle() != VK10.VK_NULL_HANDLE
            && resolvedTextureView.getBaseMipLevel() == 0
            && resolvedTextureView.getMipLevelCount() >= sampledTexture.mipLevels()) {
            descriptorImageViewHandle = sampledTexture.defaultViewHandle();
            descriptorBaseMipLevel = 0;
            descriptorMipLevelCount = Math.max(1, sampledTexture.mipLevels());
            remappedToDefaultView = requestedImageViewHandle != descriptorImageViewHandle;
        }

        boolean requiresDepthOnlyView = sampledTexture != null
            && sampledTexture.hasDepthAspect()
            && sampledTexture.hasStencilAspect();
        boolean explicitlyStorageImageBound = sampledTexture != null
            && storageImageTextureIds.contains(sampledTexture.id());
        boolean storageImageCompatibleSample = explicitlyStorageImageBound
            || isStorageImageLayoutCompatibleSampler(
                sampledTexture,
                descriptorBaseMipLevel,
                descriptorMipLevelCount,
                request.layoutLookup()
            );
        DescriptorTransitionRequirement transitionRequirement = DescriptorTransitionRequirement.NONE;
        if (sampledTexture != null) {
            transitionRequirement = explicitlyStorageImageBound
                ? DescriptorTransitionRequirement.STORAGE_IMAGE
                : storageImageCompatibleSample
                ? DescriptorTransitionRequirement.NONE
                : DescriptorTransitionRequirement.SAMPLE;
        }

        if (wantsComparisonSampler && !canUseComparisonSampler) {
            request.events().comparisonSamplerDowngraded(binding.name(), sampledTexture);
        } else if (depthFallbackBindingName != null) {
            request.events().comparisonSamplerRebound(binding.name(), depthFallbackBindingName, sampledTexture);
        }

        boolean useComparisonSampler = wantsComparisonSampler && canUseComparisonSampler;
        VulkanDescriptorSamplerKey samplerKey = descriptorSamplerKey(
            resolvedTextureView,
            sampledTexture,
            samplerBinding.samplerObject(),
            samplerBinding.textureUnit(),
            binding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                ? useComparisonSampler
                : binding.type() == PipelineDescriptor.ResourceType.SAMPLER ? Boolean.FALSE
                : null,
            request.samplerStateLookup()
        );
        int descriptorImageLayout = descriptorImageLayoutFor(
            sampledTexture,
            storageImageCompatibleSample,
            request.renderState()
        );

        return new SamplerEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            resolvedTextureView,
            sampledTexture,
            samplerBinding.textureUnit(),
            samplerBinding.samplerObject(),
            samplerKey,
            requestedImageViewHandle,
            descriptorImageViewHandle,
            descriptorBaseMipLevel,
            descriptorMipLevelCount,
            requiresDepthOnlyView,
            remappedToDefaultView,
            storageImageCompatibleSample,
            transitionRequirement,
            descriptorImageLayout,
            request.sodiumChunkDescriptor() && shouldLogSodiumDescriptorBinding(binding.name()),
            request.particleDescriptor()
                && ("Sampler0".contentEquals(binding.name()) || "Sampler2".contentEquals(binding.name())),
            request.pipelineLocation(),
            request.pipelineHandle()
        );
    }

    private UniformBufferEntry planUniformBufferBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request
    ) {
        VulkanicBufferSlice slice = request.bindings().getUniformBufferBindingOrNull(binding.name());
        if (slice == null) {
            throw new DescriptorValidationException("Missing uniform-buffer binding for '" + binding.name() + "'");
        }

        if (!(slice.buffer() instanceof VulkanBuffer vulkanBuffer)) {
            throw new IllegalArgumentException(
                "Uniform-buffer binding '" + binding.name() + "' requires VulkanBuffer on Vulkan backend");
        }

        boolean requiresTransientUniformCopy =
            (vulkanBuffer.usage() & VulkanicBuffer.USAGE_UNIFORM) == 0
                || (slice.offset() % request.minUniformBufferOffsetAlignment()) != 0;

        return new UniformBufferEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            vulkanBuffer,
            slice,
            vulkanBuffer.getVkBufferHandle(),
            slice.offset(),
            slice.length(),
            requiresTransientUniformCopy
        );
    }

    private StorageImageEntry planStorageImageBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request
    ) {
        PipelineResourceBindings.StorageImageBinding imageBinding =
            request.bindings().getStorageImageBindingOrNull(binding.name());
        if (imageBinding == null) {
            throw new DescriptorValidationException("Missing storage-image binding for '" + binding.name() + "'");
        }

        LegacyTextureStorageSnapshot texture = request.textureLookup().snapshotForTexture(imageBinding.texture());
        if (texture == null
            || texture.imageHandle() == VK10.VK_NULL_HANDLE
            || texture.defaultViewHandle() == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException(
                "Storage-image binding '" + binding.name() + "' on image unit "
                    + imageBinding.imageUnit()
                    + " references texture "
                    + imageBinding.texture()
                    + " before Vulkan image storage is available"
            );
        }

        if (texture.aspectMask() != VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
            throw new IllegalStateException(
                "Storage-image binding '" + binding.name() + "' references non-color texture "
                    + imageBinding.texture()
                    + " aspectMask=0x"
                    + Integer.toHexString(texture.aspectMask())
            );
        }

        int mipLevel = Math.max(0, imageBinding.level());
        if (mipLevel >= Math.max(1, texture.mipLevels())) {
            throw new IllegalStateException(
                "Storage-image binding '" + binding.name() + "' requested mip level "
                    + mipLevel
                    + " but texture "
                    + imageBinding.texture()
                    + " only has "
                    + texture.mipLevels()
                    + " levels"
            );
        }

        return new StorageImageEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            imageBinding,
            texture,
            mipLevel,
            texture.defaultViewHandle(),
            VK10.VK_IMAGE_LAYOUT_GENERAL
        );
    }

    private TexelBufferEntry planTexelBufferBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request
    ) {
        PipelineResourceBindings.TexelBufferBinding texelBinding =
            request.bindings().getTexelBufferBindingOrNull(binding.name());
        if (texelBinding == null) {
            throw new DescriptorValidationException("Missing texel-buffer binding for '" + binding.name() + "'");
        }

        int unit = texelBinding.textureUnit();
        TextureBindingSnapshot textureBinding = request.textureBindingLookup().bindingSnapshot(unit);
        int textureId = textureBinding != null ? textureBinding.texture2D() : 0;
        if (textureId == 0) {
            throw new IllegalStateException(
                "Texel-buffer binding '" + binding.name() + "' requires a texture-buffer object bound on unit "
                    + unit + " before descriptor binding");
        }

        LegacyTexelBufferBinding legacyTexelBinding = request.texelBufferLookup().texelBufferBinding(textureId);
        if (legacyTexelBinding == null
            || legacyTexelBinding.vkBufferViewHandle == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException(
                "Texel-buffer binding '" + binding.name() + "' on unit "
                    + unit
                    + " has no uploaded buffer-view. Ensure bindTextureBufferData/texBuffer was called");
        }

        return new TexelBufferEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            unit,
            textureId,
            legacyTexelBinding.internalFormat,
            legacyTexelBinding.legacyBufferId,
            legacyTexelBinding.vkBufferViewHandle
        );
    }

    private VulkanDescriptorSamplerKey descriptorSamplerKey(
        VulkanTextureView textureView,
        @Nullable LegacyTextureStorageSnapshot texture,
        @Nullable Integer samplerObject,
        int textureUnit,
        @Nullable Boolean compareOverride,
        SamplerStateLookup samplerStateLookup
    ) {
        if (texture == null) {
            return null;
        }

        VirtualSamplerStateSnapshot samplerState = samplerObject != null
            ? samplerStateLookup.samplerState(samplerObject)
            : textureUnit >= 0
            ? samplerStateLookup.samplerStateForTextureUnit(textureUnit)
            : null;

        net.vulkanic.VulkanicTexture boundTexture = textureView.texture();
        GpuTexture gpuTexture = boundTexture instanceof GpuTexture blazeTexture ? blazeTexture : null;
        int minFilter = samplerState != null
            ? samplerState.minFilter()
            : gpuTexture != null
            ? toLegacyMinFilter(gpuTexture.getMinFilter(), gpuTexture.usesMipmaps())
            : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_NEAREST);
        int magFilter = samplerState != null
            ? samplerState.magFilter()
            : gpuTexture != null
            ? toLegacyMagFilter(gpuTexture.getMagFilter())
            : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
        int wrapS = samplerState != null
            ? samplerState.wrapS()
            : gpuTexture != null
            ? toLegacyWrapMode(gpuTexture.getAddressModeU())
            : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_REPEAT);
        int wrapT = samplerState != null
            ? samplerState.wrapT()
            : gpuTexture != null
            ? toLegacyWrapMode(gpuTexture.getAddressModeV())
            : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_REPEAT);
        int wrapR = samplerState != null
            ? samplerState.wrapR()
            : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_WRAP_R, wrapT);
        int maxLod = usesMipmappedMinFilter(minFilter)
            ? (samplerState != null
                ? samplerState.effectiveMaxLod(textureView.getMipLevelCount())
                : Math.max(0, textureView.getMipLevelCount() - 1))
            : 0;
        int compareMode;
        if (compareOverride != null) {
            compareMode = compareOverride ? VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE : 0;
        } else {
            compareMode = samplerState != null
                ? samplerState.compareMode()
                : texture.integerParameterOrDefault(VulkanicAPI.GL_TEXTURE_COMPARE_MODE, 0);
        }

        int compareFunc = samplerState != null
            ? samplerState.compareFunc()
            : texture.integerParameterOrDefault(GL_TEXTURE_COMPARE_FUNC, VulkanicAPI.GL_LEQUAL);

        return new VulkanDescriptorSamplerKey(minFilter, magFilter, wrapS, wrapT, wrapR, maxLod, compareMode, compareFunc);
    }

    boolean isStorageImageLayoutCompatibleSampler(
        @Nullable LegacyTextureStorageSnapshot texture,
        int baseMip,
        int mipCount,
        LayoutLookup layoutLookup
    ) {
        if (texture == null
            || texture.feedbackLoopCapable()
            || texture.aspectMask() != VK10.VK_IMAGE_ASPECT_COLOR_BIT
            || (texture.imageUsageFlags() & VK10.VK_IMAGE_USAGE_STORAGE_BIT) == 0) {
            return false;
        }

        int safeBaseMip = Math.max(0, baseMip);
        int safeMipCount = Math.max(1, mipCount);
        int maxMipLevels = Math.max(1, texture.mipLevels());
        if (safeBaseMip >= maxMipLevels) {
            return false;
        }
        int endMipExclusive = Math.min(maxMipLevels, safeBaseMip + safeMipCount);
        for (int level = safeBaseMip; level < endMipExclusive; level++) {
            if (layoutLookup.trackedLayout(texture.id(), level) != VK10.VK_IMAGE_LAYOUT_GENERAL) {
                return false;
            }
        }
        return true;
    }

    int descriptorImageLayoutFor(
        @Nullable LegacyTextureStorageSnapshot texture,
        boolean storageImageCompatible,
        RenderStateSnapshot renderState
    ) {
        if (texture == null) {
            return VulkanImageUse.SAMPLED_COLOR.vkLayout();
        }
        if (storageImageCompatible) {
            return VK10.VK_IMAGE_LAYOUT_GENERAL;
        }
        if (shouldUseFeedbackLoopLayoutForSampling(texture, renderState)) {
            return VulkanImageUse.FEEDBACK_LOOP.vkLayout();
        }
        if (hasDepthAspect(texture)) {
            return VulkanImageUse.SAMPLED_DEPTH.vkLayout();
        }
        return VulkanImageUse.SAMPLED_COLOR.vkLayout();
    }

    private boolean shouldUseFeedbackLoopLayoutForSampling(
        LegacyTextureStorageSnapshot texture,
        RenderStateSnapshot renderState
    ) {
        return texture.feedbackLoopCapable()
            && renderState.renderPassRecording()
            && renderState.activeAttachmentTextureIds().contains(texture.id());
    }

    private boolean shouldLogSodiumDescriptorBinding(String name) {
        if ("Sampler0".contentEquals(name) || "Sampler2".contentEquals(name)) {
            return true;
        }
        if (!Boolean.getBoolean("mattmc.vulkan.debugDescriptorBindingSeam")) {
            return false;
        }
        return name.startsWith("customtex")
            || name.startsWith("colortex")
            || name.startsWith("gaux")
            || name.startsWith("depthtex")
            || "noisetex".contentEquals(name)
            || "normals".contentEquals(name)
            || "specular".contentEquals(name);
    }

    private static String[] depthFallbackBindings(String bindingName) {
        if ("shadowtex0".equals(bindingName)) {
            return new String[]{"depthtex0", "dhDepthTex", "dhDepthTex1", "depthtex1", "depthtex2"};
        }
        if ("shadowtex1".equals(bindingName)) {
            return new String[]{"depthtex1", "dhDepthTex1", "dhDepthTex", "depthtex0", "depthtex2"};
        }
        return new String[]{"depthtex0", "depthtex1", "depthtex2", "dhDepthTex", "dhDepthTex1"};
    }

    private static boolean hasDepthAspect(@Nullable LegacyTextureStorageSnapshot texture) {
        return texture != null && texture.hasDepthAspect();
    }

    private static boolean usesMipmappedMinFilter(int minFilter) {
        return switch (minFilter) {
            case VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST,
                VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST,
                VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR,
                VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR -> true;
            case VulkanicAPI.GL_NEAREST,
                VulkanicAPI.GL_LINEAR -> false;
            default -> false;
        };
    }

    private static int toLegacyMinFilter(net.blaze3d.textures.FilterMode minFilter, boolean useMipmaps) {
        return switch (minFilter) {
            case NEAREST -> useMipmaps ? VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR : VulkanicAPI.GL_NEAREST;
            case LINEAR -> useMipmaps ? VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR : VulkanicAPI.GL_LINEAR;
        };
    }

    private static int toLegacyMagFilter(net.blaze3d.textures.FilterMode magFilter) {
        return switch (magFilter) {
            case NEAREST -> VulkanicAPI.GL_NEAREST;
            case LINEAR -> VulkanicAPI.GL_LINEAR;
        };
    }

    private static int toLegacyWrapMode(net.blaze3d.textures.AddressMode addressMode) {
        return switch (addressMode) {
            case REPEAT -> VulkanicAPI.GL_REPEAT;
            case CLAMP_TO_EDGE -> VulkanicAPI.GL_CLAMP_TO_EDGE;
        };
    }

    record PlanRequest(
        List<PipelineDescriptor.ResourceBinding> layoutBindings,
        PipelineResourceBindings bindings,
        TextureSnapshotLookup textureLookup,
        TextureBindingLookup textureBindingLookup,
        TexelBufferLookup texelBufferLookup,
        SamplerStateLookup samplerStateLookup,
        LayoutLookup layoutLookup,
        RenderStateSnapshot renderState,
        long minUniformBufferOffsetAlignment,
        boolean sodiumChunkDescriptor,
        boolean particleDescriptor,
        String pipelineLocation,
        long pipelineHandle,
        PlannerEvents events
    ) {
        PlanRequest {
            layoutBindings = List.copyOf(layoutBindings);
            Objects.requireNonNull(bindings, "bindings");
            Objects.requireNonNull(textureLookup, "textureLookup");
            Objects.requireNonNull(textureBindingLookup, "textureBindingLookup");
            Objects.requireNonNull(texelBufferLookup, "texelBufferLookup");
            Objects.requireNonNull(samplerStateLookup, "samplerStateLookup");
            Objects.requireNonNull(layoutLookup, "layoutLookup");
            Objects.requireNonNull(renderState, "renderState");
            Objects.requireNonNull(pipelineLocation, "pipelineLocation");
            events = events != null ? events : PlannerEvents.NOOP;
            if (minUniformBufferOffsetAlignment <= 0) {
                throw new IllegalArgumentException("minUniformBufferOffsetAlignment must be positive");
            }
        }
    }

    record RenderStateSnapshot(
        boolean renderPassRecording,
        Set<Integer> activeAttachmentTextureIds
    ) {
        RenderStateSnapshot {
            activeAttachmentTextureIds = Set.copyOf(activeAttachmentTextureIds);
        }
    }

    record DescriptorBindingPlan(
        List<DescriptorPlanEntry> entries,
        boolean cacheable
    ) {
        DescriptorBindingPlan {
            entries = List.copyOf(entries);
        }
    }

    sealed interface DescriptorPlanEntry permits SamplerEntry, UniformBufferEntry, StorageImageEntry, TexelBufferEntry {
        String name();

        int bindingIndex();

        int descriptorType();
    }

    enum DescriptorTransitionRequirement {
        NONE,
        SAMPLE,
        STORAGE_IMAGE
    }

    record SamplerEntry(
        String name,
        int bindingIndex,
        int descriptorType,
        VulkanTextureView textureView,
        @Nullable LegacyTextureStorageSnapshot texture,
        int textureUnit,
        @Nullable Integer samplerObject,
        @Nullable VulkanDescriptorSamplerKey samplerKey,
        long requestedImageViewHandle,
        long descriptorImageViewHandle,
        int baseMipLevel,
        int mipLevelCount,
        boolean requiresDepthOnlyView,
        boolean remappedToDefaultView,
        boolean storageImageCompatible,
        DescriptorTransitionRequirement transitionRequirement,
        int imageLayout,
        boolean sodiumDebugLog,
        boolean particleDebugLog,
        String pipelineLocation,
        long pipelineHandle
    ) implements DescriptorPlanEntry {
        SamplerEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(textureView, "textureView");
            Objects.requireNonNull(transitionRequirement, "transitionRequirement");
            Objects.requireNonNull(pipelineLocation, "pipelineLocation");
        }
    }

    record UniformBufferEntry(
        String name,
        int bindingIndex,
        int descriptorType,
        VulkanBuffer sourceBuffer,
        VulkanicBufferSlice sourceSlice,
        long descriptorBufferHandle,
        long descriptorOffset,
        long descriptorRange,
        boolean requiresTransientCopy
    ) implements DescriptorPlanEntry {
        UniformBufferEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sourceBuffer, "sourceBuffer");
            Objects.requireNonNull(sourceSlice, "sourceSlice");
        }
    }

    record StorageImageEntry(
        String name,
        int bindingIndex,
        int descriptorType,
        PipelineResourceBindings.StorageImageBinding imageBinding,
        LegacyTextureStorageSnapshot texture,
        int mipLevel,
        long imageViewHandle,
        int imageLayout
    ) implements DescriptorPlanEntry {
        StorageImageEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(imageBinding, "imageBinding");
            Objects.requireNonNull(texture, "texture");
        }
    }

    record TexelBufferEntry(
        String name,
        int bindingIndex,
        int descriptorType,
        int textureUnit,
        int textureId,
        int internalFormat,
        int legacyBufferId,
        long bufferViewHandle
    ) implements DescriptorPlanEntry {
        TexelBufferEntry {
            Objects.requireNonNull(name, "name");
        }
    }

    interface TextureSnapshotLookup {
        @Nullable
        LegacyTextureStorageSnapshot snapshotForView(VulkanTextureView view);

        @Nullable
        LegacyTextureStorageSnapshot snapshotForTexture(int textureId);
    }

    interface TextureBindingLookup {
        @Nullable
        TextureBindingSnapshot bindingSnapshot(int unit);
    }

    interface TexelBufferLookup {
        @Nullable
        LegacyTexelBufferBinding texelBufferBinding(int textureId);
    }

    interface SamplerStateLookup {
        @Nullable
        VirtualSamplerStateSnapshot samplerState(int sampler);

        @Nullable
        VirtualSamplerStateSnapshot samplerStateForTextureUnit(int unit);
    }

    interface LayoutLookup {
        int trackedLayout(int textureId, int level);
    }

    interface PlannerEvents {
        PlannerEvents NOOP = new PlannerEvents() {
        };

        default void comparisonSamplerRebound(
            String bindingName,
            String fallbackBindingName,
            @Nullable LegacyTextureStorageSnapshot texture
        ) {
        }

        default void comparisonSamplerDowngraded(
            String bindingName,
            @Nullable LegacyTextureStorageSnapshot texture
        ) {
        }
    }

    static final class DescriptorValidationException extends IllegalStateException {
        DescriptorValidationException(String message) {
            super(message);
        }
    }
}

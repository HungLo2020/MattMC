package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.GpuTexture;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicLegacyCompatibilityAdapter;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicResourceUsage;
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
        Set<String> readWriteAliasStableKeys = collectReadWriteAliasStableKeys(layoutBindings, request.bindings());
        boolean cacheable = true;

        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, request.bindings());
            DescriptorPlanEntry entry = switch (effectiveBinding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> planSamplerBinding(
                    effectiveBinding,
                    request,
                    storageImageTextureIds,
                    readWriteAliasStableKeys
                );
                case UNIFORM_BUFFER -> {
                    UniformBufferEntry uniformEntry = planUniformBufferBinding(effectiveBinding, request);
                    if (uniformEntry.requiresTransientCopy()) {
                        cacheable = false;
                    }
                    yield uniformEntry;
                }
                case STORAGE_IMAGE -> planStorageImageBinding(effectiveBinding, request, readWriteAliasStableKeys);
                case TEXEL_BUFFER -> planTexelBufferBinding(effectiveBinding, request, readWriteAliasStableKeys);
            };
            entries.add(entry);
        }

        return new DescriptorBindingPlan(entries, cacheable);
    }

    DescriptorBindingPlan plan(CompactPlanRequest request) {
        Objects.requireNonNull(request, "request");
        VulkanCompactResourceBindingTable table = request.bindings();
        List<DescriptorPlanEntry> entries = new ArrayList<>(table.slotCount());
        Set<Integer> storageImageTextureIds = collectStorageImageTextureIds(table);
        Set<String> readWriteAliasStableKeys = collectReadWriteAliasStableKeys(table);
        boolean cacheable = true;

        for (int index = 0; index < table.slotCount(); index++) {
            PipelineDescriptor.ResourceBinding binding = table.layoutBinding(index);
            DescriptorPlanEntry entry = switch (binding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> planSamplerBinding(
                    binding,
                    table,
                    index,
                    request,
                    storageImageTextureIds,
                    readWriteAliasStableKeys
                );
                case UNIFORM_BUFFER -> {
                    UniformBufferEntry uniformEntry = planUniformBufferBinding(binding, table, index, request);
                    if (uniformEntry.requiresTransientCopy()) {
                        cacheable = false;
                    }
                    yield uniformEntry;
                }
                case STORAGE_IMAGE -> planStorageImageBinding(binding, table, index, request, readWriteAliasStableKeys);
                case TEXEL_BUFFER -> planTexelBufferBinding(binding, table, index, request, readWriteAliasStableKeys);
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

    Set<Integer> collectStorageImageTextureIds(VulkanCompactResourceBindingTable bindings) {
        Set<Integer> storageImageTextureIds = new HashSet<>();
        for (int index = 0; index < bindings.slotCount(); index++) {
            PipelineDescriptor.ResourceBinding binding = bindings.layoutBinding(index);
            if (binding.type() != PipelineDescriptor.ResourceType.STORAGE_IMAGE) {
                continue;
            }
            PipelineResourceBindings.StorageImageBinding imageBinding = bindings.storageImageBinding(index);
            if (imageBinding != null && imageBinding.texture() > 0) {
                storageImageTextureIds.add(imageBinding.texture());
            }
        }
        return storageImageTextureIds;
    }

    Set<String> collectReadWriteAliasStableKeys(
        List<PipelineDescriptor.ResourceBinding> layoutBindings,
        PipelineResourceBindings bindings
    ) {
        Set<String> sampledStableKeys = new HashSet<>();
        Set<String> storageStableKeys = new HashSet<>();
        Set<Integer> sampledLegacyIds = new HashSet<>();
        Set<Integer> storageLegacyIds = new HashSet<>();

        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, bindings);
            switch (effectiveBinding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding =
                        bindings.getSamplerBindingOrNull(effectiveBinding.name());
                    addReferenceIdentity(samplerBinding == null ? null : samplerBinding.resourceReference(),
                        sampledStableKeys, sampledLegacyIds);
                }
                case STORAGE_IMAGE -> {
                    PipelineResourceBindings.StorageImageBinding imageBinding =
                        bindings.getStorageImageBindingOrNull(effectiveBinding.name());
                    addReferenceIdentity(imageBinding == null ? null : imageBinding.resourceReference(),
                        storageStableKeys, storageLegacyIds);
                }
                case UNIFORM_BUFFER, TEXEL_BUFFER -> {
                    // These resource classes are not sampled/storage-image read-write aliases.
                }
            }
        }

        Set<String> aliases = new HashSet<>(sampledStableKeys);
        aliases.retainAll(storageStableKeys);
        Set<Integer> sharedLegacyIds = new HashSet<>(sampledLegacyIds);
        sharedLegacyIds.retainAll(storageLegacyIds);
        if (!sharedLegacyIds.isEmpty()) {
            addStableKeysForLegacyIds(layoutBindings, bindings, sharedLegacyIds, aliases);
        }
        return aliases;
    }

    Set<String> collectReadWriteAliasStableKeys(VulkanCompactResourceBindingTable bindings) {
        Set<String> sampledStableKeys = new HashSet<>();
        Set<String> storageStableKeys = new HashSet<>();
        Set<Integer> sampledLegacyIds = new HashSet<>();
        Set<Integer> storageLegacyIds = new HashSet<>();

        for (int index = 0; index < bindings.slotCount(); index++) {
            switch (bindings.layoutBinding(index).type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding = bindings.samplerBinding(index);
                    addReferenceIdentity(samplerBinding == null ? null : samplerBinding.resourceReference(),
                        sampledStableKeys, sampledLegacyIds);
                }
                case STORAGE_IMAGE -> {
                    PipelineResourceBindings.StorageImageBinding imageBinding = bindings.storageImageBinding(index);
                    addReferenceIdentity(imageBinding == null ? null : imageBinding.resourceReference(),
                        storageStableKeys, storageLegacyIds);
                }
                case UNIFORM_BUFFER, TEXEL_BUFFER -> {
                }
            }
        }

        Set<String> aliases = new HashSet<>(sampledStableKeys);
        aliases.retainAll(storageStableKeys);
        Set<Integer> sharedLegacyIds = new HashSet<>(sampledLegacyIds);
        sharedLegacyIds.retainAll(storageLegacyIds);
        if (!sharedLegacyIds.isEmpty()) {
            addStableKeysForLegacyIds(bindings, sharedLegacyIds, aliases);
        }
        return aliases;
    }

    private void addStableKeysForLegacyIds(
        List<PipelineDescriptor.ResourceBinding> layoutBindings,
        PipelineResourceBindings bindings,
        Set<Integer> sharedLegacyIds,
        Set<String> aliases
    ) {
        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            PipelineDescriptor.ResourceBinding effectiveBinding = withEffectiveBindingType(binding, bindings);
            VulkanicPassResourceModel.CanonicalResourceReference reference = switch (effectiveBinding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding =
                        bindings.getSamplerBindingOrNull(effectiveBinding.name());
                    yield samplerBinding == null ? null : samplerBinding.resourceReference();
                }
                case STORAGE_IMAGE -> {
                    PipelineResourceBindings.StorageImageBinding imageBinding =
                        bindings.getStorageImageBindingOrNull(effectiveBinding.name());
                    yield imageBinding == null ? null : imageBinding.resourceReference();
                }
                case UNIFORM_BUFFER, TEXEL_BUFFER -> null;
            };
            if (reference != null
                && reference.legacyId().isPresent()
                && sharedLegacyIds.contains(reference.legacyId().getAsInt())) {
                aliases.add(reference.resource().stableKey());
            }
        }
    }

    private void addStableKeysForLegacyIds(
        VulkanCompactResourceBindingTable bindings,
        Set<Integer> sharedLegacyIds,
        Set<String> aliases
    ) {
        for (int index = 0; index < bindings.slotCount(); index++) {
            VulkanicPassResourceModel.CanonicalResourceReference reference = bindings.resourceReference(index);
            if (reference != null
                && reference.legacyId().isPresent()
                && sharedLegacyIds.contains(reference.legacyId().getAsInt())) {
                aliases.add(reference.resource().stableKey());
            }
        }
    }

    private static void addReferenceIdentity(
        @Nullable VulkanicPassResourceModel.CanonicalResourceReference reference,
        Set<String> stableKeys,
        Set<Integer> legacyIds
    ) {
        if (reference == null) {
            return;
        }
        stableKeys.add(reference.resource().stableKey());
        reference.legacyId().ifPresent(legacyIds::add);
    }

    private SamplerEntry planSamplerBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request,
        Set<Integer> storageImageTextureIds,
        Set<String> readWriteAliasStableKeys
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
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot sampledTexture = request.textureLookup().snapshotForView(resolvedTextureView);

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
                VulkanImageResourceViewCoordinator.ImageStorageSnapshot fallbackTexture = request.textureLookup().snapshotForView(fallbackView);
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

        VulkanImageResourceViewCoordinator.DescriptorImagePlan imagePlan =
            request.textureLookup().descriptorSampledImagePlan(
                resolvedTextureView,
                sampledTexture,
                storageImageTextureIds,
                request.layoutLookup(),
                request.renderState()
            );

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
        VulkanicPassResourceModel.ResourceUse resourceUse = canonicalResourceUse(
            samplerBinding.resourceReference(),
            request.pipelineLocation() + ":sampler:" + binding.name(),
            isReadWriteAlias(samplerBinding.resourceReference(), readWriteAliasStableKeys),
            binding.binding()
        ).orElse(null);
        if (resourceUse == null) {
            resourceUse = samplerResourceUse(binding, resolvedTextureView, sampledTexture, imagePlan, request.pipelineLocation());
        }

        return new SamplerEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            resolvedTextureView,
            sampledTexture,
            samplerBinding.textureUnit(),
            samplerBinding.samplerObject(),
            samplerKey,
            imagePlan.requestedImageViewHandle(),
            imagePlan.descriptorImageViewHandle(),
            imagePlan.baseMipLevel(),
            imagePlan.mipLevelCount(),
            imagePlan.requiresDepthOnlyView(),
            imagePlan.remappedToDefaultView(),
            imagePlan.storageImageCompatible(),
            imagePlan.transitionRequirement(),
            imagePlan.imageLayout(),
            imagePlan.materializationRequest(),
            request.sodiumChunkDescriptor() && shouldLogSodiumDescriptorBinding(binding.name()),
            request.particleDescriptor()
                && ("Sampler0".contentEquals(binding.name()) || "Sampler2".contentEquals(binding.name())),
            request.pipelineLocation(),
            request.pipelineHandle(),
            resourceUse
        );
    }

    private SamplerEntry planSamplerBinding(
        PipelineDescriptor.ResourceBinding binding,
        VulkanCompactResourceBindingTable table,
        int slotIndex,
        CompactPlanRequest request,
        Set<Integer> storageImageTextureIds,
        Set<String> readWriteAliasStableKeys
    ) {
        PipelineResourceBindings.SamplerBinding samplerBinding = table.samplerBinding(slotIndex);
        if (samplerBinding == null) {
            throw new DescriptorValidationException("Missing sampler binding for '" + binding.name() + "'");
        }

        if (!(samplerBinding.textureView() instanceof VulkanTextureView vulkanTextureView)) {
            throw new IllegalArgumentException(
                "Sampler binding '" + binding.name() + "' requires VulkanTextureView on Vulkan backend");
        }

        VulkanTextureView resolvedTextureView = vulkanTextureView;
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot sampledTexture = request.textureLookup().snapshotForView(resolvedTextureView);

        boolean wantsComparisonSampler = binding.type() == PipelineDescriptor.ResourceType.COMPARISON_SAMPLER;
        boolean canUseComparisonSampler = hasDepthAspect(sampledTexture);
        String depthFallbackBindingName = null;
        if (wantsComparisonSampler && !canUseComparisonSampler) {
            for (String fallbackBindingName : depthFallbackBindings(binding.name())) {
                PipelineResourceBindings.SamplerBinding fallbackSamplerBinding =
                    request.bindings().samplerBinding(fallbackBindingName);
                if (fallbackSamplerBinding == null
                    || !(fallbackSamplerBinding.textureView() instanceof VulkanTextureView fallbackView)) {
                    continue;
                }
                VulkanImageResourceViewCoordinator.ImageStorageSnapshot fallbackTexture = request.textureLookup().snapshotForView(fallbackView);
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

        VulkanImageResourceViewCoordinator.DescriptorImagePlan imagePlan =
            request.textureLookup().descriptorSampledImagePlan(
                resolvedTextureView,
                sampledTexture,
                storageImageTextureIds,
                request.layoutLookup(),
                request.renderState()
            );

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
        VulkanicPassResourceModel.ResourceUse resourceUse = canonicalResourceUse(
            samplerBinding.resourceReference(),
            request.pipelineLocation() + ":sampler:" + binding.name(),
            isReadWriteAlias(samplerBinding.resourceReference(), readWriteAliasStableKeys),
            binding.binding()
        ).orElse(null);
        if (resourceUse == null) {
            resourceUse = samplerResourceUse(binding, resolvedTextureView, sampledTexture, imagePlan, request.pipelineLocation());
        }

        return new SamplerEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding.type()),
            resolvedTextureView,
            sampledTexture,
            samplerBinding.textureUnit(),
            samplerBinding.samplerObject(),
            samplerKey,
            imagePlan.requestedImageViewHandle(),
            imagePlan.descriptorImageViewHandle(),
            imagePlan.baseMipLevel(),
            imagePlan.mipLevelCount(),
            imagePlan.requiresDepthOnlyView(),
            imagePlan.remappedToDefaultView(),
            imagePlan.storageImageCompatible(),
            imagePlan.transitionRequirement(),
            imagePlan.imageLayout(),
            imagePlan.materializationRequest(),
            request.sodiumChunkDescriptor() && shouldLogSodiumDescriptorBinding(binding.name()),
            request.particleDescriptor()
                && ("Sampler0".contentEquals(binding.name()) || "Sampler2".contentEquals(binding.name())),
            request.pipelineLocation(),
            request.pipelineHandle(),
            resourceUse
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

        boolean dynamicUniform = VulkanDescriptorResourceClassifier.isDynamicUniformBufferBinding(binding.name());
        boolean requiresTransientUniformCopy = !dynamicUniform
            && ((vulkanBuffer.usage() & VulkanicBuffer.USAGE_UNIFORM) == 0
                || (slice.offset() % request.minUniformBufferOffsetAlignment()) != 0);
        return new UniformBufferEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding),
            vulkanBuffer,
            slice,
            vulkanBuffer.getVkBufferHandle(),
            slice.offset(),
            slice.length(),
            requiresTransientUniformCopy,
            uniformBufferResourceUse(binding, slice, request.pipelineLocation())
        );
    }

    private UniformBufferEntry planUniformBufferBinding(
        PipelineDescriptor.ResourceBinding binding,
        VulkanCompactResourceBindingTable table,
        int slotIndex,
        CompactPlanRequest request
    ) {
        VulkanicBufferSlice slice = table.uniformBufferBinding(slotIndex);
        if (slice == null) {
            throw new DescriptorValidationException("Missing uniform-buffer binding for '" + binding.name() + "'");
        }

        if (!(slice.buffer() instanceof VulkanBuffer vulkanBuffer)) {
            throw new IllegalArgumentException(
                "Uniform-buffer binding '" + binding.name() + "' requires VulkanBuffer on Vulkan backend");
        }

        boolean dynamicUniform = VulkanDescriptorResourceClassifier.isDynamicUniformBufferBinding(binding.name());
        boolean requiresTransientUniformCopy = !dynamicUniform
            && ((vulkanBuffer.usage() & VulkanicBuffer.USAGE_UNIFORM) == 0
                || (slice.offset() % request.minUniformBufferOffsetAlignment()) != 0);
        return new UniformBufferEntry(
            binding.name(),
            binding.binding(),
            VulkanDescriptorResourceClassifier.toVkDescriptorType(binding),
            vulkanBuffer,
            slice,
            vulkanBuffer.getVkBufferHandle(),
            slice.offset(),
            slice.length(),
            requiresTransientUniformCopy,
            uniformBufferResourceUse(binding, slice, request.pipelineLocation())
        );
    }

    private StorageImageEntry planStorageImageBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request,
        Set<String> readWriteAliasStableKeys
    ) {
        PipelineResourceBindings.StorageImageBinding imageBinding =
            request.bindings().getStorageImageBindingOrNull(binding.name());
        if (imageBinding == null) {
            throw new DescriptorValidationException("Missing storage-image binding for '" + binding.name() + "'");
        }

        VulkanImageResourceViewCoordinator.DescriptorImagePlan imagePlan =
            request.textureLookup().descriptorStorageImagePlan(imageBinding.texture(), imageBinding.level());
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = imagePlan.storage();
        if (texture == null
            || texture.imageHandle() == VK10.VK_NULL_HANDLE
            || imagePlan.descriptorImageViewHandle() == VK10.VK_NULL_HANDLE) {
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
            imagePlan.descriptorImageViewHandle(),
            imagePlan.imageLayout(),
            canonicalResourceUse(
                imageBinding.resourceReference(),
                request.pipelineLocation() + ":storage-image:" + binding.name(),
                isReadWriteAlias(imageBinding.resourceReference(), readWriteAliasStableKeys),
                binding.binding()
            ).orElseGet(() ->
                storageImageResourceUse(binding, imageBinding, texture, mipLevel, request.pipelineLocation())
            )
        );
    }

    private StorageImageEntry planStorageImageBinding(
        PipelineDescriptor.ResourceBinding binding,
        VulkanCompactResourceBindingTable table,
        int slotIndex,
        CompactPlanRequest request,
        Set<String> readWriteAliasStableKeys
    ) {
        PipelineResourceBindings.StorageImageBinding imageBinding = table.storageImageBinding(slotIndex);
        if (imageBinding == null) {
            throw new DescriptorValidationException("Missing storage-image binding for '" + binding.name() + "'");
        }

        VulkanImageResourceViewCoordinator.DescriptorImagePlan imagePlan =
            request.textureLookup().descriptorStorageImagePlan(imageBinding.texture(), imageBinding.level());
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture = imagePlan.storage();
        if (texture == null
            || texture.imageHandle() == VK10.VK_NULL_HANDLE
            || imagePlan.descriptorImageViewHandle() == VK10.VK_NULL_HANDLE) {
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
            imagePlan.descriptorImageViewHandle(),
            imagePlan.imageLayout(),
            canonicalResourceUse(
                imageBinding.resourceReference(),
                request.pipelineLocation() + ":storage-image:" + binding.name(),
                isReadWriteAlias(imageBinding.resourceReference(), readWriteAliasStableKeys),
                binding.binding()
            ).orElseGet(() ->
                storageImageResourceUse(binding, imageBinding, texture, mipLevel, request.pipelineLocation())
            )
        );
    }

    private TexelBufferEntry planTexelBufferBinding(
        PipelineDescriptor.ResourceBinding binding,
        PlanRequest request,
        Set<String> readWriteAliasStableKeys
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

        VulkanImageResourceViewCoordinator.TexelBufferViewPlan legacyTexelBinding = request.texelBufferLookup().texelBufferBinding(textureId);
        if (legacyTexelBinding == null
            || legacyTexelBinding.bufferViewHandle() == VK10.VK_NULL_HANDLE) {
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
            legacyTexelBinding.internalFormat(),
            legacyTexelBinding.legacyBufferId(),
            legacyTexelBinding.bufferViewHandle(),
            canonicalResourceUse(
                texelBinding.resourceReference(),
                request.pipelineLocation() + ":texel-buffer:" + binding.name(),
                isReadWriteAlias(texelBinding.resourceReference(), readWriteAliasStableKeys),
                binding.binding()
            ).orElseGet(() ->
                texelBufferResourceUse(binding, legacyTexelBinding, request.pipelineLocation())
            )
        );
    }

    private TexelBufferEntry planTexelBufferBinding(
        PipelineDescriptor.ResourceBinding binding,
        VulkanCompactResourceBindingTable table,
        int slotIndex,
        CompactPlanRequest request,
        Set<String> readWriteAliasStableKeys
    ) {
        PipelineResourceBindings.TexelBufferBinding texelBinding = table.texelBufferBinding(slotIndex);
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

        VulkanImageResourceViewCoordinator.TexelBufferViewPlan legacyTexelBinding = request.texelBufferLookup().texelBufferBinding(textureId);
        if (legacyTexelBinding == null
            || legacyTexelBinding.bufferViewHandle() == VK10.VK_NULL_HANDLE) {
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
            legacyTexelBinding.internalFormat(),
            legacyTexelBinding.legacyBufferId(),
            legacyTexelBinding.bufferViewHandle(),
            canonicalResourceUse(
                texelBinding.resourceReference(),
                request.pipelineLocation() + ":texel-buffer:" + binding.name(),
                isReadWriteAlias(texelBinding.resourceReference(), readWriteAliasStableKeys),
                binding.binding()
            ).orElseGet(() ->
                texelBufferResourceUse(binding, legacyTexelBinding, request.pipelineLocation())
            )
        );
    }

    private java.util.Optional<VulkanicPassResourceModel.ResourceUse> canonicalResourceUse(
        @Nullable VulkanicPassResourceModel.CanonicalResourceReference reference,
        String role,
        boolean feedbackLoop,
        int order
    ) {
        return reference == null
            ? java.util.Optional.empty()
            : java.util.Optional.of(reference.asResourceUse(role, feedbackLoop, order));
    }

    private static boolean isReadWriteAlias(
        @Nullable VulkanicPassResourceModel.CanonicalResourceReference reference,
        Set<String> readWriteAliasStableKeys
    ) {
        return reference != null && readWriteAliasStableKeys.contains(reference.resource().stableKey());
    }

    private VulkanicPassResourceModel.ResourceUse samplerResourceUse(
        PipelineDescriptor.ResourceBinding binding,
        VulkanTextureView textureView,
        @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
        VulkanImageResourceViewCoordinator.DescriptorImagePlan imagePlan,
        String pipelineLocation
    ) {
        int textureId = texture != null ? texture.textureId() : textureView.getLegacyTextureHandle();
        VulkanicPassResourceModel.ResourceIdentity identity = VulkanicPassResourceModel.ResourceIdentity.of(
            binding.name(),
            VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
            "texture:" + textureId + ":view-mip:" + imagePlan.baseMipLevel() + "+" + imagePlan.mipLevelCount()
        );
        VulkanicPassResourceModel.Subresource subresource = subresourceForAspectMask(
            texture == null ? 0 : texture.aspectMask(),
            imagePlan.baseMipLevel(),
            imagePlan.mipLevelCount(),
            0,
            texture == null ? 1 : VulkanImageResourceViewCoordinator.layerCount(texture)
        );
        return VulkanicLegacyCompatibilityAdapter.resourceUse(
            identity.logicalName(),
            identity.kind(),
            identity.stableKey(),
            VulkanicPassResourceModel.Access.READ,
            subresource,
            VulkanicResourceUsage.SAMPLED_READ,
            pipelineLocation + ":sampler:" + binding.name(),
            false,
            binding.binding()
        );
    }

    private VulkanicPassResourceModel.ResourceUse uniformBufferResourceUse(
        PipelineDescriptor.ResourceBinding binding,
        VulkanicBufferSlice slice,
        String pipelineLocation
    ) {
        return VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
            binding.name(),
            "uniform:" + binding.name() + ":offset:" + slice.offset() + ":length:" + slice.length(),
            slice.offset(),
            slice.length(),
            pipelineLocation + ":uniform-buffer:" + binding.name(),
            binding.binding()
        );
    }

    private VulkanicPassResourceModel.ResourceUse storageImageResourceUse(
        PipelineDescriptor.ResourceBinding binding,
        PipelineResourceBindings.StorageImageBinding imageBinding,
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
        int mipLevel,
        String pipelineLocation
    ) {
        return VulkanicLegacyCompatibilityAdapter.storageTextureUse(
            binding.name(),
            "texture:" + imageBinding.texture() + ":storage-mip:" + mipLevel,
            subresourceForAspectMask(texture.aspectMask(), mipLevel, 1, 0, VulkanImageResourceViewCoordinator.layerCount(texture)),
            pipelineLocation + ":storage-image:" + binding.name(),
            binding.binding()
        );
    }

    private VulkanicPassResourceModel.ResourceUse texelBufferResourceUse(
        PipelineDescriptor.ResourceBinding binding,
        VulkanImageResourceViewCoordinator.TexelBufferViewPlan texelBinding,
        String pipelineLocation
    ) {
        return VulkanicLegacyCompatibilityAdapter.texelBufferUse(
            binding.name(),
            "legacy-buffer:" + texelBinding.legacyBufferId() + ":format:" + texelBinding.internalFormat(),
            pipelineLocation + ":texel-buffer:" + binding.name(),
            binding.binding()
        );
    }

    private VulkanicPassResourceModel.Subresource subresourceForAspectMask(
        int aspectMask,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        int depthStencil = VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
        if ((aspectMask & depthStencil) == depthStencil) {
            return VulkanicPassResourceModel.Subresource.depthStencil(baseMipLevel, levelCount, baseLayer, layerCount);
        }
        if ((aspectMask & VK10.VK_IMAGE_ASPECT_DEPTH_BIT) != 0) {
            return VulkanicPassResourceModel.Subresource.depth(baseMipLevel, levelCount, baseLayer, layerCount);
        }
        return VulkanicPassResourceModel.Subresource.color(baseMipLevel, levelCount, baseLayer, layerCount);
    }

    private VulkanDescriptorSamplerKey descriptorSamplerKey(
        VulkanTextureView textureView,
        @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
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

    private static boolean hasDepthAspect(@Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture) {
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

    record CompactPlanRequest(
        VulkanCompactResourceBindingTable bindings,
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
        CompactPlanRequest {
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

        List<VulkanicPassResourceModel.ResourceUse> resourceUses() {
            return entries.stream()
                .map(DescriptorPlanEntry::resourceUse)
                .toList();
        }
    }

    sealed interface DescriptorPlanEntry permits SamplerEntry, UniformBufferEntry, StorageImageEntry, TexelBufferEntry {
        String name();

        int bindingIndex();

        int descriptorType();

        VulkanicPassResourceModel.ResourceUse resourceUse();
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
        @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
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
        @Nullable VulkanImageResourceViewCoordinator.ViewMaterializationRequest materializationRequest,
        boolean sodiumDebugLog,
        boolean particleDebugLog,
        String pipelineLocation,
        long pipelineHandle,
        VulkanicPassResourceModel.ResourceUse resourceUse
    ) implements DescriptorPlanEntry {
        SamplerEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(textureView, "textureView");
            Objects.requireNonNull(transitionRequirement, "transitionRequirement");
            Objects.requireNonNull(pipelineLocation, "pipelineLocation");
            Objects.requireNonNull(resourceUse, "resourceUse");
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
        boolean requiresTransientCopy,
        VulkanicPassResourceModel.ResourceUse resourceUse
    ) implements DescriptorPlanEntry {
        UniformBufferEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sourceBuffer, "sourceBuffer");
            Objects.requireNonNull(sourceSlice, "sourceSlice");
            Objects.requireNonNull(resourceUse, "resourceUse");
        }
    }

    record StorageImageEntry(
        String name,
        int bindingIndex,
        int descriptorType,
        PipelineResourceBindings.StorageImageBinding imageBinding,
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture,
        int mipLevel,
        long imageViewHandle,
        int imageLayout,
        VulkanicPassResourceModel.ResourceUse resourceUse
    ) implements DescriptorPlanEntry {
        StorageImageEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(imageBinding, "imageBinding");
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(resourceUse, "resourceUse");
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
        long bufferViewHandle,
        VulkanicPassResourceModel.ResourceUse resourceUse
    ) implements DescriptorPlanEntry {
        TexelBufferEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(resourceUse, "resourceUse");
        }
    }

    interface TextureSnapshotLookup {
        @Nullable
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForView(VulkanTextureView view);

        @Nullable
        VulkanImageResourceViewCoordinator.ImageStorageSnapshot snapshotForTexture(int textureId);

        VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorSampledImagePlan(
            VulkanTextureView view,
            @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot storage,
            Set<Integer> storageImageTextureIds,
            LayoutLookup layoutLookup,
            RenderStateSnapshot renderState
        );

        VulkanImageResourceViewCoordinator.DescriptorImagePlan descriptorStorageImagePlan(int textureId, int mipLevel);
    }

    interface TextureBindingLookup {
        @Nullable
        TextureBindingSnapshot bindingSnapshot(int unit);
    }

    interface TexelBufferLookup {
        @Nullable
        VulkanImageResourceViewCoordinator.TexelBufferViewPlan texelBufferBinding(int textureId);
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
            @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture
        ) {
        }

        default void comparisonSamplerDowngraded(
            String bindingName,
            @Nullable VulkanImageResourceViewCoordinator.ImageStorageSnapshot texture
        ) {
        }
    }

    static final class DescriptorValidationException extends IllegalStateException {
        DescriptorValidationException(String message) {
            super(message);
        }
    }
}

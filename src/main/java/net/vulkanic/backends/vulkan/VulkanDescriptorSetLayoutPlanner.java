package net.vulkanic.backends.vulkan;

import net.vulkanic.PipelineDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class VulkanDescriptorSetLayoutPlanner {
    DescriptorLayoutPlan plan(PipelineDescriptor.ResourceLayout resourceLayout) {
        Objects.requireNonNull(resourceLayout, "resourceLayout");
        return plan(resourceLayout.bindings());
    }

    DescriptorLayoutPlan plan(List<PipelineDescriptor.ResourceBinding> declarations) {
        List<PipelineDescriptor.ResourceBinding> bindings = List.copyOf(declarations);
        Map<Integer, List<DescriptorLayoutBindingPlan>> bindingsBySet = new LinkedHashMap<>();
        Map<DescriptorSlot, DescriptorLayoutBindingPlan> slots = new LinkedHashMap<>();
        int maxSet = 0;

        for (PipelineDescriptor.ResourceBinding binding : bindings) {
            DescriptorLayoutBindingPlan plannedBinding = bindingPlan(binding);
            DescriptorSlot slot = new DescriptorSlot(binding.set(), binding.binding());
            DescriptorLayoutBindingPlan previous = slots.putIfAbsent(slot, plannedBinding);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Conflicting Vulkan descriptor declarations for set "
                        + binding.set()
                        + " binding "
                        + binding.binding()
                        + ": '"
                        + previous.name()
                        + "' and '"
                        + binding.name()
                        + "'"
                );
            }
            bindingsBySet.computeIfAbsent(binding.set(), ignored -> new ArrayList<>()).add(plannedBinding);
            maxSet = Math.max(maxSet, binding.set());
        }

        List<DescriptorSetLayoutPlan> sets = new ArrayList<>(Math.max(1, maxSet + 1));
        if (bindings.isEmpty()) {
            sets.add(new DescriptorSetLayoutPlan(0, List.of()));
        } else {
            for (int set = 0; set <= maxSet; set++) {
                List<DescriptorLayoutBindingPlan> setBindings =
                    bindingsBySet.getOrDefault(set, List.of());
                sets.add(new DescriptorSetLayoutPlan(set, setBindings));
            }
        }

        return new DescriptorLayoutPlan(sets);
    }

    private static DescriptorLayoutBindingPlan bindingPlan(PipelineDescriptor.ResourceBinding binding) {
        int descriptorType = VulkanDescriptorResourceClassifier.toVkDescriptorType(binding);
        int stageFlags = VulkanDescriptorResourceClassifier.toVkShaderStageFlags(binding.stages());
        return new DescriptorLayoutBindingPlan(
            binding.name(),
            binding.set(),
            binding.binding(),
            binding.type(),
            descriptorType,
            1,
            stageFlags,
            null,
            0
        );
    }

    record DescriptorLayoutPlan(
        List<DescriptorSetLayoutPlan> sets,
        PipelineLayoutCompatibilityKey compatibilityKey
    ) {
        DescriptorLayoutPlan(List<DescriptorSetLayoutPlan> sets) {
            this(sets, PipelineLayoutCompatibilityKey.fromSets(sets));
        }

        DescriptorLayoutPlan {
            sets = List.copyOf(sets);
            Objects.requireNonNull(compatibilityKey, "compatibilityKey");
            if (sets.isEmpty()) {
                throw new IllegalArgumentException("Descriptor layout plan must contain at least set 0");
            }
            for (int i = 0; i < sets.size(); i++) {
                DescriptorSetLayoutPlan set = sets.get(i);
                if (set.set() != i) {
                    throw new IllegalArgumentException(
                        "Descriptor layout sets must be contiguous and ordered from zero; expected "
                            + i
                            + " but got "
                            + set.set()
                    );
                }
            }
        }

        DescriptorSetLayoutPlan primarySet() {
            return sets.get(0);
        }

        int totalBindingCount() {
            int count = 0;
            for (DescriptorSetLayoutPlan set : sets) {
                count += set.bindings().size();
            }
            return count;
        }

        List<DescriptorLayoutBindingPlan> allBindings() {
            List<DescriptorLayoutBindingPlan> all = new ArrayList<>(totalBindingCount());
            for (DescriptorSetLayoutPlan set : sets) {
                all.addAll(set.bindings());
            }
            return List.copyOf(all);
        }
    }

    record DescriptorSetLayoutPlan(
        int set,
        List<DescriptorLayoutBindingPlan> bindings
    ) {
        DescriptorSetLayoutPlan {
            if (set < 0) {
                throw new IllegalArgumentException("set must be >= 0");
            }
            bindings = List.copyOf(bindings);
        }
    }

    record DescriptorLayoutBindingPlan(
        String name,
        int set,
        int binding,
        PipelineDescriptor.ResourceType resourceType,
        int descriptorType,
        int descriptorCount,
        int stageFlags,
        @Nullable ImmutableSamplerRequirement immutableSamplerRequirement,
        int bindingFlags
    ) {
        DescriptorLayoutBindingPlan {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(resourceType, "resourceType");
            if (set < 0) {
                throw new IllegalArgumentException("set must be >= 0");
            }
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            if (descriptorCount <= 0) {
                throw new IllegalArgumentException("descriptorCount must be > 0");
            }
        }
    }

    record ImmutableSamplerRequirement(String debugName) {
        ImmutableSamplerRequirement {
            Objects.requireNonNull(debugName, "debugName");
        }
    }

    record PipelineLayoutCompatibilityKey(List<SetCompatibilityKey> sets) {
        static PipelineLayoutCompatibilityKey fromSets(List<DescriptorSetLayoutPlan> setPlans) {
            return new PipelineLayoutCompatibilityKey(setPlans.stream()
                .map(SetCompatibilityKey::new)
                .sorted(Comparator.comparingInt(SetCompatibilityKey::set))
                .toList());
        }

        PipelineLayoutCompatibilityKey {
            sets = List.copyOf(sets);
        }
    }

    record SetCompatibilityKey(int set, List<BindingCompatibilityKey> bindings) {
        SetCompatibilityKey(DescriptorSetLayoutPlan plan) {
            this(plan.set(), plan.bindings().stream()
                .map(BindingCompatibilityKey::new)
                .sorted(Comparator.comparingInt(BindingCompatibilityKey::binding))
                .toList());
        }

        SetCompatibilityKey {
            bindings = List.copyOf(bindings);
        }
    }

    record BindingCompatibilityKey(
        int binding,
        int descriptorType,
        int descriptorCount,
        int stageFlags,
        @Nullable ImmutableSamplerRequirement immutableSamplerRequirement,
        int bindingFlags
    ) {
        BindingCompatibilityKey(DescriptorLayoutBindingPlan binding) {
            this(
                binding.binding(),
                binding.descriptorType(),
                binding.descriptorCount(),
                binding.stageFlags(),
                binding.immutableSamplerRequirement(),
                binding.bindingFlags()
            );
        }
    }

    private record DescriptorSlot(int set, int binding) {
    }
}

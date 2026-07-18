package net.vulkanic;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral SPIR-V shader module payload.
 */
public final class VulkanicSpirvModule {
    private final VulkanicShaderStage stage;
    private final String entryPoint;
    private final String sourceName;
    private final String compilerName;
    private final boolean sourceContainsIrisFragData0;
    private final boolean sourceIsSodiumCoreVulkanChunk;
    private final List<FragmentOutput> fragmentOutputs;
    private final byte[] spirvBytes;

    public VulkanicSpirvModule(
        VulkanicShaderStage stage,
        String entryPoint,
        byte[] spirvBytes,
        String sourceName,
        String compilerName
    ) {
        this(stage, entryPoint, spirvBytes, sourceName, compilerName, false, "sodium:core/vulkan_chunk".equals(sourceName));
    }

    public VulkanicSpirvModule(
        VulkanicShaderStage stage,
        String entryPoint,
        byte[] spirvBytes,
        String sourceName,
        String compilerName,
        boolean sourceContainsIrisFragData0,
        boolean sourceIsSodiumCoreVulkanChunk
    ) {
        this(stage, entryPoint, spirvBytes, sourceName, compilerName, sourceContainsIrisFragData0, sourceIsSodiumCoreVulkanChunk, List.of());
    }

    public VulkanicSpirvModule(
        VulkanicShaderStage stage,
        String entryPoint,
        byte[] spirvBytes,
        String sourceName,
        String compilerName,
        boolean sourceContainsIrisFragData0,
        boolean sourceIsSodiumCoreVulkanChunk,
        List<FragmentOutput> fragmentOutputs
    ) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint must not be null");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName must not be null");
        this.compilerName = Objects.requireNonNull(compilerName, "compilerName must not be null");
        this.sourceContainsIrisFragData0 = sourceContainsIrisFragData0;
        this.sourceIsSodiumCoreVulkanChunk = sourceIsSodiumCoreVulkanChunk;
        this.fragmentOutputs = List.copyOf(Objects.requireNonNull(fragmentOutputs, "fragmentOutputs must not be null"));
        this.spirvBytes = Objects.requireNonNull(spirvBytes, "spirvBytes must not be null").clone();
    }

    public VulkanicSpirvModule withFragmentOutputs(List<FragmentOutput> fragmentOutputs) {
        return new VulkanicSpirvModule(
            stage,
            entryPoint,
            spirvBytes,
            sourceName,
            compilerName,
            sourceContainsIrisFragData0,
            sourceIsSodiumCoreVulkanChunk,
            fragmentOutputs
        );
    }

    public VulkanicShaderStage stage() {
        return stage;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public String sourceName() {
        return sourceName;
    }

    public String compilerName() {
        return compilerName;
    }

    public boolean sourceContainsIrisFragData0() {
        return sourceContainsIrisFragData0;
    }

    public boolean sourceIsSodiumCoreVulkanChunk() {
        return sourceIsSodiumCoreVulkanChunk;
    }

    public List<FragmentOutput> fragmentOutputs() {
        return fragmentOutputs;
    }

    public byte[] spirvBytes() {
        return spirvBytes.clone();
    }

    public int byteSize() {
        return spirvBytes.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VulkanicSpirvModule other)) {
            return false;
        }

        return stage == other.stage
            && entryPoint.equals(other.entryPoint)
            && sourceName.equals(other.sourceName)
            && compilerName.equals(other.compilerName)
            && sourceContainsIrisFragData0 == other.sourceContainsIrisFragData0
            && sourceIsSodiumCoreVulkanChunk == other.sourceIsSodiumCoreVulkanChunk
            && fragmentOutputs.equals(other.fragmentOutputs)
            && Arrays.equals(spirvBytes, other.spirvBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(stage, entryPoint, sourceName, compilerName, sourceContainsIrisFragData0, sourceIsSodiumCoreVulkanChunk, fragmentOutputs);
        result = 31 * result + Arrays.hashCode(spirvBytes);
        return result;
    }

    public record FragmentOutput(int location, String name, String typeName) {
        public FragmentOutput {
            if (location < 0) {
                throw new IllegalArgumentException("fragment output location must be non-negative");
            }
            name = Objects.requireNonNull(name, "name must not be null");
            typeName = Objects.requireNonNull(typeName, "typeName must not be null");
        }
    }
}

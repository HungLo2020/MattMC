package net.vulkanic;

import java.util.Arrays;
import java.util.Objects;

/**
 * Backend-neutral SPIR-V shader module payload.
 */
public final class VulkanicSpirvModule {
    private final VulkanicShaderStage stage;
    private final String entryPoint;
    private final String sourceName;
    private final String compilerName;
    private final byte[] spirvBytes;

    public VulkanicSpirvModule(
        VulkanicShaderStage stage,
        String entryPoint,
        byte[] spirvBytes,
        String sourceName,
        String compilerName
    ) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint must not be null");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName must not be null");
        this.compilerName = Objects.requireNonNull(compilerName, "compilerName must not be null");
        this.spirvBytes = Objects.requireNonNull(spirvBytes, "spirvBytes must not be null").clone();
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
            && Arrays.equals(spirvBytes, other.spirvBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(stage, entryPoint, sourceName, compilerName);
        result = 31 * result + Arrays.hashCode(spirvBytes);
        return result;
    }
}
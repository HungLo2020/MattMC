package net.sodium.client.render.chunk.vertex.format;

import net.minecraft.util.NativeLibraryLoader;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

public final class NativeStaticBlockModelCache {
    private static final int OK = 0;

    private static final MethodHandle CLEAR = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_static_model_cache_clear",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle REGISTER = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_static_model_cache_register",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle REGISTER_SELECTOR = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_native_model_selector_register",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle REGISTER_STATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_native_meshing_state_register",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_INT));

    private NativeStaticBlockModelCache() {
    }

    public static void clear() {
        check(invokeClear(), "native static model cache clear");
    }

    public static void register(int modelId, QuadRecordWriter writer, int quadCount) {
        if (modelId < 0) {
            throw new IllegalArgumentException("Invalid native static model id: " + modelId);
        }
        if (quadCount < 0) {
            throw new IllegalArgumentException("Invalid native static model quad count: " + quadCount);
        }

        ByteBuffer records = null;
        try {
            records = MemoryUtil.memAlloc(Math.max(1, quadCount) * NativeChunkMeshEncoder.STATIC_MODEL_QUAD_RECORD_STRIDE);
            long baseAddress = MemoryUtil.memAddress(records);
            for (int index = 0; index < quadCount; index++) {
                writer.write(baseAddress + (long) index * NativeChunkMeshEncoder.STATIC_MODEL_QUAD_RECORD_STRIDE,
                        index);
            }
            check(invokeRegister(modelId, quadCount == 0 ? 0L : baseAddress, quadCount,
                    NativeChunkMeshEncoder.STATIC_MODEL_QUAD_RECORD_STRIDE), "native static model cache register");
        } finally {
            if (records != null) {
                MemoryUtil.memFree(records);
            }
        }
    }

    public static void registerSelector(int selectorId, int kind, SelectorEntryWriter writer, int entryCount) {
        if (selectorId < 0) {
            throw new IllegalArgumentException("Invalid native selector id: " + selectorId);
        }
        if (entryCount < 0) {
            throw new IllegalArgumentException("Invalid native selector entry count: " + entryCount);
        }

        ByteBuffer records = null;
        try {
            records = MemoryUtil.memAlloc(Math.max(1, entryCount) * NativeChunkMeshEncoder.NATIVE_MODEL_SELECTOR_ENTRY_STRIDE);
            long baseAddress = MemoryUtil.memAddress(records);
            for (int index = 0; index < entryCount; index++) {
                writer.write(baseAddress + (long) index * NativeChunkMeshEncoder.NATIVE_MODEL_SELECTOR_ENTRY_STRIDE,
                        index);
            }
            check(invokeRegisterSelector(selectorId, kind, entryCount == 0 ? 0L : baseAddress, entryCount,
                    NativeChunkMeshEncoder.NATIVE_MODEL_SELECTOR_ENTRY_STRIDE), "native model selector register");
        } finally {
            if (records != null) {
                MemoryUtil.memFree(records);
            }
        }
    }

    public static void registerState(int stateId, int selectorId, int flags, int materialBits, int passId,
            int blockEmission, int renderType, int blockId, int fluidMaterialBits, int fluidPassId,
            int fluidBlockId, int skipGroup, int skipMask, int fluidType, float fluidOwnHeight, int fluidFalling,
            int offsetType, float maxHorizontalOffset, float maxVerticalOffset, int tintType,
            float fluidStillU0, float fluidStillU1, float fluidStillV0, float fluidStillV1, float fluidStillShrink,
            float fluidFlowU0, float fluidFlowU1, float fluidFlowV0, float fluidFlowV1, float fluidFlowShrink,
            float fluidOverlayU0, float fluidOverlayU1, float fluidOverlayV0, float fluidOverlayV1,
            float fluidOverlayShrink, int fluidOverlayValid) {
        check(invokeRegisterState(stateId, selectorId, flags, materialBits, passId, blockEmission, renderType,
                blockId, fluidMaterialBits, fluidPassId, fluidBlockId, skipGroup, skipMask, fluidType, fluidOwnHeight,
                fluidFalling, offsetType, maxHorizontalOffset, maxVerticalOffset, tintType,
                fluidStillU0, fluidStillU1, fluidStillV0, fluidStillV1, fluidStillShrink,
                fluidFlowU0, fluidFlowU1, fluidFlowV0, fluidFlowV1, fluidFlowShrink,
                fluidOverlayU0, fluidOverlayU1, fluidOverlayV0, fluidOverlayV1, fluidOverlayShrink,
                fluidOverlayValid),
                "native meshing state register");
    }

    public static void registerState(int stateId, int selectorId, int flags, int materialBits, int passId,
            int blockEmission, int renderType, int blockId, int fluidMaterialBits, int fluidPassId,
            int fluidBlockId, int skipGroup, int fluidType, float fluidOwnHeight, int fluidFalling,
            int offsetType, float maxHorizontalOffset, float maxVerticalOffset, int tintType,
            float fluidStillU0, float fluidStillU1, float fluidStillV0, float fluidStillV1, float fluidStillShrink,
            float fluidFlowU0, float fluidFlowU1, float fluidFlowV0, float fluidFlowV1, float fluidFlowShrink,
            float fluidOverlayU0, float fluidOverlayU1, float fluidOverlayV0, float fluidOverlayV1,
            float fluidOverlayShrink, int fluidOverlayValid) {
        registerState(stateId, selectorId, flags, materialBits, passId, blockEmission, renderType, blockId,
                fluidMaterialBits, fluidPassId, fluidBlockId, skipGroup, 0, fluidType, fluidOwnHeight,
                fluidFalling, offsetType, maxHorizontalOffset, maxVerticalOffset, tintType,
                fluidStillU0, fluidStillU1, fluidStillV0, fluidStillV1, fluidStillShrink,
                fluidFlowU0, fluidFlowU1, fluidFlowV0, fluidFlowV1, fluidFlowShrink,
                fluidOverlayU0, fluidOverlayU1, fluidOverlayV0, fluidOverlayV1, fluidOverlayShrink,
                fluidOverlayValid);
    }

    public static void registerState(int stateId, int selectorId, int flags, int materialBits, int passId,
            int blockEmission, int renderType, int blockId, int fluidMaterialBits, int fluidPassId,
            int fluidBlockId, int skipGroup, int fluidType, float fluidOwnHeight, int fluidFalling,
            int offsetType, float maxHorizontalOffset, float maxVerticalOffset, int tintType) {
        registerState(stateId, selectorId, flags, materialBits, passId, blockEmission, renderType, blockId,
                fluidMaterialBits, fluidPassId, fluidBlockId, skipGroup, 0, fluidType, fluidOwnHeight, fluidFalling,
                offsetType, maxHorizontalOffset, maxVerticalOffset, tintType,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0);
    }

    public static void registerState(int stateId, int selectorId, int flags, int materialBits, int passId,
            int blockEmission, int renderType, int blockId, int fluidMaterialBits, int fluidPassId,
            int fluidBlockId, int skipGroup) {
        registerState(stateId, selectorId, flags, materialBits, passId, blockEmission, renderType, blockId,
                fluidMaterialBits, fluidPassId, fluidBlockId, skipGroup, 0, 0.0F, 0, 0, 0.25F, 0.2F, 0);
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static int invokeClear() {
        try {
            return (int) CLEAR.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust static model cache clear downcall failed", throwable);
        }
    }

    private static int invokeRegister(int modelId, long quadAddress, int quadCount, int quadStride) {
        try {
            return (int) REGISTER.invokeExact(modelId, quadAddress, quadCount, quadStride);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust static model cache register downcall failed", throwable);
        }
    }

    private static int invokeRegisterSelector(int selectorId, int kind, long entryAddress, int entryCount,
            int entryStride) {
        try {
            return (int) REGISTER_SELECTOR.invokeExact(selectorId, kind, entryAddress, entryCount, entryStride);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native model selector register downcall failed", throwable);
        }
    }

    private static int invokeRegisterState(int stateId, int selectorId, int flags, int materialBits, int passId,
            int blockEmission, int renderType, int blockId, int fluidMaterialBits, int fluidPassId,
            int fluidBlockId, int skipGroup, int skipMask, int fluidType, float fluidOwnHeight, int fluidFalling,
            int offsetType, float maxHorizontalOffset, float maxVerticalOffset, int tintType,
            float fluidStillU0, float fluidStillU1, float fluidStillV0, float fluidStillV1, float fluidStillShrink,
            float fluidFlowU0, float fluidFlowU1, float fluidFlowV0, float fluidFlowV1, float fluidFlowShrink,
            float fluidOverlayU0, float fluidOverlayU1, float fluidOverlayV0, float fluidOverlayV1,
            float fluidOverlayShrink, int fluidOverlayValid) {
        try {
            return (int) REGISTER_STATE.invokeExact(stateId, selectorId, flags, materialBits, passId, blockEmission,
                    renderType, blockId, fluidMaterialBits, fluidPassId, fluidBlockId, skipGroup, skipMask, fluidType,
                    fluidOwnHeight, fluidFalling, offsetType, maxHorizontalOffset, maxVerticalOffset, tintType,
                    fluidStillU0, fluidStillU1, fluidStillV0, fluidStillV1, fluidStillShrink,
                    fluidFlowU0, fluidFlowU1, fluidFlowV0, fluidFlowV1, fluidFlowShrink,
                    fluidOverlayU0, fluidOverlayU1, fluidOverlayV0, fluidOverlayV1, fluidOverlayShrink,
                    fluidOverlayValid);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust native meshing state register downcall failed", throwable);
        }
    }

    @FunctionalInterface
    public interface QuadRecordWriter {
        void write(long recordAddress, int index);
    }

    @FunctionalInterface
    public interface SelectorEntryWriter {
        void write(long recordAddress, int index);
    }
}

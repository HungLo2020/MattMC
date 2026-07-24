package net.vulkanic.backends.vulkan;

import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.vulkanic.VulkanicShaderStage;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

final class NativeShadercCompiler {
    private static final int OK = 0;
    private static final MethodHandle COMPILE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_vulkan_shaderc_compile_glsl_to_spirv",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS));
    private static final MethodHandle FREE_BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_vulkan_shaderc_free_buffer",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private NativeShadercCompiler() {
    }

    static byte[] compile(VulkanicShaderStage stage, String source, String sourceName, String entryPoint) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] sourceNameBytes = sourceName.getBytes(StandardCharsets.UTF_8);
        byte[] entryPointBytes = entryPoint.getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sourceSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, sourceBytes);
            MemorySegment sourceNameSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, sourceNameBytes);
            MemorySegment entryPointSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, entryPointBytes);
            MemorySegment spirvPtrSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment spirvLenSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment errorPtrSegment = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment errorLenSegment = arena.allocate(ValueLayout.JAVA_LONG);

            int status;
            try (var zone = GraphicsFrameBenchmark.beginTracyZone("ffi.rust.shaderc.compile")) {
                GraphicsFrameBenchmark.tracyMessage("ffi.shaderc.compile source=" + sourceName + " bytes=" + sourceBytes.length);
                status = invokeCompile(
                    stage.ordinal(),
                    sourceSegment,
                    sourceBytes.length,
                    sourceNameSegment,
                    sourceNameBytes.length,
                    entryPointSegment,
                    entryPointBytes.length,
                    spirvPtrSegment,
                    spirvLenSegment,
                    errorPtrSegment,
                    errorLenSegment
                );
            }

            long spirvPtr = spirvPtrSegment.get(ValueLayout.JAVA_LONG, 0);
            long spirvLen = spirvLenSegment.get(ValueLayout.JAVA_LONG, 0);
            long errorPtr = errorPtrSegment.get(ValueLayout.JAVA_LONG, 0);
            long errorLen = errorLenSegment.get(ValueLayout.JAVA_LONG, 0);

            try {
                if (status != OK) {
                    String message = copyString(errorPtr, errorLen);
                    throw new IllegalStateException(message.isBlank()
                        ? "Shaderc failed to compile SPIR-V for '" + sourceName + "' with status " + status
                        : message);
                }

                if (spirvPtr == 0 || spirvLen <= 0 || spirvLen > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Shaderc returned an invalid SPIR-V buffer for '" + sourceName
                        + "': ptr=" + spirvPtr + ", len=" + spirvLen);
                }

                return copyBytes(spirvPtr, spirvLen);
            } finally {
                freeBuffer(spirvPtr, spirvLen);
                freeBuffer(errorPtr, errorLen);
            }
        }
    }

    private static byte[] copyBytes(long ptr, long len) {
        return MemorySegment.ofAddress(ptr)
            .reinterpret(len)
            .toArray(ValueLayout.JAVA_BYTE);
    }

    private static String copyString(long ptr, long len) {
        if (ptr == 0 || len <= 0) {
            return "";
        }
        return new String(copyBytes(ptr, len), StandardCharsets.UTF_8);
    }

    private static int invokeCompile(
        int stage,
        MemorySegment source,
        long sourceLen,
        MemorySegment sourceName,
        long sourceNameLen,
        MemorySegment entryPoint,
        long entryPointLen,
        MemorySegment spirvPtr,
        MemorySegment spirvLen,
        MemorySegment errorPtr,
        MemorySegment errorLen
    ) {
        try {
            return (int) COMPILE.invokeExact(
                stage,
                source,
                sourceLen,
                sourceName,
                sourceNameLen,
                entryPoint,
                entryPointLen,
                spirvPtr,
                spirvLen,
                errorPtr,
                errorLen
            );
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to invoke native Shaderc compiler", throwable);
        }
    }

    private static void freeBuffer(long ptr, long len) {
        if (ptr == 0) {
            return;
        }

        try {
            FREE_BUFFER.invokeExact(ptr, len);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to free native Shaderc buffer", throwable);
        }
    }
}

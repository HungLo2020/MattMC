package net.vulkanic.backends.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteOrder;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import net.vulkanic.VulkanicUniformReflectionType;
import org.junit.jupiter.api.Test;

class VulkanShaderProgramCoordinatorTest {
    @Test
    void shaderAndProgramLifecyclePreservesOpenGlDeletionSemantics() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int shader = coordinator.createShader(VulkanicShaderStage.VERTEX);
        int program = coordinator.createProgram();
        List<VulkanShaderProgramCoordinator.VirtualShader> released = new ArrayList<>();

        coordinator.attachShader(program, shader);
        coordinator.deleteShader(shader, released::add);

        assertTrue(released.isEmpty(), "attached shaders are only marked for deletion");
        assertTrue(coordinator.shader(shader).deletionPending);

        coordinator.detachShader(program, shader, released::add);

        assertEquals(1, released.size());
        assertNull(coordinator.shader(shader));
    }

    @Test
    void sourceUploadAndCompileResultsInvalidatePreviousNativeAndPayloadState() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int shader = coordinator.createShader(VulkanicShaderStage.FRAGMENT);
        VulkanShaderProgramCoordinator.VirtualShader shaderRecord = coordinator.requireShader(shader);
        shaderRecord.nativeShaderModuleHandle = 42L;
        shaderRecord.compiledModule = new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {9},
            "old",
            "old-normalized"
        );
        shaderRecord.compileStatus = true;
        List<VulkanShaderProgramCoordinator.VirtualShader> released = new ArrayList<>();

        coordinator.uploadShaderSource(shader, "void main(){}", released::add);

        assertEquals(List.of(shaderRecord), released);
        assertEquals("void main(){}", shaderRecord.source);
        assertNull(shaderRecord.compiledModule);
        assertFalse(shaderRecord.compileStatus);

        VulkanicSpirvModule module = new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {1, 2},
            "new",
            "new-normalized"
        );
        coordinator.markCompileSucceeded(shaderRecord, module);
        assertEquals(module, shaderRecord.compiledModule);
        assertTrue(shaderRecord.compileStatus);

        coordinator.markCompileFailed(shaderRecord, "bad source");
        assertNull(shaderRecord.compiledModule);
        assertFalse(shaderRecord.compileStatus);
        assertEquals("bad source", shaderRecord.infoLog);
    }

    @Test
    void deletingProgramReleasesOnlyDeletionPendingAttachedShadersAndUnbindsIt() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int pendingShader = coordinator.createShader(VulkanicShaderStage.VERTEX);
        int retainedShader = coordinator.createShader(VulkanicShaderStage.FRAGMENT);
        int program = coordinator.createProgram();
        List<VulkanShaderProgramCoordinator.VirtualShader> released = new ArrayList<>();

        coordinator.attachShader(program, pendingShader);
        coordinator.attachShader(program, retainedShader);
        coordinator.deleteShader(pendingShader, released::add);
        coordinator.bindProgram(program);

        coordinator.deleteProgram(program, released::add);

        assertEquals(1, released.size());
        assertNull(coordinator.program(program));
        assertNull(coordinator.shader(pendingShader));
        assertTrue(coordinator.shader(retainedShader) != null);
        assertEquals(0, coordinator.boundProgramId());
    }

    @Test
    void linkedExecutionSnapshotFreezesResourceLayoutAndOpaqueUniformValues() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
        program.linkStatus = true;
        program.linkedSpirvModules = List.of(new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {1, 2, 3, 4},
            "test-fragment",
            "normalized"
        ));
        program.fragmentOutputs = List.of(new VulkanicSpirvModule.FragmentOutput(0, "fragColor", "vec4"));
        program.activeUniformNames = List.of("Sampler0");
        program.activeUniforms = List.of(new VulkanShaderProgramCoordinator.ReflectedUniform(
            "Sampler0",
            1,
            VulkanicUniformReflectionType.SAMPLER_2D.toLegacyGlConstant()
        ));
        program.activeResourceBindings = List.of(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
            "Sampler0",
            PipelineDescriptor.ResourceType.SAMPLER,
            0,
            3
        ));
        program.opaqueResourceUniformValuesByIndex.put(0, 7);

        VulkanShaderProgramCoordinator.LinkedProgramExecutionSnapshot snapshot =
            coordinator.linkedExecutionSnapshot(programId, Set.of(VulkanicShaderStage.FRAGMENT));
        program.activeUniformNames = List.of("Changed");
        program.opaqueResourceUniformValuesByIndex.put(0, 9);

        assertEquals("Sampler0", snapshot.activeUniformNames().getFirst());
        assertEquals(7, snapshot.opaqueResourceUniformValuesByIndex().get(0));
        assertEquals(3, snapshot.resourceLayout().bindings().getFirst().binding());
        assertEquals(List.of(new VulkanicSpirvModule.FragmentOutput(0, "fragColor", "vec4")),
            snapshot.fragmentOutputs());
    }

    @Test
    void uniformLocationTokensAreInvalidatedWhenStandaloneStateIsRebuilt() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
        VulkanShaderProgramCoordinator.ReflectedUniform uniform =
            new VulkanShaderProgramCoordinator.ReflectedUniform(
                "uProj",
                1,
                VulkanicUniformReflectionType.FLOAT_MAT4.toLegacyGlConstant()
            );
        coordinator.installReflection(
            program,
            List.of("uProj"),
            List.of(uniform),
            List.of(ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME),
            List.of(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                0,
                0
            )),
            List.of("mat4 uProj;"),
            new int[] {1, 1, 1}
        );
        int token = coordinator.resolveUniformLocationToken(programId, program, "uProj", 0);
        assertTrue(coordinator.resolveUniformLocationRef(token) != null);

        coordinator.initializeStandaloneUniformState(program, Map.of("uProj", uniform));

        assertNull(coordinator.resolveUniformLocationRef(token));
        assertEquals(64, program.standaloneBackingSize);
        assertTrue(program.standaloneDirty);
    }

    @Test
    void standaloneUniformBackingCacheIsClosedWhenStateIsRebuilt() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
        VulkanShaderProgramCoordinator.ReflectedUniform uniform =
            new VulkanShaderProgramCoordinator.ReflectedUniform(
                "uTint",
                1,
                VulkanicUniformReflectionType.FLOAT_VEC4.toLegacyGlConstant()
            );
        AtomicInteger closes = new AtomicInteger();
        VulkanBuffer cachedSnapshot = new VulkanBuffer(
            0x1000L,
            0x2000L,
            VulkanicBuffer.USAGE_UNIFORM,
            64,
            "standalone-test",
            closes::incrementAndGet
        );
        program.standaloneGpuBuffer = cachedSnapshot;

        coordinator.installReflection(
            program,
            List.of("uTint"),
            List.of(uniform),
            List.of(ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME),
            List.of(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                0,
                0
            )),
            List.of("vec4 uTint;"),
            new int[] {1, 1, 1}
        );
        coordinator.initializeStandaloneUniformState(program, Map.of("uTint", uniform));

        assertNull(program.standaloneGpuBuffer);
        assertTrue(cachedSnapshot.isClosed());
        assertEquals(1, closes.get());
        assertTrue(program.standaloneDirty);
    }

    @Test
    void standaloneAndOpaqueUniformWritesUpdateOnlyTheirOwnedState() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
        VulkanShaderProgramCoordinator.ReflectedUniform sampler =
            new VulkanShaderProgramCoordinator.ReflectedUniform(
                "Sampler0",
                1,
                VulkanicUniformReflectionType.SAMPLER_2D.toLegacyGlConstant()
            );
        VulkanShaderProgramCoordinator.ReflectedUniform tint =
            new VulkanShaderProgramCoordinator.ReflectedUniform(
                "uTint",
                1,
                VulkanicUniformReflectionType.FLOAT_VEC4.toLegacyGlConstant()
            );
        coordinator.installReflection(
            program,
            List.of("Sampler0", "uTint"),
            List.of(sampler, tint),
            List.of(ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME),
            List.of(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                0,
                0
            )),
            List.of("vec4 uTint;"),
            new int[] {1, 1, 1}
        );
        coordinator.initializeStandaloneUniformState(program, Map.of("Sampler0", sampler, "uTint", tint));

        assertTrue(coordinator.captureOpaqueResourceUniformInt(program, 0, 5));
        assertFalse(coordinator.captureOpaqueResourceUniformInt(program, 1, 99));
        assertTrue(coordinator.writeStandaloneUniformFloats(program, 1, new float[] {0.25F, 0.5F, 0.75F, 1.0F}));

        assertEquals(5, program.opaqueResourceUniformValuesByIndex.get(0));
        VulkanShaderProgramCoordinator.StandaloneUniformField field = program.standaloneFieldsByLocation.get(1);
        assertArrayEquals(
            new float[] {0.25F, 0.5F, 0.75F, 1.0F},
            VulkanShaderProgramCoordinator.readStandaloneUniformFloats(
                field,
                program.standaloneBackingData.duplicate().order(ByteOrder.nativeOrder()),
                field.offsets()[0]
            )
        );
    }

    @Test
    void standaloneUniformPayloadVersionChangesOnlyWhenPackedBytesChange() {
        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
        VulkanShaderProgramCoordinator.ReflectedUniform tint =
            new VulkanShaderProgramCoordinator.ReflectedUniform(
                "uTint",
                1,
                VulkanicUniformReflectionType.FLOAT_VEC4.toLegacyGlConstant()
            );
        coordinator.installReflection(
            program,
            List.of("uTint"),
            List.of(tint),
            List.of(ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME),
            List.of(new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                0,
                0
            )),
            List.of("vec4 uTint;"),
            new int[] {1, 1, 1}
        );
        coordinator.initializeStandaloneUniformState(program, Map.of("uTint", tint));
        long initializedVersion = program.standalonePayloadVersion;

        assertTrue(coordinator.writeStandaloneUniformFloats(program, 0, new float[] {1.0F, 0.5F, 0.25F, 1.0F}));
        long changedVersion = program.standalonePayloadVersion;
        assertTrue(changedVersion > initializedVersion);
        program.standaloneDirty = false;

        assertTrue(coordinator.writeStandaloneUniformFloats(program, 0, new float[] {1.0F, 0.5F, 0.25F, 1.0F}));
        assertEquals(changedVersion, program.standalonePayloadVersion);
        assertFalse(program.standaloneDirty);

        assertTrue(coordinator.writeStandaloneUniformFloats(program, 0, new float[] {0.0F, 0.5F, 0.25F, 1.0F}));
        assertTrue(program.standalonePayloadVersion > changedVersion);
        assertTrue(program.standaloneDirty);
    }
}

package net.vulkanic.backends.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicUniformReflectionType;
import org.junit.jupiter.api.Test;

class VulkanShaderVariantPlannerTest {
    @Test
    void renderPipelineSourcePlanCarriesBindingsVertexInputsAndFragmentOutputs() {
        RenderPipeline pipeline = new TestRenderPipeline(
            ResourceLocation.withDefaultNamespace("pipeline/entity_cutout"),
            IrisVertexFormats.ENTITY,
            VertexFormat.Mode.QUADS,
            List.of("Sampler0"),
            List.of(new RenderPipeline.UniformDescription("DynamicTransforms", UniformType.UNIFORM_BUFFER))
        );

        VulkanShaderVariantPlanner.RenderPipelineSourcePlan plan =
            VulkanShaderVariantPlanner.planRenderPipelineSources(
                pipeline,
                "#version 330\n"
                    + "in vec3 Position;\n"
                    + "in vec4 mc_Entity;\n"
                    + "in vec3 at_midBlock;\n"
                    + "void main(){ gl_Position = vec4(Position, 1.0); }",
                "#version 330\n"
                    + "uniform sampler2D Sampler0;\n"
                    + "layout(location = 2) out vec4 brightColor;\n"
                    + "void main(){ brightColor = texture(Sampler0, vec2(0.0)); }"
            );

        assertTrue(plan.vertexSource().contains("layout(location = 0) in vec3 Position;"));
        assertTrue(plan.vertexSource().contains("layout(location = 8) in vec4 mc_Entity;"));
        assertTrue(plan.vertexSource().contains("layout(location = 9) in vec3 at_midBlock;"));
        assertTrue(plan.fragmentSource().contains("layout(set = 0, binding = 0) uniform sampler2D Sampler0;"));
        assertTrue(plan.vertexInputs().stream().anyMatch(input -> input.location() == 8));
        assertEquals(
            List.of(new VulkanShaderVariantPlanner.ReflectedFragmentOutput(2, "brightColor", "vec4")),
            plan.fragmentOutputs()
        );
        assertEquals(2, plan.standaloneUniformBindingIndex());
    }

    @Test
    void linkedReflectionNormalizesResourcesAndComputeWorkGroup() {
        VulkanShaderVariantPlanner.LinkedProgramReflectionPlan plan =
            VulkanShaderVariantPlanner.planLinkedProgramReflection(List.of(
                new VulkanShaderVariantPlanner.ShaderStageSourceRequest(
                    1,
                    VulkanicShaderStage.VERTEX,
                    "shader-1",
                    "#version 450\n"
	                        + "layout(std140, binding = 4) uniform iris_DynamicTransforms { mat4 ModelViewMat; };\n"
	                        + "layout(binding = 7) uniform sampler2D Sampler0;\n"
	                        + "uniform vec4 uColor;\n"
	                        + "void main(){ gl_Position = ModelViewMat * (uColor + texture(Sampler0, vec2(0.0))); }"
	                ),
                new VulkanShaderVariantPlanner.ShaderStageSourceRequest(
                    2,
                    VulkanicShaderStage.COMPUTE,
                    "shader-2",
	                    "#version 450\n"
	                        + "layout(local_size_x = 8, local_size_y = 4, local_size_z = 2) in;\n"
	                        + "layout(binding = 9, rgba8) uniform image2D OutImage;\n"
	                        + "void main(){ imageStore(OutImage, ivec2(0), vec4(1.0)); }"
	                )
	            ));

	        assertTrue(plan.activeUniformBlocks().contains("DynamicTransforms"));
	        assertTrue(plan.activeUniformNames().contains("uColor"));
        assertArrayEquals(new int[] {8, 4, 2}, plan.computeWorkGroupSize());
        assertTrue(plan.standaloneUniformDeclarations().contains("vec4 uColor;"));
        assertTrue(plan.activeResourceBindings().stream().anyMatch(binding ->
            binding.name().equals("DynamicTransforms")
                && binding.type() == PipelineDescriptor.ResourceType.UNIFORM_BUFFER
                && binding.binding() == 4));
        assertTrue(plan.activeResourceBindings().stream().anyMatch(binding ->
            binding.name().equals("Sampler0")
                && binding.type() == PipelineDescriptor.ResourceType.SAMPLER
                && binding.binding() == 7));
        assertTrue(plan.activeResourceBindings().stream().anyMatch(binding ->
            binding.name().equals("OutImage")
                && binding.type() == PipelineDescriptor.ResourceType.STORAGE_IMAGE
                && binding.binding() == 9));
    }

    @Test
    void linkedStagePlanInjectsResourcesAndFreezesVertexInputs() {
        List<VulkanShaderProgramCoordinator.ReflectedResourceBinding> resources = List.of(
            new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                "Sampler0",
                PipelineDescriptor.ResourceType.SAMPLER,
                0,
                3
            ),
            new VulkanShaderProgramCoordinator.ReflectedResourceBinding(
                ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                0,
                4
            )
        );

        VulkanShaderVariantPlanner.LinkedShaderStagePlan plan =
            VulkanShaderVariantPlanner.planLinkedShaderStage(
                new VulkanShaderVariantPlanner.ShaderStageSourceRequest(
                    10,
                    VulkanicShaderStage.VERTEX,
                    "shader-10",
                    "#version 450\n"
                        + "in vec3 Position;\n"
                        + "uniform sampler2D Sampler0;\n"
                        + "uniform float uScale;\n"
                        + "void main(){ gl_Position = vec4(Position * uScale, 1.0); }"
                ),
                java.util.Map.of("Position", 0),
                resources,
                List.of("float uScale;"),
                (stage, source, sourceName, standaloneUniformDeclarations, standaloneUniformBindingIndex) ->
                    ShadercSpirvCompiler.normalizeForVulkan(
                        stage,
                        source,
                        sourceName,
                        standaloneUniformDeclarations,
                        standaloneUniformBindingIndex
                    )
            );

        assertTrue(plan.reboundSource().contains("layout(location = 0) in vec3 Position;"));
        assertTrue(plan.reboundSource().contains("layout(set = 0, binding = 3) uniform sampler2D Sampler0;"));
        assertTrue(plan.usesGeneratedStandaloneBlock());
        assertEquals(4, plan.standaloneUniformBindingIndex());
        assertEquals(List.of("float uScale;"), plan.standaloneUniformDeclarations());
        assertEquals(List.of(new VulkanShaderProgramCoordinator.ReflectedVertexInput(0, "vec3")), plan.vertexInputs());
    }

    @Test
    void compatibilityVertexInputsRemainPolicyOwnedByVariantPlanner() {
        RenderPipeline entityPipeline = new TestRenderPipeline(
            ResourceLocation.withDefaultNamespace("pipeline/entity_cutout_no_cull"),
            IrisVertexFormats.ENTITY,
            VertexFormat.Mode.QUADS
        );
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipelineAndSpirvModules(
            entityPipeline,
            List.of(
                new net.vulkanic.VulkanicSpirvModule(VulkanicShaderStage.VERTEX, "main", new byte[] {1}, "v", "test"),
                new net.vulkanic.VulkanicSpirvModule(VulkanicShaderStage.FRAGMENT, "main", new byte[] {2}, "f", "test")
            )
        );

        PipelineDescriptor compatible = VulkanShaderVariantPlanner.withCompatibilityVertexInputState(descriptor);

        assertTrue(compatible.getVertexInputState().attributes().stream().anyMatch(attribute ->
            attribute.location() == 8
                && attribute.binding() == VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING
                && attribute.format() == PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT));
        assertFalse(descriptor == compatible);
    }

    @Test
    void standaloneUniformOnlyChangesDoNotAlterVariantPlanIdentityInputs() {
        VulkanShaderVariantPlanner.LinkedProgramReflectionPlan plan =
            VulkanShaderVariantPlanner.planLinkedProgramReflection(List.of(
                new VulkanShaderVariantPlanner.ShaderStageSourceRequest(
                    1,
                    VulkanicShaderStage.FRAGMENT,
                    "shader-1",
                    "#version 450\n"
                        + "uniform sampler2D Sampler0;\n"
                        + "uniform mat4 ModelViewMat;\n"
                        + "layout(location = 0) out vec4 fragColor;\n"
                        + "void main(){ fragColor = texture(Sampler0, vec2(ModelViewMat[0][0])); }"
                )
            ));

        VulkanShaderProgramCoordinator coordinator = new VulkanShaderProgramCoordinator();
        int programId = coordinator.createProgram();
        VulkanShaderProgramCoordinator.VirtualProgram program = coordinator.requireProgram(programId);
	        coordinator.installReflection(
	            program,
	            plan.activeUniformNames(),
            plan.activeUniforms(),
            plan.activeUniformBlocks(),
            plan.activeResourceBindings(),
            plan.standaloneUniformDeclarations(),
            plan.computeWorkGroupSize()
	        );
	        coordinator.initializeStandaloneUniformState(program, plan.activeUniformsByName());
	        program.linkStatus = true;
	        VulkanShaderProgramCoordinator.LinkedProgramExecutionSnapshot before =
	            coordinator.linkedExecutionSnapshot(programId, java.util.Set.of(VulkanicShaderStage.FRAGMENT));

	        VulkanShaderProgramCoordinator.ReflectedUniform matrix = plan.activeUniformsByName().get("ModelViewMat");
	        assertEquals(VulkanicUniformReflectionType.FLOAT_MAT4.toLegacyGlConstant(), matrix.legacyType());
	        int matrixLocation = plan.activeUniformNames().indexOf("ModelViewMat");
	        assertTrue(matrixLocation >= 0);
	        assertTrue(coordinator.writeStandaloneUniformFloats(program, matrixLocation, new float[] {
	            2.0F, 0.0F, 0.0F, 0.0F,
	            0.0F, 2.0F, 0.0F, 0.0F,
	            0.0F, 0.0F, 2.0F, 0.0F,
	            0.0F, 0.0F, 0.0F, 2.0F
	        }));
        VulkanShaderProgramCoordinator.LinkedProgramExecutionSnapshot after =
            coordinator.linkedExecutionSnapshot(programId, java.util.Set.of(VulkanicShaderStage.FRAGMENT));

        assertEquals(before.resourceLayout(), after.resourceLayout());
        assertEquals(before.activeResourceBindings(), after.activeResourceBindings());
        assertEquals(before.activeUniforms(), after.activeUniforms());
    }

	    private static final class TestRenderPipeline extends RenderPipeline {
	        private final VertexFormat testVertexFormat;

	        private TestRenderPipeline(ResourceLocation location, VertexFormat vertexFormat, VertexFormat.Mode mode) {
	            this(location, vertexFormat, mode, List.of(), List.of());
	        }

        private TestRenderPipeline(
            ResourceLocation location,
            VertexFormat vertexFormat,
            VertexFormat.Mode mode,
            List<String> samplers,
            List<RenderPipeline.UniformDescription> uniforms
        ) {
            super(
                location,
                ResourceLocation.withDefaultNamespace("core/particle"),
                ResourceLocation.withDefaultNamespace("core/particle"),
                ShaderDefines.builder().build(),
                samplers,
                uniforms,
                Optional.<BlendFunction>empty(),
                DepthTestFunction.LEQUAL_DEPTH_TEST,
                PolygonMode.FILL,
                true,
                true,
                true,
                true,
                LogicOp.NONE,
	                vertexFormat,
	                mode,
	                0.0f,
	                0.0f,
	                0
	            );
	            this.testVertexFormat = vertexFormat;
	        }

	        @Override
	        public VertexFormat getVertexFormat() {
	            return this.testVertexFormat;
	        }
	    }
}

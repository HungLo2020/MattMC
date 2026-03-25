package net.irisshaders.iris.compat.dh;

import com.google.common.primitives.Ints;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicDepthCompareOp;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicProgramHandle;
import net.vulkanic.VulkanicShaderHandle;
import net.vulkanic.VulkanicVertexAttributeType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IrisGenericRenderProgram implements IDhApiGenericObjectShaderProgram {
	// Uniforms
	public final int modelViewUniform;
	public final int modelViewInverseUniform;
	public final int projectionUniform;
	public final int projectionInverseUniform;
	public final int normalMatrix3fUniform;
	// DH-specific projection uniforms
	public final int dhProjectionUniform;
	public final int dhProjectionInverseUniform;
	// Fog/Clip Uniforms
	private final int id;
	private final ProgramUniforms uniforms;
	private final CustomUniforms customUniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final BlendModeOverride blend;
	private final BufferBlendOverride[] bufferBlendOverrides;

	private final int instancedShaderOffsetChunkUniform;
	private final int instancedShaderOffsetSubChunkUniform;
	private final int instancedShaderCameraChunkPosUniform;
	private final int instancedShaderCameraSubChunkPosUniform;
	private final int instancedShaderProjectionModelViewMatrixUniform;
	private final int va;
	private final int uBlockLight;
	private final int uSkyLight;

	// This will bind  AbstractVertexAttribute
	private IrisGenericRenderProgram(String name, boolean isShadowPass, boolean translucent, BlendModeOverride override, BufferBlendOverride[] bufferBlendOverrides, String vertex, String tessControl, String tessEval, String geometry, String fragment, CustomUniforms customUniforms, IrisRenderingPipeline pipeline) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicProgramHandle program = VulkanicAPI.createShaderProgramHandle(ctx);
		id = program.value();

		VulkanicAPI.setAttributeLocation(ctx, this.id, 0, "vPosition");

		this.bufferBlendOverrides = bufferBlendOverrides;

		GlShader vert = new GlShader(ShaderType.VERTEX, name + ".vsh", vertex);
		VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(vert.getHandle()));

		GlShader tessCont = null;
		if (tessControl != null) {
			tessCont = new GlShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", tessControl);
			VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(tessCont.getHandle()));
		}

		GlShader tessE = null;
		if (tessEval != null) {
			tessE = new GlShader(ShaderType.TESSELATION_EVAL, name + ".tes", tessEval);
			VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(tessE.getHandle()));
		}

		GlShader geom = null;
		if (geometry != null) {
			geom = new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometry);
			VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(geom.getHandle()));
		}

		GlShader frag = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragment);
		VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(frag.getHandle()));

		VulkanicAPI.linkProgram(ctx, program);
		if (!VulkanicAPI.isProgramLinkSuccessful(ctx, program)) {
			String message = "Shader link error in Iris DH program! Details: " + VulkanicAPI.getProgramInfoLog(ctx, program);
			this.free();
			throw new RuntimeException(message);
		} else {
			VulkanicAPI.bindShaderProgram(ctx, this.id);
		}

		vert.destroy();
		frag.destroy();

		if (tessCont != null) tessCont.destroy();
		if (tessE != null) tessE.destroy();
		if (geom != null) geom.destroy();

		blend = override;
		ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, id);
		ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(id, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
		customUniforms.assignTo(uniformBuilder);
		BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
		ProgramImages.Builder builder = ProgramImages.builder(id);
		pipeline.addGbufferOrShadowSamplers(samplerBuilder, builder, isShadowPass ? pipeline::getFlippedBeforeShadow : () -> translucent ? pipeline.getFlippedAfterTranslucent() : pipeline.getFlippedAfterPrepare(), isShadowPass, false, true, false);
		customUniforms.mapholderToPass(uniformBuilder, this);
		this.uniforms = uniformBuilder.buildUniforms();
		this.customUniforms = customUniforms;
		samplers = samplerBuilder.build();
		images = builder.build();

		this.va = VulkanicAPI.createVertexArray(ctx);
		VulkanicAPI.bindVertexArray(ctx, va);
		VulkanicAPI.setVertexAttribPointer(ctx, 0, 3, VulkanicVertexAttributeType.FLOAT, false, 0, 0);
		VulkanicAPI.enableVertexAttribArray(ctx, 0);

		projectionUniform = tryGetUniformLocation2("iris_ProjectionMatrix");
		projectionInverseUniform = tryGetUniformLocation2("iris_ProjectionMatrixInverse");
		modelViewUniform = tryGetUniformLocation2("iris_ModelViewMatrix");
		modelViewInverseUniform = tryGetUniformLocation2("iris_ModelViewMatrixInverse");
		normalMatrix3fUniform = tryGetUniformLocation2("iris_NormalMatrix");

		// DH-specific projection uniforms
		dhProjectionUniform = tryGetUniformLocation2("dhProjection");
		dhProjectionInverseUniform = tryGetUniformLocation2("dhProjectionInverse");

		// Log DH uniform locations for debugging
		//Iris.logger.info("[DH-SHADER-UNIFORMS-GENERIC] Program: " + name);
		//Iris.logger.info("[DH-SHADER-UNIFORMS-GENERIC] dhProjection uniform location: " + dhProjectionUniform);
		//Iris.logger.info("[DH-SHADER-UNIFORMS-GENERIC] dhProjectionInverse uniform location: " + dhProjectionInverseUniform);
		//Iris.logger.info("[DH-SHADER-UNIFORMS-GENERIC] iris_ProjectionMatrix uniform location: " + projectionUniform);
		//Iris.logger.info("[DH-SHADER-UNIFORMS-GENERIC] iris_ProjectionMatrixInverse uniform location: " + projectionInverseUniform);

		this.instancedShaderOffsetChunkUniform = this.tryGetUniformLocation2("uOffsetChunk");
		this.instancedShaderOffsetSubChunkUniform = this.tryGetUniformLocation2("uOffsetSubChunk");
		this.instancedShaderCameraChunkPosUniform = this.tryGetUniformLocation2("uCameraPosChunk");
		this.instancedShaderCameraSubChunkPosUniform = this.tryGetUniformLocation2("uCameraPosSubChunk");
		this.instancedShaderProjectionModelViewMatrixUniform = this.tryGetUniformLocation2("uProjectionMvm");
		this.uBlockLight = this.tryGetUniformLocation2("uBlockLight");
		this.uSkyLight = this.tryGetUniformLocation2("uSkyLight");
	}

	public static IrisGenericRenderProgram createProgram(String name, boolean isShadowPass, boolean translucent, ProgramSource source, CustomUniforms uniforms, IrisRenderingPipeline pipeline) {
		Map<PatchShaderType, String> transformed = TransformPatcher.patchDHGeneric(
			name,
			source.getVertexSource().orElseThrow(RuntimeException::new),
			source.getTessControlSource().orElse(null),
			source.getTessEvalSource().orElse(null),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(RuntimeException::new),
			pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
		String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);
		ShaderPrinter.printProgram(name + "_g")
			.addSources(transformed)
			.setName("dh_" + name + "_g")
			.print();

		List<BufferBlendOverride> bufferOverrides = new ArrayList<>();

		source.getDirectives().getBufferBlendOverrides().forEach(information -> {
			int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
			if (index > -1) {
				bufferOverrides.add(new BufferBlendOverride(index, information.blendMode()));
			}
		});

		return new IrisGenericRenderProgram(name, isShadowPass, translucent, source.getDirectives().getBlendModeOverride().orElse(null), bufferOverrides.toArray(BufferBlendOverride[]::new), vertex, tessControl, tessEval, geometry, fragment, uniforms, pipeline);
	}

	// Noise Uniforms

	private static int getChunkPosFromDouble(double value) {
		return (int) Math.floor(value / 16);
	}

	private static float getSubChunkPosFromDouble(double value) {
		double chunkPos = Math.floor(value / 16);
		return (float) (value - chunkPos * 16);
	}

	public int tryGetUniformLocation2(CharSequence name) {
		return VulkanicAPI.getUniformLocation(VulkanicAPI.getCommandContext(), this.id, name);
	}

	public void setUniform(int index, Matrix4f matrix) {
		if (index == -1 || matrix == null) return;
		CommandContext ctx = VulkanicAPI.getCommandContext();

		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.callocFloat(16);
			matrix.get(buffer);
			buffer.rewind();

			VulkanicAPI.setUniformMatrix4fv(ctx, index, false, buffer);
		}
	}

	public void setUniform(int index, Matrix3f matrix) {
		if (index == -1) return;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.callocFloat(9);
			matrix.get(buffer);
			buffer.rewind();

			IrisRenderSystem.uniformMatrix3fv(index, false, buffer);
		}
	}

	// Override ShaderProgram.bind()
	public void bind(DhApiRenderParam renderParam) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindVertexArray(ctx, va);
		VulkanicAPI.bindShaderProgram(ctx, id);
		if (blend != null) blend.apply();

		for (BufferBlendOverride override : bufferBlendOverrides) {
			override.apply();
		}

		setUniform(modelViewUniform, toJOML(renderParam.dhModelViewMatrix));
		setUniform(modelViewInverseUniform, toJOML(renderParam.dhModelViewMatrix).invert());
		setUniform(projectionUniform, toJOML(renderParam.dhProjectionMatrix));
		setUniform(projectionInverseUniform, toJOML(renderParam.dhProjectionMatrix).invert());
		setUniform(normalMatrix3fUniform, toJOML(renderParam.dhModelViewMatrix).invert().transpose3x3(new Matrix3f()));
		
		// Set DH-specific projection uniforms (these are the same as the iris ones)
		setUniform(dhProjectionUniform, toJOML(renderParam.dhProjectionMatrix));
		setUniform(dhProjectionInverseUniform, toJOML(renderParam.dhProjectionMatrix).invert());
		
		Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
		int lightmapTextureId = IrisRenderSystem.getTextureBinding(2);
		IrisRenderSystem.bindTextureToUnit(IrisSamplers.LIGHTMAP_TEXTURE_UNIT, lightmapTextureId);
		this.setUniform(this.instancedShaderProjectionModelViewMatrixUniform, toJOML(renderParam.dhProjectionMatrix).mul(toJOML(renderParam.dhModelViewMatrix)));

		samplers.update();
		uniforms.update();

		customUniforms.push(this);

		images.update();
	}

	public void unbind() {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindVertexArray(ctx, 0);
		VulkanicAPI.bindShaderProgram(ctx, 0);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		BlendModeOverride.restore();
	}

	@Override
	public void bindVertexBuffer(int i) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.VERTEX, i);
		VulkanicAPI.setVertexAttribPointer(ctx, 0, 3, VulkanicVertexAttributeType.FLOAT, false, 12, 0);
	}

	@Override
	public boolean overrideThisFrame() {
		return Iris.isPackInUseQuick();
	}

	@Override
	public int getId() {
		return id;
	}

	public void free() {
		VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(id));
	}

	public void fillIndirectUniformData(DhApiRenderParam dhApiRenderParam, DhApiRenderableBoxGroupShading dhApiRenderableBoxGroupShading, IDhApiRenderableBoxGroup boxGroup, DhApiVec3d camPos) {
		bind(dhApiRenderParam);
		CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.setDepthTestEnabled(ctx, true);
		VulkanicAPI.setDepthFunc(ctx, VulkanicDepthCompareOp.LEQUAL);
		this.setUniform(this.instancedShaderOffsetChunkUniform,
			new DhApiVec3i(
				getChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
				getChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
				getChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
			));
		this.setUniform(this.instancedShaderOffsetSubChunkUniform,
			new DhApiVec3f(
				getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
				getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
				getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
			));

		this.setUniform(this.instancedShaderCameraChunkPosUniform,
			new DhApiVec3i(
				getChunkPosFromDouble(camPos.x),
				getChunkPosFromDouble(camPos.y),
				getChunkPosFromDouble(camPos.z)
			));
		this.setUniform(this.instancedShaderCameraSubChunkPosUniform,
			new DhApiVec3f(
				getSubChunkPosFromDouble(camPos.x),
				getSubChunkPosFromDouble(camPos.y),
				getSubChunkPosFromDouble(camPos.z)
			));
		this.setUniform(this.uBlockLight,
			boxGroup.getBlockLight());
		this.setUniform(this.uSkyLight,
			boxGroup.getSkyLight());

	}

	@Override
	public void fillSharedDirectUniformData(DhApiRenderParam dhApiRenderParam, DhApiRenderableBoxGroupShading dhApiRenderableBoxGroupShading, IDhApiRenderableBoxGroup iDhApiRenderableBoxGroup, DhApiVec3d dhApiVec3d) {
		throw new IllegalStateException("Only indirect is supported with Iris.");
	}

	@Override
	public void fillDirectUniformData(DhApiRenderParam dhApiRenderParam, IDhApiRenderableBoxGroup iDhApiRenderableBoxGroup, DhApiRenderableBox dhApiRenderableBox, DhApiVec3d dhApiVec3d) {
		throw new IllegalStateException("Only indirect is supported with Iris.");
	}

	private Matrix4f toJOML(DhApiMat4f mat4f) {
		return new Matrix4f().setTransposed(mat4f.getValuesAsArray());
	}

	private void setUniform(int index, int value) {
		VulkanicAPI.setUniform1i(VulkanicAPI.getCommandContext(), index, value);
	}

	private void setUniform(int index, DhApiVec3f pos) {
		VulkanicAPI.setUniform3f(VulkanicAPI.getCommandContext(), index, pos.x, pos.y, pos.z);
	}

	private void setUniform(int index, DhApiVec3i pos) {
		VulkanicAPI.setUniform3i(VulkanicAPI.getCommandContext(), index, pos.x, pos.y, pos.z);
	}

}

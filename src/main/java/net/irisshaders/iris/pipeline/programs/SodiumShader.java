package net.irisshaders.iris.pipeline.programs;

import com.google.common.collect.ImmutableSet;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.systems.RenderPass;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import net.sodium.client.render.chunk.shader.RenderPassChunkShaderInterface;
import net.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.sodium.client.util.FogParameters;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendInformation;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pbr.TextureTracker;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureTarget;
import javax.annotation.Nonnull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public class SodiumShader implements RenderPassChunkShaderInterface {
	@Nonnull
	private static final ResourceLocation BLOCK_ATLAS_LOCATION = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"));

	private final GlUniformMatrix4f uniformModelViewMatrix;
	private final GlUniformMatrix4f uniformModelViewMatrixInv;
	private final GlUniformMatrix4f uniformProjectionMatrix;
	private final GlUniformMatrix4f uniformProjectionMatrixInv;
	private final GlUniformMatrix3f uniformNormalMatrix;
	private final GlUniformFloat3v uniformRegionOffset;
	private final GlUniformFloat2v uniformTexCoordShrink;
	private final ProgramImages images;
	private final ProgramSamplers samplers;
	private final ProgramUniforms uniforms;
	private final CustomUniforms customUniforms;
	private final BlendModeOverride blendModeOverride;
	private final List<BufferBlendInformation> bufferBlendInformations;
	private final List<BufferBlendOverride> bufferBlendOverrides;
	private final float alphaTest;
	private final boolean containsTessellation;
	private boolean isShadowPass;

	public SodiumShader(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, ShaderBindingContext context,
						int handle, BlendModeOverride blendModeOverride,
						List<BufferBlendInformation> bufferBlendInformations,
						CustomUniforms customUniforms, Supplier<ImmutableSet<Integer>> flipState, float alphaTest,
						boolean containsTessellation) {
		this.uniformModelViewMatrix = context.bindUniformOptional("iris_ModelViewMatrix", GlUniformMatrix4f::new);
		this.uniformModelViewMatrixInv = context.bindUniformOptional("iris_ModelViewMatrixInverse", GlUniformMatrix4f::new);
		this.uniformNormalMatrix = context.bindUniformOptional("iris_NormalMatrix", GlUniformMatrix3f::new);
		this.uniformProjectionMatrix = context.bindUniformOptional("iris_ProjectionMatrix", GlUniformMatrix4f::new);
		this.uniformProjectionMatrixInv = context.bindUniformOptional("iris_ProjectionMatrixInverse", GlUniformMatrix4f::new);
		this.uniformRegionOffset = context.bindUniformOptional("u_RegionOffset", GlUniformFloat3v::new);
		this.uniformTexCoordShrink = context.bindUniformOptional("u_TexCoordShrink", GlUniformFloat2v::new);

		this.alphaTest = alphaTest;
		this.containsTessellation = containsTessellation;

		isShadowPass = pass == SodiumPrograms.Pass.SHADOW || pass == SodiumPrograms.Pass.SHADOW_CUTOUT;

		this.uniforms = buildUniforms(pass, handle, customUniforms);
		this.customUniforms = customUniforms;
		this.samplers = buildSamplers(pipeline, pass, handle, isShadowPass, flipState);
		this.images = buildImages(pipeline, pass, handle, isShadowPass, flipState);

		this.blendModeOverride = blendModeOverride;
		this.bufferBlendInformations = List.copyOf(bufferBlendInformations);
		this.bufferBlendOverrides = this.bufferBlendInformations.stream()
			.map(information -> new BufferBlendOverride(information.index(), information.blendMode()))
			.toList();
	}

	private ProgramUniforms buildUniforms(SodiumPrograms.Pass pass, int handle, CustomUniforms customUniforms) {
		ProgramUniforms.Builder builder = ProgramUniforms.builder(pass.name().toLowerCase(Locale.ROOT), handle);
		CommonUniforms.addDynamicUniforms(builder, FogMode.PER_VERTEX);
		customUniforms.assignTo(builder);
		BuiltinReplacementUniforms.addBuiltinReplacementUniforms(builder);
		customUniforms.mapholderToPass(builder, this);
		return builder.buildUniforms();
	}

	private ProgramSamplers buildSamplers(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, int handle,
										  boolean isShadowPass, Supplier<ImmutableSet<Integer>> flipState) {
		ProgramSamplers.Builder builder = ProgramSamplers.builder(handle, IrisSamplers.SODIUM_RESERVED_TEXTURE_UNITS);
		pipeline.addGbufferOrShadowSamplers(builder, ProgramImages.builder(handle),
			flipState, isShadowPass, true, true, false);
		return builder.build();
	}

	private ProgramImages buildImages(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, int handle,
									  boolean isShadowPass, Supplier<ImmutableSet<Integer>> flipState) {
		ProgramImages.Builder builder = ProgramImages.builder(handle);
		pipeline.addGbufferOrShadowSamplers(ProgramSamplers.builder(handle, IrisSamplers.SODIUM_RESERVED_TEXTURE_UNITS),
			builder, flipState, isShadowPass, true, true, false);
		return builder.build();
	}

	@Override
	public void setRegionOffset(float x, float y, float z) {
		if (uniformRegionOffset != null) {
			uniformRegionOffset.set(x, y, z);
		}
	}

	@Override
	public void setModelViewMatrix(Matrix4fc matrix) {
		if (uniformModelViewMatrix != null) {
			uniformModelViewMatrix.set(matrix);
		}

		Matrix4f invertedMatrix = matrix.invert(new Matrix4f());

		if (uniformModelViewMatrixInv != null) {
			uniformModelViewMatrixInv.set(invertedMatrix);
		}

		if (uniformNormalMatrix != null) {
			Matrix3f normalMatrix = invertedMatrix.transpose3x3(new Matrix3f());
			uniformNormalMatrix.set(normalMatrix);
		}
	}

	@Override
	public void setProjectionMatrix(Matrix4fc matrix) {
		if (uniformProjectionMatrix != null) {
			uniformProjectionMatrix.set(matrix);
		}

		if (uniformProjectionMatrixInv != null) {
			Matrix4f invertedMatrix = matrix.invert(new Matrix4f());

			uniformProjectionMatrixInv.set(invertedMatrix);
		}
	}

	@SuppressWarnings("null")
	@Override
	public void setupState(TerrainRenderPass pass, FogParameters fogParameters) {
		DepthColorStorage.unlockDepthColor();

		applyBlendModes();
		TextureTracker.INSTANCE.onSetShaderTexture(0, pass.getAtlas());
		updateUniforms();
		images.update();


		if (isShadowPass) {
			VulkanicAPI.setCullFaceEnabled(VulkanicAPI.getCommandContext(), false);
		}
		@Nonnull ResourceLocation blockAtlasLocation = BLOCK_ATLAS_LOCATION;

		var textureAtlas = Minecraft.getInstance()
			.getTextureManager()
			.getTexture(blockAtlasLocation);
		textureAtlas.setFilter(false, textureAtlas.getTexture().getMipLevels() > 1);

		// There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
		// area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
		// offset texture coordinates in the vertex format by one texel, we also need to undo that here.
		double subTexelPrecision = (1 << RenderDevice.instance().getSubTexelPrecisionBits());
		double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

	if (this.uniformTexCoordShrink != null) {
			this.uniformTexCoordShrink.set(
				(float) (subTexelOffset - (((1.0D / ((TextureAtlas) textureAtlas).getWidth()) / subTexelPrecision))),
				(float) (subTexelOffset - (((1.0D / ((TextureAtlas) textureAtlas).getHeight()) / subTexelPrecision)))
			);
		}
		bindTextures(pass.getAtlas());

		if (containsTessellation) {
			ImmediateState.usingTessellation = true;
		}
	}

	private void bindTextures(GpuTextureView atlas) {
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
		atlas.texture().flushModeChanges2D();
		IrisRenderSystem.bindTextureToUnit(0, net.vulkanic.VulkanicCoreAPI.textureId(atlas));
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(0);
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, atlas.baseMipLevel());
		VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, atlas.baseMipLevel() + atlas.mipLevels() - 1);
		atlas.texture().flushModeChanges2D();

		GpuTextureView lightmap = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();
		lightmap.texture().flushModeChanges2D();
		IrisRenderSystem.bindTextureToUnit(2, net.vulkanic.VulkanicCoreAPI.textureId(lightmap));
		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(IrisSamplers.LIGHTMAP_TEXTURE_UNIT);
	}

	private void applyBlendModes() {
		if (blendModeOverride != null) {
			blendModeOverride.apply();
		}
		bufferBlendOverrides.forEach(BufferBlendOverride::apply);
	}

	private void updateUniforms() {
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest);
		samplers.update();
		uniforms.update();
		customUniforms.push(this);
	}

	@Override
	public void bindRenderPassResources(RenderPass renderPass, TerrainRenderPass pass) {
		samplers.bindToRenderPass(renderPass);
	}

	@Override
	public java.util.Collection<String> getRenderPassSamplerNames() {
		return samplers.getRenderPassSamplerNames();
	}

	@Override
	public java.util.Collection<BufferBlendInformation> getRenderPassBufferBlendOverrides() {
		return bufferBlendInformations;
	}

	@Override
	public void resetState() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		BlendModeOverride.restore();
		ImmediateState.usingTessellation = false;
	}
}

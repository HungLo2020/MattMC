package net.sodium.client.render.chunk.shader;

import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.systems.RenderPass;
import net.sodium.client.gl.device.GLRenderDevice;
import net.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.sodium.client.gl.shader.uniform.GlUniformInt;
import net.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureTarget;
import org.joml.Matrix4fc;
import javax.annotation.Nonnull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A forward-rendering shader program for chunks.
 */
@SuppressWarnings("null")
public class DefaultShaderInterface implements RenderPassChunkShaderInterface {
    @Nonnull
    private static final ResourceLocation BLOCK_ATLAS_LOCATION = Objects.requireNonNull(ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"));
	private static final java.util.List<String> RENDER_PASS_SAMPLER_NAMES = java.util.List.of("u_BlockTex", "u_LightTex");
    private final Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;

    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformFloat3v uniformRegionOffset;
    private final GlUniformFloat2v uniformTexCoordShrink;

    // The fog shader component used by this program in order to set up the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    public DefaultShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.uniformModelViewMatrix = context.bindUniform("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniform("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformRegionOffset = context.bindUniform("u_RegionOffset", GlUniformFloat3v::new);
        this.uniformTexCoordShrink = context.bindUniform("u_TexCoordShrink", GlUniformFloat2v::new);

        this.uniformTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
        this.uniformTextures.put(ChunkShaderTextureSlot.BLOCK, context.bindUniform("u_BlockTex", GlUniformInt::new));
        this.uniformTextures.put(ChunkShaderTextureSlot.LIGHT, context.bindUniform("u_LightTex", GlUniformInt::new));

        this.fogShader = options.fog().getFactory().apply(context);
    }

	@SuppressWarnings("null")
    @Override // the shader interface should not modify pipeline state
    public void setupState(TerrainRenderPass pass, FogParameters parameters) {
        this.bindTexture(ChunkShaderTextureSlot.BLOCK, pass.getAtlas());
        this.bindTexture(ChunkShaderTextureSlot.LIGHT, Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());

        var textureAtlas = getBlockTextureAtlas();

        // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
        // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
        // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
        double subTexelPrecision = (1 << GLRenderDevice.INSTANCE.getSubTexelPrecisionBits());
        double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

        this.uniformTexCoordShrink.set(
                (float) (subTexelOffset - (((1.0D / textureAtlas.width) / subTexelPrecision))),
                (float) (subTexelOffset - (((1.0D / textureAtlas.height) / subTexelPrecision)))
        );

        this.fogShader.setup(parameters);
    }

    @SuppressWarnings("null")
    private static TextureAtlas getBlockTextureAtlas() {
        return (TextureAtlas) Minecraft.getInstance()
            .getTextureManager()
            .getTexture(BLOCK_ATLAS_LOCATION);
    }

    @Override // the shader interface should not modify pipeline state
    public void resetState() {
        // This is used by alternate implementations.
    }

    @Deprecated(forRemoval = true) // should be handled properly in GFX instead.
    private void bindTexture(ChunkShaderTextureSlot slot, GpuTextureView textureView) {
        var tex = textureView.texture();
        CommandContext ctx = VulkanicAPI.getCommandContext();
        VulkanicAPI.setActiveTextureUnitIndex(ctx, slot.ordinal());
        VulkanicAPI.bindTexture2D(ctx, net.vulkanic.VulkanicCoreAPI.textureId(tex));
        VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.BASE_LEVEL, textureView.baseMipLevel());
        VulkanicAPI.texParameteri(ctx, VulkanicTextureTarget.TEXTURE_2D, VulkanicTextureParameterName.MAX_LEVEL, textureView.baseMipLevel() + textureView.mipLevels() - 1);
        tex.flushModeChanges2D();

        var uniform = this.uniformTextures.get(slot);
        uniform.setInt(slot.ordinal());
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
        this.uniformRegionOffset.set(x, y, z);
    }

	@SuppressWarnings("null")
    @Override
    public void bindRenderPassResources(RenderPass renderPass, TerrainRenderPass pass) {
        renderPass.bindSampler("u_BlockTex", Objects.requireNonNull(pass.getAtlas(), "chunk atlas view"));
        renderPass.bindSampler("u_LightTex", Objects.requireNonNull(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(), "light texture view"));
    }

    @Override
    public java.util.Collection<String> getRenderPassSamplerNames() {
        return RENDER_PASS_SAMPLER_NAMES;
    }
}

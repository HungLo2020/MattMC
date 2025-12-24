package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

@Environment(EnvType.CLIENT)
public enum ChunkSectionLayer {
	SOLID(RenderPipelines.SOLID, 4194304, true, false),
	CUTOUT_MIPPED(RenderPipelines.CUTOUT_MIPPED, 4194304, true, false),
	CUTOUT(RenderPipelines.CUTOUT, 786432, false, false),
	TRANSLUCENT(RenderPipelines.TRANSLUCENT, 786432, true, true),
	TRIPWIRE(RenderPipelines.TRIPWIRE, 1536, true, true);

	private final RenderPipeline pipeline;
	private final int bufferSize;
	private final boolean useMipmaps;
	private final boolean sortOnUpload;
	private final String label;

	private ChunkSectionLayer(final RenderPipeline renderPipeline, final int j, final boolean bl, final boolean bl2) {
		this.pipeline = renderPipeline;
		this.bufferSize = j;
		this.useMipmaps = bl;
		this.sortOnUpload = bl2;
		this.label = this.toString().toLowerCase(Locale.ROOT);
	}

	public RenderPipeline pipeline() {
		return this.pipeline;
	}

	public int bufferSize() {
		return this.bufferSize;
	}

	public String label() {
		return this.label;
	}

	public boolean sortOnUpload() {
		return this.sortOnUpload;
	}

	public GpuTextureView textureView() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		AbstractTexture abstractTexture = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS);
		abstractTexture.setUseMipmaps(this.useMipmaps);
		return abstractTexture.getTextureView();
	}
}

package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TextureAtlasSprite {
	private final ResourceLocation atlasLocation;
	private final SpriteContents contents;
	final int x;
	final int y;
	private final float u0;
	private final float u1;
	private final float v0;
	private final float v1;

	protected TextureAtlasSprite(ResourceLocation resourceLocation, SpriteContents spriteContents, int i, int j, int k, int l) {
		this.atlasLocation = resourceLocation;
		this.contents = spriteContents;
		this.x = k;
		this.y = l;
		this.u0 = (float)k / i;
		this.u1 = (float)(k + spriteContents.width()) / i;
		this.v0 = (float)l / j;
		this.v1 = (float)(l + spriteContents.height()) / j;
	}

	public int getX() {
		return this.x;
	}

	public int getY() {
		return this.y;
	}

	public float getU0() {
		return this.u0;
	}

	public float getU1() {
		return this.u1;
	}

	public SpriteContents contents() {
		return this.contents;
	}

	@Nullable
	public TextureAtlasSprite.Ticker createTicker() {
		final SpriteTicker spriteTicker = this.contents.createTicker();
		return spriteTicker != null ? new TextureAtlasSprite.Ticker() {
			@Override
			public void tickAndUpload(GpuTexture gpuTexture) {
				spriteTicker.tickAndUpload(TextureAtlasSprite.this.x, TextureAtlasSprite.this.y, gpuTexture);
			}

			@Override
			public void close() {
				spriteTicker.close();
			}
		} : null;
	}

	public float getU(float f) {
		float g = this.u1 - this.u0;
		return this.u0 + g * f;
	}

	public float getUOffset(float f) {
		float g = this.u1 - this.u0;
		return (f - this.u0) / g;
	}

	public float getV0() {
		return this.v0;
	}

	public float getV1() {
		return this.v1;
	}

	public float getV(float f) {
		float g = this.v1 - this.v0;
		return this.v0 + g * f;
	}

	public float getVOffset(float f) {
		float g = this.v1 - this.v0;
		return (f - this.v0) / g;
	}

	public ResourceLocation atlasLocation() {
		return this.atlasLocation;
	}

	public String toString() {
		return "TextureAtlasSprite{contents='" + this.contents + "', u0=" + this.u0 + ", u1=" + this.u1 + ", v0=" + this.v0 + ", v1=" + this.v1 + "}";
	}

	public void uploadFirstFrame(GpuTexture gpuTexture) {
		this.contents.uploadFirstFrame(this.x, this.y, gpuTexture);
	}

	private float atlasSize() {
		float f = this.contents.width() / (this.u1 - this.u0);
		float g = this.contents.height() / (this.v1 - this.v0);
		return Math.max(g, f);
	}

	public float uvShrinkRatio() {
		return 4.0F / this.atlasSize();
	}

	public VertexConsumer wrap(VertexConsumer vertexConsumer) {
		return new SpriteCoordinateExpander(vertexConsumer, this);
	}

	@Environment(EnvType.CLIENT)
	public interface Ticker extends AutoCloseable {
		void tickAndUpload(GpuTexture gpuTexture);

		void close();
	}
}

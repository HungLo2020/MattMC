package net.minecraft.client.renderer;

import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.sodium.api.vertex.attributes.common.TextureAttribute;
import net.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class SpriteCoordinateExpander implements VertexConsumer, VertexBufferWriter {
	private final VertexConsumer delegate;
	private final TextureAtlasSprite sprite;
	
	// Sodium: VertexBufferWriter optimization
	private boolean canUseIntrinsics;
	private float minU, minV;
	private float maxU, maxV;

	public SpriteCoordinateExpander(VertexConsumer vertexConsumer, TextureAtlasSprite textureAtlasSprite) {
		this.delegate = vertexConsumer;
		this.sprite = textureAtlasSprite;
		
		// Sodium: Initialize sprite bounds and check intrinsics support
		this.minU = textureAtlasSprite.getU0();
		this.minV = textureAtlasSprite.getV0();
		this.maxU = textureAtlasSprite.getU1();
		this.maxV = textureAtlasSprite.getV1();
		this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
	}

	@Override
	public VertexConsumer addVertex(float f, float g, float h) {
		return this.delegate.addVertex(f, g, h);
	}

	@Override
	public VertexConsumer setColor(int i, int j, int k, int l) {
		return this.delegate.setColor(i, j, k, l);
	}

	@Override
	public VertexConsumer setUv(float f, float g) {
		return this.delegate.setUv(this.sprite.getU(f), this.sprite.getV(g));
	}

	@Override
	public VertexConsumer setUv1(int i, int j) {
		return this.delegate.setUv1(i, j);
	}

	@Override
	public VertexConsumer setUv2(int i, int j) {
		return this.delegate.setUv2(i, j);
	}

	@Override
	public VertexConsumer setNormal(float f, float g, float h) {
		return this.delegate.setNormal(f, g, h);
	}

	@Override
	public void addVertex(float f, float g, float h, int i, float j, float k, int l, int m, float n, float o, float p) {
		this.delegate.addVertex(f, g, h, i, this.sprite.getU(j), this.sprite.getV(k), l, m, n, o, p);
	}
	
	// Sodium: VertexBufferWriter implementation for fast vertex processing
	@Override
	public boolean canUseIntrinsics() {
		return this.canUseIntrinsics;
	}
	
	@Override
	public void push(MemoryStack stack, final long ptr, int count, VertexFormat format) {
		transform(ptr, count, format,
				this.minU, this.minV, this.maxU, this.maxV);
		
		VertexBufferWriter.of(this.delegate)
				.push(stack, ptr, count, format);
	}
	
	/**
	 * Transforms the texture UVs for each vertex from their absolute coordinates into the sprite area specified
	 * by the parameters.
	 *
	 * @param ptr    The buffer of vertices to transform
	 * @param count  The number of vertices to transform
	 * @param format The format of the vertices
	 * @param minU   The minimum X-coordinate of the sprite bounds
	 * @param minV   The minimum Y-coordinate of the sprite bounds
	 * @param maxU   The maximum X-coordinate of the sprite bounds
	 * @param maxV   The maximum Y-coordinate of the sprite bounds
	 */
	private static void transform(long ptr, int count, VertexFormat format,
								  float minU, float minV, float maxU, float maxV) {
		long stride = format.getVertexSize();
		long offsetUV = format.getOffset(VertexFormatElement.UV0);
		
		// The width/height of the sprite
		float w = maxU - minU;
		float h = maxV - minV;
		
		for (int vertexIndex = 0; vertexIndex < count; vertexIndex++) {
			// The texture coordinates relative to the sprite bounds
			float u = TextureAttribute.getU(ptr + offsetUV);
			float v = TextureAttribute.getV(ptr + offsetUV);
			
			// The texture coordinates in absolute space on the sprite sheet
			float ut = minU + (w * u);
			float vt = minV + (h * v);
			
			TextureAttribute.put(ptr + offsetUV, ut, vt);
			
			ptr += stride;
		}
	}
}

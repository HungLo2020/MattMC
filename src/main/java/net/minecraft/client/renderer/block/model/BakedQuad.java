package net.minecraft.client.renderer.block.model;

import net.sodium.client.model.quad.BakedQuadView;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.sodium.api.util.ModelQuadUtil;

@Environment(EnvType.CLIENT)
public class BakedQuad implements BakedQuadView {
	protected final int[] vertices;
	protected final int tintIndex;
	protected final Direction direction; // This is really the light face, but we can't rename it.
	protected final TextureAtlasSprite sprite;
	protected final boolean shade;
	protected final int lightEmission;
	
	// Sodium: BakedQuadView implementation fields (merged from BakedQuadMixin)
	private int flags;
	private int normal;
	private ModelQuadFacing normalFace = null;
	
	public BakedQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, int lightEmission) {
		this.vertices = vertices;
		this.tintIndex = tintIndex;
		this.direction = direction;
		this.sprite = sprite;
		this.shade = shade;
		this.lightEmission = lightEmission;
		
		// Sodium: Initialize quad view data (merged from BakedQuadMixin)
		this.normal = this.calculateNormal();
		this.normalFace = ModelQuadFacing.fromPackedNormal(this.normal);
		this.flags = ModelQuadFlags.getQuadFlags(this, direction);
	}
	
	public boolean isTinted() {
		return this.tintIndex != -1;
	}
	
	// Record accessors
	public int[] vertices() { return vertices; }
	public int tintIndex() { return tintIndex; }
	public Direction direction() { return direction; }
	public TextureAtlasSprite sprite() { return sprite; }
	public boolean shade() { return shade; }
	public int lightEmission() { return lightEmission; }
	
	// Sodium: BakedQuadView implementation (merged from BakedQuadMixin)
	@Override
	public float getX(int idx) {
		return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX]);
	}

	@Override
	public float getY(int idx) {
		return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 1]);
	}

	@Override
	public float getZ(int idx) {
		return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 2]);
	}

	@Override
	public int getColor(int idx) {
		return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.COLOR_INDEX];
	}

	@Override
	public int getVertexNormal(int idx) {
		return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.NORMAL_INDEX];
	}

	@Override
	public int getLight(int idx) {
		return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX];
	}

	@Override
	public TextureAtlasSprite getSprite() {
		return this.sprite;
	}

	@Override
	public float getTexU(int idx) {
		return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX]);
	}

	@Override
	public float getTexV(int idx) {
		return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX + 1]);
	}

	@Override
	public int getFlags() {
		return this.flags;
	}

	@Override
	public int getTintIndex() {
		return this.tintIndex;
	}

	@Override
	public ModelQuadFacing getNormalFace() {
		return this.normalFace;
	}

	@Override
	public int getFaceNormal() {
		return this.normal;
	}

	@Override
	public Direction getLightFace() {
		return this.direction;
	}

	@Override
	public int getMaxLightQuad(int idx) {
		return LightTexture.lightCoordsWithEmission(getLight(idx), lightEmission());
	}

	@Override
	public boolean hasShade() {
		return this.shade;
	}

	@Override
	public boolean hasAO() {
		return true;
	}
}

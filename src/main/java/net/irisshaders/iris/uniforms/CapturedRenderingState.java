package net.irisshaders.iris.uniforms;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

public class CapturedRenderingState {
	public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

	private Matrix4fc gbufferModelView;
	private Matrix4fc gbufferProjection;
	private Vector3d fogColor;
	private float fogDensity;
	private float darknessLightFactor;
	private float tickDelta;
	private float realTickDelta;
	private int currentRenderedBlockEntity;

	private int currentRenderedEntity = -1;
	private int currentRenderedItem = -1;

	private float currentAlphaTest;
	private float cloudTime;

	private CapturedRenderingState() {
	}

	public Vector3d getFogColor() {
		if (Minecraft.getInstance().level == null || fogColor == null) {
			return ZERO_VECTOR_3d;
		}

		return fogColor;
	}

	public void setFogColor(float red, float green, float blue) {
		fogColor = new Vector3d(red, green, blue);
	}

	public void setFogDensity(float fogDensity) {
		this.fogDensity = fogDensity;
	}

	public void setCurrentBlockEntity(int entity) {
		this.currentRenderedBlockEntity = entity;
	}

	public int getCurrentRenderedBlockEntity() {
		return currentRenderedBlockEntity;
	}

	public void setCurrentEntity(int entity) {
		this.currentRenderedEntity = entity;
	}

	public int getCurrentRenderedEntity() {
		return currentRenderedEntity;
	}

	public int getCurrentRenderedItem() {
		return currentRenderedItem;
	}

	public void setCurrentRenderedItem(int item) {
		this.currentRenderedItem = item;
	}

}

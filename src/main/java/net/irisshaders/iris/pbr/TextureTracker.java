package net.irisshaders.iris.pbr;

import net.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

public class TextureTracker {
	public static final TextureTracker INSTANCE = new TextureTracker();

	private static Runnable bindTextureListener;

	static {
		StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;
	}

	private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectMap<GpuTextureView> textureViews = new Int2ObjectOpenHashMap<>();
	private final GpuTextureView[] shaderTexturesByUnit = new GpuTextureView[128];

	private boolean lockBindCallback;

	private TextureTracker() {
	}

	public void trackTexture(int id, AbstractTexture texture) {
		textures.put(id, texture);
		try {
			textureViews.put(id, texture.getTextureView());
		} catch (IllegalStateException ignored) {
		}
	}

	@Nullable
	public AbstractTexture getTexture(int id) {
		return textures.get(id);
	}

	@Nullable
	public GpuTextureView getTextureView(int id) {
		AbstractTexture texture = textures.get(id);
		if (texture != null) {
			try {
				GpuTextureView view = texture.getTextureView();
				textureViews.put(id, view);
				return view;
			} catch (IllegalStateException ignored) {
			}
		}

		return textureViews.get(id);
	}

	public void onSetShaderTexture(int unit, GpuTextureView id) {
		if (unit >= 0 && unit < shaderTexturesByUnit.length) {
			shaderTexturesByUnit[unit] = id;
		}
		if (id != null) {
			textureViews.put(VulkanicAPI.getTextureHandle(id.texture()), id);
		}
		if (lockBindCallback) {
			return;
		}
		if (unit == 0) {
			lockBindCallback = true;
			if (bindTextureListener != null) {
				bindTextureListener.run();
			}
			WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
			if (pipeline != null) {
				pipeline.onSetShaderTexture(id);
			}
			// Reset texture state
			IrisRenderSystem.bindTextureToUnit(0, id == null ? 0 : VulkanicAPI.getTextureHandle(id.texture()));
			lockBindCallback = false;
		}
	}

	@Nullable
	public GpuTextureView getShaderTexture(int unit) {
		if (unit < 0 || unit >= shaderTexturesByUnit.length) {
			return null;
		}

		return shaderTexturesByUnit[unit];
	}

	public void onDeleteTexture(int id) {
		textures.remove(id);
		textureViews.remove(id);
		for (int unit = 0; unit < shaderTexturesByUnit.length; unit++) {
			GpuTextureView view = shaderTexturesByUnit[unit];
			if (view != null && VulkanicAPI.getTextureHandle(view.texture()) == id) {
				shaderTexturesByUnit[unit] = null;
			}
		}
	}
}

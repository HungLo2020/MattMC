package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pbr.TextureTracker;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.ARGB;
import net.vulkanic.VulkanicAPI;

@Environment(EnvType.CLIENT)
public class OverlayTexture implements AutoCloseable {
	private static final int SIZE = 16;
	private static final net.minecraft.resources.ResourceLocation SEMANTIC_IDENTITY =
		net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mattmc", "entity_overlay");
	public static final int NO_WHITE_U = 0;
	public static final int RED_OVERLAY_V = 3;
	public static final int WHITE_OVERLAY_V = 10;
	public static final int NO_OVERLAY = pack(0, 10);
	private final DynamicTexture texture = new DynamicTexture("Entity Color Overlay", 16, 16, false);
	private boolean semanticPublished;

	public OverlayTexture() {
		NativeImage nativeImage = this.texture.getPixels();

		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				if (i < 8) {
					nativeImage.setPixel(j, i, -1291911168);
				} else {
					int k = (int)((1.0F - j / 15.0F * 0.75F) * 255.0F);
					nativeImage.setPixel(j, i, ARGB.color(k, -1));
				}
			}
		}

		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| VulkanicAPI.isVulkanBackendSelected()) {
			// DynamicTexture is CPU-only on this route; publish the completed
			// overlay pixels to the semantic asset registry instead of touching a
			// Java texture or compatibility encoder.
			ensureSemanticAsset();
		} else {
			this.texture.setClamp(true);
			this.texture.upload();
		}
	}

	/** Publishes the overlay after a late Vulkan selection without retaining a Java GPU image. */
	public void ensureSemanticAsset() {
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !VulkanicAPI.isVulkanBackendSelected()) {
			return;
		}
		if (!this.semanticPublished) {
			net.vulkanic.gui.RustGalGuiRawImageAssets.registerDynamicTexture(SEMANTIC_IDENTITY, this.texture);
			this.semanticPublished = true;
		}
	}

	public void close() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| VulkanicAPI.isVulkanBackendSelected()) {
			if (this.semanticPublished) {
				net.vulkanic.gui.RustGalGuiRawImageAssets.unregisterDynamicTexture(SEMANTIC_IDENTITY, this.texture);
				this.semanticPublished = false;
			}
		}
		this.texture.close();
	}

	public void setupOverlayColor() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| VulkanicAPI.isVulkanBackendSelected()) {
			return;
		}
		var textureView = this.texture.getTextureView();
		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindTextureUnit(ctx, 1, textureView);
		TextureTracker.INSTANCE.onSetShaderTexture(1, textureView);
	}

	public static int u(float f) {
		return (int)(f * 15.0F);
	}

	public static int v(boolean bl) {
		return bl ? 3 : 10;
	}

	public static int pack(int i, int j) {
		return i | j << 16;
	}

	public static int pack(float f, boolean bl) {
		return pack(u(f), v(bl));
	}

	public void teardownOverlayColor() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| VulkanicAPI.isVulkanBackendSelected()) {
			return;
		}
		IrisRenderSystem.bindTextureToUnit(1, 0);
		TextureTracker.INSTANCE.onSetShaderTexture(1, null);
	}
}

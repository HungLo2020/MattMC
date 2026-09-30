package net.blaze3d.pipeline;

import com.google.common.collect.ImmutableList;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.TextureFormat;
import java.util.List;
import java.util.Objects;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MainTarget extends RenderTarget {
	public static final int DEFAULT_WIDTH = 854;
	public static final int DEFAULT_HEIGHT = 480;
	static final MainTarget.Dimension DEFAULT_DIMENSIONS = new MainTarget.Dimension(854, 480);

	public MainTarget(int i, int j) {
		super("Main", true);
		this.createFrameBuffer(i, j);
	}

	@Override
	public void createBuffers(int i, int j) {
		this.createFrameBuffer(i, j);
	}

	private void createFrameBuffer(int i, int j) {
		{
			// MainTarget overrides RenderTarget.createBuffers, so the base
			// selected-Vulkan guard must protect this entry point. Rust owns the
			// acquired presentation images; retain only dimensions for semantic
			// extraction and never allocate a second Java framebuffer.
			MainTarget.Dimension dimension = this.listWithFallbackDimensions(i, j);
			this.width = dimension.width;
			this.height = dimension.height;
			this.filterMode = FilterMode.NEAREST;
			return;
		}
	}

	private MainTarget.Dimension listWithFallbackDimensions(int i, int j) {
		return MainTarget.Dimension.listWithFallback(i, j).get(0);
	}

	@Environment(EnvType.CLIENT)
	static class Dimension {
		public final int width;
		public final int height;

		Dimension(int i, int j) {
			this.width = i;
			this.height = j;
		}

		static List<MainTarget.Dimension> listWithFallback(int i, int j) {
			RenderSystem.assertOnRenderThread();
			int k = net.vulkanic.VulkanicAPI.getBackendMaxTextureSize();
			return i > 0 && i <= k && j > 0 && j <= k
				? ImmutableList.of(new MainTarget.Dimension(i, j), MainTarget.DEFAULT_DIMENSIONS)
				: ImmutableList.of(MainTarget.DEFAULT_DIMENSIONS);
		}

		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else if (object != null && this.getClass() == object.getClass()) {
				MainTarget.Dimension dimension = (MainTarget.Dimension)object;
				return this.width == dimension.width && this.height == dimension.height;
			} else {
				return false;
			}
		}

		public int hashCode() {
			return Objects.hash(new Object[]{this.width, this.height});
		}

		public String toString() {
			return this.width + "x" + this.height;
		}
	}
}

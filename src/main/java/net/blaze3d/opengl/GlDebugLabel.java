package net.blaze3d.opengl;

import net.logging.LogUtils;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.StringUtil;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class GlDebugLabel {
	private static final Logger LOGGER = LogUtils.getLogger();

	public void applyLabel(GlBuffer glBuffer) {
	}

	public void applyLabel(GlTexture glTexture) {
	}

	public void applyLabel(GlShaderModule glShaderModule) {
	}

	public void applyLabel(GlProgram glProgram) {
	}

	public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
	}

	public void pushDebugGroup(Supplier<String> supplier) {
	}

	public void popDebugGroup() {
	}

	public static GlDebugLabel create(GraphicsCapabilities gLCapabilities, boolean bl, Set<String> set) {
		if (bl) {
			if (gLCapabilities.GL_KHR_debug && GlDevice.USE_GL_KHR_debug) {
				set.add("GL_KHR_debug");
				return new GlDebugLabel.Core();
			}

			if (gLCapabilities.GL_EXT_debug_label && GlDevice.USE_GL_EXT_debug_label) {
				set.add("GL_EXT_debug_label");
				return new GlDebugLabel.Ext();
			}

			LOGGER.warn("Debug labels unavailable: neither KHR_debug nor EXT_debug_label are supported");
		}

		return new GlDebugLabel.Empty();
	}

	public boolean exists() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	static class Core extends GlDebugLabel {
		private final int maxLabelLength = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), 33512);

		@Override
		public void applyLabel(GlBuffer glBuffer) {
			Supplier<String> supplier = glBuffer.label;
			if (supplier != null) {
				VulkanicAPI.labelDebugObject(VulkanicAPI.getImmediateContext(), 33504, glBuffer.handle, StringUtil.truncateStringIfNecessary((String)supplier.get(), this.maxLabelLength, true));
			}
		}

		@Override
		public void applyLabel(GlTexture glTexture) {
			VulkanicAPI.labelDebugObject(VulkanicAPI.getImmediateContext(), 5890, glTexture.id, StringUtil.truncateStringIfNecessary(glTexture.getLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(GlShaderModule glShaderModule) {
			VulkanicAPI.labelDebugObject(VulkanicAPI.getImmediateContext(), 33505, glShaderModule.getShaderId(), StringUtil.truncateStringIfNecessary(glShaderModule.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(GlProgram glProgram) {
			VulkanicAPI.labelDebugObject(VulkanicAPI.getImmediateContext(), 33506, glProgram.getProgramId(), StringUtil.truncateStringIfNecessary(glProgram.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
			VulkanicAPI.labelDebugObject(VulkanicAPI.getImmediateContext(), 32884, vertexArray.id, StringUtil.truncateStringIfNecessary(vertexArray.format.toString(), this.maxLabelLength, true));
		}

		@Override
		public void pushDebugGroup(Supplier<String> supplier) {
			VulkanicAPI.enterDebugGroup(VulkanicAPI.getImmediateContext(), 33354, 0, (CharSequence)supplier.get());
		}

		@Override
		public void popDebugGroup() {
			VulkanicAPI.exitDebugGroup(VulkanicAPI.getImmediateContext());
		}

		@Override
		public boolean exists() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	static class Empty extends GlDebugLabel {
	}

	@Environment(EnvType.CLIENT)
	static class Ext extends GlDebugLabel {
		@Override
		public void applyLabel(GlBuffer glBuffer) {
			Supplier<String> supplier = glBuffer.label;
			if (supplier != null) {
				VulkanicAPI.labelObjectExt(37201, glBuffer.handle, StringUtil.truncateStringIfNecessary((String)supplier.get(), 256, true));
			}
		}

		@Override
		public void applyLabel(GlTexture glTexture) {
			VulkanicAPI.labelObjectExt(5890, glTexture.id, StringUtil.truncateStringIfNecessary(glTexture.getLabel(), 256, true));
		}

		@Override
		public void applyLabel(GlShaderModule glShaderModule) {
			VulkanicAPI.labelObjectExt(35656, glShaderModule.getShaderId(), StringUtil.truncateStringIfNecessary(glShaderModule.getDebugLabel(), 256, true));
		}

		@Override
		public void applyLabel(GlProgram glProgram) {
			VulkanicAPI.labelObjectExt(35648, glProgram.getProgramId(), StringUtil.truncateStringIfNecessary(glProgram.getDebugLabel(), 256, true));
		}

		@Override
		public void applyLabel(VertexArrayCache.VertexArray vertexArray) {
			VulkanicAPI.labelObjectExt(32884, vertexArray.id, StringUtil.truncateStringIfNecessary(vertexArray.format.toString(), 256, true));
		}

		@Override
		public boolean exists() {
			return true;
		}
	}
}

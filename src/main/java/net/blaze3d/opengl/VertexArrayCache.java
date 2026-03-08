package net.blaze3d.opengl;

import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class VertexArrayCache {
	public static VertexArrayCache create(GlDebugLabel glDebugLabel, Set<String> set) {
		if (VulkanicAPI.hasVertexAttribBindingExtension() && GlDevice.USE_GL_ARB_vertex_attrib_binding) {
			set.add("GL_ARB_vertex_attrib_binding");
			return new VertexArrayCache.Separate(glDebugLabel);
		} else {
			return new VertexArrayCache.Emulated(glDebugLabel);
		}
	}

	public abstract void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer);

	@Environment(EnvType.CLIENT)
	static class Emulated extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap();
		private final GlDebugLabel debugLabels;

		public Emulated(GlDebugLabel glDebugLabel) {
			this.debugLabels = glDebugLabel;
		}

		@Override
		public void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VertexArrayCache.VertexArray vertexArray = (VertexArrayCache.VertexArray)this.cache.get(vertexFormat);
			if (vertexArray == null) {
				int i = VulkanicAPI.createVertexArray(ctx);
				VulkanicAPI.bindVertexArray(ctx, i);
				if (glBuffer != null) {
					GlStateManager._glBindBuffer(VulkanicAPI.GL_ARRAY_BUFFER, glBuffer.handle);
					setupCombinedAttributes(vertexFormat, true);
				}

				VertexArrayCache.VertexArray vertexArray2 = new VertexArrayCache.VertexArray(i, vertexFormat, glBuffer);
				this.debugLabels.applyLabel(vertexArray2);
				this.cache.put(vertexFormat, vertexArray2);
			} else {
				VulkanicAPI.bindVertexArray(ctx, vertexArray.id);
				if (glBuffer != null && vertexArray.lastVertexBuffer != glBuffer) {
					GlStateManager._glBindBuffer(VulkanicAPI.GL_ARRAY_BUFFER, glBuffer.handle);
					vertexArray.lastVertexBuffer = glBuffer;
					setupCombinedAttributes(vertexFormat, false);
				}
			}
		}

		private static void setupCombinedAttributes(VertexFormat vertexFormat, boolean bl) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			int i = vertexFormat.getVertexSize();
			List<VertexFormatElement> list = vertexFormat.getElements();

			for (int j = 0; j < list.size(); j++) {
				VertexFormatElement vertexFormatElement = (VertexFormatElement)list.get(j);
				if (bl) {
					VulkanicAPI.enableVertexAttribArray(ctx, j);
				}

				switch (vertexFormatElement.usage()) {
					case POSITION:
					case GENERIC:
					case UV:
						if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
							VulkanicAPI.setVertexAttribPointer(ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, i, vertexFormat.getOffset(vertexFormatElement));
						} else {
							VulkanicAPI.setVertexAttribIPointer(ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), i, vertexFormat.getOffset(vertexFormatElement));
						}
						break;
					case NORMAL:
					case COLOR:
						VulkanicAPI.setVertexAttribPointer(ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, i, vertexFormat.getOffset(vertexFormatElement));
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class Separate extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap();
		private final GlDebugLabel debugLabels;
		private final boolean needsMesaWorkaround;

		public Separate(GlDebugLabel glDebugLabel) {
			this.debugLabels = glDebugLabel;
			if ("Mesa".equals(VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_VENDOR))) {
				String string = VulkanicAPI.getString(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_VERSION);
				this.needsMesaWorkaround = string.contains("25.0.0") || string.contains("25.0.1") || string.contains("25.0.2");
			} else {
				this.needsMesaWorkaround = false;
			}
		}

		@Override
		public void bindVertexArray(VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			net.vulkanic.CommandContext ctx = VulkanicAPI.getImmediateContext();
			VertexArrayCache.VertexArray vertexArray = (VertexArrayCache.VertexArray)this.cache.get(vertexFormat);
			if (vertexArray != null) {
				VulkanicAPI.bindVertexArray(ctx, vertexArray.id);
				if (glBuffer != null && vertexArray.lastVertexBuffer != glBuffer) {
					if (this.needsMesaWorkaround && vertexArray.lastVertexBuffer != null && vertexArray.lastVertexBuffer.handle == glBuffer.handle) {
						VulkanicAPI.bindVertexBuffer(ctx, 0, 0, 0L, 0);
					}

					VulkanicAPI.bindVertexBuffer(ctx, 0, glBuffer.handle, 0L, vertexFormat.getVertexSize());
					vertexArray.lastVertexBuffer = glBuffer;
				}
			} else {
				int i = VulkanicAPI.createVertexArray(ctx);
				VulkanicAPI.bindVertexArray(ctx, i);
				if (glBuffer != null) {
					List<VertexFormatElement> list = vertexFormat.getElements();

					for (int j = 0; j < list.size(); j++) {
						VertexFormatElement vertexFormatElement = (VertexFormatElement)list.get(j);
						VulkanicAPI.enableVertexAttribArray(ctx, j);
						switch (vertexFormatElement.usage()) {
							case POSITION:
							case GENERIC:
							case UV:
								if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
									VulkanicAPI.setVertexAttribFormat(
										ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), false, vertexFormat.getOffset(vertexFormatElement)
									);
								} else {
									VulkanicAPI.setVertexAttribIFormat(
										ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), vertexFormat.getOffset(vertexFormatElement)
									);
								}
								break;
							case NORMAL:
							case COLOR:
								VulkanicAPI.setVertexAttribFormat(
									ctx, j, vertexFormatElement.count(), GlConst.toGl(vertexFormatElement.type()), true, vertexFormat.getOffset(vertexFormatElement)
								);
						}

						VulkanicAPI.setVertexAttribBinding(ctx, j, 0);
					}
				}

				if (glBuffer != null) {
					VulkanicAPI.bindVertexBuffer(ctx, 0, glBuffer.handle, 0L, vertexFormat.getVertexSize());
				}

				VertexArrayCache.VertexArray vertexArray2 = new VertexArrayCache.VertexArray(i, vertexFormat, glBuffer);
				this.debugLabels.applyLabel(vertexArray2);
				this.cache.put(vertexFormat, vertexArray2);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class VertexArray {
		final int id;
		final VertexFormat format;
		@Nullable
		GlBuffer lastVertexBuffer;

		VertexArray(int i, VertexFormat vertexFormat, @Nullable GlBuffer glBuffer) {
			this.id = i;
			this.format = vertexFormat;
			this.lastVertexBuffer = glBuffer;
		}
	}
}

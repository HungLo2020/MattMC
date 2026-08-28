package net.minecraft.client.renderer;

import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.sodium.api.util.ColorARGB;
import net.sodium.api.vertex.attributes.common.ColorAttribute;
import net.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class OutlineBufferSource implements MultiBufferSource {
	private final MultiBufferSource.BufferSource outlineBufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
	private int outlineColor = -1;

	@Override
	public VertexConsumer getBuffer(RenderType renderType) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java entity-outline buffers are unavailable on selected Vulkan");
		}
		if (renderType.isOutline()) {
			VertexConsumer vertexConsumer = this.outlineBufferSource.getBuffer(renderType);
			return new OutlineBufferSource.EntityOutlineGenerator(vertexConsumer, this.outlineColor);
		} else {
			Optional<RenderType> optional = renderType.outline();
			if (optional.isPresent()) {
				VertexConsumer vertexConsumer2 = this.outlineBufferSource.getBuffer((RenderType)optional.get());
				return new OutlineBufferSource.EntityOutlineGenerator(vertexConsumer2, this.outlineColor);
			} else {
				throw new IllegalStateException("Can't render an outline for this rendertype!");
			}
		}
	}

	public void setColor(int i) {
		this.outlineColor = i;
	}

	public void endOutlineBatch() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			// Entity-outline masks are Rust-owned in whole-frame Vulkan. Do not
			// flush a Java outline buffer into the presentation path.
			return;
		}
		this.outlineBufferSource.endBatch();
	}

	@Environment(EnvType.CLIENT)
	record EntityOutlineGenerator(VertexConsumer delegate, int color) implements VertexConsumer, VertexBufferWriter {
		// Sodium: VertexBufferWriter optimization
		@Override
		public boolean canUseIntrinsics() {
			return VertexBufferWriter.tryOf(this.delegate) != null;
		}
		
		@Override
		public VertexConsumer addVertex(float f, float g, float h) {
			this.delegate.addVertex(f, g, h).setColor(this.color);
			return this;
		}

		@Override
		public VertexConsumer setColor(int i, int j, int k, int l) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float f, float g) {
			this.delegate.setUv(f, g);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int i, int j) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int i, int j) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float f, float g, float h) {
			return this;
		}
		
		// Sodium: VertexBufferWriter implementation for fast vertex processing
		@Override
		public void push(MemoryStack stack, long ptr, int count, VertexFormat format) {
			transform(ptr, count, format, this.color);
			
			VertexBufferWriter.of(this.delegate)
					.push(stack, ptr, count, format);
		}
		
		/**
		 * Transforms the color element of each vertex to use the specified value.
		 *
		 * @param ptr    The buffer of vertices to transform
		 * @param count  The number of vertices to transform
		 * @param format The format of the vertices
		 * @param color  The packed color to use for transforming the vertices
		 */
		private static void transform(long ptr, int count, VertexFormat format, int color) {
			long stride = format.getVertexSize();
			long offsetColor = format.getOffset(VertexFormatElement.COLOR);
			
			for (int vertexIndex = 0; vertexIndex < count; vertexIndex++) {
				ColorAttribute.set(ptr + offsetColor, ColorARGB.toABGR(color));
				ptr += stride;
			}
		}
	}
}

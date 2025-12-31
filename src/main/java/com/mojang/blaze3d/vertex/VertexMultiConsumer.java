package com.mojang.blaze3d.vertex;

import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class VertexMultiConsumer {
	public static VertexConsumer create() {
		throw new IllegalArgumentException();
	}

	public static VertexConsumer create(VertexConsumer vertexConsumer) {
		return vertexConsumer;
	}

	public static VertexConsumer create(VertexConsumer vertexConsumer, VertexConsumer vertexConsumer2) {
		return new VertexMultiConsumer.Double(vertexConsumer, vertexConsumer2);
	}

	public static VertexConsumer create(VertexConsumer... vertexConsumers) {
		return new VertexMultiConsumer.Multiple(vertexConsumers);
	}

	@Environment(EnvType.CLIENT)
	static class Double implements VertexConsumer, net.sodium.api.vertex.buffer.VertexBufferWriter {
		private final VertexConsumer first;
		private final VertexConsumer second;
		private boolean canUseIntrinsics; // Sodium: VertexBufferWriter optimization

		public Double(VertexConsumer vertexConsumer, VertexConsumer vertexConsumer2) {
			if (vertexConsumer == vertexConsumer2) {
				throw new IllegalArgumentException("Duplicate delegates");
			} else {
				this.first = vertexConsumer;
				this.second = vertexConsumer2;
				// Sodium: Check if both delegates support fast vertex processing
				this.canUseIntrinsics = net.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(this.first) != null 
					&& net.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(this.second) != null;
			}
		}
		
		// Sodium: VertexBufferWriter implementation for fast vertex processing
		@Override
		public boolean canUseIntrinsics() {
			return this.canUseIntrinsics;
		}
		
		@Override
		public void push(org.lwjgl.system.MemoryStack stack, long ptr, int count, com.mojang.blaze3d.vertex.VertexFormat format) {
			net.sodium.api.vertex.buffer.VertexBufferWriter.copyInto(net.sodium.api.vertex.buffer.VertexBufferWriter.of(this.first), stack, ptr, count, format);
			net.sodium.api.vertex.buffer.VertexBufferWriter.copyInto(net.sodium.api.vertex.buffer.VertexBufferWriter.of(this.second), stack, ptr, count, format);
		}

		@Override
		public VertexConsumer addVertex(float f, float g, float h) {
			this.first.addVertex(f, g, h);
			this.second.addVertex(f, g, h);
			return this;
		}

		@Override
		public VertexConsumer setColor(int i, int j, int k, int l) {
			this.first.setColor(i, j, k, l);
			this.second.setColor(i, j, k, l);
			return this;
		}

		@Override
		public VertexConsumer setUv(float f, float g) {
			this.first.setUv(f, g);
			this.second.setUv(f, g);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int i, int j) {
			this.first.setUv1(i, j);
			this.second.setUv1(i, j);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int i, int j) {
			this.first.setUv2(i, j);
			this.second.setUv2(i, j);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float f, float g, float h) {
			this.first.setNormal(f, g, h);
			this.second.setNormal(f, g, h);
			return this;
		}

		@Override
		public void addVertex(float f, float g, float h, int i, float j, float k, int l, int m, float n, float o, float p) {
			this.first.addVertex(f, g, h, i, j, k, l, m, n, o, p);
			this.second.addVertex(f, g, h, i, j, k, l, m, n, o, p);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Multiple implements VertexConsumer, net.sodium.api.vertex.buffer.VertexBufferWriter {
		private final VertexConsumer[] delegates;
		private boolean canUseIntrinsics; // Sodium: VertexBufferWriter optimization

		Multiple(VertexConsumer[] delegates) {
			for (int i = 0; i < delegates.length; i++) {
				for (int j = i + 1; j < delegates.length; j++) {
					if (delegates[i] == delegates[j]) {
						throw new IllegalArgumentException("Duplicate delegates");
					}
				}
			}

			this.delegates = delegates;
			// Sodium: Check if all delegates support fast vertex processing
			this.canUseIntrinsics = allDelegatesSupportIntrinsics();
		}
		
		// Sodium: Check if all delegates support intrinsics
		private boolean allDelegatesSupportIntrinsics() {
			for (var delegate : this.delegates) {
				if (net.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(delegate) == null) {
					return false;
				}
			}
			return true;
		}
		
		// Sodium: VertexBufferWriter implementation for fast vertex processing
		@Override
		public boolean canUseIntrinsics() {
			return this.canUseIntrinsics;
		}
		
		@Override
		public void push(org.lwjgl.system.MemoryStack stack, long ptr, int count, com.mojang.blaze3d.vertex.VertexFormat format) {
			for (var delegate : this.delegates) {
				net.sodium.api.vertex.buffer.VertexBufferWriter.copyInto(net.sodium.api.vertex.buffer.VertexBufferWriter.of(delegate), stack, ptr, count, format);
			}
		}
		
		public VertexConsumer[] delegates() {
			return this.delegates;
		}

		private void forEach(Consumer<VertexConsumer> consumer) {
			for (VertexConsumer vertexConsumer : this.delegates) {
				consumer.accept(vertexConsumer);
			}
		}

		@Override
		public VertexConsumer addVertex(float f, float g, float h) {
			this.forEach(vertexConsumer -> vertexConsumer.addVertex(f, g, h));
			return this;
		}

		@Override
		public VertexConsumer setColor(int i, int j, int k, int l) {
			this.forEach(vertexConsumer -> vertexConsumer.setColor(i, j, k, l));
			return this;
		}

		@Override
		public VertexConsumer setUv(float f, float g) {
			this.forEach(vertexConsumer -> vertexConsumer.setUv(f, g));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int i, int j) {
			this.forEach(vertexConsumer -> vertexConsumer.setUv1(i, j));
			return this;
		}

		@Override
		public VertexConsumer setUv2(int i, int j) {
			this.forEach(vertexConsumer -> vertexConsumer.setUv2(i, j));
			return this;
		}

		@Override
		public VertexConsumer setNormal(float f, float g, float h) {
			this.forEach(vertexConsumer -> vertexConsumer.setNormal(f, g, h));
			return this;
		}

		@Override
		public void addVertex(float f, float g, float h, int i, float j, float k, int l, int m, float n, float o, float p) {
			this.forEach(vertexConsumer -> vertexConsumer.addVertex(f, g, h, i, j, k, l, m, n, o, p));
		}
	}
}

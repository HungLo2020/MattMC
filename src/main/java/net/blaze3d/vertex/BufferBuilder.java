package net.blaze3d.vertex;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.Collectors;

import net.sodium.client.model.quad.ModelQuadView;
import net.sodium.client.render.vertex.buffer.BufferBuilderExtension;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.irisshaders.iris.vertices.BufferBuilderPolygonView;
import net.irisshaders.iris.vertices.ExtendedDataHelper;
import net.irisshaders.iris.vertices.ImmediateState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.MojangBufferAccessor;
import net.irisshaders.iris.vertices.NormI8;
import net.irisshaders.iris.vertices.NormalHelper;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.sodium.api.memory.MemoryIntrinsics;
import net.sodium.api.vertex.serializer.VertexSerializerRegistry;
import net.sodium.client.render.immediate.model.BakedModelEncoder;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class BufferBuilder implements VertexConsumer, BufferBuilderExtension, BlockSensitiveBufferBuilder {
	private static final int MAX_VERTEX_COUNT = 16777215;
	private static final long NOT_BUILDING = -1L;
	private static final long UNKNOWN_ELEMENT = -1L;
	private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
	private final ByteBufferBuilder buffer;
	private long vertexPointer = -1L;
	private int vertices;
	private final VertexFormat format;
	private final VertexFormat.Mode mode;
	private final boolean fastFormat;
	private final boolean fullFormat;
	private final int vertexSize;
	private final int initialElementsToFill;
	private final int[] offsetsByElement;
	private int elementsToFill;
	private boolean building = true;

	// Iris extended vertex format support
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();
	private final Vector3f normal = new Vector3f();
	private final long[] vertexOffsets = new long[4];
	private boolean skipEndVertexOnce;
	private boolean extending;
	private boolean injectNormalAndUV1;
	private int iris$vertexCount;
	private int currentBlock = -1;
	private byte currentRenderType = -1;
	private int currentLocalPosX;
	private int currentLocalPosY;
	private int currentLocalPosZ;

	public BufferBuilder(ByteBufferBuilder byteBufferBuilder, VertexFormat.Mode mode, VertexFormat vertexFormat) {
		if (!vertexFormat.contains(VertexFormatElement.POSITION)) {
			throw new IllegalArgumentException("Cannot build mesh with no position element");
		} else {
			this.buffer = byteBufferBuilder;
			this.mode = mode;
			
			// Iris: Dynamically extend vertex formats for shader support
			vertexFormat = iris$extendFormat(vertexFormat);
			
			this.format = vertexFormat;
			this.vertexSize = vertexFormat.getVertexSize();
			this.initialElementsToFill = vertexFormat.getElementsMask() & ~VertexFormatElement.POSITION.mask();
			this.offsetsByElement = vertexFormat.getOffsetsByElement();
			boolean bl = vertexFormat == DefaultVertexFormat.NEW_ENTITY;
			boolean bl2 = vertexFormat == DefaultVertexFormat.BLOCK;
			this.fastFormat = (bl || bl2) && !extending; // Iris: disable fastFormat when extending
			this.fullFormat = bl;
		}
	}
	
	// Iris: Extend vertex format for terrain, entity, and glyph rendering
	private VertexFormat iris$extendFormat(VertexFormat format) {
		injectNormalAndUV1 = false;

		if (ImmediateState.skipExtension.get()
			|| !ImmediateState.isRenderingLevel
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| !Iris.isPackInUseQuick()) {
			return format;
		}

		if (format == DefaultVertexFormat.BLOCK || format == IrisVertexFormats.TERRAIN) {
			extending = true;
			injectNormalAndUV1 = false;
			return IrisVertexFormats.TERRAIN;
		} else if (format == DefaultVertexFormat.NEW_ENTITY || format == IrisVertexFormats.ENTITY) {
			extending = true;
			injectNormalAndUV1 = false;
			return IrisVertexFormats.ENTITY;
		} else if (format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP || format == IrisVertexFormats.GLYPH) {
			extending = true;
			injectNormalAndUV1 = true;
			return IrisVertexFormats.GLYPH;
		}

		return format;
	}

	@Nullable
	public MeshData build() {
		this.ensureBuilding();
		this.endLastVertex();
		MeshData meshData = this.storeMesh();
		this.building = false;
		this.vertexPointer = -1L;
		return meshData;
	}

	public MeshData buildOrThrow() {
		MeshData meshData = this.build();
		if (meshData == null) {
			throw new IllegalStateException("BufferBuilder was empty");
		} else {
			return meshData;
		}
	}

	private void ensureBuilding() {
		if (!this.building) {
			throw new IllegalStateException("Not building!");
		}
	}

	@Nullable
	private MeshData storeMesh() {
		if (this.vertices == 0) {
			return null;
		} else {
			ByteBufferBuilder.Result result = this.buffer.build();
			if (result == null) {
				return null;
			} else {
				int i = this.mode.indexCount(this.vertices);
				VertexFormat.IndexType indexType = VertexFormat.IndexType.least(this.vertices);
				return new MeshData(result, new MeshData.DrawState(this.format, this.vertices, i, this.mode, indexType));
			}
		}
	}

	private long beginVertex() {
		this.ensureBuilding();
		this.endLastVertex();
		if (this.vertices >= 16777215) {
			throw new IllegalStateException("Trying to write too many vertices (>16777215) into BufferBuilder");
		} else {
			this.vertices++;
			long l = this.buffer.reserve(this.vertexSize);
			this.vertexPointer = l;
			return l;
		}
	}

	private long beginElement(VertexFormatElement vertexFormatElement) {
		int i = this.elementsToFill;
		int j = i & ~vertexFormatElement.mask();
		if (j == i) {
			return -1L;
		} else {
			this.elementsToFill = j;
			long l = this.vertexPointer;
			if (l == -1L) {
				throw new IllegalArgumentException("Not currently building vertex");
			} else {
				return l + this.offsetsByElement[vertexFormatElement.id()];
			}
		}
	}

	private void endLastVertex() {
		if (this.vertices != 0) {
			// Iris: Extended vertex format handling
			if (this.vertices > 0 && extending) {
				iris$beforeNext();
			}
			
			if (this.elementsToFill != 0) {
				String string = (String)VertexFormatElement.elementsFromMask(this.elementsToFill).map(this.format::getElementName).collect(Collectors.joining(", "));
				throw new IllegalStateException("Missing elements in vertex: " + string);
			} else {
				if (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP) {
					long l = this.buffer.reserve(this.vertexSize);
					MemoryUtil.memCopy(l - this.vertexSize, l, this.vertexSize);
					this.vertices++;
				}
			}
		}
	}
	
	// Iris: Process vertex before completion
	private void iris$beforeNext() {
		// We can't fill these yet
		this.elementsToFill = this.elementsToFill & ~IrisVertexFormats.MID_TEXTURE_ELEMENT.mask();
		this.elementsToFill = this.elementsToFill & ~IrisVertexFormats.TANGENT_ELEMENT.mask();

		if (injectNormalAndUV1 && this.elementsToFill != (this.elementsToFill & ~VertexFormatElement.NORMAL.mask())) {
			this.setNormal(0, 1, 0);
		}

		if (skipEndVertexOnce) {
			skipEndVertexOnce = false;
			return;
		}

		if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
			return;
		}

		vertexOffsets[iris$vertexCount] = vertexPointer - ((MojangBufferAccessor) buffer).getPointer();

		iris$vertexCount++;

		if (mode == VertexFormat.Mode.QUADS && iris$vertexCount == 4 || mode == VertexFormat.Mode.TRIANGLES && iris$vertexCount == 3) {
			fillExtendedData(iris$vertexCount);
		}
	}
	
	// Iris: Fill extended vertex data (tangents, mid-texture coordinates)
	private void fillExtendedData(int vertexAmount) {
		iris$vertexCount = 0;

		int stride = format.getVertexSize();

		polygon.setup(((MojangBufferAccessor) buffer).getPointer(), vertexOffsets, stride, vertexAmount);

		float midU = 0;
		float midV = 0;

		for (int vertex = 0; vertex < vertexAmount; vertex++) {
			midU += polygon.u(vertex);
			midV += polygon.v(vertex);
		}

		midU /= vertexAmount;
		midV /= vertexAmount;

		int midTexOffset = this.offsetsByElement[IrisVertexFormats.MID_TEXTURE_ELEMENT.id()];
		int normalOffset = this.offsetsByElement[VertexFormatElement.NORMAL.id()];
		int tangentOffset = this.offsetsByElement[IrisVertexFormats.TANGENT_ELEMENT.id()];
		
		if (vertexAmount == 3) {
			// Smooth shaded triangles - use per-vertex normals
			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = ((MojangBufferAccessor) buffer).getPointer() + vertexOffsets[vertex];
				int vertexNormal = MemoryUtil.memGetInt(newPointer + normalOffset);

				int tangent = NormalHelper.computeTangentSmooth(NormI8.unpackX(vertexNormal), NormI8.unpackY(vertexNormal), NormI8.unpackZ(vertexNormal), polygon);

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				MemoryUtil.memPutInt(newPointer + tangentOffset, tangent);
			}
		} else {
			// Quads - compute face normal
			boolean recalculateNormal = ImmediateState.isRenderingLevel;
			NormalHelper.computeFaceNormal(normal, polygon);
			int packedNormal = 0;
			if (recalculateNormal) {
				packedNormal = NormI8.pack(normal.x, normal.y, normal.z, 0.0f);
			}
			int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, polygon);

			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = ((MojangBufferAccessor) buffer).getPointer() + vertexOffsets[vertex];

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				if (recalculateNormal) {
					MemoryUtil.memPutInt(newPointer + normalOffset, packedNormal);
				}
				MemoryUtil.memPutInt(newPointer + tangentOffset, tangent);
			}
		}

		Arrays.fill(vertexOffsets, 0);
	}

	private static void putRgba(long l, int i) {
		int j = ARGB.toABGR(i);
		MemoryUtil.memPutInt(l, IS_LITTLE_ENDIAN ? j : Integer.reverseBytes(j));
	}

	private static void putPackedUv(long l, int i) {
		if (IS_LITTLE_ENDIAN) {
			MemoryUtil.memPutInt(l, i);
		} else {
			MemoryUtil.memPutShort(l, (short)(i & 65535));
			MemoryUtil.memPutShort(l + 2L, (short)(i >> 16 & 65535));
		}
	}

	@Override
	public VertexConsumer addVertex(float f, float g, float h) {
		long l = this.beginVertex() + this.offsetsByElement[VertexFormatElement.POSITION.id()];
		this.elementsToFill = this.initialElementsToFill;
		MemoryUtil.memPutFloat(l, f);
		MemoryUtil.memPutFloat(l + 4L, g);
		MemoryUtil.memPutFloat(l + 8L, h);
		
		// Iris: Inject extended vertex data (MID_BLOCK, ENTITY_ELEMENT, ENTITY_ID_ELEMENT)
		iris$injectMidBlock(f, g, h);
		
		return this;
	}
	
	// Iris: Inject mid-block and entity ID data after vertex position
	private void iris$injectMidBlock(float x, float y, float z) {
		if ((this.elementsToFill & IrisVertexFormats.MID_BLOCK_ELEMENT.mask()) != 0) {
			long midBlockOffset = this.beginElement(IrisVertexFormats.MID_BLOCK_ELEMENT);
			MemoryUtil.memPutInt(midBlockOffset, ExtendedDataHelper.computeMidBlock(x, y, z, currentLocalPosX, currentLocalPosY, currentLocalPosZ));
			byte currentBlockEmission = -1;
			MemoryUtil.memPutByte(midBlockOffset + 3, currentBlockEmission);
		}

		if ((this.elementsToFill & IrisVertexFormats.ENTITY_ELEMENT.mask()) != 0) {
			long offset = this.beginElement(IrisVertexFormats.ENTITY_ELEMENT);
			// ENTITY_ELEMENT
			MemoryUtil.memPutShort(offset, (short) currentBlock);
			MemoryUtil.memPutShort(offset + 2, currentRenderType);
		} else if ((this.elementsToFill & IrisVertexFormats.ENTITY_ID_ELEMENT.mask()) != 0) {
			long offset = this.beginElement(IrisVertexFormats.ENTITY_ID_ELEMENT);
			// ENTITY_ID_ELEMENT
			MemoryUtil.memPutShort(offset, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
			MemoryUtil.memPutShort(offset + 2, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
			MemoryUtil.memPutShort(offset + 4, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
		}
	}

	@Override
	public VertexConsumer setColor(int i, int j, int k, int l) {
		long m = this.beginElement(VertexFormatElement.COLOR);
		if (m != -1L) {
			MemoryUtil.memPutByte(m, (byte)i);
			MemoryUtil.memPutByte(m + 1L, (byte)j);
			MemoryUtil.memPutByte(m + 2L, (byte)k);
			MemoryUtil.memPutByte(m + 3L, (byte)l);
		}

		return this;
	}

	@Override
	public VertexConsumer setColor(int i) {
		long l = this.beginElement(VertexFormatElement.COLOR);
		if (l != -1L) {
			putRgba(l, i);
		}

		return this;
	}

	@Override
	public VertexConsumer setUv(float f, float g) {
		long l = this.beginElement(VertexFormatElement.UV0);
		if (l != -1L) {
			MemoryUtil.memPutFloat(l, f);
			MemoryUtil.memPutFloat(l + 4L, g);
		}

		return this;
	}

	@Override
	public VertexConsumer setUv1(int i, int j) {
		return this.uvShort((short)i, (short)j, VertexFormatElement.UV1);
	}

	@Override
	public VertexConsumer setOverlay(int i) {
		long l = this.beginElement(VertexFormatElement.UV1);
		if (l != -1L) {
			putPackedUv(l, i);
		}

		return this;
	}

	@Override
	public VertexConsumer setUv2(int i, int j) {
		return this.uvShort((short)i, (short)j, VertexFormatElement.UV2);
	}

	@Override
	public VertexConsumer setLight(int i) {
		long l = this.beginElement(VertexFormatElement.UV2);
		if (l != -1L) {
			putPackedUv(l, i);
		}

		return this;
	}

	private VertexConsumer uvShort(short s, short t, VertexFormatElement vertexFormatElement) {
		long l = this.beginElement(vertexFormatElement);
		if (l != -1L) {
			MemoryUtil.memPutShort(l, s);
			MemoryUtil.memPutShort(l + 2L, t);
		}

		return this;
	}

	@Override
	public VertexConsumer setNormal(float f, float g, float h) {
		long l = this.beginElement(VertexFormatElement.NORMAL);
		if (l != -1L) {
			MemoryUtil.memPutByte(l, normalIntValue(f));
			MemoryUtil.memPutByte(l + 1L, normalIntValue(g));
			MemoryUtil.memPutByte(l + 2L, normalIntValue(h));
		}

		return this;
	}

	private static byte normalIntValue(float f) {
		return (byte)((int)(Mth.clamp(f, -1.0F, 1.0F) * 127.0F) & 0xFF);
	}

	@Override
	public void addVertex(float f, float g, float h, int i, float j, float k, int l, int m, float n, float o, float p) {
		if (this.fastFormat) {
			long q = this.beginVertex();
			MemoryUtil.memPutFloat(q + 0L, f);
			MemoryUtil.memPutFloat(q + 4L, g);
			MemoryUtil.memPutFloat(q + 8L, h);
			putRgba(q + 12L, i);
			MemoryUtil.memPutFloat(q + 16L, j);
			MemoryUtil.memPutFloat(q + 20L, k);
			long r;
			if (this.fullFormat) {
				putPackedUv(q + 24L, l);
				r = q + 28L;
			} else {
				r = q + 24L;
			}

			putPackedUv(r + 0L, m);
			MemoryUtil.memPutByte(r + 4L, normalIntValue(n));
			MemoryUtil.memPutByte(r + 5L, normalIntValue(o));
			MemoryUtil.memPutByte(r + 6L, normalIntValue(p));
		} else {
			VertexConsumer.super.addVertex(f, g, h, i, j, k, l, m, n, o, p);
		}
	}

	@Override
	public void sodium$duplicateVertex() {
		if (this.vertices == 0) {
			return;
		}

		long head = this.buffer.reserve(this.vertexSize);
		MemoryIntrinsics.copyMemory(head - this.vertexSize, head, this.vertexSize);

		this.vertices++;
	}

	@Override
	public void push(MemoryStack stack, long src, int count, VertexFormat format) {
		var length = count * this.vertexSize;

		// The buffer may change in the even, so we need to make sure that the
		// pointer is retrieved *after* the resize
		var dst = this.buffer.reserve(length);

		if (format == this.format) {
			// The layout is the same, so we can just perform a memory copy
			// The stride of a vertex format is always 4 bytes, so this aligned copy is always safe
			MemoryIntrinsics.copyMemory(src, dst, length);
		} else {
			// The layout differs, so we need to perform a conversion on the vertex data
			this.copySlow(src, dst, count, format);
		}

		this.vertices += count;
		this.vertexPointer = (dst + length) - vertexSize;
		this.elementsToFill = 0;
	}

	private void copySlow(long src, long dst, int count, VertexFormat format) {
		VertexSerializerRegistry.instance()
				.get(format, this.format)
				.serialize(src, dst, count);
	}
	
	// Merged from Sodium BufferBuilderMixin (intrinsics) + Iris separate AO
	@Override
	public void putBulkData(PoseStack.Pose matrices, net.minecraft.client.renderer.block.model.BakedQuad bakedQuad, float r, float g, float b, float a, int light, int overlay) {
		// Sodium fast path
		if (this.fastFormat) {
			if (bakedQuad.vertices().length < 32) {
				return; // we do not accept quads with less than 4 properly sized vertices
			}

			net.sodium.api.vertex.buffer.VertexBufferWriter writer = net.sodium.api.vertex.buffer.VertexBufferWriter.of(this);
			ModelQuadView quad = (ModelQuadView) (Object) bakedQuad;

			int color = net.sodium.api.util.ColorABGR.pack(r, g, b, a);
			BakedModelEncoder.writeQuadVertices(writer, matrices, quad, color, light, overlay, false);

			if (quad.getSprite() != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(quad.getSprite());
			}
			return;
		}

		// Fallback to default
		VertexConsumer.super.putBulkData(matrices, bakedQuad, r, g, b, a, light, overlay);

		if (bakedQuad.sprite() != null) {
			net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(bakedQuad.sprite());
		}
	}

	@Override
	public void putBulkData(PoseStack.Pose matrices, net.minecraft.client.renderer.block.model.BakedQuad bakedQuad, float[] brightnessTable, float red, float green,
							float blue, float alpha, int[] lights, int overlay, boolean colorize) {
		// Iris: Apply separate AO if needed
		if (net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
			float[] modifiedBrightness = new float[brightnessTable.length];
			java.util.Arrays.fill(modifiedBrightness, 1.0f);
			brightnessTable = modifiedBrightness;
		}

		// Sodium fast path
		if (this.fastFormat) {
			if (bakedQuad.vertices().length < 32) {
				return; // we do not accept quads with less than 4 properly sized vertices
			}

			net.sodium.api.vertex.buffer.VertexBufferWriter writer = net.sodium.api.vertex.buffer.VertexBufferWriter.of(this);
			ModelQuadView quad = (ModelQuadView) (Object) bakedQuad;

			BakedModelEncoder.writeQuadVertices(writer, matrices, quad, red, green, blue, alpha, brightnessTable, colorize, lights, overlay);

			if (quad.getSprite() != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(quad.getSprite());
			}
			return;
		}

		// Fallback to default
		VertexConsumer.super.putBulkData(matrices, bakedQuad, brightnessTable, red, green, blue, alpha, lights, overlay, colorize);

		if (bakedQuad.sprite() != null) {
			net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(bakedQuad.sprite());
		}
	}
	
	// Iris: BlockSensitiveBufferBuilder interface implementation
	@Override
	public void beginBlock(int block, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
		this.currentBlock = block;
		this.currentRenderType = renderType;
		this.currentLocalPosX = localPosX;
		this.currentLocalPosY = localPosY;
		this.currentLocalPosZ = localPosZ;
	}

	@Override
	public void endBlock() {
		this.currentBlock = -1;
		this.currentRenderType = -1;
		this.currentLocalPosX = 0;
		this.currentLocalPosY = 0;
		this.currentLocalPosZ = 0;
	}
	
	@Override
	public void overrideBlock(int block) {
		// Iris: Override current block temporarily (unused in BufferBuilder, but required by interface)
	}
	
	@Override
	public void restoreBlock() {
		// Iris: Restore previously overridden block (unused in BufferBuilder, but required by interface)
	}
	
	@Override
	public void ignoreMidBlock(boolean b) {
		// Iris: Control whether to ignore mid-block data (unused in BufferBuilder, but required by interface)
	}
	
	// Sodium: Skip endLastVertex when Sodium calls push() - used by dynamic remap
	public void push() {
		// This method is called by Sodium via mixin - skip the next endLastVertex call
		skipEndVertexOnce = true;
	}
}

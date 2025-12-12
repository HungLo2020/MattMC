package com.mojang.blaze3d.vertex;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.shaders.IrisShaders;
import net.minecraft.client.renderer.shaders.vertices.BlockSensitiveBufferBuilder;
import net.minecraft.client.renderer.shaders.vertices.BufferBuilderPolygonView;
import net.minecraft.client.renderer.shaders.vertices.ExtendedDataHelper;
import net.minecraft.client.renderer.shaders.vertices.ImmediateState;
import net.minecraft.client.renderer.shaders.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.shaders.vertices.NormalHelper;
import net.minecraft.client.renderer.shaders.vertices.NormI8;
import net.minecraft.client.renderer.shaders.uniform.providers.CapturedRenderingState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class BufferBuilder implements VertexConsumer, BlockSensitiveBufferBuilder {
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
	
	// IRIS vertex format extension fields (following MixinBufferBuilder pattern)
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();
	private final Vector3f normal = new Vector3f();
	private final long[] vertexOffsets = new long[4];
	private boolean extending;
	private boolean injectNormalAndUV1;
	private int irisVertexCount;
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
			
			// IRIS vertex format extension (following MixinBufferBuilder pattern)
			VertexFormat extendedFormat = extendFormat(vertexFormat);
			
			this.format = extendedFormat;
			this.vertexSize = extendedFormat.getVertexSize();
			this.initialElementsToFill = extendedFormat.getElementsMask() & ~VertexFormatElement.POSITION.mask();
			this.offsetsByElement = extendedFormat.getOffsetsByElement();
			boolean bl = extendedFormat == DefaultVertexFormat.NEW_ENTITY;
			boolean bl2 = extendedFormat == DefaultVertexFormat.BLOCK;
			// Disable fast format when extending to ensure all elements are properly filled
			this.fastFormat = (bl || bl2) && !extending;
			this.fullFormat = bl;
		}
	}
	
	/**
	 * Extends the vertex format for IRIS shader pack compatibility.
	 * VERBATIM from IRIS: MixinBufferBuilder.iris$extendFormat()
	 */
	private VertexFormat extendFormat(VertexFormat format) {
		extending = false;
		injectNormalAndUV1 = false;

		// Skip extension if not rendering level or shaders not active
		if (ImmediateState.skipExtension.get() || !ImmediateState.isRenderingLevel || !IrisShaders.isEnabled()) {
			return format;
		}
		
		// CRITICAL: Only extend vertex format if shader interception is actually active
		// Otherwise we create TERRAIN format vertices but vanilla shaders expect BLOCK format
		// This causes black triangular artifacts due to vertex stride/attribute mismatch
		net.minecraft.client.renderer.shaders.pipeline.ShaderPackPipeline activePipeline = IrisShaders.getActivePipeline();
		if (activePipeline == null || !activePipeline.shouldOverrideShaders()) {
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
		if (this.vertices == 0) {
			return;
		}
		
		// IRIS extension: Process extended data before checking for missing elements
		if (extending) {
			// We'll fill mid-texture and tangent later, so mark them as not required
			this.elementsToFill = this.elementsToFill & ~IrisVertexFormats.MID_TEXTURE_ELEMENT.mask();
			this.elementsToFill = this.elementsToFill & ~IrisVertexFormats.TANGENT_ELEMENT.mask();

			if (injectNormalAndUV1 && (this.elementsToFill & VertexFormatElement.NORMAL.mask()) != 0) {
				this.setNormal(0, 1, 0);
			}

			if (mode == VertexFormat.Mode.QUADS || mode == VertexFormat.Mode.TRIANGLES) {
				// Store offset relative to buffer base pointer (following IRIS pattern exactly)
				// IRIS: vertexOffsets[iris$vertexCount] = vertexPointer - ((MojangBufferAccessor) buffer).getPointer();
				vertexOffsets[irisVertexCount] = vertexPointer - buffer.position();

				irisVertexCount++;

				if ((mode == VertexFormat.Mode.QUADS && irisVertexCount == 4) || 
					(mode == VertexFormat.Mode.TRIANGLES && irisVertexCount == 3)) {
					fillExtendedData(irisVertexCount);
				}
			}
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
	
	/**
	 * Fills extended vertex data (mid-texture, tangent) for the completed polygon.
	 * VERBATIM from IRIS: MixinBufferBuilder.fillExtendedData()
	 */
	private void fillExtendedData(int vertexAmount) {
		irisVertexCount = 0;

		int stride = format.getVertexSize();
		long bufferStart = buffer.position();

		polygon.setup(bufferStart, vertexOffsets, stride, vertexAmount);

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
			// Triangle - use smooth shaded tangent
			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = bufferStart + vertexOffsets[vertex];
				int vertexNormal = MemoryUtil.memGetInt(newPointer + normalOffset);

				int tangent = NormalHelper.computeTangentSmooth(
					NormI8.unpackX(vertexNormal), 
					NormI8.unpackY(vertexNormal), 
					NormI8.unpackZ(vertexNormal), 
					polygon);

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				MemoryUtil.memPutInt(newPointer + tangentOffset, tangent);
			}
		} else {
			// Quad - compute face normal and tangent
			NormalHelper.computeFaceNormal(normal, polygon);
			int packedNormal = NormI8.pack(normal.x, normal.y, normal.z, 0.0f);
			int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, polygon);

			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = bufferStart + vertexOffsets[vertex];

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				// Recalculate normal for quads (following IRIS pattern)
				if (ImmediateState.isRenderingLevel) {
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
		
		// IRIS extension: Inject mid-block and entity data (following MixinBufferBuilder pattern)
		if (extending) {
			injectMidBlockAndEntity(f, g, h);
		}
		
		return this;
	}
	
	/**
	 * Injects mid-block and entity data for IRIS shaders.
	 * VERBATIM from IRIS: MixinBufferBuilder.injectMidBlock()
	 */
	private void injectMidBlockAndEntity(float x, float y, float z) {
		if ((this.elementsToFill & IrisVertexFormats.MID_BLOCK_ELEMENT.mask()) != 0) {
			long midBlockOffset = this.beginElement(IrisVertexFormats.MID_BLOCK_ELEMENT);
			if (midBlockOffset != -1L) {
				MemoryUtil.memPutInt(midBlockOffset, ExtendedDataHelper.computeMidBlock(x, y, z, currentLocalPosX, currentLocalPosY, currentLocalPosZ));
				byte currentBlockEmission = -1;
				MemoryUtil.memPutByte(midBlockOffset + 3, currentBlockEmission);
			}
		}

		if ((this.elementsToFill & IrisVertexFormats.ENTITY_ELEMENT.mask()) != 0) {
			long offset = this.beginElement(IrisVertexFormats.ENTITY_ELEMENT);
			if (offset != -1L) {
				// ENTITY_ELEMENT
				MemoryUtil.memPutShort(offset, (short) currentBlock);
				MemoryUtil.memPutShort(offset + 2, currentRenderType);
			}
		} else if ((this.elementsToFill & IrisVertexFormats.ENTITY_ID_ELEMENT.mask()) != 0) {
			long offset = this.beginElement(IrisVertexFormats.ENTITY_ID_ELEMENT);
			if (offset != -1L) {
				// ENTITY_ID_ELEMENT
				MemoryUtil.memPutShort(offset, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
				MemoryUtil.memPutShort(offset + 2, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
				MemoryUtil.memPutShort(offset + 4, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
			}
		}
	}
	
	// BlockSensitiveBufferBuilder implementation (IRIS pattern)
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
	
	/**
	 * Returns whether this BufferBuilder is extending vertex formats for IRIS.
	 */
	public boolean isExtending() {
		return extending;
	}
	
	/**
	 * Returns the vertex format being used.
	 */
	public VertexFormat getFormat() {
		return format;
	}
}

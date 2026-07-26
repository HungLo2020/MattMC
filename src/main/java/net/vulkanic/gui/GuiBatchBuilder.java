package net.vulkanic.gui;

import net.vulkanic.bridge.VulkanicGalBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class GuiBatchBuilder {
	public static final int UNIFORM_BYTES = 64;
	public static final int MAX_PACKED_SPRITES = 256;
	public static final int PACKED_UNIFORM_BYTES = UNIFORM_BYTES * MAX_PACKED_SPRITES;
	static final byte[] INDEX_BYTES = indexBytes();

	private GuiBatchBuilder() {
	}

	static List<FrameSpriteBatch> packCompatibleSpriteBatches(
		List<GuiSpriteRequest> requests,
		Function<GuiSpriteRequest, GuiResourceCache.CachedResources> resourceResolver
	) {
		List<FrameSpriteBatch> packed = new ArrayList<>();
		FrameSpriteBatchBuilder current = null;
		for (GuiSpriteRequest request : requests) {
			GuiResourceCache.CachedResources resources = resourceResolver.apply(request);
			PackedSprite sprite = PackedSprite.from(request);
			if (current == null || !current.canAppend(request, resources)) {
				if (current != null) {
					packed.add(current.build());
				}
				current = new FrameSpriteBatchBuilder(request, resources);
			}
			current.add(sprite);
		}
		if (current != null) {
			packed.add(current.build());
		}
		return packed;
	}

	static VulkanicGalBridge.SubmissionBatch buildSubmission(
		VulkanicGalBridge bridge,
		long framePass,
		long frameTarget,
		List<FrameSpriteBatch> spriteBatches
	) {
		VulkanicGalBridge.SubmissionBatchBuilder builder = bridge.submissionBatchBuilder("minecraft.gui.frame");
		for (FrameSpriteBatch spriteBatch : spriteBatches) {
			GuiResourceCache.CachedResources resources = spriteBatch.resources();
			builder.barrier(resources.uniformBuffer(), VulkanicGalBridge.USAGE_SHADER_READ, VulkanicGalBridge.USAGE_TRANSFER_DST, false)
				.hostWrite(resources.uniformBuffer(), 0, packedUniformBytes(spriteBatch.sprites()))
				.barrier(resources.uniformBuffer(), VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
				.beginFramePass(framePass, frameTarget)
				.bindGraphicsPipeline(resources.pipeline())
				.bindResourceSet(resources.pipelineLayout(), resources.resourceSet())
				.setIndexBuffer(resources.indexBuffer())
				.drawIndexed(6, spriteBatch.sprites().size())
				.endPass();
		}
		return builder.build();
	}

	public static List<Integer> debugPackCompatibleRunLengthsForTests(List<GuiRenderStratum> strata, List<String> resourceKeys) {
		if (strata.size() != resourceKeys.size()) {
			throw new IllegalArgumentException("strata and resource key test inputs must have matching sizes");
		}
		List<Integer> runs = new ArrayList<>();
		GuiRenderStratum currentStratum = null;
		String currentKey = null;
		int currentSize = 0;
		for (int i = 0; i < strata.size(); i++) {
			GuiRenderStratum stratum = strata.get(i);
			String resourceKey = resourceKeys.get(i);
			if (currentSize == 0
				|| currentSize >= MAX_PACKED_SPRITES
				|| stratum != currentStratum
				|| !resourceKey.equals(currentKey)) {
				if (currentSize > 0) {
					runs.add(currentSize);
				}
				currentStratum = stratum;
				currentKey = resourceKey;
				currentSize = 1;
			} else {
				currentSize++;
			}
		}
		if (currentSize > 0) {
			runs.add(currentSize);
		}
		return runs;
	}

	public static List<String> debugPackedUniformCommandSequenceForTests(List<GuiRenderStratum> strata, List<String> resourceKeys) {
		List<Integer> runs = debugPackCompatibleRunLengthsForTests(strata, resourceKeys);
		List<String> sequence = new ArrayList<>(runs.size() * 9);
		for (int index = 0; index < runs.size(); index++) {
			sequence.add("batch-" + index + ":barrier-uniform-read-to-transfer");
			sequence.add("batch-" + index + ":host-write-uniforms");
			sequence.add("batch-" + index + ":barrier-uniform-transfer-to-read");
			sequence.add("batch-" + index + ":begin-frame-pass");
			sequence.add("batch-" + index + ":bind-pipeline");
			sequence.add("batch-" + index + ":bind-resource-set");
			sequence.add("batch-" + index + ":set-index-buffer");
			sequence.add("batch-" + index + ":draw-indexed");
			sequence.add("batch-" + index + ":end-pass");
		}
		return sequence;
	}

	static float[] debugArmorOpenGlUvYRangeForTests(ArmorIconState state) {
		RustGalGuiRenderer.GuiSprite guiSprite = RustGalGuiRenderer.debugArmorSpriteForTests(state);
		PackedSprite sprite = new PackedSprite(
			guiSprite,
			"minecraft.gui.armor." + state.id(),
			0,
			1.0F,
			GuiFillDirection.NONE,
			0xFFFFFFFF,
			0,
			0,
			9,
			9,
			0,
			0,
			9,
			9,
			320,
			180
		);
		ByteBuffer uniforms = ByteBuffer.wrap(packedUniformBytes(List.of(sprite))).order(ByteOrder.nativeOrder());
		float originY = uniforms.getFloat(36);
		float height = uniforms.getFloat(44);
		return new float[] {originY + height, originY};
	}

	static int[] debugArmorOpenGlSampledLocalRowsForTests(ArmorIconState state, int guiScale) {
		if (guiScale <= 0) {
			throw new IllegalArgumentException("GUI scale must be positive: " + guiScale);
		}
		RustGalGuiRenderer.GuiSprite guiSprite = RustGalGuiRenderer.debugArmorSpriteForTests(state);
		PackedSprite sprite = new PackedSprite(
			guiSprite,
			"minecraft.gui.armor." + state.id(),
			0,
			1.0F,
			GuiFillDirection.NONE,
			0xFFFFFFFF,
			0,
			0,
			9,
			9,
			0,
			0,
			9,
			9,
			320,
			180
		);
		GuiSpriteAtlas.TextureAtlas atlas = GuiSpriteAtlas.atlasFor(guiSprite.textureGroup);
		GuiSpriteAtlas.AtlasRegion region = atlas.region(guiSprite);
		ByteBuffer uniforms = ByteBuffer.wrap(packedUniformBytes(List.of(sprite))).order(ByteOrder.nativeOrder());
		int originY = Math.round(uniforms.getFloat(36) * atlas.height());
		int extentY = Math.round(uniforms.getFloat(44) * atlas.height());
		int[] rows = new int[sprite.height() * guiScale];
		for (int y = 0; y < rows.length; y++) {
			float cornerY = (y + 0.5F) / rows.length;
			int sourceY = Math.min(extentY - 1, Math.max(0, (int)Math.floor(cornerY * extentY)));
			int glY = originY + extentY - 1 - sourceY;
			int atlasTopY = atlas.height() - 1 - glY;
			rows[y] = atlasTopY - region.y() - sprite.sourceY();
		}
		return rows;
	}

	private static byte[] packedUniformBytes(List<PackedSprite> sprites) {
		if (sprites.isEmpty() || sprites.size() > MAX_PACKED_SPRITES) {
			throw new IllegalArgumentException("packed GUI sprite count must be in 1.." + MAX_PACKED_SPRITES + ": " + sprites.size());
		}
		ByteBuffer buffer = ByteBuffer.allocate(UNIFORM_BYTES * sprites.size()).order(ByteOrder.nativeOrder());
		for (PackedSprite sprite : sprites) {
			GuiSpriteAtlas.TextureAtlas atlas = GuiSpriteAtlas.atlasFor(sprite.sprite().textureGroup);
			GuiSpriteAtlas.AtlasRegion region = atlas.region(sprite.sprite());
			buffer.putFloat(sprite.x());
			buffer.putFloat(sprite.y());
			buffer.putFloat(sprite.width());
			buffer.putFloat(sprite.height());
			buffer.putFloat(sprite.guiWidth());
			buffer.putFloat(sprite.guiHeight());
			buffer.putFloat(sprite.progressFraction());
			buffer.putFloat(sprite.fillDirection().ordinal());
			buffer.putFloat((region.x() + sprite.sourceX()) / (float)atlas.width());
			buffer.putFloat((atlas.height() - (region.y() + sprite.sourceY() + sprite.sourceHeight())) / (float)atlas.height());
			buffer.putFloat(sprite.sourceWidth() / (float)atlas.width());
			buffer.putFloat(sprite.sourceHeight() / (float)atlas.height());
			buffer.putFloat(((sprite.colorArgb() >>> 16) & 0xFF) / 255.0F);
			buffer.putFloat(((sprite.colorArgb() >>> 8) & 0xFF) / 255.0F);
			buffer.putFloat((sprite.colorArgb() & 0xFF) / 255.0F);
			buffer.putFloat(((sprite.colorArgb() >>> 24) & 0xFF) / 255.0F);
		}
		return buffer.array();
	}

	private static byte[] indexBytes() {
		byte[] bytes = new byte[24];
		int[] values = {0, 1, 2, 3, 4, 5};
		for (int i = 0; i < values.length; i++) {
			int value = values[i];
			int offset = i * 4;
			bytes[offset] = (byte)value;
			bytes[offset + 1] = (byte)(value >>> 8);
			bytes[offset + 2] = (byte)(value >>> 16);
			bytes[offset + 3] = (byte)(value >>> 24);
		}
		return bytes;
	}

	record GuiSpriteRequest(
		GuiRenderStratum stratum,
		RustGalGuiRenderer.GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		GuiFillDirection fillDirection,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight,
		int guiWidth,
		int guiHeight
	) {
	}

	record FrameSpriteBatch(GuiRenderStratum stratum, GuiResourceCache.CachedResources resources, List<PackedSprite> sprites) {
	}

	private static final class FrameSpriteBatchBuilder {
		private final GuiRenderStratum stratum;
		private final GuiResourceCache.CachedResources resources;
		private final List<PackedSprite> sprites = new ArrayList<>();

		FrameSpriteBatchBuilder(GuiSpriteRequest first, GuiResourceCache.CachedResources resources) {
			this.stratum = first.stratum();
			this.resources = resources;
		}

		boolean canAppend(GuiSpriteRequest request, GuiResourceCache.CachedResources resources) {
			return this.sprites.size() < MAX_PACKED_SPRITES
				&& request.stratum() == this.stratum
				&& resources.sameBindingsAs(this.resources);
		}

		void add(PackedSprite sprite) {
			this.sprites.add(sprite);
		}

		FrameSpriteBatch build() {
			return new FrameSpriteBatch(this.stratum, this.resources, List.copyOf(this.sprites));
		}
	}

	private record PackedSprite(
		RustGalGuiRenderer.GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		GuiFillDirection fillDirection,
		int colorArgb,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight,
		int guiWidth,
		int guiHeight
	) {
		static PackedSprite from(GuiSpriteRequest request) {
			return new PackedSprite(
				request.sprite(),
				request.producerId(),
				request.selectedSlot(),
				request.progressFraction(),
				request.fillDirection(),
				0xFFFFFFFF,
				request.x(),
				request.y(),
				request.width(),
				request.height(),
				request.sourceX(),
				request.sourceY(),
				request.sourceWidth(),
				request.sourceHeight(),
				request.guiWidth(),
				request.guiHeight()
			);
		}
	}
}

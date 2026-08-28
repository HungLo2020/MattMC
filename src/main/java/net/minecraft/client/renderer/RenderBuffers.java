package net.minecraft.client.renderer;

import net.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.SequencedMap;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.hooks.RenderBuffersHooks;

@Environment(EnvType.CLIENT)
public class RenderBuffers {
	private final SectionBufferBuilderPack fixedBufferPack = new SectionBufferBuilderPack(rustWholeFrameVulkan());
	private final SectionBufferBuilderPool sectionBufferPool;
	private final MultiBufferSource.BufferSource bufferSource;
	private final MultiBufferSource.BufferSource crumblingBufferSource;
	private final OutlineBufferSource outlineBufferSource;

	public RenderBuffers(int i) {
		// Allow hooks to provide custom section buffer pool
		SectionBufferBuilderPool customPool = null;
		// Rust owns semantic terrain extraction on Vulkan. Do not allow an
		// extension hook to reintroduce a Java staging pool after ownership has
		// transferred; the fallback allocator supplies one minimal bookkeeping
		// pack for visibility bookkeeping instead.
		if (!rustWholeFrameVulkan()) {
			for (RenderBuffersHooks hook : HookRegistry.getRenderBuffersHooks()) {
				customPool = hook.provideSectionBufferPool(i);
				if (customPool != null) {
					break;
				}
			}
		}
		
		// Fall back to default implementation if no hook provided a result
		this.sectionBufferPool = (customPool != null) ? customPool : SectionBufferBuilderPool.allocate(i);
		SequencedMap<RenderType, ByteBufferBuilder> sequencedMap = (SequencedMap<RenderType, ByteBufferBuilder>)Util.make(
			new Object2ObjectLinkedOpenHashMap(), object2ObjectLinkedOpenHashMap -> {
				object2ObjectLinkedOpenHashMap.put(Sheets.solidBlockSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.SOLID));
				object2ObjectLinkedOpenHashMap.put(Sheets.cutoutBlockSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.CUTOUT));
				object2ObjectLinkedOpenHashMap.put(Sheets.bannerSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.CUTOUT_MIPPED));
				object2ObjectLinkedOpenHashMap.put(Sheets.translucentItemSheet(), this.fixedBufferPack.buffer(ChunkSectionLayer.TRANSLUCENT));
				put(object2ObjectLinkedOpenHashMap, Sheets.shieldSheet());
				put(object2ObjectLinkedOpenHashMap, Sheets.bedSheet());
				put(object2ObjectLinkedOpenHashMap, Sheets.shulkerBoxSheet());
				put(object2ObjectLinkedOpenHashMap, Sheets.signSheet());
				put(object2ObjectLinkedOpenHashMap, Sheets.hangingSignSheet());
				object2ObjectLinkedOpenHashMap.put(Sheets.chestSheet(), new ByteBufferBuilder(rustWholeFrameVulkan() ? 0 : 786432));
				put(object2ObjectLinkedOpenHashMap, RenderType.armorEntityGlint());
				put(object2ObjectLinkedOpenHashMap, RenderType.glint());
				put(object2ObjectLinkedOpenHashMap, RenderType.glintTranslucent());
				put(object2ObjectLinkedOpenHashMap, RenderType.entityGlint());
				put(object2ObjectLinkedOpenHashMap, RenderType.waterMask());
			}
		);
		this.bufferSource = MultiBufferSource.immediateWithBuffers(sequencedMap, new ByteBufferBuilder(rustWholeFrameVulkan() ? 0 : 786432));
		this.outlineBufferSource = new OutlineBufferSource();
		SequencedMap<RenderType, ByteBufferBuilder> sequencedMap2 = (SequencedMap<RenderType, ByteBufferBuilder>)Util.make(
			new Object2ObjectLinkedOpenHashMap(),
			object2ObjectLinkedOpenHashMap -> ModelBakery.DESTROY_TYPES.forEach(renderType -> put(object2ObjectLinkedOpenHashMap, renderType))
		);
		this.crumblingBufferSource = MultiBufferSource.immediateWithBuffers(sequencedMap2, new ByteBufferBuilder(0));
	}

	private static boolean rustWholeFrameVulkan() {
		return net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
	}

	private static void put(Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> object2ObjectLinkedOpenHashMap, RenderType renderType) {
		object2ObjectLinkedOpenHashMap.put(renderType, new ByteBufferBuilder(rustWholeFrameVulkan() ? 0 : renderType.bufferSize()));
	}

	public SectionBufferBuilderPack fixedBufferPack() {
		return this.fixedBufferPack;
	}

	public SectionBufferBuilderPool sectionBufferPool() {
		return this.sectionBufferPool;
	}

	public MultiBufferSource.BufferSource bufferSource() {
		return this.bufferSource;
	}

	public MultiBufferSource.BufferSource crumblingBufferSource() {
		return this.crumblingBufferSource;
	}

	public OutlineBufferSource outlineBufferSource() {
		return this.outlineBufferSource;
	}
}

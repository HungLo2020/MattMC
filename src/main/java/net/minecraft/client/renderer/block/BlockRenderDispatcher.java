package net.minecraft.client.renderer.block;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

@Environment(EnvType.CLIENT)
public class BlockRenderDispatcher implements ResourceManagerReloadListener {
	private final BlockModelShaper blockModelShaper;
	private final MaterialSet materials;
	private final ModelBlockRenderer modelRenderer;
	public final Supplier<SpecialBlockModelRenderer> specialBlockModelRenderer;
	private final LiquidBlockRenderer liquidBlockRenderer;
	private final RandomSource singleThreadRandom = RandomSource.create();
	private final List<BlockModelPart> singleThreadPartList = new ArrayList();
	private final BlockColors blockColors;

	public BlockRenderDispatcher(BlockModelShaper blockModelShaper, MaterialSet materialSet, Supplier<SpecialBlockModelRenderer> supplier, BlockColors blockColors) {
		this.blockModelShaper = blockModelShaper;
		this.materials = materialSet;
		this.specialBlockModelRenderer = supplier;
		this.blockColors = blockColors;
		this.modelRenderer = new ModelBlockRenderer(this.blockColors);
		this.liquidBlockRenderer = new LiquidBlockRenderer();
	}

	public BlockModelShaper getBlockModelShaper() {
		return this.blockModelShaper;
	}

	public ModelBlockRenderer getModelRenderer() {
		return this.modelRenderer;
	}

	public BlockStateModel getBlockModel(BlockState blockState) {
		return this.blockModelShaper.getBlockModel(blockState);
	}

	public void onResourceManagerReload(ResourceManager resourceManager) {
		this.liquidBlockRenderer.setupSprites(this.blockModelShaper, this.materials);
	}
}

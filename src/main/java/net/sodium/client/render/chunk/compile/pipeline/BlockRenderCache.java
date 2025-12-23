package net.sodium.client.render.chunk.compile.pipeline;

import net.sodium.client.model.color.ColorProviderRegistry;
import net.sodium.client.model.light.LightPipelineProvider;
import net.sodium.client.model.light.data.ArrayLightDataCache;
import net.sodium.client.services.FluidRendererFactory;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelShaper;

public class BlockRenderCache {
    private final ArrayLightDataCache lightDataCache;

    private final BlockRenderer blockRenderer;
    private final FluidRenderer fluidRenderer;

    private final BlockModelShaper blockModels;
    private final LevelSlice levelSlice;

    public BlockRenderCache(Minecraft minecraft, ClientLevel level) {
        this.levelSlice = new LevelSlice(level);
        this.lightDataCache = new ArrayLightDataCache(this.levelSlice);

        LightPipelineProvider lightPipelineProvider = new LightPipelineProvider(this.lightDataCache);

        var colorRegistry = new ColorProviderRegistry(minecraft.getBlockColors());

        this.blockRenderer = new BlockRenderer(colorRegistry, lightPipelineProvider);
        this.fluidRenderer = FluidRendererFactory.getInstance().createPlatformFluidRenderer(colorRegistry, lightPipelineProvider);

        this.blockModels = minecraft.getModelManager().getBlockModelShaper();
    }

    public BlockModelShaper getBlockModels() {
        return this.blockModels;
    }

    public BlockRenderer getBlockRenderer() {
        return this.blockRenderer;
    }

    public FluidRenderer getFluidRenderer() {
        return this.fluidRenderer;
    }

    public void init(ChunkRenderContext context) {
        this.lightDataCache.reset(context.getOrigin());
        this.levelSlice.copyData(context);
    }

    public LevelSlice getWorldSlice() {
        return this.levelSlice;
    }

    public void cleanup() {
        this.levelSlice.reset();
    }
}

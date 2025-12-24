package com.seibel.distanthorizons.fabric.mixins.mods.sodium;

/* Removed since DH now uses Indium so we can use the Fabric rendering API instead

// Sodium 0.5
import com.mojang.blaze3d.vertex.PoseStack;
import net.distant_horizons.core.api.internal.ClientApi;
import net.distant_horizons.core.dependencyInjection.ModAccessorInjector;
import net.distant_horizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import net.distant_horizons.fabric.wrappers.modAccessor.SodiumAccessor;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DefaultChunkRenderer.class)
public class MixinSodiumRenderer
{
    @Unique SodiumAccessor accessor = null;

    @Inject(remap = false, method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lme/jellysquid/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.AFTER))
    private void injectDHLoDRendering(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo ci)
    {
        if (accessor == null)
        {
            accessor = (SodiumAccessor)ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
        }

        if (renderPass.equals(DefaultTerrainRenderPasses.SOLID))
        {
            //TODO: use matrices.modelView() and matrices.projection() instead of
            // SodiumAccessor.mcModelViewMatrix,
            // SodiumAccessor.mcProjectionMatrix,
            ClientApi.INSTANCE.renderLods(accessor.levelWrapper,
                    accessor.mcModelViewMatrix,
                    accessor.mcProjectionMatrix,
                    accessor.partialTicks);
        }
    }


}

// Sodium 0.3 to 0.4
import net.distant_horizons.core.api.internal.ClientApi;
import net.distant_horizons.core.dependencyInjection.ModAccessorInjector;
import net.distant_horizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import net.distant_horizons.fabric.wrappers.modAccessor.SodiumAccessor;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RegionChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegionChunkRenderer.class)
public class MixinSodiumRenderer 
{
    @Unique SodiumAccessor accessor = null;

    @Inject(remap = false, method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lme/jellysquid/mods/sodium/client/render/chunk/passes/BlockRenderPass;)V", shift = At.Shift.AFTER))
    private void injectDHLoDRendering(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderList list, BlockRenderPass pass, ChunkCameraContext camera, CallbackInfo ci) 
    {
        if (accessor == null) 
		{
            accessor = (SodiumAccessor)ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
        }

        if (pass.equals(BlockRenderPass.SOLID)) 
		{
            //TODO: use matrices.modelView() and matrices.projection() instead of
            // SodiumAccessor.mcModelViewMatrix,
            // SodiumAccessor.mcProjectionMatrix,
            ClientApi.INSTANCE.renderLods(accessor.levelWrapper,
                    accessor.mcModelViewMatrix,
                    accessor.mcProjectionMatrix,
                    accessor.partialTicks);
        }
    }
	
	
}

 */
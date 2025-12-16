package net.caffeinemc.mods.sodium.fabric.level;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.sodium.model.color.ColorProviderRegistry;
import net.minecraft.client.renderer.sodium.model.light.LightPipelineProvider;
import net.minecraft.client.renderer.chunk.advanced.compile.pipeline.FluidRenderer;
import net.minecraft.client.renderer.sodium.services.PlatformLevelAccess;
import net.minecraft.client.renderer.sodium.world.LevelSlice;
import net.minecraft.client.renderer.sodium.world.SodiumAuxiliaryLightManager;
import net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Function;

public class FabricLevelAccess implements PlatformLevelAccess {
    @Override
    public @Nullable Object getBlockEntityData(BlockEntity blockEntity) {
        return blockEntity.getRenderData();
    }

    @Override
    public @Nullable SodiumAuxiliaryLightManager getLightManager(LevelChunk chunk, SectionPos pos) {
        return null;
    }
}

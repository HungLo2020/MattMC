package net.minecraft.client.dev;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Shared capture-only world state; never accesses a renderer or GPU device. */
public final class GraphicsAuditBlockDisplayFixture {
    private static final int ID = Integer.MIN_VALUE + 4096;
    private static final GraphicsAuditPhaseWait PHASE_WAIT = new GraphicsAuditPhaseWait();
    private GraphicsAuditBlockDisplayFixture() {}
    public static boolean requested() {
        return "magma".equals(System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", ""));
    }
    public static Vec3 position(Vec3 player, Vec3 look) {
        Vec3 forward = look.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : look;
        return player.add(forward.normalize().scale(4)).add(-0.5, -0.5, -0.5);
    }
    public static void install(Minecraft minecraft) {
        if (!requested() || minecraft.level == null || minecraft.player == null) return;
        var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, minecraft.level);
        display.setId(ID);
        display.setPos(position(minecraft.player.position(), minecraft.player.getLookAngle()));
        display.setBlockState(Blocks.MAGMA_BLOCK.defaultBlockState());
        display.setViewRange(16);
        display.setWidth(2);
        display.setHeight(2);
        minecraft.level.addEntity(display);
    }
    public static String receipt(Minecraft minecraft) {
        if (!requested()) return "null";
        var entity = minecraft.level == null ? null : minecraft.level.getEntity(ID);
        if (!(entity instanceof Display.BlockDisplay display)) return "{\"complete\":false}";
        return String.format(Locale.ROOT,
            "{\"fixture\":\"magma-display-v1\",\"block\":\"%s\",\"position\":[%.6f,%.6f,%.6f],\"complete\":%s}",
            display.getBlockState().getBlock().builtInRegistryHolder().key().location(),
            display.getX(), display.getY(), display.getZ(),
            display.getBlockState().equals(Blocks.MAGMA_BLOCK.defaultBlockState()) && !display.isRemoved());
    }

    public static String animationObservation(Minecraft minecraft) {
        if ((!requested() && !net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.requested())
            || minecraft.level == null) return "null";
        var texture = minecraft.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return "{\"kind\":\"missing-atlas\"}";
        var sprite = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/magma"));
        var resource = sprite.semanticAnimationResource();
        if (resource == null) return "{\"kind\":\"missing-owned-resource\"}";
        var declaration = resource.source().sprites().stream()
            .filter(value -> value.name().equals(sprite.contents().name())).findFirst().orElse(null);
        if (declaration == null) return "{\"kind\":\"missing-sprite-declaration\"}";
        return String.format(Locale.ROOT,
            "{\"kind\":\"rust-resource-producer\",\"generation\":%d,\"spriteId\":%d,\"producedTick\":%d,\"x\":%d,\"y\":%d}",
            resource.source().generation(), declaration.id(), resource.producedTickForDiagnostics(), sprite.getX(), sprite.getY());
    }

    public static boolean readyForCapture(Minecraft minecraft) {
        if ((!requested() && !net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.requested())
            || !Boolean.getBoolean("mattmc.dev.graphicsAuditMagmaCycleCapture")) return true;
        var texture = minecraft.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return PHASE_WAIT.observe(false);
        var sprite = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/magma"));
        var resource = sprite.semanticAnimationResource();
        if (resource == null) return PHASE_WAIT.observe(false);
        var declaration = resource.source().sprites().stream()
            .filter(value -> value.name().equals(sprite.contents().name())).findFirst().orElse(null);
        if (declaration == null) return PHASE_WAIT.observe(false);
        long duration = declaration.source().frames().stream().mapToLong(value -> value.durationTicks()).sum();
        // This schedules a screenshot candidate only. Native upload evidence
        // must still prove the exact sampled-source phase after the run.
        // Catch-up ticks can follow a visible tick with an empty use set. Such
        // a cycle boundary correctly retains older pixels; it is not a useful
        // screenshot candidate. Observe the submitted semantic use, do not
        // inject visibility or alter the animation clock to force an upload.
        return PHASE_WAIT.observe(GraphicsAuditPhaseWait.phaseMatches(resource.producedTickForDiagnostics(), duration,
                GraphicsAuditPhaseWait.requestedPhase(duration))
            && resource.producedTickNamedSpriteForDiagnostics(declaration.id()));
    }
}

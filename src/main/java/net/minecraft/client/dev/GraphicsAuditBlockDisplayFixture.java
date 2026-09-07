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
        // Baseline observation only: never advance the ticker or upload pixels.
        var ticker = sprite.contents().getCreatedTicker();
        if (ticker == null) return "{\"kind\":\"missing-frozen-ticker\"}";
        return String.format(Locale.ROOT,
            "{\"kind\":\"frozen-java-ticker\",\"frame\":%d,\"subFrame\":%d,\"x\":%d,\"y\":%d,\"uploadedRgbaFnv64\":\"%s\"}",
            ticker.frame, ticker.subFrame, sprite.getX(), sprite.getY(), sprite.contents().graphicsAuditUploadedRgbaFnv64());
    }

    public static boolean readyForCapture(Minecraft minecraft) {
        if ((!requested() && !net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.requested())
            || !Boolean.getBoolean("mattmc.dev.graphicsAuditMagmaCycleCapture")) return true;
        var texture = minecraft.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return PHASE_WAIT.observe(false);
        var contents = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/magma")).contents();
        var ticker = contents.getCreatedTicker();
        if (ticker == null) return PHASE_WAIT.observe(false);
        long duration = ticker.animationInfo.frames.stream().mapToLong(value -> value.time()).sum();
        long phase = ticker.subFrame;
        for (int i = 0; i < ticker.frame; i++) phase += ticker.animationInfo.frames.get(i).time();
        long requestedPhase = GraphicsAuditPhaseWait.requestedPhase(duration);
        // Observe the normal ticker only; the post-swap hold binds this state
        // to its screenshot. Actual upload hashes remain a mandatory parity gate.
        boolean ready = phase == requestedPhase;
        if (requestedPhase == 0) {
            String first = contents.graphicsAuditFirstFrameRgbaFnv64();
            ready &= first != null && first.equals(contents.graphicsAuditUploadedRgbaFnv64());
        }
        return PHASE_WAIT.observe(ready);
    }
}

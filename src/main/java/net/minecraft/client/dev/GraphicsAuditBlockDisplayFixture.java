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
    public static boolean waterAnimationRequested() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditWaterCycleCapture");
    }
    public static boolean guiItemAnimationRequested() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditGuiItemAnimation") || shieldAnimationRequested();
    }
    private static boolean shieldAnimationRequested() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditShieldAnimation");
    }
    public static net.minecraft.resources.ResourceLocation observedSprite() {
        if (shieldAnimationRequested())
            return net.minecraft.resources.ResourceLocation.withDefaultNamespace(shieldObservationSprites(
                System.getProperty("mattmc.dev.graphicsAuditShieldAnimationSprite", "entity/shield_base_nopattern")).getFirst());
        if (net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.requested())
            return net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.SPRITE;
        if (guiItemAnimationRequested())
            return net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/feather");
        if (net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.requested())
            return net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.SPRITE;
        if (GraphicsAuditLavaFixture.requested())
            return net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                GraphicsAuditLavaFixture.flowing() ? "block/lava_flow" : "block/lava_still");
        return net.minecraft.resources.ResourceLocation.withDefaultNamespace(
            waterAnimationRequested() ? switch (System.getProperty("mattmc.dev.graphicsAuditWaterSprite",
                    GraphicsAuditFlowingWaterFixture.requested() ? "flow" : "still")) {
                case "flow" -> "block/water_flow";
                case "still" -> "block/water_still";
                default -> throw new IllegalArgumentException("Unsupported diagnostic water sprite");
            } : "block/magma");
    }
    /** Diagnostic selection only: reads the named material's normal animation. */
    static String shieldObservationSprite(String name) {
        return switch (name) {
            case "entity/shield_base_nopattern", "entity/shield_base", "entity/shield/base",
                 "entity/shield/cross", "entity/shield/border" -> name;
            default -> throw new IllegalArgumentException("Unsupported diagnostic shield sprite: " + name);
        };
    }
    /** Bounded capture-only selection; every selected phase is required for readiness. */
    static java.util.List<String> shieldObservationSprites(String names) {
        var result = new java.util.ArrayList<String>();
        for (String part : names.split(",", -1)) {
            String name = shieldObservationSprite(part.trim());
            if (result.size() == 5 || result.contains(name))
                throw new IllegalArgumentException("Duplicate or excessive diagnostic shield sprites");
            result.add(name);
        }
        return java.util.List.copyOf(result);
    }

    public static boolean observesSprite(net.minecraft.resources.ResourceLocation name) {
        if (!shieldAnimationRequested()) return name.equals(observedSprite());
        return shieldObservationSprites(System.getProperty("mattmc.dev.graphicsAuditShieldAnimationSprite",
            "entity/shield_base_nopattern")).stream().anyMatch(value ->
                name.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace(value)));
    }

    public static boolean requested() {
        return "magma".equals(System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", ""));
    }

    private static net.minecraft.resources.ResourceLocation observedAtlas() {
        if (shieldAnimationRequested()) return net.minecraft.client.renderer.Sheets.SHIELD_SHEET;
        return (net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.requested()
            || net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.requested())
            ? net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES
            : net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
    public static Vec3 position(Vec3 player, Vec3 look) {
        Vec3 forward = look.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : look;
        return player.add(forward.normalize().scale(4)).add(-0.5, -0.5, -0.5);
    }
    public static void install(Minecraft minecraft) {
        GraphicsAuditFlowingWaterFixture.install(minecraft);
        GraphicsAuditLavaFixture.install(minecraft);
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
        if ((!requested() && !net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.requested()
            && !net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.requested()
            && !net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.requested() && !waterAnimationRequested() && !GraphicsAuditLavaFixture.requested() && !guiItemAnimationRequested())
            || minecraft.level == null) return "null";
        var texture = minecraft.getTextureManager().getTexture(observedAtlas());
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return "{\"kind\":\"missing-atlas\"}";
        if (shieldAnimationRequested()) {
            var names = shieldObservationSprites(System.getProperty(
                "mattmc.dev.graphicsAuditShieldAnimationSprite", "entity/shield_base_nopattern"));
            if (names.size() > 1) {
                var observations = new java.util.ArrayList<String>();
                for (String name : names) observations.add(animationSpriteObservation(atlas,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(name), minecraft.options.mipmapLevels().get() + 1));
                return "{\"kind\":\"selected-atlas-sprites\",\"sprites\":[" + String.join(",", observations) + "]}";
            }
        }
        return animationSpriteObservation(atlas, observedSprite(), minecraft.options.mipmapLevels().get() + 1);
    }

    private static String animationSpriteObservation(net.minecraft.client.renderer.texture.TextureAtlas atlas,
            net.minecraft.resources.ResourceLocation name, int requestedMipLevels) {
        var sprite = atlas.getSprite(name);
        var resource = sprite.semanticAnimationResource();
        if (resource == null) return "{\"kind\":\"missing-owned-resource\"}";
        var declaration = resource.source().sprites().stream()
            .filter(value -> value.name().equals(sprite.contents().name())).findFirst().orElse(null);
        if (declaration == null) return "{\"kind\":\"missing-sprite-declaration\"}";
        return String.format(Locale.ROOT,
            "{\"kind\":\"rust-resource-producer\",\"spriteName\":\"%s\",\"mipLevels\":%d,\"requestedMipLevels\":%d,\"generation\":%d,\"nativeGeneration\":%d,\"spriteId\":%d,\"producedTick\":%d,\"lastNonemptyUseTick\":%d,\"x\":%d,\"y\":%d}",
            sprite.contents().name(), resource.source().mipCount(), requestedMipLevels,
            resource.source().generation(), net.vulkanic.world.RustGalWorldPrimitiveRenderer.atlasAnimationGenerationForDiagnostics(resource),
            declaration.id(), resource.producedTickForDiagnostics(),
            resource.lastNonemptyTickNamingSpriteForDiagnostics(declaration.id()), sprite.getX(), sprite.getY());
    }

    /** Observe every selected material's normal playback; never advance clocks or upload. */
    private static boolean selectedShieldAnimationsReady(net.minecraft.client.renderer.texture.TextureAtlas atlas,
            java.util.List<String> names) {
        boolean[] ready = new boolean[names.size()];
        long primaryPhase = 0, primaryDuration = 0;
        for (int index = 0; index < names.size(); index++) {
            var sprite = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace(names.get(index)));
            var resource = sprite.semanticAnimationResource();
            if (resource == null) return PHASE_WAIT.observe(false);
            var declaration = resource.source().sprites().stream()
                .filter(value -> value.name().equals(sprite.contents().name())).findFirst().orElse(null);
            if (declaration == null) return PHASE_WAIT.observe(false);
            long duration = declaration.source().frames().stream().mapToLong(value -> value.durationTicks()).sum();
            ready[index] = GraphicsAuditPhaseWait.phaseMatches(
                resource.lastNonemptyTickNamingSpriteForDiagnostics(declaration.id()), duration,
                GraphicsAuditPhaseWait.requestedPhase(duration));
            if (index == 0) {
                primaryDuration = duration;
                primaryPhase = Math.floorMod(resource.producedTickForDiagnostics(), duration);
            }
        }
        return PHASE_WAIT.observeAnimations(ready, primaryPhase, primaryDuration);
    }

    public static boolean readyForCapture(Minecraft minecraft) {
        if (!GraphicsAuditFlowingWaterFixture.ready(minecraft)) return PHASE_WAIT.observe(false);
        if (!GraphicsAuditLavaFixture.ready(minecraft)) return PHASE_WAIT.observe(false);
        if (net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.staticRequested())
            return net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.staticReady(minecraft);
        if (!guiItemAnimationRequested() && !net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.requested()
            && !net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.requested()
            && !GraphicsAuditLavaFixture.requested() && !waterAnimationRequested() && ((!requested() && !net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.requested())
            || !Boolean.getBoolean("mattmc.dev.graphicsAuditMagmaCycleCapture"))) return true;
        var texture = minecraft.getTextureManager().getTexture(observedAtlas());
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return PHASE_WAIT.observe(false);
        if (shieldAnimationRequested()) {
            var names = shieldObservationSprites(System.getProperty(
                "mattmc.dev.graphicsAuditShieldAnimationSprite", "entity/shield_base_nopattern"));
            if (names.size() > 1) return selectedShieldAnimationsReady(atlas, names);
        }
        var sprite = atlas.getSprite(observedSprite());
        var resource = sprite.semanticAnimationResource();
        if (resource == null) return PHASE_WAIT.observe(false);
        var declaration = resource.source().sprites().stream()
            .filter(value -> value.name().equals(sprite.contents().name())).findFirst().orElse(null);
        if (declaration == null) return PHASE_WAIT.observe(false);
        long duration = declaration.source().frames().stream().mapToLong(value -> value.durationTicks()).sum();
        if (guiItemAnimationRequested() || net.minecraft.client.particle.GraphicsAuditAtlasParticleFixture.requested()
            || net.minecraft.client.particle.GraphicsAuditVibrationParticleFixture.requested()) {
            return PHASE_WAIT.observeAnimation(GraphicsAuditPhaseWait.phaseMatches(
                resource.lastNonemptyTickNamingSpriteForDiagnostics(declaration.id()), duration,
                GraphicsAuditPhaseWait.requestedPhase(duration)),
                Math.floorMod(resource.producedTickForDiagnostics(), duration), duration);
        }
        if (GraphicsAuditLavaFixture.requested()) {
            boolean eligible = GraphicsAuditLavaFixture.samplesUnobstructed()
                && GraphicsAuditPhaseWait.phaseMatches(
                    resource.lastNonemptyTickNamingSpriteForDiagnostics(declaration.id()), duration,
                    GraphicsAuditPhaseWait.requestedPhase(duration));
            return PHASE_WAIT.observeAnimation(eligible,
                Math.floorMod(resource.producedTickForDiagnostics(), duration), duration);
        }
        if (waterAnimationRequested()) {
            // Select a candidate from actual semantic use, not an empty
            // catch-up tick. This does not select rendering pixels: accepted
            // native upload/presentation evidence must still prove the hash.
            return PHASE_WAIT.observe(GraphicsAuditPhaseWait.phaseMatches(
                resource.lastNonemptyTickNamingSpriteForDiagnostics(declaration.id()), duration,
                GraphicsAuditPhaseWait.requestedPhase(duration)));
        }
        // This schedules a screenshot candidate only. Native upload evidence
        // must still prove the exact sampled-source phase after the run.
        // Catch-up ticks can follow a visible tick with an empty use set. Such
        // a cycle boundary correctly retains older pixels; it is not a useful
        // screenshot candidate for the interpolated magma fixture. Water may
        // legitimately retain the same noninterpolated frame on a catch-up
        // tick without another use. Its mandatory native/Frozen uploaded-pixel
        // hash comparison rejects stale pixels after candidate selection.
        // Never inject visibility or alter the animation clock.
        return PHASE_WAIT.observe(GraphicsAuditPhaseWait.phaseMatches(resource.producedTickForDiagnostics(), duration,
                GraphicsAuditPhaseWait.requestedPhase(duration))
            && resource.producedTickNamedSpriteForDiagnostics(declaration.id()));
    }
}

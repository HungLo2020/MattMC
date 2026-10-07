package net.minecraft.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in falling-leaf particles held still in front of the camera: tinted
 * {@code leaf_*} sprites with known tints, plus untinted cherry and pale oak
 * sprites. Every quad uses the ordinary {@link FallingLeavesParticle}
 * extraction; only the simulation is frozen, so a correct renderer draws the
 * same colour every frame (sprite texel times tint times light).
 */
public final class GraphicsAuditLeafParticleFixture {
    /** Sprite and ARGB tint of each fixture leaf, left to right. */
    static final List<Leaf> LEAVES = List.of(
        new Leaf("leaf_0", 0xFF48B518),
        new Leaf("leaf_3", 0xFFFF0000),
        new Leaf("leaf_6", 0xFF0000FF),
        new Leaf("leaf_9", 0xFFFFFFFF),
        new Leaf("cherry_0", 0xFFFFFFFF),
        new Leaf("pale_oak_0", 0xFFFFFFFF));
    static final float QUAD_SIZE = 0.25F;
    static final double SPACING = 0.6;

    record Leaf(String sprite, int tint) {}

    private static ClientLevel installedLevel;
    private static List<FallingLeavesParticle> particles = List.of();

    private GraphicsAuditLeafParticleFixture() {}

    static final float SPIN_PER_TICK = 0.35F;

    /** Whether the leaves spin like falling leaves (roll per tick) instead of holding still. */
    static boolean spin() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditLeafParticles.spin");
    }

    /** Blocks from the eye; far leaves are small on screen and sample coarser mips. */
    static double distance() {
        double distance = Double.parseDouble(System.getProperty("mattmc.dev.graphicsAuditLeafParticles.distance", "3"));
        if (!(distance >= 1 && distance <= 64)) throw new IllegalArgumentException("unsupported leaf fixture distance");
        return distance;
    }

    public static boolean requested() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditLeafParticles");
    }

    /** Positions left to right across the view, three blocks ahead of the eye. */
    static List<Vec3> positions(Vec3 eye, Vec3 look, int count) {
        if (look.lengthSqr() < 0.0001) throw new IllegalArgumentException("missing fixture look direction");
        Vec3 forward = look.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.0001 ? new Vec3(1, 0, 0) : right.normalize();
        double distance = distance();
        Vec3 centre = eye.add(forward.scale(distance));
        double spacing = SPACING * distance / 3.0;
        var result = new ArrayList<Vec3>(count);
        for (int index = 0; index < count; index++) {
            result.add(centre.add(right.scale((index - (count - 1) / 2.0) * spacing)));
        }
        return result;
    }

    static FallingLeavesParticle create(ClientLevel level, Vec3 position, TextureAtlasSprite sprite, int tint) {
        var particle = new FallingLeavesParticle(level, position.x, position.y, position.z, sprite,
                0.0F, 0.0F, false, false, 1.0F, 0.0F) {
            // Hold the simulation: no fall, ageing or removal; spin only when asked.
            @Override public void tick() {
                if (spin()) {
                    this.oRoll = this.roll;
                    this.roll += SPIN_PER_TICK;
                }
            }
            @Override public float getQuadSize(float partialTick) { return QUAD_SIZE; }
        };
        particle.roll = 0.0F;
        particle.oRoll = 0.0F;
        particle.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F);
        particle.setAlpha(((tint >>> 24) & 0xFF) / 255.0F);
        return particle;
    }

    /** Installs the fixture once per level; called from the client particle tick. */
    public static void install(Minecraft minecraft) {
        if (!requested() || minecraft.level == null || minecraft.player == null) return;
        // Once per level; a live session keeps the same leaves in front of the
        // camera instead of stacking new ones as it turns.
        if (installedLevel == minecraft.level) {
            follow(minecraft);
            return;
        }
        particles.forEach(Particle::remove);
        var atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        var positions = positions(minecraft.player.getEyePosition(), minecraft.player.getLookAngle(), LEAVES.size());
        var installed = new ArrayList<FallingLeavesParticle>(LEAVES.size());
        for (int index = 0; index < LEAVES.size(); index++) {
            Leaf leaf = LEAVES.get(index);
            var sprite = atlas.getSprite(ResourceLocation.withDefaultNamespace(leaf.sprite()));
            var particle = create(minecraft.level, positions.get(index), sprite, leaf.tint());
            minecraft.particleEngine.installGraphicsAuditParticle(particle);
            installed.add(particle);
        }
        particles = List.copyOf(installed);
        installedLevel = minecraft.level;
    }

    private static void follow(Minecraft minecraft) {
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3 look = new Vec3(camera.getLookVector());
        var positions = positions(camera.getPosition(), look, particles.size());
        for (int index = 0; index < particles.size(); index++) {
            var particle = particles.get(index);
            Vec3 position = positions.get(index);
            particle.setPos(position.x, position.y, position.z);
            particle.xo = position.x;
            particle.yo = position.y;
            particle.zo = position.z;
        }
    }

    /** Receipt of the installed leaves: sprite, UV bounds, ARGB tint and light. */
    public static String receipt(Minecraft minecraft) {
        if (!requested()) return "null";
        var result = new JsonObject();
        result.addProperty("fixture", "falling-leaves-held-v1");
        var leaves = new JsonArray();
        for (int index = 0; index < particles.size(); index++) {
            var particle = particles.get(index);
            var entry = new JsonObject();
            entry.addProperty("sprite", particle.sprite.contents().name().toString());
            entry.addProperty("tint", Integer.toHexString(LEAVES.get(index).tint()));
            entry.addProperty("u0", particle.getU0());
            entry.addProperty("u1", particle.getU1());
            entry.addProperty("v0", particle.getV0());
            entry.addProperty("v1", particle.getV1());
            entry.addProperty("light", particle.getLightColor(1.0F));
            entry.addProperty("alive", particle.isAlive());
            leaves.add(entry);
        }
        result.add("leaves", leaves);
        return result.toString();
    }
}

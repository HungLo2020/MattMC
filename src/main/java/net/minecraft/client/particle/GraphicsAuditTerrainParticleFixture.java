package net.minecraft.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Capture-only particle inputs. Ordinary particle construction is unchanged. */
public final class GraphicsAuditTerrainParticleFixture {
    private static final ThreadLocal<Boolean> CONSTRUCTING = new ThreadLocal<>();
    private static ClientLevel installedLevel;
    private static TerrainParticle particle;
    private GraphicsAuditTerrainParticleFixture() {}

    public static boolean requested() {
        return Boolean.getBoolean("mattmc.dev.graphicsAuditMagmaParticle");
    }

    static <T> T construct(Supplier<T> factory) {
        if (Boolean.TRUE.equals(CONSTRUCTING.get())) throw new IllegalStateException("nested particle fixture construction");
        CONSTRUCTING.set(true);
        try { return factory.get(); } finally { CONSTRUCTING.remove(); }
    }

    static float offset(float ordinary) {
        return Boolean.TRUE.equals(CONSTRUCTING.get()) ? 1.0F : ordinary;
    }

    static void configure(TerrainParticle value) {
        if (!Boolean.TRUE.equals(CONSTRUCTING.get())) return;
        value.quadSize = 0.35F;
        value.gravity = 0;
        value.hasPhysics = false;
        value.xd = value.yd = value.zd = 0;
        value.lifetime = 20000;
        value.friction = 1;
    }

    static Vec3 position(Vec3 eye, Vec3 look) {
        if (look.lengthSqr() < 0.0001) throw new IllegalArgumentException("missing fixture look direction");
        return eye.add(look.normalize().scale(3));
    }

    public static void install(Minecraft minecraft) {
        if (!requested() || minecraft.level == null || minecraft.player == null) return;
        if (installedLevel == minecraft.level && particle != null) return;
        Vec3 origin = position(minecraft.player.getEyePosition(), minecraft.player.getLookAngle());
        particle = construct(() -> new TerrainParticle(minecraft.level, origin.x, origin.y, origin.z,
            0, 0, 0, Blocks.MAGMA_BLOCK.defaultBlockState()));
        installedLevel = minecraft.level;
        minecraft.particleEngine.installGraphicsAuditParticle(particle);
    }

    public static String receipt(Minecraft minecraft) {
        if (!requested()) return "null";
        JsonObject result = new JsonObject();
        result.addProperty("fixture", "magma-terrain-particle-v1");
        boolean present = installedLevel == minecraft.level && particle != null;
        result.addProperty("complete", present && particle.isAlive()
            && minecraft.particleEngine.containsGraphicsAuditParticle(particle));
        if (!present) return result.toString();
        result.addProperty("block", "minecraft:magma_block");
        result.addProperty("sprite", particle.sprite.contents().name().toString());
        JsonArray position = new JsonArray();
        position.add(particle.x); position.add(particle.y); position.add(particle.z);
        result.add("position", position);
        JsonArray uv = new JsonArray();
        uv.add(particle.sprite.getUOffset(particle.getU0()));
        uv.add(particle.sprite.getUOffset(particle.getU1()));
        uv.add(particle.sprite.getVOffset(particle.getV0()));
        uv.add(particle.sprite.getVOffset(particle.getV1()));
        result.add("localUv", uv);
        JsonArray color = new JsonArray();
        color.add(particle.rCol); color.add(particle.gCol); color.add(particle.bCol); color.add(particle.alpha);
        result.add("color", color);
        result.addProperty("size", particle.getQuadSize(1));
        result.addProperty("light", particle.getLightColor(1));
        return result.toString();
    }
}

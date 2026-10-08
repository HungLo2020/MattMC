package net.minecraft.client.dev;

import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Vector3f;

/** Bounded observations of already-encoded reference vertices, never inputs. */
public final class GraphicsAuditGuiLeafVertices {
    private static int count;
    private static final int[] normalCounts = new int[2];
    private GraphicsAuditGuiLeafVertices() {}

    public static void recordNormal(net.sodium.client.model.quad.ModelQuadView quad, int vertex,
                                   float x, float y, float z, String path) {
        if (!Boolean.getBoolean("mattmc.dev.guiLeafNormalTrace")) return;
        int bucket = path.equals("baked-encoder-after-pack") ? 0 : 1;
        if (normalCounts[bucket] >= 16) return;
        int[] item = GraphicsAuditGuiFoilTiming.activeItemForDiagnostics();
        if (item == null || item[0] != 292 || item[1] != 341
            || quad.getSprite() == null
            || !quad.getSprite().contents().name().toString().equals("minecraft:block/oak_leaves_bushy")) return;
        normalCounts[bucket]++;
        net.logging.LogUtils.getLogger().info(
            "gui.leaf.normal path={} face={} vertex={} normal={},{},{}",
            path,quad.getLightFace(),vertex,x,y,z);
    }

    public static void record(BakedQuad quad, int vertex, Vector3f encoded, float u, float v) {
        if (!Boolean.getBoolean("mattmc.dev.guiLeafVertexTrace") || count >= 16) return;
        int[] item = GraphicsAuditGuiFoilTiming.activeItemForDiagnostics();
        if (item == null || !quad.sprite().contents().name().toString().equals("minecraft:block/oak_leaves_bushy")) return;
        count++;
        net.logging.LogUtils.getLogger().info(
            "gui.leaf.vertex item={},{} face={} vertex={} source={},{},{} encoded={},{},{} uv={},{}",
            item[0],item[1],quad.direction(),vertex,
            quad.getX(vertex),quad.getY(vertex),quad.getZ(vertex),
            encoded.x(),encoded.y(),encoded.z(),u,v);
    }
}

package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Bounded snapshots of already resolved CPU model data. Never changes model selection. */
public final class GraphicsAuditGroundFoilSources {
    private GraphicsAuditGroundFoilSources() {}

    private static Object read(Object object, Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static long currentFrameIndex() {
        try {
            // Observation precedes the existing end-of-frame counter increment.
            return ((Number)read(null, DeterministicCameraCapture.class, "renderedFrameIndex")).longValue() + 1;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot observe ground source frame", error);
        }
    }

    static JsonObject capture(ItemStackRenderState state) {
        return capture(state, currentFrameIndex());
    }

    static JsonObject capture(ItemStackRenderState state, long frame) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "ground-foil-frame-sources-v1");
        result.addProperty("renderedFrameIndex", frame);
        JsonArray sources = new JsonArray();
        result.add("quads", sources);
        boolean complete = frame > 0;
        try {
            int count = (Integer)read(state, ItemStackRenderState.class, "activeLayerCount");
            Object[] layers = (Object[])read(state, ItemStackRenderState.class, "layers");
            if (count < 1 || count > 4 || count > layers.length) complete = false;
            else {
                List<JsonObject> copied = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Object layer = layers[i];
                    Object foil = read(layer, ItemStackRenderState.LayerRenderState.class, "foilType");
                    List<?> quads = (List<?>)read(layer, ItemStackRenderState.LayerRenderState.class, "quads");
                    if (foil != ItemStackRenderState.FoilType.SPECIAL || quads.isEmpty()
                            || quads.size() > 128 - copied.size()) { complete = false; break; }
                    for (Object raw : quads) {
                        BakedQuad quad = (BakedQuad)raw;
                        JsonObject source = new JsonObject();
                        source.addProperty("layer", i);
                        source.addProperty("sprite", quad.sprite().contents().name().toString());
                        source.addProperty("face", quad.direction().getName());
                        JsonArray positions = new JsonArray(), uvs = new JsonArray();
                        for (int v = 0; v < 4; v++) {
                            for (float value : new float[]{quad.getX(v), quad.getY(v), quad.getZ(v)}) {
                                if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite ground source");
                                positions.add(value);
                            }
                            for (float value : new float[]{quad.getTexU(v), quad.getTexV(v)}) {
                                if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite ground UV");
                                uvs.add(value);
                            }
                        }
                        source.add("positions", positions); source.add("atlasUvs", uvs);
                        copied.add(source);
                    }
                }
                // Quad iteration order is not a cross-process model identity.
                copied.sort(Comparator.comparing(JsonObject::toString));
                copied.forEach(sources::add);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot observe resolved ground model", error);
        }
        result.addProperty("complete", complete);
        return result;
    }
}

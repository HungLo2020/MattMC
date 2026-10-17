package net.vulkanic.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.renderer.texture.SemanticAtlasAnimationSource;
import net.minecraft.resources.ResourceLocation;

/** Collects immutable sprite-use semantics; does not select animation frames. */
final class AtlasAnimationVisibility {
    private final ResourceLocation atlas;
    private final Map<ResourceLocation, Integer> ids;
    /** Ids by name instance (sprite names are shared objects); 0 marks a static sprite. */
    private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<ResourceLocation> idsByInstance =
        new it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<>();
    private final it.unimi.dsi.fastutil.ints.IntOpenHashSet used = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

    AtlasAnimationVisibility(ResourceLocation atlas, SemanticAtlasAnimationSource source) {
        this.atlas = java.util.Objects.requireNonNull(atlas);
        if (source.sprites().size() > 16384) throw new IllegalArgumentException("Animation visibility bound exceeded");
        var mapping = new HashMap<ResourceLocation, Integer>();
        var uniqueIds = new HashSet<Integer>();
        for (var sprite : source.sprites()) {
            if (sprite.id() <= 0 || sprite.name() == null || !uniqueIds.add(sprite.id())
                || mapping.putIfAbsent(sprite.name(), sprite.id()) != null) {
                throw new IllegalArgumentException("Ambiguous animation sprite identity");
            }
        }
        ids = Map.copyOf(mapping);
        idsByInstance.defaultReturnValue(-1);
    }

    boolean recordUse(ResourceLocation atlas, ResourceLocation name) {
        if (!this.atlas.equals(java.util.Objects.requireNonNull(atlas))) return false;
        // Uses of static sprites are valid but have no animation declaration.
        int id = idsByInstance.getInt(java.util.Objects.requireNonNull(name));
        if (id < 0) {
            Integer declared = ids.get(name);
            id = declared == null ? 0 : declared;
            if (idsByInstance.size() < 16384) idsByInstance.put(name, id);
        }
        return id != 0 && used.add(id);
    }

    int pendingCount() { return used.size(); }
    int[] snapshotUses() {
        int[] result = used.toIntArray();
        java.util.Arrays.sort(result);
        return result;
    }
    void clearUses() { used.clear(); }

    /** Detaches one tick's owned event payload; later draws form the next set. */
    int[] takeUses() {
        int[] result = snapshotUses();
        clearUses();
        return result;
    }
}

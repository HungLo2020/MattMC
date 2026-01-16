package net.caffeinemc.mods.sodium.client.render.frapi.mesh;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;

/**
 * Implementation of {@link Mesh}.
 * The way we encode meshes makes it very simple.
 */
public class MeshImpl extends MeshViewImpl implements Mesh {
    /** Used to satisfy external calls to {@link #forEach(Consumer)}. */
    private static final ThreadLocal<ObjectArrayList<QuadViewImpl>> CURSOR_POOLS = ThreadLocal.withInitial(ObjectArrayList::new);

    MeshImpl(int[] data) {
        this.data = data;
        limit = data.length;
    }

    MeshImpl() {}
}

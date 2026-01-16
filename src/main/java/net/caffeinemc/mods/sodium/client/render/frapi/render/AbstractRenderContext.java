package net.caffeinemc.mods.sodium.client.render.frapi.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

import java.util.function.Consumer;

public abstract class AbstractRenderContext {
    abstract QuadEmitter getEmitter();
}

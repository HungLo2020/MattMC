package net.fabricmc.fabric.api.renderer.v1.mesh;

/**
 * A complete, immutable mesh that can be rendered.
 */
public interface Mesh extends MeshView {
    // Mesh is an immutable version of MeshView
    // Implementation provides iteration and rendering capabilities
}

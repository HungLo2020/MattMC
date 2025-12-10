package net.minecraft.client.renderer.shaders.targets;

import org.joml.Vector4f;

/**
 * Stores information about a clear pass: clear color and viewport dimensions.
 * 
 * <p>Used to group render targets with identical clear requirements for
 * batch clearing.
 * 
 * <p>Copied VERBATIM from IRIS 1.21.9 ClearPassInformation.java
 */
public class ClearPassInformation {
    private final Vector4f color;
    private final int width;
    private final int height;

    public ClearPassInformation(Vector4f vector4f, int width, int height) {
        this.color = vector4f;
        this.width = width;
        this.height = height;
    }

    public Vector4f getColor() {
        return color;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ClearPassInformation information)) {
            return false;
        }

        return information.color.equals(this.color) && information.height == this.height && information.width == this.width;
    }

    @Override
    public int hashCode() {
        int result = color.hashCode();
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }
}

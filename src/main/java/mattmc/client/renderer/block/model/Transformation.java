package mattmc.client.renderer.block.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents a 3D transformation (translation, rotation, scale).
 * Based on Minecraft's com.mojang.math.Transformation
 */
public class Transformation {
    private final Matrix4f matrix;
    
    public Transformation(Matrix4f matrix) {
        this.matrix = matrix;
    }
    
    public Transformation(Vector3f translation, Quaternionf rotation, Vector3f scale, Quaternionf rightRotation) {
        this.matrix = new Matrix4f();
        
        if (translation != null) {
            matrix.translate(translation);
        }
        
        if (rotation != null) {
            matrix.rotate(rotation);
        }
        
        if (scale != null) {
            matrix.scale(scale);
        }
        
        if (rightRotation != null) {
            matrix.rotate(rightRotation);
        }
    }
    
    public Matrix4f getMatrix() {
        return new Matrix4f(matrix);
    }
    
    public Transformation inverse() {
        Matrix4f inv = new Matrix4f(matrix);
        try {
            inv.invert();
            return new Transformation(inv);
        } catch (Exception e) {
            // Matrix is not invertible
            return null;
        }
    }
    
    public Transformation compose(Transformation other) {
        Matrix4f result = new Matrix4f(this.matrix);
        result.mul(other.matrix);
        return new Transformation(result);
    }
    
    public static Transformation identity() {
        return new Transformation(new Matrix4f());
    }
}

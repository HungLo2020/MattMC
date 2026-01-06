package net.minecraft.worldedit.math.transform;

import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.math.Vector3;

/**
 * Affine transformation for rotating and flipping clipboards
 */
public class AffineTransform {
    private double[][] matrix;
    
    public AffineTransform() {
        // Identity matrix
        this.matrix = new double[][] {
            {1, 0, 0, 0},
            {0, 1, 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
    }
    
    /**
     * Create rotation around Y axis (vertical)
     */
    public static AffineTransform rotateY(int degrees) {
        AffineTransform transform = new AffineTransform();
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        
        transform.matrix[0][0] = cos;
        transform.matrix[0][2] = sin;
        transform.matrix[2][0] = -sin;
        transform.matrix[2][2] = cos;
        
        return transform;
    }
    
    /**
     * Create flip along X axis
     */
    public static AffineTransform flipX() {
        AffineTransform transform = new AffineTransform();
        transform.matrix[0][0] = -1;
        return transform;
    }
    
    /**
     * Create flip along Z axis
     */
    public static AffineTransform flipZ() {
        AffineTransform transform = new AffineTransform();
        transform.matrix[2][2] = -1;
        return transform;
    }
    
    /**
     * Transform a block vector
     */
    public BlockVector3 apply(BlockVector3 vector) {
        double x = vector.getX() * matrix[0][0] + vector.getY() * matrix[0][1] + 
                   vector.getZ() * matrix[0][2] + matrix[0][3];
        double y = vector.getX() * matrix[1][0] + vector.getY() * matrix[1][1] + 
                   vector.getZ() * matrix[1][2] + matrix[1][3];
        double z = vector.getX() * matrix[2][0] + vector.getY() * matrix[2][1] + 
                   vector.getZ() * matrix[2][2] + matrix[2][3];
        
        return BlockVector3.at((int)Math.round(x), (int)Math.round(y), (int)Math.round(z));
    }
    
    /**
     * Combine this transform with another
     */
    public AffineTransform combine(AffineTransform other) {
        AffineTransform result = new AffineTransform();
        
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += this.matrix[i][k] * other.matrix[k][j];
                }
                result.matrix[i][j] = sum;
            }
        }
        
        return result;
    }
}

package mattmc.client.renderer.block.model;

import mattmc.world.level.block.state.properties.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Face baking utilities for block model rendering.
 * Based on Minecraft's net.minecraft.client.renderer.block.model.FaceBakery
 */
public class FaceBakery {
    
    /**
     * Recompute UV coordinates when uvlock is enabled.
     * This is a verbatim copy of Minecraft's FaceBakery.recomputeUVs method.
     */
    public static BlockFaceUV recomputeUVs(BlockFaceUV uvs, Direction facing, Transformation modelRotation) {
        Matrix4f matrix4f = BlockMath.getUVLockTransform(modelRotation, facing, () -> {
            return "Unable to resolve UVLock for model";
        }).getMatrix();
        
        // Get the first corner UV coordinates (reverse index 0)
        float f = uvs.getU(uvs.getReverseIndex(0));
        float f1 = uvs.getV(uvs.getReverseIndex(0));
        Vector4f vector4f = matrix4f.transform(new Vector4f(f / 16.0F, f1 / 16.0F, 0.0F, 1.0F));
        float f2 = 16.0F * vector4f.x();
        float f3 = 16.0F * vector4f.y();
        
        // Get the opposite corner UV coordinates (reverse index 2)
        float f4 = uvs.getU(uvs.getReverseIndex(2));
        float f5 = uvs.getV(uvs.getReverseIndex(2));
        Vector4f vector4f1 = matrix4f.transform(new Vector4f(f4 / 16.0F, f5 / 16.0F, 0.0F, 1.0F));
        float f6 = 16.0F * vector4f1.x();
        float f7 = 16.0F * vector4f1.y();
        
        // Handle sign flipping for U coordinates
        float f8;
        float f9;
        if (Math.signum(f4 - f) == Math.signum(f6 - f2)) {
            f8 = f2;
            f9 = f6;
        } else {
            f8 = f6;
            f9 = f2;
        }
        
        // Handle sign flipping for V coordinates
        float f10;
        float f11;
        if (Math.signum(f5 - f1) == Math.signum(f7 - f3)) {
            f10 = f3;
            f11 = f7;
        } else {
            f10 = f7;
            f11 = f3;
        }
        
        // Recompute the rotation value
        float f12 = (float)Math.toRadians((double)uvs.rotation);
        Matrix3f matrix3f = new Matrix3f(matrix4f);
        Vector3f vector3f = matrix3f.transform(new Vector3f(cos(f12), sin(f12), 0.0F));
        int i = Math.floorMod(-((int)Math.round(Math.toDegrees(Math.atan2((double)vector3f.y(), (double)vector3f.x())) / 90.0D)) * 90, 360);
        
        return new BlockFaceUV(new float[]{f8, f10, f9, f11}, i);
    }
    
    // Math helper methods
    private static float cos(float value) {
        return (float)Math.cos(value);
    }
    
    private static float sin(float value) {
        return (float)Math.sin(value);
    }
}

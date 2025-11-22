package mattmc.client.renderer.block.model;

import mattmc.world.level.block.state.properties.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Block math utilities for UV transformations.
 * Based on Minecraft's net.minecraft.core.BlockMath
 */
public class BlockMath {
    public static final Map<Direction, Transformation> VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL = new EnumMap<>(Direction.class);
    public static final Map<Direction, Transformation> VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL = new EnumMap<>(Direction.class);
    
    static {
        // Local to global transforms (face-local UV space to world space)
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.SOUTH, Transformation.identity());
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.EAST, new Transformation(null, new Quaternionf().rotateY((float)Math.PI / 2F), null, null));
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.WEST, new Transformation(null, new Quaternionf().rotateY(-(float)Math.PI / 2F), null, null));
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.NORTH, new Transformation(null, new Quaternionf().rotateY((float)Math.PI), null, null));
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.UP, new Transformation(null, new Quaternionf().rotateX(-(float)Math.PI / 2F), null, null));
        VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.put(Direction.DOWN, new Transformation(null, new Quaternionf().rotateX((float)Math.PI / 2F), null, null));
        
        // Global to local transforms (inverse of above)
        for (Direction direction : Direction.values()) {
            VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL.put(direction, VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.get(direction).inverse());
        }
    }
    
    public static Transformation blockCenterToCorner(Transformation transformation) {
        Matrix4f matrix = new Matrix4f().translation(0.5F, 0.5F, 0.5F);
        matrix.mul(transformation.getMatrix());
        matrix.translate(-0.5F, -0.5F, -0.5F);
        return new Transformation(matrix);
    }
    
    public static Transformation blockCornerToCenter(Transformation transformation) {
        Matrix4f matrix = new Matrix4f().translation(-0.5F, -0.5F, -0.5F);
        matrix.mul(transformation.getMatrix());
        matrix.translate(0.5F, 0.5F, 0.5F);
        return new Transformation(matrix);
    }
    
    public static Transformation getUVLockTransform(Transformation modelRotation, Direction facingBefore, Supplier<String> errorMessage) {
        // Determine which direction this face will be after rotation
        Direction facingAfter = rotate(modelRotation.getMatrix(), facingBefore);
        
        // Get the inverse of the model rotation
        Transformation inverseRotation = modelRotation.inverse();
        if (inverseRotation == null) {
            System.err.println(errorMessage.get());
            return new Transformation(null, null, new Vector3f(0.0F, 0.0F, 0.0F), null);
        }
        
        // Compose the transformations:
        // 1. Transform from face-local UV space to world space (using original face direction)
        // 2. Apply inverse model rotation
        // 3. Transform back from world space to face-local UV space (using new face direction)
        Transformation globalToLocal = VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL.get(facingBefore);
        Transformation localToGlobal = VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL.get(facingAfter);
        
        if (globalToLocal == null || localToGlobal == null) {
            System.err.println("Missing UV transform for direction: " + facingBefore + " or " + facingAfter);
            return Transformation.identity();
        }
        
        Transformation result = globalToLocal.compose(inverseRotation).compose(localToGlobal);
        
        return blockCenterToCorner(result);
    }
    
    /**
     * Determine which direction a face will be after a rotation transformation.
     */
    public static Direction rotate(Matrix4f transform, Direction direction) {
        Vector3f normal = new Vector3f(
            direction.getStepX(),
            direction.getStepY(),
            direction.getStepZ()
        );
        
        // Transform the normal vector
        Matrix4f matrix = new Matrix4f(transform);
        matrix.transformDirection(normal);
        
        // Find the closest cardinal direction
        return Direction.getNearest(normal.x, normal.y, normal.z);
    }
}

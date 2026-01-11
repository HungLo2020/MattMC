package net.minecraft.world.level.block.state;

import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.ArrayVoxelShapeHash;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeHash;

import java.util.Arrays;
import java.util.Map;

/**
 * FerriteCore optimization #7: Deduplicate blockstate cache data (collision shapes, face sturdiness)
 * Memory savings: ~200 MB by sharing identical shape instances and arrays
 */
public class BlockStateCacheDeduplication {
    // Global caches for deduplication
    public static final Map<ArrayVoxelShape, ArrayVoxelShape> COLLISION_SHAPE_CACHE = 
        new Object2ObjectOpenCustomHashMap<>(ArrayVoxelShapeHash.INSTANCE);
    
    public static final Map<boolean[], boolean[]> FACE_STURDY_CACHE = 
        new Object2ObjectOpenCustomHashMap<>(BooleanArrays.HASH_STRATEGY);
    
    // ThreadLocal to track the previous cache when re-initializing
    private static final ThreadLocal<CacheSnapshot> LAST_CACHE = new ThreadLocal<>();
    
    /**
     * Call before a blockstate cache is (re-)initialized
     */
    public static void beforeCacheInit(VoxelShape currentCollisionShape, boolean[] currentFaceSturdy) {
        LAST_CACHE.set(new CacheSnapshot(currentCollisionShape, currentFaceSturdy));
    }
    
    /**
     * Call after a blockstate cache is (re-)initialized to deduplicate
     */
    public static DeduplicationResult afterCacheInit(VoxelShape newCollisionShape, boolean[] newFaceSturdy) {
        CacheSnapshot oldCache = LAST_CACHE.get();
        LAST_CACHE.remove();
        
        VoxelShape dedupedCollisionShape = deduplicateCollisionShape(newCollisionShape, oldCache);
        boolean[] dedupedFaceSturdy = deduplicateFaceSturdyArray(newFaceSturdy, oldCache);
        
        return new DeduplicationResult(dedupedCollisionShape, dedupedFaceSturdy);
    }
    
    private static VoxelShape deduplicateCollisionShape(VoxelShape newShape, CacheSnapshot oldCache) {
        // Check if old cache has equivalent shape
        if (oldCache != null && oldCache.collisionShape != null && 
            VoxelShapeHash.INSTANCE.equals(oldCache.collisionShape, newShape)) {
            return oldCache.collisionShape;
        }
        
        // Deduplicate ArrayVoxelShapes
        if (newShape instanceof ArrayVoxelShape arrayShape) {
            ArrayVoxelShape canonical = COLLISION_SHAPE_CACHE.computeIfAbsent(arrayShape, k -> k);
            
            // Replace internals if different instance
            if (canonical != arrayShape) {
                replaceShapeInternals(canonical, arrayShape);
            }
            
            return canonical;
        }
        
        return newShape;
    }
    
    private static boolean[] deduplicateFaceSturdyArray(boolean[] newArray, CacheSnapshot oldCache) {
        // Check if old cache has equivalent array
        if (oldCache != null && oldCache.faceSturdy != null && 
            Arrays.equals(oldCache.faceSturdy, newArray)) {
            return oldCache.faceSturdy;
        }
        
        // Deduplicate the array
        return FACE_STURDY_CACHE.computeIfAbsent(newArray, k -> k);
    }
    
    /**
     * Replace the internal data of one ArrayVoxelShape with another.
     * This is safe because VoxelShapes are treated as immutable in vanilla code.
     * This helps even when mods cache their own shapes internally.
     */
    public static void replaceShapeInternals(ArrayVoxelShape toKeep, ArrayVoxelShape toReplace) {
        if (toKeep == toReplace) {
            return;
        }
        
        // Use the internal setter methods to replace coordinate lists
        toReplace.ferritecore$setXCoords(toKeep.getCoords(net.minecraft.core.Direction.Axis.X));
        toReplace.ferritecore$setYCoords(toKeep.getCoords(net.minecraft.core.Direction.Axis.Y));
        toReplace.ferritecore$setZCoords(toKeep.getCoords(net.minecraft.core.Direction.Axis.Z));
        // Note: DiscreteVoxelShape (shape field) is already shared through the constructor
    }
    
    private static class CacheSnapshot {
        final VoxelShape collisionShape;
        final boolean[] faceSturdy;
        
        CacheSnapshot(VoxelShape collisionShape, boolean[] faceSturdy) {
            this.collisionShape = collisionShape;
            this.faceSturdy = faceSturdy;
        }
    }
    
    public static class DeduplicationResult {
        public final VoxelShape collisionShape;
        public final boolean[] faceSturdy;
        
        DeduplicationResult(VoxelShape collisionShape, boolean[] faceSturdy) {
            this.collisionShape = collisionShape;
            this.faceSturdy = faceSturdy;
        }
    }
}

package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * Fabric API stub for WorldRenderEvents
 */
public final class WorldRenderEvents {
    public static final Event<Start> START = Event.create(Start.class, callbacks -> context -> {
        for (Start callback : callbacks) {
            callback.onStart(context);
        }
    });
    
    public static final Event<AfterSetup> AFTER_SETUP = Event.create(AfterSetup.class, callbacks -> context -> {
        for (AfterSetup callback : callbacks) {
            callback.afterSetup(context);
        }
    });
    
    public static final Event<AfterEntities> AFTER_ENTITIES = Event.create(AfterEntities.class, callbacks -> context -> {
        for (AfterEntities callback : callbacks) {
            callback.afterEntities(context);
        }
    });
    
    public static final Event<AfterTranslucent> AFTER_TRANSLUCENT = Event.create(AfterTranslucent.class, callbacks -> context -> {
        for (AfterTranslucent callback : callbacks) {
            callback.afterTranslucent(context);
        }
    });
    
    public static final Event<End> END = Event.create(End.class, callbacks -> context -> {
        for (End callback : callbacks) {
            callback.onEnd(context);
        }
    });
    
    @FunctionalInterface
    public interface Start {
        void onStart(WorldRenderContext context);
    }
    
    @FunctionalInterface
    public interface AfterSetup {
        void afterSetup(WorldRenderContext context);
    }
    
    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(WorldRenderContext context);
    }
    
    @FunctionalInterface
    public interface AfterTranslucent {
        void afterTranslucent(WorldRenderContext context);
    }
    
    @FunctionalInterface
    public interface End {
        void onEnd(WorldRenderContext context);
    }
    
    private WorldRenderEvents() {}
    
    /**
     * Context for world render events
     */
    public interface WorldRenderContext {
        ClientLevel world();
        PoseStack matrixStack();
        float tickDelta();
        long limitTime();
        boolean blockOutlines();
        Camera camera();
        Matrix4f projectionMatrix();
        
        /** Returns the position matrix for MC 1.20.6+ */
        default Matrix4f positionMatrix() {
            return matrixStack().last().pose();
        }
        
        /** Returns the tick counter for MC 1.21.1+ */
        default DeltaTracker tickCounter() {
            return new DeltaTracker() {
                @Override
                public float getGameTimeDeltaTicks() {
                    return tickDelta();
                }
                
                @Override
                public float getGameTimeDeltaPartialTick(boolean bl) {
                    return tickDelta();
                }
                
                @Override
                public float getRealtimeDeltaTicks() {
                    return tickDelta();
                }
            };
        }
    }
}

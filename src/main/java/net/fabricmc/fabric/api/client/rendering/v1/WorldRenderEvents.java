package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
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
    }
}

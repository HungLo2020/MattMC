package net.minecraft.hooks;

/**
 * Hooks for customizing window creation.
 * Allows mods to set GLFW window hints before window creation.
 */
public interface WindowCreationHooks {
    /**
     * Called before GLFW window creation to allow setting window hints.
     * Mods can use GLFW.glfwWindowHint() to configure the window.
     */
    default void beforeWindowCreation() {}
}

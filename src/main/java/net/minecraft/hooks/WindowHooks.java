package net.minecraft.hooks;

/**
 * Hook interface for window hint configuration.
 * Called before window creation.
 */
public interface WindowHooks {
    /**
     * Called to set additional window hints before glfwCreateWindow.
     */
    void onBeforeWindowCreate();
}

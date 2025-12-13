/*
 * Compatibility shim - OpenGLConfigScreen extends AbstractScreen
 */
package com.seibel.distanthorizons.core.config.gui;

/**
 * Compatibility class for the old OpenGLConfigScreen class name.
 * Extends AbstractScreen to work with MinecraftScreen.getScreen().
 */
public class OpenGLConfigScreen extends AbstractScreen
{
    @Override
    public void init() {
        // Stub implementation
    }
    
    @Override
    public void render(float delta) {
        // Stub implementation
    }
}

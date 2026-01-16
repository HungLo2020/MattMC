package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;

/**
 * Called before Distant Horizons starts rendering the deferred rendering pass. <br>
 * Will only happen if {@link IDhApiRenderProxy#getDeferTransparentRendering()} is true. <br>
 * Generally this is only used when shaders are enabled. <br>
 * Canceling the event will prevent DH from rendering the deferred pass that frame.
 *
 * @author James Seibel
 * @version 2024-1-22
 * @since API 2.0.0
 */
public abstract class DhApiBeforeDeferredRenderEvent extends DhApiBeforeRenderEvent
{
	
}
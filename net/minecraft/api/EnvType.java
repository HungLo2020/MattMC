package net.minecraft.api;

/**
 * Enum representing execution environment types.
 * <p>
 * Used to document whether code runs on client or server. This is a 
 * documentation-only annotation with no runtime effect.
 * 
 * <p>Originally from Fabric API, replaced with custom implementation 
 * to remove Fabric dependency.
 * 
 * @since Minecraft 1.21.10 (MattMC port)
 */
public enum EnvType {
	/**
	 * Code that runs only on the client (rendering, UI, input, etc.)
	 * <p>
	 * Examples: Rendering code, GUI screens, client-side prediction,
	 * input handling, resource loading, shaders
	 */
	CLIENT,
	
	/**
	 * Code that runs only on the dedicated server
	 * <p>
	 * Examples: Server command handling, chunk generation (server-side),
	 * dedicated server GUI, RCON server
	 */
	SERVER
}

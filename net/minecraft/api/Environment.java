package net.minecraft.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark client-only or server-only code.
 * <p>
 * This annotation is purely for documentation purposes and has no runtime 
 * effect on code execution. It serves to clearly indicate which parts of 
 * the Minecraft codebase are specific to the client or dedicated server.
 * 
 * <p>Example usage:
 * <pre>{@code
 * @Environment(EnvType.CLIENT)
 * public class ClientOnlyRenderer {
 *     @Environment(EnvType.CLIENT)
 *     public void render() {
 *         // Client rendering code
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Migration Note:</b> This replaces the Fabric API's 
 * {@code net.fabricmc.api.Environment} annotation. Functionally identical 
 * but removes the dependency on Fabric Loader.
 * 
 * @since Minecraft 1.21.10 (MattMC port)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
@Documented
public @interface Environment {
	/**
	 * The environment type where this code should run.
	 * 
	 * @return the environment type (CLIENT or SERVER)
	 */
	EnvType value();
}

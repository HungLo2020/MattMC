package com.seibel.distanthorizons.api.interfaces;

/**
 * Implemented by wrappers so developers can
 * access the underlying Minecraft object(s).
 *
 * @author James Seibel
 * @version 2023-6-17
 * @since API 1.0.0
 */
public interface IDhApiUnsafeWrapper
{
	
	/**
	 * Returns the Minecraft object this wrapper contains. <br>
	 * <strong>Warning</strong>: This object will be Minecraft
	 * version dependent and may change without notice. <br> <br>
	 *
	 * In order to cast this object to something usable, you may want
	 * to use <code>obj.getClass()</code> when in your IDE
	 * in order to determine what object this method returns for
	 * the specific version of Minecraft you are developing for.
	 */
	Object getWrappedMcObject();
	
}

package net.distanthorizons.api.enums.config;

/**
 * AUTO, 					<br>
 * BUFFER_STORAGE, 			<br>
 * SUB_DATA, 				<br>
 * DATA						<br>
 *
 * @author Leetom
 * @author James Seibel
 * @version 2024-4-6
 * @since API 3.0.0
 */
public enum EDhApiGpuUploadMethod
{
	/** Picks the best option based on the GPU the user has. */
	AUTO(false, false),
	
	// commented out since it isn't currently in use
	//BUFFER_STORAGE_MAPPING(true, true),
	
	/** Fast rendering, no stuttering. */
	BUFFER_STORAGE(false, true),
	
	/** Fast rendering but may stutter when uploading. */
	SUB_DATA(false, false),
	
	///** Don't upload, only should be used for debugging */
	//@Deprecated
	//NONE(false, false),
	
	/**
	 * May end up storing buffers in System memory. <br>
	 * Fast rending if in GPU memory, slow if in system memory, <br>
	 * but won't stutter when uploading.
	 * 
	 * @deprecated not currently supported
	 */
	@Deprecated
	BUFFER_MAPPING(true, false),
	
	/** Fast rendering but may stutter when uploading. */
	DATA(false, false);
	
	
	
	public final boolean useEarlyMapping;
	public final boolean useBufferStorage;
	
	EDhApiGpuUploadMethod(boolean useEarlyMapping, boolean useBufferStorage)
	{
		this.useEarlyMapping = useEarlyMapping;
		this.useBufferStorage = useBufferStorage;
	}
	
}
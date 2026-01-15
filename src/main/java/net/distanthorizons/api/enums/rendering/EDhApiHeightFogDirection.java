package net.distanthorizons.api.enums.rendering;

/**
 * ABOVE_CAMERA,				<br>
 * BELOW_CAMERA,				<br>
 * ABOVE_AND_BELOW_CAMERA,		<br>
 * ABOVE_SET_HEIGHT,			<br>
 * BELOW_SET_HEIGHT,			<br>
 * ABOVE_AND_BELOW_SET_HEIGHT,	<br>
 *
 * @author Leetom
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiHeightFogDirection
{
	ABOVE_CAMERA                (true,  true,  false),
	BELOW_CAMERA                (true,  false, true),
	ABOVE_AND_BELOW_CAMERA      (true,  true,  true),
	ABOVE_SET_HEIGHT            (false, true,  false),
	BELOW_SET_HEIGHT            (false, false, true),
	ABOVE_AND_BELOW_SET_HEIGHT  (false, true,  true);
	
	public final boolean basedOnCamera;
	public final boolean fogAppliesUp;
	public final boolean fogAppliesDown;
	
	EDhApiHeightFogDirection(boolean basedOnCamera, boolean fogAppliesUp, boolean fogAppliesDown)
	{
		this.basedOnCamera = basedOnCamera;
		this.fogAppliesUp = fogAppliesUp;
		this.fogAppliesDown = fogAppliesDown;
	}
}

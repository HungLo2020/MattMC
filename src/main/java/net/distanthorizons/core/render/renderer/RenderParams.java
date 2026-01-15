package net.distanthorizons.core.render.renderer;

import net.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import net.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import net.distanthorizons.core.api.internal.SharedApi;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.level.IDhClientLevel;
import net.distanthorizons.core.logging.DhLogger;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.render.RenderBufferHandler;
import net.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import net.distanthorizons.core.util.RenderUtil;
import net.distanthorizons.core.util.math.Mat4f;
import net.distanthorizons.core.util.math.Vec3d;
import net.distanthorizons.core.world.IDhClientWorld;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;

import net.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;

/**
 * An extension of {@link DhApiRenderParam}
 * that allows additional validation and putting all
 * rendering variables in a single place.
 */
public class RenderParams extends DhApiRenderParam
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	public IDhClientWorld dhClientWorld;
	public IDhClientLevel dhClientLevel;
	public IClientLevelWrapper clientLevelWrapper;
	public ILightMapWrapper lightmap;
	public RenderBufferHandler renderBufferHandler;
	public GenericObjectRenderer genericRenderer;
	public Vec3d exactCameraPosition;
	
	public boolean validationRun = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RenderParams(
			EDhApiRenderPass renderPass,
			float newPartialTicks,
			Mat4f newMcProjectionMatrix, Mat4f newMcModelViewMatrix,
			IClientLevelWrapper clientLevelWrapper
		)
	{
		super(renderPass,
			newPartialTicks,
			RenderUtil.getNearClipPlaneDistanceInBlocks(newPartialTicks), RenderUtil.getFarClipPlaneDistanceInBlocks(),
			newMcProjectionMatrix, newMcModelViewMatrix,
			RenderUtil.createLodProjectionMatrix(newMcProjectionMatrix, newPartialTicks), RenderUtil.createLodModelViewMatrix(newMcModelViewMatrix),
			clientLevelWrapper.getMinHeight());
		
		//LOGGER.debug("[DH-RENDER-PARAMS] ========== CREATING RENDER PARAMS ==========");
		//LOGGER.debug("[DH-RENDER-PARAMS] renderPass: " + renderPass);
		//LOGGER.debug("[DH-RENDER-PARAMS] clientLevelWrapper: " + clientLevelWrapper);
		
		this.dhClientWorld = SharedApi.tryGetDhClientWorld();
		//LOGGER.debug("[DH-RENDER-PARAMS] dhClientWorld from SharedApi: " + this.dhClientWorld);
		
		if (this.dhClientWorld != null)
		{
			// TODO changing to getOrLoadClientLevel() fixes Immersive Portals only rendering the level the user starts in
			//  however this may break how other level handling is done so James doesn't want to change it.
			//  Special handling may be necessary when Immersive Portals is present, although additional testing is needed.
			this.dhClientLevel = (IDhClientLevel) this.dhClientWorld.getLevel(clientLevelWrapper);
			//LOGGER.debug("[DH-RENDER-PARAMS] dhClientLevel: " + this.dhClientLevel);
			
			if (this.dhClientLevel != null)
			{
				this.renderBufferHandler = this.dhClientLevel.getRenderBufferHandler();
				this.genericRenderer = this.dhClientLevel.getGenericRenderer();
				//LOGGER.debug("[DH-RENDER-PARAMS] renderBufferHandler: " + this.renderBufferHandler);
				//LOGGER.debug("[DH-RENDER-PARAMS] genericRenderer: " + this.genericRenderer);
			}
			else
			{
				LOGGER.debug("[DH-RENDER-PARAMS] dhClientLevel is null!");
			}
		}
		else
		{
			LOGGER.debug("[DH-RENDER-PARAMS] dhClientWorld is null!");
		}
		
		this.clientLevelWrapper = clientLevelWrapper;
		this.lightmap = MC_RENDER.getLightmapWrapper(this.clientLevelWrapper);
		
		if (MC_CLIENT.playerExists())
		{
			this.exactCameraPosition = MC_RENDER.getCameraExactPosition();
		}
		
		//LOGGER.debug("[DH-RENDER-PARAMS] ========== RENDER PARAMS CREATED ==========");
	}
	
	
	
	//======================//
	// parameter validation //
	//======================//
	
	/** 
	 * Should be called before rendering is done.
	 * @return a message if LODs shouldn't be rendered, null if the LODs can render 
	 */
	public String getValidationErrorMessage()
	{
		// Note: all strings here should be constants to prevent String allocations
		
		this.validationRun = true;
		
		//LOGGER.debug("[DH-RENDER-VALIDATION] Running validation checks...");
		
		if (!MC_CLIENT.playerExists())
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No Player Exists");
			return "No Player Exists";
		}
		
		if (this.dhClientWorld == null)
		{
			//LOGGER.warn("[DH-RENDER-VALIDATION] Failed: No DH Client World Loaded");
			//LOGGER.warn("[DH-RENDER-VALIDATION] Current abstract world: " + SharedApi.getAbstractDhWorld());
			return "No DH Client World Loaded";
		}
		
		if (this.dhClientLevel == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No DH Client Level Loaded");
			return "No DH Client Level Loaded";
		}
		
		if (this.clientLevelWrapper == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No Client Level Wrapper Loaded");
			return "No Client Level Wrapper Loaded";
		}
		
		if (this.lightmap == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No Lightmap Loaded");
			return "No Lightmap Loaded";
		}
		
		if (this.renderBufferHandler == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No RenderBufferHandler Present");
			return "No RenderBufferHandler Present";
		}
		
		if (this.genericRenderer == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No Generic Renderer Present");
			return "No Generic Renderer Present";
		}
		
		if (this.dhModelViewMatrix == null
			|| this.mcModelViewMatrix == null)
		{
			//LOGGER.debug("[DH-RENDER-VALIDATION] Failed: No MVM or Proj Matrix Given");
			return "No MVM or Proj Matrix Given";
		}
		
		//LOGGER.debug("[DH-RENDER-VALIDATION] All validation checks passed!");
		
		return null;
	}
	
	
	
}

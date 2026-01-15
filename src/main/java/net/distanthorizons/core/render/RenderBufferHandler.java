package net.distanthorizons.core.render;

import net.distanthorizons.api.DhApi;
import net.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import net.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import net.distanthorizons.core.config.Config;
import net.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import net.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import net.distanthorizons.core.dependencyInjection.SingletonInjector;
import net.distanthorizons.core.logging.DhLogger;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.logging.f3.F3Screen;
import net.distanthorizons.core.pos.DhLodPos;
import net.distanthorizons.core.pos.DhSectionPos;
import net.distanthorizons.core.pos.Pos2D;
import net.distanthorizons.core.render.renderer.LodRenderer;
import net.distanthorizons.core.render.renderer.RenderParams;
import net.distanthorizons.core.util.objects.SortedArraySet;
import net.distanthorizons.core.util.objects.quadTree.QuadNode;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import net.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import net.distanthorizons.core.util.math.Mat4f;
import net.distanthorizons.core.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Iterator;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 */
public class RenderBufferHandler implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);

	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	private final SortedArraySet<LodBufferContainer> loadedNearToFarBuffers;
	
	private int visibleBufferCount;
	private int culledBufferCount;
	private int shadowVisibleBufferCount;
	private int shadowCulledBufferCount;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RenderBufferHandler(LodQuadTree lodQuadTree) 
	{ 
		this.lodQuadTree = lodQuadTree;
		
		IDhApiCullingFrustum coreCameraFrustum = DhApi.overrides.get(IDhApiCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreCameraFrustum == null)
		{
			DhApi.overrides.bind(IDhApiCullingFrustum.class, new DhFrustumBounds());
		}
		
		// by default the shadow pass shouldn't have any frustum culling
		IDhApiShadowCullingFrustum coreShadowFrustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreShadowFrustum == null)
		{
			DhApi.overrides.bind(IDhApiShadowCullingFrustum.class, new NeverCullFrustum());
		}
		
		this.loadedNearToFarBuffers = new SortedArraySet<>(this::sortBufferContainersNearToFar);
	}
	private int sortBufferContainersNearToFar(LodBufferContainer loadedBufferA, LodBufferContainer loadedBufferB)
	{
		Pos2D aPos = DhSectionPos.getCenterBlockPos(loadedBufferA.pos).toPos2D();
		Pos2D bPos = DhSectionPos.getCenterBlockPos(loadedBufferB.pos).toPos2D();
		
		Pos2D centerPos = this.lodQuadTree.getCenterBlockPos().toPos2D();
		
		int aManhattanDistance = aPos.manhattanDist(centerPos);
		int bManhattanDistance = bPos.manhattanDist(centerPos);
		return aManhattanDistance - bManhattanDistance;
	}
	
	
	
	//=================//
	// render building //
	//=================//
	
	/**
	 * The following buildRenderList sorting method is based on the following reddit post: <br>
	 * <a href="https://www.reddit.com/r/VoxelGameDev/comments/a0l8zc/correct_depthordering_for_translucent_discrete/">correct_depth_ordering_for_translucent_discrete</a>
	 */
	public void buildRenderList(RenderParams renderParams)
	{
		// clear the old list so we can start fresh
		this.loadedNearToFarBuffers.clear();
		
		
		
		//====================================//
		// get and update the culling frustum //
		//====================================//
		
		// get the culling frustum
		boolean enableFrustumCulling;
		IDhApiCullingFrustum frustum;
		boolean isShadowPass = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isRenderingShadowPass());
		if (isShadowPass)
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class);
		}
		else
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiCullingFrustum.class);
		}
		
		
		// update the frustum if necessary
		if (enableFrustumCulling)
		{
			int worldMinY = renderParams.clientLevelWrapper.getMinHeight();
			int worldHeight = renderParams.clientLevelWrapper.getMaxHeight();
			
			Vec3d cameraPos = MC_RENDER.getCameraExactPosition();
			
			Matrix4fc matWorldView = new Matrix4f()
					.setTransposed(renderParams.mcModelViewMatrix.getValuesAsArray())
					.translate(
							-(float) cameraPos.x, 
							-(float) cameraPos.y, 
							-(float) cameraPos.z);
			
			Matrix4fc matWorldViewProjection = new Matrix4f()
					.setTransposed(renderParams.dhProjectionMatrix.getValuesAsArray())
					.mul(matWorldView);
			
			frustum.update(worldMinY, worldMinY + worldHeight, new Mat4f(matWorldViewProjection));
		}
		
		
		
		//=========================//
		// Update the section list //
		//=========================//
		
		if (isShadowPass)
		{
			this.shadowCulledBufferCount = 0;
		}
		else
		{
			this.culledBufferCount = 0;
		}
		
		// setup iterator with culling frustum
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.lodQuadTree.nodeIteratorWithStoppingFilter((QuadNode<LodRenderSection> node) ->
		{
			if (node == null)
			{
				return true;
			}
			
			LodRenderSection renderSection = node.value;
			if (renderSection == null)
			{
				return false;
			}
			
			
			try
			{
				if (enableFrustumCulling)
				{
					DhLodPos lodBounds = DhSectionPos.getSectionBBoxPos(renderSection.pos);
					int blockMinX = lodBounds.getMinX().toBlockWidth();
					int blockMinZ = lodBounds.getMinZ().toBlockWidth();
					int lodBlockWidth = lodBounds.getBlockWidth();
					if (!frustum.intersects(blockMinX, blockMinZ, lodBlockWidth, lodBounds.detailLevel))
					{
						if (isShadowPass)
						{
							this.shadowCulledBufferCount++;
						}
						else
						{
							this.culledBufferCount++;
						}
						
						return true;
					}
				}
				
				return false;
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue during culling for node pos: ["+DhSectionPos.toString(node.sectionPos)+"], error: ["+e.getMessage()+"].", e);
				
				// don't cull if there was an unexpected issue
				return false;
			}
		});
		
		while (nodeIterator.hasNext())
		{
			QuadNode<LodRenderSection> node = nodeIterator.next();
			
			long sectionPos = node.sectionPos;
			LodRenderSection renderSection = node.value;
			if (renderSection == null)
			{
				continue;
			}
			
			
			
			try
			{
				LodBufferContainer bufferContainer = renderSection.bufferContainer;
				if (bufferContainer == null 
					|| !renderSection.getRenderingEnabled())
				{
					continue;
				}
				
				this.loadedNearToFarBuffers.add(bufferContainer);
			}
			catch (Exception e)
			{
				LOGGER.error("Error updating QuadTree render source at [" + DhSectionPos.toString(renderSection.pos) + "], error: ["+e.getMessage()+"].", e);
			}
		}
		
		if (isShadowPass)
		{
			this.shadowVisibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		else
		{
			this.visibleBufferCount = this.loadedNearToFarBuffers.size();
		}
	}
	
	
	
	//================//
	// render methods //
	//================//
	
	public SortedArraySet<LodBufferContainer> getColumnRenderBuffers() { return this.loadedNearToFarBuffers; }
	
	
	
	//=========//
	// F3 menu //
	//=========//
	
	public String getVboRenderDebugMenuString()
	{
		String countText = F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount + this.culledBufferCount);
		}
		return "VBO Render Count: [" + countText + "]";
	}
	public String getShadowPassRenderDebugMenuString()
	{
		boolean hasIrisShaders = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isShaderPackInUse());
		if (!hasIrisShaders)
		{
			return null;
		}
		
		String countText = F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount + this.shadowCulledBufferCount);
		}
		return "Shadow VBO Render Count: [" + countText + "]";
	}
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close() { this.lodQuadTree.close(); }
	
	
	
}

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.Pos2D;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.DistantHorizonsSemanticCollector;

import java.util.Arrays;
import java.util.Iterator;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 */
public class RenderBufferHandler implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);

	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	private final SortedArraySet<LodBufferContainer> loadedNearToFarBuffers;
	/** The walk's drawable containers as (key, generation) pairs, reused across frames. */
	private long[] semanticCandidates = new long[512];
	/** The last walk's semantic render list (see {@link DistantHorizonsSemanticCollector#collectVisibleFrame}). */
	private DistantHorizonsSemanticCollector.VisibleFrame semanticVisibleFrame = DistantHorizonsSemanticCollector.VisibleFrame.EMPTY;
	
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
		this.semanticVisibleFrame = DistantHorizonsSemanticCollector.VisibleFrame.EMPTY;
		
		
		
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
		int visitedNodeCount = 0;
		int nullSectionCount = 0;
		int nullBufferCount = 0;
		int disabledSectionCount = 0;
		int addedBufferCount = 0;
		int semanticCandidateCount = 0;
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
			visitedNodeCount++;
			
			long sectionPos = node.sectionPos;
			LodRenderSection renderSection = node.value;
			if (renderSection == null)
			{
				nullSectionCount++;
				continue;
			}
			
			
			
			try
			{
				// Both the legacy VBO route and the Rust semantic route must honor the
				// quadtree's live visibility decision before inspecting their backing
				// representation. Rust-owned columns deliberately have no legacy VBO.
				if (!renderSection.getRenderingEnabled())
				{
					disabledSectionCount++;
					continue;
				}
				LodBufferContainer bufferContainer = renderSection.bufferContainer;
				if (net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()) {
					// A stale enabled bit can survive while DH replaces or closes a
					// section. Without an installed lifecycle container there is no
					// current CPU result to publish, just as there is no legacy VBO to
					// draw. The quadtree will re-admit the section after its build swap.
					if (bufferContainer == null) {
						nullBufferCount++;
						continue;
					}
					// A closed/replaced container may remain attached to a stale
					// render-enabled node until the next quadtree update. It owns no
					// publishable generation and must not enter this frame's demand
					// set; the collector drops containers whose generation is no
					// longer current.
					long generation = bufferContainer.rustSemanticWalkGeneration();
					if (generation < 0L) {
						nullBufferCount++;
						continue;
					}
					// A completed empty section participates in DH's parent/child
					// quadtree transition but owns no draw or native asset. Do not count
					// it as an unpublished visible candidate forever.
					if (generation == 0L) {
						continue;
					}
					// A semantic DH build may retain the Java container as a CPU-side
					// lifecycle object after its copied payload has been handed to Rust.
					// Keep every frustum-visible identity in the semantic list, including
					// unpublished columns, so preflight can mark that demand pending and
					// reject the frame coherently. Never re-admit an unpublished section to
					// the legacy VBO list: the whole-frame route owns this boundary.
					// An already-enabled DH node can survive a generation retirement
					// without re-entering LodRenderSection#canRender, so the collector
					// reasserts publication demand for every unpublished candidate,
					// immediately before the coordinator's bounded visible-only flush.
					if (semanticCandidateCount * 2 == this.semanticCandidates.length) {
						this.semanticCandidates = Arrays.copyOf(this.semanticCandidates, semanticCandidateCount * 4);
					}
					this.semanticCandidates[semanticCandidateCount * 2] = renderSection.pos;
					this.semanticCandidates[semanticCandidateCount * 2 + 1] = generation;
					semanticCandidateCount++;
					continue;
				}
				if (bufferContainer == null)
				{
					nullBufferCount++;
					continue;
				}
				
				this.loadedNearToFarBuffers.add(bufferContainer);
				addedBufferCount++;
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
		if (DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()) {
			DhBlockPos2D center = this.lodQuadTree.getCenterBlockPos();
			this.semanticVisibleFrame = DistantHorizonsSemanticCollector.collectVisibleFrame(
				this.semanticCandidates, semanticCandidateCount, center.x, center.z);
			if (this.semanticVisibleFrame.requestFailures() > 0)
			{
				LOGGER.error("Error requesting publication of [" + this.semanticVisibleFrame.requestFailures()
					+ "] visible DH columns, first: [" + this.semanticVisibleFrame.requestFailure() + "].");
			}
		}
	}
	
	
	
	//================//
	// render methods //
	//================//
	
	public SortedArraySet<LodBufferContainer> getColumnRenderBuffers() { return this.loadedNearToFarBuffers; }

	/** Real DH quadtree/frustum visibility for the Rust whole-frame route:
	 * the copied CPU column identities near to far, and what the collector
	 * admitted from them. */
	public DistantHorizonsSemanticCollector.VisibleFrame getSemanticVisibleFrame() { return this.semanticVisibleFrame; }
	
	
	
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

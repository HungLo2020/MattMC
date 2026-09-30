package com.seibel.distanthorizons.core.render.renderer.generic;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.EPlatform;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.blaze3d.systems.RenderPass;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles rendering generic groups of {@link DhApiRenderableBox}.
 * 
 * @see IDhApiCustomRenderRegister
 * @see DhApiRenderableBox
 */
public class GenericObjectRenderer implements IDhApiCustomRenderRegister
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final ISodiumAccessor SODIUM = ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
	
	private static final DhApiRenderableBoxGroupShading DEFAULT_SHADING = DhApiRenderableBoxGroupShading.getUnshaded();
	
	/** 
	 * Can be used to troubleshoot the renderer. 
	 * If enabled several debug objects will render around (0,150,0). 
	 */
	public static final boolean RENDER_DEBUG_OBJECTS = false;
	
	
	// rendering setup
	private boolean init = false;
	
	private boolean instancedRenderingAvailable;
	
	private final ConcurrentHashMap<Long, RenderableBoxGroup> boxGroupById = new ConcurrentHashMap<>();
	/** Capture-only positive-path fixture; never enabled by normal gameplay. */
	private RenderableBoxGroup rustSemanticDiagnosticFixture;
	/*
	 * The Rust route copies these values synchronously into its bounded semantic
	 * queue. Reuse the frame-local containers so the ordinary DH preflight does
	 * not allocate three lists on every render tick. They never cross the Java /
	 * Rust boundary and are cleared before the next collection.
	 */
	private final ArrayList<Long> rustSemanticGroupIds = new ArrayList<>();
	private final ArrayList<VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord> rustSemanticBoxes = new ArrayList<>();
	private final ArrayList<RenderableBoxGroup> rustSemanticGroupsToPostRender = new ArrayList<>();
	
	
	
	/** A box from 0,0,0 to 1,1,1 */
	private static final float[] BOX_VERTICES = {
			// Pos x y z
			
			// min X, vertical face
			0, 0, 0,
			1, 0, 0,
			1, 1, 0,
			0, 1, 0,
			// max X, vertical face
			0, 1, 1,
			1, 1, 1,
			1, 0, 1,
			0, 0, 1,
			
			// min Z, vertical face
			0, 0, 1,
			0, 0, 0,
			0, 1, 0,
			0, 1, 1,
			// max Z, vertical face
			1, 0, 1,
			1, 1, 1,
			1, 1, 0,
			1, 0, 0,
			
			// min Y, horizontal face
			0, 0, 1,
			1, 0, 1,
			1, 0, 0,
			0, 0, 0,
			// max Y, horizontal face
			0, 1, 1,
			1, 1, 1,
			1, 1, 0,
			0, 1, 0,
	};
	
	private static final int[] BOX_INDICES = {
			// min X, vertical face
			2, 1, 0,    
			0, 3, 2,
			// max X, vertical face
			6, 5, 4,
			4, 7, 6,
			
			// min Z, vertical face
			10, 9, 8,
			8, 11, 10,
			// max Z, vertical face
			14, 13, 12,
			12, 15, 14,
			
			// min Y, horizontal face
			18, 17, 16,
			16, 19, 18,
			// max Y, horizontal face
			20, 21, 22, 
			22, 23, 20,
	};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public GenericObjectRenderer() { }
	
	
	
	
	//==============//
	// registration //
	//==============//
	
	@Override
	public void add(IDhApiRenderableBoxGroup iBoxGroup) throws IllegalArgumentException 
	{
		if (!(iBoxGroup instanceof RenderableBoxGroup))
		{
			throw new IllegalArgumentException("Box group must be of type ["+ RenderableBoxGroup.class.getSimpleName()+"], type received: ["+(iBoxGroup != null ? iBoxGroup.getClass() : "NULL")+"].");
		}
		RenderableBoxGroup boxGroup = (RenderableBoxGroup) iBoxGroup;
		
		
		long id = boxGroup.getId();
		if (this.boxGroupById.containsKey(id))
		{
			throw new IllegalArgumentException("A box group with the ID [" + id + "] is already present.");
		}
		
		this.boxGroupById.put(id, boxGroup);
	}
	
	@Override
	public IDhApiRenderableBoxGroup remove(long id) { return this.boxGroupById.remove(id); }
	
	public void clear() {
		this.boxGroupById.clear();
		this.rustSemanticDiagnosticFixture = null;
		this.rustSemanticGroupIds.clear();
		this.rustSemanticBoxes.clear();
		this.rustSemanticGroupsToPostRender.clear();
	}
	
	
	
	//===========//
	// rendering //
	//===========//

	/**
	 * Copies DH's generic renderable-box registry into the Rust semantic world
	 * stream.  The ordinary Java renderer owns Vulkan buffers and shader state,
	 * so it is deliberately never entered while the Rust whole-frame route is
	 * selected.  Only the box geometry, color, light, and directional shading
	 * values cross this boundary.
	 *
	 * @return {@code true} when every visible group was copied (including the
	 *         empty-registry case), or {@code false} when the route must stay
	 *         unselected for this frame.
	 */
	public boolean collectRustSemantic(DhApiRenderParam renderEventParam)
	{
		if (!Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get())
		{
			String identity = "rust-vulkan-whole-frame:disabled:groups=" + this.boxGroupById.size();
			DeterministicCameraCapture.recordSubmittedWorkIdentity("distant-horizons-generic-semantics", identity);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity("distant-horizons-generic-semantics", identity);
			return true;
		}

		Vec3d camPos = MC_RENDER.getCameraExactPosition();
		if (camPos == null || !Double.isFinite(camPos.x) || !Double.isFinite(camPos.y) || !Double.isFinite(camPos.z))
		{
			return false;
		}
		ensureRustSemanticDiagnosticFixture(camPos);

		// These lists are scratch storage only. RustGalWorldPrimitiveRenderer
		// consumes the copied records synchronously, so retaining their capacity
		// is safe and avoids per-frame Java list/array allocation.
		ArrayList<Long> ids = this.rustSemanticGroupIds;
		ids.clear();
		ids.addAll(this.boxGroupById.keySet());
		ids.sort(Long::compare);
		ArrayList<VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord> semanticBoxes = this.rustSemanticBoxes;
		semanticBoxes.clear();
		ArrayList<RenderableBoxGroup> groupsToPostRender = this.rustSemanticGroupsToPostRender;
		groupsToPostRender.clear();
		int activeGroups = 0;
		int cloudGroups = 0;
		int ssaoBoxes = 0;
		int nonSsaoBoxes = 0;
		try
		{
			for (long id : ids)
			{
				RenderableBoxGroup boxGroup = this.boxGroupById.get(id);
				if (boxGroup == null)
				{
					continue;
				}
				// Match Java DH's callback contract: pre-render runs even for an
				// inactive group so cloud/beacon providers can update visibility.
				boxGroup.preRender(renderEventParam);
				if (!boxGroup.active)
				{
					continue;
				}
				activeGroups++;
				boolean cancelRendering = ApiEventInjector.INSTANCE.fireAllEvents(
					DhApiBeforeGenericObjectRenderEvent.class,
					new DhApiBeforeGenericObjectRenderEvent.EventParam(renderEventParam, boxGroup));
				if (cancelRendering)
				{
					continue;
				}
				boolean cloudGroup = "Clouds".equals(boxGroup.resourceLocationPath);
				int groupBoxStart = semanticBoxes.size();

				DhApiVec3d origin = boxGroup.getOriginBlockPos();
				DhApiRenderableBoxGroupShading shading = boxGroup.shading;
				if (shading == null)
				{
					shading = DhApiRenderableBoxGroupShading.getUnshaded();
				}
				for (int index = 0; index < boxGroup.size(); index++)
				{
					DhApiRenderableBox box;
					try
					{
						box = boxGroup.get(index);
					}
					catch (IndexOutOfBoundsException concurrentMutation)
					{
						return false;
					}
					if (box == null || box.minPos == null || box.maxPos == null || box.color == null)
					{
						return false;
					}
					if (!finite(box.minPos.x) || !finite(box.minPos.y) || !finite(box.minPos.z)
						|| !finite(box.maxPos.x) || !finite(box.maxPos.y) || !finite(box.maxPos.z)
						|| box.minPos.x > box.maxPos.x || box.minPos.y > box.maxPos.y || box.minPos.z > box.maxPos.z
						|| semanticBoxes.size() >= 10_000)
					{
						return false;
					}
					if (!finite(shading.north) || !finite(shading.south) || !finite(shading.east)
						|| !finite(shading.west) || !finite(shading.top) || !finite(shading.bottom))
					{
						return false;
					}
					semanticBoxes.add(new VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord(
						(float)(box.minPos.x + origin.x - camPos.x),
						(float)(box.minPos.y + origin.y - camPos.y),
						(float)(box.minPos.z + origin.z - camPos.z),
						(float)(box.maxPos.x + origin.x - camPos.x),
						(float)(box.maxPos.y + origin.y - camPos.y),
						(float)(box.maxPos.z + origin.z - camPos.z),
						box.color.getRGB(), LightTexture.pack(boxGroup.blockLight, boxGroup.skyLight),
						shading.north, shading.south, shading.east, shading.west, shading.top, shading.bottom,
						boxGroup.ssaoEnabled, box.material & 0xff, (activeGroups - 1) & 0xffff
					));
					if (boxGroup.ssaoEnabled)
					{
						ssaoBoxes++;
					}
					else
					{
						nonSsaoBoxes++;
					}
				}
				if (cloudGroup && semanticBoxes.size() > groupBoxStart)
				{
					cloudGroups++;
				}
				groupsToPostRender.add(boxGroup);
			}
			RustGalWorldPrimitiveRenderer.enqueueDistantHorizonsGenericBoxes(semanticBoxes);
			if (cloudGroups > 0)
			{
				RustGalWorldPrimitiveRenderer.markDistantHorizonsPrivateClouds();
			}
		}
		catch (RuntimeException rejected)
		{
			return false;
		}
		finally
		{
			// A later group can reject the Rust route (for example because it is
			// concurrently mutated) after earlier groups have
			// already run preRender.  Keep the callback lifecycle balanced even on
			// that fail-closed path; otherwise a provider can retain frame-local
			// state and make the next admission depend on a rejected frame.
			for (RenderableBoxGroup boxGroup : groupsToPostRender)
			{
				boxGroup.postRender(renderEventParam);
			}
		}
		String identity = "rust-vulkan-whole-frame:groups=" + ids.size()
			+ ":active=" + activeGroups
			+ ":cloudDelegated=0"
			+ ":cloudPrivate=" + cloudGroups
			+ ":boxes=" + semanticBoxes.size()
			+ ":faces=" + (semanticBoxes.size() * 6)
			+ ":ssaoBoxes=" + ssaoBoxes
			+ ":nonSsaoBoxes=" + nonSsaoBoxes;
		// This receipt records collector execution even when the current saved
		// world has no non-cloud generic boxes.  A separate family is retained
		// for the stronger positive "boxes actually copied" signal.
		DeterministicCameraCapture.recordSubmittedWorkIdentity("distant-horizons-generic-semantics", identity);
		GraphicsFrameBenchmark.recordSubmittedWorkIdentity("distant-horizons-generic-semantics", identity);
		if (!semanticBoxes.isEmpty())
		{
			DeterministicCameraCapture.recordSubmittedWorkIdentity("distant-horizons-generic-boxes", identity);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity("distant-horizons-generic-boxes", identity);
		}
		return true;
	}

	private void ensureRustSemanticDiagnosticFixture(Vec3d camPos)
	{
		if (!Boolean.getBoolean("mattmc.dev.dhGenericBoxFixture") || this.rustSemanticDiagnosticFixture != null)
		{
			return;
		}
		// Place the fixture in the current camera's view direction.  Generic
		// boxes use world-axis-aligned bounds, so a hard-coded -Z offset can be
		// outside the deterministic capture view whenever the camera is yawed.
		Vec3f look = MC_RENDER.getLookAtVector();
		if (look == null || !finite(look.x) || !finite(look.y) || !finite(look.z))
		{
			return;
		}
		Vec3f direction = look.copy();
		if (!direction.normalize())
		{
			return;
		}
		// The private DH pass clips near geometry at the vanilla/DH seam.
		// Place this positive-path probe beyond that seam so its submitted
		// faces can actually appear in the final deterministic screenshot.
		float centerX = direction.x * 110.0F;
		float centerY = direction.y * 110.0F;
		float centerZ = direction.z * 110.0F;
		DhApiRenderableBox fixtureBox = new DhApiRenderableBox(
			new DhApiVec3d(centerX - 12.0F, centerY - 12.0F, centerZ - 12.0F),
			new DhApiVec3d(centerX + 12.0F, centerY + 12.0F, centerZ + 12.0F),
			new Color(255, 32, 224, 255),
			EDhApiBlockMaterial.ILLUMINATED);
		RenderableBoxGroup fixture = new RenderableBoxGroup(
			"MattMC:RustGenericBoxFixture",
			new DhApiVec3d(camPos.x, camPos.y, camPos.z),
			java.util.List.of(fixtureBox),
			true);
		fixture.setSkyLight(LodUtil.MAX_MC_LIGHT);
		fixture.setBlockLight(LodUtil.MAX_MC_LIGHT);
		// Real clouds and beacons use the post-SSAO phase. Keep this explicit
		// fixture in the pre-SSAO phase so one capture exercises both sides of
		// Frozen's generic-object ordering contract.
		fixture.setSsaoEnabled(true);
		fixture.setShading(DhApiRenderableBoxGroupShading.getUnshaded());
		this.add(fixture);
		this.rustSemanticDiagnosticFixture = fixture;
	}

	private static boolean finite(double value)
	{
		return Double.isFinite(value);
	}

	private static boolean finite(float value)
	{
		return Float.isFinite(value);
	}
	

	
	
	
	
	//=========//
	// getters //
	//=========//
	
	/** @throws IllegalStateException if {@link #init()} function hasn't been called yet */
	public boolean getInstancedRenderingAvailable() throws IllegalStateException
	{
		if (!this.init)
		{
			throw new IllegalStateException("GL initialization hasn't been completed.");
		}
		
		return this.instancedRenderingAvailable; 
	}
	
	
	
	//=========//
	// F3 menu //
	//=========//
	
	public String getVboRenderDebugMenuString()
	{
		// get counts
		int totalGroupCount = this.boxGroupById.size();
		int totalBoxCount = 0;
		
		int activeGroupCount = 0;
		int activeBoxCount = 0;
		
		for (long key : this.boxGroupById.keySet())
		{
			RenderableBoxGroup renderGroup = this.boxGroupById.get(key);
			if (renderGroup.active)
			{
				activeGroupCount++;
				activeBoxCount += renderGroup.size();
			}
			totalBoxCount += renderGroup.size();
		}
		
		
		return "Generic Obj #: " + F3Screen.NUMBER_FORMAT.format(activeGroupCount) + "/" + F3Screen.NUMBER_FORMAT.format(totalGroupCount) + ", " +
				"Cube #: " + F3Screen.NUMBER_FORMAT.format(activeBoxCount) + "/" + F3Screen.NUMBER_FORMAT.format(totalBoxCount);
	}
	
}

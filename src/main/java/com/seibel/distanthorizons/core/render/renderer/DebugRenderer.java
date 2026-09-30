package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPrimitiveMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Handles rendering the wireframe particles that are used for seeing what the system's doing.
 */
public class DebugRenderer
{
	public static DebugRenderer INSTANCE = new DebugRenderer();
	
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.maxCountPerSecond(1)
			.build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	
	
	// used when rendering
	private Mat4f transformationMatrixThisFrame;
	private Vec3f camPosFloatThisFrame;
	
	
	private final RendererLists rendererLists = new RendererLists();
	private final PriorityBlockingQueue<BoxParticle> particles = new PriorityBlockingQueue<>();
	
	
	
	/** A box from 0,0,0 to 1,1,1 */
	private static final float[] BOX_VERTICES = {
			// Pos x y z
			0, 0, 0,
			1, 0, 0,
			1, 1, 0,
			0, 1, 0,
			0, 0, 1,
			1, 0, 1,
			1, 1, 1,
			0, 1, 1,
	};
	
	private static final int[] BOX_OUTLINE_INDICES = {
			0, 1,
			1, 2,
			2, 3,
			3, 0,
			
			4, 5,
			5, 6,
			6, 7,
			7, 4,
			
			0, 4,
			1, 5,
			2, 6,
			3, 7,
	};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private DebugRenderer() { }
	
	
	
	//==============//
	// registration //
	//==============//
	
	public static void makeParticle(BoxParticle particle)
	{
		if (INSTANCE != null && Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get())
		{
			INSTANCE.particles.add(particle);
		}
	}
	
	public static void register(IDebugRenderable renderable, ConfigEntry<Boolean> config) { if (INSTANCE != null) { INSTANCE.addRenderer(renderable, config); } }
	public void addRenderer(IDebugRenderable renderable, ConfigEntry<Boolean> config) { this.rendererLists.addRenderable(renderable, config); }
	
	public static void unregister(IDebugRenderable renderable, ConfigEntry<Boolean> config) { if (INSTANCE != null) { INSTANCE.removeRenderer(renderable, config); } }
	private void removeRenderer(IDebugRenderable renderable, ConfigEntry<Boolean> config) { this.rendererLists.removeRenderable(renderable, config); }
	
	public static void clearRenderables() { INSTANCE.rendererLists.clearRenderables(); }
	
	
	
	//===========//
	// rendering //
	//===========//
	
	/** Copies each registered debug box into Rust's world debug-line stream. */
	public void render(Mat4f transform)
	{
		this.transformationMatrixThisFrame = transform;
		Vec3d camPos = MC_RENDER.getCameraExactPosition();
		this.camPosFloatThisFrame = new Vec3f((float) camPos.x, (float) camPos.y, (float) camPos.z);
		this.rendererLists.render(this);
		for (BoxParticle particle : this.particles)
		{
			if (!particle.isDead()) this.renderBox(particle.getBox());
		}
	}
	
	public void renderBox(Box box)
	{
		if (box == null || this.transformationMatrixThisFrame == null || this.camPosFloatThisFrame == null) return;
		float minX = box.minPos.x - this.camPosFloatThisFrame.x;
		float minY = box.minPos.y - this.camPosFloatThisFrame.y;
		float minZ = box.minPos.z - this.camPosFloatThisFrame.z;
		float sizeX = box.maxPos.x - box.minPos.x;
		float sizeY = box.maxPos.y - box.minPos.y;
		float sizeZ = box.maxPos.z - box.minPos.z;
		if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
				|| !Float.isFinite(sizeX) || !Float.isFinite(sizeY) || !Float.isFinite(sizeZ)
				|| sizeX < 0.0F || sizeY < 0.0F || sizeZ < 0.0F) return;
		org.joml.Matrix4f transform = com.seibel.distanthorizons.core.util.math.Mat4f
			.createJomlMatrix(this.transformationMatrixThisFrame)
			.translate(minX, minY, minZ)
			.scale(sizeX, sizeY, sizeZ);
		float[] endpoints = {
			0,0,0, 1,0,0, 1,0,0, 1,1,0, 1,1,0, 0,1,0, 0,1,0, 0,0,0,
			0,0,1, 1,0,1, 1,0,1, 1,1,1, 1,1,1, 0,1,1, 0,1,1, 0,0,1,
			0,0,0, 0,0,1, 1,0,0, 1,0,1, 1,1,0, 1,1,1, 0,1,0, 0,1,1
		};
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(
				transform, endpoints, box.color.getRGB(), 1.0F)) {
			throw new IllegalStateException("Rust DH debug-wireframe route rejected semantic box lines");
		}
	}

	
	
	//================//
	// helper classes //
	//================//
	
	public static final class Box
	{
		public Vec3f minPos;
		public Vec3f maxPos;
		public Color color;
		
		
		
		public Box(Vec3f minPos, Vec3f maxPos, Color color)
		{
			this.minPos = minPos;
			this.maxPos = maxPos;
			this.color = color;
		}
		
		public Box(Vec3f minPos, Vec3f maxPos, Color color, Vec3f margin)
		{
			this.minPos = minPos;
			this.minPos.add(margin);
			this.maxPos = maxPos;
			this.maxPos.subtract(margin);
			this.color = color;
		}
		
		public Box(DhLodPos pos, float minY, float maxY, float marginPercent, Color color)
		{
			DhBlockPos2D blockMin = pos.getCornerBlockPos();
			DhBlockPos2D blockMax = blockMin.add(pos.getBlockWidth(), pos.getBlockWidth());
			float edge = pos.getBlockWidth() * marginPercent;
			Vec3f a = new Vec3f(blockMin.x + edge, minY, blockMin.z + edge);
			Vec3f b = new Vec3f(blockMax.x - edge, maxY, blockMax.z - edge);
			this.minPos = a;
			this.maxPos = b;
			this.color = color;
		}
		
		public Box(DhLodPos pos, float y, float yDiff, Object hash, float marginPercent, Color color)
		{
			float hashY = ((float) hash.hashCode() / Integer.MAX_VALUE) * yDiff;
			DhBlockPos2D blockMin = pos.getCornerBlockPos();
			DhBlockPos2D blockMax = blockMin.add(pos.getBlockWidth(), pos.getBlockWidth());
			float edge = pos.getBlockWidth() * marginPercent;
			Vec3f a = new Vec3f(blockMin.x + edge, hashY, blockMin.z + edge);
			Vec3f b = new Vec3f(blockMax.x - edge, hashY, blockMax.z - edge);
			this.minPos = a;
			this.maxPos = b;
			this.color = color;
		}
		
		public Box(long pos, float minY, float maxY, float marginPercent, Color color)
		{
			this(DhSectionPos.getSectionBBoxPos(pos), minY, maxY, marginPercent, color);
		}
		
		public Box(long pos, float y, float yDiff, Object hash, float marginPercent, Color color)
		{
			this(DhSectionPos.getSectionBBoxPos(pos), y, yDiff, hash, marginPercent, color);
		}
		
	}
	
	public static final class BoxParticle implements Comparable<BoxParticle>
	{
		public Box box;
		public long startMsTime;
		public long durationInMs;
		public float yChange;
		
		private BoxParticle(Box box, long startMsTime, long durationInMs, float yChange)
		{
			this.box = box;
			this.startMsTime = startMsTime;
			this.durationInMs = durationInMs;
			this.yChange = yChange;
		}
		
		public BoxParticle(Box box, double secondDuration, float yChange) { this(box, System.currentTimeMillis(), (long) (secondDuration * 1_000), yChange); }
		
		
		@Override
		public int compareTo(@NotNull BoxParticle particle)
		{
			return Long.compare(this.startMsTime + this.durationInMs, particle.startMsTime + particle.durationInMs);
		}
		
		public Box getBox()
		{
			long nowMs = System.currentTimeMillis();
			float percent = (nowMs - this.startMsTime) / (float) this.durationInMs;
			percent = (float) Math.pow(percent, 4);
			float yDiff = this.yChange * percent;
			return new Box(new Vec3f(this.box.minPos.x, this.box.minPos.y + yDiff, this.box.minPos.z), new Vec3f(this.box.maxPos.x, this.box.maxPos.y + yDiff, this.box.maxPos.z), this.box.color);
		}
		
		public boolean isDead() { return (System.currentTimeMillis() - this.startMsTime) > this.durationInMs; }
		
	}
	
	public static final class BoxWithLife implements IDebugRenderable, Closeable
	{
		public Box box;
		public BoxParticle particleOnClose;
		
		
		public BoxWithLife(Box box, long ns, float yChange, Color deathColor)
		{
			this.box = box;
			this.particleOnClose = new BoxParticle(new Box(box.minPos, box.maxPos, deathColor), -1, ns, yChange);
			register(this, null);
		}
		
		
		public BoxWithLife(Box box, long ns, float yChange) { this(box, ns, yChange, box.color); }
		
		public BoxWithLife(Box box, double s, float yChange, Color deathColor)
		{
			this.box = box;
			this.particleOnClose = new BoxParticle(new Box(box.minPos, box.maxPos, deathColor), s, yChange);
		}
		
		public BoxWithLife(Box box, double s, float yChange) { this(box, s, yChange, box.color); }
		
		@Override
		public void debugRender(DebugRenderer renderer) { renderer.renderBox(this.box); }
		
		@Override
		public void close()
		{
			makeParticle(new BoxParticle(this.particleOnClose.getBox(), System.nanoTime(), this.particleOnClose.durationInMs, this.particleOnClose.yChange));
			unregister(this, null);
		}
		
	}
	
	
	
	private static class RendererLists
	{
		public final LinkedList<WeakReference<IDebugRenderable>> generalRenderableList = new LinkedList<>();
		
		private final HashMap<ConfigEntry<Boolean>, LinkedList<WeakReference<IDebugRenderable>>> renderableListByConfig = new HashMap<>();
		
		
		
		// registration //
		
		public void addRenderable(IDebugRenderable renderable, @Nullable ConfigEntry<Boolean> config)
		{
			synchronized (this)
			{
				if (config != null)
				{
					if (!this.renderableListByConfig.containsKey(config))
					{
						this.renderableListByConfig.put(config, new LinkedList<>());
					}
					
					LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
					renderableList.add(new WeakReference<>(renderable));
				}
				else
				{
					this.generalRenderableList.add(new WeakReference<>(renderable));
				}
			}
		}
		
		public void removeRenderable(IDebugRenderable renderable, @Nullable ConfigEntry<Boolean> config)
		{
			synchronized (this)
			{
				if (config != null)
				{
					if (this.renderableListByConfig.containsKey(config))
					{
						LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
						this.removeRenderableFromInternalList(renderableList, renderable);	
					}
				}
				else
				{
					this.removeRenderableFromInternalList(this.generalRenderableList, renderable);
				}
			}
		}
		private void removeRenderableFromInternalList(LinkedList<WeakReference<IDebugRenderable>> rendererList, IDebugRenderable renderable)
		{
			Iterator<WeakReference<IDebugRenderable>> iterator = rendererList.iterator();
			while (iterator.hasNext())
			{
				WeakReference<IDebugRenderable> renderableRef = iterator.next();
				if (renderableRef.get() == null)
				{
					iterator.remove();
					continue;
				}
				
				if (renderableRef.get() == renderable)
				{
					iterator.remove();
					return;
				}
			}
		}
		
		public void clearRenderables()
		{
			for (ConfigEntry<Boolean> config : this.renderableListByConfig.keySet())
			{
				LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
				if (config.get() && renderableList != null)
				{
					renderableList.clear();
				}
			}
		}
		
		
		
		// rendering //
		
		public void render(DebugRenderer debugRenderer)
		{
			this.renderList(debugRenderer, this.generalRenderableList);
			
			for (ConfigEntry<Boolean> config : this.renderableListByConfig.keySet())
			{
				LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
				if (config.get() && renderableList != null && renderableList.size() != 0)
				{
					this.renderList(debugRenderer, renderableList);
				}
			}
		}
		private void renderList(DebugRenderer debugRenderer, LinkedList<WeakReference<IDebugRenderable>> rendererList)
		{
			synchronized (this)
			{
				try
				{
					Iterator<WeakReference<IDebugRenderable>> iterator = rendererList.iterator();
					while (iterator.hasNext())
					{
						WeakReference<IDebugRenderable> ref = iterator.next();
						IDebugRenderable renderable = ref.get();
						if (renderable == null)
						{
							iterator.remove();
							continue;
						}
						
						renderable.debugRender(debugRenderer);
					}
				}
				catch (Exception e)
				{
					RATE_LIMITED_LOGGER.error("Unexpected Debug renderer error, Error: "+e.getMessage(), e);
				}
			}
		}
	}
	
}

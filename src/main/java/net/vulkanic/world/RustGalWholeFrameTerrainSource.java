package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.occlusion.GraphDirection;
import net.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.joml.Vector3d;

/**
 * CPU-only semantic terrain producer for Rust whole-frame Vulkan.  This owns
 * no GL device, region, command list, or renderer.  It reuses Sodium's pure
 * meshing task solely to turn immutable level snapshots into mesh data, then
 * copies that data through VulkanicGAL and immediately releases the CPU buffer.
 */
public final class RustGalWholeFrameTerrainSource {
	private static volatile boolean wholeFrameSurfaceQueueDrained;
	private static volatile boolean wholeFrameTerrainQueueDrained;
	/** Capture-only state explaining why the explicit CPU source is not yet complete. */
	private static volatile String wholeFrameTerrainQueueSummary = "uninitialized";
    private ClientLevel level;
    private ClonedChunkSectionCache sectionCache;
	private ChunkBuilder workerBuilder;
    private final Long2ObjectOpenHashMap<RenderSection> sections = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet queued = new LongLinkedOpenHashSet();
	private final ArrayDeque<SectionPos> pending = new ArrayDeque<>();
	private final ArrayDeque<SectionPos> propagationPending = new ArrayDeque<>();
	private final LongOpenHashSet propagationQueued = new LongOpenHashSet();
	private final Long2IntOpenHashMap incomingDirections = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap propagatedIncomingDirections = new Long2IntOpenHashMap();
	private final LongOpenHashSet inFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedInFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedPending = new LongOpenHashSet();
	private final LongOpenHashSet unavailableSections = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<CompletedBuild> completedBuilds = new ConcurrentLinkedQueue<>();
	private long lastCameraSection = Long.MIN_VALUE;
    private int buildFrame;

    public void setLevel(ClientLevel level) {
        if (this.level == level) {
            return;
        }
        this.destroy();
        this.level = level;
        if (level != null) {
            this.sectionCache = new ClonedChunkSectionCache(level);
			this.workerBuilder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false);
        }
    }

    public void enqueue(Camera camera, Frustum frustum, int viewportWidth, int viewportHeight) {
        if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()
                || this.level == null || camera == null || frustum == null || this.workerBuilder == null) {
            return;
        }
		SectionPos cameraSection = SectionPos.of(camera.getPosition());
		long cameraKey = cameraSection.asLong();
		if (cameraKey != this.lastCameraSection) {
			wholeFrameSurfaceQueueDrained = false;
			wholeFrameTerrainQueueDrained = false;
			this.lastCameraSection = cameraKey;
			this.pending.clear();
			this.queued.clear();
			this.propagationPending.clear();
			this.propagationQueued.clear();
			this.incomingDirections.clear();
			this.propagatedIncomingDirections.clear();
			this.unavailableSections.clear();
			int horizontalRadius = this.configuredHorizontalRadius();
			this.evictOutsideWindow(cameraSection, horizontalRadius);
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
		} else {
			// Revisit the origin's completed CPU section so a transiently unavailable
			// camera section can never be mistaken for a settled terrain source.
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
		}
		this.admitInvalidatedSections(frustum);
		this.retryUnavailableSections(frustum);
		this.drainCompletedBuilds(frustum);
		this.drainVisibilityFrontier(frustum);
		this.scheduleBuilds(camera);
		this.sectionCache.cleanup();
		wholeFrameSurfaceQueueDrained = this.pending.isEmpty() && this.inFlight.isEmpty();
		wholeFrameTerrainQueueDrained = wholeFrameSurfaceQueueDrained
			&& this.propagationPending.isEmpty()
			&& this.completedBuilds.isEmpty()
			&& this.unavailableSections.isEmpty();
		wholeFrameTerrainQueueSummary = "pending=" + this.pending.size()
			+ ",frontier=" + this.propagationPending.size()
			+ ",inFlight=" + this.inFlight.size()
			+ ",completed=" + this.completedBuilds.size()
			+ ",retained=" + this.sections.size()
			+ ",unavailable=" + this.unavailableSections.size()
			+ ",drained=" + wholeFrameTerrainQueueDrained;
		var visibleSections = new ArrayList<RenderSection>(this.sections.size());
		for (RenderSection section : this.sections.values()) {
			if (this.isVisible(section.getPosition(), frustum)) {
				visibleSections.add(section);
			}
		}
		RustGalTerrainRenderer.enqueueWholeFrameTerrainSections(visibleSections, camera, viewportWidth, viewportHeight);
    }

	/** True once all client-resident nearby surface sections have been attempted. */
	public static boolean isWholeFrameSurfaceQueueDrained() {
		return wholeFrameSurfaceQueueDrained;
	}

	/**
	 * True only after every currently visible nearby section—surface and
	 * vertical—has either produced a retained CPU mesh or completed as empty.
	 * Deterministic parity capture uses this stronger condition so it never
	 * presents a partial semantic terrain set as a settled frame.
	 */
	public static boolean isWholeFrameTerrainQueueDrained() {
		return wholeFrameTerrainQueueDrained;
	}

	/** Diagnostic companion to {@link #isWholeFrameTerrainQueueDrained()}. */
	public static String wholeFrameTerrainQueueSummary() {
		return wholeFrameTerrainQueueSummary;
	}

    public void invalidate(int sectionX, int sectionY, int sectionZ) {
        if (this.level == null || this.sectionCache == null) {
            return;
        }
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        this.sectionCache.invalidate(sectionX, sectionY, sectionZ);
        RustGalTerrainRenderer.removeSection(sectionX, sectionY, sectionZ, "cpu-source-dirty");
        this.sections.remove(key);
		this.propagatedIncomingDirections.remove(key);
		this.unavailableSections.remove(key);
		SectionPos section = SectionPos.of(sectionX, sectionY, sectionZ);
		if (this.inFlight.contains(key)) {
			// The immutable snapshot may still be meshing on a worker. Suppress its
			// stale result and schedule a replacement once that worker completes.
			this.invalidatedInFlight.add(key);
			this.invalidatedPending.add(key);
		} else {
			this.invalidatedPending.add(key);
		}
    }

	public void destroy() {
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
		wholeFrameTerrainQueueSummary = "destroyed";
		if (this.workerBuilder != null) {
			this.workerBuilder.shutdown();
		}
		this.workerBuilder = null;
        this.sectionCache = null;
        this.sections.clear();
        this.queued.clear();
		this.pending.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
		this.inFlight.clear();
		this.invalidatedInFlight.clear();
		this.invalidatedPending.clear();
		this.unavailableSections.clear();
		this.destroyCompletedBuilds();
		this.lastCameraSection = Long.MIN_VALUE;
        this.buildFrame = 0;
        this.level = null;
    }

	/** Targets the configured client view distance without a fixed bootstrap cap. */
	private int configuredHorizontalRadius() {
		return Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance());
	}

	/**
	 * Starts at the camera section and expands through the immutable portal
	 * visibility data generated by the same CPU mesher used for the semantic
	 * terrain payload. This deliberately avoids Sodium's OpenGL-owned render
	 * lists while avoiding the raw-frustum vertical-volume over-admission.
	 */
	private void admitSection(SectionPos section, int incoming, boolean origin, Frustum frustum) {
		if (!this.isCandidate(section, frustum)) {
			return;
		}
		long key = section.asLong();
		int previousIncoming = this.incomingDirections.get(key);
		int nextIncoming = previousIncoming | incoming;
		if (origin) {
			nextIncoming |= 1 << GraphDirection.COUNT;
		}
		if (nextIncoming != previousIncoming) {
			this.incomingDirections.put(key, nextIncoming);
		}
		if (this.sections.containsKey(key)) {
			this.requestPropagation(key);
			return;
		}
		if (!this.inFlight.contains(key) && this.queued.add(key)) {
			this.pending.addLast(section);
		}
	}

	private boolean isCandidate(SectionPos section, Frustum frustum) {
		return section != null
			&& !this.level.isOutsideBuildHeight(section.minBlockY())
			&& this.level.hasChunk(section.getX(), section.getZ())
			&& this.isInsideCurrentWindow(section)
			&& this.isVisible(section, frustum);
	}

	private void requestPropagation(long key) {
		if (this.propagationQueued.add(key)) {
			this.propagationPending.addLast(SectionPos.of(key));
		}
	}

	private void admitInvalidatedSections(Frustum frustum) {
		var iterator = this.invalidatedPending.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			if (!this.isCandidate(section, frustum)) {
				continue;
			}
			this.unavailableSections.remove(key);
			this.admitSection(section, GraphDirectionSet.NONE, false, frustum);
			iterator.remove();
		}
	}

	/**
	 * A cloned section can be temporarily unavailable while an adjacent client
	 * chunk finishes arriving. Keep that condition visible to readiness, then
	 * re-admit the exact same semantic frontier node once its snapshot becomes
	 * available; never replace it with an empty or surface-only substitute.
	 */
	private void retryUnavailableSections(Frustum frustum) {
		var iterator = this.unavailableSections.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			if (!this.isInsideCurrentWindow(section) || !this.isVisible(section, frustum)) {
				iterator.remove();
				continue;
			}
			if (!this.level.hasChunk(section.getX(), section.getZ())
				|| this.sections.containsKey(key)
				|| this.inFlight.contains(key)
				|| !this.queued.add(key)) {
				continue;
			}
			this.pending.addLast(section);
		}
	}

	private void drainVisibilityFrontier(Frustum frustum) {
		SectionPos sectionPos;
		while ((sectionPos = this.propagationPending.pollFirst()) != null) {
			long key = sectionPos.asLong();
			this.propagationQueued.remove(key);
			RenderSection section = this.sections.get(key);
			if (section == null || !section.isBuilt()) {
				continue;
			}
			int incoming = this.incomingDirections.get(key);
			int alreadyPropagated = this.propagatedIncomingDirections.get(key);
			if (incoming == alreadyPropagated) {
				continue;
			}
			this.propagatedIncomingDirections.put(key, incoming);
			int directionMask = GraphDirectionSet.ALL;
			int outgoing = 0;
			int originBit = 1 << GraphDirection.COUNT;
			if ((incoming & originBit) != 0) {
				outgoing |= OcclusionCuller.getVisibilityConnections(
					section.getVisibilityData(), GraphDirectionSet.NONE, false
				);
			}
			int incomingDirections = incoming & directionMask;
			if (incomingDirections != GraphDirectionSet.NONE) {
				outgoing |= OcclusionCuller.getVisibilityConnections(
					section.getVisibilityData(), incomingDirections, true
				);
			}
			// Mirror Sodium's CPU occlusion traversal: once a portal path has
			// moved away from the camera section, never walk it back toward the
			// camera. Besides preventing redundant graph walks, this is essential
			// to preserve Sodium's visible-section domain without touching its GL
			// render lists or render device.
			outgoing &= this.outwardDirections(sectionPos);
			for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
				if (!GraphDirectionSet.contains(outgoing, direction)) {
					continue;
				}
				SectionPos neighbor = SectionPos.of(
					sectionPos.getX() + GraphDirection.x(direction),
					sectionPos.getY() + GraphDirection.y(direction),
					sectionPos.getZ() + GraphDirection.z(direction)
				);
				this.admitSection(neighbor, GraphDirectionSet.of(GraphDirection.opposite(direction)), false, frustum);
			}
		}
	}

	private int outwardDirections(SectionPos section) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return GraphDirectionSet.NONE;
		}
		int originX = SectionPos.x(this.lastCameraSection);
		int originY = SectionPos.y(this.lastCameraSection);
		int originZ = SectionPos.z(this.lastCameraSection);
		int directions = GraphDirectionSet.NONE;
		directions |= section.getX() <= originX ? 1 << GraphDirection.WEST : 0;
		directions |= section.getX() >= originX ? 1 << GraphDirection.EAST : 0;
		directions |= section.getY() <= originY ? 1 << GraphDirection.DOWN : 0;
		directions |= section.getY() >= originY ? 1 << GraphDirection.UP : 0;
		directions |= section.getZ() <= originZ ? 1 << GraphDirection.NORTH : 0;
		directions |= section.getZ() >= originZ ? 1 << GraphDirection.SOUTH : 0;
		return directions;
	}

	private void evictOutsideWindow(SectionPos center, int horizontalRadius) {
		var iterator = this.sections.long2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			RenderSection section = entry.getValue();
			if (section == null) {
				iterator.remove();
				continue;
			}
			SectionPos position = section.getPosition();
			if (Math.abs(position.getX() - center.getX()) <= horizontalRadius
					&& Math.abs(position.getZ() - center.getZ()) <= horizontalRadius) {
				continue;
			}
			RustGalTerrainRenderer.removeSection(position.getX(), position.getY(), position.getZ(), "cpu-source-window-evicted");
			this.incomingDirections.remove(entry.getLongKey());
			this.propagatedIncomingDirections.remove(entry.getLongKey());
			this.unavailableSections.remove(entry.getLongKey());
			iterator.remove();
		}
	}

	private boolean isVisible(SectionPos section, Frustum frustum) {
		return frustum.isVisible(new AABB(
			section.minBlockX(), section.minBlockY(), section.minBlockZ(),
			section.maxBlockX() + 1, section.maxBlockY() + 1, section.maxBlockZ() + 1
		));
	}

	private void scheduleBuilds(Camera camera) {
		int capacity = Math.max(1, this.workerBuilder.getTotalThreadCount() * 2);
		while (this.inFlight.size() < capacity) {
			SectionPos sectionPos = this.pending.pollFirst();
			if (sectionPos == null) {
				return;
			}
			this.queued.remove(sectionPos.asLong());
			this.scheduleBuild(sectionPos, camera);
		}
	}

	private void scheduleBuild(SectionPos sectionPos, Camera camera) {
		long key = sectionPos.asLong();
		if (!this.inFlight.add(key)) {
			return;
		}
        ChunkRenderContext renderContext;
        try {
            renderContext = LevelSlice.prepare(this.level, sectionPos, this.sectionCache);
        } catch (RuntimeException ignored) {
			this.completeUnavailableBuild(sectionPos);
            return;
        }
        if (renderContext == null) {
			// LevelSlice uses null to report an empty section, not a missing
			// snapshot. Preserve its semantic all-open visibility so the portal
			// frontier can continue through air without falsely blocking capture.
			this.completeEmptyBuild(sectionPos);
            return;
        }
        RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
        Vec3 cameraPos = camera.getPosition();
        ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(section, ++this.buildFrame,
                new Vector3d(cameraPos.x(), cameraPos.y(), cameraPos.z()), renderContext, SortBehavior.OFF, false);
		this.workerBuilder.scheduleTask(task, true, result -> this.completedBuilds.add(new CompletedBuild(sectionPos, section, result)), false);
    }

	private void drainCompletedBuilds(Frustum frustum) {
		CompletedBuild completed;
		while ((completed = this.completedBuilds.poll()) != null) {
			long key = completed.sectionPos().asLong();
			this.inFlight.remove(key);
			boolean insideCurrentWindow = this.isInsideCurrentWindow(completed.sectionPos());
			boolean stale = this.invalidatedInFlight.remove(key) || !insideCurrentWindow;
			ChunkBuildOutput output;
			try {
				output = completed.result().unwrap();
			} catch (RuntimeException ignored) {
				this.unavailableSections.add(key);
				continue;
			}
			if (output == null) {
				this.unavailableSections.add(key);
				continue;
			}
			try {
				if (stale) {
					continue;
				}
				completed.section().setInfo(output.info);
				this.sections.put(key, completed.section());
				if (!output.meshes.isEmpty()) {
					RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(output);
				}
				this.unavailableSections.remove(key);
				this.requestPropagation(key);
			} finally {
				output.destroy();
			}
		}
	}

	private boolean isInsideCurrentWindow(SectionPos section) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return false;
		}
		int horizontalRadius = this.configuredHorizontalRadius();
		return Math.abs(section.getX() - SectionPos.x(this.lastCameraSection)) <= horizontalRadius
			&& Math.abs(section.getZ() - SectionPos.z(this.lastCameraSection)) <= horizontalRadius;
	}

	private void completeUnavailableBuild(SectionPos sectionPos) {
		long key = sectionPos.asLong();
		this.inFlight.remove(key);
		this.unavailableSections.add(key);
	}

	private void completeEmptyBuild(SectionPos sectionPos) {
		long key = sectionPos.asLong();
		this.inFlight.remove(key);
		RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		section.setInfo(BuiltSectionInfo.EMPTY);
		this.sections.put(key, section);
		this.unavailableSections.remove(key);
		this.requestPropagation(key);
	}

	private void destroyCompletedBuilds() {
		CompletedBuild completed;
		while ((completed = this.completedBuilds.poll()) != null) {
			try {
				ChunkBuildOutput output = completed.result().unwrap();
				if (output != null) {
					output.destroy();
				}
			} catch (RuntimeException ignored) {
				// Worker failure is already contained by the source's unavailable result path.
			}
		}
	}

	private record CompletedBuild(SectionPos sectionPos, RenderSection section, ChunkJobResult<ChunkBuildOutput> result) {
	}

}

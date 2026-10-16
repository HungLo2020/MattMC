package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jetbrains.annotations.Nullable;

public class LevelLightEngine implements LightEventListener {
	public static final int LIGHT_SECTION_PADDING = 1;
	public static final LevelLightEngine EMPTY = new LevelLightEngine();
	protected final LevelHeightAccessor levelHeightAccessor;
	@Nullable
	private final LightEngine<?, ?> blockEngine;
	@Nullable
	private final LightEngine<?, ?> skyEngine;
    @Nullable private final NativeLevelLighting nativeLevel;

	public LevelLightEngine(LightChunkGetter lightChunkGetter, boolean bl, boolean bl2) {
		this.levelHeightAccessor = lightChunkGetter.getLevel();
        NativeLightBindings bindings = bl || bl2 ? new NativeLightBindings() : null;
		this.blockEngine = bl ? BlockLightEngine.withBindings(lightChunkGetter, bindings) : null;
		this.skyEngine = bl2 ? SkyLightEngine.withBindings(lightChunkGetter, bindings) : null;
        this.nativeLevel = bl || bl2 ? NativeLevelLighting.create(this.blockEngine, this.skyEngine) : null;
	}

	private LevelLightEngine() {
		this.levelHeightAccessor = LevelHeightAccessor.create(0, 0);
		this.blockEngine = null;
		this.skyEngine = null;
        this.nativeLevel = null;
	}

	@Override
	public void checkBlock(BlockPos blockPos) {
        if (this.nativeLevel != null && getClass() == LevelLightEngine.class && blockPos != null
                && blockPos.getClass() == BlockPos.class && this.nativeLevel.matches(this.blockEngine, this.skyEngine)) {
            this.nativeLevel.check(blockPos.asLong());
            return;
        }
		if (this.blockEngine != null) {
			this.blockEngine.checkBlock(blockPos);
		}

		if (this.skyEngine != null) {
			this.skyEngine.checkBlock(blockPos);
		}
	}

    /** Borrowed actual allocation; typed task admission retains it immediately. */
    protected final java.lang.foreign.MemorySegment nativeLevelOwnerForTasks() {
        return this.nativeLevel != null && this.nativeLevel.matches(this.blockEngine, this.skyEngine)
            ? this.nativeLevel.ownerForTasks() : java.lang.foreign.MemorySegment.NULL;
    }
    protected final void applyNativeLightImport(java.lang.foreign.MemorySegment tasks,long cursor,int target,DataLayer source) { this.nativeLevel.applyImport(tasks,cursor,target,source); }
    protected final void resumeNativeLightContext(java.lang.foreign.MemorySegment tasks,long cursor){this.nativeLevel.resumeTask(tasks,cursor);}

	@Override
	public boolean hasLightWork() {
        if(this.nativeLevel!=null&&this.nativeLevel.matches(this.blockEngine,this.skyEngine))return this.nativeLevel.hasWork();
		return this.skyEngine != null && this.skyEngine.hasLightWork() ? true : this.blockEngine != null && this.blockEngine.hasLightWork();
	}

	@Override
	public int runLightUpdates() {
        if(this.nativeLevel!=null&&this.nativeLevel.matches(this.blockEngine,this.skyEngine))return this.nativeLevel.runUpdates();
		int i = 0;
		if (this.blockEngine != null) {
			i += this.blockEngine.runLightUpdates();
		}

		if (this.skyEngine != null) {
			i += this.skyEngine.runLightUpdates();
		}

		return i;
	}

	@Override
	public void updateSectionStatus(SectionPos sectionPos, boolean bl) {
        if(this.nativeLevel!=null&&sectionPos!=null&&sectionPos.getClass()==SectionPos.class
                &&this.nativeLevel.matches(this.blockEngine,this.skyEngine)){
            this.nativeLevel.updateStatus(sectionPos.asLong(),bl);return;
        }
		if (this.blockEngine != null) {
			this.blockEngine.updateSectionStatus(sectionPos, bl);
		}

		if (this.skyEngine != null) {
			this.skyEngine.updateSectionStatus(sectionPos, bl);
		}
	}

	@Override
	public void setLightEnabled(ChunkPos chunkPos, boolean bl) {
        if(this.nativeLevel!=null&&chunkPos!=null&&chunkPos.getClass()==ChunkPos.class
                &&this.nativeLevel.matches(this.blockEngine,this.skyEngine)){
            this.nativeLevel.command(1,ChunkPos.asLong(chunkPos.x,chunkPos.z),bl);return;
        }
		if (this.blockEngine != null) {
			this.blockEngine.setLightEnabled(chunkPos, bl);
		}

		if (this.skyEngine != null) {
			this.skyEngine.setLightEnabled(chunkPos, bl);
		}
	}

	@Override
	public void propagateLightSources(ChunkPos chunkPos) {
        if(this.nativeLevel!=null&&chunkPos!=null&&chunkPos.getClass()==ChunkPos.class
                &&this.nativeLevel.matches(this.blockEngine,this.skyEngine)){
            this.nativeLevel.command(2,ChunkPos.asLong(chunkPos.x,chunkPos.z),false);return;
        }
		if (this.blockEngine != null) {
			this.blockEngine.propagateLightSources(chunkPos);
		}

		if (this.skyEngine != null) {
			this.skyEngine.propagateLightSources(chunkPos);
		}
	}

	public LayerLightEventListener getLayerListener(LightLayer lightLayer) {
		if (lightLayer == LightLayer.BLOCK) {
			return (LayerLightEventListener)(this.blockEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.blockEngine);
		} else {
			return (LayerLightEventListener)(this.skyEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.skyEngine);
		}
	}

	public String getDebugData(LightLayer lightLayer, SectionPos sectionPos) {
		if (lightLayer == LightLayer.BLOCK) {
			if (this.blockEngine != null) {
				return this.blockEngine.getDebugData(sectionPos.asLong());
			}
		} else if (this.skyEngine != null) {
			return this.skyEngine.getDebugData(sectionPos.asLong());
		}

		return "n/a";
	}

	public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer lightLayer, SectionPos sectionPos) {
		if (lightLayer == LightLayer.BLOCK) {
			if (this.blockEngine != null) {
				return this.blockEngine.getDebugSectionType(sectionPos.asLong());
			}
		} else if (this.skyEngine != null) {
			return this.skyEngine.getDebugSectionType(sectionPos.asLong());
		}

		return LayerLightSectionStorage.SectionType.EMPTY;
	}

	public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
		if (lightLayer == LightLayer.BLOCK) {
			if (this.blockEngine != null) {
				this.blockEngine.queueSectionData(sectionPos.asLong(), dataLayer);
			}
		} else if (this.skyEngine != null) {
			this.skyEngine.queueSectionData(sectionPos.asLong(), dataLayer);
		}
	}

	public void retainData(ChunkPos chunkPos, boolean bl) {
		if (this.blockEngine != null) {
			this.blockEngine.retainData(chunkPos, bl);
		}

		if (this.skyEngine != null) {
			this.skyEngine.retainData(chunkPos, bl);
		}
	}

	public int getRawBrightness(BlockPos blockPos, int i) {
		int j = this.skyEngine == null ? 0 : this.skyEngine.getLightValue(blockPos) - i;
		int k = this.blockEngine == null ? 0 : this.blockEngine.getLightValue(blockPos);
		return Math.max(k, j);
	}

	public boolean lightOnInColumn(long l) {
		return this.blockEngine == null || this.blockEngine.storage.lightOnInColumn(l) && (this.skyEngine == null || this.skyEngine.storage.lightOnInColumn(l));
	}

	public int getLightSectionCount() {
		return this.levelHeightAccessor.getSectionsCount() + 2;
	}

	public int getMinLightSection() {
		return this.levelHeightAccessor.getMinSectionY() - 1;
	}

	public int getMaxLightSection() {
		return this.getMinLightSection() + this.getLightSectionCount();
	}
}

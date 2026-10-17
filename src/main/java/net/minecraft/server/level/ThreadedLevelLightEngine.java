package net.minecraft.server.level;

import net.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ThreadedLevelLightEngine extends LevelLightEngine implements AutoCloseable {
	public static final int DEFAULT_BATCH_SIZE = 1000;
	private static final Logger LOGGER = LogUtils.getLogger();
	private final ConsecutiveExecutor consecutiveExecutor;
	private final NativeLightTasks lightTasks = new NativeLightTasks();
	private final Runnable propagation = super::runLightUpdates;
	private final ChunkMap chunkMap;
	private final ChunkTaskDispatcher taskDispatcher;

	public ThreadedLevelLightEngine(
		LightChunkGetter lightChunkGetter, ChunkMap chunkMap, boolean bl, ConsecutiveExecutor consecutiveExecutor, ChunkTaskDispatcher chunkTaskDispatcher
	) {
		super(lightChunkGetter, true, bl);
		this.chunkMap = chunkMap;
		this.taskDispatcher = chunkTaskDispatcher;
		this.consecutiveExecutor = consecutiveExecutor;
	}

	public void close() {
	}

	@Override
	public int runLightUpdates() {
		throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Ran automatically on a different thread!"));
	}

	@Override
	public void checkBlock(BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.immutable();
        if(!SharedConstants.DEBUG_NAMED_RUNNABLES&&getClass()==ThreadedLevelLightEngine.class
                &&(blockPos.getClass()==BlockPos.class||blockPos.getClass()==BlockPos.MutableBlockPos.class)
                &&blockPos2!=null&&blockPos2.getClass()==BlockPos.class){
            java.lang.foreign.MemorySegment level=this.nativeLevelOwnerForTasks();
            if(level.address()!=0){
                int x=SectionPos.blockToSectionCoord(blockPos.getX()),z=SectionPos.blockToSectionCoord(blockPos.getZ());
                long packed=blockPos2.asLong();
                IntSupplier priority=this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(x,z));
                this.taskDispatcher.submit(()->{
                    if(this.lightTasks.appendCheck(level,packed,this,blockPos2))this.runUpdate();
                },ChunkPos.asLong(x,z),priority);
                return;
            }
        }
		this.addTask(
			SectionPos.blockToSectionCoord(blockPos.getX()),
			SectionPos.blockToSectionCoord(blockPos.getZ()),
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.checkBlock(blockPos2)), () -> "checkBlock " + blockPos2)
		);
	}

	protected void updateChunkStatus(ChunkPos chunkPos) {
        LightRequest request=getClass()==ThreadedLevelLightEngine.class?nativeLightRequest(2,null,chunkPos,false,LOGGER):null;
        if(request!=null){
            this.addTask(chunkPos.x,chunkPos.z,()->0,TaskType.PRE_UPDATE,Util.name((Runnable)request::pre,()->"updateChunkStatus "+chunkPos+" true"));return;
        }
		this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			super.retainData(chunkPos, false);
			super.setLightEnabled(chunkPos, false);

			for (int i = this.getMinLightSection(); i < this.getMaxLightSection(); i++) {
				super.queueSectionData(LightLayer.BLOCK, SectionPos.of(chunkPos, i), null);
				super.queueSectionData(LightLayer.SKY, SectionPos.of(chunkPos, i), null);
			}

			for (int i = this.levelHeightAccessor.getMinSectionY(); i <= this.levelHeightAccessor.getMaxSectionY(); i++) {
				super.updateSectionStatus(SectionPos.of(chunkPos, i), true);
			}
		}), () -> "updateChunkStatus " + chunkPos + " true"));
	}

	@Override
	public void updateSectionStatus(SectionPos sectionPos, boolean bl) {
        if(!SharedConstants.DEBUG_NAMED_RUNNABLES&&getClass()==ThreadedLevelLightEngine.class
                &&sectionPos!=null&&sectionPos.getClass()==SectionPos.class){
            java.lang.foreign.MemorySegment level=this.nativeLevelOwnerForTasks();
            if(level.address()!=0){
                int x=sectionPos.x(),z=sectionPos.z();long key=sectionPos.asLong();
                this.taskDispatcher.submit(()->{
                    if(this.lightTasks.appendStatus(level,key,bl,this,sectionPos))this.runUpdate();
                },ChunkPos.asLong(x,z),()->0);return;
            }
        }
		this.addTask(
			sectionPos.x(),
			sectionPos.z(),
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.updateSectionStatus(sectionPos, bl)), () -> "updateSectionStatus " + sectionPos + " " + bl)
		);
	}

	@Override
	public void propagateLightSources(ChunkPos chunkPos) {
        if(this.appendNativeLevelCommand(chunkPos,2,false))return;
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.propagateLightSources(chunkPos)), () -> "propagateLight " + chunkPos)
		);
	}

	@Override
	public void setLightEnabled(ChunkPos chunkPos, boolean bl) {
        if(this.appendNativeLevelCommand(chunkPos,1,bl))return;
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.setLightEnabled(chunkPos, bl)), () -> "enableLight " + chunkPos + " " + bl)
		);
	}

    private boolean appendNativeLevelCommand(ChunkPos position,int kind,boolean value){
        if(SharedConstants.DEBUG_NAMED_RUNNABLES||getClass()!=ThreadedLevelLightEngine.class||position==null||position.getClass()!=ChunkPos.class)return false;
        java.lang.foreign.MemorySegment level=this.nativeLevelOwnerForTasks();if(level.address()==0)return false;
        long key=ChunkPos.asLong(position.x,position.z);
        IntSupplier priority=this.chunkMap.getChunkQueueLevel(key);
        this.taskDispatcher.submit(()->{if(this.lightTasks.appendLevel(level,kind,key,value,this,position))this.runUpdate();},key,priority);
        return true;
    }

	@Override
	public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
        if(!SharedConstants.DEBUG_NAMED_RUNNABLES&&getClass()==ThreadedLevelLightEngine.class
                &&sectionPos!=null&&sectionPos.getClass()==SectionPos.class
                &&(dataLayer==null||dataLayer.getClass()==DataLayer.class)){
            java.lang.foreign.MemorySegment level=this.nativeLevelOwnerForTasks();
            if(level.address()!=0){
                int x=sectionPos.x(),z=sectionPos.z(),channel=lightLayer==null?-1:lightLayer.ordinal();
                long key=sectionPos.asLong();
                this.taskDispatcher.submit(()->{
                    if(this.lightTasks.appendImport(level,channel,key,this,sectionPos,dataLayer))this.runUpdate();
                },ChunkPos.asLong(x,z),()->0);
                return;
            }
        }
		this.addTask(
			sectionPos.x(),
			sectionPos.z(),
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.queueSectionData(lightLayer, sectionPos, dataLayer)), () -> "queueData " + sectionPos)
		);
	}

	private void addTask(int i, int j, ThreadedLevelLightEngine.TaskType taskType, Runnable runnable) {
		this.addTask(i, j, this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(i, j)), taskType, runnable);
	}

	private void addTask(int i, int j, IntSupplier intSupplier, ThreadedLevelLightEngine.TaskType taskType, Runnable runnable) {
		this.taskDispatcher.submit(() -> {
			if (this.lightTasks.append(taskType == TaskType.POST_UPDATE, runnable)) {
				this.runUpdate();
			}
		}, ChunkPos.asLong(i, j), intSupplier);
	}

	@Override
	public void retainData(ChunkPos chunkPos, boolean bl) {
        if(!SharedConstants.DEBUG_NAMED_RUNNABLES&&getClass()==ThreadedLevelLightEngine.class
                &&chunkPos!=null&&chunkPos.getClass()==ChunkPos.class){
            java.lang.foreign.MemorySegment level=this.nativeLevelOwnerForTasks();
            if(level.address()!=0){
                long column=SectionPos.getZeroNode(chunkPos.x,chunkPos.z);
                this.taskDispatcher.submit(()->{
                    if(this.lightTasks.appendRetain(level,column,bl,this,chunkPos))this.runUpdate();
                },ChunkPos.asLong(chunkPos.x,chunkPos.z),()->0);
                return;
            }
        }
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.retainData(chunkPos, bl)), () -> "retainData " + chunkPos)
		);
	}

    void applyNativeImport(java.lang.foreign.MemorySegment owner,long cursor,int target,DataLayer source){this.applyNativeLightImport(owner,cursor,target,source);}
    void resumeNativeContext(java.lang.foreign.MemorySegment owner,long cursor){this.resumeNativeLightContext(owner,cursor);}

	public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunkAccess, boolean bl) {
		ChunkPos chunkPos = chunkAccess.getPos();
        LightRequest request=getClass()==ThreadedLevelLightEngine.class?nativeLightRequest(0,chunkAccess,chunkPos,bl,LOGGER):null;
        if(request!=null){
            this.addTask(chunkPos.x,chunkPos.z,TaskType.PRE_UPDATE,Util.name((Runnable)request::pre,()->"initializeLight: "+chunkPos));
            Runnable completion=request.postTask(chunkAccess);
            return CompletableFuture.supplyAsync(()->{completion.run();return chunkAccess;},runnable->this.addTask(chunkPos.x,chunkPos.z,TaskType.POST_UPDATE,runnable));
        }
		this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			LevelChunkSection[] levelChunkSections = chunkAccess.getSectionsForRead();

			for (int i = 0; i < chunkAccess.getSectionsCount(); i++) {
				LevelChunkSection levelChunkSection = levelChunkSections[i];
				if (!levelChunkSection.hasOnlyAir()) {
					int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
					super.updateSectionStatus(SectionPos.of(chunkPos, j), false);
				}
			}
		}), () -> "initializeLight: " + chunkPos));
		return CompletableFuture.supplyAsync(() -> {
			super.setLightEnabled(chunkPos, bl);
			super.retainData(chunkPos, false);
			return chunkAccess;
		}, runnable -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunkAccess, boolean bl) {
		ChunkPos chunkPos = chunkAccess.getPos();
		chunkAccess.setLightCorrect(false);
        LightRequest request=getClass()==ThreadedLevelLightEngine.class?nativeLightRequest(1,chunkAccess,chunkPos,bl,LOGGER):null;
        if(request!=null){
            this.addTask(chunkPos.x,chunkPos.z,TaskType.PRE_UPDATE,Util.name((Runnable)request::pre,()->"lightChunk "+chunkPos+" "+bl));
            Runnable completion=request.postTask(chunkAccess);
            return CompletableFuture.supplyAsync(()->{completion.run();return chunkAccess;},runnable->this.addTask(chunkPos.x,chunkPos.z,TaskType.POST_UPDATE,runnable));
        }
		this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			if (!bl) {
				super.propagateLightSources(chunkPos);
			}

			if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
				LOGGER.debug("LIT {}", chunkPos);
			}
		}), () -> "lightChunk " + chunkPos + " " + bl));
		return CompletableFuture.supplyAsync(() -> {
			chunkAccess.setLightCorrect(true);
			return chunkAccess;
		}, runnable -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	public void tryScheduleUpdate() {
		if (this.lightTasks.requestSchedule(this.lightTasks.pending() != 0 || super.hasLightWork())) {
			this.consecutiveExecutor.schedule(() -> {
				this.runUpdate();
				this.lightTasks.finishSchedule();
			});
		}
	}

	private void runUpdate() {
        java.lang.foreign.MemorySegment level=getClass()==ThreadedLevelLightEngine.class?this.nativeLevelOwnerForTasks():java.lang.foreign.MemorySegment.NULL;
        this.lightTasks.runUpdate(this.propagation,level,level.address()==0?null:this);
	}

	public CompletableFuture<?> waitForPendingTasks(int i, int j) {
		return CompletableFuture.runAsync(() -> {}, runnable -> this.addTask(i, j, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	static enum TaskType {
		PRE_UPDATE,
		POST_UPDATE;
	}
}

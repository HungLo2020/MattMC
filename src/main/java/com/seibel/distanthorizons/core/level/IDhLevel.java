package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.repo.BeaconBeamRepo;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A DH Level handles all DH-centric logic related to a given MC level.
 * A Level in this context is defined as a Minecraft dimension the player can play in
 * (IE the overworld, nether, end, etc.). <br><br>
 * 
 * This is different from a {@link ILevelWrapper}
 * in the following ways: <br>
 * - a DH level is created after a MC level is wrapped and passed into the {@link ClientApi} or {@link ServerApi} respectively <br>
 * - a DH level doesn't handle any MC level logic (IE getting the min/max world height) <br>
 * - a DH level keeps track of DH's database file paths and rendering <br>
 * 
 * @see ILevelWrapper
 * @see IDhClientLevel
 * @see IDhServerLevel
 */
public interface IDhLevel extends AutoCloseable, GeneratedFullDataSourceProvider.IOnWorldGenCompleteListener
{
	/**
	 * May return either a client or server level wrapper. <br>
	 * Should not return null
	 */
	@NotNull
	ILevelWrapper getLevelWrapper();
	
	/** @return 0 if no hash is known */
	int getChunkHash(DhChunkPos pos);
	void updateChunkAsync(IChunkWrapper chunk, int newChunkHash);
	
	default void updateBeaconBeamsForChunk(IChunkWrapper chunkToUpdate, ArrayList<IChunkWrapper> nearbyChunkList)
	{
		List<BeaconBeamDTO> activeBeamList = chunkToUpdate.getAllActiveBeacons(nearbyChunkList);
		this.updateBeaconBeamsForChunkPos(chunkToUpdate.getChunkPos(), activeBeamList);
	}
	void updateBeaconBeamsForChunkPos(DhChunkPos chunkPos, List<BeaconBeamDTO> activeBeamList);
	void updateBeaconBeamsForSectionPos(long sectionPos, List<BeaconBeamDTO> activeBeamList);
	
	/** @return null on server-only levels */
	@Nullable
	BeaconBeamRepo getBeaconBeamRepo();
	
	FullDataSourceProviderV2 getFullDataProvider();
	
	ISaveStructure getSaveStructure();
	
	CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data);
	
	void addDebugMenuStringsToList(List<String> messageList);
	
	/** 
	 * Will return null if the renderer isn't set up yet. <br>
	 * Not supported on the server-side. 
	 */
	@Nullable
	GenericObjectRenderer getGenericRenderer();
	/**
	 * Will return null if the renderer isn't set up yet. <br>
	 * Not supported on the server-side. 
	 */
	@Nullable
	RenderBufferHandler getRenderBufferHandler();
	
}

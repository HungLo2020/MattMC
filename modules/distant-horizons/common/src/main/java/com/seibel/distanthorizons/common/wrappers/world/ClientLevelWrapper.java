package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.*;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif

#if MC_VER < MC_1_21_3
import net.minecraft.world.phys.Vec3;
#else
import com.seibel.distanthorizons.core.util.ColorUtil;
#endif

#if MC_VER <= MC_1_21_10
#else
import net.minecraft.world.attribute.EnvironmentAttributes;
#endif


public class ClientLevelWrapper implements IClientLevelWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	/**
	 * weak references are to prevent rare issues
	 * where, upon world closure, some levels aren't shutdown/removed properly
	 * and/or for servers were the level object isn't consistent
	 */
	private static final Map<ClientLevel, WeakReference<ClientLevelWrapper>> LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());
	private static final IKeyedClientLevelManager KEYED_CLIENT_LEVEL_MANAGER = SingletonInjector.INSTANCE.get(IKeyedClientLevelManager.class);
	
	private static final Minecraft MINECRAFT = Minecraft.getInstance();
	
	private final ClientLevel level;
	private final ConcurrentHashMap<BlockState, ClientBlockStateColorCache> blockColorCacheByBlockState = new ConcurrentHashMap<>();
	
	/** cached method reference to reduce GC overhead */
	private final Function<BlockState, ClientBlockStateColorCache> createCachedBlockColorCacheFunc = (blockState) -> new ClientBlockStateColorCache(blockState, this);
	
	
	private BlockStateWrapper dirtBlockWrapper;
	private IDhLevel dhLevel;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected ClientLevelWrapper(ClientLevel level) { this.level = level; }
	
	
	
	//==================//
	// instance methods //
	//==================//
	
	/** 
	 * can be used when speed is important and the same level is likely to be passed in,
	 * IE rendering.
	 */
	@Nullable
	public static IClientLevelWrapper getWrapperIfDifferent(@Nullable IClientLevelWrapper levelWrapper, @NotNull ClientLevel level) // TODO handle null level
	{
		if (KEYED_CLIENT_LEVEL_MANAGER.isEnabled() && KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel() != levelWrapper)
		{
			return getWrapper(level);
		}
		
		ClientLevelWrapper clientLevelWrapper = (ClientLevelWrapper)levelWrapper;
		if (clientLevelWrapper == null
			|| clientLevelWrapper.level != level)
		{
			return getWrapper(level);
		}
		
		return clientLevelWrapper;
	}
	
	@Nullable
	public static IClientLevelWrapper getWrapper(@NotNull ClientLevel level) { return getWrapper(level, false); }
	
	@Nullable
	public static IClientLevelWrapper getWrapper(@Nullable ClientLevel level, boolean bypassLevelKeyManager)
	{
		if (!bypassLevelKeyManager)
		{
			if (level == null)
			{
				return null;
			}
			
			// used if the client is connected to a server that defines the currently loaded level
			IServerKeyedClientLevel overrideLevel = KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel();
			if (overrideLevel != null)
			{
				return overrideLevel;
			}
		}
		
		
		WeakReference<ClientLevelWrapper> levelRef = LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.get(level);
		if (levelRef != null)
		{
			ClientLevelWrapper levelWrapper = levelRef.get();
			if (levelWrapper != null)
			{
				return levelWrapper;
			}
		}
		
		
		return LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.compute(level, (newLevel, newLevelRef) ->
		{
			if (newLevelRef != null)
			{
				ClientLevelWrapper oldLevelWrapper = newLevelRef.get();
				if (oldLevelWrapper != null)
				{
					return newLevelRef;
				}
			}
			
			return new WeakReference<>(new ClientLevelWrapper(newLevel));
		}).get();
	}
	
	@Nullable
	@Override
	public IServerLevelWrapper tryGetServerSideWrapper()
	{
		try
		{
			// this method only makes sense if we are running a single-player server
			if (MINECRAFT.getSingleplayerServer() == null)
			{
				return null;
			}
			
			Iterable<ServerLevel> serverLevels = MINECRAFT.getSingleplayerServer().getAllLevels();
			
			// attempt to find the server level with the same dimension type
			// Note: this assumes only one level per dimension type, multiverse servers may not behave correctly
			ServerLevelWrapper foundLevelWrapper = null;
			for (ServerLevel serverLevel : serverLevels)
			{
				if (serverLevel.dimension() == this.level.dimension())
				{
					foundLevelWrapper = ServerLevelWrapper.getWrapper(serverLevel);
					break;
				}
			}
			
			return foundLevelWrapper;
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to get server side wrapper for client level: " + this.level);
			return null;
		}
	}
	
	
	
	//====================//
	// base level methods //
	//====================//
	
	@Override
	public int getBlockColor(DhBlockPos blockPos, IBiomeWrapper biome, FullDataSourceV2 fullDataSource, IBlockStateWrapper blockWrapper)
	{
		ClientBlockStateColorCache blockColorCache = this.blockColorCacheByBlockState.get(((BlockStateWrapper) blockWrapper).blockState);
		if (blockColorCache == null)
		{
			blockColorCache = this.blockColorCacheByBlockState.computeIfAbsent(
					((BlockStateWrapper) blockWrapper).blockState,
					this.createCachedBlockColorCacheFunc);
		}
		
		return blockColorCache.getColor((BiomeWrapper) biome, fullDataSource, blockPos);
	}
	
	
	@Override
	public int getDirtBlockColor()
	{
		if (this.dirtBlockWrapper == null)
		{
			try
			{
				this.dirtBlockWrapper = (BlockStateWrapper) BlockStateWrapper.deserialize(BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING, this);
			}
			catch (IOException e)
			{
				// shouldn't happen, but just in case
				LOGGER.warn("Unable to get dirt color with resource location ["+BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING+"] with level ["+this+"].", e);
				return -1;
			}
		}
		
		return this.getBlockColor(DhBlockPos.ZERO, BiomeWrapper.EMPTY_WRAPPER, null, this.dirtBlockWrapper);
	}
	
	@Override 
	public void clearBlockColorCache() { this.blockColorCacheByBlockState.clear(); }
	
	@Override
	public IDimensionTypeWrapper getDimensionType()
	{
		#if MC_VER <= MC_1_21_10
		return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.dimensionType());
		#else
		return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.dimensionType(), this.getDimensionName());
		#endif
	}
	
	@Override
	public String getDimensionName()
	{
		#if MC_VER <= MC_1_21_10
		return this.level.dimension().location().toString();
		#else
		return this.level.dimension().identifier().toString();
		#endif
	}
	
	@Override
	public long getHashedSeed() { return this.level.getBiomeManager().biomeZoomSeed; }
	
	@Override
	public String getDhIdentifier() { return this.getHashedSeedEncoded() + "@" + this.getDimensionName(); }
	
	@Override
	public EDhApiLevelType getLevelType() { return EDhApiLevelType.CLIENT_LEVEL; }
	
	public ClientLevel getLevel() { return this.level; }
	
	@Override
	public boolean hasCeiling() { return this.level.dimensionType().hasCeiling(); }
	
	@Override
	public boolean hasSkyLight() { return this.level.dimensionType().hasSkyLight(); }
	
	@Override
	public int getMaxHeight() { return this.level.getHeight(); }
	
	@Override
	public int getMinHeight()
	{
        #if MC_VER < MC_1_17_1
        return 0;
		#elif MC_VER < MC_1_21_3
		return this.level.getMinBuildHeight();
        #else
		return this.level.getMinY();
        #endif
	}
	
	@Override
	public IChunkWrapper tryGetChunk(DhChunkPos pos)
	{
		if (!this.level.hasChunk(pos.getX(), pos.getZ()))
		{
			return null;
		}
		
		ChunkAccess chunk = this.level.getChunk(pos.getX(), pos.getZ(), ChunkStatus.EMPTY, false);
		if (chunk == null)
		{
			return null;
		}
		
		return new ChunkWrapper(chunk, this);
	}
	
	@Override
	public ClientLevel getWrappedMcObject() { return this.level; }
	
	@Override
	public void onUnload() 
	{ 
		LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.remove(this.level);
		this.dhLevel = null;
	}
	
	@Override
	public File getDhSaveFolder()
	{
		if (this.dhLevel == null)
		{
			return null;
		}
		
		return this.dhLevel.getSaveStructure().getSaveFolder(this);
	}
	
	
	
	
	//===================//
	// generic rendering //
	//===================//
	
	@Override
	public void setDhLevel(IDhLevel dhLevel) { this.dhLevel = dhLevel; }
	@Override 
	public IDhLevel getDhLevel() { return this.dhLevel; }
	
	@Override 
	public IDhApiCustomRenderRegister getRenderRegister()
	{
		if (this.dhLevel == null)
		{
			return null;
		}
		
		return this.dhLevel.getGenericRenderer();
	}
	
	@Override
	public Color getCloudColor(float tickDelta)
	{
		#if MC_VER < MC_1_21_3
		Vec3 colorVec3 = this.level.getCloudColor(tickDelta);
		return new Color((float)colorVec3.x, (float)colorVec3.y, (float)colorVec3.z);
		#elif MC_VER <= MC_1_21_10
		int argbColor = this.level.getCloudColor(tickDelta);
		return ColorUtil.toColorObjARGB(argbColor);
		#else
		int argbColor = this.level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, BlockPos.ZERO);
		return new Color(ColorUtil.getRed(argbColor), ColorUtil.getGreen(argbColor), ColorUtil.getBlue(argbColor), 255 /* ignore alpha since DH clouds don't render correctly with transparency */);
		#endif
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString()
	{
		if (this.level == null)
		{
			return "Wrapped{null}";
		}
		
		return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}";
	}
	
}

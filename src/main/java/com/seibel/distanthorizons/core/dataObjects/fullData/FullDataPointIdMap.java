package com.seibel.distanthorizons.core.dataObjects.fullData;

import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WARNING: This is not THREAD-SAFE! <br><br>
 * 
 * Used to map a numerical IDs to a Biome/BlockState pair. <br><br>
 * 
 * TODO the serializing of this map might be really big
 *  since it stringifies every block and biome name, which is quite bulky.
 *  It might be worth while to have a biome and block ID that then both get mapped
 *  to the data point ID to reduce file size.
 *  And/or it would be good to dynamically remove IDs that aren't currently in use.
 * 
 * @author Leetom
 */
public class FullDataPointIdMap
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	/**
	 * Should only be enabled when debugging.
	 * Has the system check if any duplicate Entries were read/written
	 * when (de)serializing.
	 */
	private static final boolean RUN_SERIALIZATION_DUPLICATE_VALIDATION = false;
	/** Distant Horizons - Block State Wrapper */
	public static final String BLOCK_STATE_SEPARATOR_STRING = "_DH-BSW_";
	
	
	/** should only be used for debugging */
	private long pos;
	
	/** The index should be the same as the BlockBiomeWrapperPair's ID */
	private final ArrayList<BlockBiomeWrapperPair> blockBiomePairList = new ArrayList<>();
	private final ConcurrentHashMap<BlockBiomeWrapperPair, Integer> idMap = new ConcurrentHashMap<>();
	
	private int cachedHashCode = 0;
	private net.minecraft.world.level.chunk.NativeDhColumns nativeColumns;
	private net.minecraft.world.level.chunk.NativeDhIdMap nativeMap;
	private boolean nativeAllowed = true;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataPointIdMap(long pos) { this.pos = pos; }
	public static FullDataPointIdMap fromNative(net.minecraft.world.level.chunk.NativeDhColumns columns)
	{
		var map = new FullDataPointIdMap(columns.position());
		map.nativeColumns = columns;
		return map;
	}
	private void detachNative()
	{
		var columns = this.nativeColumns;
		if (columns == null) return;
		this.blockBiomePairList.ensureCapacity(columns.materialCount());
		for (int i = 0; i < columns.materialCount(); i++) this.blockBiomePairList.add(columns.material(i));
		this.nativeColumns = null;
		if (!this.initializeNative(true))
			for (int i = 0; i < this.blockBiomePairList.size(); i++) this.idMap.put(this.blockBiomePairList.get(i), i);
	}
	/** Exact stock wrappers have immutable serial identities and hash semantics. */
	private boolean initializeNative(boolean indexed)
	{
		if (this.nativeMap != null) return true;
		if (!this.nativeAllowed || this.getClass() != FullDataPointIdMap.class) return false;
		for (int i = 0; i < this.size(); i++)
			if (!net.minecraft.world.level.chunk.NativeDhIdMap.supports(this.getEntry(i))) { this.nativeAllowed = false; return false; }
		var map = new net.minecraft.world.level.chunk.NativeDhIdMap();
		for (int i = 0; i < this.size(); i++) map.add(this.getEntry(i), indexed ? 1 : 2);
		this.nativeMap = map;
		return true;
	}
	private boolean ensureNative()
	{
		// Read-only producer maps keep their existing compatibility readers;
		// only a recipient mutation needs a mutable Java binding list.
		return this.initializeNative(true);
	}
	/** Bounded CPU owner for intrinsic DH material consumers; custom inputs decline. */
	public net.minecraft.world.level.chunk.NativeDhIdMap nativeMaterialMap()
	{
		return this.ensureNative() ? this.nativeMap : null;
	}

	private void detachNativeMap()
	{
		if (this.nativeMap != null)
		{
			for (int i = 0; i < this.blockBiomePairList.size(); i++)
				if (this.nativeMap.indexed(i)) this.idMap.put(this.blockBiomePairList.get(i), i);
			this.nativeMap = null;
		}
		this.nativeAllowed = false;
	}


	
	
	
	//=========//
	// getters //
	//=========//
	
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBiomeWrapper getBiomeWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).biome; }
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBlockStateWrapper getBlockStateWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).blockState; }
	/** @throws IndexOutOfBoundsException if the given ID isn't in the {@link FullDataPointIdMap#blockBiomePairList} */
	private BlockBiomeWrapperPair getEntry(int id) throws IndexOutOfBoundsException
	{
		BlockBiomeWrapperPair pair;
		try
		{
			pair = this.nativeColumns == null ? this.blockBiomePairList.get(id) : this.nativeColumns.material(id);
		}
		catch (IndexOutOfBoundsException e)
		{
			throw new IndexOutOfBoundsException("FullData ID Map out of sync for pos: "+DhSectionPos.toString(this.pos)+". ID: ["+id+"] greater than the number of known ID's: ["+this.size()+"].");
		}
		
		return pair;
	}
	
	
	/** @return -1 if the list is empty */
	public int getMaxValidId() { return this.size() - 1; }
	public int size() { return this.nativeColumns == null ? this.blockBiomePairList.size() : this.nativeColumns.materialCount(); }
	
	public boolean isEmpty() { return this.size() == 0; }
	
	public long getPos() { return this.pos; }
	
	
	
	//=========//
	// setters //
	//=========//
	
	/**
	 * If an entry with the given values already exists nothing will
	 * be added but the existing item's ID will still be returned.
	 */
	public int addIfNotPresentAndGetId(IBiomeWrapper biome, IBlockStateWrapper blockState) { return this.addIfNotPresentAndGetId(BlockBiomeWrapperPair.get(blockState, biome)); }
	private int addIfNotPresentAndGetId(BlockBiomeWrapperPair pair)
	{
		this.detachNative();
		if (net.minecraft.world.level.chunk.NativeDhIdMap.supports(pair) && this.initializeNative(true))
		{
			this.blockBiomePairList.ensureCapacity(this.blockBiomePairList.size() + 1);
			int id = this.nativeMap.add(pair, 0);
			if (id == this.blockBiomePairList.size()) { this.blockBiomePairList.add(pair); this.cachedHashCode = 0; }
			return id;
		}
		this.detachNativeMap();
		// try getting the existing ID
		Integer nullableId = this.idMap.get(pair);
		if (nullableId != null)
		{
			return nullableId;
		}
		
		
		// create the new ID
		return this.idMap.compute(pair, (BlockBiomeWrapperPair newPair, Integer currentId) -> 
		{
			if (currentId != null)
			{
				return currentId;
			}
			
			
			// Add the new ID
			currentId = this.blockBiomePairList.size();
			this.blockBiomePairList.add(newPair);
			
			// invalidate the cached hash code
			this.cachedHashCode = 0;
			
			return currentId;
		});
	}
	
	/**
	 * Adds every {@link BlockBiomeWrapperPair} from inputMap into this map. <br>
	 * Allows duplicate entries. <br><br>
	 * 
	 * Allowing duplicate entries should be done if a datasource is just being read in and 
	 * a merge step isn't being done afterwards. If duplicates are removed it may cause 
	 * the ID's to get out of sync since everything will be shifted down after the removed
	 * ID(s).
	 */
	public void addAll(FullDataPointIdMap inputMap)
	{
		// Self-addAll observes its growing input size on the original route.
		if (inputMap != this && this.getClass() == FullDataPointIdMap.class && inputMap.getClass() == FullDataPointIdMap.class
			&& this.ensureNative() && inputMap.ensureNative())
		{
			this.mergeNative(inputMap, true); return;
		}
		for (int i = 0; i < inputMap.size(); i++)
		{
			BlockBiomeWrapperPair pair = inputMap.getEntry(i);
			this.add(pair);
		}
	}
	/** allows for adding duplicate {@link BlockBiomeWrapperPair} */
	private void add(BlockBiomeWrapperPair pair)
	{
		this.detachNative();
		if (net.minecraft.world.level.chunk.NativeDhIdMap.supports(pair) && this.initializeNative(true))
		{
			this.blockBiomePairList.ensureCapacity(this.blockBiomePairList.size() + 1);
			this.nativeMap.add(pair, 1); this.blockBiomePairList.add(pair); this.cachedHashCode = 0; return;
		}
		this.detachNativeMap();
		int id = this.blockBiomePairList.size();
		this.blockBiomePairList.add(pair);
		this.idMap.put(pair, id);
		
		// invalidate the cached hash code
		this.cachedHashCode = 0;
	}
	
	/**
	 * Adds each entry from the given map to this map. <br><br>
	 * 
	 * Note: when using this function be careful about re-mapping the
	 * same data source multiple times.
	 * Doing so may cause indexOutOfBounds issues.
	 *
	 * @return an array of each added entry's ID in this map in order
	 */
	public int[] mergeAndReturnRemappedEntityIds(FullDataPointIdMap inputMap)
	{
		int count = inputMap.size();
		if (this.getClass() == FullDataPointIdMap.class && inputMap.getClass() == FullDataPointIdMap.class
			&& this.ensureNative() && inputMap.ensureNative()) return this.mergeNative(inputMap, false);
		int[] remappedPairIds = new int[count];
		for (int i = 0; i < inputMap.size(); i++)
		{
			BlockBiomeWrapperPair entity = inputMap.getEntry(i);
			int id = this.addIfNotPresentAndGetId(entity);
			remappedPairIds[i] = id;
		}
		
		return remappedPairIds;
	}
	
	/**
	 * {@link #mergeAndReturnRemappedEntityIds} for a generated chunk's materials,
	 * without building a producer map: each material, in order, is added unless
	 * an equal entry exists, giving the same IDs, append order and tint bindings
	 * as the native deduplicating merge.
	 */
	public int[] mergeAndReturnRemappedEntityIds(net.minecraft.world.level.chunk.NativeDhColumns columns)
	{
		int count = columns.materialCount();
		int[] ids = new int[count];
		boolean supported = true;
		for (int i = 0; i < count && supported; i++) supported = net.minecraft.world.level.chunk.NativeDhIdMap.supports(columns.material(i));
		if (supported && this.getClass() == FullDataPointIdMap.class && this.ensureNative())
		{
			this.detachNative();
			int before = this.blockBiomePairList.size();
			this.blockBiomePairList.ensureCapacity(Math.addExact(before, count));
			for (int i = 0; i < count; i++)
			{
				BlockBiomeWrapperPair pair = columns.material(i);
				int id = this.nativeMap.addIfAbsent(pair, this.blockBiomePairList.size());
				if (id == this.blockBiomePairList.size()) this.blockBiomePairList.add(pair);
				ids[i] = id;
			}
			if (this.blockBiomePairList.size() != before) this.cachedHashCode = 0;
			return ids;
		}
		for (int i = 0; i < count; i++) ids[i] = this.addIfNotPresentAndGetId(columns.material(i));
		return ids;
	}
	
	private int[] mergeNative(FullDataPointIdMap inputMap, boolean append)
	{
		this.detachNative();
		int count = inputMap.size(), before = this.blockBiomePairList.size();
		this.blockBiomePairList.ensureCapacity(Math.addExact(before, count));
		var merged = this.nativeMap.merge(inputMap.nativeMap, count, append);
		for (int i = 0; i < merged.completed(); i++)
			if (merged.ids()[i] == this.blockBiomePairList.size()) this.blockBiomePairList.add(inputMap.getEntry(i));
		if (this.blockBiomePairList.size() != before) this.cachedHashCode = 0;
		if (merged.completed() != count) throw new OutOfMemoryError("DH identity merge after " + merged.completed() + " entries");
		if (!append && inputMap == this && this.blockBiomePairList.size() > count)
		{
			// Frozen's input list grows during a self-merge of unindexed imports;
			// it completes this add before the fixed result array bounds failure.
			int id = this.addIfNotPresentAndGetId(this.blockBiomePairList.get(count));
			merged.ids()[count] = id;
		}
		return merged.ids();
	}

	/** Should only be used if this map is going to be reused, otherwise bad things will happen. */
	public void clear(long pos)
	{
		this.pos = pos;
		this.nativeColumns = null;
		this.nativeMap = null; this.nativeAllowed = true;
		this.blockBiomePairList.clear();
		this.idMap.clear();
		this.cachedHashCode = 0;
	}
	
	
	
	//=============//
	// serializing //
	//=============//
	
	/** Serializes all contained entries into the given stream, formatted in UTF */
	public void serialize(DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(this.size());
		
		// only used when debugging
		HashMap<String, BlockBiomeWrapperPair> dataPointEntryBySerialization = new HashMap<>();
		
		for (int i = 0; i < this.size(); i++)
		{
			BlockBiomeWrapperPair pair = this.getEntry(i);
			String entryString = pair.serialize();
			outputStream.writeUTF(entryString);
			
			if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
			{
				if (dataPointEntryBySerialization.containsKey(entryString))
				{
					LOGGER.error("Duplicate serialized pair found with serial: " + entryString);
				}
				if (dataPointEntryBySerialization.containsValue(pair))
				{
					LOGGER.error("Duplicate serialized pair found with value: " + pair.serialize());
				}
				dataPointEntryBySerialization.put(entryString, pair);
			}
		}
	}
	
	/** Creates a new IdBiomeBlockStateMap from the given UTF formatted stream */
	public static FullDataPointIdMap deserialize(DhDataInputStream inputStream, long pos, ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException
	{
		int entityCount = inputStream.readInt();
		if (entityCount < 0)
		{
			throw new DataCorruptedException("FullDataPointIdMap deserialize entry count should have a number greater than or equal to 0, returned value ["+entityCount+"].");
		}
		
		
		// only used when debugging
		HashMap<String, BlockBiomeWrapperPair> dataPointEntryBySerialization = new HashMap<>();
		
		FullDataPointIdMap newMap = new FullDataPointIdMap(pos);
		for (int i = 0; i < entityCount; i++)
		{
			// necessary to prevent issues with deserializing objects after the level has been closed
			if (Thread.interrupted())
			{
				throw new InterruptedException("[" + FullDataPointIdMap.class.getSimpleName() + "] deserializing interrupted.");
			}
			
			
			String entryString = inputStream.readUTF();
			BlockBiomeWrapperPair newPair = BlockBiomeWrapperPair.deserialize(entryString, levelWrapper);
			newMap.blockBiomePairList.add(newPair);
			
			if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
			{
				if (dataPointEntryBySerialization.containsKey(entryString))
				{
					LOGGER.error("Duplicate deserialized entry found with serial: " + entryString);
				}
				if (dataPointEntryBySerialization.containsValue(newPair))
				{
					LOGGER.error("Duplicate deserialized entry found with value: " + newPair.serialize());
				}
				dataPointEntryBySerialization.put(entryString, newPair);
			}
		}
		
		if (newMap.size() != entityCount)
		{
			// if the mappings are out of sync then the LODs will render incorrectly due to IDs being wrong
			LodUtil.assertNotReach("ID maps failed to deserialize for pos: ["+ DhSectionPos.toString(pos)+"], incorrect entity count. Expected count ["+entityCount+"], actual count ["+newMap.size()+"]");
		}
		
		// Frozen imports preserve duplicate slots without populating its lookup.
		newMap.initializeNative(false);
		return newMap;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public boolean equals(Object other)
	{
		if (other == this)
			return true;
/*        if (!(other instanceof FullDataPointIdMap)) return false;
		FullDataPointIdMap otherMap = (FullDataPointIdMap) other;
        if (entries.size() != otherMap.entries.size()) return false;
        for (int i=0; i<entries.size(); i++) {
            if (!entries.get(i).equals(otherMap.entries.get(i))) return false;
        }*/
		return false;
	}
	
	/** Only includes the base data in this object, not the mapping */
	@Override
	public int hashCode()
	{
		if (this.cachedHashCode == 0)
		{
			if (this.ensureNative()) this.cachedHashCode = this.nativeMap.hash(this.pos);
			else this.generateHashCode();
		}
		return this.cachedHashCode;
	}
	private void generateHashCode()
	{
		int result = DhSectionPos.hashCode(this.pos);
		int listHash = 1;
		for (int i = 0; i < this.size(); i++) listHash = 31 * listHash + this.getEntry(i).hashCode();
		for (int i = 0; i < this.size(); i++) result = 31 * result + listHash;
		this.cachedHashCode = result;
	}
	
	
	
}

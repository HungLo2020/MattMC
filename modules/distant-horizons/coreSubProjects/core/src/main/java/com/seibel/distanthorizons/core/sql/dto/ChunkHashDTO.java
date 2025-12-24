/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.sql.dto;

import net.distant_horizons.api.enums.config.EDhApiDataCompressionMode;
import net.distant_horizons.api.enums.config.EDhApiWorldCompressionMode;
import net.distant_horizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import net.distant_horizons.core.dataObjects.fullData.FullDataPointIdMap;
import net.distant_horizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import net.distant_horizons.core.pos.DhChunkPos;
import net.distant_horizons.core.pos.DhSectionPos;
import net.distant_horizons.core.pos.blockPos.DhBlockPos;
import net.distant_horizons.core.util.FullDataPointUtil;
import net.distant_horizons.core.util.LodUtil;
import net.distant_horizons.core.util.objects.DataCorruptedException;
import net.distant_horizons.core.util.objects.dataStreams.DhDataInputStream;
import net.distant_horizons.core.util.objects.dataStreams.DhDataOutputStream;
import net.distant_horizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class ChunkHashDTO implements IBaseDTO<DhChunkPos>
{
	public DhChunkPos pos;
	public int chunkHash;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkHashDTO(DhChunkPos pos, int chunkHash)
	{
		this.pos = pos;
		this.chunkHash = chunkHash;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public DhChunkPos getKey() { return this.pos; }
	
	@Override
	public void close()
	{ /* no closing needed */ }
	
	
	
}

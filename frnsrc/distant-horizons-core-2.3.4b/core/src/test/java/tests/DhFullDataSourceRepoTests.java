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

package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Can also be used to test if there are memory leaks in SQLite
 * and if the {@link FullDataSourceV2DTO}/{@link FullDataSourceV2}'s are using pooled objects correctly
 */
public class DhFullDataSourceRepoTests
{
	public static String DATABASE_TYPE = "jdbc:sqlite";
	public static String DB_FILE_NAME = "test.sqlite";
	
	
	
	@BeforeClass
	public static void testSetup()
	{
		File dbFile = new File(DB_FILE_NAME);
		if (dbFile.exists())
		{
			Assert.assertTrue("unable to delete old test DB File.", dbFile.delete());
		}
	}
	
	
	
	@Test
	public void test()
	{
		try (final FullDataSourceV2Repo repo = new FullDataSourceV2Repo(DATABASE_TYPE, new File(DB_FILE_NAME)))
		{
			
			
			
			//========================//
			// create test datasource //
			//========================//
			
			long pos = DhSectionPos.encode((byte)6, 1, 2);
			FullDataPointIdMap dataMapping = new FullDataPointIdMap(pos);
			LongArrayList[] fullDataArray =  new LongArrayList[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
 
			for (int i = 0; i < FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH; i++)
			{
				fullDataArray[i] = new LongArrayList(1);
				
				for (int j = 0; j < 32; j++)
				{
					fullDataArray[i].add(FullDataPointUtil.encode(j, 1, j, LodUtil.MAX_MC_LIGHT, LodUtil.MAX_MC_LIGHT));
				}
			}
			
			byte[] columnGenStep = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
			Arrays.fill(columnGenStep, (byte)3);
			
			byte[] columnWorldCompressionMode = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
			Arrays.fill(columnWorldCompressionMode, (byte)3);
			
			
			
			FullDataSourceV2 originalDataSource = FullDataSourceV2.createWithData(pos, dataMapping, fullDataArray, columnGenStep, columnWorldCompressionMode);
			FullDataSourceV2DTO originalDto = FullDataSourceV2DTO.CreateFromDataSource(originalDataSource, EDhApiDataCompressionMode.LZMA2);
			repo.save(originalDto);
			
			
			
			//=========================//
			// assert DTO data is the same //
			//=========================//
			
			FullDataSourceV2DTO savedDto = repo.getByKey(pos);
			
			Assert.assertNotNull("Failed to find DTO", savedDto);
			Assert.assertEquals("Pos mismatch", originalDto.pos, savedDto.pos);
			assertArraysAreEqual(originalDto.compressedDataByteArray, savedDto.compressedDataByteArray);
			assertArraysAreEqual(originalDto.compressedColumnGenStepByteArray, savedDto.compressedColumnGenStepByteArray);
			assertArraysAreEqual(originalDto.compressedWorldCompressionModeByteArray, savedDto.compressedWorldCompressionModeByteArray);
			
			
			
			//====================================//
			// assert dataSource data is the same //
			//====================================//
			
			FullDataSourceV2 savedDataSource = savedDto.createUnitTestDataSource();
			
			Assert.assertNotNull("Failed to create DataSource", savedDataSource);
			Assert.assertEquals("Pos mismatch", originalDataSource.getPos(), savedDataSource.getPos());
			assertArraysAreEqual(originalDataSource.columnGenerationSteps, savedDataSource.columnGenerationSteps);
			assertArraysAreEqual(originalDataSource.columnWorldCompressionMode, savedDataSource.columnWorldCompressionMode);
			Assert.assertTrue(originalDataSource.dataPoints.length == savedDataSource.dataPoints.length);
			for (int i = 0; i < FullDataSourceV2.WIDTH*FullDataSourceV2.WIDTH; i++)
			{
				assertArraysAreEqual(originalDataSource.dataPoints[i], savedDataSource.dataPoints[i]);
			}
			
			
			
			
			//=========================//
			// (optional) loop forever //
			//=========================//
			
			// this can be set to "true"
			// so we can profile the DTO creation process and
			// see if there are any object leaks and how the GC
			// handles it
			if (false)
			{
				System.out.println("Initial save/get success, starting long update test for GC validation...");
				
				int poolSize = Runtime.getRuntime().availableProcessors();
				CompletableFuture<?>[] futures = new CompletableFuture[poolSize];
				ThreadPoolExecutor pool = ThreadUtil.makeThreadPool(poolSize, "test pool");
				for (int threadIndex = 0; threadIndex < poolSize; threadIndex++)
				{
					final int finalThreadIndex = threadIndex;
					futures[threadIndex] = CompletableFuture.runAsync(() ->
					{
						// create a new DTO so each thread can have their own to work with,
						// otherwise they'll get stuck on locks for interacting with the same row
						FullDataSourceV2DTO threadDto = null;
						try
						{
							threadDto = FullDataSourceV2DTO.CreateFromDataSource(originalDataSource, EDhApiDataCompressionMode.LZMA2);
						}
						catch (IOException e)
						{
							throw new RuntimeException(e);
						}
						
						// new position so each DTO is different and saved to a different row in the DB
						long threadPos = DhSectionPos.encode((byte)0, finalThreadIndex, 0);
						threadDto.pos = threadPos;
						
						// run for a long time
						for (int j = 0; j < 1_000_000; j++)
						{
							repo.save(threadDto); // runs significantly faster if we don't save
							
							try (FullDataSourceV2DTO pooledDto = repo.getByKey(threadPos))
							{
								Assert.assertNotNull(threadDto);
								Assert.assertEquals(pooledDto.pos, threadDto.pos);
								assertArraysAreEqual(pooledDto.compressedDataByteArray, threadDto.compressedDataByteArray);
								assertArraysAreEqual(pooledDto.compressedColumnGenStepByteArray, threadDto.compressedColumnGenStepByteArray);
								assertArraysAreEqual(pooledDto.compressedWorldCompressionModeByteArray, threadDto.compressedWorldCompressionModeByteArray);
							}
						}
					}, pool);
				}
				
				CompletableFuture.allOf(futures).join();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	
	//================//
	// helper methods //
	//================//
	
	private static void assertArraysAreEqual(ByteArrayList expectedArray, ByteArrayList actualArray)
	{
		Assert.assertEquals("size mismatch", expectedArray.size(), actualArray.size());
		
		for (int i = 0; i < expectedArray.size(); i++)
		{
			byte expectedNumb = expectedArray.getByte(i);
			byte actualNumb = actualArray.getByte(i);
			
			Assert.assertEquals("value mismatch at index ["+i+"]", expectedNumb, actualNumb);
		}
	}
	
	private static void assertArraysAreEqual(LongArrayList expectedArray, LongArrayList actualArray)
	{
		Assert.assertEquals("size mismatch", expectedArray.size(), actualArray.size());
		
		for (int i = 0; i < expectedArray.size(); i++)
		{
			long expectedNumb = expectedArray.getLong(i);
			long actualNumb = actualArray.getLong(i);
			
			Assert.assertEquals("value mismatch at index ["+i+"]", expectedNumb, actualNumb);
		}
	}
	
	
}

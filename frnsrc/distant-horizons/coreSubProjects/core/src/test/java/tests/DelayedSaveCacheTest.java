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

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.DelayedFullDataSourceSaveCache;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A few very basic tests to confirm {@link DelayedFullDataSourceSaveCache}
 * is working properly.
 *
 * @author James Seibel
 * @version 2025-10-02
 */
public class DelayedSaveCacheTest
{
	
	
	// commented out for now since it makes the normal build take longer
	//@Test
	public void CacheExpirationAndPoolingTest() throws InterruptedException
	{
		// how many times any data source has been "written to disk"
		AtomicInteger diskSaveCountRef = new AtomicInteger(0);
		
		DelayedFullDataSourceSaveCache cache = new DelayedFullDataSourceSaveCache((FullDataSourceV2 fullDataSource) -> 
				{
					diskSaveCountRef.getAndIncrement();
					return this.onDataSourceSaveAsync(fullDataSource);
				}, 1_000);
		
		
		
		//==============================//
		// single item and manual flush //
		//==============================//
		
		PhantomArrayListCheckout initialCheckout;
		try (FullDataSourceV2 initialSource = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6, 0, 0)))
		{
			initialCheckout = initialSource.getPhantomArrayCheckoutForUnitTesting();
			cache.writeDataSourceToMemoryAndQueueSave(initialSource);
		}
		Assert.assertEquals("only 1 item should be in the cache", 1, cache.getUnsavedCount());
		Assert.assertEquals("no disk saves should have happened yet", 0, diskSaveCountRef.get());
		
		// manual flush
		cache.flush();
		Assert.assertEquals("memory cache should be empty after", 0, cache.getUnsavedCount());
		Assert.assertEquals("1 manual flush was expected", 1, diskSaveCountRef.get());
		
		
		
		//======================//
		// quick group position //
		//======================//
		
		// write multiple items for the same position
		for (int i = 0; i < 4; i++)
		{
			try (FullDataSourceV2 loopSource = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 6, 0, 0)))
			{
				PhantomArrayListCheckout loopCheckout = loopSource.getPhantomArrayCheckoutForUnitTesting();
				Assert.assertEquals(initialCheckout, loopCheckout);
				
				cache.writeDataSourceToMemoryAndQueueSave(loopSource);
			}
		}
		// each item writes to the same place
		Assert.assertEquals("exactly 1 item should be in the cache", 1, cache.getUnsavedCount());
		Assert.assertEquals("no new saves should have happened yet", 1, diskSaveCountRef.get());
		
		// wait for the cache to clear
		Thread.sleep(2_000);
		Assert.assertEquals("Cache should have automatically cleared due to inactivity", 0, cache.getUnsavedCount());
		Assert.assertEquals("second save after timeout expected", 2, diskSaveCountRef.get());
		
		
		
		//=====================//
		// slow group position //
		//=====================//
		
		// write multiple items for the same position
		for (int i = 0; i < 4; i++)
		{
			try (FullDataSourceV2 loopSource = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 6, 0, 0)))
			{
				PhantomArrayListCheckout loopCheckout = loopSource.getPhantomArrayCheckoutForUnitTesting();
				Assert.assertEquals(initialCheckout, loopCheckout);
				
				cache.writeDataSourceToMemoryAndQueueSave(loopSource);
			}
			
			// long enough to prevent a timeout, but short enough that they don't happen all at once
			Thread.sleep(500);
		}
		// each item writes to the same place
		Assert.assertEquals("exactly 1 item should be in the cache", 1, cache.getUnsavedCount());
		Assert.assertEquals("no new saves should have happened yet", 2, diskSaveCountRef.get());
		
		// wait for the cache to clear
		Thread.sleep(2_000);
		Assert.assertEquals("Cache should have automatically cleared due to inactivity", 0, cache.getUnsavedCount());
		Assert.assertEquals("third timeout expected", 3, diskSaveCountRef.get());
		
	}
	private CompletableFuture<Void> onDataSourceSaveAsync(FullDataSourceV2 fullDataSource)
	{ return CompletableFuture.completedFuture(null); }
	
}
